# 第三章 — Profile 字段

[English](../../en/tutorials/03-profile-fields.md) | 中文

每个 profile 都是 TOML 文件。先通过 CLI 创建，再编辑并启动：

```sh
trierarch --create arch-demo
trierarch --config arch-demo
trierarch --status arch-demo
```

文件位置：

```text
/data/user/0/app.trierarch/files/config/profiles/<id>.toml
```

## 公共顶层字段

| 字段 | 必填 | 含义 |
| --- | --- | --- |
| `id` | 是 | 唯一 profile ID；小写字母、数字、`-` |
| `name` | 否 | 显示名称 |
| `runtime` | 是 | `proot`、`chroot` 或 `droidspaces` |
| `shell` | PRoot/chroot | rootfs 内的绝对 shell 路径 |
| `rootfs` | PRoot/chroot | 绝对 rootfs 路径 |
| `container` | DroidSpaces | 已存在的容器名 |
| `user` | DroidSpaces | 容器内用户 |

## 三种运行环境的区别

### PRoot

```toml
runtime = "proot"
rootfs = "/data/user/0/app.trierarch/files/rootfs/arch"
shell = "/bin/bash"
```

- rootfs 在 App 私有目录；
- 不需要 root；
- 没有 `container`、`user`；
- PRoot 的 KDE Wayland 额外需要 KWin compatibility，见[第五章 A](05-desktops-proot.md)。

### chroot

```toml
runtime = "chroot"
rootfs = "/data/local/my-rootfs"
shell = "/bin/bash"
```

- rootfs 是设备上的目录；
- 需要 root 和可用的 `su`；
- 没有 `container`、`user`；
- Trierarch 负责监管会话。

### DroidSpaces

```toml
runtime = "droidspaces"
container = "Arch"
user = "beauty"
```

- 容器必须已经存在；
- 顶层没有 `rootfs`、`shell`；
- Trierarch 启动容器并管理附着；
- 需要 X11 附着时，不要让 `container` 指向已经在 Trierarch 之外启动的容器。

## 显示

```toml
[display]
type = "wayland"   # none / x11 / wayland
desktop_scale = 125
```

- `none`：终端；
- `x11`：Trierarch 的 X11 surface；
- `wayland`：Trierarch 的 Wayland host + 嵌套 compositor。

`desktop_scale` 是可选字段，只在 `type = "wayland"` 时有效。它缩放 guest 桌面
输出，不缩放 Android App 界面或终端。支持 `100`、`125`、`150`、`200`、`250`、
`300`、`400`，默认 `100`。修改后需要重新启动 profile。

## 启动命令

```toml
[launch]
argv = ["startplasma-wayland"]
```

`argv` 是参数数组，不是 shell 字符串。用它选择桌面，或只添加该桌面真正需要的
环境变量。除非桌面确实需要，否则不要把整段 shell 脚本塞进去。

## 图形

```toml
[graphics]
renderer = "llvmpipe"
buffer = "auto"
qt_quick_backend = "auto"
```

| Renderer | 用途 |
| --- | --- |
| `llvmpipe` | 软件渲染，适合第一次测试 |
| `virgl` | host VirGL/vtest 加速 |
| `venus` | 实验性 Vulkan/Venus |
| `adreno` | Qualcomm Adreno，仅 Wayland |

`buffer` 默认 `auto`；`virgl`/`venus` 通常用 `shm`，其他路径通常用 `dmabuf`。
Plasma + VirGL 经常需要 `qt_quick_backend = "software"`。

硬件资产见[第四章](04-hardware-acceleration.md)。

## 音频和兼容

```toml
[audio]
backend = "pipewire"

[compat]
udev_monitor = "auto"
```

第一次终端测试可以不加。桌面会话通常保留 `udev_monitor = "auto"`，除非 guest
有特殊要求。

下一步：[第四章 — 硬件加速](04-hardware-acceleration.md)。
