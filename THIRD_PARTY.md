# Third-Party Components and Attribution

Trierarch is a shell around other people's work. This file records the
third-party source code, binaries and data that Trierarch uses, bundles or runs,
with the upstream revision and license for each component.

It complements [LICENSE](LICENSE) (Trierarch's GPL-3.0 license) and
[NOTICE](NOTICE) (the short redistribution notice). Copyright and license
notices remain with the upstream authors.

## 1. Bundled or redistributed in the app

These components are built into, packaged with, or executed by the Android app.
PRoot and the DroidSpaces supervisor are distributed as separate executables,
not linked into the app.

| Component | Upstream / pinned revision | Used for | License |
| --- | --- | --- | --- |
| **Termux terminal** | [termux/termux-app](https://github.com/termux/termux-app/tree/3df69d1da197dd9bd71a3bafd902dffd720576b4), commit `3df69d1da197dd9bd71a3bafd902dffd720576b4` | `com.termux.terminal`, `com.termux.view`: terminal emulation, rendering, input, scrolling and selection | Termux repository is GPL-3.0; `terminal-emulator` and `terminal-view` are documented by Termux as Apache-2.0 with the upstream exception; preserve their notices |
| **Termux:X11 / Lorie** | [termux/termux-x11](https://github.com/termux/termux-x11/tree/9471ad977d21b7c7bec45117008f74f330d45983), commit `9471ad977d21b7c7bec45117008f74f330d45983` | `libXlorie.so`, X11 server and XKB runtime asset | GPL-3.0-or-later |
| **PRoot** | [termux/proot](https://github.com/termux/proot/tree/a89b3732ec6ae1db674510f0843b2f3db54d0a2f), commit `a89b3732ec6ae1db674510f0843b2f3db54d0a2f` | `proot` and `loader` executables for the PRoot runtime | GPL-2.0-only |
| **talloc** | `2.4.3`, see `trierarch-packages/proot/UPSTREAM.md` | PRoot build dependency | LGPL-3.0-or-later |
| **libandroid-shmem** | `0.7`, see `trierarch-packages/proot/UPSTREAM.md` | PRoot build dependency | BSD-3-Clause |
| **virglrenderer** | [virgl/virglrenderer](https://gitlab.freedesktop.org/virgl/virglrenderer), `1.3.0` | Android vtest/VirGL host server | MIT |
| **libepoxy** | [anholt/libepoxy](https://github.com/anholt/libepoxy), `1.5.10` | EGL/GL function dispatch | MIT |
| **ANGLE** | [google/angle](https://chromium.googlesource.com/angle/angle), Termux package `2.1.24923-f09a19ce-2` | Vulkan/GL backends used by the VirGL host | BSD-3-Clause |
| **PipeWire** | [pipewire/pipewire](https://gitlab.freedesktop.org/pipewire/pipewire), `1.4.9-1` | Android audio server | MIT |
| **WirePlumber** | [pipewire/wireplumber](https://gitlab.freedesktop.org/pipewire/wireplumber), `0.5.13` | Session and policy manager | MIT |
| **Local Desktop AAudio sink** | [localdesktop](https://github.com/localdesktop/localdesktop.github.io/tree/0543368d3262e098b92ff0305e149ff0d1fce3e1), commit `0543368d...` | AAudio-backed `Audio/Sink` | GPL-3.0 |
| **Wayland** | [wayland](https://gitlab.freedesktop.org/wayland/wayland) | Wayland server/library | MIT |
| **wayland-protocols** | [wayland/wayland-protocols](https://gitlab.freedesktop.org/wayland/wayland-protocols) | Protocol definitions | MIT |
| **libffi** | [libffi/libffi](https://github.com/libffi/libffi) | Wayland server support | MIT |
| **X.Org XKB data** | XKeyboardConfig / X.Org XKB | XKB rules bundled for Lorie | X11-style; retain upstream notices |
| **DroidSpaces owner supervisor** | Trierarch-owned code | Stops Trierarch-started containers when the app dies | GPL-3.0 (Trierarch) |

## 2. Guest-side software supplied by the user

These are not reproducibly built into the Android APK. They are installed in the
rootfs/container or provided by the device:

| Component | Where it comes from | License |
| --- | --- | --- |
| **KDE Plasma / KWin** | Guest distribution | GPL-2.0-or-later / LGPL where applicable |
| **Hyprland** | Guest distribution | BSD-3-Clause |
| **aquamarine** | Guest distribution | BSD-3-Clause |
| **XFCE and other desktops** | Guest distribution | Their upstream licenses |
| **DroidSpaces** | External runtime installed by the user | GPL-3.0 |
| **AndroidX / Kotlin runtime** | Android/JetBrains toolchain | Apache-2.0 |

Guest distributions and their packages keep their own licenses. Installing a
rootfs or container does not relicense it under Trierarch's GPL-3.0.

### Mesa derivative chain

Mesa is not a single “MIT” component. Trierarch's Adreno Mesa recipe is a
downstream of several layers, and the current PKGBUILD license expression is:

```text
MIT AND BSD-3-Clause AND SGI-B-2.0
```

Individual Mesa files keep their own copyright and license notices; the
PKGBUILD expression is not a replacement for those notices.

| Layer | Source / pinned reference | What it contributes | License |
| --- | --- | --- | --- |
| **Mesa 3D upstream** | [mesa/mesa](https://gitlab.freedesktop.org/mesa/mesa) | Mesa source tree | Per-file: MIT, BSD-3-Clause, SGI-B-2.0 and others where marked |
| **lfdevs Mesa fork** | [lfdevs/mesa-for-android-container](https://github.com/lfdevs/mesa-for-android-container), tag `mesa-27.0.0-devel-20261025-r1` (reference inspected 2026-10-30) | Android-container/Freedreno changes | Mesa file headers; no single root license |
| **lfdevs packaging** | [lfdevs/archlinuxarm-PKGBUILDs](https://github.com/lfdevs/archlinuxarm-PKGBUILDs), branch `freedreno`, commit `8a29bd2e748463663852eec750b4591a36728dae` | PKGBUILD and packaging | No top-level license file found; PKGBUILD `license` field is `MIT AND BSD-3-Clause AND SGI-B-2.0` |
| **Trierarch mesa-adreno** | This repository: `trierarch-packages/mesa-adreno/` | Arch build recipe; Debian/Kali patch queue with the KGSL Wayland and build-option changes | Packages repository GPL-3.0 unless a file says otherwise; upstream Mesa/ifdevs notices are retained |

The Debian/Kali recipe adds the ifdevs Freedreno changes and Trierarch's
KGSL/Wayland adjustment on top of Kali's Mesa source package (`mesa_26.1.6-1`).
The Arch recipe pins the ifdevs `freedreno` packaging commit
`8a29bd2e...` by default; `MESA_PKGBUILD_COMMIT` overrides it. The exact commit
and Mesa tag must still be recorded for each released binary. See
`trierarch-packages/mesa-adreno/UPSTREAM.md` for the full chain.

## 3. Trierarch patch series

Trierarch does not vendor most upstream source trees. The reproducible inputs
and patches are kept in `trierarch-packages`:

- Termux terminal patches: `trierarch-app/patches/termux-terminal/`;
- Termux:X11/Lorie patches: `trierarch-packages/x11-host/patches/`;
- PRoot patches: `trierarch-packages/proot/patches/`;
- virglrenderer and libepoxy patches: `trierarch-packages/virgl-host/patches/`;
- guest Mesa build recipe: `trierarch-packages/mesa-adreno/`.

Each package's `UPSTREAM.md` or README records its pinned revision and patch
series. The Android app source is in this repository.

## 4. Source availability

Trierarch is licensed under GPL-3.0; see [LICENSE](LICENSE). The corresponding
source for Trierarch itself is this repository and the companion
`trierarch-packages` repository.

For the copyleft components above, the corresponding source is available from
their upstream repositories at the pinned revisions and from Trierarch's patch
series. If you redistribute a Trierarch binary, provide the corresponding source
and preserve all upstream copyright, license and NOTICE material, or provide a
written offer valid under the applicable license.

This file is an attribution map, not legal advice. If your distribution model
differs, review the exact upstream licenses before shipping.

## 5. Trademarks

Project names, logos and trademarks belong to their respective owners. Trierarch
does not claim ownership of Termux, Termux:X11, PRoot, Mesa, virglrenderer,
ANGLE, PipeWire, WirePlumber, KDE, Hyprland or any other upstream project.
