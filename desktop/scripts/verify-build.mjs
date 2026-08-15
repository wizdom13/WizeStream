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
assert.match(mainSource, /channel\.resolve/, 'main process must allow internal channel navigation');
assert.match(mainSource, /feed\.subscriptions/, 'main process must allow the subscription video feed');
assert.match(mainSource, /stream\.comments/, 'main process must allow lazy-loaded video comments');
assert.match(mainSource, /library\.playback-state\.save/, 'main process must persist playback progress');
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
const nativeSession = await exposedApis.get('_electronMpvVideo').create();
assert.equal(typeof nativeSession.openMedia, 'function', 'native bridge must expose typed composite media opening');
assert.equal(typeof nativeSession.setAudioTrack, 'function', 'native bridge must expose typed audio switching');
assert.equal(typeof nativeSession.setSubtitleTrack, 'function', 'native bridge must expose typed caption switching');
await nativeSession.openMedia({ source: 'https://media.example/video',
  audio: { url: 'https://media.example/audio' }, subtitle: { url: 'https://media.example/caption' } });
assert.ok(ipcCalls.some((call) => call.channel.endsWith(':player:open-media')),
  'composite media opening must use the narrow native IPC channel');
assert.equal(typeof exposedApi?.backend?.invoke, 'function');
assert.equal(typeof exposedApi?.player?.play, 'function');
assert.equal(typeof exposedApi?.player?.stop, 'function');
assert.equal(typeof exposedApi?.player?.status, 'function');
assert.equal(typeof exposedApi?.downloads?.start, 'function');
assert.equal(typeof exposedApi?.downloads?.onChanged, 'function');
assert.equal(typeof exposedApi?.settings?.get, 'function');
assert.equal(typeof exposedApi?.settings?.update, 'function');
assert.equal(typeof exposedApi?.settings?.reset, 'function');
assert.equal(typeof exposedApi?.backup?.exportFull, 'function');
assert.equal(typeof exposedApi?.backup?.restoreFull, 'function');
assert.equal(typeof exposedApi?.backup?.importSubscriptions, 'function');
assert.equal(typeof exposedApi?.backup?.exportSubscriptions, 'function');
await exposedApi.backend.invoke('health');
const healthCall = ipcCalls.find((call) => call.channel === 'backend:invoke');
assert.equal(healthCall?.payload?.method, 'health');
assert.equal(healthCall?.payload?.params, undefined);

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
assert.match(rendererJavaScript, /Combining tracks/, 'renderer must report Phase 6 adaptive muxing');
assert.match(rendererJavaScript, /Open with external mpv/, 'renderer must preserve explicit external-player recovery');
assert.match(rendererJavaScript, /Video and audio/, 'renderer must include Android-aligned desktop settings');
assert.match(rendererJavaScript, /History and cache/, 'renderer must include applicable history settings');
assert.match(rendererJavaScript, /Device synchronization/, 'renderer must link settings to Devices');
assert.match(rendererJavaScript, /Refresh channel details/, 'renderer must offer subscription metadata refresh');
assert.match(rendererJavaScript, /subscription-grid/, 'renderer must display subscriptions in a grid');
assert.match(rendererJavaScript, /Back to subscriptions/, 'renderer must include the internal channel view');
assert.match(rendererJavaScript, /Recent videos/, 'renderer must show channel videos');
assert.match(rendererJavaScript, /What.{0,12}s New/, 'renderer must include the subscription feed');
assert.match(rendererJavaScript, /Partially watched/, 'renderer must include Android-aligned feed filters');
assert.match(rendererJavaScript, /Refresh feed/, 'renderer must allow explicit feed refresh');
assert.match(rendererJavaScript, /Related items/, 'renderer must include Android-aligned video information tabs');
assert.match(rendererJavaScript, /history-grid/, 'renderer must display watch history in a grid');
assert.match(rendererJavaScript, /Backup and restore/, 'renderer must include Desktop backup tools');
assert.match(rendererJavaScript, /Android JSON subscription export/, 'renderer must explain Android subscription compatibility');

console.log('Packaged Electron startup boundary verified.');
