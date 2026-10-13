# Step 2 Engine — Work-Profile Cloning Foundation

Kuro Step 2 replaces all Step 1 mock data with a real, legitimate clone engine
based on Android managed work profiles (the same mechanism used by Island and Shelter).

No VirtualApp-style in-process virtualization, no framework hooks, no reflection into
hidden APIs, no Xposed/LSPosed, no root. UI copy says “isolated space (work profile)”.

## How it works

1. **Provisioning** (`ProvisioningManager`, `KuroDeviceAdminReceiver`)
   - `DevicePolicyManager.isProfileOwnerApp("com.kuro.app")` → `Active`
   - `UserManager.userProfiles.size > 1` without ownership → `ProfileExistsNotOwner`
   - Otherwise → `NotSetup`
   - Setup intent: `DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE` with
     `EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME` pointing at the receiver.
   - Results: `RESULT_OK` → Active; cancel → `Blocked(USER_CANCELLED)`;
     existing foreign profile → `ProfileExistsNotOwner`; policy refusal → `Blocked(POLICY_BLOCKED)`.
2. **Installed apps** (`RealInstalledAppRepository`)
   - `PackageManager.queryIntentActivities` with `ACTION_MAIN` + `CATEGORY_LAUNCHER`.
   - Manifest `<queries>` block declares exactly that intent (no `QUERY_ALL_PACKAGES`;
     Play restricts that permission and Kuro does not need it).
   - Loaded on `Dispatchers.IO` into a `StateFlow`; icons cached in an in-memory `LruCache`.
   - Kuro itself is excluded. System apps are flagged via `FLAG_SYSTEM` (filterable in UI).
3. **Clone operations** (`CloneEngine`, `WorkProfileCloneEngine`, `UnavailableCloneEngine`)
   - Executed by the Kuro instance that is profile owner **inside** the managed profile.
   - `cloneApp(packageName)`: opens a `PackageInstaller` session, copies base APK +
     split APKs from `ApplicationInfo.sourceDir` / `splitSourceDirs`, commits with a
     result `PendingIntent`. Progress comes from real `SessionCallback`-style byte-copy
     fractions and the installer result broadcast (`InstallResultReceiver`) — never timers.
     If direct install is blocked, the UI offers the Play Store listing fallback.
   - `freeze/unfreeze`: `DevicePolicyManager.setPackagesSuspended`
   - `hide/unhide`: `DevicePolicyManager.setApplicationHidden`
   - `remove`: Room record delete + uninstall request for the profile user
     (uninstalls that need confirmation surface as explicit UI states).
   - `listClones`: profile packages with `getUserBadgedIcon` / `getUserBadgedLabel` where available.
   - Until provisioning completes, the UI uses `UnavailableCloneEngine` (safe no-op with a
     human-readable message), so the app never crashes with no work profile.
4. **Cross-profile bridge** (`CrossProfileBridge`, `CloneCommandReceiver`)
   - Minimal, strongly typed: `Command { OPEN, FREEZE, UNFREEZE, HIDE, UNHIDE, REMOVE }`
     plus package name, sent as an explicit intent to `com.kuro.app`.
   - Parent (personal) forwards commands; profile-owner instance executes via `WorkProfileCloneEngine`.
   - Full `setCrossProfilePackages` / ContentProvider wiring is out of scope for this
     foundation; the contract is fixed so Step 3 can harden transport without UI changes.
5. **Persistence** (Room `KuroDatabase`)
   - `CloneRecord(packageName PK, cloneName, colorTag, createdAt, frozen, hidden, lastLaunched, ...)`
   - `ActivityRecord(uid auto, cloneId, cloneName, type, timestampMillis, detail)`
   - Repositories expose `StateFlow`; fresh installs start empty by design.

## Limitations (must tell users)

- One work profile per device. If another app owns it, Kuro shows `ProfileExistsNotOwner`
  and disables creation.
- Some banking/DRM apps detect managed profiles and refuse to run inside them.
- Clones are real installs inside the profile (separate data, separate updates).
- The system can pause the whole profile (e.g. work-profile toggle).
- PackageInstaller installs may require your confirmation inside the profile.
- No silent installs outside the managed profile. No signature/DRM/Play Integrity bypass.

## Manual test steps (real device)

1. Fresh install → Home/Library/Activity show polished empty states. No placeholder clones.
2. Settings → Isolated space card shows “Not set up” → “Set up work profile”.
3. Provisioning → accept system flow → status becomes “Active”, Add Clone enables Create.
4. Add Clone → real installed apps appear with icons + search → pick one → set name/color →
   Create → real `PackageInstaller` progress → success → Room row + activity event.
5. Details → Open (launch intent), Freeze/Unfreeze (`setPackagesSuspended`), Hide/Unhide
   (`setApplicationHidden`), Delete (confirm + Room + event). Snackbar shows real results.
6. With no profile: every engine action returns a clear “setup required” message, no crash.

### adb debug shortcut (emulator/test device only)

```bash
adb shell pm create-user --profileOf 0 --managed KuroTest
# note the returned user id, e.g. 10
adb shell dpm set-profile-owner --user 10 com.kuro.app/.admin.KuroDeviceAdminReceiver
```

Then relaunch Kuro and use Settings → Refresh status.

## Permissions

- No `QUERY_ALL_PACKAGES`. Only the LAUNCHER `<queries>` block.
- `BIND_DEVICE_ADMIN` on the receiver (required for profile ownership).
- No install/suspend/hide permissions in the manifest — those are profile-owner privileges,
  not manifest permissions.

## Tests

- `ProvisioningLogicTest`: result mapping, failure mapping, creation gating.
- `CloneRecordMappingTest`: `CloneRecord ↔ CloneProfile` status derivation + roundtrips,
  `ActivityRecord` roundtrip.
- Run on CI compile only: `gradle testDebugUnitTest`. Engine needs a real device.
