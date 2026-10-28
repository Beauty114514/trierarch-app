# Firefox crashes or misbehaves under PRoot

English | [中文](../zh-CN/firefox-proot.md)

## Symptoms

Firefox exits at startup or its content processes behave incorrectly under
PRoot.

## Cause

Firefox's content sandbox assumes Linux namespaces, `/proc` and permission
behavior that PRoot's user-space view does not fully reproduce.

## Workaround

Install Firefox, then open `about:config` and set:

| Key | Value |
| --- | --- |
| `media.cubed.sandbox` | `false` |
| `security.sandbox.content.level` | `0` |

Restart Firefox.

## Warning

This weakens Firefox's sandbox. Use it only for local, trusted PRoot sessions;
do not reuse that profile for untrusted browsing without additional isolation.

## Early-version note

This entry comes from the early `trierarch-optimize` notes. It remains relevant
for older PRoot/rootfs setups; newer environments may behave differently.
