// SPDX-License-Identifier: GPL-3.0-or-later
// Local previews and same-origin, header-authorized image requests. No pre-submit uploads.
(() => {
  const resources = new Map();
  const make = (tag, text, className) => {
    const element = document.createElement(tag);
    if (text !== undefined) element.textContent = text;
    if (className) element.className = className;
    return element;
  };
  function resource(owner) {
    if (!resources.has(owner)) resources.set(owner, { urls: new Set(), controllers: new Set() });
    return resources.get(owner);
  }
  function objectURL(owner, blob) {
    const url = URL.createObjectURL(blob); resource(owner).urls.add(url); return url;
  }
  function release(container) {
    for (const [owner, value] of resources) {
      if (owner === container || container.contains(owner)) {
        for (const controller of value.controllers) controller.abort();
        for (const url of value.urls) URL.revokeObjectURL(url);
        resources.delete(owner);
      }
    }
  }
  function request(id, variant, authorization) {
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(id) ||
        !['thumb', 'full'].includes(variant)) throw new Error('Image unavailable.');
    const url = new URL('api.php', location.href);
    url.search = new URLSearchParams({ action: 'image', id, variant }).toString();
    url.hash = '';
    const headers = { Accept: 'image/webp' };
    if (authorization?.startsWith('Bearer ')) headers['X-Moderator-Key'] = authorization.slice(7);
    else if (authorization) headers['X-Review-Authorization'] = authorization;
    return { url, options: { headers, credentials: 'omit', cache: 'no-store', referrerPolicy: 'no-referrer' } };
  }
  async function load(owner, id, variant, authorization) {
    const { url, options } = request(id, variant, authorization);
    const state = resource(owner); const controller = new AbortController();
    state.controllers.add(controller);
    try {
      const response = await fetch(url, { ...options, signal: controller.signal });
      if (!response.ok || response.headers.get('Content-Type')?.split(';')[0] !== 'image/webp') throw new Error('Image unavailable.');
      const blob = await response.blob();
      if (controller.signal.aborted || !resources.has(owner)) throw new DOMException('Cancelled', 'AbortError');
      if (blob.size > 5 * 1024 * 1024) throw new Error('Image unavailable.');
      return objectURL(owner, blob);
    } finally { state.controllers.delete(controller); }
  }
  let dialog;
  function close() { if (dialog?.open) dialog.close(); }
  function show(entry, authorization, label) {
    dialog ||= document.getElementById('review-image-dialog');
    release(dialog);
    const content = dialog.querySelector('[data-image-content]');
    content.replaceChildren(make('p', 'Loading image…', 'quiet'));
    document.getElementById('review-image-title').textContent = label;
    dialog.onclose = () => { release(dialog); content.replaceChildren(); };
    dialog.querySelector('[data-close-image]').onclick = close;
    if (!dialog.open) dialog.showModal();
    const display = url => {
      if (!dialog.open) return;
      const image = make('img'); image.alt = label; image.src = url;
      content.replaceChildren(image);
    };
    if (entry.file) display(objectURL(dialog, entry.file));
    else load(dialog, entry.id, 'full', authorization).then(display).catch(error => {
      if (error.name !== 'AbortError' && dialog.open) content.replaceChildren(make('p', error.message, 'quiet'));
    });
  }
  function thumbnail(owner, entry, authorization, index) {
    const button = make('button', undefined, 'review-image-button');
    button.type = 'button'; const label = `Review image ${index + 1}`;
    button.setAttribute('aria-label', `Enlarge ${label.toLowerCase()}`);
    const image = make('img'); image.alt = label; image.decoding = 'async';
    button.append(image);
    button.onclick = () => show(entry, authorization, label);
    if (entry.file) image.src = objectURL(owner, entry.file);
    else load(owner, entry.id, 'thumb', authorization).then(url => { image.src = url; }).catch(error => {
      if (error.name !== 'AbortError') { button.replaceChildren(make('span', 'Image unavailable')); button.disabled = true; }
    });
    return button;
  }
  function gallery(images = [], authorization) {
    const group = make('div', undefined, 'review-images');
    group.setAttribute('aria-label', 'Attached review images');
    images.forEach((entry, index) => group.append(thumbnail(group, entry, authorization, index)));
    return group;
  }
  function signature(bytes) {
    if (bytes[0] === 255 && bytes[1] === 216 && bytes[2] === 255) return 'image/jpeg';
    if ([137, 80, 78, 71, 13, 10, 26, 10].every((byte, index) => bytes[index] === byte)) return 'image/png';
    if (String.fromCharCode(...bytes.slice(0, 4)) === 'RIFF' && String.fromCharCode(...bytes.slice(8, 12)) === 'WEBP') return 'image/webp';
    return null;
  }
  function checkSize(file, limits) {
    if (!file.size || file.size > limits.maxBytes) throw new Error('Each image must be no larger than 5 MiB.');
  }
  function checkDimensions(width, height, limits) {
    if (!width || !height || width > limits.maxEdge || height > limits.maxEdge || width * height > limits.maxPixels) {
      throw new Error('Each image must be at most 12 megapixels, with no side longer than 8,192 pixels.');
    }
  }
  async function validateFile(file, limits) {
    checkSize(file, limits);
    if (!signature(new Uint8Array(await file.slice(0, 16).arrayBuffer()))) throw new Error('Choose a JPEG, PNG or WebP image.');
    const url = URL.createObjectURL(file);
    try {
      const image = new Image(); image.src = url;
      try { await image.decode(); } catch { throw new Error('This image could not be opened. Choose a different JPEG, PNG or WebP.'); }
      checkDimensions(image.naturalWidth, image.naturalHeight, limits);
    } finally { URL.revokeObjectURL(url); }
  }
  function picker(container, authorization) {
    const input = container.querySelector('input[type="file"]');
    const previews = container.querySelector('[data-image-previews]');
    const status = container.querySelector('[data-image-status]');
    let entries = []; let limits = null; let checking = false; let locked = false; let disposed = false;
    const render = () => {
      release(previews); previews.replaceChildren();
      entries.forEach((entry, index) => {
        const item = make('div', undefined, 'image-preview');
        const remove = make('button', `Remove image ${index + 1}`);
        remove.type = 'button'; remove.disabled = locked || checking;
        remove.onclick = () => { entries.splice(index, 1); status.textContent = ''; render(); };
        item.append(thumbnail(previews, entry, authorization, index), remove); previews.append(item);
      });
      input.disabled = !limits || locked || checking || entries.length >= limits.maxCount;
    };
    input.onchange = async () => {
      const files = Array.from(input.files || []); input.value = '';
      if (!files.length || !limits || locked || checking) return;
      try {
        if (entries.length + files.length > limits.maxCount) throw new Error('Attach at most three images. Remove an image before adding another.');
        checking = true; render(); status.textContent = 'Checking images on this device…';
        for (const file of files) await validateFile(file, limits);
        if (disposed) return;
        entries.push(...files.map(file => ({ file })));
        status.textContent = `${entries.length} of ${limits.maxCount} images selected. Images are sent only when you submit.`;
      } catch (error) { if (!disposed) status.textContent = error.message; }
      finally { checking = false; if (!disposed) render(); }
    };
    render();
    return {
      configure(value, existing = []) {
        limits = value; entries = existing.map(entry => ({ ...entry }));
        status.textContent = limits ? 'New images are sent only when you submit.' : 'Image uploads are unavailable on this server. You can still send a text review or use the contact links.';
        render();
      },
      payload() {
        if (checking) throw new Error('Wait for the image previews to finish loading.');
        return { files: entries.filter(entry => entry.file).map(entry => entry.file),
          keepImageIds: entries.filter(entry => entry.id).map(entry => entry.id) };
      },
      lock(value) { locked = value; render(); },
      dispose() { disposed = true; release(previews); input.onchange = null; entries = []; },
    };
  }
  const api = { request, signature, checkSize, checkDimensions, validateFile, picker, gallery, release, close };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else { globalThis.ReviewImageUI = api; window.addEventListener('pagehide', () => { close(); release(document); }); }
})();
