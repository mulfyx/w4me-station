package w4me.wasm;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;

/** Keeps f32 constant cells identical across ordinary and fused execution. */
public final class F32ConstCellCanonicalizationSmoke {
    private static final long[] EXPECTED_GLOBALS = {0x00000000bf800000L, 0x00000000c0000000L, 0x00000000c0800000L};

    private F32ConstCellCanonicalizationSmoke() {}

    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("usage: w4ir-cache-metadata-recovery.wasm");
        }
        byte[] cartridge = readFile(arguments[0]);
        Result ordinary = run(cartridge, false);
        Result compact = run(cartridge, true);
        assertGlobals("ordinary", ordinary.globals);
        assertGlobals("compact", compact.globals);
        if (ordinary.fusedSites < 3 || compact.fusedSites != ordinary.fusedSites) {
            throw new AssertionError("focused f32 fusion coverage mismatch: ordinary="
                    + ordinary.fusedSites
                    + " compact="
                    + compact.fusedSites);
        }
        if (compact.compactInstructions == 0) {
            throw new AssertionError("focused f32 constants did not reach compact execution");
        }
        System.out.println("PASS f32-const-cell-canonicalization"
                + " fused-sites="
                + ordinary.fusedSites
                + " compact-instructions="
                + compact.compactInstructions
                + " globals=exact");
    }

    private static Result run(byte[] cartridge, boolean compact) throws Exception {
        WasmModule module = WasmModule.read(cartridge, null, true);
        try {
            final int fusedSites = countFusedSites(module);
            WasmInterpreter interpreter = new WasmInterpreter(module, null);
            interpreter.setCompactExecutorEnabled(compact);
            interpreter.setTraceExecutorEnabled(false);
            interpreter.setInstructionLimit(200000000L);
            interpreter.invoke("update");
            long[] globals = new long[module.globals.length];
            System.arraycopy(module.globals, 0, globals, 0, globals.length);
            return new Result(globals, fusedSites, interpreter.compactInstructionsExecuted());
        } finally {
            module.close();
        }
    }

    private static int countFusedSites(WasmModule module) {
        int count = 0;
        int function;
        for (function = 0; function < module.functions.length; function++) {
            WasmModule.FunctionBody body = module.functions[function];
            if (body == null || body.code == null) {
                continue;
            }
            int pc;
            for (pc = 0; pc < body.instructionCount(); pc++) {
                int opcode = WasmModule.originalOpcode(body.code[pc * WasmModule.W4IR_STRIDE] & 0xffff);
                if (opcode == WasmModule.W4IR_LOCAL_F32_CONST
                        || opcode == WasmModule.W4IR_LOCAL_SET_F32_CONST
                        || opcode == WasmModule.W4IR_LOCAL_SET_F32_CONST_SET) {
                    count++;
                }
            }
        }
        return count;
    }

    private static void assertGlobals(String label, long[] actual) {
        if (actual.length != EXPECTED_GLOBALS.length) {
            throw new AssertionError(
                    label + " global count expected=" + EXPECTED_GLOBALS.length + " actual=" + actual.length);
        }
        int index;
        for (index = 0; index < actual.length; index++) {
            if (actual[index] != EXPECTED_GLOBALS[index]) {
                throw new AssertionError(label
                        + " global "
                        + index
                        + " expected=0x"
                        + Long.toHexString(EXPECTED_GLOBALS[index])
                        + " actual=0x"
                        + Long.toHexString(actual[index]));
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

    private static final class Result {
        final long[] globals;
        final int fusedSites;
        final int compactInstructions;

        Result(long[] globals, int fusedSites, int compactInstructions) {
            this.globals = globals;
            this.fusedSites = fusedSites;
            this.compactInstructions = compactInstructions;
        }
    }
}
