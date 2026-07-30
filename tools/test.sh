#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck disable=SC1091
source "${ROOT_DIR}/tools/container/env.sh"

PAIRED_FIXTURE="${ROOT_DIR}/src/test/resources/phoneme-paired-wrong-sign.csv"
PAIRED_EXPECTED="pairs=8 median-delta-us-per-frame=-255.5 median-speedup-percent=-0.220 wins=3 losses=5 ties=0 timer-resolution-us-per-frame=7.752 timer-resolved=yes order-balanced=yes source-clean=yes evidence-quality=measured"
PAIRED_ACTUAL="$(
    awk -f "${ROOT_DIR}/tools/phoneme/paired-stats.awk" "${PAIRED_FIXTURE}"
)"
if [ "${PAIRED_ACTUAL}" != "${PAIRED_EXPECTED}" ]; then
    printf 'FAIL paired-stats wrong-sign regression\n' >&2
    printf 'expected: %s\n' "${PAIRED_EXPECTED}" >&2
    printf 'actual:   %s\n' "${PAIRED_ACTUAL}" >&2
    exit 1
fi
printf 'paired-stats:pass %s\n' "${PAIRED_ACTUAL}"

PAIRED_DIRTY_ACTUAL="$(
    awk -v source_dirty=yes \
        -f "${ROOT_DIR}/tools/phoneme/paired-stats.awk" "${PAIRED_FIXTURE}"
)"
case "${PAIRED_DIRTY_ACTUAL}" in
*"source-clean=no evidence-quality=exploratory") ;;
*)
    printf 'FAIL paired-stats dirty-source classification\n' >&2
    printf 'actual: %s\n' "${PAIRED_DIRTY_ACTUAL}" >&2
    exit 1
    ;;
esac
printf 'paired-stats:pass dirty-source-classification\n'

bash "${ROOT_DIR}/tools/container/runtime-smoke.sh"

"${ROOT_DIR}/tools/bench/run.sh" w4bench

TEST_DIR="${ROOT_DIR}/build/test"
CLASSES_DIR="${TEST_DIR}/classes"
FRAMEBUFFER="${TEST_DIR}/mandelbrot-framebuffer.bin"
EXPECTED_FRAMEBUFFER_SHA256="c29d11cb46afd069a9d24b2561a8fae669fba5edaca1e42abeb335bf8beaf658"
EXTENSIONS_WASM="${TEST_DIR}/wasm-w4-extensions.wasm"
INDIRECT_TYPES_WASM="${TEST_DIR}/wasm-indirect-equivalent-types.wasm"
INTEGER_COMPACT_WASM="${TEST_DIR}/integer-compact-seven.wasm"
LOAD_TEE_FUSION_WASM="${TEST_DIR}/i32-load-local-tee-fusion.wasm"
STATIC_BRANCH_DESCRIPTORS_WASM="${TEST_DIR}/static-branch-descriptors.wasm"
DEFINED_CALL_ARGUMENTS_WASM="${TEST_DIR}/defined-call-arguments.wasm"
W4IR_CACHE_METADATA_WASM="${TEST_DIR}/w4ir-cache-metadata-recovery.wasm"
INTERPRETER_CONFIG_SOURCE="${INTERPRETER_CONFIG_SOURCE:-${ROOT_DIR}/src/main/java/w4me/wasm/InterpreterBuildConfig.java}"

[ -f "${INTERPRETER_CONFIG_SOURCE}" ] || {
    printf 'error: missing interpreter config: %s\n' \
        "${INTERPRETER_CONFIG_SOURCE}" >&2
    exit 1
}

rm -rf -- "${TEST_DIR}"
mkdir -p -- "${CLASSES_DIR}"

for cartridge in "${ROOT_DIR}"/cartridges/*.wasm; do
    wasm-validate "${cartridge}"
done
wat2wasm "${ROOT_DIR}/src/test/resources/wasm-w4-extensions.wat" \
    -o "${EXTENSIONS_WASM}"
wasm-validate "${EXTENSIONS_WASM}"
wat2wasm "${ROOT_DIR}/src/test/resources/wasm-indirect-equivalent-types.wat" \
    -o "${INDIRECT_TYPES_WASM}"
wasm-validate "${INDIRECT_TYPES_WASM}"
wat2wasm "${ROOT_DIR}/src/test/resources/integer-compact-seven.wat" \
    -o "${INTEGER_COMPACT_WASM}"
wasm-validate "${INTEGER_COMPACT_WASM}"
wat2wasm "${ROOT_DIR}/src/test/resources/i32-load-local-tee-fusion.wat" \
    -o "${LOAD_TEE_FUSION_WASM}"
wasm-validate "${LOAD_TEE_FUSION_WASM}"
wat2wasm "${ROOT_DIR}/src/test/resources/static-branch-descriptors.wat" \
    -o "${STATIC_BRANCH_DESCRIPTORS_WASM}"
wasm-validate "${STATIC_BRANCH_DESCRIPTORS_WASM}"
wat2wasm "${ROOT_DIR}/src/test/resources/defined-call-arguments.wat" \
    -o "${DEFINED_CALL_ARGUMENTS_WASM}"
wasm-validate "${DEFINED_CALL_ARGUMENTS_WASM}"
wat2wasm "${ROOT_DIR}/src/test/resources/w4ir-cache-metadata-recovery.wat" \
    -o "${W4IR_CACHE_METADATA_WASM}"
wasm-validate "${W4IR_CACHE_METADATA_WASM}"
find "${ROOT_DIR}/src/main/java/w4me/wasm" \
    "${ROOT_DIR}/src/main/java/w4me/runtime" \
    -name '*.java' \
    ! -path "${ROOT_DIR}/src/main/java/w4me/wasm/InterpreterBuildConfig.java" \
    -print | sort >"${TEST_DIR}/sources.list"
printf '%s\n' "${INTERPRETER_CONFIG_SOURCE}" >>"${TEST_DIR}/sources.list"
javac \
    -source "${J2ME_SOURCE}" \
    -target "${J2ME_TARGET}" \
    -Xlint:-options \
    -bootclasspath "${J2ME_BOOTCLASSPATH}" \
    -classpath "${MIDP_API_JAR}" \
    -d "${CLASSES_DIR}" \
    @"${TEST_DIR}/sources.list"

mkdir -p -- "${TEST_DIR}/forbidden-api-classes"
if javac \
    -source "${J2ME_SOURCE}" \
    -target "${J2ME_TARGET}" \
    -Xlint:-options \
    -bootclasspath "${J2ME_BOOTCLASSPATH}" \
    -classpath "${MIDP_API_JAR}" \
    -d "${TEST_DIR}/forbidden-api-classes" \
    "${ROOT_DIR}/src/test/resources/cldc-api/ForbiddenJavaSeApi.java" \
    >"${TEST_DIR}/forbidden-api.log" 2>&1; then
    printf 'error: CLDC API gate accepted java.lang.Math.log\n' >&2
    exit 1
fi
printf 'PASS CLDC API bootclasspath rejects java.lang.Math.log\n'

javac \
    -source "${J2ME_SOURCE}" \
    -target "${J2ME_TARGET}" \
    -Xlint:-options \
    -classpath "${CLASSES_DIR}" \
    -d "${CLASSES_DIR}" \
    "${ROOT_DIR}/src/test/java/w4me/FramebufferOracle.java"
javac \
    -source "${J2ME_SOURCE}" \
    -target "${J2ME_TARGET}" \
    -Xlint:-options \
    -classpath "${CLASSES_DIR}" \
    -d "${CLASSES_DIR}" \
    "${ROOT_DIR}/src/test/java/w4me/MandelbrotInterpreterSmoke.java"

javac \
    -source "${J2ME_SOURCE}" \
    -target "${J2ME_TARGET}" \
    -Xlint:-options \
    -classpath "${CLASSES_DIR}" \
    -d "${CLASSES_DIR}" \
    "${ROOT_DIR}/src/test/java/w4me/CartridgeCorpusSmoke.java"

javac \
    -source "${J2ME_SOURCE}" \
    -target "${J2ME_TARGET}" \
    -Xlint:-options \
    -classpath "${CLASSES_DIR}" \
    -d "${CLASSES_DIR}" \
    "${ROOT_DIR}/src/test/java/w4me/Wasm4CorpusReplaySmoke.java"

javac \
    -source "${J2ME_SOURCE}" \
    -target "${J2ME_TARGET}" \
    -Xlint:-options \
    -classpath "${CLASSES_DIR}" \
    -d "${CLASSES_DIR}" \
    "${ROOT_DIR}/src/test/java/w4me/RuntimeBenchmark.java"

javac \
    -source "${J2ME_SOURCE}" \
    -target "${J2ME_TARGET}" \
    -Xlint:-options \
    -classpath "${CLASSES_DIR}" \
    -d "${CLASSES_DIR}" \
    "${ROOT_DIR}/src/test/java/w4me/UntangleBenchmarkRoute.java" \
    "${ROOT_DIR}/src/test/java/w4me/UntangleRuntimeBenchmark.java"

javac \
    -source "${J2ME_SOURCE}" \
    -target "${J2ME_TARGET}" \
    -Xlint:-options \
    -classpath "${CLASSES_DIR}" \
    -d "${CLASSES_DIR}" \
    "${ROOT_DIR}/src/test/java/w4me/wasm/W4IrV11Smoke.java" \
    "${ROOT_DIR}/src/test/java/w4me/wasm/W4IrV11DifferentialSmoke.java" \
    "${ROOT_DIR}/src/test/java/w4me/wasm/W4IrDirectIntrinsicDifferentialSmoke.java" \
    "${ROOT_DIR}/src/test/java/w4me/wasm/W4IrFusionProfile.java"

javac \
    -source "${J2ME_SOURCE}" \
    -target "${J2ME_TARGET}" \
    -Xlint:-options \
    -classpath "${CLASSES_DIR}:${MIDP_API_JAR}" \
    -d "${CLASSES_DIR}" \
    "${ROOT_DIR}/src/test/java/w4me/wasm/CorpusWorkload.java" \
    "${ROOT_DIR}/src/test/java/w4me/wasm/InterpreterVariant.java" \
    "${ROOT_DIR}/src/test/java/w4me/wasm/FullStateDifferential.java" \
    "${ROOT_DIR}/src/test/java/w4me/wasm/IntegerCompactSevenDifferentialSmoke.java" \
    "${ROOT_DIR}/src/test/java/w4me/wasm/I32LoadLocalTeeFusionDifferentialSmoke.java" \
    "${ROOT_DIR}/src/test/java/w4me/wasm/StaticBranchDescriptorSmoke.java" \
    "${ROOT_DIR}/src/test/java/w4me/wasm/DefinedCallArgumentCopySmoke.java" \
    "${ROOT_DIR}/src/test/java/w4me/wasm/F32ConstCellCanonicalizationSmoke.java" \
    "${ROOT_DIR}/src/test/java/w4me/wasm/W4IrMalformedDescriptorCacheSmoke.java" \
    "${ROOT_DIR}/src/test/java/w4me/wasm/WasmValueStackPushGuardSmoke.java"

javac \
    -source "${J2ME_SOURCE}" \
    -target "${J2ME_TARGET}" \
    -Xlint:-options \
    -classpath "${CLASSES_DIR}" \
    -d "${CLASSES_DIR}" \
    "${ROOT_DIR}/src/test/java/w4me/SoundDemoSmoke.java"

javac \
    -source "${J2ME_SOURCE}" \
    -target "${J2ME_TARGET}" \
    -Xlint:-options \
    -classpath "${CLASSES_DIR}" \
    -d "${CLASSES_DIR}" \
    "${ROOT_DIR}/src/test/java/w4me/Wasm4PcmSmoke.java"

javac \
    -source "${J2ME_SOURCE}" \
    -target "${J2ME_TARGET}" \
    -Xlint:-options \
    -classpath "${CLASSES_DIR}" \
    -d "${CLASSES_DIR}" \
    "${ROOT_DIR}/src/test/java/w4me/runtime/audio/Wasm4PcmDifferentialSmoke.java" \
    "${ROOT_DIR}/src/test/java/w4me/runtime/audio/AudioSettingsSmoke.java" \
    "${ROOT_DIR}/src/test/java/w4me/runtime/audio/MmapiMidiBackendSmoke.java"

javac \
    -source "${J2ME_SOURCE}" \
    -target "${J2ME_TARGET}" \
    -Xlint:-options \
    -classpath "${CLASSES_DIR}" \
    -d "${CLASSES_DIR}" \
    "${ROOT_DIR}/src/test/java/w4me/ArgbBandDifferentialSmoke.java" \
    "${ROOT_DIR}/src/test/java/w4me/SoundTestSmoke.java" \
    "${ROOT_DIR}/src/test/java/w4me/TankleSmoke.java" \
    "${ROOT_DIR}/src/test/java/w4me/BlitPlainGeometrySmoke.java" \
    "${ROOT_DIR}/src/test/java/w4me/HorizontalSpanDifferentialSmoke.java" \
    "${ROOT_DIR}/src/test/java/w4me/RuntimeAbiSmoke.java" \
    "${ROOT_DIR}/src/test/java/w4me/WasmValidationSmoke.java" \
    "${ROOT_DIR}/src/test/java/w4me/WasmW4ExtensionsSmoke.java"

javac \
    -source "${J2ME_SOURCE}" \
    -target "${J2ME_TARGET}" \
    -Xlint:-options \
    -classpath "${MIDP_API_JAR}" \
    -d "${CLASSES_DIR}" \
    "${ROOT_DIR}/src/main/java/w4me/midp/FileSystemAccess.java" \
    "${ROOT_DIR}/src/test/java/w4me/midp/FilePageBuilderSmoke.java"

javac \
    -source "${J2ME_SOURCE}" \
    -target "${J2ME_TARGET}" \
    -Xlint:-options \
    -classpath "${MIDP_API_JAR}" \
    -d "${CLASSES_DIR}" \
    "${ROOT_DIR}/src/main/java/w4me/midp/AudioPreferences.java" \
    "${ROOT_DIR}/src/test/java/w4me/midp/AudioPreferencesSmoke.java"

javac \
    -source "${J2ME_SOURCE}" \
    -target "${J2ME_TARGET}" \
    -Xlint:-options \
    -classpath "${CLASSES_DIR}" \
    -d "${CLASSES_DIR}" \
    "${ROOT_DIR}/src/main/java/w4me/midp/SystemMenuModel.java" \
    "${ROOT_DIR}/src/main/java/w4me/midp/SystemMenuState.java" \
    "${ROOT_DIR}/src/main/java/w4me/midp/SettingsMenuModel.java" \
    "${ROOT_DIR}/src/test/java/w4me/midp/SystemMenuSmoke.java"

java -classpath "${CLASSES_DIR}" w4me.MandelbrotInterpreterSmoke \
    "${ROOT_DIR}/cartridges/mandelbrot.wasm" \
    "${ROOT_DIR}/src/main/resources/w4font.bin" \
    "${FRAMEBUFFER}"

actual_sha256="$(sha256sum -- "${FRAMEBUFFER}" | cut -d ' ' -f 1)"
if [ "${actual_sha256}" != "${EXPECTED_FRAMEBUFFER_SHA256}" ]; then
    printf 'error: framebuffer mismatch: expected %s, got %s\n' \
        "${EXPECTED_FRAMEBUFFER_SHA256}" "${actual_sha256}" >&2
    exit 1
fi
printf 'PASS framebuffer-sha256=%s\n' "${actual_sha256}"

java -classpath "${CLASSES_DIR}" w4me.CartridgeCorpusSmoke \
    "${ROOT_DIR}/src/main/resources/w4font.bin" \
    "${ROOT_DIR}/cartridges/duck-maze.wasm" \
    "${ROOT_DIR}/cartridges/plasma-cube.wasm" \
    "${ROOT_DIR}/testdata/oracles/plasma-cube-60-frames.csv"

java -classpath "${CLASSES_DIR}" w4me.Wasm4CorpusReplaySmoke \
    "${ROOT_DIR}/src/main/resources/w4font.bin" \
    "${ROOT_DIR}/cartridges/waternet.wasm" \
    "${ROOT_DIR}/testdata/oracles/waternet/input.csv" \
    "${ROOT_DIR}/testdata/oracles/waternet/oracle.csv" \
    "${ROOT_DIR}/testdata/oracles/waternet/tone.csv" \
    "${ROOT_DIR}/testdata/oracles/waternet/disk.csv" \
    "739f355da8e90cfd25c0c677cb5397f27affca171ae7ed731fafc51f008caa93" \
    "waternet"

java -classpath "${CLASSES_DIR}" w4me.Wasm4CorpusReplaySmoke \
    "${ROOT_DIR}/src/main/resources/w4font.bin" \
    "${ROOT_DIR}/cartridges/rubido.wasm" \
    "${ROOT_DIR}/testdata/oracles/rubido/input.csv" \
    "${ROOT_DIR}/testdata/oracles/rubido/oracle.csv" \
    "${ROOT_DIR}/testdata/oracles/rubido/tone.csv" \
    "${ROOT_DIR}/testdata/oracles/rubido/disk.csv" \
    "2b4b5d1c888d9286b87193d11420171eaeff3aff0bcb376b4396c9533ad115fd" \
    "rubido"

java -classpath "${CLASSES_DIR}" w4me.Wasm4CorpusReplaySmoke \
    "${ROOT_DIR}/src/main/resources/w4font.bin" \
    "${ROOT_DIR}/cartridges/untangle.wasm" \
    "${ROOT_DIR}/testdata/oracles/untangle/input.csv" \
    "${ROOT_DIR}/testdata/oracles/untangle/oracle.csv" \
    "${ROOT_DIR}/testdata/oracles/untangle/tone.csv" \
    "${ROOT_DIR}/testdata/oracles/untangle/disk.csv" \
    "f2923336ede479ca4b47cb3fae75d4e252439908ab680d6dcb82a4f0ac0bfb62" \
    "untangle"

java -classpath "${CLASSES_DIR}" w4me.Wasm4CorpusReplaySmoke \
    "${ROOT_DIR}/src/main/resources/w4font.bin" \
    "${ROOT_DIR}/cartridges/game-of-life-zig-edition.wasm" \
    "${ROOT_DIR}/testdata/oracles/game-of-life-zig-edition/input.csv" \
    "${ROOT_DIR}/testdata/oracles/game-of-life-zig-edition/oracle.csv" \
    "${ROOT_DIR}/testdata/oracles/game-of-life-zig-edition/tone.csv" \
    "${ROOT_DIR}/testdata/oracles/game-of-life-zig-edition/disk.csv" \
    "ca57b23b8bda728a6f92848f8981cfb7837c1c389639cc568c29fddca597d4d3" \
    "game-of-life-zig-edition"

java -classpath "${CLASSES_DIR}" w4me.wasm.W4IrV11Smoke \
    "${ROOT_DIR}/cartridges/plasma-cube.wasm"

java -classpath "${CLASSES_DIR}" w4me.wasm.W4IrV11DifferentialSmoke \
    "${ROOT_DIR}/src/main/resources/w4font.bin" \
    "${ROOT_DIR}/cartridges/plasma-cube.wasm"

java -classpath "${CLASSES_DIR}" w4me.wasm.W4IrDirectIntrinsicDifferentialSmoke \
    "${ROOT_DIR}/src/main/resources/w4font.bin" \
    "${ROOT_DIR}/cartridges/plasma-cube.wasm"

java -classpath "${CLASSES_DIR}" w4me.wasm.W4IrFusionProfile \
    "${ROOT_DIR}/src/main/resources/w4font.bin" \
    "${ROOT_DIR}/cartridges/plasma-cube.wasm" \
    "${ROOT_DIR}/cartridges/untangle.wasm"

java -classpath "${CLASSES_DIR}" w4me.wasm.FullStateDifferential \
    "host-suite-current" \
    "${ROOT_DIR}/src/main/resources/w4font.bin" \
    "${ROOT_DIR}/cartridges/plasma-cube.wasm" \
    "${ROOT_DIR}/cartridges/duck-maze.wasm" \
    "${ROOT_DIR}/cartridges/waternet.wasm" \
    "${ROOT_DIR}/testdata/oracles/waternet/input.csv" \
    "${ROOT_DIR}/cartridges/rubido.wasm" \
    "${ROOT_DIR}/testdata/oracles/rubido/input.csv" \
    "${ROOT_DIR}/cartridges/untangle.wasm" \
    "${ROOT_DIR}/testdata/oracles/untangle/input.csv" \
    "${ROOT_DIR}/cartridges/game-of-life-zig-edition.wasm"

java -classpath "${CLASSES_DIR}" w4me.wasm.FullStateDifferential \
    "host-suite-seven-opcode" \
    "${ROOT_DIR}/src/main/resources/w4font.bin" \
    "${ROOT_DIR}/cartridges/plasma-cube.wasm" \
    "${ROOT_DIR}/cartridges/duck-maze.wasm" \
    "${ROOT_DIR}/cartridges/waternet.wasm" \
    "${ROOT_DIR}/testdata/oracles/waternet/input.csv" \
    "${ROOT_DIR}/cartridges/rubido.wasm" \
    "${ROOT_DIR}/testdata/oracles/rubido/input.csv" \
    "${ROOT_DIR}/cartridges/untangle.wasm" \
    "${ROOT_DIR}/testdata/oracles/untangle/input.csv" \
    "${ROOT_DIR}/cartridges/game-of-life-zig-edition.wasm" \
    "current-seven"

java -classpath "${CLASSES_DIR}" w4me.wasm.FullStateDifferential \
    "host-suite-host-import-id" \
    "${ROOT_DIR}/src/main/resources/w4font.bin" \
    "${ROOT_DIR}/cartridges/plasma-cube.wasm" \
    "${ROOT_DIR}/cartridges/duck-maze.wasm" \
    "${ROOT_DIR}/cartridges/waternet.wasm" \
    "${ROOT_DIR}/testdata/oracles/waternet/input.csv" \
    "${ROOT_DIR}/cartridges/rubido.wasm" \
    "${ROOT_DIR}/testdata/oracles/rubido/input.csv" \
    "${ROOT_DIR}/cartridges/untangle.wasm" \
    "${ROOT_DIR}/testdata/oracles/untangle/input.csv" \
    "${ROOT_DIR}/cartridges/game-of-life-zig-edition.wasm" \
    "seven-host-import-id"

java -classpath "${CLASSES_DIR}" w4me.wasm.FullStateDifferential \
    "host-suite-load-tee" \
    "${ROOT_DIR}/src/main/resources/w4font.bin" \
    "${ROOT_DIR}/cartridges/plasma-cube.wasm" \
    "${ROOT_DIR}/cartridges/duck-maze.wasm" \
    "${ROOT_DIR}/cartridges/waternet.wasm" \
    "${ROOT_DIR}/testdata/oracles/waternet/input.csv" \
    "${ROOT_DIR}/cartridges/rubido.wasm" \
    "${ROOT_DIR}/testdata/oracles/rubido/input.csv" \
    "${ROOT_DIR}/cartridges/untangle.wasm" \
    "${ROOT_DIR}/testdata/oracles/untangle/input.csv" \
    "${ROOT_DIR}/cartridges/game-of-life-zig-edition.wasm" \
    "host-import-id-load-tee"

java -classpath "${CLASSES_DIR}" w4me.wasm.IntegerCompactSevenDifferentialSmoke \
    "${ROOT_DIR}/src/main/resources/w4font.bin" \
    "${INTEGER_COMPACT_WASM}"

java -classpath "${CLASSES_DIR}" w4me.wasm.I32LoadLocalTeeFusionDifferentialSmoke \
    "${ROOT_DIR}/src/main/resources/w4font.bin" \
    "${LOAD_TEE_FUSION_WASM}"

java -classpath "${CLASSES_DIR}" w4me.wasm.StaticBranchDescriptorSmoke \
    "${ROOT_DIR}/src/main/resources/w4font.bin" \
    "${STATIC_BRANCH_DESCRIPTORS_WASM}"

java -classpath "${CLASSES_DIR}" w4me.wasm.DefinedCallArgumentCopySmoke \
    "${DEFINED_CALL_ARGUMENTS_WASM}"

java -classpath "${CLASSES_DIR}" w4me.wasm.F32ConstCellCanonicalizationSmoke \
    "${W4IR_CACHE_METADATA_WASM}"
java -classpath "${CLASSES_DIR}:${MIDP_API_JAR}:${KEMU_HOME}/lib/*" w4me.wasm.W4IrMalformedDescriptorCacheSmoke \
    "${W4IR_CACHE_METADATA_WASM}"
java -classpath "${CLASSES_DIR}:${MIDP_API_JAR}:${KEMU_HOME}/lib/*" w4me.wasm.WasmValueStackPushGuardSmoke

java -classpath "${CLASSES_DIR}" w4me.ArgbBandDifferentialSmoke \
    "${ROOT_DIR}/src/main/resources/w4font.bin" \
    "${ROOT_DIR}/cartridges/waternet.wasm"

java -classpath "${CLASSES_DIR}" w4me.RuntimeBenchmark \
    "${ROOT_DIR}/src/main/resources/w4font.bin" \
    "${ROOT_DIR}/cartridges/mandelbrot.wasm" \
    "${ROOT_DIR}/cartridges/duck-maze.wasm" \
    "${ROOT_DIR}/cartridges/plasma-cube.wasm"

java -classpath "${CLASSES_DIR}" w4me.SoundDemoSmoke \
    "${ROOT_DIR}/src/main/resources/w4font.bin" \
    "${ROOT_DIR}/cartridges/sound-demo.wasm"

java -classpath "${CLASSES_DIR}" w4me.Wasm4PcmSmoke
java -classpath "${CLASSES_DIR}" w4me.runtime.audio.Wasm4PcmDifferentialSmoke
java -classpath "${CLASSES_DIR}" w4me.runtime.audio.AudioSettingsSmoke
java -classpath "${CLASSES_DIR}" w4me.runtime.audio.MmapiMidiBackendSmoke
java -classpath "${CLASSES_DIR}" w4me.midp.SystemMenuSmoke
java -classpath "${CLASSES_DIR}:${MIDP_API_JAR}:${KEMU_HOME}/lib/*" w4me.midp.AudioPreferencesSmoke

java -classpath "${CLASSES_DIR}" w4me.SoundTestSmoke \
    "${ROOT_DIR}/src/main/resources/w4font.bin" \
    "${ROOT_DIR}/cartridges/sound-test.wasm"

java -classpath "${CLASSES_DIR}" w4me.TankleSmoke \
    "${ROOT_DIR}/src/main/resources/w4font.bin" \
    "${ROOT_DIR}/cartridges/tankle.wasm"

java -classpath "${CLASSES_DIR}" w4me.BlitPlainGeometrySmoke \
    "${ROOT_DIR}/src/main/resources/w4font.bin" \
    "${ROOT_DIR}/cartridges/mandelbrot.wasm"

java -classpath "${CLASSES_DIR}" w4me.HorizontalSpanDifferentialSmoke \
    "${ROOT_DIR}/src/main/resources/w4font.bin" \
    "${ROOT_DIR}/cartridges/mandelbrot.wasm"

java -classpath "${CLASSES_DIR}" w4me.RuntimeAbiSmoke \
    "${ROOT_DIR}/src/main/resources/w4font.bin" \
    "${ROOT_DIR}/cartridges/mandelbrot.wasm"

java -classpath "${CLASSES_DIR}" w4me.WasmValidationSmoke \
    "${ROOT_DIR}/cartridges/mandelbrot.wasm"

java -classpath "${CLASSES_DIR}" w4me.WasmW4ExtensionsSmoke \
    "${ROOT_DIR}/src/main/resources/w4font.bin" \
    "${EXTENSIONS_WASM}" \
    "${INDIRECT_TYPES_WASM}"

java -classpath "${CLASSES_DIR}" w4me.midp.FilePageBuilderSmoke
