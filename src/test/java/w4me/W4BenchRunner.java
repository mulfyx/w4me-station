package w4me;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import w4me.runtime.Wasm4Runtime;
import w4me.wasm.WasmInterpreter;
import w4me.wasm.WasmModule;
import w4me.wasm.WasmTrap;

/**
 * Deterministic WASM cartridge benchmark runner for the native no-JIT phoneME rig. The cartridge owns the workload;
 * this runner only resets it, times {@code run}, and validates the result record outside that interval.
 *
 * <p>The generated {@link W4BenchProfile} is the frozen contract. It is deliberately a Java 1.3/CLDC-only class so this
 * runner can execute under the same VM that is used for performance acceptance.
 */
public final class W4BenchRunner {
    public static final String CARTRIDGE_RESOURCE = "/w4bench-v1.wasm";

    static final int RESULT_MAGIC = 0x57423431;
    static final int RESULT_CONTRACT_VERSION = 1;
    static final int RESULT_STATUS_PASS = 0;
    static final int RESULT_HEADER_LENGTH = 32;

    private static final int HEADER_MAGIC = 0;
    private static final int HEADER_CONTRACT_VERSION = 4;
    private static final int HEADER_CONTRACT_CRC32 = 8;
    private static final int HEADER_TEST_ID = 12;
    private static final int HEADER_WORKLOAD_UNITS = 16;
    private static final int HEADER_STATUS = 20;
    private static final int HEADER_PAYLOAD_LOW = 24;
    private static final int HEADER_PAYLOAD_HIGH = 28;

    private W4BenchRunner() {}

    /**
     * Arguments: candidate sample [timed|verify-only]. Candidate is an opaque receipt label and is intentionally not
     * used to select any interpreter behaviour.
     */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2 && arguments.length != 3) {
            throw new IllegalArgumentException("usage: candidate sample [timed|verify-only]");
        }
        final String candidate = requireToken(arguments[0], "candidate");
        int sample = Integer.parseInt(arguments[1]);
        if (sample < 0) {
            throw new IllegalArgumentException("sample must be non-negative");
        }
        boolean timerRequired = true;
        if (arguments.length == 3) {
            if ("verify-only".equals(arguments[2])) {
                timerRequired = false;
            } else if (!"timed".equals(arguments[2])) {
                throw new IllegalArgumentException("mode must be timed or verify-only");
            }
        }

        verifyProfileShape();
        byte[] cartridge = readResource(CARTRIDGE_RESOURCE);
        byte[] font = readResource("/w4font.bin");
        verifyOpcodeSweep(cartridge, font, candidate, sample);
        int[] actualCrcs = new int[W4BenchProfile.TEST_IDS.length];
        long[] medians = new long[W4BenchProfile.TEST_IDS.length];
        int testIndex;
        for (testIndex = 0; testIndex < W4BenchProfile.TEST_IDS.length; testIndex++) {
            Context context = createContext(cartridge, font);
            try {
                long[] samples = new long[W4BenchProfile.REPETITIONS];
                int canonicalCrc = 0;
                int iteration;
                for (iteration = 0; iteration < W4BenchProfile.WARMUPS + W4BenchProfile.REPETITIONS; iteration++) {
                    RunResult result = runOnce(context, testIndex);
                    if (iteration == 0) {
                        canonicalCrc = result.crc32;
                        if (testIndex == 0) {
                            verifyValidatorRejectsCorruption(context.module.memory(), testIndex, candidate, sample);
                        }
                    } else if (canonicalCrc != result.crc32) {
                        throw fail(testIndex, "non-deterministic-crc", hex8(canonicalCrc), hex8(result.crc32));
                    }
                    if (iteration >= W4BenchProfile.WARMUPS) {
                        int rep = iteration - W4BenchProfile.WARMUPS;
                        if (timerRequired && result.elapsedMs < W4BenchProfile.MIN_TIMED_MS) {
                            throw fail(
                                    testIndex,
                                    "timer-resolution",
                                    ">=" + W4BenchProfile.MIN_TIMED_MS,
                                    Long.toString(result.elapsedMs));
                        }
                        samples[rep] = result.elapsedMs;
                        System.out.println("w4bench:pass profile=" + W4BenchProfile.PROFILE_ID
                                + " profile-crc="
                                + hex8(W4BenchProfile.PROFILE_CRC32)
                                + " candidate=" + candidate
                                + " sample=" + sample
                                + " test-id=" + W4BenchProfile.TEST_IDS[testIndex]
                                + " test=" + W4BenchProfile.TEST_NAMES[testIndex]
                                + " rep=" + rep
                                + " wall-ms=" + result.elapsedMs
                                + " actual-crc=" + hex8(result.crc32));
                    } else {
                        System.out.println("w4bench:pass profile=" + W4BenchProfile.PROFILE_ID
                                + " profile-crc="
                                + hex8(W4BenchProfile.PROFILE_CRC32)
                                + " candidate=" + candidate
                                + " sample=" + sample
                                + " test-id=" + W4BenchProfile.TEST_IDS[testIndex]
                                + " test=" + W4BenchProfile.TEST_NAMES[testIndex]
                                + " phase=warmup"
                                + " wall-ms=" + result.elapsedMs
                                + " actual-crc=" + hex8(result.crc32));
                    }
                }
                actualCrcs[testIndex] = canonicalCrc;
                medians[testIndex] = median(samples);
                System.out.println("w4bench:pass profile=" + W4BenchProfile.PROFILE_ID
                        + " profile-crc=" + hex8(W4BenchProfile.PROFILE_CRC32)
                        + " candidate=" + candidate
                        + " sample=" + sample
                        + " test-id=" + W4BenchProfile.TEST_IDS[testIndex]
                        + " test=" + W4BenchProfile.TEST_NAMES[testIndex]
                        + " median-wall-ms=" + medians[testIndex]
                        + " actual-crc=" + hex8(canonicalCrc));
            } finally {
                context.close();
            }
        }
        int workCrc = workCrc32(actualCrcs);
        System.out.println("w4bench:pass profile=" + W4BenchProfile.PROFILE_ID
                + " profile-crc=" + hex8(W4BenchProfile.PROFILE_CRC32)
                + " contract-crc=" + hex8(W4BenchProfile.CONTRACT_CRC32)
                + " candidate=" + candidate
                + " sample=" + sample
                + " w4ir-format=" + WasmModule.W4IR_FORMAT_VERSION
                + " tests=" + actualCrcs.length
                + " samples=" + W4BenchProfile.REPETITIONS
                + " minimum-timed-ms=" + W4BenchProfile.MIN_TIMED_MS
                + " mode=" + (timerRequired ? "timed" : "verify-only")
                + " work-crc=" + hex8(workCrc));
    }

    private static Context createContext(byte[] cartridge, byte[] font) throws Exception {
        WasmModule module = WasmModule.read(cartridge, null, true, true);
        Wasm4Runtime runtime = new Wasm4Runtime(font);
        runtime.initialize(module);
        WasmInterpreter interpreter = new WasmInterpreter(module, runtime);
        configureProduction(interpreter);
        interpreter.invokeCartridgeLifecycle();
        return new Context(module, runtime, interpreter);
    }

    private static void verifyOpcodeSweep(byte[] cartridge, byte[] font, String candidate, int sample)
            throws Exception {
        Context context = createContext(cartridge, font);
        try {
            context.interpreter.invoke("validate_all");
            byte[] memory = context.module.memory();
            int offset = W4BenchProfile.RESULT_OFFSET;
            requireField(-1, "coverage-magic", RESULT_MAGIC, readU32Le(memory, offset));
            requireField(
                    -1,
                    "coverage-contract-version",
                    RESULT_CONTRACT_VERSION,
                    readU32Le(memory, offset + HEADER_CONTRACT_VERSION));
            requireField(
                    -1,
                    "coverage-contract-crc",
                    W4BenchProfile.CONTRACT_CRC32,
                    readU32Le(memory, offset + HEADER_CONTRACT_CRC32));
            requireField(-1, "coverage-test-id", 0x8000, readU32Le(memory, offset + HEADER_TEST_ID));
            requireField(-1, "coverage-workload", 0, readU32Le(memory, offset + HEADER_WORKLOAD_UNITS));
            requireField(-1, "coverage-status", RESULT_STATUS_PASS, readU32Le(memory, offset + HEADER_STATUS));
            requireField(
                    -1,
                    "coverage-test-id",
                    W4BenchProfile.VALIDATION_TEST_ID,
                    readU32Le(memory, offset + HEADER_TEST_ID));
            requireField(-1, "coverage-workload-units", 0, readU32Le(memory, offset + HEADER_WORKLOAD_UNITS));
            requireField(
                    -1,
                    "coverage-payload-low",
                    (int) W4BenchProfile.VALIDATION_PAYLOAD,
                    readU32Le(memory, offset + HEADER_PAYLOAD_LOW));
            requireField(
                    -1,
                    "coverage-payload-high",
                    (int) (W4BenchProfile.VALIDATION_PAYLOAD >>> 32),
                    readU32Le(memory, offset + HEADER_PAYLOAD_HIGH));
            requireField(
                    -1,
                    "coverage-result-crc",
                    W4BenchProfile.VALIDATION_EXPECTED_CRC32,
                    crc32(memory, offset, RESULT_HEADER_LENGTH));
            boolean trapped = false;
            try {
                context.interpreter.invoke("trap_unreachable");
            } catch (WasmTrap expected) {
                if (!"unreachable instruction executed".equals(expected.getMessage())) {
                    throw expected;
                }
                trapped = true;
            }
            if (!trapped) {
                throw new IllegalStateException("trap_unreachable returned normally");
            }
            System.out.println("w4bench:coverage profile=" + W4BenchProfile.PROFILE_ID
                    + " candidate=" + candidate
                    + " sample=" + sample
                    + " opcodes=190 expected-traps=1 semantic-sentinel=exact");
        } finally {
            context.close();
        }
    }

    /**
     * Reset, prepare, report, and validation are outside the timed interval. The interval includes the interpreter's
     * normal export invocation boundary around the selected {@code run_*} function.
     */
    private static RunResult runOnce(Context context, int testIndex) throws Exception {
        WasmInterpreter interpreter = context.interpreter;
        interpreter.invoke("reset");
        interpreter.invoke(W4BenchProfile.PREPARE_EXPORTS[testIndex]);
        long start = System.currentTimeMillis();
        interpreter.invoke(W4BenchProfile.RUN_EXPORTS[testIndex]);
        long elapsed = System.currentTimeMillis() - start;
        interpreter.invoke("report");
        int crc = validateResult(context.module.memory(), testIndex);
        return new RunResult(elapsed, crc);
    }

    private static void configureProduction(WasmInterpreter interpreter) {
        interpreter.setFastPathsEnabled(false);
        interpreter.setCompactExecutorEnabled(true);
        interpreter.setTraceExecutorEnabled(true);
        interpreter.setDirectNumericIntrinsicsEnabled(true);
        interpreter.setIntegerCompactOpcodesEnabled(true);
        interpreter.setNumericHostImportDispatchEnabled(true);
        interpreter.setInstructionLimit(W4BenchProfile.INSTRUCTION_LIMIT);
    }

    private static void verifyValidatorRejectsCorruption(byte[] memory, int testIndex, String candidate, int sample) {
        int corruptOffset = W4BenchProfile.RESULT_OFFSET + 24;
        memory[corruptOffset] ^= 1;
        boolean rejected = false;
        try {
            validateResult(memory, testIndex);
        } catch (IllegalStateException expected) {
            rejected = true;
        } finally {
            memory[corruptOffset] ^= 1;
        }
        if (!rejected) {
            throw new IllegalStateException("W4Bench validator accepted a corrupt result");
        }
        System.out.println("w4bench:validator-negative profile=" + W4BenchProfile.PROFILE_ID
                + " candidate=" + candidate
                + " sample=" + sample
                + " corrupt-result=rejected");
    }

    static int validateResult(byte[] memory, int testIndex) {
        int length = W4BenchProfile.RESULT_LENGTHS[testIndex];
        if (length < RESULT_HEADER_LENGTH
                || W4BenchProfile.RESULT_OFFSET < 0
                || W4BenchProfile.RESULT_OFFSET > memory.length - length) {
            throw fail(testIndex, "result-range", "valid result range", "invalid");
        }
        int offset = W4BenchProfile.RESULT_OFFSET;
        requireField(testIndex, "magic", RESULT_MAGIC, readU32Le(memory, offset + HEADER_MAGIC));
        requireField(
                testIndex,
                "contract-version",
                RESULT_CONTRACT_VERSION,
                readU32Le(memory, offset + HEADER_CONTRACT_VERSION));
        requireField(
                testIndex,
                "contract-crc",
                W4BenchProfile.CONTRACT_CRC32,
                readU32Le(memory, offset + HEADER_CONTRACT_CRC32));
        requireField(
                testIndex, "test-id", W4BenchProfile.TEST_IDS[testIndex], readU32Le(memory, offset + HEADER_TEST_ID));
        requireField(
                testIndex,
                "workload-units",
                W4BenchProfile.WORKLOAD_UNITS[testIndex],
                readU32Le(memory, offset + HEADER_WORKLOAD_UNITS));
        requireField(testIndex, "status", RESULT_STATUS_PASS, readU32Le(memory, offset + HEADER_STATUS));
        int actual = crc32(memory, offset, length);
        requireField(testIndex, "result-crc", W4BenchProfile.EXPECTED_CRC32[testIndex], actual);
        return actual;
    }

    static int workCrc32(int[] actualCrcs) {
        if (actualCrcs == null || actualCrcs.length != W4BenchProfile.TEST_IDS.length) {
            throw new IllegalArgumentException("actual CRC count differs from profile");
        }
        int crc = 0xffffffff;
        int index;
        for (index = 0; index < actualCrcs.length; index++) {
            crc = crc32Byte(crc, W4BenchProfile.TEST_IDS[index] & 0xff);
            crc = crc32Byte(crc, (W4BenchProfile.TEST_IDS[index] >>> 8) & 0xff);
            crc = crc32U32(crc, W4BenchProfile.WORKLOAD_UNITS[index]);
            crc = crc32U32(crc, actualCrcs[index]);
        }
        return ~crc;
    }

    static int crc32(byte[] data, int offset, int length) {
        if (data == null || offset < 0 || length < 0 || offset > data.length - length) {
            throw new IllegalArgumentException("invalid CRC32 range");
        }
        int crc = 0xffffffff;
        int index;
        for (index = 0; index < length; index++) {
            crc = crc32Byte(crc, data[offset + index] & 0xff);
        }
        return ~crc;
    }

    static int readU32Le(byte[] data, int offset) {
        if (data == null || offset < 0 || offset > data.length - 4) {
            throw new IllegalArgumentException("invalid u32 range");
        }
        return (data[offset] & 0xff)
                | ((data[offset + 1] & 0xff) << 8)
                | ((data[offset + 2] & 0xff) << 16)
                | ((data[offset + 3] & 0xff) << 24);
    }

    private static int crc32U32(int crc, int value) {
        crc = crc32Byte(crc, value & 0xff);
        crc = crc32Byte(crc, (value >>> 8) & 0xff);
        crc = crc32Byte(crc, (value >>> 16) & 0xff);
        return crc32Byte(crc, (value >>> 24) & 0xff);
    }

    private static int crc32Byte(int crc, int value) {
        crc ^= value;
        int bit;
        for (bit = 0; bit < 8; bit++) {
            crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1));
        }
        return crc;
    }

    private static long median(long[] values) {
        long[] copy = new long[values.length];
        System.arraycopy(values, 0, copy, 0, values.length);
        int left;
        for (left = 1; left < copy.length; left++) {
            long value = copy[left];
            int right = left - 1;
            while (right >= 0 && copy[right] > value) {
                copy[right + 1] = copy[right];
                right--;
            }
            copy[right + 1] = value;
        }
        return copy[copy.length / 2];
    }

    private static void verifyProfileShape() {
        if (!"FROZEN".equals(W4BenchProfile.PROFILE_STATE)) {
            throw new IllegalStateException("authoritative W4Bench timing requires a FROZEN profile");
        }
        int tests = W4BenchProfile.TEST_IDS.length;
        if (tests == 0
                || W4BenchProfile.TEST_NAMES.length != tests
                || W4BenchProfile.PREPARE_EXPORTS.length != tests
                || W4BenchProfile.RUN_EXPORTS.length != tests
                || W4BenchProfile.WORKLOAD_UNITS.length != tests
                || W4BenchProfile.RESULT_LENGTHS.length != tests
                || W4BenchProfile.EXPECTED_CRC32.length != tests) {
            throw new IllegalStateException("invalid W4Bench profile array shape");
        }
        if (W4BenchProfile.WARMUPS != 1 || W4BenchProfile.REPETITIONS != 9) {
            throw new IllegalStateException("W4Bench V1 requires 1 warmup and 9 repetitions");
        }
        if (W4BenchProfile.MIN_TIMED_MS <= 0) {
            throw new IllegalStateException("W4Bench timed interval must exceed timer resolution");
        }
        int index;
        for (index = 0; index < tests; index++) {
            if (W4BenchProfile.TEST_IDS[index] <= 0
                    || W4BenchProfile.WORKLOAD_UNITS[index] <= 0
                    || W4BenchProfile.RESULT_LENGTHS[index] != RESULT_HEADER_LENGTH
                    || W4BenchProfile.TEST_NAMES[index] == null
                    || W4BenchProfile.PREPARE_EXPORTS[index] == null
                    || W4BenchProfile.RUN_EXPORTS[index] == null) {
                throw new IllegalStateException("invalid W4Bench profile test at index " + index);
            }
        }
    }

    private static String requireToken(String value, String field) {
        if (value == null || value.length() == 0) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        int index;
        for (index = 0; index < value.length(); index++) {
            if (value.charAt(index) <= ' ') {
                throw new IllegalArgumentException(field + " must not contain whitespace");
            }
        }
        return value;
    }

    private static void requireField(int testIndex, String field, int expected, int actual) {
        if (expected != actual) {
            throw fail(testIndex, field, hex8(expected), hex8(actual));
        }
    }

    private static IllegalStateException fail(int testIndex, String field, String expected, String actual) {
        return new IllegalStateException("w4bench:fail profile=" + W4BenchProfile.PROFILE_ID
                + " test-index=" + testIndex
                + " field=" + field
                + " expected=" + expected
                + " actual=" + actual);
    }

    private static String hex8(int value) {
        String text = Integer.toHexString(value);
        StringBuffer result = new StringBuffer(8);
        int index;
        for (index = text.length(); index < 8; index++) {
            result.append('0');
        }
        result.append(text);
        return result.toString();
    }

    private static byte[] readResource(String path) throws Exception {
        InputStream input = W4BenchRunner.class.getResourceAsStream(path); // NOPMD -- Closed in the finally block.
        if (input == null) {
            throw new IllegalStateException("missing classpath resource: " + path);
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) > 0) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private static final class RunResult {
        final long elapsedMs;
        final int crc32;

        RunResult(long elapsedMs, int crc32) {
            this.elapsedMs = elapsedMs;
            this.crc32 = crc32;
        }
    }

    private static final class Context {
        final WasmModule module;
        final Wasm4Runtime runtime;
        final WasmInterpreter interpreter;

        Context(WasmModule module, Wasm4Runtime runtime, WasmInterpreter interpreter) {
            this.module = module;
            this.runtime = runtime;
            this.interpreter = interpreter;
        }

        void close() {
            runtime.close();
            module.close();
        }
    }
}
