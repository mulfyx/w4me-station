#!/usr/bin/env bash
# Sourced by tools/kemu/run.sh; not a standalone entrypoint.
set -euo pipefail

cmd_verify_save_state() {
    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/save-state"
    TEMP_DIR="$(mktemp -d)"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop > /dev/null 2>&1 || true
        rm -rf -- "${TEMP_DIR}"
    }
    trap cleanup EXIT

    rm -rf -- "${RESULT_DIR}"
    mkdir -p -- "${RESULT_DIR}"

    run_probe() {
        local probe_name="$1"
        local cartridge_name="$2"
        local probe_dir="${TEMP_DIR}/${probe_name}"
        local result_dir="${RESULT_DIR}/${probe_name}"
        local diagnostic_jar="${probe_dir}/save-state.jar"
        local before_hash
        local expected_index
        local expected_indices
        local loaded_hash
        local menu_json
        local missing_count
        local saved_hash
        local step

        mkdir -p -- "${probe_dir}" "${result_dir}"

        build_diagnostic_jar \
            "${SOURCE_JAR}" \
            "${diagnostic_jar}" \
            "${probe_dir}/classes" \
            "W4ME Save State ${cartridge_name}" \
            'w4me.midp.DiagnosticLibraryMidlet' \
            "${ROOT_DIR}/src/test/java/w4me/midp/DiagnosticLibraryMidlet.java"
        KEMU_SIZE=240x320 "${ROOT_DIR}/tools/kemu/run.sh" session \
            start "${diagnostic_jar}" > /dev/null
        kemu_launch_library_entry "${cartridge_name}" "${probe_dir}/library.json"

        expected_indices="2 1 2 4 2 5"
        step=0
        for expected_index in ${expected_indices}; do
            step=$((step + 1))
            kemu_key_press RSK
            kemu_wait_display list Paused - 5000
            menu_json="${result_dir}/menu-${step}.json"
            kemu_observe "${menu_json}"
            kemu_list_select "${expected_index}" "${menu_json}"
            kemu_wait_display list Paused "${expected_index}" 5000
            kemu_observe "${menu_json}"
            if ! kemu_displayable_items_match \
                "${menu_json}" \
                '["Continue","Save State","Load State","Settings","Restart Cart","Exit"]' \
                || ! grep -F -q -- "\"selectedIndex\":${expected_index}" \
                    "${menu_json}"; then
                printf 'error: %s save-state menu step %s is incorrect\n' \
                    "${cartridge_name}" "${step}" >&2
                exit 1
            fi
            kemu_command_run Select "${menu_json}"
            if [[ "${step}" -eq 6 ]]; then
                kemu_wait_display list "W4ME Station" - 5000
            else
                kemu_wait_display canvas - - 5000
            fi
        done

        "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs read \
            > "${result_dir}/worker.log"
        "${ROOT_DIR}/tools/kemu/run.sh" session stop > /dev/null

        saved_hash="$(
            sed -n \
                's/.*operation=save outcome=saved framebuffer-fnv1a=\([0-9a-f][0-9a-f]*\).*/\1/p' \
                "${result_dir}/worker.log" \
                | sed -n '1p'
        )"
        before_hash="$(
            sed -n \
                's/.*operation=load outcome=before framebuffer-fnv1a=\([0-9a-f][0-9a-f]*\).*/\1/p' \
                "${result_dir}/worker.log" \
                | sed -n '2p'
        )"
        loaded_hash="$(
            sed -n \
                's/.*operation=load outcome=loaded framebuffer-fnv1a=\([0-9a-f][0-9a-f]*\).*/\1/p' \
                "${result_dir}/worker.log" \
                | sed -n '1p'
        )"
        missing_count="$(
            grep -F -c -- 'operation=load outcome=missing' \
                "${result_dir}/worker.log" || true
        )"
        if [[ -z "${saved_hash}" ]] \
            || [[ -z "${before_hash}" ]] \
            || [[ -z "${loaded_hash}" ]] \
            || [[ "${saved_hash}" != "${loaded_hash}" ]] \
            || [[ "${saved_hash}" = "${before_hash}" ]] \
            || [[ "${missing_count}" -lt 2 ]]; then
            printf 'error: %s did not complete Save -> mutate -> Load exactly\n' \
                "${cartridge_name}" >&2
            exit 1
        fi
        if grep -E -q -- 'W4ME_ERROR|Exception in thread' \
            "${result_dir}/worker.log"; then
            printf 'error: %s save-state probe reported a runtime failure\n' \
                "${cartridge_name}" >&2
            exit 1
        fi
        printf 'cart=%s saved=%s mutated=%s loaded=%s missing=%s\n' \
            "${cartridge_name}" \
            "${saved_hash}" \
            "${before_hash}" \
            "${loaded_hash}" \
            "${missing_count}" \
            > "${result_dir}/receipt.txt"
    }

    run_probe \
        plasma \
        "Plasma Cube"
    run_probe \
        nyancat \
        "Nyan Cat"
    source_jar_sha256="$(sha256_file "${SOURCE_JAR}")"
    {
        printf 'source-jar-sha256=%s\n' "${source_jar_sha256}"
        printf '%s\n' \
            'carts=2 one-slot=yes persisted=no missing-before-save=yes missing-after-restart=yes state=exact'
    } > "${RESULT_DIR}/receipt.txt"
    printf 'PASS KEmulator save-state carts=2 Save-mutate-Load restart-clear\n'
}

cmd_verify_install() {

    JAR_PATH="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/install"
    TEMP_DIR="$(mktemp -d)"
    HTTP_PORT=18385
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
    mkdir -p -- "${RESULT_DIR}" "${TEMP_DIR}/classes"
    build_diagnostic_jar \
        "${JAR_PATH}" \
        "${TEMP_DIR}/install-probe.jar" \
        "${TEMP_DIR}/classes" \
        "W4ME Install Probe" \
        'w4me.midp.InstallUrlProbeMidlet' \
        "${ROOT_DIR}/src/test/java/w4me/midp/InstallUrlProbeMidlet.java"

    python3 -m http.server "${HTTP_PORT}" \
        --bind 127.0.0.1 \
        --directory "${ROOT_DIR}/cartridges" \
        > "${TEMP_DIR}/http.log" 2>&1 &
    HTTP_PID="$!"

    KEMU_SIZE=240x320 "${ROOT_DIR}/tools/kemu/run.sh" session \
        start "${TEMP_DIR}/install-probe.jar" > /dev/null
    if "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait permission \
        --timeout 1000 --json > /dev/null 2>&1; then
        "${ROOT_DIR}/tools/kemu/run.sh" session cmd permission allow \
            --once --json > /dev/null
    fi
    kemu_wait_display canvas - - 10000
    kemu_key_press FIRE
    kemu_wait_log 'W4ME_INSTALL state=COMMITTED id=[0-9]+' 10000
    kemu_key_press RSK
    kemu_wait_display list Paused - 5000
    kemu_observe "${TEMP_DIR}/system-menu.json"
    kemu_list_select 5 "${TEMP_DIR}/system-menu.json"
    kemu_wait_display list Paused 5 5000
    kemu_observe "${TEMP_DIR}/system-menu.json"

    kill "${HTTP_PID}" || true
    wait "${HTTP_PID}" 2> /dev/null || true
    HTTP_PID=""

    kemu_command_run Select "${TEMP_DIR}/system-menu.json" yes 10000
    kemu_launch_library_entry \
        "sound-demo" "${TEMP_DIR}/installed-library.json" 10000
    kemu_wait_log \
        'W4ME_LOAD cart=sound-demo bytes=1518 source=installed' \
        10000
    kemu_key_hold FIRE 80
    kemu_wait_log \
        'W4ME_TONE frequency=440 duration=60 volume=25700 flags=0' \
        10000
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/installed-offline.png" > /dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs read \
        > "${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"
    cp -- "${TEMP_DIR}/http.log" "${RESULT_DIR}/http.log"

    download_count="$(grep -F -c -- 'GET /sound-demo.wasm HTTP/1.1" 200' "${TEMP_DIR}/http.log")"
    if [[ "${download_count}" -ne 1 ]]; then
        printf 'error: expected exactly one HTTP cartridge download\n' >&2
        exit 1
    fi
    if ! grep -F -q -- 'W4ME_INSTALL state=DOWNLOADING' "${TEMP_DIR}/worker.log" \
        || ! grep -E -q -- \
            'W4ME_INSTALL state=RECEIVED id=[0-9]+ bytes=1518 chunks=1' \
            "${TEMP_DIR}/worker.log" \
        || ! grep -F -q -- 'W4ME_INSTALL state=VALIDATING' "${TEMP_DIR}/worker.log" \
        || ! grep -F -q -- 'W4ME_INSTALL state=TRANSLATING' "${TEMP_DIR}/worker.log" \
        || ! grep -E -q -- \
            'W4ME_INSTALL state=COMMITTED id=[0-9]+ bytes=1518 chunks=1 hash=' \
            "${TEMP_DIR}/worker.log"; then
        printf 'error: external cartridge did not complete the streamed RMS transaction\n' >&2
        exit 1
    fi
    if ! grep -F -q -- \
        'W4ME_LOAD cart=sound-demo bytes=1518 source=installed' "${TEMP_DIR}/worker.log"; then
        printf 'error: committed cartridge did not relaunch from RMS with HTTP offline\n' >&2
        exit 1
    fi
    tone_count="$(
        grep -F -c -- 'W4ME_TONE frequency=440 duration=60 volume=25700 flags=0' \
            "${TEMP_DIR}/worker.log"
    )"
    if [[ "${tone_count}" -lt 2 ]]; then
        printf 'error: Sound Demo did not execute before and after offline relaunch\n' >&2
        exit 1
    fi
    printf 'PASS KEmulator streamed HTTP install commit and offline RMS relaunch\n'
}

cmd_verify_library() {

    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/library"
    TEMP_DIR="$(mktemp -d)"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop > /dev/null 2>&1 || true
        rm -rf -- "${TEMP_DIR}"
    }
    trap cleanup EXIT

    rm -rf -- "${RESULT_DIR}"
    mkdir -p -- "${RESULT_DIR}" "${TEMP_DIR}/classes"
    cp -- "${SOURCE_JAR}" "${TEMP_DIR}/library-probe.jar"
    javac \
        -source "${J2ME_SOURCE}" \
        -target "${J2ME_TARGET}" \
        -Xlint:-options \
        -classpath "${MIDP_API_JAR}:${ROOT_DIR}/build/midlet/classes" \
        -d "${TEMP_DIR}/classes" \
        "${ROOT_DIR}/src/test/java/w4me/midp/CartridgeStoreProbeMidlet.java"
    jar uf "${TEMP_DIR}/library-probe.jar" -C "${TEMP_DIR}/classes" .
    {
        printf '%s\n' 'MIDlet-Name: W4ME Library Probe'
        printf '%s\n' 'MIDlet-1: W4ME Library Probe,,w4me.midp.CartridgeStoreProbeMidlet'
    } > "${TEMP_DIR}/probe.mf"
    jar ufm "${TEMP_DIR}/library-probe.jar" "${TEMP_DIR}/probe.mf" \
        2> "${TEMP_DIR}/manifest.log"

    KEMU_SIZE=176x220 "${ROOT_DIR}/tools/kemu/run.sh" session \
        start "${TEMP_DIR}/library-probe.jar" > /dev/null
    kemu_wait_log \
        'W4ME_LIBRARY_PROBE recovery=PASS .*tones=1' \
        10000
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs read \
        > "${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/library-probe.png" > /dev/null

    if ! grep -F -q -- \
        'W4ME_LIBRARY_PROBE recovery=PASS stream=PASS hidden=PASS committed=PASS reopen=PASS dedupe=PASS legacy=PASS chunks=1 bytes=1518 tones=1' \
        "${TEMP_DIR}/worker.log"; then
        printf 'error: transactional RMS cartridge library probe failed\n' >&2
        exit 1
    fi
    printf 'PASS KEmulator streamed RMS library staging/commit/reopen/dedupe/execute\n'
}

cmd_verify_rms() {

    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/rms"
    TEMP_DIR="$(mktemp -d)"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop > /dev/null 2>&1 || true
        rm -rf -- "${TEMP_DIR}"
    }
    trap cleanup EXIT

    rm -rf -- "${RESULT_DIR}"
    mkdir -p -- "${RESULT_DIR}" "${TEMP_DIR}/classes"
    cp -- "${SOURCE_JAR}" "${TEMP_DIR}/rms-probe.jar"
    javac \
        -source "${J2ME_SOURCE}" \
        -target "${J2ME_TARGET}" \
        -Xlint:-options \
        -classpath "${MIDP_API_JAR}:${ROOT_DIR}/build/midlet/classes" \
        -d "${TEMP_DIR}/classes" \
        "${ROOT_DIR}/src/test/java/w4me/midp/RmsDiskProbeMidlet.java"
    jar uf "${TEMP_DIR}/rms-probe.jar" \
        -C "${TEMP_DIR}/classes" w4me/midp/RmsDiskProbeMidlet.class
    {
        printf '%s\n' 'MIDlet-Name: W4ME RMS Probe'
        printf '%s\n' 'MIDlet-1: W4ME RMS Probe,,w4me.midp.RmsDiskProbeMidlet'
    } > "${TEMP_DIR}/probe.mf"
    jar ufm "${TEMP_DIR}/rms-probe.jar" "${TEMP_DIR}/probe.mf" \
        2> "${TEMP_DIR}/manifest.log"

    KEMU_SIZE=176x220 "${ROOT_DIR}/tools/kemu/run.sh" session \
        start "${TEMP_DIR}/rms-probe.jar" > /dev/null
    kemu_wait_log \
        'W4ME_RMS_PROBE grade=RMS bytes=1024 .*records=2' \
        10000
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs read \
        > "${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/rms-probe.png" > /dev/null

    if ! grep -F -q -- \
        'W4ME_RMS_PROBE grade=RMS bytes=1024 reopen=PASS recovery=PASS legacy=PASS records=2' \
        "${TEMP_DIR}/worker.log"; then
        printf 'error: production RMS disk backend failed A/B recovery probe\n' >&2
        exit 1
    fi
    printf 'PASS KEmulator RMS disk bytes=1024 A/B recovery and legacy migration\n'
}

cmd_verify_w4ir() {

    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/w4ir"
    TEMP_DIR="$(mktemp -d)"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop > /dev/null 2>&1 || true
        rm -rf -- "${TEMP_DIR}"
    }
    trap cleanup EXIT

    rm -rf -- "${RESULT_DIR}"
    mkdir -p -- "${RESULT_DIR}" "${TEMP_DIR}/classes"
    cp -- "${SOURCE_JAR}" "${TEMP_DIR}/w4ir-probe.jar"
    javac \
        -source "${J2ME_SOURCE}" \
        -target "${J2ME_TARGET}" \
        -Xlint:-options \
        -classpath "${MIDP_API_JAR}:${ROOT_DIR}/build/midlet/classes" \
        -d "${TEMP_DIR}/classes" \
        "${FRAMEBUFFER_ORACLE_SOURCE}" \
        "${ROOT_DIR}/src/test/java/w4me/midp/W4IrCacheProbeMidlet.java"
    jar uf "${TEMP_DIR}/w4ir-probe.jar" -C "${TEMP_DIR}/classes" .
    {
        printf '%s\n' 'MIDlet-Name: W4ME W4IR Probe'
        printf '%s\n' 'MIDlet-1: W4ME W4IR Probe,,w4me.midp.W4IrCacheProbeMidlet'
    } > "${TEMP_DIR}/probe.mf"
    jar ufm "${TEMP_DIR}/w4ir-probe.jar" "${TEMP_DIR}/probe.mf" \
        2> "${TEMP_DIR}/manifest.log"

    KEMU_SIZE=176x220 "${ROOT_DIR}/tools/kemu/run.sh" session \
        start "${TEMP_DIR}/w4ir-probe.jar" > /dev/null
    kemu_wait_log \
        'W4ME_W4IR_PROBE recovery=PASS old-format=PASS .*frame10-fnv1a=f90becd4' \
        30000
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs read \
        > "${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/w4ir-cache-probe.png" > /dev/null

    if ! grep -E -q -- \
        'W4ME_W4IR_PROBE recovery=PASS old-format=PASS build=PASS hit=PASS descriptors=PASS descriptor-hash=[0-9a-f]{8} slots=12 faults=[1-9][0-9]* warm-faults=0 hits=[0-9]+ promoted=[1-9][0-9]* compact-counters=(on|off) compact-blocks=[0-9]+ compact-instructions=[0-9]+ trace-loops=[1-9][0-9]* trace-iterations=[1-9][0-9]* fast-paths=0 first-ms=[0-9]+ warm-average-ms=[0-9]+ frame0-fnv1a=2e572184 frame10-fnv1a=f90becd4' \
        "${TEMP_DIR}/worker.log"; then
        printf 'error: generic RMS W4IR build/hit/paging/promotion probe failed\n' >&2
        exit 1
    fi
    source_jar_sha256="$(sha256_file "${SOURCE_JAR}")"
    captured_date="$(date -I)"
    {
        printf 'source-jar-sha256=%s\n' "${source_jar_sha256}"
        grep -F -- 'W4ME_W4IR_PROBE ' "${TEMP_DIR}/worker.log"
        printf '\nCaptured: %s\n' "${captured_date}"
    } > "${RESULT_DIR}/receipt.txt"
    printf 'PASS KEmulator generic W4IR old-format-rebuild/build/cache-hit/descriptors/12-slot paging/promotion framebuffer=2e572184\n'
}
