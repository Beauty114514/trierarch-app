# 兼容性矩阵

[English](../en/matrices.md) | 中文

最后更新：2026-11-12

测试设备：Adreno 740（Qualcomm）。

本轮记录：

- **DroidSpaces — Arch**（已 root）；
- **chroot — Kali**（已 root）；
- **PRoot — Arch**（未 root）。

## 图例

| 标记 | 含义 |
| --- | --- |
| ✅ 已验证 | 本轮实际测试通过，可用 |
| ✅ 支持 | 该软件路径支持，但对应桌面组合尚未实测 |
| ❌ 失败 | 已测试，失败；原因见下文 |
| — | 本轮未测试 |

## 运行环境能力概览

| 运行环境 | Root | Wayland GPU 路径 | X11 GPU 路径 |
| --- | --- | --- | --- |
| **DroidSpaces** | 是 | Adreno 直通，特效可用 | Adreno 直通；也可用 VirGL |
| **chroot** | 是 | Adreno 直通，特效可用 | Adreno 直通；也可用 VirGL |
| **PRoot** | 否 | 取决于 compositor 和所需 patch；不是完整的 Linux 环境。KDE Plasma Wayland 在使用兼容组件时受支持 | 宿主 **VirGL**（`virpipe`），guest 不需要 `/dev/dri` |

### Wayland 硬件加速

Wayland 是主要桌面路径，也是 Plasma/Hyprland 特效的来源。它的 GPU 路径取决于
运行环境：

| 运行环境 | Wayland GPU 路径 | 状态 |
| --- | --- | --- |
| **DroidSpaces** | Adreno 直通（freedreno/kgsl） | ✅ 已验证；特效可用 |
| **chroot** | Adreno 直通（freedreno/kgsl） | ✅ 已验证；特效可用 |
| **PRoot** | 取决于 compositor 和 patch；Adreno Plasma 使用兼容组件 | ⚠️ 取决于配置；见下方已测试项 |

PRoot 不是完整的 Linux 环境，运行桌面或窗口管理器有很多限制，很多路径需要额外
兼容 patch 或资产。下面的结果只是已经测试过的配置，不代表所有桌面都一定不能用，
也不代表任意配置都能用。

### X11 硬件加速

X11 是三种运行环境下的硬件加速桌面路径，和 Termux proot 的方案一致：

| 运行环境 | X11 GPU 路径 | 状态 |
| --- | --- | --- |
| **DroidSpaces** | Adreno 直通（freedreno/kgsl） | ✅ 已用 KDE Plasma X11 实测 |
| **chroot** | Adreno 直通（freedreno/kgsl） | ✅ 已用 KDE Plasma X11 实测 |
| **PRoot** | 宿主 VirGL（`virpipe`）；guest 不打开 `/dev/dri` | ✅ 支持；桌面 session 待实测 |

PRoot 走的是宿主侧 VirGL：应用通过宿主 VirGL/vtest 渲染，类似 Termux
proot；它不是 Adreno 直通，也不需要 guest 打开 `/dev/dri`。

## DroidSpaces — Arch（已 root）

| 桌面 | Adreno | VirGL | llvmpipe |
| --- | --- | --- | --- |
| KDE Plasma Wayland | ✅ 有特效 | ✅ 无特效 | ✅ 无特效 |
| Hyprland | ✅ 有特效 | — | — |
| XFCE Wayland | ✅ | ✅ | ✅ |
| KDE Plasma X11 | ✅ 有特效 | ✅ 无特效 | ✅ 无特效 |
| XFCE X11 | ✅ | ✅ | ✅ |

## chroot — Kali（已 root）

| 桌面 | Adreno | VirGL | llvmpipe |
| --- | --- | --- | --- |
| KDE Plasma Wayland | ✅ 有特效 | ✅ 无特效 | ✅ 无特效 |
| XFCE Wayland | ✅ | ✅ | ✅ |
| KDE Plasma X11 | ✅ 有特效 | ✅ 无特效 | ✅ 无特效 |
| XFCE X11 | ✅ | ✅ | ✅ |

chroot/Kali 下本轮未测试 Hyprland 等其他桌面。

## PRoot — Arch（未 root）

PRoot 不是完整的 Linux 环境，桌面和窗口管理器支持有限，通常需要额外 patch。下表
记录的是已测试配置；没有记录不代表某个桌面永远不能用。

| 桌面 / 显示 | Renderer | 结果 |
| --- | --- | --- |
| **Sway**（Wayland） | llvmpipe / SHM / `pixman` | ✅ 已验证；软件渲染 |
| **Sway**（Wayland） | virgl profile + `buffer = "shm"` + `pixman` | ✅ 已验证；软件渲染 |
| **KDE Plasma Wayland** | adreno + 兼容组件 | ✅ 已验证；需要匹配的 KWin 与 guest GPU userspace stack |
| **KDE Plasma Wayland** | llvmpipe / 软件 | ⚠️ 旧配置；不走 GPU 路径 |
| **Hyprland** | adreno | ❌ 当前测试配置失败；见预览说明 |
| **KDE Plasma X11** | llvmpipe / VirGL | ❌ 当前 KWin/Plasma ABI 下测试 profile 失败；需要匹配的 X11 session |

安装步骤和当前限制见
[PRoot + Adreno + Plasma Wayland](tutorials/05-desktops-proot.md)。

Plasma X11 的失败是 `kwin_x11` 6.7.5 与 Plasma 6.8.2 的包/ABI 不匹配，不能据此
断言 X11 或 VirGL 永远不可用；需要匹配的 X11 session 或 XFCE。

### PRoot 的限制

PRoot 不是完整的 Linux 环境。未 root 的 Android 上，App UID 通常打不开：

```text
/dev/dri/renderD128
/dev/dri/card0
```

`/dev/kgsl-3d0` 可能可用，但 KWin、Aquamarine 等 compositor 仍可能需要 DRM/GBM
设备和 dmabuf feedback。这就是为什么部分桌面路径需要兼容组件、patch，或退回软件
渲染。

已测试例子：

- **Sway** 可以用 `WLR_RENDERER=pixman` + SHM 运行，软件渲染；
  `WLR_RENDERER=gles2` 可能报
  `Cannot create GLES2 renderer: no DRM FD available`；
- **KDE Plasma Wayland** 使用兼容资产；未安装时，当前测试的 KWin
  版本会报：
  ```text
  kwin_core: Configured compositor not supported by Platform. Falling back to defaults
  kwin_core: The used windowing system requires compositing
  kwin_core: We are going to quit KWin now as it is broken
  ```
- **Hyprland** 在当前 Aquamarine 测试配置下失败；更新组件或 patch 后结果可能变化；
- dmabuf device report 可能报 `open /dev/dri: Permission denied`，host 因此可能不会
  发布 GPU compositor 需要的 dmabuf 路径。

这些是当前限制，不是通用兼容性结论。把矩阵当作已验证记录，把指南文档当作安装说明。

## 说明

- 结果来自一台 Adreno 740 设备，不代表所有同 GPU 设备或所有 Android 版本。
- root 会改变功能范围：DroidSpaces 和 chroot 可以使用 Adreno 直通；PRoot 的
  环境不完整，部分路径需要额外 patch 或兼容组件。
- `—` 表示“未测试”，不是“不能用”。桌面结果与配置有关，缺少记录不代表失败。
- DroidSpaces 容器需要提前创建；Trierarch 负责启动和管理它自己启动的容器。
