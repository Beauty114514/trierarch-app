# Chapter 3 — Profile fields

English | [中文](../../zh-CN/tutorials/03-profile-fields.md)

Every profile is a TOML file. Create it with the CLI and edit it before starting:

```sh
trierarch --create arch-demo
trierarch --config arch-demo
trierarch --status arch-demo
```

The file lives at:

```text
/data/user/0/app.trierarch/files/config/profiles/<id>.toml
```

## Common top-level fields

| Field | Required | Meaning |
| --- | --- | --- |
| `id` | yes | unique profile ID; lowercase letters, digits, `-` |
| `name` | no | display name |
| `runtime` | yes | `proot`, `chroot` or `droidspaces` |
| `shell` | PRoot/chroot | absolute shell path inside the rootfs |
| `rootfs` | PRoot/chroot | absolute rootfs path |
| `container` | DroidSpaces | existing container name |
| `user` | DroidSpaces | user inside the container |

## Runtime differences

### PRoot

```toml
runtime = "proot"
rootfs = "/data/user/0/app.trierarch/files/rootfs/arch"
shell = "/bin/bash"
```

- rootfs is in the app-private directory;
- no root required;
- no `container` or `user`;
- the PRoot KDE Wayland path has an extra KWin compatibility requirement, described
  in [Chapter 5](05-desktops.md).

### chroot

```toml
runtime = "chroot"
rootfs = "/data/local/my-rootfs"
shell = "/bin/bash"
```

- rootfs is a directory on the device;
- requires root and a working `su`;
- no `container` or `user`;
- Trierarch supervises the session.

### DroidSpaces

```toml
runtime = "droidspaces"
container = "Arch"
user = "beauty"
```

- the container must already exist;
- no `rootfs` or `shell` at the top level;
- Trierarch starts the container and manages the attachment;
- do not point `container` at a container that was started outside the profile
  when X11 attachment is required.

## Display

```toml
[display]
type = "wayland"   # none / x11 / wayland
desktop_scale = 125
```

- `none`: terminal;
- `x11`: Trierarch's X11 surface;
- `wayland`: Trierarch's Wayland host and a nested compositor.

`desktop_scale` is optional and only valid with `type = "wayland"`. It scales
the guest desktop output, not the Android app UI or terminal. Supported values
are `100`, `125`, `150`, `200`, `250`, `300` and `400`; the default is `100`.
Restart the profile after changing it.

## Launch command

```toml
[launch]
argv = ["startplasma-wayland"]
```

`argv` is an argument array, not a shell string. Use it to select the desktop or
add only the environment variables that desktop actually needs. Do not put a
whole shell script here unless the desktop requires it.

## Graphics

```toml
[graphics]
renderer = "llvmpipe"
buffer = "auto"
qt_quick_backend = "auto"
```

| Renderer | Use |
| --- | --- |
| `llvmpipe` | software rendering; best first test |
| `virgl` | host VirGL/vtest acceleration |
| `venus` | experimental Vulkan/Venus path |
| `adreno` | Qualcomm Adreno path; Wayland only |

`buffer` defaults to `auto`; `virgl`/`venus` normally use `shm`, other paths
normally use `dmabuf`. `qt_quick_backend = "software"` is commonly needed for
Plasma with VirGL.

Hardware components are covered in [Chapter 4](04-hardware-acceleration.md).

## Audio and compatibility

```toml
[audio]
backend = "pipewire"

[compat]
udev_monitor = "auto"
```

These are optional for a first terminal test. Keep `udev_monitor = "auto"` for
desktop sessions unless a specific guest needs otherwise.

Next: [Chapter 4 — Hardware acceleration](04-hardware-acceleration.md).
