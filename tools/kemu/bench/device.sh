#!/usr/bin/env bash
# Sourced by tools/kemu/run.sh; not a standalone entrypoint.
set -euo pipefail

cmd_bench_phone() {
    JAR_PATH="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/benchmark-phone"
    TEMP_DIR="$(mktemp -d)"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop > /dev/null 2>&1 || true
        rm -rf -- "${TEMP_DIR}"
    }
    trap cleanup EXIT

    rm -rf -- "${RESULT_DIR}"
    mkdir -p -- "${RESULT_DIR}" "${TEMP_DIR}/classes"
    build_diagnostic_jar \
        "${JAR_PATH}" \
        "${TEMP_DIR}/plasma-benchmark.jar" \
        "${TEMP_DIR}/classes" \
        "W4ME Plasma Benchmark" \
        "w4me.midp.PlasmaBenchmarkMidlet" \
        "${ROOT_DIR}/src/test/java/w4me/midp/PlasmaBenchmarkMidlet.java"
    SOURCE_SHA256="$(sha256sum -- "${JAR_PATH}" | cut -d ' ' -f 1)"
    BENCHMARK_SHA256="$(
        sha256sum -- "${TEMP_DIR}/plasma-benchmark.jar" | cut -d ' ' -f 1
    )"

    "${ROOT_DIR}/tools/kemu/run.sh" phone "${TEMP_DIR}/plasma-benchmark.jar" \
        > "${RESULT_DIR}/profile.txt"
    kemu_wait_log 'W4ME_BENCH cart=Plasma Cube frames=120 ' 120000
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs read \
        > "${RESULT_DIR}/worker.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/plasma-phone-profile.png" > /dev/null

    if ! grep 'W4ME_BENCH cart=Plasma Cube frames=120 ' \
        "${RESULT_DIR}/worker.log" > "${RESULT_DIR}/result.txt"; then
        printf 'error: constrained KEmulator did not produce a Plasma benchmark receipt\n' >&2
        exit 1
    fi
    {
        printf 'timing-authoritative=no runtime=KEmulator-HotSpot\n'
        printf 'source-jar-sha256=%s\n' "${SOURCE_SHA256}"
        printf 'benchmark-jar-sha256=%s\n' "${BENCHMARK_SHA256}"
        cat -- "${RESULT_DIR}/profile.txt" "${RESULT_DIR}/result.txt"
    } > "${RESULT_DIR}/receipt.txt"
    cat -- "${RESULT_DIR}/receipt.txt"
}

cmd_bench_plasma() {
    JAR_PATH="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/benchmark-plasma"
    TEMP_DIR="$(mktemp -d)"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop > /dev/null 2>&1 || true
        rm -rf -- "${TEMP_DIR}"
    }
    trap cleanup EXIT

    rm -rf -- "${RESULT_DIR}"
    mkdir -p -- "${RESULT_DIR}" "${TEMP_DIR}/classes"
    build_diagnostic_jar \
        "${JAR_PATH}" \
        "${TEMP_DIR}/plasma-benchmark.jar" \
        "${TEMP_DIR}/classes" \
        "W4ME Plasma Benchmark" \
        "w4me.midp.PlasmaBenchmarkMidlet" \
        "${ROOT_DIR}/src/test/java/w4me/midp/PlasmaBenchmarkMidlet.java"
    SOURCE_SHA256="$(sha256sum -- "${JAR_PATH}" | cut -d ' ' -f 1)"
    BENCHMARK_SHA256="$(
        sha256sum -- "${TEMP_DIR}/plasma-benchmark.jar" | cut -d ' ' -f 1
    )"

    KEMU_SIZE=240x320 "${ROOT_DIR}/tools/kemu/run.sh" session \
        start "${TEMP_DIR}/plasma-benchmark.jar" > /dev/null
    kemu_wait_log 'W4ME_BENCH cart=Plasma Cube frames=120 ' 30000
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs read > "${RESULT_DIR}/worker.log"

    if ! grep 'W4ME_BENCH cart=Plasma Cube frames=120 ' \
        "${RESULT_DIR}/worker.log" > "${RESULT_DIR}/result.txt"; then
        printf 'error: KEmulator did not produce the Plasma Cube benchmark receipt\n' >&2
        exit 1
    fi
    {
        printf 'timing-authoritative=no runtime=KEmulator-HotSpot\n'
        printf 'source-jar-sha256=%s\n' "${SOURCE_SHA256}"
        printf 'benchmark-jar-sha256=%s\n' "${BENCHMARK_SHA256}"
        cat -- "${RESULT_DIR}/result.txt"
    } > "${RESULT_DIR}/receipt.txt"
    cat -- "${RESULT_DIR}/receipt.txt"
}
