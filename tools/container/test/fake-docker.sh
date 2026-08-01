#!/usr/bin/env bash
set -euo pipefail

STATE_DIR="${FAKE_DOCKER_STATE_DIR:?missing fake Docker state directory}"
IMAGES_FILE="${STATE_DIR}/images.tsv"
CONTAINERS_FILE="${STATE_DIR}/containers.tsv"
CALLS_FILE="${STATE_DIR}/calls.log"

printf '%q ' "$@" >> "${CALLS_FILE}"
printf '\n' >> "${CALLS_FILE}"

find_image() {
    local target="${1:?missing image target}"

    awk -F '\t' -v target="${target}" \
        '$1 == target || $2 == target { print; exit }' \
        "${IMAGES_FILE}"
}

remove_image() {
    local target="${1:?missing image target}"
    local output_file="${IMAGES_FILE}.next"

    awk -F '\t' -v target="${target}" \
        '$1 != target && $2 != target { print }' \
        "${IMAGES_FILE}" > "${output_file}"
    mv -- "${output_file}" "${IMAGES_FILE}"
}

case "${1:-}" in
    --version)
        printf '%s\n' 'Docker version 1.0.0, fake build'
        ;;
    build)
        if [[ "$(< "${STATE_DIR}/build-result")" = "fail" ]]; then
            printf '%s\n' 'fake Docker build failed' >&2
            exit 1
        fi

        image_reference=""
        fingerprint=""
        shift
        while [[ "$#" -gt 0 ]]; do
            case "$1" in
                --tag)
                    image_reference="$2"
                    shift 2
                    ;;
                --label)
                    case "$2" in
                        io.w4me.station.toolchain.fingerprint=*)
                            fingerprint="${2#*=}"
                            ;;
                        *) ;;
                    esac
                    shift 2
                    ;;
                --file)
                    shift 2
                    ;;
                --force-rm | --no-cache)
                    shift
                    ;;
                --format | --layer-label)
                    shift 2
                    ;;
                --layers=false)
                    shift
                    ;;
                *)
                    shift
                    ;;
            esac
        done
        if [[ -z "${image_reference}" || -z "${fingerprint}" ]]; then
            printf '%s\n' 'fake Docker build did not receive image metadata' >&2
            exit 2
        fi

        new_image_id="$(< "${STATE_DIR}/next-image-id")"
        awk -F '\t' -v reference="${image_reference}" \
            'BEGIN { OFS = "\t" } { if ($2 == reference) $2 = "<none>:<none>"; print }' \
            "${IMAGES_FILE}" > "${IMAGES_FILE}.next"
        printf '%s\t%s\t%s\t1\n' \
            "${new_image_id}" "${image_reference}" "${fingerprint}" \
            >> "${IMAGES_FILE}.next"
        mv -- "${IMAGES_FILE}.next" "${IMAGES_FILE}"
        ;;
    container)
        if [[ "${2:-}" != "ls" ]]; then
            printf 'unsupported fake Docker container command: %s\n' "$*" >&2
            exit 2
        fi
        cat -- "${CONTAINERS_FILE}"
        ;;
    image)
        case "${2:-}" in
            inspect)
                record="$(find_image "${3:?missing image reference}")"
                if [[ -z "${record}" ]]; then
                    printf 'Error: %s: image not known\n' "${3}" >&2
                    exit 125
                fi
                IFS=$'\t' read -r image_id _ fingerprint _ <<< "${record}"
                format=""
                shift 3
                while [[ "$#" -gt 0 ]]; do
                    case "$1" in
                        --format)
                            format="$2"
                            shift 2
                            ;;
                        *)
                            shift
                            ;;
                    esac
                done
                case "${format}" in
                    *Config.Labels*) printf '%s\n' "${fingerprint}" ;;
                    *Id*) printf 'sha256:%s\n' "${image_id}" ;;
                    *) printf 'sha256:%s\n' "${image_id}" ;;
                esac
                ;;
            ls)
                while IFS=$'\t' read -r image_id reference _ project_image; do
                    [[ "${project_image}" = "1" ]] || continue
                    printf 'sha256:%s\t%s\n' "${image_id}" "${reference}"
                done < "${IMAGES_FILE}"
                ;;
            prune)
                awk -F '\t' \
                    'BEGIN { OFS = "\t" }
                        !($2 == "<none>:<none>" && $4 == "1") { print }' \
                    "${IMAGES_FILE}" > "${IMAGES_FILE}.next"
                mv -- "${IMAGES_FILE}.next" "${IMAGES_FILE}"
                ;;
            rm)
                remove_image "${3:?missing image target}"
                ;;
            tag)
                source_id="${3:?missing source image ID}"
                target_reference="${4:?missing target image reference}"
                source_record="$(find_image "${source_id}")"
                if [[ -z "${source_record}" ]]; then
                    printf 'Error: %s: image not known\n' "${source_id}" >&2
                    exit 125
                fi
                IFS=$'\t' read -r _ _ source_fingerprint source_project \
                    <<< "${source_record}"
                awk -F '\t' \
                    -v source_id="${source_id}" \
                    -v target_reference="${target_reference}" \
                    'BEGIN { OFS = "\t" }
                        $2 == target_reference { $2 = "<none>:<none>" }
                        $1 == source_id && $2 == "<none>:<none>" { next }
                        { print }' \
                    "${IMAGES_FILE}" > "${IMAGES_FILE}.next"
                printf '%s\t%s\t%s\t%s\n' \
                    "${source_id}" "${target_reference}" \
                    "${source_fingerprint}" "${source_project}" \
                    >> "${IMAGES_FILE}.next"
                mv -- "${IMAGES_FILE}.next" "${IMAGES_FILE}"
                ;;
            *)
                printf 'unsupported fake Docker image command: %s\n' "$*" >&2
                exit 2
                ;;
        esac
        ;;
    info)
        printf '%s\n' '1.43.2'
        ;;
    *)
        printf 'unsupported fake Docker command: %s\n' "$*" >&2
        exit 2
        ;;
esac
