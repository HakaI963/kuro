# proot binary provenance (Step 5c — supersedes the Step 5 note below)

## Shipped asset (current)

- Path in repo: `app/src/main/assets/proot/proot-aarch64` (402,632 bytes)
- What it is: the `usr/bin/proot.static` binary from the official Alpine
  Linux package **proot-static 5.4.0-r2** (edge/community/aarch64),
  downloaded as
  `https://dl-cdn.alpinelinux.org/alpine/edge/community/aarch64/proot-static-5.4.0-r2.apk`
  (.apk = gzipped tar; binary extracted unmodified).
- Verified 2026-10-14 with `file(1)` + `readelf(1)`:
  `ELF 64-bit LSB pie executable, ARM aarch64, static-pie linked`,
  `ET_DYN`, **no PT_INTERP**. Static-PIE musl executes on Android with no
  loader; ET_DYN satisfies the kernel PIE requirement.
- Observed SHA-256 (informational; the on-device validation gate —
  ELF + `proot -V` + smoke — is the functional verification):
  `9e3a279932f365e84641ecda16b11f98a938f1c47b9b84fc993d68ce4d1f9bf8`
- License: **GPL-2.0-or-later** (APKINDEX `L:GPL-2.0-or-later`, Alpine package
  `o:proot` upstream proot-me). Runs as a child process (fork+exec), never
  linked into the app.

## Why this replaced the Step 5 asset (root cause, kept on record)

The Step 5 asset (proot-me v5.3.0 `proot-v5.3.0-aarch64-static`,
sha `fa10b1a7…`) was **ET_EXEC (non-PIE)**: `readelf -h` showed
`Type: EXEC` with fixed virtual addresses. Android only executes PIE
(ET_DYN), so `execve` could never succeed — every distro failed with
"proot -V produced no version output". The Step 5c validation gate
(`ElfProbe`: EM_AARCH64 + ET_DYN + no Linux-loader PT_INTERP) rejects this
build type with the reason logged, so it can never silently regress.

## Fallback: build from source

If the asset ever needs rebuilding (newer proot release, reproducible build),
use `scripts/build-proot.sh`: static musl cross-build (musl.cc toolchain +
static talloc via `scripts/talloc-cross-answers.txt`). Re-verify with `file`
(must read aarch64 + static-pie/DYN, no INTERP), pass the validation gate on
device, and update the SHA-256 line in this file.

## At first use (on device)

`engine.proot.ProotBinary` runs the executable locations through the full
gate (`nativeLibraryDir/libkuro-proot.so` first, then
`/data/local/tmp/kuro-proot`), else `engine.proot.ProotAcquisition` tries
(in order): APK-bundled lib (in place), Alpine .apk, NDK builds,
proot-static-build assets, bundled asset. Downloaded winners are copied to
`nativeLibraryDir/libkuro-proot.so` (chmod 700) and re-validated from there
(su copy to `/data/local/tmp/kuro-proot` only in user-initiated flows);
provenance records the final path. `filesDir/bin/proot` is legacy (Step 5e:
app-private storage cannot exec — error=13) and is never executed.
All-fail yields an honest error card listing each logged reason.
