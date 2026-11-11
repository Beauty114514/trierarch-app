# Firefox 在 PRoot 中崩溃或异常

[English](../../../en/known-issues/proot/firefox.md) | 中文

## 现象

Firefox 启动即退出、内容进程异常，或音频/网络行为不稳定。

## 原因

Firefox 的内容、GMP 和 socket 沙箱依赖 Linux 命名空间、`/proc` 行为和权限检查，
PRoot 的用户态视图无法完整复现这些条件。PRoot 还可能让 Firefox 误判设备离线。

## 修复：把下面整块复制到 PRoot shell

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

执行后重启 Firefox。

## 系统级替代方案

如果希望 rootfs 内所有 Firefox profile 都生效：

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

Firefox ESR 使用 `/usr/lib/firefox-esr/defaults/pref/`。

如果沙箱配置仍不生效，启动 Firefox 时加：

```bash
export MOZ_DISABLE_CONTENT_SANDBOX=1
export MOZ_DISABLE_GMP_SANDBOX=1
export MOZ_DISABLE_RDD_SANDBOX=1
```

Wayland 会话下再加：

```bash
export MOZ_ENABLE_WAYLAND=1
```

## 说明

`layout.css.devPixelsPerPx` 只控制 Firefox 自己的界面缩放，不改变整个桌面缩放。
按屏幕情况调整 `1.25`。

## 警告

关闭 Firefox 沙箱会削弱隔离。只建议在本地、可信的 PRoot 会话中使用；不要在
没有额外隔离的情况下用同一配置浏览不可信内容。
