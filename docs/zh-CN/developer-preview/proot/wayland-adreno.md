# PRoot + Adreno + Plasma Wayland（开发者预览）

[English](../../../en/developer-preview/proot/wayland-adreno.md) | 中文

## 状态

```text
Developer Preview
资产尚未正式发布
```

这份文档描述 Trierarch 在 **未 root 的 PRoot** 下运行 KDE Plasma Wayland，
并让 KWin 使用 Adreno GPU 的嵌套合成路径。

当前验证组合：

```text
运行环境        PRoot
发行版          Arch Linux ARM
桌面            KDE Plasma 6.8.2
KWin package    6.8.2-2
KWin upstream   6.8.2
Qt              6.12.0-3
架构            aarch64
```

资产还未发布。现在只能使用本地构建产物；正式 release 后下载地址会替换本文中的
占位符，步骤本身不变。

## 它解决什么问题

PRoot 不能直接打开真实的 `/dev/dri`。这个路径用以下组件给 KWin 提供可用的
render node：

```text
Trierarch native fake DRM
  + 匹配的 KWin compatibility library
  + Adreno guest GPU userspace stack
  + host dma-buf feedback
```

KWin 仍然使用 KWin 的标准 Wayland backend；没有修改 Plasma 或其他 KDE 组件。

## 前置条件

- 已安装 Trierarch；
- Arch Linux ARM PRoot rootfs；
- rootfs 中的 KWin 版本与本文矩阵一致；
- 设备有可用的 Adreno GPU 和 KGSL；
- 已停止其他正在运行的 KWin/Plasma 会话。

不要在 KWin 版本不匹配时强行使用 compat 库。KWin 的 C++ ABI 和 Qt/KF6 依赖
必须与 rootfs 中的包一致。

## 组件

### 1. KWin compatibility library

路径：

```text
/opt/trierarch/kwin/6.8.2/compat/
├── libkwin.so -> libkwin.so.6
├── libkwin.so.6 -> libkwin.so.6.8.2
└── libkwin.so.6.8.2
```

原则：

- **不要覆盖** `/usr/lib/libkwin.so.6.8.2`；
- 只通过 `LD_LIBRARY_PATH` 让当前 profile 加载 compat 库；
- pacman 升级 KWin 后，系统库保持原版，compat 目录不受影响；
- KWin 版本变化时，必须换对应的 compat 构建。

当前本地构建产物在：

```text
trierarch-packages/kwin-compat/dist/libkwin-6.8.2-2-archlinuxarm-aarch64.so
```

正式发布后的下载地址占位：

```bash
KWIN_COMPAT_URL="<trierarch-release-url>"
```

### 2. Adreno guest GPU userspace stack

路径：

```text
/opt/trierarch/gpu/mesa-26.0/usr
```

它包含 guest 使用的 Mesa、Zink、Turnip/ICD 和 EGL/GBM 文件。它必须是一套
匹配的栈，不能只替换其中一部分。

正式发布后的下载地址占位：

```bash
ADRENO_GPU_COMPONENT_URL="<trierarch-release-url>"
```

#### 构建来源

Mesa 部分的构建方式参考 Trierarch 的
[Mesa 包配方](../../../../../trierarch-packages/mesa-adreno/README.md)。发布组件时使用的
Mesa tag 必须与组件 manifest 记录一致，不能把其他 Mesa 版本混进来。

Turnip/ICD 是外部预编译的 guest 组件。Trierarch 不在这里提供它的重建步骤；
请使用组件包内与当前 Mesa 配套的二进制，不要单独替换 Turnip 库。

当前开发样例使用的是预构建 userspace 组件。正式发布前会用 manifest 固定
Mesa/Turnip 组合、文件列表和校验值。

## 获取组件

guest 需要两个组件，二者都不打包进 Trierarch APK。

| 组件 | 来源 | 当前状态 |
| --- | --- | --- |
| KWin compatibility library | 用 `kwin-compat/patches/0001-wayland-render-node.patch` 对 KWin 源码构建 | 尚未发布 |
| Adreno guest GPU userspace stack | Mesa 按 Trierarch Mesa 配方构建，加上配套的外部 Turnip/ICD 组件 | 尚未发布 |

### KWin compatibility library

开发阶段使用本地构建产物：

```text
trierarch-packages/kwin-compat/dist/libkwin-6.8.2-2-archlinuxarm-aarch64.so
```

正式发布前，下载地址只是占位：

```bash
KWIN_COMPAT_URL="<not-published>"
```

不要真的对占位地址执行 `curl`。发布后按 manifest 记录的文件名下载，校验
大小和 SHA-256，只安装校验通过的文件。

### Adreno guest GPU userspace stack

开发阶段当前组件在：

```text
/opt/trierarch/gpu/mesa-26.0/usr
```

发布地址同样是占位：

```bash
ADRENO_GPU_COMPONENT_URL="<not-published>"
```

Mesa 部分按
[Trierarch Mesa 配方](../../../../../trierarch-packages/mesa-adreno/README.md)
构建。Turnip/ICD 是外部预编译的 guest 组件，Trierarch 不提供它的重建教程。
请下载 manifest 标记为与当前 Mesa 配套的组件，不要把不同 Turnip 构建混进来。

### 发布后的下载与校验流程

以 manifest 字段为准，预期流程：

```bash
# 替换成 release manifest 中的实际值。
KWIN_COMPAT_URL="<release-url>"
KWIN_COMPAT_SHA256="<release-sha256>"
ADRENO_GPU_COMPONENT_URL="<release-url>"
ADRENO_GPU_COMPONENT_SHA256="<release-sha256>"

curl -fL "$KWIN_COMPAT_URL" -o libkwin-compat.so
curl -fL "$ADRENO_GPU_COMPONENT_URL" -o adreno-gpu-component.tar.zst

printf '%s  %s
' "$KWIN_COMPAT_SHA256" libkwin-compat.so | sha256sum -c -
printf '%s  %s
' "$ADRENO_GPU_COMPONENT_SHA256" adreno-gpu-component.tar.zst | sha256sum -c -
```

在 release 出现之前，只能使用本地开发产物和上面描述的组件目录。这个预览目前
无法完全从公网完成，这是发布缺口，不是运行步骤缺失。

## 安装 compat 库

在 guest 内创建目录并把库放到独立位置：

```bash
install -d /opt/trierarch/kwin/6.8.2/compat
install -m 755 \
  ./libkwin-6.8.2-2-archlinuxarm-aarch64.so \
  /opt/trierarch/kwin/6.8.2/compat/libkwin.so.6.8.2
ln -sfn libkwin.so.6.8.2 /opt/trierarch/kwin/6.8.2/compat/libkwin.so.6
ln -sfn libkwin.so.6 /opt/trierarch/kwin/6.8.2/compat/libkwin.so
```

确认系统库没有被替换：

```bash
ls -l /usr/lib/libkwin.so.6.8.2
```

## 安装 guest GPU userspace stack

把组件内容解压到：

```text
/opt/trierarch/gpu/mesa-26.0/usr
```

确认关键文件存在：

```bash
ls -l /opt/trierarch/gpu/mesa-26.0/usr/lib/aarch64-linux-gnu/libvulkan_freedreno.so
ls -l /opt/trierarch/gpu/mesa-26.0/usr/lib/aarch64-linux-gnu/libgallium-*.so
```

## 创建和编辑 profile

用正常的 CLI 流程创建和编辑：

```sh
trierarch --create arch-proot-plasma-wayland-adreno
trierarch --config arch-proot-plasma-wayland-adreno
trierarch --status arch-proot-plasma-wayland-adreno
```

字段说明见 [Profile 字段](../../tutorials/03-profile-fields.md)。完整样例在本页末尾；
只有在核对 rootfs 路径、组件路径和 KWin 版本后，才复制其中的值。

不要手工 bind `/dev/kgsl-3d0`：`renderer = "adreno"` 会让 Trierarch native 层
在 PRoot 中创建 fake `/dev/dri`。

## 启动

从 App 启动该 profile，或使用 Trierarch CLI：

```bash
trierarch --run arch-proot-plasma-wayland-adreno
```

## 验证

### 1. 确认加载的是 compat 库

找到 KWin PID：

```bash
pidof kwin_wayland
```

在 guest 内检查：

```bash
grep libkwin /proc/<pid>/maps
```

预期路径包含：

```text
/opt/trierarch/kwin/6.8.2/compat/libkwin.so.6.8.2
```

如果看到的是：

```text
/usr/lib/libkwin.so.6.8.2
```

说明 `LD_LIBRARY_PATH` 没有进入 KWin，先停止会话再检查 profile。

### 2. 确认 host 收到 buffer

查看 logcat 中的渲染器统计：

```bash
adb logcat | grep TrierarchRenderer
```

预期出现：

```text
perf-surface ... mapped=1 buffer=<宽>x<高>
perf ... commits>0
```

只有 `commits=0` 而没有任何 surface，通常表示 KWin 没有输出、profile 仍有旧
会话，或 KWin 启动失败。

### 3. 查看 KWin 日志

当前 profile 将 Plasma 输出写入：

```text
/opt/trierarch/plasma-adreno.log
```

重点搜索：

```text
Using explicit nested render node
OpenGL compositing has been successfully initialized
```

## 升级

### pacman 升级后

系统库被 pacman 替换是正常的。compat 目录不应被覆盖，profile 仍然通过
`LD_LIBRARY_PATH` 使用 compat 库。

检查：

```bash
pacman -Q kwin
ls -l /usr/lib/libkwin.so.6.8.2
ls -l /opt/trierarch/kwin/6.8.2/compat/libkwin.so.6.8.2
```

### KWin 版本变化后

必须重新生成对应版本的 compat 库。旧 compat 库不能跨 KWin 版本使用。

```text
新 KWin 版本
  → 构建对应 compat 库
  → 放入新的 /opt/trierarch/kwin/<version>/compat
  → 更新 profile 的 LD_LIBRARY_PATH
```

## 回滚

1. 停止 profile；
2. 从 profile 删除 compat 的 `LD_LIBRARY_PATH` 前缀；
3. 可选：删除 compat 目录和 guest GPU userspace 目录。

系统 `/usr/lib/libkwin.so.6.8.2` 没有被本流程修改，因此不需要恢复系统包。

## 故障排查

### KWin 仍然报 compositor not supported

检查：

```bash
echo "$LD_LIBRARY_PATH"
grep libkwin /proc/<kwin-pid>/maps
```

以及 `TRIERARCH_NESTED_RENDER_NODE=1` 和
`KWIN_RENDER_NODES=/dev/dri/renderD0` 是否在 KWin 环境中。

### 启动后 SIGTRAP / GL 崩溃

通常是 guest Mesa/Turnip 不匹配。不要只替换 Mesa 或只替换 Turnip；两者必须是
同一套组件。

### 没有 frame

先停止所有 KWin/Plasma 旧会话，再重新启动 profile。旧 host surface 或旧
`wayland-1.lock` 会干扰新会话。

## 未发布资产说明

当前文档是开发者预览：

```text
KWin compat 库        尚未发布
guest GPU 组件        尚未发布
release manifest      url 为 null
```

正式发布后只替换下载 URL 和 SHA-256 校验步骤，安装和 profile 结构保持不变。

Profile sample: [`wayland-adreno.profile.toml`](wayland-adreno.profile.toml)
