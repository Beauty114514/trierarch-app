# 第一章 — Trierarch CLI

[English](../../en/tutorials/01-cli.md) | 中文

Trierarch 通过内置 shell 中的 `trierarch` 命令控制。Profile 是 TOML 文件，但通常
通过 CLI 创建和编辑。

## 命令总览

```sh
trierarch --help                 # 查看用法
trierarch --list                 # 列出 profile
trierarch --status [ID]          # 查看运行状态
trierarch --create ID            # 创建 profile 模板
trierarch --config ID            # 用 $EDITOR 编辑
trierarch --delete ID            # 删除 profile
trierarch --run ID               # 启动
trierarch --stop [ID]            # 停止当前或指定会话
trierarch --rerun ID             # 重启
trierarch --import ARCHIVE --name NAME
trierarch --remove NAME
```

这些命令要在 Trierarch 内置 shell 中运行，不是随便一个 Android 终端。

## 创建和编辑 profile

```sh
trierarch --create arch-demo
trierarch --config arch-demo
```

- `--create` 会在
  `/data/user/0/app.trierarch/files/config/profiles/arch-demo.toml`
  生成模板。
- `--config` 用 `$EDITOR` 打开文件，保存后校验。
- 没有配置编辑器时，直接编辑上面的 TOML 文件。
- 启动前确认能解析：

```sh
trierarch --status arch-demo
```

Profile ID 只能用小写字母、数字和 `-`，以字母开头，最长 64 个字符。

## 导入和删除 PRoot rootfs

PRoot rootfs 从 `.tar.xz` 归档导入：

```sh
trierarch --import ~/arch-rootfs.tar.xz --name arch
```

命令会输出安装后的 rootfs 路径，把它填进 profile 的 `rootfs` 字段。删除：

```sh
trierarch --remove arch
```

## 启动、停止和查看

```sh
trierarch --run arch-demo
trierarch --status arch-demo
trierarch --stop arch-demo
```

修改 profile 后需要替换运行中的会话时用 `--rerun`。会话运行中不要随意编辑
profile；如果改了，后续要重启才会生效。

下一步：[第二章 — 部署运行环境](02-deploy.md)。
