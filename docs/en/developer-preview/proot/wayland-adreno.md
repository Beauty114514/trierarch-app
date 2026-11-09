# PRoot + Adreno + Plasma Wayland (Developer Preview)

English | [中文](../../../zh-CN/developer-preview/proot/wayland-adreno.md)

## Status

```text
Developer Preview
Assets are not publicly released yet
```

This guide covers Trierarch running KDE Plasma Wayland in **unrooted PRoot**
with KWin compositing through an Adreno GPU.

Verified combination:

```text
Runtime          PRoot
Distribution     Arch Linux ARM
Desktop          KDE Plasma 6.8.2
KWin package     6.8.2-2
KWin upstream    6.8.2
Qt               6.12.0-3
Architecture     aarch64
```

The compatibility assets are not released yet. Use local build artifacts for now.
When official releases exist, only the download URLs and checksum steps change.

## What this solves

PRoot cannot open a real `/dev/dri` device. This setup gives KWin a usable render
node through:

```text
Trierarch native fake DRM
  + matching KWin compatibility library
  + Adreno guest GPU userspace stack
  + host dma-buf feedback
```

KWin keeps using its normal Wayland backend. Plasma and the rest of KDE are not
patched.

## Requirements

- Trierarch installed;
- Arch Linux ARM PRoot rootfs;
- KWin in the rootfs matching the version matrix above;
- an Adreno GPU with KGSL available on the host;
- no other KWin/Plasma session running.

Do not use the compatibility library with a different KWin version. KWin's C++
ABI and Qt/KF6 dependencies must match the packages in the rootfs.

## Components

### 1. KWin compatibility library

Install it outside the package-managed system directory:

```text
/opt/trierarch/kwin/6.8.2/compat/
├── libkwin.so -> libkwin.so.6
├── libkwin.so.6 -> libkwin.so.6.8.2
└── libkwin.so.6.8.2
```

Rules:

- do **not** overwrite `/usr/lib/libkwin.so.6.8.2`;
- load the compatibility library only through `LD_LIBRARY_PATH`;
- a pacman upgrade may replace the system library, while the compat directory
  stays independent;
- a KWin version change requires a new compatibility build.

Current local build artifact:

```text
trierarch-packages/kwin-compat/dist/libkwin-6.8.2-2-archlinuxarm-aarch64.so
```

Download placeholder after release:

```bash
KWIN_COMPAT_URL="<trierarch-release-url>"
```

### 2. Adreno guest GPU userspace stack

Install path:

```text
/opt/trierarch/gpu/mesa-26.0/usr
```

It contains the guest Mesa, Zink, Turnip/ICD and EGL/GBM files. Treat it as one
matched stack; do not replace only Mesa or only Turnip.

Download placeholder after release:

```bash
ADRENO_GPU_COMPONENT_URL="<trierarch-release-url>"
```

#### Build provenance

The Mesa side follows Trierarch's
[Mesa package recipe](../../../../../trierarch-packages/mesa-adreno/README.md). The Mesa tag
used to publish a component must match the component manifest; do not mix in a
different Mesa build.

The Turnip/ICD side is an externally prebuilt guest component. Trierarch does not
document rebuilding it here. Use the binary shipped with the matching Mesa
component; do not replace the Turnip library on its own.

The current developer sample uses a prebuilt userspace component. Before release,
the manifest will pin the Mesa/Turnip pair, file list and checksums.

## Get the components

There are two guest-side components. Neither is bundled in the Trierarch APK.

| Component | Where it comes from | Current status |
| --- | --- | --- |
| KWin compatibility library | Built from the KWin source with `kwin-compat/patches/0001-wayland-render-node.patch` | Not released |
| Adreno guest GPU userspace stack | Mesa built from the Trierarch Mesa recipe, plus the matching external Turnip/ICD component | Not released |

### KWin compatibility library

For development, use the local build artifact:

```text
trierarch-packages/kwin-compat/dist/libkwin-6.8.2-2-archlinuxarm-aarch64.so
```

The release URL is intentionally a placeholder until the artifact is published:

```bash
KWIN_COMPAT_URL="<not-published>"
```

Do not run `curl` against that placeholder. Once a release exists, download the
artifact named by the manifest, verify its size and SHA-256, and install only
that verified file.

### Adreno guest GPU userspace stack

For development, the current component is under:

```text
/opt/trierarch/gpu/mesa-26.0/usr
```

The release URL is also a placeholder:

```bash
ADRENO_GPU_COMPONENT_URL="<not-published>"
```

The Mesa part is built following
[Trierarch's Mesa recipe](../../../../../trierarch-packages/mesa-adreno/README.md).
Turnip/ICD is an externally prebuilt guest component: Trierarch does not provide
a rebuild guide for it. Download the component that the manifest marks as the
matching pair; do not mix a different Turnip build with this Mesa.

### Download and verification flow (after release)

The exact manifest fields are authoritative. The intended flow is:

```bash
# Replace with the values from the release manifest.
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

Until a release exists, use the local developer artifact and the component
directory described above. This preview cannot be completed from the public
internet yet; that is a publishing gap, not a missing runtime step.

## Install the compatibility library

Inside the guest:

```bash
install -d /opt/trierarch/kwin/6.8.2/compat
install -m 755 \
  ./libkwin-6.8.2-2-archlinuxarm-aarch64.so \
  /opt/trierarch/kwin/6.8.2/compat/libkwin.so.6.8.2
ln -sfn libkwin.so.6.8.2 /opt/trierarch/kwin/6.8.2/compat/libkwin.so.6
ln -sfn libkwin.so.6 /opt/trierarch/kwin/6.8.2/compat/libkwin.so
```

Confirm the system library is still the distro version:

```bash
ls -l /usr/lib/libkwin.so.6.8.2
```

## Install the guest GPU userspace stack

Extract the component under:

```text
/opt/trierarch/gpu/mesa-26.0/usr
```

Confirm the key files exist:

```bash
ls -l /opt/trierarch/gpu/mesa-26.0/usr/lib/aarch64-linux-gnu/libvulkan_freedreno.so
ls -l /opt/trierarch/gpu/mesa-26.0/usr/lib/aarch64-linux-gnu/libgallium-*.so
```

## Create and edit the profile

Create and edit it with the normal CLI flow:

```sh
trierarch --create arch-proot-plasma-wayland-adreno
trierarch --config arch-proot-plasma-wayland-adreno
trierarch --status arch-proot-plasma-wayland-adreno
```

Use the fields described in [profile fields](../../tutorials/03-profile-fields.md).
The full sample is at the end of this document; copy values from it only after
checking the rootfs path, component paths, and KWin version.

Do not add manual `/dev/kgsl-3d0` binds: `renderer = "adreno"` makes the
Trierarch native layer create the fake `/dev/dri` inside PRoot.

## Start

Start the profile from the app, or use the Trierarch CLI:

```bash
trierarch --run arch-proot-plasma-wayland-adreno
```

## Verify

### 1. Confirm the compat library is loaded

Find the KWin PID:

```bash
pidof kwin_wayland
```

Inside the guest:

```bash
grep libkwin /proc/<pid>/maps
```

Expected:

```text
/opt/trierarch/kwin/6.8.2/compat/libkwin.so.6.8.2
```

If it shows:

```text
/usr/lib/libkwin.so.6.8.2
```

then `LD_LIBRARY_PATH` did not reach KWin. Stop the session and inspect the
profile.

### 2. Confirm the host receives a buffer

Inspect the renderer statistics:

```bash
adb logcat | grep TrierarchRenderer
```

Expected:

```text
perf-surface ... mapped=1 buffer=<width>x<height>
perf ... commits>0
```

`commits=0` with no surface usually means KWin did not start, an old session is
still attached, or the profile failed before creating its output.

### 3. Inspect the KWin log

The example profile writes to:

```text
/opt/trierarch/plasma-adreno.log
```

Look for:

```text
Using explicit nested render node
OpenGL compositing has been successfully initialized
```

## Upgrading

### After pacman upgrades

The distro may replace `/usr/lib/libkwin.so.6.8.2`. The compat directory must not
be touched by pacman. Check:

```bash
pacman -Q kwin
ls -l /usr/lib/libkwin.so.6.8.2
ls -l /opt/trierarch/kwin/6.8.2/compat/libkwin.so.6.8.2
```

### After a KWin version change

Build a new compatibility library for the new KWin version. Never reuse the old
binary across KWin releases.

```text
new KWin version
  -> build matching compat library
  -> install under /opt/trierarch/kwin/<version>/compat
  -> update the profile's LD_LIBRARY_PATH
```

## Rollback

1. stop the profile;
2. remove the compat `LD_LIBRARY_PATH` entry;
3. optionally delete the compat and guest GPU userspace directories.

The system `/usr/lib/libkwin.so.6.8.2` was never modified by this procedure, so
there is no system package to restore.

## Troubleshooting

### KWin still reports "compositor not supported"

Check:

```bash
echo "$LD_LIBRARY_PATH"
grep libkwin /proc/<kwin-pid>/maps
```

Also verify that `TRIERARCH_NESTED_RENDER_NODE=1` and
`KWIN_RENDER_NODES=/dev/dri/renderD0` are present in KWin's environment.

### SIGTRAP or a GL crash during startup

This usually means the guest Mesa and Turnip are mismatched. Replace the guest
GPU userspace stack as a whole, not one library at a time.

### No frame

Stop every old KWin/Plasma session first. A stale host surface or
`wayland-1.lock` can keep the new session from attaching.

## Unreleased assets

This is a developer preview:

```text
KWin compat library       not released
guest GPU component       not released
release manifest          url is null
```

Once releases exist, only the URLs and checksum steps change. The installation
layout and profile remain the same.

Profile sample: [`wayland-adreno.profile.toml`](wayland-adreno.profile.toml)
