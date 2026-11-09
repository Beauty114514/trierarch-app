# 第二章 — 部署运行环境

[English](../../en/tutorials/02-deploy.md) | 中文

先部署运行环境，再创建 profile。三种运行环境不是同一套安装流程。

## PRoot — 下载并导入 Arch rootfs

Trierarch 不内置 Linux rootfs。从 Linux Containers 镜像站下载 Arch Linux
`arm64` rootfs：

- [Linux Containers images](https://images.linuxcontainers.org/)
- [Arch Linux, current, arm64, default](https://images.linuxcontainers.org/images/archlinux/current/arm64/default/)

选择最新的日期目录，下载其中的 `rootfs.tar.xz`。这个归档才是 Trierarch 导入的
rootfs；不要用桌面镜像或其他架构的镜像。

把它推到 App 能读取的位置，例如共享 Download 目录：

```sh
adb push rootfs.tar.xz /sdcard/Download/arch-rootfs.tar.xz
```

在 Trierarch shell 中导入：

```sh
trierarch --import /sdcard/Download/arch-rootfs.tar.xz --name arch
```

命令会输出安装路径，记录下来填进 profile：

```text
/data/user/0/app.trierarch/files/rootfs/arch
```

如果当前 Android 版本下 App 读不到共享 Download 路径，先把归档放进 App 可读目录，
再使用那个绝对路径。

删除已导入环境：

```sh
trierarch --remove arch
```

PRoot 部署完成，不需要 root、chroot 或 DroidSpaces。

## chroot — 用 root 准备 rootfs

chroot 需要已 root 的设备、可用的 `su`，以及设备上已经存在的 Linux rootfs。

rootfs 准备方式参考上游 chroot 文档：

- [ArchWiki: Chroot](https://wiki.archlinux.org/title/Chroot)

profile 的 `rootfs` 是 chroot 目录，例如：

```text
/data/local/my-rootfs
```

Trierarch 负责启动和监管 chroot 会话，不创建也不填充 rootfs。

## DroidSpaces — 先部署容器

DroidSpaces 是外部容器运行时。先在 Trierarch 之外安装并创建容器：

- [DroidSpaces-OSS](https://github.com/ravindu644/Droidspaces-OSS)

按它的安装和容器创建文档操作，记录：

- 容器名，例如 `Arch`；
- 容器内用户，例如 `beauty`；
- 容器内安装的桌面启动命令。

Trierarch 只启动已经存在的容器，不负责创建容器。

## 部署检查

| 运行环境 | 现在应该具备 |
| --- | --- |
| PRoot | 已导入的 rootfs 路径 |
| chroot | rootfs 目录 + 可用的 `su` |
| DroidSpaces | 已存在的容器 + 用户 |

下一步：[第三章 — Profile 字段](03-profile-fields.md)。
