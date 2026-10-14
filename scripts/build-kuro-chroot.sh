#!/bin/bash
# Build the kuro-chroot static helper (third_party/kuro-chroot/kuro-chroot.c).
# Output: app/src/main/assets/kuro-chroot (must read: ARM aarch64,
# static-pie linked, ET_DYN, no PT_INTERP).
#
# Uses the musl-cross toolchain (auto-downloaded if MUSL_PREFIX is unset);
# pure C, no external dependencies. Re-run after any kuro-chroot.c change
# and re-verify with file(1)/readelf(1) before committing.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$REPO_ROOT/app/src/main/assets/kuro-chroot"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

if [ -z "${MUSL_PREFIX:-}" ]; then
  echo "==> musl-cross toolchain"
  curl -sL "https://musl.cc/aarch64-linux-musl-cross.tgz" -o "$WORK/musl.tgz"
  tar xzf "$WORK/musl.tgz" -C "$WORK"
  MUSL_PREFIX="$WORK/aarch64-linux-musl-cross"
fi

echo "==> kuro-chroot (static)"
"$MUSL_PREFIX/bin/aarch64-linux-musl-gcc" -Os -static \
  -o "$WORK/kuro-chroot" "$REPO_ROOT/third_party/kuro-chroot/kuro-chroot.c"
"$MUSL_PREFIX/bin/aarch64-linux-musl-strip" --strip-unneeded \
  -o "$OUT" "$WORK/kuro-chroot"

echo "==> verify"
file "$OUT"
readelf -hW "$OUT" | grep -E "Type|Machine"
echo "INTERP entries: $(readelf -lW "$OUT" | grep -c INTERP || true)"
sha256sum "$OUT"
