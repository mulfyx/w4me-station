package w4me;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import w4me.runtime.Wasm4Runtime;
import w4me.runtime.audio.SilentAudioBackend;
import w4me.runtime.audio.Wasm4Apu;
import w4me.runtime.storage.MemoryDiskBackend;
import w4me.wasm.WasmModule;
import w4me.wasm.WasmTrap;

/** Provides the runtime abi smoke implementation. */
public final class RuntimeAbiSmoke {
    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("usage: font.bin cartridge.wasm");
        }
        WasmModule module = WasmModule.read(readFile(arguments[1]));
        MemoryDiskBackend disk = new MemoryDiskBackend();
        Wasm4Runtime runtime = new Wasm4Runtime(readFile(arguments[0]), new Wasm4Apu(new SilentAudioBackend()), disk);
        runtime.initialize(module);
        byte[] memory = module.memory();
        runtime.beginFrame(module, 0x41, 0x82, 123, 45, 1);
        assertEquals("gamepad 1", 0x41, memory[Wasm4Runtime.GAMEPAD1] & 0xff);
        assertEquals("gamepad 2", 0x82, memory[Wasm4Runtime.GAMEPAD2] & 0xff);
        assertEquals("gamepad 3", 0, memory[Wasm4Runtime.GAMEPAD3] & 0xff);
        assertEquals("gamepad 4", 0, memory[Wasm4Runtime.GAMEPAD4] & 0xff);
        int pointer = 30000;
        int index;
        for (index = 0; index < 1500; index++) {
            memory[pointer + index] = (byte) (index * 37);
        }
        assertEquals("disk write cap", 1024, invoke(runtime, module, "diskw", pointer, 1500));
        for (index = 0; index < 1500; index++) {
            memory[pointer + index] = 0;
        }
        assertEquals("disk read cap", 1024, invoke(runtime, module, "diskr", pointer, 1500));
        for (index = 0; index < 1024; index++) {
            assertEquals("disk byte " + index, (byte) (index * 37), memory[pointer + index]);
        }
        for (index = 0; index < 17; index++) {
            memory[pointer + index] = (byte) (255 - index);
        }
        assertEquals("replacement write", 17, invoke(runtime, module, "diskw", pointer, 17));
        for (index = 0; index < 1024; index++) {
            memory[pointer + index] = 0;
        }
        assertEquals("replacement read", 17, invoke(runtime, module, "diskr", pointer, 1024));
        for (index = 0; index < 17; index++) {
            assertEquals("replacement byte " + index, (byte) (255 - index), memory[pointer + index]);
        }
        verifyTracef(runtime, module);
        verifyHostGeometryBudget(runtime, module);
        runtime.close();
        System.out.println("PASS runtime gamepads=2 disk ABI cap=1024 replacement=17 tracef=exact host-budget=PASS");
    }

    private static void verifyHostGeometryBudget(Wasm4Runtime runtime, WasmModule module) {
        expectTrap(
                runtime,
                module,
                "line",
                new long[] {Integer.MIN_VALUE, 80, Integer.MAX_VALUE, 80},
                "line geometry exceeds runtime step limit");
        expectTrap(
                runtime,
                module,
                "oval",
                new long[] {0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE},
                "oval geometry exceeds runtime limit");
        runtime.invoke("env", "hline", new long[] {Integer.MAX_VALUE, 80, Integer.MAX_VALUE}, 0, 3, module);
        runtime.invoke("env", "blit", new long[] {30000, Integer.MIN_VALUE, 0, 1, 1, 0}, 0, 6, module);
    }

    private static void expectTrap(
            Wasm4Runtime runtime, WasmModule module, String name, long[] arguments, String message) {
        try {
            runtime.invoke("env", name, arguments, 0, arguments.length, module);
            throw new AssertionError(name + " accepted unbounded geometry");
        } catch (WasmTrap expected) {
            if (expected.getMessage().indexOf(message) < 0) {
                throw expected;
            }
        }
    }

    private static void verifyTracef(Wasm4Runtime runtime, WasmModule module) {
        byte[] memory = module.memory();
        int formatPointer = 32000;
        int argumentPointer = 32100;
        int stringPointer = 32200;
        writeAscii(memory, formatPointer, "char=%c dec=%d float=%f str=%s hex=%x %% %q");
        writeAscii(memory, stringPointer, "hello");
        writeI32(memory, argumentPointer, 'A');
        writeI32(memory, argumentPointer + 4, -42);
        writeI64(memory, argumentPointer + 8, Double.doubleToLongBits(1.5));
        writeI32(memory, argumentPointer + 16, stringPointer);
        writeI32(memory, argumentPointer + 20, 0x89abcdef);
        long[] traceArguments = {formatPointer, argumentPointer};
        runtime.invoke("env", "tracef", traceArguments, 0, 2, module);
        assertEquals("tracef output", "char=A dec=-42 float=1.5 str=hello hex=89abcdef % %q", runtime.lastTrace());

        memory[memory.length - 1] = '%';
        long[] invalidArguments = {memory.length - 1, argumentPointer};
        try {
            runtime.invoke("env", "tracef", invalidArguments, 0, 2, module);
            throw new AssertionError("tracef accepted an unterminated format string");
        } catch (WasmTrap expected) {
            if (expected.getMessage().indexOf("unterminated tracef") < 0) {
                throw expected;
            }
        }
    }

    private static int invoke(Wasm4Runtime runtime, WasmModule module, String name, int pointer, int size) {
        long[] values = {pointer, size};
        return (int) runtime.invoke("env", name, values, 0, 2, module);
    }

    private static void assertEquals(String label, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(String label, String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected '" + expected + "', got '" + actual + "'");
        }
    }

    private static void writeAscii(byte[] memory, int pointer, String value) {
        int index;
        for (index = 0; index < value.length(); index++) {
            memory[pointer + index] = (byte) value.charAt(index);
        }
        memory[pointer + value.length()] = 0;
    }

    private static void writeI32(byte[] memory, int pointer, int value) {
        memory[pointer] = (byte) value;
        memory[pointer + 1] = (byte) (value >>> 8);
        memory[pointer + 2] = (byte) (value >>> 16);
        memory[pointer + 3] = (byte) (value >>> 24);
    }

    private static void writeI64(byte[] memory, int pointer, long value) {
        writeI32(memory, pointer, (int) value);
        writeI32(memory, pointer + 4, (int) (value >>> 32));
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
