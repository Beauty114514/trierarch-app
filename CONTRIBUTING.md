# Contributing to Trierarch

Trierarch is an early-stage project. Contributions are welcome, but please keep
changes focused and testable. English is used for code and issue forms; Chinese
documentation and comments are welcome where they help users.

## Before you start

- Read the [documentation index](docs/README.md) and
  [known issues](docs/known-issues/README.md).
- Search existing issues before opening a new one.
- For a bug, use the **Bug report** form; it asks for the device, runtime,
  display, renderer and logs needed to reproduce the problem.
- For a feature or a new runtime, use the **Feature request** form. New runtimes
  take design, implementation and testing time.
- Do not report security problems publicly. Follow [SECURITY.md](SECURITY.md).

## Repository layout

Trierarch is split across two repositories that are normally checked out as
sibling directories:

```text
trierarch-app/          Android app, Kotlin UI, docs, issue templates
trierarch-packages/     native runtimes, packaging, upstream patches and assets
```

The app build expects the packages checkout at `../trierarch-packages/`.
Some packaged artifacts are produced by the package build scripts rather than
committed to Git.

## Development requirements

- JDK 21;
- Android SDK and NDK;
- Rust and `cargo-ndk`;
- Meson and Ninja;
- the guest cross compiler used by the asset tasks
  (for example `aarch64-linux-gnu-gcc`);
- an Android device for runtime and display testing.

## Build and test

Run unit tests that do not require a device:

```bash
./gradlew :app:testDebugUnitTest
```

Run the Rust tests:

```bash
cd ../trierarch-packages/native
cargo test
```

Build the debug APK:

```bash
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Device tests are manual for now. When a change affects a runtime, display or
renderer, record what you tested:

- device and Android version;
- SoC and GPU;
- runtime;
- display;
- renderer;
- whether effects were enabled;
- the relevant logs or a short result.

Update [docs/compatibility.en.md](docs/compatibility.en.md) and
[docs/compatibility.zh-CN.md](docs/compatibility.zh-CN.md) when you have a
result worth recording. Mark untested combinations as untested; do not turn a
missing result into a failure.

## Code guidelines

- Keep changes focused; avoid unrelated formatting or drive-by rewrites.
- Preserve upstream source boundaries. PRoot, Lorie, virglrenderer, Mesa and
  other third-party code are not relicensed by Trierarch.
- Keep the distinction between the host app, the guest runtime and the display
  layer clear.
- Do not add prebuilt binaries without a source reference, pinned revision and
  license note.
- Do not commit build output, APKs, local keystores, credentials, device dumps or
  personal data.
- Do not weaken TLS, SELinux, permissions or sandboxing merely to silence a
  warning.

## Documentation

Product documentation is bilingual:

- English: `README.md`, `docs/en/`,
  `docs/compatibility.en.md`;
- Chinese: `README.zh-CN.md`, `docs/zh-CN/`,
  `docs/compatibility.zh-CN.md`.

Known issues are one file per problem, with separate `en/` and `zh-CN/` trees.
If you add a product-level behavior, update both languages or clearly mark the
translation as pending.

## Pull requests

- Open one focused pull request per change.
- Describe what changed and why.
- List the tests you ran and the device combinations you tested.
- Update documentation and compatibility notes when behavior changes.
- Mention known limitations instead of hiding them.
- Do not include generated files, secrets or personal logs.

By contributing, you agree that your contribution is distributed under the
project's GPL-3.0 license and that you have the right to submit it. Preserve all
upstream copyright and license notices.
