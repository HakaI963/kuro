/*
 * kuro-chroot — purpose-built static helper for Kuro chroot containers.
 * Pure C, no external dependencies (libc only). Built static with
 * musl-cross (see scripts/build-kuro-chroot.sh) so it runs on any
 * aarch64 Android without a dynamic loader.
 *
 * Subcommands:
 *   version                   -> prints version, exits 0
 *   mount <rootfs>            -> bind-mount /dev /proc /sys /sdcard /storage
 *                                and /data/local/tmp onto <rootfs>/tmp;
 *                                writes /etc/resolv.conf (DNS from
 *                                KURO_DNS1/KURO_DNS2 env, else 1.1.1.1/8.8.8.8)
 *   umount <rootfs>           -> lazy unmount in reverse order
 *   enter <rootfs> <cmd...>   -> chroot(<rootfs>), chdir("/"), exec cmd
 *                                (inherits the calling pty fds)
 *   status <rootfs>           -> prints "mounted" or "not-mounted" (from
 *                                /proc/mounts), exits 0 either way
 *
 * Sessions run it via KernelSU: su -c "exec <helper> enter <rootfs> <shell> -l"
 * (Step 6 wires sessions; this step ships + validates the helper).
 */
#define _GNU_SOURCE
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <limits.h>
#include <sys/mount.h>
#include <sys/stat.h>

#define KURO_VERSION "kuro-chroot 1.0.0 (step-5c)"

static int bind_one(const char *src, const char *dst) {
    struct stat st;
    /* target must exist with the right node type */
    if (stat(src, &st) == 0 && S_ISDIR(st.st_mode)) {
        mkdir(dst, 0755);
    } else {
        FILE *f = fopen(dst, "a");
        if (f) fclose(f);
    }
    if (mount(src, dst, NULL, MS_BIND | MS_REC, NULL) != 0) {
        fprintf(stderr, "kuro-chroot: bind %s -> %s: %s\n", src, dst, strerror(errno));
        return -1;
    }
    return 0;
}

static int cmd_mount(const char *rootfs) {
    static const char *const subs[] = {"dev", "proc", "sys", "sdcard", "storage"};
    char dst[PATH_MAX];
    int rc = 0;
    size_t i;
    for (i = 0; i < sizeof(subs) / sizeof(subs[0]); i++) {
        char src[PATH_MAX];
        snprintf(src, sizeof(src), "/%s", subs[i]);
        snprintf(dst, sizeof(dst), "%s/%s", rootfs, subs[i]);
        if (bind_one(src, dst) != 0) rc = -1;
    }
    /* /data/local/tmp -> <rootfs>/tmp */
    snprintf(dst, sizeof(dst), "%s/tmp", rootfs);
    mkdir(dst, 0755);
    if (mount("/data/local/tmp", dst, NULL, MS_BIND | MS_REC, NULL) != 0) {
        fprintf(stderr, "kuro-chroot: bind /data/local/tmp -> %s: %s\n", dst, strerror(errno));
        rc = -1;
    }
    /* resolv.conf from env (passed by the Kotlin gate runner), else public DNS */
    {
        const char *d1 = getenv("KURO_DNS1");
        const char *d2 = getenv("KURO_DNS2");
        char rcpath[PATH_MAX];
        snprintf(rcpath, sizeof(rcpath), "%s/etc", rootfs);
        mkdir(rcpath, 0755);
        snprintf(rcpath, sizeof(rcpath), "%s/etc/resolv.conf", rootfs);
        FILE *f = fopen(rcpath, "w");
        if (!f) {
            fprintf(stderr, "kuro-chroot: write %s: %s\n", rcpath, strerror(errno));
            return -1;
        }
        if (!d1 || !*d1) d1 = "1.1.1.1";
        if (!d2 || !*d2) d2 = "8.8.8.8";
        fprintf(f, "nameserver %s\nnameserver %s\n", d1, d2);
        fclose(f);
    }
    if (rc == 0) printf("kuro-chroot: mounted %s\n", rootfs);
    return rc;
}

static int cmd_umount(const char *rootfs) {
    /* reverse order, lazy detach so busy mounts still release */
    static const char *const subs[] = {"tmp", "storage", "sdcard", "sys", "proc", "dev"};
    char dst[PATH_MAX];
    int rc = 0;
    size_t i;
    for (i = 0; i < sizeof(subs) / sizeof(subs[0]); i++) {
        snprintf(dst, sizeof(dst), "%s/%s", rootfs, subs[i]);
        if (umount2(dst, MNT_DETACH) != 0 && errno != EINVAL) {
            fprintf(stderr, "kuro-chroot: umount %s: %s\n", dst, strerror(errno));
            rc = -1;
        }
    }
    if (rc == 0) printf("kuro-chroot: unmounted %s\n", rootfs);
    return rc;
}

static int cmd_enter(const char *rootfs, int argc, char **argv) {
    if (chroot(rootfs) != 0) {
        fprintf(stderr, "kuro-chroot: chroot %s: %s\n", rootfs, strerror(errno));
        return 1;
    }
    if (chdir("/") != 0) {
        fprintf(stderr, "kuro-chroot: chdir /: %s\n", strerror(errno));
        return 1;
    }
    execvp(argv[0], argv);
    fprintf(stderr, "kuro-chroot: exec %s: %s\n", argv[0], strerror(errno));
    return 1;
}

static int cmd_status(const char *rootfs) {
    FILE *f = fopen("/proc/mounts", "r");
    char line[4096];
    int mounted = 0;
    if (f) {
        while (fgets(line, sizeof(line), f)) {
            char mnt[PATH_MAX];
            /* /proc/mounts columns: device mntpoint fstype ... (spaces escaped) */
            if (sscanf(line, "%*s %4095s", mnt) == 1) {
                size_t rl = strlen(rootfs);
                if (strncmp(mnt, rootfs, rl) == 0 &&
                    (mnt[rl] == '\0' || mnt[rl] == '/')) {
                    mounted = 1;
                    break;
                }
            }
        }
        fclose(f);
    }
    printf("%s\n", mounted ? "mounted" : "not-mounted");
    return 0;
}

static int usage(void) {
    fprintf(stderr, "usage: kuro-chroot {version|mount <rootfs>|umount <rootfs>|enter <rootfs> <cmd...>|status <rootfs>}\n");
    return 2;
}

int main(int argc, char **argv) {
    if (argc < 2) return usage();
    if (strcmp(argv[1], "version") == 0) {
        printf("%s\n", KURO_VERSION);
        return 0;
    }
    if (strcmp(argv[1], "mount") == 0 && argc == 3) return cmd_mount(argv[2]) == 0 ? 0 : 1;
    if (strcmp(argv[1], "umount") == 0 && argc == 3) return cmd_umount(argv[2]) == 0 ? 0 : 1;
    if (strcmp(argv[1], "status") == 0 && argc == 3) return cmd_status(argv[2]);
    if (strcmp(argv[1], "enter") == 0 && argc >= 4) return cmd_enter(argv[2], argc - 3, &argv[3]);
    return usage();
}
