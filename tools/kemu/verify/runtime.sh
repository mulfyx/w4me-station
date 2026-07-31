#!/usr/bin/env bash
# Sourced by tools/kemu/run.sh; not a standalone entrypoint.
set -euo pipefail

cmd_verify_duck() {
    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/duck"
    TEMP_DIR="$(mktemp -d)"
    DIAGNOSTIC_JAR="${TEMP_DIR}/duck-diagnostic.jar"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop > /dev/null 2>&1 || true
        rm -rf -- "${TEMP_DIR}"
    }
    trap cleanup EXIT

    rm -rf -- "${RESULT_DIR}"
    mkdir -p -- "${RESULT_DIR}"
    build_diagnostic_jar \
        "${SOURCE_JAR}" \
        "${DIAGNOSTIC_JAR}" \
        "${TEMP_DIR}/classes" \
        "W4ME Duck Diagnostic" \
        'w4me.midp.DiagnosticLibraryMidlet' \
        "${ROOT_DIR}/src/test/java/w4me/midp/DiagnosticLibraryMidlet.java"
    KEMU_SIZE=240x320 "${ROOT_DIR}/tools/kemu/run.sh" session start "${DIAGNOSTIC_JAR}" > /dev/null
    kemu_launch_library_entry "Duck Maze" "${TEMP_DIR}/library.json"
    kemu_wait_log \
        'W4ME_REPLAY_COMPLETE cart=Duck Maze frame=154 framebuffer-fnv1a=1ae224ce' \
        15000

    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs read > "${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/duck-maze-level-1-complete.png" > /dev/null
    if ! grep -q 'W4ME_REPLAY_COMPLETE cart=Duck Maze frame=154 framebuffer-fnv1a=1ae224ce' \
        "${TEMP_DIR}/worker.log"; then
        printf 'error: Duck Maze did not reach the web-oracle level-1 receipt\n' >&2
        exit 1
    fi
    printf 'PASS KEmulator duck-maze level=1 framebuffer-fnv1a=1ae224ce\n'
}

cmd_verify_generic_w4ir() {

    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    ORACLE="${ROOT_DIR}/testdata/oracles/plasma-cube-60-frames.csv"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/generic-w4ir"
    TEMP_DIR="$(mktemp -d)"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop > /dev/null 2>&1 || true
        rm -rf -- "${TEMP_DIR}"
    }
    trap cleanup EXIT

    rm -rf -- "${RESULT_DIR}"
    mkdir -p -- "${RESULT_DIR}" "${TEMP_DIR}/classes"
    cp -- "${SOURCE_JAR}" "${TEMP_DIR}/generic-w4ir-probe.jar"
    javac \
        -source "${J2ME_SOURCE}" \
        -target "${J2ME_TARGET}" \
        -Xlint:-options \
        -classpath "${MIDP_API_JAR}:${ROOT_DIR}/build/midlet/classes" \
        -d "${TEMP_DIR}/classes" \
        "${FRAMEBUFFER_ORACLE_SOURCE}" \
        "${ROOT_DIR}/src/test/java/w4me/midp/DiagnosticW4MeMidlet.java" \
        "${ROOT_DIR}/src/test/java/w4me/midp/DiagnosticW4SessionMonitor.java" \
        "${ROOT_DIR}/src/test/java/w4me/midp/GenericW4IrProbeMidlet.java"
    jar uf "${TEMP_DIR}/generic-w4ir-probe.jar" -C "${TEMP_DIR}/classes" .
    {
        printf '%s\n' 'MIDlet-Name: W4ME Generic W4IR Probe'
        printf '%s\n' 'MIDlet-1: W4ME Generic W4IR Probe,,w4me.midp.GenericW4IrProbeMidlet'
    } > "${TEMP_DIR}/probe.mf"
    jar ufm "${TEMP_DIR}/generic-w4ir-probe.jar" "${TEMP_DIR}/probe.mf" \
        2> "${TEMP_DIR}/manifest.log"

    KEMU_SIZE=240x320 "${ROOT_DIR}/tools/kemu/run.sh" session \
        start "${TEMP_DIR}/generic-w4ir-probe.jar" > /dev/null
    kemu_wait_log \
        'W4ME_FRAME cart=plasma-cube frame=59 .*fast-paths=0 ' \
        30000
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs read > "${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"

    if ! grep -F -q -- \
        'W4ME_LOAD cart=plasma-cube bytes=5573 source=bundled w4ir=' \
        "${TEMP_DIR}/worker.log"; then
        printf 'error: generic W4IR probe did not load Plasma Cube\n' >&2
        exit 1
    fi
    if ! grep -F -q -- 'fast-paths=disabled' "${TEMP_DIR}/worker.log"; then
        printf 'error: cartridge-specific fast paths were not disabled\n' >&2
        exit 1
    fi
    if ! grep -E -q -- \
        'W4ME_FRAME cart=plasma-cube frame=59 .*fast-paths=0 ' \
        "${TEMP_DIR}/worker.log"; then
        printf 'error: generic W4IR probe did not complete 60 frames without fast paths\n' >&2
        exit 1
    fi

    awk -F, 'NR > 1 { print $1 " " $3 }' "${ORACLE}" > "${TEMP_DIR}/expected.txt"
    sed -n 's/.*W4ME_FRAME cart=plasma-cube frame=\([0-9][0-9]*\).*framebuffer-fnv1a=\([0-9a-f][0-9a-f]*\).*/\1 \2/p' \
        "${TEMP_DIR}/worker.log" \
        | awk '$1 < 60 && !seen[$1]++ { print }' > "${TEMP_DIR}/actual.txt"
    diff -u "${TEMP_DIR}/expected.txt" "${TEMP_DIR}/actual.txt"
    cp -- "${TEMP_DIR}/actual.txt" "${RESULT_DIR}/frames.txt"

    sed -n 's/.*W4ME_FRAME cart=plasma-cube frame=\([0-9][0-9]*\) instructions=[0-9][0-9]* dispatches=\([0-9][0-9]*\).*elapsed-ms=\([0-9][0-9]*\).*/\1 \2 \3/p' \
        "${TEMP_DIR}/worker.log" \
        | awk '$1 < 60 && !seen[$1]++ { total += $3; dispatches += $2; if (count == 0 || $3 < minimum) minimum = $3; if ($3 > maximum) maximum = $3; count++ } END { if (count != 60) exit 1; printf "frames=%d average-ms=%.2f minimum-ms=%d maximum-ms=%d dispatches-average=%d\n", count, total / count, minimum, maximum, dispatches / count }' \
            > "${RESULT_DIR}/timing.txt"

    printf 'PASS KEmulator generic W4IR plasma-cube frames=60 fast-paths=0\n'
    cat "${RESULT_DIR}/timing.txt"
    source_jar_sha256="$(sha256_file "${SOURCE_JAR}")"
    {
        printf 'source-jar-sha256=%s\n' "${source_jar_sha256}"
        printf '%s\n' 'PASS KEmulator generic W4IR plasma-cube frames=60 fast-paths=0'
        cat "${RESULT_DIR}/timing.txt"
        cat "${RESULT_DIR}/frames.txt"
    } > "${RESULT_DIR}/receipt.txt"
}

cmd_verify_plasma() {
    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    ORACLE="${ROOT_DIR}/testdata/oracles/plasma-cube-60-frames.csv"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/plasma"
    TEMP_DIR="$(mktemp -d)"
    DIAGNOSTIC_JAR="${TEMP_DIR}/plasma-diagnostic.jar"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop > /dev/null 2>&1 || true
        rm -rf -- "${TEMP_DIR}"
    }
    trap cleanup EXIT

    rm -rf -- "${RESULT_DIR}"
    mkdir -p -- "${RESULT_DIR}"
    build_diagnostic_jar \
        "${SOURCE_JAR}" \
        "${DIAGNOSTIC_JAR}" \
        "${TEMP_DIR}/classes" \
        "W4ME Plasma Diagnostic" \
        'w4me.midp.DiagnosticLibraryMidlet' \
        "${ROOT_DIR}/src/test/java/w4me/midp/DiagnosticLibraryMidlet.java"

    KEMU_SIZE=240x320 "${ROOT_DIR}/tools/kemu/run.sh" session start "${DIAGNOSTIC_JAR}" > /dev/null
    kemu_launch_library_entry "Plasma Cube" "${TEMP_DIR}/library.json"
    kemu_wait_log \
        'W4ME_FRAME cart=Plasma Cube frame=59 ' \
        15000

    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs read > "${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"
    awk -F, 'NR > 1 { print $1 " " $3 }' "${ORACLE}" > "${TEMP_DIR}/expected.txt"
    sed -n 's/.*W4ME_FRAME cart=Plasma Cube frame=\([0-9][0-9]*\).*framebuffer-fnv1a=\([0-9a-f][0-9a-f]*\).*/\1 \2/p' \
        "${TEMP_DIR}/worker.log" \
        | awk '$1 < 60 && !seen[$1]++ { print }' > "${TEMP_DIR}/actual.txt"

    diff -u "${TEMP_DIR}/expected.txt" "${TEMP_DIR}/actual.txt"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/plasma-cube-live.png" > /dev/null
    cp -- "${TEMP_DIR}/actual.txt" "${RESULT_DIR}/frames.txt"
    sed -n 's/.*W4ME_FRAME cart=Plasma Cube frame=\([0-9][0-9]*\) .*elapsed-ms=\([0-9][0-9]*\).*/\1 \2/p' \
        "${TEMP_DIR}/worker.log" \
        | awk '$1 < 60 && !seen[$1]++ { total += $2; if (count == 0 || $2 < minimum) minimum = $2; if ($2 > maximum) maximum = $2; count++ } END { if (count != 60) exit 1; printf "frames=%d average-ms=%.2f minimum-ms=%d maximum-ms=%d\n", count, total / count, minimum, maximum }' \
            > "${RESULT_DIR}/timing.txt"
    printf 'PASS KEmulator plasma-cube frames=60\n'
    cat "${RESULT_DIR}/timing.txt"
}

cmd_verify_rubido() {

    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/rubido"
    TEMP_DIR="$(mktemp -d)"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop > /dev/null 2>&1 || true
        rm -rf -- "${TEMP_DIR}"
    }
    trap cleanup EXIT

    rm -rf -- "${RESULT_DIR}"
    mkdir -p -- \
        "${RESULT_DIR}" \
        "${TEMP_DIR}/classes/cartridges"
    cp -- "${SOURCE_JAR}" "${TEMP_DIR}/rubido-probe.jar"
    cp -- \
        "${ROOT_DIR}/cartridges/rubido.wasm" \
        "${TEMP_DIR}/classes/cartridges/rubido.wasm"
    javac \
        -source "${J2ME_SOURCE}" \
        -target "${J2ME_TARGET}" \
        -Xlint:-options \
        -classpath "${MIDP_API_JAR}:${ROOT_DIR}/build/midlet/classes" \
        -d "${TEMP_DIR}/classes" \
        "${FRAMEBUFFER_ORACLE_SOURCE}" \
        "${ROOT_DIR}/src/test/java/w4me/midp/RubidoCorpusProbeMidlet.java"
    jar uf "${TEMP_DIR}/rubido-probe.jar" -C "${TEMP_DIR}/classes" .
    {
        printf '%s\n' 'MIDlet-Name: W4ME Rubido Corpus Probe'
        printf '%s\n' 'MIDlet-1: W4ME Rubido Corpus Probe,,w4me.midp.RubidoCorpusProbeMidlet'
    } > "${TEMP_DIR}/probe.mf"
    jar ufm "${TEMP_DIR}/rubido-probe.jar" "${TEMP_DIR}/probe.mf" \
        2> "${TEMP_DIR}/manifest.log"

    KEMU_SIZE=176x220 "${ROOT_DIR}/tools/kemu/run.sh" session \
        start "${TEMP_DIR}/rubido-probe.jar" > /dev/null
    kemu_wait_log \
        'W4ME_RUBIDO_PROBE frames=70 checkpoints=30 .*framebuffer-fnv1a=47462cbf' \
        20000
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs read \
        > "${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/rubido-probe.png" > /dev/null

    if grep -F -q -- 'W4ME_RUBIDO_ERROR' "${TEMP_DIR}/worker.log"; then
        printf 'error: Rubido corpus probe reported a runtime failure\n' >&2
        exit 1
    fi
    checkpoint_count="$(grep -F -c -- 'W4ME_RUBIDO_FRAME ' "${TEMP_DIR}/worker.log")"
    if [[ "${checkpoint_count}" != 30 ]]; then
        printf 'error: expected 30 Rubido checkpoints, got %s\n' "${checkpoint_count}" >&2
        exit 1
    fi
    if ! grep -F -q -- \
        'W4ME_RUBIDO_PROBE frames=70 checkpoints=30 tones=8 disk-read=20/0 disk-write=20/20 palette=blue-to-aqua framebuffer-fnv1a=47462cbf' \
        "${TEMP_DIR}/worker.log"; then
        printf 'error: KEmulator did not match the Rubido browser oracle\n' >&2
        exit 1
    fi
    source_jar_sha256="$(sha256_file "${SOURCE_JAR}")"
    cartridge_sha256="$(sha256_file "${ROOT_DIR}/cartridges/rubido.wasm")"
    {
        printf 'source-jar-sha256=%s\n' "${source_jar_sha256}"
        printf 'cartridge-sha256=%s\n' "${cartridge_sha256}"
        grep -F -- 'W4ME_RUBIDO_' "${TEMP_DIR}/worker.log"
    } > "${RESULT_DIR}/receipt.txt"
    printf 'PASS KEmulator Rubido frames=70 checkpoints=30 mouse+palette+disk=exact\n'
}

cmd_verify_tankle() {
    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/tankle"
    TEMP_DIR="$(mktemp -d)"
    DIAGNOSTIC_JAR="${TEMP_DIR}/tankle-diagnostic.jar"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop > /dev/null 2>&1 || true
        rm -rf -- "${TEMP_DIR}"
    }
    trap cleanup EXIT

    rm -rf -- "${RESULT_DIR}"
    mkdir -p -- "${RESULT_DIR}"
    # The nested class name contains a literal dollar sign.
    # shellcheck disable=SC2016
    build_diagnostic_jar \
        "${SOURCE_JAR}" \
        "${DIAGNOSTIC_JAR}" \
        "${TEMP_DIR}/classes" \
        "W4ME Tankle Diagnostic" \
        'w4me.midp.DirectCartridgeProbeMidlet$Tankle' \
        "${ROOT_DIR}/src/test/java/w4me/midp/DirectCartridgeProbeMidlet.java"

    KEMU_SIZE=240x320 "${ROOT_DIR}/tools/kemu/run.sh" session start "${DIAGNOSTIC_JAR}" > /dev/null
    kemu_wait_log 'W4ME_FRAME cart=Tankle frame=0 ' 10000
    kemu_key_hold FIRE 120
    kemu_wait_log \
        'W4ME_FRAME cart=Tankle frame=[0-9]+ .*framebuffer-fnv1a=c35da49c' \
        10000

    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs read > "${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/tankle-playing.png" > /dev/null

    if ! grep -E -q -- \
        'W4ME_FRAME cart=Tankle frame=0 .*framebuffer-fnv1a=948baa9b' \
        "${TEMP_DIR}/worker.log"; then
        printf 'error: Tankle first frame differs from the clean web oracle\n' >&2
        exit 1
    fi
    if ! grep -E -q -- \
        'W4ME_FRAME cart=Tankle frame=[0-9]+ .*framebuffer-fnv1a=c35da49c' \
        "${TEMP_DIR}/worker.log"; then
        printf 'error: Tankle did not enter gameplay after Player 1 button 1\n' >&2
        exit 1
    fi
    printf 'PASS KEmulator Tankle web-oracle=exact gamepad1=A\n'
}

cmd_verify_trap() {

    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/trap"
    TEMP_DIR="$(mktemp -d)"
    HTTP_PORT=18387
    HTTP_PID=""

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop > /dev/null 2>&1 || true
        if [[ -n "${HTTP_PID}" ]] && kill -0 "${HTTP_PID}" 2> /dev/null; then
            kill "${HTTP_PID}" || true
        fi
        rm -rf -- "${TEMP_DIR}"
    }
    trap cleanup EXIT

    rm -rf -- "${RESULT_DIR}"
    mkdir -p -- "${RESULT_DIR}" "${TEMP_DIR}/classes" "${TEMP_DIR}/http"
    wat2wasm "${ROOT_DIR}/src/test/resources/trap-cart.wat" \
        -o "${TEMP_DIR}/http/trap.wasm"
    wasm-validate "${TEMP_DIR}/http/trap.wasm"
    cp -- "${SOURCE_JAR}" "${TEMP_DIR}/trap-probe.jar"
    javac \
        -source "${J2ME_SOURCE}" \
        -target "${J2ME_TARGET}" \
        -Xlint:-options \
        -classpath "${MIDP_API_JAR}:${ROOT_DIR}/build/midlet/classes" \
        -d "${TEMP_DIR}/classes" \
        "${FRAMEBUFFER_ORACLE_SOURCE}" \
        "${ROOT_DIR}/src/test/java/w4me/midp/DiagnosticW4MeMidlet.java" \
        "${ROOT_DIR}/src/test/java/w4me/midp/DiagnosticW4SessionMonitor.java" \
        "${ROOT_DIR}/src/test/java/w4me/midp/TrapCartProbeMidlet.java"
    jar uf "${TEMP_DIR}/trap-probe.jar" -C "${TEMP_DIR}/classes" .
    {
        printf '%s\n' 'MIDlet-Name: W4ME Trap Cart Probe'
        printf '%s\n' 'MIDlet-1: W4ME Trap Cart Probe,,w4me.midp.TrapCartProbeMidlet'
    } > "${TEMP_DIR}/probe.mf"
    jar ufm "${TEMP_DIR}/trap-probe.jar" "${TEMP_DIR}/probe.mf" \
        2> "${TEMP_DIR}/manifest.log"

    python3 -m http.server "${HTTP_PORT}" \
        --bind 127.0.0.1 \
        --directory "${TEMP_DIR}/http" \
        > "${TEMP_DIR}/http.log" 2>&1 &
    HTTP_PID="$!"

    KEMU_SIZE=240x320 "${ROOT_DIR}/tools/kemu/run.sh" session \
        start "${TEMP_DIR}/trap-probe.jar" > /dev/null
    if "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait permission \
        --timeout 1000 --json > /dev/null 2>&1; then
        "${ROOT_DIR}/tools/kemu/run.sh" session cmd permission allow \
            --once --json > /dev/null
    fi
    kemu_wait_display alert - - 5000
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/runtime-trap-alert.png" > /dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd state > "${TEMP_DIR}/alert-state.log"
    kemu_key_press LSK
    kemu_wait_display list - - 5000
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/launcher-after-runtime-trap.png" > /dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd state > "${TEMP_DIR}/launcher-state.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs read \
        > "${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"
    cp -- "${TEMP_DIR}/http.log" "${RESULT_DIR}/http.log"
    cp -- "${TEMP_DIR}/alert-state.log" "${RESULT_DIR}/alert-state.log"
    cp -- "${TEMP_DIR}/launcher-state.log" "${RESULT_DIR}/launcher-state.log"

    download_count="$(grep -F -c -- 'GET /trap.wasm HTTP/1.1" 200' "${TEMP_DIR}/http.log")"
    if [[ "${download_count}" -ne 1 ]]; then
        printf 'error: expected exactly one trap cartridge download\n' >&2
        exit 1
    fi
    if ! grep -F -q -- 'W4ME_INSTALL state=COMMITTED' "${TEMP_DIR}/worker.log"; then
        printf 'error: valid trap cartridge did not pass validation and commit\n' >&2
        exit 1
    fi
    if ! grep -F -q -- 'W4ME_ERROR w4me.wasm.WasmTrap: unreachable instruction executed' \
        "${TEMP_DIR}/worker.log"; then
        printf 'error: runtime trap was not isolated and reported\n' >&2
        exit 1
    fi
    if ! grep -F -q -- 'Displayable: alert' "${TEMP_DIR}/alert-state.log"; then
        printf 'error: runtime trap did not open a MIDP Alert\n' >&2
        exit 1
    fi
    if ! grep -F -q -- 'Displayable: list' "${TEMP_DIR}/launcher-state.log"; then
        printf 'error: dismissing the trap Alert did not restore the launcher\n' >&2
        exit 1
    fi
    printf 'PASS KEmulator runtime trap isolation and launcher recovery\n'
}

cmd_verify_untangle() {

    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/untangle"
    TEMP_DIR="$(mktemp -d)"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop > /dev/null 2>&1 || true
        rm -rf -- "${TEMP_DIR}"
    }
    trap cleanup EXIT

    rm -rf -- "${RESULT_DIR}"
    mkdir -p -- "${RESULT_DIR}" "${TEMP_DIR}/classes"
    cp -- "${SOURCE_JAR}" "${TEMP_DIR}/untangle-probe.jar"
    cp -- "${ROOT_DIR}/cartridges/untangle.wasm" "${TEMP_DIR}/classes/untangle.wasm"
    javac \
        -source "${J2ME_SOURCE}" \
        -target "${J2ME_TARGET}" \
        -Xlint:-options \
        -classpath "${MIDP_API_JAR}:${ROOT_DIR}/build/midlet/classes" \
        -d "${TEMP_DIR}/classes" \
        "${FRAMEBUFFER_ORACLE_SOURCE}" \
        "${ROOT_DIR}/src/test/java/w4me/midp/UntangleCorpusProbeMidlet.java"
    jar uf "${TEMP_DIR}/untangle-probe.jar" -C "${TEMP_DIR}/classes" .
    {
        printf '%s\n' 'MIDlet-Name: W4ME Untangle Corpus Probe'
        printf '%s\n' 'MIDlet-1: W4ME Untangle Corpus Probe,,w4me.midp.UntangleCorpusProbeMidlet'
    } > "${TEMP_DIR}/probe.mf"
    jar ufm "${TEMP_DIR}/untangle-probe.jar" "${TEMP_DIR}/probe.mf" \
        2> "${TEMP_DIR}/manifest.log"

    KEMU_SIZE=176x220 "${ROOT_DIR}/tools/kemu/run.sh" session \
        start "${TEMP_DIR}/untangle-probe.jar" > /dev/null
    kemu_wait_log \
        'W4ME_UNTANGLE_PROBE frames=401 checkpoints=47 .*framebuffer-fnv1a=bc0231d9' \
        60000
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs read \
        > "${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/untangle-probe.png" > /dev/null

    if grep -F -q -- 'W4ME_UNTANGLE_ERROR' "${TEMP_DIR}/worker.log"; then
        printf 'error: Untangle corpus probe reported a runtime failure\n' >&2
        exit 1
    fi
    checkpoint_count="$(grep -F -c -- 'W4ME_UNTANGLE_FRAME ' "${TEMP_DIR}/worker.log")"
    if [[ "${checkpoint_count}" != 47 ]]; then
        printf 'error: expected 47 Untangle checkpoints, got %s\n' "${checkpoint_count}" >&2
        exit 1
    fi
    if ! grep -F -q -- \
        'W4ME_UNTANGLE_PROBE frames=401 checkpoints=47 tones=0 disk-read=1/0 disk-write=1/1 framebuffer-fnv1a=bc0231d9' \
        "${TEMP_DIR}/worker.log"; then
        printf 'error: KEmulator did not match the Untangle browser oracle\n' >&2
        exit 1
    fi
    source_jar_sha256="$(sha256_file "${SOURCE_JAR}")"
    cartridge_sha256="$(sha256_file "${ROOT_DIR}/cartridges/untangle.wasm")"
    {
        printf 'source-jar-sha256=%s\n' "${source_jar_sha256}"
        printf 'cartridge-sha256=%s\n' "${cartridge_sha256}"
        grep -F -- 'W4ME_UNTANGLE_' "${TEMP_DIR}/worker.log"
    } > "${RESULT_DIR}/receipt.txt"
    printf 'PASS KEmulator Untangle frames=401 checkpoints=47 drag+rotate+flip+disk=exact\n'
}

cmd_verify_waternet() {

    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/waternet"
    TEMP_DIR="$(mktemp -d)"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop > /dev/null 2>&1 || true
        rm -rf -- "${TEMP_DIR}"
    }
    trap cleanup EXIT

    rm -rf -- "${RESULT_DIR}"
    mkdir -p -- "${RESULT_DIR}" "${TEMP_DIR}/classes"
    cp -- "${SOURCE_JAR}" "${TEMP_DIR}/waternet-probe.jar"
    cp -- "${ROOT_DIR}/cartridges/waternet.wasm" "${TEMP_DIR}/classes/waternet.wasm"
    javac \
        -source "${J2ME_SOURCE}" \
        -target "${J2ME_TARGET}" \
        -Xlint:-options \
        -classpath "${MIDP_API_JAR}:${ROOT_DIR}/build/midlet/classes" \
        -d "${TEMP_DIR}/classes" \
        "${FRAMEBUFFER_ORACLE_SOURCE}" \
        "${ROOT_DIR}/src/test/java/w4me/midp/WaternetCorpusProbeMidlet.java"
    jar uf "${TEMP_DIR}/waternet-probe.jar" -C "${TEMP_DIR}/classes" .
    {
        printf '%s\n' 'MIDlet-Name: W4ME Waternet Corpus Probe'
        printf '%s\n' 'MIDlet-1: W4ME Waternet Corpus Probe,,w4me.midp.WaternetCorpusProbeMidlet'
    } > "${TEMP_DIR}/probe.mf"
    jar ufm "${TEMP_DIR}/waternet-probe.jar" "${TEMP_DIR}/probe.mf" \
        2> "${TEMP_DIR}/manifest.log"

    KEMU_SIZE=176x220 "${ROOT_DIR}/tools/kemu/run.sh" session \
        start "${TEMP_DIR}/waternet-probe.jar" > /dev/null
    kemu_wait_log \
        'W4ME_WATERNET_PROBE frames=94 checkpoints=17 .*framebuffer-fnv1a=14e0f616' \
        20000
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs read \
        > "${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/waternet-probe.png" > /dev/null

    if grep -F -q -- 'W4ME_WATERNET_ERROR' "${TEMP_DIR}/worker.log"; then
        printf 'error: Waternet corpus probe reported a runtime failure\n' >&2
        exit 1
    fi
    checkpoint_count="$(grep -F -c -- 'W4ME_WATERNET_FRAME ' "${TEMP_DIR}/worker.log")"
    if [[ "${checkpoint_count}" != 17 ]]; then
        printf 'error: expected 17 Waternet checkpoints, got %s\n' "${checkpoint_count}" >&2
        exit 1
    fi
    if ! grep -F -q -- \
        'W4ME_WATERNET_PROBE frames=94 checkpoints=17 tones=14 disk-read=16/0 disk-bytes=0 framebuffer-fnv1a=14e0f616' \
        "${TEMP_DIR}/worker.log"; then
        printf 'error: KEmulator did not match the Waternet browser oracle\n' >&2
        exit 1
    fi
    source_jar_sha256="$(sha256_file "${SOURCE_JAR}")"
    cartridge_sha256="$(sha256_file "${ROOT_DIR}/cartridges/waternet.wasm")"
    {
        printf 'source-jar-sha256=%s\n' "${source_jar_sha256}"
        printf 'cartridge-sha256=%s\n' "${cartridge_sha256}"
        grep -F -- 'W4ME_WATERNET_' "${TEMP_DIR}/worker.log"
    } > "${RESULT_DIR}/receipt.txt"
    printf 'PASS KEmulator Waternet frames=94 checkpoints=17 tones=14 disk=exact\n'
}
