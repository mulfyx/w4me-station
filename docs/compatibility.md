# Compatibility

W4ME Station runs unmodified WASM-4 cartridges through a WebAssembly
interpreter on CLDC 1.1 / MIDP 2.0. Compatibility is verified against exact
framebuffer, input, disk, and audio oracles where available.

## Compatibility matrix

| Area | Status | Scope and differences |
| --- | --- | --- |
| Java ME platform | Required | CLDC 1.1, MIDP 2.0, and Java 1.3-compatible bytecode |
| Release variants | Supported | The full JAR can use optional JSR-75 FileConnection; the base JAR contains no JSR-75 classes or permissions |
| Graphics | Supported | 160×160 2bpp framebuffer, palettes, drawing primitives, text, `blit`, `blitSub`, and preserve-framebuffer behavior |
| Input | Partial | Phone keys, pointer input, and a touch controller are mapped to two gamepads; WASM-4 exposes four gamepad registers |
| Audio | Device-dependent | The `tone` API, four logical channels, sampled output, streamed MIDI compatibility output, and `playTone` fallback are implemented, but timing and waveform fidelity depend on MMAPI |
| Disk | Supported | The 1 KiB WASM-4 disk is persisted per cartridge through checksummed RMS generations |
| Cartridge loading | Supported | Bundled resources, HTTP(S), RMS, URLs, and optional JSR-75 files; cartridge files are limited to 64 KiB |
| Linear memory | Restricted | One fixed 64 KiB memory; memory growth and multiple memories are not supported |
| Frame timing | Device-dependent | The interpreter has no JIT and cannot guarantee the reference runtime's 60 Hz update rate on every cartridge |
| WebAssembly extensions | Partial | Numeric conversions, bulk memory, passive data, `i64`, `f32`, `f64`, and tables are supported; threads, SIMD, and broader reference types are not |

## Tested environments

| Environment | Role | Confirmed behavior | Boundary |
| --- | --- | --- | --- |
| Nokia E71 | Physical-device usability | Launcher, keypad input, multiple bundled cartridges, and short sound effects | Continuous music stutters in the current release; a complete cartridge-by-cartridge physical-device pass has not been recorded |
| KEmulator | MIDP integration | LCDUI, Canvas, touch, RMS, JSR-75, installation, settings, and audio lifecycle scenarios | Desktop timing and audio output are not evidence of physical-phone performance or fidelity |
| Host JVM | Deterministic correctness | Exact framebuffer, input, disk, memory, globals, and tone-event replay where an oracle exists | Not a Java ME performance measurement |
| Native i686 phoneME | No-JIT interpreter verification | Exact route parity and paired timing for the maintained benchmark corpus when the optional local rig is available | It does not exercise a handset display, keypad, or MMAPI implementation |

## Runtime

The runtime currently provides:

- fixed 64 KiB linear memory and the WASM-4 memory-mapped registers;
- structured control flow, multi-value block signatures, tables, indirect
  calls, `i32`, `i64`, `f32`, and `f64`;
- numeric conversions, saturating conversions, bulk memory operations, and
  passive data segments;
- `blit`, `blitSub`, primitives, text, palette changes, mouse input, and two
  gamepads;
- `tone`, a four-channel logical APU, sampled MMAPI output, one-player
  streamed MIDI compatibility output, `playTone` fallback, and silent
  fallback;
- the 1 KiB cartridge disk backed by checksummed RMS generations;
- validation before execution and a fixed-width W4IR cache stored in RMS;
- isolated loader failures and runtime traps that return to the library.

The implementation intentionally targets Java 1.3 language and classfile
compatibility. Production code must not depend on desktop-only Java APIs.

## Bundled cartridges

Both release JARs contain the same thirteen cartridges, packaged under
`cartridges/` inside the JAR. The library presents them in this order:

| # | Cartridge | Primary coverage |
| --- | --- | --- |
| 1 | Sokoban | turn-based puzzle; the frame changes only on a button press |
| 2 | Wasm Wars | turn-based strategy and `SYSTEM_PRESERVE_FRAMEBUFFER` |
| 3 | Annoying Robots | board game against a CPU opponent |
| 4 | Waternet | input, palette, audio, and disk lifecycle |
| 5 | Dragon Poker Draw | card game, static frame between actions |
| 6 | Tic Tac Toe | two players on one gamepad |
| 7 | Watris | real-time falling-block game |
| 8 | Glowfish Chess | board game, two players on one gamepad, static frame |
| 9 | Duck Maze | gamepad input and multi-frame state |
| 10 | Untangle | pointer dragging, rotated/flipped blits, disk, `f64`, and tables |
| 11 | Nyan Cat | continuous multi-channel music and animation |
| 12 | Sound Demo | basic tone playback |
| 13 | Plasma Cube | floating-point computation and sustained rendering |

The order is a user-visible contract pinned by the KEmulator launcher scenario.
The first entries are the cartridges whose per-frame cost is low enough that the
handset limitation is not visible; pointer-driven cartridges sit below
keypad-driven ones, and the service and technical demos come last. Plasma Cube
is by far the most expensive cartridge of the set and stays at the end.

Mandelbrot, Rubido, Sound Test, Tankle, and Game of Life: Zig Edition remain in
`cartridges/` as regression and benchmark fixtures but are not packaged in the
release JARs.

Glowfish Chess sits below the cartridges above it because its `VS CPU` mode does
not work within the per-frame budget described in the next section, and is not
expected to. The cartridge runs a complete alpha-beta search with an unbounded
quiescence search inside a single `update()` call. Measured over a scripted game,
six of ten engine turns exceeded the budget and aborted the cartridge, the first
search after leaving the opening book among them; the turns that completed cost
83 to 87 percent of the budget, which is minutes of frozen screen on a handset.
Treat `VS CPU` as unavailable.

`VS Player` never reaches the engine — the search is behind a
`mode == VsEngine` check — and stays at roughly 15,000 instructions per frame
for a whole game, so hot-seat chess for two players on one handset works
normally. That is why the cartridge is still bundled.

The per-turn figures are recorded in
[performance documentation](performance.md) as a standing interpreter
optimization target.

## Cartridge verification

Release cartridges remain byte-for-byte copies of their published upstream
files. Their sources, licenses, and SHA-256 hashes are listed in
[`THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md).

The automated suite currently provides these cartridge-level checks:

| Cartridge | Automated evidence | Physical-device evidence |
| --- | --- | --- |
| Duck Maze | Scripted level-one framebuffer oracle | Observed playable on Nokia E71 |
| Waternet | 94-frame replay with exact framebuffer, input, tone-event, and disk checks | Gameplay and short effects work on Nokia E71; continuous music stutters |
| Rubido | 70-frame replay with exact framebuffer, input, tone-event, and disk checks | Not recorded |
| Untangle | 401-frame replay with exact framebuffer, input, tone-event, and disk checks | Not recorded |
| Plasma Cube | 60-frame exact differential run | Launch observed on Nokia E71, but it is a stress workload rather than a performance target |
| Game of Life: Zig Edition | One full exact differential frame | Test-only workload; not suitable for interactive use on the tested handset |

Known negative cases are retained rather than hidden: Glowfish Chess `VS CPU`
can exceed the per-frame instruction budget, while its `VS Player` mode avoids
the engine and remains usable. Mandelbrot, Tankle, and Game of Life: Zig Edition
remain test and benchmark fixtures instead of release-library entries because
their frame cost is unsuitable for the current physical-device target.

## Per-frame instruction budget

Every `update()` call is capped at 150,000,000 executed WebAssembly
instructions. A cartridge that exceeds it is stopped with a trap and the library
returns, rather than leaving the handset frozen with no way out.

This cap is a W4ME decision, not part of WASM-4; a browser runtime has no such
limit and would merely stutter. It matters because WASM-4 assumes `update()`
returns promptly at 60 Hz, so a cartridge is free to compute a whole engine move
or a whole generated level inside one frame. That is inexpensive on a native or
JIT host and unreachable on an interpreter: 150,000,000 instructions is on the
order of minutes of wall time on a handset, so a cartridge that needs them would
be unusable even if it were allowed to finish.

Cartridges that do bounded per-frame work are unaffected; the release catalog
sits between roughly 500 and 40,000 instructions per frame in normal play.

The files are unchanged upstream works and have their own licenses. See
[`THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md).

## Loading external cartridges

Additional cartridges can be installed from HTTP(S), entered as a `file://`
URL, or selected through JSR-75 FileConnection when the device provides that
optional API. Installed cartridges are validated and copied into RMS, so the
original file or network connection is not needed for later launches.

The base JAR omits the JSR-75 adapter and remains usable on MIDP 2.0 devices
without FileConnection.

## Controls

| Input | Player 1 | Player 2 |
| --- | --- | --- |
| Movement | Arrow or directional phone keys | `E`, `S`, `D`, `F` |
| Button 1 | `X`, Fire, or `5` | `Tab` |
| Button 2 | `Z` or `0` | `Q` |

Touchscreen devices display an on-screen gamepad outside the 160×160
framebuffer whenever the screen provides enough space.

## Known limitations

- Performance depends heavily on the handset VM. Computationally expensive
  cartridges may not reach interactive frame rates on physical devices.
- Sampled MMAPI behavior and latency vary between phone implementations. On the
  tested Nokia E71, short sound effects are audible, but continuous music
  stutters and is not timing-compatible with the reference runtime.
- Nyan Cat is bundled as a sustained-music stress cartridge. WAV synthesis
  produced audible clicks in user testing on J2ME Loader. The generated PCM
  had non-silent starts and ends; a one-millisecond in-duration edge ramp now
  returns finite WAVs to silence. Audible Player lifecycle behavior still
  requires J2ME Loader and physical-device validation.
- WAV synthesis uses sampled Players only when MMAPI reports both WAV and
  mixing support, then falls back through a data-backed `audio/midi`
  Player, `Manager.playTone`, and silence as each tier proves unavailable.
- `MIDI synthesis` in `Sound settings` bypasses sampled Players and renders
  active WASM-4 channels into one Standard MIDI File Player. This avoids both
  concurrent-Player mixing and optional `device://midi` implementations that
  silently discard interactive events. MIDI preserves polyphony and timing but
  only approximates WASM-4 pulse, triangle, and noise waveforms.
- `Simple tones` exposes the monophonic `Manager.playTone` fallback directly.
- `Sound settings` reports the preferred and active technology plus the
  fallback reason. It also provides a global hard mute and, when supported,
  master volume from 0 through 100. Confirmed values are restored from RMS
  before a cartridge can submit its first tone. A profile change made during a
  game is used after reopening the cartridge.
- WebAssembly threads, SIMD, reference types beyond the supported table model,
  multiple memories, and memory growth are not supported.
- The in-game menu provides one temporary Save State/Load State for the active
  cartridge. It captures VM memory, globals, table/passive-data state, logical
  disk, and APU progress between frames. The state has no slot manager and is
  cleared on Restart Cart, Library, cartridge failure, or MIDlet shutdown.
- Bluetooth play, workshop browsing, and the remaining menu work remain tracked
  in OpenSpec and are not part of the current release.

Exact regression fixtures live under `testdata/oracles/`. Generated logs,
screenshots, and benchmark receipts are local build output under
`build/reports/`.
