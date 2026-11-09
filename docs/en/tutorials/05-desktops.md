# Chapter 5 — Desktops

English | [中文](../../zh-CN/tutorials/05-desktops.md)

Install the desktop in the guest first. Trierarch does not bundle KDE, Hyprland,
XFCE, or their drivers.

## Common download commands

These are guest commands, normally run as root in the container/rootfs:

```sh
pacman -S --needed plasma-meta        # KDE Plasma
pacman -S --needed hyprland           # Hyprland
pacman -S --needed xfce4 xfce4-goodies # XFCE, optional
```

Verify the launcher:

```sh
command -v startplasma-wayland
command -v start-hyprland
command -v startxfce4
```

For normal chroot and DroidSpaces sessions, these packages are enough for the
desktop; no Trierarch desktop patch is required.

## KDE Plasma

### KDE Plasma on PRoot

PRoot Plasma Wayland additionally needs the KWin compatibility library because
PRoot cannot open a real DRM render node.

The full developer procedure is in:

- [PRoot + Adreno + Plasma Wayland](../developer-preview/proot/wayland-adreno.md)

Components:

```text
KWin compatibility library  → KWin source + kwin-compat patch
Adreno guest userspace      → Mesa recipe + external Turnip component
```

Current status of the release assets:

```bash
KWIN_COMPAT_URL="<not-published>"
ADRENO_GPU_COMPONENT_URL="<not-published>"
```

Until a release exists, build the compatibility library locally from
`trierarch-packages/kwin-compat/` and use the developer component paths. Install
it outside `/usr/lib`:

```text
/opt/trierarch/kwin/6.8.2/compat/
├── libkwin.so -> libkwin.so.6
├── libkwin.so.6 -> libkwin.so.6.8.2
└── libkwin.so.6.8.2
```


For a developer build, the source patch and build scripts are:

- `trierarch-packages/kwin-compat/patches/0001-wayland-render-node.patch`
- `trierarch-packages/kwin-compat/build-arch.sh`

Build the local artifact with that recipe, then install it outside `/usr/lib`:

```sh
install -d /opt/trierarch/kwin/6.8.2/compat
install -m 755 libkwin-6.8.2-2-archlinuxarm-aarch64.so \
  /opt/trierarch/kwin/6.8.2/compat/libkwin.so.6.8.2
ln -sfn libkwin.so.6.8.2 /opt/trierarch/kwin/6.8.2/compat/libkwin.so.6
ln -sfn libkwin.so.6 /opt/trierarch/kwin/6.8.2/compat/libkwin.so
```

After release, replace the local artifact with the verified
`KWIN_COMPAT_URL` download. The placeholder is not a working URL yet.

The profile must prepend that directory to `LD_LIBRARY_PATH`. Do not overwrite
`/usr/lib/libkwin.so.6.8.2`.

### KDE Plasma on chroot or DroidSpaces

chroot and DroidSpaces use the real DRM/KGSL device. They do **not** need the
PRoot `kwin-compat` library.

Use the normal packages and the appropriate graphics path from
[Chapter 4](04-hardware-acceleration.md). Start Plasma with:

```toml
[launch]
argv = ["dbus-run-session", "--", "startplasma-wayland"]
```

or the Adreno-oriented launch array in the sample profile.

## Hyprland

### Normal installation

```sh
pacman -S --needed hyprland
command -v start-hyprland
```

For a first test, use the renderer path appropriate to the runtime:

- PRoot: start with `llvmpipe` or the supported VirGL path;
- chroot: real DRM/KGSL is available;
- DroidSpaces: real DRM/KGSL is available.

### Aquamarine version

Hyprland's nested Wayland behavior depends on **aquamarine**. An older distro
package can fall back to a headless output and produce a black screen.

Check the installed version:

```sh
pacman -Q aquamarine hyprland
```

If the distro package is older than the fix, build the latest aquamarine:

- Upstream: [hyprwm/aquamarine](https://github.com/hyprwm/aquamarine)

```sh
git clone https://github.com/hyprwm/aquamarine.git
cmake -S aquamarine -B aquamarine/build -G Ninja \
  -DCMAKE_BUILD_TYPE=Release
cmake --build aquamarine/build
cmake --install aquamarine/build
```

Then confirm that Hyprland loads the new library and retest the session. If the
distro's Hyprland package was built against an incompatible aquamarine ABI,
rebuild Hyprland against the new aquamarine instead of mixing the two.

The known-issue details for this path are in:

- [Hyprland black screen in nested Wayland](../developer-preview/proot/hyprland-aquamarine.md)

## Runtime differences at a glance

| Desktop | PRoot | chroot | DroidSpaces |
| --- | --- | --- | --- |
| KDE Plasma Wayland | KWin compat required | normal packages | normal packages |
| Hyprland | Aquamarine must match | normal packages | normal packages |
| XFCE | normal X11 setup | normal X11 setup | normal X11 setup |

Next: [Chapter 6 — Run and choose a sample](06-run.md).
