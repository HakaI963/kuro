# ROADMAP.md — Kuro product plan

## Stage 1: in-app cloning (this step) — DONE

- Real BlackBox engine (`ALEX5402/NewBlackbox` @ `89b5983`, Apache-2.0) vendored
  as `:blackbox` (Java + AIDL + native `libblackbox.so` via ndkBuild);
  thin `BlackBoxEngine` adapter behind `VirtualEngine` (no engine rewrites).
- Sources: installed apps only (`PackageManager` LAUNCHER visibility).
- Options: clone name + color tag only.
- Operations: real `installPackageAsUser` (engine-staged progress), `launchApk`,
  `getInstalledPackages` list, `uninstallPackageAsUser` + slot delete.
  Room metadata `CloneRecord(cloneId, packageName, cloneName, colorTag,
  createdAt, engineName, engineUserId)`; the engine is the source of truth.
- Honest `Incompatible`/`Error` status; stub flavor stays NoOp.
- Flavors: `stub` (NoOp) + `virtual` (real engine).

## Stage 2: execution depth — PLACEHOLDER

- Full in-process APK execution behind the same `VirtualEngine` interface.
- Runner UX for loading/compat failures. No UI contract changes.

## Stage 3: sources & controls — PLACEHOLDER

- APK-file import (`CloneSource.ApkFile`).
- Per-clone settings (freeze/hide/biometrics return here, not earlier).

## Later stages — PLACEHOLDER

- Backup/restore, space quotas, sharing, enterprise policy.
