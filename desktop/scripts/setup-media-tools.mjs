import { createHash } from 'node:crypto';
import { chmod, mkdir, rename, writeFile } from 'node:fs/promises';
import path from 'node:path';

const VERSION = 'b6.1.1';
const TARGETS = {
  'darwin-arm64': {
    ffmpeg: 'a90e3db6a3fd35f6074b013f948b1aa45b31c6375489d39e572bea3f18336584',
    ffprobe: 'bb2db6f5d8cef919da12fbf592119a987202a8c060a886f3cab091f9cab90b64',
    license: 'cb48bf09a11f5fb576cddb0431c8f5ed0a60157a9ec942adffc13907cbe083f2',
  },
  'darwin-x64': {
    ffmpeg: 'ebdddc936f61e14049a2d4b549a412b8a40deeff6540e58a9f2a2da9e6b18894',
    ffprobe: 'fa3add0ce901f7241abe0dfc0155d958fc834aca3f8ce61f87cc712ae669c1e0',
    license: '2e1d16c72fd74e12063776371da757322f8b77589386532f4fd8634bde7de1af',
  },
  'linux-arm64': {
    ffmpeg: '6bb182d0d75d23028db82e9e4f723ca69b853d055698486e6984ddb2c06fb8ce',
    ffprobe: 'd17ae9b4c297d48e2521ba14e417bb0537c6ff77c584cdbcd6bb0d8d0307a2e8',
    license: '8ceb4b9ee5adedde47b31e975c1d90c73ad27b6b165a1dcd80c7c545eb65b903',
  },
  'linux-x64': {
    ffmpeg: 'e7e7fb30477f717e6f55f9180a70386c62677ef8a4d4d1a5d948f4098aa3eb99',
    ffprobe: '4f231a1960d83e403d08f7971e271707bec278a9ae18e21b8b5b03186668450d',
    license: '8ceb4b9ee5adedde47b31e975c1d90c73ad27b6b165a1dcd80c7c545eb65b903',
  },
  'win32-x64': {
    ffmpeg: '04e1307997530f9cf2fe35cba2ca7e8875ca91da02f89d6c7243df819c94ad00',
    ffprobe: '3a7e2dc003dc2cd1472827e4c7c4f056ae1ae0ae7c5bbc580c99b49827351ba4',
    license: '8ceb4b9ee5adedde47b31e975c1d90c73ad27b6b165a1dcd80c7c545eb65b903',
  },
};

const target = `${process.platform}-${process.arch}`;
const expected = TARGETS[target];
if (!expected) throw new Error(`Unsupported media-tools target: ${target}`);
const directory = path.resolve(import.meta.dirname, '../native/media-tools');
await mkdir(directory, { recursive: true });

for (const tool of ['ffmpeg', 'ffprobe']) {
  const asset = `${tool}-${target}`;
  const response = await fetch(`https://github.com/eugeneware/ffmpeg-static/releases/download/${VERSION}/${asset}`);
  if (!response.ok) throw new Error(`Could not download ${asset}: HTTP ${response.status}`);
  const value = Buffer.from(await response.arrayBuffer());
  const digest = createHash('sha256').update(value).digest('hex');
  if (digest !== expected[tool]) throw new Error(`${asset} SHA-256 mismatch: ${digest}`);
  const finalPath = path.join(directory, `${tool}${process.platform === 'win32' ? '.exe' : ''}`);
  const temporary = `${finalPath}.tmp`;
  await writeFile(temporary, value);
  await chmod(temporary, 0o755);
  await rename(temporary, finalPath);
}

const licenseResponse = await fetch(`https://github.com/eugeneware/ffmpeg-static/releases/download/${VERSION}/${target}.LICENSE`);
if (!licenseResponse.ok) throw new Error(`Could not download FFmpeg license: HTTP ${licenseResponse.status}`);
const license = Buffer.from(await licenseResponse.arrayBuffer());
const licenseDigest = createHash('sha256').update(license).digest('hex');
if (licenseDigest !== expected.license) throw new Error(`FFmpeg license SHA-256 mismatch: ${licenseDigest}`);
await writeFile(path.join(directory, 'LICENSE-ffmpeg.txt'), license);

await writeFile(path.join(directory, 'manifest.json'), JSON.stringify({
  source: 'https://github.com/eugeneware/ffmpeg-static', version: VERSION, target, sha256: expected,
}, null, 2));
console.log(`Prepared checksum-verified FFmpeg tools ${VERSION} for ${target}.`);
