# Trierarch

[English](README.md) | 中文

### 口袋里的全能 Linux 工作站。

Trierarch 把 Android 手机或平板变成一台本地 Linux 机器。它提供真实终端、
X11 和 Wayland 桌面，并通过 PRoot、chroot、DroidSpaces 三种方式运行 Linux
用户空间。

它不是虚拟机，不做 CPU 模拟，也不是远程桌面。Linux 环境直接运行在你手里的
设备上；Trierarch 负责显示、输入、音频、GPU 路径和会话生命周期这些容易出问题
的部分。

## Trierarch 的重制版

这是 **Trierarch 的重制版**。早期 Trierarch 的代码和早期优化笔记已经被重写，
当前仓库是新的实现；Android App、native 运行时、打包方式和会话模型都以当前
代码为准。早期 workaround 只保留在 known issues 里，供旧环境参考。

## 为什么选择 Trierarch

很多在 Android 上跑 Linux 的方案止步于 shell，最多到 X11。Trierarch 最大的
区别是：它还能提供一个真正的 Wayland 会话——嵌套运行 KDE Plasma（KWin）或
Hyprland 这类桌面/窗口管理器，保留原生特效、动画和窗口管理能力。

这正是这个项目围绕的能力。shell 和 X11 也支持，但它们不是 Trierarch 的核心
差异。

在此之上，Trierarch 把通常的一堆脚本、socket、环境变量和半配置好的桌面变成
一个受管理的会话：

- 从 profile 启动运行环境；
- 接上终端、X11 显示或 Wayland 桌面；
- 转发触摸、键盘、鼠标和输入法；
- 通过 PipeWire 输出音频；
- 会话结束时停掉自己启动的东西。

它面向的是想要一台随身、本地 Linux 机器的人，而不是远程桌面或玩具终端。

## 可以运行什么

| 运行环境 | Root | 适合 |
| --- | --- | --- |
| **PRoot** | 不需要 | 不需要 root 的 Linux 用户空间，放在 App 私有存储里 |
| **chroot** | 需要 | 设备上的完整 rootfs，带会话 supervisor，App 消失时收尾 |
| **DroidSpaces** | 需要 | 带独立 init/systemd 的容器，由 Trierarch 启动并管理生命周期 |

Trierarch 只提供外壳，不提供发行版。rootfs 或 DroidSpaces 容器由你准备；
Trierarch 负责启动、显示和管理它的生命周期。

## 运行环境范围与扩展

Trierarch 的会话模型并不绑定这三种运行环境，理论上也可以适配其他在 Android 上
运行 Linux 的方案。

当前维护 **PRoot、chroot、DroidSpaces**，是因为开发者本人主要使用这三种，
不代表其他方案不可行，也不是设计上不支持。

如果你希望加入其他运行态，请在 issues 里留言说明你的使用场景。新增运行态需要
设计、实现和测试，开发也需要时间，请耐心等待。

## 桌面和图形

- **终端**：真实 TTY，支持触摸滚动、软键盘和硬件键盘；
- **X11**：通过 Trierarch 内置的 X 服务器运行 XFCE、KDE 等 X11 桌面；
- **Wayland**：通过 Trierarch 的 Wayland 宿主运行 KDE Plasma、Hyprland
  等嵌套合成器；
- **图形路径**：llvmpipe 软件渲染、VirGL、Venus 或 Qualcomm Adreno 直通，
  取决于设备和 profile。

图形加速与设备和 profile 强相关。llvmpipe 是兼容路径；VirGL、Venus、Adreno
需要匹配的 guest/host 栈。Trierarch 不保证每台手机都有相同的 GPU 行为。

## 设备支持与稳定性

Trierarch 主要面向并推荐 **Qualcomm Snapdragon（高通骁龙）设备**，尤其是
Adreno GPU 的手机和平板。freedreno/Turnip/Adreno 图形栈在这些设备上测试最多，
体验也相对完整。

其他 SoC 可以运行终端、PRoot、chroot 和 DroidSpaces，但 GPU 驱动相对不完善，
功能缺失会更多，更容易回退到软件渲染，也更容易遇到设备相关的兼容问题。

桌面方面，**X11 目前是更稳定的目标**，VirGL 之类的图形方案在 X11 下仍可稳定
使用。Wayland 窗口管理器和桌面本身在快速更新，很多 X11 下稳定的方案所依赖的
技术栈在新版本里被删除或修改；嵌套 Wayland 的环节也更多。我们会继续支持
Wayland，但它确实是更容易出问题的部分。

## 输入、音频和存储

- 触摸、物理键盘、鼠标和虚拟键盘输入；
- 桌面会话的 IME bridge；
- PRoot、chroot、DroidSpaces 的 PipeWire 音频；
- App 私有存储中的 profile 和 rootfs 镜像；
- Android document provider，用于导入/导出文件。

## 会话生命周期

把 Linux 桌面留在后台很有用，留下一个孤儿容器不是。

- **PRoot** 进程跟着 App/PTY 走，会话结束就结束；
- **chroot** 由 owner supervisor 监控，App 消失后结束会话；
- **DroidSpaces** 中由 Trierarch 启动的容器，在 App 退出或被 force-stop 时
  会被停止。

Trierarch 不会停止在它附着之前就已经运行的容器，只清理自己启动的东西。

## 快速开始

1. 安装 Trierarch APK；
2. 打开 App，进入内置 shell；
3. 运行 `trierarch --help`；
4. `trierarch --create my-profile` 创建 profile；
5. `trierarch --config my-profile` 编辑；
6. `trierarch --run my-profile` 启动。

第一个 profile 可以是 PRoot 环境、chroot rootfs，或已有的 DroidSpaces 容器。

## 系统要求

- Android 7.0 或更新；
- **推荐硬件：Qualcomm Snapdragon + Adreno 图形**。其他 SoC 可以跑核心路径，
  但 GPU 驱动相对不完善；
- PRoot：不需要 root；
- chroot：可用的 `su`；
- DroidSpaces：root、DroidSpaces 运行时和兼容的内核；
- 一个可用的 rootfs 或 DroidSpaces 容器。

## 项目状态

Trierarch 仍然是一个**早期阶段的项目**。终端、PRoot、chroot 和核心桌面路径
已经可用，但它还不是一个成熟的产品。

目前有些问题确实会让人不满：GPU 和嵌套 Wayland 的表现会因设备和发行版而异，
音频、输入可能存在设备相关的兼容问题，部分图形路径仍是实验性的。请预期会有
粗糙的地方、不完整的功能，甚至偶发的故障。

开发者正在积极修复和改进这些问题，兼容性、清理逻辑和可用性都在持续处理中。
如果你在设备上遇到问题，它没有被忽略，而是仍在这项工作里。

## 上游项目与版权

Trierarch 建立在很多上游项目之上，并不拥有它们的代码、二进制或数据。

| 组件 | 上游 / 固定版本 | 用途 | 许可证 |
| --- | --- | --- | --- |
| **Termux terminal** | termux/termux-app，commit `3df69d1...` | `com.termux.terminal` / `com.termux.view` | Termux 仓库 GPL-3.0；terminal 两模块 Apache-2.0 exception |
| **Termux:X11 / Lorie** | termux/termux-x11，commit `9471ad9...` | `libXlorie.so` 和 XKB 资源 | GPL-3.0-or-later |
| **PRoot** | termux/proot，commit `a89b373...` | `proot` / `loader` | GPL-2.0-only |
| **virglrenderer** | `1.3.0` | Android vtest/Venus 主机 | MIT |
| **Mesa 3D** | guest Mesa | virpipe 等 guest 路径 | MIT |
| **ANGLE** | Termux 包 `2.1.24923...` | VirGL 主机的 Vulkan/GL 后端 | BSD-3-Clause |
| **libepoxy** | `1.5.10` | EGL/GL dispatch | MIT |
| **PipeWire** | `1.4.9-1` | Android 音频服务 | MIT |
| **WirePlumber** | `0.5.13` | 会话/策略管理 | MIT |
| **Local Desktop AAudio sink** | commit `0543368...` | AAudio `Audio/Sink` | GPL-3.0 |
| **Wayland / wayland-protocols** | Wayland 项目 | 宿主合成器与协议 | MIT |
| **libffi** | libffi | Wayland server | MIT |
| **X.Org XKB** | XKeyboardConfig | Lorie 的 XKB 规则 | X11-style |
| **DroidSpaces** | Droidspaces-OSS | 可选外部容器运行时 | GPL-3.0 |
| **AndroidX / Kotlin** | AOSP / JetBrains | App UI 与 Kotlin 运行时 | Apache-2.0 |

补充：

- PRoot 构建还使用 **talloc**（LGPL-3.0-or-later）和
  **libandroid-shmem**（BSD-3-Clause）；见 packages 仓库的
  `trierarch-packages/proot/UPSTREAM.md`。
- KDE Plasma/KWin、Hyprland/aquamarine、XFCE 和各 Linux 发行版属于 guest
  环境，由用户提供的 rootfs 或容器提供，Trierarch 不打包它们。
- 精确 pin、补丁和来源说明见各包的 `UPSTREAM.md` 以及
  [Termux 跟踪](docs/upstream-termux-terminal.md) 和
  [Termux:X11 跟踪](docs/upstream-termux-x11.md)。
- 部分组件是 copyleft。重新分发二进制时请保留版权/许可证声明并遵守对应
  许可证；Trierarch 不会重新授权它们。

## 许可证

Trierarch 以 **GNU General Public License version 3** 发布，全文见
[LICENSE](LICENSE)。

上游组件保留各自的许可证；见「上游项目与版权」以及各包 `UPSTREAM.md` 里的
精确 pin。Trierarch 不会重新授权它们。特别注意：

- Termux 的 `terminal-emulator` 和 `terminal-view` 模块保留 Apache-2.0
  exception；
- Termux:X11 / Lorie 和 Local Desktop AAudio sink 是 GPL-3.0；
- PRoot 是 GPL-2.0-only，作为独立可执行文件分发，不链接进 Trierarch App。

## 文档

- [中文手册](docs/zh-CN/README.md) — 安装、配置、运行环境、显示、图形、音频、
  生命周期和故障排查；
- [English manual](docs/en/README.md)；
- [兼容性矩阵](docs/compatibility.zh-CN.md) — 已测试的运行环境、桌面和
  renderer 组合；
- [文档索引](docs/README.md)；
- [已知问题](docs/known-issues/README.md) — 每个问题单独一篇，中英文都有。
