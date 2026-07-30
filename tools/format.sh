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

quality_maven spotless:apply
npm --prefix "${ROOT_DIR}" run format
ruff format "${ROOT_DIR}/bench" "${ROOT_DIR}/tools"

shopt -s globstar nullglob
scripts=("${ROOT_DIR}"/tools/**/*.sh)
shfmt -w -i 4 -bn -ci -sr "${scripts[@]}"

printf 'PASS format: Java, repository text, Python, and shell\n'
