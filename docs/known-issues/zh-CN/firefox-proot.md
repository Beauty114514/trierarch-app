# Firefox 在 PRoot 中崩溃或异常

[English](../en/firefox-proot.md) | 中文

## 现象

Firefox 启动即退出，或内容进程在 PRoot 下行为异常。

## 原因

Firefox 的内容沙箱依赖 Linux 命名空间、`/proc` 和权限行为，PRoot 的用户态
视图无法完整复现这些条件。

## 处理

安装 Firefox 后，打开 `about:config`，设置：

| 配置项 | 值 |
| --- | --- |
| `media.cubed.sandbox` | `false` |
| `security.sandbox.content.level` | `0` |

重启 Firefox。

## 警告

这会削弱 Firefox 沙箱。只建议在本地、可信的 PRoot 会话中这样使用；不要在没有
额外隔离的情况下用同一配置浏览不可信内容。

## 早期版本说明

这条来自早期 `trierarch-optimize` 笔记。对旧 PRoot/rootfs 环境仍有参考价值；
较新的环境可能表现不同。
