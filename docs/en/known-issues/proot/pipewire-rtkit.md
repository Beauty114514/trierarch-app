# Plasma black screen from PipeWire and the realtime portal

English | [中文](../../../zh-CN/known-issues/proot/pipewire-rtkit.md)

## Symptoms

Plasma starts and may show the desktop for a short time, then turns black.
The compositor (KWin) can remain alive while `plasmashell` disappears. On some
systems the shell is restarted repeatedly, producing a black screen followed
by a brief recovery.

## Cause

Inside a Linux container, the `xdg-desktop-portal` realtime portal can report
a realtime budget of `0`. PipeWire's `libpipewire-module-rt` writes that value
to the process hard limit:

```text
RLIMIT_RTTIME=0
```

The kernel then terminates threads that consume realtime CPU time with
`SIGKILL`. In this environment the whole Plasma shell is affected, so the
desktop disappears even though KWin is still running.

A supervisor that restarts `plasmashell` only hides the symptom: the new shell
reads the same portal budget, gets `RLIMIT_RTTIME=0`, and is killed again.

## Workaround

Disable the RTKit/portal realtime path for the Plasma session:

```sh
exec env DISABLE_RTKIT=1 dbus-run-session -- startplasma-wayland
```

In a Trierarch profile, the launch entry should be:

```toml
[launch]
argv = ["env", "DISABLE_RTKIT=1", "dbus-run-session", "--", "startplasma-wayland"]
```

If the profile keeps additional environment variables, use:

```toml
argv = ["sh", "-lc", "exec env DISABLE_RTKIT=1 dbus-run-session -- startplasma-wayland"]
```

Apply the same environment to each Plasma profile that can hit this path,
including the Arch and Kali profiles.

After the change, verify inside the guest that the realtime hard limit is not
zero:

```sh
prlimit --pid "$(pgrep -n plasmashell)" --rttime
```

`RLIMIT_RTTIME` should be `unlimited`, and the desktop should remain visible.

## Notes

This is a workaround for the interaction between the container, the realtime
portal, and PipeWire's RT module. It disables realtime scheduling for the
affected session; audio latency may increase, but the desktop stays usable.
