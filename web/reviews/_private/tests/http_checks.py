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
import struct
import zlib

WEB = Path(__file__).resolve().parents[3]
if not re.search(r';dbname=wizestream_reviews_test(?:;|$)', os.environ.get('REVIEWS_TEST_DSN', '')):
    raise SystemExit('Use only the disposable wizestream_reviews_test database.')


@contextlib.contextmanager
def server(config_path, ini=None):
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
        "'images_dir'=>" + php(config_path.parent / 'review-images') + ",'per_hour_limit'=>100,\n" +
        "'database'=>['host'=>" + php(database['host']) + ",'port'=>" + database.get('port', '3306') + ",'name'=>" + php(database['dbname']) +
        ",'username'=>" + php(os.environ['REVIEWS_TEST_USER']) + ",'password'=>" + php(os.environ['REVIEWS_TEST_PASSWORD']) + "]];\n")
    env = dict(os.environ, WIZESTREAM_REVIEWS_CONFIG=str(config_path))
    settings = {'upload_max_filesize': '5M', 'post_max_size': '20M', 'max_file_uploads': '3', 'memory_limit': '256M', **(ini or {})}
    options = [item for name, value in settings.items() for item in ['-d', f'{name}={value}']]
    proc = subprocess.Popen(['php', *options, '-S', f'127.0.0.1:{port}', '-t', str(WEB)], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
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


def request(origin, path, data=None, headers=None, method=None, raw=False, body=None):
    method = method or ('POST' if data is not None or body is not None else 'GET')
    supplied = {'Origin': origin, **(headers or {})}
    if data is not None:
        body = json.dumps(data).encode()
        supplied['Content-Type'] = 'application/json'
    req = urllib.request.Request(origin + path, data=body, headers=supplied, method=method)
    try:
        response = urllib.request.urlopen(req, timeout=10)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        content = response.read()
        return response.status, response.headers, content if raw else content.decode()


def multipart(origin, action, data, files, headers=None):
    boundary = 'wize-test-' + secrets.token_hex(16)
    parts = [f'--{boundary}\r\nContent-Disposition: form-data; name="payload"\r\n\r\n'.encode() + json.dumps(data).encode() + b'\r\n']
    for file in files:
        # Deliberately misleading MIME/name: only decoded bytes may determine stored type and name.
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="images[]"; filename="../private.svg"\r\nContent-Type: image/svg+xml\r\n\r\n'.encode() + file + b'\r\n')
    body = b''.join(parts) + f'--{boundary}--\r\n'.encode()
    return request(origin, '/reviews/api.php?action=' + action, headers={**(headers or {}), 'Content-Type': f'multipart/form-data; boundary={boundary}'}, body=body)


def png():
    def chunk(kind, value):
        return struct.pack('!I', len(value)) + kind + value + struct.pack('!I', zlib.crc32(kind + value))
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('!IIBBBBB', 2, 2, 8, 2, 0, 0, 0)) +
            chunk(b'IDAT', zlib.compress((b'\x00' + b'\xff\x00\x00' * 2) * 2)) + chunk(b'IEND', b''))


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
        for path in ['service.php', 'images.php', 'request.php', 'bootstrap.php', 'config.example.php', 'cleanup.php', 'make-config.php', 'tests/reviews.php', 'tests/images.php']:
            assert request(origin, '/reviews/_private/' + path)[0] == 404, path
        for path in ['/reviews/reviews.css', '/reviews/reviews.js', '/reviews/review-images.js', '/assets/site.css']:
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

        # Actual multipart uploads, binary image access and staged publication.
        _, _, body = request(origin, '/reviews/api.php?action=challenge')
        challenge_data = json.loads(body)
        assert challenge_data['imageLimits']['maxCount'] == 3
        time.sleep(2.1)
        illustrated = {**review, 'id': str(uuid.uuid4()), 'deletionToken': secrets.token_urlsafe(32),
                       'challenge': challenge_data['challenge'], 'imageConsent': True, 'newImageCount': 1}
        status, _, body = multipart(origin, 'reviews', illustrated, [png()])
        assert status == 201, (status, body)
        owner = {'X-Review-Authorization': 'Review ' + illustrated['id'] + ':' + illustrated['deletionToken']}
        _, _, body = request(origin, '/reviews/api.php?action=manage', {}, owner)
        old_image = json.loads(body)['images'][0]['id']
        image_path = '/reviews/api.php?action=image&id=' + old_image + '&variant=full'
        assert request(origin, image_path)[0] == 404
        assert request(origin, image_path + '&token=' + illustrated['deletionToken'])[0] == 404
        status, headers, content = request(origin, image_path, headers=owner, raw=True)
        assert status == 200 and content[:4] == b'RIFF'
        assert headers['Content-Type'] == 'image/webp' and headers['Cache-Control'] == 'no-store'
        assert headers['Cross-Origin-Resource-Policy'] == 'same-origin' and headers.get('Set-Cookie') is None
        assert request(origin, image_path, headers=moderator, raw=True)[0] == 200
        edits = {'operation': 'edit', 'rating': 2, 'content': 'Illustrated feedback with a replacement image.',
                 'keepImageIds': [], 'newImageCount': 1, 'imageConsent': True}
        assert multipart(origin, 'manage', edits, [b'<svg><script>test</script></svg>'], owner)[0] == 400
        assert multipart(origin, 'manage', {**edits, 'newImageCount': 2}, [png()], owner)[0] == 400
        assert multipart(origin, 'manage', edits, [png()], {**owner, 'Origin': 'https://evil.example'})[0] == 403
        assert request(origin, '/reviews/api.php?action=moderation', {'id': illustrated['id'], 'action': 'publish'}, moderator)[0] == 200
        assert multipart(origin, 'manage', edits, [png()], owner)[0] == 202
        _, _, body = request(origin, '/reviews/api.php?action=manage', {}, owner)
        new_image = json.loads(body)['pendingEdit']['images'][0]['id']
        new_path = '/reviews/api.php?action=image&id=' + new_image + '&variant=thumb'
        assert request(origin, new_path)[0] == 404 and request(origin, image_path, raw=True)[0] == 200
        assert request(origin, '/reviews/api.php?action=moderation', {'id': illustrated['id'], 'action': 'approve-edit'}, moderator)[0] == 200
        assert request(origin, new_path, raw=True)[0] == 200 and request(origin, image_path)[0] == 404
        assert len(list((Path(private) / 'review-images').glob('*.webp'))) == 2
        assert request(origin, '/reviews/api.php?action=manage', {'operation': 'delete'}, owner)[0] == 200
        assert request(origin, new_path)[0] == 404
        assert list((Path(private) / 'review-images').glob('*.webp')) == []

    # Common hosting limits fail explicitly instead of publishing a partial image set.
    with server(Path(private) / 'reviews-config.php', {'post_max_size': '1K'}) as origin:
        status, _, body = multipart(origin, 'reviews', {'newImageCount': 1}, [png() + b'x' * 2000])
        assert status == 413 and 'server upload limit' in json.loads(body)['error'], (status, body)
    with server(Path(private) / 'reviews-config.php', {'max_file_uploads': '1'}) as origin:
        status, _, body = multipart(origin, 'reviews', {'newImageCount': 2}, [png(), png()])
        assert status == 400 and 'did not arrive' in json.loads(body)['error'], (status, body)
    with server(Path(private) / 'reviews-config.php', {'upload_max_filesize': '1K'}) as origin:
        _, _, body = request(origin, '/reviews/api.php?action=challenge')
        time.sleep(2.1)
        oversized = {**review, 'id': str(uuid.uuid4()), 'deletionToken': secrets.token_urlsafe(32),
                     'challenge': json.loads(body)['challenge'], 'newImageCount': 1, 'imageConsent': True}
        status, _, body = multipart(origin, 'reviews', oversized, [png() + b'x' * 2000])
        assert status == 413 and 'server upload limit' in json.loads(body)['error'], (status, body)

# The loader refuses a configuration file located in the public website, even if it is PHP.
with tempfile.TemporaryDirectory(prefix='config-rejection-test-', dir=WEB / 'reviews') as unsafe:
    with server(Path(unsafe) / 'config.local.php') as origin:
        status, _, body = request(origin, '/reviews/api.php?action=summary')
        assert status == 503 and 'test-only-password' not in body and 'a' * 43 not in body
print('PHP HTTP integration, multipart limits, private/public images, atomic image edits, origin checks, badges, deletion, and private configuration placement passed.')
