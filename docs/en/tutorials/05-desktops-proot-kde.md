# PRoot — KDE Plasma Wayland

English | [中文](../../zh-CN/tutorials/05-desktops-proot-kde.md)

[Back to PRoot desktops](05-desktops-proot.md)

Complete the [common Mesa 26 + Turnip setup](05-desktops-proot.md)
first, then follow this page.

## KDE compatibility asset

KDE Plasma Wayland needs one additional release asset:

```text
kwin-compat-6.8.3-1-archlinuxarm-aarch64.tar.zst
```

The Mesa component is shared with Hyprland; this KWin library is KDE-only.

### Extract the KWin library

Place the archive in `/opt/trierarch/tmp/` and extract it inside the guest:

```sh
install -d /opt/trierarch/kwin/6.8.3/compat
rm -rf /tmp/kwin-compat
mkdir -p /tmp/kwin-compat

tar --zstd -xf \
  /opt/trierarch/tmp/kwin-compat-6.8.3-1-archlinuxarm-aarch64.tar.zst \
  -C /tmp/kwin-compat

install -m 755 \
  /tmp/kwin-compat/kwin-compat/libkwin-6.8.3-1-archlinuxarm-aarch64.so \
  /opt/trierarch/kwin/6.8.3/compat/libkwin.so.6.8.3

ln -sfn libkwin.so.6.8.3 /opt/trierarch/kwin/6.8.3/compat/libkwin.so.6
ln -sfn libkwin.so.6 /opt/trierarch/kwin/6.8.3/compat/libkwin.so
```

Do not overwrite `/usr/lib/libkwin.so.6.8.3`. The profile loads the compat
directory through `LD_LIBRARY_PATH`.

## Install Plasma

```sh
pacman -S --needed plasma-meta
command -v startplasma-wayland
```

## Profile configuration

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
argv = ["sh", "-lc", "export DISABLE_RTKIT=1 KWIN_COMPOSE=O2 MESA_LOADER_DRIVER_OVERRIDE=zink GALLIUM_DRIVER=zink LIBGL_KOPPER_DRI2=true GALLIUM_THREAD=0 KWIN_RENDER_NODES=/dev/dri/renderD0 TRIERARCH_NESTED_RENDER_NODE=1; export TRIERARCH_MESA=/opt/trierarch/gpu/mesa-26.0/usr; export LD_LIBRARY_PATH=/opt/trierarch/kwin/6.8.3/compat:$TRIERARCH_MESA/lib/aarch64-linux-gnu:$LD_LIBRARY_PATH; export LIBGL_DRIVERS_PATH=$TRIERARCH_MESA/lib/aarch64-linux-gnu/dri; export GBM_BACKENDS_PATH=$TRIERARCH_MESA/lib/aarch64-linux-gnu/gbm; export __EGL_VENDOR_LIBRARY_FILENAMES=$TRIERARCH_MESA/share/glvnd/egl_vendor.d/50_mesa.json; export VK_ICD_FILENAMES=$TRIERARCH_MESA/share/vulkan/icd.d/freedreno_icd.json VK_DRIVER_FILES=$TRIERARCH_MESA/share/vulkan/icd.d/freedreno_icd.json; exec dbus-run-session -- startplasma-wayland > /opt/trierarch/kwin/6.8.3/full-plasma-app.log 2>&1"]

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

## Start Plasma

Start the profile from the app. The log is written to:

```text
/opt/trierarch/kwin/6.8.3/full-plasma-app.log
```

If Plasma does not appear, read that log inside the guest first.

## Verify the GPU path

Check the expected files:

```sh
ls -l /opt/trierarch/gpu/mesa-26.0/usr/lib/aarch64-linux-gnu/libgallium-*.so
ls -l /opt/trierarch/gpu/mesa-26.0/usr/lib/aarch64-linux-gnu/libvulkan_freedreno.so
ls -l /opt/trierarch/kwin/6.8.3/compat/libkwin.so.6.8.3
```

While Plasma is running:

```sh
grep -E 'libgallium-26|libkwin\.so\.6\.8\.2|libvulkan_freedreno' \
  /proc/$(pidof kwin_wayland)/maps
```

Expected:

```text
libgallium-26...
libkwin.so.6.8.3
libvulkan_freedreno.so
```

## Common failures

| Symptom | Cause |
| --- | --- |
| `tar: exec zstd: No such file or directory` | Ran extraction from Android shell; run it inside the guest |
| KWin loads `/usr/lib/libkwin.so.6.8.3` | Compat directory is missing from `LD_LIBRARY_PATH` or ordered after Mesa |
| Zink fails to choose a device | Turnip ICD path does not point at the extracted component |
| Plasma starts without a window | Check the compatibility library and the fake render node |
