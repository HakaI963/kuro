#!/data/data/com.kuro.app/files/usr/bin/bash
# stop_gui_chroot.sh — app-uid: stop X11/VirGL + root stop_*_gui.sh (Pulse stays up)
# Arg1 / FLUX_CHROOT_DISTRO: debian13_chroot|alpine_chroot|fedora_chroot|void_chroot|opensuse_chroot|deepin_chroot|chimera_chroot|manjaro_chroot|ubuntu_chroot|kali_chroot|parrot_chroot|archlinux_chroot.
# Does NOT pkill proot.

DISTRO_HINT="${1:-${FLUX_CHROOT_DISTRO:-debian13_chroot}}"

PKG="${TERMUX_APP__PACKAGE_NAME:-com.kuro.app}"
_HOST_ENV="${TERMUX__PREFIX:-/data/data/${PKG}/files/usr}/etc/fluxlinux-host.env"
[ -r "$_HOST_ENV" ] && . "$_HOST_ENV"

PKG="${TERMUX_APP__PACKAGE_NAME:-$PKG}"
TERMUX_PREFIX="${TERMUX__PREFIX:-/data/data/$PKG/files/usr}"
TERMUX_HOME="${TERMUX__HOME:-/data/data/$PKG/files/home}"
export HOME="$TERMUX_HOME"
export PATH="$TERMUX_PREFIX/bin:$TERMUX_PREFIX/bin/applets:/system/bin:/system/xbin:$PATH"

case "$DISTRO_HINT" in
  alpine|alpine_chroot)
    CHROOT_PATH="${CHROOT_PATH:-/data/local/kuro/chroot/alpine_chroot/rootfs}"
    ROOT_STOP_NAME="${ROOT_STOP_NAME:-stop_alpine_gui.sh}"
    ;;
  fedora|fedora_chroot)
    CHROOT_PATH="${CHROOT_PATH:-/data/local/kuro/chroot/fedora_chroot/rootfs}"
    ROOT_STOP_NAME="${ROOT_STOP_NAME:-stop_guest_gui.sh}"
    ;;
  void|void_chroot)
    CHROOT_PATH="${CHROOT_PATH:-/data/local/kuro/chroot/void_chroot/rootfs}"
    ROOT_STOP_NAME="${ROOT_STOP_NAME:-stop_guest_gui.sh}"
    ;;
  opensuse|opensuse_chroot)
    CHROOT_PATH="${CHROOT_PATH:-/data/local/kuro/chroot/opensuse_chroot/rootfs}"
    ROOT_STOP_NAME="${ROOT_STOP_NAME:-stop_guest_gui.sh}"
    ;;
  deepin|deepin_chroot)
    CHROOT_PATH="${CHROOT_PATH:-/data/local/kuro/chroot/deepin_chroot/rootfs}"
    ROOT_STOP_NAME="${ROOT_STOP_NAME:-stop_guest_gui.sh}"
    ;;
  chimera|chimera_chroot)
    CHROOT_PATH="${CHROOT_PATH:-/data/local/kuro/chroot/chimera_chroot/rootfs}"
    ROOT_STOP_NAME="${ROOT_STOP_NAME:-stop_guest_gui.sh}"
    ;;
  manjaro|manjaro_chroot)
    CHROOT_PATH="${CHROOT_PATH:-/data/local/kuro/chroot/manjaro_chroot/rootfs}"
    ROOT_STOP_NAME="${ROOT_STOP_NAME:-stop_guest_gui.sh}"
    ;;
  ubuntu|ubuntu_chroot)
    CHROOT_PATH="${CHROOT_PATH:-/data/local/kuro/chroot/ubuntu_chroot/rootfs}"
    ROOT_STOP_NAME="${ROOT_STOP_NAME:-stop_guest_gui.sh}"
    ;;
  kali|kali_chroot)
    CHROOT_PATH="${CHROOT_PATH:-/data/local/kuro/chroot/kali_chroot/rootfs}"
    ROOT_STOP_NAME="${ROOT_STOP_NAME:-stop_guest_gui.sh}"
    ;;
  parrot|parrot_chroot)
    CHROOT_PATH="${CHROOT_PATH:-/data/local/kuro/chroot/parrot_chroot/rootfs}"
    ROOT_STOP_NAME="${ROOT_STOP_NAME:-stop_guest_gui.sh}"
    ;;
  archlinux|archlinux_chroot)
    CHROOT_PATH="${CHROOT_PATH:-/data/local/kuro/chroot/archlinux_chroot/rootfs}"
    ROOT_STOP_NAME="${ROOT_STOP_NAME:-stop_guest_gui.sh}"
    ;;
  *)
    CHROOT_PATH="${CHROOT_PATH:-/data/local/kuro/chroot/debian13_chroot/rootfs}"
    ROOT_STOP_NAME="${ROOT_STOP_NAME:-stop_debian13_gui.sh}"
    ;;
esac
ROOT_STOP_SCRIPT="$TERMUX_HOME/$ROOT_STOP_NAME"
ROOT_STOP_TMP="/data/local/kuro/$ROOT_STOP_NAME"
RESOLVER_SRC="$TERMUX_HOME/resolve_bb.sh"
RESOLVER_TMP="/data/local/kuro/fluxlinux_resolve_bb.sh"

echo "========================================"
echo "Kuro: STOP XFCE (chroot mode)"
echo "  distro=$DISTRO_HINT path=$CHROOT_PATH"
echo "========================================"

if [ ! -f "$ROOT_STOP_SCRIPT" ]; then
  echo "Kuro: [WARN] missing $ROOT_STOP_SCRIPT — best-effort kill only"
else
  SU_BIN=su
  for s in /system/bin/su /system/xbin/su /sbin/su; do
    if [ -x "$s" ]; then SU_BIN="$s"; break; fi
  done
  "$SU_BIN" -c "cp -f '$ROOT_STOP_SCRIPT' '$ROOT_STOP_TMP' 2>/dev/null; \
    [ -f '$RESOLVER_SRC' ] && cp -f '$RESOLVER_SRC' '$RESOLVER_TMP' 2>/dev/null; \
    chmod 755 '$ROOT_STOP_TMP' '$RESOLVER_TMP' 2>/dev/null; \
    DEBIANPATH='$CHROOT_PATH' CHROOT_ROOT='$CHROOT_PATH' FLUX_CHROOT='$CHROOT_PATH' \
    TARGET_PREFIX='$TERMUX_PREFIX' \
    FLUX_BB='${FLUX_BB:-}' FLUX_RESOLVE_BB='$RESOLVER_TMP' sh '$ROOT_STOP_TMP'"
fi

echo "Stopping VirGL (app uid; Pulse stays running)..."
pkill -f "virgl_test_server" 2>/dev/null || true

pkill -9 -f "termux-x11" 2>/dev/null || true
pkill -9 -f "app_process.*termux-x11" 2>/dev/null || true

echo "Chroot GUI stop complete."
echo "========================================"
exit 0
