package w4me.wasm;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import w4me.runtime.Wasm4Runtime;

/**
 * Executes every source opcode exercised by the W4Bench cartridge.
 *
 * <p>The cartridge exports small deterministic groups instead of running the whole instruction set through one large
 * function. Profiling is reset at every invocation, so this smoke accumulates a bitmap after each group.
 */
public final class W4BenchOpcodeCoverageSmoke {
    private static final int PROFILE_LIMIT = 0x10000;
    private static final int W4IR_FIRST = 0x1000;
    private static final int W4IR_LAST = 0x1032;
    private static final int REQUIRED_OPCODE_COUNT = 190;
    private static final String[] COVERAGE_EXPORTS = {
        "cover_control",
        "cover_memory",
        "cover_i32",
        "cover_i64",
        "cover_f32",
        "cover_f64",
        "cover_convert",
        "cover_bulk"
    };

    private W4BenchOpcodeCoverageSmoke() {}

    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("usage: font.bin w4bench.wasm");
        }

        WasmModule module = WasmModule.read(readFile(arguments[1]), null, false, false);
        Wasm4Runtime runtime = new Wasm4Runtime(readFile(arguments[0]));
        try {
            runtime.initialize(module);
            WasmInterpreter interpreter = new WasmInterpreter(module, runtime);
            interpreter.setFastPathsEnabled(false);
            interpreter.setCompactExecutorEnabled(false);
            interpreter.setTraceExecutorEnabled(false);
            interpreter.setDirectNumericIntrinsicsEnabled(false);
            interpreter.setIntegerCompactOpcodesEnabled(false);
            interpreter.setProfilingEnabled(true);

            boolean[] observed = new boolean[PROFILE_LIMIT];
            interpreter.invokeCartridgeLifecycle();
            collect(interpreter, observed);

            int exportIndex;
            for (exportIndex = 0; exportIndex < COVERAGE_EXPORTS.length; exportIndex++) {
                interpreter.invoke(COVERAGE_EXPORTS[exportIndex]);
                collect(interpreter, observed);
            }

            boolean trapped = false;
            try {
                interpreter.invoke("trap_unreachable");
            } catch (WasmTrap expected) {
                if (!"unreachable instruction executed".equals(expected.getMessage())) {
                    throw new AssertionError( // NOPMD -- CLDC 1.1 has no cause chaining.
                            "unexpected trap: " + expected.getMessage());
                }
                trapped = true;
            }
            if (!trapped) {
                throw new AssertionError("trap_unreachable returned normally");
            }
            collect(interpreter, observed);

            int covered = requireExactCoverage(observed);
            assertNoInternalW4irOpcodes(observed);
            System.out.println("PASS opcodes=" + covered + " trap=exact fusion=none");
        } finally {
            runtime.close();
            module.close();
        }
    }

    private static void collect(WasmInterpreter interpreter, boolean[] observed) {
        collectRange(interpreter, observed, 0x00, 0x05);
        collectRange(interpreter, observed, 0x0b, 0x11);
        collectRange(interpreter, observed, 0x1a, 0x1c);
        collectRange(interpreter, observed, 0x20, 0x24);
        collectRange(interpreter, observed, 0x28, 0xc4);
        collectRange(interpreter, observed, 0xfc00, 0xfc0b);
        collectRange(interpreter, observed, W4IR_FIRST, W4IR_LAST);
    }

    private static void collectRange(WasmInterpreter interpreter, boolean[] observed, int first, int last) {
        int opcode;
        for (opcode = first; opcode <= last; opcode++) {
            if (wasExecuted(interpreter, opcode)) {
                observed[opcode] = true;
            }
        }
    }

    private static boolean wasExecuted(WasmInterpreter interpreter, int originalOpcode) {
        if (interpreter.opcodeCount(originalOpcode) != 0) {
            return true;
        }
        try {
            int executionOpcode = WasmModule.executionOpcode(originalOpcode);
            return executionOpcode != originalOpcode && interpreter.opcodeCount(executionOpcode) != 0;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static int requireExactCoverage(boolean[] observed) {
        int covered = 0;
        covered += requireRange(observed, 0x00, 0x05);
        covered += requireRange(observed, 0x0b, 0x11);
        covered += requireRange(observed, 0x1a, 0x1c);
        covered += requireRange(observed, 0x20, 0x24);
        covered += requireRange(observed, 0x28, 0xc4);
        covered += requireRange(observed, 0xfc00, 0xfc0b);
        if (covered != REQUIRED_OPCODE_COUNT) {
            throw new AssertionError(
                    "required opcode set changed: expected " + REQUIRED_OPCODE_COUNT + ", got " + covered);
        }
        return covered;
    }

    private static int requireRange(boolean[] observed, int first, int last) {
        int opcode;
        for (opcode = first; opcode <= last; opcode++) {
            if (!observed[opcode]) {
                throw new AssertionError("W4Bench did not execute source opcode 0x" + Integer.toHexString(opcode));
            }
        }
        return last - first + 1;
    }

    private static void assertNoInternalW4irOpcodes(boolean[] observed) {
        int opcode;
        for (opcode = W4IR_FIRST; opcode <= W4IR_LAST; opcode++) {
            if (observed[opcode]) {
                throw new AssertionError("fusion leaked internal W4IR opcode 0x" + Integer.toHexString(opcode));
            }
        }
    }

    private static byte[] readFile(String path) throws Exception {
        InputStream input = new FileInputStream(path);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }
}
