## 1. Durable experiment foundation

- [x] 1.1 Define the required candidate-ledger fields, statuses, and
      reconsideration rules
- [x] 1.2 Record the clean `f4c824b` baseline and exact artifact, VM, CLDC, and
      preverify hashes
- [x] 1.3 Index accepted, rejected, superseded, and explicitly deferred
      pre-ledger candidate families
- [x] 1.4 Define the native phoneME, KEmulator, QEMU, and physical-device judge
      boundaries

## 2. Candidate research and selection

- [x] 2.1 Collect independent interpreter, no-JIT VM, and non-interpreter frame
      budget research without overlapping edits or benchmark processes
- [x] 2.2 Rank new candidates against dynamic coverage, target-VM mechanism,
      implementation risk, heap cost, and prior experiment history
- [x] 2.3 Add the selected candidate's complete planned `NJIT-*` ledger entry
      before implementation

## 3. Isolated candidate gate

- [x] 3.1 Implement one production-shaped candidate without unrelated changes
- [x] 3.2 Add focused semantic, trap, budget, cache, and differential tests
      required by the affected path
- [x] 3.3 Verify Java 1.3, CLDC 1.1, classfile version 47, preverification,
      release JAR integrity, bytecode headroom, and heap or artifact-size effects
- [x] 3.4 Run the affected exact corpus, full-state, phoneME correctness, and
      MIDP integration gates before timing

## 4. Authoritative measurement and verdict

- [x] 4.1 Produce clean, hash-bound baseline and candidate artifacts with the
      experimental selection compiled out of the hot path
- [x] 4.2 Run balanced paired native i686 phoneME A/B with enough repetitions
      for a primary-workload decision; run no-regression timing controls before
      acceptance, but do not spend them after a decisive primary rejection
- [x] 4.3 Record every raw pair, timer resolution, win/loss count, counters,
      receipt path, bytecode delta, and memory delta in the candidate ledger
- [x] 4.4 Mark the candidate accepted, rejected, inconclusive, blocked, or
      superseded with an explicit reason and reconsideration condition

## 5. Stable closeout and next cycle

- [x] 5.1 Remove rejected or inconclusive implementation code while preserving
      its ledger entry, or measure an accepted candidate with the retained set
- [x] 5.2 Run the complete verification matrix on the exact stable tree
- [x] 5.3 Review scope and commit an accepted optimization to `main` with a
      focused English message; make no production commit for a rejected candidate
- [x] 5.4 Update the baseline identities and append the next candidate section,
      then repeat sections 2 through 5 until the owner changes or stops the goal

## 6. NJIT-002 resident operand-free numeric payload loads

- [x] 6.1 Implement the isolated resident-only numeric payload-load candidate
      without changing W4IR or cache formats
- [x] 6.2 Cover the resident numeric boundaries and retain the existing valid
      paged, malformed-cache, trap, and budget differential gates; the
      resident-only guard leaves paged payload reads unchanged
- [x] 6.3 Pass the Java 1.3, CLDC, target-47, preverification, exact-state,
      release, bytecode-headroom, and memory gates
- [x] 6.4 Produce clean hash-bound artifacts and run the native i686 phoneME
      Waternet paired acceptance gate
- [x] 6.5 Record all pairs and the final verdict, remove a rejected candidate or
      fully verify and commit an accepted candidate, then select `NJIT-003`

## 7. NJIT-003 sign-bit unsigned i32 comparisons

- [x] 7.1 Record the independent semantic, target-bytecode, phoneME mechanism,
      coverage, history, and adversarial reviews in the ledger
- [x] 7.2 Implement only the four unsigned `compareI32` expressions and add the
      focused 36-pair oracle for forced-outer and compact execution
- [x] 7.3 Pass focused budget, Java 1.3, CLDC, target-47, preverification,
      exact-state, release, bytecode-headroom, and memory gates
- [x] 7.4 Produce clean hash-bound artifacts and run the balanced native i686
      phoneME primary and required no-regression paired gates
- [x] 7.5 Record the verdict, remove a rejected candidate or fully verify and
      commit an accepted candidate, then select the next ledger entry

## 8. NJIT-004 short-local parameter layout

- [x] 8.1 Record the target-47 local-slot map, phoneME bytecode-cost mechanism,
      dynamic hot-reference coverage, history, and adversarial review
- [x] 8.2 Implement one isolated parameter-layout candidate without changing
      behavior, formats, persistent heap, or the instruction budget
- [x] 8.3 Pass Java 1.3, CLDC, target-47, preverification, exact-state, release,
      bytecode-headroom, and memory gates
- [x] 8.4 Produce clean hash-bound artifacts and run balanced native i686
      phoneME primary and no-regression paired gates
- [x] 8.5 Record the verdict, remove a rejected candidate or fully verify and
      commit an accepted candidate, then select the next ledger entry

## 9. NJIT-005 effective-address guard

- [x] 9.1 Record the exact WebAssembly address algebra, current target-47 and
      phoneME cost, dynamic memory-op coverage, history, and adversarial review
- [x] 9.2 Implement one isolated effective-address candidate with focused
      overflow, offset, width, trap-order, and partial-store coverage
- [x] 9.3 Pass Java 1.3, CLDC, target-47, preverification, exact-state, release,
      bytecode-headroom, and memory gates
- [x] 9.4 Produce clean hash-bound artifacts and run balanced native i686
      phoneME primary and no-regression paired gates
- [x] 9.5 Record the verdict, remove a rejected candidate or fully verify and
      commit an accepted candidate, then select the next ledger entry

## 10. NJIT-006 direct imported-call opcode

- [x] 10.1 Record import-call semantics, current target-47 and phoneME cost,
      dynamic host-call coverage, history, and adversarial review
- [x] 10.2 Implement one isolated direct imported-call lowering with focused
      argument, result, trap, budget, host-ID, cache, and fallback coverage
- [x] 10.3 Pass Java 1.3, CLDC, target-47, preverification, exact-state, release,
      bytecode-headroom, and memory gates
- [x] 10.4 Produce clean hash-bound artifacts and run balanced native i686
      phoneME primary and no-regression paired gates
- [x] 10.5 Record the verdict, remove a rejected candidate or fully verify and
      commit an accepted candidate, then select the next ledger entry

## 11. NJIT-007 exception-backed push capacity guard

- [x] 11.1 Record validator, RMS-cache, target-47, phoneME-handler,
      dynamic-coverage, exact edge-semantics, and adversarial reviews
- [x] 11.2 Implement only the exception-backed `push(long)` capacity guard and
      add focused valid, overflow, malformed-cache, trap-order, and rollback gates
- [x] 11.3 Pass Java 1.3, CLDC, target-47, preverification, exact-state,
      release, bytecode-headroom, cache, and memory gates
- [x] 11.4 Produce clean hash-bound artifacts and run the balanced native
      i686 phoneME Waternet primary and required no-regression paired gates
- [x] 11.5 Record the verdict, remove a rejected candidate or fully verify and
      commit an accepted candidate, then select the next ledger entry

## 12. NJIT-012 compact-fused short-local parameter layout

- [x] 12.1 Record the exact slot/load map, dynamic fused-handler coverage,
      phoneME handler mechanism, history, and independent adversarial reviews
- [x] 12.2 Implement only the `instruction`/`locals` parameter swap at the sole
      private call and declaration
- [x] 12.3 Pass Java 1.3, CLDC, target-47, preverification, exact-state,
      release, bytecode-headroom, cache, and persistent-memory gates
- [x] 12.4 Produce clean hash-bound artifacts and run the balanced native i686
      phoneME Rubido primary gate, followed by controls only if it passes
- [x] 12.5 Record every pair and the verdict, remove a rejected candidate or
      fully verify and commit an accepted candidate, then select the next ledger
      entry

## 13. NJIT-009 transform-free blit geometry loop

- [x] 13.1 Record exact flag/call/pixel coverage, target-47 mechanism, timing
      boundary, history, and independent reviews
- [x] 13.2 Implement only the transform-free row/coordinate specialization,
      retaining packed decode and `drawPoint`
- [x] 13.3 Add the focused all-flags geometry differential and pass Java 1.3,
      CLDC, target-47, preverification, exact-state, release, size, and memory gates
- [x] 13.4 Produce clean hash-bound artifacts and run the balanced native i686
      phoneME Waternet primary and required Rubido/Untangle controls
- [x] 13.5 Record all pairs and the verdict, remove a rejected candidate or
      fully verify and commit an accepted candidate, then select the next ledger
      entry

## 14. NJIT-014 inline plain-blit framebuffer write

- [x] 14.1 Record opaque draw coverage, phoneME call mechanism, exact isolated
      patch boundary, baseline hashes, risks, and native acceptance thresholds
- [x] 14.2 Inline only the existing `drawPoint` body in the accepted plain loop
- [x] 14.3 Pass the focused 520-case differential, Java 1.3, CLDC, target-47,
      preverification, exact-state, release, size, and memory gates
- [x] 14.4 Build clean hash-bound artifacts and run balanced native i686
      phoneME Waternet primary and Rubido/Untangle controls
- [x] 14.5 Record every pair and the verdict, remove a rejected candidate or
      fully verify and commit an accepted candidate, then select the next entry

## 15. NJIT-015 running packed plain-blit cursor

- [x] 15.1 Record dynamic coverage, exact mechanism, baseline hashes, history,
      alias/rollover risks, independent review, and native acceptance thresholds
- [x] 15.2 Compare conditional and branch-free target-47 cursor forms and
      implement only the smaller credible production candidate
- [x] 15.3 Expand focused rollover/alias coverage and pass Java 1.3, CLDC,
      target-47, preverification, exact-state, release, size, and memory gates
- [x] 15.4 Build clean hash-bound artifacts and run balanced native i686
      phoneME Waternet primary and required Rubido/Untangle controls
- [x] 15.5 Record every pair and the verdict, remove a rejected candidate or
      fully verify and commit an accepted candidate, then select the next entry

## 16. NJIT-016 adjacent upscaled ARGB row reuse

- [x] 16.1 Record the renderer coverage, phoneME arraycopy mechanism, baseline
      hashes, timing boundary, risks, history, and acceptance thresholds
- [x] 16.2 Preserve the original native/downscale loop and implement only the
      helper-isolated adjacent-row reuse for upscaled bands
- [x] 16.3 Add a CLDC renderer benchmark and focused exact differential for
      full/banded, repeated, arbitrary-map, invalid, and alias cases
- [x] 16.4 Run balanced native i686 phoneME component pairs at 240, 176, 160,
      and downscaled sides, then the required KEmulator presentation controls
- [x] 16.5 Record all raw pairs and the final verdict, remove a rejected
      candidate or fully verify and commit an accepted candidate, then continue

## 17. NJIT-017 packed framebuffer byte cache

- [x] 17.1 Record the packed-byte reuse mechanism, baseline hashes, isolated
      method boundary, risks, and native phoneME acceptance thresholds
- [x] 17.2 Prototype the per-row low-byte-address cache only in the upscaled
      conversion method and inspect target-47/preverified size
- [x] 17.3 Run the existing exact renderer differential and balanced native
      i686 phoneME pairs at 240, 176, and the 161 boundary
- [x] 17.4 Record raw pairs and the verdict, remove a rejected candidate or
      fully verify and commit an accepted candidate, then continue

## 18. NJIT-018 upscaled ARGB lookup local

- [x] 18.1 Record the field-load mechanism, post-NJIT-017 baseline hashes,
      isolated method boundary, risk, and native acceptance threshold
- [x] 18.2 Prototype the single lookup-table local and inspect target-47 and
      preverified method/class size
- [x] 18.3 Run exact differential and balanced native i686 phoneME 240/176
      primary pairs, adding banded controls when warranted
- [x] 18.4 Record raw pairs and verdict, remove a rejected candidate or fully
      verify and commit an accepted candidate, then continue

## 19. NJIT-019 native/downscale packed-byte cache

- [x] 19.1 Record the post-NJIT-018 baseline, redundant-load coverage,
      canonical-oracle gap, isolated boundary, risks, and acceptance thresholds
- [x] 19.2 Prototype the per-row packed-byte cache only in `copyArgbBand` and
      inspect target-47/preverified size
- [x] 19.3 Add an independent palette/framebuffer reference differential and
      pass native/downscale, arbitrary-map, band, invalid, and alias cases
- [x] 19.4 Run balanced native i686 pairs at 160 and 128 in full/band modes
- [x] 19.5 Record raw pairs and verdict, remove a rejected candidate or fully
      verify and commit an accepted candidate, then continue

## 20. NJIT-010 PCM common-tone fast path

- [x] 20.1 Record the post-NJIT-019 baseline, real tone coverage, isolated
      mechanism, exactness boundary, memory risks, and acceptance thresholds
- [x] 20.2 Prototype only the sustain-only/no-slide fast path and inspect
      target-47/preverified bytecode and class size
- [x] 20.3 Add the independent byte-exact differential and CLDC phoneME PCM
      benchmark with route-shaped and ADSR/slide workloads
- [x] 20.4 Run balanced native i686 pairs, exact output checks, full release
      verification, and the relevant KEmulator sound gates
- [x] 20.5 Record all raw pairs and verdict, remove a rejected candidate or
      fully verify and commit an accepted candidate, then continue

## 21. Game of Life phoneME performance route

- [x] 21.1 Confirm existing host/KEmulator coverage and measure the native
      phoneME cost and deterministic counters of one idle generation
- [x] 21.2 Add immutable one-frame input, framebuffer, palette, tone, and disk
      oracle fixtures and run them through the exact host replay
- [x] 21.3 Include Game of Life in the default and verify phoneME corpora with
      a one-frame per-route default and an explicit global override
- [x] 21.4 Exercise the balanced paired A/B path on both artifacts and record
      the exact checkpoint, counter, duration, and evidence-quality boundary
- [x] 21.5 Run the complete release verification, document the public command,
      validate OpenSpec, and review the final uncommitted diff

## 22. NJIT-020 Game of Life-primary deep profile

- [x] 22.1 Record the physical-device trigger, clean HEAD/profile artifact,
      production-variant mismatch, invalid evidence boundary, and correction gates
- [x] 22.2 Align the generic profile stream, compact metadata, tier pass, and
      full-state differential with the retained production configuration
- [x] 22.3 Re-run the seven-workload exact corpus and prove that the corrected
      Game of Life tier counters match the production phoneME route
- [x] 22.4 Rank the corrected Game of Life hot mechanisms, record one isolated
      NJIT-020 candidate with exact/bytecode/heap/A-B gates, and reject the rest

## 23. NJIT-020 defined-function argument bulk copy

- [x] 23.1 Implement only the zero/scalar/native argument-copy split after the
      unchanged full local-frame clear and inspect target-47/preverified bytecode
- [x] 23.2 Pass focused frame semantics, Java 1.3/CLDC, complete exact-state,
      trap/budget, release-JAR, cache, and relevant device gates
- [x] 23.3 Produce clean hash-bound artifacts and run at least twelve balanced
      native i686 phoneME Game of Life pairs plus the three route controls
- [x] 23.4 Record every raw pair and artifact/heap/bytecode delta, then remove a
      rejected candidate or fully verify and commit an accepted candidate

## 24. NJIT-021 signed compare plus direct conditional branch

- [x] 24.1 Record the new signed-pair coverage, accepted direct-descriptor
      mechanism, distinction from the rejected unsigned/equality batch, research
      alternatives, baseline identities, risks, and acceptance thresholds
- [x] 24.2 Implement only the three signed comparison relations plus retained
      `br_if` as one direct-descriptor W4IR mechanism, with a format bump and no
      cartridge-specific recognition
- [x] 24.3 Add focused signed-boundary, taken/untaken, branch-arity, fallback,
      trap, budget, cache-format, and profiler differentials; pass Java 1.3, CLDC,
      target-47, preverification, full-state, release, bytecode, and heap gates
- [x] 24.4 Produce clean hash-bound artifacts and run at least twelve balanced
      native i686 phoneME Game of Life pairs plus Waternet, Rubido, and Untangle
      controls when the primary gate passes
- [x] 24.5 Record all raw pairs and artifact/topology deltas, then remove a
      rejected candidate or fully verify and commit an accepted candidate before
      selecting the next ledger entry

## 25. NJIT-022 i32.load8_u plus local.set

- [x] 25.1 Record exact routed coverage, the accepted load/tee precedent,
      isolated mechanism, baseline hashes, trap/budget order, cache-format bump,
      bytecode/heap risks, commands, and native acceptance thresholds
- [x] 25.2 Implement only `i32.load8_u + local.set` as one two-instruction
      W4IR handler in the outer and compact executors, without absorbing a
      preceding address calculation or adding persistent runtime state
- [x] 25.3 Add focused byte/boundary/stack/trap/budget/cache/profile
      differentials and pass Java 1.3, CLDC, target-47, preverification,
      full-state, release, bytecode-headroom, compact-topology, and heap gates
- [x] 25.4 Produce clean hash-bound artifacts and run at least twelve balanced
      native i686 phoneME Game of Life pairs plus Waternet, Rubido, and Untangle
      controls when the primary gate passes
- [x] 25.5 Record every raw pair and artifact/topology delta, then remove a
      rejected candidate or fully verify and commit an accepted candidate before
      selecting the next ledger entry

## 26. NJIT-023 add-constant plus load8_u plus local.set

- [x] 26.1 Record the material distinction from NJIT-022, exact stable triple
      coverage, selected representation, baseline identities, semantic order,
      compatibility risks, commands, and native acceptance thresholds
- [x] 26.2 Implement only the four-logical-instruction
      `i32.add_const + i32.load8_u + local.set` W4IR handler in the outer and
      compact executors, with no runtime branch or persistent state
- [x] 26.3 Add focused stack/address/byte/local/budget/cache/profile
      differentials and pass Java 1.3, CLDC, target-47, preverification,
      full-state, release, bytecode-headroom, compact-topology, and heap gates
- [x] 26.4 Produce clean hash-bound artifacts and run at least twelve balanced
      native i686 phoneME Game of Life pairs plus Waternet, Rubido, and Untangle
      controls only if the primary gate passes
- [x] 26.5 Record every raw pair and artifact/topology delta, then remove a
      rejected candidate or fully verify and commit an accepted candidate before
      selecting the next ledger entry

## 27. NJIT-024 compact i32.load8_u direct stack/address path

- [x] 27.1 Record the exact compact coverage lower bound, distinction from
      rejected helper/fusion work, isolated mechanism, baseline identities,
      semantic risks, commands, and native acceptance thresholds
- [x] 27.2 Prototype only the existing compact `i32.load8_u` stack and
      width-one address path, inspect its bytecode, then remove it when the owner
      requests a return to stable before verification
- [x] 27.3 Record the focused-fixture validation failure and explicitly make
      no correctness claim because the full gate was stopped before it began
- [x] 27.4 Skip clean native phoneME timing and route controls after the owner
      stops the experiment; do not infer performance from bytecode
- [x] 27.5 Record the candidate as inconclusive, remove all implementation and
      test changes, and restore the stable source tree

## 28. NJIT-027 generic local.set and local.tee direct stack path

- [x] 28.1 Record exact corpus coverage, the isolated helper-call mechanism,
      retained NJIT-025 baseline hashes, semantic boundary, and acceptance gates
- [x] 28.2 Implement only the outer generic `local.set` and `local.tee`
      stack-height guards and `values[]` access, leaving `local.get` unchanged
- [x] 28.3 Pass focused underflow, full-state, Java 1.3, CLDC, target-47,
      preverification, release, bytecode, and heap gates
- [x] 28.4 Produce a hash-bound counterless artifact and run at least twelve
      balanced native i686 phoneME pairs on Rubido and Game of Life; run Waternet
      and Untangle controls only when both primary gates pass
- [x] 28.5 Record raw pairs and the verdict, then remove a rejected candidate
      or retain a fully verified accepted candidate

## 29. NJIT-028 generic local.get exact direct push

- [x] 29.1 Record exact corpus coverage, the isolated helper-frame mechanism,
      exception and rollback semantics, retained baseline hashes, and gates
- [x] 29.2 Implement only `local.get` with the exact existing `push()`
      `try/catch`, capacity trap, and `valueTop` rollback
- [x] 29.3 Pass focused stack overflow, full-state, Java 1.3, CLDC, target-47,
      preverification, release, bytecode, and heap gates
- [x] 29.4 Produce a hash-bound counterless artifact and run twelve balanced
      native i686 phoneME pairs on Rubido and Game of Life; run controls only if
      both primary gates pass
- [x] 29.5 Record raw pairs and verdict, then remove a rejected candidate or
      retain a fully verified accepted candidate

## 30. NJIT-029 packed horizontal framebuffer spans

- [x] 30.1 Build phoneME's statistical method profiler out of tree, record
      method samples for Waternet, Rubido, Untangle, and Game of Life, and bind
      the selection evidence to VM and report hashes
- [x] 30.2 Record the scalar per-pixel mechanism, packed-span replacement,
      retained NJIT-025 baseline identities, exact boundary semantics, and native
      acceptance gates before implementation
- [x] 30.3 Implement only `drawHorizontal` as leading partial, complete packed
      bytes, and trailing partial writes, with no allocation or caller changes
- [x] 30.4 Add exhaustive color/alignment differential coverage and pass the
      full-state, Java 1.3, CLDC, target-47, preverification, release, bytecode,
      and heap gates
- [x] 30.5 Produce a hash-bound counterless artifact and run at least twelve
      balanced native i686 phoneME pairs on Waternet and Untangle, with Rubido and
      Game of Life controls
- [x] 30.6 Record raw pairs and the verdict, then remove a rejected candidate
      or retain a fully verified accepted candidate

## 31. NJIT-030 inline generic control-frame entry

- [x] 31.1 Bind the candidate to the post-NJIT-029 native method-profile
      hashes and retained exact `block`/`loop`/`if` execution counts
- [x] 31.2 Record the exact two-site inlining boundary, control-stack
      semantics, excluded changes, and native acceptance gates before editing
- [x] 31.3 Inline only `enterControl` into the shared block/loop and if cases,
      remove the dead helper, and prove both invokes are absent
- [x] 31.4 Pass static-control, seven-workload full-state, Java 1.3, CLDC,
      target-47, preverification, trap, budget, cache, release, and size gates
- [x] 31.5 Produce hash-bound counterless artifacts and run twelve balanced
      native i686 Rubido pairs plus the required no-regression controls
- [x] 31.6 Record raw pairs and the verdict, then remove a rejected candidate
      or retain a fully verified accepted candidate

## 32. NJIT-031 zero- and one-value control-transfer fast paths

- [x] 32.1 Bind the candidate to the post-NJIT-030 method-profile hashes,
      retained control-flow counts, stable source, and exact phoneME toolchain
- [x] 32.2 Record the validation order, zero/scalar paths, unchanged generic
      fallback, excluded changes, and native acceptance gates before editing
- [x] 32.3 Implement only the zero- and one-value returns in `transfer`, with
      the original validation and generic multi-value loops retained
- [x] 32.4 Pass focused control-transfer, seven-workload full-state, Java 1.3,
      CLDC, target-47, preverification, trap, budget, cache, release, and size
      gates
- [x] 32.5 Produce hash-bound counterless artifacts and run twelve balanced
      native i686 Rubido and Game of Life pairs plus the required Waternet control
- [x] 32.6 Record raw pairs and the verdict, then remove a rejected candidate
      or retain a fully verified accepted candidate

## 33. NJIT-032 compact i32.load8_u direct stack/address path

- [x] 33.1 Record why the corrected NJIT-024 fixture and post-NJIT-031
      profiler evidence make this a materially new experiment rather than an
      untracked rerun
- [x] 33.2 Bind the candidate to retained source/classes, exact focused
      fixtures, isolated compact-only semantics, excluded changes, and native
      acceptance gates before editing
- [x] 33.3 Implement only the compact `i32.load8_u` direct stack and
      width-one address path, leaving generic execution and W4IR unchanged
- [x] 33.4 Pass focused success/budget/trap snapshots, seven-workload
      full-state, Java 1.3, CLDC, target-47, preverification, release, cache,
      bytecode, and heap gates
- [x] 33.5 Produce a hash-bound counterless artifact and run at least twelve
      balanced native i686 phoneME Game of Life pairs plus the required controls
      if the primary gate passes
- [x] 33.6 Record raw pairs and verdict, then remove a rejected candidate or
      retain a fully verified accepted candidate

## 34. NJIT-033 compact i32.eqz top-of-stack overwrite

- [x] 34.1 Bind E15 to exact `i32.eqz` route counts, compact-tier activity,
      retained source/classes, isolated semantics, excluded changes, and native
      acceptance gates
- [x] 34.2 Implement only compact opcode `0x45` as an underflow-checked
      overwrite of `values[valueTop - 1]`, leaving generic execution unchanged
- [x] 34.3 Pass focused value/budget snapshots, seven-workload full-state,
      Java 1.3, CLDC, target-47, preverification, release, cache, bytecode, and
      heap gates
- [x] 34.4 Produce a hash-bound counterless artifact and run at least twelve
      balanced native i686 phoneME Rubido pairs plus required controls if the
      primary gate passes
- [x] 34.5 Record raw pairs and verdict, then remove a rejected candidate or
      retain a fully verified accepted candidate

## 35. NJIT-034 compact i32.load caller-inline address guard

- [x] 35.1 Bind the NJIT-005 caller-inline reconsideration to exact current
      Rubido opcode/tier counts, post-NJIT-031 phoneME method samples, retained
      hashes, isolated semantics, exclusions, and native acceptance gates
- [x] 35.2 Inline only the folded effective-address guard in compact opcode
      `0x28`, retaining `loadI32` and every generic/fused/helper path
- [x] 35.3 Pass focused memory/budget differentials, seven-workload full-state,
      Java 1.3, CLDC, target-47, preverification, release, cache, bytecode, and
      heap gates
- [x] 35.4 Produce a hash-bound counterless artifact and run at least twelve
      balanced native i686 phoneME Rubido pairs plus required controls if the
      primary gate passes
- [x] 35.5 Record raw pairs and verdict, then remove a rejected candidate or
      retain a fully verified accepted candidate

## 36. NJIT-035 inline generic control-frame exit

- [x] 36.1 Bind the three `leaveControl` callers to current exact control-opcode
      counts, post-NJIT-031 phoneME method samples, retained hashes, exclusions,
      and native acceptance gates
- [x] 36.2 Inline the helper body at false-if, else, and non-terminal-end
      dispatch sites without changing `transfer` or terminal function return
- [x] 36.3 Pass focused control/budget differentials, seven-workload
      full-state, Java 1.3, CLDC, target-47, preverification, release, cache,
      bytecode, and counterless gates
- [x] 36.4 Produce a hash-bound counterless artifact and run at least twelve
      balanced native i686 phoneME Rubido and Game of Life pairs plus required
      controls if the primary gates pass
- [x] 36.5 Record raw pairs and verdict, then remove a rejected candidate or
      retain a fully verified accepted candidate

## 37. NJIT-036 direct generic branch-condition pop

- [x] 37.1 Bind `if` and `br_if` to the post-NJIT-035 phoneME `pop()` sample,
      exact current route counts, retained hashes, semantic ordering, exclusions,
      and native acceptance gates
- [x] 37.2 Inline only the `popI32()` condition reads in generic `if` and
      `br_if`, preserving every branch and descriptor path
- [x] 37.3 Pass focused branch/budget differentials, seven-workload
      full-state, Java 1.3, CLDC, target-47, preverification, release, cache,
      bytecode, and counterless gates
- [x] 37.4 Produce a hash-bound counterless artifact and run at least twelve
      balanced native i686 phoneME Rubido and Game of Life pairs plus required
      controls if the primary gates pass
- [x] 37.5 Record raw pairs and verdict, then remove a rejected candidate or
      retain a fully verified accepted candidate

## 38. NJIT-037 direct compact `w4ir.local_local`

- [x] 38.1 Bind the candidate to post-NJIT-035 phoneME samples, exact current
      fused-opcode counts, retained hashes, partial-stack trap semantics,
      exclusions, and native acceptance gates
- [x] 38.2 Move only compact `w4ir.local_local` into the main compact switch
      with two sequential checked writes and remove its duplicate helper case
- [x] 38.3 Pass focused stack-capacity differentials, seven-workload
      full-state, Java 1.3, CLDC, target-47, preverification, release, cache,
      bytecode, and counterless gates
- [x] 38.4 Produce a hash-bound counterless artifact and run at least twelve
      balanced native i686 phoneME Rubido and Game of Life pairs plus required
      controls if the primary gates pass
- [x] 38.5 Record raw pairs and verdict, then remove a rejected candidate or
      retain a fully verified accepted candidate

## 39. NJIT-038 compact `w4ir.local_set_get` top replacement

- [x] 39.1 Bind the candidate to post-NJIT-035 phoneME samples, exact current
      fused-opcode counts, retained hashes, alias and underflow semantics,
      exclusions, and native acceptance gates
- [x] 39.2 Move only compact `w4ir.local_set_get` into the main compact switch
      as an ordered top-slot replacement and remove its duplicate helper case
- [x] 39.3 Pass focused distinct-local/alias/underflow differentials,
      seven-workload full-state, Java 1.3, CLDC, target-47, preverification,
      release, cache, bytecode, and counterless gates
- [x] 39.4 Produce a hash-bound counterless artifact and run at least twelve
      balanced native i686 phoneME Rubido pairs plus required controls if the
      primary gate passes
- [x] 39.5 Record raw pairs and verdict, then remove a rejected candidate or
      retain a fully verified accepted candidate

## 40. NJIT-039 direct compact `w4ir.local_i32_const_add`

- [x] 40.1 Bind the candidate to post-NJIT-035 phoneME samples, exact current
      fused-opcode counts, retained hashes, exclusions, and native acceptance gates
- [x] 40.2 Move only compact `w4ir.local_i32_const_add` into the main compact
      switch as one guarded stack write and remove its duplicate helper case
- [x] 40.3 Pass focused capacity/signed/wraparound differentials,
      seven-workload full-state, Java 1.3, CLDC, target-47, preverification,
      release, cache, bytecode, and counterless gates
- [x] 40.4 Produce a hash-bound counterless artifact and run at least twelve
      balanced native i686 phoneME Rubido pairs plus required controls if the
      primary gate passes
- [x] 40.5 Record raw pairs and verdict, then remove a rejected candidate or
      retain a fully verified accepted candidate

## 41. NJIT-040 direct generic `i32.add`

- [x] 41.1 Bind the candidate to the accepted generic direct-stack precedent,
      exact current opcode counts, retained hashes, exclusions, and native
      acceptance gates
- [x] 41.2 Replace only generic opcode `0x6a` with an underflow-checked
      in-place `values[]` addition, leaving compact and fused execution unchanged
- [x] 41.3 Pass focused underflow/signed/wraparound differentials,
      seven-workload full-state, Java 1.3, CLDC, target-47, preverification,
      release, cache, bytecode, heap, and counterless gates
- [x] 41.4 Produce a hash-bound counterless artifact and run at least twelve
      balanced native i686 phoneME Game of Life pairs plus required controls if
      the primary gate passes
- [x] 41.5 Record raw pairs and verdict, then remove a rejected candidate or
      retain a fully verified accepted candidate

## 42. NJIT-041 frame-neutral terminal `br_if` compact regions

- [x] 42.1 Bind the candidate to the earlier exact branch-region prototype,
      accepted pc-indexed direct metadata, current stable hashes, frame-growth
      risk, exclusions, and native acceptance gates
- [x] 42.2 Encode eligible terminal `br_if` regions in the existing compact-end
      table and execute the branch in the outer frame while leaving ordinary
      compact regions and `executeCompactBlock` unchanged
- [x] 42.3 Pass focused branch/budget differentials, seven-workload full-state,
      Java 1.3, CLDC, target-47, preverification, release, cache, bytecode, heap,
      and counterless gates
- [x] 42.4 Produce a hash-bound counterless artifact and run at least twelve
      balanced native i686 phoneME Rubido pairs plus required controls if the
      primary gate passes
- [x] 42.5 Record raw pairs and verdict, then remove a rejected candidate or
      retain a fully verified accepted candidate

## 43. NJIT-042 direct generic i32 ALU batch

- [x] 43.1 Bind the NJIT-040 reconsideration to exact current ALU coverage,
      retained hashes, exclusions, and native acceptance gates
- [x] 43.2 Replace only generic `i32.add`, `i32.mul`, `i32.and`, `i32.xor`,
      `i32.shr_s`, and `i32.shr_u` with underflow-checked in-place `values[]`
      operations
- [x] 43.3 Pass focused operation/boundary/underflow differentials,
      seven-workload full-state, Java 1.3, CLDC, target-47, preverification,
      release, cache, bytecode, heap, and counterless gates
- [x] 43.4 Produce a hash-bound counterless artifact and run at least twelve
      balanced native i686 phoneME Game of Life pairs plus required controls if
      the primary gate passes
- [x] 43.5 Record raw pairs and verdict, then remove a rejected candidate or
      retain a fully verified accepted candidate

## 44. NJIT-043 ship the accepted counterless production configuration

- [x] 44.1 Confirm the current release build still selects the diagnostic
      interpreter config and bind revalidation to retained NJIT-035, historical
      clean phoneME evidence, exact exclusions, and current acceptance gates
- [x] 44.2 Build same-source diagnostic and counterless artifacts and run at
      least twelve balanced native i686 phoneME Rubido pairs plus Waternet and
      Untangle controls
- [x] 44.3 If the timing gate passes, select the existing counterless config
      only for distributable JAR compilation and reject diagnostic counter writes
      in release-JAR verification
- [x] 44.4 Pass the complete Java 1.3, CLDC, target-47, preverification,
      full-state, release, deterministic-build, JAR-content, bytecode, and
      counterless gates
- [x] 44.5 Record raw pairs, release hashes and verdict, then retain the
      production mapping only if every gate passes

## 45. NJIT-044 remove the cartridge-specific Plasma replacement

- [x] 45.1 Bind the exact fingerprinted selection path, universal product
      contract, retained hashes, exclusions, and no-regression gate
- [x] 45.2 Remove `PlasmaTriFast`, its production and differential call paths,
      and the shortcut-specific smoke while keeping generic probe APIs inert
- [x] 45.3 Reject the shortcut class in distributable JAR verification and
      pass seven-workload full-state, Java 1.3, CLDC, target-47, preverification,
      release, cache, bytecode, and counterless gates
- [x] 45.4 Produce a hash-bound counterless candidate and run at least twelve
      balanced native i686 phoneME Rubido no-regression pairs
- [x] 45.5 Record raw pairs, artifact hashes and verdict, then retain the
      contract-clean baseline only if every gate passes

## 46. NJIT-045 sparse exact instruction-budget recovery

- [x] 46.1 Bind the confirmed fused-budget defect, rejected full-copy
      prototype, sparse recipe layout, format bump, exclusions, footprint metrics,
      exactness matrix, and native no-regression gates
- [x] 46.2 Preserve consumed original W4IR slots with a build-only bitmap and
      emit sorted four-int recovery recipes for every rewritten fusion root
- [x] 46.3 Persist and validate recovery recipes through W4IR function metadata
      and RMS build/hit while atomically rebuilding older format records
- [x] 46.4 Switch only the near-budget invocation tail to original logical
      instructions and make batching loops and counted traces yield before the
      exact window
- [x] 46.5 Pass focused fusion-boundary, cache, call, control, batching, and
      trace exactness coverage plus the complete Java 1.3, CLDC, target-47,
      preverification, release, full-state, bytecode, and counterless gates; record
      that the exhaustive generated matrix was not justified after primary rejection
- [x] 46.6 Measure retained heap/RMS payload and run twelve balanced native
      i686 phoneME Game of Life pairs; stop Rubido and Plasma controls after the
      primary route fails the mandatory no-regression floor
- [x] 46.7 Record raw receipts, hashes, footprint, verdict, and either retain
      the exact sparse implementation or remove it completely

## 47. NJIT-046 counterless build without opcode profiling support

- [x] 47.1 Bind the candidate to instrumented phoneME bytecode-type and
      method/BCI profiles, retained baseline hashes, exact exclusions, and native
      acceptance gates
- [x] 47.2 Add a compile-time profiling-support constant to every interpreter
      config and remove profiler-dependent runtime branches only from the existing
      timed/counterless production artifact
- [x] 47.3 Prove with `javap` that counterless hot paths contain no profiling
      field reads or calls while the diagnostic corpus profiler remains functional
- [x] 47.4 Pass the complete Java 1.3, CLDC, target-47, preverification,
      release, full-state, cache, bytecode, heap, and counterless gates
- [x] 47.5 Produce hash-bound artifacts and run at least twelve balanced native
      i686 phoneME Rubido pairs plus Waternet, Untangle, and Game of Life controls
      when the primary gate passes
- [x] 47.6 Record raw profiles, pairs, hashes, footprint, and verdict, then
      remove a rejected candidate or retain a fully verified accepted candidate

## 48. NJIT-047 compact executor value-array local

- [x] 48.1 Bind the candidate to the accepted NJIT-046 artifact, method/BCI
      profile, target-47 field-access shape, exact exclusions, and native gates
- [x] 48.2 Cache only the immutable `values` array reference in
      `executeCompactBlock` and leave `valueTop` plus helper-backed paths unchanged
- [x] 48.3 Prove the compact executor replaces repeated `getfield values`
      sites with one local alias without adding invokes or breaking bytecode gates
- [x] 48.4 Pass the complete Java 1.3, CLDC, target-47, preverification,
      release, full-state, cache, bytecode, heap, and counterless gates
- [x] 48.5 Produce hash-bound artifacts and run at least twelve balanced native
      i686 phoneME Game of Life pairs plus Rubido, Waternet, and Untangle controls
      if the primary gate passes
- [x] 48.6 Record raw pairs, hashes, footprint, and verdict, then remove a
      rejected candidate or retain a fully verified accepted candidate

## 49. NJIT-048 compact-block budget-check elision

- [x] 49.1 Bind the candidate to the accepted NJIT-046 artifact, fresh
      method/BCI profile, prior NJIT-001 rejection, exact mechanism, exclusions,
      and native gates
- [x] 49.2 Prove whole-block budget admission in the outer executor and pass
      only that boolean into `executeCompactBlock`
- [x] 49.3 Elide only compact per-instruction limit comparisons for an admitted
      block while preserving every counter increment and trap-side-effect order
- [x] 49.4 Pass focused outer/compact budget sweeps plus the complete Java 1.3,
      CLDC, target-47, preverification, release, full-state, cache, bytecode,
      heap, and counterless gates
- [x] 49.5 Produce hash-bound artifacts and run at least twelve balanced native
      i686 phoneME Game of Life pairs plus required controls if the primary gate
      passes
- [x] 49.6 Record raw pairs, hashes, footprint, and verdict, then remove a
      rejected candidate or retain a fully verified accepted candidate

## 50. NJIT-049 separate admitted compact executor

- [x] 50.1 Bind the candidate to accepted NJIT-046, the NJIT-001 and NJIT-048
      measurements, exact split-method mechanism, exclusions, and native gates
- [x] 50.2 Route only fully admitted counterless compact blocks to a separate
      unchecked executor while leaving the checked executor unchanged
- [x] 50.3 Mirror compact handlers with a local logical counter and exact
      normal/exceptional publication but no inner budget comparison or selection
      branch
- [x] 50.4 Pass focused budget/trap sweeps plus the complete Java 1.3, CLDC,
      target-47, preverification, release, full-state, cache, bytecode, heap, and
      counterless gates
- [x] 50.5 Produce hash-bound artifacts and run at least twelve balanced native
      i686 phoneME Game of Life pairs plus required controls if the primary passes
- [x] 50.6 Record raw pairs, hashes, footprint, and verdict, then remove a
      rejected candidate or retain a fully verified accepted candidate

## 51. NJIT-050 scalar defined-function result arity

- [x] 51.1 Bind the candidate to accepted NJIT-046, exact call density,
      target-47 result-arity loads, prior NJIT-004/NJIT-020 distinctions,
      exclusions, baseline hashes, and native gates
- [x] 51.2 Replace only the private outer-executor `FuncType` parameter with
      its scalar result count while preserving parameter slots and all semantics
- [x] 51.3 Prove target-47 removes every `FuncType.results + arraylength` pair
      from counterless `execute` without adding fields, arrays, formats, or heap
- [x] 51.4 Pass the complete Java 1.3, CLDC, target-47, preverification,
      release, full-state, cache, budget/trap, bytecode, and counterless gates
- [x] 51.5 Produce hash-bound artifacts and run sixteen balanced native i686
      phoneME Game of Life pairs plus required controls if the primary passes
- [x] 51.6 Record raw pairs, hashes, footprint, and verdict, then remove a
      rejected candidate or retain a fully verified accepted candidate

## 52. NJIT-051 inline ordinary direct-defined-call frame setup

- [x] 52.1 Bind the candidate to accepted NJIT-046, exact direct-call density,
      target-47 frame shape, prior NJIT-006/020/050 distinctions, baseline hashes,
      exclusions, and native gates
- [x] 52.2 Inline only the ordinary direct-defined-call setup in a scoped
      generic `call` case while retaining `callFunction` for every fallback path
- [x] 52.3 Prove ordinary direct calls remove the middle Java frame without a
      W4IR/RMS or heap change and record `execute` code and local-slot growth
- [x] 52.4 Pass focused call/trap/budget tests plus the complete Java 1.3,
      CLDC, target-47, preverification, release, cache, full-state, and
      counterless gates
- [x] 52.5 Produce hash-bound artifacts and start the predeclared sixteen-pair
      native i686 phoneME Game of Life run; record the owner-requested interruption
      during sample zero before a complete pair existed
- [x] 52.6 Record the partial evidence, hashes, footprint, and inconclusive
      verdict, then remove the candidate and restore accepted NJIT-046 exactly

## 53. Final accepted-set A/B against main

- [x] 53.1 Build a hash-bound regular-production baseline from
      `main@8e850656` and bind the restored NJIT-046 production artifact
- [x] 53.2 Prove the two route harness and framebuffer-oracle classes are
      byte-identical and record VM, CLDC, source, and artifact hashes
- [x] 53.3 Run sixteen balanced native i686 phoneME pairs on Waternet, Rubido,
      Untangle, and Game of Life without competing benchmark processes
- [x] 53.4 Require exact checkpoints, logical instructions, fast-path counts,
      and direct-branch metadata while treating removed diagnostic telemetry as an
      expected production difference
- [x] 53.5 Record absolute medians, direct paired effects, throughput ratios,
      raw receipt hashes, and the headless-runtime limitation for release notes
