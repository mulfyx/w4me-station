#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"

cmd_bench() {
    # Reference-VM interpreter benchmark: compiles the current working tree
    # against local CLDC 1.1.1 system classes (rejecting any non-CLDC API
    # at compile time), preverifies it, and replays the recorded browser-oracle
    # routes on the local phoneME cldc_vm_r — a 32-bit, JIT-less C-interpreter
    # CLDC VM. Every oracle checkpoint (framebuffer FNV-1a, palette, input state)
    # is verified by a separate replay after the timed interval.
    #
    # This script intentionally does NOT source tools/container/env.sh: the local VM is
    # a host i686 binary and runs directly. Requirements: javac 8+ on PATH
    # and the 32-bit loader (/lib/ld-linux.so.2, glibc.i686, libstdc++.i686).
    #
    # usage: tools/phoneme/run.sh bench [cart ...] [--mode optimized|trace-off|fusion-only|baseline|all]
    #                         [--candidate current|seven-opcode|host-import-id|counterless|
    #                                      resident-baseline|dense-baseline|all|
    #                                      host-import-id-all|counterless-all|
    #                                      resident-fast-all|combined-all|dense-all|
    #                                      load-tee-all|branch-inline-all|
    #                                      branch-direct-all|branch-direct-vs-inline-all]
    #                         [--reps N] [--extra-frames N] [--heap-capacity 64M]
    #                         [--unverified-idle]
    #
    # Without --extra-frames, recorded routes stop on their final oracle
    # checkpoint. Duck Maze holds its last input through frame 154. Unknown
    # cartridges require the explicit --unverified-idle escape hatch.

    PHONEME_HOME="${PHONEME_HOME:-${ROOT_DIR}/.local/phoneme}"
    CLDC_VM="${PHONEME_HOME}/cldc_vm_r"
    PREVERIFY="${PHONEME_HOME}/preverify"
    CLDC_CLASSES="${PHONEME_HOME}/classes.zip"
    OUT_DIR="${ROOT_DIR}/build/reports/phoneme"
    RECEIPT="${OUT_DIR}/receipt.txt"
    PAIRED_STATS="${ROOT_DIR}/tools/phoneme/paired-stats.awk"
    INTERPRETER_CONFIG_SOURCE="${INTERPRETER_CONFIG_SOURCE:-${ROOT_DIR}/src/main/java/w4me/wasm/InterpreterBuildConfig.java}"

    [[ -f "${INTERPRETER_CONFIG_SOURCE}" ]] || {
        printf 'error: missing interpreter config: %s\n' \
            "${INTERPRETER_CONFIG_SOURCE}" >&2
        exit 1
    }

    hash_or_missing() {
        if [[ -f "$1" ]]; then
            sha256sum -- "$1" | cut -d ' ' -f 1
        else
            printf 'missing\n'
        fi
    }

    CARTS=()
    MODE="optimized"
    CANDIDATE="counterless"
    REPS=""
    EXTRA_FRAMES=""
    HEAP_CAPACITY="64M"
    ALLOW_UNVERIFIED_IDLE="no"
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --mode)
                MODE="$2"
                shift 2
                ;;
            --reps)
                REPS="$2"
                shift 2
                ;;
            --candidate)
                CANDIDATE="$2"
                shift 2
                ;;
            --extra-frames)
                EXTRA_FRAMES="$2"
                shift 2
                ;;
            --heap-capacity)
                HEAP_CAPACITY="$2"
                shift 2
                ;;
            --unverified-idle)
                ALLOW_UNVERIFIED_IDLE="yes"
                shift
                ;;
            *)
                CARTS+=("$1")
                shift
                ;;
        esac
    done
    if [[ "${#CARTS[@]}" -eq 0 ]]; then
        CARTS=(waternet rubido untangle game-of-life-zig-edition duck-maze)
    fi
    route_extra_frames() {
        if [[ -n "${EXTRA_FRAMES}" ]]; then
            printf '%s\n' "${EXTRA_FRAMES}"
        else
            case "$1" in
                duck-maze)
                    printf '48\n'
                    ;;
                waternet | rubido | untangle | game-of-life-zig-edition)
                    printf '1\n'
                    ;;
                *)
                    printf '60\n'
                    ;;
            esac
        fi
    }
    if [[ "${MODE}" = "all" ]]; then
        MODES=(optimized trace-off fusion-only baseline)
    else
        MODES=("${MODE}")
    fi
    PAIR_BASELINE=""
    PAIR_CANDIDATE=""
    case "${CANDIDATE}" in
        all)
            CANDIDATES=(current seven-opcode)
            PAIR_BASELINE="current"
            PAIR_CANDIDATE="seven-opcode"
            ;;
        host-import-id-all)
            CANDIDATES=(seven-opcode host-import-id)
            PAIR_BASELINE="seven-opcode"
            PAIR_CANDIDATE="host-import-id"
            ;;
        counterless-all)
            CANDIDATES=(host-import-id counterless)
            PAIR_BASELINE="host-import-id"
            PAIR_CANDIDATE="counterless"
            ;;
        resident-fast-all)
            CANDIDATES=(resident-baseline host-import-id)
            PAIR_BASELINE="resident-baseline"
            PAIR_CANDIDATE="host-import-id"
            ;;
        combined-all)
            CANDIDATES=(resident-baseline counterless)
            PAIR_BASELINE="resident-baseline"
            PAIR_CANDIDATE="counterless"
            ;;
        dense-all)
            CANDIDATES=(dense-baseline host-import-id)
            PAIR_BASELINE="dense-baseline"
            PAIR_CANDIDATE="host-import-id"
            ;;
        load-tee-all)
            CANDIDATES=(load-tee-baseline load-tee)
            PAIR_BASELINE="load-tee-baseline"
            PAIR_CANDIDATE="load-tee"
            ;;
        branch-inline-all)
            CANDIDATES=(branch-legacy branch-inline)
            PAIR_BASELINE="branch-legacy"
            PAIR_CANDIDATE="branch-inline"
            ;;
        branch-direct-all)
            CANDIDATES=(branch-legacy counterless)
            PAIR_BASELINE="branch-legacy"
            PAIR_CANDIDATE="counterless"
            ;;
        branch-direct-vs-inline-all)
            CANDIDATES=(branch-inline branch-direct)
            PAIR_BASELINE="branch-inline"
            PAIR_CANDIDATE="branch-direct"
            ;;
        current | seven-opcode | host-import-id | counterless | resident-baseline | dense-baseline | load-tee-baseline | load-tee | branch-legacy | branch-inline | branch-direct)
            CANDIDATES=("${CANDIDATE}")
            ;;
        *)
            printf 'error: unknown candidate: %s\n' "${CANDIDATE}" >&2
            exit 1
            ;;
    esac
    if [[ -z "${REPS}" ]]; then
        if [[ -n "${PAIR_BASELINE}" ]]; then
            REPS=8
        else
            REPS=3
        fi
    fi
    case "${REPS}" in
        '' | *[!0-9]*)
            printf 'error: reps must be a positive integer: %s\n' "${REPS}" >&2
            exit 1
            ;;
        *) ;;
    esac
    if [[ "${REPS}" -le 0 ]]; then
        printf 'error: reps must be positive: %s\n' "${REPS}" >&2
        exit 1
    fi
    case "${EXTRA_FRAMES}" in
        *[!0-9]*)
            printf 'error: extra frames must be a positive integer: %s\n' \
                "${EXTRA_FRAMES}" >&2
            exit 1
            ;;
        *) ;;
    esac
    if [[ -n "${EXTRA_FRAMES}" ]] && [[ "${EXTRA_FRAMES}" -le 0 ]]; then
        printf 'error: extra frames must be positive: %s\n' \
            "${EXTRA_FRAMES}" >&2
        exit 1
    fi
    if [[ -n "${EXTRA_FRAMES}" ]] \
        && [[ "${ALLOW_UNVERIFIED_IDLE}" != "yes" ]]; then
        printf 'error: --extra-frames extends beyond the recorded route\n' >&2
        printf 'hint: add --unverified-idle to mark the result as diagnostic\n' >&2
        exit 1
    fi
    case "${HEAP_CAPACITY}" in
        '' | [!0-9]* | *[!0-9KMGkmg]* | *[KMGkmg][0-9KMGkmg]*)
            printf 'error: heap capacity must be an integer with optional K, M, or G suffix: %s\n' \
                "${HEAP_CAPACITY}" >&2
            exit 1
            ;;
        *) ;;
    esac
    ROUTE_VERIFICATION="verified"
    if [[ "${ALLOW_UNVERIFIED_IDLE}" = "yes" ]]; then
        ROUTE_VERIFICATION="unverified-idle"
    fi
    for cart in "${CARTS[@]}"; do
        [[ -f "${ROOT_DIR}/cartridges/${cart}.wasm" ]] || {
            printf 'error: missing cartridge: %s\n' "${cart}" >&2
            exit 1
        }
        if [[ "${ROUTE_VERIFICATION}" = "verified" ]]; then
            for route_file in input.csv oracle.csv; do
                route_path="${ROOT_DIR}/testdata/oracles/${cart}/${route_file}"
                if [[ ! -s "${route_path}" ]] \
                    || ! awk 'NR > 1 && NF { found=1; exit } END { exit !found }' \
                        "${route_path}"; then
                    printf 'error: verified benchmark requires a non-empty %s for %s\n' \
                        "${route_file}" "${cart}" >&2
                    printf 'hint: use --unverified-idle only for an intentional diagnostic idle run\n' \
                        >&2
                    exit 1
                fi
            done
        fi
    done
    if [[ -n "${PAIR_BASELINE}" ]] && [[ $((REPS % 2)) -ne 0 ]]; then
        printf 'error: paired comparisons require an even rep count: %s\n' \
            "${REPS}" >&2
        exit 1
    fi
    [[ -f "${PAIRED_STATS}" ]] || {
        printf 'error: missing %s\n' "${PAIRED_STATS}" >&2
        exit 1
    }

    command -v javac > /dev/null || {
        printf 'error: javac not found on PATH\n' >&2
        exit 1
    }
    [[ -f "${CLDC_CLASSES}" ]] || {
        printf 'error: missing phoneME classes: %s\n' "${CLDC_CLASSES}" >&2
        printf 'hint: set PHONEME_HOME; see docs/performance.md\n' >&2
        exit 1
    }
    [[ -x "${PREVERIFY}" ]] || {
        printf 'error: missing phoneME preverifier: %s\n' "${PREVERIFY}" >&2
        printf 'hint: set PHONEME_HOME; see docs/performance.md\n' >&2
        exit 1
    }
    [[ -x "${CLDC_VM}" ]] || {
        printf 'error: missing %s\n' "${CLDC_VM}" >&2
        printf 'hint: set PHONEME_HOME; see docs/performance.md\n' >&2
        exit 1
    }
    VM_PROBE="$("${CLDC_VM}" 2>&1 || true)"
    if ! printf '%s' "${VM_PROBE}" | grep -q 'class not specified'; then
        printf 'error: %s does not run; install 32-bit glibc/libstdc++ (glibc.i686)\n' \
            "${CLDC_VM}" >&2
        exit 1
    fi

    rm -rf -- "${OUT_DIR}"
    mkdir -p -- "${OUT_DIR}/classes" "${OUT_DIR}/preverified"

    # Compile main sources plus the bench against the CLDC bootclasspath. This is
    # also the CLDC API lint: any java.* use outside CLDC 1.1.1 fails right here.
    # MIDP-dependent classes (MMAPI backends, RMS storage) are excluded: they need
    # javax.microedition.* and cannot exist on the headless CLDC VM.
    find "${ROOT_DIR}/src/main/java/w4me/wasm" \
        "${ROOT_DIR}/src/main/java/w4me/runtime" \
        -name '*.java' \
        ! -path "${ROOT_DIR}/src/main/java/w4me/wasm/InterpreterBuildConfig.java" \
        -print | sort \
        | xargs grep -L -E 'javax\.microedition|RmsW4IrStore|RmsDiskBackend' \
            > "${OUT_DIR}/sources.list"
    {
        printf '%s\n' "${INTERPRETER_CONFIG_SOURCE}"
        printf '%s\n' "${ROOT_DIR}/src/test/java/w4me/FramebufferOracle.java"
        printf '%s\n' "${ROOT_DIR}/src/test/java/w4me/PhoneMeArgbBandBench.java"
        printf '%s\n' "${ROOT_DIR}/src/test/java/w4me/PhoneMePcmBench.java"
        printf '%s\n' "${ROOT_DIR}/src/test/java/w4me/PhoneMeRouteBench.java"
    } >> "${OUT_DIR}/sources.list"
    javac \
        -nowarn \
        -encoding UTF-8 \
        -source 1.3 \
        -target 1.3 \
        -Xlint:-options \
        -bootclasspath "${CLDC_CLASSES}" \
        -d "${OUT_DIR}/classes" \
        @"${OUT_DIR}/sources.list"

    "${PREVERIFY}" -classpath "${CLDC_CLASSES}" -d "${OUT_DIR}/preverified" \
        "${OUT_DIR}/classes"

    stage_resources() {
        local destination="$1"
        local cart
        cp -- "${ROOT_DIR}/src/main/resources/w4font.bin" "${destination}/"
        for cart in "${CARTS[@]}"; do
            cp -- "${ROOT_DIR}/cartridges/${cart}.wasm" "${destination}/"
            if [[ -f "${ROOT_DIR}/testdata/oracles/${cart}/input.csv" ]]; then
                cp -- "${ROOT_DIR}/testdata/oracles/${cart}/input.csv" \
                    "${destination}/${cart}-input.csv"
            fi
            if [[ -f "${ROOT_DIR}/testdata/oracles/${cart}/oracle.csv" ]]; then
                cp -- "${ROOT_DIR}/testdata/oracles/${cart}/oracle.csv" \
                    "${destination}/${cart}-oracle.csv"
            fi
        done
    }

    build_alternate_artifact() {
        local name="$1"
        local config_source="$2"
        local classes="${OUT_DIR}/${name}-classes"
        local preverified="${OUT_DIR}/${name}-preverified"
        local sources="${OUT_DIR}/${name}-sources.list"
        mkdir -p -- "${classes}" "${preverified}"
        grep -v -F \
            "${INTERPRETER_CONFIG_SOURCE}" \
            "${OUT_DIR}/sources.list" > "${sources}"
        printf '%s\n' "${config_source}" >> "${sources}"
        javac \
            -nowarn \
            -encoding UTF-8 \
            -source 1.3 \
            -target 1.3 \
            -Xlint:-options \
            -bootclasspath "${CLDC_CLASSES}" \
            -d "${classes}" \
            @"${sources}"
        "${PREVERIFY}" -classpath "${CLDC_CLASSES}" \
            -d "${preverified}" \
            "${classes}"
        stage_resources "${preverified}"
    }

    build_dense_baseline_artifact() {
        local classes="${OUT_DIR}/dense-baseline-classes"
        local preverified="${OUT_DIR}/dense-baseline-preverified"
        local sources="${OUT_DIR}/dense-baseline-sources.list"
        mkdir -p -- "${classes}" "${preverified}"
        grep -v -F \
            "${ROOT_DIR}/src/main/java/w4me/wasm/OpcodeBuildConfig.java" \
            "${OUT_DIR}/sources.list" > "${sources}"
        printf '%s\n' \
            "${ROOT_DIR}/bench/configs/dense-baseline/java/w4me/wasm/OpcodeBuildConfig.java" \
            >> "${sources}"
        javac \
            -nowarn \
            -encoding UTF-8 \
            -source 1.3 \
            -target 1.3 \
            -Xlint:-options \
            -bootclasspath "${CLDC_CLASSES}" \
            -d "${classes}" \
            @"${sources}"
        "${PREVERIFY}" -classpath "${CLDC_CLASSES}" \
            -d "${preverified}" \
            "${classes}"
        stage_resources "${preverified}"
    }

    stage_resources "${OUT_DIR}/preverified"

    BUILD_COUNTERLESS="no"
    BUILD_RESIDENT_BASELINE="no"
    BUILD_DENSE_BASELINE="no"
    BUILD_BRANCH_LEGACY="no"
    BUILD_BRANCH_INLINE="no"
    BUILD_BRANCH_DIRECT="no"
    for candidate in "${CANDIDATES[@]}"; do
        case "${candidate}" in
            counterless)
                BUILD_COUNTERLESS="yes"
                ;;
            resident-baseline)
                BUILD_RESIDENT_BASELINE="yes"
                ;;
            dense-baseline)
                BUILD_DENSE_BASELINE="yes"
                ;;
            branch-legacy)
                BUILD_BRANCH_LEGACY="yes"
                ;;
            branch-inline)
                BUILD_BRANCH_INLINE="yes"
                ;;
            branch-direct)
                BUILD_BRANCH_DIRECT="yes"
                ;;
            *) ;;
        esac
    done
    if [[ "${BUILD_COUNTERLESS}" = "yes" ]]; then
        build_alternate_artifact \
            "counterless" \
            "${ROOT_DIR}/bench/configs/timed/java/w4me/wasm/InterpreterBuildConfig.java"
    fi
    if [[ "${BUILD_RESIDENT_BASELINE}" = "yes" ]]; then
        build_alternate_artifact \
            "resident-baseline" \
            "${ROOT_DIR}/bench/configs/resident-baseline/java/w4me/wasm/InterpreterBuildConfig.java"
    fi
    if [[ "${BUILD_DENSE_BASELINE}" = "yes" ]]; then
        build_dense_baseline_artifact
    fi
    if [[ "${BUILD_BRANCH_LEGACY}" = "yes" ]]; then
        build_alternate_artifact \
            "branch-legacy" \
            "${ROOT_DIR}/bench/configs/branch-legacy-timed/java/w4me/wasm/InterpreterBuildConfig.java"
    fi
    if [[ "${BUILD_BRANCH_INLINE}" = "yes" ]]; then
        build_alternate_artifact \
            "branch-inline" \
            "${ROOT_DIR}/bench/configs/branch-inline-timed/java/w4me/wasm/InterpreterBuildConfig.java"
    fi
    if [[ "${BUILD_BRANCH_DIRECT}" = "yes" ]]; then
        build_alternate_artifact \
            "branch-direct" \
            "${ROOT_DIR}/bench/configs/branch-direct-timed/java/w4me/wasm/InterpreterBuildConfig.java"
    fi
    # Bind every result to the exact preverified class/resource tree. The hash is
    # independent of file timestamps and includes the selected cartridges and
    # route oracles as well as all benchmarked classes.
    artifact_sha256() {
        (
            cd -- "$1"
            find . -type f -print0 | sort -z | xargs -0 sha256sum \
                | sha256sum | cut -d ' ' -f 1
        )
    }

    candidate_preverified() {
        case "$1" in
            counterless)
                printf '%s\n' "${OUT_DIR}/counterless-preverified"
                ;;
            resident-baseline)
                printf '%s\n' "${OUT_DIR}/resident-baseline-preverified"
                ;;
            dense-baseline)
                printf '%s\n' "${OUT_DIR}/dense-baseline-preverified"
                ;;
            branch-legacy)
                printf '%s\n' "${OUT_DIR}/branch-legacy-preverified"
                ;;
            branch-inline)
                printf '%s\n' "${OUT_DIR}/branch-inline-preverified"
                ;;
            branch-direct)
                printf '%s\n' "${OUT_DIR}/branch-direct-preverified"
                ;;
            *)
                printf '%s\n' "${OUT_DIR}/preverified"
                ;;
        esac
    }

    BASE_ARTIFACT_SHA256="$(artifact_sha256 "${OUT_DIR}/preverified")"
    COUNTERLESS_ARTIFACT_SHA256=""
    if [[ "${BUILD_COUNTERLESS}" = "yes" ]]; then
        COUNTERLESS_ARTIFACT_SHA256="$(
            artifact_sha256 "${OUT_DIR}/counterless-preverified"
        )"
    fi
    RESIDENT_BASELINE_ARTIFACT_SHA256=""
    if [[ "${BUILD_RESIDENT_BASELINE}" = "yes" ]]; then
        RESIDENT_BASELINE_ARTIFACT_SHA256="$(
            artifact_sha256 "${OUT_DIR}/resident-baseline-preverified"
        )"
    fi
    DENSE_BASELINE_ARTIFACT_SHA256=""
    if [[ "${BUILD_DENSE_BASELINE}" = "yes" ]]; then
        DENSE_BASELINE_ARTIFACT_SHA256="$(
            artifact_sha256 "${OUT_DIR}/dense-baseline-preverified"
        )"
    fi
    BRANCH_LEGACY_ARTIFACT_SHA256=""
    if [[ "${BUILD_BRANCH_LEGACY}" = "yes" ]]; then
        BRANCH_LEGACY_ARTIFACT_SHA256="$(
            artifact_sha256 "${OUT_DIR}/branch-legacy-preverified"
        )"
    fi
    BRANCH_INLINE_ARTIFACT_SHA256=""
    if [[ "${BUILD_BRANCH_INLINE}" = "yes" ]]; then
        BRANCH_INLINE_ARTIFACT_SHA256="$(
            artifact_sha256 "${OUT_DIR}/branch-inline-preverified"
        )"
    fi
    BRANCH_DIRECT_ARTIFACT_SHA256=""
    if [[ "${BUILD_BRANCH_DIRECT}" = "yes" ]]; then
        BRANCH_DIRECT_ARTIFACT_SHA256="$(
            artifact_sha256 "${OUT_DIR}/branch-direct-preverified"
        )"
    fi
    candidate_artifact_sha256() {
        case "$1" in
            counterless)
                printf '%s\n' "${COUNTERLESS_ARTIFACT_SHA256}"
                ;;
            resident-baseline)
                printf '%s\n' "${RESIDENT_BASELINE_ARTIFACT_SHA256}"
                ;;
            dense-baseline)
                printf '%s\n' "${DENSE_BASELINE_ARTIFACT_SHA256}"
                ;;
            branch-legacy)
                printf '%s\n' "${BRANCH_LEGACY_ARTIFACT_SHA256}"
                ;;
            branch-inline)
                printf '%s\n' "${BRANCH_INLINE_ARTIFACT_SHA256}"
                ;;
            branch-direct)
                printf '%s\n' "${BRANCH_DIRECT_ARTIFACT_SHA256}"
                ;;
            *)
                printf '%s\n' "${BASE_ARTIFACT_SHA256}"
                ;;
        esac
    }

    candidate_diagnostic_counters() {
        if [[ "$1" = "counterless" ]] \
            || [[ "$1" = "branch-legacy" ]] \
            || [[ "$1" = "branch-inline" ]] \
            || [[ "$1" = "branch-direct" ]]; then
            printf 'off\n'
        else
            printf 'on\n'
        fi
    }

    candidate_inline_branch_fast_path() {
        if [[ "$1" = "branch-inline" ]]; then
            printf 'on\n'
        else
            printf 'off\n'
        fi
    }

    candidate_direct_branch_fast_path() {
        if [[ "$1" = "branch-legacy" ]] || [[ "$1" = "branch-inline" ]]; then
            printf 'off\n'
        else
            printf 'on\n'
        fi
    }

    candidate_resident_fast_path() {
        if [[ "$1" = "resident-baseline" ]]; then
            printf 'off\n'
        else
            printf 'on\n'
        fi
    }

    candidate_dense_opcode_dispatch() {
        if [[ "$1" = "dense-baseline" ]]; then
            printf 'off\n'
        else
            printf 'on\n'
        fi
    }

    candidate_load_tee_fusions() {
        if [[ "$1" = "load-tee-baseline" ]]; then
            printf 'off\n'
        else
            printf 'on\n'
        fi
    }

    if [[ "${#CANDIDATES[@]}" -eq 1 ]]; then
        EXECUTED_ARTIFACT_SHA256="$(
            candidate_artifact_sha256 "${CANDIDATES[0]}"
        )"
    else
        EXECUTED_ARTIFACT_SHA256="multiple"
    fi

    SOURCE_HEAD="$(git -C "${ROOT_DIR}" rev-parse HEAD 2> /dev/null || printf 'unversioned')"
    source_status="$(git -C "${ROOT_DIR}" status --porcelain --untracked-files=normal)"
    if [[ -z "${source_status}" ]]; then
        SOURCE_DIRTY="no"
    else
        SOURCE_DIRTY="yes"
    fi
    cldc_vm_sha256="$(hash_or_missing "${CLDC_VM}")"
    cldc_classes_sha256="$(hash_or_missing "${CLDC_CLASSES}")"
    preverify_sha256="$(hash_or_missing "${PREVERIFY}")"

    {
        printf 'phoneme-bench receipt\n'
        printf 'vm-arch=i686 vm-sha256=%s\n' "${cldc_vm_sha256}"
        printf 'classes-sha256=%s\n' "${cldc_classes_sha256}"
        printf 'preverify-sha256=%s\n' "${preverify_sha256}"
        printf 'artifact-sha256=%s\n' "${EXECUTED_ARTIFACT_SHA256}"
        printf 'base-artifact-sha256=%s\n' "${BASE_ARTIFACT_SHA256}"
        printf 'source-head=%s source-dirty=%s\n' "${SOURCE_HEAD}" "${SOURCE_DIRTY}"
        printf 'cldc-api-lint=pass source=1.3 target=1.3 fast-paths=off\n'
        printf 'timer=System.currentTimeMillis paired-statistic=median-paired-effect paired-acceptance-reps=8\n'
        printf 'reps=%s extra-frames=%s heap-capacity=%s verification=%s modes=%s candidates=%s\n' \
            "${REPS}" "${EXTRA_FRAMES:-per-route}" "${HEAP_CAPACITY}" \
            "${ROUTE_VERIFICATION}" "${MODES[*]}" "${CANDIDATES[*]}"
        for candidate in "${CANDIDATES[@]}"; do
            diagnostic_counters="$(candidate_diagnostic_counters "${candidate}")"
            resident_fast_path="$(candidate_resident_fast_path "${candidate}")"
            dense_opcode_dispatch="$(candidate_dense_opcode_dispatch "${candidate}")"
            load_tee_fusions="$(candidate_load_tee_fusions "${candidate}")"
            inline_branch_fast_path="$(candidate_inline_branch_fast_path "${candidate}")"
            direct_branch_fast_path="$(candidate_direct_branch_fast_path "${candidate}")"
            candidate_sha256="$(candidate_artifact_sha256 "${candidate}")"
            printf 'artifact candidate=%s diagnostic-counters=%s resident-fast-path=%s dense-opcode-dispatch=%s load-tee-fusions=%s inline-branch-fast-path=%s direct-branch-fast-path=%s sha256=%s\n' \
                "${candidate}" \
                "${diagnostic_counters}" \
                "${resident_fast_path}" \
                "${dense_opcode_dispatch}" \
                "${load_tee_fusions}" \
                "${inline_branch_fast_path}" \
                "${direct_branch_fast_path}" \
                "${candidate_sha256}"
        done
        for cart in "${CARTS[@]}"; do
            cart_extra_frames="$(route_extra_frames "${cart}")"
            cartridge_sha256="$(hash_or_missing "${ROOT_DIR}/cartridges/${cart}.wasm")"
            input_sha256="$(hash_or_missing "${ROOT_DIR}/testdata/oracles/${cart}/input.csv")"
            oracle_sha256="$(hash_or_missing "${ROOT_DIR}/testdata/oracles/${cart}/oracle.csv")"
            printf 'route cart=%s extra-frames=%s cartridge-sha256=%s input-sha256=%s oracle-sha256=%s\n' \
                "${cart}" \
                "${cart_extra_frames}" \
                "${cartridge_sha256}" \
                "${input_sha256}" \
                "${oracle_sha256}"
        done
    } > "${RECEIPT}"

    status=0
    for cart in "${CARTS[@]}"; do
        CART_EXTRA_FRAMES="$(route_extra_frames "${cart}")"
        for mode in "${MODES[@]}"; do
            for candidate in "${CANDIDATES[@]}"; do
                : > "${OUT_DIR}/${cart}-${mode}-${candidate}-samples.txt"
            done
            sample=0
            while [[ "${sample}" -lt "${REPS}" ]]; do
                if [[ -n "${PAIR_BASELINE}" ]] && [[ $((sample % 2)) -eq 1 ]]; then
                    SAMPLE_ORDER=("${PAIR_CANDIDATE}" "${PAIR_BASELINE}")
                else
                    SAMPLE_ORDER=("${CANDIDATES[@]}")
                fi
                printf 'paired-order cart=%s mode=%s sample=%s order=%s\n' \
                    "${cart}" "${mode}" "${sample}" "${SAMPLE_ORDER[*]}" \
                    | tee -a "${RECEIPT}"
                for candidate in "${SAMPLE_ORDER[@]}"; do
                    RESULT="${OUT_DIR}/${cart}-${mode}-${candidate}-${sample}.txt"
                    RUN_PREVERIFIED="$(candidate_preverified "${candidate}")"
                    printf '=== %s %s %s sample=%s\n' \
                        "${cart}" "${mode}" "${candidate}" "${sample}"
                    if "${CLDC_VM}" -EnableTicks "=HeapCapacity${HEAP_CAPACITY}" \
                        -classpath "${CLDC_CLASSES}:${RUN_PREVERIFIED}" \
                        w4me.PhoneMeRouteBench "${cart}" "${mode}" \
                        "${CART_EXTRA_FRAMES}" 1 "${candidate}" "${sample}" \
                        "${ROUTE_VERIFICATION}" \
                        > "${RESULT}" 2>&1; then
                        PASS_COUNT="$(grep -c 'phoneme-bench:pass' "${RESULT}" || true)"
                        if [[ "${PASS_COUNT}" -ne 1 ]]; then
                            printf 'FAIL %s %s %s sample=%s passes=%s\n' \
                                "${cart}" "${mode}" "${candidate}" "${sample}" \
                                "${PASS_COUNT}" | tee -a "${RECEIPT}"
                            cat "${RESULT}"
                            status=1
                            continue
                        fi
                        PASS_LINE="$(grep 'phoneme-bench:pass' "${RESULT}")"
                        printf '%s\n' "${PASS_LINE}" \
                            | tee -a "${RECEIPT}" \
                                "${OUT_DIR}/${cart}-${mode}-${candidate}-samples.txt"
                    else
                        printf 'FAIL %s %s %s sample=%s (vm exit)\n' \
                            "${cart}" "${mode}" "${candidate}" "${sample}" \
                            | tee -a "${RECEIPT}"
                        cat "${RESULT}"
                        status=1
                    fi
                done
                sample=$((sample + 1))
            done

            for candidate in "${CANDIDATES[@]}"; do
                SAMPLES="${OUT_DIR}/${cart}-${mode}-${candidate}-samples.txt"
                PASS_COUNT="$(wc -l < "${SAMPLES}")"
                if [[ "${PASS_COUNT}" -ne "${REPS}" ]]; then
                    printf 'FAIL %s %s %s expected-samples=%s actual-samples=%s\n' \
                        "${cart}" "${mode}" "${candidate}" "${REPS}" "${PASS_COUNT}" \
                        | tee -a "${RECEIPT}"
                    status=1
                    continue
                fi
                MEDIAN_US_PER_FRAME="$(
                    sed -n 's/.* us-per-frame=\([0-9][0-9]*\).*/\1/p' "${SAMPLES}" \
                        | sort -n \
                        | awk '{ value[NR] = $1 } END { \
                            if (NR % 2 == 1) print value[(NR + 1) / 2]; \
                            else printf "%.1f\n", (value[NR / 2] + value[NR / 2 + 1]) / 2.0; \
                        }'
                )"
                CANDIDATE_ARTIFACT_SHA256="$(
                    candidate_artifact_sha256 "${candidate}"
                )"
                printf 'phoneme-bench:median cart=%s mode=%s candidate=%s reps=%s us-per-frame=%s artifact-sha256=%s\n' \
                    "${cart}" "${mode}" "${candidate}" "${REPS}" "${MEDIAN_US_PER_FRAME}" \
                    "${CANDIDATE_ARTIFACT_SHA256}" | tee -a "${RECEIPT}"
            done
            if [[ -n "${PAIR_BASELINE}" ]]; then
                PAIR_FILE="${OUT_DIR}/${cart}-${mode}-${PAIR_BASELINE}-vs-${PAIR_CANDIDATE}-pairs.csv"
                printf 'sample,baseline_us_per_frame,candidate_us_per_frame,frames,order\n' \
                    > "${PAIR_FILE}"
                sample=0
                while [[ "${sample}" -lt "${REPS}" ]]; do
                    BASELINE_RESULT="${OUT_DIR}/${cart}-${mode}-${PAIR_BASELINE}-${sample}.txt"
                    CANDIDATE_RESULT="${OUT_DIR}/${cart}-${mode}-${PAIR_CANDIDATE}-${sample}.txt"
                    BASELINE_PASS="$(
                        sed -n 's/.*phoneme-bench:pass /phoneme-bench:pass /p' \
                            "${BASELINE_RESULT}"
                    )"
                    CANDIDATE_PASS="$(
                        sed -n 's/.*phoneme-bench:pass /phoneme-bench:pass /p' \
                            "${CANDIDATE_RESULT}"
                    )"
                    BASELINE_US="$(
                        printf '%s\n' "${BASELINE_PASS}" \
                            | sed -n 's/.* us-per-frame=\([0-9][0-9]*\).*/\1/p'
                    )"
                    CANDIDATE_US="$(
                        printf '%s\n' "${CANDIDATE_PASS}" \
                            | sed -n 's/.* us-per-frame=\([0-9][0-9]*\).*/\1/p'
                    )"
                    BASELINE_FRAMES="$(
                        printf '%s\n' "${BASELINE_PASS}" \
                            | sed -n 's/.* frames=\([0-9][0-9]*\).*/\1/p'
                    )"
                    CANDIDATE_FRAMES="$(
                        printf '%s\n' "${CANDIDATE_PASS}" \
                            | sed -n 's/.* frames=\([0-9][0-9]*\).*/\1/p'
                    )"
                    BASELINE_CHECKPOINTS="$(
                        printf '%s\n' "${BASELINE_PASS}" \
                            | sed -n 's/.* checkpoints=\([0-9][0-9]*\).*/\1/p'
                    )"
                    CANDIDATE_CHECKPOINTS="$(
                        printf '%s\n' "${CANDIDATE_PASS}" \
                            | sed -n 's/.* checkpoints=\([0-9][0-9]*\).*/\1/p'
                    )"
                    BASELINE_INSTRUCTIONS="$(
                        printf '%s\n' "${BASELINE_PASS}" \
                            | sed -n 's/.* instructions=\([0-9][0-9]*\).*/\1/p'
                    )"
                    CANDIDATE_INSTRUCTIONS="$(
                        printf '%s\n' "${CANDIDATE_PASS}" \
                            | sed -n 's/.* instructions=\([0-9][0-9]*\).*/\1/p'
                    )"
                    BASELINE_FRAMEBUFFER="$(
                        printf '%s\n' "${BASELINE_PASS}" \
                            | sed -n 's/.* final-framebuffer-fnv1a=\([0-9a-f][0-9a-f]*\).*/\1/p'
                    )"
                    CANDIDATE_FRAMEBUFFER="$(
                        printf '%s\n' "${CANDIDATE_PASS}" \
                            | sed -n 's/.* final-framebuffer-fnv1a=\([0-9a-f][0-9a-f]*\).*/\1/p'
                    )"
                    BASELINE_VERIFICATION="$(
                        printf '%s\n' "${BASELINE_PASS}" \
                            | sed -n 's/.* verification=\([^ ]*\).*/\1/p'
                    )"
                    CANDIDATE_VERIFICATION="$(
                        printf '%s\n' "${CANDIDATE_PASS}" \
                            | sed -n 's/.* verification=\([^ ]*\).*/\1/p'
                    )"
                    if [[ -z "${BASELINE_US}" ]] || [[ -z "${CANDIDATE_US}" ]] \
                        || [[ -z "${BASELINE_FRAMES}" ]] \
                        || [[ -z "${BASELINE_CHECKPOINTS}" ]] \
                        || [[ -z "${BASELINE_INSTRUCTIONS}" ]] \
                        || [[ -z "${BASELINE_FRAMEBUFFER}" ]] \
                        || [[ "${BASELINE_FRAMES}" != "${CANDIDATE_FRAMES}" ]] \
                        || [[ "${BASELINE_CHECKPOINTS}" != "${CANDIDATE_CHECKPOINTS}" ]] \
                        || [[ "${BASELINE_INSTRUCTIONS}" != "${CANDIDATE_INSTRUCTIONS}" ]] \
                        || [[ "${BASELINE_FRAMEBUFFER}" != "${CANDIDATE_FRAMEBUFFER}" ]] \
                        || [[ "${BASELINE_VERIFICATION}" != "${CANDIDATE_VERIFICATION}" ]] \
                        || { [[ "${ROUTE_VERIFICATION}" = "verified" ]] \
                            && [[ "${BASELINE_CHECKPOINTS}" -le 0 ]]; }; then
                        printf 'FAIL %s %s incomplete-or-inexact-pair sample=%s\n' \
                            "${cart}" "${mode}" "${sample}" | tee -a "${RECEIPT}"
                        status=1
                        sample=$((sample + 1))
                        continue
                    fi
                    if [[ $((sample % 2)) -eq 0 ]]; then
                        ORDER="baseline-first"
                    else
                        ORDER="candidate-first"
                    fi
                    printf '%s,%s,%s,%s,%s\n' \
                        "${sample}" "${BASELINE_US}" "${CANDIDATE_US}" \
                        "${BASELINE_FRAMES}" "${ORDER}" >> "${PAIR_FILE}"
                    sample=$((sample + 1))
                done
                PAIR_COUNT="$(awk 'NR > 1 { count++ } END { print count + 0 }' "${PAIR_FILE}")"
                if [[ "${PAIR_COUNT}" -ne "${REPS}" ]]; then
                    printf 'FAIL %s %s expected-pairs=%s actual-pairs=%s\n' \
                        "${cart}" "${mode}" "${REPS}" "${PAIR_COUNT}" \
                        | tee -a "${RECEIPT}"
                    status=1
                else
                    PAIR_RESULT="$(
                        awk -v source_dirty="${SOURCE_DIRTY}" \
                            -f "${PAIRED_STATS}" "${PAIR_FILE}"
                    )"
                    BASELINE_ARTIFACT_SHA256="$(
                        candidate_artifact_sha256 "${PAIR_BASELINE}"
                    )"
                    PAIR_CANDIDATE_ARTIFACT_SHA256="$(
                        candidate_artifact_sha256 "${PAIR_CANDIDATE}"
                    )"
                    printf 'phoneme-bench:paired cart=%s mode=%s baseline=%s candidate=%s %s baseline-artifact-sha256=%s candidate-artifact-sha256=%s pairs-file=%s\n' \
                        "${cart}" "${mode}" "${PAIR_BASELINE}" "${PAIR_CANDIDATE}" \
                        "${PAIR_RESULT}" "${BASELINE_ARTIFACT_SHA256}" \
                        "${PAIR_CANDIDATE_ARTIFACT_SHA256}" \
                        "$(basename -- "${PAIR_FILE}")" | tee -a "${RECEIPT}"
                fi
            fi
        done
    done

    printf 'receipt: %s\n' "${RECEIPT}"
    exit "${status}"
}

prepare_phoneme_component() {
    COMPONENT_OUT_DIR="$1"
    shift
    COMPONENT_SOURCES=()
    COMPONENT_RESOURCES=()
    COMPONENT_ARGUMENT_KIND="source"
    while [[ "$#" -gt 0 ]]; do
        if [[ "$1" = "--" ]]; then
            COMPONENT_ARGUMENT_KIND="resource"
        elif [[ "${COMPONENT_ARGUMENT_KIND}" = "source" ]]; then
            COMPONENT_SOURCES+=("$1")
        else
            COMPONENT_RESOURCES+=("$1")
        fi
        shift
    done
    if [[ "${#COMPONENT_SOURCES[@]}" -eq 0 ]]; then
        printf 'error: phoneME component requires at least one Java source\n' >&2
        exit 1
    fi
    PHONEME_HOME="${PHONEME_HOME:-${ROOT_DIR}/.local/phoneme}"
    COMPONENT_VM="${PHONEME_HOME}/cldc_vm_r"
    COMPONENT_PREVERIFY="${PHONEME_HOME}/preverify"
    COMPONENT_CLASSES="${PHONEME_HOME}/classes.zip"

    command -v javac > /dev/null || {
        printf 'error: javac not found on PATH\n' >&2
        exit 1
    }
    [[ -x "${COMPONENT_VM}" ]] || {
        printf 'error: missing executable %s\n' "${COMPONENT_VM}" >&2
        exit 1
    }
    [[ -x "${COMPONENT_PREVERIFY}" ]] || {
        printf 'error: missing executable %s\n' "${COMPONENT_PREVERIFY}" >&2
        exit 1
    }
    [[ -f "${COMPONENT_CLASSES}" ]] || {
        printf 'error: missing phoneME classes: %s\n' "${COMPONENT_CLASSES}" >&2
        exit 1
    }

    rm -rf -- "${COMPONENT_OUT_DIR}"
    mkdir -p -- \
        "${COMPONENT_OUT_DIR}/classes" \
        "${COMPONENT_OUT_DIR}/preverified"
    find "${ROOT_DIR}/src/main/java/w4me/wasm" \
        "${ROOT_DIR}/src/main/java/w4me/runtime" \
        -name '*.java' \
        ! -path "${ROOT_DIR}/src/main/java/w4me/wasm/InterpreterBuildConfig.java" \
        -print | sort \
        | xargs grep -L -E 'javax\.microedition|RmsW4IrStore|RmsDiskBackend' \
            > "${COMPONENT_OUT_DIR}/sources.list"
    {
        printf '%s\n' \
            "${ROOT_DIR}/bench/configs/timed/java/w4me/wasm/InterpreterBuildConfig.java"
        printf '%s\n' "${COMPONENT_SOURCES[@]}"
    } >> "${COMPONENT_OUT_DIR}/sources.list"
    javac \
        -nowarn \
        -encoding UTF-8 \
        -source 1.3 \
        -target 1.3 \
        -Xlint:-options \
        -bootclasspath "${COMPONENT_CLASSES}" \
        -d "${COMPONENT_OUT_DIR}/classes" \
        @"${COMPONENT_OUT_DIR}/sources.list"
    "${COMPONENT_PREVERIFY}" \
        -classpath "${COMPONENT_CLASSES}" \
        -d "${COMPONENT_OUT_DIR}/preverified" \
        "${COMPONENT_OUT_DIR}/classes"
    cp -- "${ROOT_DIR}/src/main/resources/w4font.bin" \
        "${COMPONENT_OUT_DIR}/preverified/"
    if [[ "${#COMPONENT_RESOURCES[@]}" -gt 0 ]]; then
        cp -- "${COMPONENT_RESOURCES[@]}" "${COMPONENT_OUT_DIR}/preverified/"
    fi

    COMPONENT_ARTIFACT_SHA256="$(
        cd -- "${COMPONENT_OUT_DIR}/preverified"
        find . -type f -print0 | sort -z | xargs -0 sha256sum \
            | sha256sum | cut -d ' ' -f 1
    )"
}

component_median() {
    sort -n -- "$1" \
        | awk '{ value[NR] = $1 } END { \
            if (NR % 2 == 1) print value[(NR + 1) / 2]; \
            else printf "%.1f\n", (value[NR / 2] + value[NR / 2 + 1]) / 2.0; \
        }'
}

write_component_header() {
    local receipt="$1"
    local component="$2"
    local parameters="$3"
    local source_head
    local source_dirty
    source_head="$(git -C "${ROOT_DIR}" rev-parse HEAD 2> /dev/null || printf 'unversioned')"
    local source_status
    source_status="$(git -C "${ROOT_DIR}" status --porcelain --untracked-files=normal)"
    if [[ -z "${source_status}" ]]; then
        source_dirty="no"
    else
        source_dirty="yes"
    fi
    local component_vm_sha256
    local component_classes_sha256
    local component_preverify_sha256
    component_vm_sha256="$(hash_or_missing "${COMPONENT_VM}")"
    component_classes_sha256="$(hash_or_missing "${COMPONENT_CLASSES}")"
    component_preverify_sha256="$(hash_or_missing "${COMPONENT_PREVERIFY}")"
    {
        printf 'phoneme-component-bench receipt\n'
        printf 'component=%s %s\n' "${component}" "${parameters}"
        printf 'vm-arch=i686 vm-sha256=%s\n' "${component_vm_sha256}"
        printf 'classes-sha256=%s\n' "${component_classes_sha256}"
        printf 'preverify-sha256=%s\n' "${component_preverify_sha256}"
        printf 'artifact-sha256=%s\n' "${COMPONENT_ARTIFACT_SHA256}"
        printf 'source-head=%s source-dirty=%s\n' "${source_head}" "${source_dirty}"
        printf 'cldc-api-lint=pass source=1.3 target=1.3 production-config=counterless\n'
    } > "${receipt}"
}

cmd_bench_pcm() {
    local workload="waternet"
    local cycles=20
    local reps=5
    local heap_capacity="64M"
    if [[ $# -gt 0 ]] && [[ "$1" != --* ]]; then
        workload="$1"
        shift
    fi
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --cycles)
                cycles="$2"
                shift 2
                ;;
            --reps)
                reps="$2"
                shift 2
                ;;
            --heap-capacity)
                heap_capacity="$2"
                shift 2
                ;;
            *)
                printf 'error: unknown bench-pcm option: %s\n' "$1" >&2
                exit 2
                ;;
        esac
    done
    case "${workload}" in
        waternet | rubido | slide | adsr) ;;
        *)
            printf 'error: PCM workload must be waternet, rubido, slide, or adsr\n' >&2
            exit 2
            ;;
    esac
    if ! [[ "${cycles}" =~ ^[1-9][0-9]*$ && "${reps}" =~ ^[1-9][0-9]*$ ]]; then
        printf 'error: cycles and reps must be positive integers\n' >&2
        exit 2
    fi

    local out_dir="${ROOT_DIR}/build/reports/phoneme-pcm"
    local receipt="${out_dir}/receipt.txt"
    prepare_phoneme_component \
        "${out_dir}" \
        "${ROOT_DIR}/src/test/java/w4me/PhoneMePcmBench.java" \
        -- \
        "${ROOT_DIR}/cartridges/waternet.wasm"
    write_component_header \
        "${receipt}" pcm \
        "workload=${workload} cycles=${cycles} reps=${reps} heap-capacity=${heap_capacity}"
    : > "${out_dir}/metrics.txt"
    local expected_hash=""
    local sample=0
    while [[ "${sample}" -lt "${reps}" ]]; do
        local result="${out_dir}/sample-${sample}.txt"
        "${COMPONENT_VM}" -EnableTicks "=HeapCapacity${heap_capacity}" \
            -classpath "${COMPONENT_CLASSES}:${out_dir}/preverified" \
            w4me.PhoneMePcmBench "${workload}" "${cycles}" "${sample}" \
            > "${result}" 2>&1
        local pass_line
        pass_line="$(grep -F -- 'pcm-bench:pass ' "${result}")"
        local output_hash
        output_hash="$(
            printf '%s\n' "${pass_line}" \
                | sed -n 's/.* output-fnv1a=\([0-9a-f][0-9a-f]*\).*/\1/p'
        )"
        local metric
        metric="$(
            printf '%s\n' "${pass_line}" \
                | sed -n 's/.* us-per-sequence=\([0-9][0-9]*\).*/\1/p'
        )"
        if [[ -z "${output_hash}" ]] || [[ -z "${metric}" ]]; then
            printf 'error: incomplete PCM sample %s\n' "${sample}" >&2
            exit 1
        fi
        if [[ -n "${expected_hash}" ]] && [[ "${output_hash}" != "${expected_hash}" ]]; then
            printf 'error: PCM output changed at sample %s\n' "${sample}" >&2
            exit 1
        fi
        expected_hash="${output_hash}"
        printf '%s\n' "${metric}" >> "${out_dir}/metrics.txt"
        printf '%s\n' "${pass_line}" | tee -a "${receipt}"
        sample=$((sample + 1))
    done
    local median_metric
    median_metric="$(component_median "${out_dir}/metrics.txt")"
    printf 'phoneme-component-bench:median component=pcm reps=%s us-per-sequence=%s output-fnv1a=%s\n' \
        "${reps}" "${median_metric}" "${expected_hash}" \
        | tee -a "${receipt}"
    printf 'receipt: %s\n' "${receipt}"
}

cmd_bench_argb() {
    local side=160
    local band_height=16
    local frames=100
    local reps=5
    local heap_capacity="64M"
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --side)
                side="$2"
                shift 2
                ;;
            --band-height)
                band_height="$2"
                shift 2
                ;;
            --frames)
                frames="$2"
                shift 2
                ;;
            --reps)
                reps="$2"
                shift 2
                ;;
            --heap-capacity)
                heap_capacity="$2"
                shift 2
                ;;
            *)
                printf 'error: unknown bench-argb option: %s\n' "$1" >&2
                exit 2
                ;;
        esac
    done
    if ! [[ "${side}" =~ ^[1-9][0-9]*$ &&
        "${band_height}" =~ ^[1-9][0-9]*$ &&
        "${frames}" =~ ^[1-9][0-9]*$ &&
        "${reps}" =~ ^[1-9][0-9]*$ ]]; then
        printf 'error: side, band-height, frames, and reps must be positive integers\n' >&2
        exit 2
    fi

    local out_dir="${ROOT_DIR}/build/reports/phoneme-argb"
    local receipt="${out_dir}/receipt.txt"
    prepare_phoneme_component \
        "${out_dir}" \
        "${ROOT_DIR}/src/test/java/w4me/PhoneMeArgbBandBench.java" \
        -- \
        "${ROOT_DIR}/cartridges/waternet.wasm"
    write_component_header \
        "${receipt}" argb \
        "side=${side} band-height=${band_height} frames=${frames} reps=${reps} heap-capacity=${heap_capacity}"
    : > "${out_dir}/metrics.txt"
    local expected_hash=""
    local sample=0
    while [[ "${sample}" -lt "${reps}" ]]; do
        local result="${out_dir}/sample-${sample}.txt"
        "${COMPONENT_VM}" -EnableTicks "=HeapCapacity${heap_capacity}" \
            -classpath "${COMPONENT_CLASSES}:${out_dir}/preverified" \
            w4me.PhoneMeArgbBandBench \
            "${side}" "${band_height}" "${frames}" "${sample}" \
            > "${result}" 2>&1
        local pass_line
        pass_line="$(grep -F -- 'argb-band:pass ' "${result}")"
        local output_hash
        output_hash="$(
            printf '%s\n' "${pass_line}" \
                | sed -n 's/.* output-fnv1a=\([0-9a-f][0-9a-f]*\).*/\1/p'
        )"
        local metric
        metric="$(
            printf '%s\n' "${pass_line}" \
                | sed -n 's/.* us-per-frame=\([0-9][0-9]*\).*/\1/p'
        )"
        if [[ -z "${output_hash}" ]] || [[ -z "${metric}" ]]; then
            printf 'error: incomplete ARGB sample %s\n' "${sample}" >&2
            exit 1
        fi
        if [[ -n "${expected_hash}" ]] && [[ "${output_hash}" != "${expected_hash}" ]]; then
            printf 'error: ARGB output changed at sample %s\n' "${sample}" >&2
            exit 1
        fi
        expected_hash="${output_hash}"
        printf '%s\n' "${metric}" >> "${out_dir}/metrics.txt"
        printf '%s\n' "${pass_line}" | tee -a "${receipt}"
        sample=$((sample + 1))
    done
    local median_metric
    median_metric="$(component_median "${out_dir}/metrics.txt")"
    printf 'phoneme-component-bench:median component=argb reps=%s us-per-frame=%s output-fnv1a=%s\n' \
        "${reps}" "${median_metric}" "${expected_hash}" \
        | tee -a "${receipt}"
    printf 'receipt: %s\n' "${receipt}"
}

cmd_bench_w4bench() {
    local samples=3
    local heap_capacity="64M"
    local candidate="current"
    while [[ "$#" -gt 0 ]]; do
        case "$1" in
            --samples)
                samples="$2"
                shift 2
                ;;
            --heap-capacity)
                heap_capacity="$2"
                shift 2
                ;;
            --candidate)
                candidate="$2"
                shift 2
                ;;
            *)
                printf 'error: unknown bench-w4bench option: %s\n' "$1" >&2
                exit 2
                ;;
        esac
    done
    if ! [[ "${samples}" =~ ^[1-9][0-9]*$ ]]; then
        printf 'error: samples must be a positive integer\n' >&2
        exit 2
    fi
    case "${heap_capacity}" in
        '' | [!0-9]* | *[!0-9KMGkmg]* | *[KMGkmg][0-9KMGkmg]*)
            printf 'error: heap capacity must be an integer with optional K, M, or G suffix: %s\n' \
                "${heap_capacity}" >&2
            exit 2
            ;;
        *) ;;
    esac
    case "${candidate}" in
        '' | *[[:space:]]*)
            printf 'error: candidate must be one non-empty token\n' >&2
            exit 2
            ;;
        *) ;;
    esac

    "${ROOT_DIR}/tools/bench/run.sh" w4bench

    local out_dir="${ROOT_DIR}/build/reports/w4bench/v1"
    local receipt="${out_dir}/receipt.txt"
    local profile="${ROOT_DIR}/bench/w4bench/profile_v1.json"
    local host_gate_dir="${ROOT_DIR}/build/reports/bench/w4bench"
    local generated_profile="${host_gate_dir}/generated/java/w4me/W4BenchProfile.java"
    local cartridge="${ROOT_DIR}/bench/w4bench/w4bench-v1.wasm"
    local profile_state
    profile_state="$(
        sed -n 's/^[[:space:]]*"state":[[:space:]]*"\([^"]*\)".*/\1/p' \
            "${profile}"
    )"
    if [[ "${profile_state}" != "FROZEN" ]]; then
        printf 'error: authoritative W4Bench timing requires a FROZEN profile\n' >&2
        exit 1
    fi
    prepare_phoneme_component \
        "${out_dir}" \
        "${generated_profile}" \
        "${ROOT_DIR}/src/test/java/w4me/W4BenchRunner.java" \
        -- \
        "${cartridge}"
    cp -- "${host_gate_dir}/oracle.txt" "${out_dir}/host-oracle.txt"
    cp -- "${host_gate_dir}/opcode-coverage.txt" \
        "${out_dir}/host-opcode-coverage.txt"
    cp -- "${host_gate_dir}/verification.txt" \
        "${out_dir}/host-verification.txt"
    write_component_header \
        "${receipt}" w4bench \
        "profile=w4bench-v1 profile-state=${profile_state} timing-authoritative=yes samples=${samples} repetitions=9 heap-capacity=${heap_capacity} candidate=${candidate}"
    local profile_sha256
    local generated_profile_sha256
    local cartridge_sha256
    local calibration_sha256
    local host_oracle_sha256
    local host_coverage_sha256
    local host_verification_sha256
    local oracle_source_sha256
    local coverage_source_sha256
    profile_sha256="$(hash_or_missing "${profile}")"
    generated_profile_sha256="$(hash_or_missing "${generated_profile}")"
    cartridge_sha256="$(hash_or_missing "${cartridge}")"
    calibration_sha256="$(hash_or_missing "${ROOT_DIR}/bench/w4bench/calibration_v1.json")"
    host_oracle_sha256="$(hash_or_missing "${out_dir}/host-oracle.txt")"
    host_coverage_sha256="$(hash_or_missing "${out_dir}/host-opcode-coverage.txt")"
    host_verification_sha256="$(hash_or_missing "${out_dir}/host-verification.txt")"
    oracle_source_sha256="$(hash_or_missing "${ROOT_DIR}/bench/w4bench/reference_oracle.py")"
    coverage_source_sha256="$(
        hash_or_missing "${ROOT_DIR}/src/test/java/w4me/wasm/W4BenchOpcodeCoverageSmoke.java"
    )"
    {
        printf 'profile-sha256=%s\n' "${profile_sha256}"
        printf 'generated-profile-sha256=%s\n' "${generated_profile_sha256}"
        printf 'cartridge-sha256=%s\n' "${cartridge_sha256}"
        printf 'calibration-sha256=%s\n' "${calibration_sha256}"
        printf 'host-oracle-output-sha256=%s\n' "${host_oracle_sha256}"
        printf 'host-coverage-output-sha256=%s\n' "${host_coverage_sha256}"
        printf 'host-verification-output-sha256=%s\n' "${host_verification_sha256}"
        printf 'host-oracle-source-sha256=%s\n' "${oracle_source_sha256}"
        printf 'host-coverage-source-sha256=%s\n' "${coverage_source_sha256}"
        printf 'coverage=all-190-source-opcodes expected-traps=1 timed-coverage=no\n'
    } >> "${receipt}"

    local expected_profile_crc=""
    local expected_contract_crc=""
    local expected_work_crc=""
    local expected_test_index=""
    local sample=0
    while [[ "${sample}" -lt "${samples}" ]]; do
        local result="${out_dir}/sample-${sample}.txt"
        if ! "${COMPONENT_VM}" -EnableTicks "=HeapCapacity${heap_capacity}" \
            -classpath "${COMPONENT_CLASSES}:${out_dir}/preverified" \
            w4me.W4BenchRunner "${candidate}" "${sample}" timed \
            > "${result}" 2>&1; then
            cat -- "${result}" >&2
            exit 1
        fi

        local coverage_count
        local validator_count
        local final_count
        local median_count
        local repetition_count
        coverage_count="$(grep -c -F -- 'w4bench:coverage ' "${result}" || true)"
        validator_count="$(grep -c -F -- 'w4bench:validator-negative ' "${result}" || true)"
        final_count="$(grep -c -E -- 'w4bench:pass .* work-crc=' "${result}" || true)"
        median_count="$(grep -c -E -- 'w4bench:pass .* median-wall-ms=' "${result}" || true)"
        repetition_count="$(grep -c -E -- 'w4bench:pass .* rep=[0-9]+ ' "${result}" || true)"
        if [[ "${coverage_count}" -ne 1 ]] \
            || [[ "${validator_count}" -ne 1 ]] \
            || [[ "${final_count}" -ne 1 ]] \
            || [[ "${median_count}" -ne 7 ]] \
            || [[ "${repetition_count}" -ne 63 ]]; then
            printf 'error: incomplete W4Bench sample %s: coverage=%s validator-negative=%s final=%s medians=%s repetitions=%s\n' \
                "${sample}" "${coverage_count}" "${validator_count}" \
                "${final_count}" "${median_count}" "${repetition_count}" >&2
            cat -- "${result}" >&2
            exit 1
        fi

        local sample_medians
        local sample_test_index
        sample_medians="${out_dir}/sample-${sample}-medians.txt"
        sed -n \
            's/.* test-id=\([^ ]*\) test=\([^ ]*\) median-wall-ms=\([0-9][0-9]*\).*/\1 \2 \3/p' \
            "${result}" > "${sample_medians}"
        sample_test_index="$(awk '{ print $1, $2 }' "${sample_medians}")"
        if [[ "${sample}" -eq 0 ]]; then
            expected_test_index="${sample_test_index}"
        elif [[ "${sample_test_index}" != "${expected_test_index}" ]]; then
            printf 'error: W4Bench test index changed at sample %s\n' "${sample}" >&2
            exit 1
        fi
        while read -r test_id test_name metric; do
            if [[ "${sample}" -eq 0 ]]; then
                : > "${out_dir}/metrics-test-${test_id}.txt"
            fi
            printf '%s\n' "${metric}" >> "${out_dir}/metrics-test-${test_id}.txt"
        done < "${sample_medians}"

        local final_line
        local profile_crc
        local contract_crc
        local work_crc
        final_line="$(grep -E -- 'w4bench:pass .* work-crc=' "${result}")"
        profile_crc="$(
            printf '%s\n' "${final_line}" \
                | sed -n 's/.* profile-crc=\([0-9a-f][0-9a-f]*\).*/\1/p'
        )"
        contract_crc="$(
            printf '%s\n' "${final_line}" \
                | sed -n 's/.* contract-crc=\([0-9a-f][0-9a-f]*\).*/\1/p'
        )"
        work_crc="$(
            printf '%s\n' "${final_line}" \
                | sed -n 's/.* work-crc=\([0-9a-f][0-9a-f]*\).*/\1/p'
        )"
        if [[ -z "${expected_profile_crc}" ]]; then
            expected_profile_crc="${profile_crc}"
            expected_contract_crc="${contract_crc}"
            expected_work_crc="${work_crc}"
        elif [[ "${profile_crc}" != "${expected_profile_crc}" ]] \
            || [[ "${contract_crc}" != "${expected_contract_crc}" ]] \
            || [[ "${work_crc}" != "${expected_work_crc}" ]]; then
            printf 'error: W4Bench identity changed at sample %s\n' "${sample}" >&2
            exit 1
        fi
        grep -E -- 'w4bench:(coverage|validator-negative|pass).* (median-wall-ms|work-crc|opcodes=|corrupt-result=)' \
            "${result}" \
            | tee -a "${receipt}"
        sample=$((sample + 1))
    done

    while read -r test_id test_name; do
        median_metric="$(component_median "${out_dir}/metrics-test-${test_id}.txt")"
        printf 'phoneme-component-bench:median component=w4bench test-id=%s test=%s samples=%s median-wall-ms=%s\n' \
            "${test_id}" "${test_name}" "${samples}" \
            "${median_metric}" \
            | tee -a "${receipt}"
    done <<< "${expected_test_index}"
    printf 'phoneme-component-bench:pass component=w4bench samples=%s repetitions=9 profile-crc=%s contract-crc=%s work-crc=%s\n' \
        "${samples}" "${expected_profile_crc}" "${expected_contract_crc}" \
        "${expected_work_crc}" \
        | tee -a "${receipt}"
    printf 'receipt: %s\n' "${receipt}"
}

cmd_verify_arm64() {
    # Cross-ISA correctness gate for the phoneME portable-C interpreter.
    # Native i686 remains the performance judge. The AArch64 VM runs under QEMU
    # TCG only to prove that route checkpoints and deterministic VM counters match.

    PHONEME_HOME="${PHONEME_HOME:-${ROOT_DIR}/.local/phoneme}"
    OUT_DIR="${ROOT_DIR}/build/reports/phoneme"
    I686_VM="${PHONEME_HOME}/cldc_vm_r"
    ARM64_VM="${PHONEME_HOME}/cldc_vm_r-arm64"
    CLDC_CLASSES="${PHONEME_HOME}/classes.zip"
    ARM64_IMAGE="${PHONEME_ARM64_IMAGE:-docker.io/library/debian:stable-slim}"
    CANDIDATE="host-import-id"
    MODE="optimized"
    CARTS=(waternet rubido untangle duck-maze)

    command -v docker > /dev/null || {
        printf 'error: docker command not found on PATH\n' >&2
        exit 1
    }
    [[ -x "${ARM64_VM}" ]] || {
        printf 'error: missing executable %s\n' "${ARM64_VM}" >&2
        printf 'hint: set PHONEME_HOME; see docs/performance.md\n' >&2
        exit 1
    }
    [[ -x "${I686_VM}" ]] || {
        printf 'error: missing executable %s\n' "${I686_VM}" >&2
        exit 1
    }
    [[ -f "${CLDC_CLASSES}" ]] || {
        printf 'error: missing phoneME classes: %s\n' "${CLDC_CLASSES}" >&2
        exit 1
    }

    # Build one CLDC-clean, preverified tree and collect the native reference run.
    "${ROOT_DIR}/tools/phoneme/run.sh" bench \
        "${CARTS[@]}" --mode "${MODE}" --candidate "${CANDIDATE}" --reps 1

    RECEIPT="${OUT_DIR}/arm64-isa-receipt.txt"
    ARTIFACT_SHA256="$(sed -n 's/^artifact-sha256=//p' "${OUT_DIR}/receipt.txt")"
    SOURCE_IDENTITY="$(sed -n 's/^source-head=//p' "${OUT_DIR}/receipt.txt")"

    deterministic_signature() {
        local result="$1"
        local count
        count="$(grep -c '^phoneme-bench:pass ' "${result}" || true)"
        if [[ "${count}" -ne 1 ]]; then
            printf 'error: expected one pass line in %s, found %s\n' \
                "${result}" "${count}" >&2
            return 1
        fi
        sed -n 's/ init-ms=.*$//p' "${result}"
    }

    classes_sha256="$(hash_or_missing "${CLDC_CLASSES}")"
    i686_vm_sha256="$(hash_or_missing "${I686_VM}")"
    arm64_vm_sha256="$(hash_or_missing "${ARM64_VM}")"
    {
        printf 'phoneme-arm64-isa receipt\n'
        printf 'artifact-sha256=%s\n' "${ARTIFACT_SHA256}"
        printf 'source-head=%s\n' "${SOURCE_IDENTITY}"
        printf 'classes-sha256=%s\n' "${classes_sha256}"
        printf 'reference-vm-arch=i686 reference-vm-sha256=%s\n' "${i686_vm_sha256}"
        printf 'candidate-vm-arch=arm64 candidate-vm-sha256=%s\n' "${arm64_vm_sha256}"
        printf 'candidate-execution=qemu-tcg timing-authoritative=no image=%s\n' \
            "${ARM64_IMAGE}"
        printf 'mode=%s candidate=%s extra-frames=per-route\n' \
            "${MODE}" "${CANDIDATE}"
    } > "${RECEIPT}"

    for cart in "${CARTS[@]}"; do
        case "${cart}" in
            duck-maze)
                CART_EXTRA_FRAMES=48
                ;;
            *)
                CART_EXTRA_FRAMES=1
                ;;
        esac
        I686_RESULT="${OUT_DIR}/${cart}-${MODE}-${CANDIDATE}-0.txt"
        ARM64_RESULT="${OUT_DIR}/${cart}-${MODE}-${CANDIDATE}-arm64-0.txt"
        I686_SIGNATURE="$(deterministic_signature "${I686_RESULT}")"

        if ! docker run --rm --platform linux/arm64 \
            -v "${PHONEME_HOME}:/vp:ro,Z" \
            -v "${OUT_DIR}/preverified:/pv:ro,Z" \
            "${ARM64_IMAGE}" \
            /vp/cldc_vm_r-arm64 -EnableTicks =HeapCapacity64M \
            -classpath /vp/classes.zip:/pv \
            w4me.PhoneMeRouteBench "${cart}" "${MODE}" \
            "${CART_EXTRA_FRAMES}" 1 "${CANDIDATE}" 0 verified \
            > "${ARM64_RESULT}" 2>&1; then
            printf 'FAIL phoneME arm64 cart=%s (VM exit)\n' "${cart}" >&2
            cat -- "${ARM64_RESULT}" >&2
            exit 1
        fi
        ARM64_SIGNATURE="$(deterministic_signature "${ARM64_RESULT}")"

        if [[ "${I686_SIGNATURE}" != "${ARM64_SIGNATURE}" ]]; then
            printf 'FAIL phoneME cross-ISA cart=%s deterministic state differs\n' \
                "${cart}" >&2
            printf 'i686: %s\n' "${I686_SIGNATURE}" >&2
            printf 'arm64: %s\n' "${ARM64_SIGNATURE}" >&2
            exit 1
        fi
        printf 'route cart=%s signature=%s\n' "${cart}" "${ARM64_SIGNATURE}" \
            | tee -a "${RECEIPT}"
        printf 'PASS phoneME cross-ISA cart=%s checkpoints-and-counters=exact\n' \
            "${cart}"
    done

    printf 'PASS phoneME cross-ISA routes=%s timing-authoritative=no\n' \
        "${#CARTS[@]}"
    printf 'receipt: %s\n' "${RECEIPT}"
}

case "${1:-}" in
    bench)
        shift
        cmd_bench "$@"
        ;;
    bench-pcm)
        shift
        cmd_bench_pcm "$@"
        ;;
    bench-argb)
        shift
        cmd_bench_argb "$@"
        ;;
    bench-w4bench)
        shift
        cmd_bench_w4bench "$@"
        ;;
    verify)
        shift
        cmd_bench waternet rubido untangle game-of-life-zig-edition duck-maze \
            --reps 1 "$@"
        ;;
    verify-arm64)
        shift
        cmd_verify_arm64 "$@"
        ;;
    *)
        printf '%s\n' \
            'usage: tools/phoneme/run.sh <bench|bench-pcm|bench-argb|bench-w4bench|verify|verify-arm64> [args...]' \
            >&2
        exit 1
        ;;
esac
