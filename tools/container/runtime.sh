#!/usr/bin/env bash

# Inspect an image without confusing an inaccessible container runtime with a
# missing image. Callers may pass docker image inspect options after the image.
inspect_container_image() {
    image="$1"
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
