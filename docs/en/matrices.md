# Compatibility Matrix

English | [中文](../zh-CN/matrices.md)

Last updated: 2026-11-09

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
| **PRoot** | No | Depends on the compositor and required patches; not a complete Linux environment. KDE Plasma Wayland is a **Developer Preview** with compatibility components | Host **VirGL** via `virpipe`; no `/dev/dri` required |

### Wayland hardware acceleration

Wayland is the primary desktop path: it is what enables Plasma/Hyprland effects.
Its GPU path depends on the runtime:

| Runtime | Wayland GPU path | Status |
| --- | --- | --- |
| **DroidSpaces** | Adreno direct (freedreno/kgsl) | ✅ tested; effects usable |
| **chroot** | Adreno direct (freedreno/kgsl) | ✅ tested; effects usable |
| **PRoot** | Depends on the compositor and patches; Plasma Adreno is a Developer Preview | ⚠️ configuration-dependent; see the tested rows below |

PRoot is not a complete Linux environment. Running a desktop or window manager
inside it has significant limitations, and many paths need extra compatibility
patches or assets. The rows below record what has actually been tested; they do
not mean every desktop is impossible, nor that every configuration will work.

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

PRoot is not a complete Linux environment, so desktop and window-manager support
is limited and often requires extra patches. The table records tested
configurations; the absence of a result is not a claim that a desktop can never
work.

| Desktop / display | Renderer | Result |
| --- | --- | --- |
| **Sway** (Wayland) | llvmpipe / SHM / `pixman` | ✅ tested; software rendering |
| **Sway** (Wayland) | virgl profile with `buffer = "shm"` and `pixman` | ✅ tested; software rendering |
| **KDE Plasma Wayland** | adreno with compat components | 🧪 Developer Preview; frame output verified with matching KWin + guest GPU userspace stack |
| **KDE Plasma Wayland** | llvmpipe / software | ⚠️ legacy configuration; GPU path not used |
| **Hyprland** | adreno | ❌ failed in the tested configuration; see the preview notes |
| **KDE Plasma X11** | llvmpipe / VirGL | ❌ tested profile failed on the current KWin/Plasma ABI; a matching X11 session is required |

The preview procedure and current limits are in
[PRoot + Adreno + Plasma Wayland](developer-preview/proot/wayland-adreno.md).

The Plasma X11 result is a package/ABI mismatch (`kwin_x11` 6.7.5 with Plasma
6.8.2), not proof that X11 or VirGL can never work; a matching X11 session or
XFCE is required.

### PRoot constraints

PRoot does not provide a complete Linux environment. On unrooted Android,
Android SELinux denies the app UID access to:

```text
/dev/dri/renderD128
/dev/dri/card0
```

`/dev/kgsl-3d0` may be available, but KWin, Aquamarine and other compositors can
still require a DRM/GBM device and dmabuf feedback. This is why some tested
desktop paths need compatibility components, patches or fall back to software
rendering.

Tested examples:

- **Sway** can run with `WLR_RENDERER=pixman` and SHM; software rendering.
  `WLR_RENDERER=gles2` can fail with
  `Cannot create GLES2 renderer: no DRM FD available`.
- **KDE Plasma Wayland** is a developer preview with the compatibility assets;
  without them, the tested KWin version reports:
  ```text
  kwin_core: Configured compositor not supported by Platform. Falling back to defaults
  kwin_core: The used windowing system requires compositing
  kwin_core: We are going to quit KWin now as it is broken
  ```
- **Hyprland** failed in the tested Aquamarine configuration; newer components
  or patches may change that result.
- A dmabuf device report can fail with `open /dev/dri: Permission denied`, so the
  host may not publish the dmabuf path a GPU compositor needs.

These are current limitations, not a universal compatibility list. Use the
matrix as the tested record and the developer-preview documents for work in
progress.

## Notes

- These results are from one Adreno 740 device. They are not a guarantee for
  every device with the same GPU or every Android build.
- Root changes the available feature set: DroidSpaces and chroot can use Adreno
  directly; PRoot is an incomplete Linux environment and some paths need extra
  patches or compatibility components.
- `—` means “not tested”, not “does not work”. Desktop results are
  configuration-dependent; a missing result should not be read as a failure.
- DroidSpaces containers must already exist; Trierarch starts and manages the
  ones it owns.
