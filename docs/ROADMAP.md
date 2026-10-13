# ROADMAP.md — Kuro product plan

## Stage 1: in-app cloning (this step) — DONE

- In-app virtual space under `filesDir/virtualspace/<cloneId>` (no system install,
  no work profile, no root).
- Sources: installed apps only (`PackageManager` LAUNCHER visibility).
- Options: clone name + color tag only.
- Operations: install into space (real byte-copy progress), launch validation,
  list, delete. Room metadata `CloneRecord(cloneId, packageName, cloneName,
  colorTag, createdAt, engineName)`.
- Honest `Incompatible` status card on unsupported Android versions.
- Flavors: `stub` (NoOp) + `virtual` (BlackBox Stage 1 adapter).

## Stage 2: execution depth — PLACEHOLDER

- Full in-process APK execution behind the same `VirtualEngine` interface.
- Runner UX for loading/compat failures. No UI contract changes.

## Stage 3: sources & controls — PLACEHOLDER

- APK-file import (`CloneSource.ApkFile`).
- Per-clone settings (freeze/hide/biometrics return here, not earlier).

## Later stages — PLACEHOLDER

- Backup/restore, space quotas, sharing, enterprise policy.
