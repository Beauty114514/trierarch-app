# Chapter 5A — Desktops on PRoot

English | [中文](../../zh-CN/tutorials/05-desktops-proot.md)

PRoot runs without root, but it cannot open a real DRM render node. GPU desktops
therefore use Trierarch's compatibility path:

```text
fake render node
+ Trierarch Mesa component
+ Turnip
+ desktop-specific compatibility library
```

For PRoot Plasma Wayland and Hyprland, OpenGL runs through **Zink**, and Zink
runs on **Turnip**. Plasma also needs the KWin compatibility library; Hyprland
uses the aquamarine-compat library instead.

Install each desktop inside the guest from its section below.

## KDE Plasma Wayland: Zink + Turnip

Install Plasma and verify the launcher:

```sh
pacman -S --needed plasma-meta
command -v startplasma-wayland
```

### Release assets

Use these two release assets together:

```text
mesa-26.0-noroot.tar.zst
kwin-compat-6.8.2-2-archlinuxarm-aarch64.tar.zst
```

The first contains the Mesa guest userspace and the matching Turnip ICD. The
second is only for KDE Plasma Wayland; other desktops do not need it.

### 1. Extract inside the PRoot guest

Do this inside the guest, from the `arch-shell` profile or a guest terminal.
Do **not** run these commands from `adb shell`: Android's toybox `tar` has no
usable zstd support.

Place both archives in a guest-visible directory:

```text
/opt/trierarch/tmp/
```

Create the install directories:

```sh
install -d /opt/trierarch/gpu /opt/trierarch/kwin/6.8.2/compat
rm -rf /opt/trierarch/gpu/mesa-26.0
mkdir -p /opt/trierarch/gpu/mesa-26.0
```

Extract the Mesa component. The guest's `tar --zstd` uses the guest's zstd:

```sh
tar --zstd -xf /opt/trierarch/tmp/mesa-26.0-noroot.tar.zst \
  --strip-components=1 -C /opt/trierarch/gpu/mesa-26.0
```

Extract the KWin compatibility library:

```sh
rm -rf /tmp/kwin-compat
mkdir -p /tmp/kwin-compat
tar --zstd -xf \
  /opt/trierarch/tmp/kwin-compat-6.8.2-2-archlinuxarm-aarch64.tar.zst \
  -C /tmp/kwin-compat

install -m 755 \
  /tmp/kwin-compat/kwin-compat/libkwin-6.8.2-2-archlinuxarm-aarch64.so \
  /opt/trierarch/kwin/6.8.2/compat/libkwin.so.6.8.2

ln -sfn libkwin.so.6.8.2 /opt/trierarch/kwin/6.8.2/compat/libkwin.so.6
ln -sfn libkwin.so.6 /opt/trierarch/kwin/6.8.2/compat/libkwin.so
```

Do not overwrite `/usr/lib/libkwin.so.6.8.2`. The profile loads the compat
directory through `LD_LIBRARY_PATH`.

### 2. Profile configuration

Use the sample profile:

- [`arch-proot-plasma-wayland-adreno.toml`](../../profiles/arch-proot-plasma-wayland-adreno.toml)

Its important settings are:

```toml
runtime = "proot"
shell = "/bin/bash"

[display]
type = "wayland"
desktop_scale = 100

[launch]
argv = ["sh", "-lc", "export DISABLE_RTKIT=1 KWIN_COMPOSE=O2 MESA_LOADER_DRIVER_OVERRIDE=zink GALLIUM_DRIVER=zink LIBGL_KOPPER_DRI2=true GALLIUM_THREAD=0 KWIN_RENDER_NODES=/dev/dri/renderD0 TRIERARCH_NESTED_RENDER_NODE=1; export TRIERARCH_MESA=/opt/trierarch/gpu/mesa-26.0/usr; export LD_LIBRARY_PATH=/opt/trierarch/kwin/6.8.2/compat:$TRIERARCH_MESA/lib/aarch64-linux-gnu:$LD_LIBRARY_PATH; export LIBGL_DRIVERS_PATH=$TRIERARCH_MESA/lib/aarch64-linux-gnu/dri; export GBM_BACKENDS_PATH=$TRIERARCH_MESA/lib/aarch64-linux-gnu/gbm; export __EGL_VENDOR_LIBRARY_FILENAMES=$TRIERARCH_MESA/share/glvnd/egl_vendor.d/50_mesa.json; export VK_ICD_FILENAMES=$TRIERARCH_MESA/share/vulkan/icd.d/freedreno_icd.json VK_DRIVER_FILES=$TRIERARCH_MESA/share/vulkan/icd.d/freedreno_icd.json; exec dbus-run-session -- startplasma-wayland > /opt/trierarch/kwin/6.8.2/full-plasma-app.log 2>&1"]

[graphics]
renderer = "adreno"
buffer = "dmabuf"
qt_quick_backend = "auto"

[audio]
backend = "pipewire"
```

Why these variables matter:

| Variable | Purpose |
| --- | --- |
| `GALLIUM_DRIVER=zink` | Use Zink for OpenGL |
| `MESA_LOADER_DRIVER_OVERRIDE=zink` | Make the Mesa loader select Zink |
| `LIBGL_KOPPER_DRI2=true` | Use the allocated DRI2 path under PRoot |
| `GALLIUM_THREAD=0` | Avoid the threaded Gallium path |
| `KWIN_RENDER_NODES=/dev/dri/renderD0` | Trierarch's fake render node |
| `TRIERARCH_NESTED_RENDER_NODE=1` | Mark the nested PRoot output |
| `KWIN_COMPOSE=O2` | Use the OpenGL 2 KWin compositor |
| `LD_LIBRARY_PATH=compat:...` | Load the compat KWin before system KWin |
| `VK_ICD_FILENAMES` / `VK_DRIVER_FILES` | Select the bundled Turnip ICD |

The Mesa library path and the Turnip ICD path must match the extracted component.

### 3. Start Plasma

Start the profile from the app. The log is written to:

```text
/opt/trierarch/kwin/6.8.2/full-plasma-app.log
```

If Plasma does not appear, read that log inside the guest first.

### 4. Verify the GPU path

Check the expected files:

```sh
ls -l /opt/trierarch/gpu/mesa-26.0/usr/lib/aarch64-linux-gnu/libgallium-*.so
ls -l /opt/trierarch/gpu/mesa-26.0/usr/lib/aarch64-linux-gnu/libvulkan_freedreno.so
ls -l /opt/trierarch/kwin/6.8.2/compat/libkwin.so.6.8.2
```

Check the loaded KWin libraries while Plasma is running:

```sh
grep -E 'libgallium-26|libkwin\.so\.6\.8\.2|libvulkan_freedreno' \
  /proc/$(pidof kwin_wayland)/maps
```

Expected:

```text
libgallium-26...
libkwin.so.6.8.2
libvulkan_freedreno.so
```

Check Zink and Turnip from the guest:

```sh
export TRIERARCH_MESA=/opt/trierarch/gpu/mesa-26.0/usr
export LD_LIBRARY_PATH=/opt/trierarch/kwin/6.8.2/compat:$TRIERARCH_MESA/lib/aarch64-linux-gnu
export LIBGL_DRIVERS_PATH=$TRIERARCH_MESA/lib/aarch64-linux-gnu/dri
export GBM_BACKENDS_PATH=$TRIERARCH_MESA/lib/aarch64-linux-gnu/gbm
export __EGL_VENDOR_LIBRARY_FILENAMES=$TRIERARCH_MESA/share/glvnd/egl_vendor.d/50_mesa.json
export VK_ICD_FILENAMES=$TRIERARCH_MESA/share/vulkan/icd.d/freedreno_icd.json
export VK_DRIVER_FILES=$VK_ICD_FILENAMES
export GALLIUM_DRIVER=zink
export MESA_LOADER_DRIVER_OVERRIDE=zink

eglinfo -B | grep -i 'OpenGL core profile renderer'
vulkaninfo --summary | grep -i turnip
```

`eglinfo` should show a Zink renderer; `vulkaninfo` should show Turnip.

### 5. Common failures

| Symptom | Cause |
| --- | --- |
| `tar: exec zstd: No such file or directory` | Ran extraction from Android shell; run it inside the guest |
| KWin loads `/usr/lib/libkwin.so.6.8.2` | Compat directory is missing from `LD_LIBRARY_PATH` or ordered after Mesa |
| Zink fails to choose a device | Turnip ICD path does not point at the extracted component |
| Plasma starts without a window | Check the compatibility library and the fake render node |

## Hyprland: Zink + Turnip + aquamarine-compat

### Release assets

Use these two files together:

```text
mesa-26.0-noroot.tar.zst
libaquamarine-compat.so
```

The Mesa component is the same one used by Plasma. `libaquamarine-compat.so` is
the PRoot Hyprland compatibility library. It combines the fake-node name, PRIME,
lease and single-GBM fixes into one `LD_PRELOAD` library. Do not install the KWin
compatibility library for Hyprland.

### 1. Install Hyprland and the compatibility library

Run this inside the PRoot guest, not from `adb shell`:

```sh
pacman -S --needed hyprland

install -d /opt/trierarch/compat
install -m 755 /path/to/libaquamarine-compat.so /opt/trierarch/compat/

ls -l /opt/trierarch/compat/libaquamarine-compat.so
command -v Hyprland
```

If you build the library from a checkout instead of using the release asset:

```sh
cd trierarch-packages/aquamarine-compat
make combined
```

The output is `dist/linux/aarch64/libaquamarine-compat.so`.

### 2. Use 8-bit output in the tested path

The verified PRoot path uses 8-bit scanout. In the guest's
`~/.config/hypr/hyprland.lua`, keep the monitor rule at:

```lua
hl.monitor({
    output   = "",
    mode     = "preferred",
    position = "auto",
    scale    = "auto",
    bitdepth = 8,
})
```

The generated config may show an "autogenerated config" warning overlay; remove
`hl.config({ autogenerated = true })` if you want to hide it.

### 3. Profile configuration

Use the sample profile:

- [`arch-proot-hyprland-wayland-adreno.toml`](../../profiles/arch-proot-hyprland-wayland-adreno.toml)

The important parts are:

```toml
runtime = "proot"
shell = "/bin/bash"

[display]
type = "wayland"
desktop_scale = 100

[launch]
argv = ["sh", "-lc", "export MESA_LOADER_DRIVER_OVERRIDE=zink GALLIUM_DRIVER=zink LIBGL_KOPPER_DRI2=true GALLIUM_THREAD=0 TRIERARCH_NESTED_RENDER_NODE=1; export TRIERARCH_MESA=/opt/trierarch/gpu/mesa-26.0/usr; export LD_LIBRARY_PATH=$TRIERARCH_MESA/lib/aarch64-linux-gnu:$LD_LIBRARY_PATH; export LIBGL_DRIVERS_PATH=$TRIERARCH_MESA/lib/aarch64-linux-gnu/dri; export GBM_BACKENDS_PATH=$TRIERARCH_MESA/lib/aarch64-linux-gnu/gbm; export __EGL_VENDOR_LIBRARY_FILENAMES=$TRIERARCH_MESA/share/glvnd/egl_vendor.d/50_mesa.json; export VK_ICD_FILENAMES=$TRIERARCH_MESA/share/vulkan/icd.d/freedreno_icd.json VK_DRIVER_FILES=$TRIERARCH_MESA/share/vulkan/icd.d/freedreno_icd.json; export LD_PRELOAD=/opt/trierarch/compat/libaquamarine-compat.so; exec Hyprland --i-am-really-stupid >/tmp/trierarch-hyprland-pr.log 2>&1"]

[graphics]
renderer = "adreno"
buffer = "dmabuf"
```

Why these variables matter:

| Variable | Purpose |
| --- | --- |
| `GALLIUM_DRIVER=zink` | Use Zink for OpenGL |
| `MESA_LOADER_DRIVER_OVERRIDE=zink` | Make the Mesa loader select Zink |
| `LIBGL_KOPPER_DRI2=true` | Use the allocated DRI2 path under PRoot |
| `GALLIUM_THREAD=0` | Avoid the threaded Gallium path |
| `TRIERARCH_NESTED_RENDER_NODE=1` | Mark the nested PRoot output |
| `LD_PRELOAD=.../libaquamarine-compat.so` | Answer Aquamarine's fake-node probes and share one GBM device |
| `TRIERARCH_MESA`, `LIBGL_DRIVERS_PATH`, `GBM_BACKENDS_PATH` | Select the extracted PRoot Mesa component |
| `VK_ICD_FILENAMES` / `VK_DRIVER_FILES` | Select the bundled Turnip ICD |

`Hyprland --i-am-really-stupid` is used because the PRoot guest runs as root and
Trierarch starts the compositor directly, without `start-hyprland`.

### 4. Start Hyprland

Start the profile from the app. The log is written to:

```text
/tmp/trierarch-hyprland-pr.log
```

If the desktop does not appear, read that log inside the guest first.

To confirm the GPU libraries while Hyprland is running:

```sh
grep -E 'libgallium-26|libvulkan_freedreno' /proc/$(pidof Hyprland)/maps
```

Expected:

```text
libgallium-26...
libvulkan_freedreno.so
```

`hyprctl` may report a `Socket2` "path is too long" error in the current PRoot
session. The desktop can still render; this is a known limitation of the long
Trierarch runtime path, not a GPU failure.

### 5. Common failures

| Symptom | Cause |
| --- | --- |
| `CBackend::create() failed` and Hyprland exits | `libaquamarine-compat.so` is missing or not in `LD_PRELOAD` |
| `PRIME export is not supported` or no GBM allocator | Old/partial shim; use the combined `libaquamarine-compat.so` |
| `DRM_IOCTL_MODE_CREATE_DUMB failed` | Wrong Mesa stack or software rendering; use the PRoot Mesa 26 component with Zink/Turnip |
| Hyprland starts but nothing appears | Check the host attach state and the guest log; make sure the profile uses `display.type = "wayland"` |
| `hyprctl` cannot connect | Known PRoot socket-path limitation |
| Autogenerated config warning overlay | Remove `hl.config({ autogenerated = true })` from the guest config |

## XFCE

Use X11 with the graphics path from [Chapter 4](04-hardware-acceleration.md).
No KWin compatibility library is needed.

## Limits

- PRoot cannot open `/dev/dri/renderD128` or `/dev/dri/card0`;
- Adreno Plasma needs the compatibility components and a matching KWin build;
- sound uses the normal `audio.backend = "pipewire"` profile setting;
- Hyprland is supported on PRoot with `libaquamarine-compat.so`; treat this newer path as experimental;
- other tested combinations are in the [compatibility matrix](../matrices.md).

Next: [Chapter 6 — Run and choose a sample](06-run.md).
