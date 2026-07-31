#!/usr/bin/env bash
# Sourced by tools/kemu/run.sh; not a standalone entrypoint.
set -euo pipefail

cmd_verify_launcher() {
    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/launcher"
    TEMP_DIR="$(mktemp -d)"
    DIAGNOSTIC_JAR="${TEMP_DIR}/launcher-diagnostic.jar"
    # Pins the release catalog and its order, which is a user-visible contract.
    # Keep in sync with w4me.midp.LibraryList and the list in tools/build.sh.
    EXPECTED_ITEMS='["Sokoban","Wasm Wars","Annoying Robots","Waternet","Dragon Poker Draw","Tic Tac Toe","Watris","Glowfish Chess","Duck Maze","Untangle","Nyan Cat","Sound Demo","Plasma Cube"]'
    SYSTEM_MENU_ITEMS='["Continue","Save State","Load State","Settings","Restart Cart","Exit"]'

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
        "W4ME Launcher Diagnostic" \
        "w4me.midp.DiagnosticLibraryMidlet" \
        "${ROOT_DIR}/src/test/java/w4me/midp/DiagnosticLibraryMidlet.java"
    KEMU_SIZE=176x220 "${ROOT_DIR}/tools/kemu/run.sh" session \
        start "${DIAGNOSTIC_JAR}" > /dev/null
    kemu_wait_display list "W4ME Station" 0 5000
    kemu_observe "${RESULT_DIR}/initial.json"

    if ! grep -F -q -- '"kind":"list"' \
        "${RESULT_DIR}/initial.json" \
        || ! grep -F -q -- '"title":"W4ME Station"' \
            "${RESULT_DIR}/initial.json" \
        || ! grep -F -q -- '"selectedIndex":0' \
            "${RESULT_DIR}/initial.json" \
        || ! kemu_displayable_items_match \
            "${RESULT_DIR}/initial.json" "${EXPECTED_ITEMS}" \
        || ! grep -F -q -- '"id":1,"text":"Run"' \
            "${RESULT_DIR}/initial.json" \
        || ! grep -F -q -- '"id":2,"text":"Choose .wasm file"' \
            "${RESULT_DIR}/initial.json" \
        || ! grep -F -q -- '"id":3,"text":"Settings"' \
            "${RESULT_DIR}/initial.json"; then
        printf 'error: native LCDUI launcher structure is incomplete\n' >&2
        exit 1
    fi

    kemu_command_run Run "${RESULT_DIR}/initial.json"
    kemu_wait_display canvas - - 5000
    kemu_observe "${RESULT_DIR}/cartridge.json"
    if ! grep -F -q -- '"kind":"canvas"' \
        "${RESULT_DIR}/cartridge.json" \
        || ! grep -F -q -- '"softkeys":{"left":"","right":""}' \
            "${RESULT_DIR}/cartridge.json"; then
        printf 'error: gameplay Canvas still exposes a platform command chooser\n' >&2
        exit 1
    fi

    # The raw Nokia right-softkey event directly requests a frame-boundary pause.
    # There is no intermediate one-item LCDUI command chooser; the next
    # Displayable must be the native paused-menu List.
    kemu_key_press RSK
    kemu_wait_display list Paused 0 5000
    kemu_observe "${RESULT_DIR}/system-menu.json"
    if ! grep -F -q -- '"kind":"list"' \
        "${RESULT_DIR}/system-menu.json" \
        || ! grep -F -q -- '"title":"Paused"' \
            "${RESULT_DIR}/system-menu.json" \
        || ! kemu_displayable_items_match \
            "${RESULT_DIR}/system-menu.json" "${SYSTEM_MENU_ITEMS}"; then
        printf 'error: native LCDUI system menu is incomplete\n' >&2
        exit 1
    fi
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/paused-menu.png" > /dev/null
    kemu_command_run Select "${RESULT_DIR}/system-menu.json"
    kemu_wait_display canvas - - 5000

    # Restart follows Continue, Save, Load, and Settings.
    kemu_key_press RSK
    kemu_wait_display list Paused - 5000
    kemu_observe "${RESULT_DIR}/restart-menu.json"
    kemu_list_select 4 "${RESULT_DIR}/restart-menu.json"
    kemu_wait_display list Paused 4 5000
    kemu_observe "${RESULT_DIR}/restart-menu.json"
    if ! grep -F -q -- '"selectedIndex":4' \
        "${RESULT_DIR}/restart-menu.json"; then
        printf 'error: diagnostic Restart Cart selection is missing\n' >&2
        exit 1
    fi
    kemu_command_run Select "${RESULT_DIR}/restart-menu.json"
    kemu_wait_display canvas - - 5000
    kemu_observe "${RESULT_DIR}/restarted.json"
    if ! grep -F -q -- '"kind":"canvas"' \
        "${RESULT_DIR}/restarted.json"; then
        printf 'error: Restart Cart did not reopen the cartridge Canvas\n' >&2
        exit 1
    fi

    # Game-origin Settings keeps the worker paused and returns through
    # Audio -> Settings -> the same native system menu.
    kemu_key_press RSK
    kemu_wait_display list Paused - 5000
    kemu_observe "${RESULT_DIR}/settings-menu.json"
    kemu_list_select 3 "${RESULT_DIR}/settings-menu.json"
    kemu_wait_display list Paused 3 5000
    kemu_observe "${RESULT_DIR}/settings-menu.json"
    if ! grep -F -q -- '"selectedIndex":3' \
        "${RESULT_DIR}/settings-menu.json"; then
        printf 'error: diagnostic Settings selection is missing\n' >&2
        exit 1
    fi
    kemu_command_run Select "${RESULT_DIR}/settings-menu.json"
    kemu_wait_display list Settings 0 5000
    kemu_observe "${RESULT_DIR}/game-settings.json"
    if ! grep -F -q -- '"kind":"list"' \
        "${RESULT_DIR}/game-settings.json" \
        || ! grep -F -q -- '"title":"Settings"' \
            "${RESULT_DIR}/game-settings.json" \
        || ! kemu_displayable_items_match \
            "${RESULT_DIR}/game-settings.json" '["Audio"]'; then
        printf 'error: game-origin Settings category list is incomplete\n' >&2
        exit 1
    fi

    kemu_command_run Open "${RESULT_DIR}/game-settings.json"
    kemu_wait_display form Audio - 5000
    kemu_observe "${RESULT_DIR}/game-audio.json"
    if ! grep -F -q -- '"kind":"form"' \
        "${RESULT_DIR}/game-audio.json" \
        || ! grep -F -q -- '"title":"Audio"' \
            "${RESULT_DIR}/game-audio.json" \
        || ! grep -F -q -- '"left":"Save"' \
            "${RESULT_DIR}/game-audio.json" \
        || ! grep -F -q -- '"right":"Cancel"' \
            "${RESULT_DIR}/game-audio.json"; then
        printf 'error: game-origin Audio form is incomplete\n' >&2
        exit 1
    fi
    kemu_key_press RSK
    kemu_wait_display list Settings 0 5000
    kemu_observe "${TEMP_DIR}/game-settings-returned.json"
    kemu_key_press RSK
    kemu_wait_display list Paused 3 5000
    kemu_observe "${TEMP_DIR}/paused-returned.json"
    kemu_key_press RSK
    kemu_wait_display canvas - - 5000

    # Exit remains the final action after Save/Load are enabled.
    kemu_key_press RSK
    kemu_wait_display list Paused - 5000
    kemu_observe "${RESULT_DIR}/exit-menu.json"
    kemu_list_select 5 "${RESULT_DIR}/exit-menu.json"
    kemu_wait_display list Paused 5 5000
    kemu_observe "${RESULT_DIR}/exit-menu.json"
    if ! grep -F -q -- '"selectedIndex":5' \
        "${RESULT_DIR}/exit-menu.json"; then
        printf 'error: diagnostic Exit-last selection is missing\n' >&2
        exit 1
    fi
    kemu_command_run Select "${RESULT_DIR}/exit-menu.json"
    kemu_wait_display list "W4ME Station" 0 5000
    kemu_observe "${RESULT_DIR}/returned.json"
    if ! grep -F -q -- '"kind":"list"' \
        "${RESULT_DIR}/returned.json" \
        || ! grep -F -q -- '"selectedIndex":0' \
            "${RESULT_DIR}/returned.json" \
        || ! kemu_displayable_items_match \
            "${RESULT_DIR}/returned.json" "${EXPECTED_ITEMS}"; then
        printf 'error: returning from a cartridge did not restore launcher focus\n' >&2
        exit 1
    fi

    # Library-origin Settings returns to the library without a game session.
    kemu_command_run Settings "${RESULT_DIR}/returned.json"
    kemu_wait_display list Settings 0 5000
    kemu_observe "${RESULT_DIR}/library-settings.json"
    if ! grep -F -q -- '"kind":"list"' \
        "${RESULT_DIR}/library-settings.json" \
        || ! grep -F -q -- '"title":"Settings"' \
            "${RESULT_DIR}/library-settings.json" \
        || ! kemu_displayable_items_match \
            "${RESULT_DIR}/library-settings.json" '["Audio"]'; then
        printf 'error: launcher did not open library-origin Settings\n' >&2
        exit 1
    fi
    kemu_key_press RSK
    kemu_wait_display list "W4ME Station" 0 5000

    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs read \
        > "${RESULT_DIR}/worker.log"
    if grep -E -q -- 'W4ME_ERROR|Exception in thread' \
        "${RESULT_DIR}/worker.log"; then
        printf 'error: native launcher flow reported a runtime failure\n' >&2
        exit 1
    fi
    if ! grep -F -q -- \
        'W4ME_SESSION_CLOSED cart=Sokoban reason=restart count=1' \
        "${RESULT_DIR}/worker.log" \
        || ! grep -F -q -- \
            'W4ME_SESSION_CLOSED cart=Sokoban reason=exit count=2' \
            "${RESULT_DIR}/worker.log"; then
        printf 'error: restart/exit did not close each worker-owned session exactly once\n' >&2
        exit 1
    fi
    source_jar_sha256="$(sha256_file "${SOURCE_JAR}")"
    {
        printf 'source-jar-sha256=%s\n' "${source_jar_sha256}"
        printf '%s\n' \
            'displayable=list items=13 selected=0 native-menu=PASS restart=PASS exit-last=PASS game-settings=PASS library-settings=PASS cleanup=PASS'
    } > "${RESULT_DIR}/receipt.txt"
    printf 'PASS KEmulator launcher native-menu restart exit-last settings origins cleanup\n'
}

cmd_verify_external() {

    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/external"
    TEMP_DIR="$(mktemp -d)"
    HTTP_PORT=18384
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
    mkdir -p -- "${RESULT_DIR}"
    cp -- "${SOURCE_JAR}" "${TEMP_DIR}/external-loader.jar"
    mkdir -p -- "${TEMP_DIR}/classes"
    javac \
        -source "${J2ME_SOURCE}" \
        -target "${J2ME_TARGET}" \
        -Xlint:-options \
        -classpath "${MIDP_API_JAR}:${ROOT_DIR}/build/midlet/classes" \
        -d "${TEMP_DIR}/classes" \
        "${ROOT_DIR}/src/test/java/w4me/midp/ExternalLoaderProbeMidlet.java"
    jar uf "${TEMP_DIR}/external-loader.jar" \
        -C "${TEMP_DIR}/classes" w4me/midp/ExternalLoaderProbeMidlet.class
    {
        printf '%s\n' 'MIDlet-Name: W4ME External Probe'
        printf '%s\n' 'MIDlet-1: W4ME External Probe,,w4me.midp.ExternalLoaderProbeMidlet'
    } > "${TEMP_DIR}/probe.mf"
    jar ufm "${TEMP_DIR}/external-loader.jar" "${TEMP_DIR}/probe.mf" \
        2> "${TEMP_DIR}/manifest.log"

    python3 -m http.server "${HTTP_PORT}" \
        --bind 127.0.0.1 \
        --directory "${ROOT_DIR}/cartridges" \
        > "${TEMP_DIR}/http.log" 2>&1 &
    HTTP_PID="$!"

    KEMU_SIZE=240x320 "${ROOT_DIR}/tools/kemu/run.sh" session \
        start "${TEMP_DIR}/external-loader.jar" > /dev/null
    kemu_wait_log \
        'W4ME_EXTERNAL_PROBE bytes=1518 tones=1 backend=[A-E]-[A-Za-z0-9-]+' \
        10000
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs read > "${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"
    cp -- "${TEMP_DIR}/http.log" "${RESULT_DIR}/http.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/external-sound-demo.png" > /dev/null

    if ! grep -F -q -- 'GET /sound-demo.wasm HTTP/1.1" 200' "${TEMP_DIR}/http.log"; then
        printf 'error: KEmulator did not fetch the cartridge over HTTP\n' >&2
        exit 1
    fi
    # This scenario proves the HTTP fetch and execution path, not audio tiering.
    # The backend name is deliberately not pinned: it reports whichever tier of the
    # PCM/MIDI/playTone/silent ladder is active at that instant, which depends on
    # whether the player has finished starting. Tier selection is asserted exactly
    # by the dedicated sound scenario.
    if ! grep -E -q -- \
        'W4ME_EXTERNAL_PROBE bytes=1518 tones=1 backend=[A-E]-[A-Za-z0-9-]+' \
        "${TEMP_DIR}/worker.log"; then
        printf 'error: externally downloaded cartridge was not executed\n' >&2
        exit 1
    fi
    if ! grep -E -q -- \
        'W4ME_TONE frequency=440 duration=60 volume=25700 flags=0 backend=[A-E]-[A-Za-z0-9-]+' \
        "${TEMP_DIR}/worker.log"; then
        printf 'error: externally downloaded Sound Demo did not run correctly\n' >&2
        exit 1
    fi
    printf 'PASS KEmulator external HTTP cartridge bytes=1518 tone=440,60,25700,0\n'
}

cmd_verify_file_picker() {

    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    BASE_JAR="${ROOT_DIR}/dist/w4me-station-base.jar"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/file-picker"
    TEMP_DIR="$(mktemp -d)"
    UI_JAR="${TEMP_DIR}/file-picker-ui.jar"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop > /dev/null 2>&1 || true
        rm -rf -- "${TEMP_DIR}"
    }
    trap cleanup EXIT

    rm -rf -- "${RESULT_DIR}"
    mkdir -p -- \
        "${RESULT_DIR}" \
        "${TEMP_DIR}/classes" \
        "${TEMP_DIR}/file-root/w4me-picker" \
        "${TEMP_DIR}/kemu-no-jsr75"
    cp -- "${ROOT_DIR}/cartridges/sound-demo.wasm" \
        "${TEMP_DIR}/file-root/w4me-picker/sound-demo.wasm"
    printf '%s\n' 'not a cartridge' \
        > "${TEMP_DIR}/file-root/w4me-picker/ignored.txt"

    cp -- "${SOURCE_JAR}" "${TEMP_DIR}/file-picker-probe.jar"
    javac \
        -source "${J2ME_SOURCE}" \
        -target "${J2ME_TARGET}" \
        -Xlint:-options \
        -classpath "${MIDP_API_JAR}:${ROOT_DIR}/build/midlet/classes" \
        -d "${TEMP_DIR}/classes" \
        "${ROOT_DIR}/src/test/java/w4me/midp/FilePickerProbeMidlet.java"
    jar uf "${TEMP_DIR}/file-picker-probe.jar" -C "${TEMP_DIR}/classes" .
    {
        printf '%s\n' 'MIDlet-Name: W4ME File Picker Probe'
        printf '%s\n' 'MIDlet-1: W4ME File Picker Probe,,w4me.midp.FilePickerProbeMidlet'
    } > "${TEMP_DIR}/probe.mf"
    jar ufm "${TEMP_DIR}/file-picker-probe.jar" "${TEMP_DIR}/probe.mf" \
        2> "${TEMP_DIR}/manifest.log"

    KEMU_FILE_ROOT="${TEMP_DIR}/file-root" KEMU_RESET_STATE=no \
        KEMU_SIZE=240x320 \
        "${ROOT_DIR}/tools/kemu/run.sh" session \
        start "${TEMP_DIR}/file-picker-probe.jar" > /dev/null
    if "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait permission \
        --name javax.microedition.io.Connector.file.read \
        --timeout 1000 --json > /dev/null 2>&1; then
        "${ROOT_DIR}/tools/kemu/run.sh" session cmd permission allow \
            --once --json > /dev/null
    fi
    kemu_wait_log \
        'W4ME_FILE_PICKER_PROBE roots=PASS.*cleanup=PASS' \
        5000
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/probe.png" > /dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs read \
        > "${RESULT_DIR}/worker.log"

    if ! grep -F -q -- \
        'W4ME_FILE_PICKER_PROBE roots=PASS filter=PASS select=PASS size=1518 stage=PASS validate=PASS commit=PASS denied=PASS changed=PASS oversized=PASS cleanup=PASS' \
        "${RESULT_DIR}/worker.log"; then
        printf 'error: JSR-75 file picker probe did not pass\n' >&2
        exit 1
    fi
    if grep -F -q -- 'W4ME_FILE_PICKER_ERROR' "${RESULT_DIR}/worker.log"; then
        printf 'error: JSR-75 file picker probe reported a failure\n' >&2
        exit 1
    fi

    "${ROOT_DIR}/tools/kemu/run.sh" session stop > /dev/null
    build_diagnostic_jar \
        "${SOURCE_JAR}" \
        "${UI_JAR}" \
        "${TEMP_DIR}/ui-classes" \
        "W4ME File Picker UI" \
        "w4me.midp.DiagnosticLibraryMidlet" \
        "${ROOT_DIR}/src/test/java/w4me/midp/DiagnosticLibraryMidlet.java"

    KEMU_FILE_ROOT="${TEMP_DIR}/file-root" KEMU_RESET_STATE=no \
        KEMU_SIZE=240x320 \
        "${ROOT_DIR}/tools/kemu/run.sh" session \
        start "${UI_JAR}" > /dev/null
    kemu_wait_display list "W4ME Station" 0 5000
    kemu_observe "${TEMP_DIR}/launcher.json"
    if ! grep -F -q -- '"id":2,"text":"Choose .wasm file"' \
        "${TEMP_DIR}/launcher.json"; then
        printf 'error: native launcher file-picker command is missing\n' >&2
        exit 1
    fi
    kemu_command_run "Choose .wasm file" "${TEMP_DIR}/launcher.json"
    kemu_wait_display list "Choose .wasm file" - 5000
    kemu_observe "${RESULT_DIR}/roots.json"
    if ! grep -F -q -- '"kind":"list"' "${RESULT_DIR}/roots.json" \
        || ! grep -F -q -- '"title":"Choose .wasm file"' "${RESULT_DIR}/roots.json" \
        || ! kemu_displayable_items_match \
            "${RESULT_DIR}/roots.json" '["[dir] root"]' \
        || ! grep -F -q -- '"text":"Enter manually"' "${RESULT_DIR}/roots.json"; then
        printf 'error: file picker did not open as a native list\n' >&2
        exit 1
    fi
    kemu_command_run Open "${RESULT_DIR}/roots.json"
    kemu_wait_display list root 0 5000
    kemu_observe "${RESULT_DIR}/root-directory.json"

    if ! kemu_displayable_items_match \
        "${RESULT_DIR}/root-directory.json" '["[..]","[dir] w4me-picker"]'; then
        printf 'error: file picker did not list the parent row and test directory\n' >&2
        exit 1
    fi
    kemu_list_select 1 "${RESULT_DIR}/root-directory.json"
    kemu_wait_display list root 1 5000
    kemu_observe "${TEMP_DIR}/selected-directory.json"
    kemu_command_run Open "${TEMP_DIR}/selected-directory.json"
    kemu_wait_display list w4me-picker 0 5000
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/filtered-directory.png" > /dev/null
    kemu_observe "${RESULT_DIR}/selection.json"

    # Entering the test directory must show the parent row followed by the
    # filtered cartridge. The row layout, scrolling and highlight belong to the
    # platform; selecting index 1 is the critical non-first native List action.
    if ! grep -F -q -- '"kind":"list"' \
        "${RESULT_DIR}/selection.json" \
        || ! grep -F -q -- '"title":"w4me-picker"' \
            "${RESULT_DIR}/selection.json" \
        || ! kemu_displayable_items_match \
            "${RESULT_DIR}/selection.json" '["[..]","sound-demo.wasm"]'; then
        printf 'error: file picker did not filter the selected directory\n' >&2
        exit 1
    fi
    kemu_list_select 1 "${RESULT_DIR}/selection.json"
    kemu_wait_display list w4me-picker 1 5000
    kemu_observe "${TEMP_DIR}/selected-file.json"
    kemu_command_run Open "${TEMP_DIR}/selected-file.json"
    kemu_wait_display form "Install .wasm" - 5000
    kemu_observe "${TEMP_DIR}/install-form.json"
    kemu_command_run Install "${TEMP_DIR}/install-form.json"
    kemu_wait_display canvas - - 5000

    "${ROOT_DIR}/tools/kemu/run.sh" session stop > /dev/null

    source_jsr75_count="$(
        unzip -Z1 "${SOURCE_JAR}" \
            | awk '$0 == "w4me/midp/Jsr75FileSystem.class" { count++ } END { print count + 0 }'
    )"
    if [[ "${source_jsr75_count}" -ne 1 ]]; then
        printf 'error: optional JAR does not contain the JSR-75 adapter\n' >&2
        exit 1
    fi
    base_jsr75_count="$(
        unzip -Z1 "${BASE_JAR}" \
            | awk '$0 == "w4me/midp/Jsr75FileSystem.class" { count++ } END { print count + 0 }'
    )"
    if [[ "${base_jsr75_count}" -ne 0 ]]; then
        printf 'error: base JAR contains the JSR-75 adapter\n' >&2
        exit 1
    fi

    cp -R -- "${KEMU_HOME}/." "${TEMP_DIR}/kemu-no-jsr75/"
    zip -q -d "${TEMP_DIR}/kemu-no-jsr75/KEmulator.jar" \
        'javax/microedition/io/file/*'
    KEMU_BUNDLE="${TEMP_DIR}/kemu-no-jsr75" KEMU_SIZE=240x320 \
        "${ROOT_DIR}/tools/kemu/run.sh" session \
        start "${BASE_JAR}" > /dev/null
    kemu_wait_display list "W4ME Station" - 5000
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd state \
        > "${RESULT_DIR}/base-without-jsr75-state.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs read \
        > "${RESULT_DIR}/base-without-jsr75-worker.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/base-without-jsr75.png" > /dev/null

    if ! grep -F -q -- 'Displayable: list' \
        "${RESULT_DIR}/base-without-jsr75-state.log"; then
        printf 'error: base JAR did not open its LCDUI library without JSR-75\n' >&2
        exit 1
    fi
    if grep -E -q -- 'NoClassDefFoundError|ClassNotFoundException|W4ME_ERROR' \
        "${RESULT_DIR}/base-without-jsr75-worker.log"; then
        printf 'error: base JAR referenced JSR-75 at runtime\n' >&2
        exit 1
    fi

    printf 'PASS KEmulator JSR-75 UI picker, RMS cleanup, and base runtime without JSR-75\n'
}

cmd_verify_invalid() {

    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/invalid"
    TEMP_DIR="$(mktemp -d)"
    HTTP_PORT=18386
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
    printf '\000asm\001\000\000\000\001\001\000\001\001\000' \
        > "${TEMP_DIR}/http/bad.wasm"
    cp -- "${SOURCE_JAR}" "${TEMP_DIR}/invalid-probe.jar"
    javac \
        -source "${J2ME_SOURCE}" \
        -target "${J2ME_TARGET}" \
        -Xlint:-options \
        -classpath "${MIDP_API_JAR}:${ROOT_DIR}/build/midlet/classes" \
        -d "${TEMP_DIR}/classes" \
        "${FRAMEBUFFER_ORACLE_SOURCE}" \
        "${ROOT_DIR}/src/test/java/w4me/midp/DiagnosticW4MeMidlet.java" \
        "${ROOT_DIR}/src/test/java/w4me/midp/DiagnosticW4SessionMonitor.java" \
        "${ROOT_DIR}/src/test/java/w4me/midp/InvalidCartProbeMidlet.java"
    jar uf "${TEMP_DIR}/invalid-probe.jar" -C "${TEMP_DIR}/classes" .
    {
        printf '%s\n' 'MIDlet-Name: W4ME Invalid Cart Probe'
        printf '%s\n' 'MIDlet-1: W4ME Invalid Cart Probe,,w4me.midp.InvalidCartProbeMidlet'
    } > "${TEMP_DIR}/probe.mf"
    jar ufm "${TEMP_DIR}/invalid-probe.jar" "${TEMP_DIR}/probe.mf" \
        2> "${TEMP_DIR}/manifest.log"

    python3 -m http.server "${HTTP_PORT}" \
        --bind 127.0.0.1 \
        --directory "${TEMP_DIR}/http" \
        > "${TEMP_DIR}/http.log" 2>&1 &
    HTTP_PID="$!"

    KEMU_SIZE=240x320 "${ROOT_DIR}/tools/kemu/run.sh" session \
        start "${TEMP_DIR}/invalid-probe.jar" > /dev/null
    if "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait permission \
        --timeout 1000 --json > /dev/null 2>&1; then
        "${ROOT_DIR}/tools/kemu/run.sh" session cmd permission allow \
            --once --json > /dev/null
    fi
    kemu_wait_display alert - - 5000
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/invalid-cart-alert.png" > /dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd state > "${TEMP_DIR}/alert-state.log"
    kemu_key_press LSK
    kemu_wait_display list - - 5000
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/launcher-after-invalid-cart.png" > /dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd state > "${TEMP_DIR}/launcher-state.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs read \
        > "${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"
    cp -- "${TEMP_DIR}/http.log" "${RESULT_DIR}/http.log"
    cp -- "${TEMP_DIR}/alert-state.log" "${RESULT_DIR}/alert-state.log"
    cp -- "${TEMP_DIR}/launcher-state.log" "${RESULT_DIR}/launcher-state.log"

    download_count="$(grep -F -c -- 'GET /bad.wasm HTTP/1.1" 200' "${TEMP_DIR}/http.log")"
    if [[ "${download_count}" -ne 1 ]]; then
        printf 'error: expected exactly one invalid cartridge download\n' >&2
        exit 1
    fi
    if ! grep -F -q -- 'W4ME_ERROR w4me.wasm.WasmException: duplicate section 1' \
        "${TEMP_DIR}/worker.log"; then
        printf 'error: strict loader rejection was not reported\n' >&2
        exit 1
    fi
    if grep -F -q -- 'W4ME_INSTALL state=COMMITTED' "${TEMP_DIR}/worker.log"; then
        printf 'error: rejected cartridge was committed to the RMS library\n' >&2
        exit 1
    fi
    if ! grep -F -q -- 'Displayable: alert' "${TEMP_DIR}/alert-state.log"; then
        printf 'error: invalid cartridge did not open a MIDP Alert\n' >&2
        exit 1
    fi
    if ! grep -F -q -- 'Displayable: list' "${TEMP_DIR}/launcher-state.log"; then
        printf 'error: dismissing the Alert did not return to the LCDUI launcher\n' >&2
        exit 1
    fi
    printf 'PASS KEmulator invalid cart alert, launcher recovery, no RMS commit\n'
}
