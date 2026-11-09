# 第五章 — 桌面

[English](../../en/tutorials/05-desktops.md) | 中文

先在 guest 安装桌面。Trierarch 不内置 KDE、Hyprland、XFCE 或它们的驱动。

## 常用下载命令

以下命令在 guest 中执行，通常是容器/rootfs 内的 root：

```sh
pacman -S --needed plasma-meta        # KDE Plasma
pacman -S --needed hyprland           # Hyprland
pacman -S --needed xfce4 xfce4-goodies # XFCE，可选
```

确认启动命令：

```sh
command -v startplasma-wayland
command -v start-hyprland
command -v startxfce4
```

普通 chroot 和 DroidSpaces 会话，装好这些包即可；不需要 Trierarch 的桌面 patch。

## KDE Plasma

### PRoot 上的 KDE Plasma

PRoot Plasma Wayland 额外需要 KWin compatibility library，因为 PRoot 不能打开真实
DRM render node。

完整开发步骤见：

- [PRoot + Adreno + Plasma Wayland](../developer-preview/proot/wayland-adreno.md)

组件：

```text
KWin compatibility library  → KWin 源码 + kwin-compat patch
Adreno guest userspace      → Mesa 配方 + 外部 Turnip 组件
```

当前 release 资产占位：

```bash
KWIN_COMPAT_URL="<not-published>"
ADRENO_GPU_COMPONENT_URL="<not-published>"
```

release 之前，从 `trierarch-packages/kwin-compat/` 本地构建 compat 库，并使用开发
组件路径。安装到 `/usr/lib` 之外：

```text
/opt/trierarch/kwin/6.8.2/compat/
├── libkwin.so -> libkwin.so.6
├── libkwin.so.6 -> libkwin.so.6.8.2
└── libkwin.so.6.8.2
```


开发阶段使用源码 patch 和构建脚本：

- `trierarch-packages/kwin-compat/patches/0001-wayland-render-node.patch`
- `trierarch-packages/kwin-compat/build-arch.sh`

按该配方构建本地产物，然后安装到 `/usr/lib` 之外：

```sh
install -d /opt/trierarch/kwin/6.8.2/compat
install -m 755 libkwin-6.8.2-2-archlinuxarm-aarch64.so \
  /opt/trierarch/kwin/6.8.2/compat/libkwin.so.6.8.2
ln -sfn libkwin.so.6.8.2 /opt/trierarch/kwin/6.8.2/compat/libkwin.so.6
ln -sfn libkwin.so.6 /opt/trierarch/kwin/6.8.2/compat/libkwin.so
```

正式 release 后，用校验过的 `KWIN_COMPAT_URL` 下载替换本地产物。占位地址目前不能
直接下载。

profile 要把该目录加到 `LD_LIBRARY_PATH` 前面。不要覆盖
`/usr/lib/libkwin.so.6.8.2`。

### chroot 或 DroidSpaces 上的 KDE Plasma

chroot 和 DroidSpaces 使用真实 DRM/KGSL 设备，**不需要** PRoot 的
`kwin-compat` 库。

使用普通包，并按[第四章](04-hardware-acceleration.md)选择图形路径。启动 Plasma：

```toml
[launch]
argv = ["dbus-run-session", "--", "startplasma-wayland"]
```

或使用样例 profile 中的 Adreno 启动数组。

## Hyprland

### 正常安装

```sh
pacman -S --needed hyprland
command -v start-hyprland
```

第一次测试按运行环境选择 renderer：

- PRoot：先用 `llvmpipe` 或受支持的 VirGL 路径；
- chroot：可以使用真实 DRM/KGSL；
- DroidSpaces：可以使用真实 DRM/KGSL。

### Aquamarine 版本

Hyprland 的嵌套 Wayland 行为依赖 **aquamarine**。发行版旧包可能退回 headless 输出，
导致黑屏。

检查版本：

```sh
pacman -Q aquamarine hyprland
```

如果发行版包落后于修复，构建最新 aquamarine：

- 上游：[hyprwm/aquamarine](https://github.com/hyprwm/aquamarine)

```sh
git clone https://github.com/hyprwm/aquamarine.git
cmake -S aquamarine -B aquamarine/build -G Ninja \
  -DCMAKE_BUILD_TYPE=Release
cmake --build aquamarine/build
cmake --install aquamarine/build
```

然后确认 Hyprland 加载新库并重测。如果发行版 Hyprland 是针对不兼容的
aquamarine ABI 构建的，应重新构建 Hyprland，不要混用两个版本。

这条路径的已知问题说明：

- [Hyprland 在嵌套 Wayland 下黑屏](../developer-preview/proot/hyprland-aquamarine.md)

## 运行环境差异

| 桌面 | PRoot | chroot | DroidSpaces |
| --- | --- | --- | --- |
| KDE Plasma Wayland | 需要 KWin compat | 普通包 | 普通包 |
| Hyprland | Aquamarine 必须匹配 | 普通包 | 普通包 |
| XFCE | 普通 X11 配置 | 普通 X11 配置 | 普通 X11 配置 |

下一步：[第六章 — 运行和选择样例](06-run.md)。
