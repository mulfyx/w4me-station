## 1. Descriptor fixtures and baseline

- [x] 1.1 Add focused control-flow fixtures for block results, loop parameters, `if`/`else`, function-label branches, nested depths, and unreachable-polymorphic code
- [x] 1.2 Add focused `br_table` fixtures covering repeated targets, default selection, loop targets, block targets, function return, and the maximum supported value arity
- [x] 1.3 Record legacy target PC, value-stack height, transferred values, control depth, result, trap, and logical instruction count for every fixture
- [x] 1.4 Confirm the unchanged baseline passes host tests, exact corpus replay, full-state differential, CLDC compilation, preverification, and native phoneME route verification

## 2. Static descriptor generation

- [x] 2.1 Annotate validated structured-control openers with their static value-stack height without changing execution
- [x] 2.2 Add a bounded flat primitive descriptor representation for target PC, destination height, arity, active control depth, and loop or function-return flags
- [x] 2.3 Resolve `br` and `br_if` depths to descriptors after matching `else` and `end` positions are known while retaining the original depth operand
- [x] 2.4 Build a parallel descriptor table for every `br_table` while retaining the validated legacy depth table
- [x] 2.5 Validate descriptor field bounds and reject any internally inconsistent decoded function before it can execute
- [x] 2.6 Add test-only descriptor inspection and exact expected-descriptor assertions for all focused fixtures

## 3. W4IR cache persistence

- [x] 3.1 Extend `W4IrFunction` and `W4IrStore` with primitive descriptor records and branch descriptor tables
- [x] 3.2 Persist and restore descriptors in `RmsW4IrStore` with bounded lengths and trailing-data validation
- [x] 3.3 Bump `W4IR_FORMAT_VERSION` and atomically reject and rebuild older cache records
- [x] 3.4 Verify descriptor identity across resident decode, RMS write, RMS reopen, paged execution, and resident promotion
- [x] 3.5 Add malformed-cache tests for truncated records, invalid descriptor indices, out-of-range targets, invalid heights, invalid arities, and invalid control depths

## 3B. W4IR function-metadata hardening

- [x] 3.6 Bump W4IR to v17 and checksum each complete function metadata record before parsing persisted counts
- [x] 3.7 Bound persisted declared-local, instruction, intrinsic, table, descriptor, and page metadata before allocation and validate cached f32 intrinsic signatures
- [x] 3.8 Add adversarial RMS tests for checksum corruption, arithmetic-overflow instruction counts, invalid local counts, and invalid intrinsic identifiers
- [x] 3.9 Verify automatic cache rejection/rebuild, focused f32-state canonicalization, Java 1.3/CLDC output, full-state routes, and i686/ARM64 deterministic parity

## 4. Legacy-shadow differential

- [x] 4.1 Add a verification-only descriptor mode that is absent from production timing artifacts
- [x] 4.2 Compare descriptor and legacy outcomes for every taken `br`, including loop, block, `if`, and function targets
- [x] 4.3 Compare taken and untaken `br_if` outcomes without perturbing fallthrough state
- [x] 4.4 Compare every selected `br_table` edge, including default and repeated-target entries
- [x] 4.5 Verify zero-, one-, and multi-value transfer order plus active control depth after every edge
- [x] 4.6 Run the focused suite, all WebAssembly tests, exact corpus replay, full-state differential, and i686/ARM64 phoneME correctness parity with shadow verification enabled
- [x] 4.7 Verify with `javap` that the production artifact contains no shadow copy, comparison, allocation, counter, or runtime flag branch in the hot path

## 5. Authoritative generic descriptor execution

- [x] 5.1 Implement an isolated descriptor-backed `br` candidate while retaining a build-time legacy fallback
- [x] 5.2 Add specialized arity-zero and arity-one transfers plus one bounded overlap-safe multi-value transfer
- [x] 5.3 Pass exact, budget-boundary, cache, target-artifact, and native phoneME paired gates for descriptor `br`; retain it only on a resolved positive result
- [x] 5.4 Implement and gate descriptor-backed `br_if` independently from `br`
- [x] 5.5 Implement and gate descriptor-backed `br_table` independently from `br_if`
- [x] 5.6 Remove each rejected candidate before measuring the next phase and retain its evidence with an explicit rejection reason

## 5A. Constant-time direct-branch retry

- [x] 5.7 Add compile-time legacy, inline-only, and pc-indexed direct-branch artifacts without changing W4IR v16 or production execution
- [x] 5.8 Derive bounded parallel branch arrays at function bind and inline exact arity-zero/one transfers for ordinary `br` and taken `br_if`
- [x] 5.9 Pass focused descriptors, malformed cache, budget boundaries, full-state corpus, Java 1.3, preverification, dense-switch, method-size, and constrained-heap gates
- [x] 5.10 Measure at least twelve balanced native i686 phoneME pairs on Rubido and no-regression controls on Waternet, Untangle, and generic Plasma; retain only a resolved improvement

## 6. Branch-capable compact regions

Not implemented: the later constant-time retry retains only ordinary
arity-zero/one `br` and taken `br_if`. It establishes a profitable generic
branch primitive but does not itself implement compact-region exits, fused
branches, `br_table`, or function return. Those remain separately gated work
rather than being folded into the retained patch.

- [ ] 6.1 Extend compact region metadata with canonical external entry points and an explicit next-PC or function-return result
- [ ] 6.2 Prototype conditional fallthrough regions with one descriptor-backed `br_if`, flushing cached stack state only on the taken edge
- [ ] 6.3 Add exact logical-instruction and between-instruction budget tests for taken, untaken, trapping, and returning region exits
- [ ] 6.4 Measure conditional fallthrough regions on affected native phoneME routes without changing compact activation
- [ ] 6.5 Prototype loop backedges only if the conditional phase is exact and either accepted or provides evidence that isolates the remaining boundary cost
- [ ] 6.6 Retain ordinary dense-dispatch entry at every external branch target and verify no edge enters the middle of a region

## 7. Dynamic control-stack retirement

Not implemented: no descriptor-complete authoritative executor was retained.
Removing the runtime control stack would therefore violate this phase's stated
precondition and rollback contract.

- [ ] 7.1 Prove descriptor coverage for every supported `block`, `loop`, `if`/`else`, `end`, `br`, `br_if`, `br_table`, return, block parameter, result, multi-value, recursion, and unreachable-polymorphic case
- [ ] 7.2 Stop maintaining runtime control frames in an isolated descriptor-complete candidate while retaining the legacy implementation as a build-time fallback
- [ ] 7.3 Run complete exactness, cache, constrained-heap, target-artifact, KEmulator integration, and native phoneME paired gates for the no-runtime-control-frame candidate
- [ ] 7.4 Remove `controlKind`, `controlStart`, `controlEnd`, `controlBase`, `controlParameters`, and `controlResults` only after task 7.3 passes
- [ ] 7.5 Verify the final executor remains a dense `tableswitch`, stays within method-size limits, and produces Java 1.3 preverified CLDC-clean classes

## 8. Acceptance, evidence, and explicit deferrals

- [ ] 8.1 Store clean same-build receipts, artifact hashes, route hashes, deterministic counters, memory overhead, and paired phoneME statistics for every retained or rejected execution phase
- [x] 8.2 Accept speed claims only from balanced native i686 phoneME pairs with exact oracles and resolved effects; keep KEmulator, HotSpot, and QEMU timing diagnostic-only
- [x] 8.3 Update interpreter architecture and W4IR format documentation with the final retained descriptor stages and rollback behavior
- [x] 8.4 Explicitly defer 32-bit cell stacks, split value storage, register-slot W4IR, and cartridge-specific Wasm-to-Java AOT to separate OpenSpec changes
- [x] 8.5 Run the complete repository verification matrix and leave the tree at the last stable retained phase

Task 8.1 remains open only for the historical rejected execution phases, whose
removed source forms cannot be reproduced from the retained tree. The retained
constant-time phase now has clean same-build receipts for Rubido, Waternet,
Untangle, generic Plasma, and the inline-only control, with both artifacts,
the VM, routes, inputs, oracles, counters, and memory payload bound by hash.
