# W4ME Station

**Run [WASM-4](https://wasm4.org/) games on Java ME phones.**

W4ME Station brings unmodified WebAssembly cartridges to CLDC 1.1 / MIDP 2.0
devices, including feature phones from the mid 2000s.

<p>
  <a href="https://github.com/mulfyx/w4me-station/actions/workflows/ci.yml"><img src="https://github.com/mulfyx/w4me-station/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <img src="https://img.shields.io/badge/Java_ME-CLDC_1.1_%2F_MIDP_2.0-ED8B00" alt="Java ME: CLDC 1.1 / MIDP 2.0">
  <img src="https://img.shields.io/badge/release-1.0.4-blue" alt="Release 1.0.4">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-green" alt="MIT License"></a>
</p>

<p align="center">
  <img src="docs/media/hero-phone.jpg" alt="W4ME Station running on a Nokia E71" width="680">
</p>

Thirteen bundled cartridges ship in a roughly 286 KB JAR. The runtime uses no
JIT, and the bundled library needs no network connection. Both release variants
stay below 300 KB.

## Download

| Variant | Application | Descriptor |
| --- | --- | --- |
| Full, with optional JSR-75 file browsing | [w4me-station.jar](https://github.com/mulfyx/w4me-station/releases/download/v1.0.4/w4me-station.jar) | [w4me-station.jad](https://github.com/mulfyx/w4me-station/releases/download/v1.0.4/w4me-station.jad) |
| Base, without JSR-75 classes or permissions | [w4me-station-base.jar](https://github.com/mulfyx/w4me-station/releases/download/v1.0.4/w4me-station-base.jar) | [w4me-station-base.jad](https://github.com/mulfyx/w4me-station/releases/download/v1.0.4/w4me-station-base.jad) |

[Release notes](https://github.com/mulfyx/w4me-station/releases/tag/v1.0.4)
and [SHA-256 checksums](https://github.com/mulfyx/w4me-station/releases/download/v1.0.4/SHA256SUMS)
are stored beside the versioned `1.0.4` artifacts.

W4ME Station targets CLDC 1.1 / MIDP 2.0 devices. It is independent from and not
endorsed by the WASM-4 maintainers.

## Cartridge library

| # | Cartridge |
| ---: | --- |
| 1 | [Sokoban](https://wasm4.org/play/sokoban/) |
| 2 | [Wasm Wars](https://wasm4.org/play/wasm-wars/) |
| 3 | [Annoying Robots](https://wasm4.org/play/annoyingrobots/) |
| 4 | [Waternet](https://wasm4.org/play/waternet/) |
| 5 | [Dragon Poker Draw](https://wasm4.org/play/dragon-poker-draw/) |
| 6 | [Tic Tac Toe](https://wasm4.org/play/tictactoe/) |
| 7 | [Watris](https://wasm4.org/play/watris/) |
| 8 | [Glowfish Chess](https://wasm4.org/play/glowfish-chess/) |
| 9 | [Duck Maze](https://wasm4.org/play/duck-maze/) |
| 10 | [Untangle](https://wasm4.org/play/untangle/) |
| 11 | [Nyan Cat](https://wasm4.org/play/nyancat/) |
| 12 | [Sound Demo](https://wasm4.org/play/sound-demo/) |
| 13 | [Plasma Cube](https://wasm4.org/play/plasma-cube/) |

Further `.wasm` files can be installed from the device.

<p align="center">
  <img src="docs/media/sokoban.jpg" alt="Sokoban running on a Nokia E71" width="31%">
  <img src="docs/media/wasm-wars.jpg" alt="Wasm Wars running on a Nokia E71" width="31%">
  <img src="docs/media/plasma-cube.jpg" alt="Plasma Cube running on a Nokia E71" width="31%">
</p>

## Features

- validated WebAssembly execution with a persistent fixed-width W4IR cache;
- WASM-4 graphics, input, audio, disk, text, and tracing host APIs;
- thirteen bundled cartridges plus HTTP(S), RMS, URL, and optional JSR-75 loading;
- phone keys, keyboard controls, pointer input, and an on-screen touch pad;
- per-cartridge persistent disk storage with checksummed RMS generations;
- one temporary in-session Save State/Load State slot from the native game menu;
- device-dependent MMAPI audio with streamed MIDI, `playTone`, and silent
  fallbacks;
- global RMS-backed sound On/Off and master-volume controls;
- deterministic host, KEmulator, and optional phoneME verification.

The release version is `1.0.4`. The verified scope and remaining limitations
are documented in [Compatibility](docs/compatibility.md).

## Requirements

Development requires Linux with `just` and a `docker` command. Docker Engine
works directly; Podman users can provide its Docker-compatible command. The
project toolchain contains JDK 8, ProGuard, KEmulator, WABT, and supporting
utilities. Version-sensitive components are pinned by the container definition.

```sh
just setup
just doctor
```

Java sources and classfiles remain pinned to Java 1.3.

## Build

```sh
just build
```

Artifacts are written to `dist/`:

| Artifact | Purpose |
| --- | --- |
| `w4me-station.jar` / `.jad` | full build with optional JSR-75 file browsing |
| `w4me-station-base.jar` / `.jad` | build without JSR-75 classes or permissions |

Both variants contain the same thirteen cartridges. The base build works on MIDP
2.0 devices that do not implement the optional FileConnection API.

## Installing on a phone

Download one matching `.jad`/`.jar` pair from the table above and keep both
files in the same directory. Use the full build on devices with JSR-75
FileConnection support; use the base build if the full build is rejected or the
device lacks that optional API.

Open the `.jad` from the phone's application manager or file manager. If the
device does not accept JAD installation, transfer and open the matching `.jar`
directly. Installation details and troubleshooting are covered in
[Installation](docs/installation.md).

## Controls

| Input | Player 1 | Player 2 |
| --- | --- | --- |
| Movement | Arrow or directional phone keys | `E`, `S`, `D`, `F` |
| Button 1 | `X`, Fire, or `5` | `Tab` |
| Button 2 | `Z` or `0` | `Q` |

Touchscreen devices display an on-screen directional pad and action buttons.
The controls stay outside the 160×160 framebuffer whenever the screen is large
enough.

`Sound settings` is available from the cartridge library and the in-game
command menu. It offers explicit WAV synthesis, MIDI synthesis, and Simple
tones profiles, a hard global Sound On/Off mute, and master volume when the
backend supports it. The form reports the active fallback when the selected
technology is unavailable. Confirmed settings persist across MIDlet restarts.

The in-game menu also provides one temporary `Save State`/`Load State` slot.
It is replaced by the next save and is cleared when the cartridge is restarted,
closed, or returned to the library. It is separate from persistent WASM-4 disk
storage and does not survive a MIDlet restart.

On the tested Nokia E71, short sound effects work, but continuous music
stutters. Audio timing and fidelity remain device-dependent; see
[Compatibility](docs/compatibility.md) and
[Audio architecture and diagnostics](docs/audio.md).

## Loading cartridges

The launcher includes the thirteen cartridges listed above, in that order.

Additional `.wasm` files can be installed from HTTP(S), entered as a `file://`
URL, or selected with the JSR-75 browser on supported devices. Installed
cartridges are copied into RMS and can be relaunched without their original
file or network source.

Cartridges are third-party works. Their authors, upstream sources, hashes, and
licenses are listed in [Third-party notices](THIRD_PARTY_NOTICES.md).

## Verification

```sh
just test
just verify
just run
```

The stable commands are listed by `just --list`. Specialized KEmulator,
phoneME, and profiling commands are described in
[Development](docs/development.md). Generated logs, screenshots, and benchmark
receipts are written under `build/reports/`.

## Documentation

- [Compatibility and limitations](docs/compatibility.md)
- [Installing on a phone](docs/installation.md)
- [1.0.4 release notes](CHANGELOG.md)
- [Development and testing](docs/development.md)
- [Performance methodology](docs/performance.md)
- [Release checklist](docs/releasing.md)
- [OpenSpec changes](openspec/changes/)

## License

W4ME Station source code is available under the [MIT License](LICENSE).
Bundled cartridges retain their own licenses and are not covered by the MIT
License.

The optional local phoneME rig is GPL-2.0-only and is not distributed with the
project. Its modified corresponding source is not public yet, so its binaries
must remain local and ignored. See [Performance](docs/performance.md).
