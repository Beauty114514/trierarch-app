# Hyprland 在嵌套 Wayland 下黑屏

[English](../en/hyprland-aquamarine.md) | 中文

## 现象

Hyprland 启动了，但桌面是黑的，Trierarch 渲染器一直是：

```
commits=0 damage=0 renders=0
```

guest 的 Hyprland 日志显示 aquamarine 使用了 **headless** 输出：

```
Output WAYLAND-1: initialized
backend: poll fd ... for implementation headless
CHeadlessOutput::scheduleFrame ...
```

宿主输出本身是存在的：

```
interface: 'wl_output', version: 4
    name: Trierarch-1
```

## 原因

较旧的发行版 aquamarine 在嵌套 Wayland 输出处理上有回归。上游修复：

```
163b138 headless: reuse parent Wayland buffer formats when nested (#451)
```

## 处理

如果发行版仓库已有修复后的包，直接安装。否则在 guest 内从最新源码编译：

```bash
pacman -S --needed --noconfirm cmake ninja git \
    hyprutils wayland wayland-protocols libdrm libinput seatd \
    libdisplay-info mesa libglvnd pixman

rm -rf /tmp/aquamarine /tmp/aquamarine-build
git clone --depth 1 https://github.com/hyprwm/aquamarine /tmp/aquamarine
cmake -S /tmp/aquamarine -B /tmp/aquamarine-build \
    -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX=/usr
cmake --build /tmp/aquamarine-build -j4
cmake --install /tmp/aquamarine-build
ldconfig
```

重启 Hyprland，宿主侧应出现非零的 `commits`/`renders`。

## 注意

- 只影响 Hyprland/aquamarine；KWin/Plasma 不使用 aquamarine。
- `AQ_BACKENDS` 不是 aquamarine 的有效变量，不要用它强制后端。
- 修好黑屏后单个应用窗口闪不在这个问题的范围内。
