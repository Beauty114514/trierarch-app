# Chapter 4 — Hardware acceleration assets

English | [中文](../../zh-CN/tutorials/04-hardware-acceleration.md)

Choose the renderer before installing a desktop. Most desktop failures happen
because the renderer, guest drivers, and display transport do not match.

## Choose a path

| Path | What runs on the GPU | Extra guest assets |
| --- | --- | --- |
| `llvmpipe` | CPU | none |
| `virgl` | host VirGL/vtest | none for the compositor; distro Mesa for apps |
| `adreno` | guest Adreno/KGSL, host display | Mesa + Turnip userspace stack |

`llvmpipe` is the correct first test. `virgl` is the simplest accelerated path
for a rooted or unrooted compositor. `adreno` is the path used by the sample
profiles in this tutorial and is the focus of the product examples.

## Runtime differences

| Runtime | Adreno device access | KWin compatibility patch |
| --- | --- | --- |
| PRoot | Trierarch synthesizes a fake render node | required for Plasma Wayland |
| chroot | real `/dev/dri` via root | no |
| DroidSpaces | real `/dev/dri` inside the container | no |

The KWin patch is not a GPU driver. It is a Wayland render-node compatibility
change for the PRoot fake-DRM path; it is covered in
[Chapter 5](05-desktops.md).

## Adreno guest GPU userspace stack

The guest needs Mesa, EGL/GBM and a matching Turnip Vulkan ICD. Do not mix an
arbitrary Mesa build with an arbitrary Turnip build.

### Mesa

Trierarch builds the Mesa part from its recipe:

- [Mesa package recipe](../../../../trierarch-packages/mesa-adreno/README.md)

The recipe must be pinned to the same Mesa tag as the component manifest.

### Turnip / Vulkan ICD

Turnip is an externally prebuilt guest component. Trierarch does not provide a
rebuild guide; use the Turnip binary shipped with the matching component. Do not
replace only `libvulkan_freedreno.so`.

### Download placeholders

The release does not exist yet. The URLs and checksums are intentionally
placeholders:

```bash
ADRENO_GPU_COMPONENT_URL="<not-published>"
ADRENO_GPU_COMPONENT_SHA256="<not-published>"
```

Do not run `curl` against those values. When a release exists, use the manifest
to download and verify the component:

```bash
curl -fL "$ADRENO_GPU_COMPONENT_URL" -o adreno-gpu-component.tar.zst
printf '%s  %s\n' "$ADRENO_GPU_COMPONENT_SHA256" \
  adreno-gpu-component.tar.zst | sha256sum -c -
```

### Install

Install the verified component under a stable path:

```sh
mkdir -p /opt/trierarch/gpu/mesa-26.0
tar --zstd -xf adreno-gpu-component.tar.zst -C /opt/trierarch/gpu/mesa-26.0
```

Expected library directory:

```text
/opt/trierarch/gpu/mesa-26.0/usr/lib/aarch64-linux-gnu/
```

## Environment used by the profile

For an Adreno profile, point the guest at the component before starting the
desktop:

```sh
export TRIERARCH_MESA=/opt/trierarch/gpu/mesa-26.0/usr
export LD_LIBRARY_PATH=$TRIERARCH_MESA/lib/aarch64-linux-gnu:$LD_LIBRARY_PATH
export LIBGL_DRIVERS_PATH=$TRIERARCH_MESA/lib/aarch64-linux-gnu/dri
export GBM_BACKENDS_PATH=$TRIERARCH_MESA/lib/aarch64-linux-gnu/gbm
export __EGL_VENDOR_LIBRARY_FILENAMES=$TRIERARCH_MESA/share/glvnd/egl_vendor.d/50_mesa.json
export VK_ICD_FILENAMES=$TRIERARCH_MESA/share/vulkan/icd.d/freedreno_icd.json
export VK_DRIVER_FILES=$TRIERARCH_MESA/share/vulkan/icd.d/freedreno_icd.json
```

The sample profiles put these exports in `launch.argv`. If the component is in a
different directory, update every path.

## PRoot Plasma compatibility

PRoot Plasma Wayland also needs the KWin compatibility library. Its build,
installation and profile environment are described in
[Chapter 5](05-desktops.md#kde-plasma-on-proot).

Next: [Chapter 5 — Desktops](05-desktops.md).
