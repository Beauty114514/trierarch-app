# Chapter 6 — Run and choose a sample

English | [中文](../../zh-CN/tutorials/06-run.md)

The profile is deployed, the fields are filled in, the GPU assets (if needed) are
installed, and the desktop is installed. Now create the profile, edit it, and run
it.

## Create and edit

```sh
trierarch --create arch-demo
trierarch --config arch-demo
trierarch --status arch-demo
```

Fill in the fields from [Chapter 3](03-profile-fields.md). The profile path is:

```text
/data/user/0/app.trierarch/files/config/profiles/arch-demo.toml
```

If `$EDITOR` is unavailable, edit that file directly.

## Run

```sh
trierarch --run arch-demo
```

Then:

```sh
trierarch --status arch-demo
```

Open the terminal, X11, or Wayland surface in Trierarch. A desktop session may
take longer on first start.

Stop it with:

```sh
trierarch --stop arch-demo
```

If you changed the profile, restart with:

```sh
trierarch --rerun arch-demo
```

## Sample profiles

The samples are starting points, not device-independent defaults. All use
Wayland and the Adreno-oriented path; change `id`, paths, container names, and
users for your device.

| Sample | Runtime | Desktop | Notes |
| --- | --- | --- | --- |
| [Arch PRoot Plasma](../../profiles/arch-proot-plasma-wayland-adreno.toml) | PRoot | KDE Plasma | KWin compat + fake DRM |
| [Arch DroidSpaces Plasma](../../profiles/arch-droidspaces-plasma-wayland-adreno.toml) | DroidSpaces | KDE Plasma | Adreno, existing container |
| [Arch DroidSpaces Hyprland](../../profiles/arch-droidspaces-hyprland-wayland-adreno.toml) | DroidSpaces | Hyprland | Adreno, existing container |
| [Kali chroot Plasma](../../profiles/kali-chroot-plasma-wayland-adreno.toml) | chroot | KDE Plasma | Adreno, chroot rootfs |

From a local checkout, the same files are under:

```text
docs/profiles/
```

## Sample notes

- [Arch PRoot Plasma profile](../../profiles/arch-proot-plasma-wayland-adreno.toml)
  is the reference for the compatibility-asset path.
- [Arch DroidSpaces Plasma profile](../../profiles/arch-droidspaces-plasma-wayland-adreno.toml)
  and [Arch DroidSpaces Hyprland profile](../../profiles/arch-droidspaces-hyprland-wayland-adreno.toml)
  are the phone's working Arch profiles, cleaned up as examples.
- [Kali chroot Plasma profile](../../profiles/kali-chroot-plasma-wayland-adreno.toml)
  preserves the Kali chroot layout; change `rootfs` to your own path.

Do not copy a sample without checking the absolute paths and runtime-specific
fields.
