# Adreno 740: corruption when the notification shade or a floating window appears

English | [中文](../../zh-CN/known-issues/adreno-740-ubwc.md)

## Symptoms

- In a PRoot, DroidSpaces or chroot hardware-accelerated session, the desktop/game
  picture shows colored vertical stripes, blocky mosaics, tearing or black
  flashes;
- Trierarch's own UI stays correct; only the guest picture is corrupted;
- Pulling down the notification shade, the status bar, the volume panel, a
  floating window, or the screen-recording overlay triggers or clearly worsens
  it;
- The picture recovers when the overlay disappears;
- It is not specific to one runtime; all three runtimes can show it.

## Scope

- GPU: Adreno 740 (Snapdragon 8 Gen 2 / SM8550);
- Runtime: **any**. PRoot, DroidSpaces and chroot can all show it; this is a
  GPU/display-pipeline issue, not a runtime-specific one.

## Cause

Adreno UBWC-compressed buffers must carry the correct compression marker. When
Turnip/KGSL allocates a UBWC buffer on a real render node and the Android
display pipeline does not receive that marker, it reads compressed data as raw
pixels. The notification shade, a floating window or a recording overlay changes
SurfaceFlinger's composition choice or the buffer transform, so stripes and
blocky corruption appear at random. This is not a Trierarch container bug.

The problem is in the GPU's UBWC marker and the Android display pipeline, not
in the container. DroidDeck is a proot-based project and hits the same issue on
Adreno 740, so do not read this as a runtime-specific problem.

DroidDeck documented the same class of issue on Adreno 740
([#1960](https://github.com/Droid-Deck/DroidDeck/issues/1960),
[#3018](https://github.com/Droid-Deck/DroidDeck/issues/3018),
[#2710](https://github.com/Droid-Deck/DroidDeck/issues/2710)); its workaround
controls the marker through `FD_DEV_FEATURES=enable_tp_ubwc_flag_hint=...`.

## Workaround

Add one entry to the `env` list in the profile's `[launch]`, before
`dbus-run-session`:

```toml
[launch]
argv = ["env", "FD_DEV_FEATURES=enable_tp_ubwc_flag_hint=1", "GALLIUM_DRIVER=freedreno", "MESA_LOADER_DRIVER_OVERRIDE=kgsl", "dbus-run-session", "--", "startplasma-wayland"]
```

For a Hyprland profile, do the same and replace the final
`startplasma-wayland` with `start-hyprland`; keep the other `env` entries already
present in the profile.

If the profile launches through a shell:

```sh
export FD_DEV_FEATURES=enable_tp_ubwc_flag_hint=1
exec ...
```

Try `1` first. If it does not help, or makes things worse, try `0`:

```toml
"FD_DEV_FEATURES=enable_tp_ubwc_flag_hint=0",
```

Restart the guest session/compositor after the change; the environment variable
is read when the compositor creates its Vulkan device.

Verify inside the guest:

```sh
tr '\0' '\n' < /proc/$(pidof kwin_wayland)/environ | grep FD_DEV_FEATURES
```

For Hyprland:

```sh
tr '\0' '\n' < /proc/$(pidof Hyprland)/environ | grep FD_DEV_FEATURES
```

## Notes

- This is an Adreno 740 workaround; apply it to every Adreno 740 profile that
  shows the symptoms, including PRoot, DroidSpaces and chroot. Do not add it to
  profiles for other GPUs.
- `1` keeps UBWC compression. `0` may make the display pipeline treat the buffer
  as uncompressed/unmarked, which can cost bandwidth and power. Keep `1` when it
  works.
- If a system or driver update brings the corruption back, retry `1` and `0`.
- It is not a runtime-level switch; add it on any of the three runtimes when the
  symptoms appear.
