# 第五章 A — PRoot 桌面

[English](../../en/tutorials/05-desktops-proot.md) | 中文

PRoot 不需要 root，但不能打开真实的 DRM render node。GPU 桌面需要本文描述的
兼容路径。

先在 guest 内安装桌面：

```sh
pacman -S --needed plasma-meta
pacman -S --needed xfce4 xfce4-goodies
```

确认启动命令：

```sh
command -v startplasma-wayland
command -v startxfce4
```

## KDE Plasma Wayland

PRoot 上的 Plasma 还需要：

```text
KWin compatibility library  → kwin-compat
Adreno guest userspace      → Mesa + 配套的 Turnip/ICD
```

0.5.0 release 提供：

```text
kwin-compat-6.8.2-2-archlinuxarm-aarch64.tar.zst
mesa-26.0-noroot.tar.zst
```

把 compat 库安装到 `/usr/lib` 之外：

```sh
install -d /opt/trierarch/kwin/6.8.2/compat
install -m 755 \
  ./kwin-compat/libkwin-6.8.2-2-archlinuxarm-aarch64.so \
  /opt/trierarch/kwin/6.8.2/compat/libkwin.so.6.8.2
ln -sfn libkwin.so.6.8.2 /opt/trierarch/kwin/6.8.2/compat/libkwin.so.6
ln -sfn libkwin.so.6 /opt/trierarch/kwin/6.8.2/compat/libkwin.so
```

不要覆盖 `/usr/lib/libkwin.so.6.8.2`。profile 必须通过 `LD_LIBRARY_PATH` 加载
compat 目录；样例已经这样配置。

按[第四章](04-hardware-acceleration.md)安装 release 中的 guest GPU 组件，然后
使用样例 profile：

- [`arch-proot-plasma-wayland-adreno.toml`](../../profiles/arch-proot-plasma-wayland-adreno.toml)

源码构建使用：

```text
trierarch-packages/kwin-compat/build-arch.sh
trierarch-packages/kwin-compat/patches/0001-wayland-render-node.patch
```

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
