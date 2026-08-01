#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(readlink -f -- "$(dirname -- "${BASH_SOURCE[0]}")/../..")"
SMOKE_DIR="$(mktemp -d)"
STATE_DIR="${SMOKE_DIR}/state"
FAKE_BIN_DIR="${SMOKE_DIR}/bin"
CANONICAL_IMAGE="localhost/w4me-station:latest"

trap 'rm -rf -- "${SMOKE_DIR}"' EXIT
mkdir -p -- "${STATE_DIR}" "${FAKE_BIN_DIR}" "${SMOKE_DIR}/runtime"
ln -s -- "${ROOT_DIR}/tools/container/test/fake-docker.sh" \
    "${FAKE_BIN_DIR}/docker"
: > "${STATE_DIR}/calls.log"
: > "${STATE_DIR}/containers.tsv"

export FAKE_DOCKER_STATE_DIR="${STATE_DIR}"
export PATH="${FAKE_BIN_DIR}:${PATH}"
export XDG_RUNTIME_DIR="${SMOKE_DIR}/runtime"
export W4ME_TOOLCHAIN_IMAGE="${CANONICAL_IMAGE}"

build_call_count() {
    awk '$1 == "build" { count++ } END { print count + 0 }' \
        "${STATE_DIR}/calls.log"
}

printf '%s\n' 'success' > "${STATE_DIR}/build-result"
printf '%s\n' 'new-image' > "${STATE_DIR}/next-image-id"
printf '%s\t%s\t%s\t%s\n' \
    'old-image' "${CANONICAL_IMAGE}" 'stale-fingerprint' '1' \
    > "${STATE_DIR}/images.tsv"
printf '%s\t%s\t%s\t%s\n' \
    'foreign-image' 'example.org/foreign:latest' '-' '0' \
    >> "${STATE_DIR}/images.tsv"

"${ROOT_DIR}/tools/container/setup.sh" > "${SMOKE_DIR}/first.stdout"
if ! grep -F -q -- $'new-image\tlocalhost/w4me-station:latest\t' \
    "${STATE_DIR}/images.tsv" \
    || grep -F -q -- 'old-image' "${STATE_DIR}/images.tsv" \
    || ! grep -F -q -- 'foreign-image' "${STATE_DIR}/images.tsv"; then
    printf '%s\n' 'FAIL setup did not replace only the W4ME image' >&2
    exit 1
fi
if ! grep -F -q -- '--force-rm' "${STATE_DIR}/calls.log" \
    || ! grep -F -q -- '--layers=false' "${STATE_DIR}/calls.log" \
    || ! grep -F -q -- \
        '--layer-label io.w4me.station.toolchain=true' \
        "${STATE_DIR}/calls.log" \
    || ! grep -F -q -- \
        'image prune --force --filter label=io.w4me.station.toolchain=true' \
        "${STATE_DIR}/calls.log"; then
    printf '%s\n' 'FAIL setup did not disable and prune Podman build layers' >&2
    exit 1
fi

build_calls="$(build_call_count)"
"${ROOT_DIR}/tools/container/setup.sh" > "${SMOKE_DIR}/noop.stdout"
current_build_calls="$(build_call_count)"
if [[ "${current_build_calls}" -ne "${build_calls}" ]]; then
    printf '%s\n' 'FAIL setup rebuilt an up-to-date image' >&2
    exit 1
fi

cp -- "${STATE_DIR}/images.tsv" "${STATE_DIR}/before-failed-build.tsv"
printf '%s\n' 'fail' > "${STATE_DIR}/build-result"
set +e
W4ME_TOOLCHAIN_FORCE_REBUILD=1 \
    "${ROOT_DIR}/tools/container/setup.sh" \
    > "${SMOKE_DIR}/failed.stdout" 2> "${SMOKE_DIR}/failed.stderr"
failed_status="$?"
set -e
if [[ "${failed_status}" -eq 0 ]] \
    || ! cmp -s -- \
        "${STATE_DIR}/before-failed-build.tsv" "${STATE_DIR}/images.tsv" \
    || ! grep -F -q -- 'the previous image remains' \
        "${SMOKE_DIR}/failed.stderr"; then
    printf '%s\n' 'FAIL failed build did not preserve the previous image' >&2
    exit 1
fi

printf '%s\n' 'success' > "${STATE_DIR}/build-result"
printf '%s\n' $'container-id\tw4me-station-kemu\tUp' \
    > "${STATE_DIR}/containers.tsv"
build_calls="$(build_call_count)"
set +e
W4ME_TOOLCHAIN_FORCE_REBUILD=1 \
    "${ROOT_DIR}/tools/container/setup.sh" \
    > "${SMOKE_DIR}/active.stdout" 2> "${SMOKE_DIR}/active.stderr"
active_status="$?"
set -e
current_build_calls="$(build_call_count)"
if [[ "${active_status}" -eq 0 ]] \
    || [[ "${current_build_calls}" -ne "${build_calls}" ]]; then
    printf '%s\n' 'FAIL active W4ME container did not block image replacement' >&2
    exit 1
fi
: > "${STATE_DIR}/containers.tsv"

printf '%s\t%s\t%s\t%s\n' \
    'superseded-image' '<none>:<none>' 'old-fingerprint' '1' \
    >> "${STATE_DIR}/images.tsv"
build_calls="$(build_call_count)"
"${ROOT_DIR}/tools/container/setup.sh" > "${SMOKE_DIR}/cleanup.stdout"
current_build_calls="$(build_call_count)"
if grep -F -q -- 'superseded-image' "${STATE_DIR}/images.tsv" \
    || [[ "${current_build_calls}" -ne "${build_calls}" ]]; then
    printf '%s\n' 'FAIL setup did not reconcile a superseded W4ME image' >&2
    exit 1
fi

printf '%s\n' 'override-image' > "${STATE_DIR}/next-image-id"
printf '%s\n' 'foreign-image'$'\t''example.org/foreign:latest'$'\t''-'$'\t''0' \
    > "${STATE_DIR}/images.tsv"
W4ME_TOOLCHAIN_IMAGE='w4me-toolchain:test' \
    "${ROOT_DIR}/tools/container/setup.sh" > "${SMOKE_DIR}/override.stdout"
if ! grep -F -q -- \
    $'override-image\tw4me-toolchain:test\t' "${STATE_DIR}/images.tsv"; then
    printf '%s\n' 'FAIL setup ignored W4ME_TOOLCHAIN_IMAGE' >&2
    exit 1
fi

printf '%s\n' \
    'PASS container setup: replace no-op rollback active-block cleanup override'
