# Changelog

## Unreleased

### Reliability

- checksums complete persisted W4IR function metadata and validates every
  stored count before it can size an allocation;
- rejects invalid cached numeric intrinsics and rebuilds the whole cartridge
  cache through the existing damaged-cache path;
- keeps fused `f32.const` cells in the same zero-extended representation as
  ordinary decoded `f32` values.

### Startup performance

- decodes function bodies through bounded local instruction/control arrays,
  removing parse-loop `ObjectList` calls while preserving the existing limits.

### Save states

- adds one temporary Save State/Load State slot to the native in-game menu;
- restores linear memory, globals, table/passive-data state, logical disk, and
  APU channel progress only at a frame boundary;
- replaces repeated saves atomically and clears the slot on restart, library
  exit, cartridge failure, or MIDlet shutdown.

### Audio

- removes click-capable WAV boundaries with a one-millisecond ramp inside the
  requested tone duration; Watris and Nyan Cat traces now start and end at
  unsigned 8-bit PCM silence;
- renames Automatic/Compatible to the actual WAV synthesis, MIDI synthesis,
  and Simple tones technologies, migrates the old RMS values, and reports the
  active fallback and reason;
- adds opt-in MMAPI lifecycle timings for PCM synthesis, Player creation,
  realization, prefetch, start, stop, and close;
- makes Sound Off a hard host-import fast path that skips APU bookkeeping,
  synthesis, Player work, and end-of-frame audio ticks;
- specializes constant mono pulse synthesis by hoisting the phase step and
  precomputing the two output levels while preserving byte-exact PCM output;
- replaces Rubido in the bundled catalog with Jake Ledoux's unmodified Nyan Cat
  cartridge as a continuous-music stress case; Rubido remains a regression and
  phoneME fixture;
- documents the reported Automatic-mode clicks on J2ME Loader.

## 1.0.4 — 2026-07-29

This release accelerates universal WASM-4 drawing paths and 240-pixel
presentation without changing the cartridge format, controls, storage, or
installation requirements.

### Runtime graphics

- converts aligned, opaque, untransformed 2bpp sprite bytes directly into
  packed framebuffer bytes;
- writes the common rotated 1bpp shape directly down framebuffer columns;
- handles vertical spans and axis-aligned lines without a per-pixel Java
  method call;
- unpacks the fixed 160-to-240 nearest-neighbour scale in six-pixel groups
  instead of performing generic map and packed-address work per destination
  pixel.

All fast paths are selected from standard WASM-4 drawing parameters. They do
not identify or replace individual cartridges.

### Performance

The same corrected route harness was applied to `v1.0.3@8f39e93` and the 1.0.4
runtime. Twelve balanced native i686 phoneME pairs per workload measured median
headless frame-time reductions of 48.001% in Waternet, 8.785% in Untangle, and
3.996% on the Duck Maze level-one route. Rubido remained effectively neutral
at +0.303%.

Game of Life was treated as a no-gain control. Its first combined run measured
-0.691%, while an independent repeat measured -0.185% and the drawing-only
combination measured -0.231%. These sub-percent results are not presented as
either an acceleration or a confirmed regression.

Separately, twelve balanced native phoneME component pairs measured a 61.870%
reduction in 160-to-240 framebuffer conversion time with identical ARGB output.
This component result excludes `drawRGB`, `flushGraphics`, scheduling, and
physical display latency.

The cartridge route measurements include WASM execution and synchronous host
drawing imports, but exclude MIDP presentation and audio latency. They are not
physical-device FPS figures.

### Verification

- keeps exact instruction counts, oracle checkpoints, memory, and final
  framebuffer hashes across the real-cartridge corpus;
- passes W4Bench, drawing geometry and overlap differentials, audio and storage
  smokes, Java 1.3 compilation, CLDC 1.1 API checks, and preverification;
- passes all 20 KEmulator functional scenarios, including the exact Waternet,
  Untangle, Duck Maze, W4IR cache, JSR-75, audio, and recovery paths;
- adds about 1 KB of permanent lookup storage and 1.2 KB to the full release
  JAR.

The canonical container now includes `diffutils`. Its counterless KEmulator
probes no longer require production-disabled dispatch or compact counters.

## 1.0.3 — 2026-07-29

This release improves the universal interpreter, corrects unsigned `i64`
conversion rounding, and strengthens performance verification without changing
the cartridge format, controls, storage, or installation requirements.

### Interpreter

- keeps the instruction counter local on the main dispatch path;
- reads auxiliary W4IR operands only for instructions that need them;
- correctly rounds `f32.convert_i64_u` and `f64.convert_i64_u` for
  rounding-sensitive unsigned values above `2^63`.

### Performance

The corrected route harness was applied identically to `v1.0.2@33a6632` and the
1.0.3 interpreter. Twelve balanced native i686 phoneME pairs per workload
measured median headless frame-time reductions of 5.910% in Waternet, 4.961% in
Rubido, 6.275% in Untangle, 6.418% in Game of Life, and 3.728% on the Duck Maze
level-one route. All 60 pairs favored 1.0.3 and matched exact instruction
counts, oracle checkpoints, and final framebuffer hashes.

These measurements use a CLDC 1.1 C interpreter without a JIT. They exclude
MIDP presentation and audio latency and are not physical-device FPS figures.

### Verification

- separates native phoneME timing from exact replay validation and rejects
  unverified idle routes by default;
- adds the frozen W4Bench v1 suite with seven deterministic workload families;
- dynamically reaches all 190 supported source-WebAssembly opcodes on the host,
  verifies the single expected `unreachable` trap, and rejects internal W4IR
  opcode leakage;
- checks workload results and unsigned-`i64` conversion sentinels against an
  independent Python oracle and rejects deliberately corrupted result records.

W4Bench timings are diagnostic interpreter measurements, not a replacement for
paired real-cartridge A/B evidence.

## 1.0.2 — 2026-07-28

This release further reduces universal interpreter overhead without changing
the cartridge format, controls, storage, or installation requirements.

### Performance

- avoids repeated compact-tier eligibility work in the outer dispatch loop;
- folds compact-tier activation into the existing instruction-budget boundary;
- packs control-frame metadata into one array instead of three parallel arrays;
- removes an extra Java method call from common 32-bit stack pushes and pops.

The Duck Maze host benchmark now enters gameplay, and the phoneME benchmark
replays and validates the complete 155-frame level-one route instead of timing
an idle screen.

Twelve balanced native i686 phoneME pairs per workload measured median headless
frame-time reductions of 5.464% in Waternet, 6.684% in Rubido, 4.931% in
Untangle, 7.005% in Game of Life, 3.214% in Plasma Cube, and 3.681% on the Duck
Maze level-one route compared with `main@7a77b08`. Exact instruction counts and
oracle checkpoints remained unchanged.

These measurements use a CLDC 1.1 C interpreter without a JIT. They exclude
MIDP presentation and audio latency and are not physical-device FPS figures.

## 1.0.1 — 2026-07-27

This release improves the universal Java ME runtime without changing the
cartridge format, controls, storage, or installation requirements.

### Performance

- packed horizontal framebuffer spans replace per-pixel Java calls on common
  WASM-4 drawing paths;
- generic integer comparisons and common control-frame operations execute with
  fewer Java helper calls;
- distributable JARs compile diagnostic dispatch counters and opcode profiling
  out of production bytecode;
- sixteen balanced native i686 phoneME pairs per workload measured headless
  frame-time reductions of 35.753% in Waternet, 17.057% in Rubido, 65.151% in
  Untangle, and 10.826% in Game of Life compared with `main@8e85065`; all 64
  pairs favored 1.0.1 and matched exact oracle checkpoints.

These measurements use a CLDC 1.1 C interpreter without a JIT. They exclude
MIDP presentation and audio latency and are not physical-device FPS figures.

### Runtime integrity

- removed the cartridge-fingerprinted Plasma Cube Java replacement so every
  bundled and external cartridge now follows the universal interpreter path;
- expanded exact framebuffer, interpreter, production-bytecode, and
  counterless-build verification;
- reduced the full release JAR from about 275 KB to 270 KB.

Plasma Cube may run slower than in 1.0.0 because it now exercises the actual
universal interpreter instead of a cartridge-specific replacement. It remains
a technical stress workload rather than a representative game.

## 1.0.0 — 2026-07-26

The first public W4ME Station release targets CLDC 1.1 / MIDP 2.0 phones with
Java 1.3-compatible bytecode.

### Included

- WebAssembly interpreter and persistent W4IR cache for unmodified WASM-4
  cartridges;
- native LCDUI library, file picker, sound settings, and in-game system menu;
- thirteen bundled cartridges ordered for keypad usability and handset frame
  cost;
- HTTP(S), URL, RMS, and optional JSR-75 cartridge installation;
- latched gamepad input for slow frames, pointer input, and an on-screen touch
  controller;
- graphics, audio, disk, tracing, and two-gamepad WASM-4 host APIs;
- full and base JAR variants, both below 300 KB; the base variant contains no
  JSR-75 classes or permission declaration;
- deterministic state, framebuffer, audio, storage, KEmulator, and optional
  native no-JIT phoneME verification.

### Known limitations

- Performance depends heavily on the handset VM. Plasma Cube is a technical
  stress workload, not a representative first cartridge.
- Glowfish Chess is practical in its two-player mode; its CPU opponent can
  exceed the per-frame instruction budget on current hardware.
- MMAPI implementations vary. On the tested Nokia E71, short sound effects
  work, but continuous music stutters. `Compatible` audio mode changes the
  backend path but does not guarantee gapless playback.
- Save-state slots, Bluetooth play, and the workshop catalog are not part of
  1.0.0.
- Physical-device coverage is limited and will be documented per handset as
  reports arrive.

Application source is MIT-licensed. Bundled cartridges and the console font
retain the separate licenses recorded in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
