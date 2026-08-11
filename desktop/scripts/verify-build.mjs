import assert from 'node:assert/strict';
import { readFile, stat } from 'node:fs/promises';
import path from 'node:path';
import vm from 'node:vm';

const desktopDirectory = path.resolve(import.meta.dirname, '..');
const preloadPath = path.join(desktopDirectory, 'dist-electron/preload/index.cjs');
const mainPath = path.join(desktopDirectory, 'dist-electron/main/main.js');
const htmlPath = path.join(desktopDirectory, 'dist-renderer/index.html');

const [preloadSource, mainSource, html] = await Promise.all([
  readFile(preloadPath, 'utf8'),
  readFile(mainPath, 'utf8'),
  readFile(htmlPath, 'utf8'),
]);

assert.match(preloadSource, /require\(["']electron["']\)/, 'sandboxed preload must be CommonJS');
assert.doesNotMatch(preloadSource, /^\s*import\s/m, 'sandboxed preload must not contain ESM imports');
assert.match(mainSource, /preload[\\/]index\.cjs/, 'main process must load the CommonJS preload');
assert.match(mainSource, /library\.history\.record/, 'main process must allow Phase 3 library RPC');
assert.match(mainSource, /downloads:start/, 'main process must expose Phase 4 downloads');
assert.match(mainSource, /embeddedMpvAvailable/, 'main process must gate the embedded native renderer');

let exposedName;
let exposedApi;
const exposedApis = new Map();
const ipcCalls = [];
const sandbox = {
  exports: {},
  module: { exports: {} },
  process: { platform: 'linux' },
  require(specifier) {
    assert.equal(specifier, 'electron', `unexpected preload dependency: ${specifier}`);
    return {
      contextBridge: {
        exposeInMainWorld(name, api) {
          exposedName = name;
          exposedApi = api;
          exposedApis.set(name, api);
        },
      },
      ipcRenderer: {
        invoke(channel, payload) {
          ipcCalls.push({ channel, payload });
          return Promise.resolve({});
        },
      },
    };
  },
};
vm.runInNewContext(preloadSource, sandbox, { filename: preloadPath });

assert.equal(exposedName, 'wizestream', 'preload must expose the WizeStream bridge');
assert.equal(typeof exposedApis.get('_electronMpvVideo')?.create, 'function', 'preload must expose the embedded libmpv bridge');
assert.equal(typeof exposedApi?.backend?.invoke, 'function');
assert.equal(typeof exposedApi?.player?.play, 'function');
assert.equal(typeof exposedApi?.player?.stop, 'function');
assert.equal(typeof exposedApi?.player?.status, 'function');
assert.equal(typeof exposedApi?.downloads?.start, 'function');
assert.equal(typeof exposedApi?.downloads?.onChanged, 'function');
await exposedApi.backend.invoke('health');
assert.equal(ipcCalls[0]?.channel, 'backend:invoke');
assert.equal(ipcCalls[0]?.payload?.method, 'health');
assert.equal(ipcCalls[0]?.payload?.params, undefined);

const assetReferences = [...html.matchAll(/(?:src|href)="\.\/([^"]+)"/g)].map((match) => match[1]);
assert.ok(assetReferences.some((asset) => asset.endsWith('.js')), 'renderer HTML must load JavaScript');
assert.ok(assetReferences.some((asset) => asset.endsWith('.css')), 'renderer HTML must load CSS');

for (const asset of assetReferences) {
  const assetPath = path.join(desktopDirectory, 'dist-renderer', asset);
  assert.ok((await stat(assetPath)).isFile(), `renderer asset is missing: ${asset}`);
}

const rendererJavaScript = (await Promise.all(
  assetReferences.filter((asset) => asset.endsWith('.js'))
    .map((asset) => readFile(path.join(desktopDirectory, 'dist-renderer', asset), 'utf8')),
)).join('\n');
assert.match(rendererJavaScript, /WizeStream could not start/, 'renderer must show a bridge failure');
assert.match(rendererJavaScript, /secure desktop bridge did not load/, 'bridge failure must be actionable');
assert.match(rendererJavaScript, /Learning notes/, 'renderer must include the Phase 3 Learning editor');
assert.match(rendererJavaScript, /Add channel/, 'renderer must include the Phase 3 subscription editor');
assert.match(rendererJavaScript, /Play embedded/, 'renderer must include Phase 4 embedded playback');
assert.match(rendererJavaScript, /Download current stream/, 'renderer must include Phase 4 downloads');

console.log('Packaged Electron startup boundary verified.');
