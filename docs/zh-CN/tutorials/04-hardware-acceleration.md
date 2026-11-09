# 第四章 — 硬件加速资产

[English](../../en/tutorials/04-hardware-acceleration.md) | 中文

先选 renderer，再安装桌面。大多数桌面启动失败，都是 renderer、guest 驱动和显示
传输方式不匹配。

## 选择路径

| 路径 | GPU 上运行什么 | 额外 guest 资产 |
| --- | --- | --- |
| `llvmpipe` | CPU | 无 |
| `virgl` | host VirGL/vtest | compositor 不需要；应用使用发行版 Mesa |
| `adreno` | guest Adreno/KGSL + host 显示 | Mesa + Turnip userspace stack |

`llvmpipe` 是第一次测试的正确选择。`virgl` 是 rooted/unrooted compositor 最简单的
加速路径。`adreno` 是本教程样例 profile 使用、也是产品示例重点的路径。

## 运行环境差异

| 运行环境 | Adreno 设备访问 | KWin compatibility patch |
| --- | --- | --- |
| PRoot | Trierarch 合成 fake render node | Plasma Wayland 需要 |
| chroot | 通过 root 访问真实 `/dev/dri` | 不需要 |
| DroidSpaces | 容器内访问真实 `/dev/dri` | 不需要 |

KWin patch 不是 GPU 驱动，而是 PRoot fake-DRM 路径的 Wayland render-node 兼容修改；
见[第五章](05-desktops.md)。

## Adreno guest GPU userspace stack

guest 需要 Mesa、EGL/GBM 和配套的 Turnip Vulkan ICD。不能把任意 Mesa 构建和任意
Turnip 构建混在一起。

### Mesa

Mesa 部分按 Trierarch 配方构建：

- [Mesa 包配方](../../../../trierarch-packages/mesa-adreno/README.md)

配方 pin 的 Mesa tag 必须和组件 manifest 一致。

### Turnip / Vulkan ICD

Turnip 是外部预编译的 guest 组件。Trierarch 不提供重建教程；使用配套组件包里的
Turnip 二进制，不要只替换 `libvulkan_freedreno.so`。

### 下载占位

目前没有 release，URL 和校验值故意是占位：

```bash
ADRENO_GPU_COMPONENT_URL="<not-published>"
ADRENO_GPU_COMPONENT_SHA256="<not-published>"
```

不要真的对占位值执行 `curl`。发布后按 manifest 下载并校验：

```bash
curl -fL "$ADRENO_GPU_COMPONENT_URL" -o adreno-gpu-component.tar.zst
printf '%s  %s\n' "$ADRENO_GPU_COMPONENT_SHA256" \
  adreno-gpu-component.tar.zst | sha256sum -c -
```

### 安装

校验后安装到固定路径：

```sh
mkdir -p /opt/trierarch/gpu/mesa-26.0
tar --zstd -xf adreno-gpu-component.tar.zst -C /opt/trierarch/gpu/mesa-26.0
```

预期库目录：

```text
/opt/trierarch/gpu/mesa-26.0/usr/lib/aarch64-linux-gnu/
```

## Profile 使用的环境

Adreno profile 启动桌面之前，把 guest 指向组件：

```sh
export TRIERARCH_MESA=/opt/trierarch/gpu/mesa-26.0/usr
export LD_LIBRARY_PATH=$TRIERARCH_MESA/lib/aarch64-linux-gnu:$LD_LIBRARY_PATH
export LIBGL_DRIVERS_PATH=$TRIERARCH_MESA/lib/aarch64-linux-gnu/dri
export GBM_BACKENDS_PATH=$TRIERARCH_MESA/lib/aarch64-linux-gnu/gbm
export __EGL_VENDOR_LIBRARY_FILENAMES=$TRIERARCH_MESA/share/glvnd/egl_vendor.d/50_mesa.json
export VK_ICD_FILENAMES=$TRIERARCH_MESA/share/vulkan/icd.d/freedreno_icd.json
export VK_DRIVER_FILES=$TRIERARCH_MESA/share/vulkan/icd.d/freedreno_icd.json
```

样例 profile 会把它们放进 `launch.argv`。组件装在别的位置时，同步修改所有路径。

## PRoot Plasma 兼容

PRoot Plasma Wayland 还需要 KWin compatibility library。构建、安装和 profile 环境
见[第五章](05-desktops.md#kde-plasma-on-proot)。

下一步：[第五章 — 桌面](05-desktops.md)。
