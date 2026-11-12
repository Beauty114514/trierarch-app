# KDE Baloo / `tags:/` pop-ups under PRoot

English | [中文](../../../zh-CN/known-issues/proot/baloo-tags.md)

## Symptoms

KDE shows “Could not enter folder tags:/” or repeated Baloo warnings.

## Cause

Baloo combines KDE's indexer with filesystem watching. PRoot's namespaces, bind
layout, `/proc` view and inotify behavior differ from a normal desktop, so
Baloo can mis-index or fail on virtual `tags:/` access.

## Fix: copy this block into the PRoot shell

```bash
mkdir -p "$HOME/.config"
printf '%s\n' '[Basic Settings]' 'Indexing-Enabled=false' > "$HOME/.config/baloofilerc"
if command -v balooctl6 >/dev/null 2>&1; then
    balooctl6 disable
    balooctl6 purge
elif command -v balooctl >/dev/null 2>&1; then
    balooctl disable
    balooctl purge
fi
```

Then log out and back into Plasma. The `tags:/` error should stop.

## System-wide alternative

If the PRoot session runs as root and the setting should apply to every user:

```bash
mkdir -p /etc/xdg
printf '%s\n' '[Basic Settings]' 'Indexing-Enabled=false' > /etc/xdg/baloofilerc
```

## Notes

The `tags:/` entry can remain in Dolphin's sidebar even after Baloo is disabled.
The error pop-up should stop; the virtual location itself is not provided by
PRoot.
