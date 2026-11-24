// SPDX-License-Identifier: GPL-3.0-or-later
// Real Chromium UI checks with an isolated same-origin API fixture.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '../../..');
const output = process.env.REVIEWS_BROWSER_ARTIFACTS || path.join(require('node:os').tmpdir(), 'review-browser-artifacts');
fs.mkdirSync(output, { recursive: true });
const imageId = '11111111-1111-4111-8111-111111111111';
const reviewId = '22222222-2222-4222-8222-222222222222';
const token = 'a'.repeat(43);
const imageLimits = { maxCount: 3, maxBytes: 5242880, maxPixels: 12000000, maxEdge: 8192, outputEdge: 2048 };
const entry = { id: imageId, width: 600, height: 1200, bytes: 12345 };
const review = { id: reviewId, rating: 5, nickname: 'UI test', content: 'The attached screenshot explains my suggestion.', appVersion: '1.41.0', date: '2026-11-24', state: 'published', images: [entry] };
const csp = "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' blob:; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'";

let browser;
(async () => {
  browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1050 } });
  const fixturePage = await context.newPage();
  const normalized = await fixturePage.evaluate(async png => {
    const image = new Image(); image.src = 'data:image/png;base64,' + png;
    await image.decode();
    const canvas = document.createElement('canvas');
    canvas.width = image.naturalWidth; canvas.height = image.naturalHeight;
    canvas.getContext('2d').drawImage(image, 0, 0);
    return { width: canvas.width, height: canvas.height, bytes: canvas.toDataURL('image/webp').split(',')[1] };
  }, fs.readFileSync(path.join(root, 'assets/images/playlists.png')).toString('base64'));
  await fixturePage.close();
  const imageBytes = Buffer.from(normalized.bytes, 'base64');
  Object.assign(entry, { width: normalized.width, height: normalized.height, bytes: imageBytes.length });
  const page = await context.newPage();
  page.setDefaultTimeout(15000);
  const errors = [];
  const requests = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
  await page.route('**/*', async route => {
    const request = route.request();
    const url = new URL(request.url());
    if (url.hostname !== 'review-local.test') return route.abort();
    if (url.pathname === '/reviews/api.php') {
      const action = url.searchParams.get('action');
      requests.push({ action, method: request.method(), url: request.url(), headers: request.headers(), body: request.postData() });
      if (action === 'image') {
        return route.fulfill({ contentType: 'image/webp', body: imageBytes });
      }
      let data;
      if (action === 'challenge') data = { challenge: 'test-challenge', minimumWaitMs: 0, imageLimits };
      else if (action === 'reviews' && request.method() === 'GET') data = { count: 1, average: 5, distribution: [0, 0, 0, 0, 1], page: 1, pageSize: 12, reviews: [review] };
      else if (action === 'reviews') data = { id: reviewId, state: 'pending' };
      else if (action === 'manage') data = { ...review, imageLimits, pendingEdit: null };
      else if (action === 'moderation') data = { reviews: [{ ...review, state: 'pending' }], count: 1, page: 1, pageSize: 12 };
      else data = { count: 1, average: 5, distribution: [0, 0, 0, 0, 1] };
      return route.fulfill({ contentType: 'application/json', body: JSON.stringify(data) });
    }
    let file = path.resolve(root, '.' + (url.pathname === '/reviews/' ? '/reviews/index.php' : url.pathname));
    if (!file.startsWith(root + '/') || !fs.existsSync(file)) return route.fulfill({ status: 404, body: 'Not found' });
    let body = fs.readFileSync(file);
    const extension = path.extname(file);
    const type = { '.php': 'text/html', '.html': 'text/html', '.js': 'application/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.png': 'image/png' }[extension] || 'application/octet-stream';
    if (extension === '.php') body = Buffer.from(body.toString().split('?>').slice(1).join('?>'));
    return route.fulfill({ body, contentType: type, headers: extension === '.php' ? { 'Content-Security-Policy': csp } : {} });
  });

  await page.goto('https://review-local.test/reviews/');
  await page.locator('#reviews .review').waitFor();
  await page.locator('#review-image-files:not([disabled])').waitFor();
  await page.locator('#review-image-files').setInputFiles([path.join(root, 'assets/images/playlists.png'), path.join(root, 'assets/images/channel.png')]);
  await page.getByText('2 of 3 images selected.', { exact: false }).waitFor();
  assert.equal(requests.filter(request => request.method === 'POST').length, 0, 'Selecting images must not upload anything');
  assert.equal(await page.locator('#review-image-picker .image-preview').count(), 2);
  await page.locator('#review-image-picker').screenshot({ path: path.join(output, 'picker-desktop.png') });
  await page.locator('#review-image-picker .review-image-button').first().click();
  await page.locator('#review-image-dialog[open] img').waitFor();
  await page.screenshot({ path: path.join(output, 'enlarged-desktop.png') });
  await page.keyboard.press('Escape');
  await page.locator('#review-image-dialog').waitFor({ state: 'hidden' });

  await page.locator('label[for="rating-5"]').click();
  await page.locator('#review-content').fill('This is a browser test of the optional image submission flow.');
  await page.locator('#review-consent').check();
  await page.locator('#submit-review').click();
  await page.getByText('Received. Your rating and review will appear after moderation.', { exact: true }).waitFor();
  const submission = requests.find(request => request.action === 'reviews' && request.method === 'POST');
  assert.match(submission.headers['content-type'], /^multipart\/form-data; boundary=/);
  assert.match(submission.body, /name="payload"/);
  assert.match(submission.body, /"newImageCount":2/);
  assert.equal((submission.body.match(/name="images\[\]"/g) || []).length, 2);

  await page.setViewportSize({ width: 360, height: 800 });
  await page.goto('https://review-local.test/reviews/');
  await page.locator('#review-image-files:not([disabled])').waitFor();
  await page.locator('#review-image-files').setInputFiles(path.join(root, 'assets/images/playlists.png'));
  await page.getByText('1 of 3 images selected.', { exact: false }).waitFor();
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true, 'Mobile page must not overflow horizontally');
  await page.locator('#review-image-picker').screenshot({ path: path.join(output, 'picker-mobile.png') });
  await page.locator('.feedback-contact').screenshot({ path: path.join(output, 'contact-mobile.png') });
  await page.locator('#review-image-picker .review-image-button').click();
  await page.locator('#review-image-dialog[open] img').waitFor();
  await page.screenshot({ path: path.join(output, 'enlarged-mobile.png') });
  await page.keyboard.press('Escape');

  await page.goto('https://review-local.test/reviews/?view=manage#' + reviewId + ':' + token);
  await page.locator('#edit-review-form:not([hidden])').waitFor();
  await page.locator('#edit-image-picker .image-preview').waitFor();
  await page.locator('#edit-image-picker').screenshot({ path: path.join(output, 'manage-mobile.png') });
  const ownerImages = requests.filter(request => request.action === 'image' && request.headers['x-review-authorization']);
  assert.ok(ownerImages.length > 0, 'Private image requests must include the owner header');
  for (const request of ownerImages) assert.equal(new URL(request.url).hash, '', 'Private token must never appear in image URL');
  await page.locator('#edit-image-picker').getByRole('button', { name: 'Remove image 1', exact: true }).click();
  assert.equal(await page.locator('#edit-image-picker .image-preview').count(), 0);
  assert.equal(await page.locator('#managed-review .review-image-button').count(), 1, 'Removing a proposed image must retain the published card');

  await page.goto('https://review-local.test/reviews/?view=moderate');
  await page.locator('#moderator-key').fill('m'.repeat(43));
  await page.locator('#moderator-login button').click();
  await page.locator('#moderator-reviews .review-image-button').waitFor();
  await page.locator('#moderator-reviews .review-image-button').click();
  await page.locator('#review-image-dialog[open] img').waitFor();
  await page.keyboard.press('Escape');
  await page.locator('#moderator-logout').click();
  assert.equal(await page.locator('#moderator-reviews').textContent(), '');
  assert.equal(await page.locator('#review-image-dialog').getAttribute('open'), null);
  assert.ok(requests.some(request => request.action === 'image' && request.headers['x-moderator-key']), 'Moderator image requests must use an authorization header');
  assert.deepEqual(errors, [], 'No browser or CSP errors');
  await browser.close();
  console.log(JSON.stringify({ status: 'passed', cases: ['local-only previews', 'multipart submission', 'desktop/mobile enlargement', '360px layout', 'owner header authorization', 'staged removal', 'moderator lock cleanup', 'CSP'], screenshots: output }, null, 2));
})().catch(async error => { console.error(error); if (browser) await browser.close(); process.exitCode = 1; });
