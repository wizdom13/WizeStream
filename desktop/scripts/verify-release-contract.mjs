import { readFile } from 'node:fs/promises';

const packageJson = JSON.parse(await readFile(new URL('../package.json', import.meta.url), 'utf8'));
const failures = [];

if (packageJson.version !== '0.6.0-beta') failures.push('desktop version must be 0.6.0-beta');
if (packageJson.dependencies?.['electron-updater'] !== '6.8.9') failures.push('electron-updater must be pinned');

const publisher = packageJson.build?.publish?.[0];
if (publisher?.provider !== 'github' || publisher.owner !== 'wizdom13'
  || publisher.repo !== 'WizeStream' || publisher.channel !== 'beta'
  || publisher.releaseType !== 'prerelease') {
  failures.push('GitHub beta publish configuration is incomplete');
}

for (const [platform, required] of Object.entries({
  linux: ['AppImage', 'deb'], win: ['nsis', 'portable'], mac: ['dmg', 'zip'],
})) {
  const targets = packageJson.build?.[platform]?.target ?? [];
  for (const target of required) if (!targets.includes(target)) failures.push(`${platform} target ${target} is missing`);
  if (!String(packageJson.build?.[platform]?.artifactName ?? '').includes('${arch}')) {
    failures.push(`${platform} artifact names must include architecture`);
  }
}

if (!String(packageJson.build?.nsis?.artifactName ?? '').includes('-setup.')
  || !String(packageJson.build?.portable?.artifactName ?? '').includes('-portable.')) {
  failures.push('Windows installer and portable packages must use distinct artifact names');
}

if (packageJson.build?.mac?.hardenedRuntime !== true || packageJson.build?.mac?.notarize !== true) {
  failures.push('macOS hardened runtime and notarization must be enabled');
}

if (failures.length > 0) {
  console.error(failures.map((failure) => `- ${failure}`).join('\n'));
  process.exitCode = 1;
} else {
  console.log(`WIZESTREAM_RELEASE_CONTRACT_OK ${packageJson.version}`);
}
