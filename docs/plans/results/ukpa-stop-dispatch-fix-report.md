# UKPA chroot Stop / uninstall dispatch fix

- Date: 2026-08-14
- Device: Xiaomi 2311DRK48I (duchamp), serial `Y5WWBMJVOZSK4HU8`, KernelSU
- Overall: **PASS**
- Parent plan: `docs/plan/ubuntu-kali-parrot-arch.md` stays **DEVICE PASS — 2026-08-14**
- APK: `app/build/outputs/apk/ivarna/release/app-ivarna-release.apk` (`adb -s Y5WWBMJVOZSK4HU8 install -r`)

## What was wrong

`DesktopLauncher.stop()` runs `bash $HOME/stop_gui_chroot.sh <cardId>` with no `CHROOT_PATH` env. `start_gui_chroot.sh` already had UKPA arms; `stop_gui_chroot.sh` did not. `ubuntu_chroot` / `kali_chroot` / `parrot_chroot` / `archlinux_chroot` hit `*` → `CHROOT_PATH=/data/local/kuro/chroot/debian13_chroot/rootfs` + `stop_debian13_gui.sh`. That can unmount a live Debian chroot. Home still flipped to Start because `StateManager.setGuiRunning` is cleared before the script, and host `pkill termux-x11` hid the miss.

Same-class miss: `uninstall_guest_chroot.sh` allowlist stopped at FVO+DCM, so in-app uninstall of a UKPA chroot refused `/data/local/kuro/chroot/ubuntu_chroot/rootfs` (etc.).

## Files changed

- `app/src/main/assets/scripts/chroot/stop_gui_chroot.sh` — four UKPA arms before `*`; header Arg1 list updated; `*` still Debian 13 + `stop_debian13_gui.sh`; alpine still `stop_alpine_gui.sh`
- `app/src/main/assets/scripts/chroot/uninstall_guest_chroot.sh` — allowlist adds `chrootUbuntu` / `chrootKali` / `chrootParrot` / `chrootArch`; refuse-unknown kept
- `app/src/test/java/com/ivarna/fluxlinux/core/install/UkpaHostDispatchContractTest.kt` — start/stop twins, default Debian, uninstall allowlist, family landmines

Not edited: family scripts, `DistroInstallProfile`, `DistroRepository`, `start_gui_chroot.sh`.

## Dispatch grep (`app/src/main/assets/scripts/chroot/`)

Only `start_gui_chroot.sh` / `stop_gui_chroot.sh` / `uninstall_guest_chroot.sh` dispatch by guest name and list DCM. Debian-only scripts (`start_debian13_gui.sh`, `stop_debian13_gui.sh`, `setup_debian13_chroot.sh`, `uninstall_debian13_chroot.sh`) and env-driven helpers (`fluxlinux_chroot.sh`, `chroot_processes.sh`, `chroot_size.sh`) were left alone.

## Unit tests

```
./gradlew :app:testIvarnaDebugUnitTest --no-daemon
BUILD SUCCESSFUL
```

Includes `UkpaHostDispatchContractTest`. Existing catalog sizes unchanged (24 installable / 12 rootfs).

## Per-path D1 / D5

Guests were already installed; none reinstalled.

| Card | D1 XFCE `/proc/<pid>/root` | D5 log path | xfce gone | card Start | shots |
|------|----------------------------|-------------|-----------|------------|-------|
| `ubuntu_chroot` | `/data/local/kuro/chroot/ubuntu_chroot/rootfs` | `distro=ubuntu_chroot path=/data/local/kuro/chroot/ubuntu_chroot/rootfs` | yes | yes | `ubuntu_chroot_xfce_retest.png`, `ubuntu_chroot_stopped_retest.png` |
| `kali_chroot` | `/data/local/kuro/chroot/kali_chroot/rootfs` | `distro=kali_chroot path=/data/local/kuro/chroot/kali_chroot/rootfs` | yes | yes | `kali_chroot_xfce_retest.png`, `kali_chroot_stopped_retest.png` |
| `parrot_chroot` | `/data/local/kuro/chroot/parrot_chroot/rootfs` | `distro=parrot_chroot path=/data/local/kuro/chroot/parrot_chroot/rootfs` | yes | yes | `parrot_chroot_xfce_retest.png`, `parrot_chroot_stopped_retest.png` |
| `archlinux_chroot` | `/data/local/kuro/chroot/archlinux_chroot/rootfs` | `distro=archlinux_chroot path=/data/local/kuro/chroot/archlinux_chroot/rootfs` | yes | yes | `archlinux_chroot_xfce_retest.png`, `archlinux_chroot_stopped_retest.png` |

No Stop log named `path=/data/local/kuro/chroot/debian13_chroot/rootfs` for a UKPA card.

On-device after first Stop (`HostScriptDeployer.deployScripts` from `DesktopLauncher.stop`):

- `$HOME/stop_gui_chroot.sh` contains `ubuntu|ubuntu_chroot` and `chrootUbuntu`
- `$HOME/uninstall_guest_chroot.sh` contains the four UKPA paths (dry-check only; no guest deleted)

## Debian sibling smoke

After the first UKPA Stop (`ubuntu_chroot`):

- `/data/local/kuro/chroot/debian13_chroot/rootfs` still present; `usr/bin/startxfce4` still present
- Home Debian (Rooted) still showed Start
- Start → Launch XFCE4 painted; pids rooted at `/data/local/kuro/chroot/debian13_chroot/rootfs`
- Screenshot: `ukpa_stopfix_debian_chroot_smoke.png`
- Debian Stop then used `distro=debian13_chroot path=/data/local/kuro/chroot/debian13_chroot/rootfs` (`*` arm intact)

## Other checks

- D-PROOT: Ubuntu proot Start → Stop used `=== STOP method=proot script=stop_gui.sh ===`; XFCE gone; card back to Start. Unchanged.
- Plan status not flipped.

## Gradle

`./gradlew --stop` after this report.
