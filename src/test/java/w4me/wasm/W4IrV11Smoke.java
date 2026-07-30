package w4me.wasm;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;

/** Provides the W4IR v 11 smoke implementation. */
public final class W4IrV11Smoke {
    private static final int[] OPTIMIZED_OPCODES = {
        WasmModule.W4IR_LOCAL_TRIPLE_F32_STORE,
        WasmModule.W4IR_LOCAL4_F32_MUL_ADD_TEE,
        WasmModule.W4IR_F32_LOAD_NEG_INDEX_DIV,
        WasmModule.W4IR_TRIPLE_F32_STORE_LOCAL_LOAD_LOCAL,
        WasmModule.W4IR_LOCAL_TEE_MUL_ADD_SET_LOAD_MUL_ADD,
        WasmModule.W4IR_LOCAL_SET_DUAL_ADD_SET,
        WasmModule.W4IR_COUNTED_F32_TRACE
    };

    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("usage: plasma.wasm");
        }
        byte[] cartridge = readFile(arguments[0]);
        WasmModule optimized = WasmModule.read(cartridge, null, true);
        int[] counts = new int[OPTIMIZED_OPCODES.length];
        int index;
        for (index = 0; index < counts.length; index++) {
            counts[index] = countOpcode(optimized, OPTIMIZED_OPCODES[index]);
            if (counts[index] == 0) {
                throw new AssertionError(
                        "missing W4IR optimized opcode 0x" + Integer.toHexString(OPTIMIZED_OPCODES[index]));
            }
        }

        WasmModule baseline = WasmModule.read(cartridge, null, false);
        for (index = 0; index < OPTIMIZED_OPCODES.length; index++) {
            if (countOpcode(baseline, OPTIMIZED_OPCODES[index]) != 0) {
                throw new AssertionError(
                        "baseline contains optimized opcode 0x" + Integer.toHexString(OPTIMIZED_OPCODES[index]));
            }
        }
        System.out.println("PASS W4IR-v11 v9="
                + counts[0]
                + ","
                + counts[1]
                + ","
                + counts[2]
                + " v10="
                + counts[3]
                + ","
                + counts[4]
                + ","
                + counts[5]
                + " v11="
                + counts[6]
                + " baseline=clean");
    }

    private static int countOpcode(WasmModule module, int expectedOpcode) {
        int count = 0;
        int functionIndex;
        for (functionIndex = 0; functionIndex < module.functions.length; functionIndex++) {
            WasmModule.FunctionBody body = module.functions[functionIndex];
            if (body == null || body.code == null) {
                continue;
            }
            int pc;
            for (pc = 0; pc < body.instructionCount(); pc++) {
                int opcode = WasmModule.originalOpcode(body.code[pc * WasmModule.W4IR_STRIDE] & 0xffff);
                if (opcode == expectedOpcode) {
                    count++;
                }
            }
        }
        return count;
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
