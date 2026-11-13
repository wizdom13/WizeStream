#!/usr/bin/env python3
"""Validate the generated site without fetching remote resources or dependencies."""
from __future__ import annotations

import json
import re
import sys
from collections import Counter
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parent
VOID = {'area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input', 'link', 'meta', 'param', 'source', 'track', 'wbr'}


class Page(HTMLParser):
    def __init__(self, path):
        super().__init__(convert_charrefs=True)
        self.path = path
        self.ids, self.links, self.nodes, self.errors, self.stack = [], [], [], [], []
        self.h1_count, self.title, self.text = 0, [], []

    def handle_starttag(self, tag, attributes):
        attrs = dict(attributes)
        self.nodes.append((tag, attrs))
        if 'id' in attrs:
            self.ids.append(attrs['id'])
        if tag == 'h1':
            self.h1_count += 1
        for key in ('href', 'src'):
            if key in attrs:
                self.links.append((tag, key, attrs[key]))
        if tag == 'img' and ('alt' not in attrs or not attrs.get('width') or not attrs.get('height')):
            self.errors.append('Image needs alt and explicit width/height')
        if tag == 'button' and attrs.get('type') not in ('button', 'reset', 'submit'):
            self.errors.append('Button needs an explicit type')
        if tag == 'script' and not attrs.get('src'):
            self.errors.append('Inline script is unexpected')
        if tag not in VOID:
            self.stack.append(tag)

    def handle_startendtag(self, tag, attributes):
        self.handle_starttag(tag, attributes)
        if tag not in VOID:
            self.handle_endtag(tag)

    def handle_endtag(self, tag):
        if not self.stack or self.stack[-1] != tag:
            self.errors.append(f'Unexpected closing tag </{tag}>')
        else:
            self.stack.pop()

    def handle_data(self, text):
        if 'title' in self.stack:
            self.title.append(text)
        if 'main' in self.stack:
            self.text.append(text)


def check():
    errors = []
    manifest = json.loads((ROOT / 'build-manifest.json').read_text())
    data = json.loads((ROOT / 'content/site.json').read_text())
    expected = set(manifest['pages'])
    actual = {str(path.relative_to(ROOT)) for path in ROOT.rglob('*.html')}
    if expected != actual:
        errors.append(f'Page manifest mismatch: missing={expected-actual}, extra={actual-expected}')
    pages = {}
    for name in sorted(actual):
        path = ROOT / name
        parser = Page(path)
        parser.feed(path.read_text(encoding='utf8'))
        parser.close()
        pages[path] = parser
        errors.extend(f'{name}: {error}' for error in parser.errors)
        if parser.stack:
            errors.append(f'{name}: Unclosed tags {parser.stack}')
        for id_, count in Counter(parser.ids).items():
            if count > 1:
                errors.append(f'{name}: Duplicate id {id_}')
        if parser.h1_count != 1:
            errors.append(f'{name}: Expected exactly one h1')
        if not ''.join(parser.title).strip():
            errors.append(f'{name}: Missing title')
        for tag, attribute, value in [('html', 'lang', 'en'), ('meta', 'name', 'viewport'), ('meta', 'name', 'description'), ('main', 'id', 'main')]:
            if not any(t == tag and a.get(attribute) == value for t, a in parser.nodes):
                errors.append(f'{name}: Missing {tag} {attribute}={value}')
        if not any(t == 'a' and a.get('href') == '#main' and 'skip-link' in a.get('class', '') for t, a in parser.nodes):
            errors.append(f'{name}: Missing skip link')
        labels = {a.get('for') for t, a in parser.nodes if t == 'label'}
        for tag, attrs in parser.nodes:
            if tag in ('input', 'select') and not (attrs.get('id') in labels or attrs.get('aria-label')):
                errors.append(f'{name}: Unlabelled {tag} {attrs.get("id")}')
            for attribute in ('aria-controls', 'aria-labelledby', 'aria-describedby'):
                for target in attrs.get(attribute, '').split():
                    if target not in parser.ids:
                        errors.append(f'{name}: Missing {attribute} target {target}')
        if name.startswith('docs/') and name != 'docs/index.html' and len(' '.join(parser.text).split()) < 300:
            errors.append(f'{name}: Guide content is unexpectedly short')

    link_count = 0
    for path, page in pages.items():
        for tag, attribute, value in page.links:
            name = str(path.relative_to(ROOT))
            parsed = urlsplit(value)
            if not value or value == '#':
                errors.append(f'{name}: Empty/placeholder link')
                continue
            if parsed.scheme or parsed.netloc:
                if parsed.scheme != 'https' or not parsed.hostname:
                    errors.append(f'{name}: Unexpected link scheme: {value}')
                if attribute == 'src' or tag == 'link' and attribute == 'href':
                    # Canonical URLs are metadata; styles/fonts/images/scripts must be local.
                    if not any(t == tag and a.get('href') == value and a.get('rel') == 'canonical' for t, a in page.nodes):
                        errors.append(f'{name}: Remote resource: {value}')
                continue
            if parsed.path.startswith('/'):
                errors.append(f'{name}: Root-absolute link breaks project-path hosting: {value}')
            target = (path.parent / unquote(parsed.path)).resolve() if parsed.path else path
            if not target.is_relative_to(ROOT) or not target.is_file():
                errors.append(f'{name}: Missing/unsafe target {value}')
            elif parsed.fragment and target in pages and unquote(parsed.fragment) not in pages[target].ids:
                errors.append(f'{name}: Missing anchor {value}')
            link_count += 1

    source = (ROOT / 'assets/search-index.js').read_text(encoding='utf8')
    prefix = 'window.WizeStreamSearchIndex = '
    if not source.startswith(prefix):
        errors.append('Search index format is invalid')
        entries = []
    else:
        entries = json.loads(source[len(prefix):].strip().removesuffix(';'))
    if {entry['url'] for entry in entries} != expected or len(entries) != len(expected):
        errors.append('Search index does not cover every page exactly once')
    if not any('download window' in entry['text'].lower() for entry in entries):
        errors.append('Search index is missing guide body text')

    comparison = pages.get(ROOT / 'compare.html')
    if comparison:
        rows = [a for t, a in comparison.nodes if t == 'tr' and 'data-comparison-row' in a]
        if len(rows) != len(data['comparison']):
            errors.append('Comparison is missing feature rows')
        expected_keys = {project['key'] for project in data['projects']}
        for row in data['comparison']:
            if set(row['cells']) != expected_keys:
                errors.append(f'Incomplete comparison: {row["feature"]}')
            for cell in row['cells'].values():
                if cell['level'] not in ('yes', 'partial', 'unknown', 'no', 'archived') or not cell['text'].strip():
                    errors.append(f'Invalid comparison status: {row["feature"]}')

    titles = [''.join(p.title).strip() for p in pages.values()]
    if len(titles) != len(set(titles)):
        errors.append('Page titles are not unique')
    if errors:
        print('\n'.join(errors), file=sys.stderr)
        return 1
    print(f'Validated {len(pages)} pages, {len(data["docs"])} guides, {len(data["comparison"])} comparison rows, and {link_count} internal links/resources.')
    print('Markup structure, metadata, labels, local assets, anchors and full-text search coverage passed.')
    return 0


if __name__ == '__main__':
    raise SystemExit(check())
