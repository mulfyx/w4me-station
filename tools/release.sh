#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck disable=SC1091
source "${ROOT_DIR}/tools/container/env.sh"

"${ROOT_DIR}/tools/lint.sh"
"${ROOT_DIR}/tools/test.sh"
"${ROOT_DIR}/tools/build.sh"
"${ROOT_DIR}/tools/verify.sh" counterless

(
    cd -- "${ROOT_DIR}/dist"
    sha256sum -- \
        w4me-station.jar \
        w4me-station.jad \
        w4me-station-base.jar \
        w4me-station-base.jad \
        > SHA256SUMS
)

printf 'Release artifacts are ready in %s\n' "${ROOT_DIR}/dist"
