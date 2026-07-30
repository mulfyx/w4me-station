package w4me;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import w4me.runtime.Wasm4Runtime;
import w4me.wasm.WasmInterpreter;
import w4me.wasm.WasmModule;
import w4me.wasm.WasmTrap;

/** Provides the WASM w 4 extensions smoke implementation. */
public final class WasmW4ExtensionsSmoke {
    private WasmW4ExtensionsSmoke() {}

    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 3) {
            throw new IllegalArgumentException("usage: font.bin extensions.wasm indirect-equivalent-types.wasm");
        }
        WasmModule module = WasmModule.read(readFile(arguments[1]));
        Wasm4Runtime runtime = new Wasm4Runtime(readFile(arguments[0]));
        runtime.initialize(module);
        WasmInterpreter interpreter = new WasmInterpreter(module, runtime);
        interpreter.invoke("update");

        byte[] memory = module.memory();
        assertEquals("passive byte 0", 'W', memory[20000] & 0xff);
        assertEquals("passive byte 1", '4', memory[20001] & 0xff);
        assertEquals("passive byte 2", 'M', memory[20002] & 0xff);
        assertEquals("passive byte 3", 'E', memory[20003] & 0xff);
        assertEquals("nan to signed i32", 0, readI32(memory, 20004));
        assertEquals("negative saturation to i32", Integer.MIN_VALUE, readI32(memory, 20008));
        assertEquals("positive saturation to unsigned i32", -1, readI32(memory, 20012));
        assertEquals("negative saturation to i64", Long.MIN_VALUE, readI64(memory, 20016));
        assertEquals("positive saturation to unsigned i64", -1L, readI64(memory, 20024));
        assertEquals("fixed memory grow by zero", 1, readI32(memory, 20032));

        try {
            interpreter.invoke("update");
            throw new AssertionError("memory.init accepted a dropped data segment");
        } catch (WasmTrap expected) {
            if (expected.getMessage().indexOf("passive data range") < 0) {
                throw expected;
            }
        }
        assertEquals("zero-length memory.init after data.drop", 1, readI32(memory, 20036));
        module.close();
        runtime.close();

        WasmModule indirectModule = WasmModule.read(readFile(arguments[2]));
        Wasm4Runtime indirectRuntime = new Wasm4Runtime(readFile(arguments[0]));
        indirectRuntime.initialize(indirectModule);
        WasmInterpreter indirectInterpreter = new WasmInterpreter(indirectModule, indirectRuntime);
        indirectInterpreter.invoke("update");
        assertEquals("structurally equivalent call_indirect type", 123456789, readI32(indirectModule.memory(), 20040));
        try {
            indirectInterpreter.invoke("mismatch");
            throw new AssertionError("call_indirect accepted a near-miss result type");
        } catch (WasmTrap expected) {
            if (expected.getMessage().indexOf("indirect call type mismatch") < 0) {
                throw expected;
            }
        }
        indirectModule.close();
        indirectRuntime.close();
        System.out.println("PASS WASM-W4 trunc_sat=8 passive-data=memory.init/data.drop"
                + " indirect-types=structural memory.grow(0)=1");
    }

    private static int readI32(byte[] memory, int address) {
        return (memory[address] & 0xff)
                | ((memory[address + 1] & 0xff) << 8)
                | ((memory[address + 2] & 0xff) << 16)
                | (memory[address + 3] << 24);
    }

    private static long readI64(byte[] memory, int address) {
        long low = readI32(memory, address) & 0xffffffffL;
        long high = readI32(memory, address + 4) & 0xffffffffL;
        return low | (high << 32);
    }

    private static void assertEquals(String label, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(String label, long expected, long actual) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
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
