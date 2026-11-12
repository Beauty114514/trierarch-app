# Chapter 4 — Hardware acceleration assets

English | [中文](../../zh-CN/tutorials/04-hardware-acceleration.md)

Choose the renderer before installing a desktop. Most desktop failures happen
because the renderer, guest drivers, and display transport do not match.

## Choose a path

| Path | What runs on the GPU | Extra guest assets |
| --- | --- | --- |
| `llvmpipe` | CPU | none |
| `virgl` | host VirGL/vtest | none for the compositor; distro Mesa for apps |
| `adreno` + Freedreno | guest Adreno/KGSL for OpenGL, Turnip for Vulkan | Mesa + Turnip userspace |

`llvmpipe` is the correct first test. `virgl` is the simplest accelerated path
for a rooted or unrooted compositor.

`adreno` is one renderer. For DroidSpaces and chroot, the supported OpenGL
frontend is Freedreno; Vulkan applications use Turnip.

## Runtime differences

| Runtime | Adreno device access | KWin compatibility patch |
| --- | --- | --- |
| PRoot | Trierarch synthesizes a fake render node | required for Plasma Wayland |
| chroot | real `/dev/dri` via root | no |
| DroidSpaces | real `/dev/dri` inside the container | no |

The KWin patch is not a GPU driver. It is a Wayland render-node compatibility
change for the PRoot fake-DRM path; it is covered in
[Chapter 5A](05-desktops-proot.md).

## Install the Trierarch release drivers

Use the pinned Mesa/Turnip assets from the Trierarch release. Do not replace
them with arbitrary distro Mesa packages.

| Runtime / desktop | Release asset |
| --- | --- |
| DroidSpaces / chroot (Arch guest) | `mesa-27.0.0-91-root.tar.xz` |
| PRoot, any desktop | `mesa-26.0-noroot.tar.zst` |
| PRoot + KDE Plasma Wayland only | `kwin-compat-6.8.2-2-archlinuxarm-aarch64.tar.zst` |

Freedreno is the OpenGL frontend inside the Mesa asset. Turnip is the Vulkan
ICD included with the DroidSpaces/chroot release asset.

### DroidSpaces or chroot, Arch guest

Run inside the Arch guest as root:

```sh
tar -xf mesa-27.0.0-91-root.tar.xz
cd mesa-27.0.0-packages
cp /etc/pacman.conf /tmp/pacman-release.conf
sed -i 's/^SigLevel.*/SigLevel = Never/' /tmp/pacman-release.conf
pacman --config /tmp/pacman-release.conf -U --noconfirm ./*.pkg.tar.xz
rm -f /tmp/pacman-release.conf
```

The release packages are local unsigned packages. Use the temporary config only
for this transaction; do not weaken the permanent `/etc/pacman.conf`.

This installs the release Mesa and Turnip packages into the guest. Do not mix
them with a different Mesa or Turnip build.

### PRoot Mesa

This part is required for any accelerated PRoot desktop:

```sh
mkdir -p /opt/trierarch/gpu/mesa-26.0
tar --zstd -xf mesa-26.0-noroot.tar.zst \
  --strip-components=1 -C /opt/trierarch/gpu/mesa-26.0
```

Run this inside the PRoot guest, not from `adb shell`; Android's toybox tar has
no usable zstd. The complete Zink + Turnip and Plasma profile setup is in
[Chapter 5A](05-desktops-proot.md).

### PRoot KWin compatibility: KDE Plasma Wayland only

Do **not** install this for Hyprland, XFCE, X11, or terminal-only profiles. It is
only needed when PRoot runs KDE Plasma Wayland:

```sh
mkdir -p /tmp/kwin-compat /opt/trierarch/kwin/6.8.2/compat
tar --zstd -xf kwin-compat-6.8.2-2-archlinuxarm-aarch64.tar.zst \
  -C /tmp/kwin-compat
install -m 755 /tmp/kwin-compat/kwin-compat/libkwin-6.8.2-2-archlinuxarm-aarch64.so \
  /opt/trierarch/kwin/6.8.2/compat/libkwin.so.6.8.2
ln -sfn libkwin.so.6.8.2 /opt/trierarch/kwin/6.8.2/compat/libkwin.so.6
ln -sfn libkwin.so.6 /opt/trierarch/kwin/6.8.2/compat/libkwin.so
```

### Verify

```sh
find /usr/share/vulkan/icd.d -iname '*freedreno*'
eglinfo -B | grep -i 'OpenGL core profile renderer'
vulkaninfo --summary | grep -i turnip
```

Freedreno should report an Adreno renderer, and Vulkan should report Turnip. If
`eglinfo` does not exist, install `mesa-utils` for the test only.

## DroidSpaces and chroot: Freedreno + Turnip

Both use the same KGSL device and the same Mesa/Turnip installation. For these
runtimes, the supported OpenGL frontend is Freedreno; Vulkan applications use
Turnip.

### Freedreno + Turnip

OpenGL/EGL goes directly through Mesa's Freedreno Gallium driver. Vulkan
applications use Turnip.

Profile launch environment:

```sh
export GALLIUM_DRIVER=freedreno
export MESA_LOADER_DRIVER_OVERRIDE=kgsl
```

Keep the KGSL hints:

```text
TURNIP_KMD=kgsl
FD_FORCE_KGSL=1
```

Trierarch injects them for `graphics.renderer = "adreno"`; do not remove them.

Point Vulkan at the matching Freedreno/Turnip ICD:

```sh
export VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json
export VK_DRIVER_FILES=$VK_ICD_FILENAMES
```

The filename differs on some distributions. Locate it with:

```sh
find /usr/share/vulkan/icd.d -iname '*freedreno*'
```

Do not mix an arbitrary Mesa build with an arbitrary Turnip build.

## Sample profiles
## Sample profiles

- [Freedreno sample](../../profiles/arch-droidspaces-plasma-wayland-adreno.toml)

For chroot, use the same environment in the chroot profile. The sample

```text
kali-chroot-plasma-wayland-adreno.toml
```

shows the Freedreno variant.

## PRoot guest GPU component

PRoot cannot open a real render node, so its profile uses the Trierarch Mesa
component plus the KWin compatibility library. The component and the
`kwin-compat` library must stay pinned together; see
[Chapter 5A](05-desktops-proot.md).

The Mesa/Turnip pairing used by PRoot is not interchangeable with an arbitrary
distro Mesa package.

## PRoot Plasma compatibility

PRoot Plasma Wayland also needs the KWin compatibility library. Its build,
installation and profile environment are described in
[Chapter 5A](05-desktops-proot.md).

Next: [Chapter 5 — Desktops](05-desktops.md).
