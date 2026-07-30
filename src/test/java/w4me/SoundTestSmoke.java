package w4me;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import w4me.runtime.Wasm4Runtime;
import w4me.wasm.WasmInterpreter;
import w4me.wasm.WasmModule;

/** Provides the sound test smoke implementation. */
public final class SoundTestSmoke {
    private static final String EXPECTED_FRAMEBUFFER =
            "a5b0708798680b369af307c6c4b3f63f275f87943339a9fdb2a86c84afe4b22f";

    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("usage: font.bin sound-test.wasm");
        }
        WasmModule module = WasmModule.read(readFile(arguments[1]));
        Wasm4Runtime runtime = new Wasm4Runtime(readFile(arguments[0]));
        runtime.initialize(module);
        WasmInterpreter interpreter = new WasmInterpreter(module, runtime);
        interpreter.setInstructionLimit(200000000L);
        interpreter.invokeCartridgeLifecycle();
        runtime.beginFrame(module, 0, 0, 0, 0);
        interpreter.invoke("update");
        runtime.endFrame();
        String actual = framebufferSha256(module);
        if (!EXPECTED_FRAMEBUFFER.equals(actual)) {
            throw new AssertionError("sound-test framebuffer: expected " + EXPECTED_FRAMEBUFFER + ", got " + actual);
        }
        System.out.println("PASS sound-test framebuffer-sha256=" + actual);
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
}
