# PipeWire 与实时 portal 导致 Plasma 黑屏

[English](../../en/known-issues/pipewire-rtkit.md) | 中文

## 症状

Plasma 启动后可能短暂显示桌面，随后黑屏。KWin 仍然存活，但
`plasmashell` 消失。某些环境下 shell 会被反复拉起，表现为黑屏后短暂恢复。

## 原因

在 Linux 容器中，`xdg-desktop-portal` 的实时（realtime）portal 可能返回
`0` 的运行预算。PipeWire 的 `libpipewire-module-rt` 会把这个值写成进程的
硬限制：

```text
RLIMIT_RTTIME=0
```

内核随后会向消耗实时 CPU 时间的线程发送 `SIGKILL`。在当前环境中，
整个 Plasma shell 会受到影响，因此 KWin 还在但桌面消失。

只用 supervisor 重启 `plasmashell` 只能掩盖症状：新 shell 再次读取 portal
预算，再次得到 `RLIMIT_RTTIME=0`，然后再次被杀。

## Workaround

对 Plasma 会话禁用 RTKit/portal 的实时路径：

```sh
exec env DISABLE_RTKIT=1 dbus-run-session -- startplasma-wayland
```

Trierarch profile 中应写成：

```toml
[launch]
argv = ["env", "DISABLE_RTKIT=1", "dbus-run-session", "--", "startplasma-wayland"]
```

如果 profile 还需要保留其它环境变量：

```toml
argv = ["sh", "-lc", "exec env DISABLE_RTKIT=1 dbus-run-session -- startplasma-wayland"]
```

需要把这个环境变量应用到所有可能走到该路径的 Plasma profile，包括
Arch 和 Kali。

修改后可在 guest 内确认实时硬限制：

```sh
prlimit --pid "$(pgrep -n plasmashell)" --rttime
```

`RLIMIT_RTTIME` 应为 `unlimited`，桌面也应保持可见。

## 说明

这是针对容器、实时 portal 和 PipeWire RT 模块交互的 workaround。
它会关闭受影响会话的实时调度，音频延迟可能增加，但桌面可以正常使用。
