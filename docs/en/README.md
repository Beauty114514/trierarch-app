# Trierarch User Manual

Trierarch is an Android shell for running Linux environments. It connects a
real Linux terminal, X11 or Wayland desktops, PRoot, chroot and DroidSpaces
containers to one Android app, and owns:

- starting and stopping the runtime;
- carrying the terminal or desktop surface to Android;
- forwarding touch, keyboard, mouse and IME input;
- managing PipeWire audio;
- cleaning up sessions and containers that Trierarch started when the app
  exits.

Trierarch is not a virtual machine and does not replace Android with Linux. It
runs on top of Android, using the device kernel and Android's display, input and
audio facilities.

---

## 1. What you can do with it

- open a real Linux shell on a phone;
- run Arch, Debian, Ubuntu, Alpine and other rootfs images;
- run a Linux userspace through PRoot **without root**;
- run a full rootfs through chroot as root;
- run containers with their own init/systemd through DroidSpaces;
- open X11 or Wayland desktops (XFCE, KDE Plasma, Hyprland and others);
- use llvmpipe, VirGL, Venus or Adreno graphics paths where the device supports
  them;
- let Trierarch manage the lifetime of the sessions and containers it starts.

---

## 2. Three runtimes

Trierarch describes "which environment, which display, which graphics path" in
a profile.

### PRoot

- works without root;
- the rootfs lives in the app's private directory;
- processes are children of the app/PTY and end when the app exits;
- supports terminal, X11, Wayland and PipeWire.

### chroot

- requires root (through Magisk, KernelSU, APatch or another `su` provider);
- the rootfs is a directory on the device;
- a session supervisor watches the app process and ends the session if the app
  disappears;
- supports terminal, X11, Wayland and PipeWire.

### DroidSpaces

- requires root, the DroidSpaces runtime, and a container that already exists;
- the container has its own init (systemd/OpenRC/etc.) and can outlive the app;
- Trierarch starts the container and stops **containers that it started** when
  the app exits;
- containers that were already running before Trierarch are not stopped
  automatically;
- supports terminal, X11, Wayland and PipeWire.

> Create the DroidSpaces container in the DroidSpaces app first. Trierarch
> starts, attaches and manages it; it does not create the container.

---

## 3. Requirements

- Android 7.0 or newer (`minSdk 24`);
- PRoot: no root;
- chroot: a working `su`;
- DroidSpaces: root, a supported kernel, and the DroidSpaces runtime;
- **recommended hardware:** Qualcomm Snapdragon with Adreno graphics. Other
  SoCs work for the core paths but have less complete GPU drivers;
- graphics acceleration depends on the device GPU and drivers; not every device
  supports every renderer;
- **X11 is currently the more stable desktop target** for VirGL-style paths.
  Wayland compositors and desktops move quickly; the stack a stable X11 setup
  relied on is often removed or changed, so nested Wayland is more likely to
  break.

---

## 4. Install and first launch

1. Install the Trierarch APK.
2. Open the app. It starts in the built-in shell.
3. Run:

   ```sh
   trierarch --help
   ```

4. Create a profile:

   ```sh
   trierarch --create my-profile
   ```

5. Edit it:

   ```sh
   trierarch --config my-profile
   ```

   `--config` starts `$EDITOR` and falls back to `vi`. The Android built-in
   shell does **not** include `vi`, `nano` or any other editor. If you have an
   editor binary in `PATH`, point `EDITOR` at it; otherwise edit the generated
   profile through Trierarch's document provider (Storage Access Framework)
   with an Android text editor, or pull/edit/push it with `adb` during
   development.

6. Run it:

   ```sh
   trierarch --run my-profile
   ```

Trierarch then shows a terminal, X11 or Wayland surface according to
`display.type`.

---

## 5. Profile management

A profile is a TOML file in the app's private directory:

```
/data/data/app.trierarch/files/config/profiles/<id>.toml
```

Normally you manage it with the CLI:

```sh
trierarch --list              # list profiles
trierarch --status            # show runtime state
trierarch --status ID         # show one profile
trierarch --create ID         # create a template
trierarch --config ID         # edit with $EDITOR
trierarch --run ID            # start
trierarch --stop [ID]         # stop the current or named session
trierarch --rerun ID          # restart
trierarch --delete ID         # delete
```

Profile ID rules:

- lowercase English letters, digits and `-` only;
- must start with a letter;
- at most 64 characters.

### Import a PRoot rootfs

```sh
trierarch --import rootfs.tar.xz --name arch
```

Use the path printed by the import command in `rootfs`. To remove an imported
environment:

```sh
trierarch --remove arch
```

---

## 6. Profile fields

Minimal template:

```toml
id = "my-profile"
runtime = "proot"

rootfs = "/absolute/path/to/rootfs"
shell = "/bin/bash"

[display]
type = "none"
```

### Top-level fields

| Field | Meaning |
| --- | --- |
| `id` | required, unique profile ID |
| `runtime` | `proot`, `chroot` or `droidspaces` |
| `rootfs` | absolute rootfs path for PRoot/chroot |
| `shell` | absolute shell path inside PRoot/chroot |
| `container` | DroidSpaces container name |
| `user` | DroidSpaces login user |
| `name` | optional display name |

### Display

```toml
[display]
type = "none"     # none / x11 / wayland
```

- `none`: terminal;
- `x11`: through Trierarch's built-in X server;
- `wayland`: through Trierarch's Wayland host; the guest runs a nested
  compositor.

### Launch command

```toml
[launch]
argv = ["startplasma-wayland"]
```

`argv` is the command to execute inside the guest. If omitted, the default shell
or distribution session is used.

### Graphics

```toml
[graphics]
renderer = "llvmpipe"
buffer = "auto"
qt_quick_backend = "auto"
```

| renderer | Meaning |
| --- | --- |
| `llvmpipe` | software rendering, best compatibility |
| `virgl` | host-side VirGL/vtest path, aimed at compositor acceleration |
| `venus` | Vulkan/Venus, experimental |
| `adreno` | Qualcomm Adreno direct path (freedreno/kgsl), Wayland only |

Notes:

- `virgl`/`venus` require X11 or Wayland;
- `adreno` supports only DroidSpaces/chroot + Wayland;
- `virgl` currently accelerates mainly the **compositor**. Whether a normal
  application gets hardware OpenGL depends on the guest Mesa and display stack;
  it is not guaranteed for every app;
- `buffer` defaults to `auto`: `shm` for `virgl`/`venus`, `dmabuf` otherwise;
- `qt_quick_backend = "software"` is often used for Plasma + virgl so Qt Quick
  does not look for an unavailable guest GL device.

### Adreno Mesa drivers: Arch and Debian

The `adreno` renderer needs a guest Mesa built for the distribution inside the
guest. Trierarch keeps two recipes in `trierarch-packages/mesa-adreno/`:

- `arch/` — Arch Linux ARM (pacman packages);
- `debian/` — Kali/Debian (deb packages).

**Do not mix them.** Never install the Arch packages in a Debian guest, or the
Debian packages in an Arch guest.

#### Recommended: build it yourself

Building locally is recommended. Mesa and Adreno move quickly, and a version
update can break the patch queue or the package recipe. When that happens, you
can update the patch/recipe with an AI assistant much faster than waiting for a
new release build.

Arch guest:

```bash
cd /path/to/trierarch-packages/mesa-adreno
./arch/build.sh
```

Install all generated packages together. For locally built unsigned packages,
use a temporary pacman config instead of weakening the permanent one:

```bash
cd .build/archlinuxarm-PKGBUILDs/extra/mesa
cp /etc/pacman.conf /tmp/pacman-nosig.conf
sed -i 's|^SigLevel.*|SigLevel = Never|' /tmp/pacman-nosig.conf
sudo pacman --config /tmp/pacman-nosig.conf -U --noconfirm ./*.pkg.tar.*
```

Debian/Kali guest (enable a matching `deb-src` entry and the Mesa build
dependencies first):

```bash
cd /path/to/trierarch-packages/mesa-adreno
./debian/build.sh
./debian/install.sh .debian-build
```

The Debian recipe checks the source package version before patching. If you
upgrade Mesa, update the patch queue and version check instead of forcing the
old patches onto a new source tree. The package README in `mesa-adreno/`
contains the dependency and troubleshooting details.

#### Alternative: Trierarch release drivers (less recommended)

Trierarch releases are intended to ship two matching driver bundles: one for
Arch-based guests and one for Debian-based guests. Pick the bundle for the
guest distribution and install it with that distribution's package manager.

This is convenient, but less recommended: the developer's rebuild cadence can
lag behind Mesa/Adreno changes for reasons outside this project. A release
driver can be older than the Mesa version your desktop or game expects.

#### Verify

Inside the guest:

```bash
vulkaninfo --summary
eglinfo -B
```

For the Adreno/Turnip stack, the renderer should mention `Turnip`/`Adreno`.
Exact environment variables for the Zink/Turnip check are in
`trierarch-packages/mesa-adreno/README.md`.

### Audio

```toml
[audio]
backend = "pipewire"
```

- `off`: no audio;
- `pipewire`: supported for DroidSpaces, PRoot and chroot; not for the internal
  shell.

### Compatibility

```toml
[compat]
udev_monitor = "auto"
```

- `auto`: enable Trierarch's udev monitor shim for desktops such as KWin;
- `off`: disable.

---

## 7. Common setups

### 7.1 PRoot terminal

```toml
id = "arch-terminal"
runtime = "proot"
rootfs = "/data/data/app.trierarch/files/rootfs/arch"
shell = "/bin/bash"

[display]
type = "none"
```

```sh
trierarch --run arch-terminal
```

### 7.2 PRoot + X11

```toml
id = "arch-x11"
runtime = "proot"
rootfs = "/data/data/app.trierarch/files/rootfs/arch"
shell = "/bin/bash"

[display]
type = "x11"

[launch]
argv = ["startxfce4"]

[graphics]
renderer = "llvmpipe"
```

### 7.3 chroot + Wayland

```toml
id = "arch-wayland"
runtime = "chroot"
rootfs = "/data/local/my-rootfs"
shell = "/bin/bash"

[display]
type = "wayland"

[launch]
argv = ["startplasma-wayland"]

[graphics]
renderer = "llvmpipe"
```

chroot requires `su`. Trierarch manages the session with its supervisor.

### 7.4 DroidSpaces + Plasma Wayland + VirGL

```toml
id = "arch-plasma-wayland"
runtime = "droidspaces"
container = "Arch"
user = "beauty"

[display]
type = "wayland"

[launch]
argv = ["startplasma-wayland"]

[graphics]
renderer = "virgl"
buffer = "dmabuf"
qt_quick_backend = "software"

[compat]
udev_monitor = "auto"

[audio]
backend = "pipewire"
```

```sh
trierarch --run arch-plasma-wayland
```

### 7.5 DroidSpaces + Hyprland + Adreno

```toml
id = "arch-hyprland"
runtime = "droidspaces"
container = "Arch"
user = "beauty"

[display]
type = "wayland"

[launch]
argv = ["start-hyprland"]

[graphics]
renderer = "adreno"

[compat]
udev_monitor = "auto"
```

`adreno` requires a Qualcomm device and a working Adreno driver. Unsupported
devices fail to start or show a black screen.

---

## 8. UI and input

- The terminal supports touch scrolling, the soft keyboard and hardware
  keyboards.
- In X11/Wayland, the floating menu lets you:
  - return to the Trierarch shell;
  - show or hide the soft keyboard;
  - switch the virtual keyboard between text and key-event modes.
- External mice, keyboards and touch input are forwarded to the visible session.
- IME input on the desktop is handled by Trierarch's IME bridge.

Stop a graphical session:

```sh
trierarch --stop
```

or a named profile:

```sh
trierarch --stop arch-plasma-wayland
```

---

## 9. Lifecycle and background behavior

### PRoot

PRoot processes are children of the Trierarch app/PTY. When the app exits, the
PTY closes or the session stops, they end with it.

### chroot

chroot is watched by `trierarch-session-supervisor` in owner mode:

- it records the app PID and starttime;
- when the app disappears or the PID is reused, it ends the session process
  group;
- no second app is needed to clean up a chroot session.

### DroidSpaces

A DroidSpaces container has its own init and cannot be stopped by closing a PTY.
Before Trierarch starts a container, it starts an owner supervisor:

- when the app process disappears, the supervisor runs:
  ```sh
  droidspaces --name=<container> stop
  ```
- this applies only to containers that Trierarch started;
- a container that was already running before Trierarch is left alone;
- explicit stop still works:
  ```sh
  trierarch --stop
  ```

After Android kills the app in the background, you no longer need to open the
DroidSpaces app to stop a Trierarch-started container.

---

## 10. Troubleshooting

### "container is already running without a required Trierarch attachment"

The container is already running, but it was not started by the current
Trierarch session and is missing the bind mounts/sockets this profile needs.

Try:

```sh
trierarch --stop
```

If it was not started by Trierarch, stop it in DroidSpaces:

```sh
droidspaces --name=Arch stop
```

Then run the profile again.

### Black screen with Hyprland only

If Hyprland is running but the host's `TrierarchRenderer` stays at:

```
commits=0 damage=0 renders=0
```

check the aquamarine version. An older distro aquamarine can fall back to a
headless output in nested Wayland.

See:

- [Known issues](../known-issues/en/hyprland-aquamarine.md)

The short version: if the distribution package does not contain the fix, build
and install aquamarine from the latest source.

### Only one application window flickers

If the desktop itself is stable and only a window such as SuperTuxKart
flickers, this is not the whole-screen AHB/token path. It is that application's
own rendering/SDL/Wayland swapchain, or KWin's composition of that client.

Try:

```sh
vblank_mode=1 supertuxkart
```

or:

```sh
SDL_VIDEODRIVER=x11 DISPLAY=:0 supertuxkart
```

Compare with `glmark2-wayland` to isolate the app from KWin.

### A graphics renderer does not work

- `virgl`: needs the matching host virgl/vtest and guest Mesa;
- `venus`: experimental;
- `adreno`: needs a Qualcomm GPU and kgsl/freedreno;
- `llvmpipe`: software rendering, best compatibility but limited performance.

Start with `llvmpipe` to prove the session works, then enable a graphics path.

### Logs

Android side:

```sh
adb logcat -s TrierarchTvg TrierarchRenderer TrierarchWayland
```

App-private files:

```sh
adb shell run-as app.trierarch ls files
adb shell run-as app.trierarch cat files/virgl-run/virgl_host.log
```

Inside the guest:

```sh
/tmp/trierarch-wayland/kwin.log
/tmp/trierarch-wayland-host/ime/trierarch-session-supervisor.log
```

DroidSpaces supervisor:

```sh
cat /data/local/tmp/trierarch-droidspaces-supervisor.log
```

---

## 11. Known limitations

- Trierarch is a work-in-progress runtime; graphics/display behavior varies by
  device;
- chroot and DroidSpaces require root;
- DroidSpaces depends on the device kernel and runtime support;
- VirGL currently targets the compositor path; ordinary applications may not
  get hardware OpenGL;
- Venus, dmabuf and Adreno direct paths are experimental;
- compatibility differs across GPUs, Android versions and distributions;
- there is no universal one-click image for every distribution; provide a rootfs
  or a DroidSpaces container yourself.

---

## 12. Building

Development requirements:

- JDK 21;
- Android SDK/NDK;
- Rust with `cargo-ndk`;
- Meson/Ninja;
- guest cross compilers used by the asset tasks (for example
  `aarch64-linux-gnu-gcc`).

Build the APK:

```sh
./gradlew :app:assembleDebug
```

Output:

```
app/build/outputs/apk/debug/app-debug.apk
```

The build also compiles:

- the Rust PTY/native library;
- the PRoot runtime;
- X11/Termux:X11;
- VirGL/Venus components;
- the Wayland IME bridge;
- the chroot session supervisor;
- the DroidSpaces owner supervisor;
- the PipeWire runtime.

---

## 13. Developer references

- [Documentation index](../README.md)
- [中文手册](../zh-CN/README.md)
- [Known issues](../known-issues/README.md)
- [Upstream: Termux terminal](../upstream-termux-terminal.md)
- [Upstream: Termux:X11](../upstream-termux-x11.md)
