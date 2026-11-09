# 第六章 — 运行和选择样例

[English](../../en/tutorials/06-run.md) | 中文

运行环境已部署、字段已填写、需要的 GPU 资产已安装、桌面已安装。现在创建 profile、
编辑并运行。

## 创建和编辑

```sh
trierarch --create arch-demo
trierarch --config arch-demo
trierarch --status arch-demo
```

按[第三章](03-profile-fields.md)填写字段。profile 路径：

```text
/data/user/0/app.trierarch/files/config/profiles/arch-demo.toml
```

没有 `$EDITOR` 时直接编辑该文件。

## 运行

```sh
trierarch --run arch-demo
```

然后：

```sh
trierarch --status arch-demo
```

在 Trierarch 中打开终端、X11 或 Wayland surface。桌面第一次启动可能较慢。

停止：

```sh
trierarch --stop arch-demo
```

修改 profile 后重启：

```sh
trierarch --rerun arch-demo
```

## 样例 profile

样例只是起点，不是跨设备默认值。全部使用 Wayland 和 Adreno 路径；请修改 `id`、
路径、容器名和用户。

| 样例 | 运行环境 | 桌面 | 说明 |
| --- | --- | --- | --- |
| [Arch PRoot Plasma](../../profiles/arch-proot-plasma-wayland-adreno.toml) | PRoot | KDE Plasma | KWin compat + fake DRM |
| [Arch DroidSpaces Plasma](../../profiles/arch-droidspaces-plasma-wayland-adreno.toml) | DroidSpaces | KDE Plasma | Adreno，已有容器 |
| [Arch DroidSpaces Hyprland](../../profiles/arch-droidspaces-hyprland-wayland-adreno.toml) | DroidSpaces | Hyprland | Adreno，已有容器 |
| [Kali chroot Plasma](../../profiles/kali-chroot-plasma-wayland-adreno.toml) | chroot | KDE Plasma | Adreno，chroot rootfs |

本地仓库中同样位于：

```text
docs/profiles/
```

## 样例说明

- [Arch PRoot Plasma profile](../../profiles/arch-proot-plasma-wayland-adreno.toml)
  是 compatibility 资产路径的参考。
- [Arch DroidSpaces Plasma profile](../../profiles/arch-droidspaces-plasma-wayland-adreno.toml)
  和 [Arch DroidSpaces Hyprland profile](../../profiles/arch-droidspaces-hyprland-wayland-adreno.toml)
  是手机上可用的 Arch profile，整理成样例。
- [Kali chroot Plasma profile](../../profiles/kali-chroot-plasma-wayland-adreno.toml)
  保留 Kali chroot 布局；把 `rootfs` 改成你自己的路径。

不要不检查绝对路径和运行环境字段就直接照抄。
