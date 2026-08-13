import { spawn } from 'node:child_process';
import { readdir, stat } from 'node:fs/promises';
import path from 'node:path';

const release = path.resolve(import.meta.dirname, '../release');
const executable = await findExecutable(release);
const args = process.platform === 'linux' ? ['-a', executable] : [];
const command = process.platform === 'linux' ? 'xvfb-run' : executable;
const output = await new Promise((resolve, reject) => {
  const child = spawn(command, args, {
    shell: false, windowsHide: true, env: { ...process.env, WIZESTREAM_PACKAGE_SMOKE: '1' },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  let value = '';
  child.stdout.on('data', (chunk) => { value += chunk.toString(); });
  child.stderr.on('data', (chunk) => { value += chunk.toString(); });
  const timeout = setTimeout(() => { child.kill(); reject(new Error(`Packaged smoke timed out:\n${value}`)); }, 45_000);
  child.once('error', (error) => { clearTimeout(timeout); reject(error); });
  child.once('exit', (code) => {
    clearTimeout(timeout);
    code === 0 ? resolve(value) : reject(new Error(`Packaged application exited ${code}:\n${value}`));
  });
});
if (!output.includes(`WIZESTREAM_PACKAGE_SMOKE_OK ${process.platform}-${process.arch}`)) {
  throw new Error(`Packaged application did not report a healthy startup:\n${output}`);
}
console.log(`Verified unpacked packaged application: ${executable}`);

async function findExecutable(directory) {
  const candidates = [];
  async function visit(current) {
    for (const entry of await readdir(current, { withFileTypes: true })) {
      const target = path.join(current, entry.name);
      if (entry.isDirectory()) await visit(target);
      else if (isCandidate(target)) candidates.push(target);
    }
  }
  await visit(directory);
  for (const candidate of candidates) {
    const metadata = await stat(candidate);
    if (process.platform === 'win32' || (metadata.mode & 0o111) !== 0) return candidate;
  }
  throw new Error(`Could not locate the unpacked WizeStream executable in ${directory}`);
}

function isCandidate(value) {
  const normalized = value.replaceAll('\\', '/');
  if (process.platform === 'win32') return normalized.endsWith('/win-unpacked/WizeStream Desktop.exe');
  if (process.platform === 'darwin') return normalized.endsWith('/WizeStream Desktop.app/Contents/MacOS/WizeStream Desktop');
  return /\/linux(?:-arm64)?-unpacked\/wizestream-desktop$/.test(normalized);
}
