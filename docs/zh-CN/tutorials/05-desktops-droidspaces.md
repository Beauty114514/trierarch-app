# 第五章 C — DroidSpaces 桌面

[English](../../en/tutorials/05-desktops-droidspaces.md) | 中文

DroidSpaces 启动已存在的容器，所以桌面安装在容器内，而不是 App 的 rootfs 中。

启动容器并安装桌面：

```sh
pacman -S --needed plasma-meta
pacman -S --needed hyprland
pacman -S --needed xfce4 xfce4-goodies
```

在容器内确认启动命令：

```sh
command -v startplasma-wayland
command -v start-hyprland
command -v startxfce4
```

## KDE Plasma Wayland

使用[第四章](04-hardware-acceleration.md)的真实 DRM/KGSL 路径。启动 Plasma：

```toml
[launch]
argv = ["env", "DISABLE_RTKIT=1", "dbus-run-session", "--", "startplasma-wayland"]
```

不需要 KWin compatibility library。

使用样例 profile：

- [`arch-droidspaces-plasma-wayland-adreno.toml`](../../profiles/arch-droidspaces-plasma-wayland-adreno.toml)

## Hyprland

使用普通 `hyprland` 包和真实 DRM/KGSL 路径。如果会话启动后没有可见输出，检查
Aquamarine 版本。

使用样例 profile：

- [`arch-droidspaces-hyprland-wayland-adreno.toml`](../../profiles/arch-droidspaces-hyprland-wayland-adreno.toml)

## XFCE

使用[第四章](04-hardware-acceleration.md)的普通 X11 配置。

## 说明

- 容器必须已经存在；Trierarch 负责启动或附着；
- `container` 和 `user` 是顶层 profile 字段；
- 音频通过 `audio.backend = "pipewire"` 启用；
- 已验证组合见[兼容性矩阵](../matrices.md)。

下一步：[第六章 — 运行和选择样例](06-run.md)。
