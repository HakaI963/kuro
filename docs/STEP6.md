# STEP6.md — chroot backend (planned; helper + gates ship in Step 5c)

Chroot containers do NOT use proot. They run through `kuro-chroot`, a
purpose-built static musl helper shipped in Step 5c
(`app/src/main/assets/kuro-chroot`, built by `scripts/build-kuro-chroot.sh`
from `third_party/kuro-chroot/kuro-chroot.c` — pure C, no dependencies).

## Runtime rules (implement sessions in Step 6)

- Sessions run the helper via KernelSU only:
  `su -c "exec <helper> enter <rootfs> <shell> -l"`.
  The proot path never invokes su; the Step 4b silent root check is unchanged.
- Rootfs storage: `/data/local/kuro/chroot/<id>/rootfs` (one filesystem per
  container). Install = extract into the Kuro cache dir, then `su -c mv` to
  the final path — same `/data` volume, so the move is an instant rename.
- Mount state is tracked per container id, derived from `/proc/mounts`
  (`kuro-chroot status <rootfs>`); no separate state file to go stale.
- DNS for `/etc/resolv.conf` is passed as `KURO_DNS1`/`KURO_DNS2` env
  assignments inside the `su -c` command line (helper falls back to
  1.1.1.1/8.8.8.8); the app reads Android LinkProperties (ACCESS_NETWORK_STATE
  already declared).

## Device validation gates (shipped, run through su from Settings)

- g1: `su -c "kuro-chroot version"` exits 0 with version output.
- g2: mount + umount cycle on the empty test dir `/data/local/kuro/.gate-test`.
- Results persist to `filesDir/bin/chroot-gates`, are logged to
  `kuro-engine.log`, and surface in Settings (root card) + Engine Diagnostics.
  Gate failures are honest error states, never crashes. Gates run ONLY from
  the explicit Settings action while root is granted.
