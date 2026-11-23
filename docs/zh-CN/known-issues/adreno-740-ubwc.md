# Adreno 740：通知栏/悬浮窗出现时花屏

[English](../../en/known-issues/adreno-740-ubwc.md) | 中文

## 症状

- PRoot、DroidSpaces 或 chroot 的硬件加速会话里，桌面/游戏画面出现彩色竖条、块状花屏、
  撕裂或黑闪；
- Trierarch 自己的 UI 正常，只有 guest 画面坏；
- 下拉通知栏、状态栏、音量条、悬浮窗、录屏浮层出现时触发或明显加重；
- 浮层消失后画面恢复；
- 不是某个运行环境专属；三种运行环境都可能出现。

## 范围

- GPU：Adreno 740（骁龙 8 Gen 2 / SM8550）；
- 运行环境：**不限**。PRoot、DroidSpaces、chroot 都可能出现；这是 GPU/显示链路
  的问题，不是某个运行环境专属。

## 原因

Adreno 的 UBWC 压缩 buffer 需要携带正确的压缩标记。Turnip/KGSL 在真实 render
node 上分配 UBWC buffer 时，如果 Android 显示链路没有拿到标记，就会把压缩数据当
原始像素读；通知栏、悬浮窗或录屏浮层会改变 SurfaceFlinger 的合成方式或 buffer
transform，于是随机出现条纹和块状花屏。这不是 Trierarch 容器本身的 bug。

问题出在 GPU 的 UBWC 标记和 Android 显示链路上，不在容器。DroidDeck 本身就是
proot 方案，同样在 Adreno 740 上遇到了这个问题；所以不要把它理解成某个运行环境专属。

DroidDeck 在 Adreno 740 上记录过同类问题（[#1960](https://github.com/Droid-Deck/DroidDeck/issues/1960)、
[#3018](https://github.com/Droid-Deck/DroidDeck/issues/3018)、
[#2710](https://github.com/Droid-Deck/DroidDeck/issues/2710)），它的处理方式是用
`FD_DEV_FEATURES=enable_tp_ubwc_flag_hint=...` 控制这个标记。

## Workaround

在 profile 的 `[launch]` 里，给 `env` 增加一项，放在 `dbus-run-session` 之前：

```toml
[launch]
argv = ["env", "FD_DEV_FEATURES=enable_tp_ubwc_flag_hint=1", "GALLIUM_DRIVER=freedreno", "MESA_LOADER_DRIVER_OVERRIDE=kgsl", "dbus-run-session", "--", "startplasma-wayland"]
```

Hyprland profile 同理，只把最后的 `startplasma-wayland` 换成 `start-hyprland`；
保留你原有的其它 `env` 项。

如果 profile 使用 shell 启动：

```sh
export FD_DEV_FEATURES=enable_tp_ubwc_flag_hint=1
exec ...
```

先试 `1`。如果没有效果或更糟，改成 `0` 再试：

```toml
"FD_DEV_FEATURES=enable_tp_ubwc_flag_hint=0",
```

修改后必须重启 guest 会话/合成器，环境变量才会生效。

在 guest 内确认：

```sh
tr '\0' '\n' < /proc/$(pidof kwin_wayland)/environ | grep FD_DEV_FEATURES
```

Hyprland 用：

```sh
tr '\0' '\n' < /proc/$(pidof Hyprland)/environ | grep FD_DEV_FEATURES
```

## 说明

- 这是针对 Adreno 740 的 workaround；对所有 Adreno 740 profile 都适用，
  包括 PRoot、DroidSpaces、chroot。不要加到其他 GPU 的 profile。
- `1` 保留 UBWC 压缩；`0` 可能是让显示链路按未压缩/未标记处理，带宽和功耗可能
  变差。能用 `1` 就保持 `1`。
- 如果以后系统或驱动更新后又出现，重新试 `1` / `0`。
- 它不是容器级开关；三种运行环境出现症状时都可以加。
