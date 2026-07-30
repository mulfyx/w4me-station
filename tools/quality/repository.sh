#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"

# shellcheck disable=SC1091
source "${ROOT_DIR}/tools/container/env.sh"

quality_temp_dir="$(mktemp -d)"
cleanup() {
    rm -rf -- "${quality_temp_dir}"
}
trap cleanup EXIT

wasm_list="${quality_temp_dir}/wasm-files"
find "${ROOT_DIR}" \
    \( -path "${ROOT_DIR}/.git" \
    -o -path "${ROOT_DIR}/.local" \
    -o -path "${ROOT_DIR}/build" \
    -o -path "${ROOT_DIR}/dist" \
    -o -path "${ROOT_DIR}/node_modules" \
    -o -path "${ROOT_DIR}/target" \
    -o -path '*/generated' \) -prune \
    -o -type f -name '*.wasm' -print0 \
    > "${wasm_list}"
sort -z -o "${wasm_list}" "${wasm_list}"
mapfile -d '' wasm_files < "${wasm_list}"
for wasm_file in "${wasm_files[@]}"; do
    wasm-validate "${wasm_file}"
done

wat_build_dir="${quality_temp_dir}/wat"
mkdir -p -- "${wat_build_dir}"
wat_list="${quality_temp_dir}/wat-files"
find "${ROOT_DIR}" \
    \( -path "${ROOT_DIR}/.git" \
    -o -path "${ROOT_DIR}/.local" \
    -o -path "${ROOT_DIR}/build" \
    -o -path "${ROOT_DIR}/dist" \
    -o -path "${ROOT_DIR}/node_modules" \
    -o -path "${ROOT_DIR}/target" \
    -o -path '*/generated' \) -prune \
    -o -type f -name '*.wat' -print0 \
    > "${wat_list}"
sort -z -o "${wat_list}" "${wat_list}"
mapfile -d '' wat_files < "${wat_list}"
wat_index=0
for wat_file in "${wat_files[@]}"; do
    wat_output="${wat_build_dir}/${wat_index}.wasm"
    wat2wasm --debug-names --output="${wat_output}" "${wat_file}"
    wasm-validate "${wat_output}"
    wat_index=$((wat_index + 1))
done

"${ROOT_DIR}/tools/quality/repository.py" "${ROOT_DIR}"

paired_sample="$(
    printf '%s\n' \
        'sample,baseline-us,candidate-us,frames,order' \
        '1,2000,1900,100,baseline-first' \
        '2,2000,1900,100,candidate-first' \
        '3,2000,1900,100,baseline-first' \
        '4,2000,1900,100,candidate-first' \
        '5,2000,1900,100,baseline-first' \
        '6,2000,1900,100,candidate-first' \
        '7,2000,1900,100,baseline-first' \
        '8,2000,1900,100,candidate-first' \
        | gawk --lint=fatal -v source_dirty=no \
            -f "${ROOT_DIR}/tools/phoneme/paired-stats.awk"
)"
case "${paired_sample}" in
    *"evidence-quality=measured") ;;
    *)
        printf 'error: paired-stats.awk strict smoke returned: %s\n' \
            "${paired_sample}" >&2
        exit 1
        ;;
esac

git -C "${ROOT_DIR}" diff --check

printf 'PASS repository contracts: %d WASM, %d WAT, AWK, Git whitespace\n' \
    "${#wasm_files[@]}" "${#wat_files[@]}"
