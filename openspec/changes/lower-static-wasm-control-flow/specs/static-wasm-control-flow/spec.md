## ADDED Requirements

### Requirement: Static branch descriptor generation
The module decoder SHALL derive an immutable primitive descriptor for every
`br`, `br_if`, and `br_table` edge from the validated structured-control state.
Each descriptor SHALL identify the direct target, destination value-stack
height relative to the current function, transfer arity, active control depth
after transfer, and whether the edge targets a loop or function return.

#### Scenario: Decode a loop branch
- **WHEN** a branch targets a loop with parameters
- **THEN** its descriptor targets the first instruction in the loop and uses the loop parameter arity

#### Scenario: Decode a block branch
- **WHEN** a branch targets a block or `if`
- **THEN** its descriptor targets the instruction after the matching `end` and uses the target result arity

#### Scenario: Decode a function branch
- **WHEN** a branch depth selects the implicit function label
- **THEN** its descriptor identifies a function return with the declared function result arity

### Requirement: Primitive bounded representation
Branch descriptors and descriptor tables SHALL use bounded primitive arrays or
packed integer records and SHALL NOT allocate per-edge execution objects.

#### Scenario: Load descriptor metadata
- **WHEN** a decoded function is executed repeatedly
- **THEN** selecting a descriptor requires only bounded primitive indexing and creates no branch object

### Requirement: Legacy compatibility verification
The first descriptor phase SHALL retain the current dynamic control stack and
legacy branch operands as the authoritative path. A verification mode SHALL
compare descriptor and legacy outcomes without affecting production timing
artifacts.

#### Scenario: Verify a taken branch
- **WHEN** compatibility verification executes a taken branch
- **THEN** both paths agree on target PC, destination height, arity, transferred values, active control depth, and function-return state

#### Scenario: Verify an untaken conditional branch
- **WHEN** compatibility verification executes an untaken `br_if`
- **THEN** the descriptor path preserves the fallthrough PC and leaves the value and control stacks equivalent to the legacy path

#### Scenario: Disable verification for timing
- **WHEN** the production timing artifact is built
- **THEN** descriptor shadow checks add no runtime branch, copy, counter, or allocation to the measured path

### Requirement: Independently staged branch execution
Descriptor execution for `br`, `br_if`, and `br_table` SHALL be implemented and
accepted as separate reversible phases.

#### Scenario: Reject one branch family
- **WHEN** a descriptor phase fails an exactness or phoneME acceptance gate
- **THEN** that phase is removed or remains legacy without blocking independently successful earlier phases

#### Scenario: Select a branch-table edge
- **WHEN** `br_table` receives an in-range selector or selects its default edge
- **THEN** it uses the descriptor for exactly the same target selected by the validated legacy table

### Requirement: Constant-time ordinary branch selection
An accepted ordinary `br` or taken `br_if` fast path SHALL derive bounded
PC-indexed primitive metadata from the persisted descriptor records when a
function is bound. The hot edge SHALL NOT perform a binary search or allocate
an execution object.

#### Scenario: Execute a common direct edge
- **WHEN** an ordinary branch has a non-return target and arity zero or one
- **THEN** execution selects its target, destination height, arity, and control depth by bounded primitive indexing and transfers the value inline

#### Scenario: Fall back from the direct edge
- **WHEN** an edge is a function return, has higher arity, uses `br_table`, or is not covered by the direct metadata
- **THEN** execution uses the validated legacy control path with identical logical instruction accounting

#### Scenario: Bind cached direct metadata
- **WHEN** a current-format W4IR function is decoded resident or restored from cache
- **THEN** the same derived direct metadata is built without changing the persisted format or fingerprint

### Requirement: Exact stack transfer
Descriptor execution SHALL preserve WebAssembly value ordering and stack height
for zero-, one-, and multi-value branch arities, including loop parameters and
block, `if`, and function results.

#### Scenario: Transfer no values
- **WHEN** a descriptor has arity zero
- **THEN** execution resets the value top to the descriptor destination height without copying a value

#### Scenario: Transfer one value
- **WHEN** a descriptor has arity one
- **THEN** execution preserves the top value and writes it at the target destination

#### Scenario: Transfer multiple values
- **WHEN** a descriptor has an arity greater than one
- **THEN** execution performs a bounded overlap-safe transfer that preserves source order

### Requirement: Exact logical instruction semantics
Control-flow lowering SHALL preserve logical instruction counts, instruction
budget checks, trap points, host-visible state, and function-return behavior.

#### Scenario: Exhaust the budget at a branch boundary
- **WHEN** the instruction budget expires before or within a descriptor-backed control sequence
- **THEN** execution traps at the same logical Wasm instruction as the legacy path

#### Scenario: Compare full state
- **WHEN** a descriptor candidate and the legacy executor replay the same route
- **THEN** framebuffer memory, all linear memory, globals, table, results, traps, and deterministic counters are identical

### Requirement: Persistent W4IR descriptors
The W4IR cache SHALL persist the complete descriptor representation and SHALL
atomically reject records from an older, damaged, or incompatible format.
Function metadata SHALL be checksummed and verified before any persisted length
or count controls an allocation. Persisted local counts, instruction counts,
intrinsic identifiers, table lengths, descriptor lengths, and page counts SHALL
be bounded by the same limits as resident decoding.

#### Scenario: Reopen cached descriptors
- **WHEN** a function is decoded, written to RMS, reopened, paged, and promoted
- **THEN** every descriptor and descriptor-table selection is identical to the resident decoded function

#### Scenario: Encounter an older cache
- **WHEN** the cartridge cache uses a prior W4IR format
- **THEN** the cache is discarded and rebuilt rather than partially interpreted

#### Scenario: Encounter damaged function metadata
- **WHEN** a function metadata record fails its checksum or contains an out-of-range count or intrinsic identifier
- **THEN** no persisted count controls an unbounded allocation and the complete cartridge cache is discarded and rebuilt

#### Scenario: Bind a cached numeric intrinsic
- **WHEN** a cached function identifies a numeric f32 intrinsic
- **THEN** the identifier is in the supported range and the function has exactly one f32 parameter and one f32 result before the intrinsic can execute

### Requirement: Conservative branch-capable regions
Compact execution SHALL support descriptor-backed control flow only in a
separate phase after generic descriptor execution is exact. Every external
branch target SHALL remain a valid ordinary dispatcher entry point.

#### Scenario: Take a branch inside a region
- **WHEN** a supported branch is taken inside a compact region
- **THEN** cached stack state is flushed and execution continues at the descriptor target with exact accounting

#### Scenario: Fall through a conditional branch
- **WHEN** a supported `br_if` is not taken inside a compact region
- **THEN** execution continues within the region without changing its canonical external entry points

#### Scenario: Keep activation unchanged
- **WHEN** branch-capable regions become available
- **THEN** the existing compact activation and profitability policy remains unchanged until an isolated phoneME experiment accepts a replacement

### Requirement: Last-stage control-stack removal
The dynamic control stack SHALL remain available until descriptor execution
covers every supported control construct and passes all exactness, cache,
target-artifact, corpus, and phoneME gates.

#### Scenario: Descriptor coverage is incomplete
- **WHEN** any supported branch, multi-value form, unreachable-polymorphic state, recursion case, or cache path still requires dynamic control metadata
- **THEN** the corresponding control arrays remain in the production implementation

#### Scenario: Remove dynamic control state
- **WHEN** the descriptor-complete path passes all required gates and no accepted path reads or writes runtime control frames
- **THEN** control-array removal is performed as its own reversible and measured phase

### Requirement: Java ME target invariants
Every retained phase SHALL remain compatible with Java 1.3 classfiles, CLDC 1.1
APIs, CLDC preverification, dense `tableswitch` execution dispatch, configured
method-size limits, and bounded heap operation.

#### Scenario: Verify the target artifact
- **WHEN** a control-flow phase produces a candidate JAR
- **THEN** classfile version, StackMap attributes, CLDC API lint, switch shape, method sizes, and KEmulator loading checks pass

### Requirement: Native phoneME performance acceptance
A control-flow optimization SHALL be described as a speed improvement only
after clean same-build balanced native i686 phoneME pairs pass exact workload
oracles and show a resolved positive effect on the affected routes.

#### Scenario: Counters improve but time does not
- **WHEN** a candidate reduces outer dispatches or stack operations but is neutral or slower on native phoneME
- **THEN** the candidate is rejected as a performance change and is not combined with the next phase

#### Scenario: Non-authoritative timing environment
- **WHEN** timing comes from HotSpot, KEmulator, desktop `-Xint`, or QEMU
- **THEN** it is retained only as diagnostic evidence and cannot accept the candidate

### Requirement: Separate value-representation research
A 32-bit cell stack, split value representation, or register-slot W4IR SHALL
remain independent from branch-descriptor implementation and acceptance.

#### Scenario: Evaluate a value representation
- **WHEN** a 32-bit or register-slot prototype is proposed
- **THEN** it receives a separate format, memory, correctness, and native phoneME comparison rather than being included in a descriptor A/B
