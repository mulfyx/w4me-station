#!/usr/bin/env bash
# Sourced by tools/kemu/run.sh; not a standalone entrypoint.
set -euo pipefail

cmd_verify_sound_test() {
    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/sound-test"
    TEMP_DIR="$(mktemp -d)"
    DIAGNOSTIC_JAR="${TEMP_DIR}/sound-test-diagnostic.jar"

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
        "W4ME Sound Test Diagnostic" \
        'w4me.midp.DirectCartridgeProbeMidlet$SoundTest' \
        "${ROOT_DIR}/src/test/java/w4me/midp/DirectCartridgeProbeMidlet.java"

    KEMU_SIZE=240x320 "${ROOT_DIR}/tools/kemu/run.sh" session start "${DIAGNOSTIC_JAR}" > /dev/null
    kemu_wait_log \
        'W4ME_FRAME cart=Sound Test frame=0 .*framebuffer-fnv1a=a4b700fa' \
        10000

    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs read > "${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/sound-test-live.png" > /dev/null

    if ! grep -F -q -- \
        'W4ME_FRAME cart=Sound Test frame=0' "${TEMP_DIR}/worker.log"; then
        printf 'error: Sound Test did not start in KEmulator\n' >&2
        exit 1
    fi
    if ! grep -E -q -- \
        'W4ME_FRAME cart=Sound Test frame=0 .*framebuffer-fnv1a=a4b700fa' \
        "${TEMP_DIR}/worker.log"; then
        printf 'error: Sound Test first frame differs from the clean web oracle\n' >&2
        exit 1
    fi
    printf 'PASS KEmulator sound-test frame=0 framebuffer-fnv1a=a4b700fa\n'
}

cmd_verify_sound() {
    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/sound"
    TEMP_DIR="$(mktemp -d)"
    DIAGNOSTIC_JAR="${TEMP_DIR}/sound-diagnostic.jar"

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
        "W4ME Sound Diagnostic" \
        'w4me.midp.DiagnosticLibraryMidlet' \
        "${ROOT_DIR}/src/test/java/w4me/midp/DiagnosticLibraryMidlet.java"
    printf '%s\n' 'W4ME-Audio-Backend: midi' > "${TEMP_DIR}/audio.mf"
    jar ufm "${DIAGNOSTIC_JAR}" "${TEMP_DIR}/audio.mf" \
        2> "${TEMP_DIR}/manifest.log"

    KEMU_SIZE=240x320 "${ROOT_DIR}/tools/kemu/run.sh" session start "${DIAGNOSTIC_JAR}" > /dev/null
    kemu_launch_library_entry "Sound Demo" "${TEMP_DIR}/library.json"
    kemu_key_hold FIRE 80
    kemu_wait_log \
        'W4ME_TONE frequency=440 duration=60 volume=25700 flags=0 backend=C-smf4' \
        10000

    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs read > "${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/sound-demo-live.png" > /dev/null

    expected='W4ME_TONE frequency=440 duration=60 volume=25700 flags=0 backend=C-smf4'
    if ! grep -F -q -- "${expected}" "${TEMP_DIR}/worker.log"; then
        printf 'error: Sound Demo did not reach the exact MMAPI tone receipt\n' >&2
        exit 1
    fi
    printf 'PASS KEmulator sound-demo tone=440,60,25700,0 backend=C-smf4\n'
}

cmd_verify_audio_settings() {
    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/audio-settings"
    TEMP_DIR="$(mktemp -d)"
    PROBE_JAR="${TEMP_DIR}/audio-settings-probe.jar"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop > /dev/null 2>&1 || true
        rm -rf -- "${TEMP_DIR}"
    }
    trap cleanup EXIT

    rm -rf -- "${RESULT_DIR}"
    mkdir -p -- "${RESULT_DIR}" "${TEMP_DIR}/classes"
    cp -- "${SOURCE_JAR}" "${PROBE_JAR}"
    javac \
        -source "${J2ME_SOURCE}" \
        -target "${J2ME_TARGET}" \
        -Xlint:-options \
        -classpath "${MIDP_API_JAR}:${ROOT_DIR}/build/midlet/classes" \
        -d "${TEMP_DIR}/classes" \
        "${ROOT_DIR}/src/test/java/w4me/midp/AudioSettingsProbeMidlet.java"
    jar uf "${PROBE_JAR}" -C "${TEMP_DIR}/classes" .
    {
        printf '%s\n' 'MIDlet-Name: W4ME Audio Settings Probe'
        printf '%s\n' \
            'MIDlet-1: W4ME Audio Settings Probe,,w4me.midp.AudioSettingsProbeMidlet'
    } > "${TEMP_DIR}/probe.mf"
    jar ufm "${PROBE_JAR}" "${TEMP_DIR}/probe.mf" \
        2> "${TEMP_DIR}/manifest.log"

    KEMU_SIZE=176x220 "${ROOT_DIR}/tools/kemu/run.sh" session start "${PROBE_JAR}" > /dev/null
    kemu_wait_log \
        'W4ME_AUDIO_SETTINGS_PROBE active-mute=PASS .*form-mode=PASS' \
        10000
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/audio-settings-default.png" > /dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs read \
        > "${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"

    if grep -F -q -- 'W4ME_AUDIO_SETTINGS_ERROR' "${TEMP_DIR}/worker.log"; then
        printf 'error: audio settings probe reported a failure\n' >&2
        exit 1
    fi
    if ! grep -F -q -- \
        'W4ME_AUDIO_SETTINGS_PROBE active-mute=PASS persisted-mute=PASS gain=50 scaled=12850 capability=MUTE_ONLY form-gain=100 form-mode=PASS' \
        "${TEMP_DIR}/worker.log"; then
        printf 'error: audio settings probe did not complete\n' >&2
        exit 1
    fi
    source_jar_sha256="$(sha256_file "${SOURCE_JAR}")"
    {
        printf 'source-jar-sha256=%s\n' "${source_jar_sha256}"
        grep -F -- 'W4ME_AUDIO_SETTINGS_PROBE' "${TEMP_DIR}/worker.log"
    } > "${RESULT_DIR}/receipt.txt"
    printf 'PASS KEmulator audio-settings active-mute persisted-mute gain capability form-mode\n'
}

cmd_verify_touch() {
    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/touch"
    TEMP_DIR="$(mktemp -d)"
    DIAGNOSTIC_JAR="${TEMP_DIR}/touch-diagnostic.jar"

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
        "W4ME Touch Diagnostic" \
        'w4me.midp.DiagnosticLibraryMidlet' \
        "${ROOT_DIR}/src/test/java/w4me/midp/DiagnosticLibraryMidlet.java"

    KEMU_SIZE=240x320 "${ROOT_DIR}/tools/kemu/run.sh" session start "${DIAGNOSTIC_JAR}" > /dev/null
    kemu_launch_library_entry "Sound Demo" "${TEMP_DIR}/library.json"
    # A zero-duration tap can press and release entirely between two 30 Hz MIDlet
    # frames. Keep the pointer inside the A button while holding it long enough for
    # the runtime to observe GAMEPAD1.
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd drag 210 288 211 288 --delay 120 > /dev/null
    kemu_wait_log \
        'W4ME_INPUT cart=Sound Demo frame=[0-9]+ gamepad=1 touch=1 mouse=0' \
        10000

    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs read > "${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/touch-controls.png" > /dev/null

    if ! grep -E -q -- 'W4ME_INPUT cart=Sound Demo frame=[0-9]+ gamepad=1 touch=1 mouse=0' \
        "${TEMP_DIR}/worker.log"; then
        printf 'error: touch A did not reach WASM-4 gamepad 1\n' >&2
        exit 1
    fi
    if ! grep -F -q -- \
        'W4ME_LAYOUT screen=240x320 framebuffer=0,8,240 controls=256,64 overlap=0' \
        "${TEMP_DIR}/worker.log"; then
        printf 'error: touch controls overlap the WASM-4 framebuffer\n' >&2
        exit 1
    fi
    # This scenario proves that the on-screen gamepad reaches GAMEPAD1 and drives
    # the cartridge, not which audio tier answers. See cmd_verify_external for why
    # the backend name is not pinned here.
    if ! grep -E -q -- \
        'W4ME_TONE frequency=440 duration=60 volume=25700 flags=0 backend=[A-E]-[A-Za-z0-9-]+' \
        "${TEMP_DIR}/worker.log"; then
        printf 'error: touch A did not trigger the Sound Demo tone\n' >&2
        exit 1
    fi
    printf 'PASS KEmulator touch gamepad=1 tone=440,60,25700,0\n'
}
