# Building

English | [中文](../../zh-CN/upstream/build.md)

Development requirements:

- JDK 21;
- Android SDK/NDK;
- Rust with `cargo-ndk`;
- Meson/Ninja;
- guest cross compilers used by the asset tasks (for example
  `aarch64-linux-gnu-gcc`).

Build the APK:

```sh
./gradlew :app:assembleDebug
```

Output:

```
app/build/outputs/apk/debug/app-debug.apk
```

The build also compiles:

- the Rust PTY/native library;
- the PRoot runtime;
- X11/Termux:X11;
- VirGL/Venus components;
- the Wayland IME bridge;
- the chroot session supervisor;
- the DroidSpaces owner supervisor;
- the PipeWire runtime.

---
