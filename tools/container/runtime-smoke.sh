#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(readlink -f -- "$(dirname -- "${BASH_SOURCE[0]}")/../..")"

# shellcheck disable=SC1091
source "${ROOT_DIR}/tools/container/runtime.sh"

SMOKE_DIR="$(mktemp -d)"
trap 'rm -rf -- "${SMOKE_DIR}"' EXIT

docker() {
    case "${FAKE_DOCKER_MODE}" in
        available)
            printf '%s\n' 'sha256:toolchain'
            ;;
        missing)
            printf '%s\n' 'Error: missing:latest: image not known' >&2
            return 125
            ;;
        unavailable)
            printf '%s\n' \
                'Failed to obtain podman configuration: /run/user/1000/libpod is read-only' \
                >&2
            return 125
            ;;
        *)
            printf 'unexpected fake Docker mode: %s\n' "${FAKE_DOCKER_MODE}" >&2
            return 2
            ;;
    esac
}

FAKE_DOCKER_MODE=available
available_output="$(
    inspect_container_image available:latest \
        2> "${SMOKE_DIR}/available.stderr"
)"
if [[ "${available_output}" != "sha256:toolchain" ]] \
    || [[ -s "${SMOKE_DIR}/available.stderr" ]]; then
    printf '%s\n' 'FAIL container-runtime available-image classification' >&2
    exit 1
fi

FAKE_DOCKER_MODE=missing
set +e
inspect_container_image missing:latest \
    > "${SMOKE_DIR}/missing.stdout" \
    2> "${SMOKE_DIR}/missing.stderr"
missing_status="$?"
set -e
if [[ "${missing_status}" -eq 0 ]]; then
    printf '%s\n' 'FAIL container-runtime missing image accepted' >&2
    exit 1
fi
if [[ "${missing_status}" -ne 1 ]] \
    || [[ -s "${SMOKE_DIR}/missing.stdout" ]] \
    || [[ -s "${SMOKE_DIR}/missing.stderr" ]]; then
    printf '%s\n' 'FAIL container-runtime missing-image classification' >&2
    exit 1
fi

FAKE_DOCKER_MODE=unavailable
set +e
inspect_container_image available:latest \
    > "${SMOKE_DIR}/unavailable.stdout" \
    2> "${SMOKE_DIR}/unavailable.stderr"
unavailable_status="$?"
set -e
if [[ "${unavailable_status}" -eq 0 ]]; then
    printf '%s\n' 'FAIL container-runtime unavailable runtime accepted' >&2
    exit 1
fi
if [[ "${unavailable_status}" -ne 2 ]] \
    || ! grep -F -q -- \
        'cannot query the container runtime while checking image available:latest' \
        "${SMOKE_DIR}/unavailable.stderr" \
    || ! grep -F -q -- \
        'rebuilding the toolchain will not fix this error' \
        "${SMOKE_DIR}/unavailable.stderr"; then
    printf '%s\n' 'FAIL container-runtime unavailable-runtime classification' >&2
    exit 1
fi

printf '%s\n' \
    'PASS container-runtime available=0 missing=1 unavailable=2 no-false-setup-hint'
