#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(readlink -f -- "$(dirname -- "${BASH_SOURCE[0]}")/../..")"
IMAGE="${W4ME_TOOLCHAIN_IMAGE:-w4me-station:latest}"
KEMU_SESSION_CONTAINER="${W4ME_KEMU_SESSION_CONTAINER:-w4me-station-kemu}"

# shellcheck disable=SC1091
source "${ROOT_DIR}/tools/container/runtime.sh"

if [ "${W4ME_TOOLCHAIN_CONTAINER:-}" = "1" ]; then
    printf 'error: tools/container/run.sh is a host-side entrypoint\n' >&2
    exit 1
fi
if ! command -v docker >/dev/null 2>&1; then
    printf 'error: docker command not found; install Docker or a Docker-compatible Podman shim\n' >&2
    exit 1
fi
if inspect_container_image "${IMAGE}" >/dev/null; then
    :
else
    inspect_status="$?"
    if [ "${inspect_status}" -ne 1 ]; then
        exit "${inspect_status}"
    fi
    printf 'error: toolchain image %s not found; run just setup\n' "${IMAGE}" >&2
    exit 1
fi

HOST_UID="$(id -u)"
HOST_GID="$(id -g)"
CONTAINER_ROOT="/workspace"
CONTAINER_USER_ARGS=(--user "${HOST_UID}:${HOST_GID}")
if [ "$(docker info --format '{{.Host.Security.Rootless}}' 2>/dev/null || true)" = "true" ]; then
    CONTAINER_USER_ARGS=(--userns=keep-id)
fi
# shellcheck disable=SC2016
ENTRYPOINT_SCRIPT='
mkdir -p -- "${HOME}" "${XDG_RUNTIME_DIR}"
chmod 700 "${XDG_RUNTIME_DIR}"
cd /workspace
exec "$@"
'

COMMON_ARGS=(
    --init
    "${CONTAINER_USER_ARGS[@]}"
    -e W4ME_TOOLCHAIN_CONTAINER=1
    -e "W4ME_TOOLCHAIN_IMAGE=${IMAGE}"
    -e HOME=/tmp/w4me-home
    -e XDG_RUNTIME_DIR=/tmp/w4me-runtime
    -v "${ROOT_DIR}:${CONTAINER_ROOT}:Z"
    -w /tmp
)

for variable_name in $(compgen -e); do
    case "${variable_name}" in
    INTERPRETER_CONFIG_SOURCE | KEMU_* | W4ME_*)
        case "${variable_name}" in
        W4ME_TOOLCHAIN_CONTAINER | W4ME_TOOLCHAIN_IMAGE) ;;
        *) COMMON_ARGS+=(-e "${variable_name}") ;;
        esac
        ;;
    esac
done

container_path() {
    host_path="$(readlink -f -- "$1")"
    case "${host_path}" in
    "${ROOT_DIR}")
        printf '%s\n' "${CONTAINER_ROOT}"
        ;;
    "${ROOT_DIR}"/*)
        printf '%s/%s\n' "${CONTAINER_ROOT}" "${host_path#"${ROOT_DIR}/"}"
        ;;
    *)
        printf 'error: path is outside the repository: %s\n' "$1" >&2
        return 1
        ;;
    esac
}

KEMU_ENV_ARGS=()
collect_kemu_environment() {
    KEMU_ENV_ARGS=()
    for variable_name in \
        KEMU_BUNDLE \
        KEMU_DISPLAY \
        KEMU_JAR \
        KEMU_SIZE \
        KEMU_WORKER_HEAP_MB; do
        if [ -n "${!variable_name+x}" ]; then
            KEMU_ENV_ARGS+=(-e "${variable_name}")
        fi
    done
}

run_ephemeral() {
    script_path="$(container_path "$1")"
    shift
    exec docker run --rm \
        "${COMMON_ARGS[@]}" \
        "${EXTRA_RUN_ARGS[@]}" \
        "${IMAGE}" \
        bash -c "${ENTRYPOINT_SCRIPT}" w4me-entrypoint \
        "${script_path}" "$@"
}

session_exists() {
    docker container inspect "${KEMU_SESSION_CONTAINER}" >/dev/null 2>&1
}

remove_session_container() {
    if session_exists; then
        docker rm -f "${KEMU_SESSION_CONTAINER}" >/dev/null
    fi
}

run_kemu_session() {
    action="${1:?missing KEmulator session action}"
    shift
    collect_kemu_environment

    case "${action}" in
    start)
        if [ "$#" -gt 0 ]; then
            case "$1" in
            /*) set -- "$(container_path "$1")" "${@:2}" ;;
            esac
        fi
        remove_session_container
        docker run -d --rm \
            --name "${KEMU_SESSION_CONTAINER}" \
            "${COMMON_ARGS[@]}" \
            "${KEMU_ENV_ARGS[@]}" \
            "${IMAGE}" \
            bash -c "${ENTRYPOINT_SCRIPT}" w4me-entrypoint \
            sleep infinity >/dev/null
        if ! docker exec \
            "${KEMU_ENV_ARGS[@]}" \
            "${KEMU_SESSION_CONTAINER}" \
            bash -c "${ENTRYPOINT_SCRIPT}" w4me-entrypoint \
            "${CONTAINER_ROOT}/tools/kemu/run.sh" session start "$@"; then
            remove_session_container
            return 1
        fi
        printf 'KEmulator session container: %s\n' "${KEMU_SESSION_CONTAINER}"
        ;;
    cmd)
        if ! session_exists; then
            printf 'error: no active KEmulator session; run session start first\n' >&2
            exit 1
        fi
        exec docker exec \
            "${KEMU_ENV_ARGS[@]}" \
            "${KEMU_SESSION_CONTAINER}" \
            bash -c "${ENTRYPOINT_SCRIPT}" w4me-entrypoint \
            "${CONTAINER_ROOT}/tools/kemu/run.sh" session cmd "$@"
        ;;
    stop)
        if session_exists; then
            docker exec \
                "${KEMU_ENV_ARGS[@]}" \
                "${KEMU_SESSION_CONTAINER}" \
                bash -c "${ENTRYPOINT_SCRIPT}" w4me-entrypoint \
                "${CONTAINER_ROOT}/tools/kemu/run.sh" session stop "$@" ||
                true
            remove_session_container
        fi
        ;;
    *)
        printf 'error: unknown KEmulator session action: %s\n' "${action}" >&2
        exit 1
        ;;
    esac
}

run_kemu_quota() {
    quota_percent="${1:?missing CPU quota percent}"
    shift
    quota_period_us="${KEMU_CPU_PERIOD_US:-20000}"
    if ! [[ "${quota_percent}" =~ ^[0-9]+$ ]] ||
        [ "${quota_percent}" -lt 5 ] ||
        [ "${quota_percent}" -gt 100 ]; then
        printf 'error: CPU quota percent must be from 5 to 100\n' >&2
        exit 2
    fi
    if ! [[ "${quota_period_us}" =~ ^[0-9]+$ ]] ||
        [ "${quota_period_us}" -lt 10000 ] ||
        [ "${quota_period_us}" -gt 1000000 ]; then
        printf 'error: KEMU_CPU_PERIOD_US must be from 10000 to 1000000\n' >&2
        exit 2
    fi
    quota_us=$((quota_period_us * quota_percent / 100))
    EXTRA_RUN_ARGS=(
        --cpu-period "${quota_period_us}"
        --cpu-quota "${quota_us}"
        -e "W4ME_KEMU_CPU_PERCENT=${quota_percent}"
        -e "W4ME_KEMU_CPU_PERIOD_US=${quota_period_us}"
    )
    run_ephemeral "${ROOT_DIR}/tools/kemu/run.sh" \
        cpu-quota "${quota_percent}" "$@"
}

mode="${1:-}"
case "${mode}" in
exec)
    shift
    EXTRA_RUN_ARGS=()
    run_ephemeral "$@"
    ;;
kemu)
    shift
    case "${1:-}" in
    session)
        shift
        run_kemu_session "$@"
        ;;
    cpu-quota)
        shift
        run_kemu_quota "$@"
        ;;
    *)
        EXTRA_RUN_ARGS=()
        run_ephemeral "${ROOT_DIR}/tools/kemu/run.sh" "$@"
        ;;
    esac
    ;;
*)
    printf 'usage: %s <exec SCRIPT [ARG ...] | kemu [ARG ...]>\n' "$0" >&2
    exit 2
    ;;
esac
