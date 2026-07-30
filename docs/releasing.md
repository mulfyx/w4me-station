# Releasing

## Public source

Before publishing:

- confirm that phoneME binaries, preverifiers, and class libraries are absent
  from the source tree and complete public history;
- confirm that intermediate build output and emulator state are ignored;
- confirm that the checked-in `dist/` artifacts match a fresh `just release`;
- confirm that README download links target the versioned release
  assets rather than files from `main`;
- review `LICENSE`, `THIRD_PARTY_NOTICES.md`, `SECURITY.md`, and
  `CONTRIBUTING.md`;
- check the source tree for machine-specific absolute paths and private
  references;
- smoke-test the final JAR on a physical CLDC 1.1 / MIDP 2.0 handset;
- configure private vulnerability reporting on the repository host.

## Build

```sh
just release
```

The command runs the deterministic tests, builds both JAR variants, validates
them, runs the counterless production-build differential, and writes
`dist/SHA256SUMS`.

The release gate checks:

- Java 1.3 classfile version and Java ME `StackMap` attributes;
- the bytecode-size and dispatch-shape limits of the interpreter;
- exact cartridge state with production diagnostic counters removed;
- hashes of all thirteen bundled cartridges;
- the embedded MIT license and third-party notices;
- absence of phoneME tools from the JARs;
- the final JAD sizes.

Release artifacts:

| File                             | Purpose                                     |
| -------------------------------- | ------------------------------------------- |
| `w4me-station.jar` / `.jad`      | full build with optional JSR-75 browsing    |
| `w4me-station-base.jar` / `.jad` | build without JSR-75 classes or permissions |
| `SHA256SUMS`                     | hashes for all four release files           |

The base JAR and JAD must not contain JSR-75 classes or declare the
`javax.microedition.io.Connector.file.read` permission. The full variant must
contain both.

## Release notes

State that:

- W4ME Station is independent from and not endorsed by WASM-4;
- application source is MIT-licensed;
- bundled cartridges are separately licensed and attributed;
- the target is CLDC 1.1 / MIDP 2.0 with Java 1.3 bytecode;
- physical-device compatibility and performance vary by VM and handset;
- phoneME is an optional local reference rig and is not distributed.

Do not present host JVM, KEmulator, constrained-host, or QEMU timing as
physical-phone performance.

The manifest and generated JAD must retain
`MIDlet-Info-URL: https://github.com/mulfyx/w4me-station`.
