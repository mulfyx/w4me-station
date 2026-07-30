# Cartridges

Every `.wasm` file in this directory is unchanged upstream work and is validated
by the regression suite.

Thirteen of them make up the release catalog and ship unchanged in both JAR
variants. The list and its order live in `tools/build.sh` and
`w4me.midp.LibraryList`, and `tools/verify.sh` pins each packaged hash.

`mandelbrot.wasm`, `rubido.wasm`, `sound-test.wasm`, `tankle.wasm` and
`game-of-life-zig-edition.wasm` are fixtures only. They stay here for the
framebuffer oracle, the corpus profiler and the phoneME benchmark, but are kept
out of the release JARs: their per-frame cost, or a mouse-only control scheme,
makes them a poor first impression on a handset. `tools/kemu/run.sh` adds them
to diagnostic JARs so the probes that need them keep working.

The cartridges are third-party works and are not covered by the project's MIT
license. Authors, upstream sources, licenses, and exact hashes are recorded in
[`THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md).
