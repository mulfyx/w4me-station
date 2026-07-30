#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck disable=SC1091
source "${ROOT_DIR}/tools/container/env.sh"

gitleaks git --no-banner --redact --verbose --config "${ROOT_DIR}/.gitleaks.toml" --log-opts='--all' "${ROOT_DIR}"
gitleaks dir --no-banner --redact --verbose --config "${ROOT_DIR}/.gitleaks.toml" "${ROOT_DIR}"
osv-scanner scan source --recursive "${ROOT_DIR}"
reuse --root "${ROOT_DIR}" lint

if [[ -n "${COMMITLINT_FROM:-}" ]]; then
    case "${COMMITLINT_FROM}" in
        0000000000000000000000000000000000000000)
            COMMITLINT_FROM="$(git -C "${ROOT_DIR}" rev-parse HEAD^)"
            ;;
        *) ;;
    esac
    commitlint --cwd "${ROOT_DIR}" --from "${COMMITLINT_FROM}" \
        --to "${COMMITLINT_TO:-HEAD}" --verbose
else
    latest_commit="$(git -C "${ROOT_DIR}" rev-parse HEAD)"
    parent_commit="$(git -C "${ROOT_DIR}" rev-parse "${latest_commit}^")"
    commitlint --cwd "${ROOT_DIR}" \
        --from "${parent_commit}" \
        --to "${latest_commit}" \
        --verbose
fi

printf 'PASS security: secrets, dependency advisories, licenses, commits\n'
