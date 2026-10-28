# KDE Baloo / `tags:/` pop-ups under PRoot

English | [中文](../zh-CN/baloo-tags.md)

## Symptoms

KDE shows “Could not enter folder tags:/” or repeated Baloo warnings.

## Cause

Baloo combines KDE's indexer with filesystem watching. PRoot's namespaces, bind
layout, `/proc` view and inotify behavior differ from a normal desktop, so
Baloo can mis-index or fail on virtual `tags:/` access.

## Workaround

In the PRoot shell (Plasma 6 uses `balooctl6`; Plasma 5 may use `balooctl`):

```bash
balooctl6 status
balooctl6 purge
balooctl6 suspend
balooctl6 disable
```

Disabling Baloo is usually a good trade-off on a phone: it stops noisy indexing
and avoids repeated `tags:/` failures.

## Early-version note

This entry comes from the early `trierarch-optimize` notes. It remains useful
for older KDE/rootfs setups; newer versions may produce different warnings.
