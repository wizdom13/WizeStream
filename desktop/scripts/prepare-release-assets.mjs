import { createHash } from 'node:crypto';
import { copyFile, mkdir, readFile, readdir, rm, stat, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { dump, load } from 'js-yaml';

const desktopDirectory = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const input = path.resolve(argument('--input') ?? path.join(desktopDirectory, 'release-input'));
const output = path.resolve(argument('--output') ?? path.join(desktopDirectory, 'release-ready'));
const allowedExtensions = new Set(['.AppImage', '.deb', '.exe', '.dmg', '.zip', '.yml', '.yaml', '.blockmap']);

await rm(output, { recursive: true, force: true });
await mkdir(output, { recursive: true });

const inputs = (await filesUnder(input)).filter((file) => allowedExtensions.has(path.extname(file)));
if (inputs.length === 0) throw new Error(`No release assets found under ${input}`);

const groups = Map.groupBy(inputs, (file) => path.basename(file));
for (const [name, files] of groups) {
  const destination = path.join(output, name);
  if ((name.endsWith('.yml') || name.endsWith('.yaml')) && files.length > 1) {
    await mergeMetadata(files, destination);
  } else {
    if (files.length > 1) throw new Error(`Duplicate release asset: ${name}`);
    await copyFile(files[0], destination);
  }
}

const outputFiles = (await readdir(output)).sort();
const requiredMetadata = ['beta.yml', 'beta-mac.yml', 'beta-linux.yml', 'beta-linux-arm64.yml'];
for (const name of requiredMetadata) {
  if (!outputFiles.includes(name)) throw new Error(`Missing updater metadata: ${name}`);
}

for (const pattern of [
  /windows-x64.*\.exe$/i,
  /macos-x64.*\.dmg$/i,
  /macos-arm64.*\.dmg$/i,
  /linux-x64.*\.AppImage$/i,
  /linux-arm64.*\.AppImage$/i,
]) {
  if (!outputFiles.some((name) => pattern.test(name))) throw new Error(`Missing release artifact matching ${pattern}`);
}

const macMetadata = load(await readFile(path.join(output, 'beta-mac.yml'), 'utf8'));
const macUrls = Array.isArray(macMetadata?.files) ? macMetadata.files.map((file) => String(file.url)) : [];
if (!macUrls.some((url) => url.includes('x64')) || !macUrls.some((url) => url.includes('arm64'))) {
  throw new Error('beta-mac.yml must contain both x64 and arm64 update files');
}

const checksums = [];
for (const name of outputFiles) {
  const data = await readFile(path.join(output, name));
  checksums.push(`${createHash('sha256').update(data).digest('hex')}  ${name}`);
}
await writeFile(path.join(output, 'SHA256SUMS'), `${checksums.join('\n')}\n`);
await writeFile(path.join(output, 'release-manifest.json'), `${JSON.stringify({
  version: '0.6.0-beta', tag: 'desktop_v0.6.0-beta', prerelease: true,
  assets: [...outputFiles, 'SHA256SUMS'],
}, null, 2)}\n`);
console.log(`WIZESTREAM_RELEASE_ASSETS_OK ${outputFiles.length + 2}`);

async function mergeMetadata(files, destination) {
  const documents = await Promise.all(files.map(async (file) => load(await readFile(file, 'utf8'))));
  const version = documents[0]?.version;
  if (!version || documents.some((document) => document?.version !== version)) {
    throw new Error(`Updater metadata versions disagree for ${path.basename(destination)}`);
  }
  const updateFiles = documents.flatMap((document) => Array.isArray(document.files) ? document.files : []);
  const unique = [...new Map(updateFiles.map((file) => [file.url, file])).values()]
    .sort((left, right) => String(left.url).localeCompare(String(right.url)));
  if (unique.length === 0) throw new Error(`Updater metadata has no files: ${path.basename(destination)}`);
  const primary = unique.find((file) => String(file.url).includes('x64')) ?? unique[0];
  const merged = {
    ...documents[0], files: unique, path: primary.url, sha512: primary.sha512,
    releaseDate: documents.map((document) => document.releaseDate).filter(Boolean).sort().at(-1),
  };
  await writeFile(destination, dump(merged, { lineWidth: -1, noRefs: true, sortKeys: false }));
}

async function filesUnder(directory) {
  const result = [];
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const resolved = path.join(directory, entry.name);
    if (entry.isDirectory()) result.push(...await filesUnder(resolved));
    else if (entry.isFile() && (await stat(resolved)).size > 0) result.push(resolved);
  }
  return result;
}

function argument(name) {
  const index = process.argv.indexOf(name);
  return index < 0 ? undefined : process.argv[index + 1];
}
