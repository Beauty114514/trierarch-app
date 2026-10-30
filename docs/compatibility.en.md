# Compatibility Matrix

Last updated: 2026-10-30

Test device: Adreno 740 (Qualcomm).

This round covers:

- **DroidSpaces — Arch** (rooted);
- **chroot — Kali** (rooted);
- **PRoot — Arch** (unrooted).

## Legend

| Mark | Meaning |
| --- | --- |
| ✅ tested | Tested and usable in this round |
| ✅ supported | The software path is supported, but this particular desktop/combination was not tested yet |
| ❌ failed | Tested and failed; the reason is recorded below |
| — | Not tested in this round |

## Runtime capability summary

| Runtime | Root | Wayland GPU path | X11 GPU path |
| --- | --- | --- | --- |
| **DroidSpaces** | Yes | Adreno direct; effects usable | Adreno direct; VirGL also available |
| **chroot** | Yes | Adreno direct; effects usable | Adreno direct; VirGL also available |
| **PRoot** | No | **Software only** in the tested configuration; KWin/Plasma/Hyprland cannot initialize a GPU compositor | Host **VirGL** via `virpipe`; no `/dev/dri` required |

### Wayland hardware acceleration

Wayland is the primary desktop path: it is what enables Plasma/Hyprland effects.
Its GPU path depends on the runtime:

| Runtime | Wayland GPU path | Status |
| --- | --- | --- |
| **DroidSpaces** | Adreno direct (freedreno/kgsl) | ✅ tested; effects usable |
| **chroot** | Adreno direct (freedreno/kgsl) | ✅ tested; effects usable |
| **PRoot** | Software only (pixman/SHM); no GPU compositor | ✅ tested as software; Plasma/Hyprland fail |

On unrooted PRoot, Android SELinux denies `/dev/dri`; KWin and Aquamarine cannot
initialize a GPU compositor. The PRoot Wayland session is therefore software-only.

### X11 hardware acceleration

X11 is the hardware-accelerated desktop path in all three runtimes, using the
same model as Termux's proot setup:

| Runtime | X11 GPU path | Status |
| --- | --- | --- |
| **DroidSpaces** | Adreno direct (freedreno/kgsl) | ✅ tested with KDE Plasma X11 |
| **chroot** | Adreno direct (freedreno/kgsl) | ✅ tested with KDE Plasma X11 |
| **PRoot** | Host VirGL (`virpipe`); the guest does not open `/dev/dri` | ✅ supported; desktop session test pending |

The PRoot path is deliberately host-side VirGL: applications render through
the host's VirGL/vtest stack, like Termux proot. It is not a direct Adreno
passthrough and does not need `/dev/dri` in the guest.

## DroidSpaces — Arch (rooted)

| Desktop | Adreno | VirGL | llvmpipe |
| --- | --- | --- | --- |
| KDE Plasma Wayland | ✅ effects | ✅ no effects | ✅ no effects |
| Hyprland | ✅ effects | — | — |
| XFCE Wayland | ✅ | ✅ | ✅ |
| KDE Plasma X11 | ✅ effects | ✅ no effects | ✅ no effects |
| XFCE X11 | ✅ | ✅ | ✅ |

## chroot — Kali (rooted)

| Desktop | Adreno | VirGL | llvmpipe |
| --- | --- | --- | --- |
| KDE Plasma Wayland | ✅ effects | ✅ no effects | ✅ no effects |
| XFCE Wayland | ✅ | ✅ | ✅ |
| KDE Plasma X11 | ✅ effects | ✅ no effects | ✅ no effects |
| XFCE X11 | ✅ | ✅ | ✅ |

Hyprland and other desktops were not tested under chroot/Kali in this round.

## PRoot — Arch (unrooted)

| Desktop / display | Renderer | Result |
| --- | --- | --- |
| **Sway** (Wayland) | llvmpipe / SHM / `pixman` | ✅ tested; software rendering |
| **Sway** (Wayland) | virgl profile with `buffer = "shm"` and `pixman` | ✅ tested; software rendering |
| **KDE Plasma Wayland** | adreno or llvmpipe | ❌ failed; see below |
| **Hyprland** | adreno | ❌ failed; see below |
| **KDE Plasma X11** | llvmpipe / VirGL | ❌ tested profile failed on the current KWin/Plasma ABI; X11 + VirGL remains the supported path once a matching X11 session is installed |

The Plasma X11 failure above is a package/ABI mismatch (`kwin_x11` 6.7.5 with
Plasma 6.8.2), not an X11 or VirGL limitation. A matching X11 session or XFCE
is required.

### Why PRoot Wayland is software-only

On an unrooted Android device, the app UID cannot open:

```text
/dev/dri/renderD128
/dev/dri/card0
```

Android SELinux denies both the app and the ADB shell. `/dev/kgsl-3d0` may be
available, but KWin and Aquamarine still need a DRM/GBM device and dmabuf
feedback to initialize their GPU compositor.

Consequences:

- **Sway** starts only with `WLR_RENDERER=pixman` and SHM; the desktop is
  software-rendered. `WLR_RENDERER=gles2` fails with
  `Cannot create GLES2 renderer: no DRM FD available`.
- **KDE Plasma Wayland** fails in KWin before presenting a frame. KWin 6.8.2
  reports:
  ```text
  kwin_core: Configured compositor not supported by Platform. Falling back to defaults
  kwin_core: The used windowing system requires compositing
  kwin_core: We are going to quit KWin now as it is broken
  ```
- **Hyprland** fails in Aquamarine: the DRM backend cannot open a seat, the
  Wayland backend reports `Missing protocols`, and there is no allocator.
  `CBackend::create()` then aborts.
- The dmabuf device report also fails with `open /dev/dri: Permission denied`,
  so the host never publishes the dmabuf path that a GPU compositor needs.

PRoot Wayland is therefore useful for a software Sway session, but it is not a
GPU desktop path on an unrooted device.

## Notes

- These results are from one Adreno 740 device. They are not a guarantee for
  every device with the same GPU or every Android build.
- Root changes the available feature set: DroidSpaces and chroot can use Adreno
  directly, while unrooted PRoot cannot.
- `—` means “not tested”, not “does not work”. Desktop results are
  configuration-dependent; a missing result should not be read as a failure.
- DroidSpaces containers must already exist; Trierarch starts and manages the
  ones it owns.
