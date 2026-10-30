# 兼容性矩阵

最后更新：2026-10-30

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
| **PRoot** | 否 | 当前测试配置下只能软件渲染；KWin/Plasma/Hyprland 无法初始化 GPU 合成器 | 宿主 **VirGL**（`virpipe`），guest 不需要 `/dev/dri` |

### Wayland 硬件加速

Wayland 是主要桌面路径，也是 Plasma/Hyprland 特效的来源。它的 GPU 路径取决于
运行环境：

| 运行环境 | Wayland GPU 路径 | 状态 |
| --- | --- | --- |
| **DroidSpaces** | Adreno 直通（freedreno/kgsl） | ✅ 已验证；特效可用 |
| **chroot** | Adreno 直通（freedreno/kgsl） | ✅ 已验证；特效可用 |
| **PRoot** | 只能软件渲染（pixman/SHM），没有 GPU 合成器 | ✅ 已验证软件路径；Plasma/Hyprland 失败 |

未 root 的 PRoot 下，Android SELinux 拒绝 `/dev/dri`，KWin 和 Aquamarine 无法
初始化 GPU 合成器，所以 PRoot Wayland 只能软件渲染。

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

| 桌面 / 显示 | Renderer | 结果 |
| --- | --- | --- |
| **Sway**（Wayland） | llvmpipe / SHM / `pixman` | ✅ 已验证；软件渲染 |
| **Sway**（Wayland） | virgl profile + `buffer = "shm"` + `pixman` | ✅ 已验证；软件渲染 |
| **KDE Plasma Wayland** | adreno 或 llvmpipe | ❌ 失败；见下文 |
| **Hyprland** | adreno | ❌ 失败；见下文 |
| **KDE Plasma X11** | llvmpipe / VirGL | ❌ 当前 KWin/Plasma ABI 下测试 profile 失败；安装匹配的 X11 session 后，X11 + VirGL 仍是支持路径 |

上面的 Plasma X11 失败是 `kwin_x11` 6.7.5 与 Plasma 6.8.2 的包/ABI 不匹配，
不是 X11 或 VirGL 的限制；需要安装匹配的 X11 session 或 XFCE。

### PRoot Wayland 为什么只能软件渲染

未 root 的 Android 设备上，App UID 打不开：

```text
/dev/dri/renderD128
/dev/dri/card0
```

Android SELinux 同时拒绝 App 和 ADB shell。`/dev/kgsl-3d0` 可能可打开，但
KWin 和 Aquamarine 仍然需要 DRM/GBM 设备和 dmabuf feedback 才能初始化
GPU 合成器。

结果是：

- **Sway** 只能用 `WLR_RENDERER=pixman` + SHM 启动，桌面是软件渲染；
  `WLR_RENDERER=gles2` 会报
  `Cannot create GLES2 renderer: no DRM FD available`；
- **KDE Plasma Wayland** 在出帧前就失败，KWin 6.8.2 报：
  ```text
  kwin_core: Configured compositor not supported by Platform. Falling back to defaults
  kwin_core: The used windowing system requires compositing
  kwin_core: We are going to quit KWin now as it is broken
  ```
- **Hyprland** 在 Aquamarine 中失败：DRM backend 打不开 seat，Wayland
  backend 报 `Missing protocols`，没有 allocator，随后
  `CBackend::create()` 崩溃；
- dmabuf device report 也报 `open /dev/dri: Permission denied`，host 因此
  不会发布 GPU 合成器需要的 dmabuf 路径。

所以 PRoot Wayland 可以跑软件 Sway，但不是未 root 设备上的 GPU 桌面路径。

## 说明

- 结果来自一台 Adreno 740 设备，不代表所有同 GPU 设备或所有 Android 版本。
- root 会改变功能范围：DroidSpaces 和 chroot 可以使用 Adreno 直通；未 root 的
  PRoot 不能。
- `—` 表示“未测试”，不是“不能用”。桌面结果与配置有关，缺少记录不代表失败。
- DroidSpaces 容器需要提前创建；Trierarch 负责启动和管理它自己启动的容器。
