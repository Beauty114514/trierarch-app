# Hyprland black screen in nested Wayland

English | [中文](../zh-CN/hyprland-aquamarine.md)

## Symptoms

Hyprland starts, but the desktop is black and the Trierarch renderer stays at:

```
commits=0 damage=0 renders=0
```

The guest Hyprland log shows a **headless** aquamarine output:

```
Output WAYLAND-1: initialized
backend: poll fd ... for implementation headless
CHeadlessOutput::scheduleFrame ...
```

The host output itself exists:

```
interface: 'wl_output', version: 4
    name: Trierarch-1
```

## Cause

An older distro aquamarine can regress nested Wayland output handling. The
upstream fix is:

```
163b138 headless: reuse parent Wayland buffer formats when nested (#451)
```

## Workaround / fix

If the distribution repository has the fixed package, install it. Otherwise
build the latest aquamarine from source in the guest:

```bash
pacman -S --needed --noconfirm cmake ninja git \
    hyprutils wayland wayland-protocols libdrm libinput seatd \
    libdisplay-info mesa libglvnd pixman

rm -rf /tmp/aquamarine /tmp/aquamarine-build
git clone --depth 1 https://github.com/hyprwm/aquamarine /tmp/aquamarine
cmake -S /tmp/aquamarine -B /tmp/aquamarine-build \
    -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX=/usr
cmake --build /tmp/aquamarine-build -j4
cmake --install /tmp/aquamarine-build
ldconfig
```

Restart Hyprland. The host renderer should show non-zero `commits`/`renders`.

## Notes

- Hyprland/aquamarine only. KWin/Plasma does not use aquamarine.
- `AQ_BACKENDS` is not a valid aquamarine variable; do not use it.
- A single application window flickering after this fix is outside the scope of this
  issue.
