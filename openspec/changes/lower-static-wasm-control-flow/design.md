## Context

`WasmInterpreter` currently keeps the active structured-control state in six
parallel primitive arrays. Every `block`, `loop`, and `if` pushes a runtime
frame; every `end` pops it; and `br`, `br_if`, and `br_table` resolve a relative
depth through that state before transferring result values. The compact
executor only accepts straight-line regions and returns to the outer executor
at every control-flow boundary.

The current path is validated and stable, but it repeats work which the module
validator already performs. During decoding the validator knows the active
control frame, its kind, its value-stack height, and its parameter or result
arity. The decoder also resolves matching `else` and `end` instruction
positions. That information is enough to describe a branch edge as a direct
target plus a statically known stack transfer.

The implementation is constrained by Java 1.3 classfiles, CLDC 1.1 APIs,
preverification, a 32-bit phoneME C interpreter without JIT, a bounded heap,
RMS-paged W4IR, exact instruction-budget trap timing, and a bytecode-size gate
on the main executor. Java objects, reflection, generated classes, method-per-op
dispatch, and native handler pointers are not viable building blocks.

### Research basis

The design follows mechanisms independently present in several runtimes:

- [WAMR fast interpreter](https://github.com/bytecodealliance/wasm-micro-runtime/blob/main/core/iwasm/interpreter/wasm_loader.c)
  emits branch metadata containing arity, cell counts, source and destination
  frame offsets, and a patched target before execution.
- [wasmi](https://github.com/wasmi-labs/wasmi/tree/main/crates/wasmi/src/engine)
  translates the Wasm value stack to registers and patches labels to branch
  offsets per function.
- [wasm3](https://github.com/wasm3/wasm3/blob/main/docs/Interpreter.md)
  translates Wasm to slot-oriented metacode with patched targets, but its
  function-pointer tail-call dispatch is not transferable to Java ME.
- [Endive](https://github.com/bytecodealliance/endive/blob/main/wasm/src/main/java/run/endive/wasm/ControlTree.java)
  demonstrates the conservative Java staging point: direct branch labels can
  coexist with a runtime control stack.
- [wasm2j](https://github.com/eutro/wasm2j/blob/main/wasm2j-core/src/main/java/io/github/eutro/wasm2j/core/passes/convert/WasmToWir.java)
  distinguishes loop parameters from block results and lowers each branch edge
  to a value transfer and direct control-flow edge.
- [A fast in-place interpreter for WebAssembly](https://arxiv.org/abs/2205.01183)
  shows that the validator can emit the otherwise implicit control-flow and
  value-stack information into a compact constant-time side table.
- [Sun CVM/J2ME interpreter instruction-set enhancement](https://publications.scss.tcd.ie/tech-reports/reports.05/TCD-CS-2005-61.pdf)
  provides a target-class precedent for short superinstructions, stack-cache
  flushes, and preserving ordinary entry points at branch targets.

These sources establish the mechanism, not a performance result for W4ME. Each
retained phase still requires native phoneME evidence.

## Goals / Non-Goals

**Goals:**

- Resolve structured branch depths into primitive static descriptors during
  validation and W4IR construction.
- Introduce descriptors beside the current dynamic control stack so every phase
  has a compatibility oracle and a rollback path.
- Make `br`, `br_if`, and `br_table` independently testable and independently
  acceptable.
- Preserve exact value-stack effects, multi-value ordering, control depth,
  function returns, instruction accounting, and trap points.
- Permit branch-capable compact regions only after the generic descriptor path
  is exact and measured.
- Remove runtime control arrays only after no accepted execution path depends on
  them.
- Keep all hot-path data in primitive arrays with bounded sizes and a
  serializable numeric format.

**Non-Goals:**

- Runtime generation or loading of Java classes.
- Computed-goto, function-pointer, tail-call, object-per-instruction, or
  method-per-opcode dispatch.
- Changing instruction-budget checks from logical-instruction boundaries to
  region or backedge boundaries.
- Combining control-flow lowering with a new 32-bit value representation,
  register W4IR, renderer work, audio work, or user-facing features.
- Treating fewer dispatches, wider compact coverage, desktop HotSpot results, or
  QEMU timing as proof of a phoneME speedup.
- Removing the compatibility path before the final control-stack phase.

## Decisions

### 1. Use one immutable descriptor per branch edge

A descriptor contains only primitive integer fields:

```text
targetPc
destinationValueHeightRelativeToFunction
arity
activeControlDepthAfterTransfer
flags: LOOP_TARGET | FUNCTION_RETURN
```

`destinationValueHeightRelativeToFunction` identifies the value-stack base at
the target before the branch values are restored. `arity` is the loop parameter
count for loop targets and the result count for block, `if`, and function
targets. `activeControlDepthAfterTransfer` is relative to the current function,
so recursive and nested calls need no absolute patching.

Descriptors are stored in flat primitive arrays or packed fixed-width integer
records. The implementation MUST NOT allocate an instruction or descriptor
object per edge at execution time.

Alternative considered: store only an absolute target PC and continue resolving
stack transfer through the dynamic control arrays. This is a useful initial
compatibility step but does not define the final descriptor contract and leaves
most runtime work intact.

### 2. Preserve legacy branch operands during shadow verification

During the compatibility phase, `br` and `br_if` retain their original relative
depth and reference a descriptor separately. `br_table` retains its depth table
and gains a parallel descriptor table. A test-only verification mode compares:

- selected target PC;
- destination stack height;
- transferred arity and values;
- active control depth;
- function-return behavior.

The legacy `branch()` result remains authoritative in this phase. Descriptor
verification is disabled in production timing artifacts and adds no branch to
their hot path.

Alternative considered: overwrite the depth immediately and rely only on
end-state differential tests. This removes the most local oracle exactly when it
is most useful and makes malformed descriptor diagnosis harder.

### 3. Derive descriptors from the validation control state

The module decoder annotates each structured-control opener with its static
value-stack height. After matching `else` and `end` locations are known, a
bounded control-stack scan resolves every branch edge:

- loop target: first instruction inside the loop;
- block or `if` target: instruction after its matching `end`;
- function target: function return sentinel;
- destination height: target control height;
- arity: loop parameters or target results;
- control depth: remaining active frames after the transfer.

Unreachable-polymorphic validation states do not weaken the descriptor: their
branch labels still have a statically defined height and type vector.

Alternative considered: reconstruct descriptors lazily from W4IR on first
execution. That would load paged code, duplicate validator work, and make
startup behavior depend on the first route.

### 4. Stage execution by opcode family

The implementation order is:

1. emit and verify descriptors while all execution remains legacy;
2. make descriptor `br` authoritative;
3. make descriptor `br_if` authoritative;
4. make descriptor `br_table` authoritative;
5. allow descriptor-backed branches inside selected compact regions;
6. stop maintaining runtime control frames in descriptor-complete functions;
7. remove unused control arrays only after complete corpus and target evidence.

Each stage retains a build-time or source-level fallback until its acceptance
gate passes. A rejected stage is removed rather than hidden behind a permanent
runtime flag.

Alternative considered: lower all control constructs and delete the control
stack in one patch. This was rejected because a semantic or performance
regression could not be localized to descriptor generation, transfer, compact
execution, or frame removal.

### 5. Specialize transfers for arity zero and one

An arity-zero edge sets the value top directly to the destination base. An
arity-one edge saves one value, resets the top, and writes one value without a
temporary-array loop. Higher arities use one bounded, overlap-safe transfer
routine and retain source ordering.

The specialization is introduced only after descriptor equivalence is proven;
it is not mixed into the first shadow implementation.

Alternative considered: use `System.arraycopy` for every transfer. Source and
destination overlap and the need to reset stack height make it possible, but
the common zero- and one-result cases should not pay general setup cost on the
reference VM.

### 6. Keep branch targets as ordinary entry points

Every branch destination remains executable by the outer dense dispatcher.
Compact or fused fall-through code MUST NOT erase the ordinary instruction at a
possible external target. A taken branch flushes any cached stack state before
returning or continuing at the descriptor target.

This mirrors the conservative CVM technique and avoids entering the middle of a
region with a mismatched cached-stack state.

### 7. Make branch-capable compact execution a separate acceptance phase

The first branch-capable region supports only descriptor-backed control edges
whose logical instruction and trap accounting can remain exact. Its executor
returns an explicit next PC or function-return signal instead of assuming
`pc = compactEnd`.

The existing invocation threshold and profitability policy remain unchanged.
Prior phoneME evidence showed that simply increasing compact coverage can slow
the target VM, so descriptor support does not imply forced compact activation.

Alternative considered: enable all control opcodes in `isCompactOpcode` as soon
as descriptors exist. This conflates semantic readiness with profitability.

### 8. Version and persist the complete descriptor representation

RMS function metadata persists branch descriptors and descriptor tables beside
W4IR pages. Any layout change bumps `W4IR_FORMAT_VERSION`. Older records are
rejected as a unit and rebuilt from the cartridge; no partial migration is
attempted.

Resident decode, RMS write, RMS reopen, page load, and promotion MUST expose the
same descriptor contents.

W4IR v17 additionally appends a checksum to every function metadata record and
verifies it before parsing any persisted count. The loader applies the resident
decoder's bounds to declared locals, logical instructions, numeric intrinsic
identifiers, table lengths, descriptor payloads, and page counts before those
values can size an allocation. A checksum or bounds failure is reported through
the existing damaged-cache path, which discards the complete cache and rebuilds
it from the cartridge. Page records retain their existing independent
checksums. No cartridge, save, or user data format changes.

### 9. Keep dense Java dispatch and compilation constraints as invariants

Execution opcode IDs remain dense so `javac` emits `tableswitch`. New handlers
MUST preserve classfile major version 47, CLDC preverification, CLDC API lint,
and the configured bytecode-size limit for `execute`.

Computed goto and direct threading from C runtimes are represented by direct
numeric target PCs inside one Java executor, not by Java method calls.

### 10. Treat 32-bit cell or register-slot W4IR as an independent follow-up

WAMR, wasm3, wasmi, and Pulley all support moving beyond a universal
stack-oriented `long[]`. A J2ME candidate may use one `int[]` word frame where
i32/f32 occupy one cell and i64/f64 occupy two, or a register-slot W4IR with
explicit source and destination slots.

This design records that structural reserve but does not accept or implement it
in the descriptor phases. It needs its own memory model, cache format,
differential suite, and phoneME A/B.

### 11. Revisit direct branches without per-edge binary search

The rejected generic descriptor candidates resolved each ordinary `br` or
taken `br_if` PC through `branchDescriptorIndexAt`, whose binary search and
Java call cost can exceed the legacy dynamic-control lookup on native phoneME.
That result rejects the searched lookup shape, not constant-time descriptor
execution.

A new isolated experiment derives one `pc -> site` array plus parallel target,
height, arity, and control-depth arrays when a function is bound. The persisted
W4IR v16 records, fingerprint, and RMS format remain unchanged. The common
arity-zero and arity-one transfer is inlined in the ordinary `br` and taken
`br_if` handlers; function return, higher arity, malformed metadata, fused
branches, and `br_table` retain the legacy fallback during this experiment.

An independent inline-only artifact uses the same common transfer
specialization while resolving targets from the legacy control arrays. Native
phoneME compares legacy versus inline-only and inline-only versus direct-index
artifacts so the data-layout effect is not conflated with removing Java helper
calls. Additional derived heap is reported per cartridge and checked under
constrained heaps before retention.

## Measured outcome

Descriptor generation, W4IR v16 persistence, malformed-cache rejection, and
the compile-time shadow differential are retained. The first three descriptor
execution candidates remain rejected because their per-edge binary search was
slower on native i686 phoneME.

| Candidate             | Waternet |  Rubido | Untangle | Decision |
| --------------------- | -------: | ------: | -------: | -------- |
| descriptor `br`       |  -0.890% | -1.807% |  -0.174% | rejected |
| descriptor `br_if`    |  -1.207% | -0.504% |  +0.388% | rejected |
| descriptor `br_table` |  -0.217% | -0.323% |  -0.225% | rejected |

Each result is the median paired effect from eight balanced pairs. All browser
route checkpoints remained exact. The receipts are classified as exploratory
because the requested no-commit workflow leaves the source tree dirty; this
prevents accepting a speed claim, but it does not rescue candidates with
resolved regressions or no positive median effect.

Each rejected execution candidate, its build configuration, and its hot-path
branch were removed before the next family was measured. Raw laboratory
receipts are intentionally excluded from the public source tree; the measured
results and rejection decisions remain recorded in this design.

The constant-time retry removes that binary search for ordinary `br` and taken
`br_if`. It was introduced without changing W4IR v16 and derives one `pc -> site` table plus four
parallel metadata tables at bind time. The common arity-zero and arity-one path
is authoritative; function returns, higher arities, `br_table`, and fused
branches still fall back to the dynamic control stack.

| Constant-time retry                  | Paired result | Wins/losses | Decision                |
| ------------------------------------ | ------------: | ----------: | ----------------------- |
| Rubido, direct versus legacy         |       +2.011% |        12/0 | retain                  |
| Waternet, direct versus legacy       |       +0.640% |         7/1 | no regression           |
| Untangle, direct versus legacy       |       +0.501% |      12/3/1 | no regression           |
| generic Plasma, direct versus legacy |       +0.587% |         6/2 | no regression           |
| Rubido, direct versus inline-only    |       +1.132% |         8/0 | direct layout confirmed |

All Waternet, Rubido, and Untangle browser checkpoints and deterministic
counters remained exact. The full host state matrix, native i686 route gate,
and AArch64 ISA gate passed. The production JAR remains Java 1.3 preverified
with a dense `tableswitch`; `execute()` is 7039 bytes against the 7800-byte
limit. All three browser routes also pass with a 2 MiB phoneME heap.

Derived `int[]` payload is 41488 bytes for Waternet, 35824 for Rubido, and
63272 for Untangle, excluding array headers and the five nullable references
on each function body. The persisted W4IR/RMS format does not change.

The primary paired receipts were captured from clean commit `c59b533`; each
reports `source-clean=yes` and `evidence-quality=measured`. The earlier
pre-commit exploratory receipts remain stored separately for audit history and
are not used for the accepted percentages above.

Branch-capable compact regions and runtime control-stack retirement are still
not implemented. The retained fast path is intentionally partial and does not
yet satisfy the descriptor-complete precondition for retiring dynamic control
state.

## Risks / Trade-offs

- [A descriptor is correct for simple blocks but wrong for loop parameters or
  multi-value results] → Derive arity from the validator's exact label type,
  test all control forms, and compare every field with the legacy path.
- [Shadow verification perturbs timing] → Compile it out or leave it disabled
  in timing artifacts; never use a shadow run as performance evidence.
- [Descriptor metadata increases RMS or heap pressure] → Use flat primitive
  records, report bytes per function and cartridge, and reject layouts that
  break the constrained-heap gate.
- [Branch-capable compact execution changes the budget trap point] → Account
  every logical Wasm instruction before its original side effect or trap and
  add between-instruction budget tests.
- [A taken edge enters a region with stale cached stack state] → Preserve
  canonical entry points and flush all cached values at external targets.
- [Dense opcode IDs regress to `lookupswitch`] → Keep `javap` switch-shape
  verification as a target-artifact gate.
- [A lower dispatch count is slower on phoneME] → Require workload-specific
  balanced native i686 A/B and remove unprofitable execution phases.
- [Removing the control stack breaks rare validation states] → Keep removal
  last and require block parameters/results, multi-value, `br_table`,
  unreachable-polymorphic, recursion, traps, and paged-cache coverage first.

## Migration Plan

1. Add descriptor metadata and shadow differential while preserving the current
   executor.
2. Persist the metadata under a bumped W4IR format and verify resident/cache
   equivalence.
3. Prototype `br`, `br_if`, and `br_table` one at a time and remove every
   candidate that fails its native phoneME gate.
4. Retain the pc-indexed ordinary-branch path only after its native phoneME,
   constrained-heap, target-artifact, and exactness gates pass.
5. Keep the dynamic control state for function return, higher arity,
   `br_table`, fused branches, and rollback until a descriptor-complete
   executor passes independently.
6. Bump the cache to W4IR v17, checksum function metadata before decoding
   persisted counts, and reject/rebuild the whole cache on checksum, bounds, or
   intrinsic-signature failure.

Rollback at every stage is the prior authoritative legacy path plus an atomic
W4IR cache rebuild. No user data or cartridge bytes require migration.

## Open Questions

- Does a packed two-int descriptor outperform a flat four- or five-int record
  after decode and bounds costs are included on phoneME?
- If descriptor execution is revisited with a different IR representation,
  should repeated `br_table` targets share descriptor indices?
- A future branch-capable-region design must first identify a profitable
  replacement for the rejected generic descriptor lookup and transfer path.
- Dynamic control-stack omission is deferred until such a descriptor-complete
  authoritative path exists.
- Is a one-value Java-local stack cache profitable inside descriptor-backed
  regions before any 32-bit word-frame redesign?
