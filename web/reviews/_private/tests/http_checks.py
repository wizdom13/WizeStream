#!/usr/bin/env python3
"""Exercise real PHP entry points against the disposable CI MySQL database."""
import contextlib
import json
import os
from pathlib import Path
import re
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid
import secrets

WEB = Path(__file__).resolve().parents[3]
if not re.search(r';dbname=wizestream_reviews_test(?:;|$)', os.environ.get('REVIEWS_TEST_DSN', '')):
    raise SystemExit('Use only the disposable wizestream_reviews_test database.')


@contextlib.contextmanager
def server(config_path):
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        port = sock.getsockname()[1]
    origin = f'http://127.0.0.1:{port}'
    # All values are disposable test configuration, never production credentials.
    database = dict(part.split('=', 1) for part in os.environ['REVIEWS_TEST_DSN'].removeprefix('mysql:').split(';') if '=' in part)
    php = lambda value: "'" + str(value).replace('\\', '\\\\').replace("'", "\\'") + "'"
    config_path.write_text("<?php return [\n" +
        "'public_url'=>" + php(origin + '/reviews/') + ",\n" +
        "'moderator_key'=>" + php('a' * 43) + ",'challenge_key'=>" + php('b' * 43) + ",'rate_key'=>" + php('c' * 43) + ",\n" +
        "'database'=>['host'=>" + php(database['host']) + ",'port'=>" + database.get('port', '3306') + ",'name'=>" + php(database['dbname']) +
        ",'username'=>" + php(os.environ['REVIEWS_TEST_USER']) + ",'password'=>" + php(os.environ['REVIEWS_TEST_PASSWORD']) + "]];\n")
    env = dict(os.environ, WIZESTREAM_REVIEWS_CONFIG=str(config_path))
    proc = subprocess.Popen(['php', '-S', f'127.0.0.1:{port}', '-t', str(WEB)], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        for _ in range(100):
            try:
                with urllib.request.urlopen(origin + '/index.html', timeout=1):
                    break
            except urllib.error.URLError:
                if proc.poll() is not None:
                    raise RuntimeError('PHP server exited before starting.')
                time.sleep(.05)
        else:
            raise RuntimeError('PHP server did not start.')
        yield origin
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            proc.kill()
            proc.wait()


def request(origin, path, data=None, headers=None, method=None):
    method = method or ('POST' if data is not None else 'GET')
    supplied = {'Origin': origin, **(headers or {})}
    body = None if data is None else json.dumps(data).encode()
    if data is not None:
        supplied['Content-Type'] = 'application/json'
    req = urllib.request.Request(origin + path, data=body, headers=supplied, method=method)
    try:
        response = urllib.request.urlopen(req, timeout=10)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return response.status, response.headers, response.read().decode()


with tempfile.TemporaryDirectory(prefix='wizestream-private-test-') as private:
    with server(Path(private) / 'reviews-config.php') as origin:
        status, headers, page = request(origin, '/reviews/')
        assert status == 200, (status, page)
        assert headers['Cache-Control'] == 'no-store' and headers['Referrer-Policy'] == 'no-referrer'
        assert headers.get('Set-Cookie') is None
        assert "connect-src 'self'" in headers['Content-Security-Policy']
        assert 'reviews.js' in page and 'reviews.css' in page and 'Community reviews' in page
        assert 'a' * 43 not in page and 'test-only-password' not in page
        status, headers, _ = request(origin, '/reviews/?view=manage')
        assert status == 200 and headers['X-Robots-Tag'] == 'noindex, nofollow'
        for path in ['service.php', 'bootstrap.php', 'config.example.php', 'cleanup.php', 'make-config.php', 'tests/reviews.php']:
            assert request(origin, '/reviews/_private/' + path)[0] == 404, path
        for path in ['/reviews/reviews.css', '/reviews/reviews.js', '/assets/site.css']:
            assert request(origin, path)[0] == 200, path
        _, _, body = request(origin, '/reviews/api.php?action=summary')
        baseline = json.loads(body)['count']
        _, _, body = request(origin, '/reviews/api.php?action=challenge')
        challenge = json.loads(body)['challenge']
        time.sleep(2.1)
        review = {'id': str(uuid.uuid4()), 'deletionToken': secrets.token_urlsafe(32), 'rating': 1,
                  'content': 'HTTP integration: playback needs improvement.', 'nickname': '', 'appVersion': '',
                  'consent': True, 'website': '', 'challenge': challenge}
        status, _, body = request(origin, '/reviews/api.php?action=reviews', review)
        assert status == 201, (status, body)
        status, _, body = request(origin, '/reviews/api.php?action=reviews', review)
        assert status == 200 and json.loads(body)['alreadyReceived']
        moderator = {'X-Moderator-Key': 'a' * 43}
        assert request(origin, '/reviews/api.php?action=moderation')[0] == 401
        status, _, body = request(origin, '/reviews/api.php?action=moderation', {'id': review['id'], 'action': 'publish'}, moderator)
        assert status == 200, (status, body)
        _, _, body = request(origin, '/reviews/api.php?action=summary')
        assert json.loads(body)['count'] == baseline + 1
        author = {'X-Review-Authorization': 'Review ' + review['id'] + ':' + review['deletionToken']}
        status, _, body = request(origin, '/reviews/api.php?action=manage', {}, author)
        assert status == 200 and json.loads(body)['state'] == 'published'
        assert request(origin, '/reviews/api.php?action=manage', {'operation': 'delete'}, author)[0] == 200
        assert request(origin, '/reviews/api.php?action=manage', {}, author)[0] == 404
        _, _, body = request(origin, '/reviews/api.php?action=summary')
        assert json.loads(body)['count'] == baseline
        status, _, body = request(origin, '/reviews/badge.php?format=json')
        assert status == 200 and json.loads(body)['schemaVersion'] == 1
        status, headers, body = request(origin, '/reviews/badge.php')
        assert status == 200 and headers['Content-Type'].startswith('image/svg+xml') and '<svg' in body
        assert request(origin, '/reviews/api.php?action=reviews', review, {'Origin': 'https://evil.example'})[0] == 403

# The loader refuses a configuration file located in the public website, even if it is PHP.
with tempfile.TemporaryDirectory(prefix='config-rejection-test-', dir=WEB / 'reviews') as unsafe:
    with server(Path(unsafe) / 'config.local.php') as origin:
        status, _, body = request(origin, '/reviews/api.php?action=summary')
        assert status == 503 and 'test-only-password' not in body and 'a' * 43 not in body
print('PHP HTTP integration, origin checks, badges, deletion, and private configuration placement passed.')
