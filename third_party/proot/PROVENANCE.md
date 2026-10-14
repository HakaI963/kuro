# proot binary provenance (Step 5)

## Shipped asset

- Path in repo: `app/src/main/assets/proot/proot-aarch64` (1,479,432 bytes)
- What it is: official static aarch64 build of PRoot, release asset
  `proot-v5.3.0-aarch64-static` from `proot-me/proot` release **v5.3.0**
  (`https://github.com/proot-me/proot/releases/tag/v5.3.0`).
- Verified 2026-10-14 with `file(1)`:
  `ELF 64-bit LSB executable, ARM aarch64, version 1 (SYSV), statically linked`
- Observed SHA-256 (informational; the on-device `proot -V` run is the
  functional verification, see `engine.proot.ProotBinary`):
  `fa10b1a7818c2f5b1dcb5834450570c368c9ecf66d31521509621b95c4538a45`
- License: **GPLv2** (`COPYING` in proot-me/proot at tag v5.3.0 —
  "GNU GENERAL PUBLIC LICENSE, Version 2, June 1991"). The binary is used
  unmodified as a shipped asset; proot runs as a child process (fork+exec),
  it is not linked into the app.

## Why v5.3.0 and not newer

Newer proot-me releases (v5.4.x, v5.5.0) publish a single `proot` asset whose
ELF header identifies it as **x86_64** (e_machine 0x3E, checked 2026-10-14).
v5.3.0 is the newest release publishing an explicitly labeled static aarch64
asset (e_machine 0xB7). Kuro targets aarch64 devices, so v5.3.0 is correct.

## Fallback: build from source

If the asset ever needs rebuilding (newer release, reproducible build), use
`scripts/build-proot.sh`: it cross-compiles proot + a static talloc with the
Android NDK (same NDK as CI) and drops the result at the asset path above.
Re-verify with `file` (must read aarch64 + statically linked) and update the
SHA-256 line in this file.

## At first use (on device)

`engine.proot.ProotBinary` extracts the asset to `filesDir/bin/proot`,
`chmod 700`, then runs `proot -V` through a guarded process call and checks
the output. Any failure is an honest error state in the UI — never silent.
