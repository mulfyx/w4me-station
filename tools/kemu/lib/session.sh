#!/usr/bin/env bash
# Sourced by tools/kemu/run.sh; not a standalone entrypoint.
set -euo pipefail

stop_kemu_session() {
    local session_dir="$1"
    local bundle_file="$2"
    local session_id_file="$3"
    local bundle
    local session_id

    if [[ -r "${bundle_file}" ]] && [[ -r "${session_id_file}" ]]; then
        bundle="$(cat -- "${bundle_file}")"
        session_id="$(cat -- "${session_id_file}")"
        if [[ -x "${bundle}/kemu.sh" ]]; then
            (
                cd -- "${bundle}"
                ./kemu.sh --session-id "${session_id}" stop --force
            ) || true
        fi
    fi
    rm -rf -- "${session_dir}"
}

cmd_session() {
    local session_dir="/tmp/w4me-station-kemu-session"
    local session_id_file="${session_dir}/session-id"
    local bundle_file="${session_dir}/bundle"
    local data_dir="${session_dir}/data"
    local default_jar="${ROOT_DIR}/dist/w4me-station.jar"
    local screen_size="${KEMU_SIZE:-240x320}"
    local bundle
    local jar
    local session_id
    local -a open_args

    case "${1:?usage: tools/kemu/run.sh session <start [jar]|cmd args...|stop>}" in
        start)
            jar="${2:-${KEMU_JAR:-${default_jar}}}"
            if [[ ! -f "${jar}" ]]; then
                printf 'error: MIDlet JAR not found: %s\n' "${jar}" >&2
                exit 1
            fi
            jar="$(readlink -f -- "${jar}")"
            stop_kemu_session "${session_dir}" "${bundle_file}" "${session_id_file}"
            mkdir -p -- "${session_dir}"
            bundle="$(readlink -f -- "${KEMU_BUNDLE:-${KEMU_HOME}}")"
            if [[ ! -x "${bundle}/kemu.sh" ]]; then
                printf 'error: KEmulator bundle is incomplete: %s\n' "${bundle}" >&2
                exit 1
            fi
            session_id="${W4ME_KEMU_SESSION_ID:-w4me-$$}"
            printf '%s\n' "${bundle}" > "${bundle_file}"
            printf '%s\n' "${session_id}" > "${session_id_file}"
            open_args=(
                --session-id "${session_id}"
                open "${jar}"
                --headless
                --runtime release
                --size "${screen_size}"
                --data-dir "${data_dir}"
                --wait-ready
            )
            if [[ "${KEMU_RESET_STATE:-yes}" != "no" ]]; then
                open_args+=(--reset-state)
            fi
            if [[ -n "${KEMU_FILE_ROOT:-}" ]]; then
                open_args+=(--file-root "${KEMU_FILE_ROOT}")
            fi
            if [[ -n "${KEMU_WORKER_HEAP_MB:-}" ]]; then
                if ! [[ "${KEMU_WORKER_HEAP_MB}" =~ ^[1-9][0-9]{0,2}$ ]]; then
                    printf 'error: KEMU_WORKER_HEAP_MB must be from 1 to 999\n' >&2
                    exit 1
                fi
                open_args+=(--worker-xmx "${KEMU_WORKER_HEAP_MB}M")
            fi
            cd -- "${bundle}"
            ./kemu.sh "${open_args[@]}"
            ;;
        cmd)
            shift
            if [[ ! -r "${bundle_file}" ]] || [[ ! -r "${session_id_file}" ]]; then
                printf 'error: no active KEmulator session; run start first\n' >&2
                exit 1
            fi
            bundle="$(cat -- "${bundle_file}")"
            session_id="$(cat -- "${session_id_file}")"
            if [[ ! -x "${bundle}/kemu.sh" ]]; then
                printf 'error: active KEmulator bundle is missing: %s\n' "${bundle}" >&2
                exit 1
            fi
            cd -- "${bundle}"
            ./kemu.sh --session-id "${session_id}" "$@"
            ;;
        stop)
            stop_kemu_session "${session_dir}" "${bundle_file}" "${session_id_file}"
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

    local jar_path="${1:-${ROOT_DIR}/dist/w4me-station.jar}"
    local controller_pid
    local cpu_max
    local cpu_percent
    local cpu_period
    local cpu_quota
    local status_json
    local worker_pid

    KEMU_SIZE=176x220 KEMU_WORKER_HEAP_MB=64 \
        "${ROOT_DIR}/tools/kemu/run.sh" session start "${jar_path}"

    status_json="$("${ROOT_DIR}/tools/kemu/run.sh" session cmd status --json)"
    if command -v jq > /dev/null 2>&1; then
        controller_pid="$(printf '%s\n' "${status_json}" | jq -r '.result.pid')"
    else
        controller_pid="$(printf '%s\n' "${status_json}" \
            | sed -n 's/.*"pid"[^0-9]*\([0-9][0-9]*\).*/\1/p' \
            | sed -n '1p')"
    fi
    if ! [[ "${controller_pid}" =~ ^[1-9][0-9]*$ ]]; then
        printf 'error: KEmulator controller PID is missing from status JSON\n' >&2
        exit 1
    fi
    worker_pid="$(pgrep -P "${controller_pid}" -f 'AutomationWorkerMain' | sed -n '1p')"
    if [[ -z "${worker_pid}" ]]; then
        printf 'error: KEmulator worker process not found\n' >&2
        exit 1
    fi
    taskset -pc 0 "${worker_pid}" > /dev/null
    renice 10 -p "${worker_pid}" > /dev/null

    if [[ -n "${W4ME_KEMU_CPU_PERCENT:-}" ]] && [[ -n "${W4ME_KEMU_CPU_PERIOD_US:-}" ]]; then
        cpu_percent="${W4ME_KEMU_CPU_PERCENT}%"
        cpu_period="${W4ME_KEMU_CPU_PERIOD_US}"
    elif [[ -r /sys/fs/cgroup/cpu.max ]]; then
        cpu_max="$(cat -- /sys/fs/cgroup/cpu.max)"
        cpu_quota="${cpu_max%% *}"
        cpu_period="${cpu_max##* }"
        if [[ "${cpu_quota}" = "max" ]]; then
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
    local quota_percent

    if [[ "$#" -lt 2 ]]; then
        printf 'usage: %s PERCENT COMMAND [ARG ...]\n' "$0" >&2
        exit 2
    fi
    quota_percent="$1"
    shift
    if [[ "${W4ME_KEMU_CPU_PERCENT:-}" != "${quota_percent}" ]] \
        || [[ -z "${W4ME_KEMU_CPU_PERIOD_US:-}" ]]; then
        printf 'error: CPU quota must be launched through the host Docker runner\n' >&2
        exit 1
    fi
    printf 'CPU_QUOTA_PROFILE percent=%s period-us=%s engine=docker\n' \
        "${quota_percent}" "${W4ME_KEMU_CPU_PERIOD_US}"
    "$@"
}
