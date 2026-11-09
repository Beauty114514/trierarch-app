# Termux:X11 Lorie 集成

[English](../../en/upstream/termux-x11.md) | 中文


## native 基线

- 仓库：https://github.com/termux/termux-x11
- Commit：`9471ad977d21b7c7bec45117008f74f330d45983`
- native 源码和 Android 构建配方：独立的 `trierarch-packages/x11-host` 仓库。

应用只打包该仓库的 `libXlorie.so` 和 XKB rules 资源。该包在 App 构建前独立编译。

## 有意保留的第一层边界

`com.termux.x11` 只包含很小的 JNI-ABI bridge，不复制 Termux:X11 的 Android UI。
Lorie 需要这些精确的 Java 类名和方法名。服务器运行在 App 的 `:x11` 进程里，因为
它的 native Xorg 入口在服务器退出时会结束所属进程。Binder 把每个客户端的 Unix
socket fd 传给主进程的 `LorieView`。

第一版集成只负责渲染。输入、剪贴板、输出选择和缩放策略仍是独立配置功能。

## DroidSpaces 会话启动

Trierarch 负责 DroidSpaces profile 的启动决策；DroidSpaces 仍然是创建命名空间和
启动 init 的运行时。容器停止时，Trierarch 先启动 Lorie，再调用 DroidSpaces
`start`，把 App 的 X11 目录绑定到 `/tmp/.X11-unix`。启动完成后，才用
`DISPLAY=:0` 和 `XDG_SESSION_TYPE=x11` 运行配置的命令。

`droidspaces run` 只进入已经运行的容器，不能添加 bind mount。因此，如果容器已经
在运行且不是由该 profile 启动，Trierarch 会拒绝把 X11 profile 启动进去，也不会
静默重启或重新配置该容器。

Profile 可以声明直接运行的程序：

```toml
[launch]
argv = ["startxfce4"]
```

没有 `launch.argv` 时，Trierarch 打开配置用户的交互 shell。`argv` 故意使用数组，
避免应用自己发明 shell 解析器或修改用户的 shell 启动文件。

当前固定的 Lorie 产物以 Android API 26 为最低支持平台。`display.type = "x11"`
的 profile 在 Android 8.0 以下会被明确拒绝；terminal-only profile 仍支持 App 的
更低基线。