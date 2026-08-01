#!/usr/bin/env bash

export W4ME_DEFAULT_TOOLCHAIN_IMAGE="localhost/w4me-station:latest"
export W4ME_TOOLCHAIN_PROJECT_LABEL="io.w4me.station.toolchain"
export W4ME_TOOLCHAIN_FINGERPRINT_LABEL="${W4ME_TOOLCHAIN_PROJECT_LABEL}.fingerprint"
W4ME_TOOLCHAIN_IMAGE="${W4ME_TOOLCHAIN_IMAGE:-${W4ME_DEFAULT_TOOLCHAIN_IMAGE}}"
export W4ME_TOOLCHAIN_IMAGE
W4ME_TOOLCHAIN_LOCK_FILE="${XDG_RUNTIME_DIR:-/tmp}/w4me-station-toolchain-$(id -u).lock"
export W4ME_TOOLCHAIN_LOCK_FILE

toolchain_image_fingerprint() (
    local root_dir="${1:?missing repository root}"
    local fingerprint_input
    local fingerprint_line
    local input_hashes

    cd -- "${root_dir}" || return
    input_hashes="$(
        sha256sum -- \
            config/quality/requirements.lock \
            package-lock.json \
            package.json \
            tools/container/Containerfile
    )" || return
    fingerprint_input="w4me-toolchain-image-v3
${input_hashes}"
    fingerprint_line="$(sha256sum <<< "${fingerprint_input}")" || return
    printf '%s\n' "${fingerprint_line%% *}"
)

lock_toolchain_image() {
    local mode="${1:?missing toolchain image lock mode}"

    if ! command -v flock > /dev/null 2>&1; then
        printf '%s\n' 'error: flock is required to coordinate W4ME toolchain image access' >&2
        return 1
    fi

    exec 9> "${W4ME_TOOLCHAIN_LOCK_FILE}"
    case "${mode}" in
        shared)
            flock --shared 9
            ;;
        exclusive)
            flock --exclusive 9
            ;;
        *)
            printf 'error: unknown toolchain image lock mode: %s\n' "${mode}" >&2
            return 2
            ;;
    esac
}

# Inspect an image without confusing an inaccessible container runtime with a
# missing image. Callers may pass docker image inspect options after the image.
inspect_container_image() {
    local image="$1"
    local inspect_error_file
    shift
    inspect_error_file="$(mktemp)"
    if docker image inspect "${image}" "$@" 2> "${inspect_error_file}"; then
        rm -f -- "${inspect_error_file}"
        return 0
    fi

    if grep -E -i -q \
        'no such (object: )?image|image not known|image .* does not exist' \
        "${inspect_error_file}"; then
        rm -f -- "${inspect_error_file}"
        return 1
    fi

    printf 'error: cannot query the container runtime while checking image %s\n' \
        "${image}" >&2
    sed 's/^/container-runtime: /' "${inspect_error_file}" >&2
    printf '%s\n' \
        'hint: restore local container-runtime access; rebuilding the toolchain will not fix this error' \
        >&2
    rm -f -- "${inspect_error_file}"
    return 2
}
