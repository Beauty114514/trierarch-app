# 构建

中文 | [English](../../en/upstream/build.md)

开发构建需要：

- JDK 21；
- Android SDK/NDK；
- Rust 和 `cargo-ndk`；
- Meson/Ninja；
- 构建 guest 资源所需的交叉编译器（如 `aarch64-linux-gnu-gcc`）。

构建 APK：

```sh
./gradlew :app:assembleDebug
```

产物：

```
app/build/outputs/apk/debug/app-debug.apk
```

构建脚本会同时编译：

- Rust PTY/native 库；
- PRoot 运行库；
- X11/Termux:X11；
- VirGL/Venus 组件；
- Wayland IME bridge；
- chroot session supervisor；
- DroidSpaces owner supervisor；
- PipeWire 运行时。

---
