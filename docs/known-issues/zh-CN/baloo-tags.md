# KDE Baloo / `tags:/` 提示（PRoot）

[English](../en/baloo-tags.md) | 中文

## 现象

KDE 反复提示 “Could not enter folder tags:/” 或 Baloo 警告。

## 原因

Baloo 结合了 KDE 索引器和文件系统监视。PRoot 的命名空间、bind 布局、`/proc`
视图和 inotify 行为与普通桌面不同，容易导致索引异常或虚拟 `tags:/` 访问失败。

## 处理

在 PRoot shell 中执行（Plasma 6 使用 `balooctl6`，Plasma 5 可尝试
`balooctl`）：

```bash
balooctl6 status
balooctl6 purge
balooctl6 suspend
balooctl6 disable
```

在手机上关闭 Baloo 通常划算：减少无意义的后台索引和 `tags:/` 报错。

## 早期版本说明

这条来自早期 `trierarch-optimize` 笔记。对旧 KDE/rootfs 环境仍有参考价值；
较新的版本可能显示不同的提示。
