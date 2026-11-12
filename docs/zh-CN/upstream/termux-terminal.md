# Termux terminal 上游跟踪

[English](../../en/upstream/termux-terminal.md) | 中文


## 上游基线

- 仓库：https://github.com/termux/termux-app
- Commit：`3df69d1da197dd9bd71a3bafd902dffd720576b4`
- Commit 日期：2026-07-15

以下上游源码包会作为 Trierarch App 的一部分编译：

- `com.termux.terminal`
- `com.termux.view`

保留原包名。Trierarch 自己的 Android 代码继续放在 `app.trierarch` 下。

## 运行边界

Trierarch 不创建 `com.termux.terminal.TerminalSession`，也不使用 Termux 的 PTY
native 实现（`termux.c` / `libtermux.so`）。进程创建、PTY I/O 和窗口大小调整由
Trierarch native 代码负责。

Termux terminal 代码用于终端仿真、渲染、输入处理、滚动和文本选择。native PTY
会话通过一个 Trierarch 维护的小型适配器连接到 terminal view，设计来源于旧项目的
`DisplayableTermSession`。

## 许可证

Termux 仓库整体为 GPLv3-only。Termux 将 `terminal-emulator` 和
`terminal-view` 模块说明为源自 Android Terminal Emulator、带 Apache 2.0
许可证例外。导入或更新这些模块时必须保留上游版权和许可证声明。

## 更新规则

不要从浮动 branch 替换这些源码。每次导入都必须在这里记录确切的上游 commit，
并把 Trierarch 自己的修改限制在 terminal-view/session 适配层。

## Trierarch patch 集

相对基线的完整源码差异存放在
[`patches/termux-terminal/`](../../../patches/termux-terminal/)，顺序如下：

1. `0001-add-displayable-term-session.patch`
2. `0002-adapt-terminal-view-session.patch`
3. `0003-adapt-terminal-view-client.patch`
4. `0004-use-app-resource-namespace.patch`

把新的上游快照导入相同路径后，在 `trierarch-app` 仓库根目录执行：

```sh
for patch_file in patches/termux-terminal/*.patch; do
  patch -p1 < "$patch_file"
done
```

第四个 patch 是必需的，因为这些源码在 `app.trierarch` Android 应用命名空间下编译，
而不是 Termux 独立的 `com.termux.view` 库命名空间。