# PRoot — KDE Plasma Wayland

[English](../../en/tutorials/05-desktops-proot-kde.md) | 中文

[返回 PRoot 桌面总览](05-desktops-proot.md)

先完成[公共 Mesa 26 + Turnip 准备](05-desktops-proot.md)，再按本页配置。

## KDE 专用兼容资产

KDE Plasma Wayland 额外需要一个 release 资产：

```text
kwin-compat-6.8.3-1-archlinuxarm-aarch64.tar.zst
```

Mesa 组件与 Hyprland 共用；这个 KWin 库只用于 KDE。

### 解压 KWin library

把压缩包放到 `/opt/trierarch/tmp/`，在 guest 内解压：

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

不要覆盖 `/usr/lib/libkwin.so.6.8.3`。profile 通过 `LD_LIBRARY_PATH` 加载
compat 目录。

## 安装 Plasma

```sh
pacman -S --needed plasma-meta
command -v startplasma-wayland
```

## Profile 配置

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
argv = ["sh", "-lc", "export DISABLE_RTKIT=1 KWIN_COMPOSE=O2 MESA_LOADER_DRIVER_OVERRIDE=zink GALLIUM_DRIVER=zink LIBGL_KOPPER_DRI2=true GALLIUM_THREAD=0 KWIN_RENDER_NODES=/dev/dri/renderD0 TRIERARCH_NESTED_RENDER_NODE=1; export TRIERARCH_MESA=/opt/trierarch/gpu/mesa-26.0/usr; export LD_LIBRARY_PATH=/opt/trierarch/kwin/6.8.3/compat:$TRIERARCH_MESA/lib/aarch64-linux-gnu:$LD_LIBRARY_PATH; export LIBGL_DRIVERS_PATH=$TRIERARCH_MESA/lib/aarch64-linux-gnu/dri; export GBM_BACKENDS_PATH=$TRIERARCH_MESA/lib/aarch64-linux-gnu/gbm; export __EGL_VENDOR_LIBRARY_FILENAMES=$TRIERARCH_MESA/share/glvnd/egl_vendor.d/50_mesa.json; export VK_ICD_FILENAMES=$TRIERARCH_MESA/share/vulkan/icd.d/freedreno_icd.json VK_DRIVER_FILES=$TRIERARCH_MESA/share/vulkan/icd.d/freedreno_icd.json; exec dbus-run-session -- startplasma-wayland > /opt/trierarch/kwin/6.8.3/full-plasma-app.log 2>&1"]

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

## 启动 Plasma

从 App 启动 profile。日志写入：

```text
/opt/trierarch/kwin/6.8.3/full-plasma-app.log
```

如果 Plasma 没有出现，先在 guest 内看这个日志。

## 输入法

Trierarch 已经在 guest 内安装了名为 **Trierarch Wayland Input Method** 的虚拟键盘条目。
首次进入 Plasma 后，在 **系统设置 → 键盘 → 虚拟键盘** 中选中它，只需设置一次。
不选中的话，软键盘只能按按键方式输入英文，中文输入和点击文本框自动弹出键盘都不会生效。

## 验证 GPU 路径

检查文件：

```sh
ls -l /opt/trierarch/gpu/mesa-26.0/usr/lib/aarch64-linux-gnu/libgallium-*.so
ls -l /opt/trierarch/gpu/mesa-26.0/usr/lib/aarch64-linux-gnu/libvulkan_freedreno.so
ls -l /opt/trierarch/kwin/6.8.3/compat/libkwin.so.6.8.3
```

Plasma 运行时检查 KWin 实际加载的库：

```sh
grep -E 'libgallium-26|libkwin\.so\.6\.8\.2|libvulkan_freedreno' \
  /proc/$(pidof kwin_wayland)/maps
```

预期包含：

```text
libgallium-26...
libkwin.so.6.8.3
libvulkan_freedreno.so
```

## 常见错误

| 现象 | 原因 |
| --- | --- |
| `tar: exec zstd: No such file or directory` | 在 Android shell 里解压；必须在 guest 内执行 |
| KWin 仍加载 `/usr/lib/libkwin.so.6.8.3` | `LD_LIBRARY_PATH` 没有包含 compat，或顺序在 Mesa 之后 |
| Zink 选不到设备 | Turnip ICD 没指向解压后的组件 |
| Plasma 启动但没有窗口 | 检查 compatibility library 和 fake render node |
