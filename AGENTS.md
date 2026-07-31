# W4ME Station Agent Instructions

## Scope

These instructions apply to the entire repository. More specific `AGENTS.md`
files, if added later, override this file within their directories.

Keep durable architecture and product requirements in the relevant
documentation or OpenSpec change. Keep this file limited to stable working
rules for repository automation.

## Platform contract

- Production code targets Java 1.3 bytecode, CLDC 1.1, and MIDP 2.0.
- Do not raise the Java source or target level.
- Do not use Java SE-only APIs, reflection, runtime class generation,
  `ClassLoader`, `Unsafe`, JNI, or implementation-specific phoneME APIs in
  production code.
- Preserve compatibility with constrained physical phones. Do not design around
  desktop emulators, abundant heap, a JIT, or implementation-specific behavior.
- Use LCDUI for application menus, settings, forms, alerts, and navigation.
  Custom Canvas rendering is appropriate for gameplay and small game-state
  overlays such as pause or FPS indicators.

## Release variants and optional APIs

- Keep both release variants working:
    - `w4me-station.jar` may use optional JSR-75 FileConnection APIs;
    - `w4me-station-base.jar` must contain no JSR-75 classes or permissions.
- Keep optional APIs behind isolated classes and graceful capability checks.
- File browsing must remain bounded in memory. Do not materialize an arbitrary
  directory listing in full.
- Do not add mandatory permissions when an optional permission or fallback is
  sufficient.

## Runtime and interpreter correctness

- Every cartridge, bundled or external, must follow universal runtime rules.
  Cartridge hashes, fingerprints, names, or embedded byte patterns must not
  select optimized behavior.
- Preserve instruction-budget accounting and the exact point at which a budget
  trap becomes observable.
- Preserve traps, memory, globals, tables, passive data, disk state, APU state,
  framebuffer output, and logical instruction counts unless an approved
  specification explicitly changes the contract.
- Treat persisted RMS and W4IR data as untrusted. Validate counts before
  allocation, validate metadata before execution, and retain discard-and-rebuild
  recovery for damaged caches.
- Keep `WasmInterpreter.execute` within the bytecode ceiling enforced by
  `tools/verify.sh` and keep the production dispatch compatible with the no-JIT
  Java ME VM.

## Performance evidence

- Native i686 phoneME `cldc_vm_r` is the performance judge for interpreter and
  headless runtime claims.
- HotSpot, KEmulator, QEMU, dispatch counters, allocation counts, and class-size
  changes are diagnostic evidence only. Do not describe them as a confirmed
  phoneME speedup.
- Performance A/B runs must use clean, artifact-bound builds, balanced paired
  ordering, identical routes, and exact correctness oracles.
- Report paired effects, wins/losses/ties, timer resolution, and regressions on
  control workloads. Do not infer a gain from independent medians.
- Physical-device claims about LCDUI, MMAPI, input, heap pressure, or perceived
  smoothness require physical-device evidence.

## UI and product changes

- Prefer native LCDUI interaction patterns over custom widgets.
- Preserve short key presses and pointer events across slow frames.
- Do not put blocking MMAPI, RMS, network, or filesystem work on the gameplay
  loop without explicitly accounting for frame behavior.
- Update the relevant OpenSpec change when behavior, user-visible interaction,
  persistence, compatibility, or performance methodology changes.

## Repository workflow

- Use the pinned project commands instead of host-installed substitutes.
- Run only one container-backed `just` command at a time in a checkout. Parallel
  commands can relabel the same rootless Podman bind mount and race over shared
  `build/` output; use separate worktrees for concurrent work.
- Run `just quality` and `just verify` before presenting a source change as
  ready to commit.
- Run `just analysis` when changing Java production code or analysis
  configuration.
- Run `just security` when changing dependencies, workflows, release tooling,
  permissions, or security configuration.
- Generate release JAR/JAD files and `dist/SHA256SUMS` only through
  `just release`. Keep generated release artifacts in a separate commit.
- Keep functional changes separate from mechanical formatting when practical.
- Do not edit vendored code, cartridge binaries, generated files, or release
  artifacts unless the task explicitly requires it.
- Preserve unrelated worktree changes.

## Git and publication

- Do not stage, commit, push, tag, publish, merge, or rebase without explicit
  authorization for the current batch.
- Use Conventional Commits for authorized commits.
- Before an authorized commit, review the staged diff and report unresolved
  correctness, compatibility, performance, or release risks.
- Never treat authorization for a commit as authorization to push or publish.
