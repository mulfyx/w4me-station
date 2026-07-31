#!/usr/bin/env bash
# Sourced by tools/kemu/run.sh; not a standalone entrypoint.
set -euo pipefail

kemu_wait_display() {
    local kind="$1"
    local title="$2"
    local selected_index="${3:--}"
    local timeout="${4:-5000}"
    local -a wait_args=(
        session cmd wait display
        --kind "${kind}"
        --timeout "${timeout}"
        --json
    )
    if [[ "${title}" != "-" ]]; then
        wait_args+=(--title "${title}")
    fi
    if [[ "${selected_index}" != "-" ]]; then
        wait_args+=(--selected-index "${selected_index}")
    fi
    "${ROOT_DIR}/tools/kemu/run.sh" "${wait_args[@]}" > /dev/null
}

kemu_observe() {
    local output_file="$1"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd observe --json > "${output_file}"
}

kemu_revision() {
    jq -er '.result.revision | numbers' "$1"
}

kemu_displayable_items_match() {
    local observation="$1"
    local expected="$2"
    jq -e --argjson expected "${expected}" \
        '[.result.displayable.items[]?.text] == $expected' \
        "${observation}" > /dev/null
}

kemu_command_run() {
    local label="$1"
    local observation="$2"
    local wait_next_display="${3:-yes}"
    local timeout="${4:-5000}"
    local revision
    local -a command_args

    revision="$(kemu_revision "${observation}")"
    command_args=(
        session cmd command run
        --label "${label}"
        --expect-revision "${revision}"
        --timeout "${timeout}"
        --json
    )
    if [[ "${wait_next_display}" = "yes" ]]; then
        command_args+=(--wait-next-display)
    fi
    "${ROOT_DIR}/tools/kemu/run.sh" "${command_args[@]}" > /dev/null
}

kemu_list_select() {
    local index="$1"
    local observation="$2"
    local revision

    revision="$(kemu_revision "${observation}")"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd list select "${index}" \
        --expect-revision "${revision}" --json > /dev/null
}

kemu_launch_library_entry() {
    local title="$1"
    local observation="$2"
    local timeout="${3:-5000}"
    local index

    kemu_wait_display list "W4ME Station" - "${timeout}"
    kemu_observe "${observation}"
    if ! index="$(
        jq -er --arg title "${title}" \
            '[
                .result.displayable.items
                | to_entries[]
                | select(.value.text == $title)
                | .key
            ]
            | if length == 1 then .[0] else empty end' \
            "${observation}"
    )"; then
        printf 'error: library must contain exactly one entry named %s\n' "${title}" >&2
        return 1
    fi
    kemu_list_select "${index}" "${observation}"
    kemu_wait_display list "W4ME Station" "${index}" "${timeout}"
    kemu_observe "${observation}"
    kemu_command_run Run "${observation}" yes "${timeout}"
    kemu_wait_display canvas - - "${timeout}"
}

kemu_key_press() {
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd key press "$1" \
        --wait-dispatched --json > /dev/null
}

kemu_key_hold() {
    local key="$1"
    local duration="$2"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd key hold "${key}" \
        --duration "${duration}" --wait-release --json > /dev/null
}

kemu_wait_log() {
    local regex="$1"
    local timeout="${2:-5000}"
    "${ROOT_DIR}/tools/kemu/run.sh" session cmd wait log \
        --regex "${regex}" --timeout "${timeout}" --json > /dev/null
}
