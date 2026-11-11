# KDE Baloo / `tags:/` 提示（PRoot）

[English](../../../en/known-issues/proot/baloo-tags.md) | 中文

## 现象

KDE 反复提示 “Could not enter folder tags:/” 或 Baloo 警告。

## 原因

Baloo 结合了 KDE 索引器和文件系统监视。PRoot 的命名空间、bind 布局、`/proc`
视图和 inotify 行为与普通桌面不同，容易导致索引异常或虚拟 `tags:/` 访问失败。

## 修复：把下面整块复制到 PRoot shell

```bash
mkdir -p "$HOME/.config"
printf '%s\n' '[Basic Settings]' 'Indexing-Enabled=false' > "$HOME/.config/baloofilerc"
if command -v balooctl6 >/dev/null 2>&1; then
    balooctl6 disable
    balooctl6 purge
elif command -v balooctl >/dev/null 2>&1; then
    balooctl disable
    balooctl purge
fi
```

然后退出 Plasma 再重新登录，`tags:/` 报错应该消失。

## 系统级替代方案

如果 PRoot 以 root 运行，并且希望所有用户都生效：

```bash
mkdir -p /etc/xdg
printf '%s\n' '[Basic Settings]' 'Indexing-Enabled=false' > /etc/xdg/baloofilerc
```

## 说明

关闭 Baloo 后，Dolphin 侧边栏仍可能显示 `tags:/` 入口，但错误弹窗应停止。
`tags:/` 这个虚拟位置本身不是 PRoot 提供的。
