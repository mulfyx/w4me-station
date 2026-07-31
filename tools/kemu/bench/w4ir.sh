#!/usr/bin/env bash
# Sourced by tools/kemu/run.sh; not a standalone entrypoint.
set -euo pipefail

cmd_bench_w4ir() {
    JAR_PATH="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/w4ir-benchmark"
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
        "${TEMP_DIR}/w4ir-benchmark.jar" \
        "${TEMP_DIR}/classes" \
        "W4ME W4IR Benchmark" \
        "w4me.midp.DiagnosticBenchmarkLibraryMidlet" \
        "${ROOT_DIR}/src/test/java/w4me/midp/DiagnosticBenchmarkLibraryMidlet.java"
    SOURCE_SHA256="$(sha256sum -- "${JAR_PATH}" | cut -d ' ' -f 1)"
    BENCHMARK_SHA256="$(
        sha256sum -- "${TEMP_DIR}/w4ir-benchmark.jar" | cut -d ' ' -f 1
    )"

    KEMU_SIZE=240x320 "${ROOT_DIR}/tools/kemu/run.sh" session \
        start "${TEMP_DIR}/w4ir-benchmark.jar" > /dev/null
    kemu_wait_log 'W4ME_BENCH cart=Plasma Cube.*w4ir=RMS-build' 30000
    kemu_key_press RSK
    kemu_wait_log 'W4ME_BENCH cart=Plasma Cube.*w4ir=RMS-hit' 30000
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs read \
        > "${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"

    build_receipt="$(grep -F -- 'W4ME_BENCH cart=Plasma Cube' "${TEMP_DIR}/worker.log" \
        | grep -F -- 'w4ir=RMS-build' | tail -n 1)"
    hit_receipt="$(grep -F -- 'W4ME_BENCH cart=Plasma Cube' "${TEMP_DIR}/worker.log" \
        | grep -F -- 'w4ir=RMS-hit' | tail -n 1)"
    if [[ -z "${build_receipt}" ]] || [[ -z "${hit_receipt}" ]]; then
        printf 'error: missing first-launch or cached W4IR benchmark receipt\n' >&2
        exit 1
    fi
    if ! printf '%s\n' "${hit_receipt}" | grep -E -q -- \
        'code-faults=[1-9][0-9]* code-hits=[0-9]+ code-promoted=[1-9][0-9]*'; then
        printf 'error: cached W4IR benchmark did not page and promote hot code\n' >&2
        exit 1
    fi
    {
        printf 'timing-authoritative=no runtime=KEmulator-HotSpot\n'
        printf 'source-jar-sha256=%s\n' "${SOURCE_SHA256}"
        printf 'benchmark-jar-sha256=%s\n' "${BENCHMARK_SHA256}"
        printf '%s\n' "${build_receipt}" "${hit_receipt}"
    } | tee "${RESULT_DIR}/receipt.txt"
}
