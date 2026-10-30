# 兼容性矩阵

最后更新：2026-10-30

测试设备：Adreno 740（Qualcomm）。

本轮只记录：

- **DroidSpaces — Arch**；
- **chroot — Kali**。

## 图例

| 标记 | 含义 |
| --- | --- |
| ✅ 有特效 | 桌面能启动、可用，合成器特效正常启用 |
| ✅ 无特效 | 桌面能启动、可用，但没有特效或特效被关闭 |
| ✅ | 桌面能启动、可用；本轮未记录特效情况 |
| — | 本轮未测试 |

## DroidSpaces — Arch

| 桌面 | Adreno | VirGL | llvmpipe |
| --- | --- | --- | --- |
| KDE Plasma Wayland | ✅ 有特效 | ✅ 无特效 | ✅ 无特效 |
| KDE Plasma X11 | ✅ 有特效 | ✅ 无特效 | ✅ 无特效 |
| Hyprland | ✅ 有特效 | — | — |
| XFCE Wayland | ✅ | ✅ | ✅ |
| XFCE X11 | ✅ | ✅ | ✅ |

## chroot — Kali

| 桌面 | Adreno | VirGL | llvmpipe |
| --- | --- | --- | --- |
| KDE Plasma Wayland | ✅ 有特效 | ✅ 无特效 | ✅ 无特效 |
| KDE Plasma X11 | ✅ 有特效 | ✅ 无特效 | ✅ 无特效 |
| XFCE Wayland | ✅ | ✅ | ✅ |
| XFCE X11 | ✅ | ✅ | ✅ |

chroot/Kali 下本轮未测试 Hyprland 等其他桌面。

## 说明

- 结果来自一台 Adreno 740 设备，不代表所有同 GPU 设备或所有 Android 版本。
- “有特效”指桌面合成器的常规特效正常启用；“无特效”指桌面能运行，但当前
  测试组合下没有特效。VirGL/llvmpipe 下 KDE Plasma 在本轮测试中无特效。
- `—` 表示“未测试”，不是“不能用”。桌面结果与配置有关，缺少记录不代表失败。
- DroidSpaces 容器需要提前创建；Trierarch 负责启动和管理它自己启动的容器。
