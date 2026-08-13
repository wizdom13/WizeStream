import { spawn } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import path from 'node:path';

const directory = path.resolve(import.meta.dirname, '../native/media-tools');
const suffix = process.platform === 'win32' ? '.exe' : '';
const manifest = JSON.parse(await readFile(path.join(directory, 'manifest.json'), 'utf8'));
if (manifest.target !== `${process.platform}-${process.arch}`) {
  throw new Error(`Media-tools architecture mismatch: ${manifest.target}`);
}
for (const tool of ['ffmpeg', 'ffprobe']) {
  await new Promise((resolve, reject) => {
    const child = spawn(path.join(directory, `${tool}${suffix}`), ['-version'], {
      shell: false, stdio: ['ignore', 'ignore', 'pipe'], windowsHide: true,
    });
    let error = '';
    child.stderr.on('data', (value) => { error += value.toString(); });
    child.once('error', reject);
    child.once('exit', (code) => code === 0 ? resolve() : reject(new Error(`${tool} failed: ${error.slice(0, 500)}`)));
  });
}
console.log(`Verified FFmpeg tools for ${manifest.target}.`);
