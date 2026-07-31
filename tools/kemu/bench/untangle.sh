#!/usr/bin/env bash
# Sourced by tools/kemu/run.sh; not a standalone entrypoint.
set -euo pipefail

cmd_bench_untangle_matrix() {
    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    MODE="${2:-optimized}"
    RUNS="${3:-3}"
    PROFILE="${4:-phone}"
    case "${MODE}" in
        optimized | trace-off | fusion-only | baseline | comparison) ;;
        *)
            printf 'error: invalid Untangle benchmark mode\n' >&2
            exit 2
            ;;
    esac
    case "${PROFILE}" in
        0 | 1 | phone) ;;
        *)
            printf 'error: profile must be 0, 1, or phone\n' >&2
            exit 2
            ;;
    esac
    if ! [[ "${RUNS}" =~ ^[13579]$ ]]; then
        printf 'error: sample count must be one of 1, 3, 5, 7, 9\n' >&2
        exit 2
    fi

    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/benchmark-untangle-matrix-${MODE}-${PROFILE}"
    CHILD_DIR="${ROOT_DIR}/build/reports/kemu/benchmark-untangle-${MODE}"
    if [[ "${PROFILE}" = "phone" ]] || [[ "${PROFILE}" = "1" ]]; then
        CHILD_DIR="${CHILD_DIR}-phone"
    fi
    rm -rf -- "${RESULT_DIR}"
    mkdir -p -- "${RESULT_DIR}"
    : > "${RESULT_DIR}/totals.txt"

    run=1
    while [[ "${run}" -le "${RUNS}" ]]; do
        if ! "${ROOT_DIR}/tools/kemu/run.sh" bench untangle \
            "${SOURCE_JAR}" "${MODE}" "${PROFILE}" \
            > "${RESULT_DIR}/sample-${run}.out"; then
            "${ROOT_DIR}/tools/kemu/run.sh" session stop > /dev/null 2>&1 || true
            sleep 2
            "${ROOT_DIR}/tools/kemu/run.sh" bench untangle \
                "${SOURCE_JAR}" "${MODE}" "${PROFILE}" \
                > "${RESULT_DIR}/sample-${run}.out"
        fi
        cp -- "${CHILD_DIR}/receipt.txt" "${RESULT_DIR}/receipt-${run}.txt"
        if [[ "${MODE}" = "comparison" ]]; then
            total_ms="$(sed -n \
                's/.*W4ME_UNTANGLE_BENCH .* update-route-median-ms=\([0-9][0-9]*\) .*/\1/p' \
                "${RESULT_DIR}/receipt-${run}.txt" \
                | awk '{ total += $1; count++ } END { if (count != 4) exit 1; print total }')"
        else
            total_ms="$(sed -n \
                's/.*W4ME_UNTANGLE_BENCH .* update-total-ms=\([0-9][0-9]*\) .*/\1/p' \
                "${RESULT_DIR}/receipt-${run}.txt")"
        fi
        if ! [[ "${total_ms}" =~ ^[0-9]+$ ]]; then
            printf 'error: sample %s has no update total\n' "${run}" >&2
            exit 1
        fi
        printf '%s %s\n' "${run}" "${total_ms}" >> "${RESULT_DIR}/totals.txt"
        sleep 2
        run=$((run + 1))
    done

    sort -n -k2,2 -- "${RESULT_DIR}/totals.txt" > "${RESULT_DIR}/totals-sorted.txt"
    median_line=$((RUNS / 2 + 1))
    median="$(sed -n "${median_line}p" "${RESULT_DIR}/totals-sorted.txt")"
    median_run="${median%% *}"
    median_ms="${median##* }"
    {
        printf 'timing-authoritative=no runtime=KEmulator-HotSpot\n'
        if [[ "${MODE}" = "comparison" ]]; then
            printf 'UNTANGLE_MATRIX mode=%s profile=%s samples=%s median-run=%s median-aggregate-ms=%s\n' \
                "${MODE}" "${PROFILE}" "${RUNS}" "${median_run}" "${median_ms}"
        else
            printf 'UNTANGLE_MATRIX mode=%s profile=%s samples=%s median-run=%s median-update-total-ms=%s\n' \
                "${MODE}" "${PROFILE}" "${RUNS}" "${median_run}" "${median_ms}"
        fi
        printf 'samples='
        awk 'BEGIN { first=1 } { if (!first) printf ","; printf "%s:%s", $1, $2; first=0 } END { printf "\n" }' \
            "${RESULT_DIR}/totals.txt"
        cat -- "${RESULT_DIR}/receipt-${median_run}.txt"
    } > "${RESULT_DIR}/receipt.txt"
    cat -- "${RESULT_DIR}/receipt.txt"
}

cmd_bench_untangle() {

    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    MODE="${2:-optimized}"
    PROFILE="${3:-${KEMU_PHONE_PROFILE:-0}}"
    case "${MODE}" in
        optimized)
            PROBE_CLASS="w4me.midp.UntangleW4IrBenchmarkMidlet"
            ;;
        trace-off)
            PROBE_CLASS="w4me.midp.UntangleW4IrTraceOffBenchmarkMidlet"
            ;;
        fusion-only)
            PROBE_CLASS="w4me.midp.UntangleW4IrFusionBenchmarkMidlet"
            ;;
        baseline)
            PROBE_CLASS="w4me.midp.UntangleW4IrBaselineBenchmarkMidlet"
            ;;
        comparison)
            PROBE_CLASS="w4me.midp.UntangleW4IrComparisonBenchmarkMidlet"
            ;;
        *)
            printf 'usage: %s [jar] [optimized|trace-off|fusion-only|baseline|comparison]\n' \
                "$0" >&2
            exit 2
            ;;
    esac

    PROFILE_SUFFIX=""
    if [[ "${PROFILE}" = "phone" ]] || [[ "${PROFILE}" = "1" ]]; then
        PROFILE_SUFFIX="-phone"
    elif [[ "${PROFILE}" != "0" ]]; then
        printf 'usage: %s [jar] [optimized|trace-off|fusion-only|baseline|comparison] [0|phone]\n' \
            "$0" >&2
        exit 2
    fi
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/benchmark-untangle-${MODE}${PROFILE_SUFFIX}"
    TEMP_DIR="$(mktemp -d)"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop > /dev/null 2>&1 || true
        rm -rf -- "${TEMP_DIR}"
    }
    trap cleanup EXIT

    rm -rf -- "${RESULT_DIR}"
    mkdir -p -- "${RESULT_DIR}" "${TEMP_DIR}/classes"
    cp -- "${SOURCE_JAR}" "${TEMP_DIR}/untangle-benchmark.jar"
    cp -- "${ROOT_DIR}/cartridges/untangle.wasm" "${TEMP_DIR}/classes/untangle.wasm"
    javac \
        -source "${J2ME_SOURCE}" \
        -target "${J2ME_TARGET}" \
        -Xlint:-options \
        -classpath "${MIDP_API_JAR}:${ROOT_DIR}/build/midlet/classes" \
        -d "${TEMP_DIR}/classes" \
        "${FRAMEBUFFER_ORACLE_SOURCE}" \
        "${ROOT_DIR}/src/test/java/w4me/UntangleBenchmarkRoute.java" \
        "${ROOT_DIR}/src/test/java/w4me/midp/UntangleW4IrBenchmarkMidlet.java" \
        "${ROOT_DIR}/src/test/java/w4me/midp/UntangleW4IrTraceOffBenchmarkMidlet.java" \
        "${ROOT_DIR}/src/test/java/w4me/midp/UntangleW4IrFusionBenchmarkMidlet.java" \
        "${ROOT_DIR}/src/test/java/w4me/midp/UntangleW4IrBaselineBenchmarkMidlet.java" \
        "${ROOT_DIR}/src/test/java/w4me/midp/UntangleW4IrComparisonBenchmarkMidlet.java"
    jar uf "${TEMP_DIR}/untangle-benchmark.jar" -C "${TEMP_DIR}/classes" .
    {
        printf '%s\n' 'MIDlet-Name: W4ME Untangle W4IR Benchmark'
        printf 'MIDlet-1: W4ME Untangle W4IR Benchmark,,%s\n' "${PROBE_CLASS}"
    } > "${TEMP_DIR}/probe.mf"
    jar ufm "${TEMP_DIR}/untangle-benchmark.jar" "${TEMP_DIR}/probe.mf" \
        2> "${TEMP_DIR}/manifest.log"

    if [[ -n "${PROFILE_SUFFIX}" ]]; then
        "${ROOT_DIR}/tools/kemu/run.sh" phone \
            "${TEMP_DIR}/untangle-benchmark.jar" > "${RESULT_DIR}/profile.txt"
    else
        KEMU_SIZE=176x220 "${ROOT_DIR}/tools/kemu/run.sh" session \
            start "${TEMP_DIR}/untangle-benchmark.jar" > /dev/null
    fi

    COMPLETION_MODE="${MODE}"
    if [[ "${MODE}" = "comparison" ]]; then
        COMPLETION_MODE="baseline"
    fi
    kemu_wait_log \
        "W4ME_UNTANGLE_BENCH mode=${COMPLETION_MODE} frames=3208 " \
        120000
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs read \
        > "${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"

    if grep -F -q -- 'W4ME_UNTANGLE_BENCH_ERROR' "${TEMP_DIR}/worker.log"; then
        printf 'error: Untangle W4IR benchmark failed\n' >&2
        exit 1
    fi
    if [[ "${MODE}" = "comparison" ]]; then
        grep -E -- \
            'W4ME_UNTANGLE_BENCH mode=(optimized|trace-off|fusion-only|baseline) frames=3208 ' \
            "${TEMP_DIR}/worker.log" > "${RESULT_DIR}/result.txt"
        for expected_mode in optimized trace-off fusion-only baseline; do
            mode_count="$(
                grep -F -c -- "W4ME_UNTANGLE_BENCH mode=${expected_mode} frames=3208 " \
                    "${RESULT_DIR}/result.txt"
            )"
            if [[ "${mode_count}" -ne 1 ]]; then
                printf 'error: comparison receipt is missing mode %s\n' "${expected_mode}" >&2
                exit 1
            fi
        done
    else
        if ! grep -F -- "W4ME_UNTANGLE_BENCH mode=${MODE} frames=3208 " \
            "${TEMP_DIR}/worker.log" > "${RESULT_DIR}/result.txt"; then
            printf 'error: KEmulator did not produce the Untangle benchmark receipt\n' >&2
            exit 1
        fi
    fi
    if grep -F -v -q -- ' fast-paths=0 framebuffer-fnv1a=bc0231d9 ' \
        "${RESULT_DIR}/result.txt"; then
        printf 'error: Untangle benchmark was not exact generic execution\n' >&2
        exit 1
    fi
    if [[ "${MODE}" = "comparison" ]]; then
        grep -E -- \
            'W4ME_UNTANGLE_PHASE mode=(optimized|trace-off|fusion-only|baseline) ' \
            "${TEMP_DIR}/worker.log" > "${RESULT_DIR}/phases.txt"
    else
        grep -F -- "W4ME_UNTANGLE_PHASE mode=${MODE} " \
            "${TEMP_DIR}/worker.log" > "${RESULT_DIR}/phases.txt"
    fi
    source_jar_sha256="$(sha256_file "${SOURCE_JAR}")"
    cartridge_sha256="$(sha256_file "${ROOT_DIR}/cartridges/untangle.wasm")"
    {
        printf 'timing-authoritative=no runtime=KEmulator-HotSpot\n'
        printf 'source-jar-sha256=%s\n' "${source_jar_sha256}"
        printf 'cartridge-sha256=%s\n' "${cartridge_sha256}"
        if [[ -f "${RESULT_DIR}/profile.txt" ]]; then
            cat -- "${RESULT_DIR}/profile.txt"
        fi
        cat -- "${RESULT_DIR}/phases.txt" "${RESULT_DIR}/result.txt"
    } > "${RESULT_DIR}/receipt.txt"
    cat -- "${RESULT_DIR}/receipt.txt"
}
