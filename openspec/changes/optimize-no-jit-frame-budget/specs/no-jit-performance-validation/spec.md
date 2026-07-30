## ADDED Requirements

### Requirement: Every performance candidate has a durable identity

The project SHALL assign every new performance candidate a unique identifier
and record its hypothesis, source, mechanism, affected files, expected benefit,
risks, baseline identities, commands, workloads, verification results,
bytecode and memory effects, raw measurements, verdict, and reconsideration
conditions.

#### Scenario: Candidate begins implementation

- **WHEN** a new optimization is prototyped
- **THEN** its ledger entry exists before or during implementation and does not
  depend only on conversation history or ignored temporary files

#### Scenario: Candidate is removed

- **WHEN** a candidate is rejected or superseded and its source code is removed
- **THEN** its measured result and decision reason remain in the ledger

### Requirement: Previous dead ends are not repeated silently

Before running a candidate, the project SHALL compare it with prior ledger and
OpenSpec results and SHALL state the materially new evidence, implementation,
or measurement method when revisiting a closed idea.

#### Scenario: Proposed mechanism matches a rejected experiment

- **WHEN** research proposes a mechanism already recorded as rejected
- **THEN** the candidate is not rerun unless its entry explains why the prior
  verdict no longer answers the new hypothesis

### Requirement: Measurement uses the applicable no-JIT judge

Java execution changes covered by the headless route harness SHALL be judged by
balanced paired native i686 phoneME measurements. MIDP-boundary behavior not
executed by that harness SHALL use KEmulator for integration and a physical
phone for final timing claims.

#### Scenario: Interpreter candidate is timed

- **WHEN** a candidate changes interpreter or pure Java runtime execution
- **THEN** its speed verdict comes from clean, production-shaped native i686
  phoneME artifacts with exact checkpoint equivalence

#### Scenario: Canvas or MMAPI boundary is timed

- **WHEN** the affected cost exists only in device Canvas or MMAPI code
- **THEN** phoneME timing is not presented as proof and the verdict remains
  provisional until the applicable emulator or physical-device gate is run

### Requirement: Correctness and compatibility precede acceptance

Every retained optimization SHALL preserve exact WebAssembly and WASM-4 state,
trap and instruction-budget behavior, Java 1.3 source compatibility, CLDC 1.1
and MIDP 2.0 API compatibility, classfile version 47, preverification, release
JAR integrity, and relevant cache and MIDP behavior.

#### Scenario: Candidate produces a timing improvement

- **WHEN** paired timing indicates a useful improvement
- **THEN** the candidate is not accepted until all affected correctness,
  artifact, memory, and integration gates pass

### Requirement: Initial comparisons isolate one mechanism

The project SHALL measure an optimization independently before combining it
with other unaccepted changes and SHALL measure the final combined retained
set because independent percentages are not additive.

#### Scenario: Two unrelated optimizations are available

- **WHEN** both candidates are ready for timing
- **THEN** each receives a separate initial A/B before any combined candidate
  is evaluated

### Requirement: Stable accepted changes are committed independently

An accepted optimization SHALL update its ledger and relevant tasks, pass the
complete required verification, contain no unrelated or generated changes, and
be committed to `main` with a focused English message before the next cycle
uses it as the baseline.

#### Scenario: Candidate passes its acceptance gate

- **WHEN** authoritative measurements are repeatably positive and all required
  gates pass
- **THEN** the stable change is committed independently and the next candidate
  starts from that clean baseline

#### Scenario: Candidate fails its acceptance gate

- **WHEN** a candidate regresses, moves cost elsewhere, is unresolved, or adds
  unjustified risk
- **THEN** its verdict is recorded and its implementation is removed without a
  production commit
