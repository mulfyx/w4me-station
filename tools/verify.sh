#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
EXECUTE_CODE_LIMIT=16000

# shellcheck disable=SC1091
source "${ROOT_DIR}/tools/container/env.sh"

cmd_jar() {

    JAR_PATH="${1:-${ROOT_DIR}/dist/w4me-station.jar}"

    if [ ! -f "${JAR_PATH}" ]; then
        printf 'error: JAR not found: %s\n' "${JAR_PATH}" >&2
        exit 1
    fi
    JAD_PATH="${JAR_PATH%.jar}.jad"
    if [ ! -f "${JAD_PATH}" ]; then
        printf 'error: JAD not found: %s\n' "${JAD_PATH}" >&2
        exit 1
    fi
    case "$(basename -- "${JAR_PATH}")" in
    w4me-station.jar)
        expect_jsr75=true
        ;;
    w4me-station-base.jar)
        expect_jsr75=false
        ;;
    *)
        printf 'error: unknown release JAR variant: %s\n' "${JAR_PATH}" >&2
        exit 1
        ;;
    esac

    for class_name in w4me.midp.W4Canvas w4me.wasm.WasmInterpreter; do
        class_dump="$(javap -verbose -classpath "${JAR_PATH}" "${class_name}")"
        if [[ "${class_dump}" != *'major version: 47'* ]]; then
            printf 'error: %s is not Java 1.3 bytecode\n' "${class_name}" >&2
            exit 1
        fi
        if [[ "${class_dump}" != *'StackMap:'* ]]; then
            printf 'error: %s has no Java ME StackMap attributes\n' "${class_name}" >&2
            exit 1
        fi
    done

    interpreter_dump="$(
        javap -c -p -classpath "${JAR_PATH}" w4me.wasm.WasmInterpreter
    )"
    if printf '%s\n' "${interpreter_dump}" |
        grep -E 'putfield.*Field (dispatchesExecuted|compactBlockCalls|compactInstructionsExecuted):' \
            >/dev/null; then
        printf 'error: distributable JAR still writes an optional diagnostic counter\n' >&2
        exit 1
    fi
    if printf '%s\n' "${interpreter_dump}" |
        grep -E '(getfield.*Field profilingEnabled:|invoke[^[:space:]]*.*Method profileInstruction:)' \
            >/dev/null; then
        printf 'error: distributable JAR still executes opcode-profiling support\n' >&2
        exit 1
    fi
    execute_dump="$(
        printf '%s\n' "${interpreter_dump}" |
            awk '
                /^  private void execute\(int,/ {
                    in_method = 1
                }
                in_method && /^  private / && !/^  private void execute\(int,/ {
                    in_method = 0
                }
                in_method {
                    print
                }'
    )"
    execute_code_bytes="$(
        printf '%s\n' "${execute_dump}" |
            awk '
                /^[[:space:]]*[0-9]+:/ {
                    offset = $1
                    found = 1
                }
                END {
                    if (found) {
                        sub(/:$/, "", offset)
                        print offset + 1
                    }
                }'
    )"
    if ! [[ "${execute_code_bytes}" =~ ^[1-9][0-9]*$ ]]; then
        printf 'error: could not measure WasmInterpreter.execute bytecode\n' >&2
        exit 1
    fi
    if [[ "${execute_dump}" != *tableswitch* ]]; then
        printf 'error: WasmInterpreter.execute main dispatch is not a tableswitch\n' >&2
        exit 1
    fi
    if [ "${execute_code_bytes}" -gt "${EXECUTE_CODE_LIMIT}" ]; then
        printf 'error: WasmInterpreter.execute is %s bytes; maximum %s preserves method headroom\n' \
            "${execute_code_bytes}" "${EXECUTE_CODE_LIMIT}" >&2
        exit 1
    fi
    printf 'PASS WasmInterpreter.execute bytecode=%s maximum=%s dispatch=tableswitch diagnostic-counter-writes=0 profiling-runtime-uses=0\n' \
        "${execute_code_bytes}" "${EXECUTE_CODE_LIMIT}"

    check_cartridge() {
        name="$1"
        expected="$2"
        actual="$(unzip -p "${JAR_PATH}" "${name}" | sha256sum | cut -d ' ' -f 1)"
        if [ "${actual}" != "${expected}" ]; then
            printf 'error: packaged %s hash mismatch: expected %s, got %s\n' \
                "${name}" "${expected}" "${actual}" >&2
            exit 1
        fi
    }

    check_cartridge cartridges/sokoban.wasm 090c346dda166c5a92d9c6bd9fbd360f6fb2f4bfb088cd669bc2640b2a041948
    check_cartridge cartridges/wasm-wars.wasm aa88450ef73c4b900673c2368e9f06597ae000fdfb806030b86c9fb17263cab6
    check_cartridge cartridges/annoyingrobots.wasm 5ddf10cb816a64527f9b15f1bd29d3ee04343d8a508f4e81a9afce67a1a3017c
    check_cartridge cartridges/waternet.wasm 739f355da8e90cfd25c0c677cb5397f27affca171ae7ed731fafc51f008caa93
    check_cartridge cartridges/dragon-poker-draw.wasm 33d20f18c2ad3a6862c8f234ce4b4cac6137ce355c2e24db0529e1beb4023656
    check_cartridge cartridges/tictactoe.wasm 99caf1e44523f77598e8384476aba234d35670c370eabea5e815d2680c13ac4f
    check_cartridge cartridges/watris.wasm d66521048add571396bcaf7c80c2feb83fed0d5db3741f23f096bf389210d2d4
    check_cartridge cartridges/glowfish-chess.wasm 2804cc53da22eb62d54fd67f8d0c986bb8b12321aab07eb8486009701416e159
    check_cartridge cartridges/duck-maze.wasm 72805af4802d8f46d7f4a1f4a2edb97e9a5f5e587e17b234eda2e1b654d7dec8
    check_cartridge cartridges/untangle.wasm f2923336ede479ca4b47cb3fae75d4e252439908ab680d6dcb82a4f0ac0bfb62
    check_cartridge cartridges/nyancat.wasm 42befc2b97c26ab4e0c792824741547229c4e3973614dc05351159388c8dd069
    check_cartridge cartridges/sound-demo.wasm cd6e1219f2c9a95b21984ffd78fe0933c76a2f89b4391e41f8d6549935ca09f9
    check_cartridge cartridges/plasma-cube.wasm b15a4cc80dacd759b85b471557a803216231a9b1cf0c4fae96e661127daaa0c9

    for excluded in mandelbrot rubido sound-test tankle game-of-life-zig-edition; do
        if unzip -l "${JAR_PATH}" | grep -q -- "cartridges/${excluded}.wasm"; then
            printf 'error: test-only cartridge %s.wasm must not ship in the JAR\n' \
                "${excluded}" >&2
            exit 1
        fi
    done

    if ! unzip -p "${JAR_PATH}" META-INF/LICENSE | grep -q '^MIT License$'; then
        printf 'error: packaged MIT license is missing or incomplete\n' >&2
        exit 1
    fi

    for metadata_file in "${JAD_PATH}" manifest; do
        if [ "${metadata_file}" = manifest ]; then
            metadata="$(unzip -p "${JAR_PATH}" META-INF/MANIFEST.MF)"
        else
            metadata="$(cat -- "${metadata_file}")"
        fi
        if ! printf '%s\n' "${metadata}" |
            grep -q '^MIDlet-Version: 1\.1\.0$'; then
            printf 'error: %s does not declare MIDlet-Version 1.1.0\n' \
                "${metadata_file}" >&2
            exit 1
        fi
        if ! printf '%s\n' "${metadata}" |
            grep -q '^MIDlet-Info-URL: https://github\.com/mulfyx/w4me-station$'; then
            printf 'error: %s does not declare the public repository URL\n' \
                "${metadata_file}" >&2
            exit 1
        fi
        if [ "${expect_jsr75}" = true ]; then
            if ! printf '%s\n' "${metadata}" |
                grep -q 'javax\.microedition\.io\.Connector\.file\.read'; then
                printf 'error: %s full variant does not declare JSR-75 file permission\n' \
                    "${metadata_file}" >&2
                exit 1
            fi
        elif printf '%s\n' "${metadata}" |
            grep -q 'javax\.microedition\.io\.Connector\.file\.read'; then
            printf 'error: %s base variant declares JSR-75 file permission\n' \
                "${metadata_file}" >&2
            exit 1
        fi
    done

    jar_entries="$(unzip -Z1 "${JAR_PATH}")"
    if [[ "${jar_entries}" == *'w4me/midp/Jsr75FileSystem.class'* ]]; then
        has_jsr75=true
    else
        has_jsr75=false
    fi
    if [ "${has_jsr75}" != "${expect_jsr75}" ]; then
        printf 'error: JSR-75 class presence mismatch: expected %s, got %s\n' \
            "${expect_jsr75}" "${has_jsr75}" >&2
        exit 1
    fi
    if [[ "${jar_entries}" == *'w4me/wasm/PlasmaTriFast.class'* ]]; then
        printf 'error: distributable JAR contains a cartridge-specific execution shortcut\n' >&2
        exit 1
    fi

    if ! unzip -p "${JAR_PATH}" META-INF/THIRD-PARTY-NOTICES.md |
        grep -q 'Creative Commons Attribution-NonCommercial-ShareAlike 4.0'; then
        printf 'error: packaged third-party notice is missing or incomplete\n' >&2
        exit 1
    fi

    forbidden_phone_entry="$(
        unzip -Z1 "${JAR_PATH}" |
            awk '
                /(^|\/)cldc_vm_r(-arm64)?$/ ||
                /(^|\/)preverify$/ ||
                /(^|\/)classes\.zip$/ {
                    print
                    exit
                }'
    )"
    if [ -n "${forbidden_phone_entry}" ]; then
        printf 'error: distributable JAR contains phoneME tool: %s\n' \
            "${forbidden_phone_entry}" >&2
        exit 1
    fi

    forbidden_diagnostic_entry="$(
        unzip -Z1 "${JAR_PATH}" |
            awk '
                /^w4me\/midp\/Diagnostic.*\.class$/ ||
                /^w4me\/midp\/PlasmaBenchmarkMidlet\.class$/ {
                    print
                    exit
                }'
    )"
    if [ -n "${forbidden_diagnostic_entry}" ]; then
        printf 'error: distributable JAR contains test MIDlet: %s\n' \
            "${forbidden_diagnostic_entry}" >&2
        exit 1
    fi

    forbidden_diagnostic_marker="$(
        unzip -p "${JAR_PATH}" |
            strings |
            grep -E -m 1 -- \
                'W4ME_(FRAME|INPUT|LAYOUT|LOAD|INSTALL|REPLAY|BENCH)|W4ME-(Diagnostics|Benchmark-Warmup-Frames)' ||
            true
    )"
    if [ -n "${forbidden_diagnostic_marker}" ]; then
        printf 'error: distributable JAR contains test diagnostic marker: %s\n' \
            "${forbidden_diagnostic_marker}" >&2
        exit 1
    fi

    if javap -p -classpath "${JAR_PATH}" w4me.runtime.Wasm4Runtime |
        grep -q -- 'framebufferFnv1a'; then
        printf 'error: distributable JAR exposes the test-only framebuffer oracle\n' >&2
        exit 1
    fi

    expected_jar_size="$(stat -c '%s' -- "${JAR_PATH}")"
    declared_jar_size="$(
        sed -n 's/^MIDlet-Jar-Size: //p' "${JAD_PATH}"
    )"
    if [ "${declared_jar_size}" != "${expected_jar_size}" ]; then
        printf 'error: JAD size mismatch: expected %s, got %s\n' \
            "${expected_jar_size}" "${declared_jar_size:-missing}" >&2
        exit 1
    fi

    printf 'PASS preverified Java 1.3 JAR with MIT license, 13 cartridges, matching JAD, and notices; test diagnostics and phoneME tools excluded\n'
}

cmd_counterless() {

    OUT_DIR="${ROOT_DIR}/build/reports/verify/counterless"
    CLASSES_DIR="${OUT_DIR}/classes"
    ARTIFACT="${OUT_DIR}/counterless-exactness.jar"
    RECEIPT="${OUT_DIR}/receipt.txt"

    rm -rf -- "${OUT_DIR}"
    mkdir -p -- "${CLASSES_DIR}"

    find \
        "${ROOT_DIR}/src/main/java/w4me/wasm" \
        "${ROOT_DIR}/src/main/java/w4me/runtime" \
        -name '*.java' \
        ! -path "${ROOT_DIR}/src/main/java/w4me/wasm/InterpreterBuildConfig.java" \
        -print | sort >"${OUT_DIR}/sources.list"
    printf '%s\n' \
        "${ROOT_DIR}/bench/configs/timed/java/w4me/wasm/InterpreterBuildConfig.java" \
        >>"${OUT_DIR}/sources.list"

    javac \
        -source "${J2ME_SOURCE}" \
        -target "${J2ME_TARGET}" \
        -Xlint:-options \
        -bootclasspath "${J2ME_BOOTCLASSPATH}" \
        -classpath "${MIDP_API_JAR}" \
        -d "${CLASSES_DIR}" \
        @"${OUT_DIR}/sources.list"

    javac \
        -source "${J2ME_SOURCE}" \
        -target "${J2ME_TARGET}" \
        -Xlint:-options \
        -classpath "${CLASSES_DIR}:${MIDP_API_JAR}" \
        -d "${CLASSES_DIR}" \
        "${ROOT_DIR}/src/test/java/w4me/FramebufferOracle.java" \
        "${ROOT_DIR}/src/test/java/w4me/wasm/CorpusWorkload.java" \
        "${ROOT_DIR}/src/test/java/w4me/wasm/InterpreterVariant.java" \
        "${ROOT_DIR}/src/test/java/w4me/wasm/FullStateDifferential.java"

    find "${CLASSES_DIR}" -exec touch -h -t 198001010000.00 -- {} +
    jar cfM "${ARTIFACT}" -C "${CLASSES_DIR}" .
    ARTIFACT_SHA256="$(sha256sum -- "${ARTIFACT}" | cut -d ' ' -f 1)"

    INTERPRETER_DUMP="$(
        javap -c -p -classpath "${ARTIFACT}" w4me.wasm.WasmInterpreter
    )"
    if printf '%s\n' "${INTERPRETER_DUMP}" |
        grep -E 'putfield.*Field (dispatchesExecuted|compactBlockCalls|compactInstructionsExecuted):' \
            >/dev/null; then
        printf 'error: counterless artifact still writes a diagnostic counter\n' >&2
        exit 1
    fi
    if printf '%s\n' "${INTERPRETER_DUMP}" |
        grep -E '(getfield.*Field profilingEnabled:|invoke[^[:space:]]*.*Method profileInstruction:)' \
            >/dev/null; then
        printf 'error: counterless artifact still executes opcode-profiling support\n' >&2
        exit 1
    fi
    EXECUTE_CODE_BYTES="$(
        printf '%s\n' "${INTERPRETER_DUMP}" |
            awk '
                /^  private void execute\(int,/ { in_method = 1; next }
                in_method && /^  private / { in_method = 0 }
                in_method && /^[[:space:]]*[0-9]+:/ {
                    offset = $1
                    found = 1
                }
                END {
                    if (found) {
                        sub(/:$/, "", offset)
                        print offset + 1
                    }
                }'
    )"
    if ! [[ "${EXECUTE_CODE_BYTES}" =~ ^[1-9][0-9]*$ ]]; then
        printf 'error: could not measure counterless execute bytecode\n' >&2
        exit 1
    fi
    if [ "${EXECUTE_CODE_BYTES}" -gt "${EXECUTE_CODE_LIMIT}" ]; then
        printf 'error: counterless execute is %s bytes; maximum %s\n' \
            "${EXECUTE_CODE_BYTES}" "${EXECUTE_CODE_LIMIT}" >&2
        exit 1
    fi

    java_arguments=(
        "${ARTIFACT_SHA256}"
        "${ROOT_DIR}/src/main/resources/w4font.bin"
        "${ROOT_DIR}/cartridges/plasma-cube.wasm"
        "${ROOT_DIR}/cartridges/duck-maze.wasm"
        "${ROOT_DIR}/cartridges/waternet.wasm"
        "${ROOT_DIR}/testdata/oracles/waternet/input.csv"
        "${ROOT_DIR}/cartridges/rubido.wasm"
        "${ROOT_DIR}/testdata/oracles/rubido/input.csv"
        "${ROOT_DIR}/cartridges/untangle.wasm"
        "${ROOT_DIR}/testdata/oracles/untangle/input.csv"
        "${ROOT_DIR}/cartridges/game-of-life-zig-edition.wasm"
        "reference-host-import-id"
    )

    {
        printf 'counterless-exactness receipt\n'
        printf 'artifact-sha256=%s diagnostic-counters=off source=1.3 target=1.3\n' \
            "${ARTIFACT_SHA256}"
        printf 'bytecode execute=%s maximum=%s diagnostic-counter-writes=0 profiling-runtime-uses=0\n' \
            "${EXECUTE_CODE_BYTES}" "${EXECUTE_CODE_LIMIT}"
        java -classpath "${ARTIFACT}" w4me.wasm.FullStateDifferential \
            "${java_arguments[@]}"
    } | tee "${RECEIPT}"

    printf 'PASS counterless exactness artifact=%s receipt=%s\n' \
        "${ARTIFACT_SHA256}" "${RECEIPT}"
}

case "${1:-}" in
jar)
    shift
    cmd_jar "$@"
    ;;
counterless)
    shift
    cmd_counterless "$@"
    ;;
*)
    printf '%s\n' 'usage: tools/verify.sh <jar|counterless> [args...]' >&2
    exit 1
    ;;
esac
