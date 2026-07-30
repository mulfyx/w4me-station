package w4me.wasm;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/** Exact defined-call argument, local-frame reuse, recursion, and budget coverage. */
public final class DefinedCallArgumentCopySmoke {
    private static final WasmHost NO_HOST = new WasmHost() {
        public long invoke(int importId, long[] valueStack, int argumentBase, int argumentCount, WasmModule module) {
            throw new AssertionError("unexpected numeric host call");
        }

        public long invoke(
                String moduleName,
                String name,
                long[] valueStack,
                int argumentBase,
                int argumentCount,
                WasmModule module) {
            throw new AssertionError("unexpected string host call");
        }
    };

    private DefinedCallArgumentCopySmoke() {}

    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("usage: DefinedCallArgumentCopySmoke <module.wasm>");
        }
        byte[] cartridge = readFile(new File(arguments[0]));
        long logical = verifySuccessfulCalls(cartridge);
        verifyBudgetBoundary(cartridge, logical);
        System.out.println("PASS defined-call-arguments"
                + " arities=0,1,2,5"
                + " raw-types=i32,i64,f32,f64"
                + " frame-reuse=sequential,recursive"
                + " logical="
                + logical
                + " budget=exact");
    }

    private static long verifySuccessfulCalls(byte[] cartridge) throws Exception {
        WasmModule module = WasmModule.read(cartridge);
        try {
            WasmInterpreter interpreter = new WasmInterpreter(module, NO_HOST);
            interpreter.invoke("update");
            long logical = interpreter.instructionsExecuted();
            verifyMemory(module.memory);
            interpreter.invoke("update");
            assertEquals(logical, interpreter.instructionsExecuted(), "repeat logical");
            verifyMemory(module.memory);
            return logical;
        } finally {
            module.close();
        }
    }

    private static void verifyBudgetBoundary(byte[] cartridge, long logical) throws Exception {
        WasmModule allowedModule = WasmModule.read(cartridge);
        try {
            WasmInterpreter allowed = new WasmInterpreter(allowedModule, NO_HOST);
            allowed.setInstructionLimit(logical);
            allowed.invoke("update");
            assertEquals(logical, allowed.instructionsExecuted(), "allowed logical");
            verifyMemory(allowedModule.memory);
        } finally {
            allowedModule.close();
        }

        WasmModule deniedModule = WasmModule.read(cartridge);
        try {
            WasmInterpreter denied = new WasmInterpreter(deniedModule, NO_HOST);
            denied.setInstructionLimit(logical - 1);
            try {
                denied.invoke("update");
                throw new AssertionError("instruction limit accepted logical - 1");
            } catch (WasmTrap expected) {
                if (!"instruction budget exhausted".equals(expected.getMessage())) {
                    throw new AssertionError( // NOPMD -- CLDC 1.1 has no cause chaining.
                            "wrong instruction-budget trap: " + expected);
                }
                assertEquals(logical, denied.instructionsExecuted(), "denied logical");
            }
        } finally {
            deniedModule.close();
        }
    }

    private static void verifyMemory(byte[] memory) {
        assertEquals(0L, readI64(memory, 0), "zero first");
        assertEquals(0L, readI64(memory, 8), "zero reused");
        assertEquals(0x8877665544332211L, readI64(memory, 16), "one i64");
        assertEquals((0x89abcdefL << 32) ^ 0x0123456789abcdefL, readI64(memory, 24), "two arguments");

        assertEquals(0x80000001, readI32(memory, 64), "many first i32");
        assertEquals(0x8877665544332211L, readI64(memory, 72), "many first i64");
        assertEquals(0x7fc12345, readI32(memory, 80), "many first f32");
        assertEquals(0xfff8123456789abcL, readI64(memory, 88), "many first f64");
        assertEquals(0L, readI64(memory, 96), "many first local");

        assertEquals(0x7ffffffe, readI32(memory, 112), "many reused i32");
        assertEquals(0x0123456789abcdefL, readI64(memory, 120), "many reused i64");
        assertEquals(0x80000000, readI32(memory, 128), "many reused f32");
        assertEquals(1L, readI64(memory, 136), "many reused f64");
        assertEquals(0L, readI64(memory, 144), "many reused local");

        assertEquals(0x1011121314151617L + 3L, readI64(memory, 160), "recursive first");
        assertEquals(0x0f0e0d0c0b0a0908L + 4L, readI64(memory, 168), "recursive reused");
    }

    private static int readI32(byte[] memory, int address) {
        return (memory[address] & 0xff)
                | ((memory[address + 1] & 0xff) << 8)
                | ((memory[address + 2] & 0xff) << 16)
                | (memory[address + 3] << 24);
    }

    private static long readI64(byte[] memory, int address) {
        return ((long) memory[address] & 0xffL)
                | (((long) memory[address + 1] & 0xffL) << 8)
                | (((long) memory[address + 2] & 0xffL) << 16)
                | (((long) memory[address + 3] & 0xffL) << 24)
                | (((long) memory[address + 4] & 0xffL) << 32)
                | (((long) memory[address + 5] & 0xffL) << 40)
                | (((long) memory[address + 6] & 0xffL) << 48)
                | (((long) memory[address + 7] & 0xffL) << 56);
    }

    private static void assertEquals(long expected, long actual, String label) {
        if (expected != actual) {
            throw new AssertionError(
                    label + ": expected 0x" + Long.toHexString(expected) + ", got 0x" + Long.toHexString(actual));
        }
    }

    private static byte[] readFile(File file) throws IOException {
        FileInputStream input = new FileInputStream(file);
        try {
            byte[] result = new byte[(int) file.length()];
            int offset = 0;
            while (offset < result.length) {
                int count = input.read(result, offset, result.length - offset);
                if (count < 0) {
                    throw new IOException("unexpected end of file");
                }
                offset += count;
            }
            return result;
        } finally {
            input.close();
        }
    }
}
