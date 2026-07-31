# Contributing

W4ME Station targets CLDC 1.1 / MIDP 2.0 and Java 1.3. Changes must preserve
that compatibility unless the project scope is explicitly revised.

## Before sending a change

1. Keep production Java compatible with `-source 1.3 -target 1.3`.
2. Do not add desktop-only `java.*` APIs to CLDC code.
3. Keep third-party cartridges unchanged and update
   `THIRD_PARTY_NOTICES.md` when the corpus changes.
4. Do not add or replace phoneME binaries without the exact corresponding
   GPL source and reproducible build files.
5. Do not commit intermediate build output, RMS state, or local emulator data.
   The reproducible versioned artifacts under `dist/` are the only build-output
   exception and must come from a successful `just release`.
6. Run the relevant exactness gates and retain reproducible performance
   receipts outside the tracked source tree.

The minimum local verification is:

```sh
just quality
just verify
```

Run `just analysis` when changing production Java code or analysis
configuration. Run `just security` when changing dependencies, workflows,
permissions, release tooling, or security configuration. The deeper
`just nightly` gate additionally checks external links, scans the repository
and toolchain with Trivy, emits a CycloneDX SBOM, and verifies reproducible
release output.

Specialized KEmulator scenarios use `tools/kemu/run.sh verify <scenario>`.
Interpreter performance changes require paired native i686 phoneME
measurements; KEmulator, HotSpot, and QEMU wall time are not substitutes.

## Scope

Keep changes focused. Preserve unrelated work and avoid mixing behavioral
changes with formatting or evidence updates. Public reports should distinguish
verified results from estimates and unresolved physical-device behavior.

## Licensing

Contributions are provided under the project's
[MIT License](LICENSE) unless explicitly documented otherwise. Do not submit
third-party code or cartridges unless their license and attribution are
compatible and recorded.
