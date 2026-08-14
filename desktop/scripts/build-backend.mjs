import { spawnSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const desktopDirectory = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const isWindows = process.platform === 'win32';
const wrapper = resolve(desktopDirectory, '..', isWindows ? 'gradlew.bat' : 'gradlew');
const result = spawnSync(
  wrapper,
  ['-p', 'backend', 'installDist', 'runtimeImage'],
  {
    cwd: desktopDirectory,
    stdio: 'inherit',
    shell: isWindows,
  },
);

if (result.error) {
  console.error(`Unable to start the Gradle wrapper: ${result.error.message}`);
  process.exit(1);
}

if (result.signal) {
  console.error(`Gradle wrapper terminated by signal ${result.signal}`);
  process.exit(1);
}

process.exit(result.status ?? 1);
