#!/usr/bin/env bash
set -euo pipefail

if [[ "$(uname -s)" == "Darwin" ]]; then
  brew install mpv
  mpv_version="$(mpv --version | sed -n '1p')"
  [[ "$mpv_version" == *"0.41.0"* ]] || { echo "Unexpected mpv version: $mpv_version" >&2; exit 1; }
  mpv_prefix="$(brew --prefix mpv)"
  brew_prefix="$(brew --prefix)"
  dependency_roots="$mpv_prefix/lib:$brew_prefix/lib"
  while IFS= read -r dependency; do
    dependency_roots="$dependency_roots:$(brew --prefix "$dependency")/lib"
  done < <(brew deps mpv)
  {
    echo "MPV_INCLUDE_DIR=$mpv_prefix/include"
    echo "MPV_LIB=$mpv_prefix/lib/libmpv.dylib"
    echo "MPV_RUNTIME_DIR=$mpv_prefix/lib"
    echo "MPV_DEPENDENCY_ROOTS=$dependency_roots"
  } >> "$GITHUB_ENV"
  exit 0
fi

sudo apt-get update
sudo apt-get install --yes libmpv-dev patchelf xvfb
package_version="$(dpkg-query -W -f='${Version}' libmpv-dev)"
[[ "$package_version" == "0.37.0-1ubuntu4" ]] || {
  echo "Unexpected libmpv-dev version: $package_version" >&2
  exit 1
}
libdir="$(pkg-config --variable=libdir mpv)"
includedir="$(pkg-config --variable=includedir mpv)"
{
  echo "MPV_INCLUDE_DIR=$includedir"
  echo "MPV_LIB=$libdir/libmpv.so"
  echo "MPV_RUNTIME_DIR=$libdir"
} >> "$GITHUB_ENV"
