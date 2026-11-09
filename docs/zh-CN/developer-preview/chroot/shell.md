# chroot 终端

[English](../../../en/developer-preview/chroot/shell.md) | 中文

chroot 使用设备的 `su` 提供者运行 rootfs。Trierarch 负责启动和监管会话。

Arch 示例：

```sh
trierarch --run arch-chroot-terminal
```

chroot 需要 root；如果 `su` 缺失或被拒绝，这是环境配置问题，不是图形问题。

Profile 样例: [`shell.profile.toml`](shell.profile.toml)

## 创建和编辑 profile

```sh
trierarch --create arch-chroot-terminal
trierarch --config arch-chroot-terminal
trierarch --status arch-chroot-terminal
```

填写 `runtime = "chroot"`、rootfs 绝对路径、`shell` 和 `display.type = "none"`。完整样例见
[`shell.profile.toml`](shell.profile.toml)；把
container/rootfs/user 换成设备上的实际值。

