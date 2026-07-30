## Context

The retained interpreter already includes several independently measured
optimizations, but Waternet and Rubido still exceed a 16,667 microsecond frame
budget on the native i686 phoneME C interpreter. Performance work now spans
the full logical frame: W4IR and execution, runtime host calls, allocation,
framebuffer conversion, presentation, input, audio, and persistent cache
behavior.

Previous changes contain detailed evidence for their own candidates, but there
is no single ongoing ledger for future work. That makes it too easy to repeat a
rejected experiment, lose a negative result after removing its prototype, or
mistake a desktop/JIT result for evidence about old Java ME hardware.

The current verified baseline is commit
`f4c824b1433ee831609caabe30bbce5627c50350`, production artifact SHA-256
`b84d58e19c061fefd7d57523eee7b06fa540a917298ccbe0b38a66daac1ae360`,
native i686 VM SHA-256
`bb4866969747430bb619d139c75ab31982e8967aa0e2dcc3e278fcc7920839a2`,
CLDC classes SHA-256
`117820661071b411b3937e8c708a9581aa48ba06fd06e24eadb5ebbf2036afbe`,
and preverify SHA-256
`4fe0d1b160ac7f18c4f7489917a1d868ff82df50842ca718729b098b25f2f50c`.
A one-repetition clean-tree sanity run passed every route checkpoint and
reported 21,104 us/frame for Waternet, 110,472 for Rubido, and 4,830 for
Untangle. These single samples establish rig health only, not a new timing
baseline or acceptance result.

## Goals / Non-Goals

**Goals:**

- Keep one durable record of every new performance hypothesis and verdict.
- Search the complete no-JIT frame budget rather than optimizing only one
  interpreter tier.
- Measure isolated, production-shaped candidates on the runtime that executes
  the affected code.
- Preserve exact WebAssembly, WASM-4, cache, trap, budget, and MIDP behavior.
- Commit each accepted and fully verified optimization as an independently
  revertible stable step.
- Continue the research, prototype, measure, and decision loop until the owner
  changes the goal or asks it to stop.

**Non-Goals:**

- User-facing features or cartridge-specific shortcuts.
- Treating bytecode counts, HotSpot, KEmulator, QEMU, or deterministic counters
  alone as proof of a phone speedup.
- Combining unrelated hypotheses in their first timing comparison.
- Raising the source level above Java 1.3 or depending on APIs beyond CLDC 1.1
  and MIDP 2.0.
- Keeping speculative code in production merely because its semantics are
  correct.

## Decisions

### 1. Use this design as the durable candidate ledger

Every candidate receives a unique `NJIT-NNN` identifier before or during its
first implementation. Its entry records:

- hypothesis and inspiration;
- affected files and exact mechanism;
- expected benefit and compatibility, heap, code-size, and complexity risks;
- baseline commit plus artifact, VM, class-library, and preverify hashes;
- exact build, correctness, inspection, and timing commands;
- workloads and why they exercise the mechanism;
- exactness results, bytecode and memory effects;
- raw balanced pair results and receipt locations;
- one final status: `accepted`, `rejected`, `inconclusive`, `blocked`, or
  `superseded`;
- decision reason and explicit conditions for reconsideration.

Removed prototypes retain their result entry. Raw generated reports stay in
ignored build storage, while the ledger retains enough values and hashes to
understand the verdict after cleanup.

### 2. Index earlier work instead of re-running it

The detailed pre-ledger record remains in
`optimize-generic-interpreter-tiering/design.md` and
`lower-static-wasm-control-flow/design.md`. The following families are already
closed unless materially new evidence or a different mechanism is stated:

- seven integer compact opcodes: accepted;
- counterless timing artifact and resident W4IR dispatch: accepted;
- dense internal opcode mapping: accepted;
- `i32.load + local.tee`: accepted;
- canonical function type IDs: accepted;
- pc-indexed direct ordinary branch path: accepted;
- forced compact activation: rejected;
- persistent runtime array-reference fields: rejected;
- declared-local-only zeroing: rejected;
- compare-plus-`br_if` and `local.set + br` fusion batches: rejected;
- binary-search branch descriptors: rejected and superseded by direct lookup;
- hot generic push/pop inlining and wrapper-only pop simplification: rejected
  by local native phoneME experiments;
- `int[]` linear memory and cartridge-specific fast paths: out of scope.

A candidate may revisit one of these only when its ledger entry identifies the
new evidence, materially different implementation, or improved measurement
method that changes the old hypothesis.

### 3. Match the performance judge to the code being optimized

Native i686 phoneME without JIT is the primary judge for Java interpreter,
runtime, pure conversion, and pure synthesis work that its harness executes.
Balanced paired effects, not independent medians, determine the sign. At least
eight pairs are required for small effects; more are required when signs,
layout, timer resolution, or host contention are unstable.

KEmulator validates interactive Canvas, touch, RMS, installation, filesystem,
and audio behavior. Its JIT wall time is secondary only. A physical phone is
the final judge for `Graphics.drawRGB`, `flushGraphics`, MMAPI latency, real
heap limits, and input responsiveness. AArch64 phoneME under QEMU verifies only
cross-ISA checkpoints and deterministic counters.

### 4. Gate correctness before timing

Each candidate first passes focused semantic tests and all affected exact
oracles. Interpreter changes additionally preserve instruction-budget trap
points, target-47 bytecode shape, dense `tableswitch`, preverified StackMaps,
and the 16,000-byte `execute` sanity ceiling. The ceiling prevents accidental
unbounded method growth; it is not a device, JAR, or performance-acceptance
limit. Historical entries below retain the 7,800-byte gate that was active
when those artifacts were measured. All candidates record class/JAR and
persistent heap deltas. Timing artifacts compile selection flags out of the hot
path and must be bound to a clean source snapshot and exact binary hashes.

### 5. Keep one variable per first comparison

The first authoritative A/B changes only the candidate mechanism. An accepted
candidate is then measured with the currently retained set before landing;
percentage effects from independent patches are never added. A candidate that
regresses, moves cost elsewhere, falls below reliable resolution, or adds
unjustified memory or complexity is documented, removed, and not committed.

### 6. Candidate ledger

The research queue is ordered by expected target relevance, dynamic coverage,
implementation risk, and the ability to get an authoritative verdict:

| ID         | Candidate                                                                                                                | Source                                                                              | State                                             |
| ---------- | ------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------- | ------------------------------------------------- |
| `NJIT-001` | Local mandatory instruction counter inside compact execution                                                             | phoneME C-interpreter bytecode cost and current `javap`                             | rejected                                          |
| `NJIT-002` | Skip W4IR operand and auxiliary loads for operand-free numeric opcodes                                                   | current dense executor and corpus profile                                           | rejected                                          |
| `NJIT-003` | Implement unsigned i32 comparisons with sign-bit XOR instead of `long` conversion                                        | WebAssembly integer ordering and phoneME ILP32 cost                                 | rejected                                          |
| `NJIT-004` | Reorder `execute` parameters so hot references use `_n` local-load bytecodes                                             | phoneME generic versus short local-load handlers                                    | rejected                                          |
| `NJIT-005` | Fold memory base, offset, overflow, and end checks into one effective-address guard                                      | local algebraic analysis of `checkedAddress`                                        | rejected                                          |
| `NJIT-006` | Lower direct imported calls to a dedicated W4IR host-call opcode                                                         | current intrinsic lowering and import metadata                                      | rejected                                          |
| `NJIT-007` | Replace the explicit `push` capacity check with the mandatory JVM array bounds check                                     | target-47 bytecode, phoneME `lastore`, and exact stack-write counts                 | accepted                                          |
| `NJIT-008` | Expand packed framebuffer bytes and reuse repeated scaled rows                                                           | renderer audit                                                                      | queued, needs renderer microbench and device gate |
| `NJIT-009` | Add an unrotated, unflipped `blitSub` inner loop                                                                         | host-render audit                                                                   | accepted                                          |
| `NJIT-010` | Reduce exact PCM envelope and pitch arithmetic per sample                                                                | audio synthesis audit                                                               | accepted                                          |
| `NJIT-011` | Avoid a third clock read on non-presented logical frames                                                                 | Canvas timing audit                                                                 | queued, physical/KEmulator judge only             |
| `NJIT-012` | Reorder `executeCompactFused` parameters for short local-load bytecodes                                                  | phoneME local-load handlers                                                         | rejected                                          |
| `NJIT-013` | Revisit split i32/long value storage with the corrected phoneME cost model                                               | phoneME stack representation research                                               | queued, separate high-risk design                 |
| `NJIT-014` | Inline packed framebuffer read-modify-write in the transform-free blit loop                                              | `drawPoint` target-47 cost and NJIT-009 coverage                                    | accepted                                          |
| `NJIT-015` | Carry the packed destination byte and bit shift across a plain-blit row                                                  | NJIT-014 target-47 bytecode and opaque-pixel profile                                | accepted                                          |
| `NJIT-016` | Reuse adjacent upscaled ARGB rows through native `System.arraycopy`                                                      | framebuffer scaler audit and phoneME native-arraycopy cost                          | accepted                                          |
| `NJIT-017` | Cache the last packed framebuffer byte while scaling a row                                                               | independent renderer target-bytecode review                                         | accepted                                          |
| `NJIT-018` | Hoist `argbLookup` into the ARGB conversion loop's local frame                                                           | independent renderer target-bytecode review                                         | accepted                                          |
| `NJIT-019` | Cache the packed framebuffer byte in native/downscale conversion                                                         | NJIT-017 result and deferred canonical-loop follow-up                               | accepted                                          |
| `NJIT-020` | Replace the defined-function argument-copy loop with a scalar/native bulk-copy split                                     | Game of Life call profile and native phoneME arraycopy cost                         | accepted                                          |
| `NJIT-021` | Fuse signed i32 comparisons with `br_if` and use the pc-indexed direct branch path                                       | Game of Life control-flow profile plus the accepted direct branch descriptors       | rejected                                          |
| `NJIT-022` | Fuse `i32.load8_u + local.set` into one exact two-instruction W4IR handler                                               | corrected Game of Life production-stream profile and accepted load/tee precedent    | rejected                                          |
| `NJIT-023` | Fuse `i32.add_const + i32.load8_u + local.set` into one exact four-logical-instruction handler                           | NJIT-022 rejection plus stable compact-stream profile                               | rejected                                          |
| `NJIT-024` | Inline the compact `i32.load8_u` stack and scalar-address path                                                           | Game of Life compact coverage plus phoneME Java-call cost                           | inconclusive                                      |
| `NJIT-025` | Inline generic i32 comparisons directly over `values[]`                                                                  | phoneME call-frame cost and integer-heavy route profiles                            | accepted                                          |
| `NJIT-026` | Inline the generic `i32.load` stack, address guard, and byte assembly                                                    | Rubido load profile and NJIT-025 stable baseline                                    | rejected                                          |
| `NJIT-027` | Inline generic `local.set` and `local.tee` stack access                                                                  | phoneME call-frame cost and exact corpus opcode counts                              | rejected                                          |
| `NJIT-028` | Inline generic `local.get` with the exact `push()` capacity trap                                                         | phoneME call-frame cost and exact corpus opcode counts                              | rejected                                          |
| `NJIT-029` | Write horizontal 2-bpp spans a packed byte at a time                                                                     | native phoneME statistical method profile                                           | accepted                                          |
| `NJIT-030` | Inline generic control-frame entry into `execute`                                                                        | post-NJIT-029 phoneME method profile and exact control-opcode counts                | accepted                                          |
| `NJIT-031` | Specialize zero- and one-value control transfers before the generic copy loops                                           | post-NJIT-030 phoneME method profile and exact control-flow counts                  | accepted                                          |
| `NJIT-032` | Inline the compact `i32.load8_u` stack and width-one address path                                                        | corrected NJIT-024 exact fixture plus post-NJIT-031 phoneME method profile          | rejected                                          |
| `NJIT-033` | Overwrite the compact `i32.eqz` input slot with its result                                                               | E15 TOS-overwrite design plus exact corpus opcode counts                            | rejected                                          |
| `NJIT-034` | Inline a folded effective-address guard in compact `i32.load`                                                            | NJIT-005 caller-inline reconsideration plus post-NJIT-031 Rubido profile            | rejected                                          |
| `NJIT-035` | Inline generic control-frame exit at its three dispatch sites                                                            | post-NJIT-031 phoneME method profile and current exact control-opcode counts        | accepted                                          |
| `NJIT-036` | Pop generic `if` and `br_if` conditions directly from `values[]`                                                         | post-NJIT-035 phoneME method profile and current exact branch counts                | rejected                                          |
| `NJIT-037` | Execute compact `w4ir.local_local` directly in the compact loop                                                          | post-NJIT-035 phoneME method profile and exact fused-opcode counts                  | rejected                                          |
| `NJIT-038` | Execute compact `w4ir.local_set_get` as an in-place top-of-stack replacement                                             | post-NJIT-035 phoneME method profile and exact fused-opcode counts                  | rejected                                          |
| `NJIT-039` | Execute compact `w4ir.local_i32_const_add` directly with one guarded stack write                                         | post-NJIT-035 phoneME method profile and exact fused-opcode counts                  | rejected                                          |
| `NJIT-040` | Execute generic `i32.add` as an in-place `values[]` update                                                               | accepted generic comparison precedent and exact Game of Life opcode count           | rejected                                          |
| `NJIT-041` | Absorb a terminal descriptor-backed `br_if` into an existing compact region without enlarging the compact executor frame | prior exact branch-region prototype plus the accepted pc-indexed direct branch path | rejected                                          |
| `NJIT-042` | Execute the six hot helper-backed generic i32 ALU opcodes directly over `values[]`                                       | NJIT-040 reconsideration condition plus whole-corpus ALU coverage                   | rejected                                          |
| `NJIT-043` | Compile distributable JARs with the accepted counterless production config                                               | generic-tiering clean A/B plus current release-path audit and revalidation          | accepted                                          |
| `NJIT-044` | Remove the cartridge-fingerprinted Plasma Java replacement from the universal interpreter                                | product contract and contract-clean baseline audit                                  | accepted                                          |
| `NJIT-045` | Recover the original logical stream sparsely at fused instruction-budget boundaries                                      | rejected full-copy exactness prototype plus fusion-root audit                       | rejected                                          |
| `NJIT-046` | Compile opcode profiling support out of the counterless production artifact                                              | instrumented phoneME bytecode-type profile plus method/BCI sampling                 | accepted                                          |
| `NJIT-047` | Cache the immutable value-stack array in the compact executor's local frame                                              | NJIT-046 method/BCI profile plus target-47 field-access audit                       | rejected                                          |
| `NJIT-048` | Elide compact per-instruction budget comparisons behind one block-admission boolean                                      | post-NJIT-046 method/BCI profile and compact-region accounting                      | rejected                                          |
| `NJIT-049` | Route admitted compact blocks to a separate unchecked executor with a local published counter                            | NJIT-001 and NJIT-048 results plus phoneME branch cost                              | rejected                                          |
| `NJIT-050` | Pass the defined function result arity into the outer executor as a scalar                                               | target-47 result-arity load audit plus Game of Life call density                    | rejected                                          |
| `NJIT-051` | Inline ordinary direct-defined-call frame setup into the outer executor                                                  | Game of Life call density plus phoneME Java-frame cost                              | inconclusive                                      |

The earlier `popFirst`/`popSecond` wrapper-only experiment is not in this
queue: a 16-pair native phoneME repeat measured only +0.060% on Waternet and
mixed neutral or negative controls, so it remains rejected in the historical
index.

### NJIT-001: local compact instruction counter

**Status:** `rejected`.

**Hypothesis and source.** The compact loop currently reads and writes the
`instructionsExecuted` field at every token, including twice for the accepted
two-instruction load/tee fusion. On the portable-C phoneME interpreter every
`aload_0`, `getfield`, arithmetic bytecode, and `putfield` is separately
dispatched. Keeping the mandatory instruction-budget counter in a Java local
for one compact block and publishing it on every normal or exceptional exit
should remove this repeated field traffic without weakening exact budget
accounting. This is distinct from the accepted counterless artifact: that
artifact removes optional diagnostic counters but deliberately leaves this
mandatory counter active.

**Affected files and mechanism.** The production change is limited to
`WasmInterpreter.executeCompactBlock`. It snapshots
`instructionsExecuted`, performs the same per-logical-instruction increments
and limit checks on the local value, and writes the final value back in a
`finally` path so memory, stack, arithmetic, host, and budget traps expose the
same count. Benchmark snapshots use the existing counterless timing config;
no runtime selection branch enters the hot path.

**Expected benefit and risks.** Rubido is the primary real-game workload: the
clean rig sanity receipt records 25,448,416 compact instructions across 129
frames. Generic Plasma is a compute-heavy mechanism control. Waternet and
Untangle currently enter no compact blocks and must remain timing-neutral.
Expected benefit is 1–5% on compact-heavy routes, with zero persistent heap
growth. Main risks are an off-by-one budget trap, failure to publish the count
on a non-budget trap, target-47 `finally`/StackMap behavior, and code growth in
`executeCompactBlock`.

**Baseline identity.**

- source commit:
  `f4c824b1433ee831609caabe30bbce5627c50350`;
- production artifact:
  `b84d58e19c061fefd7d57523eee7b06fa540a917298ccbe0b38a66daac1ae360`;
- native i686 VM:
  `bb4866969747430bb619d139c75ab31982e8967aa0e2dcc3e278fcc7920839a2`;
- CLDC classes:
  `117820661071b411b3937e8c708a9581aa48ba06fd06e24eadb5ebbf2036afbe`;
- preverify:
  `4fe0d1b160ac7f18c4f7489917a1d868ff82df50842ca718729b098b25f2f50c`.

**Planned commands and workloads.**

- focused and complete correctness: `just test`, followed by `just verify`;
- clean artifact and checkpoint sanity:
  `tools/phoneme/run.sh bench waternet rubido untangle plasma-cube --mode
optimized --candidate counterless --reps 1`;
- target inspection: `javap -c -p` on both clean target-47
  `WasmInterpreter.class` files, plus the existing release method-size,
  dense-switch, class-version, and StackMap gates;
- timing: alternate clean baseline and candidate preverified trees with
  `.local/phoneme/cldc_vm_r -EnableTicks =HeapCapacity64M -classpath
.local/phoneme/classes.zip:<tree> w4me.PhoneMeRouteBench <cart> optimized 60
1 counterless <sample>` and calculate paired effects with
  `tools/phoneme/paired-stats.awk`;
- at least eight balanced pairs on Rubido and generic Plasma, with Waternet and
  Untangle controls. Increase repetitions if pair signs or timer resolution
  are unstable.

**Acceptance rule.** Require exact checkpoints and counters, at least +0.8%
median paired effect on Rubido, and no resolved regression worse than -0.5% on
Waternet or Untangle. Plasma explains the compact mechanism but cannot accept
the candidate by itself.

**Correctness and artifact results.**

- `just test` and `just verify` passed on the candidate tree. Both release JARs
  were preverified, all 10 bundled cartridges passed release integrity checks,
  and the seven-workload full-state matrix remained exact.
- Native i686 phoneME checkpoint sanity passed for Waternet, Rubido, Untangle,
  and generic Plasma. Every deterministic output field before timing matched
  between the paired clean artifacts.
- The focused load/tee differential test exercised successful compact
  execution, budget boundaries, and an out-of-bounds memory trap. It also
  exposed an existing outer-versus-compact fused-span budget-count difference;
  that unrelated legacy behavior was not changed by this candidate.
- The clean target-47 counterless `executeCompactBlock` grew from 2,815 to
  2,820 code bytes. Accesses to `instructionsExecuted` fell from six
  `getfield` plus three `putfield` instructions to one `getfield` plus two
  `putfield` instructions, with one exception-table entry added by `finally`.
  The complete `WasmInterpreter.class` grew from 79,340 to 79,505 bytes.
  Persistent heap use was unchanged.
- Clean temporary source snapshots were commit
  `ac3b6cd17bc112da17ba129bddcaffef77b3c1ba` for the baseline and
  `7d02c27d30d5b93fd45a2b6b6f04efe68f75f324` for the candidate. Their staged
  counterless preverified tree hashes were respectively
  `5cb285f3f514651dfefc6a27d22f3be70e1e3582501c7add5c9a290c35d6a0e7`
  and
  `58800cc59b1edf417ae89b27fa0f3b919d11d22382c078b45da2988a59f351b2`.
  The source snapshots and raw receipts are retained under
  `/tmp/w4me-njit001.f2r3fe/` for the lifetime of this host session.

**Native i686 phoneME A/B.** Eight balanced Rubido pairs used 129 routed
frames per invocation. Timer resolution was 7.752 us/frame, source snapshots
were clean, all 30 checkpoints passed per invocation, and all invocations
reported exactly 43,301,827 logical instructions:

| Pair | Order           | Baseline us/frame | Candidate us/frame |
| ---: | --------------- | ----------------: | -----------------: |
|    0 | baseline first  |           103,511 |            104,093 |
|    1 | candidate first |           104,279 |            103,767 |
|    2 | baseline first  |           103,697 |            104,209 |
|    3 | candidate first |           105,108 |            103,573 |
|    4 | baseline first  |           104,317 |            103,837 |
|    5 | candidate first |           103,922 |            103,984 |
|    6 | baseline first  |           104,689 |            103,581 |
|    7 | candidate first |           104,426 |            104,255 |

The median paired delta was 325.5 us/frame, or **+0.312%**, with five wins and
three losses. This is below the predeclared +0.8% acceptance floor. Additional
control timing cannot make the primary Rubido gate pass, so the planned
Waternet, Untangle, and Plasma timing pairs were not spent after their
checkpoint sanity runs.

**Decision.** Reject and remove the implementation. The bytecode mechanism was
real, exact, and slightly positive, but the authoritative improvement was too
small for the added `finally` control flow, class growth, and maintenance cost.
No production commit is made.

**Reconsideration condition if rejected:** only a materially different
accounting layout, a new compact-heavy real cartridge route, or a more precise
target-device timing method.

The rejected source and focused-test changes were removed exactly. The
restored stable tree then passed `just verify`: diagnostic `execute` remained
7,039 bytes, the counterless `execute` remained 7,007 bytes, both release JARs
were preverified, and the seven-workload full-state matrix passed. The stable
counterless verification artifact is
`3378a78eacab9d4271adad5bedda954570e81f0673ddaafc3e167a7019d1bcfc`;
the station and base JAR hashes are respectively
`da61cf43640dd20a8c65c7cde6746d038c1cb428e0526a590b7ad7ef49d3920e`
and
`5ec791eb2a0a363da36c268f711a362014f5dad244dc56870c3c68b5d7d7a3dc`.
No production commit was made for NJIT-001.

### NJIT-002: resident operand-free numeric payload loads

**Status:** `rejected`.

**Hypothesis and source.** The outer executor eagerly reads all three integers
of every fixed-width W4IR record before dispatch. Standard numeric opcodes
`0x45..0xc4` use neither `operand` nor `auxiliary`, so a resident-code range
guard can replace two bounds-checked `iaload` operations with zero
initialization. Target-47 `javap` shows that each current payload read expands
to six interpreted Java bytecodes before the main `tableswitch`; local phoneME
disassembly confirms that `iaload` performs null, negative-index, and upper
bounds checks in the portable-C interpreter.

This does not repeat the accepted resident W4IR page guard or dense opcode
mapping: those changes remove page-range arithmetic and linear switch lookup,
while both payload array reads remain present on the current stable tree.

**Coverage and selected scope.** The preserved format-15 exact corpus profile
contains 169,131,105 outer dispatches. The numeric band accounts for 23.872%
overall, 18.331% on the Waternet browser route, 18.315% on Rubido, 10.751% on
Untangle, 22.997% on Duck Maze, 23.706% on Game of Life, and 24.527% on
generic Plasma. This profile disabled compact execution and therefore
overstates the optimized outer-path exposure on compact-heavy Rubido and
Plasma; Waternet is the primary real-game workload.

The first candidate intentionally excludes other scattered operand-free
opcodes, dense bulk saturating conversions, and `W4IR_F32_MUL_ADD`. It also
leaves `executeCompactBlock` unchanged. That keeps the classifier to one
contiguous standard-opcode range and isolates one outer-executor mechanism.

**Affected file and mechanism.** In
`WasmInterpreter.execute`, declare `operand` and `auxiliary` without eager
initializers. When `residentCode` is true and `opcode` is in `0x45..0xc4`,
assign zero to both unused locals; otherwise perform the original two array
loads in their original pre-accounting position. The resident guard is
mandatory: a checksum-valid but truncated paged RMS W4IR record currently
fails on the eager `+1` or `+2` access after page reload. Skipping those reads
on paged code could execute an incomplete numeric instruction and change
trap, mutation, profiling, or budget order. The existing page-range guard is
not changed.

**Expected benefit and risks.** The resident numeric path removes two checked
array accesses but every outer dispatch pays the resident/range classifier.
The sign is deliberately unresolved until native phoneME A/B. Persistent heap
delta is zero and no W4IR/cache format change is expected. Risks are a
wrong range boundary, accidental payload suppression on paged code, method
layout changes, a nonnumeric slowdown larger than the numeric saving, and
`tableswitch` alignment or method-size growth.

**Baseline identity.**

- source commit:
  `f4c824b1433ee831609caabe30bbce5627c50350`;
- stable counterless verification artifact:
  `3378a78eacab9d4271adad5bedda954570e81f0673ddaafc3e167a7019d1bcfc`;
- native i686 VM:
  `bb4866969747430bb619d139c75ab31982e8967aa0e2dcc3e278fcc7920839a2`;
- CLDC classes:
  `117820661071b411b3937e8c708a9581aa48ba06fd06e24eadb5ebbf2036afbe`;
- preverify:
  `4fe0d1b160ac7f18c4f7489917a1d868ff82df50842ca718729b098b25f2f50c`.

**Planned verification and measurements.**

- Add a focused differential covering resident and paged W4IR at the
  `0x44/0x45/0xc4` boundaries, numeric stack and arithmetic traps, and
  instruction-budget boundaries. A checksum-valid page truncated by one or
  two integers must retain the stable pre-execution failure and state.
- Run `just test` and `just verify`, inspect both diagnostic and counterless
  target-47 `execute` methods with `javap`, and record method/class/JAR and
  persistent-heap deltas.
- Build clean hash-bound baseline and candidate counterless artifacts. Run
  checkpoint sanity before timing.
- Use at least eight balanced native i686 phoneME pairs on Waternet as the
  primary route, with Untangle as a nonnumeric-sensitive no-regression control.
  If Waternet passes, run Rubido and generic Plasma to detect compact/compute
  interaction. Do not time KEmulator or QEMU as performance evidence.

**Acceptance rule.** Require exact state, trap, budget, paged-cache, and
checkpoint behavior; a median paired Waternet improvement of at least +0.8%;
and no resolved regression worse than -0.5% on Untangle. Rubido and Plasma
cannot accept a Waternet-oriented outer-dispatch candidate by themselves.

**Correctness and artifact results.**

- `just test` and `just verify` passed on the isolated candidate. The complete
  exact corpus, seven-workload full-state differential, valid and malformed
  paged W4IR, trap and instruction-budget suites, both preverified release
  JARs, and all 10 bundled cartridge integrity checks remained green.
- The focused extension fixture executes the inclusive numeric boundary
  `0xc4` (`i64.extend32_s`) and retains the existing `0x44` constant and bulk
  opcode coverage. The resident-only guard leaves paged payload reads in their
  original pre-accounting position.
- Native i686 phoneME checkpoint sanity passed for Waternet, Rubido, Untangle,
  and generic Plasma. Every deterministic field before `init-ms` matched
  between baseline and candidate in every timing pair.
- The diagnostic target-47 `execute` method grew from 7,039 to 7,067 code
  bytes; the counterless method grew from 7,007 to 7,035. Both retained the
  dense `tableswitch`, classfile version 47, preverified StackMaps, and 733
  bytes of diagnostic method headroom below the 7,800-byte gate.
- The clean counterless `WasmInterpreter.class` grew from 52,751 to 52,791
  bytes. The station JAR grew from 228,102 to 228,157 bytes and the base JAR
  from 225,588 to 225,643 bytes. Candidate JAR hashes were respectively
  `3be2fd9ff4e451a897254600f0b1bd8e6706ca86dc9b418189c7950294d93496`
  and
  `534467eb21ed4fdd2f23fe9fe6fcdcf57a96d0c33dcbbe50acd4b5915b71408c`.
  No field, array, retained object, or W4IR record was added, so persistent
  heap delta is zero.
- Clean temporary source snapshots were commit
  `ac3b6cd17bc112da17ba129bddcaffef77b3c1ba` for the baseline and
  `71dcac96e1767c67481dad250ac503b9fbca7fc4` for the candidate. The baseline
  counterless preverified tree hash was
  `5cb285f3f514651dfefc6a27d22f3be70e1e3582501c7add5c9a290c35d6a0e7`.
  Waternet timing used the candidate's Waternet-only tree hash
  `7dc9a18f2ec3c5070697abec04ccb2c638a279a1045e24030281319b2ede732c`;
  the unchanged classes plus all four control resources produced tree hash
  `e3e530f17854f263ea94947943730340428fc3f6ce06d5fb9bdb6f1b5640cdf3`
  for the other timing runs.

**Native i686 phoneME A/B.** All runs used the counterless optimized artifact,
balanced order, exact route checkpoints, and the VM and CLDC hashes recorded
above. Raw outputs and CSV files are under
`/tmp/w4me-njit002.1KywKl/evidence/` for the lifetime of this host session.

| Workload       | Routed frames | Median delta us/frame | Median speedup | Wins/losses | Timer resolution |
| -------------- | ------------: | --------------------: | -------------: | ----------: | ---------------: |
| Waternet       |           153 |                +219.0 |    **+1.074%** |         7/1 |   6.536 us/frame |
| Untangle       |           460 |                 +26.0 |    **+0.544%** |         7/1 |   2.174 us/frame |
| Rubido         |           129 |              +1,778.5 |    **+1.711%** |         8/0 |   7.752 us/frame |
| generic Plasma |            10 |             -43,200.0 |    **-3.751%** |         0/8 | 100.000 us/frame |

The raw pairs were:

| Pair |    Waternet B/C |  Untangle B/C |        Rubido B/C |            Plasma B/C |
| ---: | --------------: | ------------: | ----------------: | --------------------: |
|    0 | 20,483 / 20,176 | 4,723 / 4,765 | 103,844 / 102,062 | 1,172,700 / 1,186,000 |
|    1 | 20,647 / 20,117 | 4,760 / 4,726 | 104,162 / 102,240 | 1,158,400 / 1,177,400 |
|    2 | 20,673 / 20,202 | 4,758 / 4,721 | 104,271 / 101,891 | 1,144,100 / 1,198,700 |
|    3 | 20,607 / 20,542 | 4,758 / 4,750 | 104,186 / 102,635 | 1,151,700 / 1,196,600 |
|    4 | 20,496 / 20,339 | 4,839 / 4,756 | 104,085 / 102,310 | 1,163,500 / 1,187,100 |
|    5 | 20,313 / 20,496 | 4,758 / 4,743 | 104,379 / 102,697 | 1,144,000 / 1,203,300 |
|    6 | 20,483 / 20,366 | 4,789 / 4,745 | 107,527 / 102,155 | 1,151,400 / 1,192,900 |
|    7 | 20,346 / 20,065 | 4,815 / 4,797 | 104,186 / 102,581 | 1,150,500 / 1,218,200 |

**Decision.** Reject and remove the candidate. It exceeded the primary
Waternet floor and improved both game controls, but generic Plasma regressed
by 3.751% in all eight pairs. That material compute regression is larger than
the accepted game gains and demonstrates that the added classifier and method
layout are not generally profitable. No production commit is made.

The candidate and its focused boundary fixture were removed exactly. The
restored baseline then passed `just verify`: diagnostic `execute` returned to
7,039 bytes, counterless `execute` returned to 7,007 bytes, both release JARs
were preverified at 228,102 and 225,588 bytes, and the seven-workload
counterless full-state matrix remained exact. The stable counterless artifact
is again
`3378a78eacab9d4271adad5bedda954570e81f0673ddaafc3e167a7019d1bcfc`.

**Reconsideration condition if rejected:** only a branch-free operand-shape
encoding, a separately validated page format that makes all records safe for
lazy payload access, a function-level policy that avoids the measured compute
regression, or a target-device measurement method precise enough to resolve a
smaller effect.

### NJIT-003: sign-bit lowering for unsigned i32 comparisons

**Status:** `rejected`.

**Hypothesis and source.** WebAssembly unsigned 32-bit ordering can be mapped
exactly to Java signed `int` ordering by XORing both operands with
`0x80000000`. The current common `compareI32` helper instead converts each
operand to `long`, masks both with `0xffffffffL`, runs `lcmp`, and branches.
Target-47 `javap` shows 10 Java bytecodes in each unsigned comparison core.
The XOR form uses seven and removes two `i2l`, two 64-bit `land`, and one
`lcmp` in exchange for two 32-bit `ixor` instructions.

Three independent read-only reviews agreed on the minimal form and found no
semantic counterexample or prior duplicate. Local phoneME disassembly and an
isolated Java 1.3 prototype support the mechanism but do not establish wall
time. The prototype passed a boundary cross product and one million
deterministic input pairs. The four changes remain inside the existing private
helper; outer and compact executors already share it. Counted trace and the
cartridge fast path do not call this helper and are intentionally unchanged.

This is not a repeat of the rejected unsigned-compare-plus-`br_if` fusion in
commits `874054a` and `20cdc1d`. That experiment changed W4IR dispatch and
control flow and regressed Rubido. NJIT-003 changes only the arithmetic
lowering after the same two stack pops and introduces no opcode or fusion.
Repository history contains no earlier sign-bit implementation in
`compareI32`.

**Coverage and selected scope.** The preserved exact generic-corpus profile
contains 2,697,395 executions of `i32.lt_u` (674,905), `i32.gt_u`
(1,347,966), `i32.le_u` (9,790), and `i32.ge_u` (664,734). Generic Plasma
contributes 2,606,428 across 60 frames, or about
43,440 per frame. The Waternet browser oracle route contributes 27,905, Duck
Maze 15,300, Untangle 19,638, Rubido 700, and one Game of Life frame 25,923.
Plasma is therefore the primary mechanism workload and Waternet is the
representative game control.

**Affected files and mechanism.** In
`WasmInterpreter.compareI32`, keep the existing `right`-then-`left` pop order
and replace only operations 3, 5, 7, and the default operation 9 with signed
comparisons of `left ^ 0x80000000` and `right ^ 0x80000000`. Add a focused
WASM fixture that evaluates the full cross product of
`{0, 1, 0x7fffffff, 0x80000000, 0xfffffffe, 0xffffffff}` for all four
relations, and a
Java smoke test that checks every result against the old unsigned-`long`
oracle in both forced-outer and compact-enabled execution. Include exact
`N-1/N` instruction-budget boundaries.

**Expected benefit and risks.** Every dynamic unsigned i32 comparison removes
three interpreted Java-bytecode dispatches and all 64-bit comparison traffic.
The current target-47 helper is 266 code bytes and is expected to shrink to
about 254; `execute`,
W4IR, cache format, persistent heap, and cartridge state are unchanged. Main
risks are a reversed operand or relation, insufficient boundary coverage,
compact-versus-outer divergence, and whole-class layout effects on phoneME.

**Baseline identity.**

- source commit:
  `f4c824b1433ee831609caabe30bbce5627c50350`;
- stable counterless verification artifact:
  `3378a78eacab9d4271adad5bedda954570e81f0673ddaafc3e167a7019d1bcfc`;
- native i686 VM:
  `bb4866969747430bb619d139c75ab31982e8967aa0e2dcc3e278fcc7920839a2`;
- CLDC classes:
  `117820661071b411b3937e8c708a9581aa48ba06fd06e24eadb5ebbf2036afbe`;
- preverify:
  `4fe0d1b160ac7f18c4f7489917a1d868ff82df50842ca718729b098b25f2f50c`.

**Planned verification and measurements.**

- Run the focused 36-pair by four-relation oracle in forced outer and
  compact-enabled modes, including the exact instruction-budget boundary.
- Run `just test` and `just verify`, inspect target-47 `compareI32` to confirm
  `i2l`, `land`, and `lcmp` disappeared from its unsigned cases, and record
  method/class/JAR and persistent-heap deltas.
- Build clean hash-bound baseline and candidate counterless artifacts and
  require identical checkpoint and deterministic route fields.
- Run at least eight balanced native i686 phoneME pairs on generic Plasma,
  using 10 compute frames per invocation for a tractable but timer-resolved
  primary series. If it passes, run at least eight Waternet pairs and
  no-regression controls on Duck Maze, Untangle, and Rubido. KEmulator and
  QEMU wall time are not evidence for this candidate.

**Acceptance rule.** Require exact semantics and all compatibility gates, a
median paired generic Plasma improvement of at least +0.8%, and no resolved
regression worse than -0.5% on Waternet, Untangle, or Rubido. A game route may
strengthen but cannot replace the compare-heavy primary result.

**Correctness and artifact results.**

- The focused fixture passed all 36 operand pairs and four unsigned relations
  against the original `long`-mask oracle in both forced-outer and
  compact-enabled execution. It also passed the exact `N-1/N` instruction
  budget boundary and proved that compact execution was exercised.
- `just test` and `just verify` passed. Both release JARs were preverified, all
  10 bundled cartridges passed release integrity checks, and the seven-workload
  full-state matrix remained exact.
- Clean native i686 phoneME sanity runs matched every deterministic field
  between baseline and candidate. Waternet passed 17/17 checkpoints over 94
  frames, Untangle passed 47/47 over 401 frames, and Rubido passed 30/30 over
  70 frames. Generic Plasma matched its instruction, trace, branch-fast, and
  W4IR counters over 10 frames.
- Target-47 `compareI32` shrank from 266 to 254 code bytes. The complete
  counterless `WasmInterpreter.class` shrank from 52,751 to 52,739 bytes.
  Diagnostic and counterless `execute` remained 7,039 and 7,007 code bytes.
  Persistent heap and W4IR format were unchanged. Station and base release
  JARs each grew by one byte because ZIP compression changed, to 228,103 and
  225,589 bytes.
- The candidate verification artifact was
  `c55d646ce4ca68991948b6b97124d488ddbf44a8cc2da3a85834314dce46921f`;
  station and base JAR hashes were
  `bca87973dc3278f5323a84e941862759c304ceba3057757403defc64684c01df`
  and
  `74cca579200527cc32c7ce4ff3c2e9d29359b4cff174f50afb20fd9cbd692ff3`.
- Clean timing snapshots were baseline commit
  `f4c824b1433ee831609caabe30bbce5627c50350` and temporary candidate commit
  `3cd3074591306e3087b241eb176513ee415d9873`. Their complete staged
  counterless tree hashes were
  `57fd453b47a4153b0c3d7ac983e85ea51341183a710e1b7d6d793bf25cf08a51`
  and
  `598221b48da3a1ad006125d9cf13e1dfca9015c97d9a670ee52ee9b56b33dd58`.
  Their counterless interpreter-class hashes were
  `ca4644d8ef04fdd8625f28ded3d2e434f3fb0af915ce8886f23db38a397e59c5`
  and
  `e69d32998f9fd853b4096d1f29a51e16f6823a0d98a0b8420dfff66d78e6e103`.

**Native i686 phoneME A/B.** Eight balanced generic Plasma pairs used 10
compute frames per invocation. Timer resolution was 100 us/frame, source
snapshots were clean, and every invocation reported exactly 50,492,866 logical
instructions, 110,662 trace calls, and 331,986 trace iterations:

| Pair | Order           | Baseline us/frame | Candidate us/frame |
| ---: | --------------- | ----------------: | -----------------: |
|    0 | baseline first  |         1,168,200 |          1,183,200 |
|    1 | candidate first |         1,155,800 |          1,167,600 |
|    2 | baseline first  |         1,169,800 |          1,194,400 |
|    3 | candidate first |         1,160,100 |          1,178,100 |
|    4 | baseline first  |         1,150,800 |          1,171,100 |
|    5 | candidate first |         1,151,200 |          1,184,700 |
|    6 | baseline first  |         1,155,600 |          1,165,600 |
|    7 | candidate first |         1,170,400 |          1,164,300 |

The median paired delta was -16,500 us/frame, or **-1.418%**, with one win and
seven losses. This decisively failed the predeclared +0.8% primary gate.
Additional game timing could not make the primary gate pass, so the Waternet,
Untangle, and Rubido controls were not spent after their exact sanity runs.
The phoneME harness has no routed Duck Maze checkpoint workload, so its
single-idle-frame path was not treated as a meaningful timing control. Raw
receipts and the pair CSV are retained under
`/tmp/w4me-njit003.g1fuw2/evidence/` for the lifetime of this host session.

**Decision.** Reject and remove the candidate. The arithmetic identity,
semantic coverage, and bytecode reduction are real, but the production-shaped
class is repeatably slower on the authoritative no-JIT runtime. The most
plausible unresolved cause is whole-class or handler-layout sensitivity;
shorter Java bytecode is not sufficient evidence of lower phoneME wall time.
No production commit is made.

**Reconsideration condition if rejected:** only a different branch-free
32-bit comparison lowering, evidence that whole-class layout rather than the
helper caused the verdict, or a more precise target-device measurement method.

The focused candidate fixture and production expressions were removed exactly.
The restored stable tree then passed `just verify`: diagnostic `execute`
returned to 7,039 bytes, counterless `execute` returned to 7,007 bytes, both
release JARs were preverified at 228,102 and 225,588 bytes, and the
seven-workload counterless full-state matrix remained exact. The stable
counterless verification artifact is again
`3378a78eacab9d4271adad5bedda954570e81f0673ddaafc3e167a7019d1bcfc`.
No production commit was made for NJIT-003.

### NJIT-004: short-local parameter layout for the outer executor

**Status:** `rejected`.

**Hypothesis and source.** Java bytecodes have one-byte `aload_0..aload_3` and
`iload_0..iload_3` forms, while a load from a higher local slot uses a
two-byte generic opcode plus an operand. The native i686 phoneME portable-C
interpreter also gives the short forms materially smaller handlers: the
current binary contains 33-byte `aload_1`/`iload_1` handlers and 51-byte
generic `aload`/`iload` handlers. The generic handlers additionally fetch the
local index from the bytecode stream, advance by two bytes, scale the index,
and save/restore a native register.

The current private `execute` signature assigns slots as follows:

| Slot | Parameter             | Static target-47 loads |
| ---: | --------------------- | ---------------------: |
|    1 | `functionIndex`       |                      2 |
|    2 | `body`                |                     21 |
|    3 | `functionType`        |                     10 |
|    4 | `locals`              |                     64 |
|    5 | `functionStackBase`   |                     12 |
|    6 | `functionControlBase` |                     10 |

The same counts occur in diagnostic and counterless target-47 artifacts.
Consequently the current three short parameter slots cover 33 static loads,
while the two hottest generic parameters account for 76.

Three independent reviews selected the same minimal layout: swap only
`functionIndex` and `locals`, yielding
`execute(long[] locals, FunctionBody body, FuncType functionType, int
functionIndex, int functionStackBase, int functionControlBase)`. This leaves
every other parameter in its current relative order, moves 64 `locals` loads
to `aload_1`, and moves only two `functionIndex` loads to generic `iload 4`.
It therefore replaces a net 62 generic parameter loads in the method body with
short forms and should shrink `execute` by about 62 code bytes. Sorting all
parameters by frequency could save only two additional bytes while mixing
three same-typed integer arguments, so it is deliberately excluded.

**History and dynamic selection evidence.** The signature has kept its
current order since the interpreter first appeared in commit `bd3c374`.
Repository and OpenSpec history contain no earlier parameter-layout A/B.
The preserved exact format-15 outer profile is selection evidence rather than
a current timing verdict. Even before counting fused W4IR handlers, standard
`local.get`, `local.set`, and `local.tee` account for 185,867 dispatches
(14.25%) on the Waternet browser route, 2,493,657 (19.81%) on Rubido,
25,066,556 (17.46%) on generic Plasma, 127,123 (11.87%) on Untangle, and
20,561 (4.15%) on Duck Maze. Each such outer handler loads the `locals`
parameter; many existing fused handlers load it multiple times. Waternet and
Untangle execute no compact blocks on the retained route and therefore expose
the outer parameter layout directly. Compact and trace executors are separate
methods and are intentionally unchanged.

**Affected files and mechanism.** Change only the argument order at the sole
private call site and method declaration in `WasmInterpreter`. Every argument
at the call site is already a side-effect-free local variable. Update the two
method-extraction matchers in `tools/verify.sh` to recognize the private
`execute` method independently of its first parameter type; this is a gate
repair, not production behavior. No expression, handler, data representation,
W4IR/cache format, counter, trap point, or persistent allocation changes.

**Expected benefit and risks.** Expected benefit is a small but broad reduction
in interpreted bytecode work on outer-executor workloads, with Waternet as the
primary real-game judge. The candidate should have zero persistent-heap delta,
reduce method/class/JAR size slightly, and increase bytecode headroom. Risks
are whole-class or call-site layout sensitivity on phoneME, a benefit below
timer resolution, a loss from moving the rarely used index parameter, and an
accidental mismatch between declaration and the sole call site. All parameters
are category-1, so compiler temporary slots 7 through 129 do not move. The
method is private, has no overload or reflective caller, and does not recurse
directly.

**Baseline identity.**

- source commit:
  `f4c824b1433ee831609caabe30bbce5627c50350`;
- stable counterless verification artifact:
  `3378a78eacab9d4271adad5bedda954570e81f0673ddaafc3e167a7019d1bcfc`;
- native i686 VM:
  `bb4866969747430bb619d139c75ab31982e8967aa0e2dcc3e278fcc7920839a2`;
- CLDC classes:
  `117820661071b411b3937e8c708a9581aa48ba06fd06e24eadb5ebbf2036afbe`;
- preverify:
  `4fe0d1b160ac7f18c4f7489917a1d868ff82df50842ca718729b098b25f2f50c`.

**Planned verification and measurements.**

- Compile target 47 and inspect the exact signature, parameter slots, short and
  generic load counts, `execute` size, dense `tableswitch`, StackMap, class
  size, JAR size, and heap delta.
- Run `just test` and `just verify`; require the complete seven-workload
  full-state differential, budget/trap fixtures, release integrity,
  Java 1.3, CLDC bootclasspath, preverification, and relevant KEmulator gates.
- Build clean hash-bound counterless baseline and candidate snapshots and
  require byte-identical deterministic phoneME fields before timing.
- Run at least eight balanced native i686 phoneME pairs on the Waternet browser
  route as the primary gate. If it passes, run eight-pair no-regression controls
  on Untangle, Rubido, and generic Plasma. Use current routed frame counts for
  games and 10 compute frames for Plasma.

**Acceptance rule.** Require at least +0.8% median paired improvement on
Waternet, at least six wins in eight pairs, exact behavior, and no resolved
regression worse than -0.5% on Untangle, Rubido, or generic Plasma. Static
handler and bytecode reductions cannot accept the candidate by themselves.

**Correctness and artifact results before timing.**

- `just test` and `just verify` passed. Both release JARs were preverified, all
  10 bundled cartridges passed release integrity checks, and the seven-workload
  full-state matrix remained exact, including memory, globals, framebuffer,
  instruction counters, traps, branches, calls, and cached W4IR.
- Target-47 produced the planned descriptor
  `execute(long[], FunctionBody, FuncType, int, int, int)`. Its parameter-load
  histogram is 64 `aload_1` for `locals`, 21 `aload_2` for `body`, 10
  `aload_3` for `functionType`, two generic `iload 4` for `functionIndex`, 12
  `iload 5` for `functionStackBase`, and 10 `iload 6` for
  `functionControlBase`.
- Diagnostic `execute` shrank from 7,039 to 6,976 code bytes; counterless
  `execute` shrank from 7,007 to 6,944. Both retain a dense `tableswitch`.
  The diagnostic interpreter class shrank from 52,867 to 52,804 bytes and the
  counterless class from 52,751 to 52,688 bytes. Persistent heap use and W4IR
  format are unchanged.
- The candidate counterless verification artifact is
  `4021d7919f58fd418a47207782aeaee2a61b61c4211c99dc443fe6c15c439aa0`.
  Station and base release JARs are 228,093 and 225,579 bytes, with hashes
  `77ce72197eb4703f1c28ae6d2a7cd2e73267b5c2f02e7931699021cf5e422700`
  and
  `b81d7a2231438a5567df93396c01ea03453a4f795be33b554934582e591e91e9`.

**Clean snapshot identity and phoneME sanity.** The clean baseline was commit
`f4c824b1433ee831609caabe30bbce5627c50350`; the temporary candidate was
commit `e9aa5dc042377750e75f225f04933f61d013eda9`. Their complete staged
counterless tree hashes were
`d7ca20dcf808c12a33395ca144db5310e4685cd42688e6a4a726fc57d8982017`
and
`58aacecd2c32f21ecf4ce7fef490ab576bcb4e365c0ced330ae7d7b769d954c9`.
The preverified interpreter-class hashes were
`ca4644d8ef04fdd8625f28ded3d2e434f3fb0af915ce8886f23db38a397e59c5`
and
`9a8c22f0f3e10f810d9aa4269455fef67cb4d5184a7ee4d4606a58f391e3484f`.
A one-repetition native Waternet sanity run passed all 17 checkpoints and
matched every deterministic field.

**Native i686 phoneME A/B.** Eight balanced Waternet pairs used the complete
94-frame browser route per invocation. Timer resolution was 6.536 us/frame,
source snapshots were clean, and every invocation reported exactly 1,650,956
logical instructions and the same branch-fast payload:

| Pair | Order           | Baseline us/frame | Candidate us/frame |
| ---: | --------------- | ----------------: | -----------------: |
|    0 | baseline first  |            20,483 |             20,320 |
|    1 | candidate first |            20,326 |             20,594 |
|    2 | baseline first  |            20,542 |             20,477 |
|    3 | candidate first |            20,535 |             20,483 |
|    4 | baseline first  |            20,594 |             20,457 |
|    5 | candidate first |            20,431 |             20,326 |
|    6 | baseline first  |            20,339 |             20,248 |
|    7 | candidate first |            20,562 |             20,359 |

The median paired delta was 98 us/frame, or **+0.481%**, with seven wins and
one loss. The sign is credible, but the magnitude is below the predeclared
+0.8% acceptance floor. Additional controls cannot make the primary gate pass,
so Untangle, Rubido, and Plasma timing pairs were not spent. Raw receipts and
the pair CSV are retained under `/tmp/w4me-njit004.c8UYAO/evidence/` for the
lifetime of this host session.

**Decision.** Reject and remove the candidate. The short-load mechanism,
bytecode and class-size reduction, exact semantics, and small positive wall
effect are all confirmed, but a sub-half-percent gain does not justify
source-order churn and a verification-tool dependency under the goal's
conservative sub-percent policy. No production commit is made.

The production and verification-tool changes were removed exactly. The
restored stable tree then passed `just verify`: diagnostic `execute` returned
to 7,039 bytes, counterless `execute` returned to 7,007 bytes, both release
JARs were preverified at 228,102 and 225,588 bytes, and the seven-workload
counterless full-state matrix remained exact. The stable counterless artifact
is again
`3378a78eacab9d4271adad5bedda954570e81f0673ddaafc3e167a7019d1bcfc`.
No production commit was made for NJIT-004.

**Reconsideration condition if rejected:** only a different slot assignment
supported by dynamic reference counts, evidence that class layout rather than
the local-load mechanism caused the verdict, or a more precise physical-device
measurement method.

### NJIT-005: folded scalar effective-address guard

**Status:** `rejected`.

**Hypothesis and source.** WebAssembly memory operands contain an unsigned
32-bit static offset. A scalar load or store computes the effective address
from that offset and the unsigned 32-bit dynamic base and traps when any byte
of the access is outside linear memory. W4ME stores both bit patterns in signed
Java `int` values, so a negative Java value is necessarily outside the fixed
64-KiB memory but must not be confused with a valid signed address.

The current scalar guard is exact:

```java
int maximumBase = module.memory.length - size;
if (base < 0
        || offset < 0
        || base > maximumBase
        || offset > maximumBase - base) {
    throw new WasmTrap("out-of-bounds memory access");
}
return base + offset;
```

For every current caller, `size` is one of 1, 2, 4, or 8 and therefore
`maximumBase` is in 65,528 through 65,535. After rejecting a set sign bit in
either operand, both operands are nonnegative and
`base > maximumBase - offset` is exactly equivalent to
`base + offset > maximumBase`, without first performing a possibly overflowing
sum. The isolated candidate is consequently:

```java
if ((base | offset) < 0 || base > maximumBase - offset) {
    throw new WasmTrap("out-of-bounds memory access");
}
```

`maximumBase - offset` cannot overflow after the sign guard: its minimum is
greater than `Integer.MIN_VALUE`. The returned addition cannot overflow on the
successful path because the second comparison has already proved that its
result is at most 65,535. This is the same algebra used by bounds-check
elimination in small fixed memories; the WebAssembly 1.1 binary, syntax, load,
and store specifications are the semantic authority. Java's defined wrapping
integer arithmetic is accounted for explicitly rather than used as an
unsigned-address shortcut.

Three independent read-only reviews converged on this two-branch form. A
target-47 scratch compilation measured the current helper at 48 code bytes and
23 successful-path JVM bytecodes with four conditional branches. The
candidate is 40 bytes and 20 JVM bytecodes with two conditional branches.
The retained i686 phoneME binary has 30-byte `ior` and `isub` handlers,
95-byte `iflt`/`ifge` handlers, and 103-byte integer-compare branch handlers.
This proves that three Java bytecode dispatches and two branch handlers are
removed from every successful helper call; it does not prove a wall-time
speedup.

**Scope and alternatives.** Change only `checkedAddress` in the first isolated
candidate. Its 37 `address` call sites and 16 direct call sites cover ordinary,
compact, fused, and trace scalar accesses. Two outer-executor W4IR loops contain
manually inlined copies of the old four-condition guard. They remain unchanged
so the first A/B measures one helper implementation rather than a helper plus
hot-loop specialization. If the helper passes, applying the same algebra to
those two loops is a separate candidate.

Bulk `memory.init`, `memory.copy`, and `memory.fill` stay out of scope. Their
length is dynamic, zero length admits an address at exactly the memory end, and
their source/destination trap order differs from scalar accesses. Also
excluded are an early `base + offset` check, which can accept a double-wrapped
address; `try`/`catch`, which can expose Java exceptions or partially write a
multi-byte store; a `long` unsigned sum, which restores measured ILP32
conversion costs; and predecoded unconditional traps, which would change the
persisted W4IR representation.

**Dynamic selection evidence.** The retained exact seven-workload profile is
format 15 rather than current format 16, so it is selection evidence, not the
timing artifact. The intervening direct-branch format change does not alter
the scalar memory instruction definitions. On the complete browser routes it
records 1,578,959 scalar memory operations on Rubido (10.176% of logical
instructions, 22,557 per frame), 88,076 on Waternet (937 per frame), and
126,451 on Untangle (315 per frame). Generic Plasma executes at least
20,514,986 helper-backed accesses over 60 frames after accounting for fused
instructions and excluding the two manual-guard handlers. The candidate saves
three target-JVM bytecode dispatches per covered access. Rubido is the primary
game gate; Plasma is a high-coverage control rather than a substitute for a
game result.

**Correctness risks and focused gates.**

- Preserve all valid final-byte boundaries for widths 1, 2, 4, and 8 and trap
  one byte beyond them for base-only, offset-only, and split base-plus-offset
  addresses.
- Cover bit patterns `-1`, `Integer.MIN_VALUE`, and `Integer.MAX_VALUE`,
  unsigned offsets `0x80000000` and `0xffffffff`, and additions that would
  overflow a signed Java `int`.
- Require the exact `WasmTrap("out-of-bounds memory access")`, never a Java
  array exception.
- Prove that a failed width-2, width-4, or width-8 store changes no byte.
  Preserve the intentional non-atomic ordering of fused sequences containing
  several distinct Wasm stores.
- Exercise outer and compact scalar paths and sweep budgets across the
  instruction immediately before the memory operation. The dispatcher's
  budget trap must continue to win before address evaluation.
- Keep the complete memory, globals, table, logical instruction count, cache,
  release, CLDC, and MIDP gates exact.

Checksum-valid cached operands may contain any 32-bit value because persisted
W4IR pages are integrity-checked but not re-decoded semantically. The guard
must therefore remain correct for every `int` bit pattern. This algebra-only
change does not alter W4IR format 16, cache fingerprints, instruction
accounting, trap position, stack order, or persistent heap.

**Baseline identity.**

- source commit:
  `f4c824b1433ee831609caabe30bbce5627c50350`;
- stable counterless verification artifact:
  `3378a78eacab9d4271adad5bedda954570e81f0673ddaafc3e167a7019d1bcfc`;
- native i686 VM:
  `bb4866969747430bb619d139c75ab31982e8967aa0e2dcc3e278fcc7920839a2`;
- CLDC classes:
  `117820661071b411b3937e8c708a9581aa48ba06fd06e24eadb5ebbf2036afbe`;
- preverify:
  `4fe0d1b160ac7f18c4f7489917a1d868ff82df50842ca718729b098b25f2f50c`.

**Planned commands and measurements.** Add a focused Wasm fixture and
host/CLDC-compatible smoke covering the matrix above, then run `just test` and
`just verify`. Inspect target-47 `javap`, helper and `execute` code sizes,
dense-switch shape, class/JAR sizes, preverification, and persistent heap.
Create clean, hash-bound counterless baseline and candidate snapshots and
first run one native phoneME Rubido sanity repetition for deterministic-field
identity. The primary verdict uses at least eight balanced native i686 pairs
on the complete 70-frame Rubido route. Only if it passes, run eight-pair
Waternet, Untangle, and generic-Plasma no-regression controls.

**Acceptance rule.** Require at least +0.8% median paired improvement on
Rubido, at least six wins in eight pairs, exact behavior, and no resolved
regression worse than -0.5% on any control. Method-size reduction and
theoretical handler savings cannot accept the candidate. A primary result
below the floor is rejected without spending control timing.

**Correctness and artifact results.** The focused fixture covers every scalar
load and store family, widths 1, 2, 4, and 8, the final valid byte, first
invalid byte, split addresses, signed-negative `u32` representations, signed
addition overflow, failed multi-byte stores, outer and compact execution, and
budget-before-address ordering. It passed with exact trap strings, logical
instruction counts, full memory, globals, and table state.

`just test` and `just verify` passed after the fixture was integrated. All
three browser replay oracles, all seven full-state workloads, Java 1.3,
CLDC-only compilation, classfile major 47, StackMap preverification, both
release JARs, cache checks, audio, storage, and MIDP gates remained green.
The counterless full-state artifact is
`2263114cb6ead98dd978f3852787370518726917ffd5fb5e76bf85d0e1e0272d`.

Target-47 confirmed the scratch prediction exactly: `checkedAddress` shrank
from 48 to 40 code bytes. Diagnostic `WasmInterpreter.class` shrank from
52,867 to 52,859 bytes; counterless from 52,751 to 52,743 bytes. Preverified
counterless class size shrank from 79,340 to 79,332 bytes. Diagnostic and
counterless `execute` remained 7,039 and 7,007 bytes respectively, both with
dense `tableswitch`. Station and base release JARs became 228,101 and 225,587
bytes, one byte smaller each. W4IR format and persistent heap are unchanged.

**Clean snapshot identity and native sanity.** The baseline snapshot is clean
commit `f4c824b1433ee831609caabe30bbce5627c50350`; the temporary candidate
snapshot is clean commit `d35a76baa31e77102c6a29d57246f496cfe3afb5`.
Their staged counterless phoneME artifacts are
`b9b388cf624fb8a7c65e0f22649bcc5d90175ca25c1a3464735e2138bc9a8a54`
and
`9e00523e860635b39ee43d96872c6ce5547a4f0b04b070e6cd14fd93c91be9cd`.
Their preverified interpreter-class hashes are
`ca4644d8ef04fdd8625f28ded3d2e434f3fb0af915ce8886f23db38a397e59c5`
and
`16e1f32ddbcd2b070b93bbdb286ffe50b485c600e659dacc31c26b9e48604509`.
A one-repetition native sanity run passed all 30 Rubido checkpoints and
matched all deterministic fields at 15,515,777 logical instructions.

**Native i686 phoneME A/B.** Eight balanced pairs used the complete 70-frame
Rubido browser route, counterless production-shaped artifacts, and the native
i686 no-JIT VM. Timer resolution was 14.286 us/frame. Every invocation passed
30 checkpoints and reported identical logical instructions and direct-branch
payload:

| Pair | Order           | Baseline us/frame | Candidate us/frame |
| ---: | --------------- | ----------------: | -----------------: |
|    0 | baseline first  |            72,628 |             71,757 |
|    1 | candidate first |            71,857 |             71,800 |
|    2 | baseline first  |            72,400 |             71,100 |
|    3 | candidate first |            71,800 |             71,671 |
|    4 | baseline first  |            71,671 |             71,485 |
|    5 | candidate first |            71,571 |             71,800 |
|    6 | baseline first  |            71,371 |             70,871 |
|    7 | candidate first |            72,157 |             71,757 |

The median paired delta is 293 us/frame, or **+0.407%**, with seven wins and
one loss. The positive sign is credible and the result is timer-resolved, but
the effect is only half the predeclared +0.8% acceptance floor. Waternet,
Untangle, and Plasma control timing cannot make the primary gate pass and was
therefore not spent. Raw invocations, pair CSV, receipts, and summary are under
`/tmp/w4me-njit005.325Jgx/evidence/` for the lifetime of this host session.

**Decision.** Reject and remove the candidate. The algebra, target bytecode
reduction, exact behavior, and small native phoneME improvement are all
confirmed, but a 0.407% median does not justify retaining a standalone helper
rewrite under the goal's conservative sub-percent policy. The focused oracle
is candidate-specific and will be removed with the implementation rather than
adding permanent test surface for a rejected optimization. No production
commit is made.

The production, fixture, and test-runner changes were removed exactly. The
restored stable tree then passed `just verify`: diagnostic `execute` returned
to 7,039 bytes, counterless `execute` returned to 7,007 bytes, release JARs
returned to 228,102 and 225,588 bytes, and all seven counterless full-state
workloads remained exact. The stable counterless artifact is again
`3378a78eacab9d4271adad5bedda954570e81f0673ddaafc3e167a7019d1bcfc`.

**Reconsideration condition if rejected:** only an independently measured
caller-inline form that removes the nested Java call frame, a separate
manual-guard specialization with materially higher dynamic coverage, or a
more precise physical-device measurement method.

### NJIT-006: direct imported-call W4IR opcode

**Status:** `rejected`.

**Hypothesis and source.** A validated direct WebAssembly `call` currently
enters the same 919-byte target-47 `callFunction` method used by defined
functions. On the successful numeric-import path it executes the range and
profiling prologue, loads a nullable `FunctionBody`, checks call-depth and
intrinsic conditions for defined functions, resolves the function type,
checks the argument stack, passes two Plasma-only gates, and only then reaches
the host import. Target-47 `javap` counts 85 JVM instructions, 13 branches,
19--20 `getfield` operations, three array loads, and the mandatory
`invokeinterface` on this import path after the outer `call` handler's own
`invokespecial`. The retained i686 phoneME C interpreter sends method calls
through its VM call machinery and does no JIT inlining.

The isolated candidate adds one `W4IR_DIRECT_HOST_CALL` opcode and lowers only
a statically decoded `call` whose function index is below the validated import
count. It must retain the original imported function index as its operand.
That identity is required for profiling, duplicate imports, string-dispatch
fallback, the validated function signature, and fail-closed cached-W4IR
checks. `call_indirect` remains unchanged because its table target and
signature traps are dynamic.

Three independent read-only reviews agreed that this is materially different
from the retained numeric-host-ID implementation. The older change only
replaced the `String.equals` chain inside host dispatch; its corrected paired
effects were +0.530% Waternet, -0.220% Rubido, and +0.196% Untangle and it was
not accepted as a speed claim. `git log -S` found no previous direct imported
call lowering.

**Narrow implementation.** Add original opcode `0x1033`, extend the dense
execution mapping by one slot, and change W4IR format 16/14 to 17/15. Reuse the
post-decode direct-call specialization pass that already recognizes numeric
intrinsics. The new opcode stays outside compact and trace execution, so the
outer dispatcher continues to charge and check its single logical instruction
before any host side effect.

The first safe prototype keeps the function index as the authoritative
metadata and delegates to one import-only helper shared by the specialized
opcode and the malformed/unspecialized direct-call fallback. The helper must:

1. reject an out-of-range or defined-function operand with `WasmTrap` before
   profiling, stack mutation, or host activity;
2. increment the same `functionCallCounts[functionIndex]` slot before argument
   underflow checking;
3. derive parameter and result counts from the validated
   `module.functionTypes[functionIndex]`, never from untrusted packed cache
   metadata;
4. leave arguments on `values` while calling the existing numeric or string
   `WasmHost.invoke` overload;
5. set `valueTop` to the argument base only after a successful host return,
   then use the existing `push(long)` behavior for one result;
6. propagate host traps without wrapping, rollback, hidden instruction charge,
   or fallthrough to the next guest instruction.

This deliberately rejects the faster-looking raw-`hostId` or packed
`hostId/arity/result` handler for the first experiment. RMS pages have
integrity checks but do not semantically revalidate every token, and
`Wasm4Runtime.invoke(int, ...)` indexes arguments immediately. Trusting forged
metadata could therefore expose Java array exceptions, the wrong host import,
or a changed stack effect. If the safe form fails only because its remaining
metadata work dominates, a separately recorded candidate may investigate
one-time cache validation and an inline trusted form.

**Dynamic selection evidence.** The retained exact seven-workload profile is
W4IR format 15, so it is historical selection evidence rather than the timing
artifact. Import counts were independently mapped from the current
cartridges. It records:

- Duck Maze: 16,991 direct host calls over 155 frames, 109.619 per frame and
  100% of dynamic direct calls;
- Waternet browser route: 14,476 over 94 frames, exactly 154 per frame and
  45.67% of direct calls; 14,368 are `blitSub`;
- Waternet idle: 780 over 60 frames, 13 per frame;
- Rubido: 2,134 over 70 frames, 30.486 per frame and 5.62% of direct calls;
- Untangle: 34,265 over 401 frames, 85.449 per frame and 64.76% of direct
  calls;
- Plasma Cube and the retained Game of Life frame: zero host calls.

Waternet is the primary game gate because it has the highest host-call rate
and is close enough to the 60-FPS budget for a small improvement to matter.
Duck Maze and Untangle are high-coverage controls; Rubido is a low-coverage
no-regression control. Static analysis predicts no persistent heap or W4IR
stride growth, one four-byte dense `tableswitch` slot, and roughly 100--160
bytes of handler/helper code. The current counterless `execute` is 7,007
bytes against the 7,800-byte ceiling. These estimates select the experiment;
they do not establish a speedup.

**Correctness and cache gates.**

- Cover all allowed WASM-4 import arities and both void and i32-result imports,
  duplicate import names, a defined direct call, and an imported
  `call_indirect` that remains opcode `0x11`.
- Exercise both numeric-ID and string fallback dispatch and assert exact
  import identity, argument base/count/order, lower-stack preservation, result
  placement, and profile counts.
- Sweep the budget immediately before the call; a denied instruction must
  produce no host call or side effect. A host trap must preserve its class and
  text, must not execute the following guest instruction, and must retain the
  baseline instruction count and stack-mutation order.
- Exercise an import called from the maximum legal defined-function depth; it
  must not consume an additional call frame.
- Compare resident build, new-format paged build/hit, page promotion, and exact
  streams. Reject and rebuild the previous format. Check checksum corruption
  and checksum-valid specialized tokens with negative, defined-function, and
  one-past-import operands; all must fail with `WasmTrap` before host or stack
  side effects.
- Preserve every existing invalid-import, indirect-table/type, lifecycle,
  full-state, framebuffer, audio, disk, trace, Java 1.3, CLDC, target-47,
  StackMap, dense-switch, release-JAR, and MIDP gate.

**Baseline identity.**

- source commit:
  `f4c824b1433ee831609caabe30bbce5627c50350`;
- stable counterless verification artifact:
  `3378a78eacab9d4271adad5bedda954570e81f0673ddaafc3e167a7019d1bcfc`;
- native i686 VM:
  `bb4866969747430bb619d139c75ab31982e8967aa0e2dcc3e278fcc7920839a2`;
- CLDC classes:
  `117820661071b411b3937e8c708a9581aa48ba06fd06e24eadb5ebbf2036afbe`;
- preverify:
  `4fe0d1b160ac7f18c4f7489917a1d868ff82df50842ca718729b098b25f2f50c`.

**Correctness and artifact results before timing.** The isolated
implementation lowers a decoded direct imported `call` to original W4IR
opcode `0x1033`, retains the import function index as its only operand, and
uses a guarded import-only helper. Unspecialized and indirect imported calls
retain the generic `callFunction` entry and share the same helper after the
dynamic target and type checks.

- `just test` and `just verify` passed. The new focused fixture covers 13
  specialized sites; direct imports with arities 1, 2, 3, 4, 6, and 9; void
  and i32 results; duplicate names; numeric-ID and string dispatch; a defined
  direct-call fallback; an imported `call_indirect`; lifecycle imports; exact
  budget boundaries; host traps; paged build/hit behavior; and malformed
  cached operands. It observed seven direct and one indirect host execution
  with exact arguments, stack placement, profile counts, and side effects.
- The seven-workload full-state matrix remained exact at W4IR format 17.
  Release JARs remained target-47, preverified, dense-`tableswitch`, and passed
  cartridge, MIDP, license, and diagnostic-exclusion checks.
- Diagnostic `execute` grew from 7,039 to 7,055 code bytes; counterless
  `execute` grew from 7,007 to 7,023. The new counterless helper is 222 code
  bytes. The unpreverified counterless `WasmInterpreter.class` grew 52,751 to
  53,041 bytes and `WasmModule.class` grew 34,723 to 34,818 bytes.
  Preverification changed those sizes from 79,340 to 79,689 and from 45,352
  to 45,464 bytes respectively. Release JARs grew by 208 bytes each, to
  228,310 and 225,796 bytes.
- No field, array, W4IR token, or metadata stride was added. Persistent runtime
  heap is unchanged; only the cache format version and opcode value change.
  The complete candidate counterless exactness artifact is
  `efd5ff52587a0915fa114a82388faa68a4ef4901467dec83e61029d24004aa52`.
- Clean temporary snapshots are baseline
  `f4c824b1433ee831609caabe30bbce5627c50350` and candidate
  `067017bd8119f8bef057bc37341df46ef683c00e`. Their Waternet-staged
  counterless tree hashes are respectively
  `d7ca20dcf808c12a33395ca144db5310e4685cd42688e6a4a726fc57d8982017`
  and
  `785e81dbe24fcfafd4c6c0bcaead9e2417a22519815b16c84e9727e8b0eec162`.
  The snapshots, bytecode dumps, and raw receipts are retained under
  `/tmp/w4me-njit006.xz5nyr/` for the lifetime of this host session.

**Planned commands and verdict.** Implement the focused fixture and smoke,
then run `just test` and `just verify`. Inspect target-47 `javap`, diagnostic
and counterless `execute` sizes, dense `tableswitch`, class/JAR sizes,
preverification, W4IR cache invalidation, and persistent heap. Create clean,
hash-bound baseline and candidate counterless snapshots. First prove exact
fields with one native phoneME sanity repetition, then run at least eight
balanced pairs on the complete Waternet route. Require at least +0.8% median,
at least six wins in eight pairs, exact checkpoints and counters, and no
resolved regression worse than -0.5% on Duck Maze, Untangle, or Rubido.
Reject without controls if Waternet is decisively below the floor. Record raw
pairs and receipt paths before retaining or removing the implementation.

**Native i686 phoneME A/B.** One clean sanity repetition for each artifact
matched every deterministic Waternet field after normalizing only the expected
W4IR format 16 versus 17. The primary gate then ran eight balanced pairs over
the complete route plus 60 extra frames, 153 frames per invocation. Every
invocation passed 17 checkpoints and reported exactly 3,521,339 logical
instructions, zero optional diagnostic dispatch counters, and identical
branch-fast metadata.

| Pair | Order           | Baseline us/frame | Candidate us/frame |
| ---: | --------------- | ----------------: | -----------------: |
|    0 | baseline first  |            20,281 |             20,398 |
|    1 | candidate first |            20,588 |             20,418 |
|    2 | baseline first  |            20,464 |             20,444 |
|    3 | candidate first |            20,215 |             20,274 |
|    4 | baseline first  |            20,450 |             20,692 |
|    5 | candidate first |            20,313 |             20,529 |
|    6 | baseline first  |            20,509 |             20,588 |
|    7 | candidate first |            20,300 |             20,254 |

The median paired delta was -69.0 us/frame, or **-0.339%**, with three wins
and five losses. Timer resolution was 6.536 us/frame, order was balanced, and
both source snapshots were clean. Raw outputs, the pairs CSV, and the paired
summary are retained under `/tmp/w4me-njit006.xz5nyr/evidence/`.

**Decision.** Reject and remove the implementation. The safe import-only
helper eliminated the unrelated defined-function prologue and preserved every
semantic gate, but it added a W4IR opcode, format bump, method, and artifact
growth for a measured Waternet regression. The primary gate is both below the
+0.8% floor and negative, so Duck Maze, Untangle, and Rubido control timing
cannot make the candidate acceptable and was not spent. Do not repeat this
safe metadata-derived helper. A future packed or inline host-call handler is a
materially different candidate only if it first validates cached metadata
once, remains fail-closed for malformed W4IR, and removes the extra Java
method frame that this candidate retained.

### NJIT-007: exception-backed `push` capacity guard

**Status:** `accepted`.

**Hypothesis and source.** Every successful generic `push(long)` first checks
`valueTop >= values.length`, then performs a `lastore` whose JVM semantics and
phoneME C handler independently perform the same array bounds check. A
target-47 Java 1.3 prototype shows 18 JVM instructions in the current
successful path and 12 in a `try`-guarded path. After phoneME field-access
quickening, the defensible target mechanism is about four fewer interpreted
handlers per successful push: two quickened field loads, `arraylength`, and
the compare branch. Java exception tables are metadata and phoneME searches
them only after a failing array access, so the valid path does not execute an
additional catch-dispatch bytecode.

This candidate does not remove runtime stack protection and does not trust the
Wasm validator or persisted W4IR. It replaces one explicit capacity check with
the JVM's mandatory `lastore` bounds check and converts only the resulting
high-side failure back to the existing
`WasmTrap("value stack exhausted")`. Raw removal of `push`, `pop`, or `peek`
guards is rejected: fresh Wasm is validated, but an RMS W4IR cache hit skips
semantic decode and stack validation, and the fixed 4,096-slot value stack is
shared across nested calls while the validator limits each function
independently.

Three independent read-only reviews selected `push` as the first isolated A/B.
`pop` would remove only three successful-path JVM instructions while growing
its method, and `peek` has low dynamic coverage. Combining them would hide
which guard paid. Compact handlers with their own inline guards are unchanged.
The old wrapper-only `popFirst`/`popSecond` experiment remains rejected and is
not repeated.

**Dynamic selection evidence.** A temporary counter-only tree at
`/tmp/w4me-njit007-counts.OPgwzb/` changed only three diagnostic counters,
their getters, helper-entry increments, and receipt output. It was compiled
with Java 1.3 and run under HotSpot only to count deterministic route
coverage; none of its wall times are evidence:

| Workload | Routed frames | `push` calls | Calls/frame | `pop` calls | `peek` calls |
| -------- | ------------: | -----------: | ----------: | ----------: | -----------: |
| Waternet |           153 |    2,132,141 |      13,935 |   1,742,746 |       80,839 |
| Rubido   |           129 |   16,421,546 |     127,299 |  14,727,036 |      337,538 |
| Untangle |           460 |      827,269 |       1,798 |     586,450 |       27,225 |

All route checkpoints and deterministic counters passed. The unmodified source
hashes used for selection were
`69657e2d937a85804ee07274e502fd06b1fa9ac38f2054b5da57f0676ffc28c3`
for `WasmInterpreter.java` and
`160d4d5f0c44b81e10ba1f2b62e0deb069184ba797b2685b903bc3055d1ccc1e`
for `PhoneMeRouteBench.java`. Waternet is the primary real-game gate because
it executes about 13,935 covered writes per frame and remains near the
60-FPS boundary. Rubido is a higher-coverage compute/game control; Untangle
is the low-coverage no-regression control.

**Affected file and exact mechanism.** Change only
`WasmInterpreter.push(long)`. The successful path is:

```java
try {
    values[valueTop++] = value;
    return;
} catch (ArrayIndexOutOfBoundsException failure) {
    if (valueTop > values.length || valueTop == Integer.MIN_VALUE) {
        valueTop--;
        throw new WasmTrap("value stack exhausted");
    }
    throw failure;
}
```

The post-increment occurs before `lastore`. For an old top in
`[values.length, Integer.MAX_VALUE - 1]`, the new top is above the array
length; decrement restores the exact old value before throwing the existing
Wasm trap. For `Integer.MAX_VALUE`, post-increment wraps to
`Integer.MIN_VALUE`; the explicit equality recognizes that case and decrement
again restores the old value. For a negative old top, the current method
allows `lastore` to throw Java `ArrayIndexOutOfBoundsException` after
incrementing the field. The candidate rethrows that same failure without
rollback, preserving even this unsupported private-field-corruption behavior.
No other array access or method call is inside the protected bytecode range.

The normal guest invariant remains `0 <= valueTop <= values.length`: reset
starts at zero; call argument bases, control entry, transfers, direct branches,
compact raw decrements, and compact raw increments validate their ranges
before assigning; RMS branch metadata is range-checked when loaded; cached
W4IR tokens cannot write `valueTop` directly; and hosts do not receive the
interpreter. The all-`int` failure handling above avoids relying on that
invariant for observable equivalence.

**Expected benefit and risks.** The Waternet selection count corresponds to
about 55,740 fewer quickened phoneME handler dispatches per frame; Rubido has
roughly nine times that coverage. This is a selection estimate only. The
candidate retains the `invokespecial` helper frame and 64-bit `lastore`, so the
real effect may remain below the +0.8% acceptance floor or change sign through
class layout. No field, array, allocation, W4IR token, cache format, or
persistent heap object is added. Expected artifact growth is only exception
metadata and constant-pool entries. Failure becomes deliberately slower
because phoneME must create or obtain an array exception, search the exception
table, and then create the same Wasm trap.

Correctness risks are a widened catch range, failure to restore the exact top
on full or integer-wrapped overflow, converting a negative-index Java
exception into a Wasm trap, changing trap text or order, classfile-47
preverification failure, whole-class layout regression, and accidentally
changing compact inline guards. The candidate must preserve instruction
budget and diagnostic ordering because `push` itself does not charge an
instruction.

**Baseline identity.**

- source commit:
  `f4c824b1433ee831609caabe30bbce5627c50350`;
- stable counterless verification artifact:
  `3378a78eacab9d4271adad5bedda954570e81f0673ddaafc3e167a7019d1bcfc`;
- station release JAR:
  `da61cf43640dd20a8c65c7cde6746d038c1cb428e0526a590b7ad7ef49d3920e`;
- base release JAR:
  `5ec791eb2a0a363da36c268f711a362014f5dad244dc56870c3c68b5d7d7a3dc`;
- native i686 VM:
  `bb4866969747430bb619d139c75ab31982e8967aa0e2dcc3e278fcc7920839a2`;
- CLDC classes:
  `117820661071b411b3937e8c708a9581aa48ba06fd06e24eadb5ebbf2036afbe`;
- preverify:
  `4fe0d1b160ac7f18c4f7489917a1d868ff82df50842ca718729b098b25f2f50c`.

**Pre-implementation proof.** The exact all-`int` scratch candidate is under
`/tmp/w4me-njit007-exact-probe/`. It compiled with
`javac -source 1.3 -target 1.3 -bootclasspath
.local/phoneme/classes.zip`, passed `.local/phoneme/preverify`, produced
classfile major 47 with a CLDC StackMap, and passed natively on the i686
phoneME VM for top values `0`, `length - 1`, `length`, `length + 1`,
`Integer.MAX_VALUE`, `-1`, and `Integer.MIN_VALUE`.
`javap -c -p -verbose` shows a 0--17 protected range ending immediately after
`lastore`, no successful-path `if*`, and a catch of exactly
`ArrayIndexOutOfBoundsException`.

**Planned correctness and artifact gates.**

- Add a focused package-level helper smoke that compares the current oracle
  and candidate for valid writes, full-stack exhaustion, high and negative
  synthetic tops, exact throwable class/message, and post-failure top.
- Exercise sequential writes at the capacity edge, nested caller/callee
  exhaustion, and checksum-valid malformed cached W4IR that pushes past
  capacity. Preserve checksum failure, budget-before-effect, host-failure, and
  compact inline-guard behavior.
- Run `just test` and `just verify`, including all seven full-state workloads,
  resident/paged/promoted cache paths, exact traps and counters, both release
  JARs, MIDP gates, Java 1.3, CLDC bootclasspath, target-47, and preverification.
- Inspect unpreverified and preverified target-47 `push` bytecode, exception
  range, StackMap, method/class/JAR sizes, diagnostic and counterless
  `execute` headroom, W4IR format, and persistent heap delta.

**Planned timing and acceptance.** Build clean, hash-bound counterless baseline
and candidate source snapshots with no runtime selection branch. First run
one native i686 phoneME checkpoint sanity repetition on Waternet, Rubido, and
Untangle and require every deterministic field to match. Then run at least
eight balanced pairs on the complete Waternet route plus 60 frames. Accept
only with at least +0.8% median paired improvement, at least six wins in eight
pairs, timer-resolved effects, and exact checkpoints and counters. If Waternet
passes, run at least eight Rubido and Untangle no-regression pairs and reject
any resolved regression worse than -0.5%. Static bytecode savings, HotSpot
counts, or a Rubido-only improvement cannot accept the candidate.

**Correctness and artifact results.** The retained source passed `just test`
and `just verify`, including the focused seven-state private-helper oracle,
validated nested-call exhaustion, checksum-valid paged and promoted malformed
W4IR overflow, all seven full-state workloads, release-JAR validation, MIDP
integration gates, and counterless exactness. The focused smoke reports:

```text
PASS value-stack-push-guard helper-edges=7 nested-overflow=PASS cached-paged-promoted=PASS
```

The counterless exactness artifact is
`3f05ae2e49c1b378760030179d2a813d95e8b487249e78485d1c592836bdb904`.
Its seven full-state routes are exact, with W4IR format 16 unchanged. Both
diagnostic and timed builds remain below the `execute` gate at 7,039 and 7,007
bytes respectively, unchanged from the baseline. No field, array, or
persistent object was added, so the persistent heap delta is zero.

Target-47 `javap` on the clean timed artifacts confirms the intended shape.
The baseline `push` is 40 code bytes and its successful path contains 18 JVM
instructions, including the explicit capacity branch. The candidate is 63
code bytes including its cold handler, while its 0--17 successful path contains
12 JVM instructions and no `if*`. Its exception table catches only
`ArrayIndexOutOfBoundsException` over bytecodes 0--17, ending immediately after
`lastore`; the preverified class contains the corresponding CLDC StackMap.
The unpreverified `WasmInterpreter.class` grows from 52,751 to 52,840 bytes
(+89), and the preverified class from 79,340 to 79,458 (+118). The station and
base release JARs grow by 69 bytes each, from 228,102/225,588 to
228,171/225,657. Their retained hashes are
`282e5f7154c06e9fda1816ac647141b8c8f3859ffd6661ece520ce5429581a31`
and
`fc732c49593d628eb1fb83cb7805c68f5a473839077e6fdd69387729a622431a`.

**Clean timing artifacts.** The native window used two clean local snapshots
under `/tmp/w4me-njit007.zQhTOP/`. The baseline is commit
`f4c824b1433ee831609caabe30bbce5627c50350`; the isolated candidate snapshot
is `4ea74aff7b1592324fa201c0acf6ac66239c37f7`. The only production difference
between them is `WasmInterpreter.push(long)`. Their counterless preverified
artifact hashes are:

- baseline:
  `f16654bea8d953f3f378f443e93c1625b1abdd927748cf96ae7e42dd037978ce`;
- candidate:
  `c0ce61e7731a458eb72e44c9d260f2391ff29d5eaf9375af33b7cf416cc05754`.

Both receipts report `source-dirty=no`, Java source/target 1.3, the baseline VM,
CLDC, and preverify hashes above, counterless timed configuration, W4IR format
16, and exact checkpoint/counter fields. One preliminary invocation with
`--extra-frames 0` was invalid because one final checkpoint remained
unconsumed; it was discarded before timing. The required `--extra-frames 60`
sanity runs passed on Waternet, Rubido, and Untangle for both artifacts.

**Native i686 phoneME paired measurements.** Every row is a full route plus 60
frames under `=HeapCapacity64M`; order alternates within each eight-pair set.
All deterministic receipt prefixes match exactly. The timer is
`System.currentTimeMillis`; per-frame resolution is reported by the paired
statistics.

Waternet raw pairs:

| Pair | Order           | Baseline us/frame | Candidate us/frame |
| ---: | --------------- | ----------------: | -----------------: |
|    0 | baseline first  |            20,248 |             20,084 |
|    1 | candidate first |            20,522 |             20,281 |
|    2 | baseline first  |            20,418 |             20,307 |
|    3 | candidate first |            20,490 |             20,300 |
|    4 | baseline first  |            20,424 |             20,333 |
|    5 | candidate first |            20,588 |             20,209 |
|    6 | baseline first  |            20,522 |             20,222 |
|    7 | candidate first |            20,405 |             20,274 |

Result: median delta 177.0 us/frame, **+0.869%**, 8 wins, 0 losses,
6.536 us/frame timer resolution.

Rubido raw pairs:

| Pair | Order           | Baseline us/frame | Candidate us/frame |
| ---: | --------------- | ----------------: | -----------------: |
|    0 | baseline first  |           103,573 |            101,914 |
|    1 | candidate first |           103,511 |            102,519 |
|    2 | baseline first  |           103,930 |            102,031 |
|    3 | candidate first |           103,829 |            102,217 |
|    4 | baseline first  |           102,899 |            102,069 |
|    5 | candidate first |           103,441 |            102,333 |
|    6 | baseline first  |           103,426 |            101,852 |
|    7 | candidate first |           103,457 |            102,310 |

Result: median delta 1,360.5 us/frame, **+1.315%**, 8 wins, 0 losses,
7.752 us/frame timer resolution.

Untangle raw pairs:

| Pair | Order           | Baseline us/frame | Candidate us/frame |
| ---: | --------------- | ----------------: | -----------------: |
|    0 | baseline first  |             4,773 |              4,723 |
|    1 | candidate first |             4,750 |              4,700 |
|    2 | baseline first  |             4,763 |              4,767 |
|    3 | candidate first |             4,745 |              4,760 |
|    4 | baseline first  |             4,752 |              4,734 |
|    5 | candidate first |             4,786 |              4,721 |
|    6 | baseline first  |             4,771 |              4,765 |
|    7 | candidate first |             4,797 |              4,765 |

Result: median delta 25.0 us/frame, **+0.523%**, 6 wins, 2 losses,
2.174 us/frame timer resolution.

Raw files and paired summaries are retained under
`/tmp/w4me-njit007.zQhTOP/evidence/`. The exact commands were:

```sh
PHONEME_HOME=.local/phoneme tools/phoneme/run.sh bench \
  waternet rubido untangle --candidate counterless --reps 1 \
  --extra-frames 60
bash /tmp/w4me-njit007.zQhTOP/run-pairs.sh waternet 8 60
bash /tmp/w4me-njit007.zQhTOP/run-pairs.sh rubido 8 60
bash /tmp/w4me-njit007.zQhTOP/run-pairs.sh untangle 8 60
```

**Decision.** Accepted. Waternet exceeds the predeclared +0.8% floor with
8/8 wins; Rubido independently improves by +1.315%; Untangle has no
regression and instead shows a timer-resolved +0.523% median. Exact state,
trap behavior, Java 1.3/CLDC compatibility, bytecode headroom, cache paths,
release artifacts, and persistent memory all pass. Reconsider only if a
physical CLDC/MIDP device exposes materially different exception-table or
array-bounds behavior, or if a future whole-class layout change reverses the
paired native result.

### NJIT-012: short-local parameter layout for `executeCompactFused`

**Status:** `rejected`.

**Hypothesis and source.** The private compact-fusion helper currently has
descriptor `(IIII[J)I`. Its target-47 parameter slots are `opcode=1`,
`instruction=2`, `operand=3`, `auxiliary=4`, and `locals=5`. The phoneME
portable-C interpreter uses 33-byte handlers for the short
`aload_1..aload_3` and `iload_1..iload_3` bytecodes, but 51-byte handlers for
generic `aload` and `iload`; the generic form also fetches an index operand,
advances the bytecode PC by two, scales the index, and saves a native register.
Moving the dynamically hot `locals` reference into slot 2 and the colder
`instruction` value into slot 5 should therefore reduce work inside every
covered fused handler without changing the number of Java calls or JVM
dispatches.

This is materially different from rejected `NJIT-004`. That candidate
reordered parameters of the outer `execute` method and measured +0.481% on
Waternet, where compact execution was not active. This candidate affects only
`executeCompactFused`, has zero Waternet and Untangle coverage, and uses Rubido
as its primary workload. The earlier result supports the handler-cost
mechanism but also warns that this candidate may remain below the +0.8%
acceptance floor.

**Independent selection evidence.** Three read-only reviews confirmed a sole
private caller, category-1 arguments, no recursion or reflective entry, zero
format or heap effect, and the following target-47 load counts:

| Parameter     | Current slot | Static loads |
| ------------- | -----------: | -----------: |
| `opcode`      |            1 |            3 |
| `instruction` |            2 |           13 |
| `operand`     |            3 |           49 |
| `auxiliary`   |            4 |           33 |
| `locals`      |            5 |           54 |

Deterministic route instrumentation measured 1,236,449 compact-fused calls on
Rubido over 70 frames. The selected swap removes 1,843,402 generic parameter
loads there, or 26,334 per frame. Generic Plasma executes 34,893,596 fused
calls over 60 frames and removes 34,421,052 generic loads. Game of Life
removes 786,740 in its measured frame. Waternet, Untangle, and Duck Maze
execute this helper zero times under the retained activation policy.

Two reviews recommend an isolated native A/B because the change is cheap and
the dynamic coverage is exact. One adversarial review recommends skipping it
because both `NJIT-004` and the broader compact-counter `NJIT-001` remained
below the acceptance floor. The conflict is resolved by a primary Rubido
native falsification rather than by treating bytecode counts as performance
proof.

**Affected files and mechanism.** Change only the sole call and declaration
in `WasmInterpreter` to:

```java
executeCompactFused(
        int opcode,
        long[] locals,
        int operand,
        int auxiliary,
        int instruction)
```

The swap keeps the switch selector in slot 1 and the hot operand in slot 3,
moves 54 `locals` loads from generic `aload 5` to `aload_2`, and moves only 13
`instruction` loads from `iload_2` to generic `iload 5`. A target-47 scratch
build reduced generic parameter loads from 87 to 46, helper code length from
1,376 to 1,335 bytes, and `WasmInterpreter.class` from 52,956 to 52,915 bytes.
The `tableswitch`, `max_stack=6`, `locals=49`, caller instruction lengths, and
persistent heap are unchanged. A temporary candidate already passed
preverification and seven-workload full-state differential checks, but those
results are selection evidence rather than the production gate.

**Risks.** Four `int` arguments can be mismatched between the call and
declaration while still compiling. Whole-class layout can outweigh the
mechanical bytecode saving, and the effect may be too small for the
conservative floor. No W4IR, RMS cache, trap, instruction-accounting, call,
stack, or memory semantics should change. The full exact-state and cached-path
matrix must nevertheless prove the argument mapping.

**Baseline identity.**

- source commit:
  `e65c2e61e07db2f230502840ec54ca7590411332`;
- stable counterless verification artifact:
  `3f05ae2e49c1b378760030179d2a813d95e8b487249e78485d1c592836bdb904`;
- station release JAR:
  `282e5f7154c06e9fda1816ac647141b8c8f3859ffd6661ece520ce5429581a31`;
- base release JAR:
  `fc732c49593d628eb1fb83cb7805c68f5a473839077e6fdd69387729a622431a`;
- native i686 VM:
  `bb4866969747430bb619d139c75ab31982e8967aa0e2dcc3e278fcc7920839a2`;
- CLDC classes:
  `117820661071b411b3937e8c708a9581aa48ba06fd06e24eadb5ebbf2036afbe`;
- preverify:
  `4fe0d1b160ac7f18c4f7489917a1d868ff82df50842ca718729b098b25f2f50c`.

**Planned commands, workloads, and acceptance.** Compile with Java source and
target 1.3 against the phoneME CLDC bootclasspath, inspect target-47 and
preverified bytecode with `javap`, and run `just test` plus `just verify`.
Require exact seven-workload state, resident/paged/promoted cache behavior,
traps, budgets, release JAR integrity, preverification, and unchanged W4IR
format and persistent heap. Produce clean hash-bound counterless baseline and
candidate snapshots and first require matching native checkpoint and counter
fields.

The authoritative primary gate is eight balanced native i686 phoneME Rubido
pairs over the complete route plus 60 frames. Accept only at a median paired
improvement of at least +0.8%, at least six wins in eight pairs, and exact
deterministic outputs. If it passes, run Waternet and Untangle no-regression
controls and reject a resolved regression worse than -0.5%; generic Plasma is
only a high-coverage mechanism control. If Rubido decisively fails, do not
spend the controls. Raw pair values, artifact hashes, timer resolution, code
size, and final verdict must be recorded before removing or retaining the
candidate.

**Correctness and artifact results.** The isolated candidate passed
`just test` and `just verify`. The full seven-workload state matrix, cached and
resident W4IR paths, exact traps and budgets, both release JARs, Java 1.3,
CLDC API lint, classfile 47, preverification, and the release-integrity gates
all passed. W4IR format 16, diagnostic `execute=7,039`, counterless
`execute=7,007`, and persistent heap use were unchanged. The candidate
counterless verification artifact was
`4b9583484b7232477177fbac97bfd83fde19f4bbe3ea6d8381d6e40f1ec83512`.

The unpreverified counterless interpreter class shrank from 52,956 to 52,915
bytes and the preverified class from 79,458 to 79,417 bytes. Their preverified
class hashes were
`2259d3d6f20205993ecf2e33d2215171c2a38c8a377bdb6c28d41ae719a7c1ad`
and
`5bf23d877f81d639b74ee216a632b38d805cfec4ede1fd37bbc0bd84073dd008`.
The candidate station and base release JARs were 228,177 and 225,663 bytes,
six bytes larger than the retained JARs because of ZIP compression, with
hashes
`a76bd599c36136cd1acca2ed3605d1a859bb00d2be0c9cbad65a594d1df86f6f`
and
`7237bbd4f40e6b040d4a0b1d724b334a6e107a90cbe960af0f49586c537ff3c4`.

**Clean timing artifacts and sanity.** The clean snapshots were baseline
commit `e65c2e61e07db2f230502840ec54ca7590411332` and temporary candidate
commit `1b21063fe4848db299f94c1d260e5c3bdae4f909`. Their complete phoneME
artifact hashes were
`11fc13742f33e6d316df71493f0e2c04b4aa3a76e0cacea86d5705fe429a683c`
and
`db4a2e4d36654cd91bcdd7c4febc2696f79cd17e9c93af1ac63693dff1cbdde1`;
their counterless preverified artifact hashes were
`c0ce61e7731a458eb72e44c9d260f2391ff29d5eaf9375af33b7cf416cc05754`
and
`b26ff54bd981e48acc6d389bc6c3e3584522c84c5af23fe8698f24be0832e4d6`.
Both receipts report clean source, Java source/target 1.3, the baseline VM,
CLDC, and preverify hashes, W4IR format 16, and a 64-MiB heap. One native
sanity repetition on Waternet, Rubido, and Untangle passed every checkpoint
and matched all deterministic fields between artifacts.

**Native i686 phoneME A/B.** Eight balanced Rubido pairs used the complete
route plus 60 frames, 129 total frames per invocation. Every deterministic
receipt prefix was identical:

| Pair | Order           | Baseline us/frame | Candidate us/frame |
| ---: | --------------- | ----------------: | -----------------: |
|    0 | baseline first  |           101,992 |            102,147 |
|    1 | candidate first |           102,651 |            102,527 |
|    2 | baseline first  |           102,829 |            102,558 |
|    3 | candidate first |           102,488 |            102,302 |
|    4 | baseline first  |           102,170 |            102,046 |
|    5 | candidate first |           102,751 |            102,511 |
|    6 | baseline first  |           102,434 |            102,581 |
|    7 | candidate first |           102,286 |            102,232 |

The median paired delta was 124.0 us/frame, or **+0.121%**, with six wins and
two losses. Timer resolution was 7.752 us/frame, order was balanced, and the
source snapshots were clean. Raw receipts and statistics are under
`/tmp/w4me-njit012.rInVJH/evidence/` for the lifetime of this host session.
The exact primary command was:

```sh
bash /tmp/w4me-njit012.rInVJH/run-pairs.sh rubido 8 60
```

**Decision.** Reject and remove the candidate. The short-local handler
mechanism, expected 41-byte class reduction, exact semantics, and positive
wall-time sign are all confirmed, but +0.121% is 6.6 times below the
predeclared +0.8% floor and does not justify production source-order churn.
The primary gate decisively failed, so zero-coverage Waternet and Untangle
controls and the synthetic Plasma control were not spent. No production
commit is made.

Reconsider only with a physical-device measurement method that resolves and
values sub-percent effects, a combined layout change that removes JVM
dispatches rather than merely shortening handlers, or a future compact-tier
policy that greatly increases real-game coverage. The two production argument
order changes were removed exactly; the durable ledger result remains.

### NJIT-009: transform-free `blitSub` geometry loop

**Status:** `accepted`.

**Hypothesis and source.** WASM-4 uses flag bit 0 for 2-bpp sprites and bits
1, 2, and 3 for horizontal flip, vertical flip, and rotation. Calls satisfying
`(flags & 0x0e) == 0` therefore use the same unrotated, unflipped geometry for
both 1-bpp and 2-bpp sources. The current general inner loop nevertheless
performs four transform-dependent coordinate selections and recomputes
`sampledY * sourceStride` for every pixel. A transform-free loop can compute
source and destination row bases once per row and advance `targetX` and
`bitIndex` linearly.

This is the standard scanline specialization used by software rasterizers,
but the first candidate deliberately retains the existing packed-pixel decode,
draw-color transparency, and `drawPoint` call. It tests only geometry
specialization; framebuffer read-modify-write inlining, packed-byte reuse, and
transform-specific loops remain separate future candidates. Repository and
OpenSpec history contain no prior transform-free `blit` or `blitSub`
implementation or native A/B.

**Dynamic selection evidence.** A temporary instrumentation-only runtime under
`/tmp/w4me-njit009-counts.AXafZl/` counted calls, requested pixels, and clipped
pixels by the low four flag bits while executing the exact seven-workload
corpus. The instrumented full-state matrix remained exact. Its artifact is
`1f4a29f5dfaade366436ee210ab5540e4a512ecd92fb4b044b6d71d0747ce6ad`;
the complete report hash is
`b1b3aa269275254ef8128941694be5853a21fee61ad1c5f41ccb03aa2d19b7f2`
and the extracted summary hash is
`c0f50c80a43b792970c6fedf0067e7979761c259c41bc693f493c671a485fe17`.
No instrumentation is added to production.

| Workload and route           | Plain calls | Plain clipped pixels |  Per route frame |
| ---------------------------- | ----------: | -------------------: | ---------------: |
| Waternet browser, 94 frames  |      14,368 |            1,899,520 | 20,207.66 pixels |
| Rubido browser, 70 frames    |       1,631 |              715,347 | 10,219.24 pixels |
| Duck Maze, 155 frames        |         157 |               40,192 |    259.30 pixels |
| Untangle browser, 401 frames |         368 |                7,248 |     18.08 pixels |

Waternet and Rubido use only transform-free flags in the recorded routes.
Untangle is the fallback control: 25,964 of its 26,332 total calls use
transforms, predominantly rotation, so it exposes the cost and exactness of
the additional guard while barely exercising the new loop. The native
phoneME timer includes `beginFrame`, guest `update`, and all runtime host
drawing calls, so it measures this rasterization. It does not include MIDP
framebuffer conversion, `drawRGB`, or `flushGraphics`.

Static target-47 inspection of the current loop confirms a general pixel body
from bytecode offsets 303 through 489. It evaluates rotate twice, flip-X once,
flip-Y once, multiplies the source row, selects 1-bpp versus 2-bpp, and invokes
`drawPoint` for every nontransparent pixel. The planned specialization removes
about 22 JVM bytecode dispatches from each plain pixel's geometry while
retaining the decode and destination helper. At Waternet coverage that is
roughly 445,000 fewer interpreted Java bytecodes per route frame. This is a
mechanism estimate, not a speedup claim.

**Affected files and isolated mechanism.** In `Wasm4Runtime.blitSub`, preserve
all current size, source-geometry, 64-bit extent, range, and clipping checks in
their current order. After clipping and `DRAW_COLORS` load, branch once on
`(flags & 0x0e) == 0`. The plain loop computes:

- `targetY = destinationY + yIndex` once per row;
- `targetX = destinationX + clipXMinimum` once per row;
- `bitIndex = (sourceY + yIndex) * sourceStride + sourceX +
clipXMinimum` once per row;
- the unchanged packed source color, draw-color lookup, transparency check,
  and `drawPoint`, followed by linear increments.

The existing general loop remains byte-for-byte semantically responsible for
all flipped or rotated calls. A focused differential smoke will compare the
optimized host call against an independent copy of the original scalar
algorithm across all flags 0 through 15, both bpp modes, transparent and
opaque draw-color mappings, clipping on every edge, nonzero source origins and
strides, unknown high flag bits, extreme destination coordinates, and invalid
source geometry. It must prove exact framebuffer bytes and exact trap class
and text.

**Expected benefit and risks.** Waternet is the primary real-game judge because
it is near the 60-FPS budget and executes the highest plain pixel count.
Rubido is a high-coverage no-regression control. Untangle proves the transformed
fallback and guard cost. Expected benefit is a multi-percent reduction in
runtime rasterization work with no persistent allocation, W4IR/cache change,
or interpreter bytecode-headroom effect. Risks are a clipped source/destination
off-by-one, changing validation or trap order, mishandling 2-bpp bit indices,
changing transparency, a general-loop regression, runtime class/JAR growth,
and a wall-time effect smaller than the static dispatch reduction suggests.

**Baseline identity.**

- source commit:
  `e65c2e61e07db2f230502840ec54ca7590411332`;
- stable counterless verification artifact:
  `3f05ae2e49c1b378760030179d2a813d95e8b487249e78485d1c592836bdb904`;
- station release JAR:
  `282e5f7154c06e9fda1816ac647141b8c8f3859ffd6661ece520ce5429581a31`;
- base release JAR:
  `fc732c49593d628eb1fb83cb7805c68f5a473839077e6fdd69387729a622431a`;
- native i686 VM:
  `bb4866969747430bb619d139c75ab31982e8967aa0e2dcc3e278fcc7920839a2`;
- CLDC classes:
  `117820661071b411b3937e8c708a9581aa48ba06fd06e24eadb5ebbf2036afbe`;
- preverify:
  `4fe0d1b160ac7f18c4f7489917a1d868ff82df50842ca718729b098b25f2f50c`.

**Planned gates and acceptance.** Run the focused differential, `just test`,
and `just verify`; require Java source/target 1.3, CLDC API lint, classfile 47,
preverification, exact seven-workload state, every replay framebuffer
checkpoint, unchanged W4IR/cache/trap/budget behavior, release JAR integrity,
zero persistent heap growth, and recorded runtime class/method/JAR deltas.
Build clean hash-bound counterless baseline and candidate snapshots and require
matching deterministic native receipt fields before timing.

Run at least eight balanced native i686 phoneME pairs on the complete Waternet
route plus 60 frames. Accept only at a median paired improvement of at least
+0.8% and at least six wins in eight pairs. If it passes, run eight-pair
Rubido and Untangle controls and reject a resolved regression worse than
-0.5%. KEmulator and physical-device rendering remain later integration gates,
but their JIT wall time cannot accept this pure Java candidate. Record all raw
pairs, hashes, timer resolution, size effects, and the final verdict before
retaining or removing the code.

**Correctness and artifact results.** The implementation adds only the plain
geometry loop and the focused `BlitPlainGeometrySmoke`; the transformed loop
and `drawPoint` remain unchanged. The smoke compared 518 independent cases
covering flags 0 through 15 plus unknown high bits, both bpp modes, clipping on
all sides, source/framebuffer overlap, transparency, `blit` delegation, and
exact trap/rollback behavior. `just test` and `just verify` passed, including
the seven-workload full-state matrix, all framebuffer checkpoints, Java
source/target 1.3, CLDC lint, classfile 47, preverification, release checks,
counterless exactness, cache, instruction budget, and persistent-memory gates.
The candidate counterless exactness artifact is
`7f09e54dab05a01dc4c11b4c0c4f219c982d9e3c355f1240a5d943854e1a1885`.
`WasmInterpreter.execute` remains 7,039 bytes diagnostic and 7,007 bytes
counterless.

The unpreverified `Wasm4Runtime.class` grows from 16,115 to 16,376 bytes
(+261); its preverified form grows from 21,691 to 22,255 bytes (+564).
`blitSub` grows from approximately 502 to 679 target-47 bytecode bytes and from
280 to 384 disassembled instructions. The station/base release JARs grow from
228,171/225,657 to 228,395/225,881 bytes (+224 each); their candidate hashes
are `d9422ce2cceee5d6ea525911829657f4210702aa6f7b6f46ae66a01a181d50aa`
and `c41f62be7c9773e34d174b19580295d392443301a4922aeb33e564b58015f35c`.
No field, allocation, cache, W4IR, or persistent-heap delta is introduced.

Clean snapshots under `/tmp/w4me-njit009.E4DIZE/` used baseline commit
`e65c2e61e07db2f230502840ec54ca7590411332` and an experiment commit containing
only `Wasm4Runtime.java`; the production source SHA-256 was
`76af888f1aa1e95f2f5257732c2b4befe88a9bee2bbcd6cab62f624ec8c0c5a3`.
The counterless phoneME artifacts were
`c0ce61e7731a458eb72e44c9d260f2391ff29d5eaf9375af33b7cf416cc05754`
baseline and
`61b1fd457d4fa331093eb23620529f8b62226ab2c62a3954c3928f1d3aa9fbae`
candidate. The build receipts additionally bind their complete build artifacts
as `11fc13742f33e6d316df71493f0e2c04b4aa3a76e0cacea86d5705fe429a683c`
and `26a2ae617763e6caa3ad807014a4faf3fc459ff5bb593077b73ab70933d68adb`.
Every timed pair matched frames, checkpoints, logical instructions, disabled
diagnostic counters, branch metadata, W4IR format, and all other deterministic
receipt fields.

The exact command used to build each clean artifact was:

```sh
PHONEME_HOME=./.local/phoneme \
  ./tools/phoneme/run.sh bench waternet rubido untangle \
  --candidate counterless --reps 1 --extra-frames 60
```

The paired runner invoked `PhoneMeRouteBench <cart> optimized 60 1 counterless
<sample>` directly on the native i686 VM, alternating baseline-first and
candidate-first. Raw results in microseconds per frame were:

| Pair |    Waternet B/C |        Rubido B/C |  Untangle B/C |
| ---: | --------------: | ----------------: | ------------: |
|    0 | 20,294 / 18,954 | 102,131 / 101,496 | 4,734 / 4,700 |
|    1 | 20,202 / 19,300 | 102,534 / 101,689 | 4,710 / 4,741 |
|    2 | 20,032 / 19,352 | 102,798 / 102,263 | 4,747 / 4,743 |
|    3 | 20,209 / 19,287 | 102,302 / 101,829 | 4,745 / 4,726 |
|    4 | 20,267 / 19,176 | 102,286 / 101,620 | 4,821 / 4,773 |
|    5 | 20,411 / 19,209 | 102,232 / 102,240 | 4,802 / 4,878 |
|    6 | 20,254 / 19,372 | 102,364 / 101,806 | 4,706 / 4,754 |
|    7 | 20,294 / 19,333 | 102,248 / 102,054 | 4,752 / 4,773 |
|    8 |               — |                 — | 4,721 / 4,797 |
|    9 |               — |                 — | 4,723 / 4,756 |
|   10 |               — |                 — | 4,756 / 4,771 |
|   11 |               — |                 — | 4,802 / 4,728 |
|   12 |               — |                 — | 4,710 / 4,804 |
|   13 |               — |                 — | 4,754 / 4,782 |
|   14 |               — |                 — | 4,765 / 4,763 |
|   15 |               — |                 — | 4,782 / 4,804 |

Waternet measured +4.649% median (941.5 us/frame, 8/8 wins; timer resolution
6.536 us/frame), comfortably above the predeclared +0.8% and 6/8 gate. Rubido
measured +0.533% (546.5 us/frame, 7/8; resolution 7.752 us/frame). The first
eight Untangle pairs landed at -0.486%, too close to the -0.5% rejection line,
so the control was conservatively extended to 16 pairs. Its final median was
-0.451% (-21.5 us/frame, 6/16; resolution 2.174 us/frame), inside the
predeclared no-regression limit and consistent with the expected one-guard
fallback cost.

KEmulator integration also passed the exact Waternet 94-frame, Rubido
70-frame, and Untangle 401-frame routes. The first Untangle wrapper invocation
returned `java.io.IOException: Bad file descriptor` after its worker log had
already reached the exact final checkpoint; after stopping the stale session,
an immediate clean repeat produced the normal wrapper PASS. This was an
infrastructure failure, not a cartridge or framebuffer mismatch.

**Verdict:** accepted. The isolated scanline geometry specialization produces
a repeatable 4.649% improvement on the high-coverage Waternet route, slightly
improves Rubido, remains within the predefined transformed-fallback control
limit, preserves exact behavior, and has a small fixed code-size cost with no
persistent-memory cost.

### NJIT-014: inline plain-blit framebuffer read-modify-write

**Status:** `accepted`.

**Hypothesis and source.** The accepted NJIT-009 loop still invokes the private
`drawPoint(byte[], int, int, int)` helper for every nontransparent plain-blit
pixel. Target-47 `javap` shows a 57-byte helper plus an `invokespecial` at
`blitSub` offset 436. The helper computes the packed framebuffer byte, shift,
mask, read-modify-write, and returns. phoneME quickens the call site but still
executes its `fast_invokespecial`, frame entry, and return machinery on every
draw. The byte-identical phoneME source/disassembly study attributes roughly
135 i686 instructions to this fixed call/return path, while its native
microbench found about 36 ns saved by inlining one representative small
two-argument helper. That magnitude is shape- and layout-sensitive and is only
motivation for a route A/B, not evidence of a W4ME speedup.

A temporary instrumentation-only profile under
`/tmp/w4me-njit014-profile.mZXvAW/` counted plain pixels, opaque destination
writes, and bpp mode on the exact corpus. It preserved the full seven-workload
state matrix. The artifact is
`7e25249ea4a8d4a0c14d04e1ddfe2aa0e437dfcd673a65550d875a965d153dce`;
the complete report hash is
`d903835056b5b061e8daab389cacc1dd804eac9a96d189fc435b543e8bc0c378`
and the extracted summary hash is
`015ef128df36581e62ea0d84515d9eacc9f285302da1e56034d9dc56f497e4b1`.
No counters or getters are added to production.

| Workload and route           | Plain pixels | Opaque draws | Opaque draws/frame |
| ---------------------------- | -----------: | -----------: | -----------------: |
| Waternet browser, 94 frames  |    1,899,520 |    1,795,296 |          19,098.89 |
| Rubido browser, 70 frames    |      715,347 |      583,629 |           8,337.56 |
| Duck Maze, 155 frames        |       40,192 |       40,192 |             259.30 |
| Untangle browser, 401 frames |        7,248 |        4,727 |              11.79 |

Waternet writes 94.51% of its plain pixels and Rubido writes 81.59%, so this
candidate removes about 19,099 and 8,338 Java calls per frame respectively.
Untangle remains the transformed-path control and has negligible dynamic
coverage. A naive multiplication of the helper microbench by Waternet coverage
suggests a sub-millisecond opportunity, but the real inlined body uses
high-numbered locals in the already large `blitSub`; only native paired timing
can determine whether call removal outweighs that local-layout cost.

An independent read-only target-47 prototype under
`/tmp/w4me-njit014-research.WYfkOz/` recommends the same isolated candidate
with only two new locals, `address` and `shift`. Its `blitSub` grows from about
679 to 719 bytes and from 384 to 413 disassembled instructions; the
unpreverified/preverified runtime classes grow from 16,376/22,255 to
16,424/22,303 bytes. `max_stack=7`, `max_locals=37`, and classfile major 47
remain unchanged. The opaque-pixel path falls from 10 caller instructions plus
40 helper instructions to 39 inlined instructions: 11 fewer Java bytecode
dispatches as well as no call-frame entry or return. A four-local translation
was rejected before implementation because it raised `max_locals` to 39 for no
semantic benefit.

**Affected files and isolated mechanism.** Change only the nontransparent
branch of the already accepted `(flags & 14) == 0` loop. Replace
`drawPoint(memory, (drawColor - 1) & 3, targetX, targetY)` with the exact helper
body:

- `address = FRAMEBUFFER + ((WIDTH * targetY + targetX) >> 2)`;
- `shift = (targetX & 3) << 1`;
- the same byte read-modify-write with `((drawColor - 1) & 3)` and
  `~(3 << shift)`.

Do not split the 1-bpp and 2-bpp loops, cache packed source bytes, convert
`targetX` into a row-running framebuffer index, alter clipping, or touch the
transformed fallback in this first comparison. Those are distinct candidates.
Preserving the existing source decode before the destination write also
preserves self-overlap behavior when sprite data aliases the framebuffer.

The existing independent `BlitPlainGeometrySmoke` compares the entire 64-KiB
memory after 518 cases covering both bpp modes, all low flag values, ignored
high bits, every clipping side, transparent and out-of-range color nibbles,
source/framebuffer address overlap, `blit` delegation, extreme coordinates,
and exact traps. Review found that its overlap geometry does not actually
overwrite a source byte before that byte is sampled. Add two true
alias-feedback cases at `pointer=FRAMEBUFFER`, destination `(0,0)`, size `8x1`,
source `(0,0)`, stride 8, and flags 0/1. The scratch prototype passes all 520
cases. This expanded smoke is the focused differential; no production-visible
test hook is needed.

**Expected benefit and risks.** The expected useful range is roughly 1-3% on
Waternet and smaller positive movement on Rubido. Risks are omitting the
`& 3` color normalization, changing packed-bit shift semantics, evaluating a
framebuffer address before transparency, changing source-overlap order,
inflating `blitSub` or the release JAR, and losing the call saving to generic
high-local loads or phoneME layout bimodality. There is no new allocation,
field, persistent heap, W4IR, cache, interpreter bytecode, or transformed-path
branch.

**Baseline identity.**

- source commit:
  `7967206f4776e66957d6859b72f01f85f24c4cb5`;
- counterless exactness artifact:
  `7f09e54dab05a01dc4c11b4c0c4f219c982d9e3c355f1240a5d943854e1a1885`;
- station/base release JAR:
  `d9422ce2cceee5d6ea525911829657f4210702aa6f7b6f46ae66a01a181d50aa`
  /
  `c41f62be7c9773e34d174b19580295d392443301a4922aeb33e564b58015f35c`;
- `Wasm4Runtime.class`: 16,376 bytes unpreverified and 22,255 bytes
  preverified; `blitSub`: approximately 679 target-47 bytes;
- native i686 VM:
  `bb4866969747430bb619d139c75ab31982e8967aa0e2dcc3e278fcc7920839a2`;
- CLDC classes:
  `117820661071b411b3937e8c708a9581aa48ba06fd06e24eadb5ebbf2036afbe`;
- preverify:
  `4fe0d1b160ac7f18c4f7489917a1d868ff82df50842ca718729b098b25f2f50c`.

**Planned gates and acceptance.** Inspect target-47 bytecode to prove that the
plain branch loses its `drawPoint` invoke and record exact method/class/JAR
deltas. Run the focused 520-case differential, `just test`, and `just verify`;
require Java 1.3, CLDC lint, preverification, seven-workload exact state,
framebuffer oracles, trap/budget/cache equivalence, release integrity, and zero
persistent heap growth.

Build clean hash-bound counterless snapshots from commit `7967206` and the
one-file candidate. Run at least eight balanced native i686 phoneME Waternet
pairs over the complete route plus 60 frames. Accept only at median paired
improvement of at least +0.8% with at least six wins in eight. If it passes,
run eight Rubido and Untangle control pairs and reject a resolved regression
worse than -0.5%. Repeat a borderline control rather than deciding at the
threshold. Record all raw pairs and the final verdict before retaining or
removing the implementation.

**Verification, bytecode, and artifact results.** The focused smoke now covers
520 cases, including the two destructive source/framebuffer alias cases, and
passes with complete 64-KiB memory equality and exact traps. `just test` and
`just verify` pass, including all seven full-state workloads, framebuffer,
audio, validation, storage, Java 1.3, CLDC lint, preverification, release, and
counterless gates. The candidate removes the plain-loop `drawPoint` invoke;
the only remaining invoke in `blitSub` is the unchanged transformed fallback.
Target-47 `blitSub` grows from 679 to 719 bytes and 384 to 413 disassembled
instructions while `max_stack=7`, `max_locals=37`, and classfile major 47 stay
unchanged. The counterless unpreverified/preverified `Wasm4Runtime.class`
grows from 16,376/22,255 to 16,424/22,303 bytes. Diagnostic/counterless
`WasmInterpreter.execute` remains 7,039/7,007 bytes. There is no allocation,
field, W4IR, cache-format, or persistent-heap delta.

The final verification artifacts are:

- counterless exactness:
  `c8aaef8a534307dca81d3c2f9e22e534361f73980f9dd2d002fb45bbc5240ac4`;
- station/base release JAR:
  `be9767e87532b131f175344ef4df2f24c1e394d96471c900d8bafddfc9a016ac`
  /
  `9e3abf78534b8117fc16b44a5354dcbf2351ec0a0fdaa1ed1f4650ea709dde3`,
  228,427/225,913 bytes;
- candidate unpreverified/preverified runtime class:
  `4bf80334856d6a74b58e49f02b02b11c4ac87b2cad0924dca49a31f5cffd5fe1`
  /
  `0c6a61aa8dedfacf373c861a036c509b932e86ab6cb504454dfb922cdcdd0226`.

**Clean native artifacts and commands.** The A/B snapshots are under
`/tmp/w4me-njit014.Sop1F0/`. The baseline is clean commit
`7967206f4776e66957d6859b72f01f85f24c4cb5`; the clean temporary candidate
commit `03a3c59b99bf34fdf1c7951b15b23e859d27efe0` contains only the production
`Wasm4Runtime.java` hunk. Their counterless artifacts are
`61b1fd457d4fa331093eb23620529f8b62226ab2c62a3954c3928f1d3aa9fbae`
and
`afb6bb392f22ec824090858cff85863fab09c2e4b0c163e163b8ad274f8eff49`;
the complete build artifacts are
`26a2ae617763e6caa3ad807014a4faf3fc459ff5bb593077b73ab70933d68adb`
and
`ad5df1f974c539dcc4d24d5de62477d18f0735436681859113495ae3c246bc24`.
Both receipts report clean source, Java source/target 1.3, identical VM,
classes, preverify, route, oracle, and cartridge hashes, and a 64-MiB judge
heap. Each artifact was built and sanity-run with:

```sh
PHONEME_HOME=./.local/phoneme \
  ./tools/phoneme/run.sh bench waternet rubido untangle \
  --candidate counterless --reps 1 --extra-frames 60
```

The paired runner invoked
`PhoneMeRouteBench <cart> optimized 60 1 counterless <sample>` directly on the
native i686 no-JIT VM, alternating baseline-first and candidate-first. Every
pair matched frames, checkpoints, logical instructions, disabled diagnostic
counters, branch metadata, W4IR format, and every other deterministic receipt
field. Raw microseconds per frame were:

| Pair |    Waternet B/C |        Rubido B/C |  Untangle B/C |
| ---: | --------------: | ----------------: | ------------: |
|    0 | 19,150 / 18,379 | 101,906 / 101,565 | 4,786 / 4,739 |
|    1 | 19,274 / 18,261 | 102,178 / 101,263 | 4,713 / 4,730 |
|    2 | 19,117 / 18,601 | 101,434 / 102,023 | 4,732 / 4,708 |
|    3 | 19,209 / 18,562 | 101,984 / 101,682 | 4,736 / 4,756 |
|    4 | 19,137 / 18,522 | 101,883 / 101,682 | 4,771 / 4,821 |
|    5 | 19,202 / 18,601 | 101,449 / 102,085 | 4,754 / 4,723 |
|    6 | 18,928 / 18,352 | 101,224 / 101,472 | 4,750 / 4,704 |
|    7 | 19,183 / 18,620 | 101,713 / 101,403 | 4,800 / 4,780 |

Waternet measures +3.172% median, 608.0 us/frame, and 8/8 wins, comfortably
above the predeclared +0.8% and 6/8 acceptance gate. Rubido measures +0.247%,
251.5 us/frame, and 5/8 wins. Untangle measures +0.462%, 22.0 us/frame, and
5/8 wins. Both controls are positive and therefore clear the -0.5%
no-regression limit. Timer resolutions were 6.536, 7.752, and 2.174
us/frame respectively.

KEmulator exact integration passed the 94-frame Waternet and 70-frame Rubido
routes normally. Two Untangle wrapper attempts both reached the exact final
401-frame receipt with all 47 checkpoints and framebuffer
`bc0231d9`, then the controller's post-run screenshot command returned
`java.io.IOException: Bad file descriptor`. The worker evidence is exact; the
failure is in the KEmulator controller after cartridge completion and is
recorded rather than hidden.

**Verdict:** accepted. Removing one private Java call per opaque plain-blit
pixel gives a repeatable +3.172% on the high-coverage Waternet route, does not
regress Rubido or the low-coverage Untangle control, preserves exact behavior,
and costs only 48 runtime-class bytes with no persistent memory.

### NJIT-015: running packed destination cursor for plain blits

**Status:** `accepted`.

**Hypothesis and source.** After NJIT-014, every opaque transform-free pixel
still recomputes
`FRAMEBUFFER + ((WIDTH * targetY + targetX) >> 2)` and
`(targetX & 3) << 1`. Target-47 emits an `imul`, two additions, a shift, an
`iand`, and another shift before the packed read-modify-write. The destination
coordinates are contiguous within one clipped row, so the packed byte address
and two-bit shift can instead be initialized once per row and advanced after
each pixel. This is the conventional scanline-cursor shape used by packed
framebuffer rasterizers; here it is a separate experiment from the accepted
geometry and helper-inlining changes.

The NJIT-014 exact profile remains the dynamic basis: Waternet processes
20,207.66 plain clipped pixels and 19,098.89 opaque writes per frame; Rubido
processes about 10,219 plain clipped pixels and 8,337.56 opaque writes per
frame; Untangle has only 18.08 plain pixels and 11.79 opaque writes per frame.
Thus Waternet and Rubido should avoid destination multiply/divide work on
thousands of pixels, while Untangle remains a low-coverage fallback control.
This is mechanism and coverage evidence only; no speedup is claimed before a
native paired A/B.

**Baseline identity.**

- source commit:
  `f30ffe25ac856652eb40153f1a418fbf95a607ee`;
- counterless exactness:
  `c8aaef8a534307dca81d3c2f9e22e534361f73980f9dd2d002fb45bbc5240ac4`;
- station/base release JAR:
  `be9767e87532b131f175344ef4df2f24c1e394d96471c900d8bafddfc9a016ac`
  /
  `9e3abf78534b8117fc16b44a5354dcbf2351ec0a0fdaa1ed1f4650ea709dde3`;
- runtime class: 16,424 bytes unpreverified and 22,303 bytes preverified;
- native i686 VM:
  `bb4866969747430bb619d139c75ab31982e8967aa0e2dcc3e278fcc7920839a2`;
- CLDC classes:
  `117820661071b411b3937e8c708a9581aa48ba06fd06e24eadb5ebbf2036afbe`;
- preverify:
  `4fe0d1b160ac7f18c4f7489917a1d868ff82df50842ca718729b098b25f2f50c`.

**Isolated mechanism.** In the accepted plain-row loop, initialize one
framebuffer byte address and one two-bit shift from the first clipped
destination pixel. Use those locals in the existing opaque write, then advance
the shift by two for every processed source pixel and increment the byte
address only when the shift wraps after four pixels. Remove only the now-dead
per-pixel `targetX` increment and destination address/shift calculation.
Preserve source decoding before any destination memory access, transparency,
row clipping, source validation and traps, 1-bpp/2-bpp behavior, the
transformed fallback, and the exact packed read-modify-write.

The first target-47 comparison must evaluate both a conditional wrap and a
branch-free carry shape before choosing one production prototype. A taken Java
branch reaches phoneME's timer-tick check, while a branch-free carry executes
more bytecodes on every pixel; static counts select the smaller credible
candidate but do not prove speed. Do not combine source-byte caching, 1-bpp
versus 2-bpp loop splitting, multi-pixel stores, or row unpacking in this A/B.

The isolated target-47 comparison selected the conditional rollover form.
Against the 719-byte, 413-instruction baseline `blitSub`, it produces a
732-byte, 419-instruction method; the branch-free carry form produces a
735-byte, 423-instruction method. Both retain `max_stack=7`,
`max_locals=37`, and classfile major 47. The selected form changes the
unpreverified runtime class from 16,424 to 16,449 bytes and the preverified
class from 22,303 to 22,381 bytes. Its opaque write falls from 39 to 23
target bytecodes, while cursor maintenance averages 7.75 bytecodes per
processed pixel plus 16 bytecodes per nonempty row. Applying those exact
counts to the retained coverage predicts about 229,804 fewer Java bytecode
dispatches per Waternet frame and 95,079 per Rubido frame before row setup;
this is still only mechanism evidence.

An independent source, target-bytecode, history, and alias review found no
previous attempt at this candidate and returned `GO` for the isolated native
A/B. It confirmed that the cursor must advance through transparent pixels,
must start from the clipped destination `x`, and must keep source decoding
before the destination read-modify-write. Source-byte caching and whole-byte
assembly remain excluded because framebuffer-backed sprites make sequential
alias feedback observable. The focused differential is expanded with a
destination beginning at packed shift one, mixed transparent/opaque 1-bpp and
2-bpp mappings, byte-boundary crossings, and destructive framebuffer aliasing
from destination `x=1`; the temporary candidate passed 874/874 cases and the
full host test suite before the same patch was applied to the working tree.

**Expected benefit and risks.** A useful result is expected in the 1-2% range
on Waternet, with a smaller Rubido benefit. The main risks are paying cursor
maintenance for transparent pixels, adding a taken branch every fourth pixel,
increasing `max_locals`, advancing the destination cursor in the wrong order,
or changing feedback when the sprite source aliases the framebuffer. The
existing 520-case full-memory differential includes destructive alias
feedback; add focused rows beginning at all four packed shifts and crossing
one or more byte boundaries if current cases do not distinguish cursor
rollover. There must be no field, allocation, persistent heap, W4IR, cache, or
interpreter-bytecode delta.

**Planned gates and acceptance.** Record exact target-47 method, instruction,
local, class, and preverified-class deltas for the chosen minimal form. Pass
the expanded plain-blit differential, `just test`, and `just verify`, including
Java 1.3, CLDC, full-state, framebuffer, traps, release, counterless, size, and
memory gates. Build clean hash-bound counterless snapshots from `f30ffe2` and
the one-file production candidate with:

```sh
PHONEME_HOME=./.local/phoneme \
  ./tools/phoneme/run.sh bench waternet rubido untangle \
  --candidate counterless --reps 1 --extra-frames 60
```

Run eight balanced native i686 Waternet pairs over the full route plus 60
frames. Accept only at median paired improvement of at least +0.8% with at
least six wins in eight. If it passes, run eight Rubido and Untangle controls;
reject a resolved regression worse than -0.5% and extend a borderline control.
Record every pair and remove the implementation if it fails.

**Correctness, compatibility, and artifact result.** The selected one-file
production candidate plus the focused test expansion passed:

- the 874/874 plain-blit full-memory differential, including every flag
  combination, mixed transparent/opaque mappings, clipped packed shifts,
  byte-boundary rollover, and destructive framebuffer overlap;
- all seven full-state workloads, framebuffer and browser-route oracles,
  traps, budget gates, Java 1.3/target 1.3 compilation, CLDC bootclasspath
  lint, target-47 inspection, preverification, release-JAR validation, and
  `just verify`;
- KEmulator Waternet (94 frames/17 checkpoints/14 tones), Rubido
  (70 frames/30 checkpoints), and Untangle (401 frames/47 checkpoints) with
  exact palette, input, framebuffer, and disk state.

The production target-47 class is 16,449 bytes with SHA-256
`3c51bb35ec61f14ea82e5ba3947c94678eaa5e88676854c89f40a5b7fda8845c`;
the release-preverified normalized class is 21,823 bytes with SHA-256
`2f3b61137b46d8b528bcd539f02c6bd629ad65276fb02c65222bc21cab6cfbe0`.
The complete station/base JARs are 228,434/225,920 bytes with SHA-256
`c854fa68c486442e56be58292e5ee49c26fa9c8b701c037ead28dd4ccd4c33a1`
and
`1c01a1437974ba51f74b205c58872ab9e231168d370fe9e923eaf66ca833b160`.
The final counterless exactness artifact is
`0bb15d039554d66b808d50141bc7181c58d468247bb1c323fff7396637300dab`;
`execute` remains 7,039 bytes in the diagnostic build and 7,007 bytes in the
counterless build. The change adds no fields, allocations, W4IR/cache bytes,
or persistent heap.

**Clean native i686 phoneME A/B.** Clean snapshots under
`/tmp/w4me-njit015.nBqekr/` compare source
`f30ffe25ac856652eb40153f1a418fbf95a607ee` with the isolated temporary
candidate `aa3141f`. The counterless artifacts are
`afb6bb392f22ec824090858cff85863fab09c2e4b0c163e163b8ad274f8eff49`
and
`85c103d781f790d225d2284b20142df9ef21bd6aa124016e3c2bd01b82f59c42`.
Their phoneME-preverified runtime classes are 22,303/22,381 bytes with
SHA-256
`0c6a61aa8dedfacf373c861a036c509b932e86ab6cb504454dfb922cdcdd0226`
and
`3a971082ce8d37a0c544020f4f9408afa00896ebc2d8531b3add040f714a9373`.
All deterministic receipt fields match in every pair.

| Sample | Waternet baseline/candidate µs/frame | Rubido baseline/candidate µs/frame | Untangle baseline/candidate µs/frame |
| -----: | -----------------------------------: | ---------------------------------: | -----------------------------------: |
|      0 |                      18,431 / 17,052 |                  101,542 / 100,317 |                        4,721 / 4,695 |
|      1 |                      18,535 / 17,032 |                  101,736 / 100,116 |                        4,739 / 4,721 |
|      2 |                      18,496 / 17,019 |                  101,674 / 101,147 |                        4,795 / 4,767 |
|      3 |                      18,601 / 17,091 |                  101,945 / 100,666 |                        4,710 / 4,726 |
|      4 |                      18,542 / 16,862 |                  101,627 / 100,914 |                        4,743 / 4,721 |
|      5 |                      18,535 / 16,888 |                  101,666 / 101,023 |                        4,717 / 4,758 |
|      6 |                      18,483 / 16,869 |                  101,984 / 101,255 |                        4,726 / 4,756 |
|      7 |                      18,490 / 17,052 |                  101,658 / 100,697 |                        4,728 / 4,710 |

Waternet measures a median +1,506.5 µs/frame, **+8.113%**, with 8/8
wins. Rubido measures +845.0 µs/frame, **+0.830%**, with 8/8 wins. Untangle,
whose coverage is negligible, measures +18.0 µs/frame, **+0.380%**, with
5/8 wins and no resolved regression. Timer resolutions are 6.536, 7.752,
and 2.174 µs/frame respectively; order is balanced and both snapshots are
source-clean.

**Verdict.** Accept. The running packed cursor turns the static destination
address/shift saving into a repeatable +8.113% on Waternet and a smaller
+0.830% Rubido gain, while preserving exact behavior and avoiding a control
regression. The branch-free form is rejected as an implementation variant:
it is larger and executes more target bytecodes per pixel, so it was not
promoted to native A/B. Reconsider it only if a different target VM makes
taken conditional branches demonstrably expensive enough to overcome that
larger steady-state stream.

### NJIT-016: native reuse of adjacent upscaled ARGB rows

**Status:** `accepted`.

**Hypothesis and source.** `W4Canvas` builds nearest-neighbor `yMap` entries as
`index * 160 / side`, converted to packed framebuffer row offsets. At common
upscaled sides, adjacent destination rows therefore repeat the same source
row: 80 of 240 rows at side 240 and 16 of 176 at side 176. Nevertheless,
`Wasm4Runtime.copyArgbBand` currently re-runs the complete inner conversion
for every destination pixel. Its target-47 loop performs `yMap`/`xMap` loads,
packed framebuffer addressing, a byte load, `argbLookup`, and an `iastore`
for every repeated pixel. phoneME implements `System.arraycopy` for primitive
arrays in native code, so copying the already expanded preceding destination
row should replace thousands of C-interpreted Java bytecodes per presented
frame.

This candidate is the row-reuse half of the older queued `NJIT-008`; packed
byte expansion and repeated-column reuse remain separate ideas. The native
phoneME route benchmark does not call the MIDP renderer, so its normal
cartridge wall time cannot judge this change. The authoritative component
judge is instead a CLDC-clean `PhoneMeArgbBandBench` running the production
`prepareArgb`/`copyArgbBand` methods on the native i686 no-JIT VM. KEmulator
is retained only for the complete `W4Canvas`/`drawRGB`/`flushGraphics`
integration gate, and physical-device evidence overrides the component result
when available.

**Baseline identity.**

- source commit:
  `58f1469952f3ed1fbd9f096e8fd10ef08b03c16a`;
- counterless exactness:
  `0bb15d039554d66b808d50141bc7181c58d468247bb1c323fff7396637300dab`;
- station/base release JAR:
  `c854fa68c486442e56be58292e5ee49c26fa9c8b701c037ead28dd4ccd4c33a1`
  /
  `1c01a1437974ba51f74b205c58872ab9e231168d370fe9e923eaf66ca833b160`;
- runtime class: 16,449 bytes unpreverified and 22,381 bytes in the
  phoneME-preverified counterless tree; the complete baseline tree digest is
  `dd1cf262cc1963a08372d4ccbbad487695a1fec9b599d25635ce492f62246f27`;
- native i686 VM:
  `bb4866969747430bb619d139c75ab31982e8967aa0e2dcc3e278fcc7920839a2`;
- CLDC classes:
  `117820661071b411b3937e8c708a9581aa48ba06fd06e24eadb5ebbf2036afbe`;
- preverify:
  `4fe0d1b160ac7f18c4f7489917a1d868ff82df50842ca718729b098b25f2f50c`.

**Prototype narrowing.** A first temporary prototype added a
`previousSourceRow` local and used `System.arraycopy` for every adjacent equal
row. It changed `copyArgbBand` from 156 bytes/86 target instructions/15
locals to 190 bytes/104 instructions/16 locals. Single exploratory native
runs at full-buffer presentation measured 6,105 -> 4,100 µs/frame at side
240 and 3,340 -> 2,883 at side 176, but 2,580 -> 2,683 at native side 160.
Those unpaired numbers are not a performance verdict; they exposed a material
no-upscale regression risk and superseded that implementation form.

A direct per-row `width > 160` guard avoided the extra local but still altered
the hot loop layout. A helper selected from inside `copyArgbBand` then
measured a borderline -0.575% median on twelve side-160 pairs. The current
production-shaped prototype therefore leaves the complete
`copyArgbBand` target bytecode identical and adds a separate public
`copyUpscaledArgbBand`; `W4Canvas` selects it only when `side > 160`. The new
method copies a row only when its `yMap` entry equals the immediately
preceding entry within the same band; otherwise it executes the canonical
conversion loop. This also handles the low-memory 16-row band renderer
without retaining another row or adding heap. Aliased output/map arrays fall
back to the canonical method because copying the first expanded row could
otherwise overwrite a later map entry. The final
target-47 shape is:

- unchanged `copyArgbBand`: 156 bytes, 86 instructions, `max_stack=6`,
  `max_locals=15`;
- `copyUpscaledArgbBand`: 222 bytes, `max_stack=6`,
  `max_locals=15`;
- runtime class: 16,851 bytes unpreverified and 23,034 bytes preverified;
- counterless phoneME candidate tree:
  `2e62a10d9ca6e7ec06544279cdf319f4d84593e972f06d2e3d29ddf2d4ca9c0c`,
  with runtime class
  `b5931d094fb473de8772e3b1d2e997907b0f2942bda027bf25cd9db0f5597b0b`;
- no new fields, persistent arrays, allocations, W4IR, cache, or interpreter
  bytecode.

**Native i686 phoneME A/B.** The fixed baseline/candidate class trees were
run through `/tmp/w4me-njit016.chLQ6O/run-render-pairs.sh SIDE BAND FRAMES
PAIRS`; every invocation checked the complete output FNV before appending a
pair and used `tools/phoneme/paired-stats.awk`. The reusable single-artifact
path is now:

```sh
tools/phoneme/run.sh bench waternet --reps 1
.local/phoneme/cldc_vm_r -EnableTicks =HeapCapacity64M \
  -classpath .local/phoneme/classes.zip:build/reports/phoneme/preverified \
  w4me.PhoneMeArgbBandBench 240 240 200 0
```

The latter reproduces `output-fnv1a=91a5116c`. Raw baseline/candidate
µs/frame pairs follow; order alternated on every row:

| Shape       | Frames | Raw baseline/candidate pairs                                                                                                       | Paired result                   |
| ----------- | -----: | ---------------------------------------------------------------------------------------------------------------------------------- | ------------------------------- |
| 240/full    |    200 | 6095/3900, 5945/3965, 5935/3985, 5935/4005, 5870/4010, 6055/4075, 5935/4030, 6080/4045, 5990/3960, 5915/3980, 5960/3995, 5940/4020 | **+32.785%**, +1957.5 µs, 12/12 |
| 240/band-16 |    200 | 6170/4165, 5860/4215, 5880/4120, 6025/4140, 5985/4195, 5855/4160, 6085/4175, 6080/4235, 5985/4215, 5860/4045, 5980/4070, 5990/4125 | **+30.659%**, +1830.0 µs, 12/12 |
| 176/full    |    300 | 3193/2946, 3173/2960, 3133/2910, 3310/2916, 3196/2913, 3260/2916, 3146/2926, 3150/2940, 3190/2990, 3186/2903, 3173/2946, 3186/2943 | **+7.391%**, +235.0 µs, 12/12   |
| 176/band-16 |    300 | 3176/2956, 3103/2950, 3193/2960, 3183/2926, 3190/2926, 3193/2953, 3206/2950, 3286/2963, 3223/2953, 3233/2993, 3243/2876, 3173/2940 | **+7.751%**, +248.0 µs, 12/12   |
| 320/full    |    100 | 11370/5330, 10810/5330, 10810/5330, 10480/5300, 11030/5300, 10540/5380, 10690/5320, 10470/5270                                     | **+50.464%**, +5425.0 µs, 8/8   |
| 161/full    |    600 | 2663/2680, 2675/2716, 2671/2693, 2660/2681, 2695/2688, 2698/2698, 2690/2668, 2658/2693, 2681/2651, 2700/2693, 2680/2686, 2673/2678 | -0.205%, -5.5 µs, 4/7/1         |
| 160/full    |    600 | 2621/2630, 2645/2646, 2696/2650, 2623/2643, 2591/2621, 2685/2633, 2673/2681, 2660/2633, 2600/2663, 2646/2650, 2666/2600, 2775/2640 | -0.094%, -2.5 µs, 5/7           |
| 128/full    |    800 | 1716/1708, 1691/1678, 1685/1681, 1701/1701, 1702/1677, 1815/1696, 1711/1686, 1688/1693, 1683/1703, 1652/1772, 1767/1686, 1688/1695 | +0.352%, +6.0 µs, 7/4/1         |

Timer resolutions were 5.000, 3.333, 10.000, 1.667, and 1.250
µs/frame according to sample length. The temporary runner printed
`source-clean=yes`, but both detached snapshots contained their benchmark
source as an untracked file; that metadata bit is therefore not used as
evidence. The compiled trees were immutable throughout all pairs and are
identified by the complete tree digests above. All large positive effects
are many timer ticks, directionally unanimous, and repeat across full and
banded rendering. The 161/160/128 controls are conservatively classified as
no resolved effect and stay above the -0.5% rejection floor.

**Correctness and release gates.** `ArgbBandDifferentialSmoke` compared the
new method to `copyArgbBand` in 3,101 cases across sides 161/176/240/320,
band heights 1/2/15/16/17/full, zero/first/last/partial bands, repeated and
non-monotonic maps, two palettes, untouched output tails, invalid geometry,
and `pixels == xMap` / `pixels == yMap`; all outputs and exception gates
passed. `just verify` passed all replay/full-state/trap tests, target-47
builds, release validation, and counterless exactness. The final counterless
artifact is
`c7f2a189ab91b569ccc3461b772df4746d510541c4a9f050803a2f0673dbaf75`;
`WasmInterpreter.execute` remains 7,039 bytes in production and 7,007 in the
counterless build. Release artifacts are:

- station: 228,701 bytes,
  `863dc617ed578ed70170d0e7dd7ad5c3f225f2b4067c0ead9736092fa8277723`;
- base: 226,187 bytes,
  `579e0db0135cd2ebd667675069023acc5f28464d74f3dddc2f8340fc8859cd4b`.

KEmulator presentation controls also passed exact Waternet 94/94, Rubido
70/70, and Untangle 401/401 routes at the 176x220 phone layout. These are
MIDP correctness gates, not timing evidence. No physical-phone timing was
available.

**Verdict.** Accept. The separate-method boundary preserves native/downscale
target bytecode and has no persistent-heap cost, while native phoneME shows
a repeatable +7.4--7.8% conversion win at 176 and +30.7--32.8% at 240. The
measured percentages cover ARGB conversion only; they must not be reported as
whole-frame or physical-device speedups because `drawRGB`, `flushGraphics`,
the interpreter, and audio remain outside the component timer.

### NJIT-017: cache the packed framebuffer byte across an upscaled row

**Status:** `accepted`.

**Hypothesis and source.** The independent renderer bytecode review following
NJIT-016 found that the horizontal nearest-neighbor map repeats one packed
framebuffer byte across several destination pixels. A WASM-4 byte stores four
2-bpp pixels, so even native 160-wide conversion reads the same byte four
times; an upscaled 240-wide row reads each of its 40 packed bytes about six
times. NJIT-016 removes vertically repeated rows but its remaining 160 source
rows at side 240 still execute 38,400 `baload` operations instead of the
6,400 distinct packed-byte reads required. On phoneME every `baload`, address
calculation, bounds check, and JVM dispatch runs in the C interpreter.

The first isolated form changes only `copyUpscaledArgbBand`: within each
non-copied row it retains the previous packed-byte index and value, reloads
memory only when `(mapping & 0xff)` changes, and still uses the current
mapping's high bits to select the correct 2-bpp lane. `copyArgbBand` and the
native/downscale `W4Canvas` path remain bytecode-identical. Extending the same
mechanism to native/downscale conversion is a separate follow-up only if the
upscale result is accepted and a dedicated A/B justifies altering that loop.

**Baseline identity.**

- source commit:
  `191910ec34db9b5d3b4b645a69b23ef43eb9da5d`;
- counterless exactness:
  `c7f2a189ab91b569ccc3461b772df4746d510541c4a9f050803a2f0673dbaf75`;
- station/base JAR:
  `863dc617ed578ed70170d0e7dd7ad5c3f225f2b4067c0ead9736092fa8277723`
  /
  `579e0db0135cd2ebd667675069023acc5f28464d74f3dddc2f8340fc8859cd4b`;
- runtime class: 16,851 bytes target-47 and 23,034 bytes in the
  phoneME-preverified tree;
- `copyArgbBand` / `copyUpscaledArgbBand`: 156 / 222 bytes;
- native i686 VM, CLDC classes, and preverify:
  `bb4866969747430bb619d139c75ab31982e8967aa0e2dcc3e278fcc7920839a2`,
  `117820661071b411b3937e8c708a9581aa48ba06fd06e24eadb5ebbf2036afbe`,
  and
  `4fe0d1b160ac7f18c4f7489917a1d868ff82df50842ca718729b098b25f2f50c`.

**Expected benefit and risks.** The candidate adds two scalar locals and one
integer comparison per computed destination pixel, but removes five of six
packed-byte loads at side 240 and more than four of five at side 176. It adds
no fields, arrays, allocations, W4IR, cache data, or persistent heap. The main
risks are that phoneME's taken/not-taken branch and larger method frame cost
more than the saved `baload` path, that cache state leaks across rows, or that
an arbitrary/non-monotonic `xMap` reuses a stale byte. Resetting the cache at
each computed row and keying it only by the complete low-byte address preserves
arbitrary maps; the existing 3,101-case differential, including alias and
band boundaries, must remain exact.

**Experiment plan.** Build fixed baseline/candidate CLDC trees from
`191910e`, confirm `copyArgbBand` target bytecode is unchanged, and measure the
candidate independently with `PhoneMeArgbBandBench`. Run at least twelve
balanced native i686 pairs at 240/full, 240/band-16, 176/full, and
176/band-16. Require a repeatable useful gain of at least +1% at both widths
with no mode-specific regression; side 161 is the low-coverage guard. Native
160 and downscaled 128 must remain artifact-identical at the original method
boundary. If the timing gate passes, rerun the focused differential, CLDC
preverify, `just verify`, release size/hash checks, and KEmulator presentation
oracles. Record raw pairs and reject the extra branch/locals if phoneME does
not pay them back.

**Artifact and bytecode result.** The isolated baseline/candidate phoneME
trees are
`35128db287d8cdf3fe4c222399a57c38a9586891bc76b0b6f1c1ca1c0fb5fed1`
and
`ffcaff38f604b6916bed9d4b338e7ffff0a8cdef38ffc3172b6756a2c309b4a3`.
The candidate runtime is byte-identical between the measured `/tmp` snapshot
and main:

- target-47 class:
  `d0de4d0c207b3cd3ef98823ebe4d35730f707bdbb979c077ad018217da1185f0`,
  16,892 bytes (+41);
- preverified class:
  `bbff3015a89562e6878258d836537c5b8ca8a93b0e532472605c8318caf92c9c`,
  23,112 bytes (+78);
- `copyArgbBand`: unchanged at 156 bytes;
- `copyUpscaledArgbBand`: 243 bytes (+21), with no new fields, arrays,
  allocations, or persistent heap.

`ArgbBandDifferentialSmoke` passed all 3,101 cases before timing and again
from main. All paired samples verified the full-output FNV. Raw
baseline/candidate µs/frame pairs are:

| Shape       | Frames | Raw baseline/candidate pairs                                                                                                       | Paired result                  |
| ----------- | -----: | ---------------------------------------------------------------------------------------------------------------------------------- | ------------------------------ |
| 240/full    |    200 | 3955/3370, 3970/3380, 3960/3425, 3995/3375, 3885/3325, 4010/3370, 4030/3465, 4010/3360, 4150/3320, 4025/3365, 4000/3395, 4010/3365 | **+15.322%**, +612.5 µs, 12/12 |
| 240/band-16 |    200 | 4235/3470, 4045/3470, 4165/3455, 4170/3540, 4165/3525, 4120/3500, 4125/3495, 4140/3490, 4110/3505, 4115/3460, 4160/3485, 4120/3480 | **+15.450%**, +640.0 µs, 12/12 |
| 176/full    |    300 | 2936/2530, 2943/2543, 2920/2523, 2893/2513, 2913/2533, 2913/2540, 2903/2553, 2986/2523, 2973/2570, 2920/2566, 2920/2526, 2940/2530 | **+13.524%**, +395.5 µs, 12/12 |
| 176/band-16 |    300 | 3046/2790, 2946/2550, 2950/2560, 3043/2550, 2923/2550, 3046/2576, 2960/2543, 2940/2566, 2936/2566, 3016/2536, 2963/2550, 3090/2556 | **+13.690%**, +404.5 µs, 12/12 |
| 161/full    |    600 | 2650/2348, 2626/2340, 2670/2365, 2710/2351, 2645/2345, 2655/2368, 2650/2356, 2680/2370, 2701/2358, 2670/2366, 2666/2360, 2660/2335 | **+11.410%**, +304.5 µs, 12/12 |

Timer resolutions were 5.000, 3.333, and 1.667 µs/frame, orders were
balanced, and every effect was unanimous and larger than 300 µs/frame. The
temporary candidate checkout was intentionally uncommitted, so
`paired-stats.awk` conservatively labeled the runs `exploratory`; no source
changed during timing, both fixed artifact hashes are recorded, and the final
main target-47 and preverified classes match the measured candidate
byte-for-byte.

**Final gates and verdict.** `just verify` passed Java 1.3, all cartridge
replays/full-state/traps, the 3,101-case scaler differential, release
validation, and counterless exactness. The final counterless artifact is
`de198887d0b35369dc6e650f355c0fe0f23ed6036af01ba495313c364f6581c9`.
Production `WasmInterpreter.execute` remains 7,039 bytes. Release artifacts
are:

- station: 228,785 bytes,
  `f1ab50c0a3bd27f163c0d60847122bd725cf1b904009382c650c847de75003ed`;
- base: 226,271 bytes,
  `0c3f2240247d612d02d987669a17eb79602ca92bb804230c3b0da69c1acf6790`.

KEmulator exact presentation routes passed Waternet 94/94, Rubido 70/70,
and Untangle 401/401 at 176x220. Accept the helper-only cache: it gives an
additional +11.4--15.5% native-phoneME ARGB conversion improvement on every
upscaled shape tested, while leaving native/downscale bytecode unchanged.
As with NJIT-016, this is a component result, not a whole-frame or
physical-phone FPS claim.

### NJIT-018: hoist the upscaled ARGB lookup table into a local

**Status:** `accepted`.

**Hypothesis and source.** The same independent renderer review found one
remaining `aload_0; getfield argbLookup` in every computed destination pixel
of `copyUpscaledArgbBand`. phoneME does not inline ordinary getters; the
reference-runtime disassembly and native microbench research measured a
quickened instance-field read as materially more expensive than a local
reference load. After NJIT-016 and NJIT-017, side 240 still computes 38,400
pixels and side 176 computes 28,160, so one immutable table-reference load at
method entry can remove that many field-handler dispatches per conversion.

The isolated candidate changes only `copyUpscaledArgbBand`: assign the final
`argbLookup` array to one local after validation/alias fallback and use that
local inside the computed-row loop. It does not alter row reuse, packed-byte
caching, `copyArgbBand`, `W4Canvas`, heap ownership, or output.

**Baseline identity.**

- source commit:
  `272891ba0ad355484417b25044755ce4a3b57f61`;
- counterless exactness:
  `de198887d0b35369dc6e650f355c0fe0f23ed6036af01ba495313c364f6581c9`;
- station/base JAR:
  `f1ab50c0a3bd27f163c0d60847122bd725cf1b904009382c650c847de75003ed`
  /
  `0c3f2240247d612d02d987669a17eb79602ca92bb804230c3b0da69c1acf6790`;
- phoneME production-shaped artifact:
  `ffcaff38f604b6916bed9d4b338e7ffff0a8cdef38ffc3172b6756a2c309b4a3`;
- runtime class: 16,892 bytes target-47 and 23,112 bytes preverified;
- `copyArgbBand` / `copyUpscaledArgbBand`: 156 / 243 bytes;
- native i686 VM, CLDC classes, and preverify remain
  `bb4866969747430bb619d139c75ab31982e8967aa0e2dcc3e278fcc7920839a2`,
  `117820661071b411b3937e8c708a9581aa48ba06fd06e24eadb5ebbf2036afbe`,
  and
  `4fe0d1b160ac7f18c4f7489917a1d868ff82df50842ca718729b098b25f2f50c`.

**Expected benefit, risks, and gates.** The patch should add only one
reference local and a few method-entry bytecodes, with no persistent or peak
heap increase. Its risk is entirely phoneME execution shape: the larger frame
or changed method layout may cost more than the removed field reads, and a
sub-percent apparent effect may be layout/timer noise. Prototype in a fixed
snapshot, verify target-47 form and the 3,101-case differential, then run at
least twelve balanced native i686 pairs at 240/full and 176/full. Promote
band-16 controls if either primary is positive. Accept only a repeatable
paired median of at least +0.8% with a clear win majority at both widths and
no result below -0.5%; otherwise reject and record the exact dead end.

**Artifact, A/B, and verdict.** The fixed baseline/candidate phoneME artifacts
are
`ffcaff38f604b6916bed9d4b338e7ffff0a8cdef38ffc3172b6756a2c309b4a3`
and
`27d00eb4fc54e51c92ba1549021e5d5dec3abff68ffd6443ef2a1c918305dc52`.
The target-47 runtime is 16,900 bytes (+8), the preverified runtime is 23,138
bytes (+26), and `copyUpscaledArgbBand` is 247 bytes (+4). Main matches the
measured target-47 class
`67edb79f17208afc59da664ef981502ba6b1395642b3acda5654d223e271f9ba`
and preverified class
`63ce15aae1f76f793b4ffa5bcb409acf323325214645fafc9585519b31a49978`
byte-for-byte. No field, allocation, or heap state was added.

Raw baseline/candidate µs/frame pairs:

| Shape              | Frames | Raw baseline/candidate pairs                                                                                                                                                   | Paired result                     |
| ------------------ | -----: | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------- |
| 240/full           |    300 | 3350/3386, 3363/3340, 3430/3286, 3350/3323, 3363/3286, 3390/3363, 3373/3390, 3370/3366, 3393/3336, 3366/3260, 3383/3263, 3393/3253                                             | **+1.243%**, +42.0 µs, 10/2       |
| 176/full           |    400 | 2522/2470, 2512/2467, 2527/2557, 2530/2457, 2542/2532, 2552/2495, 2492/2562, 2537/2512, 2545/2545, 2532/2562, 2525/2457, 2540/2482                                             | **+1.388%**, +35.0 µs, 8/3/1      |
| 240/band-16        |    300 | 3500/3366, 3470/3353, 3523/3440, 3493/3453, 3480/3480, 3470/3453, 3480/3493, 3470/3470, 3490/3453, 3510/3483, 3473/3406, 3476/3473                                             | **+0.915%**, +32.0 µs, 9/1/2      |
| 176/band-16 first  |    400 | 2550/2572, 2552/2565, 2547/2582, 2560/2450, 2567/2477, 2560/2545, 2520/2565, 2555/2495, 2572/2465, 2557/2482, 2530/2562, 2547/2852                                             | +0.038%, +1.0 µs, 6/6, unresolved |
| 176/band-16 repeat |    600 | 2531/2516, 2556/2565, 2576/2566, 2571/2500, 2521/2581, 2556/2531, 2568/2560, 2518/2515, 2560/2553, 2551/2531, 2546/2508, 2560/2508, 2560/2545, 2555/2548, 2576/2490, 2546/2573 | +0.487%, +12.5 µs, 13/3           |

The primary full-buffer modes clear the predeclared +0.8% threshold, and
240/band-16 also clears it. The longer 176/band-16 repeat is directionally
positive but deliberately reported as below the standalone acceptance
threshold; its role is no-regression evidence. Timer resolutions were 3.333,
2.500, and 1.667 µs/frame. The temporary candidate remained uncommitted and
therefore received the conservative `exploratory` label, but the artifacts
were fixed throughout timing and main is byte-identical to the measured
candidate.

`just verify` passed the 3,101-case renderer differential, all replay/state
and trap checks, Java 1.3/preverification, release validation, and counterless
exactness. The final counterless artifact is
`91942c2fbc75e201c8153964e7a84aa003bb7951e419ee2d48bd14fca4ec1417`.
Release artifacts are station 228,802 bytes
(`ea870077356454b8e90a7fa547dbb05e2dfc78e7b904a5d069a507d85e9d2339`)
and base 226,288 bytes
(`aa4d9193b8e88ce52104355b4c033235f5f0347ae8398fe1df217478aeabcb22`).
KEmulator Waternet passed 94/94 exact presentation checkpoints at 176x220.

Accept. A four-byte method change removes a field read per computed pixel,
produces repeatable +1.2--1.4% native phoneME conversion gains on both primary
upscaled shapes, remains positive or neutral in low-memory band mode, and has
no heap or correctness cost. This remains component timing, not a whole-frame
FPS claim.

### NJIT-019: cache packed bytes in native/downscale ARGB conversion

**Status:** `accepted`.

**Hypothesis and source.** NJIT-017 proved on native phoneME that retaining the
current packed framebuffer byte inside the upscaled inner loop is worth
+11.4--15.5% conversion time. The same redundancy remains in the intentionally
untouched canonical `copyArgbBand`: side 160 performs 25,600 `baload`
operations although a frame contains only 6,400 packed bytes, while side 128
performs 16,384 loads for about 5,120 distinct bytes selected by its map.
This is the deferred native/downscale half of the same mechanism, not a
combined experiment.

The candidate adds `previousPackedAddress` and `packed` locals to
`copyArgbBand`, resets them for every row, and reloads only when
`mapping & 0xff` changes. `copyUpscaledArgbBand`, NJIT-016 row reuse,
NJIT-017's cache, NJIT-018's lookup local, and `W4Canvas` dispatch remain
byte-identical.

**Baseline identity.**

- source commit:
  `5bb77cd31275df4a5e74d6f4b0a80612f9725cc6`;
- counterless exactness:
  `91942c2fbc75e201c8153964e7a84aa003bb7951e419ee2d48bd14fca4ec1417`;
- station/base JAR:
  `ea870077356454b8e90a7fa547dbb05e2dfc78e7b904a5d069a507d85e9d2339`
  /
  `aa4d9193b8e88ce52104355b4c033235f5f0347ae8398fe1df217478aeabcb22`;
- phoneME artifact:
  `27d00eb4fc54e51c92ba1549021e5d5dec3abff68ffd6443ef2a1c918305dc52`;
- runtime class: 16,900 bytes target-47 and 23,138 bytes preverified;
- `copyArgbBand` / `copyUpscaledArgbBand`: 156 / 247 bytes;
- native i686 phoneME tool hashes remain those recorded by NJIT-018.

**Correctness boundary.** The existing scaler differential intentionally
uses `copyArgbBand` as the canonical oracle, so it cannot independently prove
a rewrite of that same method. Before acceptance, add a direct reference
conversion that reads palette and framebuffer memory without calling either
production converter. Cover native 160, downscaled 128, arbitrary/non-monotonic
maps, band boundaries, palette changes, untouched tails, invalid geometry,
and the public `pixels == xMap` / `pixels == yMap` alias behavior. The cache
key is the complete low-byte address, so a changing 2-bpp lane in the high
mapping bits must still select from the retained byte correctly.

**Performance and decision gates.** Prototype only the canonical loop, inspect
target-47/preverified size, and run at least twelve balanced native i686 pairs
at 160/full, 160/band-16, 128/full, and 128/band-16. Require at least +2% on
both full-buffer primaries, no banded result below -0.5%, exact output on
every sample, and no persistent heap. If it passes, add the independent
reference test to main, run `just verify`, release gates, and KEmulator
native/downscale presentation where available; otherwise remove the cache and
record the rejected implementation.

**Artifact, exactness, and bytecode result.** The fixed baseline and candidate
production-shaped phoneME artifacts are
`27d00eb4fc54e51c92ba1549021e5d5dec3abff68ffd6443ef2a1c918305dc52`
and
`cdc59be1c0e514f9e1a6db915fcf5b66710c2f50e1ad328c3a4cddeadb125974`.
After promotion, main rebuilt the candidate artifact with the same SHA-256.
Its runtime class is byte-identical to the measured `/tmp` snapshot:

- target-47:
  `3523e906614f6728b24e908a500dc36338cd516f6ee3c50ca8f90fc5ea5c9764`,
  16,941 bytes (+41);
- preverified:
  `95fd55080b40b5686ef0c7d87cad32dd2619bbe5cc8691a077a4a49584480adf`,
  23,218 bytes (+80);
- `copyArgbBand`: 177 bytes (+21);
- `copyUpscaledArgbBand`: unchanged at 247 bytes.

The change adds two scalar locals and no field, array, allocation, or
persistent heap. The independent reference reads the 2-bpp framebuffer and
little-endian palette directly rather than using either production converter.
It covers native 160, downscaled 128, full and partial bands, non-monotonic
maps, repeated rows, changing lanes within one packed byte, untouched tails,
aliases, palette changes, and invalid geometry. `ArgbBandDifferentialSmoke`
passes all 3,731 cases.

**Native i686 phoneME A/B.** The native VM, CLDC classes, and preverify hashes
are the NJIT-018 baseline values. Each sample converted a fixed framebuffer
through the public production method, checked the complete output FNV, and
alternated baseline-first and candidate-first order. Raw baseline/candidate
µs/frame pairs are:

| Shape       | Frames | Raw baseline/candidate pairs                                                                                                       | Paired result                  |
| ----------- | -----: | ---------------------------------------------------------------------------------------------------------------------------------- | ------------------------------ |
| 160/full    |    600 | 2623/2338, 2641/2348, 2645/2326, 2651/2348, 2643/2340, 2736/2345, 2620/2330, 2758/2333, 2665/2343, 2758/2340, 2583/2345, 2606/2340 | **+11.447%**, +303.0 µs, 12/12 |
| 160/band-16 |    600 | 2741/2345, 2648/2335, 2638/2321, 2655/2326, 2605/2340, 2660/2338, 2673/2333, 2655/2358, 2611/2358, 2651/2323, 2705/2335, 2645/2333 | **+12.061%**, +319.5 µs, 12/12 |
| 128/full    |    800 | 1683/1543, 1672/1533, 1716/1502, 1713/1542, 1673/1528, 1662/1531, 1681/1538, 1697/1538, 1670/1537, 1686/1533, 1683/1537, 1690/1555 | **+8.587%**, +144.0 µs, 12/12  |
| 128/band-16 |    800 | 1707/1533, 1683/1537, 1721/1551, 1705/1535, 1701/1530, 1721/1547, 1681/1540, 1693/1556, 1772/1541, 1725/1523, 1715/1537, 1818/1545 | **+10.082%**, +172.5 µs, 12/12 |

Timer resolutions are 1.667 µs/frame at side 160 and 1.250 µs/frame at side 128. Every one of the 48 pairs favors the candidate and all deterministic
output fields match. The isolated candidate checkout was intentionally
uncommitted, so the statistics label it `exploratory`; the artifacts remained
fixed throughout timing and the promoted main classes match them
byte-for-byte.

The exact timing commands were:

```sh
/tmp/w4me-njit019.91mC2c/run-render-pairs.sh 160 160 600 12
/tmp/w4me-njit019.91mC2c/run-render-pairs.sh 160 16 600 12
/tmp/w4me-njit019.91mC2c/run-render-pairs.sh 128 128 800 12
/tmp/w4me-njit019.91mC2c/run-render-pairs.sh 128 16 800 12
```

**Final gates and verdict.** `just verify` passes Java 1.3, CLDC lint and
preverification, all replay/full-state/trap checks, the 3,731-case renderer
differential, release validation, and counterless exactness. The counterless
artifact is
`544c0a3bf17074ade5dffe7bd61cf54b34a4b33099e9f01b69e2bb21a4345607`;
diagnostic/counterless `WasmInterpreter.execute` remain 7,039/7,007 bytes.
Release artifacts are:

- station: 228,837 bytes,
  `9b4de64851b0d331ee469e5f0244c62827c96096c7026b3c48d4b3ebed61e221`;
- base: 226,323 bytes,
  `6ac441edbb4ee5ea9c1ca07fe77d6c1d9af978405e3770910fd843a04efa2c6f`.

Two KEmulator diagnostic sessions exercised the production Canvas and the
actual affected method rather than the usual side-176 upscale path:

- screen 160x220 reported
  `framebuffer=0,8,160 controls=176,44 overlap=0`, completed all 60 exact
  Plasma checkpoints, and produced screenshot
  `f95004e8e96d05af1c32ce3f1fc5a122a345d03c12b70009dc6f3dbb7cfb2e61`;
- screen 128x180 reported
  `framebuffer=0,6,128 controls=140,40 overlap=0`, completed the same oracle,
  and produced screenshot
  `40ff63d1edf1c5278a58d0cd8b2193fecfed5e131583d2b6eb11947e80e65f2a`.

Accept. The cache exceeds both predeclared primary gates, wins every pair,
preserves arbitrary-map and alias semantics, costs no heap, and passes real
MIDP native/downscale presentation. This remains a component conversion
result, not a whole-frame FPS claim.

### NJIT-010: constant-envelope and constant-pitch PCM fast path

**Status:** `accepted`.

**Hypothesis and source.** `Wasm4Pcm.synthesize` currently recomputes
`sample * 60 / 8000`, calls the seven-argument `envelopeVolume`, and performs
a signed 64-bit multiply/divide for pitch interpolation on every sample. This
work also runs when a tone has no attack, decay, or release and has no pitch
slide. That is the complete audible route corpus: all 12 non-null Waternet
tones and all seven non-null Rubido tones have a sustain-only envelope and
equal decoded start/end frequencies. One Waternet route synthesizes 41,332
WAV bytes and one Rubido route 7,777 bytes through this shape.

The isolated candidate checks frequency equality first, followed by the three
zero envelope segments, before entering the scalar loop. For that shape it
calls one private helper and returns; the helper retains the decoded start
frequency and sustain volume while preserving the existing waveform, phase,
PCM, pan, and output logic. The original general ADSR/slide calculation stays
unchanged after the guard. This does not change the APU ABI, MMAPI lifecycle,
sample rate, WAV layout, waveform math, allocation count, or buffer size.

**Baseline identity.**

- source commit:
  `df3f518586cd6d012144df80c02b818f37db94b2`;
- counterless exactness:
  `544c0a3bf17074ade5dffe7bd61cf54b34a4b33099e9f01b69e2bb21a4345607`;
- station/base JAR:
  `9b4de64851b0d331ee469e5f0244c62827c96096c7026b3c48d4b3ebed61e221`
  /
  `6ac441edbb4ee5ea9c1ca07fe77d6c1d9af978405e3770910fd843a04efa2c6f`;
- phoneME production-shaped artifact:
  `cdc59be1c0e514f9e1a6db915fcf5b66710c2f50e1ad328c3a4cddeadb125974`;
- target-47/preverified `Wasm4Pcm.class`:
  `f0121589e43d0770532eee16e18c8c2cd1916cb3d88b1dfed4e0f90babb2ddf2`
  (2,631 bytes) /
  `7197e0e2dbe13a1520b36978fc9cbfdc8989c7d6bed012a0a801c07104539666`
  (3,832 bytes);
- `synthesize`: 638 target-47 code bytes;
- native i686 phoneME VM/classes/preverify hashes remain the NJIT-019 values.

**Correctness and performance gates.** Freeze the original implementation as
an independent test reference and compare every returned byte or `null` over
the Cartesian edge corpus, maximum-duration cases, deterministic random
ADSR/slides/note-mode/pan/waveforms, and route fixtures. Require at least
10,000 cases and 50 MiB of compared output, plus stable hashes for Waternet,
Rubido, upward/downward slides, ADSR, note-bend slide, and the maximum
duration. Compile and preverify both artifacts with source/target 1.3 and
record class/method/JAR growth.

Add a CLDC-only `PhoneMePcmBench` that executes exact Waternet and Rubido tone
sequences and separate ADSR/slide slow-path controls, retaining only aggregate
FNV/byte counts so allocation and GC remain production-shaped. Run at least
twelve balanced native i686 pairs per workload under a 64-MiB heap. Accept
only if both route-shaped primaries improve by at least +5% with a clear win
majority, every output is exact, and neither control regresses below -0.5%.
Then run `just verify`, exact KEmulator sound/sound-test/touch gates, release
validation, and record that native phoneME proves synthesis cost only: the
real MMAPI `createPlayer`/`realize`/`prefetch`/`start` boundary still requires
KEmulator or physical-device evidence.

**Correctness and artifact results.** The independent differential freezes
the complete pre-NJIT-010 scalar implementation rather than sharing the new
helper. It compared 11,941 input combinations, including null results,
maximum-duration tones, deterministic random ADSR, slides, note mode, pan,
and every waveform, and found all 52,525,193 returned bytes equal. Seven
immutable fixtures additionally retain exact output lengths and SHA-256:

| Fixture          |   Bytes | SHA-256                                                            |
| ---------------- | ------: | ------------------------------------------------------------------ |
| Waternet 262 Hz  |   6,711 | `f844e1b3d5ea49578821154e55e241bca7dcef93e0f37cbe527324c4cfec23f1` |
| Rubido 900 Hz    |   1,111 | `b35dcb15e954cac510abd22f2c6f82fc0ad1d9177c89071e3ee9ec571a4a0814` |
| upward slide     |   8,044 | `22e25f167915097da1053114496e36d236ca1999fa060452b262d1c50f26d579` |
| downward slide   |   8,044 | `f4c91b123fbc1ac0408d31197a4452310f14c9fd8d61a8c2e8753c2f4be111c1` |
| ADSR             |   1,111 | `ca59388dd6a48d7a5de7e49dfb46a95d0a3435e6d1b20b55d1b07ea28eefacc7` |
| note-bend slide  |   2,444 | `14e39141f9d6f65a3f4c94c590152ac42fc8db2d8e3dc691a7084c9b702c4f37` |
| maximum duration | 272,044 | `eebdb0b1dd1ad9f674ce644e023e04f9b92ca3a6ce2062b6526e4bb4380378cc` |

`just test` and `just verify` pass, including Java source/target 1.3, the CLDC
bootclasspath lint, classfile 47, preverification, the full replay/state,
trap, cache, release, and counterless matrices. Native i686 phoneME exact
verification passes Waternet, Rubido, and Untangle. The final counterless
exactness artifact is
`3040229cf4640b41825e8eecc9a79550ed4c69a318fd2bf36704fbec2dc97102`;
diagnostic/counterless `WasmInterpreter.execute` remain 7,039/7,007 bytes.

The retained source file has SHA-256
`110d579a3e3519d4fbd4f0dcaa4c09105ecf5d9d129c40b304b833ee65e1e863`.
The target-47 `Wasm4Pcm.class` is 3,130 bytes
(`cc30bb53e207c834781d2c36c29971b75931b32ab9e81589f9b20636930f22f1`)
and its preverified form is 4,663 bytes
(`d1f0e28699d7b89cbb18c1bb6d5e19a050c3f2e1a1af34f10d2de07bda3fcb6d`).
Against the baseline, `synthesize` grows from 638 to 683 code bytes, the new
helper is 249 bytes, and the class grows by 499 target-47 bytes and 831
preverified bytes. The helper is called once per qualifying tone; the
candidate adds no field, persistent allocation, output allocation, or device
heap state.

The verified uncommitted station/base release JARs are 229,203/226,689 bytes,
366 bytes larger than the baseline, with SHA-256
`fc77ae06cce732242afdba7e29e5158f151b1f7fb93ba3f961eff79964c60a43`
and
`661ce78287f536e5a87844cea7258a8abdd1e8f403ba134f9081b7cd02e83dba`.

**Clean timing artifacts and commands.** The clean snapshots are temporary
commits
`d1eb1973f46ee9dedbcc832492dfb03581b20d5a` for the baseline and
`6f54debb12c8d18c293b07cc21e550c6ac2cc873` for the candidate. Their only
non-build source difference is `Wasm4Pcm.java`; both report an empty
`git status`. Their baseline/candidate preverified PCM-class hashes are
`7197e0e2dbe13a1520b36978fc9cbfdc8989c7d6bed012a0a801c07104539666`
and
`d1f0e28699d7b89cbb18c1bb6d5e19a050c3f2e1a1af34f10d2de07bda3fcb6d`.
The fixed runner and raw evidence are retained under
`/tmp/w4me-njit010-current.dz2WZs/` for the lifetime of this host session.
Each pair alternated order and invoked:

```sh
.local/phoneme/cldc_vm_r -EnableTicks =HeapCapacity64M \
  -classpath .local/phoneme/classes.zip:<preverified-tree> \
  w4me.PhoneMePcmBench <workload> <cycles> <sample>
```

The complete final commands and gates were:

```sh
bash /tmp/w4me-njit010-current.dz2WZs/run-pcm-pairs-v3.sh waternet 50 12
bash /tmp/w4me-njit010-current.dz2WZs/run-pcm-pairs-v3.sh rubido 200 12
bash /tmp/w4me-njit010-current.dz2WZs/run-pcm-pairs-v3.sh slide 50 12
bash /tmp/w4me-njit010-current.dz2WZs/run-pcm-pairs-v3.sh adsr 200 12
just test
just verify
tools/phoneme/run.sh verify
tools/kemu/run.sh verify sound
tools/kemu/run.sh verify sound-test
tools/kemu/run.sh verify touch
```

Every timed invocation matched calls, output byte count, aggregate FNV, and
allocation shape before its wall clock was accepted. Raw baseline/candidate
microseconds per complete tone sequence were:

| Pair |    Waternet B/C |    Rubido B/C |       slide B/C |      ADSR B/C |
| ---: | --------------: | ------------: | --------------: | ------------: |
|    0 | 23,440 / 17,820 | 4,290 / 3,350 | 18,060 / 17,920 | 4,560 / 4,565 |
|    1 | 23,280 / 17,660 | 4,310 / 3,310 | 18,080 / 18,020 | 4,515 / 4,585 |
|    2 | 23,140 / 17,720 | 4,450 / 3,350 | 18,000 / 17,980 | 4,545 / 4,520 |
|    3 | 23,000 / 17,640 | 4,365 / 3,455 | 18,000 / 18,040 | 4,555 / 4,545 |
|    4 | 23,320 / 17,520 | 4,280 / 3,225 | 18,020 / 18,200 | 4,560 / 4,535 |
|    5 | 23,120 / 17,660 | 4,275 / 3,255 | 18,020 / 18,060 | 4,540 / 4,475 |
|    6 | 23,340 / 17,700 | 4,245 / 3,250 | 18,060 / 18,020 | 4,525 / 4,560 |
|    7 | 23,160 / 17,680 | 4,235 / 3,280 | 17,980 / 18,320 | 4,560 / 4,545 |
|    8 | 23,240 / 17,640 | 4,285 / 3,245 | 17,980 / 17,980 | 4,580 / 4,570 |
|    9 | 23,240 / 17,740 | 4,205 / 3,290 | 18,120 / 17,960 | 4,660 / 4,560 |
|   10 | 23,440 / 17,760 | 4,260 / 3,295 | 18,140 / 18,280 | 4,730 / 4,615 |
|   11 | 23,920 / 17,840 | 4,295 / 3,245 | 17,940 / 18,300 | 4,630 / 4,660 |

Waternet measures **+24.036%**, +5,610 us/sequence, and 12/12 wins at
20 us resolution. Rubido measures **+23.321%**, +997.5 us/sequence, and
12/12 wins at 5 us resolution. The slow-path controls remain within the
predeclared limit: slide is -0.111% with 5/6/1 wins/losses/ties, and ADSR is
+0.274% with 8/4. All four summaries report balanced order,
`source-clean=yes`, and `evidence-quality=measured`.

KEmulator's `sound`, `sound-test`, and `touch` checks pass; `sound-test`
retains framebuffer FNV `a4b700fa`. The selected emulator backend reports
`D-playTone`, so these results prove that the candidate does not break the
MIDP audio/control integration but do not time or audibly validate the
`audio/x-wav` MMAPI player path. No physical-phone audio timing was available.

**Verdict.** Accept. The isolated production shape exceeds both +5% primary
gates unanimously, keeps the general slide and ADSR paths above the -0.5%
floor, preserves every returned byte and all release/device integration
checks, and costs no persistent heap. Report the +23--24% values only as PCM
sequence synthesis improvements during tone emission; they are not whole-game
FPS gains and do not include MMAPI player setup or hardware playback.

### Performance-corpus gate: Game of Life idle generation

**Status:** `verified`.

Game of Life was already present in the host full-state corpus and the
KEmulator generic workload, but not as an exact route in the native phoneME
workflow. Passing its cartridge name to `tools/phoneme/run.sh` staged no input
or oracle files, verified zero checkpoints, and inherited the generic
60-extra-frame default. One frame is already a complete heavy workload, so
that implicit invocation was both weakly checked and needlessly long.

The new immutable `idle-1` route fixes input at gamepad `0`, mouse
`32767,32767`, and buttons `0`, then checks frame zero against:

- framebuffer SHA-256
  `36257a0b35add0f58649174d73f7d568a82f0ca1a251639cb1acc24ae0118fda`;
- framebuffer FNV-1a `a9255758`;
- palette `00fff6d3,00004231,00764462,00edb4a1`;
- zero tone and disk events;
- cartridge SHA-256
  `ca57b23b8bda728a6f92848f8981cfb7837c1c389639cc568c29fddca597d4d3`.

The host replay validates the full framebuffer SHA-256, palette, input memory,
and empty side effects. The existing full-state differential independently
reports exact 64-KiB memory/globals/table state with final memory SHA-256
`45198d7efef567bfe59fee600cc06a41f288bfcf959d46716f66cfee3dc5cc5a`.
The CLDC phoneME route consumes one checkpoint and reproduces
12,802,761 logical instructions, 5,565,951 outer dispatches, 482,291 compact
calls, and 6,407,444 compact instructions with the current integer compact
configuration.

`just bench` and `tools/phoneme/run.sh verify` now include this cartridge.
When `--extra-frames` is absent, existing recorded routes retain their
60-frame tail while Game of Life selects one frame; an explicit value still
overrides all selected routes. This prevents an accidental 60-generation run
without changing any established route baseline. The receipt records the
resolved value per cartridge.

On native i686 phoneME, the one-frame route took 3.40--3.56 seconds in
exploratory dirty-tree checks. A two-pair balanced `current` versus
`seven-opcode` exercise consumed the exact checkpoint on all four VM runs and
produced the normal paired CSV/statistic. Its +0.566% median with one win and
one loss is intentionally not a performance verdict; that run exists only to
prove the A/B path. The workload adds no production class, release-JAR, or
device heap cost. Its role is a high-density integer/control-flow primary or
no-regression control for future candidates.

### NJIT-020 preparation: production-shaped Game of Life deep profile

**Status:** `verified`.

**Trigger and scope.** The owner reports that Game of Life remains extremely
slow on a physical Galaxy S25+ through J2ME Loader, so the next candidate uses
the exact `idle-1-v1` Game of Life frame as its primary workload rather than
selecting only by average corpus coverage. Native i686 phoneME remains the
authoritative no-JIT A/B judge; the physical report proves target relevance
but does not yet identify the responsible VM handler or provide a paired
effect.

The first clean `main@924c1d5f15577531d3999f4974a141109b701ce9`
host-exact profile produced artifact
`5da7dd191969eb64a9b32f47973dba080a853da72cf796c0112b2c1467792e28`
and passed all seven full-state differentials. It also exposed a measurement
configuration defect: `GenericCorpusProfiler` labels its tier side
`CURRENT`, whose explicit test variant has `integer-compact=off` and
`host-import-id=off`, while both defaults are enabled in the production
`WasmInterpreter` and in the phoneME `host-import-id` artifact. The profiler's
raw generic opcode stream is still deterministic, but its 5,848,629 outer
dispatches, 430,771 compact calls, 5,918,928 compact instructions, compact
break metadata, and pre-load-tee pair counts are not the current production
shape. They must not select NJIT-020.

**Instrumentation correction and gates.** Keep profiling execution outside
the compact executor so every dynamic W4IR opcode, pair, triple, and function
entry remains visible, but decode the production load-tee stream and build
compact-region metadata with integer compact opcodes enabled. Run the separate
tier pass with the complete `HOST_IMPORT_ID` production variant. The corpus
full-state differential must compare `REFERENCE` directly with
`HOST_IMPORT_ID`, not the stale intermediate `CURRENT` variant.

The corrected report must:

- retain Java source/target 1.3 and all seven exact full-state workloads;
- identify its artifact, source HEAD, cartridge, route, and complete variant
  configuration;
- reproduce the existing exact Game of Life logical count `12,802,761`;
- reproduce the phoneME production-tier counts `5,565,951` outer dispatches,
  `482,291` compact calls, and `6,407,444` compact instructions;
- replace the stale load/store/signed-comparison compact-break and sequence
  data before a production candidate is chosen.

This correction changes only host test/profiling code and the benchmark
wrapper. It adds no MIDlet class, release-JAR byte, persistent device heap, or
runtime branch. After the corrected report and independent read-only reviews
agree on a high-coverage mechanism, append NJIT-020's isolated implementation,
exactness, bytecode, heap, and at-least-twelve-pair native phoneME acceptance
plan before changing production code.

The corrected Java 1.3 artifact is
`ad211ace5b3697cf0540d82f1d1ebab5d60cb52ab2b1c90a09aeb4f7e3ab3425`;
the complete report SHA-256 is
`7ba74f3fc820a95b62abddde3e5d2c0434397254cf2b219640805d73cf646fad`.
All seven `REFERENCE` versus `HOST_IMPORT_ID` full-state workloads pass. Both
printed configurations now include the production load-tee stream, integer
compact eligibility, and numeric host-import dispatch. Game of Life reproduces
exactly `12,802,761` logical instructions, `5,565,951` outer dispatches,
`482,291` compact calls, and `6,407,444` compact instructions. The raw
profiling pass executes every decoded instruction outside compact execution
and records 9,888,650 production-stream dispatches for coverage only.

The frame has no tone or disk events and executes no host drawing import.
Guest code writes the framebuffer directly, so the phoneME route's
multi-second update is not caused by PCM, MMAPI, blit, rect, ARGB conversion,
or presentation. `W4Canvas` framebuffer conversion, `drawRGB`, touch overlay,
and `flushGraphics` remain outside this headless timing boundary and need a
separate physical-device phase split, but optimizing them cannot remove the
measured 12.8-million-instruction guest update.

### NJIT-020: native bulk copy for defined-function arguments

**Status:** `accepted`.

**Selection evidence and mechanism.** The corrected Game of Life profile has
281,600 defined-function calls in one frame:

| Function | Entries | Parameters | Declared locals | Raw dispatches |
| -------: | ------: | ---------: | --------------: | -------------: |
|        7 |  25,600 |          2 |               1 |        870,400 |
|        8 | 204,800 |          2 |               1 |      5,096,038 |
|        9 |  51,200 |          3 |               0 |      1,536,000 |

The current `callFunction` clears its reused local frame and then copies every
argument from the value stack with an interpreted Java loop. These calls copy
614,400 `long` slots per Game of Life frame. No post-warmup allocation is
involved: functions 7 and 8 reuse one three-slot frame at the same call depth,
function 9 also fits that frame, while the exported update frame has twelve
slots. The candidate leaves full frame clearing unchanged, handles one
argument with one scalar assignment, handles two or more with
`System.arraycopy(values, argumentBase, locals, 0, argumentCount)`, and does
nothing for zero arguments. It then performs the existing `valueTop` update
and function entry unchanged.

This is materially different from the rejected declared-local-only zeroing
candidate. That experiment changed which slots were cleared and regressed the
Duck Maze phoneME route by -0.484%. NJIT-020 preserves the complete clear and
changes only the subsequent copy implementation. CLDC 1.1 provides primitive
`System.arraycopy`, and phoneME implements it below the Java bytecode
interpreter.

An isolated source/target-1.3, preverified native i686 phoneME microbenchmark
used non-overlapping `long[]` arrays and the same dynamic length/source-offset
shape. One five-million-iteration selection run measured:

| Length |  Manual loop | `System.arraycopy` | Relative copy time |
| -----: | -----------: | -----------------: | -----------------: |
|      2 | 64.8 ns/copy |       48.2 ns/copy |             -25.6% |
|      3 | 86.8 ns/copy |       49.0 ns/copy |             -43.5% |

These component figures justify a production A/B but are not a game-speed
claim. The exact full-call context includes frame clearing, type lookup, Java
method entry/return, W4IR execution, and phoneME layout effects.

**Alternatives rejected for this iteration.** A transformed drawing fast path
has zero coverage in this frame. A new `i32.gt_s + br_if` fusion covers
408,161 pairs but repeats the mechanism of the rejected compare-branch batch,
which removed dispatches yet regressed Rubido by -0.214%, and risks compact-run
erosion. `i32.load8_u + local.set` covers 202,884 pairs and remains a plausible
later W4IR batch, but needs a format bump and two-instruction budget/trap
handling; the analogous accepted `i32.load + local.tee` produced only +0.419%
on Game of Life. Inlining address helpers covers more than 510,000 memory
operations but is a larger multi-site reconsideration of NJIT-005. The
argument-copy candidate is chosen first because it has independent VM-level
support, no W4IR/cache change, no persistent heap, and one semantic boundary.

**Baseline and acceptance plan.** The production baseline is clean
`main@924c1d5f15577531d3999f4974a141109b701ce9`, with native i686 phoneME
`bb4866969747430bb619d139c75ab31982e8967aa0e2dcc3e278fcc7920839a2`,
CLDC classes
`117820661071b411b3937e8c708a9581aa48ba06fd06e24eadb5ebbf2036afbe`,
and preverifier
`4fe0d1b160ac7f18c4f7489917a1d868ff82df50842ca718729b098b25f2f50c`.
Record clean baseline/candidate preverified-tree hashes after the isolated
source snapshots are built.

Correctness must cover zero, one, two, and three-or-more parameters, reused and
recursive frames, 32/64-bit raw argument bits, zeroed declared locals, return
values, traps, instruction budgets, all seven full-state workloads, exact
Game of Life route state, Java 1.3/CLDC lint, classfile 47, preverification,
release JARs, and existing W4IR cache/device gates. Inspect `callFunction`
target-47 and preverified bytecode, class/JAR growth, and confirm zero new
fields, arrays, or persistent heap.

Run at least twelve balanced clean-source native i686 pairs with the exact
one-frame Game of Life route as primary. Require a median speedup of at least
+0.5%, at least eight wins, identical logical/tier/oracle counters, and a
timer-resolved effect. Then run Waternet, Rubido, and Untangle controls and
reject below -0.5%. Only after native acceptance should a physical J2ME Loader
JAR be used to split update versus render time and check that the benefit
survives its different VM; absence of that physical follow-up narrows the
claim to native no-JIT phoneME rather than invalidating exactness.

**Correctness, bytecode, and artifact results.** The retained implementation
changes only the argument copy after the existing complete local-frame clear.
`DefinedCallArgumentCopySmoke` exercises zero, one, two, and five parameters;
raw i32, i64, f32, and f64 bits; sequential and recursive frame reuse; zeroed
declared locals; return values; a second invocation on the same module; and
the exact accepted/denied instruction-budget boundary. It reports:

```text
PASS defined-call-arguments arities=0,1,2,5 raw-types=i32,i64,f32,f64 frame-reuse=sequential,recursive logical=271 budget=exact
```

`just verify` passes the focused test, all replay and seven full-state
workloads, the exact Game of Life checkpoint, traps, budgets, resident and
cached W4IR, Java source/target 1.3, CLDC API lint, classfile 47,
preverification, release integrity, and counterless exactness. The final
counterless artifact is
`16a3364cf2f635eaea07d0993716a2025c7b5fd7900158d6a4869dfb3b84868c`.
Diagnostic/counterless `execute` remain 7,039/7,007 bytes and retain the dense
`tableswitch`.

The isolated clean target-47 `callFunction` grows from 919 to 928 code bytes.
The unpreverified `WasmInterpreter.class` grows from 52,956 to 52,969 bytes,
with hashes
`ac6f075c0ef5b900adaf7167f09fefb04d1567445f6d17c3788c2d9254d0d20a`
and
`4f7ebc4dd671c1c7311282b6da549326fd0658af2ecedf98e3729caf3e498e76`.
Its preverified form grows from 79,574 to 79,587 bytes, with hashes
`1ed81325f2aa6419ed05afcc000349b93d9e30f710c381d501e393be74e1f79b`
and
`89f88b9d3ce8cf70a2782ff7fe0e332177cb20de68940a44d0f615cbefbed6c3`.
The final station/base release JARs are 229,207/226,693 bytes, four bytes
larger than the baseline, with SHA-256
`ab1067d494818feae0f933efe196a52f4b965b0e00862a7a6bc8b2425eba28fb`
and
`4752473a5a81925003ec16459cdedd811f987eed9b03c7876e365b9aa11c34df`.
No field, array, retained object, W4IR token, cache-format byte, or persistent
heap state is added. `System.arraycopy` operates on the existing value and
local arrays and does not allocate.

**Clean native artifacts and commands.** The fixed snapshots under
`/tmp/w4me-njit020.DNUacb/` are clean temporary commits
`956740356ecfc7c0c2af8abfd707f2e4ff8863e8` for the baseline and
`556fd072e71fbb8ae338f54a79704ef60cc33ee1` for the candidate. Their only
production difference is the argument-copy hunk in `WasmInterpreter`.
Complete staged phoneME artifacts are respectively
`0e1d032af5b14eb97d86014e2ef4fb252c7ec06627e66d72ac876c94d576614f`
and
`039bf358fe35847b92eed13afe9e1e6f5b4623b9604a19a349eefafbbef183e4`.
Both receipts bind the native i686 VM, CLDC classes, and preverify hashes
recorded above, `source-dirty=no`, Java 1.3, W4IR format 16, a 64-MiB judge
heap, and the production `host-import-id` configuration.

The clean artifacts were built and sanity-run with:

```sh
PHONEME_HOME=.local/phoneme tools/phoneme/run.sh bench \
  waternet rubido untangle game-of-life-zig-edition \
  --candidate host-import-id --reps 1
bash /tmp/w4me-njit020.DNUacb/run-pairs.sh
bash /tmp/w4me-njit020.DNUacb/run-control-pairs.sh waternet
bash /tmp/w4me-njit020.DNUacb/run-control-pairs.sh rubido
bash /tmp/w4me-njit020.DNUacb/run-control-pairs.sh untangle
```

Every invocation matched checkpoints, logical instructions, outer dispatches,
compact calls/instructions, branch-fast metadata, W4IR format, and all other
deterministic receipt fields. Raw microseconds per frame follow:

| Pair | Order           |      Game of Life B/C |    Waternet B/C |        Rubido B/C |  Untangle B/C |
| ---: | --------------- | --------------------: | --------------: | ----------------: | ------------: |
|    0 | baseline first  | 3,491,000 / 3,532,000 | 17,313 / 17,163 | 105,310 / 105,457 | 4,778 / 4,802 |
|    1 | candidate first | 3,499,000 / 3,486,000 | 17,339 / 17,156 | 106,116 / 105,116 | 4,739 / 4,743 |
|    2 | baseline first  | 3,540,000 / 3,445,000 | 17,156 / 17,222 | 105,844 / 105,480 | 4,789 / 4,765 |
|    3 | candidate first | 3,444,000 / 3,502,000 | 17,366 / 17,156 | 105,542 / 105,651 | 4,804 / 4,817 |
|    4 | baseline first  | 3,439,000 / 3,498,000 | 17,294 / 17,307 | 105,705 / 105,348 | 4,841 / 4,773 |
|    5 | candidate first | 3,506,000 / 3,460,000 | 17,228 / 17,235 | 110,069 / 105,232 | 4,736 / 4,813 |
|    6 | baseline first  | 3,455,000 / 3,445,000 | 17,156 / 17,078 | 105,736 / 104,790 | 4,806 / 4,758 |
|    7 | candidate first | 3,487,000 / 3,422,000 | 17,431 / 17,300 | 105,186 / 105,496 | 4,802 / 4,802 |
|    8 | baseline first  | 3,449,000 / 3,355,000 |               — |                 — |             — |
|    9 | candidate first | 3,430,000 / 3,402,000 |               — |                 — |             — |
|   10 | baseline first  | 3,461,000 / 3,479,000 |               — |                 — |             — |
|   11 | candidate first | 3,460,000 / 3,420,000 |               — |                 — |             — |

Game of Life measures a median +20,500 us/frame, **+0.594%**, with 8 wins
and 4 losses. The 1,000 us/frame timer resolution is much smaller than the
median effect, order is balanced, and the result clears the predeclared +0.5%
and 8/12 acceptance gates exactly. Controls measure +0.603% Waternet
(+104.5 us/frame, 5/3), +0.341% Rubido (+360.5 us/frame, 5/3), and -0.042%
Untangle (-2.0 us/frame, 3/4/1). The Untangle value is below its 2.174
us/frame resolution and is conservatively classified as exploratory; it is
also far inside the -0.5% no-regression limit. Waternet and Rubido are
timer-resolved. All raw outputs and paired summaries remain under
`/tmp/w4me-njit020.DNUacb/evidence/` for the lifetime of this host session.

**Verdict.** Accept. The isolated native-copy shape meets the predeclared
Game of Life floor and win count, stays exact, adds no persistent memory or
format cost, and has no resolved route regression. The effect is deliberately
reported as only a native i686 no-JIT phoneME whole-update improvement: about
20.5 ms removed from a roughly 3.45-second generation. It does not make Game
of Life interactive and is not yet evidence of an improvement under the
Galaxy S25+ J2ME Loader VM. Reconsider if physical-device phase timing reverses
the result, a future phoneME/class-layout change changes its sign, or a later
call-lowering design removes the argument copy entirely.

### NJIT-021: signed compare plus direct conditional branch

**Status:** `rejected`.

**Hypothesis, source, and dynamic coverage.** The corrected one-frame Game of
Life profile contains the following adjacent pairs after the retained
production fusion pass:

| Pair               | Game of Life count | Other routed-corpus count |
| ------------------ | -----------------: | ------------------------: |
| `i32.lt_s + br_if` |            204,800 |                       915 |
| `i32.gt_s + br_if` |            408,161 |                     6,430 |
| `i32.le_s + br_if` |            203,362 |                       207 |
| **Total**          |        **816,323** |                 **7,552** |

The three pairs account for 8.26% of Game of Life's 9,888,650 profiled
production-stream dispatches and can remove at most 14.67% of its 5,565,951
outer dispatches. The exact tiered frame remains 12,802,761 logical
instructions. The expected wall benefit is unresolved until native phoneME
A/B because `i32.gt_s` is currently compact-eligible, while `i32.lt_s` and
`i32.le_s` are enabled only by the integer compact option; replacing the pair
with an outer fused handler changes compact-region boundaries as well as
dispatch count.

This candidate revisits but does not repeat the rejected format-15
compare-branch batch from `optimize-generic-interpreter-tiering`. That batch
covered `i32.eqz`, `i32.eq`, `i32.lt_u`, and `i32.ge_u`, executed taken
branches through the legacy `branch()` helper, saved 75,581 Rubido outer
dispatches, and measured -0.214% on the decisive 16-pair Rubido repeat. Its
ledger explicitly allowed reconsideration after static branch descriptors or
branch-capable compact execution changed the target cost. Since then the
accepted pc-indexed direct ordinary-branch path has replaced binary search and
legacy control-stack lookup for validated arity-zero/one sites, and Game of
Life has added a new exact primary workload with more than ten times the old
saved-dispatch coverage. NJIT-021 uses only the three previously untested
signed relations and the direct descriptor arrays; it does not restore the
old four-opcode batch.

**Selected mechanism and affected files.** Add one tail W4IR opcode representing
`signed-compare + br_if`; encode the relation kind and retained branch depth
in its existing operand fields, keep the second `br_if` token, and keep the
original branch descriptor mapping at `pc + 1`. The outer handler:

1. charges the comparison at the existing dispatcher boundary;
2. pops the two i32 operands and computes `lt_s`, `gt_s`, or `le_s`;
3. charges and checks the original `br_if` instruction before any branch
   transfer;
4. restores the comparison result on the value stack if that intermediate
   budget check traps;
5. on a taken branch, uses `branchFastSiteByPc[pc + 1]` and the accepted
   arity-zero/one direct transfer, with the canonical legacy fallback for a
   function return, larger arity, or rejected descriptor;
6. advances by two original instructions when not taken.

The fused opcode remains compact-eligible with span one. Compact execution
performs only the original comparison and reaches the retained outer `br_if`,
preserving the current compact-region topology; outer execution handles both
instructions and uses the direct descriptor. `WasmModule`, `WasmInterpreter`,
focused differential/profile tests, explicit benchmark variants, opcode
labels, cache-format checks, and the phoneME receipt plumbing are affected. No
cartridge fingerprint, hard-coded function index, or Game-of-Life-specific
runtime path is permitted.

The dense W4IR cache format must rise from 16 to 17 (and the non-dense
development format from 14 to 15) because the function fingerprint is
calculated before fusion and cannot distinguish the new post-fusion stream.
The W4IR array length, branch descriptor arrays, direct-branch arrays, local
frames, and persistent heap shape otherwise remain unchanged.

**Alternatives and research verdicts.** A full decode-time inline of Game of
Life functions 7, 8, and 9 is rejected for this cycle: preserving their
callee-relative branch heights, explicit return, local namespace, profiler
identity, cache-hit path, and budget semantics requires new persisted
metadata and approximately 6-7 KiB of additional W4IR/direct-map storage for
one module. A narrower caller-side defined-leaf path would remove only one
Java frame while retaining nested `execute`; its estimated 0.3-1.0% effect
and risk of increasing `execute` max locals make it a later spike.
`i32.load8_u + local.set` is retained as the next bounded candidate: it covers
202,884 Game of Life pairs and has a successful load/tee precedent, but it is
narrower than the current control-flow reserve and cannot substitute for it
merely because it is easier.

**Baseline identity and planned evidence.**

- source: clean `main@63f82aad4399321be6a143ddb889ac1e94430573`;
- retained NJIT-020 staged artifact:
  `039bf358fe35847b92eed13afe9e1e6f5b4623b9604a19a349eefafbbef183e4`;
- retained counterless exactness artifact:
  `16a3364cf2f635eaea07d0993716a2025c7b5fd7900158d6a4869dfb3b84868c`;
- station/base JAR SHA-256:
  `ab1067d494818feae0f933efe196a52f4b965b0e00862a7a6bc8b2425eba28fb`
  and
  `4752473a5a81925003ec16459cdedd811f987eed9b03c7876e365b9aa11c34df`;
- native i686 phoneME:
  `bb4866969747430bb619d139c75ab31982e8967aa0e2dcc3e278fcc7920839a2`;
- CLDC classes:
  `117820661071b411b3937e8c708a9581aa48ba06fd06e24eadb5ebbf2036afbe`;
- preverify:
  `4fe0d1b160ac7f18c4f7489917a1d868ff82df50842ca718729b098b25f2f50c`.

Record new clean baseline and candidate staged-tree hashes after building the
isolated snapshots. Focused correctness must cover every signed relation at
minimum/maximum and neighboring values; taken and untaken branches; arity-zero,
arity-one, loop, block, and function-return targets; stack underflow; every
budget point before the comparison, between comparison and `br_if`, and after
the branch; direct-path hits and canonical fallback. The complete seven
full-state workloads, exact Game of Life checkpoint, profiling stream,
resident/paged/promoted W4IR, format-16 rejection and rebuild, Java 1.3, CLDC
API lint, classfile 47, preverification, release JARs, dense tableswitch,
StackMaps, and the 7,800-byte `execute` limit must pass. Record class/JAR size,
method bytes, max locals, W4IR topology, compact counters, and zero persistent
heap growth.

Run at least twelve balanced native i686 phoneME pairs on the exact one-frame
Game of Life route. Require a timer-resolved median improvement of at least
+0.8%, at least nine wins, identical logical/oracle/tier fields, and no
resolved regression worse than -0.5% on Waternet, Rubido, or Untangle. The
primary A/B changes only this signed direct-branch fusion. Static dispatch
reduction, HotSpot, KEmulator, and QEMU timing cannot accept it.

**Correctness, bytecode, cache, and heap gates before timing.** The isolated
implementation and its focused fixture pass `just verify`. The focused smoke
contains seven static fusion sites and 13 dynamic executions across all three
signed relations. It covers signed minimum/maximum and adjacent values,
taken and untaken branches, arities zero through two, loop, block, and
function-return targets, direct hits and canonical fallback, stack underflow,
every outer and compact instruction-budget boundary, active lower stack, and
the profiling stream. Its exact receipt is:

```text
PASS signed-compare-branch-fusion sites=7 direct=4 fallback=3 dynamic=13 outer-dispatches-saved=13 compact-outer-saved=9 compact-comparisons=4 logical=179 compact-logical=90187 budget=outer+compact stack=exact profiler=exact
```

The complete counterless exactness artifact is
`8243280e981df134426a9d5c6f24a410b82697ef7fda0ae5a221f0d32a1d917e`.
All seven `REFERENCE` versus production-candidate full-state workloads pass
at W4IR format 17, including the exact one-frame Game of Life result at
12,802,761 logical instructions, memory SHA-256
`45198d7efef567bfe59fee600cc06a41f288bfcf959d46716f66cfee3dc5cc5a`,
and framebuffer FNV-1a `a9255758`. Java source/target 1.3, the CLDC-only API
build, classfile 47, StackMap preverification, dense `tableswitch`, both
release JARs, cartridge integrity, traps, budgets, and the complete host test
matrix pass. KEmulator independently passes both the generic 60-frame W4IR
execution route and the old-format rejection/rebuild, build, cache-hit,
descriptor, 12-slot paging, promotion, and compact-execution probe.

The diagnostic and counterless target-47 `execute` methods are respectively
7,340 and 7,308 code bytes, both below the 7,800-byte ceiling and both with
`max_stack=10` and `max_locals=133`. The candidate unpreverified diagnostic
and counterless interpreter classes are 53,584 and 53,498 bytes. The
preverified release interpreter class is 80,716 bytes. Candidate station/base
release JARs are 230,122/227,608 bytes with SHA-256
`df2c59988c4a9bbda6e9db328ecd00acbc3f02602aafc848f38471b4a891a96d`
and
`121153be16e49a3396ce280766748d050b1a2c1027f7e96b20a4644cf2acb536`.
The format bump adds one opcode but no W4IR record, branch-descriptor,
direct-branch, local-frame, or value-stack slot. The candidate selection flag
is threaded only through decode-time method arguments; no persistent module
or interpreter field, array, retained object, or device-heap state is added.
Clean source snapshots, exact staged-tree hashes, and final baseline deltas
remain part of the authoritative timing gate rather than this dirty-tree
correctness receipt.

**Clean artifacts and timing method.** The stable source snapshot is clean
`63f82aad4399321be6a143ddb889ac1e94430573`; the isolated candidate snapshot
is clean temporary commit
`0aeacca9c80efcffab0b3c999ffdee0d66048445`. Their complete phoneME build
artifacts are respectively
`039bf358fe35847b92eed13afe9e1e6f5b4623b9604a19a349eefafbbef183e4`
and
`4f0f7788ec93875b87e0c1919a7337899fee6dfbbba6db1ffb90de1c1b471406`;
their production counterless preverified class trees are
`559cf436bf9eafe3d8ed515504ce557c52da9c1ddcfba0e0b113d832ffbe89f5`
and
`e2b6d624629669d67c2d1f5a2117ab5eb1b11505e1155393ea3647e01f4be89f`.
Both snapshots report `source-dirty=no`, Java source/target 1.3, the same
native VM, CLDC, preverify, cartridge, oracle, and 64-MiB heap identities.
The expected W4IR format difference is 16 versus 17; all logical, checkpoint,
branch-metadata, framebuffer, palette, input, tone, and disk fields match.

Two timing layers distinguish the intended fusion from whole-class layout:

1. A same-class isolate uses the candidate class tree for both sides and
   switches the decode-time fusion flag only. Both sides therefore have format
   17 and byte-identical classes. The Game of Life staged tree is
   `b146dc6fc3786f4e4fc4e5410347a56aef0959256a5b2fb99f3b5dd3f6e92c25`;
   routed controls use
   `362fd1785da10f2cde60a4cd5ff3cc61f00381e27c74b2407dd23f0455a1ac82`.
2. The decisive production comparison executes stable format-16 classes
   against candidate format-17 classes, with no runtime candidate branch. It
   therefore includes the actual method, class, constant-pool, and handler
   layout that would ship.

The same-class raw baseline/candidate microseconds per frame were:

| Pair |      Game of Life B/C |    Waternet B/C |        Rubido B/C |  Untangle B/C |
| ---: | --------------------: | --------------: | ----------------: | ------------: |
|    0 | 3,308,000 / 3,081,000 | 17,026 / 17,045 | 102,255 / 102,325 | 4,773 / 4,750 |
|    1 | 3,312,000 / 3,117,000 | 17,124 / 17,052 | 101,759 / 102,124 | 4,765 / 4,786 |
|    2 | 3,296,000 / 3,136,000 | 17,078 / 16,993 | 101,976 / 102,472 | 4,771 / 4,810 |
|    3 | 3,322,000 / 3,117,000 | 17,039 / 17,104 | 101,759 / 102,379 | 4,765 / 4,782 |
|    4 | 3,756,000 / 3,077,000 | 17,326 / 17,026 | 101,899 / 102,472 | 4,765 / 4,734 |
|    5 | 3,284,000 / 3,014,000 | 17,045 / 16,954 | 102,348 / 102,395 | 4,793 / 4,797 |
|    6 | 3,332,000 / 3,073,000 | 16,921 / 16,771 | 102,186 / 101,992 | 4,821 / 4,765 |
|    7 | 3,289,000 / 3,152,000 | 16,921 / 16,954 | 101,891 / 102,124 | 4,800 / 4,717 |
|    8 | 3,284,000 / 3,106,000 |               — |                 — |             — |
|    9 | 3,314,000 / 3,138,000 |               — |                 — |             — |
|   10 | 3,282,000 / 3,121,000 |               — |                 — |             — |
|   11 | 3,276,000 / 3,149,000 |               — |                 — |             — |

Same-class paired results are +5.654% Game of Life, 12/12 wins;
+0.459% Waternet, 5/3; -0.294% Rubido, 1/7; and +0.199% Untangle,
4/4. This proves that the fusion mechanism is useful on Game of Life and
that its Rubido instruction-stream effect alone stays inside the -0.5%
control limit. It cannot accept production code because it intentionally
removes the candidate's class-layout cost from both sides.

The production raw baseline/candidate microseconds per frame were:

| Pair |      Game of Life B/C |    Waternet B/C | Rubido B/C, first | Rubido B/C, repeat |  Untangle B/C |
| ---: | --------------------: | --------------: | ----------------: | -----------------: | ------------: |
|    0 | 3,236,000 / 3,092,000 | 16,986 / 16,751 | 100,697 / 101,736 |  100,155 / 101,899 | 4,750 / 4,743 |
|    1 | 3,293,000 / 3,095,000 | 16,862 / 17,071 | 101,480 / 102,542 |  100,860 / 101,736 | 4,800 / 4,780 |
|    2 | 3,229,000 / 3,105,000 | 17,098 / 16,986 | 101,279 / 102,038 |  100,837 / 101,713 | 4,826 / 4,769 |
|    3 | 3,291,000 / 3,126,000 | 16,973 / 17,013 | 100,348 / 101,844 |  100,519 / 101,829 | 4,808 / 4,754 |
|    4 | 3,299,000 / 3,090,000 | 17,000 / 17,117 | 101,325 / 102,240 |  100,806 / 102,263 | 4,767 / 4,754 |
|    5 | 3,248,000 / 3,141,000 | 16,830 / 16,758 | 100,643 / 102,054 |  101,488 / 101,821 | 4,795 / 4,780 |
|    6 | 3,285,000 / 3,134,000 | 16,973 / 16,967 | 100,759 / 102,286 |  100,697 / 102,255 | 4,791 / 4,734 |
|    7 | 3,275,000 / 3,117,000 | 16,928 / 16,973 | 101,085 / 102,465 |  100,457 / 101,922 | 4,793 / 4,706 |
|    8 | 3,245,000 / 3,101,000 |               — |                 — |                  — |             — |
|    9 | 3,321,000 / 3,178,000 |               — |                 — |                  — |             — |
|   10 | 3,318,000 / 3,059,000 |               — |                 — |                  — |             — |
|   11 | 3,308,000 / 3,105,000 |               — |                 — |                  — |             — |

Production Game of Life measures **+4.711%**, +154,500 us/frame, and
12/12 wins at 1,000 us/frame resolution. Waternet measures -0.100%,
-17 us/frame, and 4/4. Untangle measures +0.770%, +37 us/frame, and
8/8. The first Rubido control measures **-1.206%**, -1,221 us/frame,
and 0/8; an independent repeat measures **-1.374%**, -1,383.5 us/frame,
and 0/8. Every summary reports timer-resolved effects, balanced order,
clean source, and exact deterministic outputs. Pair CSV SHA-256 values are:

- Game of Life:
  `6c65b989b3f0bf5cb25010e71ab35d23f2a1220814b622019e25d15bfacdfb96`;
- Waternet:
  `709eaacc15bb85d45823cfe5a25f0861472899a21383ffb4d722455fffd12323`;
- first Rubido:
  `86ba0fd8727eeac9a1dbd96aa605a56be3297147e8024881938d6ac7d3b69ea7`;
- repeated Rubido:
  `6566628c3086a65c6677c2a263d5887fa6da1dbe39833235d0e9dd1184c1901b`;
- Untangle:
  `3a2dc086726254411c52cdf96fca92139bb3641102e5539c02488586d6280590`.

The production diagnostic stream confirms the intended topology: Game of
Life outer dispatches fall from 5,565,951 to 4,749,628, exactly 816,323
fewer, while compact calls and compact instructions remain 482,291 and
6,407,444. The candidate grows diagnostic/counterless `execute` from
7,039/7,007 to 7,340/7,308 bytes. The preverified release interpreter grows
from 79,587 to 80,716 bytes; station/base JARs grow from
229,207/226,693 to 230,122/227,608 bytes, +915 each. W4IR rises from format
16 to 17 but adds no record, direct-map entry, field, allocation, or
persistent device-heap state.

**Decision.** Reject and remove the implementation. It delivers a large,
repeatable Game of Life improvement and preserves exact behavior, but the
actual production artifact regresses Rubido by more than twice the
predeclared -0.5% limit in two independent unanimous series. The same-class
isolate localizes the additional loss to whole-class or handler layout rather
than the signed-pair stream alone, but production layout is part of the
shipping cost and cannot be excluded from the verdict. No production commit
is made.

The opcode, format bump, handlers, decode flag, benchmark variants, focused
fixture, and test-runner wiring were then removed exactly. `src/main`,
`src/test`, and `tools` are byte-identical to stable
`63f82aad4399321be6a143ddb889ac1e94430573`; only this durable ledger and
task history remain changed. The restored tree passed `just verify`, including
all seven exact full-state workloads, replay/framebuffer oracles, traps,
instruction budgets, resident/paged/promoted W4IR, Java 1.3, CLDC API lint,
classfile 47, StackMap preverification, release integrity, and counterless
exactness. W4IR is back at format 16, diagnostic/counterless `execute` are
7,039/7,007 bytes, and the stable counterless artifact is
`16a3364cf2f635eaea07d0993716a2025c7b5fd7900158d6a4869dfb3b84868c`.
Station/base JARs are again 229,207/226,693 bytes with SHA-256
`ab1067d494818feae0f933efe196a52f4b965b0e00862a7a6bc8b2425eba28fb`
and
`4752473a5a81925003ec16459cdedd811f987eed9b03c7876e365b9aa11c34df`.

**Reconsideration condition if rejected:** only branch-capable compact regions,
a relation-specific handler that removes a demonstrated inner-dispatch cost,
an implementation that recovers the measured Rubido class-layout loss, or
physical-device evidence that contradicts the native phoneME result.

### NJIT-022: `i32.load8_u + local.set` fusion

**Status:** `rejected`.

**Hypothesis, source, and dynamic coverage.** The corrected production-stream
profile at SHA-256
`7ba74f3fc820a95b62abddde3e5d2c0434397254cf2b219640805d73cf646fad`
contains 202,884 adjacent `i32.load8_u + local.set` pairs in the exact
one-frame Game of Life route. That is 66.46% of its 305,291 dynamic
`i32.load8_u` operations and 2.05% of its 9,888,650 profiled W4IR dispatches.
The pair can remove at most 202,884 of the production tier's 5,565,951 outer
dispatches, or 3.65%, while preserving the frame's 12,802,761 logical
instructions.

The routed controls contain 10,005 pairs on Waternet, 27 on Rubido, 420 on
Untangle, and 155 on Duck Maze; generic Plasma has no recorded occurrence.
This makes Game of Life the primary performance judge and Waternet the only
material route control for the instruction stream, while Rubido and Untangle
remain mandatory whole-artifact no-regression controls.

The mechanism is inspired by the accepted `i32.load + local.tee` W4IR fusion.
That precedent proved exact two-phase instruction-budget accounting and
measured a small positive Game of Life effect, but it neither covers byte
loads nor removes the loaded value from the stack. NJIT-022 therefore does
not repeat a closed candidate. It isolates a different opcode pair with four
times the Game of Life coverage and a distinct final stack effect.

**Selected implementation and affected files.** Add the tail original W4IR
opcode `W4IR_I32_LOAD8_U_LOCAL_SET = 0x1033`. The fusion pass replaces only an
adjacent `0x2d + 0x21` pair when the second token is not a branch target. The
memory offset remains in `operand`, the local index is copied into
`auxiliary`, and the second token is cleared through the existing
`replacePair` mechanism. Do not absorb a preceding
`W4IR_I32_ADD_CONST`, even though that triple has the same 202,884 Game of
Life executions; it is a separate three-instruction candidate with another
budget boundary.

The outer handler preserves the exact unfused observable order:

1. the dispatcher charges `i32.load8_u`;
2. stack underflow traps without mutation;
3. the address is removed from the value stack before address validation;
4. an invalid address traps with the address already removed and the local
   unchanged;
5. a valid byte is zero-extended and placed back on the value stack;
6. the handler charges `local.set` and checks its instruction budget;
7. an intermediate budget trap leaves the loaded byte on the stack and the
   local unchanged;
8. successful `local.set` writes the local and removes the loaded byte.

The compact handler uses the same two-phase accounting as the accepted
load/tee fusion, reports span two, and remains compact-eligible. A single
range check identifies the two special load/local opcodes that account their
first instruction before the handler and their second instruction inside it;
do not add an `A || B` branch to every compact dispatch. The existing
decode-time load/local-fusion boolean controls both handlers only for focused
differentials. Production keeps the default enabled, and the focused
NJIT-022 fixture contains no `i32.load + local.tee` site, so its enabled versus
disabled comparison remains isolated without adding a module field or runtime
selection branch.

`WasmModule`, `WasmInterpreter`, opcode profile labels, the dense-map
differential, one focused WAT/Java smoke, and test-runner wiring are affected.
The dense W4IR format rises from 16 to 17 and the non-dense development format
from 14 to 15 because the cartridge fingerprint is calculated before fusion
and cannot distinguish the new post-fusion stream. The W4IR stride and token
count, branch metadata, stack/local arrays, and persistent heap shape remain
unchanged.

**Exactness, compatibility, and artifact risks.**

- Cover loaded bytes `0x00`, `0x7f`, `0x80`, and `0xff`, the final valid
  address, nonzero static offsets, signed-negative dynamic addresses, and a
  large unsigned static offset.
- Compare complete memory, globals, table, trap class/text, logical
  instruction count, and final results in forced-outer and compact execution.
- Use a sentinel below the address and inspect the private value stack at the
  intermediate budget boundary to prove that the byte remains on top while
  the local is unchanged.
- Sweep the budget before the load, between load and set, and after set in
  both executor modes. An out-of-bounds load must not write the local.
- Count the exact fused sites and saved outer dispatches, and profile compact
  calls/instructions to detect a four-dispatch-region topology loss.
- Exercise resident, paged, promoted, checksum-invalid, and previous-format
  RMS paths. Format 16 must be rejected and rebuilt rather than interpreted
  as format 17.
- Preserve Java source/target 1.3, CLDC 1.1, classfile major 47,
  preverification, dense `tableswitch`, StackMaps, both release JARs, and the
  7,800-byte diagnostic `execute` ceiling.

Expected target-47 growth is approximately 75--120 bytes in `execute` and
90--130 bytes in `executeCompactBlock`; the current stable methods are
7,039/7,007 bytes for diagnostic/counterless `execute`, leaving sufficient
headroom only if the actual compiled forms remain within the gate. The opcode
adds no field, array, allocation, W4IR record, or persistent device-heap byte.
The principal performance risks are compact-region erosion, whole-class
layout sensitivity like NJIT-021, and helper/frame cost overwhelming the
single removed W4IR dispatch.

**Baseline identity.**

- source: clean `main@63f82aad4399321be6a143ddb889ac1e94430573`;
- retained production phoneME artifact:
  `039bf358fe35847b92eed13afe9e1e6f5b4623b9604a19a349eefafbbef183e4`;
- retained counterless exactness artifact:
  `16a3364cf2f635eaea07d0993716a2025c7b5fd7900158d6a4869dfb3b84868c`;
- station/base JAR SHA-256:
  `ab1067d494818feae0f933efe196a52f4b965b0e00862a7a6bc8b2425eba28fb`
  and
  `4752473a5a81925003ec16459cdedd811f987eed9b03c7876e365b9aa11c34df`;
- stable `WasmInterpreter.java` / `WasmModule.java` SHA-256:
  `b12f7966d446529216374e9fa520a1d5375b4dce6dd9e914b0f0f927e1caaf16`
  and
  `6a155c8cc6ba04a007df6790d9c73c8fb39215502ed77a13b816b572ab85da41`;
- native i686 phoneME:
  `bb4866969747430bb619d139c75ab31982e8967aa0e2dcc3e278fcc7920839a2`;
- CLDC classes:
  `117820661071b411b3937e8c708a9581aa48ba06fd06e24eadb5ebbf2036afbe`;
- preverify:
  `4fe0d1b160ac7f18c4f7489917a1d868ff82df50842ca718729b098b25f2f50c`.

Record new clean baseline and candidate staged-tree hashes after producing the
isolated snapshots. Planned correctness commands are `just test`, then
`just verify`, followed by native checkpoint sanity through:

```sh
PHONEME_HOME=.local/phoneme tools/phoneme/run.sh bench \
  waternet rubido untangle game-of-life-zig-edition \
  --candidate host-import-id --reps 1
```

The decisive production comparison alternates clean format-16 baseline and
format-17 candidate preverified trees directly under
`.local/phoneme/cldc_vm_r -EnableTicks =HeapCapacity64M`, with no runtime
candidate branch. Run at least twelve balanced pairs on the exact one-frame
Game of Life route. Require a timer-resolved median improvement of at least
`+0.8%`, at least nine wins, exact oracle/logical/tier fields, and no resolved
regression worse than `-0.5%` on Waternet, Rubido, or Untangle. Record every
raw pair, timer resolution, class/method/JAR delta, W4IR topology, heap delta,
artifact hash, decision, and reconsideration condition before retaining or
removing the code.

**Correctness, profile, and artifact results before timing.** The isolated
candidate passes `just verify`, including the focused differential, all replay
oracles, all seven full-state workloads, traps, instruction budgets, Java
source/target 1.3, the CLDC-only API build, classfile 47, StackMap
preverification, dense `tableswitch`, release integrity, and counterless
exactness. The focused receipt is:

```text
PASS i32-load8-u-local-set-fusion sites=10 update-executions=5 outer-dispatches-saved=5 bytes=00,7f,80,ff,last budget=outer+compact stack=exact traps=negative,oob,large-offset,underflow compact=exact
```

The complete format-17 state matrix retains the exact Game of Life result at
`12,802,761` logical instructions, memory SHA-256
`45198d7efef567bfe59fee600cc06a41f288bfcf959d46716f66cfee3dc5cc5a`,
and framebuffer FNV-1a `a9255758`. KEmulator independently passes the
old-format rejection and rebuild, resident build, cache hit, static
descriptors, 12-slot paging, promotion, and compact execution probe with
framebuffer `2e572184`.

The exact production-profile artifact is
`b7e7478370aeb8a00bf932605818e364c3dca282565366b00895cba6128bf70e`;
its complete report SHA-256 is
`d5bda7a1099dcbb50532a4496f7ae6f12bc6f6b70a5694dbf9ce66ef00d47bfd`.
It corrects the control-route fused counts to 10,075 Waternet, 61 Rubido, 463
Untangle, and 155 Duck Maze while retaining exactly 202,884 Game of Life
executions. The raw profiled Game of Life stream falls from 9,888,650 to
9,685,766 dispatches, exactly one per fused pair. In the production tier,
however, outer dispatches fall only from 5,565,951 to 5,564,838 because most
sites execute inside compact blocks. Compact calls and logical compact
instructions remain exactly 482,291 and 6,407,444, so the candidate does not
erode compact-region topology.

Diagnostic/counterless `execute` grow from 7,039/7,007 to 7,175/7,143 code
bytes and retain respectively 625/657 bytes of headroom below the 7,800-byte
gate. Candidate diagnostic/counterless `executeCompactBlock` are 3,002/2,959
code bytes. The unpreverified diagnostic/counterless interpreter classes are
53,329/53,229 bytes. The candidate counterless exactness artifact is
`1732cc1f5e2e970ffe5ecba5effb67099d97177c86c6bb2ead6f5e12ed3cf440`.
The verified station/base JARs are 229,474/226,960 bytes with SHA-256
`d912cbfcfd9c89fa27d71639b5533c6b52c0e54d000d98c5a95cb9bd6eab1490`
and
`506ca021d17e1ba8664aca168f91a6f4a9dba8634f3bdcefab71806850af88bf`.
No interpreter or module instance field, array, allocation, W4IR record,
direct-map entry, stack/local slot, or persistent device-heap state is added.
`WasmModule` gains only the compile-time static-final opcode constant; the
cache format also changes. Clean baseline/candidate class and staged-tree
deltas remain to be bound by the timing snapshots.

**Clean timing artifacts.** The fixed snapshots under
`/tmp/w4me-njit022.yvNFhI/` are clean commits
`63f82aad4399321be6a143ddb889ac1e94430573` for the baseline and
`7a654f4a6b18c5f044286d9d1be648d9af0282c8` for the candidate. Their only
production differences are the isolated `WasmInterpreter` and `WasmModule`
hunks described above. Complete staged phoneME artifact SHA-256 values are
respectively
`039bf358fe35847b92eed13afe9e1e6f5b4623b9604a19a349eefafbbef183e4`
and
`f2a773e1e8f134f1f8c6ca540519bcf9eecfe813b5ba2d712d63f4cd4f0d97bd`.
Both receipts report clean source, Java source/target 1.3, the native VM,
CLDC, and preverify hashes recorded above, a 64 MiB judge heap, and the
production `host-import-id` configuration.

The clean target-47 `WasmInterpreter.class` grows from 52,969 to 53,359 bytes
(+390), and its preverified form grows from 79,587 to 80,337 bytes (+750).
Their target-47 SHA-256 values are
`4f7ebc4dd671c1c7311282b6da549326fd0658af2ecedf98e3729caf3e498e76`
and
`fed3caa39212506222a6163add19313b659113a279e085b8e9879afffdccd494`;
their preverified hashes are
`89f88b9d3ce8cf70a2782ff7fe0e332177cb20de68940a44d0f615cbefbed6c3`
and
`8241215926a019b980abb4cd407ecc35fd7e0e0935097fa1ca52f7e8cf7446c3`.
The clean target-47 `WasmModule.class` grows from 34,723 to 34,778 bytes
(+55), and its preverified form grows from 45,352 to 45,407 bytes (+55).
Diagnostic `execute` grows by 136 code bytes, from 7,039 to 7,175, while
`executeCompactBlock` grows by 154, from 2,848 to 3,002. No persistent heap
object or per-module array is added.

**Native i686 phoneME A/B.** Twelve balanced pairs used the exact one-frame
Game of Life route. Every invocation passed its checkpoint and reported
12,802,761 logical instructions, 482,291 compact calls, 6,407,444 compact
instructions, and identical branch-fast metadata. The candidate removed
exactly 1,113 outer dispatches in every pair. Raw microseconds per frame were:

| Pair | Order           |  Baseline | Candidate |
| ---: | --------------- | --------: | --------: |
|    0 | baseline first  | 3,419,000 | 3,468,000 |
|    1 | candidate first | 3,502,000 | 3,466,000 |
|    2 | baseline first  | 3,482,000 | 3,460,000 |
|    3 | candidate first | 3,407,000 | 3,389,000 |
|    4 | baseline first  | 3,475,000 | 3,525,000 |
|    5 | candidate first | 5,192,000 | 3,465,000 |
|    6 | baseline first  | 3,445,000 | 3,456,000 |
|    7 | candidate first | 3,462,000 | 3,509,000 |
|    8 | baseline first  | 3,431,000 | 3,468,000 |
|    9 | candidate first | 3,434,000 | 3,455,000 |
|   10 | baseline first  | 3,472,000 | 3,387,000 |
|   11 | candidate first | 3,480,000 | 3,456,000 |

The paired median is +3,500 us/frame, or only **+0.105%**, with six wins and
six losses. The 1,000 us/frame timer resolution is below the median effect,
order is balanced, and source is clean. Pair 5 contains an isolated slow
baseline outlier; it favors the candidate and does not change the median
failure. The pair CSV, paired summary, and dispatch receipt SHA-256 values
are respectively
`7ef7f03b750e237b16faae3d0290d313525d7c66ccf122e09f6359cfe9d05696`,
`f3801619bfdeb2d7c0a8fad7a2606e7e089fafc1bb9c7ac531ddf24f6d261e46`,
and
`c8549992bd6aef1d8c45432c0f7b75b03aedba96443ca94cdd96a1943427f98d`.
Raw files remain under
`/tmp/w4me-njit022.yvNFhI/evidence/game-of-life-zig-edition/` for the
lifetime of this host session.

**Decision.** Reject and remove the implementation. The candidate is exact,
preserves compact topology, and removes the predicted outer dispatches, but
it misses both predeclared primary gates: +0.105% versus the required +0.8%,
and 6/12 wins versus the required 9/12. Most of the 202,884 fused pairs run
inside compact blocks, where the new handler replaces rather than eliminates
an inner dispatch, while the whole interpreter class grows by 390 target-47
bytes. Waternet, Rubido, and Untangle timing controls cannot make the failed
primary gate pass and were therefore not spent after their exact clean-artifact
sanity runs.

**Reconsideration condition if rejected:** only a three-instruction form that
also removes the preceding address calculation, a compact handler that
demonstrably reduces inner-dispatch work rather than merely replacing it, a
future class-layout change that eliminates the added handler cost, or a more
precise physical-device measurement showing a materially larger effect.

The opcode, handlers, format bump, focused fixture, profile label, dense-map
expectation, and test-runner wiring were removed exactly. Production, test,
and tool files then matched stable
`main@63f82aad4399321be6a143ddb889ac1e94430573` byte for byte. The restored
tree passed `just verify`: all seven full-state workloads are exact at W4IR
format 16, diagnostic/counterless `execute` are 7,039/7,007 bytes with dense
`tableswitch`, both release JARs are Java 1.3/CLDC-preverified, and the stable
counterless artifact is again
`16a3364cf2f635eaea07d0993716a2025c7b5fd7900158d6a4869dfb3b84868c`.
Station/base JARs returned to 229,207/226,693 bytes with SHA-256
`ab1067d494818feae0f933efe196a52f4b965b0e00862a7a6bc8b2425eba28fb`
and
`4752473a5a81925003ec16459cdedd811f987eed9b03c7876e365b9aa11c34df`.
No production commit is made for NJIT-022.

### NJIT-023: `i32.add_const + i32.load8_u + local.set` fusion

**Status:** `rejected`.

**Why this is not a repeat of NJIT-022.** NJIT-022 fused only
`i32.load8_u + local.set`. Although the exact Game of Life stream executed
that pair 202,884 times, most sites were already inside compact blocks. The
candidate therefore removed only 1,113 outer dispatches, left all 482,291
compact calls and 6,407,444 compact logical instructions unchanged, and
measured only +0.105% with 6/12 wins. Its reconsideration condition explicitly
allows a form that also absorbs the preceding address calculation and removes
inner compact-dispatch work.

The stable format-16 production profile now proves that every one of those
202,884 Game of Life pairs is immediately preceded by
`W4IR_I32_ADD_CONST`. That token already represents the original
`i32.const + i32.add`, so the selected three-token W4IR sequence represents
four logical WebAssembly instructions. One combined handler replaces three
compact switch iterations with one rather than merely replacing the load/set
iteration. The stable profile artifact is
`402d9966b4e3b09b9b97fc14c5d16709e8957314dc93f1d6e4220752f7710b0b`;
the complete report SHA-256 is
`e9be1bf62e7499874da12fc3e119fa0f4c379a7e0654379e79c1137af029c59c`.
It passes all seven full-state workloads at W4IR format 16.

**Coverage and workload selection.** The exact triple executes 202,884 times
in one Game of Life frame and 1,584 times over the 94-frame Waternet route
(16.85 per frame). It does not occur on the routed Rubido, Untangle, Duck
Maze, or generic Plasma workloads. Game of Life is therefore the only primary
mechanism judge. Waternet checks the low-coverage stream, while Rubido and
Untangle remain mandatory whole-class-layout controls because NJIT-021 proved
that a zero-coverage or low-coverage route can regress after a large handler
changes the phoneME dispatch layout.

The stable Game of Life tier executes 12,802,761 logical instructions,
5,565,951 outer dispatches, 482,291 compact calls, and 6,407,444 compact
logical instructions. Static dispatch reduction is selection evidence only:
native i686 phoneME remains the speed judge.

**Selected representation and implementation.** Add the tail original W4IR
opcode `W4IR_I32_ADD_CONST_LOAD8_U_LOCAL_SET = 0x1033` and bump dense/non-dense
formats from 16/14 to 17/15. The post-pair fusion pass recognizes only:

1. `W4IR_I32_ADD_CONST` at index `pc`, whose cleared second original
   instruction remains at `pc + 1`;
2. `i32.load8_u` at `pc + 2`;
3. `local.set` at `pc + 3`;
4. neither active successor is a branch target.

The add constant remains in `operand`; the full unsigned memory offset moves
to `auxiliary`; and the validated local index is stored in the high 16 bits of
the instruction word. This is lossless because the decoder caps total locals
at 4,096. Dense opcode rewriting already preserves the instruction high bits.
The two active successor records are cleared, W4IR stride and instruction
count do not change, and old format 16 records are rejected and rebuilt.

Both executors preserve the existing optimized baseline's exact order:

1. charge and check the first logical instruction;
2. execute the current add-constant stack effect;
3. charge the second logical instruction exactly as the current fused add
   handler does;
4. charge and check `i32.load8_u` before popping its address;
5. pop and validate the effective address, leaving the local unchanged on a
   trap;
6. zero-extend and push the loaded byte;
7. charge and check `local.set`, leaving the byte on the stack on an
   intermediate budget trap;
8. write the local and remove the byte.

The compact handler performs all four accounting phases itself, reports span
four, and remains compact-eligible. It must preserve the current baseline's
add-result stack state at the budget boundary before the load. No runtime
selection branch, module field, array, allocation, direct-map entry, or
persistent heap object is added. The existing decode-time
`loadTeeFusionsEnabled` flag controls the new fusion only for focused
differentials; production keeps it compiled on.

**Correctness and artifact gates.** Add an independent WAT/Java differential
covering add wraparound, byte values `0x00`, `0x7f`, `0x80`, and `0xff`,
nonzero and large unsigned memory offsets, the final valid byte, negative and
overflowed dynamic addresses, local indices above 255, stack underflow,
address traps after the add result is popped, and every budget boundary in
forced-outer and compact execution. Compare complete memory, globals, table,
value-stack contents, locals/results, trap class/text, and logical counters
with fusion disabled.

Run `just test`, `just verify`, the exact production corpus, and the
KEmulator W4IR cache matrix. Require Java source/target 1.3, CLDC-only
compilation, classfile major 47, StackMap preverification, dense
`tableswitch`, both release JARs, previous-format rejection/rebuild,
resident/paged/promoted equivalence, and the 7,800-byte diagnostic `execute`
ceiling. Record diagnostic/counterless method and class growth, JAR deltas,
compact-region topology, staged-tree hashes, and the zero persistent-heap
delta before timing.

**Baseline and native acceptance rule.**

- source:
  `main@63f82aad4399321be6a143ddb889ac1e94430573`;
- production phoneME artifact:
  `039bf358fe35847b92eed13afe9e1e6f5b4623b9604a19a349eefafbbef183e4`;
- counterless exactness artifact:
  `16a3364cf2f635eaea07d0993716a2025c7b5fd7900158d6a4869dfb3b84868c`;
- native i686 VM:
  `bb4866969747430bb619d139c75ab31982e8967aa0e2dcc3e278fcc7920839a2`;
- CLDC classes:
  `117820661071b411b3937e8c708a9581aa48ba06fd06e24eadb5ebbf2036afbe`;
- preverify:
  `4fe0d1b160ac7f18c4f7489917a1d868ff82df50842ca718729b098b25f2f50c`.

Build clean production-shaped baseline and candidate artifacts with no runtime
candidate branch. Run at least twelve balanced exact one-frame Game of Life
pairs. Require a timer-resolved median improvement of at least +0.8%, at
least 9/12 wins, identical oracle/logical/tier fields, and a stable predicted
dispatch delta. Only if the primary passes, run at least eight balanced
Waternet, Rubido, and Untangle controls and reject any resolved regression
worse than -0.5%. Record and remove a failed implementation before selecting
another candidate.

**Correctness and artifact results.** The isolated implementation passes the
focused differential, `just verify`, all replay oracles, all seven full-state
workloads, Java source/target 1.3, the CLDC-only API build, classfile 47,
StackMap preverification, dense `tableswitch`, both release JARs, and
counterless exactness. The focused receipt is:

```text
PASS i32-add-const-load8-u-local-set-fusion sites=6 wide-local=259 outer-dispatches-saved=12 compact-blocks=2 logical=90057 budget=outer+compact traps=negative,oob,offset,underflow cache=build+hit profile=exact
```

It covers bytes `00/7f/80/ff`, address 65,535, signed wrapping, a local above
255, all outer and compact budget boundaries, stack underflow, negative/OOB
and unsigned-large offsets, complete private stack/local state, in-memory
W4IR build/hit, page faults, and the persisted opcode. During implementation
two focused failures were corrected before timing: the compact handler first
double-counted one logical instruction, and counting the fused token as one
eligibility dispatch could remove a reference four-dispatch compact region.
The retained test proves exact logical accounting and weights the new token as
three replaced compact dispatches only for the region eligibility threshold.

The complete format-17 state matrix retains the exact Game of Life result at
12,802,761 logical instructions, memory SHA-256
`45198d7efef567bfe59fee600cc06a41f288bfcf959d46716f66cfee3dc5cc5a`,
and framebuffer FNV-1a `a9255758`. The candidate counterless exactness
artifact is
`4517f6aa079da824a88b114928e3db976cae84b99b22b5b391cee3509f9f5c29`.
KEmulator independently passes old-format rejection/rebuild, resident build,
cache hit, static descriptors, 12-slot paging, promotion, and compact
execution with framebuffer `2e572184`.

The diagnostic/counterless `execute` methods grow from 7,039/7,007 to
7,246/7,214 code bytes, retaining 554/586 bytes below the 7,800-byte gate.
Diagnostic/counterless `executeCompactBlock` grow from 2,848/2,815 to
3,130/3,071 bytes. The verified candidate station/base JARs are
229,761/227,247 bytes, both +554 bytes, with SHA-256
`32d1d9b8aaae839ae1f96af7f2a561af88104af01c5302b6188707187b7ca4c2`
and
`cbd9bf07280e18a21a4d268fb41256eb5adef17c1cb9cf4ea844d0d828e47616`.
There is no new interpreter/module instance field, array, allocation, W4IR
record, direct-map entry, or persistent device-heap byte. `WasmModule` gains
only one compile-time static-final opcode constant.

**Clean timing artifacts.** The clean snapshots live under
`/tmp/w4me-njit023.Ua9e64/`. Baseline is
`63f82aad4399321be6a143ddb889ac1e94430573`; candidate is the temporary
clean commit `c05d378ad4fc90dc8f1052cb8b64f8bd4142e6ae`, whose production diff
contains only `WasmInterpreter.java` and `WasmModule.java`. Their
Game-of-Life-staged diagnostic artifacts are respectively
`fc5de15e50289c84b5b36506a7cb19c6a525be95d65389908ebd1b974589e568`
and
`aeeb60683a72a7da623b5f1768465bc18eec26d3b2353d314bbd5c6a807d4010`;
counterless artifacts are
`6c1793b14b883b4a5fc23dc8965588356a85c4ade15fd3f9727d9f3729e54e54`
and
`e757bcda499d66dd2d1e11f0374c5a15988cfe74da57f9b208441c959c61b644`.

The target-47 `WasmInterpreter.class` grows from 52,969 to 53,752 bytes
(+783), and the preverified class from 79,587 to 81,015 (+1,428). The
target/preverified `WasmModule.class` grows from 34,723/45,352 to
34,788/45,417 (+65 each). In the counterless build the interpreter grows
from 52,853/79,471 to 53,598/80,861 target/preverified bytes
(+745/+1,390). Both snapshot repositories report clean source.

**Native i686 phoneME A/B.** Twelve balanced pairs used the exact one-frame
Game of Life route on the native no-JIT VM with a 64 MiB judge heap. Every
invocation passed its checkpoint and reported 12,802,761 logical
instructions, 482,291 compact calls, 6,407,444 compact logical instructions,
and identical branch-fast metadata. The candidate reduced outer dispatches
from 5,565,951 to 5,563,725, exactly 2,226 per frame. Raw microseconds per
frame were:

| Pair | Order           |  Baseline | Candidate |
| ---: | --------------- | --------: | --------: |
|    0 | baseline first  | 3,438,000 | 3,512,000 |
|    1 | candidate first | 3,447,000 | 3,415,000 |
|    2 | baseline first  | 3,426,000 | 3,520,000 |
|    3 | candidate first | 3,459,000 | 3,400,000 |
|    4 | baseline first  | 3,452,000 | 3,496,000 |
|    5 | candidate first | 3,429,000 | 3,470,000 |
|    6 | baseline first  | 3,420,000 | 3,480,000 |
|    7 | candidate first | 3,454,000 | 3,511,000 |
|    8 | baseline first  | 3,462,000 | 3,498,000 |
|    9 | candidate first | 3,399,000 | 3,439,000 |
|   10 | baseline first  | 3,400,000 | 3,450,000 |
|   11 | candidate first | 3,465,000 | 3,416,000 |

The paired median is -42,500 us/frame, or **-1.235%**, with three wins and
nine losses. Timer resolution is 1,000 us/frame, order is balanced, source is
clean, and all deterministic fields are exact. The raw pair, paired-summary,
and dispatch-receipt SHA-256 values are respectively
`a06d93a040a8d9de070e8d453de8ed4f7265919d8530a604a8151a9bbedd91cc`,
`ce2cf595df7e7a13cbe16aa99ff1fd1a3b9b604607058703e6082bb54a2d87ff`,
and
`9719b5da49fae469b679fdfb66526d98eeb9802af511db94bd8a378018e1bf4f`.
Two preliminary invocations accidentally overlapped outside the sandbox PID
namespace and were discarded completely; only the single managed PTY series
under `evidence-clean/` is authoritative.

**Decision.** Reject and remove the implementation. It is semantically exact
and reduces both outer and compact inner-dispatch work, but fails both
predeclared gates and instead produces a resolved -1.235% regression. The
783-byte target-class and 282-byte compact-handler growth outweigh the saved
switch iterations on this phoneME layout. Waternet, Rubido, and Untangle
controls cannot rescue a failed primary and were therefore not spent.

Reconsider only if the same semantics can be expressed through a materially
smaller existing handler shape, a future accepted class-layout change removes
the measured regression, or precise physical-device evidence contradicts the
native phoneME result. Do not repeat the present standalone opcode and handler
layout.

The opcode, handlers, eligibility weighting, format bump, profile label,
dense-map expectation, focused fixture, and test-runner wiring were removed
exactly. Production, test, and tool files then matched stable
`main@63f82aad4399321be6a143ddb889ac1e94430573` byte for byte. The restored
tree passed `just verify`: all seven full-state workloads are exact at W4IR
format 16, diagnostic/counterless `execute` are 7,039/7,007 bytes with dense
`tableswitch`, and the stable counterless artifact is again
`16a3364cf2f635eaea07d0993716a2025c7b5fd7900158d6a4869dfb3b84868c`.
Station/base JARs returned to 229,207/226,693 bytes with SHA-256
`ab1067d494818feae0f933efe196a52f4b965b0e00862a7a6bc8b2425eba28fb`
and
`4752473a5a81925003ec16459cdedd811f987eed9b03c7876e365b9aa11c34df`.
No production commit is made for NJIT-023.

### NJIT-024: compact `i32.load8_u` direct stack/address path

**Status:** `inconclusive`.

**Hypothesis and distinction from closed work.** The stable one-frame Game of
Life profile executes 305,291 `i32.load8_u` operations. NJIT-022 proved that
202,884 of them are followed by `local.set`; only 1,113 of those pairs execute
outside compact regions, so at least 201,771 byte loads per frame execute the
existing compact `case 0x2d`. That handler currently evaluates
`pushI32(module.memory[address(operand, 1)] & 0xff)`. On the portable-C
phoneME interpreter this crosses six nested Java method boundaries:
`address`, `popI32`, `pop`, `checkedAddress`, `pushI32`, and `push`. Each
boundary creates and returns from an interpreted Java frame because phoneME
has no JIT or general Java inlining.

NJIT-005 changed only the shared `checkedAddress` algebra and measured a
correct but below-floor +0.407% Rubido result. Its ledger explicitly permits
reconsideration through a caller-inline form that removes the nested Java
frame. The historical push/pop experiment modified hot **generic** handlers
and was rejected. NJIT-024 instead changes one already selected compact
handler with new Game of Life coverage and removes the complete nested
stack/address call chain. It does not repeat NJIT-022 or NJIT-023: no new
W4IR opcode or fusion is introduced and no compact switch iteration is
replaced by a larger standalone handler.

**Selected implementation.** Change only compact `case 0x2d`. Check stack
underflow, decrement `valueTop` before validating the address exactly as
`address()` currently does, perform the existing four-condition scalar bounds
guard inline for width one, read and zero-extend the byte, and write it back
to the now-free stack slot while incrementing `valueTop`. The explicit guard
must retain signed-negative dynamic and static-offset rejection and must trap
before the array read. It must not use exception-backed memory bounds because
that would expose a Java exception and would not preserve multi-byte
follow-up semantics if the mechanism is later generalized.

The outer `i32.load8_u` handler, every other scalar width, the shared
`address`/`checkedAddress` helpers, W4IR decoding, cache format 16, instruction
accounting, compact-region metadata, and runtime selection policy remain
unchanged. The candidate adds no opcode, field, array, allocation, retained
object, local frame, or persistent device-heap byte. The expected target
effect is growth only inside `executeCompactBlock`, in exchange for removing
six interpreted Java calls from every covered compact byte load.

**Correctness and artifact gates.** A focused forced-compact differential must
cover bytes `0x00`, `0x7f`, `0x80`, and `0xff`; zero and nonzero static
offsets; the final valid byte; signed-negative dynamic and static operands;
one-past-end and wrapping addresses; stack underflow; a sentinel below the
address; and the instruction budget immediately before the load. It must
compare result/stack state, full memory, globals, table, trap class/text, and
logical/tier counters with compact execution disabled. Run the complete
`just verify` matrix, exact Game of Life checkpoint, Java source/target 1.3,
CLDC-only lint, classfile 47, StackMap preverification, dense `tableswitch`,
both release JARs, cache/device checks, and counterless exactness.

Inspect target-47 and preverified `executeCompactBlock`, complete interpreter
class/JAR deltas, max locals, and the unchanged 7,039/7,007-byte outer
`execute` methods. Reject before timing if the compiler introduces a
wide-local cascade, changes compact topology or counters, or grows the
interpreter enough to make the six-call saving implausible.

**Baseline and native acceptance plan.**

- source:
  `main@63f82aad4399321be6a143ddb889ac1e94430573`;
- stable `WasmInterpreter.java`:
  `b12f7966d446529216374e9fa520a1d5375b4dce6dd9e914b0f0f927e1caaf16`;
- production phoneME artifact:
  `039bf358fe35847b92eed13afe9e1e6f5b4623b9604a19a349eefafbbef183e4`;
- counterless exactness artifact:
  `16a3364cf2f635eaea07d0993716a2025c7b5fd7900158d6a4869dfb3b84868c`;
- station/base release JARs:
  `ab1067d494818feae0f933efe196a52f4b965b0e00862a7a6bc8b2425eba28fb`
  and
  `4752473a5a81925003ec16459cdedd811f987eed9b03c7876e365b9aa11c34df`;
- native i686 phoneME:
  `bb4866969747430bb619d139c75ab31982e8967aa0e2dcc3e278fcc7920839a2`;
- CLDC classes:
  `117820661071b411b3937e8c708a9581aa48ba06fd06e24eadb5ebbf2036afbe`;
- preverify:
  `4fe0d1b160ac7f18c4f7489917a1d868ff82df50842ca718729b098b25f2f50c`.

Build clean, production-shaped baseline and candidate artifacts with no
runtime candidate branch. Run at least twelve balanced native i686 phoneME
pairs on the exact one-frame Game of Life route. Require a timer-resolved
median improvement of at least +0.8%, at least 9/12 wins, and identical
checkpoint, logical, outer-dispatch, compact-call, compact-instruction, and
branch metadata. If the primary passes, run at least eight balanced Waternet,
Rubido, and Untangle controls and reject a resolved regression worse than
-0.5%. Record every raw pair and remove a failed candidate before selecting
the next experiment.

**Stopped prototype result.** The isolated source form compiled successfully
with Java source/target 1.3 and produced preverified release JARs. The outer
`execute` method remained exactly 7,039 bytes with its dense `tableswitch`.
The target-47 `executeCompactBlock` grew from 2,848 to 2,944 code bytes
(+96), its max locals from 41 to 43, and the unpreverified diagnostic
`WasmInterpreter.class` from 52,969 to 53,059 bytes (+90). Station/base JARs
were 229,289/226,775 bytes, +82 bytes each relative to the stable baseline.
No W4IR, field, array, or persistent-memory shape changed.

The first focused-test invocation did not reach semantic comparison because
the new WAT fixture failed validation: its intentional trapping functions
left an extra sentinel on the validator's nominal fallthrough stack, and the
underflow probe was itself statically ill-typed. This is a fixture-design
failure, not evidence for or against the runtime candidate. The owner then
requested an immediate return to the stable variant before the fixture was
corrected or any native phoneME timing began.

**Decision.** Classify the candidate as inconclusive and remove the prototype,
fixture, and runner wiring. Production, test, and tool sources again match
stable `main@63f82aad4399321be6a143ddb889ac1e94430573` byte for byte. No speed,
correctness, or rejection claim is made and no production commit is created.
Reconsider only by first fixing the statically valid trap fixture, then
repeating the complete exactness/artifact gates and the planned clean twelve
pairs; the +96-byte compact-method cost must be included in that verdict.

### NJIT-025: generic i32 comparisons over `values[]`

**Status:** `accepted`, not yet committed.

The generic handlers for `i32.eqz` and `i32.eq` through `i32.ge_u` previously
called `popI32First`, `popI32Second`, `popI32`, `pop`, `compareI32`,
`pushI32`, and `push`. The candidate performs the same stack checks, signed or
unsigned comparisons, and result write directly over `values[]`. The helper
methods remain in the class so the first A/B does not also change class
layout. No W4IR token, cache format, retained field, heap allocation, compact
region, trace, instruction count, or trap point changes.

The retained source is
`cae0f2b3e21006e816e4ffaa76fdb9e5daad8e48a7b1c69a46e84adf74ecc444`.
Its counterless interpreter class is
`4cbb8fa6ebbc248444c3da434dd5104b3e7ed7ae8bceb550f5cfdc911d140213`.
The full `just verify` matrix passes: all seven full-state workloads are exact,
Java source/target 1.3, CLDC bootclasspath, classfile 47, StackMaps,
preverification, dense dispatch, release JAR, and counterless gates pass.
Diagnostic/counterless `execute` sizes are 7,577/7,545 bytes.

Twelve balanced native i686 phoneME pairs against stable
`8e850656f2b19256c2559cdd07f165c7788b16d4` produced:

| Workload     | Median effect | Wins/losses/ties | Resolution             |
| ------------ | ------------: | ---------------: | ---------------------- |
| Rubido       |       +1.274% |           11/1/0 | measured               |
| Waternet     |       +0.462% |            9/3/0 | measured               |
| Untangle     |       +0.176% |            7/4/1 | below timer resolution |
| Game of Life |       +1.718% |           11/1/0 | measured               |

Raw results are under
`/tmp/w4me-interpreter-research/raw/njit024/runs/inline-i32-compare-vs-8e85065-20260727/`.
Rubido pairs/summary SHA-256 values are
`b33a2d4346752a2461d13f170da4929e47444415c6734786fc0e0550debd0a2b`
and
`97d860354d0debfbababb82c3c2f9315838a7263523fc150c26a152e398317c8`.
Waternet values are
`9e062fe1dcd1d764e39d4a165c7d5662dbdd18f608ce1c7d7a4bbce02ffaeb1f`
and
`aa9d1f38fd696ec24be47d6da79547cf2a458b14bea81c04289638e8a63eea25`.
Game of Life values are
`ef9b60038dbd8cb2e8fdc07e656ed56c6c64021d5ccf756529d2a78c7dec7633`
and
`1c6f1d282f1d5aff1776e7f3cdf0d3678b296ab682ec0fdc398e8253ef4fbfc8`.

**Decision.** Retain. The two primary integer-heavy routes show resolved
improvements above one percent, Waternet also improves, and the light control
is neutral. The implementation remains universal and introduces no runtime
selection or cartridge identity.

### NJIT-026: generic `i32.load` direct stack/address path

**Status:** `rejected`.

This candidate replaced the generic `i32.load` helper chain with direct
`values[]` stack access, the existing four-condition address guard, and inline
little-endian byte assembly. It did not touch compact or fused execution,
W4IR, cache format, retained state, or instruction accounting. Its source was
`59cc79c1bf3d1be7488df20615f495d69aa8635e3f3797b1cc212f626538b7e7`;
the counterless interpreter class was
`c44ffab9483ccb2be71566049775a53e273f9b4d3178896f66020d19a4bb1e4f`.

An initial prototype incorrectly incremented `valueTop` twice and failed the
Duck Maze level transition. That prototype was never timed. The corrected
candidate removed the extra increment, passed the focused Duck and Plasma
checks, then passed the complete `just verify` matrix with all seven
full-state workloads exact. Its diagnostic/counterless `execute` sizes were
7,725/7,693 bytes. The project sanity ceiling was subsequently raised to
16,000 bytes; method or JAR size is therefore not a rejection reason.

Twelve balanced native i686 phoneME pairs against NJIT-025 produced:

| Workload     | Median effect | Wins/losses | Decision evidence |
| ------------ | ------------: | ----------: | ----------------- |
| Rubido       |       +0.178% |         9/3 | measured          |
| Waternet     |       -0.270% |         4/8 | measured          |
| Game of Life |       -0.141% |         5/7 | measured          |

Rubido raw results are under
`generic-i32-load-vs-inline-compare-clean2-20260727/rubido`, with pairs and
summary SHA-256
`8c2aa588d40f547a2f0957f65b046f1081869e32265f87868dcba432f0c7be38`
and
`cb91fb8e26b9880cdf35b1bf7f14c0e17e47337bca0a97a420db4213d911a22a`.
The clean Waternet rerun is under
`generic-i32-load-waternet-clean4-20260727/waternet`, hashes
`6265e20fcc5329f18ed4579f5d7082581d05525a1896804c71f7da6ab0cc953f`
and
`0a5d23f1b66a4ee982703c3ac7563ea5e15ca7519ce574ba9d8d395c23726cc5`.
The clean Game of Life rerun is under
`generic-i32-load-gol-clean4-20260727/game-of-life-zig-edition`, hashes
`d991c6b6c079686a2e7166a7f96100a81148d4d447005ff90f9056af73eb1a1f`
and
`3d17459b75359b23c51ac8de49f8cd22384b1c9c5d1e615810bb02ad719889fe`.
Two earlier control attempts overlapped an unrelated Buildroot or phoneME
process and are explicitly non-authoritative.

**Decision.** Reject and remove. The only positive route is below two tenths
of a percent, while Waternet and the heavy Game of Life route regress. The
direct handler shifts cost rather than reducing total phoneME work. Revisit
only as part of a materially different memory representation or slot-addressed
IR, not as the same standalone inline handler.

### Confirmed instruction-budget prerequisite

The current fused W4IR path still fails exact instruction-budget recovery.
The current checkout reproduces a limit-three case as
`instructions=5`, `memory[0]=0`, and stack `0,3`, while the reference traps
before the forbidden add with `instructions=4` and stack `0,1,2`.
Retaining a complete unfused stream fixes exactness but approximately doubles
W4IR/RMS storage, so that form remains rejected. Compact or fused memory
experiments must first use sparse recovery metadata or another exact mechanism;
generic single-opcode candidates NJIT-025 and NJIT-026 do not alter this
pre-existing defect.

### NJIT-027: generic `local.set` and `local.tee` direct stack path

**Status:** `rejected`.

The outer handlers currently call `pop()` for `local.set` and `peek()` for
`local.tee`. Both helpers add a Java method frame around one stack-height
guard and one `long[]` access. The candidate duplicates those exact guards in
the handlers and reads or removes the top value directly. It deliberately
leaves `local.get` and `push()` unchanged because the latter also implements
the distinct value-stack-capacity trap and exception rollback.

The exact production-stream profile records 733,704 `local.set` and 12,917
`local.tee` operations on Rubido, 408,010 and 409,924 on the single Game of
Life frame, 22,826 and 23,743 on the Waternet browser route, and 29,531 and
18,632 on Untangle. These totals include compact execution, so they are
selection evidence and an upper bound rather than a claim that every operation
will use the changed outer handler.

Baseline source is retained NJIT-025 with SHA-256
`cae0f2b3e21006e816e4ffaa76fdb9e5daad8e48a7b1c69a46e84adf74ecc444`.
The freshly rebuilt counterless phoneME class is
`4cbb8fa6ebbc248444c3da434dd5104b3e7ed7ae8bceb550f5cfdc911d140213`;
the complete staged tree used for the next comparison is preserved at
`/tmp/w4me-njit027-baseline-20260727/counterless-preverified`.

Acceptance requires exact full-state and trap behavior, Java 1.3/CLDC and
preverification gates, then at least twelve balanced native i686 phoneME pairs
against NJIT-025. Rubido and Game of Life are primary routes; Waternet and
Untangle are no-regression controls. No W4IR, cache format, retained field,
heap allocation, instruction accounting, compact region, or trace changes are
allowed.

The isolated candidate source was
`a2babce77167442904ab659a905d2b63e2999cf1b194391887b720c24a48e488`;
its counterless interpreter class was
`0cabc892574c97de849c3bb21a14aef2e3908a501f8bf6b79e0f4b913b6bfbc9`.
The complete `just verify` matrix passed with all seven workload states exact.
Diagnostic/counterless `execute` sizes were 7,629/7,597 bytes, compared with
7,577/7,545 for NJIT-025.

Twelve balanced native i686 phoneME pairs against NJIT-025 produced:

| Workload     | Median effect | Wins/losses | Decision evidence       |
| ------------ | ------------: | ----------: | ----------------------- |
| Rubido       |       -0.249% |         4/8 | measured regression     |
| Game of Life |       +0.173% |         8/4 | resolved but immaterial |

Raw results are under
`/tmp/w4me-interpreter-research/raw/njit024/runs/njit027-local-set-tee-vs-njit025-20260727/`.
Rubido pairs and summary SHA-256 values are
`47d3e1d2f9f4a1e0a9f6fce52151321c22af7deeda9ffce5be6d7ff425beaee0`
and
`1656efd37868fc8fae75dd25b5fdf3691f61d4a3c4bebb31e5044bf388d6be4e`.
Game of Life values are
`7ffd5f2a5e4cabfc421783c50fac559ce0453856368f961a0cbadf3bf772825e`
and
`cd504d6f9db9cf42209013d5e1214eb8f9035c8ba5e798d098c65084872d9698`.
Waternet and Untangle controls were not run because the primary Rubido gate
already failed.

**Decision.** Reject and remove. Eliminating these two helper frames does not
produce a portable aggregate benefit on the no-JIT judge. The retained source
again matches NJIT-025 exactly. Revisit local access only through a materially
different representation such as slot-addressed W4IR, not by repeating the
same handler inlining.

### NJIT-028: generic `local.get` exact direct push

**Status:** `rejected`.

This candidate isolates the remaining generic local handler. `local.get`
currently calls `push()`, which creates a Java method frame around the
`long[]` write, post-increment, mandatory JVM bounds check, overflow rollback,
and canonical `value stack exhausted` trap. The candidate duplicates that
exact `try/catch` sequence inside only `case 0x20`; `local.set`, `local.tee`,
the helper itself, and every other caller remain unchanged.

The exact production-stream profile records 996,056 `local.get` operations on
Rubido, 817,287 on the single Game of Life frame, 107,082 on the Waternet
browser route, and 68,633 on Untangle. These are upper bounds because compact
execution bypasses the outer handler. Compared with rejected NJIT-027, this
candidate has both higher coverage and a more expensive helper boundary, but
also adds an exception-table entry to the large outer method.

The baseline remains retained NJIT-025:
source `cae0f2b3e21006e816e4ffaa76fdb9e5daad8e48a7b1c69a46e84adf74ecc444`,
counterless class
`4cbb8fa6ebbc248444c3da434dd5104b3e7ed7ae8bceb550f5cfdc911d140213`.
Acceptance requires focused stack-capacity and overflow exactness, the complete
Java 1.3/CLDC/full-state matrix, then twelve balanced native i686 phoneME pairs
on Rubido and Game of Life. Waternet and Untangle are controls only if both
primary gates pass. No retained state, allocation, W4IR, cache format,
instruction accounting, tier topology, or cartridge recognition may change.

The isolated source was
`cf7a56c5afac84eb227966d7faf8b7da4cd7128a0a1eb94f977ffe513fb628c8`;
its counterless interpreter class was
`a03fe48a693c19eb0280e775ae42c5059090974f3adcbbf78ee733dd32489502`.
The complete `just verify` matrix passed, including the dedicated
`value-stack-push-guard` cases and all seven exact workload states.
Diagnostic/counterless `execute` sizes were 7,641/7,609 bytes.

Twelve balanced native i686 phoneME pairs against NJIT-025 produced:

| Workload     | Median effect | Wins/losses | Decision evidence         |
| ------------ | ------------: | ----------: | ------------------------- |
| Rubido       |       -0.635% |         3/9 | measured regression       |
| Game of Life |       +0.356% |         7/5 | measured but insufficient |

Raw results are under
`/tmp/w4me-interpreter-research/raw/njit024/runs/njit028-local-get-vs-njit025-20260727/`.
Rubido pairs and summary SHA-256 values are
`83c445bd6dbe80aca22a617bf7b1ff689224b9f551c6ee18861301ddc307dfe5`
and
`4d4c8af447cd2e78e2de2847c6252238c5743f719ff3b131522ab8162fbbe8ac`.
Game of Life values are
`913775fef3c10751f83f73b67a445c7de75826dc4af98fb044d09ba91b5d1102`
and
`992f0ed742b7c5afde460f66e4654c353050ec330f748898dba3ab7e1b718ca5`.
Controls were skipped after the Rubido primary gate failed.

**Decision.** Reject and remove. Inlining the exact capacity `try/catch`
increases the outer method and regresses Rubido more than the small Game of
Life benefit. Together NJIT-027 and NJIT-028 close standalone generic local
handler inlining; revisit local traffic only through a different IR or stack
representation.

### NJIT-029: packed horizontal framebuffer spans

**Status:** `accepted`.

The native i686 phoneME VM was rebuilt out of tree with its upstream
statistical Java-method profiler enabled. This is a selection profile, not a
timing comparison: timer ticks and profiling perturb execution, so none of its
wall times are acceptance evidence. The profiled VM SHA-256 is
`5f0d0bc236742cb5e166feba9ffe24e3ff866d586d64395ba51d77e25011380c`.
Its method samples attribute the following shares to
`Wasm4Runtime.drawHorizontal`:

| Workload     | Samples | Share |
| ------------ | ------: | ----: |
| Waternet     |      78 | 34.7% |
| Rubido       |      89 |  9.5% |
| Untangle     |     184 | 74.5% |
| Game of Life |       0 |    0% |

The raw `flat.prf` SHA-256 values are
`80dfdcc0a261941f60e3416cd4bce3b1dc94c50f6fc5c6ca30f2c6bd4bd1dc`
for Waternet,
`d71bb59aa4123092ba7ffce10d449ab3f83152e9cdd1b295be965201e7dde0ae`
for Rubido,
`fa90f352671317ea4d090b85891da2894da996fe8a012f9141382c0e23da0be7`
for Untangle, and
`3ad6be241b9f15665abd13d4b6c2fe5213eaa417d2a91f3c3f66212f99187d6a`
for Game of Life. Aggregate VM instrumentation independently records
hundreds of millions of `aload_0 + getfield` shapes and tens of millions of
Java method entries on the heavy routes, but those totals are supporting
mechanism evidence only.

`drawHorizontal` currently invokes `drawPoint` once per destination pixel.
Every call reconstructs the linear pixel index and packed-byte address, enters
another Java frame, reloads runtime constants, reads the same framebuffer byte
up to four times, and writes only two bits. The candidate keeps the existing
private method and all caller-side clipping unchanged, but handles the leading
partial byte, complete four-pixel bytes, and trailing partial byte directly.
Complete bytes use one pre-expanded color byte; partial bytes preserve all
pixels outside `[startX, endX)`.

The retained baseline is NJIT-025. `Wasm4Runtime.java` SHA-256 is
`8d2c633824c3ffcb58971e0a28e91ada6639fc26179091be7450c28639cba830`;
the counterless preverified runtime class SHA-256 is
`95fd55080b40b5686ef0c7d87cad32dd2619bbe5cc8691a077a4a49584480adf`
and its size is 23,218 bytes. The interpreter source remains
`cae0f2b3e21006e816e4ffaa76fdb9e5daad8e48a7b1c69a46e84adf74ecc444`.
The VM, CLDC classes, and preverify hashes remain the values recorded for
NJIT-028.

The implementation must remain Java 1.3 and CLDC-only, allocate no objects,
retain the 160x160 2-bpp layout, preserve the untouched bits of both boundary
bytes, and leave `drawPoint`, every primitive's clipping rules, W4IR, cache
format, instruction accounting, input, disk, and audio untouched. Focused
tests must compare every color and every start/end alignment, including empty,
one-pixel, cross-byte, full-row, and boundary spans, against the scalar
reference. The complete full-state and Java ME verification matrix must pass
before timing.

The authoritative A/B uses the normal counterless native i686 phoneME VM,
never the profiler build. Run at least twelve balanced pairs on Waternet and
Untangle as primary routes, with Rubido as a no-regression and secondary-win
control. Game of Life is an exactness control only because the method profile
found no samples there. Accept only a resolved primary improvement with no
exact-state or route regression; remove the candidate otherwise.

The isolated implementation has
`Wasm4Runtime.java` SHA-256
`cf4c0350162364f69a1a7cb53c2bd97d5872d8efd1622bc355b51c0fa718d703`.
Its counterless preverified runtime class is 23,625 bytes, SHA-256
`16657bca89a38f69d9845c28dd42b8b88ce4eda4e41546f2fe2be03f5aac47b5`,
which is 407 class bytes larger than the retained baseline and allocates no
additional heap. The candidate-specific staged phoneME artifact SHA-256 is
`314cf65997e87a527642a8fec64a12b26269024d6c8db15986db332976c21da5`.
The interpreter class is byte-identical to the baseline at SHA-256
`4cbb8fa6ebbc248444c3da434dd5104b3e7ed7ae8bceb550f5cfdc911d140213`.

The focused differential covers 320 cases across all four colors, every
start/end modulo-four alignment, empty and one-pixel spans, cross-byte spans,
complete rows, and both clipping boundaries. The complete `just verify` gate
passes, including full-state exactness for all seven workloads, Java 1.3,
CLDC, target-47, preverification, release integrity, and the 16,000-byte
interpreter sanity limit.

The completed primary native pairs are:

| Route        | Baseline/candidate median effect |  Wins | Timer-resolved |
| ------------ | -------------------------------: | ----: | -------------- |
| Waternet     |  **+32.070%**, +5,412.0 us/frame | 12/12 | yes            |
| Untangle     |  **+63.839%**, +3,058.5 us/frame | 12/12 | yes            |
| Rubido       |   **+8.307%**, +8,174.5 us/frame | 12/12 | yes            |
| Game of Life |      +0.617%, +19,500.0 us/frame |  9/12 | yes            |

All route signatures are exact. Waternet raw `pairs.csv` / `receipt.txt`
SHA-256 values are
`afc23c80ebc10657f2d3c79a8558566527ab33ce828763e16e229b6f08e7f7ce`
and
`042d19e5c41e2cf28bdc0b9f360f8e3ae6ee1b946660ed2e22e9ee542264916d`.
Untangle values are
`4d52529a80e566411dcecd7e9376bc67a286c283e5dc5289c2f19a67c029812d`
and
`f1e75b76acf881e686ac776e5d85124ee84d8e3851fd1103aa74b17c89f8c259`.
Rubido values are
`b750bf26f35d5c576ec7b9a8a87ff663d209800737d84f3596490e72ca36a345`
and
`424c99e89c074fe4d9682c927e2a958a16e960fe8c6c41b6c436e43924309025`.
Game of Life values are
`0066533b978664146d336664666375ca206af173e275089bd1614a8b6253d2df`
and
`8b436dee3863c9c3273c3524efea3b8366f9f65372ddf7b66692eb4a21cdb599`.
Both live under
`/tmp/w4me-interpreter-research/raw/njit024/runs/`
`njit029-packed-horizontal-vs-njit025-20260727/`.
Game of Life uses one measured frame per VM and therefore has a coarse
1,000-us/frame timer resolution; its sub-one-percent result is a
no-regression control, not an attributed renderer win.

**Verdict:** accept and retain. Both primary routes have large resolved wins,
Rubido independently confirms the method-profile mechanism, Game of Life has
no measured regression, and every deterministic signature remains exact.
The implementation adds 407 preverified runtime-class bytes and no persistent
or peak heap. The earlier contaminated Rubido attempts were discarded before
receipt finalization: a concurrent 12-way Buildroot build started between the
paired runs. The final receipt was restarted from sample zero after the host
became quiet and contains only the twelve clean balanced pairs above.

An independent renderer review found three additional non-overlapping
candidates and recorded them separately so this loop does not rediscover or
combine them: hoisting `argbLookup` removes one `getfield` per destination
pixel for only eight class bytes; caching the last packed framebuffer byte
can reduce 57,600 byte loads to 9,600 at side 240 for about 41 class bytes;
and the existing `copyArgb` is a possible side-160-only path. NJIT-017 and
NJIT-018 were accepted independently before this candidate. None of those
changes is included in NJIT-029's code or timing.

### NJIT-030: inline generic control-frame entry

**Status:** `accepted`.

The post-NJIT-029 native i686 phoneME statistical profile moves Rubido's
remaining time back into the interpreter: `executeCompactBlock` accounts for
41.4%, `execute` for 39.3%, and the private `enterControl` helper for 5.2%.
Waternet attributes 3.4% to the helper, while Untangle and Game of Life are
lower-coverage controls. These are selection samples only; the profiler VM
perturbs timing and is never an acceptance judge. The Rubido `flat.prf`
SHA-256 is
`9e5764084224d64b94159cb1d79d4bc3060fd1b34cf6fffa3c48f78588f5ae4e`;
Waternet, Untangle, and Game of Life are
`aab26bae2e3fed41b10e67248aa470e63a802ad64821a4d917ca241d64fd73e0`,
`01cfea2e66410de19160cb0aec2999446210d35ef42234f559dc9a0c0cbdea7d`,
and
`39ed7f7212a22d6fee277b2ec4622118620be4d291ebe9653de418864d7f76d7`.

The retained exact opcode profile, report SHA-256
`c1d45035a5114b75e2e376feca193d93b5271fe218fa891a5e4986d93e8d92c5`,
records the following `block + loop + if` executions:

| Workload and route           | Control entries | Entries/frame |
| ---------------------------- | --------------: | ------------: |
| Rubido browser, 70 frames    |       2,210,927 |     31,584.67 |
| Waternet browser, 94 frames  |         173,315 |      1,843.78 |
| Untangle browser, 401 frames |         114,271 |        284.97 |
| Game of Life idle, 1 frame   |         333,456 |    333,456.00 |

The profile predates NJIT-029, but NJIT-025 through NJIT-029 do not rewrite
`block`, `loop`, or `if`; their exact logical streams and route state remain
unchanged. The counts therefore remain coverage evidence for this isolated
helper-call candidate.

`execute` has two `invokespecial enterControl` sites: one shared by
`block`/`loop` and one in `if`. The candidate copies the exact helper body
into those two cases and removes the now-unreachable private method. It must
preserve the control-stack limit check, opcode, parameter/result counts,
parameter underflow trap, all six parallel control arrays, increment order,
`if` condition pop order, else/end behavior, and every trap message. It must
not cache control state across dispatches, lower `end`/`else`, change branch
descriptors, W4IR, instruction accounting, compact eligibility, heap, or any
other opcode.

Existing `StaticBranchDescriptorSmoke` cases cover block results, loop
parameters, if results, branch tables, and exact logical counts. The complete
seven-workload full-state matrix additionally covers nested real-world
control flow. Before timing, `javap` must prove both `enterControl` invokes
and the private method are absent, while Java 1.3, CLDC, target-47,
preverification, trap, budget, cache, release, and interpreter-size gates all
pass.

Use NJIT-029 as the hash-bound counterless baseline. Run at least twelve
balanced native i686 phoneME pairs on Rubido as the primary route, followed
by Waternet and Game of Life no-regression controls only if Rubido is not
decisively negative. Accept a repeatable timer-resolved positive primary
effect with exact signatures and no resolved control regression; otherwise
remove the inlining and record the rejection. Bytecode or class-size savings
alone are not performance evidence.

The isolated implementation has `WasmInterpreter.java` SHA-256
`f6858e981b0ad841c8a750886421bafbb432e7aa1a99318d9c8cba60446a66dd`.
Target-47 `javap` proves that `enterControl` is absent as both a method and a
call target. Diagnostic/counterless `execute` sizes are 7,833/7,801 bytes,
up from the NJIT-029 7,577/7,545-byte baseline and below the 16,000-byte
sanity limit. The candidate counterless preverified interpreter class is
84,664 bytes, SHA-256
`f7a54576940a0f1d825af8838de37e3d95bf4612e02eb3478917b3ec2bc0afca`;
the baseline is 84,439 bytes,
`4cbb8fa6ebbc248444c3da434dd5104b3e7ed7ae8bceb550f5cfdc911d140213`.
The runtime class remains byte-identical to NJIT-029 at
`16657bca89a38f69d9845c28dd42b8b88ce4eda4e41546f2fe2be03f5aac47b5`.

`just test` and `just verify` pass, including the 12-case static branch
descriptor smoke at exact logical count 160, the complete seven-workload
full-state matrix, Java 1.3, CLDC, target-47, preverification, trap, budget,
cache, framebuffer, audio, storage, release, and counterless gates. The
counterless exactness artifact is
`76a0ab4c2f79438f63a62a6d7aeea87b1d1c9288d8a2ca3eb1dee84bdf6a67e8`.
The staged phoneME candidate artifact is
`159e3acd509c9ffaaf231b6dca9ba20fc51ed87226a812613596d1c15267ecbe`;
its build receipt SHA-256 is
`0c040b97ce96ba77af3c3bd9489bc8ceda546c07a38055c78b5bbf1482725ae8`.
The candidate adds 225 preverified interpreter-class bytes and no field,
array, allocation, persistent heap, W4IR, RMS, or runtime-class bytes.

The final balanced native i686 phoneME result is:

| Route        |            Median paired effect |  Wins | Timer resolution |
| ------------ | ------------------------------: | ----: | ---------------: |
| Rubido       |    **+0.930%**, +829.5 us/frame | 12/12 |  16.667 us/frame |
| Waternet     |    **+1.017%**, +114.5 us/frame | 10/12 |  16.667 us/frame |
| Game of Life | **+0.768%**, +24,500.0 us/frame |  8/12 |   1,000 us/frame |

All deterministic route signatures match exactly. Raw evidence lives under
`/tmp/w4me-interpreter-research/raw/njit024/runs/`
`njit030-inline-control-entry-vs-njit029-20260727/`. Rubido
`pairs.csv` / `receipt.txt` SHA-256 values are
`ca4b804cc56f18265d340f90af62afde8459b5cefbdc91a2cdaa2d0985879925`
and
`1b31d24a14f573aad571cd71f39c7fa0253b9351695aeca81890d138486aff38`.
Waternet values are
`e723ccb34d6027ea401228c8820b9372e1c8c727bff8b97fb2d7281d4c595c61`
and
`44ebc148f40a07ed331723b6851145c41ab87bd7a47ae0c174c3c6e473aa16d6`.
Game of Life values are
`5ee77e7cab162aeb1ea4d58787c22c4bb183f8692a63348a43e9febf2a2533e6`
and
`18a8215b10efeb2d3ea2760e8b26e9076df0c14416ea220c727efef04ea743c6`.

**Verdict:** accept and retain. The primary Rubido effect is
timer-resolved, order-independent, and positive in all twelve pairs. Both
controls are positive, exactness is complete, and the change adds no runtime
state. The 225-byte class growth is not an acceptance cost under the current
size policy; it records the price of duplicating the hot body into the two
dispatch sites.

### NJIT-031: zero- and one-value control-transfer fast paths

**Status:** `accepted`.

The post-NJIT-030 native i686 phoneME statistical profile attributes 3.7% of
Rubido samples, 1.9% of Waternet samples, and 1.5% of Game of Life samples to
the private `transfer(int, int)` helper. Its current implementation validates
the transfer, copies every result through the fixed `transferValues[]` scratch
array, resets `valueTop`, and enters a second loop that calls `push()` for each
result. WebAssembly 1.0 control signatures overwhelmingly transfer zero or one
value, so both loops and the scratch array traffic are avoidable on the common
paths without changing the general multi-value fallback.

The retained exact opcode profile records 26,766 dynamic `end` operations on
the 94-frame Waternet route, 133,183 on the 70-frame Rubido route, and 535,685
on the single Game of Life frame. Branch and function-return paths call the
same helper. These counts are selection evidence rather than a direct count of
transfer arity. The method-profile `flat.prf` SHA-256 values are
`c11bd733b52f5036698697975625063a4c666e43b275efb12ec41caa5a9232e7`
for Waternet,
`dfc24d7e2920763538852370f154093f44cc3fd17eb441a11fc4b1ded587bfdd`
for Rubido, and
`5be14ff4d6415c1e559dece8587c4870ae193899194d39e45d578f947e4329f7`
for Game of Life. Profiling selects the candidate only; its perturbed wall
time is not acceptance evidence.

The candidate keeps the existing validation first and adds two returns before
the generic loops. For `count == 0`, it sets `valueTop` directly to
`destinationBase`. For `count == 1`, it saves `values[valueTop - 1]`, resets
`valueTop`, and calls the unchanged `push(long)` so capacity failure,
post-increment rollback, exception type, and trap text remain canonical.
Counts above one use the existing scratch array and both loops byte-for-byte.
The candidate must not change control descriptors, control arrays, function
entry/return ordering, branch targets, W4IR, RMS, instruction accounting,
value-stack representation, persistent heap, or any cartridge-specific
behavior.

The hash-bound baseline is accepted NJIT-030:
`WasmInterpreter.java` SHA-256
`f6858e981b0ad841c8a750886421bafbb432e7aa1a99318d9c8cba60446a66dd`;
counterless preverified interpreter SHA-256
`f7a54576940a0f1d825af8838de37e3d95bf4612e02eb3478917b3ec2bc0afca`,
size 84,664 bytes; runtime class SHA-256
`16657bca89a38f69d9845c28dd42b8b88ce4eda4e41546f2fe2be03f5aac47b5`.
The native VM, CLDC classes, and preverify SHA-256 values are respectively
`bb4866969747430bb619d139c75ab31982e8967aa0e2dcc3e278fcc7920839a2`,
`117820661071b411b3937e8c708a9581aa48ba06fd06e24eadb5ebbf2036afbe`,
and
`4fe0d1b160ac7f18c4f7489917a1d868ff82df50842ca718729b098b25f2f50c`.

Before timing, pass focused zero/scalar/multi-result, underflow, function
return, branch, exact-budget, and full-state coverage; Java 1.3, CLDC,
target-47, preverification, release, cache, and 16,000-byte method gates; and
inspect `transfer` to prove both common cases bypass the copy loops. Build a
counterless candidate with no runtime selection branch. Run at least twelve
balanced native i686 phoneME pairs on Rubido and Game of Life as primary
routes, followed by Waternet as a no-regression control when the primaries are
not decisively negative. Accept only a repeatable timer-resolved positive
aggregate result with exact deterministic signatures and no resolved control
regression; otherwise remove the source change and retain this ledger entry.

The isolated implementation has `WasmInterpreter.java` SHA-256
`b1438a228dbcb73ac2e3ae2eb5d4643dbda5ff39a0351be090f5505e2602b9da`.
Target-47 `transfer` retains the original 34-byte validation prefix, returns
from the zero-result path at bytecode offset 43, handles one result before
offset 72, and leaves the former two loops as the only path for larger
arities. Its code grows from 94 to 133 bytes. The counterless preverified
interpreter is 84,756 bytes, SHA-256
`26e45b9d553fe4677425b0b05feb2a138deb060abfc3138480e6e178b1f0101f`,
which is 92 class bytes above NJIT-030. The counterless runtime remains
byte-identical at
`16657bca89a38f69d9845c28dd42b8b88ce4eda4e41546f2fe2be03f5aac47b5`.
There is no field, array, allocation, W4IR, RMS, persistent-heap, or release
resource delta. Inner-class binary differences are limited to shifted
`LineNumberTable` entries caused by the ten added source lines; their
executable `javap -c` output is identical.

`just test` and `just verify` pass. The focused static-control fixture covers
zero/scalar and 16-value transfers at exact logical count 160; the defined
call fixture covers result and argument arities; stack-capacity and malformed
descriptor traps remain exact. The seven-workload full-state matrix, Java 1.3,
CLDC bootclasspath, target-47, preverification, release, cache, budget, and
16,000-byte method gates all pass. The counterless exactness artifact is
`92a70f3d6ed369e9fdcb03cca80987f3553b30189e9b1841a3d29a92bc288b6b`.
The station and base JARs are 275,406 and 272,884 bytes, SHA-256
`e522fa8740790478b9eed483a3ecbe7f596f45209a1d8e2fe61d185c19a49448`
and
`920e540af0ee636e989be1d5e1316a51597472c1c8f6e823a2347c96794dd3a0`.
The staged phoneME artifact is
`b6bd0be856f00c5f65df32aa82571dc9c024e110ffe712e119566cbdd9b0d468`;
its build receipt SHA-256 is
`8f7661fd15eca3a33f4030bb8dacbe11c399de2779384614c11367a04dc1b45f`.

The authoritative native i686 phoneME results against NJIT-030 are:

| Route                              |          Median paired effect |  Wins | Timer resolution |
| ---------------------------------- | ----------------------------: | ----: | ---------------: |
| Rubido                             |  **+0.695%**, +612.5 us/frame | 11/12 |  16.667 us/frame |
| Game of Life                       | **+1.009%**, +32,000 us/frame | 11/12 |   1,000 us/frame |
| Waternet, initial                  |       -0.181%, -20.0 us/frame |  4/12 |  16.667 us/frame |
| Waternet, expanded decision series |   **+0.324%**, +36.0 us/frame | 14/24 |  16.667 us/frame |

Every invocation matched checkpoints and all deterministic counters. The
Rubido series contains one isolated candidate scheduling spike
(103,062 us/frame versus the otherwise 87,418--88,844 candidate range); the
paired median is insensitive to it and the other eleven pairs all favor the
candidate. Because the first Waternet series was small and negative, it was
not discarded or reinterpreted: a separately named 24-pair decision series
was run. That larger balanced series reverses the sign and satisfies the
predeclared no-regression gate.

Raw evidence lives under
`/tmp/w4me-interpreter-research/raw/njit024/runs/`. In
`njit031-transfer-arity-fast-vs-njit030-20260727`, Rubido
`pairs.csv` / `receipt.txt` SHA-256 values are
`e640413f99fc0b412d99e6b21de90e9a29bada791e2012b5ffbae0ac19f7b15f`
and
`e50ad07889f955d9692b58e33133aeb75e3f4b2e98266798ba6afa2129cd30f6`;
Game of Life values are
`36833fa5405fec6a2e93526731d8078e4987bb367fc88f8b335b6ad20a4fcc82`
and
`8a448b3593921878158a87f1fb34f8fc8ee8fd54097feaed632e4156f9161dac`;
the initial Waternet values are
`9ef331daac6c4d56d53a4c6573ae85e66e3743b9597f5fd912e7ef5acecd3bf5`
and
`ffbfe35fe971cc859b90b1199a6f4daf68f14f3f9ec88f2c882b0e2eb08aea44`.
The expanded decision series is
`njit031-transfer-arity-fast-waternet-repeat-20260727`; its Waternet
`pairs.csv` / `receipt.txt` SHA-256 values are
`5496f705c90312286c57e5264c53e308d8588bf86d5f89672a5ea483339f7d98`
and
`8012f858750fd3fbdf81b33ce5305a26e0309d225906d1a730676ac4c886b448`.

**Verdict:** accept and retain. Both heavy primary routes show resolved,
order-independent improvements with eleven wins each. The expanded Waternet
control is positive, exactness is complete, and the implementation adds only
92 preverified class bytes with no runtime state or format change.

### NJIT-032: compact `i32.load8_u` direct stack/address path

**Status:** `rejected`.

This is a materially new continuation of the inconclusive NJIT-024 entry, not
an unrecorded repeat. NJIT-024 stopped before its correctness gate because its
first hand-written WAT fixture did not validate. A later independent research
pass corrected the fixture and demonstrated byte-identical success, budget,
and five out-of-bounds snapshots on the old source state. That pass also
measured two independent twelve-pair Game of Life series at +1.019% and
+0.978% on native i686 phoneME. Those old timings are selection evidence only:
NJIT-032 must be rebuilt and remeasured against the retained
NJIT-025+NJIT-029+NJIT-030+NJIT-031 source.

The post-NJIT-031 statistical profile still attributes 2.3% of Game of Life
samples and 3.7% of Rubido samples to `checkedAddress`, while `pop` remains
5.6% of Game of Life samples. The `executeCompactBlock` handler for opcode
`0x2d` currently calls `address`, which calls `popI32`, and then calls
`pushI32`; the generic `execute` handler already has a direct exact path.
NJIT-032 changes only the compact handler: validate stack availability, read
and decrement `valueTop`, validate the width-one effective address without
integer wrap, and write the zero-extended byte back to `values[valueTop++]`.
It must retain the current underflow-before-address ordering, the exact
out-of-bounds trap, successful stack height, instruction accounting, compact
region boundaries, and all existing generic behavior.

The hash-bound retained source is `WasmInterpreter.java` SHA-256
`b1438a228dbcb73ac2e3ae2eb5d4643dbda5ff39a0351be090f5505e2602b9da`.
Its counterless preverified interpreter is 84,756 bytes, SHA-256
`26e45b9d553fe4677425b0b05feb2a138deb060abfc3138480e6e178b1f0101f`;
the runtime class is SHA-256
`16657bca89a38f69d9845c28dd42b8b88ce4eda4e41546f2fe2be03f5aac47b5`.
The current host-test interpreter and runtime class SHA-256 values are
`d439755d5b6786ce265db1b7d42ccf3cee50c605995ab8c36a2cb905ce34a212`
and
`73abadb379e19fdd12962f732557b5d2631bc9b0fb635c719c2760332a7858bd`.
The post-NJIT-031 `flat.prf` SHA-256 values are
`15244f17a02ff3a4e39260a6839fee9da57267e708abbdcb0c901492a78fed83`
for Game of Life,
`4d001a43b94ebe97987328b7e71e106e0d92c12a1928a486a3f36066329e63dc`
for Rubido,
`b83f21d017cd331516de363df2e42d485cac49939d03ed61106bc1c2b39f698a`
for Waternet, and
`e081b431f88ff5839f42e12fb5deec3a73b96fb2d339ef5d1adad18e4622059f`
for Untangle. Profiling selects the candidate and is not timing evidence.

The corrected focused WAT and Java probe live under
`/tmp/w4me-interpreter-research/candidates/njit024-focused/src/`; their
SHA-256 values are
`226e885680e129940871b7b1acbb0b94c5c4bb272118c30a21888143a1c26171`
and
`2170e6475b02c7928132e356817f98b559cff020140456bca5141cbf6cac8c3e`.
They cover successful offsets at zero, byte boundaries, and the last memory
byte; eighteen instruction-budget boundaries; negative and end bases;
offset-past-end; unsigned offset overflow; and a non-wrapping sum. Before
timing, NJIT-032 must make baseline and candidate outputs byte-identical on
this fixture, pass the seven-workload full-state matrix, Java 1.3, CLDC,
target-47, preverification, release, cache, budget, and 16,000-byte method
gates, and record method/class/JAR and persistent-heap deltas. It must not add
W4IR, RMS, persistent fields, runtime selection branches, or
cartridge-specific recognition.

The authoritative primary measurement is at least twelve balanced native
i686 phoneME pairs on Game of Life against NJIT-031, with exact checkpoints
and counters. Acceptance requires a paired median of at least +0.8% and at
least nine wins. If that gate passes, run Rubido, Waternet, and Untangle
controls and reject any timer-resolved regression worse than -0.5%. The old
NJIT-024 measurements do not satisfy this gate and will not be pooled with
the new series. Any unrelated fused-budget or cartridge-fast-path behavior in
the retained baseline is explicitly outside this single-variable comparison;
phoneME artifacts keep cartridge fast paths disabled.

The isolated implementation had `WasmInterpreter.java` SHA-256
`9d326a4c961bbd6c8108355e919f0b4e52cbc5eab2a9aaa0564683189a429edd`.
Target-47 `executeCompactBlock` grew from 3,101 to 3,197 bytecode bytes.
The `0x2d` handler contained no `address`, `checkedAddress`, `popI32`, or
`pushI32` invocation. Its counterless preverified interpreter was 85,092
bytes, SHA-256
`6969495d164f927665b90cb2f7c7c9ca92fc60ce3642b8797ce93be7975e45f0`,
which is 336 class bytes above NJIT-031. The runtime remained byte-identical
at
`16657bca89a38f69d9845c28dd42b8b88ce4eda4e41546f2fe2be03f5aac47b5`.
Station and base release JARs were 275,491 and 272,969 bytes, each 85 bytes
above NJIT-031, with SHA-256
`78f73edf8a4efb4025f1b62c1655329d4bab1addfb27cf874f878772553368ae`
and
`c727ad72e295bf8a9c96e21f5ac8009f8250bf72e435f2c2c75b692ba96ac66f`.
There was no field, array, allocation, W4IR, RMS, persistent-heap, or release
resource delta.

`just test` and `just verify` passed. The focused baseline and candidate
outputs were byte-identical across 24 snapshots, SHA-256
`250f99be0038b7a3bde98e14f59679e0ab0c7800e570f30080e92c759ab205a5`.
The compiled focused WASM SHA-256 was
`47482964d5a8f70ec98212ca97cd2dc970fed7b96bcb1849bce13b226500a966`.
The seven-workload counterless exactness artifact was
`ae5b1e7c9340083c788ee1781f669812b65b3bddb0f9abe97f80c179c48e4429`.
The native phoneME candidate artifact was
`5bcbefc386b9e08484aea3beb4ac02b81709a0950dc44cd9f3249ed276d4b9a6`;
its build receipt SHA-256 was
`36103bd44f9fffea0d035e758a55b145d22f58c0464df3fc10f2ef81527fe163`.

The authoritative twelve-pair Game of Life result against NJIT-031 was
**+0.417%**, +13,000 us/frame, with 7 wins and 5 losses and a 1,000
us/frame timer resolution. Every checkpoint and deterministic counter
matched. The result is positive and timer-resolved, but it fails both
predeclared primary gates: at least +0.8% and at least 9/12 wins. Therefore
Rubido, Waternet, and Untangle controls were not run. The raw evidence is
`/tmp/w4me-interpreter-research/raw/njit024/runs/`
`njit032-compact-load8-vs-njit031-20260727/game-of-life-zig-edition/`;
`pairs.csv`, `paired-stats.txt`, and `receipt.txt` SHA-256 values are
`f1689950dc74986a6f60a1f2262f72af6943f93726b13cde9cea874c91c63f60`,
`13166b2caef55f8df9de2bfe1a88fe2ce14b9f0259b9ca16f15f689a7be1f01a`,
and
`cd845856a13d527e955c8410a4f2b5c09f1d631eaca490d0812969d1c4e52fe9`.

**Verdict:** reject and remove. The compact direct path is correct and
slightly faster, but the retained baseline reduces its marginal benefit below
the declared acceptance threshold. The earlier approximately +1% results
must not be reused because they came from a different source state.

### NJIT-033: compact `i32.eqz` top-of-stack overwrite

**Status:** `rejected`.

This candidate is the smallest untested form of the E15 top-of-stack overwrite
design. Generic `i32.eqz` already checks `valueTop` and writes the boolean
result to `values[valueTop - 1]`. The compact handler instead evaluates
`pushI32(popI32() == 0 ? 1 : 0)`, which crosses four private Java method
boundaries through `popI32 -> pop` and `pushI32 -> push`, decrements and then
increments the same stack pointer, and reloads the same stack array slot.
NJIT-033 applies the existing generic representation only to compact opcode
`0x45`; it does not combine instructions or add an opcode.

The exact W4IR corpus report SHA-256 is
`e9be1bf62e7499874da12fc3e119fa0f4c379a7e0654379e79c1137af029c59c`.
It records 737,257 dynamic `i32.eqz` operations on the 70-frame Rubido route,
76,806 on the one-frame Game of Life route, 694,600 on 60 Plasma frames,
45,244 on the 94-frame Waternet route, and 7,197 on the 401-frame Untangle
route. Rubido executes 9,138,220 logical instructions through 1,333,633
compact calls; Game of Life executes 6,407,444 through 482,291 compact calls.
Waternet and Untangle execute no compact calls and therefore serve as
whole-class layout controls. The report profiles opcodes with tiers disabled,
so the total opcode counts are selection evidence rather than an exact
compact-only count.

The hash-bound retained source is `WasmInterpreter.java` SHA-256
`b1438a228dbcb73ac2e3ae2eb5d4643dbda5ff39a0351be090f5505e2602b9da`.
Its counterless preverified interpreter is 84,756 bytes, SHA-256
`26e45b9d553fe4677425b0b05feb2a138deb060abfc3138480e6e178b1f0101f`;
the runtime class is SHA-256
`16657bca89a38f69d9845c28dd42b8b88ce4eda4e41546f2fe2be03f5aac47b5`.
The current host-test interpreter and runtime SHA-256 values are
`d439755d5b6786ce265db1b7d42ccf3cee50c605995ab8c36a2cb905ce34a212`
and
`73abadb379e19fdd12962f732557b5d2631bc9b0fb635c719c2760332a7858bd`.
The native VM, CLDC classes, and preverify identities remain the values
recorded by NJIT-031.

The implementation must keep the underflow check before any stack access,
leave `valueTop` unchanged on success, preserve raw i32 truncation from the
`long[]` slot, and write canonical `0L` or `1L`. It must not alter the generic
handler, compact eligibility/topology, accounting, budget points, stack
capacity behavior, W4IR, RMS, persistent fields, or cartridge behavior. A
focused warm compact fixture must compare zero, positive, negative, and
signed-boundary inputs and sweep exact instruction budgets around the handler.
Then pass seven-workload full-state, Java 1.3, CLDC, target-47,
preverification, release, cache, budget, 16,000-byte method, and heap gates.

Build a counterless candidate with no runtime selection branch. Run at least
twelve balanced native i686 phoneME pairs on Rubido as the primary route.
Accept only at a paired median of at least +0.8%, at least 9/12 wins, exact
deterministic signatures, and no unresolved semantic issue. If the primary
gate passes, run twelve Game of Life pairs plus Waternet and Untangle
no-regression controls; reject any timer-resolved control regression worse
than -0.5%. Do not pool this result with NJIT-032: the opcodes, helper paths,
coverage, and baseline/candidate class layouts differ.

The isolated implementation passed the full host suite and `just verify`.
The focused fixture exercised zero, positive, negative, and both signed
boundary values, then swept 18 instruction budgets around the completed
invocation. Its 19 baseline and candidate snapshots are byte-identical at
SHA-256
`975e038b91b837920113f1fe70dc5fe63beb7f402318636d3cf2cde69a859d80`;
the fixture module SHA-256 is
`62c200b76e9cd2805b70c68512122c728b64f87efd0746c06da497c9e2b97edb`.
Target-47 `javap` confirmed that compact opcode `0x45` changed from two
`invokespecial` calls to an underflow guard and direct `long[]` overwrite.
`executeCompactBlock` grew from 3,101 to 3,133 bytecode bytes. The candidate
host-test interpreter was 54,021 bytes at SHA-256
`9fc978c04ad4ab33c8b4a841af3b2af6f3ee9f10511bba6a09a838c22be9cef4`.
The counterless preverified interpreter was 84,823 bytes at SHA-256
`57ac195fb90f8b8928d48e2c145f6ec627845b798c99c85986ee1f649e094854`;
the unchanged runtime remained 23,625 bytes at SHA-256
`16657bca89a38f69d9845c28dd42b8b88ce4eda4e41546f2fe2be03f5aac47b5`.
The exact counterless artifact SHA-256 was
`3291f63dc009338fcb05c3c29011de81ca79eb1d556b5c0f74f60ffeaa3e0d79`.

The authoritative twelve-pair native i686 phoneME Rubido A/B measured a
paired median of only **+0.123%**, or 108.5 us/frame, with 8 wins and 4
losses. Deterministic signatures matched, the order was balanced, and the
timer-resolution gate passed, but the result failed both predeclared
acceptance thresholds of +0.8% and 9/12 wins. The pair values are:

```text
sample  baseline  candidate  order
0       88147     88007      baseline-first
1       87100     87821      candidate-first
2       87674     87496      baseline-first
3       87914     87705      candidate-first
4       87968     87852      baseline-first
5       87945     87798      candidate-first
6       87914     88372      baseline-first
7       87930     87829      candidate-first
8       87790     87720      baseline-first
9       87767     87984      candidate-first
10      88689     87906      baseline-first
11      87147     87651      candidate-first
```

Raw evidence is under
`/tmp/w4me-interpreter-research/raw/njit024/runs/`
`njit033-compact-eqz-vs-njit031-20260727/rubido/`.
`pairs.csv`, `paired-stats.txt`, and `receipt.txt` have SHA-256
`ea608570114f0a7a861750c02f950a06d73015701c4796877e0809913243ad2e`,
`a3222db70063ef3c89d9a177b9a0c786c10d1b8cc9f6751bcd1293637ef41f63`,
and
`102fc194bf6044181c30d4fdd7df5b4793117d2ea2c02077bfa1789427d8e537`.
Because the primary gate failed, the predeclared controls were not run. The
candidate implementation was removed and the retained NJIT-031 source was
restored exactly. The result closes this exact helper-elimination shape:
removing two calls did not overcome the extra array and field traffic by a
material margin on the target VM.

### NJIT-034: compact `i32.load` caller-inline address guard

**Status:** `rejected`.

This candidate follows the explicit reconsideration condition of NJIT-005
without repeating NJIT-026 or NJIT-032. NJIT-005 proved that the folded
effective-address predicate is exact and measured a positive but below-floor
+0.407% when it only changed the shared helper body. NJIT-026 inlined the
complete generic `i32.load` handler and was neutral to negative outside
Rubido. NJIT-032 inlined the six-call compact `i32.load8_u` chain and failed
its post-NJIT-031 acceptance gate. NJIT-034 changes only compact opcode
`0x28`: it removes the `checkedAddress` Java call, applies the already-proven
two-condition guard at the caller, and retains the existing `loadI32` helper.
It therefore tests a smaller bytecode/layout change on a different, much
hotter opcode.

The exact current corpus report is SHA-256
`e9be1bf62e7499874da12fc3e119fa0f4c379a7e0654379e79c1137af029c59c`.
With tiers disabled for opcode counting it records 823,125 dynamic
`i32.load` operations on the 70-frame Rubido route. The optimized tier run
executes 9,240,467 logical instructions in 1,338,988 compact calls. The same
source stream also contains 750,980 fused `i32.load + local.tee` operations,
but that separate compact handler is deliberately excluded from this first
single-site experiment. The post-NJIT-031 native statistical profile assigns
39.7% of Rubido Java ticks to `executeCompactBlock` and 3.7% to
`checkedAddress`; its `flat.prf` SHA-256 is
`4d001a43b94ebe97987328b7e71e106e0d92c12a1928a486a3f36066329e63dc`.
The profile is candidate-selection evidence, not timing proof, and it does
not distinguish ordinary from fused callers.

The retained baseline source is `WasmInterpreter.java` SHA-256
`b1438a228dbcb73ac2e3ae2eb5d4643dbda5ff39a0351be090f5505e2602b9da`.
Its host-test interpreter and runtime classes are 53,981 and 17,213 bytes at
SHA-256
`d439755d5b6786ce265db1b7d42ccf3cee50c605995ab8c36a2cb905ce34a212`
and
`73abadb379e19fdd12962f732557b5d2631bc9b0fb635c719c2760332a7858bd`.
The stable counterless preverified interpreter is 84,756 bytes at SHA-256
`26e45b9d553fe4677425b0b05feb2a138deb060abfc3138480e6e178b1f0101f`;
the runtime is 23,625 bytes at SHA-256
`16657bca89a38f69d9845c28dd42b8b88ce4eda4e41546f2fe2be03f5aac47b5`.
The stable station/base JARs are 275,406 and 272,884 bytes at SHA-256
`e522fa8740790478b9eed483a3ecbe7f596f45209a1d8e2fe61d185c19a49448`
and
`920e540af0ee636e989be1d5e1316a51597472c1c8f6e823a2347c96794dd3a0`.
The native VM, CLDC classes, and preverify identities remain the values
recorded by NJIT-031.

The isolated compact handler must preserve the underflow check before address
evaluation and leave `valueTop` unchanged on success and on any
out-of-bounds trap. After the underflow check it reads the signed Java `int`
base from `values[valueTop - 1]`, computes
`maximumBase = module.memory.length - 4`, and rejects
`(base | operand) < 0 || base > maximumBase - operand`. Java's left-to-right
short-circuit evaluation is required: the subtraction is reached only when
both values are nonnegative, so it cannot overflow. The successful path calls
the unchanged `loadI32(base + operand)` and stores the same sign-extended
`int` result in the existing slot.

Do not change generic execution, the fused load/tee handler, `checkedAddress`,
`loadI32`, compact topology, accounting, budget points, W4IR/RMS, fields,
arrays, allocations, or cartridge behavior. Reuse the existing integer
compact and load/tee differentials for success, final-byte trap, stack,
budget, outer/compact, and fused-control coverage; add an isolated
baseline/candidate snapshot only if those gates leave an operand boundary
uncovered. Pass the seven-workload full-state matrix, Java 1.3, CLDC,
target-47, preverification, release, cache, 16,000-byte method, and
counterless exactness gates before timing.

Build a branch-free counterless candidate and run at least twelve balanced
native i686 phoneME Rubido pairs against retained NJIT-031. Accept only at a
paired median of at least +0.8%, at least 9/12 wins, exact deterministic
signatures, and no unresolved semantic issue. If the primary gate passes, run
at least twelve Game of Life pairs plus Waternet and Untangle controls; reject
any timer-resolved regression worse than -0.5%. If it fails, skip controls,
record the raw evidence, remove the implementation, and restore the retained
source exactly. A later fused-load variant is allowed only as a separately
measured candidate and must not pool its result with NJIT-034.

The isolated implementation passed the complete host and CLDC gates. The
24-snapshot focused baseline/candidate comparison was exact at SHA-256
`8a4eb3d213aa22585d4ed57fe215094563db140903d917a036ebae8fcd24c32f`;
its WAT source was
`73c7dec39768607728d9e8d2c93cc8f0bb4abc02117ea2affb029aafd104425b`.
The seven-workload counterless full-state matrix was exact with artifact
SHA-256
`fcc49bbb1b212ab40215d8811e879dcd6978eab043382980567c89c08b781e16`
and receipt SHA-256
`598dc83c3e69e0644dc3ffb70d42a13b23fb362f4df1729bed545470c65463af`.
The target-47 host interpreter class was 54,029 bytes at SHA-256
`a5d13d7c253fad2f6682451eaded358497cdcbc9d7418e65d1f74f8d151d5f63`;
the compact method grew from 3,101 to 3,137 bytes. The timed counterless
preverified interpreter was 84,906 bytes at SHA-256
`bb4bf2d7c5acfa6e354ead9571ca5d249fc38809650cbbfecd83c975145b9ac0`;
its artifact identity was
`4b99dcd8718be33d0d2239987c26ddd613ee7de0857af0e19123683b34e3f941`.

Twelve balanced native i686 phoneME Rubido pairs against retained NJIT-031
produced a timer-resolved paired median of **-0.106%**, or -93.0
microseconds/frame, with 5 wins and 7 losses. The result fails both the +0.8%
effect gate and the 9/12 consistency gate, so the declared Game of Life,
Waternet, and Untangle controls were skipped. Raw evidence is under
`/tmp/w4me-interpreter-research/raw/njit024/runs/`
`njit034-compact-load-address-vs-njit031-20260727/rubido`;
`pairs.csv`, `paired-stats.txt`, and `receipt.txt` have SHA-256
`f27cae62039655bfe6bb3dce79560302f1f96e0a4c7dd534c3ede64ec7b792ce`,
`885cd7a56096323faba1ff849e8ee966ae275a1ef62a90234ef22ee98fc8a698`,
and
`594700056cbe42c1c7ccde5df7565960418931c5846c8ad4a4f68ab1224c0ab3`.
The candidate was removed and the retained NJIT-031 source restored exactly.
This closes the ordinary compact `i32.load` caller-inline guard shape; a fused
load/tee experiment remains a distinct candidate and cannot reuse this timing
result.

### NJIT-035: inline generic control-frame exit

**Status:** `accepted`.

The retained post-NJIT-031 native i686 phoneME statistical profile still
attributes 0.9% of Rubido ticks to the private `leaveControl(int)` method,
after assigning 47.1% to `execute`, 39.7% to `executeCompactBlock`, and 2.2%
to `transfer`. Its Rubido `flat.prf` SHA-256 is
`4d001a43b94ebe97987328b7e71e106e0d92c12a1928a486a3f36066329e63dc`.
The Game of Life, Waternet, and Untangle profile hashes are
`15244f17a02ff3a4e39260a6839fee9da57267e708abbdcb0c901492a78fed83`,
`b83f21d017cd331516de363df2e42d485cac49939d03ed61106bc1c2b39f698a`,
and
`e081b431f88ff5839f42e12fb5deec3a73b96fb2d339ef5d1adad18e4622059f`.
These samples select a candidate only; they are not timing evidence.

The exact current generic corpus artifact is
`163f230c2cb19a64a5efce3e315225ef509412fb6275a010eebdac234ffd28d4`
and its report SHA-256 is
`6d2362c6272120eb0b28a4bdcd58f0e884ca2bfa2b13f262cc7748e4a200a945`.
It records 133,183 dynamic `end` operations on the 70-frame Rubido route,
535,685 on the one-frame Game of Life route, 26,766 on the 94-frame Waternet
route, and 40,623 on the 401-frame Untangle route. It also records 44, 0, 29,
and 3,143 dynamic `else` operations respectively. A false `if` without an
else is the third caller but is not separately counted by the existing
profiler, so these opcode totals are coverage bounds rather than an exact
method-call count.

The candidate changes only the three generic dispatch sites that call
`leaveControl`: false `if` without an else, `else`, and non-terminal `end`.
At each site it must preserve the helper's exact ordering: check
`controlTop <= 0`, compute `frame = controlTop - 1`, call the retained
`transfer(resultCount, controlBase[frame])`, then assign
`controlTop = frame`. The private helper may be removed only after target-47
`javap` proves no call site remains. Do not inline or otherwise change
`transfer`, terminal function return, direct branches, compact execution,
instruction accounting, W4IR/RMS, arrays, fields, allocations, trap text, or
cartridge behavior.

The retained baseline is NJIT-031: `WasmInterpreter.java` SHA-256
`b1438a228dbcb73ac2e3ae2eb5d4643dbda5ff39a0351be090f5505e2602b9da`;
host interpreter class 53,981 bytes at
`d439755d5b6786ce265db1b7d42ccf3cee50c605995ab8c36a2cb905ce34a212`;
counterless preverified interpreter 84,756 bytes at
`26e45b9d553fe4677425b0b05feb2a138deb060abfc3138480e6e178b1f0101f`.
The runtime remains 23,625 bytes at
`16657bca89a38f69d9845c28dd42b8b88ce4eda4e41546f2fe2be03f5aac47b5`.

Before timing, pass the static control, malformed descriptor, defined-call,
value-stack, seven-workload full-state, Java 1.3, CLDC, target-47,
preverification, release, cache, budget, 16,000-byte method, and counterless
gates. Run at least twelve balanced native i686 phoneME pairs on Rubido and
Game of Life against retained NJIT-031. Accept only if at least one primary
has a timer-resolved paired median of at least +0.3%, neither primary is
worse than -0.5%, and the improving route wins at least 9/12 pairs. If those
gates pass, run Waternet and Untangle as no-regression controls with the same
-0.5% floor. Otherwise record the raw evidence, remove the implementation,
and restore retained source and release artifacts exactly.

The isolated implementation has `WasmInterpreter.java` SHA-256
`3b6b4c8b8e1d65689d7c7837777cd89172b390044def0149d301da22027b3d8f`.
Target-47 `javap` contains no `leaveControl` method or call target. Diagnostic
and counterless `execute` sizes are 7,937 and 7,905 bytes, up from 7,833 and
7,801 in NJIT-031 and below the 16,000-byte sanity limit. The host-test
interpreter class is 54,008 bytes at SHA-256
`b4867376d7cab7926a624a014264acd7f986e8c32cf6567e51d935635cf0bcb2`.
The timed counterless preverified interpreter is 84,936 bytes at SHA-256
`e65235aaf3f0430d639982dbf21851946bc48f1500c9ab2ab98d8bbd979e635a`,
180 bytes above the retained baseline. The runtime remains byte-identical at
SHA-256
`16657bca89a38f69d9845c28dd42b8b88ce4eda4e41546f2fe2be03f5aac47b5`.
There is no field, array, allocation, W4IR, RMS, persistent-heap, or release
resource delta.

`just test` and `just verify` pass, including static control descriptors,
defined calls, malformed descriptors, stack capacity, every budget boundary,
and all seven exact full-state workloads. Counterless exactness artifact
SHA-256 is
`9c8ab1a6fa89b19968859540de0fed5456a2f77efbf3b7fcbb53bcd04dfd8d6a`;
its receipt is
`4e80c4fcce681dafa80027a9b9080db894729e1ae6bde97e8d6b9f19c3fce39e`.
The staged phoneME artifact identity is
`750659edaf7f2adebedd65267cdc346daadd9477fcaad44ed2f096037b4219ab`.
The station/base JARs are 275,441 and 272,919 bytes at SHA-256
`5a98900951eb1eef35db1138bd70b9f9011844b32ee2d7ba830f3d3a6136d135`
and
`9d6c9a0cd211e6e2b42f68a6e48bdd3b3d5e2e7247c9623a6ad2564a6999701c`.

The completed native i686 phoneME pairs against NJIT-031 are:

| Route        |          Median paired effect | Wins/losses/ties | Evidence                            |
| ------------ | ----------------------------: | ---------------: | ----------------------------------- |
| Rubido       |  **+0.697%**, +612.5 us/frame |           12/0/0 | measured                            |
| Game of Life | **+0.914%**, +28,500 us/frame |            8/4/0 | measured                            |
| Waternet     |       -0.236%, -26.0 us/frame |            5/7/0 | measured, above -0.5% floor         |
| Untangle     |        -0.179%, -3.0 us/frame |            5/6/1 | exploratory, below timer resolution |

Every route matched checkpoints and deterministic signatures. Raw evidence
is under `/tmp/w4me-interpreter-research/raw/njit024/runs/`
`njit035-inline-control-exit-vs-njit031-20260727/`. Rubido
`pairs.csv` / `paired-stats.txt` / `receipt.txt` SHA-256 values are
`2475bb44576ac5d0412b1e904658bbdb30142441d482766449d25668d0ad371e`,
`c159b73214d706c794165e2c674d8d1e4637cdb70a14792b42a46c91dd226bc4`,
and
`48138c36fa31e77469f41c477b97cd0a03b9259d2f744070b3f73f9673f70d91`.
Game of Life values are
`9d419e212afb07eb0fe9be03fa043dafbe223747d7c6d3f99313e6631afd3afc`,
`cfbf2a8d537b63e142d5bb08ace5c550ba4d779be9bd13cecd9c0fd5e6a6398b`,
and
`e950c87ccf64133dff0ef930509531fee6e12b4eec43aecfe5b150c14605952c`.
Waternet values are
`c2718b4d5148599d6e6aabd4305c1f7b18b87b2a58625992cc4ed267c4ce913b`,
`5e32952b7c1adb7e767a8af5959acd2c1a016e5f2b821d1ad9006310136a6f85`,
and
`5c23f4419cb6cf8214130d0e3abb38b77786fe00641b6480a088cf28d12d48cd`.
Untangle values are
`60cc6809820760e019e7e4a5ffb3e73fd4f426a7c980a40f4218ef75cd534700`,
`73472368c900002b77aee9728cc797e1126f19513327ad2564df7c44796937d5`,
and
`cfcc3d57717d2cd2fa8f93e735da98b3884305afb8fbbc30494c240fff6441c8`.

**Verdict:** accept and retain. Rubido clears every predeclared primary gate
with twelve wins, Game of Life independently improves, Waternet stays inside
the declared no-regression floor, and Untangle's small negative median is
below one timer tick per frame. The candidate removes a real Java frame from
all generic control exits without changing runtime state or formats.

### NJIT-036: direct generic branch-condition pop

**Status:** `rejected`.

The native i686 phoneME selection profile was repeated after accepting
NJIT-035. Rubido now attributes 44.7% of samples to `execute`, 41.7% to
`executeCompactBlock`, and 1.7% to the private `pop()` helper; its
`flat.prf` SHA-256 is
`495aa73d521c2c0bb443e3c88d3e4ef07fb09268cbfc222be1c9de3ec0456b09`.
The profiler VM is the same SHA-256
`5f0d0bc236742cb5e166feba9ffe24e3ff866d586d64395ba51d77e25011380c`
selection build used for NJIT-029 through NJIT-035. Its timing is perturbed
and is not acceptance evidence.

The exact current generic corpus report, SHA-256
`6d2362c6272120eb0b28a4bdcd58f0e884ca2bfa2b13f262cc7748e4a200a945`,
records 1,447,533 dynamic `if` plus 115,891 `br_if` operations on the
70-frame Rubido route. Game of Life records no `if` and 970,256 `br_if`
operations in its single measured frame. Waternet records 45,679 `if`
operations; Untangle records 60,549. These control opcodes never execute in a
compact block, so every occurrence reaches the generic handler and currently
calls `popI32()`, which calls `pop()`.

NJIT-036 changes only those two condition consumers. Preserve the exact
helper ordering by checking `valueTop <= 0`, throwing the canonical
`value stack underflow` trap, then reading `(int) values[--valueTop]`. The
existing `if` control-stack check, frame creation, branch targets, direct
branch descriptors, shadow verification, fallthrough, accounting, and
instruction budget remain byte-for-byte in the same semantic order after the
pop. Do not change `br_table`, `pop()`, `popI32()`, comparison handlers,
compact execution, W4IR/RMS, arrays, fields, allocations, or cartridge
behavior.

The retained baseline is accepted NJIT-035:
`WasmInterpreter.java` SHA-256
`3b6b4c8b8e1d65689d7c7837777cd89172b390044def0149d301da22027b3d8f`;
counterless preverified interpreter 84,936 bytes at
`e65235aaf3f0430d639982dbf21851946bc48f1500c9ab2ab98d8bbd979e635a`;
runtime 23,625 bytes at
`16657bca89a38f69d9845c28dd42b8b88ce4eda4e41546f2fe2be03f5aac47b5`.
NJIT-035's station/base JAR hashes and exactness artifact remain its retained
release evidence.

Before timing, pass focused underflow, true/false branch, descriptor shadow,
budget, static-control, seven-workload full-state, Java 1.3, CLDC, target-47,
preverification, release, cache, 16,000-byte method, and counterless gates.
Run at least twelve balanced native i686 phoneME Rubido and Game of Life pairs
against retained NJIT-035. Accept only if at least one primary reaches a
timer-resolved +0.5% median with at least 9/12 wins, neither primary regresses
worse than -0.5%, and every deterministic signature is exact. If the primary
gate passes, run Waternet and Untangle controls with the same -0.5% floor;
otherwise remove the candidate and restore NJIT-035 exactly.

The isolated candidate used `WasmInterpreter.java` SHA-256
`2942072f1b25152536564777bec14a5ed1905152fc7a311521f645c735607946`.
Its host target-47 class was 54,092 bytes at SHA-256
`ec0454e01a8b1702b25e76f0feb20d69e883a27defd066286812dff6a8d81052`;
the release and counterless `execute` methods were 8,001 and 7,969 bytes.
The station/base JARs were 275,513/272,991 bytes at SHA-256
`7a46ce082c433c0bea701c86e2a18af8d5d586549f4925a317253e0061fb04ec`
and
`b5ad6472295f95af8d76bfd3149f31d98933dfa8d7df2cd5a7fbff1a45fc2e8a`.
The seven-workload counterless exactness artifact was
`2128f1b6014cc14d725b85d393b8cf291257dfbd57cc83dace2bf9cefb3c1ff5`;
its receipt SHA-256 was
`2a5bf9050dd2482c595538a83f4ae27fbc1d72f5f677c391950ee3f2b63f2e55`.
The timed counterless artifact identity was
`6709cc830b7e53607fe41e3bb5688cf3cfe6f9f4cf1035196da8a29d97d25d42`;
its preverified interpreter was 85,194 bytes at SHA-256
`14b1cd748fc4375154faaefa799e6d191fdbe6f2da8da1f6292f665405978e0c`.
The runtime class remained byte-identical to NJIT-035.

Twelve balanced native i686 phoneME pairs against retained NJIT-035 did not
confirm the hypothesis:

- Rubido: median `-11.5 us/frame`, `-0.013%`, 5 wins, 7 losses, below timer
  resolution;
- Game of Life: median `-4,000 us/frame`, `-0.128%`, 5 wins, 6 losses, 1 tie.

The raw evidence root is
`/tmp/w4me-interpreter-research/raw/njit024/runs/njit036-direct-branch-condition-pop-vs-njit035-20260727`.
Rubido `pairs.csv`, `paired-stats.txt`, and `receipt.txt` have SHA-256
`2dc02d0918630bac009c2d4f59c4deea8596b405ece959f55d276f2d65e51d82`,
`3a3605cea779b99e98a170a43b2767fc8a6fcf731015124d442228a789826f93`,
and
`d482e81268d1db3caf5f68ddc9c1497b9549b3a0438eee6f8339f00ca83e466b`.
Game of Life equivalents have SHA-256
`209730abf971b2b8c6755a4834fcd2befcb28004074255ceda105814bc3aa7f6`,
`2ea63acc0a103ba3d2f90d10e16feb7b441fd87b53b3ce3069d254ef3ff47e59`,
and
`2f3d0689210c9ed6626886542372a97c6d6da0921f3948ab5b722b4de7fef5c7`.

**Verdict:** reject. Neither primary workload reached the predeclared +0.5%
and 9/12 gate, so Waternet and Untangle controls were correctly skipped. The
two removed Java calls were offset by the larger generic handlers and local
layout on the judge VM. The candidate was removed and the production source
was restored exactly to accepted NJIT-035 SHA-256
`3b6b4c8b8e1d65689d7c7837777cd89172b390044def0149d301da22027b3d8f`.

### NJIT-037: direct compact `w4ir.local_local`

**Status:** `rejected`.

The post-NJIT-035 native phoneME selection profile attributes 2.1% of Rubido
samples to `executeCompactFused`. The exact corpus report at SHA-256
`6d2362c6272120eb0b28a4bdcd58f0e884ca2bfa2b13f262cc7748e4a200a945`
records 747,218 dynamic `w4ir.local_local` operations on Rubido and 204,800 on
Game of Life. Both workloads enter compact blocks, while Waternet and
Untangle have zero compact calls and are natural no-effect controls. These
stream counts are exact upper bounds; native timing, not the count alone,
decides whether enough occurrences reach the compact handler to matter.

The current compact loop sends every unlisted fused opcode through one
`executeCompactFused(...)` Java call. Its `w4ir.local_local` handler then
makes two more `push(...)` calls before returning span two. NJIT-037 moves
only this opcode into the main `executeCompactBlock` switch and performs the
same two sequential stack-capacity checks and writes:

- check `valueTop >= values.length`, trap with the canonical
  `value stack exhausted`, then push `locals[operand]`;
- repeat the same check before pushing `locals[auxiliary]`;
- set `span = 2` and retain the existing post-handler two-instruction
  accounting.

The sequential checks are required. Combining them into one precheck would
change observable trap state when exactly one stack slot remains: the current
helper path commits the first push before the second push traps. Do not change
the generic fused handler, other fused opcodes, compact region formation,
instruction accounting, W4IR/RMS formats, fields, arrays, allocations, or
cartridge behavior. Remove the now-unreachable duplicate case from
`executeCompactFused`.

The retained baseline is accepted NJIT-035 at source SHA-256
`3b6b4c8b8e1d65689d7c7837777cd89172b390044def0149d301da22027b3d8f`,
counterless interpreter SHA-256
`e65235aaf3f0430d639982dbf21851946bc48f1500c9ab2ab98d8bbd979e635a`,
and exactness artifact
`9c8ab1a6fa89b19968859540de0fed5456a2f77efbf3b7fcbb53bcd04dfd8d6a`.
Before timing, pass a focused zero/one/two-free-slot stack differential,
seven-workload full-state, Java 1.3, CLDC, target-47, preverification,
release, cache, 16,000-byte method, and counterless gates. Inspect
`executeCompactBlock` and `executeCompactFused` byte sizes so method growth is
recorded rather than guessed.

Run at least twelve balanced native i686 phoneME pairs against retained
NJIT-035 on Rubido and Game of Life. Accept only if at least one primary
reaches a timer-resolved +0.5% median with at least 9/12 wins, neither primary
regresses worse than -0.5%, and every deterministic signature remains exact.
If the primary gate passes, run Waternet and Untangle controls with a -0.5%
floor; otherwise remove the candidate and restore NJIT-035 exactly.

The isolated candidate used `WasmInterpreter.java` SHA-256
`1a785b9a6144015354aff5ff42b5623578ae4cbae1762eac9c1ac5368a79d0a4`.
Its focused test source had SHA-256
`e5ba8e7f22e42e19b8134ac97a77c7c2b4376d1c1813a2ee0ae0a942e84890fe`
and verified the zero-, one-, and two-free-slot cases, including the required
partial first push. The host target-47 class was 54,101 bytes at SHA-256
`63ccfb2fafbba47f789ca54c051089f8994834b4eec9453eb4cd7fa01159e68b`.
`executeCompactBlock` grew from 3,101 to 3,193 bytecodes while
`executeCompactFused` shrank from 1,376 to 1,357. The station/base JARs were
275,476/272,954 bytes at SHA-256
`71d6d0000cdd58ad099f765238c66deead6c3a2a1fc23c26f5154f8b10f9d7de`
and
`3328556903e13f725ca90e94185e5492ec0806b453ad374845639e3671dd7e52`.
The seven-workload counterless exactness artifact was
`6670ad3cb375393a82f6bb704d60abbd1cf371ddab956c00a5babe325bf94e33`;
its receipt SHA-256 was
`1e29f956c554b28c1aa1ff634fe71538c33995900624b17f6e14e24456e17dad`.
The timed counterless artifact identity was
`5049d8e8c4c0a5a379d0fcf9a6b6ada8cc6e87514bff7ed2a0a1d2b6cdc94c3c`;
its preverified interpreter was 85,088 bytes at SHA-256
`97bafaef8f7f59bf08b7d7eb3cffad796fc8decf54f0dfd9047f8ab132295610`.
The runtime class remained byte-identical to NJIT-035.

Twelve balanced native i686 phoneME pairs against retained NJIT-035 produced:

- Rubido: median `+128 us/frame`, `+0.147%`, 7 wins and 5 losses;
- Game of Life: median `-4,500 us/frame`, `-0.145%`, 4 wins and 8 losses.

The raw evidence root is
`/tmp/w4me-interpreter-research/raw/njit024/runs/njit037-direct-compact-local-local-vs-njit035-20260727`.
Rubido `pairs.csv`, `paired-stats.txt`, and `receipt.txt` have SHA-256
`e91bfb3ab42295aa58dbe164d0398445dcef2a47ab82b49773062468e7bab7e6`,
`143391b197918178aa9cc32eacd6cf46a7db6b5cae6e3abd4497978465041f33`,
and
`51fff1b8a37d1b0bf8b91a47531600da32981f5d2df692cc87dfd2d2ec3da07a`.
Game of Life equivalents have SHA-256
`50cf9cd479fd557a4c31174f63c31f987197f61fb8f5d4b964c516da49a45ae1`,
`5625cfb60bfec659b6b1742a6ae163d75a9e1c15ceeb9b11685d84a94e048843`,
and
`714178d94d62447f0f51d460ccc1899dd855a7c93fffae63f0246ddfa4ddfc61`.

**Verdict:** reject. Rubido's small positive median did not reach +0.5% or
9/12 wins, and Game of Life moved negative. Removing one outer Java call and
two `push` calls did not outweigh the larger compact loop and changed method
layout on phoneME. Waternet and Untangle controls were skipped because neither
primary gate passed. The production and focused-test changes were removed;
`WasmInterpreter.java` was restored exactly to accepted NJIT-035 SHA-256
`3b6b4c8b8e1d65689d7c7837777cd89172b390044def0149d301da22027b3d8f`.

### NJIT-038: compact `w4ir.local_set_get` top replacement

**Status:** `rejected`.

The same post-NJIT-035 phoneME profile leaves 2.1% of Rubido samples in
`executeCompactFused`. The exact corpus report at SHA-256
`6d2362c6272120eb0b28a4bdcd58f0e884ca2bfa2b13f262cc7748e4a200a945`
records 698,736 dynamic `w4ir.local_set_get` operations on Rubido. Game of
Life has only one occurrence; Waternet and Untangle have 4,281 and 19,682 but
zero compact calls, so Rubido is the sole timing primary and the other routes
are no-regression controls.

The current compact path calls `executeCompactFused`, which executes
`locals[operand] = pop(); push(locals[auxiliary]);`. After a successful pop,
the push cannot exhaust the fixed-size value stack because it reuses the slot
that was just released. NJIT-038 moves only this opcode into
`executeCompactBlock` and replaces the pair with:

- check `valueTop <= 0` and throw the canonical `value stack underflow`
  before changing any local;
- save `values[valueTop - 1]`, assign it to `locals[operand]`, then replace
  `values[valueTop - 1]` with `locals[auxiliary]`;
- leave `valueTop` unchanged, set span two, and retain the existing
  post-handler two-instruction accounting.

Assignment order is part of the contract. When `operand == auxiliary`, the
source read must observe the newly assigned local, exactly as the current
pop-then-push path does. Do not change other fused handlers, the generic
handler, compact regions, formats, arrays, fields, allocations, or cartridge
behavior. Remove the duplicate helper case while the candidate is active.

The baseline remains accepted NJIT-035 at source SHA-256
`3b6b4c8b8e1d65689d7c7837777cd89172b390044def0149d301da22027b3d8f`,
counterless interpreter SHA-256
`e65235aaf3f0430d639982dbf21851946bc48f1500c9ab2ab98d8bbd979e635a`,
and exactness artifact
`9c8ab1a6fa89b19968859540de0fed5456a2f77efbf3b7fcbb53bcd04dfd8d6a`.
Before timing, pass focused distinct-local, aliased-local, and underflow
differentials plus seven-workload full-state, Java 1.3, CLDC, target-47,
preverification, release, cache, bytecode, and counterless gates.

Run at least twelve balanced native i686 phoneME Rubido pairs. Accept only a
timer-resolved median of at least +0.5% with at least 9/12 wins and exact
deterministic signatures. If that primary gate passes, run Game of Life,
Waternet, and Untangle controls with a -0.5% floor. Otherwise remove the
candidate and restore NJIT-035 exactly.

The implementation and its focused edge fixture passed before timing. The
candidate source and fixture SHA-256 values were
`dcea0965bac385c3af39c3560500ac45ecb0318392369ea2b7302c2064c13141`
and
`8c261102a1fe003d6072c4827498ce8241301e76d26ca621c161078efdbb6cf2`.
The focused fixture covered distinct locals, `operand == auxiliary`, and
underflow before local mutation. Full `just verify` passed all seven
full-state routes, Java 1.3, CLDC, target-47, preverification, release, cache,
bytecode, and counterless gates. The counterless exactness artifact was
`eb3ec6ce5984986f98ee312a245c50c1eb6228263db9a82206e52bdecc53b12c`
and its receipt SHA-256 was
`47cd966fc2250a82d9a51be76af068dee403aecd08e4a8d5a2dd11a34fe71daf`.
`executeCompactBlock` grew from 3,101 to 3,193 bytecodes while
`executeCompactFused` shrank from 1,376 to 1,357. The release/base JARs were
275,467/272,945 bytes with SHA-256
`30da6d73278d79be48940ffaf0c13568f07aa66ab7481b2ca56faca1cf9538cb`
and
`b35f26d7964c4c71da2409bd9f4f6d964f08087e03f962e811659b058cf39bd6`.

The timed artifact was
`f5d7543adcde5e8ccfe74829497e835a8ca17f82cc8b842303753691f0d76ae0`;
its 85,026-byte counterless interpreter SHA-256 was
`0e047c6e438be6a0475d60922c66c945cb82b04a09cb3e94f400b8bce2d26123`.
Twelve balanced native i686 phoneME Rubido pairs measured only +0.249%
median with 7 wins and 5 losses. This misses both the predefined +0.5%
effect floor and 9/12 win floor, so the no-regression controls were not
eligible. Raw evidence is under
`/tmp/w4me-interpreter-research/raw/njit024/runs/`
`njit038-compact-local-set-get-top-vs-njit035-20260727/rubido/`;
`pairs.csv`, `paired-stats.txt`, and `receipt.txt` have SHA-256
`27a832a5f227332ec9332cac2253cb974ee51fcba2432573ae357dd581a5ed87`,
`66f5975dedcd3871d151b0f524e31bcaa26396259c6a174cf6b0151d8177f464`,
and
`9aead5c5b3e865385d3d8cdc584849e6e63603d781af73605bc891f8cbbc40d3`.
The candidate and its temporary fixture were removed, restoring NJIT-035
exactly. Do not repeat this handler-only shape without a materially different
code layout or VM-cost hypothesis.

### NJIT-039: direct compact `w4ir.local_i32_const_add`

**Status:** `rejected`.

NJIT-037 and NJIT-038 showed that moving a fused handler into the main compact
switch is not sufficient by itself: their Rubido medians were only +0.147% and
+0.249%. `w4ir.local_i32_const_add` has a materially different handler shape.
The current path calls `executeCompactFused`, then `pushI32`, then `push`,
whereas a direct compact handler can perform the local read, 32-bit wrapping
addition, capacity guard, and single stack write without any Java method call.

The exact corpus report at SHA-256
`6d2362c6272120eb0b28a4bdcd58f0e884ca2bfa2b13f262cc7748e4a200a945`
records 702,113 dynamic occurrences on Rubido and 281,920 on Game of Life.
It also records 1,401,140 on the generic Plasma route, but that route is not
available in the native phoneME route harness and the production Plasma route
uses a cartridge fast path, so it is not a valid timing primary for this
candidate. Waternet and Untangle record 8,126 and 23,608 occurrences but have
zero compact calls under the retained tier policy.

Move only
`W4IR_LOCAL_I32_CONST_ADD + W4IR_EXECUTION_OFFSET` into the main compact
switch. Before mutation, check `valueTop >= values.length` and throw the
canonical `value stack exhausted`. Otherwise write
`(int) locals[operand] + auxiliary` into `values[valueTop++]`, preserving Java
`int` wraparound and the existing sign-extended `long` representation, set
span three, and retain the common three-instruction accounting. Remove the
duplicate helper case while the candidate is active. Do not change other
handlers, tier selection, formats, arrays, fields, allocations, or cartridge
behavior.

The baseline remains accepted NJIT-035 at source SHA-256
`3b6b4c8b8e1d65689d7c7837777cd89172b390044def0149d301da22027b3d8f`,
counterless interpreter SHA-256
`e65235aaf3f0430d639982dbf21851946bc48f1500c9ab2ab98d8bbd979e635a`,
and exactness artifact
`9c8ab1a6fa89b19968859540de0fed5456a2f77efbf3b7fcbb53bcd04dfd8d6a`.
Before timing, pass focused empty-slot, last-slot, full-stack, signed-result,
and wrapping-add differentials plus seven-workload full-state, Java 1.3,
CLDC, target-47, preverification, release, cache, bytecode, and counterless
gates.

Run at least twelve balanced native i686 phoneME Rubido pairs. Accept only a
timer-resolved median of at least +0.5% with at least 9/12 wins and exact
deterministic signatures. If that primary gate passes, run Game of Life,
Waternet, and Untangle controls with a -0.5% floor. Otherwise remove the
candidate and restore NJIT-035 exactly.

The implementation and its focused edge fixture passed before timing. The
candidate source, fixture, and host interpreter class SHA-256 values were
`ddaf502af25a67c4d8a9090353cf4eb0cd7a34506b961891f062cf28dd35a4bd`,
`e672d93f6677b09029d457ef903aa5961ef47f10f4e557a2210e0db312ab09d4`,
and
`0158dc5acbcdc13ecaa864af8d7935a4c785ccb78f55aa2ddd2c9051080dd939`.
The focused fixture covered empty, last, and full stack slots, a negative
result, and signed 32-bit wraparound. Full `just verify` passed all seven
full-state routes, Java 1.3, CLDC, target-47, preverification, release, cache,
bytecode, and counterless gates. The counterless exactness artifact was
`abacfde5e87f9da9a2b99397f444da9d3545f45b54e60966633fd0f0bebca73e`
and its receipt SHA-256 was
`e6ab813c7892788dd71608602513457cf4c251a4fca93173c66a496b6397cea9`.
`executeCompactBlock` grew from 3,101 to 3,157 bytecodes while
`executeCompactFused` shrank from 1,376 to 1,362. The release/base JARs were
275,456/272,934 bytes with SHA-256
`bd88d4716fc1a25f6c30c30988d6eff7fe46a6d056800682e6fad7cdfc13ccde`
and
`cbbaa54566b19da1f8a636f01c828169891db069d0ea880e6cabf2f5aabf3e0d`.

The timed artifact was
`9cc325a050eead1b1cf1959e44bef20af1e11993403edfe75554fa9dbc3a60f2`;
its 85,020-byte counterless interpreter SHA-256 was
`95b601cf90b6ef6cf0ec6b399a98c2c60d486dda8fc9004262f711ff863949ca`.
Twelve balanced native i686 phoneME Rubido pairs passed the primary gate at
+0.527% median with 9 wins and 3 losses. The required Game of Life control
then measured -0.992% with only 2 wins and 10 losses, failing the predefined
-0.5% no-regression floor. Waternet and Untangle controls were therefore not
eligible and cannot change the verdict.

Raw evidence is under
`/tmp/w4me-interpreter-research/raw/njit024/runs/`
`njit039-direct-compact-local-i32-const-add-vs-njit035-20260727/`.
For Rubido, `pairs.csv`, `paired-stats.txt`, and `receipt.txt` have SHA-256
`0a2c18fa35699f0991bdab3ffca033cf5ddcc4fd3b9d5408cc25c10bcf5eb34f`,
`cacd03780a988f750d2fbf7a5b6356ee5e884a1c8320159b8aa10c0bacb43e00`,
and
`3d51d3469ff78eb5a287f17763e0a86ff665ad5987cb56f5d2ae7db6c71e6810`.
For Game of Life, the corresponding SHA-256 values are
`489ba4a44674dfeff90b820319193a830f5d25e5caf83a1fbc6577a65f670bfe`,
`b5bb7a5eae78c961b53f6a548f9a3129264fed06286165b4ebf878626489778c`,
and
`701bd2aaa35d4c46883b7e046450f5493e1e8c6e0f91e297f2f71beceef494a5`.
The candidate and temporary fixture were removed, restoring NJIT-035
exactly. This result is direct evidence that a handler-local call reduction
can improve one workload while a small compact-switch layout change regresses
another; future compact handlers require corpus controls even after a strong
primary result.

### NJIT-040: direct generic `i32.add`

**Status:** `rejected`.

This candidate is materially different from NJIT-039. NJIT-039 enlarged and
changed the layout of `executeCompactBlock`, improved Rubido, and regressed
Game of Life.
NJIT-040 leaves compact execution unchanged and changes only generic opcode
`0x6a` in the outer `execute` switch. It also follows the accepted NJIT-025
generic-comparison mechanism and the retained direct generic `i32.sub`,
`i32.or`, and `i32.shl` handlers rather than moving another fused opcode
between compact methods.

The current handler executes
`pushI32(popI32Second() + popI32First())`. On target-47 bytecode this crosses
the Java method boundary through both wrapper methods, two `popI32()` calls,
two `pop()` calls, `pushI32()`, and `push()`: eight invokes for one arithmetic
instruction. The selected replacement checks `valueTop < 2`, removes the
right operand with `--valueTop`, and stores the wrapping 32-bit sum into
`values[valueTop - 1]`. It performs no Java call on the valid path and cannot
overflow the value stack because two inputs become one output.

The exact format-16 corpus report at SHA-256
`6d2362c6272120eb0b28a4bdcd58f0e884ca2bfa2b13f262cc7748e4a200a945`
records 484,484 dynamic `i32.add` instructions in the one-frame Game of Life
route, 13,989 on Rubido, 9,213 on Waternet, and 10,641 on Untangle. The report
also records 1,305,284 on generic Plasma, but the native route harness cannot
use that as a production-shaped primary because the shipped Plasma cartridge
uses its retained runtime fast path. Stream counts are upper bounds across
tiers; native timing decides the actual generic-path benefit.

The retained baseline is accepted NJIT-035 at source SHA-256
`3b6b4c8b8e1d65689d7c7837777cd89172b390044def0149d301da22027b3d8f`,
counterless interpreter SHA-256
`e65235aaf3f0430d639982dbf21851946bc48f1500c9ab2ab98d8bbd979e635a`,
and exactness artifact
`9c8ab1a6fa89b19968859540de0fed5456a2f77efbf3b7fcbb53bcd04dfd8d6a`.
The native i686 phoneME VM, CLDC classes, and preverify SHA-256 values are
`bb4866969747430bb619d139c75ab31982e8967aa0e2dcc3e278fcc7920839a2`,
`117820661071b411b3937e8c708a9581aa48ba06fd06e24eadb5ebbf2036afbe`,
and
`4fe0d1b160ac7f18c4f7489917a1d868ff82df50842ca718729b098b25f2f50c`.

Before timing, pass focused underflow, ordinary, signed, and wrapping-add
coverage plus the seven-workload full-state, Java 1.3, CLDC, target-47,
preverification, release, cache, bytecode, heap, and counterless gates.
Inspect `execute` rather than treating the raised 16,000-byte sanity ceiling
as a size optimization target. Do not change any other arithmetic handler,
compact/fused execution, formats, tier selection, accounting, arrays, fields,
allocations, or cartridge behavior.

Run at least twelve balanced native i686 phoneME Game of Life pairs against
retained NJIT-035. Accept only a timer-resolved median of at least +0.5% with
at least 9/12 wins and exact deterministic signatures. If the primary passes,
run Rubido, Waternet, and Untangle controls with a -0.5% no-regression floor.
If it fails, remove the candidate and focused fixture and restore NJIT-035
exactly.

The isolated candidate used `WasmInterpreter.java` SHA-256
`7460ba5fdcc8c12c0849e2f7ffcfe9bfdf6323f5e2afeb030f27cba287bcd5c3`.
Its focused generic cached-W4IR fixture SHA-256 was
`ec7ce48292a5995af1a6913c39e812f690a6056b6b793519517a0369a910a61b`;
it covered ordinary addition, both signed wraparound directions, and the
canonical underflow trap. The target-47 host interpreter class was 54,071
bytes at SHA-256
`d145b14c631154fcd1030b13f3464a70fc6e225ff93f1d8ee9e45bedcd3b6a2a`.
The valid `i32.add` bytecode path contained no `invoke*`. Release and
counterless `execute` grew from 7,937/7,905 to 7,988/7,956 bytes.

Full `just verify` passed all focused, seven-workload full-state, Java 1.3,
CLDC, target-47, preverification, release, cache, bytecode, and counterless
gates. The station/base JARs were 275,451/272,929 bytes at SHA-256
`0890d1780bf16cb25cf868ee88cbcd2c2527a9ba0c9e4cb6e2f98df8beb64189`
and
`7f3fe87cc98c8867e902bf6f53a4695074feca6c4ed2e8157b7819964d36b312`.
The counterless exactness artifact was
`e006baebb2e93c0a5ffaa50fa774018c7e4c64f7e4c5ff8bffd8aaf84f7fd918`;
its receipt SHA-256 was
`f089c4be62fcb82ed00bed3545949ac5cf85f57c106fef56e51177ae812e1c12`.
The timed counterless artifact was
`95b2bd0fb07f88d89efa8d9acf71ea6db37fe0e5bdb18ebc203518339e06c412`;
its 85,070-byte preverified interpreter SHA-256 was
`26cfb69bc367535ac1626c04608e8caa2c407722ad3a807611ea7b875544cec7`.
No W4IR/RMS format, retained field, array, or heap allocation changed.

Twelve balanced native i686 phoneME Game of Life pairs against retained
NJIT-035 measured `+10,000 us/frame`, `+0.317%`, with 7 wins, 4 losses, and
1 tie. Deterministic route signatures were identical, and the result was
timer-resolved, but it missed both the predefined +0.5% effect floor and 9/12
win floor. Rubido, Waternet, and Untangle controls were therefore not
eligible. Raw evidence is under
`/tmp/w4me-interpreter-research/raw/njit024/runs/`
`njit040-direct-generic-i32-add-vs-njit035-20260727/game-of-life-zig-edition/`.
The `pairs.csv`, `paired-stats.txt`, and `receipt.txt` SHA-256 values are
`e9cc4487bb197286618f9e7bde14cf86ed3220d8f9a6376aae617645fceed6dc`,
`3e208933d49a760393576061085feaea95087cb367a2ca4e7e32a2d7d5246f68`,
and
`fd2d7db6293ed522f053839b13fd1e21321687665a21c613df25ea3cec46667d`.

**Verdict:** reject. Removing eight Java calls from this handler produces a
real but sub-threshold effect on the only heavy production-shaped route. The
candidate and focused fixture were removed, restoring accepted NJIT-035
source exactly. Reconsider only as part of a materially broader arithmetic
layout whose combined candidate is measured across the corpus; do not rerun
this single-handler form.

### NJIT-041: frame-neutral terminal `br_if` compact regions

**Status:** `rejected`.

The earlier branch-capable compact prototype is semantically complete but was
never timed on native i686 phoneME. It admitted one terminal ordinary
descriptor-backed `br_if` into a compact region and removed 1.356% of Rubido
outer dispatches, 0.456% on Game of Life, and 6.032% on generic Plasma. It
passed focused arity-zero/one, taken/fallthrough, nested-control, trap, budget,
and full-corpus exactness. Its unresolved target cost was structural:
`executeCompactBlock` grew by 237 bytecodes, nine local slots, and three
arguments for every compact call, including regions without a branch.

NJIT-041 retains the same universal eligibility rules while removing that
global compact-frame cost. `buildCompactBlockEnds` may admit exactly one
terminal `br_if` only when the accepted pc-indexed direct metadata exists, the
target is not a function return, and arity is zero or one. The existing end
array encodes such a region with a negative end marker; no field, array,
W4IR/RMS format, or persistent allocation is added. The outer executor decodes
the marker, invokes the unchanged `executeCompactBlock` only for the prefix,
then accounts and executes the terminal branch directly from its already
cached branch arrays. Ordinary compact regions retain their current positive
marker and method signature.

The candidate must preserve the exact pre-side-effect instruction-budget check
for the terminal logical instruction, the existing one-dispatch accounting for
the whole compact region, canonical underflow and descriptor failures, arity
zero/one value transfer, control depth, taken target, and fallthrough `pc`.
It must not add compare-specific fusion, enable compact on cold invocations,
change tier selection, fix the separate fused compact accounting defect,
special-case a cartridge, or modify the dynamic control stack.

The retained baseline is accepted NJIT-035 at source SHA-256
`3b6b4c8b8e1d65689d7c7837777cd89172b390044def0149d301da22027b3d8f`,
counterless interpreter SHA-256
`e65235aaf3f0430d639982dbf21851946bc48f1500c9ab2ab98d8bbd979e635a`,
and exactness artifact
`9c8ab1a6fa89b19968859540de0fed5456a2f77efbf3b7fcbb53bcd04dfd8d6a`.
The native i686 phoneME VM, CLDC classes, and preverify SHA-256 values are
`bb4866969747430bb619d139c75ab31982e8967aa0e2dcc3e278fcc7920839a2`,
`117820661071b411b3937e8c708a9581aa48ba06fd06e24eadb5ebbf2036afbe`,
and
`4fe0d1b160ac7f18c4f7489917a1d868ff82df50842ca718729b098b25f2f50c`.

Before timing, pass focused taken/fallthrough, arity-zero/one, nested-control,
unreachable-skip, underflow, and nearby budget-boundary differentials plus the
seven-workload full-state, Java 1.3, CLDC, target-47, preverification, release,
cache, bytecode, heap, and counterless gates. Record the exact `execute` and
`executeCompactBlock` bytecode sizes and local counts to prove that the latter
frame is unchanged.

Run at least twelve balanced native i686 phoneME Rubido pairs against retained
NJIT-035. Accept only a timer-resolved median of at least +0.5% with at least
9/12 wins and exact deterministic signatures. If the primary passes, run Game
of Life, Waternet, and Untangle controls with a -0.5% no-regression floor.
Generic Plasma may be reported only as a dispatch/correctness diagnostic:
the shipped native route uses its retained runtime fast path and is not a
production-shaped performance judge for this candidate.

The isolated candidate used `WasmInterpreter.java` SHA-256
`e2f741dbf5c1044766d28903bfa2d629f5563245defcea12a1aa0aeae674fc25`.
It encoded terminal branch regions as negative entries in the existing
`compactBlockEnds` arrays, ran the unchanged compact executor up to the
terminal branch, and executed the accounted branch in the outer frame. No
field, array, W4IR/RMS format, persistent allocation, or cartridge-dependent
path changed.

Full `just test` and `just verify` passed all seven-workload full-state,
instruction-budget, Java 1.3, CLDC, target-47, preverification, release,
cache, bytecode, heap, and counterless gates. The station/base JARs were
275,975/273,453 bytes at SHA-256
`edc4afb3274bf870621a7f11daeff5de5a71c289f7a6260f93e9d5c207f6207c`
and
`aa9457dcd30c25506eb46dc2c9ba1dcc98c96e353024df54d5ec34df28b2f9f9`.
The counterless exactness artifact was
`5c62af754f969474c3200f9a951d12e25966a7cf9511d92abc8cc1a72703ac73`;
its receipt SHA-256 was
`6f97b3c1832f63b5d0ba2b40601693dac9d22e7f2e41d8e91492b0481276d7fa`.
The timed artifact was
`2da4f0d0da26f387c129c0829013c189791702b0d31c461c964e0b50a1740cdf`;
its 86,590-byte preverified interpreter SHA-256 was
`d39a2ddba2fae871e182f5b625b0d22608f4f40854d3ef7c8adcf2960d6f1fa3`.

The intended frame-neutral property was achieved for the compact executor:
release/counterless `executeCompactBlock` remained exactly 3,101/3,070
bytecodes, and its release frame remained stack 7, locals 44, args 5.
However, release/counterless `execute` grew from 7,937/7,905 to 8,225/8,185
bytecodes. Twelve balanced native i686 phoneME Rubido pairs against retained
NJIT-035 measured `-2,906.5 us/frame`, `-3.334%`, with zero wins and twelve
losses. Deterministic route signatures were identical and the effect was
timer-resolved. Controls were ineligible after this decisive primary failure.

Raw evidence is under
`/tmp/w4me-interpreter-research/raw/njit024/runs/`
`njit041-frame-neutral-branch-compact-vs-njit035-20260727/rubido/`.
The `pairs.csv`, `paired-stats.txt`, and `receipt.txt` SHA-256 values are
`1914675a1994f05a780637bdb872f2161042a67a5e74372f9e83d86ae6e641ee`,
`5a27a41431255cd3fb672749e82bdd47c54444d3dd2a9f2edd5ef0a3054d578e`,
and
`e4035ec4e14fe9b1ccfab68a83eeb9c2081f2dabeb7ebede44da4c371bd50267`.

**Verdict:** reject. Removing one outer dispatch for the covered terminal
branches does not repay the enlarged and relaid-out outer executor on the
target C interpreter. The candidate was removed and NJIT-035 source restored
exactly. Reconsider branch-capable compact execution only with a materially
different representation that does not add a branch path to either universal
hot Java frame; do not rerun this negative-marker outer-frame form.

### NJIT-042: direct generic i32 ALU batch

**Status:** `rejected`.

NJIT-040 removed all valid-path Java calls from generic `i32.add` and measured
a timer-resolved `+0.317%` on Game of Life, but missed the predefined `+0.5%`
and 9/12-win gates. Its verdict explicitly permits reconsideration only as a
materially broader arithmetic layout. NJIT-042 is that broader candidate: it
changes the six helper-backed, dynamically covered generic operations
`i32.add`, `i32.mul`, `i32.and`, `i32.xor`, `i32.shr_s`, and `i32.shr_u`
together. The already direct `i32.sub`, `i32.or`, and `i32.shl` handlers remain
unchanged. Rotates remain unchanged because the exact corpus has only ten
`i32.rotl` executions on Untangle and no material `i32.rotr` coverage.

The exact format-16 corpus report at SHA-256
`6d2362c6272120eb0b28a4bdcd58f0e884ca2bfa2b13f262cc7748e4a200a945`
records 1,019,971 selected ALU instructions in the one-frame Game of Life
route: 484,484 add, 305,284 multiply, 76,801 and, 76,801 xor, one signed
shift, and 76,800 unsigned shifts. The same selected set totals 15,572 on
Rubido, 13,193 on the primary Waternet route, 17,812 on Untangle, and
6,539,209 on generic Plasma; the latter remains diagnostic only because the
shipped native Plasma route uses its retained runtime fast path. Profiling
disables compact execution, so these counts establish decoded dynamic
coverage rather than optimized-tier residency. Native paired timing remains
the performance judge.

The retained baseline is accepted NJIT-035 at source SHA-256
`3b6b4c8b8e1d65689d7c7837777cd89172b390044def0149d301da22027b3d8f`,
counterless interpreter SHA-256
`e65235aaf3f0430d639982dbf21851946bc48f1500c9ab2ab98d8bbd979e635a`,
and exactness artifact
`9c8ab1a6fa89b19968859540de0fed5456a2f77efbf3b7fcbb53bcd04dfd8d6a`.
The native i686 phoneME VM, CLDC classes, and preverify SHA-256 values are
`bb4866969747430bb619d139c75ab31982e8967aa0e2dcc3e278fcc7920839a2`,
`117820661071b411b3937e8c708a9581aa48ba06fd06e24eadb5ebbf2036afbe`,
and
`4fe0d1b160ac7f18c4f7489917a1d868ff82df50842ca718729b098b25f2f50c`.

Each selected handler must first check `valueTop < 2`, remove the right
operand with `--valueTop`, and overwrite `values[valueTop - 1]` with the
operation result. Shifts must retain WebAssembly's low-five-bit count mask.
This preserves canonical underflow order, wrapping Java `int` arithmetic,
and the two-input/one-output stack effect while removing all `push`,
`pushI32`, `pop`, `popI32`, and operand-wrapper calls on valid paths. Do not
change compact/fused execution, tier selection, accounting, formats, arrays,
fields, allocations, cartridge behavior, or the three already-direct ALU
handlers.

Before timing, pass a focused fixture covering every selected operation,
signed and unsigned boundary values, zero/31/32/63 shift counts, wraparound,
and canonical underflow, plus the seven-workload full-state, Java 1.3, CLDC,
target-47, preverification, release, cache, bytecode, heap, and counterless
gates. Inspect the exact `execute` bytecode delta and valid paths; the raised
16,000-byte verifier ceiling is a corruption guard rather than an optimization
budget.

Run at least twelve balanced native i686 phoneME Game of Life pairs against
retained NJIT-035. Accept only a timer-resolved median of at least +0.5% with
at least 9/12 wins and exact deterministic signatures. If the primary passes,
run Rubido, Waternet, and Untangle controls with a -0.5% no-regression floor.
If it fails, record the result, remove the six-handler batch and focused
fixture, and restore NJIT-035 exactly.

The isolated candidate used `WasmInterpreter.java` SHA-256
`03dde13f77ef1c67936b0d6142bb049fd297cac467127d1b8e2fefbc7db992b8`.
Its focused Java/WAT fixture SHA-256 values were
`4e91940a692ad13fb1966ff6bd3d499490cd4da460f4c10fd863bd17981f0600`
and
`a13998db5a1d20d242b88f21d0539ad63755fc1599f39c1e8a0e4b89a7d3e332`.
The fixture forced unfused generic execution, checked eight arithmetic and
shift-boundary results, and created canonical underflow states for all six
handlers by replacing only operand-producing W4IR instructions with nops.

Full `just test` and `just verify` passed the focused fixture, seven-workload
full-state, Java 1.3, CLDC, target-47, preverification, release, cache,
bytecode, heap, and counterless gates. Release/counterless `execute` grew from
7,937/7,905 to 8,235/8,203 bytecodes. The station/base JARs were
275,513/272,991 bytes at SHA-256
`1412dd9f6966d9b0f6c8b4c8a98d6b4ceb682969545e8fd4e7842122a3944a5e`
and
`19254ee23ec3b8c9bb1260b93aff97925c8dbbdfa615c3ffe991a9eb998dde50`.
The counterless exactness artifact was
`942f39d138095f0e7a4bacd14e7264a2cc10047bdc5c610cee9d4df0d1347938`;
its receipt SHA-256 was
`c36249517accebfeb009980d35f8c8e86e521dbd4c953785a931c84b52360205`.
The timed artifact was
`0623a78ed6b02fac6c69c2bedce694a1b999c75314d4f7efa0d2ec0ec7cb7b6b`;
its 85,659-byte preverified interpreter SHA-256 was
`c964c6ffac439388f37f1153b55f71d926a86ab621fe90b6d17d9c65a0e31fe3`.
No W4IR/RMS format, retained field, array, or persistent allocation changed.

Twelve balanced native i686 phoneME Game of Life pairs against retained
NJIT-035 measured `+17,000 us/frame`, `+0.552%`, with 8 wins and 4 losses.
Deterministic route signatures were identical and the effect was
timer-resolved. The candidate cleared the +0.5% effect floor but missed the
predefined 9/12 win floor, so Rubido, Waternet, and Untangle controls were
ineligible. Raw evidence is under
`/tmp/w4me-interpreter-research/raw/njit024/runs/`
`njit042-direct-generic-i32-alu-vs-njit035-20260727/`
`game-of-life-zig-edition/`. The `pairs.csv`, `paired-stats.txt`, and
`receipt.txt` SHA-256 values are
`d8d96a2bed13210c3a63e73e0d82ba92a5c8f18f7656a9133d775d9cf7c7928a`,
`cd96f37bd160b4b397dbad355046acddd0896d0e16c0d49de4450e0be5be21c1`,
and
`067f667f3b1360a486571298352ada5a38d92f45c3d104b9fea968705d63e6bc`.

**Verdict:** reject. Broadening NJIT-040 raised its median above the effect
floor but did not make the result repeatable enough for the fixed acceptance
rule, while adding 298 bytecodes to the universal outer executor. The batch
and focused fixture were removed, restoring NJIT-035 exactly. Reconsider these
operations only through a materially different executor or stack
representation that removes more than helper calls without enlarging each
case independently.

### NJIT-043: ship the accepted counterless production configuration

**Status:** `accepted`.

The generic-tiering change already accepted a separately compiled counterless
configuration as the production timing artifact. Its clean historical native
i686 phoneME result was +0.998% on Waternet, +3.929% on Rubido, and +0.884% on
Untangle, with exact checkpoints. The current release pipeline does not
actually select that configuration: `tools/build.sh` compiles every
`src/main/java` source, including the regular diagnostic
`InterpreterBuildConfig` where `DIAGNOSTIC_COUNTERS=true`. Therefore both
public JAR variants still update `dispatchesExecuted`, `compactBlockCalls`, and
`compactInstructionsExecuted` in their production hot paths. `just release`
only verifies a separate counterless differential artifact and never proves
that the distributable JARs themselves are counterless.

Revalidate this already accepted mechanism on retained NJIT-035 before changing
packaging. Build diagnostic and counterless artifacts from the identical
current source, differing only in the package-private compile-time
`InterpreterBuildConfig`. Run twelve balanced native i686 phoneME pairs on
Rubido as the primary route, with Waternet and Untangle controls. Accept only
with exact deterministic route signatures, a timer-resolved Rubido median of
at least +1.0% and at least 9/12 wins, and no control below -0.5%.

If the current result passes, make `tools/build.sh` replace only the regular
diagnostic config source with the existing timed config while leaving the host
test suite diagnostic. Extend release-JAR verification to reject writes to the
three optional diagnostic counters. Keep `instructionsExecuted` and its exact
budget trap unchanged. Run the complete Java 1.3, CLDC, target-47,
preverification, full-state, release, JAR-content, deterministic-build, and
counterless gates. Record the new JAR sizes, hashes, method shape, and exactness
artifact. If the timing gate fails, leave the release mapping unchanged and
record the current result as a rejected revalidation.

The current same-source revalidation passed every timing gate. Twelve balanced
native i686 phoneME pairs measured +4.316% on Rubido with 12/12 wins, +2.355%
on Waternet with 12/12 wins, and +2.177% on Untangle with 10/12 wins.
Deterministic route signatures and logical instruction counts matched in every
pair, and every median was timer-resolved. The runner labels these receipts
exploratory because the accepted NJIT-025 through NJIT-035 work remains
uncommitted in the shared tree; both sides were nevertheless built from that
same source and are bound to distinct artifact hashes. The Rubido diagnostic
and counterless artifact SHA-256 values were
`34bc1c8a9d329c7772b2ba2c7527c9de992119b7728afad7237b496d191737ef`
and
`750659edaf7f2adebedd65267cdc346daadd9477fcaad44ed2f096037b4219ab`.
The control artifact values were
`7f2dd6f66f6c31b39eff5adb48eeeca7d5433b14ae0445d9164421cf411f6f5a`
and
`ef3d29e603f928324d85bdf949e1cf5c94a5bc70c18fba866e42477abfd0d91f`.
This current revalidation confirms rather than replaces the retained clean
historical A/B for the same compile-time mechanism.

Raw evidence is under `/tmp/w4me-njit043-20260727/`. The Rubido receipt and
pairs SHA-256 values are
`64050b06f24e818c77293a0d85214da76ded1b7906250949976b97d3dbbf2789`
and
`8e0615b1913f1a705b72a8dfde5428e6e27e8d5ecf7f35f0fda583401f3ac7a6`.
The combined-control receipt SHA-256 is
`3f8f2db30109e8de785aa3755ec4e9e606edb4d0aeef6a166009aae2dd42e3ae`;
its Waternet and Untangle pair SHA-256 values are
`ac5730ad37e830853d475ee7d52c345bb93a04a42e00990263b646ee1b95a90c`
and
`ac0c1efa3b8d3c0c2d8b76dba8125038b68f21f526f04f0eaac5b98329449b78`.

`tools/build.sh` now substitutes only the existing timed
`InterpreterBuildConfig` when compiling distributable MIDlets. The regular
source config remains diagnostic for host tests. `tools/verify.sh` inspects
the full target-47 interpreter bytecode and rejects any release JAR containing
a write to `dispatchesExecuted`, `compactBlockCalls`, or
`compactInstructionsExecuted`. The complete `just verify` gate passes; both
public JARs report `execute=7905`, dense `tableswitch`, and zero optional
diagnostic-counter writes. The separate seven-workload counterless exactness
artifact remains
`9c8ab1a6fa89b19968859540de0fed5456a2f77efbf3b7fcbb53bcd04dfd8d6a`.

The full and base JARs are 275,375 and 272,853 bytes at SHA-256
`53105481fb96ad5c80b7440deb6c186e119e2188a55950b464916cf7e1ab6149`
and
`55cdbb29238fbd003d969ca962a1593d9f881170c750cb2d282eef6775b0b364`.
Their JAD SHA-256 values are
`9d6a5692e8920f1519c302983cb02db13256f40898f03fc4159def5fc7be5779`
and
`3d4feb10dff1002f75a12756381e18d26f8d85df56738f604fa4326b3dd18538`.
A second release build reproduced all four hashes exactly.

**Verdict:** accept. The public JARs now use the already accepted production
configuration instead of paying for test-only counters. No runtime flag,
instruction-budget change, format change, cartridge-specific behavior, heap
allocation, or user-facing behavior was added.

### NJIT-044: remove the cartridge-specific Plasma replacement

**Status:** `accepted`.

The production source still contains `PlasmaTriFast`, a Java implementation of
one function selected by exact cartridge length, cartridge fingerprint, and
function fingerprint. `W4Canvas` enables it by default, so the bundled Plasma
Cube demonstrates a hand-written per-ROM replacement rather than the
capability of the universal WebAssembly interpreter. This violates the current
product contract even though all authoritative generic phoneME benchmarks
already set fast paths off.

Remove the fingerprinted class and both selection paths from `callFunction`,
including the differential-only clone/compare machinery. Retain the generic
diagnostic API temporarily as an inert compatibility surface:
`setFastPathsEnabled` becomes a no-op and `fastPathCalls` always returns zero,
so existing generic probes need no unrelated interface rewrite. Remove the
test that proves the forbidden shortcut and add a release-JAR gate that rejects
`PlasmaTriFast.class`. Do not change W4IR, compact/trace selection, numeric
intrinsics, instruction accounting, cartridge data, heap representation, or
any other function dispatch.

Pass the complete seven-workload full-state, Java 1.3, CLDC, target-47,
preverification, release, cache, bytecode, and counterless gates. The generic
Plasma route must remain exact and report zero fast-path calls. Then compare a
hash-bound counterless candidate with retained NJIT-043/NJIT-035 on at least
twelve balanced native i686 phoneME Rubido pairs; accept the contract cleanup
only if the ordinary route stays above the -0.5% no-regression floor with exact
checkpoints. Plasma wall time is not a valid before/after speed comparison:
the removed baseline executes different hand-written Java code.

The implementation removed `PlasmaTriFast`, both production and differential
selection paths, and the shortcut-specific smoke. The generic compatibility
probes remain inert as planned. `tools/verify.sh` now rejects
`w4me/wasm/PlasmaTriFast.class` in either distributable JAR. The retained
`WasmInterpreter.java` source is SHA-256
`8545ca66f38983146857c6f7233fbcc72e8db4094ec554b03da0952e462423d8`.

`just verify` passed the complete host and release matrix. The generic Plasma
route executed 298,939,472 logical instructions over 60 frames with exact
memory and framebuffer state and `fast-paths=0`. The release interpreter
remains a 7,905-byte target-47 `tableswitch` method with zero diagnostic
counter writes. Counterless exactness passed all seven workloads with artifact
SHA-256
`b1926068f773e5b48335ea5a4b8822762b5642d6763b7be612f46c230301ba1e`;
the receipt is
`build/reports/verify/counterless/receipt.txt` at SHA-256
`df7a2f1c59129a3b3e2a548a2f8d5744e9031d496d05f0940a71ed8b22da07a4`.

The distributable full and base JARs are 270,007 and 267,485 bytes at SHA-256
`039a8f989b55eaa142c59721ee3ff905fcdf6f7f6af0c5f086657fd3b885081e`
and
`a7c9351b03ae3116439d5bc85d208751fcfd58c3cb35ef221247e9a73fffd038`.
Their JAD SHA-256 values are
`9d45053924d2fc6528cfcc9d4b3f57c1b537bcd123162a954b7afc21d60ad9d1`
and
`2bc1ca8818442df82cabfc6f94a81bf75347bf7068deff5eacd314bee06c0a65`.

The native counterless candidate reported artifact SHA-256
`255e66180bba149df460e470487b659043673a589a94ec3a6670263b211c55d0`;
its preverified `WasmInterpreter.class` is 83,035 bytes at SHA-256
`9240fe8e4b9d4fe42e5dd7943bd06fa6006d48e92a6e355bec9a35f3a5abb31b`.
Against the retained NJIT-035/NJIT-043 interpreter
`e65235aaf3f0430d639982dbf21851946bc48f1500c9ab2ab98d8bbd979e635a`,
twelve balanced native i686 phoneME Rubido pairs measured a median
+97.5 us/frame, **+0.112%**, with 8/12 wins. All checkpoint and deterministic
signatures matched, the timer resolved the effect, and the -0.5% no-regression
floor passed.

Raw evidence lives under
`/tmp/w4me-interpreter-research/raw/njit024/runs/njit044-contract-clean-vs-njit043-20260727/rubido/`.
The receipt, raw pairs, and paired statistics have SHA-256
`e70bb2e96aaa45529c6a1e9e3c737ac52c0b642ebaabc2311fda9625a4514440`,
`5df8b8cdff4977148c1aa37c48a5b6b3286c7825fbd4d4c0145fe589eb57d0c3`,
and
`9dcf5f5630cd7f6699059be5e694899e1aa76a42bdc4ee3badc4c57ea54603a3`.

**Verdict:** accept as the contract-clean universal baseline. The measured
Rubido effect is no-regression evidence, not a claimed speed optimization.
Plasma is intentionally slower than the forbidden hand-written replacement,
so only generic exactness, not its before/after wall time, is comparable.

### NJIT-045: sparse exact instruction-budget recovery

**Status:** `rejected`.

The current fused W4IR accounts the full logical span only after executing a
fused handler. If the instruction limit falls inside that span, the handler
can mutate stack, locals, memory, or control state for logical instructions
that should never have started. The isolated full-copy prototype proved both
the defect and the correct semantics, including resident, paged, and RMS-hit
paths, but was rejected because retaining a second complete W4IR stream cost
99--100% of the primary W4IR payload.

Prototype a sparse recovery stream without retaining the full unfused code.
During fusion, consumed interior slots keep their original three W4IR words
and a build-only consumed bitmap hides them from later fusion passes. After
fusion, each pc whose root word differs from the decoded original instruction
gets one sorted four-int recipe:

```text
pc, original instruction word, original operand, original auxiliary
```

This also covers intermediate fusion roots later consumed by larger fusions.
At runtime, the optimized stream remains unchanged until the global budget is
within the maximum 512-logical-instruction compact region. The executor then
disables compact batching for that invocation tail and reads the original
word from the sparse recipe when the current pc is a rewritten root; unchanged
and consumed interior slots are already the original stream. The rare boundary
path may use binary search per recovered instruction. Ordinary steady-state
execution must not perform recipe searches or allocate recovery objects.

Persist the recipes in function metadata and bump the W4IR format so old RMS
records are atomically rejected and rebuilt. Validate sorted unique pcs,
four-int stride, pc bounds, and standard original opcodes on cache build and
load. Measure retained primitive payload per corpus module, RMS record growth,
decode peak, method size, class/JAR size, and the steady-state native cost of
the boundary guard. JAR size is informational only; phone heap, RMS, exactness,
and native no-regression remain acceptance constraints.

The generated exactness matrix must cover every emitted `span>1` W4IR opcode
at every boundary from zero through `span+1`, observable stack/local/memory and
control side effects, resident code, in-memory paged cache, RMS build/hit,
promotion, nested calls, batching loops, and counted traces. The seven-workload
full-state and all Java 1.3/CLDC/target-47/preverify/release gates remain
mandatory.

For performance acceptance, compare hash-bound counterless artifacts on native
i686 phoneME with at least twelve balanced pairs on Game of Life and Rubido,
plus Plasma as the trace/compact control. Exact checkpoints and deterministic
counters must match. Accept only if every route stays above the -0.5%
no-regression floor; any speedup is secondary to restoring exact budget
semantics.

The prototype implemented the four-int recipe stream, build-only consumed-slot
bitmap, RMS persistence and validation, W4IR format 17, near-budget fallback,
batch-loop yielding, and counted-trace yielding. A focused resident,
in-memory-cache, and RMS-hit fixture proved exact stack, local, and memory
side effects at representative fused boundaries. The full Java 1.3, CLDC,
target-47, preverification, release, counterless, and seven-workload
full-state gates passed. The counterless artifact was
`5fd6502d5036dee334022edcb2be1cd8ea0c8001650ccd568d12037d972329ba`;
`execute()` was 8041 bytes under the 16000-byte corruption guard.

The retained primitive payload was material rather than negligible:

| Workload     | Recovery bytes | Primary W4IR bytes | Recovery / W4IR | RMS growth |
| ------------ | -------------: | -----------------: | --------------: | ---------: |
| Waternet     |          16672 |             106680 |          15.63% |      16888 |
| Rubido       |          16976 |              92028 |          18.45% |      17100 |
| Untangle     |          34544 |             181476 |          19.04% |      34952 |
| Game of Life |           2352 |              15528 |          15.15% |       2404 |
| Plasma Cube  |           6640 |              27096 |          24.51% |       6680 |

The first mandatory native route rejected the candidate before the larger
matrix was justified. Twelve balanced native i686 phoneME pairs on Game of
Life measured median `-40500 us/frame`, **-1.312%**, with 3 wins and 9 losses.
All 12 pairs matched the 12802761 logical instructions, checkpoint, direct
branch, and counterless signatures exactly. The receipt, pair CSV, and paired
statistics have SHA-256
`bbc7c33da8c3909f11407d8ded3b7258ec0e711254282f37fb234c6630794f98`,
`c8b1f27b64e36aefc56447ebaac8f599109688eef41fee0bcc625040cc7b35b2`,
and
`828290816e9124d6cd18c5be4c9e269bf7b9784ea50ffb6ba4d69c2cede79764`.
The preverified candidate interpreter class has SHA-256
`aa330c2de41e02778e14998f25af357b46a3ae23a7734d938bde32cbba457225`.
Raw evidence is under
`/tmp/w4me-interpreter-research/raw/njit024/runs/njit045-sparse-budget-vs-njit044-20260727/`.

**Verdict:** reject. The measured -1.312% fails the mandatory -0.5%
no-regression floor, so Rubido and Plasma could not change the decision and
were not completed. The generated all-fusion boundary matrix was therefore
also not built. All NJIT-045 production, cache, and focused-test code was
removed; the stable W4IR format remains 16. Sparse recovery must not be
revisited without a design that removes the steady-state per-dispatch guard
and materially reduces the 15--25% retained W4IR payload.

### NJIT-046: counterless build without opcode profiling support

**Status:** `accepted`.

The counterless production artifact already removes diagnostic dispatch and
compact counters, but it still retains the dynamic opcode profiler. Profiling
is disabled at runtime, so the generic executor nevertheless executes a
quickened `getfield profilingEnabled` and conditional branch for every outer
dispatch. Defined-function calls and a small number of helper paths repeat the
same false runtime check. The production JAR never enables this test-only
profiler; corpus profiling is built with the regular diagnostic config.

This candidate adds one compile-time `PROFILING_SUPPORT` constant to every
interpreter config. It remains `true` in the regular diagnostic build and in
rollback configs, and becomes `false` only in the existing timed/counterless
production config. Every profiler-dependent branch is guarded by that
constant, so `javac` must remove the runtime field read and branch from the
counterless bytecode. The diagnostic artifact must retain the existing
profiler behavior and corpus reports exactly. W4IR, RMS, heap layout, opcode
selection, exact instruction budget, and all runtime semantics remain
unchanged.

The hypothesis comes from two read-only diagnostic phoneME builds made from
the byte-identical source family used by the native judge. A `-pg` release VM
with SHA-256
`00d4d921da28a54d7ae9c506ee137723f864d1870e8b4450de044af784b0641a`
completed the exact 129-frame Rubido route. Its flat profile identified
Java-bytecode interpretation, rather than GC or native runtime work, as the
steady-state cost center: `iload` 11.00%, `istore` 7.50%, quickened
`igetfield` 6.88%, and `aload_0` 6.00%. Absolute wall time is invalid because
`-pg` instruments every C/C++ call; only attribution and handler call counts
are diagnostic evidence.

A separate method/BCI sampler VM with SHA-256
`d7cb28fe7eebadbbb42f5825b5b806b262752a8f09246d3054fcbcb815e96cf1`
completed the same exact route and produced 4077 periodic bytecode samples.
The dominant methods were `execute` 1838, `executeCompactBlock` 1472,
`loadI32` 149, `push` 117, `pop` 109, `blitSub` 100,
`checkedAddress` 99, `executeCompactFused` 60, and `transfer` 57. Within
`execute`, 1107 samples landed in the outer-dispatch preamble, 693 in opcode
handlers, 35 on the dense `tableswitch`, and 3 in one-time setup. The
runtime-false profiler check at bytecodes 297--301 accounted for 49 samples,
about 1.2% of all route samples before counting call-site checks. Sampling
changes layout and adds one diagnostic call per Java bytecode, so these ratios
select the candidate but do not prove a speedup.

The retained baseline is source commit
`8e850656f2b19256c2559cdd07f165c7788b16d4`, counterless artifact
`b1926068f773e5b48335ea5a4b8822762b5642d6763b7be612f46c230301ba1e`,
and preverified `WasmInterpreter.class`
`9240fe8e4b9d4fe42e5dd7943bd06fa6006d48e92a6e355bec9a35f3a5abb31b`.
Raw diagnostic profiles are under
`build/reports/phoneme-gprof/` and `build/reports/phoneme-trace/`; they are
gitignored local research artifacts, not release inputs.

Implementation is limited to `WasmInterpreter` and the interpreter config
classes. Verify with `javap` that the counterless `execute` no longer reads
`profilingEnabled` or calls `profileInstruction`, while the diagnostic class
still does both. Run the exact corpus profiler under the diagnostic config,
the complete Java 1.3, CLDC, target-47, preverification, release, full-state,
cache, bytecode, and counterless gates, and compare class/JAR/heap footprints.

For native acceptance, compare hash-bound counterless artifacts on native
i686 phoneME with at least twelve balanced pairs on Rubido. If the primary
route passes, run Waternet and Untangle no-regression controls and Game of
Life as the generic integer-heavy control. Exact checkpoints, deterministic
logical instruction counts, direct-branch metadata, and artifact signatures
must match. Accept only a repeatable median improvement of at least 0.8% on
Rubido with every control above the -0.5% no-regression floor. Otherwise
remove the compile-time specialization and record the rejection.

The retained implementation meets those gates. The diagnostic config keeps
`PROFILING_SUPPORT=true`; the timed/counterless configs use `false`. The
counterless preverified `WasmInterpreter.class` falls from 83,035 bytes,
SHA-256
`9240fe8e4b9d4fe42e5dd7943bd06fa6006d48e92a6e355bec9a35f3a5abb31b`,
to 82,166 bytes, SHA-256
`17795670307a11bda48f1c9826e9a1b78962c76292ce8dfa9461ce0a1bb5804a`.
Its `execute` method falls from 7905 to 7865 target-47 bytecode bytes.
`javap` finds zero counterless reads of `profilingEnabled` and zero calls to
`profileInstruction`; the 83,149-byte diagnostic class retains all nine
profiler references and the corpus test still emits opcode, pair, function,
and fusion profiles. Heap, W4IR 16, RMS, branch metadata, and exact logical
instruction counts are unchanged. The phoneME candidate artifact SHA-256 is
`a9f6f9055e99aeafc4a7d22d7d8d4eca516cb20c3108d967eeb7c2d741bd3e99`.

Twelve balanced native i686 phoneME pairs against the retained NJIT-044
counterless tree measured:

- Rubido: +1.385% median, 1202.0 us/frame, 12 wins and 0 losses;
- Waternet: +1.362% median, 150.5 us/frame, 8 wins and 4 losses;
- Untangle: +1.363% median, 23.0 us/frame, 9 wins, 2 losses, and 1 tie;
- Game of Life: +2.789% median, 86,500 us/frame, 10 wins and 2 losses.

Every pair matched checkpoints, logical instructions, and direct-branch
payload exactly. Raw receipts live under
`/tmp/w4me-interpreter-research/raw/njit024/runs/`
`njit046-profileless-vs-njit044-20260727/`. Receipt SHA-256 values for
Rubido, Waternet, Untangle, and Game of Life are respectively
`bc6ecbf00170d4a40dd7dc2a96b879811446720a4c7924732e91a0ae3d1e28df`,
`86cd1c6b6a7d074f4ea992bfa1f68806ecd038f6cf8d1f81be43165a4366c178`,
`63ad87bb7ed696c96d7a92379d224213f9130f6849f56d58f9a7d7479b469ec6`,
and
`22be8c81e18cca221c39763b65d4942a4bdbe6861752cfbbcd362ad3ebc9b63d`.
The paired runner's `source-clean=yes` classification means the two immutable
preverified class trees were hash-bound; the main worktree remained dirty by
design and was not used as a mutable classpath during any pair.

The complete `just verify` matrix passes. Its counterless exactness artifact
is
`bda67da37618a3a1849464d915715e7dd59529ecb1bf97490047450b31f04156`;
the final receipt SHA-256 is
`ba563b0410bf9301337218298fffe46b478b0a8da25672fa8c9a02805718da88`.
`tools/verify.sh` now rejects any distributable or counterless artifact that
still reads `profilingEnabled` or invokes `profileInstruction`, in addition
to its existing diagnostic-counter checks.

### NJIT-047: compact executor value-array local

**Status:** `rejected`.

The post-NJIT-046 method/BCI profile assigns 1472 of 4077 periodic Rubido
samples to `executeCompactBlock`, second only to the outer executor. The
accepted NJIT-046 result also confirms that removing repeated Java field work
from an interpreted hot loop produces a repeatable cross-workload gain. In
the counterless target-47 class, the compact executor's direct handlers
contain 46 source-level references to the final `values` field. Each becomes
`aload_0; getfield values` before the actual `laload`, `lastore`, or length
check. The native phoneME bytecode-type profile attributes 6.88% of Rubido
ticks to quickened instance-field loads, while ordinary local loads are
materially cheaper.

This candidate adds one method-local alias, `long[] compactValues = values`,
at the entry to `executeCompactBlock` and uses it only in the direct handlers
already implemented inside that method. It does not cache `valueTop`, because
helper-backed and fused handlers observe and mutate that field. It does not
change helpers, opcode eligibility, compact topology, accounting, instruction
budget points, W4IR/RMS, heap allocation, or traps. `values` is a final array
field and is never rebound, so the alias preserves object and element
identity. Helper-backed handlers continue to use the field and therefore
remain coherent with writes made through the alias.

Static acceptance requires the target-47 compact executor to replace its
`getfield values` sites with one entry load without increasing the method's
invoke count or changing classfile major version, StackMap validity, or the
16,000-byte corruption guard. Run the complete Java 1.3, CLDC,
preverification, exact-state, cache, release, and counterless matrix.

Native acceptance compares the accepted NJIT-046 counterless tree with the
hash-bound candidate on native i686 phoneME. Game of Life is primary because
its single update executes 12,802,761 logical instructions and spends most of
the route in compact integer handlers. Require at least twelve balanced pairs
and a median improvement of at least 0.8%. If it passes, run Rubido, Waternet,
and Untangle controls with a -0.5% no-regression floor. Record raw pairs and
hashes; remove the alias completely if the primary gate fails.

The prototype passed the complete exactness and release matrix. Its
counterless exactness artifact was
`83d3a6dec677d0e1cc9287b0647b96790bb2c29ec73a086d94174d9adcd2ded5`.
In the preverified phoneME class, `executeCompactBlock` field reads of
`values` fell from 50 to 1, method bytecode fell from 3070 to 2976 bytes, and
the invoke count remained 77. The containing class nevertheless grew from
82,166 to 82,414 bytes because of local-variable and StackMap metadata; its
SHA-256 was
`674c9864a305bf1479fc8617601a47a10fe9c86cf2e1a73eab47ac86f805d9c1`.
The phoneME artifact SHA-256 was
`a543bde2bf78c6b481a746a7a778211bee7e838f8aca1d70040af05740bc38c8`.

Twelve balanced native i686 phoneME Game of Life pairs measured only +0.148%
median, 4500 us/frame, with 6 wins and 6 losses. This fails the predeclared
+0.8% primary gate, so no control workloads were required. The raw receipt,
pair CSV, and paired-stat SHA-256 values are respectively
`f89d6ec83567e00e03f07e76ec60bcd8d8c12cda40b1b222c11040a019f37e93`,
`d1dea96c279939ff5da4f732c81bf3184b3f346d8609c3e1e4e93033b6e7df8b`,
and
`bf0226d35f74a49c4e76f0b583e19955e6284817498384b6516721789a8a77cb`.
They live under
`/tmp/w4me-interpreter-research/raw/njit024/runs/`
`njit047-compact-values-vs-njit046-20260727/game-of-life-zig-edition/`.
The alias is removed completely. Do not revisit array-reference-only caching
without a design that also safely caches `valueTop` across helper boundaries;
the field-load reduction alone is not a measured phoneME win.

### NJIT-048: compact-block budget-check elision

**Status:** `rejected`.

A fresh method/BCI profile was captured after restoring accepted NJIT-046
exactly. The profile used the instrumented no-JIT phoneME VM only to select a
hot shape; its wall time is not acceptance evidence. The exact 129-frame
Rubido route passed all 30 checkpoints and executed 43,301,827 logical
instructions. Of 1,816 samples in `execute`, 1,091 landed in its per-dispatch
preamble and 699 in handlers. Of 1,461 samples in `executeCompactBlock`, 878
landed before the handler switch, 360 in handlers, and 223 in the loop tail.
The raw trace is
`build/reports/phoneme-trace-njit046/rubido/trace.txt`, SHA-256
`0fc60f80be45302bbd03a6e42699349bf94a291c29ddebfa5e9b23ede9eec2fe`.
The sampler VM SHA-256 is
`d7cb28fe7eebadbbb42f5825b5b806b262752a8f09246d3054fcbcb815e96cf1`;
its timing is invalid because it instruments every interpreted Java
bytecode.

The compact executor currently increments `instructionsExecuted` and compares
it with `instructionLimit` for every standard compact instruction and every
fused logical span. In ordinary frames the remaining budget is much larger
than a compact block, so every comparison is mathematically unable to trap.
NJIT-001 already showed that merely caching the counter while retaining a
per-instruction budget decision is not enough: it measured only +0.312% on
Rubido. This candidate instead proves once per compact-block call whether the
entire block fits in the remaining budget and elides only the redundant
per-instruction limit comparisons for such a block.

The outer executor computes the exact logical block length as
`compactEnd - pc`. Compact formation caps a region at 512 logical
instructions, and fused handlers advance `pc` by their logical span, so the
difference equals the total instruction accounting performed by the compact
loop. A block is declared within budget only when:

```text
instructionsExecuted <= instructionLimit - (compactEnd - pc)
```

The boolean is passed into `executeCompactBlock`. The existing counter
increments, diagnostic compact counters, handler order, helper calls, semantic
traps, and fused post-handler accounting remain at their current locations.
Only `instructionsExecuted > instructionLimit` is skipped while the
block-level proof is true. If the block can cross the limit, all existing
checks run unchanged. This is important for exact semantic-trap state: the
candidate does not pre-account the block and therefore cannot count
instructions after an earlier memory, stack, numeric, or helper trap.

The retained baseline is accepted NJIT-046 at source commit
`8e850656f2b19256c2559cdd07f165c7788b16d4`, current
`WasmInterpreter.java` SHA-256
`a62bb687c2803bcbf1763cc63e1b3cb0f030b46060fdaf3b42fde8c76e6879e5`,
and 82,166-byte counterless preverified interpreter SHA-256
`17795670307a11bda48f1c9826e9a1b78962c76292ce8dfa9461ce0a1bb5804a`.
The retained phoneME artifact SHA-256 is
`a9f6f9055e99aeafc4a7d22d7d8d4eca516cb20c3108d967eeb7c2d741bd3e99`;
the accepted seven-workload counterless exactness artifact is
`bda67da37618a3a1849464d915715e7dd59529ecb1bf97490047450b31f04156`.
There is no planned W4IR/RMS format, field, array, allocation, or retained heap
change.

Static and correctness acceptance require Java 1.3, CLDC, target-47,
preverification, release, cache, counterless, and seven-workload full-state
gates. The existing compact integer and `i32.load + local.tee` focused
differentials must sweep outer-versus-compact instruction budgets, including
both whole-block admission and the unchanged per-instruction fallback. Inspect
target-47 `javap` to confirm that the admitted path replaces repeated field
limit comparisons with a local boolean branch and record method/class growth.

Native acceptance compares hash-bound counterless artifacts on native i686
phoneME. Game of Life is primary because its compact blocks average more
logical instructions per entry and therefore best amortize the one new
block-level proof. Require at least twelve balanced pairs, a timer-resolved
median improvement of at least +0.8%, and at least 9/12 wins. If it passes,
run Rubido, Waternet, and Untangle controls with a -0.5% no-regression floor.
Record all raw receipts and hashes. If the primary gate fails, remove the
boolean path completely and restore NJIT-046 exactly.

The isolated implementation passed every static and correctness gate. Its
source SHA-256 was
`a63abea0689f783e1ff3299d89810fbb512f316ff82e5a14e5246aa270a44b4d`.
The focused integer compact test passed 42 budget boundaries, and the
`i32.load + local.tee` differential passed 27 outer plus 27 compact budget
boundaries and its memory trap. `just verify` passed all seven full-state
workloads, Java 1.3, CLDC, target-47, preverification, release, cache,
counterless, and JAR gates. Its counterless exactness artifact was
`47e63e0dfcf4314b84fec6c6ece32cad477f5d4d50631327d7d17d6345fdfa39`;
the receipt SHA-256 was
`8f5085c977f34c67cc50dc7622a4ff7ecaf23e90e8c3eaccf86d87aea29a58bb`.

Target-47 `javap` confirmed the intended shape: an admitted compact
instruction executes `iload withinBudget; ifne` before the old limit
comparison, skipping both `getfield instructionsExecuted` and
`getfield instructionLimit` on that path. `execute` grew from 7,865 to 7,893
bytecodes and `executeCompactBlock` from 3,070 to 3,087. The preverified
interpreter grew from 82,166 to 82,433 bytes at SHA-256
`936276f4a9d7a25694ecc29c414ef8330f15da2233ee394913ebf9ec10e620c5`.
The phoneME artifact SHA-256 was
`5d1e0ca293faff03d6059f660c8e4725c819c61c2f6f3a7309ee84df06396d4e`.
The full and base JARs grew by 20 bytes each to 269,592 and 267,070 bytes,
with SHA-256
`a30f58e644b9096c67b881ab9178567a50756a578b54a8eb1087794c5ef2030b`
and
`bb554d37006fa84f11ccff1f6d32138939cb2a4eddeff38ccbb5e1a34d82aae8`.
No W4IR/RMS, field, array, allocation, or persistent-heap delta occurred.

Twelve balanced native i686 phoneME Game of Life pairs decisively rejected
the candidate: median `-22,500 us/frame`, **-0.731%**, with 2 wins and 10
losses. Every pair matched the 12,802,761 logical instructions, checkpoint,
and direct-branch payload exactly. The result is timer-resolved and fails
both the +0.8% effect floor and the 9/12 win floor, so Rubido, Waternet, and
Untangle controls were correctly skipped. Raw evidence is under
`/tmp/w4me-interpreter-research/raw/njit024/runs/`
`njit048-budget-elision-vs-njit046-20260727/game-of-life-zig-edition/`.
The receipt, pair CSV, and paired-stat SHA-256 values are respectively
`7a10a815024e68650a088598028d4c90d3e921925bda666f19af11af01a9e997`,
`9e53c873667e494bf593bef78100e11dffbf6ef06088f8526f14fb3479538444`,
and
`ea51395a0086716d14980107848c8592102bcc5b76d530894645b6b24fc6e90c`.

**Verdict:** reject. On phoneME, the extra local branch in every compact
dispatch costs more than the skipped pair of quickened field reads and
comparison, even when the one block-level proof is amortized across long Game
of Life regions. The implementation is removed completely. Do not retry a
per-instruction boolean gate; a future accounting optimization must remove
the per-instruction branch itself, for example through a separate safe
executor shape with no duplicated hot-loop decision.

### NJIT-049: separate admitted compact executor

**Status:** `rejected`.

NJIT-048 proved that whole-block budget admission is exact but lost 0.731% on
Game of Life because the admitted path still executed one new local boolean
branch on every compact dispatch. NJIT-001 earlier moved the mandatory
instruction counter into a local with exact `finally` publication and measured
a small +0.312% Rubido result, but retained a per-instruction limit comparison.
These two results isolate a new shape: select a separate executor once per
compact block, then let its inner loop contain neither a budget comparison nor
a selection branch.

The outer executor uses the same exact admission proof:

```text
instructionsExecuted <= instructionLimit - (compactEnd - pc)
```

If true in the counterless production build, call
`executeCompactBlockUnchecked`; otherwise call the existing
`executeCompactBlock` byte-for-byte unchanged. The unchecked method mirrors
the same compact dispatch and handlers, snapshots `instructionsExecuted` into
a local, performs the same standard, load/tee, and post-fused increments on
that local, and publishes it from `finally` on both normal and exceptional
exit. Because the whole block is known to fit, no omitted budget comparison
can trap. The `finally` publication preserves the exact counter observed after
stack, memory, numeric, or helper traps, including fused handlers whose span is
accounted only after successful completion.

The separate method intentionally duplicates the compact switch for this
experiment. JAR size is not an acceptance constraint, but target-47 class and
method sizes, StackMap validity, and phone heap remain measured. Do not change
compact eligibility, W4IR/RMS, handler semantics, diagnostics, arrays, fields,
allocations, or the existing checked executor. The diagnostic build continues
to use the checked method so corpus profiling remains canonical; the
counterless full-state build exercises the unchecked path.

The retained baseline is accepted NJIT-046 at source SHA-256
`a62bb687c2803bcbf1763cc63e1b3cb0f030b46060fdaf3b42fde8c76e6879e5`,
82,166-byte preverified interpreter SHA-256
`17795670307a11bda48f1c9826e9a1b78962c76292ce8dfa9461ce0a1bb5804a`,
phoneME artifact SHA-256
`a9f6f9055e99aeafc4a7d22d7d8d4eca516cb20c3108d967eeb7c2d741bd3e99`,
and seven-workload exactness artifact
`bda67da37618a3a1849464d915715e7dd59529ecb1bf97490047450b31f04156`.
The currently rebuilt stable full/base JARs are 269,565 and 267,043 bytes at
SHA-256
`cc01dc2ca822eeb83626ec843a2c19f1269c2db3dce1fd2f2a22f5a0d94912fd`
and
`9e1e916e16d2e627df5cd5e82845d798470e481ee085af426f16f17d782bf908`.

Before timing, run the focused outer/compact budget sweeps, semantic-trap
fixtures, all seven full-state workloads, Java 1.3, CLDC, target-47,
preverification, release, cache, counterless, StackMap, and method-size gates.
Use `javap` to prove that the unchecked loop has no `instructionLimit` read or
per-dispatch admission branch and that its local counter is published through
an exception handler.

Native acceptance uses at least twelve balanced i686 phoneME pairs on Game of
Life. Require a timer-resolved median of at least +0.8% and at least 9/12 wins.
If it passes, run Rubido, Waternet, and Untangle controls with a -0.5%
no-regression floor. Record raw receipts, artifact hashes, class/JAR growth,
and exact counters. If the primary gate fails, remove the duplicated executor
and restore accepted NJIT-046 exactly.

The prototype implemented that shape without changing W4IR, RMS, arrays,
fields, or heap allocation. The existing checked compact executor remained
unchanged. The counterless build selected the duplicate only after the
subtraction-safe whole-block proof, kept the logical count in a local, and
published it through `finally`. `javap` confirmed a 3,017-byte unchecked
method with a `tableswitch`, an exception table, and no `instructionLimit`
read; the checked method remained 3,070 bytes. `execute` grew from 7,865 to
7,901 bytes. The preverified interpreter grew from 82,166 bytes at SHA-256
`17795670307a11bda48f1c9826e9a1b78962c76292ce8dfa9461ce0a1bb5804a`
to 89,790 bytes at SHA-256
`c86c7efd7e35f0efc4a50194a73f61e309db77a69e584e14b30ea34b6dad866b`.
The full/base JARs were 271,524 and 269,002 bytes at SHA-256
`ed0ad87cb9ab63547e2765f8696fcb18de5ad82a0e7880d836dd62413260bf11`
and
`03c405b3445b84b429cbf1cb43b0378ecdb1eab38d43cd80be0adfb01cd5dc0c`.

`just test`, `just verify`, strict OpenSpec validation, all focused compact
budget/trap sweeps, and all seven counterless full-state workloads passed.
The counterless exactness artifact was
`139a5d5e0231ef3894b63c4e7f524fc6eeee28ca51dc81ee89c6e3bccdd05440`;
its receipt SHA-256 was
`3bc2d6db89291cc7ba56420534b56b1d481aa5bf24b01f446a163240c2ddb997`.
The exact commands were:

```text
just test
just verify
openspec validate optimize-no-jit-frame-budget --strict
tools/phoneme/run.sh bench game-of-life-zig-edition \
  --mode optimized --candidate counterless --reps 1
BASELINE=/tmp/w4me-njit046-20260727/counterless-preverified \
CANDIDATE=/tmp/w4me-njit049-20260727/candidate-preverified \
RUN_ID=njit049-unchecked-compact-vs-njit046-20260727 \
  /tmp/w4me-interpreter-research/raw/njit024/run-paired.sh \
  12 game-of-life-zig-edition
```

The native i686 phoneME primary gate decisively failed:

```text
baseline/candidate us per frame:
3013000/3089000, 3019000/3052000, 3071000/3114000,
3033000/3081000, 3069000/3101000, 3045000/3048000,
3011000/3083000, 3009000/3082000, 3038000/3092000,
3085000/3010000, 3045000/3078000, 3080000/3047000
median speedup -1.247%, median delta -38000 us/frame,
2 wins, 10 losses, timer-resolved, order-balanced
```

Raw evidence is under
`/tmp/w4me-interpreter-research/raw/njit024/runs/njit049-unchecked-compact-vs-njit046-20260727/game-of-life-zig-edition/`;
the receipt, pair CSV, and paired-stat hashes are
`e102052cb3228a3ac8c232ff3f22c25b6d1ae74ea4b68297d9e339326f98729c`,
`d24a267489a6691d7b83460b530ceaa8d3b6cc2b37e01fbbe62166ea8b29730a`,
and
`f932d49e5afa5451dea0fffb864b3d4ff6c04378a643c67ddd17d55004410258`.
The runner receipt's `source-head` field refers to its immutable research
checkout; the candidate is instead bound to current repository HEAD
`8e850656f2b19256c2559cdd07f165c7788b16d4` and the source/artifact hashes
above.

**Verdict:** reject. Removing the per-token budget comparison and field
counter traffic did not repay the duplicated method's call/layout cost on
phoneME. Do not retry a separate full compact switch merely with another
counter-publication shape. Reconsider only with a materially smaller
specialized region body or direct native evidence that method layout no
longer dominates.

### NJIT-050: scalar defined-function result arity

**Status:** `rejected`.

The outer `execute` method receives a complete `FuncType`, but its only use is
to evaluate `functionType.results.length`. The accepted counterless target-47
artifact contains ten such dynamic sites. Every site currently executes
`aload_3`, a quickened `getfield results`, and `arraylength`; the proposed
shape passes `type.results.length` from `callFunction` as an `int` in the same
category-1 parameter slot and replaces each use with `iload_3`. Parameter
order and all later local slots remain unchanged.

This is not a repeat of NJIT-004. That candidate moved `locals` into a short
parameter slot and changed the load form across most opcode handlers.
NJIT-050 keeps every parameter slot and handler layout except for the type of
slot 3 and the ten result-arity reads. It is also independent of NJIT-020:
argument copying, frame clearing, call entry, and return transfer remain
byte-for-byte equivalent apart from the scalar argument at the sole
`execute` call site.

The current exact corpus report, SHA-256
`6d2362c6272120eb0b28a4bdcd58f0e884ca2bfa2b13f262cc7748e4a200a945`,
records 281,601 defined-function entries in the one-frame Game of Life route,
including 281,600 internal calls. Every successful defined-function execution
must reach one of the scalar result-arity sites through final `end`, explicit
`return`, or a branch to the function label. Other routed totals are 38,040
for Rubido, 33,650 for Waternet, 53,317 for Untangle, and 17,146 for Duck
Maze. These totals include imported and intrinsic entries in the diagnostic
profile, so they are upper bounds outside Game of Life; native timing remains
the performance judge.

The retained baseline is accepted NJIT-046 at source commit
`8e850656f2b19256c2559cdd07f165c7788b16d4`, current
`WasmInterpreter.java` SHA-256
`a62bb687c2803bcbf1763cc63e1b3cb0f030b46060fdaf3b42fde8c76e6879e5`,
82,166-byte counterless preverified interpreter SHA-256
`17795670307a11bda48f1c9826e9a1b78962c76292ce8dfa9461ce0a1bb5804a`,
phoneME artifact SHA-256
`a9f6f9055e99aeafc4a7d22d7d8d4eca516cb20c3108d967eeb7c2d741bd3e99`,
and seven-workload counterless exactness artifact
`bda67da37618a3a1849464d915715e7dd59529ecb1bf97490047450b31f04156`.
The baseline target-47 dump is
`/tmp/w4me-njit050-baseline.javap`, SHA-256
`d150e0816e3119c695a5eec17bdf3e107848da03b9bd177edc4b534c636ee6a9`.

Change only the private `execute` parameter type, its sole call site, and the
result-arity uses in `WasmInterpreter`. Do not add a `FunctionBody` field,
array, cache entry, W4IR/RMS token, allocation, tier condition, opcode,
cartridge test, or semantic shortcut. Imported calls continue to use the
complete `FuncType` in `callFunction`; `call_indirect` canonical-type checks
remain unchanged. Instruction accounting, trap order, stack transfer,
descriptor shadowing, compact/trace selection, and direct branches must be
identical.

Correctness and static gates are `just test`, `just verify`, strict OpenSpec
validation, and the complete Java 1.3, CLDC, target-47, preverification,
release, cache, budget/trap, counterless, and seven-workload full-state
matrix. Inspect `javap` to require an `int` third parameter, zero
`FuncType.results` reads inside counterless `execute`, the dense
`tableswitch`, valid StackMap data, and no new fields or persistent heap.
Record method, class, and release-JAR size deltas as diagnostics rather than
acceptance constraints.

Build a hash-bound counterless candidate and first require one exact native
phoneME sanity run. Game of Life is primary because it has the highest
production-shaped defined-call density. Run sixteen balanced native i686
phoneME pairs with identical checkpoints and deterministic counters. Because
the change removes only two interpreted JVM bytecodes per dynamic arity read,
predeclare a conservative small-effect gate: median speedup at least +0.3%,
at least eleven wins in sixteen pairs, and an effect above timer resolution.
If it passes, run eight balanced Rubido, Waternet, and Untangle controls with
a -0.5% no-regression floor. Record all raw receipts and hashes. Reject and
remove the candidate if the primary gate fails or any control regresses.

Planned commands:

```text
just test
just verify
openspec validate optimize-no-jit-frame-budget --strict
tools/phoneme/run.sh bench game-of-life-zig-edition \
  --mode optimized --candidate counterless --reps 1
BASELINE=/tmp/w4me-njit046-20260727/counterless-preverified \
CANDIDATE=/tmp/w4me-njit050-20260727/candidate-preverified \
RUN_ID=njit050-result-arity-vs-njit046-20260727 \
  /tmp/w4me-interpreter-research/raw/njit024/run-paired.sh \
  16 game-of-life-zig-edition
```

The isolated implementation changed only the scalar argument described
above. Its `WasmInterpreter.java` SHA-256 was
`e2a4794e0ac32e13ea3be64a4b96a8abb5fc3c775c7fba7699447e309436da7c`.
`just test` and `just verify` passed the complete focused, Java 1.3, CLDC,
target-47, preverification, release, cache, budget/trap, counterless, and
seven-workload full-state matrix. The counterless exactness artifact was
`f03fcb8e16183b73068ca67103f24f1314d5e7ef0fe898c06b3d505bd545b436`;
its receipt SHA-256 was
`0ac9086d956c1ddfabda2a9a089ff8d2aa7a6e93e32c9fdd1516fe58bc665d69`.

Target-47 `javap` produced
`execute(int, FunctionBody, int, long[], int, int)` with zero
`FuncType.results` reads in the counterless method. `execute` shrank from
7,865 to 7,825 bytecodes and retained its dense `tableswitch`. The
counterless preverified interpreter shrank from 82,166 to 81,526 bytes at
SHA-256
`92d574b733f5ecdec8a8693b6f6df3648ed355b3553287e9d633e724f79a7d5f`.
The full/base JARs shrank by 25 bytes each to 269,540 and 267,018 bytes at
SHA-256
`924b9169181aa0bc35f40daccac635eefc8e256fdea4229e4612045ca51622a8`
and
`97ad20db8fc1c15f4d8568b75aacd57c3159f818b2389ba7895d548acdbb2068`.
There was no W4IR/RMS, field, array, allocation, or persistent-heap delta.
The staged phoneME artifact SHA-256 was
`a165c38b750680e8bdd9049c300ad1c4fea3340de3e24774446f0b47f96ac0bc`.

Sixteen balanced native i686 phoneME Game of Life pairs measured median
`-5,500 us/frame`, **-0.181%**, with 7 wins and 9 losses. Every invocation
matched the 12,802,761 logical instructions, checkpoint, and direct-branch
payload exactly. Sample 7 was a visible one-sided host outlier at
7,205,000 us/frame for the candidate versus the normal approximately
3,000,000 us/frame range. Removing that sample only changes the tally to
7 wins and 8 losses across the remaining 15 samples; their median paired
percentage remains negative at -0.066%. Even converting the outlier into a
candidate win could produce at most 8/16, below the predeclared 11/16 gate,
so a rerun cannot rescue this completed primary result without discarding
additional ordinary samples.

Raw evidence is under
`/tmp/w4me-interpreter-research/raw/njit024/runs/`
`njit050-result-arity-vs-njit046-20260727/game-of-life-zig-edition/`.
The receipt, pair CSV, and paired-stat SHA-256 values are respectively
`50cd462556c9e98c103e06f033d7ac6a5e02c1a596394320fb0de4464124a830`,
`e76320f5afdef63b8b14b1e6083fe1c1e9c1cd79f30d3fafbe7f95ebb10503f9`,
and
`1d12ae4d40c76b6027a169281906c1ade7d0c80cb1514cc278fb17d5c5a085e3`.

**Verdict:** reject. Replacing repeated `getfield + arraylength` work with a
short scalar local reduces bytecode and artifact size but does not improve
the authoritative runtime. Rubido, Waternet, and Untangle controls cannot
make the failed primary gate pass and were not spent. Remove the candidate
and restore accepted NJIT-046 exactly. Reconsider result metadata only as
part of a materially broader call-frame redesign with independently measured
coverage, not by repeating this scalar parameter form.

### NJIT-051: inline ordinary direct-defined-call frame setup

**Status:** `inconclusive`.

**Hypothesis and source.** Every ordinary direct WebAssembly `call` currently
executes `execute(caller) -> callFunction -> execute(callee)`. The middle Java
frame remains live for the complete callee execution even though its
post-call work is only the `finally` restoration of `callDepth` and
`controlTop`. The accepted target-47 counterless release has
`callFunction` at 540 code bytes with 13 local slots, while `execute` has
7,865 code bytes and 134 local slots. The retained phoneME C interpreter has
no JIT or Java inlining, so each middle frame and its `invokespecial`/return
are real VM work.

The exact current Game of Life route executes 281,600 direct ordinary calls in
one frame: function 7 is entered 25,600 times, function 8 204,800 times, and
function 9 51,200 times. The ordinary direct `call` opcode count is exactly
281,600; the route has no indirect calls or numeric-intrinsic opcodes. This is
the highest measured direct-defined-call density in the corpus.

This is materially different from NJIT-006, which retained a helper frame and
specialized only direct host imports, and from NJIT-020/NJIT-050, which changed
argument copying or one result-arity parameter inside the existing
`callFunction` frame. It is the broader call-frame redesign explicitly named
by NJIT-050's reconsideration condition.

**Isolated mechanism.** Change only generic direct opcode `0x10` in
`WasmInterpreter.execute`. For a range-valid, non-null,
`INTRINSIC_NONE` target, perform the existing defined-function setup,
recursive `execute`, and `finally` restoration directly inside a scoped case
block. Keep the exact existing order for profiling, call-depth validation,
type and argument validation, local-frame selection, complete frame clearing,
the NJIT-020 scalar/native argument copy, `valueTop`, `callDepth`, and
`controlTop`. Invalid operands, imports, and numeric-intrinsic bodies fall
back to the unchanged `callFunction`; `call_indirect`, lifecycle calls, and
export invocation remain unchanged.

The scoped case is required so the candidate can reuse Java local slots
instead of permanently shifting all later switch locals. There is no new
W4IR opcode, format version, cache metadata, field, array, allocation,
cartridge identity, tier condition, or persistent heap. The candidate is
allowed to grow `execute` beyond the old 7,800 heuristic; the current
corruption guard is 16,000 code bytes. Record the actual `max_locals`, code
length, class/JAR size, and preverified frame shape because increasing the
134-slot recursive executor frame can erase the saved 13-slot middle frame.

**Baseline identity.**

- source and `origin/main`:
  `8e850656f2b19256c2559cdd07f165c7788b16d4`;
- `WasmInterpreter.java`:
  `a62bb687c2803bcbf1763cc63e1b3cb0f030b46060fdaf3b42fde8c76e6879e5`;
- accepted NJIT-046 counterless preverified interpreter:
  82,166 bytes at
  `17795670307a11bda48f1c9826e9a1b78962c76292ce8dfa9461ce0a1bb5804a`;
- accepted phoneME artifact:
  `a9f6f9055e99aeafc4a7d22d7d8d4eca516cb20c3108d967eeb7c2d741bd3e99`;
- counterless exactness artifact:
  `bda67da37618a3a1849464d915715e7dd59529ecb1bf97490047450b31f04156`;
- native i686 VM, CLDC classes, and preverifier:
  `bb4866969747430bb619d139c75ab31982e8967aa0e2dcc3e278fcc7920839a2`,
  `117820661071b411b3937e8c708a9581aa48ba06fd06e24eadb5ebbf2036afbe`,
  and
  `4fe0d1b160ac7f18c4f7489917a1d868ff82df50842ca718729b098b25f2f50c`.

The restored stable release artifacts are 269,565 and 267,043 bytes at
`cc01dc2ca822eeb83626ec843a2c19f1269c2db3dce1fd2f2a22f5a0d94912fd`
and
`9e1e916e16d2e627df5cd5e82845d798470e481ee085af426f16f17d782bf908`.

**Correctness and measurement gates.** Add a focused direct-call fixture
covering zero, one, two, and larger argument arities; zero/one/multi-value
results; recursion; call-depth exhaustion; underflow; ordinary, import,
intrinsic, indirect, export, and lifecycle targets; local-frame reuse;
32/64-bit raw values; callee traps; and instruction-budget boundaries.
Require exact stack, locals, memory, globals, table, control restoration,
profile counts, trap class/text/order, and logical instruction count. Then
pass Java 1.3, CLDC, target-47, preverification, release, cache, seven-workload
full-state, counterless, and strict OpenSpec gates.

Inspect target-47 and preverified bytecode to require one fewer
`callFunction` invocation for an ordinary direct call, an unchanged fallback,
the dense `tableswitch`, valid StackMap data, and bounded `execute`
`max_locals`/code size. Build a hash-bound counterless candidate and require
one exact native phoneME sanity run. Run sixteen balanced native i686 phoneME
Game of Life pairs. The primary gate is median speedup at least +0.5%, at
least eleven wins in sixteen pairs, an effect above timer resolution, and
identical checkpoints/counters. If it passes, run eight Rubido, Waternet, and
Untangle control pairs with a -0.5% no-regression floor. Reject and remove the
candidate on a failed primary or control gate.

Planned commands:

```text
just test
just verify
openspec validate optimize-no-jit-frame-budget --strict
tools/phoneme/run.sh bench game-of-life-zig-edition \
  --mode optimized --candidate counterless --reps 1
BASELINE=/tmp/w4me-njit046-20260727/counterless-preverified \
CANDIDATE=/tmp/w4me-njit051-20260727/candidate-preverified \
RUN_ID=njit051-inline-direct-call-vs-njit046-20260727 \
  /tmp/w4me-interpreter-research/raw/njit024/run-paired.sh \
  16 game-of-life-zig-edition
```

**Observed result and verdict.** The isolated candidate source was
`b1548a2bf9dcc1fe2b0df8b6bebc429b4ab0dcbc5504c63fcca4d404c46092a7`.
It passed the complete `just test` and `just verify` gates. The exactness
artifact was
`c49b458b50f17982fe1fac273c162873550c27de8f32491e8e8cc681f7b98ff4`
and its receipt was
`5ec66006b2259e91e2a2fa73f139eb3a7c9544c4d8e9af1e2f7b2cfd20210c0f`.
The target-47 `execute` method grew from 7,865 to 8,177 code bytes while
remaining at 134 local slots and retaining the dense `tableswitch`. There was
no W4IR, RMS, or persistent-heap format change. The preverified counterless
interpreter was 83,583 bytes at
`0257f1bb75d54d35ac56e3489e78b4dce1c147ed19435267de6f652b3a5de21f`.

One native phoneME exactness sanity run completed with the expected
12,802,761 logical instructions and exact checkpoint state. Its wall time was
4,382,000 microseconds per frame, but an unpaired sanity run is not
authoritative performance evidence. The predeclared sixteen-pair run was then
started with the exact NJIT-046 and NJIT-051 trees. The owner stopped the
experiment during sample zero, before either a complete pair or any row in
`pairs.csv` existed. The partial receipt and empty-header CSV are preserved at
`/tmp/w4me-interpreter-research/raw/njit024/runs/`
`njit051-inline-direct-call-vs-njit046-20260727/`
`game-of-life-zig-edition/`, with SHA-256
`265138ab40018981147c52b3021f4abbdf014566f08b140f6659761b30b11874`
and
`5da70d1281c2f9eb30ac73d948b1c07ffc95c5264b7e28328fb7c63411b0c264`.

Therefore NJIT-051 has no performance verdict and is classified
`inconclusive`, not rejected. The candidate code was removed and the exact
accepted NJIT-046 source and release artifacts were restored at the owner's
request to stop on a stable version. Reconsideration requires a fresh complete
sixteen-pair run from the recorded accepted baseline; the isolated sanity
timing must not be reused as a rejection or acceptance result.

### Final accepted-set A/B against the main release

The complete retained production set was measured directly against
`main@8e850656f2b19256c2559cdd07f165c7788b16d4` so release notes do not
need to compose medians from the isolated candidate series. The baseline was
built from an archive of that exact commit with its regular production
configuration, which still writes diagnostic dispatch and compact counters.
The candidate was the restored stable NJIT-046 production configuration at
`WasmInterpreter.java` SHA-256
`a62bb687c2803bcbf1763cc63e1b3cb0f030b46060fdaf3b42fde8c76e6879e5`,
with diagnostic counters and opcode profiling compiled out.

The preverified baseline and candidate tree aggregate SHA-256 values were
`27780eb7db0c6595685cd703a5c77728446931cc1285c307c6e312bda48f622c`
and
`4c5bf0fb1953f729f36d383d3ce51c20f5906a8ea670f73245701bb43990f7d3`.
Their `PhoneMeRouteBench.class` and `FramebufferOracle.class` files were
byte-identical, at
`38a538946d6044e16864be0107df54cbf8f6439b025b5b980768cbe29496a98e`
and
`c4cfedb70fccc4991aa778f29168b5aff432040f423f27c76b92e476903a7905`.
The native i686 phoneME VM and CLDC classes remained
`bb4866969747430bb619d139c75ab31982e8967aa0e2dcc3e278fcc7920839a2`
and
`117820661071b411b3937e8c708a9581aa48ba06fd06e24eadb5ebbf2036afbe`.

Sixteen balanced native pairs per route produced:

| Route        |        Main median |    NJIT-046 median | Median frame-time reduction | Throughput |  Wins |
| ------------ | -----------------: | -----------------: | --------------------------: | ---------: | ----: |
| Waternet     |  17,202.5 us/frame |  11,064.5 us/frame |                 **35.753%** | **1.555x** | 16/16 |
| Rubido       | 104,573.5 us/frame |  86,767.0 us/frame |                 **17.057%** | **1.205x** | 16/16 |
| Untangle     |   4,835.5 us/frame |   1,682.0 us/frame |                 **65.151%** | **2.875x** | 16/16 |
| Game of Life | 3,463,000 us/frame | 3,090,000 us/frame |                 **10.826%** | **1.121x** | 16/16 |

Every route was timer-resolved and all 64 pairs favored the retained
candidate. Every invocation matched the browser-oracle checkpoints, logical
instruction count, fast-path count, and direct-branch metadata. Optional
dispatch/compact/trace telemetry was deliberately excluded from the equality
signature because removing those writes is one of the measured production
changes; the raw pass lines retain both sides' values and show the expected
nonzero-main/zero-candidate distinction.

Raw evidence lives under
`/tmp/w4me-interpreter-research/raw/njit024/runs/`
`final-main-release-vs-njit046-production-clean-20260727/`. The
`pairs.csv` / `paired-stats.txt` / `receipt.txt` SHA-256 values are:

- Waternet:
  `b87a8b14ff1898e29611304f4632b9c307a65e9e0e06e75579983b6edde52091`,
  `02893b06fec71a360ee190a9ccdb17895b9cdee79cc13d8fb6316c1c195f5959`,
  and
  `10a278fbdc0cc03cf113e4d3af09c5b476149189cec209b7846d27785c4da3bd`;
- Rubido:
  `948c64f6438471055299c396114cb73b1fa8a52d95d509ebeed71094e0c3915f`,
  `5d2f67e75d001202dea4a0516d0e2dbc6e2cd6968dd1ae6cc1fee1b9734e40db`,
  and
  `adacf2ab8ba9e9c57de82ae7189ff628fccd78d094971190d0855d46b831b104`;
- Untangle:
  `3ec161a586df5396e4dc2be35040629d297d10ef2cfc38f1112d9af36dc65ea4`,
  `bf156650a062a07f74424d2b8d7efb53abd6f65d7cb6b775cbf1d454810ae295`,
  and
  `3c978bf25a486557951456d64b850f9ab50a78017db3f5a98ddb26468a8c8e6d`;
- Game of Life:
  `a4b1a4c9ac8473ed0063097bd3acf455b5b1de62859af1dfffd67159a434d8ab`,
  `4fceda84cba025c0cd589713ff22e34874ff510e97324376674543bcd2df0902`,
  and
  `886d55216b147455a95ebf757b9945a79864b46fe0cca77ecbc072185331ca53`.

These are headless runtime-route results. They exclude MIDP framebuffer
presentation, physical display scaling, input delivery, and audio backend
latency, so the frame rates must not be presented as measured Nokia E71 FPS.

## Risks / Trade-offs

- [The ledger becomes prose without reproducible evidence] -> Require commands,
  hashes, workloads, raw paired values, and receipt paths in every entry.
- [A benchmark process contaminates another run] -> Check active processes
  before every native timing window and never delegate competing benchmarks.
- [A small apparent win is host noise] -> Use balanced paired effects, repeat
  unstable results, and classify unresolved effects as inconclusive.
- [A microbenchmark optimizes a shape absent from games] -> Record dynamic
  corpus coverage before using a microbenchmark and retain route controls.
- [A faster path consumes scarce phone heap] -> Record persistent and peak heap
  effects and run constrained-heap gates.
- [The stable branch accumulates experimental code] -> Remove rejected
  candidates and commit only accepted, fully verified changes.

## Migration Plan

1. Create the ledger and record the current clean baseline.
2. Select one new candidate not closed by the historical index.
3. Add its complete planned entry, implement it in isolation, and run exactness
   and artifact gates.
4. Run the authoritative paired A/B on a clean source snapshot.
5. Update the verdict, remove rejected code or commit an accepted stable
   optimization, and begin the next candidate.

Rollback of an accepted candidate is the focused revert of its commit. Cache
format changes, when unavoidable, must atomically reject and rebuild older
records.

## Open Questions

- Which renderer conversion shapes dominate on the actual physical phone
  display sizes?
- Which sound-active cartridge provides a deterministic route that separates
  PCM synthesis cost from MMAPI setup latency?
- Can branch-capable compact regions provide a positive no-JIT result now that
  ordinary taken branches have a constant-time direct path?
