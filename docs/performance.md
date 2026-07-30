# Performance and runtime verification

W4ME Station uses different environments for different questions. No single
emulator represents every physical Java ME phone.

## Environment roles

| Environment                       | Authoritative use                                                            |
| --------------------------------- | ---------------------------------------------------------------------------- |
| Native i686 phoneME C interpreter | interpreter performance A/B                                                  |
| AArch64 phoneME under QEMU        | cross-ISA deterministic correctness only                                     |
| KEmulator                         | interactive MIDP, Canvas, touch, RMS, JSR-75, installation, and audio checks |
| Host JVM                          | fast deterministic state, parser, and framebuffer regression tests           |
| Physical devices                  | final usability, audio latency, heap, controls, and frame rate               |

KEmulator executes MIDlet bytecode on a desktop JVM. Limiting its CPU share is
a useful pressure test, but it does not turn HotSpot into a feature-phone VM.
QEMU TCG timing measures binary translation and is never performance evidence.

## Local phoneME rig

The optional reference rig is expected at `.local/phoneme/` or at
`PHONEME_HOME`:

```text
.local/phoneme/
├── classes.zip
├── cldc_vm_r
├── cldc_vm_r-arm64
└── preverify
```

The binaries were built from
[`magicus/phoneME`](https://github.com/magicus/phoneME) plus modifications
maintained in a private source tree. That complete modified source is not
public yet. Because phoneME is GPL-2.0-only, the binaries are ignored and must
not be added to this repository or to release artifacts until their exact
corresponding source and build files can be published.

## Commands

```sh
just bench
just bench --reps 5
just bench rubido --mode optimized --reps 8
just bench game-of-life-zig-edition --reps 8
just bench-pcm waternet --cycles 20 --reps 5
just bench-argb --side 160 --band-height 16 --frames 100 --reps 5
just bench-w4bench
tools/phoneme/run.sh verify
tools/phoneme/run.sh verify-arm64
```

The benchmark compiles the interpreter and runtime with Java 1.3 settings
against phoneME's CLDC `classes.zip`, preverifies the result, stages the
cartridges and exact routes, and starts a fresh VM for every sample.
Each verified sample first runs the timed route without oracle work, then
replays it outside the timed interval to check every checkpoint, collect
deterministic counters, and confirm that its final framebuffer matches the
timed execution.
The default artifact uses the same counterless compile-time configuration as
the release JAR. The default corpus includes Waternet, Rubido, Untangle, Duck
Maze, and the integer/control-flow-heavy first generation of Game of Life.
Each route ends on its final recorded oracle checkpoint; Game of Life
deliberately uses one frame because that frame alone executes about 12.8
million WASM instructions.

A cartridge without non-empty `input.csv` and `oracle.csv` is rejected.
`--unverified-idle` is an explicit diagnostic escape hatch and its output is
not performance evidence. An explicit `--extra-frames` extends the run beyond
the recorded route and is therefore rejected unless `--unverified-idle` is
also present.

Generated artifacts and receipts are written to `build/reports/phoneme/`.

## Synthetic cartridge benchmark

`just bench-w4bench` runs seven fixed interpreter workloads on native i686
phoneME. It also performs an untimed 190/190 source-opcode reachability sweep,
checks exact results with an independent oracle, and rejects timer samples
below the calibrated resolution floor. The canonical receipt is
`build/reports/w4bench/v1/receipt.txt`.

W4Bench is a diagnostic breakdown, not a replacement for exact game routes or
paired A/B measurements. Its compact contract and maintenance notes live in
[`bench/w4bench/README.md`](../bench/w4bench/README.md).

## Acceptance rules

- The native i686 `cldc_vm_r` is the only timing judge for interpreter changes.
- Compare matching alternating pairs, not independent candidate medians.
- Every timed route must consume at least one exact framebuffer, palette, and
  input checkpoint. Paired candidates must also agree on frame count, checkpoint
  count, logical instruction count, and final framebuffer.
- Full memory, globals, table, disk, tone, and trap equivalence belongs to the
  deterministic differential and replay suites run by `just test`; the timed
  phoneME route alone does not prove those states.
- Small changes must clear timer resolution and run-to-run noise.
- AArch64 counters and checkpoints must match i686 exactly, but AArch64 wall
  time is ignored.
- Physical-device behavior can override an emulator-only conclusion.

The current goal is correctness and useful physical-device performance, not a
desktop benchmark score. Historical experiment receipts are intentionally not
kept in the public source tree.

## Standing optimization target: Glowfish Chess `VS CPU`

This is the heaviest real workload in the release catalog and the reference case
for how far the interpreter still is from running a cartridge that computes a
whole move inside one frame.

The cartridge runs a fixed depth-2 alpha-beta search plus an unbounded
quiescence search inside a single `update()` call, so its cost is set by the
position, not by the frame rate. Replaying a scripted game through the counting
interpreter gives, per engine turn:

| Engine turn | Logical instructions |
| ----------- | -------------------- |
| 1           | 163,079,705          |
| 2           | 36,288,242           |
| 3           | 199,347,994          |
| 4           | 554,929,307          |
| 5           | 165,811,127          |
| 6           | 258,506,628          |
| 7           | 199,097,656          |
| 8           | 128,237,895          |
| 9           | 129,881,756          |
| 10          | 124,653,796          |

Six of ten turns exceed the 150,000,000 per-frame budget and abort the
cartridge; the first search after leaving the opening book already does. The
turns that survive sit at 83 to 87 percent of the budget, which is around 72
seconds on the desktop `cldc_vm_r` and minutes on a handset, so they are not
playable either. A normal frame of the same cartridge costs 14,828.

`VS Player` never reaches the engine and stays at roughly 15,000 instructions
per frame for a whole game, so hot-seat play is unaffected.

Treat this as a ceiling marker rather than a regression: making `VS CPU` usable
needs an order-of-magnitude interpreter gain, not a few percent. It is a useful
target because the workload is pure integer, allocation-free in the hot path
apart from a full board copy per node, and completely deterministic.

## KEmulator

Open an interactive session with:

```sh
just run
tools/kemu/run.sh session cmd state
tools/kemu/run.sh session stop
```

Specialized integration and diagnostic scenarios are available through:

```sh
tools/kemu/run.sh verify <scenario> dist/w4me-station.jar
tools/kemu/run.sh bench <scenario> [args...]
tools/kemu/run.sh phone dist/w4me-station.jar
```

KEmulator output is written below `build/reports/kemu/`.

### What the diagnostic benchmarks prove

| Command family                            | Valid conclusion                                                           | Invalid conclusion                           |
| ----------------------------------------- | -------------------------------------------------------------------------- | -------------------------------------------- |
| `tools/bench/run.sh corpus`               | full-state equivalence and dynamic opcode/tier coverage                    | handset speed                                |
| `tools/bench/run.sh untangle` / `fusions` | deterministic route behavior, dispatch counts, and profiling               | no-JIT speedup                               |
| KEmulator `generic-corpus`                | the recorded route reaches the exact final framebuffer                     | phone performance                            |
| KEmulator `generic-w4ir`                  | the requested fusion, compact, trace, and intrinsic tiers actually execute | phone performance                            |
| KEmulator `untangle`                      | the long route stays exact and reports phase counters                      | phone performance                            |
| KEmulator `w4ir`                          | RMS build, cached load, paging, and promotion execute                      | steady-state phone speed                     |
| KEmulator `phone` / `plasma`              | constrained MIDP presentation and pacing smoke                             | interpreter A/B evidence                     |
| KEmulator `*-matrix`                      | repeated aggregation of the corresponding child scenario                   | stronger correctness than the child scenario |

Only `tools/phoneme/run.sh bench` produces interpreter timing evidence. The
PCM and ARGB commands isolate their named runtime components; they do not
measure a complete rendered or audible frame.
