#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(readlink -f -- "$(dirname -- "${BASH_SOURCE[0]}")/../..")"
IMAGE="${W4ME_TOOLCHAIN_IMAGE:-w4me-station:latest}"
PROJECT_LABEL="io.w4me.station.toolchain"
FINGERPRINT_LABEL="${PROJECT_LABEL}.fingerprint"
PREVIOUS_IMAGE="w4me-station-setup-previous:$$"

# shellcheck disable=SC1091
source "${ROOT_DIR}/tools/container/runtime.sh"

if ! command -v docker >/dev/null 2>&1; then
    printf 'error: docker command not found; install Docker or a Docker-compatible Podman shim\n' >&2
    exit 1
fi

fingerprint="$(
    cd -- "${ROOT_DIR}"
    sha256sum -- \
        tools/container/Containerfile \
        tools/container/kemu-icon.xpm |
        sha256sum |
        cut -d ' ' -f 1
)"
image_available=0
if current_fingerprint="$(
    inspect_container_image "${IMAGE}" \
        --format "{{ index .Config.Labels \"${FINGERPRINT_LABEL}\" }}"
)"; then
    image_available=1
else
    inspect_status="$?"
    if [ "${inspect_status}" -ne 1 ]; then
        exit "${inspect_status}"
    fi
    current_fingerprint=""
fi

if [ "${W4ME_TOOLCHAIN_FORCE_REBUILD:-0}" != "1" ] &&
    [ "${current_fingerprint}" = "${fingerprint}" ]; then
    printf 'Toolchain image %s is up to date (%s).\n' \
        "${IMAGE}" "${fingerprint}"
    exit 0
fi

previous_saved=0
cleanup_previous() {
    if [ "${previous_saved}" = "1" ]; then
        docker image rm "${PREVIOUS_IMAGE}" >/dev/null 2>&1 || true
        previous_saved=0
    fi
}
trap cleanup_previous EXIT

if [ "${image_available}" = "1" ]; then
    docker image tag "${IMAGE}" "${PREVIOUS_IMAGE}"
    previous_saved=1
fi

build_arguments=(
    build
    --tag "${IMAGE}"
    --file "${ROOT_DIR}/tools/container/Containerfile"
    --label "${PROJECT_LABEL}=true"
    --label "${FINGERPRINT_LABEL}=${fingerprint}"
)
if docker --version 2>/dev/null | grep -qi podman; then
    build_arguments+=(--layer-label "${PROJECT_LABEL}=true")
fi
build_arguments+=("${ROOT_DIR}")

docker "${build_arguments[@]}"

# Removing the temporary tag also prunes the superseded image's private parent
# chain. Shared base layers and images used by containers remain untouched.
cleanup_previous
trap - EXIT

# Future W4ME builds carry this label on their intermediate images. Remove only
# unused W4ME records; never prune unrelated project images or volumes.
docker image prune --force --filter "label=${PROJECT_LABEL}=true"

printf 'Toolchain image %s is ready (%s).\n' \
    "${IMAGE}" "${fingerprint}"
