# engine-blackbox module

Thin Stage 1 adapter implementing `:engine`'s `VirtualEngine` with a file-backed
virtual space under `filesDir/virtualspace/<cloneId>`.

It is deliberately structured so the real BlackBox native core drops in behind the
same interface without touching app UI:

- `BlackBoxVirtualEngine` keeps the exact `VirtualEngine` method shapes the native
  core will implement (`installIntoSpace`, `launch`, `listSpacePackages`, `remove`).
- Space layout (`base_<i>_<name>.apk` + `clone.meta`) is the staging format the
  native core's `installPackageAsUser`-equivalent will consume in a later stage.
- `launch()` currently performs real integrity work (space exists, APKs present,
  `getPackageArchiveInfo` parses, package name matches) and reports `Started`;
  full in-process APK execution is the documented next swap (see `docs/ENGINE.md`).

No code here was copied from the BlackBox repo; this is Kuro's own adapter written
against the interface. Upstream core integration must respect the Apache-2.0 license
attribution noted in `docs/ENGINE.md`.
