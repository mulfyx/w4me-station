#!/usr/bin/env bash
# Sourced by tools/kemu/run.sh; not a standalone entrypoint.
set -euo pipefail

sha256_file() {
    sha256sum -- "$1" | cut -d ' ' -f 1
}

compile_diagnostic_runtime() {
    local classes_dir="$1"
    local sources_file="$2"

    mkdir -p -- "${classes_dir}"
    find "${ROOT_DIR}/src/main/java" -name '*.java' -print \
        | sort > "${sources_file}"
    javac \
        -source "${J2ME_SOURCE}" \
        -target "${J2ME_TARGET}" \
        -Xlint:-options \
        -bootclasspath "${J2ME_BOOTCLASSPATH}" \
        -classpath "${MIDP_API_JAR}" \
        -d "${classes_dir}" \
        @"${sources_file}"
}

build_diagnostic_jar() {
    local source_jar="$1"
    local output_jar="$2"
    local classes_dir="$3"
    local midlet_name="$4"
    local midlet_class="$5"
    shift 5

    cp -- "${source_jar}" "${output_jar}"
    mkdir -p -- "${classes_dir}"
    javac \
        -source "${J2ME_SOURCE}" \
        -target "${J2ME_TARGET}" \
        -Xlint:-options \
        -classpath "${MIDP_API_JAR}:${ROOT_DIR}/build/midlet/classes" \
        -d "${classes_dir}" \
        "${FRAMEBUFFER_ORACLE_SOURCE}" \
        "${ROOT_DIR}/src/test/java/w4me/midp/DiagnosticW4MeMidlet.java" \
        "${ROOT_DIR}/src/test/java/w4me/midp/DiagnosticW4SessionMonitor.java" \
        "$@"
    jar uf "${output_jar}" -C "${classes_dir}" .
    # Cartridges that are regression fixtures but are not part of the release
    # catalog. Probes load them from the classpath, so the diagnostic JAR carries
    # them and the test corpus stays independent of what the library ships.
    jar uf "${output_jar}" -C "${ROOT_DIR}" cartridges/mandelbrot.wasm
    jar uf "${output_jar}" -C "${ROOT_DIR}" cartridges/sound-test.wasm
    jar uf "${output_jar}" -C "${ROOT_DIR}" cartridges/tankle.wasm
    jar uf "${output_jar}" -C "${ROOT_DIR}" cartridges/game-of-life-zig-edition.wasm
    {
        printf 'MIDlet-Name: %s\n' "${midlet_name}"
        printf 'MIDlet-1: %s,,%s\n' "${midlet_name}" "${midlet_class}"
    } > "${classes_dir}/probe.mf"
    jar ufm "${output_jar}" "${classes_dir}/probe.mf" \
        2> "${classes_dir}/manifest.log"
}
