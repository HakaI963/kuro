# Assumptions — Kuro Stage 1 (virtual engine)

1. **Direction change**: all work-profile code from the previous step is deleted
   (receiver, device-admin XML, provisioning, profile-owner checks, banners).
   Verified with repo-wide grep for `DeviceAdmin`, `Provisioning`, `WorkProfile`,
   `setPackagesSuspended`, `setApplicationHidden`, and `work profile`.
2. **Engine choice**: BlackBox-style (`FBlackBox/BlackBox`, Apache-2.0, API 21+)
   over VirtualApp (full source gated behind a purchased business license).
   Nothing is copied from upstream in this step; `:engine-blackbox` is Kuro's own
   thin adapter. See `docs/ENGINE.md`.
3. **Stage 1 execution scope**: install = real APK byte-copy into
   `filesDir/virtualspace/<cloneId>` with real progress; launch = real integrity
   validation (`getPackageArchiveInfo` + package match) + `lastLaunched` running
   state. Full in-process APK execution plugs in behind `VirtualEngine` later.
4. **No wrapper jar**: repo ships `gradle-wrapper.properties` only. CI installs
   Gradle 8.10.2 plus cached NDK/CMake directly.
5. **Flavors**: `stub` (NoOp, always green) + `virtual` (Stage 1 adapter). CI builds
   and uploads both APKs.
6. **Fresh installs are empty**: Room starts empty; no seed data anywhere.
7. **Copy**: “inside Kuro” / “Kuro space” / “virtual space”. Never claims full
   virtualization of arbitrary apps; virtualization-detecting apps refusing to run
   is documented expected behavior.
