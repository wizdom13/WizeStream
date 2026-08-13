import { spawn } from 'node:child_process';
import { createRequire } from 'node:module';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { Worker } from 'node:worker_threads';
import { app } from 'electron';

const root = path.resolve(import.meta.dirname, '..');
process.env.MPV_AO = 'null';
const nativeDirectory = path.join(root, 'native/mpv');
const toolSuffix = process.platform === 'win32' ? '.exe' : '';
const ffmpeg = path.join(root, 'native/media-tools', `ffmpeg${toolSuffix}`);
const require = createRequire(import.meta.url);
const temporary = await mkdtemp(path.join(tmpdir(), 'wizestream-native-media-'));
const videoPath = path.join(temporary, 'video.mp4');
const audioPath = path.join(temporary, 'audio.m4a');
const captionPath = path.join(temporary, 'caption.vtt');
let server;
let player;
let exitCode = 0;

try {
  await run(ffmpeg, ['-y', '-f', 'lavfi', '-i', 'color=c=blue:s=160x90:d=1',
    '-an', '-c:v', 'libx264', '-pix_fmt', 'yuv420p', videoPath]);
  await run(ffmpeg, ['-y', '-f', 'lavfi', '-i', 'sine=frequency=440:duration=1',
    '-vn', '-c:a', 'aac', audioPath]);
  await writeFile(captionPath, 'WEBVTT\n\n00:00.000 --> 00:00.800\nWizeStream Phase 6\n');
  const files = { '/video': await readFile(videoPath), '/audio': await readFile(audioPath),
    '/caption': await readFile(captionPath) };
  const fixture = await startFixtureServer(files);
  server = fixture.worker;
  const base = `http://127.0.0.1:${fixture.port}`;
  const addon = require(path.join(nativeDirectory, 'mpv_addon.node'));
  player = new addon.MpvPlayer({ mode: 'software' });
  player.openMedia({ source: `${base}/video`, audio: { url: `${base}/audio`, title: 'Fixture audio', language: 'en' },
    subtitle: { url: `${base}/caption`, title: 'Fixture captions', language: 'en' } });
  await waitForFile(player);
  player.setAudioFile({ url: `${base}/audio`, title: 'Switched audio', language: 'en' });
  player.setSubtitleFile(null);
  player.setSubtitleFile({ url: `${base}/caption`, title: 'Switched captions', language: 'en' });
  player.play();
  await new Promise((resolve) => setTimeout(resolve, 150));
  player.pause();
  const frame = player.renderFrame(64, 64);
  if (frame.width !== 64 || frame.height !== 64 || frame.rgba.length !== 64 * 64 * 4) {
    throw new Error('libmpv software renderer returned an invalid frame');
  }
  console.log(`Verified composite playback and track switching on ${process.platform}-${process.arch}.`);
} catch (error) {
  exitCode = 1;
  console.error(error);
} finally {
  player?.destroy();
  if (server) await server.terminate();
  await rm(temporary, { recursive: true, force: true });
  app.exit(exitCode);
}

async function startFixtureServer(files) {
  const worker = new Worker(`
    const { createServer } = require('node:http');
    const { parentPort, workerData } = require('node:worker_threads');
    const server = createServer((request, response) => {
      const value = workerData[request.url];
      if (!value) { response.writeHead(404).end(); return; }
      response.writeHead(200, { 'content-length': value.length, 'connection': 'close' });
      response.end(value);
    });
    server.listen(0, '127.0.0.1', () => parentPort.postMessage(server.address().port));
  `, { eval: true, workerData: files });
  const port = await new Promise((resolve, reject) => {
    worker.once('message', resolve);
    worker.once('error', reject);
  });
  return { worker, port };
}

function run(executable, args) {
  return new Promise((resolve, reject) => {
    const child = spawn(executable, args, { shell: false, stdio: ['ignore', 'ignore', 'pipe'], windowsHide: true });
    let error = '';
    child.stderr.on('data', (value) => { error += value.toString(); });
    child.once('error', reject);
    child.once('exit', (code) => code === 0 ? resolve() : reject(new Error(error.slice(-1000))));
  });
}

async function waitForFile(target) {
  const deadline = Date.now() + 20_000;
  while (Date.now() < deadline) {
    const events = target.pollEvents();
    const failure = events.find((event) => event.error);
    if (failure) throw new Error(failure.error);
    if (events.some((event) => event.type === 'file-loaded')) return;
    await new Promise((resolve) => setTimeout(resolve, 20));
  }
  throw new Error('Timed out opening the composite native fixture');
}
