# W4ME Station Docker toolchain

The `localhost/w4me-station:latest` image is the only local project image. It
pins JDK 8, KEmulator, and supporting tools.

## Setup

Install `just`, `flock`, and a `docker` command on the Linux host. Docker Engine
works directly; Podman users can provide a Docker-compatible `docker` command.
Build the image once per machine:

```sh
just setup
just doctor
```

`just setup` fingerprints the Containerfile and its local inputs. Repeating it
without a toolchain change is a no-op. A failed build leaves the existing
canonical image intact. After a successful build, setup removes superseded
project-labelled images and Podman build records, then verifies that only the
canonical image remains. Podman layer caching is disabled for this image so a
successful multi-stage rebuild leaves no visible `<none>` image records. Cleanup
is filtered by the W4ME project label; unrelated images and volumes are never
pruned. A running project container blocks replacement. Set
`W4ME_TOOLCHAIN_FORCE_REBUILD=1` to rebuild the image and refresh the pinned
Fedora Minimal packages without a source change.

Project scripts source `tools/container/env.sh`. When launched from the host,
they automatically re-exec themselves in a disposable `docker run --rm`
container with a sanitized `PATH`. The repository is bind-mounted at
`/workspace`, so `build/` and `dist/` remain on the host. Java ME sources target
Java 1.3. Both `J2ME_SOURCE` and `J2ME_TARGET` are pinned to `1.3` by
`tools/container/env.sh`.

The image defaults to an unprivileged numeric user. The runner explicitly
preserves the host UID and GID for generated files, and also uses
`--userns=keep-id` when the `docker` command is backed by rootless Podman.

## KEmulator

Start a clean headless session for a MIDlet JAR:

```sh
tools/kemu/run.sh session start path/to/application.jar
tools/kemu/run.sh session cmd status
tools/kemu/run.sh session cmd screenshot --out /tmp/w4me.png
tools/kemu/run.sh session stop
```

The emulator bundle from `/opt/kemu` is copied to a temporary writable
directory for every session because KEmulator writes RMS and runtime state
next to its bundle. The default display size is `240x320`; override it with
`KEMU_SIZE=160x160` (or another `WxH` value). The default virtual display is
`:98`, configurable through `KEMU_DISPLAY`.

The raw automation entry point is also available:

```sh
tools/kemu/run.sh session start path/to/application.jar
tools/kemu/run.sh session cmd tap 80 80
tools/kemu/run.sh session cmd key FIRE
tools/kemu/run.sh session stop
```

`session start` creates the named container `w4me-station-kemu` so Xvfb and
KEmulator can remain alive between commands. `session stop` removes it. If a
host interruption leaves the session running, remove it with:

```sh
docker rm -f w4me-station-kemu
```

## Pinned tools

| Tool                        | Version / source                                                                             |
| --------------------------- | -------------------------------------------------------------------------------------------- |
| Base image                  | Fedora Minimal 44                                                                            |
| Temurin JDK                 | `jdk8u492-b09` in `/opt/jdk8`                                                                |
| Java ME source/target       | `1.3` / `1.3`                                                                                |
| ProGuard                    | `7.0.1`, Java ME `StackMap` preverification                                                  |
| CLDC/MIDP API lint          | MicroEmulator `cldcapi11:2.0.4` and `midpapi20:2.0.4` LGPL build-time stubs, checksum-pinned |
| KEmulator                   | `mulfyx/KEmulator` commit `eefea4dd8373` in `/opt/kemu`                                      |
| WABT                        | Fedora Minimal 44 package (`wasm2wat`, `wasm-objdump`, `wasm-validate`)                      |
| Python, binutils, diffutils | Fedora Minimal 44 packages                                                                   |
| ShellCheck, shfmt           | Checksum-pinned standalone releases                                                          |
