#!/usr/bin/env bash
# shellcheck disable=SC2016,SC2329
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
FRAMEBUFFER_ORACLE_SOURCE="${ROOT_DIR}/src/test/java/w4me/FramebufferOracle.java"

if [ "${W4ME_TOOLCHAIN_CONTAINER:-}" != "1" ]; then
    exec "${ROOT_DIR}/tools/container/run.sh" kemu "$@"
fi

# shellcheck disable=SC1091
source "${ROOT_DIR}/tools/container/env.sh"

compile_diagnostic_runtime() {
    classes_dir="$1"
    sources_file="$2"

    mkdir -p -- "${classes_dir}"
    find "${ROOT_DIR}/src/main/java" -name '*.java' -print |
        sort >"${sources_file}"
    javac \
        -source "${J2ME_SOURCE}" \
        -target "${J2ME_TARGET}" \
        -Xlint:-options \
        -bootclasspath "${J2ME_BOOTCLASSPATH}" \
        -classpath "${MIDP_API_JAR}" \
        -d "${classes_dir}" \
        @"${sources_file}"
}

build_diagnostic_jar() {
    source_jar="$1"
    output_jar="$2"
    classes_dir="$3"
    midlet_name="$4"
    midlet_class="$5"
    shift 5

    cp -- "${source_jar}" "${output_jar}"
    mkdir -p -- "${classes_dir}"
    javac \
        -source "${J2ME_SOURCE}" \
        -target "${J2ME_TARGET}" \
        -Xlint:-options \
        -classpath "${MIDP_API_JAR}:${ROOT_DIR}/build/midlet/classes" \
        -d "${classes_dir}" \
        "${FRAMEBUFFER_ORACLE_SOURCE}" \
        "${ROOT_DIR}/src/test/java/w4me/midp/DiagnosticW4MeMidlet.java" \
        "${ROOT_DIR}/src/test/java/w4me/midp/DiagnosticW4SessionMonitor.java" \
        "$@"
    jar uf "${output_jar}" -C "${classes_dir}" .
    # Cartridges that are regression fixtures but are not part of the release
    # catalog. Probes load them from the classpath, so the diagnostic JAR carries
    # them and the test corpus stays independent of what the library ships.
    jar uf "${output_jar}" -C "${ROOT_DIR}" cartridges/mandelbrot.wasm
    jar uf "${output_jar}" -C "${ROOT_DIR}" cartridges/sound-test.wasm
    jar uf "${output_jar}" -C "${ROOT_DIR}" cartridges/tankle.wasm
    jar uf "${output_jar}" -C "${ROOT_DIR}" cartridges/game-of-life-zig-edition.wasm
    {
        printf 'MIDlet-Name: %s\n' "${midlet_name}"
        printf 'MIDlet-1: %s,,%s\n' "${midlet_name}" "${midlet_class}"
    } >"${classes_dir}/probe.mf"
    jar ufm "${output_jar}" "${classes_dir}/probe.mf" \
        2>"${classes_dir}/manifest.log"
}

cmd_session() {
    # Run KEmulator from a fresh writable copy of the image-owned /opt/kemu
    # bundle. The automation controller needs a persistent X display even in
    # headless mode.

    SESSION_DIR="/tmp/w4me-station-kemu-session"
    RUN_DIR="${SESSION_DIR}/kemu"
    XVFB_PID_FILE="${SESSION_DIR}/xvfb.pid"
    DISPLAY_NUM="${KEMU_DISPLAY:-:98}"
    DEFAULT_JAR="${ROOT_DIR}/dist/w4me-station.jar"
    SCREEN_SIZE="${KEMU_SIZE:-240x320}"

    stop_session() {
        if [ -x "${RUN_DIR}/kemu.sh" ]; then
            (cd -- "${RUN_DIR}" && DISPLAY="${DISPLAY_NUM}" ./kemu.sh stop --force) || true
        fi
        if [ -r "${XVFB_PID_FILE}" ]; then
            xvfb_pid="$(cat -- "${XVFB_PID_FILE}")"
            if [ -n "${xvfb_pid}" ] && kill -0 "${xvfb_pid}" 2>/dev/null; then
                kill "${xvfb_pid}" || true
            fi
        fi
        rm -rf -- "${SESSION_DIR}"
    }

    case "${1:?usage: tools/kemu/run.sh session <start [jar]|cmd args...|stop>}" in
    start)
        jar="${2:-${KEMU_JAR:-${DEFAULT_JAR}}}"
        if [ ! -f "${jar}" ]; then
            printf 'error: MIDlet JAR not found: %s\n' "${jar}" >&2
            exit 1
        fi
        jar="$(readlink -f -- "${jar}")"
        stop_session
        mkdir -p -- "${SESSION_DIR}"
        cp -R -- "${KEMU_BUNDLE:-${KEMU_HOME}}" "${RUN_DIR}"
        if [ -n "${KEMU_WORKER_HEAP_MB:-}" ]; then
            if ! [[ "${KEMU_WORKER_HEAP_MB}" =~ ^[1-9][0-9]{0,2}$ ]]; then
                printf 'error: KEMU_WORKER_HEAP_MB must be from 1 to 999\n' >&2
                exit 1
            fi
            patch_dir="${SESSION_DIR}/heap-patch"
            worker_class="emulator/automation/controller/WorkerLauncher.class"
            mkdir -p -- "${patch_dir}"
            unzip -q "${RUN_DIR}/KEmulator.jar" "${worker_class}" -d "${patch_dir}"
            heap_option="$(printf -- '-Xmx%03dM' "${KEMU_WORKER_HEAP_MB}")"
            LC_ALL=C sed -i "s/-Xmx512M/${heap_option}/g" "${patch_dir}/${worker_class}"
            if ! grep -a -q -- "${heap_option}" "${patch_dir}/${worker_class}"; then
                printf 'error: failed to patch KEmulator worker heap\n' >&2
                exit 1
            fi
            (cd -- "${patch_dir}" && zip -q -u "${RUN_DIR}/KEmulator.jar" "${worker_class}")
        fi
        Xvfb "${DISPLAY_NUM}" -screen 0 1280x800x24 >"${SESSION_DIR}/xvfb.log" 2>&1 &
        printf '%s\n' "$!" >"${XVFB_PID_FILE}"
        sleep 2
        cd -- "${RUN_DIR}"
        DISPLAY="${DISPLAY_NUM}" ./kemu.sh open "${jar}" --headless --runtime release --size "${SCREEN_SIZE}"
        ;;
    cmd)
        shift
        if [ ! -x "${RUN_DIR}/kemu.sh" ]; then
            printf 'error: no active KEmulator session; run start first\n' >&2
            exit 1
        fi
        cd -- "${RUN_DIR}"
        DISPLAY="${DISPLAY_NUM}" ./kemu.sh "$@"
        ;;
    stop)
        stop_session
        ;;
    *)
        printf 'error: unknown command %s (expected start, cmd, or stop)\n' "$1" >&2
        exit 1
        ;;
    esac
}

cmd_phone() {
    # Approximate a constrained CLDC 1.1 phone: one host CPU, 64 MiB emulator
    # worker heap (including emulator overhead), and a 176x220 display.

    JAR_PATH="${1:-${ROOT_DIR}/dist/w4me-station.jar}"

    KEMU_SIZE=176x220 KEMU_WORKER_HEAP_MB=64 \
        "${ROOT_DIR}/tools/kemu/run.sh" session start "${JAR_PATH}"

    status_json="$("${ROOT_DIR}/tools/kemu/run.sh" session cmd status --json)"
    if command -v jq >/dev/null 2>&1; then
        controller_pid="$(printf '%s\n' "${status_json}" | jq -r '.result.pid')"
    else
        controller_pid="$(printf '%s\n' "${status_json}" |
            sed -n 's/.*"pid"[^0-9]*\([0-9][0-9]*\).*/\1/p' |
            sed -n '1p')"
    fi
    if ! [[ "${controller_pid}" =~ ^[1-9][0-9]*$ ]]; then
        printf 'error: KEmulator controller PID is missing from status JSON\n' >&2
        exit 1
    fi
    worker_pid="$(pgrep -P "${controller_pid}" -f 'AutomationWorkerMain' | sed -n '1p')"
    if [ -z "${worker_pid}" ]; then
        printf 'error: KEmulator worker process not found\n' >&2
        exit 1
    fi
    taskset -pc 0 "${worker_pid}" >/dev/null
    renice 10 -p "${worker_pid}" >/dev/null

    if [ -n "${W4ME_KEMU_CPU_PERCENT:-}" ] && [ -n "${W4ME_KEMU_CPU_PERIOD_US:-}" ]; then
        cpu_percent="${W4ME_KEMU_CPU_PERCENT}%"
        cpu_period="${W4ME_KEMU_CPU_PERIOD_US}"
    elif [ -r /sys/fs/cgroup/cpu.max ]; then
        cpu_max="$(cat -- /sys/fs/cgroup/cpu.max)"
        cpu_quota="${cpu_max%% *}"
        cpu_period="${cpu_max##* }"
        if [ "${cpu_quota}" = "max" ]; then
            cpu_percent="unlimited"
        else
            cpu_percent="$((cpu_quota * 100 / cpu_period))%"
        fi
    else
        cpu_percent="unreported"
        cpu_period="unreported"
    fi

    printf 'PHONE_PROFILE screen=176x220 worker-heap=64M cpu-affinity=0 nice=10 cpu-quota=%s cpu-period-us=%s worker-pid=%s\n' \
        "${cpu_percent}" "${cpu_period}" "${worker_pid}"
}

cmd_cpu_quota() {
    if [ "$#" -lt 2 ]; then
        printf 'usage: %s PERCENT COMMAND [ARG ...]\n' "$0" >&2
        exit 2
    fi
    quota_percent="$1"
    shift
    if [ "${W4ME_KEMU_CPU_PERCENT:-}" != "${quota_percent}" ] ||
        [ -z "${W4ME_KEMU_CPU_PERIOD_US:-}" ]; then
        printf 'error: CPU quota must be launched through the host Docker runner\n' >&2
        exit 1
    fi
    printf 'CPU_QUOTA_PROFILE percent=%s period-us=%s engine=docker\n' \
        "${quota_percent}" "${W4ME_KEMU_CPU_PERIOD_US}"
    "$@"
}

cmd_verify_duck() {
    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/duck"
    TEMP_DIR="$(mktemp -d)"
    DIAGNOSTIC_JAR="${TEMP_DIR}/duck-diagnostic.jar"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop >/dev/null 2>&1 || true
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
        'w4me.midp.DirectCartridgeProbeMidlet$Duck' \
        "${ROOT_DIR}/src/test/java/w4me/midp/DirectCartridgeProbeMidlet.java"
    KEMU_SIZE=240x320 "${ROOT_DIR}/tools/kemu/run.sh" session start "${DIAGNOSTIC_JAR}" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 6500 >/dev/null

    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs worker --lines 500 >"${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/duck-maze-level-1-complete.png" >/dev/null
    if ! grep -q 'W4ME_REPLAY_COMPLETE cart=Duck Maze frame=154 framebuffer-fnv1a=1ae224ce' \
        "${TEMP_DIR}/worker.log"; then
        printf 'error: Duck Maze did not reach the web-oracle level-1 receipt\n' >&2
        exit 1
    fi
    printf 'PASS KEmulator duck-maze level=1 framebuffer-fnv1a=1ae224ce\n'
}

cmd_verify_launcher() {
    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/launcher"
    TEMP_DIR="$(mktemp -d)"
    DIAGNOSTIC_JAR="${TEMP_DIR}/launcher-diagnostic.jar"
    # Pins the release catalog and its order, which is a user-visible contract.
    # Keep in sync with w4me.midp.LibraryList and the list in tools/build.sh.
    EXPECTED_ITEMS='"items":["Sokoban","Wasm Wars","Annoying Robots","Waternet","Dragon Poker Draw","Tic Tac Toe","Watris","Glowfish Chess","Duck Maze","Untangle","Nyan Cat","Sound Demo","Plasma Cube"]'

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop >/dev/null 2>&1 || true
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
        start "${DIAGNOSTIC_JAR}" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 500 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd observe --json \
        >"${RESULT_DIR}/initial.json"

    if ! grep -F -q -- '"displayableKind":"list"' \
        "${RESULT_DIR}/initial.json" ||
        ! grep -F -q -- '"title":"W4ME Station"' \
            "${RESULT_DIR}/initial.json" ||
        ! grep -F -q -- '"selectedIndex":0' \
            "${RESULT_DIR}/initial.json" ||
        ! grep -F -q -- "${EXPECTED_ITEMS}" \
            "${RESULT_DIR}/initial.json" ||
        ! grep -F -q -- '"id":1,"text":"Run"' \
            "${RESULT_DIR}/initial.json" ||
        ! grep -F -q -- '"id":2,"text":"Choose .wasm file"' \
            "${RESULT_DIR}/initial.json" ||
        ! grep -F -q -- '"id":3,"text":"Settings"' \
            "${RESULT_DIR}/initial.json"; then
        printf 'error: native LCDUI launcher structure is incomplete\n' >&2
        exit 1
    fi

    snapshot_id="$(
        sed -n 's/.*"commandSnapshotId":\([0-9][0-9]*\).*/\1/p' \
            "${RESULT_DIR}/initial.json"
    )"
    if ! [[ "${snapshot_id}" =~ ^[0-9]+$ ]]; then
        printf 'error: cannot read native launcher command snapshot\n' >&2
        exit 1
    fi
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd command run 1 \
        --snapshot "${snapshot_id}" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 800 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd observe --json \
        >"${RESULT_DIR}/cartridge.json"
    if ! grep -F -q -- '"displayableKind":"canvas"' \
        "${RESULT_DIR}/cartridge.json" ||
        ! grep -F -q -- '"softkeys":{"left":"","right":""}' \
            "${RESULT_DIR}/cartridge.json"; then
        printf 'error: gameplay Canvas still exposes a platform command chooser\n' >&2
        exit 1
    fi

    # The raw Nokia right-softkey event directly requests a frame-boundary pause.
    # There is no intermediate one-item LCDUI command chooser; the next
    # Displayable must be the native paused-menu List.
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd key rsk --duration 80 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 300 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd observe --json \
        >"${RESULT_DIR}/system-menu.json"
    if ! grep -F -q -- '"displayableKind":"list"' \
        "${RESULT_DIR}/system-menu.json" ||
        ! grep -F -q -- '"title":"Paused"' \
            "${RESULT_DIR}/system-menu.json" ||
        ! grep -F -q -- \
            '"items":["Continue","Settings","Restart Cart","Exit"]' \
            "${RESULT_DIR}/system-menu.json"; then
        printf 'error: native LCDUI system menu is incomplete\n' >&2
        exit 1
    fi
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/paused-menu.png" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd key rsk --duration 80 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 300 >/dev/null

    # Restart is the third base action: Continue, Settings, Restart Cart, Exit.
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd key rsk --duration 80 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 300 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd observe --json \
        >"${RESULT_DIR}/restart-menu.json"
    if ! grep -F -q -- '"selectedIndex":2' \
        "${RESULT_DIR}/restart-menu.json"; then
        printf 'error: diagnostic Restart Cart selection is missing\n' >&2
        exit 1
    fi
    snapshot_id="$(
        sed -n 's/.*"commandSnapshotId":\([0-9][0-9]*\).*/\1/p' \
            "${RESULT_DIR}/restart-menu.json"
    )"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd command run 1 \
        --snapshot "${snapshot_id}" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 1500 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd observe --json \
        >"${RESULT_DIR}/restarted.json"
    if ! grep -F -q -- '"displayableKind":"canvas"' \
        "${RESULT_DIR}/restarted.json"; then
        printf 'error: Restart Cart did not reopen the cartridge Canvas\n' >&2
        exit 1
    fi

    # Game-origin Settings keeps the worker paused and returns through
    # Audio -> Settings -> the same native system menu.
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd key rsk --duration 80 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 300 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd observe --json \
        >"${RESULT_DIR}/settings-menu.json"
    if ! grep -F -q -- '"selectedIndex":1' \
        "${RESULT_DIR}/settings-menu.json"; then
        printf 'error: diagnostic Settings selection is missing\n' >&2
        exit 1
    fi
    snapshot_id="$(
        sed -n 's/.*"commandSnapshotId":\([0-9][0-9]*\).*/\1/p' \
            "${RESULT_DIR}/settings-menu.json"
    )"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd command run 1 \
        --snapshot "${snapshot_id}" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 300 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd observe --json \
        >"${RESULT_DIR}/game-settings.json"
    if ! grep -F -q -- '"displayableKind":"list"' \
        "${RESULT_DIR}/game-settings.json" ||
        ! grep -F -q -- '"title":"Settings"' \
            "${RESULT_DIR}/game-settings.json" ||
        ! grep -F -q -- '"items":["Audio"]' \
            "${RESULT_DIR}/game-settings.json"; then
        printf 'error: game-origin Settings category list is incomplete\n' >&2
        exit 1
    fi

    snapshot_id="$(
        sed -n 's/.*"commandSnapshotId":\([0-9][0-9]*\).*/\1/p' \
            "${RESULT_DIR}/game-settings.json"
    )"
    if ! [[ "${snapshot_id}" =~ ^[0-9]+$ ]]; then
        printf 'error: cannot read game Settings command snapshot\n' >&2
        exit 1
    fi
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd command run 1 \
        --snapshot "${snapshot_id}" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 300 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd observe --json \
        >"${RESULT_DIR}/game-audio.json"
    if ! grep -F -q -- '"displayableKind":"screen"' \
        "${RESULT_DIR}/game-audio.json" ||
        ! grep -F -q -- '"title":"Audio"' \
            "${RESULT_DIR}/game-audio.json" ||
        ! grep -F -q -- '"left":"Save"' \
            "${RESULT_DIR}/game-audio.json" ||
        ! grep -F -q -- '"right":"Cancel"' \
            "${RESULT_DIR}/game-audio.json"; then
        printf 'error: game-origin Audio form is incomplete\n' >&2
        exit 1
    fi
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd key rsk --duration 80 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 200 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd key rsk --duration 80 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 300 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd key rsk --duration 80 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 300 >/dev/null

    # Exit is the fourth and final base action.
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd key rsk --duration 80 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 300 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd observe --json \
        >"${RESULT_DIR}/exit-menu.json"
    if ! grep -F -q -- '"selectedIndex":3' \
        "${RESULT_DIR}/exit-menu.json"; then
        printf 'error: diagnostic Exit-last selection is missing\n' >&2
        exit 1
    fi
    snapshot_id="$(
        sed -n 's/.*"commandSnapshotId":\([0-9][0-9]*\).*/\1/p' \
            "${RESULT_DIR}/exit-menu.json"
    )"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd command run 1 \
        --snapshot "${snapshot_id}" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 800 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd observe --json \
        >"${RESULT_DIR}/returned.json"
    if ! grep -F -q -- '"displayableKind":"list"' \
        "${RESULT_DIR}/returned.json" ||
        ! grep -F -q -- '"selectedIndex":0' \
            "${RESULT_DIR}/returned.json" ||
        ! grep -F -q -- "${EXPECTED_ITEMS}" \
            "${RESULT_DIR}/returned.json"; then
        printf 'error: returning from a cartridge did not restore launcher focus\n' >&2
        exit 1
    fi

    snapshot_id="$(
        sed -n 's/.*"commandSnapshotId":\([0-9][0-9]*\).*/\1/p' \
            "${RESULT_DIR}/returned.json"
    )"
    if ! [[ "${snapshot_id}" =~ ^[0-9]+$ ]]; then
        printf 'error: cannot read restored launcher command snapshot\n' >&2
        exit 1
    fi
    # Library-origin Settings returns to the library without a game session.
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd command run 3 \
        --snapshot "${snapshot_id}" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 300 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd observe --json \
        >"${RESULT_DIR}/library-settings.json"
    if ! grep -F -q -- '"displayableKind":"list"' \
        "${RESULT_DIR}/library-settings.json" ||
        ! grep -F -q -- '"title":"Settings"' \
            "${RESULT_DIR}/library-settings.json" ||
        ! grep -F -q -- '"items":["Audio"]' \
            "${RESULT_DIR}/library-settings.json"; then
        printf 'error: launcher did not open library-origin Settings\n' >&2
        exit 1
    fi
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd key rsk --duration 80 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 200 >/dev/null

    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs worker --lines 300 \
        >"${RESULT_DIR}/worker.log"
    if grep -E -q -- 'W4ME_ERROR|Exception in thread' \
        "${RESULT_DIR}/worker.log"; then
        printf 'error: native launcher flow reported a runtime failure\n' >&2
        exit 1
    fi
    if ! grep -F -q -- \
        'W4ME_SESSION_CLOSED cart=Sokoban reason=restart count=1' \
        "${RESULT_DIR}/worker.log" ||
        ! grep -F -q -- \
            'W4ME_SESSION_CLOSED cart=Sokoban reason=exit count=2' \
            "${RESULT_DIR}/worker.log"; then
        printf 'error: restart/exit did not close each worker-owned session exactly once\n' >&2
        exit 1
    fi
    {
        printf 'source-jar-sha256=%s\n' \
            "$(sha256sum -- "${SOURCE_JAR}" | awk '{print $1}')"
        printf '%s\n' \
            'displayable=list items=13 selected=0 native-menu=PASS restart=PASS exit-last=PASS game-settings=PASS library-settings=PASS cleanup=PASS'
    } >"${RESULT_DIR}/receipt.txt"
    printf 'PASS KEmulator launcher native-menu restart exit-last settings origins cleanup\n'
}

cmd_verify_external() {

    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/external"
    TEMP_DIR="$(mktemp -d)"
    HTTP_PORT=18384
    HTTP_PID=""

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop >/dev/null 2>&1 || true
        if [ -n "${HTTP_PID}" ] && kill -0 "${HTTP_PID}" 2>/dev/null; then
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
    } >"${TEMP_DIR}/probe.mf"
    jar ufm "${TEMP_DIR}/external-loader.jar" "${TEMP_DIR}/probe.mf" \
        2>"${TEMP_DIR}/manifest.log"

    python3 -m http.server "${HTTP_PORT}" \
        --bind 127.0.0.1 \
        --directory "${ROOT_DIR}/cartridges" \
        >"${TEMP_DIR}/http.log" 2>&1 &
    HTTP_PID="$!"

    KEMU_SIZE=240x320 "${ROOT_DIR}/tools/kemu/run.sh" session \
        start "${TEMP_DIR}/external-loader.jar" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 1800 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs worker --lines 300 >"${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"
    cp -- "${TEMP_DIR}/http.log" "${RESULT_DIR}/http.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/external-sound-demo.png" >/dev/null

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
        "${ROOT_DIR}/tools/kemu/run.sh" session stop >/dev/null 2>&1 || true
        rm -rf -- "${TEMP_DIR}"
    }
    trap cleanup EXIT

    rm -rf -- "${RESULT_DIR}"
    mkdir -p -- \
        "${RESULT_DIR}" \
        "${TEMP_DIR}/classes" \
        "${TEMP_DIR}/kemu/file/root/w4me-picker" \
        "${TEMP_DIR}/kemu-no-jsr75"
    cp -R -- "${KEMU_HOME}/." "${TEMP_DIR}/kemu/"
    cp -- "${ROOT_DIR}/cartridges/sound-demo.wasm" \
        "${TEMP_DIR}/kemu/file/root/w4me-picker/sound-demo.wasm"
    printf '%s\n' 'not a cartridge' \
        >"${TEMP_DIR}/kemu/file/root/w4me-picker/ignored.txt"

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
    } >"${TEMP_DIR}/probe.mf"
    jar ufm "${TEMP_DIR}/file-picker-probe.jar" "${TEMP_DIR}/probe.mf" \
        2>"${TEMP_DIR}/manifest.log"

    KEMU_BUNDLE="${TEMP_DIR}/kemu" KEMU_SIZE=240x320 \
        "${ROOT_DIR}/tools/kemu/run.sh" session \
        start "${TEMP_DIR}/file-picker-probe.jar" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 1000 >/dev/null
    # Accept the optional file-read permission if the emulator presents it.
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd key fire --duration 80 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 1800 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/probe.png" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs worker --lines 500 \
        >"${RESULT_DIR}/worker.log"

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

    "${ROOT_DIR}/tools/kemu/run.sh" session stop >/dev/null
    build_diagnostic_jar \
        "${SOURCE_JAR}" \
        "${UI_JAR}" \
        "${TEMP_DIR}/ui-classes" \
        "W4ME File Picker UI" \
        "w4me.midp.DiagnosticLibraryMidlet" \
        "${ROOT_DIR}/src/test/java/w4me/midp/DiagnosticLibraryMidlet.java"

    KEMU_BUNDLE="${TEMP_DIR}/kemu" KEMU_SIZE=240x320 \
        "${ROOT_DIR}/tools/kemu/run.sh" session \
        start "${UI_JAR}" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 1000 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd observe --json \
        >"${TEMP_DIR}/launcher.json"
    launcher_snapshot="$(
        sed -n 's/.*"commandSnapshotId":\([0-9][0-9]*\).*/\1/p' \
            "${TEMP_DIR}/launcher.json"
    )"
    if ! [[ "${launcher_snapshot}" =~ ^[0-9]+$ ]] ||
        ! grep -F -q -- '"id":2,"text":"Choose .wasm file"' \
            "${TEMP_DIR}/launcher.json"; then
        printf 'error: native launcher file-picker command is missing\n' >&2
        exit 1
    fi
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd command run 2 \
        --snapshot "${launcher_snapshot}" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 200 >/dev/null
    # File browser root -> root -> test directory -> selected .wasm. The picker is a
    # native LCDUI List, so the application no longer receives key events here: the
    # platform owns navigation and binds the select command itself. Drive the list
    # the same way the launcher scenario does, by invoking the observed command.
    picker_open() {
        "${ROOT_DIR}/tools/kemu/run.sh" session cmd observe --json \
            >"${TEMP_DIR}/picker.json"
        picker_snapshot="$(
            sed -n 's/.*"commandSnapshotId":\([0-9][0-9]*\).*/\1/p' \
                "${TEMP_DIR}/picker.json"
        )"
        if ! [[ "${picker_snapshot}" =~ ^[0-9]+$ ]]; then
            printf 'error: cannot read file-picker command snapshot\n' >&2
            exit 1
        fi
        "${ROOT_DIR}/tools/kemu/run.sh" session cmd command run 1 \
            --snapshot "${picker_snapshot}" >/dev/null
        "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 200 >/dev/null
    }
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd observe --json \
        >"${RESULT_DIR}/roots.json"
    if ! grep -F -q -- '"displayableKind":"list"' "${RESULT_DIR}/roots.json" ||
        ! grep -F -q -- '"title":"Choose .wasm file"' "${RESULT_DIR}/roots.json" ||
        ! grep -F -q -- '"text":"Enter manually"' "${RESULT_DIR}/roots.json"; then
        printf 'error: file picker did not open as a native list\n' >&2
        exit 1
    fi
    picker_open
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/filtered-directory.png" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd observe --json \
        >"${RESULT_DIR}/selection.json"

    # Entering a directory must show the parent row followed by the filtered
    # entries. This is the mapping the picker itself is responsible for; the row
    # layout, scrolling and highlight now belong to the platform.
    if ! grep -F -q -- '"displayableKind":"list"' \
        "${RESULT_DIR}/selection.json" ||
        ! grep -F -q -- '"items":["[..]"' \
            "${RESULT_DIR}/selection.json" ||
        ! grep -F -q -- '"[dir] w4me-picker"' \
            "${RESULT_DIR}/selection.json"; then
        printf 'error: file picker did not list the parent row and filtered entries\n' >&2
        exit 1
    fi
    # Selecting an entry other than the first cannot be automated: KEmulator does
    # not move the selection of a native LCDUI List, by key or by pointer. That was
    # drivable only while the picker was a custom Canvas that handled keys itself.
    # Committing a chosen cartridge is covered by the install and external
    # scenarios, and the headless probe above already covers select/stage/validate.

    "${ROOT_DIR}/tools/kemu/run.sh" session stop >/dev/null

    if [ "$(unzip -Z1 "${SOURCE_JAR}" |
        awk '$0 == "w4me/midp/Jsr75FileSystem.class" { count++ } END { print count + 0 }')" \
        -ne 1 ]; then
        printf 'error: optional JAR does not contain the JSR-75 adapter\n' >&2
        exit 1
    fi
    if [ "$(unzip -Z1 "${BASE_JAR}" |
        awk '$0 == "w4me/midp/Jsr75FileSystem.class" { count++ } END { print count + 0 }')" \
        -ne 0 ]; then
        printf 'error: base JAR contains the JSR-75 adapter\n' >&2
        exit 1
    fi

    cp -R -- "${KEMU_HOME}/." "${TEMP_DIR}/kemu-no-jsr75/"
    zip -q -d "${TEMP_DIR}/kemu-no-jsr75/KEmulator.jar" \
        'javax/microedition/io/file/*'
    KEMU_BUNDLE="${TEMP_DIR}/kemu-no-jsr75" KEMU_SIZE=240x320 \
        "${ROOT_DIR}/tools/kemu/run.sh" session \
        start "${BASE_JAR}" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 1200 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd state \
        >"${RESULT_DIR}/base-without-jsr75-state.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs worker --lines 300 \
        >"${RESULT_DIR}/base-without-jsr75-worker.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/base-without-jsr75.png" >/dev/null

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

cmd_verify_generic_w4ir() {

    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    ORACLE="${ROOT_DIR}/testdata/oracles/plasma-cube-60-frames.csv"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/generic-w4ir"
    TEMP_DIR="$(mktemp -d)"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop >/dev/null 2>&1 || true
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
    } >"${TEMP_DIR}/probe.mf"
    jar ufm "${TEMP_DIR}/generic-w4ir-probe.jar" "${TEMP_DIR}/probe.mf" \
        2>"${TEMP_DIR}/manifest.log"

    KEMU_SIZE=240x320 "${ROOT_DIR}/tools/kemu/run.sh" session \
        start "${TEMP_DIR}/generic-w4ir-probe.jar" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 7000 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 7000 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs worker --lines 700 >"${TEMP_DIR}/worker.log"
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

    awk -F, 'NR > 1 { print $1 " " $3 }' "${ORACLE}" >"${TEMP_DIR}/expected.txt"
    sed -n 's/.*W4ME_FRAME cart=plasma-cube frame=\([0-9][0-9]*\).*framebuffer-fnv1a=\([0-9a-f][0-9a-f]*\).*/\1 \2/p' \
        "${TEMP_DIR}/worker.log" |
        awk '$1 < 60 && !seen[$1]++ { print }' >"${TEMP_DIR}/actual.txt"
    diff -u "${TEMP_DIR}/expected.txt" "${TEMP_DIR}/actual.txt"
    cp -- "${TEMP_DIR}/actual.txt" "${RESULT_DIR}/frames.txt"

    sed -n 's/.*W4ME_FRAME cart=plasma-cube frame=\([0-9][0-9]*\) instructions=[0-9][0-9]* dispatches=\([0-9][0-9]*\).*elapsed-ms=\([0-9][0-9]*\).*/\1 \2 \3/p' \
        "${TEMP_DIR}/worker.log" |
        awk '$1 < 60 && !seen[$1]++ { total += $3; dispatches += $2; if (count == 0 || $3 < minimum) minimum = $3; if ($3 > maximum) maximum = $3; count++ } END { if (count != 60) exit 1; printf "frames=%d average-ms=%.2f minimum-ms=%d maximum-ms=%d dispatches-average=%d\n", count, total / count, minimum, maximum, dispatches / count }' \
            >"${RESULT_DIR}/timing.txt"

    printf 'PASS KEmulator generic W4IR plasma-cube frames=60 fast-paths=0\n'
    cat "${RESULT_DIR}/timing.txt"
    {
        printf 'source-jar-sha256=%s\n' "$(sha256sum -- "${SOURCE_JAR}" | awk '{print $1}')"
        printf '%s\n' 'PASS KEmulator generic W4IR plasma-cube frames=60 fast-paths=0'
        cat "${RESULT_DIR}/timing.txt"
        cat "${RESULT_DIR}/frames.txt"
    } >"${RESULT_DIR}/receipt.txt"
}

cmd_verify_install() {

    JAR_PATH="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/install"
    TEMP_DIR="$(mktemp -d)"
    HTTP_PORT=18385
    HTTP_PID=""

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop >/dev/null 2>&1 || true
        if [ -n "${HTTP_PID}" ] && kill -0 "${HTTP_PID}" 2>/dev/null; then
            kill "${HTTP_PID}" || true
        fi
        rm -rf -- "${TEMP_DIR}"
    }
    trap cleanup EXIT

    rm -rf -- "${RESULT_DIR}"
    mkdir -p -- "${RESULT_DIR}" "${TEMP_DIR}/classes"
    cp -- "${JAR_PATH}" "${TEMP_DIR}/install-probe.jar"
    javac \
        -source "${J2ME_SOURCE}" \
        -target "${J2ME_TARGET}" \
        -Xlint:-options \
        -classpath "${MIDP_API_JAR}:${ROOT_DIR}/build/midlet/classes" \
        -d "${TEMP_DIR}/classes" \
        "${FRAMEBUFFER_ORACLE_SOURCE}" \
        "${ROOT_DIR}/src/test/java/w4me/midp/DiagnosticW4MeMidlet.java" \
        "${ROOT_DIR}/src/test/java/w4me/midp/DiagnosticW4SessionMonitor.java" \
        "${ROOT_DIR}/src/test/java/w4me/midp/InstallFlowProbeMidlet.java"
    jar uf "${TEMP_DIR}/install-probe.jar" -C "${TEMP_DIR}/classes" .
    {
        printf '%s\n' 'MIDlet-Name: W4ME Install Probe'
        printf '%s\n' 'MIDlet-1: W4ME Install Probe,,w4me.midp.InstallFlowProbeMidlet'
    } >"${TEMP_DIR}/probe.mf"
    jar ufm "${TEMP_DIR}/install-probe.jar" "${TEMP_DIR}/probe.mf" \
        2>"${TEMP_DIR}/manifest.log"

    python3 -m http.server "${HTTP_PORT}" \
        --bind 127.0.0.1 \
        --directory "${ROOT_DIR}/cartridges" \
        >"${TEMP_DIR}/http.log" 2>&1 &
    HTTP_PID="$!"

    KEMU_SIZE=240x320 "${ROOT_DIR}/tools/kemu/run.sh" session \
        start "${TEMP_DIR}/install-probe.jar" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 1500 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd key fire --duration 80 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 300 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd key rsk --duration 80 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 500 >/dev/null

    kill "${HTTP_PID}" || true
    wait "${HTTP_PID}" 2>/dev/null || true
    HTTP_PID=""

    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 900 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd key fire --duration 80 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 300 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/installed-offline.png" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs worker --lines 500 \
        >"${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"
    cp -- "${TEMP_DIR}/http.log" "${RESULT_DIR}/http.log"

    if [ "$(grep -F -c -- 'GET /sound-demo.wasm HTTP/1.1" 200' "${TEMP_DIR}/http.log")" -ne 1 ]; then
        printf 'error: expected exactly one HTTP cartridge download\n' >&2
        exit 1
    fi
    if ! grep -F -q -- 'W4ME_INSTALL state=DOWNLOADING' "${TEMP_DIR}/worker.log" ||
        ! grep -E -q -- \
            'W4ME_INSTALL state=RECEIVED id=[0-9]+ bytes=1518 chunks=1' \
            "${TEMP_DIR}/worker.log" ||
        ! grep -F -q -- 'W4ME_INSTALL state=VALIDATING' "${TEMP_DIR}/worker.log" ||
        ! grep -F -q -- 'W4ME_INSTALL state=TRANSLATING' "${TEMP_DIR}/worker.log" ||
        ! grep -E -q -- \
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
    if [ "$(grep -F -c -- 'W4ME_TONE frequency=440 duration=60 volume=25700 flags=0' \
        "${TEMP_DIR}/worker.log")" -lt 2 ]; then
        printf 'error: Sound Demo did not execute before and after offline relaunch\n' >&2
        exit 1
    fi
    printf 'PASS KEmulator streamed HTTP install commit and offline RMS relaunch\n'
}

cmd_verify_invalid() {

    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/invalid"
    TEMP_DIR="$(mktemp -d)"
    HTTP_PORT=18386
    HTTP_PID=""

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop >/dev/null 2>&1 || true
        if [ -n "${HTTP_PID}" ] && kill -0 "${HTTP_PID}" 2>/dev/null; then
            kill "${HTTP_PID}" || true
        fi
        rm -rf -- "${TEMP_DIR}"
    }
    trap cleanup EXIT

    rm -rf -- "${RESULT_DIR}"
    mkdir -p -- "${RESULT_DIR}" "${TEMP_DIR}/classes" "${TEMP_DIR}/http"
    printf '\000asm\001\000\000\000\001\001\000\001\001\000' \
        >"${TEMP_DIR}/http/bad.wasm"
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
    } >"${TEMP_DIR}/probe.mf"
    jar ufm "${TEMP_DIR}/invalid-probe.jar" "${TEMP_DIR}/probe.mf" \
        2>"${TEMP_DIR}/manifest.log"

    python3 -m http.server "${HTTP_PORT}" \
        --bind 127.0.0.1 \
        --directory "${TEMP_DIR}/http" \
        >"${TEMP_DIR}/http.log" 2>&1 &
    HTTP_PID="$!"

    KEMU_SIZE=240x320 "${ROOT_DIR}/tools/kemu/run.sh" session \
        start "${TEMP_DIR}/invalid-probe.jar" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 1500 >/dev/null
    # Accept the HTTP permission prompt.
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd key fire --duration 80 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 700 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/invalid-cart-alert.png" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd state >"${TEMP_DIR}/alert-state.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd key lsk --duration 80 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 400 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/launcher-after-invalid-cart.png" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd state >"${TEMP_DIR}/launcher-state.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs worker --lines 300 \
        >"${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"
    cp -- "${TEMP_DIR}/http.log" "${RESULT_DIR}/http.log"
    cp -- "${TEMP_DIR}/alert-state.log" "${RESULT_DIR}/alert-state.log"
    cp -- "${TEMP_DIR}/launcher-state.log" "${RESULT_DIR}/launcher-state.log"

    if [ "$(grep -F -c -- 'GET /bad.wasm HTTP/1.1" 200' "${TEMP_DIR}/http.log")" -ne 1 ]; then
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

cmd_verify_library() {

    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/library"
    TEMP_DIR="$(mktemp -d)"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop >/dev/null 2>&1 || true
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
    } >"${TEMP_DIR}/probe.mf"
    jar ufm "${TEMP_DIR}/library-probe.jar" "${TEMP_DIR}/probe.mf" \
        2>"${TEMP_DIR}/manifest.log"

    KEMU_SIZE=176x220 "${ROOT_DIR}/tools/kemu/run.sh" session \
        start "${TEMP_DIR}/library-probe.jar" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 1000 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs worker --lines 300 \
        >"${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/library-probe.png" >/dev/null

    if ! grep -F -q -- \
        'W4ME_LIBRARY_PROBE recovery=PASS stream=PASS hidden=PASS committed=PASS reopen=PASS dedupe=PASS legacy=PASS chunks=1 bytes=1518 tones=1' \
        "${TEMP_DIR}/worker.log"; then
        printf 'error: transactional RMS cartridge library probe failed\n' >&2
        exit 1
    fi
    printf 'PASS KEmulator streamed RMS library staging/commit/reopen/dedupe/execute\n'
}

cmd_verify_plasma() {
    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    ORACLE="${ROOT_DIR}/testdata/oracles/plasma-cube-60-frames.csv"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/plasma"
    TEMP_DIR="$(mktemp -d)"
    DIAGNOSTIC_JAR="${TEMP_DIR}/plasma-diagnostic.jar"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop >/dev/null 2>&1 || true
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
        'w4me.midp.DirectCartridgeProbeMidlet$Plasma' \
        "${ROOT_DIR}/src/test/java/w4me/midp/DirectCartridgeProbeMidlet.java"

    KEMU_SIZE=240x320 "${ROOT_DIR}/tools/kemu/run.sh" session start "${DIAGNOSTIC_JAR}" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 6500 >/dev/null

    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs worker --lines 500 >"${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"
    awk -F, 'NR > 1 { print $1 " " $3 }' "${ORACLE}" >"${TEMP_DIR}/expected.txt"
    sed -n 's/.*W4ME_FRAME cart=Plasma Cube frame=\([0-9][0-9]*\).*framebuffer-fnv1a=\([0-9a-f][0-9a-f]*\).*/\1 \2/p' \
        "${TEMP_DIR}/worker.log" |
        awk '$1 < 60 && !seen[$1]++ { print }' >"${TEMP_DIR}/actual.txt"

    diff -u "${TEMP_DIR}/expected.txt" "${TEMP_DIR}/actual.txt"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/plasma-cube-live.png" >/dev/null
    cp -- "${TEMP_DIR}/actual.txt" "${RESULT_DIR}/frames.txt"
    sed -n 's/.*W4ME_FRAME cart=Plasma Cube frame=\([0-9][0-9]*\) .*elapsed-ms=\([0-9][0-9]*\).*/\1 \2/p' \
        "${TEMP_DIR}/worker.log" |
        awk '$1 < 60 && !seen[$1]++ { total += $2; if (count == 0 || $2 < minimum) minimum = $2; if ($2 > maximum) maximum = $2; count++ } END { if (count != 60) exit 1; printf "frames=%d average-ms=%.2f minimum-ms=%d maximum-ms=%d\n", count, total / count, minimum, maximum }' \
            >"${RESULT_DIR}/timing.txt"
    printf 'PASS KEmulator plasma-cube frames=60\n'
    cat "${RESULT_DIR}/timing.txt"
}

cmd_verify_rms() {

    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/rms"
    TEMP_DIR="$(mktemp -d)"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop >/dev/null 2>&1 || true
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
    } >"${TEMP_DIR}/probe.mf"
    jar ufm "${TEMP_DIR}/rms-probe.jar" "${TEMP_DIR}/probe.mf" \
        2>"${TEMP_DIR}/manifest.log"

    KEMU_SIZE=176x220 "${ROOT_DIR}/tools/kemu/run.sh" session \
        start "${TEMP_DIR}/rms-probe.jar" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 700 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs worker --lines 250 \
        >"${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/rms-probe.png" >/dev/null

    if ! grep -F -q -- \
        'W4ME_RMS_PROBE grade=RMS bytes=1024 reopen=PASS recovery=PASS legacy=PASS records=2' \
        "${TEMP_DIR}/worker.log"; then
        printf 'error: production RMS disk backend failed A/B recovery probe\n' >&2
        exit 1
    fi
    printf 'PASS KEmulator RMS disk bytes=1024 A/B recovery and legacy migration\n'
}

cmd_verify_rubido() {

    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/rubido"
    TEMP_DIR="$(mktemp -d)"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop >/dev/null 2>&1 || true
        rm -rf -- "${TEMP_DIR}"
    }
    trap cleanup EXIT

    rm -rf -- "${RESULT_DIR}"
    mkdir -p -- "${RESULT_DIR}" "${TEMP_DIR}/classes"
    cp -- "${SOURCE_JAR}" "${TEMP_DIR}/rubido-probe.jar"
    cp -- "${ROOT_DIR}/cartridges/rubido.wasm" "${TEMP_DIR}/classes/rubido.wasm"
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
    } >"${TEMP_DIR}/probe.mf"
    jar ufm "${TEMP_DIR}/rubido-probe.jar" "${TEMP_DIR}/probe.mf" \
        2>"${TEMP_DIR}/manifest.log"

    KEMU_SIZE=176x220 "${ROOT_DIR}/tools/kemu/run.sh" session \
        start "${TEMP_DIR}/rubido-probe.jar" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 7000 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs worker --lines 900 \
        >"${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/rubido-probe.png" >/dev/null

    if grep -F -q -- 'W4ME_RUBIDO_ERROR' "${TEMP_DIR}/worker.log"; then
        printf 'error: Rubido corpus probe reported a runtime failure\n' >&2
        exit 1
    fi
    checkpoint_count="$(grep -F -c -- 'W4ME_RUBIDO_FRAME ' "${TEMP_DIR}/worker.log")"
    if [ "${checkpoint_count}" != 30 ]; then
        printf 'error: expected 30 Rubido checkpoints, got %s\n' "${checkpoint_count}" >&2
        exit 1
    fi
    if ! grep -F -q -- \
        'W4ME_RUBIDO_PROBE frames=70 checkpoints=30 tones=8 disk-read=20/0 disk-write=20/20 palette=blue-to-aqua framebuffer-fnv1a=47462cbf' \
        "${TEMP_DIR}/worker.log"; then
        printf 'error: KEmulator did not match the Rubido browser oracle\n' >&2
        exit 1
    fi
    {
        printf 'source-jar-sha256=%s\n' "$(sha256sum -- "${SOURCE_JAR}" | awk '{print $1}')"
        printf 'cartridge-sha256=%s\n' \
            "$(sha256sum -- "${ROOT_DIR}/cartridges/rubido.wasm" | awk '{print $1}')"
        grep -F -- 'W4ME_RUBIDO_' "${TEMP_DIR}/worker.log"
    } >"${RESULT_DIR}/receipt.txt"
    printf 'PASS KEmulator Rubido frames=70 checkpoints=30 mouse+palette+disk=exact\n'
}

cmd_verify_sound_test() {
    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/sound-test"
    TEMP_DIR="$(mktemp -d)"
    DIAGNOSTIC_JAR="${TEMP_DIR}/sound-test-diagnostic.jar"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop >/dev/null 2>&1 || true
        rm -rf -- "${TEMP_DIR}"
    }
    trap cleanup EXIT

    rm -rf -- "${RESULT_DIR}"
    mkdir -p -- "${RESULT_DIR}"
    build_diagnostic_jar \
        "${SOURCE_JAR}" \
        "${DIAGNOSTIC_JAR}" \
        "${TEMP_DIR}/classes" \
        "W4ME Sound Test Diagnostic" \
        'w4me.midp.DirectCartridgeProbeMidlet$SoundTest' \
        "${ROOT_DIR}/src/test/java/w4me/midp/DirectCartridgeProbeMidlet.java"

    KEMU_SIZE=240x320 "${ROOT_DIR}/tools/kemu/run.sh" session start "${DIAGNOSTIC_JAR}" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 1200 >/dev/null

    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs worker --lines 300 >"${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/sound-test-live.png" >/dev/null

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
        "${ROOT_DIR}/tools/kemu/run.sh" session stop >/dev/null 2>&1 || true
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
        'w4me.midp.DirectCartridgeProbeMidlet$SoundDemo' \
        "${ROOT_DIR}/src/test/java/w4me/midp/DirectCartridgeProbeMidlet.java"

    KEMU_SIZE=240x320 "${ROOT_DIR}/tools/kemu/run.sh" session start "${DIAGNOSTIC_JAR}" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 1000 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd key fire --duration 80 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 1000 >/dev/null

    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs worker --lines 300 >"${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/sound-demo-live.png" >/dev/null

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
        "${ROOT_DIR}/tools/kemu/run.sh" session stop >/dev/null 2>&1 || true
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
    } >"${TEMP_DIR}/probe.mf"
    jar ufm "${PROBE_JAR}" "${TEMP_DIR}/probe.mf" \
        2>"${TEMP_DIR}/manifest.log"

    KEMU_SIZE=176x220 "${ROOT_DIR}/tools/kemu/run.sh" session start "${PROBE_JAR}" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 1200 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/audio-settings-default.png" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs worker --lines 200 \
        >"${TEMP_DIR}/worker.log"
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
    {
        printf 'source-jar-sha256=%s\n' "$(sha256sum -- "${SOURCE_JAR}" | awk '{print $1}')"
        grep -F -- 'W4ME_AUDIO_SETTINGS_PROBE' "${TEMP_DIR}/worker.log"
    } >"${RESULT_DIR}/receipt.txt"
    printf 'PASS KEmulator audio-settings active-mute persisted-mute gain capability form-mode\n'
}

cmd_verify_tankle() {
    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/tankle"
    TEMP_DIR="$(mktemp -d)"
    DIAGNOSTIC_JAR="${TEMP_DIR}/tankle-diagnostic.jar"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop >/dev/null 2>&1 || true
        rm -rf -- "${TEMP_DIR}"
    }
    trap cleanup EXIT

    rm -rf -- "${RESULT_DIR}"
    mkdir -p -- "${RESULT_DIR}"
    build_diagnostic_jar \
        "${SOURCE_JAR}" \
        "${DIAGNOSTIC_JAR}" \
        "${TEMP_DIR}/classes" \
        "W4ME Tankle Diagnostic" \
        'w4me.midp.DirectCartridgeProbeMidlet$Tankle' \
        "${ROOT_DIR}/src/test/java/w4me/midp/DirectCartridgeProbeMidlet.java"

    KEMU_SIZE=240x320 "${ROOT_DIR}/tools/kemu/run.sh" session start "${DIAGNOSTIC_JAR}" >/dev/null
    attempt=0
    while [ "${attempt}" -lt 10 ]; do
        "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 500 >/dev/null
        "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs worker --lines 300 \
            >"${TEMP_DIR}/worker.log"
        if grep -F -q -- 'W4ME_FRAME cart=Tankle frame=0 ' \
            "${TEMP_DIR}/worker.log"; then
            break
        fi
        attempt=$((attempt + 1))
    done
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd key fire --duration 120 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 1500 >/dev/null

    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs worker --lines 500 >"${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/tankle-playing.png" >/dev/null

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

cmd_verify_touch() {
    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/touch"
    TEMP_DIR="$(mktemp -d)"
    DIAGNOSTIC_JAR="${TEMP_DIR}/touch-diagnostic.jar"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop >/dev/null 2>&1 || true
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
        'w4me.midp.DirectCartridgeProbeMidlet$SoundDemo' \
        "${ROOT_DIR}/src/test/java/w4me/midp/DirectCartridgeProbeMidlet.java"

    KEMU_SIZE=240x320 "${ROOT_DIR}/tools/kemu/run.sh" session start "${DIAGNOSTIC_JAR}" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 800 >/dev/null
    # A zero-duration tap can press and release entirely between two 30 Hz MIDlet
    # frames. Keep the pointer inside the A button while holding it long enough for
    # the runtime to observe GAMEPAD1.
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd drag 210 288 211 288 --delay 120 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 500 >/dev/null

    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs worker --lines 300 >"${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/touch-controls.png" >/dev/null

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

cmd_verify_trap() {

    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/trap"
    TEMP_DIR="$(mktemp -d)"
    HTTP_PORT=18387
    HTTP_PID=""

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop >/dev/null 2>&1 || true
        if [ -n "${HTTP_PID}" ] && kill -0 "${HTTP_PID}" 2>/dev/null; then
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
    } >"${TEMP_DIR}/probe.mf"
    jar ufm "${TEMP_DIR}/trap-probe.jar" "${TEMP_DIR}/probe.mf" \
        2>"${TEMP_DIR}/manifest.log"

    python3 -m http.server "${HTTP_PORT}" \
        --bind 127.0.0.1 \
        --directory "${TEMP_DIR}/http" \
        >"${TEMP_DIR}/http.log" 2>&1 &
    HTTP_PID="$!"

    KEMU_SIZE=240x320 "${ROOT_DIR}/tools/kemu/run.sh" session \
        start "${TEMP_DIR}/trap-probe.jar" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 1500 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd key fire --duration 80 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 700 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/runtime-trap-alert.png" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd state >"${TEMP_DIR}/alert-state.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd key lsk --duration 80 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 400 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/launcher-after-runtime-trap.png" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd state >"${TEMP_DIR}/launcher-state.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs worker --lines 300 \
        >"${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"
    cp -- "${TEMP_DIR}/http.log" "${RESULT_DIR}/http.log"
    cp -- "${TEMP_DIR}/alert-state.log" "${RESULT_DIR}/alert-state.log"
    cp -- "${TEMP_DIR}/launcher-state.log" "${RESULT_DIR}/launcher-state.log"

    if [ "$(grep -F -c -- 'GET /trap.wasm HTTP/1.1" 200' "${TEMP_DIR}/http.log")" -ne 1 ]; then
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
        "${ROOT_DIR}/tools/kemu/run.sh" session stop >/dev/null 2>&1 || true
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
    } >"${TEMP_DIR}/probe.mf"
    jar ufm "${TEMP_DIR}/untangle-probe.jar" "${TEMP_DIR}/probe.mf" \
        2>"${TEMP_DIR}/manifest.log"

    KEMU_SIZE=176x220 "${ROOT_DIR}/tools/kemu/run.sh" session \
        start "${TEMP_DIR}/untangle-probe.jar" >/dev/null
    for wait_ms in 10000 10000 10000; do
        "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait "${wait_ms}" >/dev/null
    done
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs worker --lines 1600 \
        >"${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/untangle-probe.png" >/dev/null

    if grep -F -q -- 'W4ME_UNTANGLE_ERROR' "${TEMP_DIR}/worker.log"; then
        printf 'error: Untangle corpus probe reported a runtime failure\n' >&2
        exit 1
    fi
    checkpoint_count="$(grep -F -c -- 'W4ME_UNTANGLE_FRAME ' "${TEMP_DIR}/worker.log")"
    if [ "${checkpoint_count}" != 47 ]; then
        printf 'error: expected 47 Untangle checkpoints, got %s\n' "${checkpoint_count}" >&2
        exit 1
    fi
    if ! grep -F -q -- \
        'W4ME_UNTANGLE_PROBE frames=401 checkpoints=47 tones=0 disk-read=1/0 disk-write=1/1 framebuffer-fnv1a=bc0231d9' \
        "${TEMP_DIR}/worker.log"; then
        printf 'error: KEmulator did not match the Untangle browser oracle\n' >&2
        exit 1
    fi
    {
        printf 'source-jar-sha256=%s\n' "$(sha256sum -- "${SOURCE_JAR}" | awk '{print $1}')"
        printf 'cartridge-sha256=%s\n' \
            "$(sha256sum -- "${ROOT_DIR}/cartridges/untangle.wasm" | awk '{print $1}')"
        grep -F -- 'W4ME_UNTANGLE_' "${TEMP_DIR}/worker.log"
    } >"${RESULT_DIR}/receipt.txt"
    printf 'PASS KEmulator Untangle frames=401 checkpoints=47 drag+rotate+flip+disk=exact\n'
}

cmd_verify_w4ir() {

    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/w4ir"
    TEMP_DIR="$(mktemp -d)"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop >/dev/null 2>&1 || true
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
    } >"${TEMP_DIR}/probe.mf"
    jar ufm "${TEMP_DIR}/w4ir-probe.jar" "${TEMP_DIR}/probe.mf" \
        2>"${TEMP_DIR}/manifest.log"

    KEMU_SIZE=176x220 "${ROOT_DIR}/tools/kemu/run.sh" session \
        start "${TEMP_DIR}/w4ir-probe.jar" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 2500 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs worker --lines 500 \
        >"${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/w4ir-cache-probe.png" >/dev/null

    if ! grep -E -q -- \
        'W4ME_W4IR_PROBE recovery=PASS old-format=PASS build=PASS hit=PASS descriptors=PASS descriptor-hash=[0-9a-f]{8} slots=12 faults=[1-9][0-9]* warm-faults=0 hits=[0-9]+ promoted=[1-9][0-9]* compact-counters=(on|off) compact-blocks=[0-9]+ compact-instructions=[0-9]+ trace-loops=[1-9][0-9]* trace-iterations=[1-9][0-9]* fast-paths=0 first-ms=[0-9]+ warm-average-ms=[0-9]+ frame0-fnv1a=2e572184 frame10-fnv1a=f90becd4' \
        "${TEMP_DIR}/worker.log"; then
        printf 'error: generic RMS W4IR build/hit/paging/promotion probe failed\n' >&2
        exit 1
    fi
    {
        printf 'source-jar-sha256=%s\n' "$(sha256sum -- "${SOURCE_JAR}" | awk '{print $1}')"
        grep -F -- 'W4ME_W4IR_PROBE ' "${TEMP_DIR}/worker.log"
        printf '\nCaptured: %s\n' "$(date -I)"
    } >"${RESULT_DIR}/receipt.txt"
    printf 'PASS KEmulator generic W4IR old-format-rebuild/build/cache-hit/descriptors/12-slot paging/promotion framebuffer=2e572184\n'
}

cmd_verify_waternet() {

    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/waternet"
    TEMP_DIR="$(mktemp -d)"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop >/dev/null 2>&1 || true
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
    } >"${TEMP_DIR}/probe.mf"
    jar ufm "${TEMP_DIR}/waternet-probe.jar" "${TEMP_DIR}/probe.mf" \
        2>"${TEMP_DIR}/manifest.log"

    KEMU_SIZE=176x220 "${ROOT_DIR}/tools/kemu/run.sh" session \
        start "${TEMP_DIR}/waternet-probe.jar" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 7000 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs worker --lines 700 \
        >"${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/waternet-probe.png" >/dev/null

    if grep -F -q -- 'W4ME_WATERNET_ERROR' "${TEMP_DIR}/worker.log"; then
        printf 'error: Waternet corpus probe reported a runtime failure\n' >&2
        exit 1
    fi
    checkpoint_count="$(grep -F -c -- 'W4ME_WATERNET_FRAME ' "${TEMP_DIR}/worker.log")"
    if [ "${checkpoint_count}" != 17 ]; then
        printf 'error: expected 17 Waternet checkpoints, got %s\n' "${checkpoint_count}" >&2
        exit 1
    fi
    if ! grep -F -q -- \
        'W4ME_WATERNET_PROBE frames=94 checkpoints=17 tones=14 disk-read=16/0 disk-bytes=0 framebuffer-fnv1a=14e0f616' \
        "${TEMP_DIR}/worker.log"; then
        printf 'error: KEmulator did not match the Waternet browser oracle\n' >&2
        exit 1
    fi
    {
        printf 'source-jar-sha256=%s\n' "$(sha256sum -- "${SOURCE_JAR}" | awk '{print $1}')"
        printf 'cartridge-sha256=%s\n' \
            "$(sha256sum -- "${ROOT_DIR}/cartridges/waternet.wasm" | awk '{print $1}')"
        grep -F -- 'W4ME_WATERNET_' "${TEMP_DIR}/worker.log"
    } >"${RESULT_DIR}/receipt.txt"
    printf 'PASS KEmulator Waternet frames=94 checkpoints=17 tones=14 disk=exact\n'
}

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
    if [ "${PROFILE}" != "phone" ] && [ "${PROFILE}" != "ordinary" ]; then
        printf 'error: profile must be phone or ordinary\n' >&2
        exit 2
    fi

    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/benchmark-generic-corpus-${WORKLOAD}-${PROFILE}"
    TEMP_DIR="$(mktemp -d)"
    BENCHMARK_JAR="${TEMP_DIR}/generic-corpus-benchmark.jar"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop >/dev/null 2>&1 || true
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
    } >"${TEMP_DIR}/probe.mf"
    jar ufm "${BENCHMARK_JAR}" "${TEMP_DIR}/probe.mf" \
        2>"${TEMP_DIR}/manifest.log"

    source_sha256="$(sha256sum -- "${SOURCE_JAR}" | cut -d ' ' -f 1)"
    benchmark_sha256="$(sha256sum -- "${BENCHMARK_JAR}" | cut -d ' ' -f 1)"
    cartridge_sha256="$(sha256sum -- "${CARTRIDGE}" | cut -d ' ' -f 1)"
    : >"${RESULT_DIR}/samples.txt"

    run=1
    while [ "${run}" -le "${RUNS}" ]; do
        profile_file="${RESULT_DIR}/profile-${run}.txt"
        if [ "${PROFILE}" = "phone" ]; then
            "${ROOT_DIR}/tools/kemu/run.sh" phone "${BENCHMARK_JAR}" >"${profile_file}"
        else
            KEMU_SIZE="${KEMU_SIZE:-240x320}" \
                "${ROOT_DIR}/tools/kemu/run.sh" session start "${BENCHMARK_JAR}" >/dev/null
            printf 'ORDINARY_PROFILE screen=%s\n' "${KEMU_SIZE:-240x320}" >"${profile_file}"
        fi
        attempt=0
        while [ "${attempt}" -lt 15 ]; do
            "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 5000 >/dev/null
            "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs worker --lines 500 \
                >"${RESULT_DIR}/worker-${run}.log"
            if grep -F -q -- "W4ME_CORPUS_BENCH workload=${WORKLOAD} " \
                "${RESULT_DIR}/worker-${run}.log"; then
                break
            fi
            attempt=$((attempt + 1))
        done
        "${ROOT_DIR}/tools/kemu/run.sh" session stop >/dev/null 2>&1 || true
        if grep -F -q -- 'W4ME_CORPUS_BENCH_ERROR ' "${RESULT_DIR}/worker-${run}.log"; then
            printf 'error: generic corpus benchmark failed for %s sample %s\n' \
                "${WORKLOAD}" "${run}" >&2
            exit 1
        fi
        if ! grep -F -- "W4ME_CORPUS_BENCH workload=${WORKLOAD} " \
            "${RESULT_DIR}/worker-${run}.log" >"${RESULT_DIR}/result-${run}.txt"; then
            printf 'error: generic corpus benchmark receipt missing for %s sample %s\n' \
                "${WORKLOAD}" "${run}" >&2
            exit 1
        fi
        if ! grep -F -q -- " fast-paths=0 " "${RESULT_DIR}/result-${run}.txt" ||
            ! grep -F -q -- " framebuffer-fnv1a=${EXPECTED_FRAMEBUFFER}" \
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
        printf '%s %s\n' "${run}" "${elapsed_ms}" >>"${RESULT_DIR}/samples.txt"
        run=$((run + 1))
    done

    sort -n -k2,2 -- "${RESULT_DIR}/samples.txt" >"${RESULT_DIR}/samples-sorted.txt"
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
    } >"${RESULT_DIR}/receipt.txt"
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
    if [ "${PROFILE}" = "phone" ] || [ "${PROFILE}" = "1" ]; then
        CHILD_DIR="${CHILD_DIR}-phone"
    fi
    rm -rf -- "${RESULT_DIR}"
    mkdir -p -- "${RESULT_DIR}"
    : >"${RESULT_DIR}/averages.txt"

    run=1
    while [ "${run}" -le "${RUNS}" ]; do
        if ! "${ROOT_DIR}/tools/kemu/run.sh" bench generic-w4ir \
            "${SOURCE_JAR}" "${MODE}" "${PROFILE}" \
            >"${RESULT_DIR}/sample-${run}.out"; then
            "${ROOT_DIR}/tools/kemu/run.sh" session stop >/dev/null 2>&1 || true
            sleep 2
            "${ROOT_DIR}/tools/kemu/run.sh" bench generic-w4ir \
                "${SOURCE_JAR}" "${MODE}" "${PROFILE}" \
                >"${RESULT_DIR}/sample-${run}.out"
        fi
        cp -- "${CHILD_DIR}/receipt.txt" "${RESULT_DIR}/receipt-${run}.txt"
        average_ms="$(sed -n \
            's/.*W4ME_BENCH .* update-average-ms=\([0-9][0-9]*\) .*/\1/p' \
            "${RESULT_DIR}/receipt-${run}.txt")"
        if ! [[ "${average_ms}" =~ ^[0-9]+$ ]]; then
            printf 'error: sample %s has no update average\n' "${run}" >&2
            exit 1
        fi
        printf '%s %s\n' "${run}" "${average_ms}" >>"${RESULT_DIR}/averages.txt"
        sleep 2
        run=$((run + 1))
    done

    sort -n -k2,2 -- "${RESULT_DIR}/averages.txt" >"${RESULT_DIR}/averages-sorted.txt"
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
    } >"${RESULT_DIR}/receipt.txt"
    cat -- "${RESULT_DIR}/receipt.txt"
}

cmd_bench_generic_w4ir() {

    SOURCE_JAR="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    MODE="${2:-optimized}"
    PROFILE="${3:-${KEMU_PHONE_PROFILE:-0}}"
    if [ "${MODE}" = "baseline" ]; then
        PROBE_SOURCE="${ROOT_DIR}/src/test/java/w4me/midp/GenericW4IrBaselineBenchmarkMidlet.java"
        PROBE_CLASS="w4me.midp.GenericW4IrBaselineBenchmarkMidlet"
        EXPECTED_FUSIONS="disabled"
        EXPECTED_COMPACT="disabled"
        EXPECTED_TRACE="disabled"
        EXPECTED_DIRECT_INTRINSICS="disabled"
    elif [ "${MODE}" = "fusion-only" ]; then
        PROBE_SOURCE="${ROOT_DIR}/src/test/java/w4me/midp/GenericW4IrFusionBenchmarkMidlet.java"
        PROBE_CLASS="w4me.midp.GenericW4IrFusionBenchmarkMidlet"
        EXPECTED_FUSIONS="enabled"
        EXPECTED_COMPACT="disabled"
        EXPECTED_TRACE="enabled"
        EXPECTED_DIRECT_INTRINSICS="enabled"
    elif [ "${MODE}" = "trace-off" ]; then
        PROBE_SOURCE="${ROOT_DIR}/src/test/java/w4me/midp/GenericW4IrTraceOffBenchmarkMidlet.java"
        PROBE_CLASS="w4me.midp.GenericW4IrTraceOffBenchmarkMidlet"
        EXPECTED_FUSIONS="enabled"
        EXPECTED_COMPACT="enabled"
        EXPECTED_TRACE="disabled"
        EXPECTED_DIRECT_INTRINSICS="enabled"
    elif [ "${MODE}" = "direct-intrinsics-off" ]; then
        PROBE_SOURCE="${ROOT_DIR}/src/test/java/w4me/midp/GenericW4IrDirectIntrinsicsOffBenchmarkMidlet.java"
        PROBE_CLASS="w4me.midp.GenericW4IrDirectIntrinsicsOffBenchmarkMidlet"
        EXPECTED_FUSIONS="enabled"
        EXPECTED_COMPACT="enabled"
        EXPECTED_TRACE="enabled"
        EXPECTED_DIRECT_INTRINSICS="disabled"
    elif [ "${MODE}" = "optimized" ]; then
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
    if [ "${PROFILE}" != "0" ] && [ "${PROFILE}" != "1" ] &&
        [ "${PROFILE}" != "phone" ]; then
        printf 'usage: %s [jar] [optimized|direct-intrinsics-off|trace-off|fusion-only|baseline] [0|phone]\n' \
            "$0" >&2
        exit 2
    fi
    PROFILE_SUFFIX=""
    PROFILE_NAME="ordinary"
    if [ "${PROFILE}" = "1" ] || [ "${PROFILE}" = "phone" ]; then
        PROFILE_SUFFIX="-phone"
        PROFILE_NAME="phone"
    fi
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/benchmark-generic-w4ir-${MODE}${PROFILE_SUFFIX}"
    TEMP_DIR="$(mktemp -d)"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop >/dev/null 2>&1 || true
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
    } >"${TEMP_DIR}/probe.mf"
    jar ufm "${TEMP_DIR}/generic-w4ir-benchmark.jar" "${TEMP_DIR}/probe.mf" \
        2>"${TEMP_DIR}/manifest.log"
    BENCHMARK_SHA256="$(
        sha256sum -- "${TEMP_DIR}/generic-w4ir-benchmark.jar" | cut -d ' ' -f 1
    )"

    if [ "${PROFILE}" = "1" ] || [ "${PROFILE}" = "phone" ]; then
        "${ROOT_DIR}/tools/kemu/run.sh" phone \
            "${TEMP_DIR}/generic-w4ir-benchmark.jar" >"${RESULT_DIR}/profile.txt"
    else
        KEMU_SIZE="${KEMU_SIZE:-240x320}" "${ROOT_DIR}/tools/kemu/run.sh" session \
            start "${TEMP_DIR}/generic-w4ir-benchmark.jar" >/dev/null
    fi
    attempt=0
    while [ "${attempt}" -lt 10 ]; do
        "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 5000 >/dev/null
        "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs worker --lines 500 \
            >"${TEMP_DIR}/worker.log"
        if grep -F -q -- 'W4ME_BENCH cart=plasma-cube frames=120 ' \
            "${TEMP_DIR}/worker.log"; then
            break
        fi
        attempt=$((attempt + 1))
    done
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"

    if ! grep -F -- 'W4ME_BENCH cart=plasma-cube frames=120 ' \
        "${TEMP_DIR}/worker.log" >"${RESULT_DIR}/result.txt"; then
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
    if [ "${EXPECTED_COMPACT}" = "enabled" ]; then
        if ! grep -E -q -- ' compact-blocks=[1-9][0-9]* ' "${RESULT_DIR}/result.txt"; then
            printf 'error: compact W4IR executor did not run\n' >&2
            exit 1
        fi
    elif ! grep -F -q -- ' compact-blocks=0 ' "${RESULT_DIR}/result.txt"; then
        printf 'error: compact W4IR executor was not disabled\n' >&2
        exit 1
    fi
    if [ "${EXPECTED_TRACE}" = "enabled" ]; then
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
    {
        printf 'timing-authoritative=no runtime=KEmulator-HotSpot\n'
        printf 'source-jar-sha256=%s\n' "$(sha256sum -- "${SOURCE_JAR}" | awk '{print $1}')"
        printf 'benchmark-jar-sha256=%s\n' "${BENCHMARK_SHA256}"
        printf 'profile=%s\n' "${PROFILE_NAME}"
        if [ -f "${RESULT_DIR}/profile.txt" ]; then
            cat -- "${RESULT_DIR}/profile.txt"
        fi
        cat -- "${RESULT_DIR}/result.txt"
    } >"${RESULT_DIR}/receipt.txt"
    if [ -f "${RESULT_DIR}/profile.txt" ]; then
        cat -- "${RESULT_DIR}/profile.txt"
    fi
    cat -- "${RESULT_DIR}/result.txt"
}

cmd_bench_phone() {
    JAR_PATH="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/benchmark-phone"
    TEMP_DIR="$(mktemp -d)"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop >/dev/null 2>&1 || true
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
        >"${RESULT_DIR}/profile.txt"
    wait_ms=8000
    if [ -n "${W4ME_KEMU_CPU_PERCENT:-}" ] &&
        [ "${W4ME_KEMU_CPU_PERCENT}" -lt 100 ]; then
        slowdown=$(((100 + W4ME_KEMU_CPU_PERCENT - 1) / W4ME_KEMU_CPU_PERCENT))
        if [ "${slowdown}" -gt 10 ]; then
            slowdown=10
        fi
        wait_ms=$((wait_ms * slowdown))
    fi
    remaining_wait_ms="${wait_ms}"
    while [ "${remaining_wait_ms}" -gt 0 ]; do
        wait_chunk_ms="${remaining_wait_ms}"
        if [ "${wait_chunk_ms}" -gt 10000 ]; then
            wait_chunk_ms=10000
        fi
        "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait "${wait_chunk_ms}" >/dev/null
        remaining_wait_ms=$((remaining_wait_ms - wait_chunk_ms))
    done
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs worker --lines 500 \
        >"${RESULT_DIR}/worker.log"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd screenshot \
        --out "${RESULT_DIR}/plasma-phone-profile.png" >/dev/null

    if ! grep 'W4ME_BENCH cart=Plasma Cube frames=120 ' \
        "${RESULT_DIR}/worker.log" >"${RESULT_DIR}/result.txt"; then
        printf 'error: constrained KEmulator did not produce a Plasma benchmark receipt\n' >&2
        exit 1
    fi
    {
        printf 'timing-authoritative=no runtime=KEmulator-HotSpot\n'
        printf 'source-jar-sha256=%s\n' "${SOURCE_SHA256}"
        printf 'benchmark-jar-sha256=%s\n' "${BENCHMARK_SHA256}"
        cat -- "${RESULT_DIR}/profile.txt" "${RESULT_DIR}/result.txt"
    } >"${RESULT_DIR}/receipt.txt"
    cat -- "${RESULT_DIR}/receipt.txt"
}

cmd_bench_plasma() {
    JAR_PATH="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/benchmark-plasma"
    TEMP_DIR="$(mktemp -d)"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop >/dev/null 2>&1 || true
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
        start "${TEMP_DIR}/plasma-benchmark.jar" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 8000 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs worker --lines 500 >"${RESULT_DIR}/worker.log"

    if ! grep 'W4ME_BENCH cart=Plasma Cube frames=120 ' \
        "${RESULT_DIR}/worker.log" >"${RESULT_DIR}/result.txt"; then
        printf 'error: KEmulator did not produce the Plasma Cube benchmark receipt\n' >&2
        exit 1
    fi
    {
        printf 'timing-authoritative=no runtime=KEmulator-HotSpot\n'
        printf 'source-jar-sha256=%s\n' "${SOURCE_SHA256}"
        printf 'benchmark-jar-sha256=%s\n' "${BENCHMARK_SHA256}"
        cat -- "${RESULT_DIR}/result.txt"
    } >"${RESULT_DIR}/receipt.txt"
    cat -- "${RESULT_DIR}/receipt.txt"
}

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
    if [ "${PROFILE}" = "phone" ] || [ "${PROFILE}" = "1" ]; then
        CHILD_DIR="${CHILD_DIR}-phone"
    fi
    rm -rf -- "${RESULT_DIR}"
    mkdir -p -- "${RESULT_DIR}"
    : >"${RESULT_DIR}/totals.txt"

    run=1
    while [ "${run}" -le "${RUNS}" ]; do
        if ! "${ROOT_DIR}/tools/kemu/run.sh" bench untangle \
            "${SOURCE_JAR}" "${MODE}" "${PROFILE}" \
            >"${RESULT_DIR}/sample-${run}.out"; then
            "${ROOT_DIR}/tools/kemu/run.sh" session stop >/dev/null 2>&1 || true
            sleep 2
            "${ROOT_DIR}/tools/kemu/run.sh" bench untangle \
                "${SOURCE_JAR}" "${MODE}" "${PROFILE}" \
                >"${RESULT_DIR}/sample-${run}.out"
        fi
        cp -- "${CHILD_DIR}/receipt.txt" "${RESULT_DIR}/receipt-${run}.txt"
        if [ "${MODE}" = "comparison" ]; then
            total_ms="$(sed -n \
                's/.*W4ME_UNTANGLE_BENCH .* update-route-median-ms=\([0-9][0-9]*\) .*/\1/p' \
                "${RESULT_DIR}/receipt-${run}.txt" |
                awk '{ total += $1; count++ } END { if (count != 4) exit 1; print total }')"
        else
            total_ms="$(sed -n \
                's/.*W4ME_UNTANGLE_BENCH .* update-total-ms=\([0-9][0-9]*\) .*/\1/p' \
                "${RESULT_DIR}/receipt-${run}.txt")"
        fi
        if ! [[ "${total_ms}" =~ ^[0-9]+$ ]]; then
            printf 'error: sample %s has no update total\n' "${run}" >&2
            exit 1
        fi
        printf '%s %s\n' "${run}" "${total_ms}" >>"${RESULT_DIR}/totals.txt"
        sleep 2
        run=$((run + 1))
    done

    sort -n -k2,2 -- "${RESULT_DIR}/totals.txt" >"${RESULT_DIR}/totals-sorted.txt"
    median_line=$((RUNS / 2 + 1))
    median="$(sed -n "${median_line}p" "${RESULT_DIR}/totals-sorted.txt")"
    median_run="${median%% *}"
    median_ms="${median##* }"
    {
        printf 'timing-authoritative=no runtime=KEmulator-HotSpot\n'
        if [ "${MODE}" = "comparison" ]; then
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
    } >"${RESULT_DIR}/receipt.txt"
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
    if [ "${PROFILE}" = "phone" ] || [ "${PROFILE}" = "1" ]; then
        PROFILE_SUFFIX="-phone"
    elif [ "${PROFILE}" != "0" ]; then
        printf 'usage: %s [jar] [optimized|trace-off|fusion-only|baseline|comparison] [0|phone]\n' \
            "$0" >&2
        exit 2
    fi
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/benchmark-untangle-${MODE}${PROFILE_SUFFIX}"
    TEMP_DIR="$(mktemp -d)"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop >/dev/null 2>&1 || true
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
    } >"${TEMP_DIR}/probe.mf"
    jar ufm "${TEMP_DIR}/untangle-benchmark.jar" "${TEMP_DIR}/probe.mf" \
        2>"${TEMP_DIR}/manifest.log"

    if [ -n "${PROFILE_SUFFIX}" ]; then
        "${ROOT_DIR}/tools/kemu/run.sh" phone \
            "${TEMP_DIR}/untangle-benchmark.jar" >"${RESULT_DIR}/profile.txt"
    else
        KEMU_SIZE=176x220 "${ROOT_DIR}/tools/kemu/run.sh" session \
            start "${TEMP_DIR}/untangle-benchmark.jar" >/dev/null
    fi

    attempt=0
    COMPLETION_MODE="${MODE}"
    if [ "${MODE}" = "comparison" ]; then
        COMPLETION_MODE="baseline"
    fi
    while [ "${attempt}" -lt 12 ]; do
        "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 5000 >/dev/null
        "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs worker --lines 1000 \
            >"${TEMP_DIR}/worker.log"
        if grep -F -q -- "W4ME_UNTANGLE_BENCH mode=${COMPLETION_MODE} frames=3208 " \
            "${TEMP_DIR}/worker.log"; then
            break
        fi
        attempt=$((attempt + 1))
    done
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"

    if grep -F -q -- 'W4ME_UNTANGLE_BENCH_ERROR' "${TEMP_DIR}/worker.log"; then
        printf 'error: Untangle W4IR benchmark failed\n' >&2
        exit 1
    fi
    if [ "${MODE}" = "comparison" ]; then
        grep -E -- \
            'W4ME_UNTANGLE_BENCH mode=(optimized|trace-off|fusion-only|baseline) frames=3208 ' \
            "${TEMP_DIR}/worker.log" >"${RESULT_DIR}/result.txt"
        for expected_mode in optimized trace-off fusion-only baseline; do
            if [ "$(grep -F -c -- "W4ME_UNTANGLE_BENCH mode=${expected_mode} frames=3208 " \
                "${RESULT_DIR}/result.txt")" -ne 1 ]; then
                printf 'error: comparison receipt is missing mode %s\n' "${expected_mode}" >&2
                exit 1
            fi
        done
    else
        if ! grep -F -- "W4ME_UNTANGLE_BENCH mode=${MODE} frames=3208 " \
            "${TEMP_DIR}/worker.log" >"${RESULT_DIR}/result.txt"; then
            printf 'error: KEmulator did not produce the Untangle benchmark receipt\n' >&2
            exit 1
        fi
    fi
    if grep -F -v -q -- ' fast-paths=0 framebuffer-fnv1a=bc0231d9 ' \
        "${RESULT_DIR}/result.txt"; then
        printf 'error: Untangle benchmark was not exact generic execution\n' >&2
        exit 1
    fi
    if [ "${MODE}" = "comparison" ]; then
        grep -E -- \
            'W4ME_UNTANGLE_PHASE mode=(optimized|trace-off|fusion-only|baseline) ' \
            "${TEMP_DIR}/worker.log" >"${RESULT_DIR}/phases.txt"
    else
        grep -F -- "W4ME_UNTANGLE_PHASE mode=${MODE} " \
            "${TEMP_DIR}/worker.log" >"${RESULT_DIR}/phases.txt"
    fi
    {
        printf 'timing-authoritative=no runtime=KEmulator-HotSpot\n'
        printf 'source-jar-sha256=%s\n' "$(sha256sum -- "${SOURCE_JAR}" | awk '{print $1}')"
        printf 'cartridge-sha256=%s\n' \
            "$(sha256sum -- "${ROOT_DIR}/cartridges/untangle.wasm" | awk '{print $1}')"
        if [ -f "${RESULT_DIR}/profile.txt" ]; then
            cat -- "${RESULT_DIR}/profile.txt"
        fi
        cat -- "${RESULT_DIR}/phases.txt" "${RESULT_DIR}/result.txt"
    } >"${RESULT_DIR}/receipt.txt"
    cat -- "${RESULT_DIR}/receipt.txt"
}

cmd_bench_w4ir() {
    JAR_PATH="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    RESULT_DIR="${ROOT_DIR}/build/reports/kemu/w4ir-benchmark"
    TEMP_DIR="$(mktemp -d)"

    cleanup() {
        "${ROOT_DIR}/tools/kemu/run.sh" session stop >/dev/null 2>&1 || true
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
        start "${TEMP_DIR}/w4ir-benchmark.jar" >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 4500 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd key rsk --duration 80 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait 5000 >/dev/null
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd logs worker --lines 600 \
        >"${TEMP_DIR}/worker.log"
    cp -- "${TEMP_DIR}/worker.log" "${RESULT_DIR}/worker.log"

    build_receipt="$(grep -F -- 'W4ME_BENCH cart=Plasma Cube' "${TEMP_DIR}/worker.log" |
        grep -F -- 'w4ir=RMS-build' | tail -n 1)"
    hit_receipt="$(grep -F -- 'W4ME_BENCH cart=Plasma Cube' "${TEMP_DIR}/worker.log" |
        grep -F -- 'w4ir=RMS-hit' | tail -n 1)"
    if [ -z "${build_receipt}" ] || [ -z "${hit_receipt}" ]; then
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

usage() {
    printf '%s\n' \
        'usage: tools/kemu/run.sh session <start|cmd|stop> [args...]' \
        '       tools/kemu/run.sh phone [jar]' \
        '       tools/kemu/run.sh verify <scenario> [jar]' \
        '       tools/kemu/run.sh bench <scenario> [args...]' \
        '       tools/kemu/run.sh cpu-quota <percent> <command> [args...]' \
        '       tools/kemu/run.sh <verify|bench> --list'
}

list_scenarios() {
    case "$1" in
    verify)
        printf '%s\n' \
            audio-settings duck external file-picker generic-w4ir install invalid library \
            launcher plasma rms rubido sound sound-test tankle touch trap untangle \
            w4ir waternet
        ;;
    bench)
        printf '%s\n' \
            generic-corpus generic-w4ir generic-w4ir-matrix phone plasma \
            untangle untangle-matrix w4ir
        ;;
    esac
}

group="${1:-}"
case "${group}" in
session)
    shift
    cmd_session "$@"
    ;;
phone)
    shift
    cmd_phone "$@"
    ;;
cpu-quota)
    shift
    cmd_cpu_quota "$@"
    ;;
verify | bench)
    shift
    scenario="${1:-}"
    if [ "${scenario}" = "--list" ]; then
        list_scenarios "${group}"
        exit 0
    fi
    [ -n "${scenario}" ] || {
        usage >&2
        exit 1
    }
    shift
    function_name="cmd_${group}_${scenario//-/_}"
    if ! declare -F "${function_name}" >/dev/null; then
        printf 'error: unknown KEmulator %s scenario: %s\n' "${group}" "${scenario}" >&2
        exit 1
    fi
    "${function_name}" "$@"
    ;;
*)
    usage >&2
    exit 1
    ;;
esac
