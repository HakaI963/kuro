# STAGE2.md — Kuro Stage 2 stage map

Stage 1 (in-app app-cloning via the vendored virtual engine) is DONE and FROZEN.
Stage 2 adds Linux containers beside clones. This file is the stage map; the
honest-placeholder inventory at the bottom is normative — anything listed there
must stay non-functional (no fake progress, no fake success) until its stage.

## Stage map

| Step | Scope | Status |
|---|---|---|
| Step 4 | Stage 2 UI shell: System / Terminal / Clones / Settings tabs, distro wizard UI, SAF APK selection UI, root/status/storage/bridge cards | this branch |
| Step 5 | proot container engine: real download, install, terminal sessions, foreground-service persistence | next |
| Step 6 | chroot backend: root via KernelSU, `/data/local/kuro` filesystems | planned |
| Step 7 | Kuro Bridge + APK install: localhost service, `kuro` CLI in distros, APK-file installs | planned |
| Step 8 | Kuro View (GUI): container graphical output | planned |
| Step 9 | Polish: performance, accessibility, release hardening | planned |

## Naming decisions (locked in Step 4)

- Tabs: **System** (dashboard) | **Terminal** (distro manager) | **Clones** (Stage 1 clone manager, moved intact) | **Settings**.
- **Kuro Bridge**: localhost service that lets distro containers control Kuro
  clones via the `kuro` command (Step 7).
- **Kuro View**: GUI output surface of a container (Step 8).
- **`kuro` CLI**: the in-distro command backed by Kuro Bridge (Step 7).
- Container types: **proot** (no root needed) / **chroot** (requires root).

## Storage layout (locked in Step 4)

- proot containers: Kuro private data, `<filesDir>/containers/<id>`.
- chroot containers: `/data/local/kuro/chroot/<id>`.
- One filesystem per container — every container gets its own isolated dir.
- See `core.system.StoragePaths`.

## Session persistence rule (locked in Step 4, enforced Step 5+)

Sessions persist via a foreground service until the user stops them manually
("Stop" per session, "Stop all sessions" in Settings). No auto-kill on app
background.

## Root rules (locked in Step 4)

- Silent background availability check on every app open (short timeout),
  result cached in memory (`core.root.RootChecker`), shown in Settings.
- `su` runs ONLY on user-initiated actions ("Request root access", chroot
  creation in Step 6). Never blocks start, never auto-dialogs.

## Honest placeholders (normative — keep honest until the stated step)

- Add-distro Create → dialog "Container engine integration lands in the next
  build stage (Step 5)." No download progress anywhere.
- Terminal session screen → designed "no session running" state until Step 5.
- "Add Kuro View" action → Step 8 note dialog.
- APK file source → real SAF pick + real name/size, then "APK install engine
  arrives in Step 7" state. Installed-apps source stays fully functional.
- Kuro Bridge card values (port/token) → labeled static placeholders until Step 7.

## Quality rules (unchanged from Stage 1)

Motion tokens (`docs/MOTION.md`), M3 tonal palette, opaque screen roots,
enter-only tab transitions, spacing scale 4/8/12/16/24/32, reduce-motion
support. Splash intro and the virtual-engine integration stay untouched.
