# Chapter 5B — Desktops on chroot

English | [中文](../../zh-CN/tutorials/05-desktops-chroot.md)

A chroot has real DRM/KGSL access, so GPU desktops do not need the PRoot
compatibility library.

Install the desktop inside the chroot rootfs:

```sh
pacman -S --needed plasma-meta
pacman -S --needed hyprland
pacman -S --needed xfce4 xfce4-goodies
```

Verify the launcher:

```sh
command -v startplasma-wayland
command -v start-hyprland
command -v startxfce4
```

## KDE Plasma Wayland

Use the real GPU path from [Chapter 4](04-hardware-acceleration.md). Start Plasma
with:

```toml
[launch]
argv = ["env", "DISABLE_RTKIT=1", "dbus-run-session", "--", "startplasma-wayland"]
```

The `DISABLE_RTKIT=1` setting avoids the realtime-portal black screen described in
[PipeWire/RTKit](../known-issues/pipewire-rtkit.md).

Use the sample profile:

- [`kali-chroot-plasma-wayland-adreno.toml`](../../profiles/kali-chroot-plasma-wayland-adreno.toml)

## Hyprland

Install the normal `hyprland` package and use the real DRM/KGSL path. If the
session opens without a visible output, check the Aquamarine version:

```sh
pacman -Q aquamarine hyprland
```

An older Aquamarine can fall back to a headless output. Rebuild it only when the
package is older than the needed fix.

## XFCE

Use the normal X11 setup from [Chapter 4](04-hardware-acceleration.md). No KWin
compatibility library is needed.

## Notes

- chroot requires root and a working `su`;
- the rootfs must contain the desktop and GPU userspace;
- PipeWire audio is enabled with `audio.backend = "pipewire"`;
- tested combinations are in the [compatibility matrix](../matrices.md).

Next: [Chapter 6 — Run and choose a sample](06-run.md).
