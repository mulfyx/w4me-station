package w4me;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.security.MessageDigest;
import w4me.runtime.Wasm4Runtime;
import w4me.wasm.WasmInterpreter;
import w4me.wasm.WasmModule;

/** Provides the cartridge corpus smoke implementation. */
public final class CartridgeCorpusSmoke {
    private static final String DUCK_FINAL_FRAME = "6337e3f491e31a78714896d0232618fe5ad8c713239fc253eb5b4c985f72f91a";

    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 4) {
            throw new IllegalArgumentException("usage: font.bin duck.wasm plasma.wasm plasma-frames.csv");
        }
        byte[] font = readFile(arguments[0]);
        runDuck(font, readFile(arguments[1]));
        runPlasma(font, readFile(arguments[2]), readPlasmaFrames(arguments[3]));
    }

    private static void runDuck(byte[] font, byte[] cartridge) throws Exception {
        WasmModule module = WasmModule.read(cartridge);
        Wasm4Runtime runtime = new Wasm4Runtime(font);
        runtime.initialize(module);
        WasmInterpreter interpreter = new WasmInterpreter(module, runtime);
        interpreter.invokeCartridgeLifecycle();

        frame(module, runtime, interpreter, 0);
        frame(module, runtime, interpreter, 1);
        frame(module, runtime, interpreter, 0);
        frames(module, runtime, interpreter, 128, 32);
        frames(module, runtime, interpreter, 32, 24);
        frames(module, runtime, interpreter, 128, 16);
        frames(module, runtime, interpreter, 32, 32);
        frames(module, runtime, interpreter, 64, 48);

        assertEquals("duck level", 1, readI32(module.memory(), 17056));
        assertEquals("duck x", 1, readI32(module.memory(), 17040));
        assertEquals("duck y", 1, readI32(module.memory(), 17044));
        String frameHash = framebufferSha256(module);
        assertEquals("duck final framebuffer", DUCK_FINAL_FRAME, frameHash);
        System.out.println("PASS duck-maze level=1 framebuffer-sha256=" + frameHash);
    }

    private static void runPlasma(byte[] font, byte[] cartridge, String[] expectedFrames) throws Exception {
        WasmModule module = WasmModule.read(cartridge);
        Wasm4Runtime runtime = new Wasm4Runtime(font);
        runtime.initialize(module);
        WasmInterpreter interpreter = new WasmInterpreter(module, runtime);
        interpreter.invokeCartridgeLifecycle();

        int index;
        for (index = 0; index < expectedFrames.length; index++) {
            frame(module, runtime, interpreter, 0);
            String actual = framebufferSha256(module);
            assertEquals("plasma frame " + index, expectedFrames[index], actual);
        }
        System.out.println("PASS plasma-cube frames=" + expectedFrames.length);
    }

    private static String[] readPlasmaFrames(String path) throws Exception {
        BufferedReader input = new BufferedReader(new FileReader(path));
        try {
            String[] frames = new String[60];
            String line = input.readLine();
            int count = 0;
            while ((line = input.readLine()) != null) {
                int firstComma = line.indexOf(',');
                int lastComma = line.lastIndexOf(',');
                if (firstComma < 0 || lastComma <= firstComma || count >= frames.length) {
                    throw new IllegalArgumentException("invalid Plasma oracle row: " + line);
                }
                frames[count] = line.substring(firstComma + 1, lastComma);
                count++;
            }
            if (count != frames.length) {
                throw new IllegalArgumentException("expected 60 Plasma oracle frames, got " + count);
            }
            return frames;
        } finally {
            input.close();
        }
    }

    private static void frames(
            WasmModule module, Wasm4Runtime runtime, WasmInterpreter interpreter, int gamepad, int count)
            throws Exception {
        int index;
        for (index = 0; index < count; index++) {
            frame(module, runtime, interpreter, gamepad);
        }
    }

    private static void frame(WasmModule module, Wasm4Runtime runtime, WasmInterpreter interpreter, int gamepad)
            throws Exception {
        runtime.beginFrame(module, gamepad, 0, 0, 0);
        interpreter.invoke("update");
        runtime.endFrame();
    }

    private static String framebufferSha256(WasmModule module) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(module.memory(), Wasm4Runtime.FRAMEBUFFER, Wasm4Runtime.FRAMEBUFFER_SIZE);
        return hex(digest.digest());
    }

    private static int readI32(byte[] memory, int address) {
        return (memory[address] & 0xff)
                | ((memory[address + 1] & 0xff) << 8)
                | ((memory[address + 2] & 0xff) << 16)
                | (memory[address + 3] << 24);
    }

    private static void assertEquals(String label, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(String label, String expected, String actual) {
        if (!expected.equals(actual)) {
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

    private static String hex(byte[] bytes) {
        StringBuffer result = new StringBuffer(bytes.length * 2);
        int index;
        for (index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            if (value < 16) {
                result.append('0');
            }
            result.append(Integer.toHexString(value));
        }
        return result.toString();
    }
}
