import { execFileSync } from 'node:child_process'
import { chmodSync, copyFileSync, existsSync, mkdirSync, readdirSync, rmSync, statSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const target = `${process.platform}-${process.arch}`
const outputDir = path.join(root, 'native', 'mpv-addon', 'build', 'Release')
const addonPath = path.join(outputDir, 'mpv_addon.node')
const mode = process.argv[2]

function fail(message) {
  console.error(`libmpv: ${message}`)
  process.exit(1)
}

function requireFile(file, description) {
  if (!existsSync(file)) {
    fail(`${description} not found: ${file}`)
  }
}

function requireDirectory(directory, description) {
  if (!existsSync(directory) || !statSync(directory).isDirectory()) {
    fail(`${description} not found: ${directory}`)
  }
}

function copyRuntimeFile(source, destination) {
  rmSync(destination, { force: true })
  copyFileSync(source, destination)
  chmodSync(destination, 0o755)
}

function copyMatching(sourceDir, extension) {
  if (!existsSync(sourceDir)) return []

  const copied = []
  for (const entry of readdirSync(sourceDir, { withFileTypes: true })) {
    if (!entry.isFile() && !entry.isSymbolicLink()) continue
    if (!entry.name.toLowerCase().endsWith(extension)) continue

    const destination = path.join(outputDir, entry.name)
    copyRuntimeFile(path.join(sourceDir, entry.name), destination)
    copied.push(destination)
  }
  return copied
}

function copyStartingWith(sourceDir, prefix) {
  if (!existsSync(sourceDir)) return []
  const copied = []
  for (const entry of readdirSync(sourceDir, { withFileTypes: true })) {
    if (!entry.isFile() && !entry.isSymbolicLink()) continue
    if (!entry.name.toLowerCase().startsWith(prefix.toLowerCase())) continue
    const destination = path.join(outputDir, entry.name)
    copyRuntimeFile(path.join(sourceDir, entry.name), destination)
    copied.push(destination)
  }
  return copied
}

function configuredPaths() {
  if (target === 'darwin-arm64' || target === 'darwin-x64') {
    const prefix = target === 'darwin-arm64' ? '/opt/homebrew' : '/usr/local'
    return {
      includeDir: path.resolve(process.env.MPV_INCLUDE_DIR || `${prefix}/include`),
      library: path.resolve(process.env.MPV_LIB || `${prefix}/lib/libmpv.dylib`),
      runtimeDir: path.resolve(
        process.env.MPV_RUNTIME_DIR || `${prefix}/opt/mpv/lib`,
      ),
    }
  }

  if (target === 'linux-x64' || target === 'linux-arm64') {
    const library = path.resolve(process.env.MPV_LIB || '/usr/lib/libmpv.so')
    return {
      includeDir: path.resolve(process.env.MPV_INCLUDE_DIR || '/usr/include'),
      library,
      runtimeDir: path.resolve(process.env.MPV_RUNTIME_DIR || path.dirname(library)),
    }
  }

  if (target === 'win32-x64') {
    const root = path.join(os.homedir(), 'libmpv')
    return {
      includeDir: path.resolve(process.env.MPV_INCLUDE_DIR || path.join(root, 'include')),
      library: path.resolve(process.env.MPV_LIB || path.join(root, 'lib', 'mpv.lib')),
      runtimeDir: path.resolve(process.env.MPV_RUNTIME_DIR || path.join(root, 'bin')),
    }
  }

  fail(`unsupported build target: ${target}`)
}

function validateHeaders(includeDir) {
  for (const header of ['client.h', 'render.h', 'render_gl.h']) {
    requireFile(path.join(includeDir, 'mpv', header), `mpv/${header}`)
  }
}

function getMacInstallName(library) {
  const lines = execFileSync('otool', ['-D', library], { encoding: 'utf8' })
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
  return lines[1]
}

function check() {
  if (target === 'darwin-arm64' || target === 'darwin-x64') {
    const paths = configuredPaths()
    validateHeaders(paths.includeDir)
    requireFile(paths.library, 'libmpv link library')
    requireDirectory(paths.runtimeDir, 'libmpv runtime directory')
    return
  }


  if (target === 'linux-x64' || target === 'linux-arm64') {
    const paths = configuredPaths()
    validateHeaders(paths.includeDir)
    requireFile(paths.library, 'libmpv link library')
    requireDirectory(paths.runtimeDir, 'libmpv runtime directory')
    return
  }

  if (target === 'win32-x64') {
    const paths = configuredPaths()
    validateHeaders(paths.includeDir)
    requireFile(paths.library, 'libmpv import library')
    requireDirectory(paths.runtimeDir, 'libmpv runtime directory')
    const dlls = readdirSync(paths.runtimeDir).map((name) => name.toLowerCase())
    if (!dlls.includes('libmpv-2.dll') && !dlls.includes('mpv-2.dll')) {
      fail(`libmpv runtime DLL not found in: ${paths.runtimeDir}`)
    }
    return
  }

  fail(`unsupported build target: ${target}`)
}

function stage() {
  requireFile(addonPath, 'native addon')
  mkdirSync(outputDir, { recursive: true })

  if (target === 'darwin-arm64' || target === 'darwin-x64') {
    const paths = configuredPaths()
    const stagedLibrary = path.join(outputDir, 'libmpv.dylib')
    copyMatching(paths.runtimeDir, '.dylib')
    copyRuntimeFile(paths.library, stagedLibrary)

    const installName = getMacInstallName(paths.library)
    if (!installName) fail(`unable to read install name from: ${paths.library}`)

    execFileSync('install_name_tool', [
      '-change', installName, '@loader_path/libmpv.dylib', addonPath,
    ])
    execFileSync('install_name_tool', [
      '-id', '@loader_path/libmpv.dylib', stagedLibrary,
    ])
    execFileSync('codesign', ['--force', '--sign', '-', stagedLibrary])
    console.log(`libmpv: staged macOS runtime in ${outputDir}`)
    return
  }


  if (target === 'linux-x64' || target === 'linux-arm64') {
    const copied = copyStartingWith(configuredPaths().runtimeDir, 'libmpv.so')
    if (copied.length === 0) fail('no libmpv shared library was staged')
    console.log(`libmpv: staged ${copied.length} Linux runtime file(s) in ${outputDir}`)
    return
  }

  if (target === 'win32-x64') {
    const copied = copyMatching(configuredPaths().runtimeDir, '.dll')
    if (copied.length === 0) fail('no runtime DLLs were staged')
    console.log(`libmpv: staged ${copied.length} Windows runtime DLL(s) in ${outputDir}`)
    return
  }

  fail(`unsupported build target: ${target}`)
}

if (mode === 'check') check()
else if (mode === 'stage') stage()
else fail('expected command: check or stage')
