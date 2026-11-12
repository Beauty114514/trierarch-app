# 第五章 B — chroot 桌面

[English](../../en/tutorials/05-desktops-chroot.md) | 中文

chroot 可以使用真实 DRM/KGSL，因此 GPU 桌面不需要 PRoot 的 compatibility
library。

在 chroot rootfs 内安装桌面：

```sh
pacman -S --needed plasma-meta
pacman -S --needed hyprland
pacman -S --needed xfce4 xfce4-goodies
```

确认启动命令：

```sh
command -v startplasma-wayland
command -v start-hyprland
command -v startxfce4
```

## KDE Plasma Wayland

使用[第四章](04-hardware-acceleration.md)的真实 GPU 路径。启动 Plasma：

```toml
[launch]
argv = ["env", "DISABLE_RTKIT=1", "dbus-run-session", "--", "startplasma-wayland"]
```

`DISABLE_RTKIT=1` 用于避免实时 portal 导致的黑屏，见
[PipeWire/RTKit](../known-issues/pipewire-rtkit.md)。

使用样例 profile：

- [`kali-chroot-plasma-wayland-adreno.toml`](../../profiles/kali-chroot-plasma-wayland-adreno.toml)

## Hyprland

安装普通 `hyprland` 包，使用真实 DRM/KGSL 路径。如果会话启动后没有可见输出，
检查 Aquamarine 版本：

```sh
pacman -Q aquamarine hyprland
```

旧版 Aquamarine 可能退回 headless 输出。只有发行版包落后于所需修复时才需要重建。

## XFCE

使用[第四章](04-hardware-acceleration.md)的普通 X11 配置，不需要 KWin
compatibility library。

## 说明

- chroot 需要 root 和可用的 `su`；
- rootfs 必须包含桌面和 GPU userspace；
- 音频通过 `audio.backend = "pipewire"` 启用；
- 已验证组合见[兼容性矩阵](../matrices.md)。

下一步：[第六章 — 运行和选择样例](06-run.md)。
