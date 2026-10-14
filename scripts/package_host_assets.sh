#!/usr/bin/env bash
# package_host_assets.sh — stage host bootstrap + jniLibs into the app APK.
#
# Usage:
#   ./scripts/package_host_assets.sh com.kuro.app
#
# Actions:
#   1. Verify native/bootstrap/<id>/{bootstrap.tar,jniLibs/arm64-v8a/*.so}
#   2. Copy bootstrap.tar   → app/src/main/assets/bootstrap.tar
#   3. Copy jniLibs         → app/src/main/jniLibs/arm64-v8a/
#   4. Print sizes + SHA256 for CI logs.
#
# Rootfs archives are NOT packaged anymore — they ship via the GitHub release
# tag `rootfs` and are downloaded at install time
# (docs/plans/rootfs-github-release-no-apk-bloat.md).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BOOTSTRAP_ROOT="$ROOT/native/bootstrap"
APP_SRC="$ROOT/app/src"

usage() {
  sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'
  exit 2
}

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

stage_app_id() {
  local app_id="$1"
  if [[ "$app_id" != "com.kuro.app" ]]; then
    echo "ERROR: unknown application id '$app_id' (expected com.kuro.app)" >&2
    exit 2
  fi

  local src="$BOOTSTRAP_ROOT/$app_id"
  local main_assets="$APP_SRC/main/assets"
  local main_jni="$APP_SRC/main/jniLibs"

  # 1. Verify inputs. The app ships bootstrap.tar in the APK.
  local missing=0
  local required=("$src/bootstrap.tar" "$src/jniLibs/arm64-v8a/libbash.so" \
           "$src/jniLibs/arm64-v8a/libproot.so" "$src/jniLibs/arm64-v8a/libloader.so" \
           "$src/jniLibs/arm64-v8a/libloader32.so" \
           "$src/jniLibs/arm64-v8a/libpulseaudio.so" \
           "$src/jniLibs/arm64-v8a/libpactl.so")
  for f in "${required[@]}"; do
    if [ ! -f "$f" ]; then
      echo "ERROR: missing $f — run assemble_bootstrap.py --package-name $app_id first" >&2
      missing=1
    fi
  done
  if [ "$missing" -eq 1 ]; then exit 1; fi

  # 2-3. Stage assets + jniLibs (main source set)
  mkdir -p "$main_assets" "$main_jni/arm64-v8a"
  cp -f "$src/bootstrap.tar" "$main_assets/bootstrap.tar"
  cp -f "$src/jniLibs/arm64-v8a/"*.so "$main_jni/arm64-v8a/"

  # Pulse runtime overlay for already-extracted PREFIX (no EXTRACT_VERSION bump).
  mkdir -p "$main_assets/pulse-runtime"
  local lib_root="$src/root/data/data/$app_id/files/usr/lib"
  local so
  for so in libsoxr.so libsoxr-lsr.so libandroid-execinfo.so libFLAC.so libmp3lame.so; do
    if [ -e "$lib_root/$so" ]; then
      cp -fL "$lib_root/$so" "$main_assets/pulse-runtime/$so"
    fi
  done

  # 4. Report
  echo "=== packaged host assets for $app_id ==="
  if [ -f "$main_assets/bootstrap.tar" ]; then
    local bootstrap_sha bootstrap_size
    bootstrap_sha="$(sha256_of "$main_assets/bootstrap.tar")"
    bootstrap_size="$(du -h "$main_assets/bootstrap.tar" | awk '{print $1}')"
    echo "  app/src/main/assets/bootstrap.tar  $bootstrap_size  sha256=$bootstrap_sha"
  else
    echo "  app/src/main/assets/bootstrap.tar  NOT packaged — download from GitHub tag 'rootfs'"
  fi
  echo "  app/src/main/jniLibs/arm64-v8a/    $(ls "$main_jni/arm64-v8a" | tr '\n' ' ')"
  echo "  rootfs: NOT packaged — downloaded at install time from GitHub release tag 'rootfs'"
}

main() {
  case "${1:-}" in
    com.*)
      stage_app_id "$1"
      ;;
    *)
      usage
      ;;
  esac
}

main "$@"
