# Trierarch 使用手册

Trierarch 是一个 Android 上的 Linux 运行环境外壳。它把 Linux 终端、X11 桌面、
Wayland 桌面、PRoot、chroot 和 DroidSpaces 容器接到同一个 App 里，并负责：

- 启动和停止运行环境；
- 把终端或桌面显示到 Android 上；
- 转发触摸、键盘、鼠标和输入法；
- 管理 PipeWire 音频；
- 在 App 退出时收尾 Trierarch 自己启动的会话和容器。

Trierarch 不是虚拟机，也不是把 Android 换成 Linux。它运行在 Android 上，
使用设备内核和 Android 的显示、输入、音频设施。

---

## 1. 你能用它做什么

- 在手机上打开一个真正的 Linux shell；
- 运行 Arch、Debian、Ubuntu、Alpine 等发行版的 rootfs；
- 通过 PRoot 在**不需要 root** 的情况下运行 Linux 用户空间；
- 通过 chroot 以 root 运行完整 rootfs；
- 通过 DroidSpaces 运行带独立 init/systemd 的容器；
- 打开 X11 或 Wayland 桌面（如 XFCE、KDE Plasma、Hyprland）；
- 在有条件的设备上使用 llvmpipe、VirGL、Venus 或 Adreno 图形路径；
- 把容器和桌面会话的生命周期交给 Trierarch 管理。

---

## 2. 三种运行环境

Trierarch 用 profile 描述“运行哪个环境、怎么显示、用什么图形”。

### PRoot

- 适合没有 root 的设备；
- rootfs 放在 App 私有目录；
- 进程是 App/PTY 的子进程，App 退出后自然结束；
- 支持终端、X11、Wayland 和 PipeWire。

### chroot

- 需要 root（Magisk/KernelSU/APatch 等提供的 `su`）；
- rootfs 是设备上的一个目录；
- Trierarch 用 session supervisor 监控 App 进程，App 消失时结束会话；
- 支持终端、X11、Wayland 和 PipeWire。

### DroidSpaces

- 需要 root，并且设备上已安装 DroidSpaces 运行时和容器；
- 容器有独立的 init（systemd/OpenRC 等），可以比 Trierarch App 活得更久；
- Trierarch 启动容器，并在 App 退出时停止**由 Trierarch 启动的**容器；
- 原本就在运行的容器不会被 Trierarch 自动停止；
- 支持终端、X11、Wayland 和 PipeWire。

> DroidSpaces 容器需要先在 DroidSpaces 中创建。Trierarch 负责启动、附着和
> 生命周期管理，不负责创建容器。

---

## 3. 系统要求

- Android 7.0 或更新（`minSdk 24`）；
- PRoot：不需要 root；
- chroot：需要可用的 `su`；
- DroidSpaces：需要 root、受支持的内核和 DroidSpaces 运行时；
- **推荐硬件：Qualcomm Snapdragon（高通骁龙）+ Adreno 图形**。其他 SoC 可以跑
  核心路径，但 GPU 驱动相对不完善；
- 图形加速取决于设备 GPU 和驱动，不是所有设备都支持所有 renderer；
- **X11 目前是更稳定的桌面目标**，VirGL 等方案在 X11 下仍可稳定使用。Wayland
  合成器和桌面更新很快，X11 稳定方案依赖的技术栈常被新版本删除或修改，嵌套
  Wayland 更容易出问题。

---

## 4. 安装与第一次启动

1. 安装 Trierarch APK；
2. 打开 App，会进入内置 shell；
3. 在 shell 里运行：

   ```sh
   trierarch --help
   ```

4. 创建一个 profile：

   ```sh
   trierarch --create my-profile
   ```

5. 编辑它：

   ```sh
   trierarch --config my-profile
   ```

   `--config` 会启动 `$EDITOR`，默认尝试 `vi`。Android 自带 shell **不包含**
   `vi`、`nano` 之类编辑器。如果你的 `PATH` 里有编辑器，把 `EDITOR` 指向它；
   否则用 Android 文本编辑器通过 Trierarch 的 document provider（Storage
   Access Framework）编辑生成的 profile，或在开发时用 `adb` pull/edit/push。

6. 运行：

   ```sh
   trierarch --run my-profile
   ```

运行后，Trierarch 会根据 profile 的 `display.type` 显示终端、X11 或 Wayland。

---

## 5. Profile 管理

Profile 是 App 私有目录下的 TOML 文件：

```
/data/data/app.trierarch/files/config/profiles/<id>.toml
```

通常不需要直接改路径，用 CLI 管理：

```sh
trierarch --list              # 列出 profile
trierarch --status            # 查看所有运行状态
trierarch --status ID         # 查看一个 profile
trierarch --create ID         # 创建模板
trierarch --config ID         # 用编辑器修改
trierarch --run ID            # 启动
trierarch --stop [ID]         # 停止当前或指定会话
trierarch --rerun ID          # 重启
trierarch --delete ID         # 删除
```

Profile ID 规则：

- 只能用小写英文字母、数字和 `-`；
- 必须以字母开头；
- 最长 64 个字符。

### PRoot rootfs 导入

```sh
trierarch --import rootfs.tar.xz --name arch
```

导入后，`--create` 的模板会提示你填写 rootfs 的绝对路径；也可以直接使用导入
命令输出的路径。

```sh
trierarch --remove arch
```

---

## 6. Profile 字段

最小模板：

```toml
id = "my-profile"
runtime = "proot"

rootfs = "/absolute/path/to/rootfs"
shell = "/bin/bash"

[display]
type = "none"
```

### 顶层字段

| 字段 | 说明 |
| --- | --- |
| `id` | 必填，profile 的唯一 ID |
| `runtime` | `proot`、`chroot` 或 `droidspaces` |
| `rootfs` | PRoot/chroot 的 rootfs 绝对路径 |
| `shell` | PRoot/chroot 内的 shell 绝对路径 |
| `container` | DroidSpaces 容器名 |
| `user` | DroidSpaces 登录用户 |
| `name` | 可选显示名 |

### 显示

```toml
[display]
type = "none"     # none / x11 / wayland
```

- `none`：终端；
- `x11`：通过 Trierarch 内置的 X 服务器显示；
- `wayland`：通过 Trierarch 的 Wayland 宿主显示，guest 运行嵌套合成器。

### 启动命令

```toml
[launch]
argv = ["startplasma-wayland"]
```

`argv` 是 guest 内要执行的命令。留空时使用默认 shell 或发行版会话。

### 图形

```toml
[graphics]
renderer = "llvmpipe"
buffer = "auto"
qt_quick_backend = "auto"
```

| renderer | 说明 |
| --- | --- |
| `llvmpipe` | 软件渲染，兼容性最好 |
| `virgl` | 主机侧 VirGL/vtest 路径，适合合成器加速 |
| `venus` | Vulkan/Venus，实验性 |
| `adreno` | Qualcomm Adreno 直通（freedreno/kgsl），仅 Wayland |

说明：

- `virgl`/`venus` 需要 X11 或 Wayland；
- `adreno` 支持 DroidSpaces、chroot 和 PRoot，但仅限 Wayland；
- `virgl` 当前主要加速**合成器**；普通应用的 OpenGL 是否走硬件取决于 guest
  Mesa 和显示栈，不能保证所有应用都加速；
- `buffer` 默认 `auto`：`virgl`/`venus` 默认 `shm`，其他默认 `dmabuf`；
- `qt_quick_backend = "software"` 常用于 Plasma + virgl 的组合，避免 Qt Quick
  在 guest 里找不到可用 GL。

### Adreno Mesa 驱动：Arch 系和 Deb 系

`adreno` renderer 需要 guest 内安装匹配发行版的 Mesa。Trierarch 在
`trierarch-packages/mesa-adreno/` 里提供两份配方：

- `arch/`：Arch Linux ARM（pacman 包）；
- `debian/`：Kali/Debian（deb 包）。

**不要混用。** 不要把 Arch 包装进 Debian guest，也不要把 deb 包装进 Arch
guest。

#### 推荐：自己编译

推荐自己编译。Mesa 和 Adreno 更新很快，版本变化可能让补丁队列或打包配方
失效；这时可以用 AI 协助快速更新补丁/配方，比等新的 release 构建快得多。

Arch guest：

```bash
cd /path/to/trierarch-packages/mesa-adreno
./arch/build.sh
```

把生成的所有包一起安装。本地构建的包没有签名，用临时 pacman 配置，不要修改
永久的 `/etc/pacman.conf`：

```bash
cd .build/archlinuxarm-PKGBUILDs/extra/mesa
cp /etc/pacman.conf /tmp/pacman-nosig.conf
sed -i 's|^SigLevel.*|SigLevel = Never|' /tmp/pacman-nosig.conf
sudo pacman --config /tmp/pacman-nosig.conf -U --noconfirm ./*.pkg.tar.*
```

Debian/Kali guest（先启用匹配的 `deb-src`，并安装 Mesa 构建依赖）：

```bash
cd /path/to/trierarch-packages/mesa-adreno
./debian/build.sh
./debian/install.sh .debian-build
```

Debian 配方会在打补丁前检查源码包版本。升级 Mesa 时请更新补丁队列和版本检查，
不要把旧补丁强行套到新源码上。依赖和排错细节见 `mesa-adreno/README.md`。

#### 备选：Trierarch release 提供的驱动（相对不推荐）

Trierarch 的 release 计划提供两份匹配的驱动包：一份给 Arch 系，一份给 Deb 系。
按 guest 发行版选择对应的包，用该发行版的包管理器安装。

这很方便，但相对不推荐：开发者的重新构建节奏可能因为各种原因落后于 Mesa/Adreno
的变化，release 驱动可能比你的桌面或游戏期望的 Mesa 版本旧。

#### 验证

在 guest 内：

```bash
vulkaninfo --summary
eglinfo -B
```

Adreno/Turnip 路径的 renderer 应该包含 `Turnip`/`Adreno`。Zink/Turnip 检查的
具体环境变量见 `trierarch-packages/mesa-adreno/README.md`。

### 音频

```toml
[audio]
backend = "pipewire"
```

- `off`：不启动音频；
- `pipewire`：DroidSpaces、PRoot、chroot 支持；内部 shell 不支持。

### 兼容项

```toml
[compat]
udev_monitor = "auto"
```

- `auto`：为 KWin 等桌面启用 Trierarch 的 udev 监控兼容层；
- `off`：关闭。

---

## 7. 常见场景

### 7.1 PRoot 终端

```toml
id = "arch-terminal"
runtime = "proot"
rootfs = "/data/data/app.trierarch/files/rootfs/arch"
shell = "/bin/bash"

[display]
type = "none"
```

```sh
trierarch --run arch-terminal
```

### 7.2 PRoot + X11

```toml
id = "arch-x11"
runtime = "proot"
rootfs = "/data/data/app.trierarch/files/rootfs/arch"
shell = "/bin/bash"

[display]
type = "x11"

[launch]
argv = ["startxfce4"]

[graphics]
renderer = "llvmpipe"
```

### 7.3 chroot + Wayland

```toml
id = "arch-wayland"
runtime = "chroot"
rootfs = "/data/local/my-rootfs"
shell = "/bin/bash"

[display]
type = "wayland"

[launch]
argv = ["startplasma-wayland"]

[graphics]
renderer = "llvmpipe"
```

chroot 需要 `su`；Trierarch 会用 supervisor 管理会话。

### 7.4 DroidSpaces + Plasma Wayland + VirGL

```toml
id = "arch-plasma-wayland"
runtime = "droidspaces"
container = "Arch"
user = "beauty"

[display]
type = "wayland"

[launch]
argv = ["startplasma-wayland"]

[graphics]
renderer = "virgl"
buffer = "dmabuf"
qt_quick_backend = "software"

[compat]
udev_monitor = "auto"

[audio]
backend = "pipewire"
```

运行：

```sh
trierarch --run arch-plasma-wayland
```

### 7.5 DroidSpaces + Hyprland + Adreno

```toml
id = "arch-hyprland"
runtime = "droidspaces"
container = "Arch"
user = "beauty"

[display]
type = "wayland"

[launch]
argv = ["start-hyprland"]

[graphics]
renderer = "adreno"

[compat]
udev_monitor = "auto"
```

`adreno` 需要 Qualcomm 设备和可用的 Adreno 驱动；不支持的设备会启动失败或
黑屏。

---

## 8. 界面和输入

- 终端界面支持触摸滚动、软键盘和硬件键盘；
- 进入 X11/Wayland 后，右下角浮动菜单可以：
  - 返回 Trierarch shell；
  - 显示/隐藏软键盘；
  - 切换虚拟键盘的文本/按键模式；
- 外部鼠标、键盘和触控输入会转发给当前显示的会话；
- 桌面下的输入法由 Trierarch 的 IME 桥接处理。

停止一个图形会话：

```sh
trierarch --stop
```

或指定 profile：

```sh
trierarch --stop arch-plasma-wayland
```

---

## 9. 生命周期和后台行为

### PRoot

PRoot 进程是 Trierarch App/PTY 的子进程。App 退出、PTY 关闭或会话停止时，
PRoot 进程随之结束。

### chroot

chroot 由 `trierarch-session-supervisor` 的 owner 模式监控：

- 记录 App 的 PID 和 starttime；
- App 消失或 PID 被复用时，结束会话进程组；
- 因此不需要单独打开别的 App 来清理 chroot。

### DroidSpaces

DroidSpaces 容器有独立 init，不能靠关闭 PTY 结束。Trierarch 在**自己启动**
容器前启动 owner supervisor：

- App 进程消失后，supervisor 执行：
  ```sh
  droidspaces --name=<container> stop
  ```
- 只对 Trierarch 启动的容器生效；
- 如果容器在 Trierarch 启动前已经运行，Trierarch 不会在退出时停止它；
- 正常停止仍可使用：
  ```sh
  trierarch --stop
  ```

因此 App 被后台杀掉后，不需要再进 DroidSpaces App 手动关闭 Trierarch 启动的
容器。

---

## 10. 故障排查

### 提示 “container is already running without a required Trierarch attachment”

这个容器已经在运行，但不是当前 Trierarch 会话启动的，缺少本次 profile 需要的
绑定/套接字。

处理：

```sh
trierarch --stop
```

如果它不是 Trierarch 启动的，需要在 DroidSpaces 中停止：

```sh
droidspaces --name=Arch stop
```

然后重新运行 profile。

### 黑屏：只有 Hyprland

如果 Hyprland 进程在，但宿主的 `TrierarchRenderer` 一直是：

```
commits=0 damage=0 renders=0
```

检查 aquamarine 版本。发行版自带的旧版 aquamarine 可能在嵌套 Wayland 下退回
headless 输出。

处理方式见：

- [已知问题](../known-issues/zh-CN/hyprland-aquamarine.md)

核心是：如果官方源没有包含修复，就从最新源码编译安装 aquamarine。

### 只有某个应用窗口闪

如果桌面本身稳定，只有 SuperTuxKart 等某个应用窗口闪，那不是 Trierarch 的
整屏 AHB/token 路径问题，而是该应用自己的渲染/SDL/Wayland 交换链或 KWin 对该
客户端的合成问题。

可以先试：

```sh
vblank_mode=1 supertuxkart
```

或：

```sh
SDL_VIDEODRIVER=x11 DISPLAY=:0 supertuxkart
```

以及用 `glmark2-wayland` 对比。

### 图形 renderer 不工作

- `virgl`：需要匹配的宿主 virgl/vtest 和 guest Mesa；
- `venus`：实验性；
- `adreno`：需要 Qualcomm GPU 和 kgsl/freedreno；
- `llvmpipe`：软件渲染，兼容性最好，但性能有限。

先用 `llvmpipe` 确认会话本身正常，再逐项启用图形路径。

### 查看日志

Android 侧：

```sh
adb logcat -s TrierarchTvg TrierarchRenderer TrierarchWayland
```

App 私有文件：

```sh
adb shell run-as app.trierarch ls files
adb shell run-as app.trierarch cat files/virgl-run/virgl_host.log
```

guest 内：

```sh
/tmp/trierarch-wayland/kwin.log
/tmp/trierarch-wayland-host/ime/trierarch-session-supervisor.log
```

DroidSpaces supervisor：

```sh
cat /data/local/tmp/trierarch-droidspaces-supervisor.log
```

---

## 11. 已知限制

- Trierarch 仍是一个持续开发中的运行环境，图形/显示路径会因设备而异；
- chroot 和 DroidSpaces 需要 root；
- DroidSpaces 需要设备内核和运行时支持；
- VirGL 当前主要服务合成器路径，普通应用不一定获得硬件 OpenGL；
- Venus、dmabuf、Adreno 直通等路径带有实验性质；
- 不同 GPU、Android 版本和发行版组合的兼容性不同；
- 没有“一键安装所有发行版”的通用镜像，需要自己准备 rootfs 或 DroidSpaces
  容器。

---

## 12. 构建

开发构建需要：

- JDK 21；
- Android SDK/NDK；
- Rust 和 `cargo-ndk`；
- Meson/Ninja；
- 构建 guest 资源所需的交叉编译器（如 `aarch64-linux-gnu-gcc`）。

构建 APK：

```sh
./gradlew :app:assembleDebug
```

产物：

```
app/build/outputs/apk/debug/app-debug.apk
```

构建脚本会同时编译：

- Rust PTY/native 库；
- PRoot 运行库；
- X11/Termux:X11；
- VirGL/Venus 组件；
- Wayland IME bridge；
- chroot session supervisor；
- DroidSpaces owner supervisor；
- PipeWire 运行时。

---

## 13. 开发者参考

- [Documentation index](../README.md)
- [English manual](../en/README.md)
- [已知问题](../known-issues/README.md)
- [Upstream: Termux terminal](../upstream-termux-terminal.md)
- [Upstream: Termux:X11](../upstream-termux-x11.md)
