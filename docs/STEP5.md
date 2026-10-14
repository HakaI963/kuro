# STEP5.md — real proot engine + live terminals

Step 5 turns the Stage 2 shell into a working proot container engine: real
rootfs downloads, real extraction + bootstrap, and live pty terminal sessions
in a foreground service. No fake progress anywhere — every stage reports
measured values or an honest typed error.

## Vendored components (unmodified except build-system patches)
| Component | Source (pinned) | License | Notes |
|---|---|---|---|
| `terminal-emulator` + JNI (`:termux-terminal-emulator`) | termux/termux-app tag **v0.118.2** (`21c0d51`) | Apache-2.0 (repo's own exception statement + jackpal origin; see below) | VT emulation core + `termux.c` pty JNI (`createSubprocess`, `setPtyWindowSize`, `waitFor`, `close`). Builds `libtermux.so` via ndkBuild. |
| `terminal-view` (`:termux-terminal-view`) | same tag | Apache-2.0 (same statement) | `TerminalView` renderer + input. |
| proot binary (`app/src/main/assets/proot/proot-aarch64`, also shipped as `app/src/main/jniLibs/arm64-v8a/libkuro-proot.so` — offline byte source, relocated to codeCache per Step 5f) | Alpine **proot-static 5.4.0-r2** (`usr/bin/proot.static` from the official edge/community/aarch64 `.apk`) — Step 5c replacement, see below | **GPL-2.0-or-later** | Static-PIE musl aarch64, ET_DYN, no PT_INTERP (verified by `file(1)`/`readelf(1)`). Runs as a child process (fork+exec), never linked. Full provenance: `third_party/proot/PROVENANCE.md`. |
| kuro-chroot helper (`app/src/main/assets/kuro-chroot`) | Built from `third_party/kuro-chroot/kuro-chroot.c` via `scripts/build-kuro-chroot.sh` (musl-cross static) | Kuro (no third-party code; pure C) | Static-PIE aarch64 for Step 6 chroot sessions; device gates g1/g2 in Settings. |
| talloc (fallback builds only) | samba.org talloc 2.4.2, static | LGPLv3 (talloc) — only used by `scripts/build-proot.sh`, not shipped | proot's allocator dependency for source rebuilds. |
| Apache Commons Compress 1.27.1 | Maven Central | Apache-2.0 | tar reading (symlinks, modes, traversal guard in `TarPathGuard`). |
| XZ for Java 1.9 (`org.tukaani:xz`) | Maven Central | Public domain (Unlicense-style; see its `COPYING`) | `.tar.xz` decompression backend for Commons Compress. |
| OkHttp 4.12.0 | Maven Central | Apache-2.0 | Range-resume downloads with retry. |

License note on the Termux modules: termux-app as a whole is GPLv3, but its
own `LICENSE.md` places `terminal-emulator/` and `terminal-view/` under the
Apache-2.0 exception (jackpal Android-Terminal-Emulator origin). Kuro vendors
only those two module directories, unmodified except `KURO-PATCH` build files
(sdk literals, module renames). If upstream ever relicenses, re-audit
`third_party/termux-app` before updating the pin.

## proot launch (see `engine.proot.ProotLauncher`)

```
proot -r <rootfs> -b /dev -b /proc -b /sys -b /sdcard -b /storage
      -b /data/local/tmp:/tmp -w /root -0 <shell> -l
```

Bind rationale:

| Bind | Why |
|---|---|
| `-b /dev` | device nodes for a functional userspace |
| `-b /proc` | process info (`ps`, `/proc/stat`) |
| `-b /sys` | sysfs (some tools expect it) |
| `-b /sdcard`, `-b /storage` | user files visible inside (`ls /sdcard`) |
| `-b /data/local/tmp:/tmp` | writable tmp (Android has no shared `/tmp`) |
| `-w /root` | sane login working dir |
| `-0` | fakeroot: guest `whoami` = root, no host root needed |

`<shell>` = configured bash/zsh when present in the rootfs, else `/bin/sh`.
Environment: `HOME=/root`, standard Linux `PATH`, `TERM=xterm-256color`,
`LANG=C.UTF-8`, `TZ` from Android, `KURO_CONTAINER=<id>`,
`KURO_BRIDGE=0|1`. The proot path never invokes `su` (Step 6 owns root).

## Session lifecycle

```
wizard Create ──► provisioner: Downloading → Verifying → Extracting
                                        │ (cancel: partial cleanup)
                                        ▼ (retry per stage)
                                  Bootstrapping → Ready ──► Room + store
                                                              │
Terminal card "Open terminal" ──► session screen ──► auto-start ──► LIVE
         │ back = detach only (fd stays)          ▲     │
         └──────── reopen = ATTACH same fd ───────┘     │ Stop = SIGKILL group
                                                        ▼
                                              ENDED note + restart
FGS: first spawn → startForeground (sessions summary + per-download
progress + Cancel/Stop actions). Last session end + no installs → stopSelf.
Background + screen-off safe via FGS (+ optional per-container wake lock).
```

Attach/detach: `TerminalView.attachSession()` adopts a live emulator or spawns
via `updateSize()` on first attach; disposing the View never kills the pty.
Multiple sessions per container via selector chips + New session.

## Storage layout (supersedes the Step 4 `<filesDir>/containers` sketch)

- proot containers: `<filesDir>/distros/proot/<id>/rootfs` (one fs per container)
- tarball cache: `<cacheDir>/downloads/<distro>-<file>` (Range-resume + reuse)
- proot binary: `codeCacheDir/files/bin/proot` (Step 5f; validated `-V` +
  smoke from there) or `cacheDir/bin/proot` fallback; `/data/local/tmp/`
  only via su with granted root; legacy `filesDir/bin/proot` is never
  executed (targetSdk 29+ error=13)
- engine log: `<filesDir>/kuro-engine.log` (1 MB rotate); crash log unchanged
- Delete = stop sessions + `rm -rf` container dir + Room delete, reporting
  real freed bytes. System tab storage reflects it on next refresh.

## Catalog sources + refresh policy

- Fedora 43 / Ubuntu 24.04 noble / Debian 13 trixie / Alpine 3.22: pinned
  termux/proot-distro release tarballs (aarch64, proot-ready). proot-distro
  publishes no checksums → size-verified against catalog byte counts.
- Kali 2026.2 minimal: official NetHunter `kali-2026.2/rootfs` tarball with
  upstream SHA256SUMS checksum → streaming verify enforced.
- Refresh = bump `DistroCatalog` (versions, URLs, byte counts, checksums).
  A rotated upstream checksum surfaces as an honest checksum error with retry,
  never silent acceptance. `docs/STAGE2.md` placeholder inventory updated.

## Error honesty

Typed `InstallFailure` (network / checksum / extract / exec / permission /
storage), each with title + copyable detail, retry where meaningful, cancel
anywhere with partial cleanup. Everything also lands in `kuro-engine.log`.

## Step 5d: download resume/416 fix + official-mirror failover

Device-verified defect: resuming from a stale temp (size >= real asset size)
sent `Range: bytes=<past-EOF>-`, and the server correctly answered HTTP 416.
The downloader now resolves the expected size per attempt (HEAD, else catalog),
sends Range ONLY for strictly-partial temps (`DownloadPolicy.shouldResume`),
deletes stale temps before any GET, retries once with a plain GET on 416/415,
and re-downloads fully once on completion size mismatch before failing.

If the primary (termux/proot-distro) fails twice with HTTP 4xx/5xx, the install
continues automatically on the catalog `fallbackUrl` (official vendor rootfs;
Alpine minirootfs, Ubuntu ubuntu-base, Kali `current` symlink, Fedora Container
Base OCI) with a UI note ("Primary mirror failed, switched to official
mirror") + engine-log line. Timeouts/DNS retry the same source and never count
toward failover. The extractor sniffs gzip vs xz vs plain tar, and
materializes OCI image layers (`OciLayout`, Fedora only). Wizard names
auto-default to the selected distro id with a computed
"e.g. <distro>-mobile" placeholder; user-typed names are preserved
(`DistroNamePolicy`).

## Step 5f: extract proot to codeCacheDir (fix EACCES)

`nativeLibraryDir` is read-only at runtime (EACCES on copy), so Step 5e's
copy target never worked. Winners now install to the app-writable
`codeCacheDir/files/bin/proot` (chmod 700, `-V` + smoke re-validated from
that absolute path — sessions launch it directly, never via su), with
`cacheDir/bin/proot` as the no-su fallback. `/data/local/tmp/kuro-proot`
is attempted ONLY in user-initiated flows with granted root; otherwise both
failing logs "No executable directory found (noexec on codeCache and
cache)" as the honest terminal error. The APK-bundled `libkuro-proot.so`
remains the offline byte source (relocated, never executed in place).
Settings > Root Access gains a "Test root access" button (`su -c id` with
a grant hint on failure) so su denials are diagnosable on device.

## Step 5e: executable location (fix error=13 Permission denied)

App-private storage (`files/`, `cache/`) cannot exec on targetSdk 29+
(`execve` fails with error=13 even for static PIE). The winner is now
relocated BEFORE it counts: APK-bundled `libkuro-proot.so` (shipped via
`app/src/main/jniLibs/arm64-v8a/`, same Alpine static bytes) validates in
place at `nativeLibraryDir/libkuro-proot.so`; downloaded candidates are
copied there, chmod 700, and RE-VALIDATED (`-V` + smoke) from the final
path. Only then is provenance recorded (now including the final `path=`
line shown in Engine Diagnostics). Fallback, user-initiated only: su copy
to `/data/local/tmp/kuro-proot`, validated from there; background
revalidation never invokes su. The ELF gate rejects ANY PT_INTERP (even the
Android linker); every candidate's EI_CLASS/machine/PIE/INTERP proof is
logged to `kuro-engine.log`. `filesDir/bin/proot` is legacy and never
executed.

## Step 5c: validated static proot + kuro-chroot helper

The Step 5 asset (proot-me v5.3.0 static aarch64) was ET_EXEC (non-PIE):
Android only executes PIE, so `execve` could never succeed. Step 5c adds an
ELF forensics gate (`engine.proot.ElfProbe`: EM_AARCH64 + ET_DYN + no
Linux-loader PT_INTERP, logged forever) and a reliability-ordered acquisition
pipeline (`engine.proot.ProotAcquisition`): Alpine musl-static `.apk` →
NDK-built PRoot releases → proot-static-build assets → bundled asset →
(local CI build script). Winner installs to `filesDir/bin/proot` with
provenance + validation proof visible in Engine Diagnostics; all-fail yields
an honest card listing each logged reason. Cold starts re-validate and
auto re-acquire in background. See `third_party/proot/PROVENANCE.md`.
