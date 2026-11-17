# PRoot — Hyprland

[English](../../en/tutorials/05-desktops-proot-hyprland.md) | 中文

[返回 PRoot 桌面总览](05-desktops-proot.md)

先完成[公共 Mesa 26 + Turnip 准备](05-desktops-proot.md)，然后配置 Hyprland。
Hyprland 还需要 `libaquamarine-compat.so` 和壁纸 daemon。

## 安装包

在 guest 内：

```sh
pacman -S --needed hyprland dbus swaybg
command -v Hyprland
command -v start-hyprland
command -v dbus-run-session
command -v swaybg
```

需要完整桌面时再加：

```sh
pacman -S --needed \
  kitty waybar wofi mako \
  ttf-jetbrains-mono-nerd noto-fonts noto-fonts-cjk
```

`--needed` 会跳过已安装的包。`dbus` 提供 `dbus-run-session`；`swaybg` 负责画壁纸。

## Aquamarine 兼容库

PRoot Hyprland 使用一个合并库：

```text
libaquamarine-compat.so
```

它作为 Trierarch release 资产发布。如果从源码构建，在
`trierarch-packages/aquamarine-compat` 里执行 `make combined`。

在 guest 内安装：

```sh
install -d /opt/trierarch/compat
install -m 755 /path/to/libaquamarine-compat.so /opt/trierarch/compat/
```

Hyprland 不要安装 KWin compatibility library。

## Profile

使用样例：

- [`arch-proot-hyprland-adreno.toml`](../../profiles/arch-proot-hyprland-adreno.toml)

样例额外加了两行 PRoot 专用设置：

```sh
ln -sfn /tmp/trierarch-wayland-user /tmp/t
export XDG_RUNTIME_DIR=/tmp/t
```

Hyprland 的 IPC socket 路径原本刚好卡在 Unix socket 上限，`hyprctl`/Waybar
连不上；软链把路径缩短，同时不移动 host Wayland socket。

启动命令使用上游推荐的 wrapper：

```sh
exec dbus-run-session -- start-hyprland -- --i-am-really-stupid
```

`dbus-run-session` 提供 session bus；`--` 把 `--i-am-really-stupid` 透传给
Hyprland，因为 PRoot guest 以 root 运行。

## 壁纸

把图片放进 guest，例如：

```text
/home/user/Pictures/luca-micheli.jpg
```

从宿主机复制的一种方式：

```sh
adb push /path/to/luca-micheli.jpg /data/local/tmp/
adb shell run-as app.trierarch mkdir -p \
  /data/data/app.trierarch/files/rootfs/arch/home/user/Pictures
adb shell run-as app.trierarch cp \
  /data/local/tmp/luca-micheli.jpg \
  /data/data/app.trierarch/files/rootfs/arch/home/user/Pictures/
```

在 Hyprland 的 Lua 配置里启动 `swaybg`：

```lua
hl.on("hyprland.start", function()
    hl.exec_cmd("GLYCIN_DISABLE_SANDBOX=i-know-the-risks swaybg -i /home/user/Pictures/luca-micheli.jpg -m fill >/tmp/swaybg.log 2>&1")
end)
```

Arch 的图片 loader 使用 glycin，默认会启动 `bwrap` 沙箱。PRoot 里 `bwrap`
起不来，所以不加 `GLYCIN_DISABLE_SANDBOX` 时图片会静默加载失败，桌面保持黑色。
这个变量让 loader 不使用沙箱；只对可信的本地图片使用。

这里不要用 `hyprpaper`。它当前的 EGL/hyprtoolkit 路径需要 primary DRM 设备和
支持 PRIME export 的 GBM allocator，在 PRoot 里会报
`Couldn't open a gbm fd`。`swaybg` 使用 Wayland shared-memory buffer，不需要
走 GBM。

## 启动和验证

从 App 启动 profile。日志：

```text
/tmp/trierarch-hyprland-pr.log
/tmp/swaybg.log
```

Hyprland 运行时检查 GPU 库：

```sh
grep -E 'libgallium-26|libvulkan_freedreno' \
  /proc/$(pidof Hyprland)/maps
```

某些 session 里 `hyprctl` 仍可能报 socket 错误；桌面可以正常渲染。

## 常见错误

| 现象 | 原因 |
| --- | --- |
| `CBackend::create() failed` | 没有安装或没有在 `LD_PRELOAD` 里加载 `libaquamarine-compat.so` |
| 背景黑，`swaybg` 日志里有 `Failed to load image` | 没有设置 `GLYCIN_DISABLE_SANDBOX` |
| `hyprpaper` 报 `Couldn't open a gbm fd` | hyprpaper 需要 primary DRM/GBM；改用 `swaybg` |
| Hyprland 启动但画面不出来 | 检查 host surface attach 和 profile 日志 |
| `hyprctl` 连不上 | Hyprland IPC 路径问题；保留 `/tmp/t` runtime 前缀 |
