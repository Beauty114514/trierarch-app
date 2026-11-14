# Chapter 1 — Trierarch CLI

English | [中文](../../zh-CN/tutorials/01-cli.md)

Trierarch is controlled from its shell with the `trierarch` command. Profiles are
TOML files, but you normally create and edit them through the CLI.

## Command overview

```sh
trierarch --help                 # show usage
trierarch --list                 # list profiles
trierarch --status [ID]          # show runtime/profile state
trierarch --create ID            # create a profile template
trierarch --config ID            # edit a profile with $EDITOR
trierarch --delete ID            # delete a profile
trierarch --run ID               # start a profile
trierarch --stop [ID]            # stop the current or named session
trierarch --rerun ID             # restart a profile
trierarch --import ARCHIVE --name NAME
trierarch --remove NAME
```

Run these commands in the Trierarch shell, not in an unrelated Android terminal.

## Create and edit a profile

```sh
trierarch --create arch-demo
trierarch --config arch-demo
```

- `--create` writes a template at
  `/data/user/0/app.trierarch/files/config/profiles/arch-demo.toml`.
- `--config` opens the file in `$EDITOR` and validates it after saving.
- If no editor is configured, edit the TOML file directly at that path.
- Check that it parses before starting:

```sh
trierarch --status arch-demo
```

Profile IDs are lowercase letters, digits and `-`, start with a letter, and are
at most 64 characters.

## Import and remove a PRoot rootfs

A PRoot rootfs is imported from an `.tar.xz` archive:

```sh
trierarch --import ~/arch-rootfs.tar.xz --name arch
```

The command prints the installed rootfs path. Put that path in the profile's
`rootfs` field. Remove it with:

```sh
trierarch --remove arch
```

## Start, stop and inspect

```sh
trierarch --run arch-demo
trierarch --status arch-demo
trierarch --stop arch-demo
```

Use `--rerun` after editing a profile if you want to replace a running session.
Do not edit a profile while its session is running unless you intend to restart
it afterward.

Next: [Chapter 2 — Deploy a runtime](02-deploy.md).
