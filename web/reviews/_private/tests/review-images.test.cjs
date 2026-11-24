// SPDX-License-Identifier: GPL-3.0-or-later
'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const images = require('../../review-images.js');
const limits = { maxCount: 3, maxBytes: 5242880, maxPixels: 12000000, maxEdge: 8192 };
const id = '123e4567-e89b-42d3-a456-426614174000';
const token = 'a'.repeat(43);
const png = new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 0, 0, 0, 0, 0]);

test('pending image credentials are headers only, with no cookies, referrer, or cache', () => {
  global.location = { href: `https://wizestream.org/reviews/?view=manage#${id}:${token}` };
  for (const [authorization, header] of [[`Review ${id}:${token}`, 'X-Review-Authorization'], [`Bearer ${token}`, 'X-Moderator-Key']]) {
    const request = images.request(id, 'full', authorization);
    assert.equal(request.url.hash, '');
    assert.equal(request.url.searchParams.get('id'), id);
    assert.equal(request.url.toString().includes(token), false);
    assert.equal(request.options.headers[header], header === 'X-Moderator-Key' ? token : authorization);
    assert.equal(request.options.credentials, 'omit'); assert.equal(request.options.cache, 'no-store');
    assert.equal(request.options.referrerPolicy, 'no-referrer');
  }
  assert.equal(images.request(id, 'thumb').options.headers['X-Review-Authorization'], undefined);
  assert.throws(() => images.request('../config.php', 'full'), /unavailable/);
  assert.throws(() => images.request(id, 'original'), /unavailable/);
});

test('local image validation bounds bytes, pixels and formats before preview', async () => {
  assert.equal(images.signature(png), 'image/png');
  assert.equal(images.signature(new Uint8Array([255, 216, 255])), 'image/jpeg');
  assert.equal(images.signature(new TextEncoder().encode('RIFF0000WEBP')), 'image/webp');
  assert.equal(images.signature(new TextEncoder().encode('<svg><script>')), null);
  assert.throws(() => images.checkSize({ size: 0 }, limits), /5 MiB/);
  assert.throws(() => images.checkSize({ size: limits.maxBytes + 1 }, limits), /5 MiB/);
  images.checkSize({ size: limits.maxBytes }, limits);
  assert.throws(() => images.checkDimensions(4000, 4000, limits), /12 megapixels/);
  assert.throws(() => images.checkDimensions(8193, 1, limits), /8,192/);
  images.checkDimensions(4000, 3000, limits);
  global.Image = class { naturalWidth = 40; naturalHeight = 30; decode() { return Promise.resolve(); } };
  await images.validateFile(new File([png], 'image.png'), limits);
  await assert.rejects(() => images.validateFile(new File(['<svg/>'], 'pretend.png', { type: 'image/png' }), limits), /JPEG, PNG or WebP/);
});

class Element {
  constructor(tag = 'div') { this.tagName = tag; this.children = []; this.attributes = {}; this.textContent = ''; this.disabled = false; }
  append(...children) { this.children.push(...children); }
  replaceChildren(...children) { this.children = children; }
  setAttribute(name, value) { this.attributes[name] = value; }
  contains(element) { return this === element || this.children.some(child => child.contains?.(element)); }
  querySelector(selector) { return this.selectors?.[selector]; }
}
function pickerFixture() {
  const input = new Element('input'); input.files = []; input.value = '';
  const previews = new Element(); const status = new Element(); const container = new Element();
  container.append(input, previews, status);
  container.selectors = { 'input[type="file"]': input, '[data-image-previews]': previews, '[data-image-status]': status };
  global.document = { createElement: tag => new Element(tag) };
  global.Image = class { naturalWidth = 40; naturalHeight = 30; decode() { return Promise.resolve(); } };
  const picker = images.picker(container);
  return { picker, input, previews, status, container };
}

test('picker previews locally, enforces combined count, supports removal, and never uploads before submit', async () => {
  let networkRequests = 0; global.fetch = async () => { networkRequests++; throw new Error('Unexpected request'); };
  const f = pickerFixture(); assert.equal(f.input.disabled, true); f.picker.configure(limits);
  assert.equal(f.input.disabled, false);
  f.input.files = [new File([png], 'one.png'), new File([png], 'two.png')]; await f.input.onchange();
  assert.equal(f.picker.payload().files.length, 2); assert.equal(f.previews.children.length, 2); assert.equal(networkRequests, 0);
  f.input.files = [new File([png], 'three.png'), new File([png], 'four.png')]; await f.input.onchange();
  assert.equal(f.picker.payload().files.length, 2); assert.match(f.status.textContent, /at most three/);
  f.previews.children[0].children[1].onclick(); assert.equal(f.picker.payload().files.length, 1);
  f.picker.lock(true); assert.equal(f.input.disabled, true); assert.equal(f.previews.children[0].children[1].disabled, true);
  f.picker.lock(false); assert.equal(f.input.disabled, false);
  f.input.files = [new File(['<svg/>'], 'invalid.png')]; await f.input.onchange();
  assert.equal(f.picker.payload().files.length, 1); assert.match(f.status.textContent, /JPEG, PNG or WebP/);
  f.picker.dispose(); assert.equal(f.input.onchange, null); assert.equal(networkRequests, 0);
});

test('existing selected images retain their ids and removals survive a disabled upload server', () => {
  const f = pickerFixture();
  global.fetch = async () => ({ ok: false, headers: new Headers() });
  f.picker.configure(null, [{ id, width: 40, height: 30 }]);
  assert.deepEqual(f.picker.payload().keepImageIds, [id]); assert.equal(f.input.disabled, true);
  f.previews.children[0].children[1].onclick(); assert.deepEqual(f.picker.payload().keepImageIds, []);
  f.picker.dispose();
});

function apiFixture(response) {
  const requests = [];
  const source = fs.readFileSync(path.join(__dirname, '../../reviews.js'), 'utf8');
  const scope = { URL, FormData, location: { origin: 'https://wizestream.org', href: 'https://wizestream.org/reviews/' },
    fetch: async (url, options) => { requests.push({ url, options }); return response; } };
  vm.runInNewContext(source.slice(0, source.indexOf('function reviewCard')) + '\n;globalThis.call = api;', scope);
  return { requests, call: scope.call };
}
test('multipart sends explicit image count, fixed filenames and header authorization; JSON stays compatible', async () => {
  const f = apiFixture({ ok: true, status: 201, json: async () => ({ state: 'pending' }) });
  await f.call('/api/manage', { method: 'POST', authorization: `Review ${id}:${token}`,
    data: { operation: 'edit', imageConsent: true, keepImageIds: [id] }, files: [new File([png], 'private-name.png')] });
  const sent = f.requests[0]; assert.equal(sent.options.headers['Content-Type'], undefined);
  assert.equal(sent.options.headers['X-Review-Authorization'], `Review ${id}:${token}`);
  assert.equal(sent.options.body.get('images[]').name, 'review-image');
  const payload = JSON.parse(sent.options.body.get('payload'));
  assert.equal(payload.newImageCount, 1); assert.equal(payload.imageConsent, true); assert.deepEqual(payload.keepImageIds, [id]);
  await f.call('/api/manage', { method: 'POST', data: { operation: 'read' } });
  assert.equal(f.requests[1].options.headers['Content-Type'], 'application/json');
  assert.deepEqual(JSON.parse(f.requests[1].options.body), { operation: 'read' });
});
test('host-level upload rejection produces a useful message even when its response is HTML', async () => {
  const f = apiFixture({ ok: false, status: 413, json: async () => { throw new Error('HTML response'); } });
  await assert.rejects(() => f.call('/api/reviews', { method: 'POST', data: {} }), error => error.status === 413 && /smaller images/.test(error.message));
});
