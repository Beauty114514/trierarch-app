# Trierarch

English | [中文](README.zh-CN.md)

### The all-in-one Linux workstation in your pocket.

Trierarch turns an Android phone or tablet into a local Linux machine. It gives
you a real terminal, X11 and Wayland desktops, and three ways to run a Linux
userspace: PRoot, chroot, and DroidSpaces containers.

No virtual machine. No CPU emulation. No cloud desktop. The Linux environment
runs on the device you are already holding, and Trierarch handles the parts
that normally make that experience painful: display surfaces, input, audio,
GPU paths, and session cleanup.

## A Trierarch remake

This repository is the **current, rewritten Trierarch**. The earlier Trierarch
version has been rewritten: its old codebase and early optimization notes are
not the current implementation. The Android app, native runtimes, packaging and
session model in this repository are the new version. Early workarounds are
kept only in the known-issues notes for older environments.

---

## Why choose Trierarch

Many ways to run Linux on Android stop at a terminal or, at best, X11.
Trierarch's biggest difference is that it also gives you a real Wayland
session: a nested compositor such as KDE Plasma (KWin) or Hyprland, with its
native effects, animations and window management.

That is the capability this project is built around. Shell and X11 are
supported too, but they are not the reason Trierarch exists.

On top of that, Trierarch turns the usual pile of scripts, sockets, environment
variables and half-configured desktops into a managed session:

- start a runtime from a profile;
- attach a terminal, an X11 display, or a Wayland desktop;
- forward touch, keyboard, mouse and IME input;
- route audio through PipeWire;
- stop what was started when the session ends.

It is built for people who want a small, local Linux machine on a device they
already carry — not a remote desktop and not a toy terminal.

## What you can run

| Runtime | Root | Best for |
| --- | --- | --- |
| **PRoot** | No | A Linux userspace without root, kept inside the app's private storage |
| **chroot** | Yes | A full rootfs on the device, with a session supervisor that cleans up after the app |
| **DroidSpaces** | Yes | Containers with their own init/systemd, started and stopped by Trierarch when it owns them |

Trierarch is the shell, not the distribution. You provide a rootfs or a
DroidSpaces container; Trierarch starts it, displays it, and manages its
lifetime.

## Runtime scope and extensibility

Trierarch's session model is not tied to these three runtimes: in principle it
can be adapted to other ways of running Linux on Android.

Trierarch currently maintains **PRoot, chroot and DroidSpaces** because those
are the three the developer uses day to day. That is not a claim that other
runtimes are impossible or unsupported by design.

If you want another runtime added, open an issue and describe your use case.
Adding a new runtime is real design, implementation and testing work, and
development takes time. Please be patient.

## Desktops and graphics

- **Terminal** — a real TTY with touch scrolling, soft keyboard and hardware
  keyboard support.
- **X11** — XFCE, KDE and other X11 desktops through Trierarch's built-in X
  server.
- **Wayland** — KDE Plasma, Hyprland and other nested compositors through
  Trierarch's Wayland host.
- **Graphics paths** — llvmpipe software rendering, VirGL, Venus, or direct
  Qualcomm Adreno, depending on the device and profile.

Graphics acceleration is device- and profile-dependent. llvmpipe is the
compatibility path; VirGL, Venus and Adreno are for devices and desktops where
the matching guest/host stack is available. Trierarch does not promise the same
GPU behavior on every phone.

## Device support and stability

Trierarch is primarily developed and recommended for **Qualcomm Snapdragon
devices**, especially Adreno-based phones and tablets. That is where the
freedreno/Turnip/Adreno graphics stack is tested most and where the most
complete experience is expected.

Other SoCs can run the terminal, PRoot, chroot and DroidSpaces paths, but their
GPU drivers are less complete. Expect more missing graphics features, more
fallbacks to software rendering, and more device-specific problems.

For desktops, **X11 is currently the more stable target** for VirGL-style
graphics paths. Wayland window managers and desktops are themselves moving
fast: the technology stack a stable X11 setup relied on is often removed or
changed in newer versions, and nested Wayland has more moving parts. Wayland is
supported and improving, but it is the area where breakage is more likely.

## Input, audio and storage

- Touch, physical keyboard, mouse and virtual keyboard input;
- an IME bridge for desktop sessions;
- PipeWire audio for PRoot, chroot and DroidSpaces sessions;
- app-private storage for profiles and rootfs images;
- Android's document provider for moving files in and out.

## Sessions that clean up after themselves

Leaving a Linux desktop running in the background is useful. Leaving an
orphaned container behind is not.

- **PRoot** processes live with the app/PTY and end with the session.
- **chroot** sessions are watched by an owner supervisor and end when the app
  disappears.
- **DroidSpaces** containers that Trierarch started are stopped when the app
  exits or is force-stopped.

Containers that were already running before Trierarch are left alone. Trierarch
only cleans up what it started.

## Quick start

1. Install the Trierarch APK.
2. Open the app; it starts in the built-in shell.
3. Run `trierarch --help`.
4. Create a profile with `trierarch --create my-profile`.
5. Edit it with `trierarch --config my-profile`.
6. Start it with `trierarch --run my-profile`.

The first profile can be a PRoot environment, a chroot rootfs, or an existing
DroidSpaces container.

## Requirements

- Android 7.0 or newer.
- **Recommended hardware:** Qualcomm Snapdragon with Adreno graphics. Other
  SoCs work for the core paths, but have less complete GPU drivers.
- PRoot: no root required.
- chroot: a supported `su` provider.
- DroidSpaces: root, the DroidSpaces runtime, and a compatible kernel.
- A rootfs or DroidSpaces container of your choice.

## Project status

Trierarch is an **early-stage project**. The terminal, PRoot, chroot and core
desktop paths are usable, but Trierarch is not a finished product.

Some parts can be genuinely frustrating right now: GPU behavior and nested
Wayland vary by device and distribution, audio and input can have
device-specific quirks, and some graphics paths are still experimental. Expect
rough edges, incomplete features and occasional breakage.

The developers are actively working on these problems. Fixes, compatibility
work and cleanup are ongoing, and the project will keep changing as that work
lands. If something does not work on your device, it is not being ignored — it
is part of the work still in progress.

## Built on other projects

Trierarch is the shell, not the whole stack. It uses, builds on, or
redistributes components, binaries and data from other projects. Copyright and
license notices remain with their upstream authors; none of the following is
claimed as Trierarch's own work.

| Component | Upstream / pinned revision | What Trierarch uses | License |
| --- | --- | --- | --- |
| **Termux terminal** | [termux/termux-app](https://github.com/termux/termux-app), commit `3df69d1da197dd9bd71a3bafd902dffd720576b4` | `com.termux.terminal`, `com.termux.view` | Termux repo is GPL-3.0; `terminal-emulator`/`terminal-view` are Apache-2.0 per Termux's notices |
| **Termux:X11 / Lorie** | [termux/termux-x11](https://github.com/termux/termux-x11), commit `9471ad977d21b7c7bec45117008f74f330d45983` | `libXlorie.so` and XKB runtime asset | GPL-3.0-or-later |
| **PRoot** | [termux/proot](https://github.com/termux/proot), commit `a89b3732ec6ae1db674510f0843b2f3db54d0a2f` | `proot` and `loader` Android binaries | GPL-2.0-only |
| **virglrenderer** | [virgl/virglrenderer](https://gitlab.freedesktop.org/virgl/virglrenderer), `1.3.0` | Android vtest/Venus host server | MIT |
| **Mesa 3D** | [mesa/mesa](https://gitlab.freedesktop.org/mesa/mesa) | Guest-side virpipe/Mesa paths, supplied by the container | MIT |
| **ANGLE** | [google/angle](https://chromium.googlesource.com/angle/angle), Termux package `2.1.24923-f09a19ce-2` | Vulkan/GL backends used by the VirGL host | BSD-3-Clause |
| **libepoxy** | [anholt/libepoxy](https://github.com/anholt/libepoxy), `1.5.10` | EGL/GL function dispatch | MIT |
| **PipeWire** | [pipewire/pipewire](https://gitlab.freedesktop.org/pipewire/pipewire), `1.4.9-1` | Android audio server | MIT |
| **WirePlumber** | [pipewire/wireplumber](https://gitlab.freedesktop.org/pipewire/wireplumber), `0.5.13` | Session/policy manager | MIT |
| **Local Desktop AAudio sink** | [localdesktop](https://github.com/localdesktop/localdesktop.github.io/tree/0543368d3262e098b92ff0305e149ff0d1fce3e1), commit `0543368d...` | AAudio-backed `Audio/Sink` | GPL-3.0 |
| **Wayland / wayland-protocols** | [wayland](https://gitlab.freedesktop.org/wayland/wayland) | Host compositor and protocols | MIT |
| **libffi** | [libffi/libffi](https://github.com/libffi/libffi) | Wayland server support | MIT |
| **X.Org XKB data** | XKeyboardConfig | XKB rules bundled for Lorie | X11-style |
| **DroidSpaces** | [Droidspaces-OSS](https://github.com/ravindu644/Droidspaces-OSS) | Optional external container runtime | GPL-3.0 |
| **AndroidX / Kotlin** | Android Open Source Project / JetBrains | App UI and Kotlin runtime | Apache-2.0 |

Additional notes:

- PRoot's build also uses **talloc** (LGPL-3.0-or-later) and
  **libandroid-shmem** (BSD-3-Clause); see `trierarch-packages/proot/UPSTREAM.md`
  in the packages repository.
- The guest environments themselves — KDE Plasma/KWin, Hyprland/aquamarine,
  XFCE and the Linux distributions — are external projects supplied by the
  user's rootfs or container. Trierarch supports them but does not bundle them.
- Exact pins, patch series and provenance notes live in the package
  `UPSTREAM.md` files and in
  [Termux terminal tracking](docs/upstream-termux-terminal.md) and
  [Termux:X11 tracking](docs/upstream-termux-x11.md).
- Some of these components are copyleft. If you redistribute binaries, keep
  their copyright/license notices and comply with their licenses; Trierarch
  does not relicense them.

## Documentation

- [English manual](docs/en/README.md) — installation, profiles, runtimes,
  displays, graphics, audio, lifecycle, troubleshooting.
- [中文手册](docs/zh-CN/README.md) — 安装、配置、运行环境、显示、图形、音频、
  生命周期和故障排查。
- [Documentation index](docs/README.md)
- [Known issues](docs/known-issues/README.md) — common problems, workarounds and
  current status.
