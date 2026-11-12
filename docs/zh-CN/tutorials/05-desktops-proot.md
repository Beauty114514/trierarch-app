# 第五章 A — PRoot 桌面

[English](../../en/tutorials/05-desktops-proot.md) | 中文

PRoot 不需要 root，但不能打开真实的 DRM render node。因此 GPU 桌面使用
Trierarch 的兼容路径：

```text
fake render node
+ Trierarch Mesa 组件
+ Turnip
+ KWin compatibility library
```

PRoot Plasma Wayland 里，OpenGL 走 **Zink**，Zink 再走 **Turnip**。这是 0.5.0
已经验证的路径。

先在 guest 内安装桌面：

```sh
pacman -S --needed plasma-meta
```

确认启动命令：

```sh
command -v startplasma-wayland
```

## KDE Plasma Wayland：Zink + Turnip

### Release 资产

需要同时使用：

```text
mesa-26.0-noroot.tar.zst
kwin-compat-6.8.2-2-archlinuxarm-aarch64.tar.zst
```

第一个包含 Mesa guest userspace 和配套的 Turnip ICD。第二个只用于 KDE
Plasma Wayland，其他桌面不需要。

### 1. 在 PRoot guest 内解压

必须从 `arch-shell` profile 或 guest terminal 内执行。不要从 `adb shell`
执行：Android 的 toybox `tar` 没有可用的 zstd 支持。

把两个压缩包放到 guest 能访问的目录：

```text
/opt/trierarch/tmp/
```

创建安装目录：

```sh
install -d /opt/trierarch/gpu /opt/trierarch/kwin/6.8.2/compat
rm -rf /opt/trierarch/gpu/mesa-26.0
mkdir -p /opt/trierarch/gpu/mesa-26.0
```

解压 Mesa 组件，guest 的 `tar --zstd` 会调用 guest 内的 zstd：

```sh
tar --zstd -xf /opt/trierarch/tmp/mesa-26.0-noroot.tar.zst \
  --strip-components=1 -C /opt/trierarch/gpu/mesa-26.0
```

解压 KWin compatibility library：

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

不要覆盖 `/usr/lib/libkwin.so.6.8.2`。profile 通过 `LD_LIBRARY_PATH` 加载
compat 目录。

### 2. Profile 配置

使用样例：

- [`arch-proot-plasma-wayland-adreno.toml`](../../profiles/arch-proot-plasma-wayland-adreno.toml)

关键配置：

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

变量作用：

| 变量 | 作用 |
| --- | --- |
| `GALLIUM_DRIVER=zink` | OpenGL 使用 Zink |
| `MESA_LOADER_DRIVER_OVERRIDE=zink` | 让 Mesa loader 选择 Zink |
| `LIBGL_KOPPER_DRI2=true` | PRoot 下使用分配式 DRI2 路径 |
| `GALLIUM_THREAD=0` | 关闭 Gallium 线程路径 |
| `KWIN_RENDER_NODES=/dev/dri/renderD0` | Trierarch 的 fake render node |
| `TRIERARCH_NESTED_RENDER_NODE=1` | 标记 nested PRoot 输出 |
| `KWIN_COMPOSE=O2` | KWin 使用 OpenGL 2 合成器 |
| `LD_LIBRARY_PATH=compat:...` | 优先加载 compat KWin |
| `VK_ICD_FILENAMES` / `VK_DRIVER_FILES` | 选择组件内的 Turnip ICD |

Mesa 库路径和 Turnip ICD 路径必须与实际解压目录一致。

### 3. 启动 Plasma

从 App 启动 profile。日志写入：

```text
/opt/trierarch/kwin/6.8.2/full-plasma-app.log
```

如果 Plasma 没有出现，先在 guest 内看这个日志。

### 4. 验证 GPU 路径

检查文件：

```sh
ls -l /opt/trierarch/gpu/mesa-26.0/usr/lib/aarch64-linux-gnu/libgallium-*.so
ls -l /opt/trierarch/gpu/mesa-26.0/usr/lib/aarch64-linux-gnu/libvulkan_freedreno.so
ls -l /opt/trierarch/kwin/6.8.2/compat/libkwin.so.6.8.2
```

Plasma 运行时检查 KWin 实际加载的库：

```sh
grep -E 'libgallium-26|libkwin\.so\.6\.8\.2|libvulkan_freedreno' \
  /proc/$(pidof kwin_wayland)/maps
```

预期包含：

```text
libgallium-26...
libkwin.so.6.8.2
libvulkan_freedreno.so
```

在 guest 内检查 Zink 和 Turnip：

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

`eglinfo` 应显示 Zink renderer；`vulkaninfo` 应显示 Turnip。

### 5. 常见错误

| 现象 | 原因 |
| --- | --- |
| `tar: exec zstd: No such file or directory` | 在 Android shell 里解压；必须在 guest 内执行 |
| KWin 仍加载 `/usr/lib/libkwin.so.6.8.2` | `LD_LIBRARY_PATH` 没有包含 compat，或顺序在 Mesa 之后 |
| Zink 选不到设备 | Turnip ICD 没指向解压后的组件 |
| Plasma 启动但没有窗口 | 检查 compatibility library 和 fake render node |

## XFCE

使用 X11 和[第四章](04-hardware-acceleration.md)的图形路径，不需要 KWin
compatibility library。

## 限制

- PRoot 不能打开 `/dev/dri/renderD128` 或 `/dev/dri/card0`；
- Adreno Plasma 需要兼容组件和匹配的 KWin 构建；
- 音频使用 profile 的 `audio.backend = "pipewire"`；
- PRoot 不包含 Hyprland 支持；
- 其他已验证组合见[兼容性矩阵](../matrices.md)。

下一步：[第六章 — 运行和选择样例](06-run.md)。
