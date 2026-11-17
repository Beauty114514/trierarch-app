# 第五章 A — PRoot 桌面

[English](../../en/tutorials/05-desktops-proot.md) | 中文

PRoot 不需要 root，但不能打开真实的 DRM render node。因此 GPU 桌面使用
Trierarch 的兼容路径：

```text
fake render node
+ Trierarch Mesa 组件
+ Turnip
+ 桌面专用的 compatibility library
```

PRoot Plasma Wayland 和 Hyprland 里，OpenGL 走 **Zink**，Zink 再走
**Turnip**。Plasma 还需要 KWin compatibility library；Hyprland 使用
aquamarine-compat library。

每个桌面的安装步骤见各自章节。

## KDE Plasma Wayland：Zink + Turnip

安装 Plasma 并确认启动命令：

```sh
pacman -S --needed plasma-meta
command -v startplasma-wayland
```

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

## Hyprland：Zink + Turnip + aquamarine-compat

### Release 资产

需要同时使用：

```text
mesa-26.0-noroot.tar.zst
libaquamarine-compat.so
```

Mesa 组件和 Plasma 共用。`libaquamarine-compat.so` 是 PRoot Hyprland 的兼容库，
把 fake node 的 name、PRIME、lease 和 single-GBM 四段修复合进一个
`LD_PRELOAD` 库。Hyprland 不要安装 KWin compatibility library。

### 1. 安装 Hyprland 和兼容库

在 PRoot guest 内执行，不要从 `adb shell` 执行：

```sh
pacman -S --needed hyprland
pacman -S --needed dbus

install -d /opt/trierarch/compat
install -m 755 /path/to/libaquamarine-compat.so /opt/trierarch/compat/

ls -l /opt/trierarch/compat/libaquamarine-compat.so
command -v Hyprland
command -v dbus-run-session
```

`--needed` 表示已安装的包不重复安装。`dbus` 提供 `dbus-run-session`，
profile 用它建立 session bus。

如果不用 release 资产，而是在源码树里构建：

```sh
cd trierarch-packages/aquamarine-compat
make combined
```

产物是 `dist/linux/aarch64/libaquamarine-compat.so`。

### 2. 使用已验证的 8-bit 输出

当前验证过的 PRoot 路径使用 8-bit scanout。在 guest 的
`~/.config/hypr/hyprland.lua` 里，把 monitor rule 保持为：

```lua
hl.monitor({
    output   = "",
    mode     = "preferred",
    position = "auto",
    scale    = "auto",
    bitdepth = 8,
})
```

自动生成的配置会显示 “autogenerated config” 警告；去掉
`hl.config({ autogenerated = true })` 可以隐藏。

### 3. Profile 配置

使用样例 profile：

- [`arch-proot-hyprland-adreno.toml`](../../profiles/arch-proot-hyprland-adreno.toml)

关键部分是：

```toml
runtime = "proot"
shell = "/bin/bash"

[display]
type = "wayland"
desktop_scale = 100

[launch]
argv = ["sh", "-lc", "export MESA_LOADER_DRIVER_OVERRIDE=zink GALLIUM_DRIVER=zink LIBGL_KOPPER_DRI2=true GALLIUM_THREAD=0 TRIERARCH_NESTED_RENDER_NODE=1; export TRIERARCH_MESA=/opt/trierarch/gpu/mesa-26.0/usr; export LD_LIBRARY_PATH=$TRIERARCH_MESA/lib/aarch64-linux-gnu:$LD_LIBRARY_PATH; export LIBGL_DRIVERS_PATH=$TRIERARCH_MESA/lib/aarch64-linux-gnu/dri; export GBM_BACKENDS_PATH=$TRIERARCH_MESA/lib/aarch64-linux-gnu/gbm; export __EGL_VENDOR_LIBRARY_FILENAMES=$TRIERARCH_MESA/share/glvnd/egl_vendor.d/50_mesa.json; export VK_ICD_FILENAMES=$TRIERARCH_MESA/share/vulkan/icd.d/freedreno_icd.json VK_DRIVER_FILES=$TRIERARCH_MESA/share/vulkan/icd.d/freedreno_icd.json; export LD_PRELOAD=/opt/trierarch/compat/libaquamarine-compat.so; exec dbus-run-session -- start-hyprland -- --i-am-really-stupid >/tmp/trierarch-hyprland-pr.log 2>&1"]

[graphics]
renderer = "adreno"
buffer = "dmabuf"
```

这些变量的作用：

| 变量 | 作用 |
| --- | --- |
| `GALLIUM_DRIVER=zink` | OpenGL 使用 Zink |
| `MESA_LOADER_DRIVER_OVERRIDE=zink` | 让 Mesa loader 选择 Zink |
| `LIBGL_KOPPER_DRI2=true` | PRoot 下使用已分配的 DRI2 路径 |
| `GALLIUM_THREAD=0` | 避免 threaded Gallium |
| `TRIERARCH_NESTED_RENDER_NODE=1` | 标记 PRoot nested 输出 |
| `LD_PRELOAD=.../libaquamarine-compat.so` | 通过 Aquamarine 的 fake-node 探测，并复用同一个 GBM device |
| `TRIERARCH_MESA`、`LIBGL_DRIVERS_PATH`、`GBM_BACKENDS_PATH` | 指向解压后的 PRoot Mesa 组件 |
| `VK_ICD_FILENAMES` / `VK_DRIVER_FILES` | 指向配套 Turnip ICD |

`dbus-run-session` 提供 session bus，供 portal、通知和音频等服务使用。
`start-hyprland` 是 Hyprland 上游推荐的 watchdog 启动包装器；`--` 后面的
`--i-am-really-stupid` 会透传给 Hyprland，因为 PRoot guest 以 root 运行。

### 4. 启动 Hyprland

从 app 启动 profile。日志写在：

```text
/tmp/trierarch-hyprland-pr.log
```

如果桌面没有出现，先在 guest 里读这个日志。

Hyprland 运行时可确认 GPU 库：

```sh
grep -E 'libgallium-26|libvulkan_freedreno' /proc/$(pidof Hyprland)/maps
```

期望看到：

```text
libgallium-26...
libvulkan_freedreno.so
```

当前 PRoot session 里 `hyprctl` 可能报 `Socket2` 的 `path is too long`。桌面仍
可以正常渲染；这是 Trierarch 运行时路径过长的已知限制，不是 GPU 失败。

### 5. 常见错误

| 现象 | 原因 |
| --- | --- |
| `CBackend::create() failed` 后 Hyprland 退出 | 没有安装或没有在 `LD_PRELOAD` 里加载 `libaquamarine-compat.so` |
| `PRIME export is not supported` 或没有 GBM allocator | 用了旧的/不完整的 shim；改用合并版 `libaquamarine-compat.so` |
| `DRM_IOCTL_MODE_CREATE_DUMB failed` | Mesa 栈不对或退回软件渲染；使用 PRoot Mesa 26 + Zink/Turnip |
| Hyprland 启动但画面不出来 | 检查 host attach 状态和 guest 日志；确认 profile 使用 `display.type = "wayland"` |
| `hyprctl` 连不上 | 已知的 PRoot socket 路径过长限制 |
| 一直显示 autogenerated config 警告 | 从 guest 配置里去掉 `hl.config({ autogenerated = true })` |

## XFCE

使用 X11 和[第四章](04-hardware-acceleration.md)的图形路径，不需要 KWin
compatibility library。

## 限制

- PRoot 不能打开 `/dev/dri/renderD128` 或 `/dev/dri/card0`；
- Adreno Plasma 需要兼容组件和匹配的 KWin 构建；
- 音频使用 profile 的 `audio.backend = "pipewire"`；
- PRoot 通过 `libaquamarine-compat.so` 支持 Hyprland；这是较新的路径，暂时按实验性功能对待；
- 其他已验证组合见[兼容性矩阵](../matrices.md)。

下一步：[第六章 — 运行和选择样例](06-run.md)。
