# PRoot — Hyprland

English | [中文](../../zh-CN/tutorials/05-desktops-proot-hyprland.md)

[Back to PRoot desktops](05-desktops-proot.md)

Complete the [common Mesa 26 + Turnip setup](05-desktops-proot.md)
first. Hyprland also needs `libaquamarine-compat.so` and a wallpaper daemon.

## Packages

Inside the guest:

```sh
pacman -S --needed hyprland dbus swaybg
command -v Hyprland
command -v start-hyprland
command -v dbus-run-session
command -v swaybg
```

For a fuller desktop, also install:

```sh
pacman -S --needed \
  kitty waybar wofi mako \
  ttf-jetbrains-mono-nerd noto-fonts noto-fonts-cjk
```

`--needed` skips packages that are already installed. `dbus` provides
`dbus-run-session`; `swaybg` draws the wallpaper.

## Aquamarine compatibility library

PRoot Hyprland uses one combined library:

```text
libaquamarine-compat.so
```

It is published as a Trierarch release asset. If you build it from source, run
`make combined` in `trierarch-packages/aquamarine-compat`.

Install it inside the guest:

```sh
install -d /opt/trierarch/compat
install -m 755 /path/to/libaquamarine-compat.so /opt/trierarch/compat/
```

Do not install the KWin compatibility library for Hyprland.

## Profile

Use the sample profile:

- [`arch-proot-hyprland-adreno.toml`](../../profiles/arch-proot-hyprland-adreno.toml)

The sample adds two PRoot-specific things:

```sh
ln -sfn /tmp/trierarch-wayland-user /tmp/t
export XDG_RUNTIME_DIR=/tmp/t
```

Hyprland's IPC socket path is otherwise exactly at the Unix-socket limit and
`hyprctl`/Waybar cannot connect. The symlink shortens the path without moving
the host Wayland socket.

The launch command uses the upstream watchdog wrapper:

```sh
exec dbus-run-session -- start-hyprland -- --i-am-really-stupid
```

`dbus-run-session` provides the session bus. `--` forwards
`--i-am-really-stupid` to Hyprland because the PRoot guest runs as root.

## Wallpaper

Place the image inside the guest, for example:

```text
/home/user/Pictures/luca-micheli.jpg
```

From the host, one way to copy it is:

```sh
adb push /path/to/luca-micheli.jpg /data/local/tmp/
adb shell run-as app.trierarch mkdir -p \
  /data/data/app.trierarch/files/rootfs/arch/home/user/Pictures
adb shell run-as app.trierarch cp \
  /data/local/tmp/luca-micheli.jpg \
  /data/data/app.trierarch/files/rootfs/arch/home/user/Pictures/
```

Start `swaybg` from the Hyprland Lua config:

```lua
hl.on("hyprland.start", function()
    hl.exec_cmd("GLYCIN_DISABLE_SANDBOX=i-know-the-risks swaybg -i /home/user/Pictures/luca-micheli.jpg -m fill >/tmp/swaybg.log 2>&1")
end)
```

Arch's image loader uses glycin, which normally starts a `bwrap` sandbox. PRoot
cannot run `bwrap`, so without `GLYCIN_DISABLE_SANDBOX` the wallpaper silently
fails to load and the desktop stays black. The variable makes the loader run
unsandboxed; only use it for a trusted local image.

Do not use `hyprpaper` here. Its current EGL/hyprtoolkit path needs a primary
DRM device and a GBM allocator with PRIME export, and fails in PRoot with
`Couldn't open a gbm fd`. `swaybg` uses shared-memory Wayland buffers and avoids
that path.

## Start and verify

Start the profile from the app. Logs:

```text
/tmp/trierarch-hyprland-pr.log
/tmp/swaybg.log
```

Check the loaded GPU libraries while Hyprland is running:

```sh
grep -E 'libgallium-26|libvulkan_freedreno' \
  /proc/$(pidof Hyprland)/maps
```

`hyprctl` may still report a socket error in some sessions; the desktop can
render without it.

## Common failures

| Symptom | Cause |
| --- | --- |
| `CBackend::create() failed` | `libaquamarine-compat.so` missing or not in `LD_PRELOAD` |
| Black background, `Failed to load image` in the `swaybg` log | `GLYCIN_DISABLE_SANDBOX` is missing |
| `hyprpaper` exits with `Couldn't open a gbm fd` | hyprpaper needs primary DRM/GBM; use `swaybg` |
| Hyprland starts but nothing appears | Check the host surface attach and the profile log |
| `hyprctl` cannot connect | Hyprland IPC path issue; keep the `/tmp/t` runtime prefix |
