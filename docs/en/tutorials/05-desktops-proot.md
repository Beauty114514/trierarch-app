# Chapter 5A — Desktops on PRoot

English | [中文](../../zh-CN/tutorials/05-desktops-proot.md)

PRoot runs without root, but it cannot open a real DRM render node. GPU desktops
therefore need the compatibility path described here.

Install the desktop inside the guest first:

```sh
pacman -S --needed plasma-meta
pacman -S --needed xfce4 xfce4-goodies
```

Verify the launcher:

```sh
command -v startplasma-wayland
command -v startxfce4
```

## KDE Plasma Wayland

Plasma on PRoot additionally needs:

```text
KWin compatibility library  → kwin-compat
Adreno guest userspace      → Mesa + matching Turnip/ICD
```

The 0.5.0 release provides:

```text
kwin-compat-6.8.2-2-archlinuxarm-aarch64.tar.zst
mesa-26.0-noroot.tar.zst
```

Install the compatibility library outside `/usr/lib`:

```sh
install -d /opt/trierarch/kwin/6.8.2/compat
install -m 755 \
  ./kwin-compat/libkwin-6.8.2-2-archlinuxarm-aarch64.so \
  /opt/trierarch/kwin/6.8.2/compat/libkwin.so.6.8.2
ln -sfn libkwin.so.6.8.2 /opt/trierarch/kwin/6.8.2/compat/libkwin.so.6
ln -sfn libkwin.so.6 /opt/trierarch/kwin/6.8.2/compat/libkwin.so
```

Do not overwrite `/usr/lib/libkwin.so.6.8.2`. The profile must load the compat
directory through `LD_LIBRARY_PATH`; the sample already does this.

Install the guest GPU component from the release using
[Chapter 4](04-hardware-acceleration.md), then use the sample profile:

- [`arch-proot-plasma-wayland-adreno.toml`](../../profiles/arch-proot-plasma-wayland-adreno.toml)

For source builds, use:

```text
trierarch-packages/kwin-compat/build-arch.sh
trierarch-packages/kwin-compat/patches/0001-wayland-render-node.patch
```

## XFCE

Use X11 with the graphics path from [Chapter 4](04-hardware-acceleration.md).
No KWin compatibility library is needed.

## Limits

- PRoot cannot open `/dev/dri/renderD128` or `/dev/dri/card0`;
- Adreno Plasma needs the compatibility components and a matching KWin build;
- sound uses the normal `audio.backend = "pipewire"` profile setting;
- Hyprland is not part of the supported PRoot desktop path;
- other tested combinations are in the [compatibility matrix](../matrices.md).

Next: [Chapter 6 — Run and choose a sample](06-run.md).
