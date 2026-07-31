#!/usr/bin/env bash
# Sourced by tools/kemu/run.sh; not a standalone entrypoint.
set -euo pipefail

cmd_bench_generic_corpus() {

    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    WORKLOAD="${2:-rubido}"
    RUNS="${3:-3}"
    PROFILE="${4:-phone}"
    case "${WORKLOAD}" in
        waternet)
            PROBE_CLASS="w4me.midp.WaternetGenericCorpusBenchmarkMidlet"
            PROBE_SOURCE="${ROOT_DIR}/src/test/java/w4me/midp/WaternetGenericCorpusBenchmarkMidlet.java"
            CARTRIDGE="${ROOT_DIR}/cartridges/waternet.wasm"
            EXPECTED_FRAMEBUFFER="14e0f616"
            ;;
        rubido)
            PROBE_CLASS="w4me.midp.RubidoGenericCorpusBenchmarkMidlet"
            PROBE_SOURCE="${ROOT_DIR}/src/test/java/w4me/midp/RubidoGenericCorpusBenchmarkMidlet.java"
            CARTRIDGE="${ROOT_DIR}/cartridges/rubido.wasm"
            EXPECTED_FRAMEBUFFER="47462cbf"
            ;;
        duck-maze)
            PROBE_CLASS="w4me.midp.DuckMazeGenericCorpusBenchmarkMidlet"
            PROBE_SOURCE="${ROOT_DIR}/src/test/java/w4me/midp/DuckMazeGenericCorpusBenchmarkMidlet.java"
            CARTRIDGE="${ROOT_DIR}/cartridges/duck-maze.wasm"
            EXPECTED_FRAMEBUFFER="1ae224ce"
            ;;
        game-of-life-zig-edition)
            PROBE_CLASS="w4me.midp.GameOfLifeGenericCorpusBenchmarkMidlet"
            PROBE_SOURCE="${ROOT_DIR}/src/test/java/w4me/midp/GameOfLifeGenericCorpusBenchmarkMidlet.java"
            CARTRIDGE="${ROOT_DIR}/cartridges/game-of-life-zig-edition.wasm"
            EXPECTED_FRAMEBUFFER="a9255758"
            ;;
        *)
            printf 'error: workload must be waternet, rubido, duck-maze, or game-of-life-zig-edition\n' >&2
            exit 2
            ;;
    esac
    if ! [[ "${RUNS}" =~ ^[13579]$ ]]; then
        printf 'error: sample count must be one of 1, 3, 5, 7, 9\n' >&2
        exit 2
    fi
    if [[ "${PROFILE}" != "phone" ]] && [[ "${PROFILE}" != "ordinary" ]]; then
        printf 'error: profile must be phone or ordinary\n' >&2
        exit 2
    fi

    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/benchmark-generic-corpus-${WORKLOAD}-${PROFILE}"
    TEMP_DIR="$(mktemp -d)"
    BENCHMARK_JAR="${TEMP_DIR}/generic-corpus-benchmark.jar"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop > /dev/null 2>&1 || true
        rm -rf -- "${TEMP_DIR}"
    }
    trap cleanup EXIT

    rm -rf -- "${RESULT_DIR}"
    mkdir -p -- "${RESULT_DIR}" "${TEMP_DIR}/classes"
    cp -- "${SOURCE_JAR}" "${BENCHMARK_JAR}"
    cp -- "${CARTRIDGE}" "${TEMP_DIR}/classes/benchmark-cart.wasm"
    javac \
        -source "${J2ME_SOURCE}" \
        -target "${J2ME_TARGET}" \
        -Xlint:-options \
        -classpath "${MIDP_API_JAR}:${ROOT_DIR}/build/midlet/classes" \
        -d "${TEMP_DIR}/classes" \
        "${FRAMEBUFFER_ORACLE_SOURCE}" \
        "${ROOT_DIR}/src/test/java/w4me/midp/GenericCorpusBenchmarkMidlet.java" \
        "${PROBE_SOURCE}"
    find "${TEMP_DIR}/classes" -exec touch -h -t 198001010000.00 -- {} +
    jar uf "${BENCHMARK_JAR}" -C "${TEMP_DIR}/classes" .
    {
        printf 'MIDlet-Name: W4ME Generic Corpus %s Benchmark\n' "${WORKLOAD}"
        printf 'MIDlet-1: W4ME Generic Corpus %s Benchmark,,%s\n' \
            "${WORKLOAD}" "${PROBE_CLASS}"
    } > "${TEMP_DIR}/probe.mf"
    jar ufm "${BENCHMARK_JAR}" "${TEMP_DIR}/probe.mf" \
        2> "${TEMP_DIR}/manifest.log"

    source_sha256="$(sha256sum -- "${SOURCE_JAR}" | cut -d ' ' -f 1)"
    benchmark_sha256="$(sha256sum -- "${BENCHMARK_JAR}" | cut -d ' ' -f 1)"
    cartridge_sha256="$(sha256sum -- "${CARTRIDGE}" | cut -d ' ' -f 1)"
    : > "${RESULT_DIR}/samples.txt"

    run=1
    while [[ "${run}" -le "${RUNS}" ]]; do
        profile_file="${RESULT_DIR}/profile-${run}.txt"
        if [[ "${PROFILE}" = "phone" ]]; then
            "${ROOT_DIR}/tools/kemu/run.sh" phone "${BENCHMARK_JAR}" > "${profile_file}"
        else
            KEMU_SIZE="${KEMU_SIZE:-240x320}" \
                "${ROOT_DIR}/tools/kemu/run.sh" session start "${BENCHMARK_JAR}" > /dev/null
            printf 'ORDINARY_PROFILE screen=%s\n' "${KEMU_SIZE:-240x320}" > "${profile_file}"
        fi
        kemu_wait_log "W4ME_CORPUS_BENCH workload=${WORKLOAD} " 120000
        "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs read \
            > "${RESULT_DIR}/worker-${run}.log"
        "${ROOT_DIR}/tools/kemu/run.sh" session stop > /dev/null 2>&1 || true
        if grep -F -q -- 'W4ME_CORPUS_BENCH_ERROR ' "${RESULT_DIR}/worker-${run}.log"; then
            printf 'error: generic corpus benchmark failed for %s sample %s\n' \
                "${WORKLOAD}" "${run}" >&2
            exit 1
        fi
        if ! grep -F -- "W4ME_CORPUS_BENCH workload=${WORKLOAD} " \
            "${RESULT_DIR}/worker-${run}.log" > "${RESULT_DIR}/result-${run}.txt"; then
            printf 'error: generic corpus benchmark receipt missing for %s sample %s\n' \
                "${WORKLOAD}" "${run}" >&2
            exit 1
        fi
        if ! grep -F -q -- " fast-paths=0 " "${RESULT_DIR}/result-${run}.txt" \
            || ! grep -F -q -- " framebuffer-fnv1a=${EXPECTED_FRAMEBUFFER}" \
                "${RESULT_DIR}/result-${run}.txt"; then
            printf 'error: generic corpus benchmark was not exact generic execution\n' >&2
            exit 1
        fi
        elapsed_ms="$(sed -n \
            's/.* median-elapsed-ms=\([0-9][0-9]*\) .*/\1/p' \
            "${RESULT_DIR}/result-${run}.txt")"
        if ! [[ "${elapsed_ms}" =~ ^[0-9]+$ ]]; then
            printf 'error: generic corpus sample %s has no elapsed time\n' "${run}" >&2
            exit 1
        fi
        printf '%s %s\n' "${run}" "${elapsed_ms}" >> "${RESULT_DIR}/samples.txt"
        run=$((run + 1))
    done

    sort -n -k2,2 -- "${RESULT_DIR}/samples.txt" > "${RESULT_DIR}/samples-sorted.txt"
    median_line=$((RUNS / 2 + 1))
    median="$(sed -n "${median_line}p" "${RESULT_DIR}/samples-sorted.txt")"
    median_run="${median%% *}"
    median_ms="${median##* }"
    {
        printf 'GENERIC_CORPUS_PHONE_MATRIX workload=%s profile=%s samples=%s median-run=%s median-elapsed-ms=%s\n' \
            "${WORKLOAD}" "${PROFILE}" "${RUNS}" "${median_run}" "${median_ms}"
        printf 'timing-authoritative=no runtime=KEmulator-HotSpot\n'
        printf 'source-jar-sha256=%s\n' "${source_sha256}"
        printf 'benchmark-jar-sha256=%s\n' "${benchmark_sha256}"
        printf 'cartridge-sha256=%s\n' "${cartridge_sha256}"
        printf 'samples='
        awk 'BEGIN { first=1 } { if (!first) printf ","; printf "%s:%s", $1, $2; first=0 } END { printf "\n" }' \
            "${RESULT_DIR}/samples.txt"
        cat -- "${RESULT_DIR}/profile-${median_run}.txt"
        cat -- "${RESULT_DIR}/result-${median_run}.txt"
    } > "${RESULT_DIR}/receipt.txt"
    cat -- "${RESULT_DIR}/receipt.txt"
}

cmd_bench_generic_w4ir_matrix() {
    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    MODE="${2:-optimized}"
    RUNS="${3:-3}"
    PROFILE="${4:-phone}"
    case "${MODE}" in
        optimized | direct-intrinsics-off | trace-off | fusion-only | baseline) ;;
        *)
            printf 'error: mode must be optimized, direct-intrinsics-off, trace-off, fusion-only, or baseline\n' >&2
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

    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/benchmark-generic-w4ir-matrix-${MODE}-${PROFILE}"
    CHILD_DIR="${ROOT_DIR}/build/reports/kemu/benchmark-generic-w4ir-${MODE}"
    if [[ "${PROFILE}" = "phone" ]] || [[ "${PROFILE}" = "1" ]]; then
        CHILD_DIR="${CHILD_DIR}-phone"
    fi
    rm -rf -- "${RESULT_DIR}"
    mkdir -p -- "${RESULT_DIR}"
    : > "${RESULT_DIR}/averages.txt"

    run=1
    while [[ "${run}" -le "${RUNS}" ]]; do
        if ! "${ROOT_DIR}/tools/kemu/run.sh" bench generic-w4ir \
            "${SOURCE_JAR}" "${MODE}" "${PROFILE}" \
            > "${RESULT_DIR}/sample-${run}.out"; then
            "${ROOT_DIR}/tools/kemu/run.sh" session stop > /dev/null 2>&1 || true
            sleep 2
            "${ROOT_DIR}/tools/kemu/run.sh" bench generic-w4ir \
                "${SOURCE_JAR}" "${MODE}" "${PROFILE}" \
                > "${RESULT_DIR}/sample-${run}.out"
        fi
        cp -- "${CHILD_DIR}/receipt.txt" "${RESULT_DIR}/receipt-${run}.txt"
        average_ms="$(sed -n \
            's/.*W4ME_BENCH .* update-average-ms=\([0-9][0-9]*\) .*/\1/p' \
            "${RESULT_DIR}/receipt-${run}.txt")"
        if ! [[ "${average_ms}" =~ ^[0-9]+$ ]]; then
            printf 'error: sample %s has no update average\n' "${run}" >&2
            exit 1
        fi
        printf '%s %s\n' "${run}" "${average_ms}" >> "${RESULT_DIR}/averages.txt"
        sleep 2
        run=$((run + 1))
    done

    sort -n -k2,2 -- "${RESULT_DIR}/averages.txt" > "${RESULT_DIR}/averages-sorted.txt"
    median_line=$((RUNS / 2 + 1))
    median="$(sed -n "${median_line}p" "${RESULT_DIR}/averages-sorted.txt")"
    median_run="${median%% *}"
    median_ms="${median##* }"
    {
        printf 'GENERIC_W4IR_MATRIX mode=%s profile=%s samples=%s median-run=%s median-update-average-ms=%s\n' \
            "${MODE}" "${PROFILE}" "${RUNS}" "${median_run}" "${median_ms}"
        printf 'timing-authoritative=no runtime=KEmulator-HotSpot\n'
        printf 'samples='
        awk 'BEGIN { first=1 } { if (!first) printf ","; printf "%s:%s", $1, $2; first=0 } END { printf "\n" }' \
            "${RESULT_DIR}/averages.txt"
        cat -- "${RESULT_DIR}/receipt-${median_run}.txt"
    } > "${RESULT_DIR}/receipt.txt"
    cat -- "${RESULT_DIR}/receipt.txt"
}

cmd_bench_generic_w4ir() {

    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    MODE="${2:-optimized}"
    PROFILE="${3:-${KEMU_PHONE_PROFILE:-0}}"
    if [[ "${MODE}" = "baseline" ]]; then
        PROBE_SOURCE="${ROOT_DIR}/src/test/java/w4me/midp/GenericW4IrBaselineBenchmarkMidlet.java"
        PROBE_CLASS="w4me.midp.GenericW4IrBaselineBenchmarkMidlet"
        EXPECTED_FUSIONS="disabled"
        EXPECTED_COMPACT="disabled"
        EXPECTED_TRACE="disabled"
        EXPECTED_DIRECT_INTRINSICS="disabled"
    elif [[ "${MODE}" = "fusion-only" ]]; then
        PROBE_SOURCE="${ROOT_DIR}/src/test/java/w4me/midp/GenericW4IrFusionBenchmarkMidlet.java"
        PROBE_CLASS="w4me.midp.GenericW4IrFusionBenchmarkMidlet"
        EXPECTED_FUSIONS="enabled"
        EXPECTED_COMPACT="disabled"
        EXPECTED_TRACE="enabled"
        EXPECTED_DIRECT_INTRINSICS="enabled"
    elif [[ "${MODE}" = "trace-off" ]]; then
        PROBE_SOURCE="${ROOT_DIR}/src/test/java/w4me/midp/GenericW4IrTraceOffBenchmarkMidlet.java"
        PROBE_CLASS="w4me.midp.GenericW4IrTraceOffBenchmarkMidlet"
        EXPECTED_FUSIONS="enabled"
        EXPECTED_COMPACT="enabled"
        EXPECTED_TRACE="disabled"
        EXPECTED_DIRECT_INTRINSICS="enabled"
    elif [[ "${MODE}" = "direct-intrinsics-off" ]]; then
        PROBE_SOURCE="${ROOT_DIR}/src/test/java/w4me/midp/GenericW4IrDirectIntrinsicsOffBenchmarkMidlet.java"
        PROBE_CLASS="w4me.midp.GenericW4IrDirectIntrinsicsOffBenchmarkMidlet"
        EXPECTED_FUSIONS="enabled"
        EXPECTED_COMPACT="enabled"
        EXPECTED_TRACE="enabled"
        EXPECTED_DIRECT_INTRINSICS="disabled"
    elif [[ "${MODE}" = "optimized" ]]; then
        PROBE_SOURCE="${ROOT_DIR}/src/test/java/w4me/midp/GenericW4IrBenchmarkMidlet.java"
        PROBE_CLASS="w4me.midp.GenericW4IrBenchmarkMidlet"
        EXPECTED_FUSIONS="enabled"
        EXPECTED_COMPACT="enabled"
        EXPECTED_TRACE="enabled"
        EXPECTED_DIRECT_INTRINSICS="enabled"
    else
        printf 'usage: %s [jar] [optimized|direct-intrinsics-off|trace-off|fusion-only|baseline] [0|phone]\n' \
            "$0" >&2
        exit 2
    fi
    if [[ "${PROFILE}" != "0" ]] && [[ "${PROFILE}" != "1" ]] \
        && [[ "${PROFILE}" != "phone" ]]; then
        printf 'usage: %s [jar] [optimized|direct-intrinsics-off|trace-off|fusion-only|baseline] [0|phone]\n' \
            "$0" >&2
        exit 2
    fi
    PROFILE_SUFFIX=""
    PROFILE_NAME="ordinary"
    if [[ "${PROFILE}" = "1" ]] || [[ "${PROFILE}" = "phone" ]]; then
        PROFILE_SUFFIX="-phone"
        PROFILE_NAME="phone"
    fi
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/benchmark-generic-w4ir-${MODE}${PROFILE_SUFFIX}"
    TEMP_DIR="$(mktemp -d)"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop > /dev/null 2>&1 || true
        rm -rf -- "${TEMP_DIR}"
    }
    trap cleanup EXIT

    rm -rf -- "${RESULT_DIR}"
    mkdir -p -- "${RESULT_DIR}" "${TEMP_DIR}/classes"
    cp -- "${SOURCE_JAR}" "${TEMP_DIR}/generic-w4ir-benchmark.jar"
    compile_diagnostic_runtime \
        "${TEMP_DIR}/diagnostic-classes" \
        "${TEMP_DIR}/diagnostic-sources.list"
    jar uf "${TEMP_DIR}/generic-w4ir-benchmark.jar" \
        -C "${TEMP_DIR}/diagnostic-classes" .
    javac \
        -source "${J2ME_SOURCE}" \
        -target "${J2ME_TARGET}" \
        -Xlint:-options \
        -classpath "${MIDP_API_JAR}:${TEMP_DIR}/diagnostic-classes" \
        -d "${TEMP_DIR}/classes" \
        "${FRAMEBUFFER_ORACLE_SOURCE}" \
        "${ROOT_DIR}/src/test/java/w4me/midp/DiagnosticW4MeMidlet.java" \
        "${ROOT_DIR}/src/test/java/w4me/midp/DiagnosticW4SessionMonitor.java" \
        "${PROBE_SOURCE}"
    jar uf "${TEMP_DIR}/generic-w4ir-benchmark.jar" -C "${TEMP_DIR}/classes" .
    {
        printf 'MIDlet-Name: W4ME Generic W4IR %s Benchmark\n' "${MODE}"
        printf 'MIDlet-1: W4ME Generic W4IR %s Benchmark,,%s\n' \
            "${MODE}" "${PROBE_CLASS}"
    } > "${TEMP_DIR}/probe.mf"
    jar ufm "${TEMP_DIR}/generic-w4ir-benchmark.jar" "${TEMP_DIR}/probe.mf" \
        2> "${TEMP_DIR}/manifest.log"
    BENCHMARK_SHA256="$(
        sha256sum -- "${TEMP_DIR}/generic-w4ir-benchmark.jar" | cut -d ' ' -f 1
    )"

    if [[ "${PROFILE}" = "1" ]] || [[ "${PROFILE}" = "phone" ]]; then
        "${ROOT_DIR}/tools/kemu/run.sh" phone \
            "${TEMP_DIR}/generic-w4ir-benchmark.jar" > "${RESULT_DIR}/profile.txt"
    else
        KEMU_SIZE="${KEMU_SIZE:-240x320}" "${ROOT_DIR}/tools/kemu/run.sh" session \
            start "${TEMP_DIR}/generic-w4ir-benchmark.jar" > /dev/null
    fi
    kemu_wait_log 'W4ME_BENCH cart=plasma-cube frames=120 ' 120000
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs read \
        > "${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"

    if ! grep -F -- 'W4ME_BENCH cart=plasma-cube frames=120 ' \
        "${TEMP_DIR}/worker.log" > "${RESULT_DIR}/result.txt"; then
        printf 'error: KEmulator did not produce the generic W4IR benchmark receipt\n' >&2
        exit 1
    fi
    if ! grep -F -q -- ' fast-paths=0 ' "${RESULT_DIR}/result.txt"; then
        printf 'error: generic W4IR benchmark used a cartridge-specific fast path\n' >&2
        exit 1
    fi
    if ! grep -F -q -- " extended-fusions=${EXPECTED_FUSIONS} " \
        "${RESULT_DIR}/result.txt"; then
        printf 'error: generic W4IR benchmark fusion mode mismatch\n' >&2
        exit 1
    fi
    if [[ "${EXPECTED_COMPACT}" = "enabled" ]]; then
        if ! grep -E -q -- ' compact-blocks=[1-9][0-9]* ' "${RESULT_DIR}/result.txt"; then
            printf 'error: compact W4IR executor did not run\n' >&2
            exit 1
        fi
    elif ! grep -F -q -- ' compact-blocks=0 ' "${RESULT_DIR}/result.txt"; then
        printf 'error: compact W4IR executor was not disabled\n' >&2
        exit 1
    fi
    if [[ "${EXPECTED_TRACE}" = "enabled" ]]; then
        if ! grep -E -q -- ' trace-loops=[1-9][0-9]* ' "${RESULT_DIR}/result.txt"; then
            printf 'error: W4IR trace executor did not run\n' >&2
            exit 1
        fi
    elif ! grep -F -q -- ' trace-loops=0 ' "${RESULT_DIR}/result.txt"; then
        printf 'error: W4IR trace executor was not disabled\n' >&2
        exit 1
    fi
    if ! grep -F -q -- " direct-numeric-intrinsics=${EXPECTED_DIRECT_INTRINSICS} " \
        "${RESULT_DIR}/result.txt"; then
        printf 'error: generic W4IR benchmark direct intrinsic mode mismatch\n' >&2
        exit 1
    fi
    source_jar_sha256="$(sha256_file "${SOURCE_JAR}")"
    {
        printf 'timing-authoritative=no runtime=KEmulator-HotSpot\n'
        printf 'source-jar-sha256=%s\n' "${source_jar_sha256}"
        printf 'benchmark-jar-sha256=%s\n' "${BENCHMARK_SHA256}"
        printf 'profile=%s\n' "${PROFILE_NAME}"
        if [[ -f "${RESULT_DIR}/profile.txt" ]]; then
            cat -- "${RESULT_DIR}/profile.txt"
        fi
        cat -- "${RESULT_DIR}/result.txt"
    } > "${RESULT_DIR}/receipt.txt"
    if [[ -f "${RESULT_DIR}/profile.txt" ]]; then
        cat -- "${RESULT_DIR}/profile.txt"
    fi
    cat -- "${RESULT_DIR}/result.txt"
}
