# 第四章 — 硬件加速资产

[English](../../en/tutorials/04-hardware-acceleration.md) | 中文

先选 renderer，再安装桌面。大多数桌面启动失败，都是 renderer、guest 驱动和显示
传输方式不匹配。

## 选择路径

| 路径 | GPU 上运行什么 | 额外 guest 资产 |
| --- | --- | --- |
| `llvmpipe` | CPU | 无 |
| `virgl` | host VirGL/vtest | compositor 不需要；应用使用发行版 Mesa |
| `adreno` + Freedreno | OpenGL 走 guest Adreno/KGSL，Vulkan 走 Turnip | Mesa + Turnip userspace |

`llvmpipe` 是第一次测试的正确选择。`virgl` 是 rooted/unrooted compositor 最简单的
加速路径。

`adreno` 只是一个 renderer。DroidSpaces 和 chroot 支持的 OpenGL 前端是
Freedreno；Vulkan 应用使用 Turnip。

## 运行环境差异

| 运行环境 | Adreno 设备访问 | KWin compatibility patch |
| --- | --- | --- |
| PRoot | Trierarch 合成 fake render node | Plasma Wayland 需要 |
| chroot | 通过 root 访问真实 `/dev/dri` | 不需要 |
| DroidSpaces | 容器内访问真实 `/dev/dri` | 不需要 |

KWin patch 不是 GPU 驱动，而是 PRoot fake-DRM 路径的 Wayland render-node 兼容修改；
见[第五章 A](05-desktops-proot.md)。

## 安装 Trierarch release 驱动

使用 Trierarch release 里固定版本的 Mesa/Turnip 资产，不要换成任意发行版 Mesa 包。

| 运行环境 / 桌面 | Release 资产 |
| --- | --- |
| DroidSpaces / chroot（Arch guest） | `mesa-27.0.0-91-root.tar.xz` |
| PRoot，任意桌面 | `mesa-26.0-noroot.tar.zst` |
| 仅 PRoot + KDE Plasma Wayland | `kwin-compat-6.8.2-2-archlinuxarm-aarch64.tar.zst` |

Freedreno 是 Mesa 资产里的 OpenGL 前端；Turnip 是 DroidSpaces/chroot
release 资产里的 Vulkan ICD。

### DroidSpaces 或 chroot，Arch guest

在 Arch guest 内以 root 执行：

```sh
tar -xf mesa-27.0.0-91-root.tar.xz
cd mesa-27.0.0-packages
cp /etc/pacman.conf /tmp/pacman-release.conf
sed -i 's/^SigLevel.*/SigLevel = Never/' /tmp/pacman-release.conf
pacman --config /tmp/pacman-release.conf -U --noconfirm ./*.pkg.tar.xz
rm -f /tmp/pacman-release.conf
```

release 包是本地未签名包。临时配置只用于这次安装；不要削弱永久的
`/etc/pacman.conf`。

这会把 release 的 Mesa 和 Turnip 安装进 guest。不要和别的 Mesa/Turnip 构建混用。

### PRoot Mesa

任何需要加速的 PRoot 桌面都要装：

```sh
mkdir -p /opt/trierarch/gpu/mesa-26.0
tar --zstd -xf mesa-26.0-noroot.tar.zst \
  --strip-components=1 -C /opt/trierarch/gpu/mesa-26.0
```

必须在 PRoot guest 内执行，不要从 `adb shell` 执行；Android 的 toybox tar 没有
可用的 zstd。完整的 Zink + Turnip 和 Plasma profile 配置见
[第五章 A](05-desktops-proot.md)。

### PRoot KWin compatibility：仅 KDE Plasma Wayland

Hyprland、XFCE、X11、纯终端 profile **不要装**。只有 PRoot 跑 KDE Plasma
Wayland 时才需要：

```sh
mkdir -p /tmp/kwin-compat /opt/trierarch/kwin/6.8.2/compat
tar --zstd -xf kwin-compat-6.8.2-2-archlinuxarm-aarch64.tar.zst \
  -C /tmp/kwin-compat
install -m 755 /tmp/kwin-compat/kwin-compat/libkwin-6.8.2-2-archlinuxarm-aarch64.so \
  /opt/trierarch/kwin/6.8.2/compat/libkwin.so.6.8.2
ln -sfn libkwin.so.6.8.2 /opt/trierarch/kwin/6.8.2/compat/libkwin.so.6
ln -sfn libkwin.so.6 /opt/trierarch/kwin/6.8.2/compat/libkwin.so
```

### 验证

```sh
find /usr/share/vulkan/icd.d -iname '*freedreno*'
eglinfo -B | grep -i 'OpenGL core profile renderer'
vulkaninfo --summary | grep -i turnip
```

Freedreno 应该报告 Adreno renderer，Vulkan 应该报告 Turnip。没有 `eglinfo`
时仅为测试安装 `mesa-utils`。

## DroidSpaces 和 chroot：Freedreno + Turnip

两者都使用同一个 KGSL 设备、同一套 Mesa/Turnip 安装。这两个运行环境支持的
OpenGL 前端是 Freedreno；Vulkan 应用使用 Turnip。

### Freedreno + Turnip

OpenGL/EGL 直接走 Mesa 的 Freedreno Gallium 驱动；Vulkan 应用使用 Turnip。

profile 的 launch 环境：

```sh
export GALLIUM_DRIVER=freedreno
export MESA_LOADER_DRIVER_OVERRIDE=kgsl
```

保留 KGSL 提示：

```text
TURNIP_KMD=kgsl
FD_FORCE_KGSL=1
```

`graphics.renderer = "adreno"` 时 Trierarch 会注入它们，不要删。

Vulkan 必须指向配套的 Freedreno/Turnip ICD：

```sh
export VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json
export VK_DRIVER_FILES=$VK_ICD_FILENAMES
```

不同发行版文件名可能不同：

```sh
find /usr/share/vulkan/icd.d -iname '*freedreno*'
```

不能把任意 Mesa 构建和任意 Turnip 构建混在一起。

## 样例 profile
## 样例 profile

- [Freedreno 样例](../../profiles/arch-droidspaces-plasma-wayland-adreno.toml)

chroot 使用同样的环境。现有样例：

```text
kali-chroot-plasma-wayland-adreno.toml
```

展示的是 Freedreno 变体。

## PRoot guest GPU 组件

PRoot 不能打开真实 render node，所以它的 profile 使用 Trierarch 的 Mesa 组件
加 KWin compatibility library。组件和 `kwin-compat` 必须成对固定版本；见
[第五章 A](05-desktops-proot.md)。

PRoot 使用的 Mesa/Turnip 组合不能和任意发行版 Mesa 包互换。

## PRoot Plasma 兼容

PRoot Plasma Wayland 还需要 KWin compatibility library。构建、安装和 profile
环境见[第五章 A](05-desktops-proot.md)。

下一步：[第五章 — 桌面](05-desktops.md)。
