# Compatibility Matrix

Last updated: 2026-10-30

Test device: Adreno 740 (Qualcomm).

This round covers only:

- **DroidSpaces — Arch**;
- **chroot — Kali**.


## Legend

| Mark | Meaning |
| --- | --- |
| ✅ effects | Desktop launches, is usable, and compositor effects are enabled |
| ✅ no effects | Desktop launches and is usable, but effects are unavailable or disabled |
| ✅ | Desktop launches and is usable; effects were not part of this record |
| — | Not tested in this round |

## DroidSpaces — Arch

| Desktop | Adreno | VirGL | llvmpipe |
| --- | --- | --- | --- |
| KDE Plasma Wayland | ✅ effects | ✅ no effects | ✅ no effects |
| KDE Plasma X11 | ✅ effects | ✅ no effects | ✅ no effects |
| Hyprland | ✅ effects | — | — |
| XFCE Wayland | ✅ | ✅ | ✅ |
| XFCE X11 | ✅ | ✅ | ✅ |

## chroot — Kali

| Desktop | Adreno | VirGL | llvmpipe |
| --- | --- | --- | --- |
| KDE Plasma Wayland | ✅ effects | ✅ no effects | ✅ no effects |
| KDE Plasma X11 | ✅ effects | ✅ no effects | ✅ no effects |
| XFCE Wayland | ✅ | ✅ | ✅ |
| XFCE X11 | ✅ | ✅ | ✅ |

Hyprland and other desktops were not tested under chroot/Kali in this round.

## Notes

- These results are from one Adreno 740 device. They are not a guarantee for
  every device with the same GPU or every Android build.
- “Effects” means the desktop's normal compositor effects are enabled and
  usable. On VirGL/llvmpipe, KDE Plasma currently runs without effects in this
  test set.
- `—` means “not tested”, not “does not work”. Desktop results are
  configuration-dependent; a missing result should not be read as a failure.
- DroidSpaces containers must already exist; Trierarch starts and manages the
  ones it owns.
