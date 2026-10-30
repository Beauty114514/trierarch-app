# Security Policy

Trierarch is an early-stage project and has no supported stable release yet.
Security fixes are developed against the latest commit on the default branch.

## Reporting a vulnerability

**Do not open a public issue for a security problem.**

Use GitHub's private vulnerability reporting:

1. Open the repository's **Security** tab.
2. Choose **Report a vulnerability**.
3. Describe the issue with reproduction steps and the affected build.

If private reporting is not available, contact the maintainer through the
GitHub profile linked from the repository. Do not include exploit details in a
public issue.

Please include:

- affected commit or build;
- device, Android version, SoC and GPU;
- runtime and display mode;
- whether root was required;
- a minimal reproduction;
- impact, for example guest-to-Android privilege escalation, host path escape,
  unsafe file permissions, or command injection.

There is no bug bounty. Reports are handled on a best-effort basis; please allow
time for investigation before public disclosure.

## Threat model

Trierarch is a privileged Android app. It can start chroot sessions, PRoot
sessions and DroidSpaces containers, and it runs a root helper for lifecycle
management.

- Root inside a container or rootfs is powerful. A hostile root process can
  reach the host. **Do not run untrusted rootfs images or containers.**
- The Device already being rooted is not, by itself, a Trierarch vulnerability.
- Guest root being able to modify the guest is expected.
- A malicious rootfs that only affects its own guest is expected behavior, not a
  sandbox escape.

## In scope

Examples of issues that are in scope:

- escaping the guest or container into the Android host;
- command injection through profile fields, container names, paths or launch
  arguments;
- path traversal in profile loading, rootfs import or file handling;
- unsafe permissions on runtime sockets, logs or app-private state;
- leaking credentials, tokens or personal files through logs or the document
  provider;
- a rooted helper being controllable by an unprivileged app or user;
- a lifecycle supervisor that can be tricked into stopping or starting an
  unrelated container.

## Out of scope

- root inside a container being able to modify that container;
- running an intentionally malicious rootfs and suffering the consequences;
- general Android rooting risks;
- bugs in upstream projects (PRoot, Termux, Termux:X11/Lorie, Mesa, virglrenderer,
  PipeWire, DroidSpaces, desktops). Report those upstream; Trierarch can
  coordinate if the issue affects integration.
- feature requests and non-security crashes; use the normal issue forms.
