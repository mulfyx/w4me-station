#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck disable=SC1091
source "${ROOT_DIR}/tools/container/env.sh"

quality_maven() {
    JAVA_HOME="${QUALITY_JAVA_HOME}" \
        PATH="${QUALITY_JAVA_HOME}/bin:${PATH}" \
        mvn --batch-mode --no-transfer-progress -f "${ROOT_DIR}/pom.xml" "$@"
}

"${ROOT_DIR}/tools/fmt-check.sh"
quality_maven checkstyle:check pmd:check

shopt -s globstar nullglob
scripts=("${ROOT_DIR}"/tools/**/*.sh)

shellcheck --enable=all --severity=style "${scripts[@]}"

ruff check "${ROOT_DIR}/bench" "${ROOT_DIR}/tools"

npm --prefix "${ROOT_DIR}" run lint:markdown
npm --prefix "${ROOT_DIR}" run lint:renovate
vale_documents=(
    "${ROOT_DIR}/CHANGELOG.md"
    "${ROOT_DIR}/CONTRIBUTING.md"
    "${ROOT_DIR}/README.md"
    "${ROOT_DIR}/SECURITY.md"
    "${ROOT_DIR}/THIRD_PARTY_NOTICES.md"
    "${ROOT_DIR}/bench/README.md"
    "${ROOT_DIR}/bench/w4bench/README.md"
    "${ROOT_DIR}/cartridges/README.md"
    "${ROOT_DIR}/docs"
    "${ROOT_DIR}/testdata/README.md"
    "${ROOT_DIR}/tools/container/README.md"
    "${ROOT_DIR}/tools/reference/README.md"
)
# OpenSpec artifacts and agent prompts use controlled schema language rather
# than user-facing prose, so the Google documentation style does not own them.
vale --config "${ROOT_DIR}/.vale.ini" "${vale_documents[@]}"
typos "${ROOT_DIR}"

yamllint -c "${ROOT_DIR}/.yamllint.yml" "${ROOT_DIR}"
shellcheck_path="$(command -v shellcheck)"
actionlint -shellcheck="${shellcheck_path}" \
    "${ROOT_DIR}/.github/workflows/"*.yml
zizmor --persona pedantic "${ROOT_DIR}/.github/workflows"
hadolint --config "${ROOT_DIR}/.hadolint.yaml" \
    "${ROOT_DIR}/tools/container/Containerfile"
editorconfig-checker -config "${ROOT_DIR}/.editorconfig-checker.json" "${ROOT_DIR}"

(
    cd -- "${ROOT_DIR}"
    openspec validate --all --strict
)
"${ROOT_DIR}/tools/quality/repository.sh"

printf 'PASS quality: all fast blocking repository gates\n'
