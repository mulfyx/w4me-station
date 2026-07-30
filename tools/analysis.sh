#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck disable=SC1091
source "${ROOT_DIR}/tools/container/env.sh"

JAVA_HOME="${QUALITY_JAVA_HOME}" \
    PATH="${QUALITY_JAVA_HOME}/bin:${PATH}" \
    mvn --batch-mode --no-transfer-progress \
    -f "${ROOT_DIR}/pom.xml" \
    -Panalysis \
    clean compile spotbugs:check

printf 'PASS Java analysis: Error Prone and SpotBugs\n'
