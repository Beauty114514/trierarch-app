# Chapter 5A — Desktops on PRoot

English | [中文](../../zh-CN/tutorials/05-desktops-proot.md)

PRoot runs without root, but it cannot open a real DRM render node. GPU desktops
therefore use Trierarch's compatibility path:

```text
fake render node
+ Trierarch Mesa component
+ Turnip
+ desktop-specific compatibility library
```

For PRoot Plasma Wayland and Hyprland, OpenGL runs through **Zink**, and Zink
runs on **Turnip**. Plasma additionally needs the KWin compatibility library;
Hyprland uses the aquamarine-compat library.

## Common prerequisites: Mesa 26 + Turnip

Use this release asset for every accelerated PRoot desktop:

```text
mesa-26.0-noroot.tar.zst
```

It contains the Mesa guest userspace and the matching Turnip ICD.

### Extract inside the PRoot guest

Do this inside the guest, from the `arch-shell` profile or a guest terminal.
Do **not** run these commands from `adb shell`: Android's toybox `tar` has no
usable zstd support.

Place the archive in a guest-visible directory:

```text
/opt/trierarch/tmp/
```

Create the install directory and extract:

```sh
install -d /opt/trierarch/gpu
rm -rf /opt/trierarch/gpu/mesa-26.0
mkdir -p /opt/trierarch/gpu/mesa-26.0

tar --zstd -xf /opt/trierarch/tmp/mesa-26.0-noroot.tar.zst \
  --strip-components=1 -C /opt/trierarch/gpu/mesa-26.0
```

Verify the important files:

```sh
ls -l /opt/trierarch/gpu/mesa-26.0/usr/lib/aarch64-linux-gnu/libgallium-*.so
ls -l /opt/trierarch/gpu/mesa-26.0/usr/lib/aarch64-linux-gnu/libvulkan_freedreno.so
```

The `TRIERARCH_MESA`, `LIBGL_DRIVERS_PATH`, `GBM_BACKENDS_PATH`,
`__EGL_VENDOR_LIBRARY_FILENAMES` and `VK_ICD_FILENAMES` paths in the profiles
must point at this extracted component.

## Choose a desktop

- [KDE Plasma Wayland](05-desktops-proot-kde.md) — KWin compatibility library
- [Hyprland](05-desktops-proot-hyprland.md) — aquamarine-compat and wallpaper setup

## XFCE

XFCE uses the X11 and VirGL path from
[Chapter 4](04-hardware-acceleration.md). It does not need the KWin or
aquamarine compatibility libraries.

## Limits

- PRoot cannot open `/dev/dri/renderD128` or `/dev/dri/card0`;
- Adreno Plasma needs the KWin compatibility components and a matching KWin build;
- Hyprland needs `libaquamarine-compat.so`; treat this newer path as experimental;
- sound uses the normal `audio.backend = "pipewire"` profile setting;
- other tested combinations are in the [compatibility matrix](../matrices.md).

Next: [Chapter 6 — Run and choose a sample](06-run.md).
