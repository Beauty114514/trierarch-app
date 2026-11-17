# 第五章 A — PRoot 桌面

[English](../../en/tutorials/05-desktops-proot.md) | 中文

PRoot 不需要 root，但不能打开真实的 DRM render node。因此 GPU 桌面使用
Trierarch 的兼容路径：

```text
fake render node
+ Trierarch Mesa 组件
+ Turnip
+ 桌面专用的 compatibility library
```

PRoot Plasma Wayland 和 Hyprland 里，OpenGL 走 **Zink**，Zink 再走
**Turnip**。Plasma 还需要 KWin compatibility library；Hyprland 使用
aquamarine-compat library。

## 公共准备：Mesa 26 + Turnip

所有需要 GPU 加速的 PRoot 桌面都使用这个资产：

```text
mesa-26.0-noroot.tar.zst
```

它包含 Mesa guest userspace 和配套的 Turnip ICD。

### 在 PRoot guest 内解压

必须从 `arch-shell` profile 或 guest terminal 内执行。不要从 `adb shell`
执行：Android 的 toybox `tar` 没有可用的 zstd 支持。

把压缩包放到 guest 能访问的目录：

```text
/opt/trierarch/tmp/
```

创建目录并解压：

```sh
install -d /opt/trierarch/gpu
rm -rf /opt/trierarch/gpu/mesa-26.0
mkdir -p /opt/trierarch/gpu/mesa-26.0

tar --zstd -xf /opt/trierarch/tmp/mesa-26.0-noroot.tar.zst \
  --strip-components=1 -C /opt/trierarch/gpu/mesa-26.0
```

确认关键文件：

```sh
ls -l /opt/trierarch/gpu/mesa-26.0/usr/lib/aarch64-linux-gnu/libgallium-*.so
ls -l /opt/trierarch/gpu/mesa-26.0/usr/lib/aarch64-linux-gnu/libvulkan_freedreno.so
```

profile 里的 `TRIERARCH_MESA`、`LIBGL_DRIVERS_PATH`、`GBM_BACKENDS_PATH`、
`__EGL_VENDOR_LIBRARY_FILENAMES` 和 `VK_ICD_FILENAMES` 都必须指向这个解压目录。

## 选择桌面

- [KDE Plasma Wayland](05-desktops-proot-kde.md) — KWin compatibility library
- [Hyprland](05-desktops-proot-hyprland.md) — aquamarine-compat 和壁纸配置

## XFCE

XFCE 使用[第四章](04-hardware-acceleration.md)的 X11 + VirGL 路径，不需要
KWin 或 aquamarine compatibility library。

## 限制

- PRoot 不能打开 `/dev/dri/renderD128` 或 `/dev/dri/card0`；
- Adreno Plasma 需要 KWin 兼容组件和匹配的 KWin 构建；
- Hyprland 需要 `libaquamarine-compat.so`；这是较新的路径，暂时按实验性功能对待；
- 音频使用 profile 的 `audio.backend = "pipewire"`；
- 其他已验证组合见[兼容性矩阵](../matrices.md)。

下一步：[第六章 — 运行和选择样例](06-run.md)。
