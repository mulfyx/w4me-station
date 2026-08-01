# Development

## Repository layout

```text
src/main/             production Java ME sources and resources
src/test/             host and MIDP verification harnesses
cartridges/           bundled upstream WASM-4 cartridges
testdata/oracles/     immutable exact replay fixtures
bench/configs/        compile-time interpreter benchmark configurations
bench/w4bench/        deterministic synthetic benchmark and frozen contract
tools/                build, verification, emulator, and benchmark entrypoints
openspec/             accepted and proposed product changes
```

Generated classes, emulator state, screenshots, logs, and receipts belong under
the ignored `build/` directory and are not source artifacts. `dist/` contains
the reproducible versioned JAR/JAD release set produced by `just release`.

## Toolchain

Install `just`, `flock`, and a `docker` command on the Linux host. Docker Engine
works directly; Podman users can provide its Docker-compatible command. The
project image contains JDK 8, ProGuard, KEmulator, WABT, Python, ShellCheck, and
formatting tools.

```sh
just setup
just doctor
```

Project scripts automatically start a disposable container from the canonical
`localhost/w4me-station:latest` image when they need the pinned Java ME and
quality toolchain. The image is built only by `just setup`. A failed build
preserves the previous image; a successful build removes superseded W4ME
images and project-labelled intermediate build records. Podman layer caching is
disabled for this project image, so successful multi-stage rebuilds leave no
visible `<none>` image records. Command containers use `--rm` and leave
generated files in the bind-mounted `build/` and `dist/` directories.

Run only one container-backed `just` command at a time in a checkout. Concurrent
commands can relabel the same rootless Podman bind mount and can clean or write
the same `build/` paths, resulting in transient `Permission denied` or missing
output. Use separate Git worktrees when commands must run concurrently.

## Common commands

```sh
just fmt           # format every repository-owned language
just quality       # formatting, lint, OpenSpec, and repository contracts
just analysis      # Error Prone and SpotBugs on analysis-only Java 8 classes
just security      # secrets, advisories, licenses, and commit messages
just build         # build and validate both release JAR variants
just test          # deterministic host regression suite
just verify        # lint, test, build, JAR checks, counterless differential
just run           # open the station in KEmulator
just bench         # native phoneME corpus benchmark
just bench-pcm     # native phoneME PCM synthesis benchmark
just bench-argb    # native phoneME framebuffer conversion benchmark
just bench-w4bench # deterministic synthetic interpreter benchmark
just nightly       # links, Trivy, SBOM, and reproducible release
just release       # complete release gate and SHA256SUMS
```

Run `just quality` and `just verify` before submitting a change. Run
`just analysis` for production Java changes and `just security` for dependency,
workflow, permission, release-tooling, or security changes. `just nightly`
matches the deeper weekly audit and writes its reports to
`build/reports/nightly/`.

The `justfile` is the stable public interface. Specialized tools keep stable
subcommand entrypoints:

```sh
tools/kemu/run.sh session <start|cmd|stop> [args...]
tools/kemu/run.sh verify <scenario> [jar]
tools/kemu/run.sh bench <scenario> [args...]
tools/phoneme/run.sh <bench|bench-pcm|bench-argb|bench-w4bench|verify|verify-arm64> [args...]
tools/bench/run.sh <untangle|corpus|fusions|w4bench>
tools/verify.sh <jar|counterless> [args...]
```

`tools/kemu/run.sh` is only the KEmulator dispatcher. Shared build, automation,
and session code lives under `tools/kemu/lib/`; verification scenarios are
grouped under `tools/kemu/verify/`, and benchmark scenarios under
`tools/kemu/bench/`. Those modules are sourced by the dispatcher and are not
standalone commands. ShellCheck follows that source graph from the dispatcher
instead of treating the modules as unrelated executables.

All KEmulator and benchmark output is written below `build/reports/`.

## Automated checks

The main GitHub Actions workflow runs strict Java analysis, security and supply
chain gates, the complete release gate, and a check that tracked
artifacts in `dist/` are reproducible from the submitted source.

The weekly and manually triggered nightly workflow adds external-link
validation, Trivy filesystem and toolchain scans, a CycloneDX SBOM, an OpenSSF
Scorecard report when GitHub repository metadata is available, and a clean
two-build reproducibility comparison. Nightly reports are retained as workflow
artifacts and are also available locally under `build/reports/nightly/`.

The pinned toolchain owns formatting and lint versions. Do not substitute
host-installed formatters when changing repository files. See
[`AGENTS.md`](../AGENTS.md) for repository automation rules,
[`CONTRIBUTING.md`](../CONTRIBUTING.md) for contribution requirements, and
[`SECURITY.md`](../SECURITY.md) for vulnerability reporting.

## Java ME constraints

- Compile production code with `-source 1.3 -target 1.3`.
- Keep CLDC code free of Java SE-only APIs.
- Compile production sources against the checksum-pinned CLDC 1.1 API
  bootclasspath; do not fall back to the host JDK API.
- Preserve major version 47 and Java ME `StackMap` attributes.
- Avoid allocations and helper calls in interpreter hot paths unless measured.
- Keep `WasmInterpreter.execute` below the bytecode limit enforced by
  `tools/verify.sh jar`.

The optional phoneME build compiles against its CLDC `classes.zip` as a
bootclasspath. This is also the strictest available CLDC API lint.

## Test data

`testdata/oracles/` contains only immutable inputs and expected state required
by automated tests. Do not write new results there. A command that captures a
receipt, log, or screenshot must write it to `build/reports/`.

Replay routes, expected framebuffer hashes, and benchmark receipt formatting
belong to test MIDlets under `src/test/`. KEmulator commands inject those
classes into temporary probe JARs. Release JARs contain the cartridges and
runtime integrity hashes, but no replay route or expected screen oracle;
`tools/verify.sh jar` enforces that boundary.

Bundled-cartridge KEmulator scenarios select the real LCDUI library entry
by resolving its unique visible title and using the revision-gated automation
API. Direct-launch test MIDlets are reserved for non-catalog fixtures and
specialized runtime harnesses; they are not a substitute for product
navigation coverage.

Third-party cartridges must remain byte-identical. Update
`THIRD_PARTY_NOTICES.md` when the corpus changes.

## Performance changes

Interpreter performance claims require:

1. exact state or replay-oracle equivalence;
2. a production-shape Java 1.3 artifact;
3. paired native i686 phoneME measurements;
4. no material regression on the remaining corpus routes.

KEmulator, HotSpot, and QEMU wall time are not substitutes for the native
phoneME measurement. See [performance.md](performance.md).
