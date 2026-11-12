# Chapter 5A — Desktops on PRoot

English | [中文](../../zh-CN/tutorials/05-desktops-proot.md)

PRoot runs without root, but it cannot open a real DRM render node. GPU desktops
therefore use Trierarch's compatibility path:

```text
fake render node
+ Trierarch Mesa component
+ Turnip
+ KWin compatibility library
```

For PRoot Plasma Wayland, OpenGL runs through **Zink**, and Zink runs on
**Turnip**. This is the verified 0.5.0 path.

Install the desktop inside the guest first:

```sh
pacman -S --needed plasma-meta
```

Verify the launcher:

```sh
command -v startplasma-wayland
```

## KDE Plasma Wayland: Zink + Turnip

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

## XFCE

Use X11 with the graphics path from [Chapter 4](04-hardware-acceleration.md).
No KWin compatibility library is needed.

## Limits

- PRoot cannot open `/dev/dri/renderD128` or `/dev/dri/card0`;
- Adreno Plasma needs the compatibility components and a matching KWin build;
- sound uses the normal `audio.backend = "pipewire"` profile setting;
- Hyprland is not part of the supported PRoot desktop path;
- other tested combinations are in the [compatibility matrix](../matrices.md).

Next: [Chapter 6 — Run and choose a sample](06-run.md).
