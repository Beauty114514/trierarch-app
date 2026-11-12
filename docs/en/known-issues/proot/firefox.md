# Firefox crashes or misbehaves under PRoot

English | [中文](../../../zh-CN/known-issues/proot/firefox.md)

## Symptoms

Firefox exits at startup, its content processes fail, or audio/network behavior
is unstable under PRoot.

## Cause

Firefox's content, GMP and socket sandboxes assume Linux namespaces, `/proc`
behavior and permission checks that PRoot's user-space view does not fully
reproduce. PRoot can also make Firefox believe the device is offline.

## Fix: copy this block into the PRoot shell

```bash
PROFILE=$(find "$HOME/.mozilla/firefox" -maxdepth 1 -type d -name '*.default*' | head -n 1)
test -n "$PROFILE"
cat >> "$PROFILE/user.js" <<'EOF'
pref("network.manage-offline-status", false);
pref("browser.offline", false);
pref("security.sandbox.content.level", 0);
pref("media.cubeb.sandbox", false);
pref("security.sandbox.socket.process.level", 0);
pref("browser.shell.checkDefaultBrowser", false);
pref("datareporting.policy.dataSubmissionEnabled", false);
pref("browser.aboutwelcome.enabled", false);
pref("browser.startup.homepage", "about:blank");
pref("layout.css.devPixelsPerPx", "1.25");
EOF
```

Restart Firefox after running it.

## System-wide alternative

If the rootfs should apply these preferences to every Firefox profile:

```bash
mkdir -p /usr/lib/firefox/defaults/pref
cat > /usr/lib/firefox/defaults/pref/trierarch-firefox.js <<'EOF'
pref("network.manage-offline-status", false);
pref("browser.offline", false);
pref("security.sandbox.content.level", 0);
pref("media.cubeb.sandbox", false);
pref("security.sandbox.socket.process.level", 0);
pref("browser.shell.checkDefaultBrowser", false);
pref("datareporting.policy.dataSubmissionEnabled", false);
pref("browser.aboutwelcome.enabled", false);
pref("browser.startup.homepage", "about:blank");
pref("layout.css.devPixelsPerPx", "1.25");
EOF
```

Firefox ESR uses `/usr/lib/firefox-esr/defaults/pref/` instead.

If sandbox preferences are not enough, start Firefox with:

```bash
export MOZ_DISABLE_CONTENT_SANDBOX=1
export MOZ_DISABLE_GMP_SANDBOX=1
export MOZ_DISABLE_RDD_SANDBOX=1
```

On Wayland, also set:

```bash
export MOZ_ENABLE_WAYLAND=1
```

## Notes

`layout.css.devPixelsPerPx` controls Firefox UI scaling only. Adjust `1.25` for
the display; it does not change the desktop scale.

## Warning

Disabling Firefox sandboxes weakens isolation. Use these settings only in a
local, trusted PRoot session; do not reuse the same profile for untrusted
browsing without additional isolation.
