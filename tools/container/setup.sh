#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(readlink -f -- "$(dirname -- "${BASH_SOURCE[0]}")/../..")"

# shellcheck disable=SC1091
source "${ROOT_DIR}/tools/container/runtime.sh"

IMAGE="${W4ME_TOOLCHAIN_IMAGE}"

if ! command -v docker > /dev/null 2>&1; then
    printf 'error: docker command not found; install Docker or a Docker-compatible Podman shim\n' >&2
    exit 1
fi

lock_toolchain_image exclusive

buildah_version="$(
    docker info --format '{{.Host.BuildahVersion}}' 2> /dev/null || true
)"
docker_version="$(docker --version 2> /dev/null || true)"
container_runtime_is_podman=0
if [[ -n "${buildah_version}" || "${docker_version,,}" = *podman* ]]; then
    container_runtime_is_podman=1
fi

require_image_idle() {
    local image_id="${1:-}"
    local containers

    containers="$(
        docker container ls --all \
            --filter "label=${W4ME_TOOLCHAIN_PROJECT_LABEL}=true" \
            --format '{{.ID}}\t{{.Names}}\t{{.Status}}'
    )"
    if [[ -z "${containers}" && -n "${image_id}" ]]; then
        containers="$(
            docker container ls --all \
                --filter "ancestor=${image_id}" \
                --format '{{.ID}}\t{{.Names}}\t{{.Status}}'
        )"
    fi
    if [[ -n "${containers}" ]]; then
        printf '%s\n' \
            'error: stop and remove W4ME toolchain containers before rebuilding the image' \
            >&2
        printf '%s\n' "${containers}" >&2
        return 1
    fi
}

project_image_records() {
    docker image ls --all --no-trunc \
        --filter "label=${W4ME_TOOLCHAIN_PROJECT_LABEL}=true" \
        --format '{{.ID}}\t{{.Repository}}:{{.Tag}}' \
        | sed 's/^sha256://'
}

prune_project_build_images() {
    docker image prune --force \
        --filter "label=${W4ME_TOOLCHAIN_PROJECT_LABEL}=true" \
        > /dev/null
}

remove_superseded_images() {
    local keep_image_id="${1:?missing canonical toolchain image ID}"
    local image_id
    local reference
    local records
    local remaining

    prune_project_build_images
    records="$(project_image_records)"
    while IFS=$'\t' read -r image_id reference; do
        [[ -n "${image_id}" ]] || continue
        if [[ "${image_id}" = "${keep_image_id}" && "${reference}" = "${IMAGE}" ]]; then
            continue
        fi
        if [[ "${reference}" = '<none>:<none>' ]]; then
            docker image rm "${image_id}" > /dev/null
        else
            docker image rm "${reference}" > /dev/null
        fi
    done <<< "${records}"

    remaining="$(project_image_records)"
    if [[ "${remaining}" != "${keep_image_id}"$'\t'"${IMAGE}" ]]; then
        printf '%s\n' 'error: expected exactly one canonical W4ME toolchain image' >&2
        if [[ -n "${remaining}" ]]; then
            printf '%s\n' "${remaining}" >&2
        fi
        return 1
    fi
}

fingerprint="$(toolchain_image_fingerprint "${ROOT_DIR}")"
current_fingerprint=""
current_image_id=""
set +e
current_fingerprint="$(
    inspect_container_image "${IMAGE}" \
        --format "{{ index .Config.Labels \"${W4ME_TOOLCHAIN_FINGERPRINT_LABEL}\" }}"
)"
inspect_status="$?"
set -e
if [[ "${inspect_status}" -eq 0 ]]; then
    current_image_id="$(
        inspect_container_image "${IMAGE}" --format '{{.Id}}'
    )"
    current_image_id="${current_image_id#sha256:}"
elif [[ "${inspect_status}" -ne 1 ]]; then
    exit "${inspect_status}"
fi

if [[ "${W4ME_TOOLCHAIN_FORCE_REBUILD:-0}" != "1" ]] \
    && [[ "${current_fingerprint}" = "${fingerprint}" ]]; then
    prune_project_build_images
    expected_record="${current_image_id}"$'\t'"${IMAGE}"
    records="$(project_image_records)"
    if [[ "${records}" = "${expected_record}" ]]; then
        printf 'Toolchain image %s is up to date (%s).\n' \
            "${IMAGE}" "${fingerprint}"
        exit 0
    fi

    require_image_idle "${current_image_id}"
    remove_superseded_images "${current_image_id}"
    printf 'Toolchain image %s is up to date; superseded images removed.\n' \
        "${IMAGE}"
    exit 0
fi

require_image_idle "${current_image_id}"

build_arguments=(
    build
    --tag "${IMAGE}"
    --file "${ROOT_DIR}/tools/container/Containerfile"
    --label "${W4ME_TOOLCHAIN_PROJECT_LABEL}=true"
    --label "${W4ME_TOOLCHAIN_FINGERPRINT_LABEL}=${fingerprint}"
)
if [[ "${container_runtime_is_podman}" = "1" ]]; then
    build_arguments+=(
        --format docker
        --force-rm
        --layers=false
        --layer-label "${W4ME_TOOLCHAIN_PROJECT_LABEL}=true"
    )
fi
if [[ "${W4ME_TOOLCHAIN_FORCE_REBUILD:-0}" = "1" ]]; then
    build_arguments+=(--no-cache)
fi
build_arguments+=("${ROOT_DIR}")

if ! docker "${build_arguments[@]}"; then
    printf 'error: toolchain build failed; the previous image remains at %s\n' \
        "${IMAGE}" >&2
    exit 1
fi

new_fingerprint="$(
    inspect_container_image "${IMAGE}" \
        --format "{{ index .Config.Labels \"${W4ME_TOOLCHAIN_FINGERPRINT_LABEL}\" }}"
)"
new_image_id="$(inspect_container_image "${IMAGE}" --format '{{.Id}}')"
new_image_id="${new_image_id#sha256:}"
if [[ "${new_fingerprint}" != "${fingerprint}" ]]; then
    if [[ -n "${current_image_id}" ]]; then
        docker image tag "${current_image_id}" "${IMAGE}"
    fi
    if [[ "${new_image_id}" != "${current_image_id}" ]]; then
        docker image rm "${new_image_id}" > /dev/null
    fi
    printf 'error: built toolchain fingerprint mismatch: expected %s, got %s\n' \
        "${fingerprint}" "${new_fingerprint}" >&2
    exit 1
fi

# The exclusive lock covers project runners. Recheck for containers started
# directly through Docker while the image was being built.
require_image_idle "${current_image_id}"
remove_superseded_images "${new_image_id}"

printf 'Toolchain image %s is ready (%s).\n' \
    "${IMAGE}" "${fingerprint}"
