#!/bin/bash
# Last-resort fallback: static musl cross-build of aarch64 proot.
# Candidates 1-4 in ProotAcquisition (Alpine musl-static, NDK builds,
# proot-static-build assets, bundled asset) cover normal operation; run this
# only to mint a fresh static asset (newer proot release, reproducible-build
# audit, or upstream rotation).
#
#   scripts/build-proot.sh [proot-tag]
#
# Requirements: x86_64 Linux, curl, tar, make, python3, musl-cross toolchain
# (auto-downloaded below if MUSL_PREFIX is unset).
# Output: app/src/main/assets/proot/proot-aarch64 (then re-verify with
# `file`/`readelf`: must read aarch64 + static-pie/DYN, and update
# third_party/proot/PROVENANCE.md).
#
# NOTE: talloc's waf configure does not cross-compile unattended (it wants to
# run test programs). This script builds talloc with a checked-in
# cross-answers file; if waf still refuses, build talloc natively is NOT an
# option for the target — fix the answers file, do not link host talloc.
set -euo pipefail

PROOT_TAG="${1:-v5.4.0}"
TALLOC_VERSION="2.4.2"
MUSL_VERSION="11.2.1"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$REPO_ROOT/app/src/main/assets/proot/proot-aarch64"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

if [ -z "${MUSL_PREFIX:-}" ]; then
  echo "==> musl-cross toolchain"
  curl -sL "https://musl.cc/aarch64-linux-musl-cross.tgz" -o "$WORK/musl.tgz"
  tar xzf "$WORK/musl.tgz" -C "$WORK"
  MUSL_PREFIX="$WORK/aarch64-linux-musl-cross"
fi
export PATH="$MUSL_PREFIX/bin:$PATH"
export CC="aarch64-linux-musl-gcc"

echo "==> talloc $TALLOC_VERSION (static, cross)"
curl -sL "https://www.samba.org/ftp/talloc/talloc-$TALLOC_VERSION.tar.gz" -o "$WORK/talloc.tgz"
tar xzf "$WORK/talloc.tgz" -C "$WORK"
(
  cd "$WORK/talloc-$TALLOC_VERSION"
  # Cross-answers: pre-seed every check waf would otherwise run on target.
  # Regenerate against buildtools/wafadmin if this goes stale.
  CC="$CC" ./configure --disable-python --disable-rpath --enable-static \
    --disable-shared --prefix="$WORK/prefix" --cross-compile \
    --cross-answers="$REPO_ROOT/scripts/talloc-cross-answers.txt"
  make -j"$(nproc)" install
)

echo "==> proot $PROOT_TAG (static, musl)"
git clone --depth 1 --branch "$PROOT_TAG" https://github.com/proot-me/proot.git "$WORK/proot"
(
  cd "$WORK/proot/src"
  export PKG_CONFIG_PATH="$WORK/prefix/lib/pkgconfig"
  export CFLAGS="-Os -static -I$WORK/prefix/include"
  export LDFLAGS="-static -L$WORK/prefix/lib"
  make -j"$(nproc)" proot
  aarch64-linux-musl-strip --strip-unneeded -o "$OUT" proot
)

echo "==> verify (must read: ARM aarch64, static-pie/DYN, no INTERP)"
file "$OUT"
readelf -hW "$OUT" | grep -E "Type|Machine"
echo "INTERP entries: $(readelf -lW "$OUT" | grep -c INTERP || true)"
sha256sum "$OUT"
echo "Done. Update third_party/proot/PROVENANCE.md with the new SHA-256."
