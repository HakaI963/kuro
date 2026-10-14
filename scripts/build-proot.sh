#!/bin/bash
# Fallback: cross-compile a static aarch64 proot with the Android NDK.
# Primary path is the committed asset documented in PROVENANCE.md; run this
# only to rebuild it (newer proot release, reproducible-build audit).
#
#   ANDROID_NDK_HOME=/path/to/ndk scripts/build-proot.sh [proot-tag]
#
# Requirements: Android NDK r25+ (same major as CI: 27.x), git, make, gcc.
# Output: app/src/main/assets/proot/proot-aarch64 (then re-verify + update
# PROVENANCE.md SHA-256). NOTE: needs network (talloc release tarball).
set -euo pipefail

PROOT_TAG="${1:-v5.3.0}"
TALLOC_VERSION="2.4.2"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$REPO_ROOT/app/src/main/assets/proot/proot-aarch64"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to an Android NDK (CI uses 27.0.12077973)}"
TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64"
export CC="$TOOLCHAIN/bin/aarch64-linux-android21-clang"
export AR="$TOOLCHAIN/bin/llvm-ar"
export RANLIB="$TOOLCHAIN/bin/llvm-ranlib"

echo "==> talloc $TALLOC_VERSION (static)"
curl -sL "https://www.samba.org/ftp/talloc/talloc-$TALLOC_VERSION.tar.gz" -o "$WORK/talloc.tgz"
tar xzf "$WORK/talloc.tgz" -C "$WORK"
# talloc uses waf; force static-only archive for proot to link.
(
  cd "$WORK/talloc-$TALLOC_VERSION"
  ./configure --disable-python --disable-rpath --enable-static --disable-shared \
    --prefix="$WORK/prefix" --cross-compile \
    --cross-answers-file <(printf 'Checking uname machine type: "aarch64"\n')
  make -j"$(nproc)" install
)

echo "==> proot $PROOT_TAG (static)"
git clone --depth 1 --branch "$PROOT_TAG" https://github.com/proot-me/proot.git "$WORK/proot"
(
  cd "$WORK/proot/src"
  # Static link against the talloc we just built.
  CFLAGS="-Os -static -I$WORK/prefix/include" \
  LDFLAGS="-static -L$WORK/prefix/lib" \
  make -j"$(nproc)"
  "$TOOLCHAIN/bin/llvm-strip" --strip-unneeded -o "$OUT" proot 2>/dev/null || cp proot "$OUT"
)

echo "==> verify"
file "$OUT"
sha256sum "$OUT"
echo "Done. Re-verify on device via 'proot -V' and update third_party/proot/PROVENANCE.md."
