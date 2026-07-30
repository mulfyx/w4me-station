#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck disable=SC1091
source "${ROOT_DIR}/tools/container/env.sh"

report_dir="${ROOT_DIR}/build/reports/reproducible"
first_hashes="${report_dir}/first.sha256"
second_hashes="${report_dir}/second.sha256"
mkdir -p -- "${report_dir}"

"${ROOT_DIR}/tools/build.sh"
(
    cd -- "${ROOT_DIR}/dist"
    sha256sum -- \
        w4me-station.jar \
        w4me-station.jad \
        w4me-station-base.jar \
        w4me-station-base.jad
) > "${first_hashes}"

"${ROOT_DIR}/tools/build.sh"
(
    cd -- "${ROOT_DIR}/dist"
    sha256sum -- \
        w4me-station.jar \
        w4me-station.jad \
        w4me-station-base.jar \
        w4me-station-base.jad
) > "${second_hashes}"

diff -u -- "${first_hashes}" "${second_hashes}"
printf 'PASS reproducible release: two clean builds are byte-identical\n'
