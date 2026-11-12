# Chapter 2 — Deploy a runtime

English | [中文](../../zh-CN/tutorials/02-deploy.md)

Deploy the runtime before creating a profile. The three runtimes do not share one
installation procedure.

## PRoot — download and import an Arch rootfs

Trierarch does not bundle a Linux rootfs. Download an Arch Linux `arm64` rootfs
from the Linux Containers image server:

- [Linux Containers images](https://images.linuxcontainers.org/)
- [Arch Linux, current, arm64, default](https://images.linuxcontainers.org/images/archlinux/current/arm64/default/)

Choose the latest date directory and download its `rootfs.tar.xz`. The archive
is the rootfs tarball Trierarch imports; do not use a desktop image or a
different architecture.

Then push it to a location the app can read, for example the shared Download
directory:

```sh
adb push rootfs.tar.xz /sdcard/Download/arch-rootfs.tar.xz
```

In the Trierarch shell, import it:

```sh
trierarch --import /sdcard/Download/arch-rootfs.tar.xz --name arch
```

The command prints the installed path. Record it for the profile:

```text
/data/user/0/app.trierarch/files/rootfs/arch
```

If the app cannot read the shared Download path on your Android version, put the
archive in an app-readable directory first, then use that absolute path.

Remove an imported environment with:

```sh
trierarch --remove arch
```

PRoot is now deployed. It does not need root, chroot, or DroidSpaces.

## chroot — prepare a rootfs with root

chroot needs a rooted device with a working `su` provider and an existing Linux
rootfs on the device.

Use the upstream chroot guide for preparing the rootfs:

- [ArchWiki: Chroot](https://wiki.archlinux.org/title/Chroot)

The profile's `rootfs` is the chroot directory, for example:

```text
/data/local/my-rootfs
```

Trierarch starts and supervises the chroot session. It does not create or
populate the rootfs.

## DroidSpaces — deploy the container first

DroidSpaces is an external container runtime. Install it and create the container
before involving Trierarch:

- [DroidSpaces-OSS](https://github.com/ravindu644/Droidspaces-OSS)

Follow its installation and container-creation instructions. Record:

- the container name, for example `Arch`;
- the user inside the container, for example `beauty`;
- the desktop launcher installed in the container.

Trierarch starts a container that already exists. It does not create the
container.

## Deployment check

| Runtime | You should now have |
| --- | --- |
| PRoot | imported rootfs path |
| chroot | rootfs directory + working `su` |
| DroidSpaces | existing container + user |

Next: [Chapter 3 — Profile fields](03-profile-fields.md).
