## ADDED Requirements

### Requirement: Whole-corpus execution profiling

The project SHALL provide a deterministic profiler that records the complete decoded opcode space, dynamic opcode pairs and triples, per-function entries, logical instructions, outer dispatches, compact calls and instructions, compact block-length distribution, and actual compact-region break or rejection reasons.

#### Scenario: Profile the exact generic corpus

- **WHEN** the profiler runs the defined Plasma, Waternet, Rubido, Untangle, Duck Maze, and Game of Life Zig Edition workloads
- **THEN** it emits all required metrics with build identity, route identity, and decoded labels for standard, prefixed, fused, and internal opcodes

#### Scenario: Report a compact break reason

- **WHEN** the real compact-region builder rejects or terminates a candidate region
- **THEN** the profiler attributes the event to that actual interpreter decision without consulting a separately maintained eligibility mirror

### Requirement: Frame-complete differential verification

Every optimization candidate SHALL be compared with the reference interpreter from identical initial state after every executed frame.

#### Scenario: Candidate matches the reference

- **WHEN** a corpus replay frame completes in both interpreters
- **THEN** complete 64 KiB linear memory, globals, table, framebuffer, palette, input state, tone events, and logical disk are identical

#### Scenario: Hidden state diverges

- **WHEN** any compared state differs even though the framebuffer is identical
- **THEN** the differential run fails and identifies the first frame and state category that diverged

### Requirement: Isolated and reproducible A/B variants

The benchmark system SHALL isolate one optimization variable per candidate artifact and SHALL bind every result to the exact build, W4IR format, workload route, and execution profile.

#### Scenario: Measure initial compact variants

- **WHEN** initial compact-tier experiments are produced
- **THEN** separate same-build artifacts exist for current behavior and seven-opcode-only behavior, while rejected activation experiments remain separate historical evidence

#### Scenario: Reject a confounded comparison

- **WHEN** two timing results differ in compact opcode coverage and any other runtime or activation policy
- **THEN** the comparison is not accepted as evidence for the compact opcode batch

### Requirement: Paired performance statistics

The benchmark system SHALL derive an A/B verdict from matching baseline and candidate samples, not from the difference between independent per-candidate medians.

#### Scenario: Summarize a paired A/B

- **WHEN** matching baseline and candidate samples complete
- **THEN** the harness reports the median per-pair percentage effect, median per-pair time delta, wins, losses, ties, timer resolution, run-order balance, and evidence quality

#### Scenario: Independent medians disagree with paired effects

- **WHEN** subtracting the two independent medians has a different sign from the median paired effect
- **THEN** the paired effect determines the verdict and the independent medians remain descriptive only

#### Scenario: Measure a small effect

- **WHEN** an A/B result is used to accept a small performance change
- **THEN** at least eight pairs run in balanced alternating order and the median time delta exceeds the effective timer resolution per frame

#### Scenario: Record a dirty or under-resolved run

- **WHEN** the source tree is dirty, fewer than eight pairs are available for a small effect, or the median delta is below timer resolution
- **THEN** the receipt is retained as exploratory evidence and cannot accept a production performance candidate

### Requirement: Seven-opcode integer compact batch

The compact executor SHALL support `i32.load`, `i32.store`, `i32.ne`, `i32.lt_s`, `i32.le_s`, `i32.le_u`, and `i32.ge_s` as one isolated first coverage batch.

#### Scenario: Execute an added integer opcode in a compact region

- **WHEN** a compact region contains one of the seven added opcodes
- **THEN** its result, stack effect, memory behavior, program counter, trap order, and instruction accounting match the main executor

#### Scenario: Memory access traps in compact execution

- **WHEN** an added compact load or store receives an invalid effective address
- **THEN** it traps at the same logical instruction and before the same later effects as the unfused main-executor operation

### Requirement: Independently evaluated runtime lookup optimizations

Stable runtime-reference caching, numeric host-import dispatch, canonical structural type IDs, and declared-local-only zeroing SHALL each be implemented and evaluated as an independent candidate.

#### Scenario: Dispatch a resolved host import

- **WHEN** an imported WASM-4 host function is invoked after module loading
- **THEN** the runtime dispatches by its validated canonical numeric ID without a per-call string comparison chain

#### Scenario: Compare duplicate equivalent function types

- **WHEN** `call_indirect` compares two structurally identical signatures declared at different type-section indices
- **THEN** their canonical structural type IDs compare equal

#### Scenario: Compare different function types

- **WHEN** `call_indirect` compares signatures with a different parameter or result vector
- **THEN** the runtime traps exactly as the reference structural comparison does

#### Scenario: Initialize function locals

- **WHEN** a function frame is entered
- **THEN** argument slots contain the supplied arguments and only declared non-argument local slots require zero initialization

### Requirement: Build-time diagnostic isolation

The project SHALL provide a separately compiled counterless timing artifact in which dispatch and compact diagnostic counter updates are removed at compile time while logical instruction-budget accounting remains unchanged.

#### Scenario: Inspect the counterless executor

- **WHEN** target-47 bytecode for the timing artifact is inspected
- **THEN** the main and compact execution paths contain no runtime flag branch and no writes to `dispatchesExecuted`, `compactBlockCalls`, or `compactInstructionsExecuted`

#### Scenario: Exhaust the instruction budget

- **WHEN** the counterless artifact reaches its configured logical instruction limit
- **THEN** it traps at the same logical instruction as the diagnostic artifact

#### Scenario: Run the exact corpus

- **WHEN** the counterless artifact executes the seven full-state differential workloads and the three phoneME routes
- **THEN** all observable runtime state and route checkpoints match the reference while disabled diagnostic fields are reported as unavailable or zero

### Requirement: Stable compact activation policy

The current invocation-local compact threshold SHALL remain unchanged in this change because an isolated same-head forced-activation overlay was slower on all three phoneME routes and no clean positive evidence justifies forced or sticky activation. The overlay magnitude SHALL remain classified as exploratory rather than a reusable speed claim.

#### Scenario: Counter coverage improves without reference-VM speed

- **WHEN** an activation candidate increases compact coverage or reduces outer dispatches but is neutral or slower on phoneME
- **THEN** the candidate is rejected and is not combined with opcode coverage or another optimization

#### Scenario: Reconsider compact activation

- **WHEN** a future activation policy has new isolated positive phoneME evidence
- **THEN** it is proposed and verified as a separate change rather than enabled by this change

### Requirement: Resident W4IR page-check specialization

The interpreter SHALL distinguish resident function code from paged W4IR once
per invocation and SHALL avoid page-range arithmetic on resident outer
dispatches without changing paged execution.

#### Scenario: Dispatch resident W4IR

- **WHEN** a function body has a resident `body.code` array
- **THEN** every outer dispatch reads from that fixed array without testing whether the current PC crosses a code-page boundary

#### Scenario: Dispatch paged W4IR

- **WHEN** a function body does not have a resident `body.code` array
- **THEN** the interpreter retains the existing range check, page load, and page-base update behavior

#### Scenario: Compare resident specialization with its baseline

- **WHEN** the resident fast path is evaluated
- **THEN** the baseline and candidate differ only in the compile-time resident specialization and retain identical diagnostic accounting

### Requirement: Dense internal opcode dispatch

The decoder SHALL map all standard, prefixed, fused, and internal W4IR operations into a dense internal ID range used by the main interpreter dispatch while retaining sufficient original identity for diagnostics and profiling.

#### Scenario: Inspect target dispatch bytecode

- **WHEN** the Java 1.3 target classes are compiled
- **THEN** bytecode inspection confirms that the main opcode switch is a `tableswitch`

#### Scenario: Report an opcode after remapping

- **WHEN** execution traps or profiling reports a densely remapped instruction
- **THEN** the output identifies the corresponding WebAssembly or W4IR operation unambiguously

### Requirement: Atomic W4IR cache migration

Dense opcode remapping SHALL increment the RMS W4IR format version, reject incompatible entries atomically, and rebuild them without partially decoding an old stream.

#### Scenario: Open an older cached stream

- **WHEN** the runtime encounters an RMS W4IR entry from the previous format version
- **THEN** it discards that entry and rebuilds a current-format stream from the cartridge

#### Scenario: Compare resident and reopened streams

- **WHEN** the same module executes from a newly decoded resident stream and from a current-format RMS-reopened stream
- **THEN** every decoded instruction and all frame-complete differential state are identical

### Requirement: Evidence-selected compare and branch fusion

Generic compare-plus-`br_if` fusion SHALL be selected from whole-corpus dynamic pair and triple evidence, SHALL not depend on cartridge identity, and SHALL be retained only if workload-specific phoneME measurements show no corpus regression.

#### Scenario: Execute a fused compare and conditional branch

- **WHEN** a selected comparison followed by `br_if` is fused and executed
- **THEN** the comparison result consumption, taken or untaken target, stack transfer, trap behavior, and instruction accounting match the two unfused instructions

#### Scenario: Fusion lacks corpus evidence

- **WHEN** a candidate combination does not meet the documented whole-corpus selection threshold
- **THEN** it is not added solely to optimize one named cartridge

#### Scenario: Fusion reduces dispatches but regresses a corpus route

- **WHEN** a selected fusion passes exactness gates and reduces outer dispatches but a clean balanced phoneME run shows a corpus wall-time regression
- **THEN** the fusion is removed and the counter reduction is retained only as rejected diagnostic evidence

### Requirement: Evidence-selected adjacent instruction fusion

Adjacent memory/local and local/control fusions SHALL be selected from whole-corpus dynamic evidence, SHALL be evaluated as separate batches, and SHALL preserve the execution boundary of every original logical instruction for traps and instruction budgets.

#### Scenario: Execute fused `i32.load + local.tee`

- **WHEN** a validated `i32.load` followed by `local.tee` is fused and executed
- **THEN** the effective-address calculation, load trap behavior, stack result, local write, program-counter movement, and two logical instruction charges match the unfused sequence

#### Scenario: Budget expires between fused operations

- **WHEN** the instruction budget permits the load but expires at the original `local.tee` boundary
- **THEN** the load result remains on the value stack, the local is not written, and execution traps at the same logical point as the unfused sequence

#### Scenario: Evaluate the next adjacent fusion batch

- **WHEN** `local.set + br` is selected from the profile after the load/tee batch
- **THEN** it receives an independent opcode toggle, exact-state differential, target-artifact verification, and clean phoneME corpus A/B before it can be retained

#### Scenario: Execute fused `local.set + br`

- **WHEN** a validated `local.set` followed by an unconditional `br` is fused and executed
- **THEN** the value is popped and written at the first logical instruction point, the second budget check occurs before control transfer, and the original branch depth, stack transfer, target, and function-return behavior are preserved

### Requirement: Deferred control-flow lowering

This change SHALL retain the current dynamic control-stack implementation and the current compact-region control-flow boundary. It SHALL NOT add static branch descriptors, branch-capable compact regions, or control-stack removal.

#### Scenario: Build the stable artifact

- **WHEN** this change is built and verified
- **THEN** control transfers continue to use the existing validated dynamic control-stack path and compact execution returns to the outer executor at the existing control-flow boundaries

#### Scenario: Reconsider control-flow lowering

- **WHEN** static branch descriptors, branch-capable compact regions, or control-stack removal are proposed again
- **THEN** they are specified as a separate change with independent exactness, target-artifact, and phoneME performance gates

### Requirement: Exact instruction-budget and memory-trap semantics

Optimized execution SHALL preserve the logical instruction at which budget exhaustion and invalid memory accesses trap.

#### Scenario: Budget expires inside a compact or fused sequence

- **WHEN** the execution budget is exhausted by a logical instruction represented inside a compact block or superinstruction
- **THEN** execution traps at that logical instruction exactly as the reference executor does

#### Scenario: Predecode an out-of-range unsigned memory offset

- **WHEN** a memory instruction has an unsigned offset greater than or equal to 65,536
- **THEN** any decode-time replacement executes an unconditional trap at the original instruction point and preserves earlier side effects

### Requirement: Target artifact compatibility

Every accepted phase SHALL compile against the CLDC 1.1 phoneME bootclasspath, produce a Java 1.3-compatible target artifact with classfile major version 47, valid preverified StackMaps, successful KEmulator loading, and main `execute` bytecode size no greater than 16,000 bytes.

#### Scenario: Verify a candidate JAR

- **WHEN** a candidate phase is ready for performance acceptance
- **THEN** classfile, StackMap, KEmulator load, and method-size checks all pass before timing results are considered

#### Scenario: Use an unavailable Java SE API

- **WHEN** main interpreter or runtime sources reference a `java.*` API absent from CLDC 1.1
- **THEN** compilation against the local phoneME `classes.zip` fails the candidate before performance measurement

#### Scenario: Build a distributable artifact

- **WHEN** a release JAR or archive is assembled
- **THEN** locally supplied GPL-2.0 phoneME binaries are not included

### Requirement: Workload-specific performance acceptance

An optimization SHALL be accepted only after exact compatibility gates pass and workload-specific phoneME A/B measurements show no corpus regression and a positive effect on the workload the optimization targets.

#### Scenario: Measure a candidate on the reference VM

- **WHEN** an optimization affects a corpus workload
- **THEN** balanced paired phoneME samples are collected, the median paired effect is reported, unchanged control workloads are included, and the receipt identifies the exact VM, clean source build, preverified artifact, routes, and timing method

#### Scenario: Measure generic Plasma execution

- **WHEN** Plasma is used to evaluate generic interpreter changes
- **THEN** the benchmark bypasses the specialized production Plasma fast path

#### Scenario: Counters improve without phone speed

- **WHEN** outer dispatches decrease or compact coverage increases but workload-specific phoneME time does not improve
- **THEN** the counters are retained as diagnostic evidence but the candidate is not accepted as a speed improvement

#### Scenario: KEmulator timing disagrees with phoneME

- **WHEN** a KEmulator benchmark and the phoneME reference benchmark produce different speed conclusions
- **THEN** phoneME determines interpreter-performance acceptance and KEmulator timing remains secondary JIT-regime evidence

### Requirement: Cross-ISA phoneME correctness

The native i686 and AArch64 builds of the phoneME portable-C interpreter SHALL execute the same preverified production route artifact with identical checkpoints and deterministic counters. AArch64 execution under QEMU SHALL NOT be used as performance evidence.

#### Scenario: Compare native and translated VM execution

- **WHEN** the Waternet, Rubido, and Untangle production routes run on native i686 and QEMU AArch64 phoneME
- **THEN** both runs pass every route oracle and report identical W4IR format, frame count, checkpoint count, logical instructions, dispatches, fast-path calls, compact calls and instructions, and trace calls and iterations

#### Scenario: Record AArch64 wall time

- **WHEN** the AArch64 route emits initialization, wall-clock, or per-frame timing fields under QEMU
- **THEN** those fields are excluded from cross-ISA comparison and never used to accept or reject an interpreter optimization

### Requirement: Exact regression gate

Each phase SHALL pass WebAssembly validation, the host test suite, exact Mandelbrot hash, Duck Maze level 1, exact 60-frame generic Plasma, exact Waternet, Rubido, and Untangle replays, deterministic Game of Life state, phoneME route verification, and all KEmulator sound, input, canvas, installation, and RMS gates affected by the phase.

#### Scenario: A single oracle fails

- **WHEN** any required validation, state differential, corpus oracle, target artifact, or affected subsystem gate fails
- **THEN** the optimization phase is rejected and is not combined with the next phase

### Requirement: Generic implementation boundary

Interpreter optimizations SHALL be selected from operation and workload characteristics and SHALL not branch on cartridge identity or introduce user-facing behavior.

#### Scenario: Optimization applies to a cartridge

- **WHEN** a cartridge executes an eligible operation sequence
- **THEN** eligibility depends only on decoded semantics, validated metadata, and measured generic policy inputs

### Requirement: Evidence gate for deferred optimizations

Unsigned i64 division/remainder replacement, SWAR bit operations, and `int`-word stack or local redesign SHALL remain outside the implementation batch until an isolated implementation has reference-VM evidence justifying each change independently. General evidence that handler work or ILP32 `long` operations are expensive is not sufficient acceptance evidence for a specific redesign.

#### Scenario: Deferred candidate has no target evidence

- **WHEN** a deferred optimization has only source-level or desktop evidence
- **THEN** it remains unimplemented by this change
