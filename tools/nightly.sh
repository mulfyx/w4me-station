#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck disable=SC1091
source "${ROOT_DIR}/tools/container/env.sh"

report_dir="${ROOT_DIR}/build/reports/nightly"
trivy_cache_dir="${ROOT_DIR}/build/cache/trivy"
rootfs_report="${report_dir}/rootfs-trivy.json"
mkdir -p -- "${report_dir}" "${trivy_cache_dir}"

"${ROOT_DIR}/tools/lint.sh"
"${ROOT_DIR}/tools/analysis.sh"
"${ROOT_DIR}/tools/security.sh"

lychee --config "${ROOT_DIR}/lychee.toml" "${ROOT_DIR}"
trivy --cache-dir "${trivy_cache_dir}" filesystem \
    --exit-code 1 \
    --include-dev-deps \
    --scanners misconfig,secret,vuln \
    --severity HIGH,CRITICAL \
    --skip-dirs .git \
    --skip-dirs .local \
    --skip-dirs build \
    --skip-dirs dist \
    --skip-dirs node_modules \
    "${ROOT_DIR}"
trivy --cache-dir "${trivy_cache_dir}" filesystem \
    --format cyclonedx \
    --include-dev-deps \
    --output "${report_dir}/sbom.cdx.json" \
    --scanners vuln \
    --skip-dirs .git \
    --skip-dirs .local \
    --skip-dirs build \
    --skip-dirs dist \
    "${ROOT_DIR}"

if [[ -n "${GITHUB_REPOSITORY:-}" ]]; then
    scorecard \
        --format json \
        --output "${report_dir}/scorecard.json" \
        --repo="https://github.com/${GITHUB_REPOSITORY}"
fi

"${ROOT_DIR}/tools/reproducible.sh"

trivy --cache-dir "${trivy_cache_dir}" rootfs \
    --exit-code 0 \
    --format json \
    --output "${rootfs_report}" \
    --scanners vuln \
    --severity HIGH,CRITICAL \
    /
rootfs_findings="$(
    jq '[.Results[]? | (.Vulnerabilities // [])[]] | length' \
        "${rootfs_report}"
)"
if [[ "${rootfs_findings}" -ne 0 ]]; then
    jq -r '
        [.Results[]? |
            .Target as $target |
            (.Vulnerabilities // [])[] |
            {target: $target}] |
        group_by(.target)[] |
        "\(length)\t\(.[0].target)"
    ' "${rootfs_report}" | sort -nr >&2
    printf 'FAIL toolchain rootfs: %s HIGH/CRITICAL findings; report=%s\n' \
        "${rootfs_findings}" "${rootfs_report}" >&2
    exit 1
fi

printf 'PASS nightly: links, Trivy, SBOM, and reproducible release\n'
