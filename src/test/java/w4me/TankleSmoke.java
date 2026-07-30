package w4me;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import w4me.runtime.Wasm4Runtime;
import w4me.wasm.WasmInterpreter;
import w4me.wasm.WasmModule;

/** Provides the tankle smoke implementation. */
public final class TankleSmoke {
    private static final String IDLE_FRAME_SHA256 = "433d43259e023b589268b8ad369798095475dd8001afc8c76dff87cc16f9e100";
    private static final String START_FRAME_SHA256 = "cff64c241106b5951e21283f1d265c1c35e5233023e05d8d111008cd672b854b";

    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("usage: font.bin tankle.wasm");
        }
        WasmModule module = WasmModule.read(readFile(arguments[1]));
        Wasm4Runtime runtime = new Wasm4Runtime(readFile(arguments[0]));
        runtime.initialize(module);
        WasmInterpreter interpreter = new WasmInterpreter(module, runtime);
        interpreter.invokeCartridgeLifecycle();

        frame(module, runtime, interpreter, 0, 0);
        assertEquals("idle frame", IDLE_FRAME_SHA256, framebufferSha256(module));
        frame(module, runtime, interpreter, 1, 0);
        assertEquals("start frame", START_FRAME_SHA256, framebufferSha256(module));
        frame(module, runtime, interpreter, 0, 0);
        assertEquals("released start frame", START_FRAME_SHA256, framebufferSha256(module));

        runtime.close();
        System.out.println("PASS tankle official-oracle idle=" + IDLE_FRAME_SHA256 + " start=" + START_FRAME_SHA256);
    }

    private static void frame(
            WasmModule module, Wasm4Runtime runtime, WasmInterpreter interpreter, int gamepad1, int gamepad2)
            throws Exception {
        runtime.beginFrame(module, gamepad1, gamepad2, 0, 0, 0);
        interpreter.invoke("update");
        runtime.endFrame();
    }

    private static String framebufferSha256(WasmModule module) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(module.memory(), Wasm4Runtime.FRAMEBUFFER, Wasm4Runtime.FRAMEBUFFER_SIZE);
        return hex(digest.digest());
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

    private static void assertEquals(String label, String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
