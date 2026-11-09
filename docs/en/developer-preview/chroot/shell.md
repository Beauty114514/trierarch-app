# chroot terminal

English | [中文](../../../zh-CN/developer-preview/chroot/shell.md)

chroot runs the rootfs with the device's `su` provider. Trierarch starts and supervises the session.

## Create and edit the profile

```sh
trierarch --create arch-chroot-terminal
trierarch --config arch-chroot-terminal
trierarch --status arch-chroot-terminal
```

Set `runtime = "chroot"`, the absolute `rootfs`, `shell`, and `display.type = "none"`. The complete sample is
[`shell.profile.toml`](shell.profile.toml); change the
container/rootfs/user values to match your device.

Start it with:

```sh
trierarch --run arch-chroot-terminal
```

The session needs root; a missing or denied `su` prompt is a setup problem, not a graphical issue.

Profile sample: [`shell.profile.toml`](shell.profile.toml)
