#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
FRAMEBUFFER_ORACLE_SOURCE="${ROOT_DIR}/src/test/java/w4me/FramebufferOracle.java"

if [[ "${W4ME_TOOLCHAIN_CONTAINER:-}" != "1" ]]; then
    exec "${ROOT_DIR}/tools/container/run.sh" kemu "$@"
fi

# shellcheck source=../container/env.sh
source "${ROOT_DIR}/tools/container/env.sh"
# shellcheck source=lib/build.sh
source "${ROOT_DIR}/tools/kemu/lib/build.sh"
# shellcheck source=lib/api.sh
source "${ROOT_DIR}/tools/kemu/lib/api.sh"
# shellcheck source=lib/session.sh
source "${ROOT_DIR}/tools/kemu/lib/session.sh"
# shellcheck source=verify/navigation.sh
source "${ROOT_DIR}/tools/kemu/verify/navigation.sh"
# shellcheck source=verify/runtime.sh
source "${ROOT_DIR}/tools/kemu/verify/runtime.sh"
# shellcheck source=verify/storage.sh
source "${ROOT_DIR}/tools/kemu/verify/storage.sh"
# shellcheck source=verify/audio-input.sh
source "${ROOT_DIR}/tools/kemu/verify/audio-input.sh"
# shellcheck source=bench/corpus.sh
source "${ROOT_DIR}/tools/kemu/bench/corpus.sh"
# shellcheck source=bench/device.sh
source "${ROOT_DIR}/tools/kemu/bench/device.sh"
# shellcheck source=bench/untangle.sh
source "${ROOT_DIR}/tools/kemu/bench/untangle.sh"
# shellcheck source=bench/w4ir.sh
source "${ROOT_DIR}/tools/kemu/bench/w4ir.sh"

usage() {
    printf '%s\n' \
        'usage: tools/kemu/run.sh session <start|cmd|stop> [args...]' \
        '       tools/kemu/run.sh phone [jar]' \
        '       tools/kemu/run.sh verify <scenario> [jar]' \
        '       tools/kemu/run.sh bench <scenario> [args...]' \
        '       tools/kemu/run.sh cpu-quota <percent> <command> [args...]' \
        '       tools/kemu/run.sh <verify|bench> --list'
}

list_scenarios() {
    case "$1" in
        verify)
            printf '%s\n' \
                audio-settings duck external file-picker generic-w4ir install invalid library \
                launcher plasma rms rubido save-state sound sound-test tankle touch trap untangle \
                w4ir waternet
            ;;
        bench)
            printf '%s\n' \
                generic-corpus generic-w4ir generic-w4ir-matrix phone plasma \
                untangle untangle-matrix w4ir
            ;;
        *)
            printf 'error: unknown scenario family: %s\n' "$1" >&2
            return 2
            ;;
    esac
}

dispatch_verify() {
    local scenario="$1"
    shift

    case "${scenario}" in
        audio-settings) cmd_verify_audio_settings "$@" ;;
        duck) cmd_verify_duck "$@" ;;
        external) cmd_verify_external "$@" ;;
        file-picker) cmd_verify_file_picker "$@" ;;
        generic-w4ir) cmd_verify_generic_w4ir "$@" ;;
        install) cmd_verify_install "$@" ;;
        invalid) cmd_verify_invalid "$@" ;;
        library) cmd_verify_library "$@" ;;
        launcher) cmd_verify_launcher "$@" ;;
        plasma) cmd_verify_plasma "$@" ;;
        rms) cmd_verify_rms "$@" ;;
        rubido) cmd_verify_rubido "$@" ;;
        save-state) cmd_verify_save_state "$@" ;;
        sound) cmd_verify_sound "$@" ;;
        sound-test) cmd_verify_sound_test "$@" ;;
        tankle) cmd_verify_tankle "$@" ;;
        touch) cmd_verify_touch "$@" ;;
        trap) cmd_verify_trap "$@" ;;
        untangle) cmd_verify_untangle "$@" ;;
        w4ir) cmd_verify_w4ir "$@" ;;
        waternet) cmd_verify_waternet "$@" ;;
        *)
            printf 'error: unknown KEmulator verify scenario: %s\n' "${scenario}" >&2
            return 1
            ;;
    esac
}

dispatch_bench() {
    local scenario="$1"
    shift

    case "${scenario}" in
        generic-corpus) cmd_bench_generic_corpus "$@" ;;
        generic-w4ir) cmd_bench_generic_w4ir "$@" ;;
        generic-w4ir-matrix) cmd_bench_generic_w4ir_matrix "$@" ;;
        phone) cmd_bench_phone "$@" ;;
        plasma) cmd_bench_plasma "$@" ;;
        untangle) cmd_bench_untangle "$@" ;;
        untangle-matrix) cmd_bench_untangle_matrix "$@" ;;
        w4ir) cmd_bench_w4ir "$@" ;;
        *)
            printf 'error: unknown KEmulator bench scenario: %s\n' "${scenario}" >&2
            return 1
            ;;
    esac
}

group="${1:-}"
case "${group}" in
    session)
        shift
        cmd_session "$@"
        ;;
    phone)
        shift
        cmd_phone "$@"
        ;;
    cpu-quota)
        shift
        cmd_cpu_quota "$@"
        ;;
    verify)
        shift
        scenario="${1:-}"
        if [[ "${scenario}" = "--list" ]]; then
            list_scenarios verify
            exit 0
        fi
        [[ -n "${scenario}" ]] || {
            usage >&2
            exit 1
        }
        shift
        dispatch_verify "${scenario}" "$@"
        ;;
    bench)
        shift
        scenario="${1:-}"
        if [[ "${scenario}" = "--list" ]]; then
            list_scenarios bench
            exit 0
        fi
        [[ -n "${scenario}" ]] || {
            usage >&2
            exit 1
        }
        shift
        dispatch_bench "${scenario}" "$@"
        ;;
    *)
        usage >&2
        exit 1
        ;;
esac
