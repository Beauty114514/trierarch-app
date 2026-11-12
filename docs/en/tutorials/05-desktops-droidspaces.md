# Chapter 5C — Desktops on DroidSpaces

English | [中文](../../zh-CN/tutorials/05-desktops-droidspaces.md)

DroidSpaces starts an existing container, so the desktop is installed inside that
container rather than in the app's rootfs.

Start the container and install the desktop:

```sh
pacman -S --needed plasma-meta
pacman -S --needed hyprland
pacman -S --needed xfce4 xfce4-goodies
```

Verify the launcher inside the container:

```sh
command -v startplasma-wayland
command -v start-hyprland
command -v startxfce4
```

## KDE Plasma Wayland

Use the real DRM/KGSL path from [Chapter 4](04-hardware-acceleration.md). Start
Plasma with:

```toml
[launch]
argv = ["env", "DISABLE_RTKIT=1", "dbus-run-session", "--", "startplasma-wayland"]
```

No KWin compatibility library is needed.

Use the sample profile:

- [`arch-droidspaces-plasma-wayland-adreno.toml`](../../profiles/arch-droidspaces-plasma-wayland-adreno.toml)

## Hyprland

Use the normal `hyprland` package and the real DRM/KGSL path. Check the
Aquamarine version if the session opens without a visible output.

Use the sample profile:

- [`arch-droidspaces-hyprland-wayland-adreno.toml`](../../profiles/arch-droidspaces-hyprland-wayland-adreno.toml)

## XFCE

Use the normal X11 setup from [Chapter 4](04-hardware-acceleration.md).

## Notes

- the container must already exist; Trierarch starts or attaches to it;
- `container` and `user` are top-level profile fields;
- PipeWire audio is enabled with `audio.backend = "pipewire"`;
- tested combinations are in the [compatibility matrix](../matrices.md).

Next: [Chapter 6 — Run and choose a sample](06-run.md).
