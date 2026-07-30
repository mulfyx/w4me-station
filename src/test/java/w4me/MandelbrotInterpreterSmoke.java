package w4me;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import w4me.runtime.Wasm4Runtime;
import w4me.wasm.WasmInterpreter;
import w4me.wasm.WasmModule;

/** Provides the mandelbrot interpreter smoke implementation. */
public final class MandelbrotInterpreterSmoke {
    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 3) {
            throw new IllegalArgumentException("usage: input.wasm wasm4-font.bin output-framebuffer.bin");
        }
        byte[] cartridge = readFile(arguments[0]);
        byte[] font = readFile(arguments[1]);
        WasmModule module = WasmModule.read(cartridge);
        Wasm4Runtime runtime = new Wasm4Runtime(font);
        runtime.initialize(module);
        WasmInterpreter interpreter = new WasmInterpreter(module, runtime);
        interpreter.invokeStartIfPresent();
        runtime.beginFrame(module, 0, 0, 0, 0);
        interpreter.invoke("update");

        FileOutputStream output = new FileOutputStream(arguments[2]);
        try {
            output.write(module.memory(), 160, 6400);
        } finally {
            output.close();
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(module.memory(), 160, 6400);
        System.out.println("functions=" + module.functionCount());
        System.out.println("instructions=" + interpreter.instructionsExecuted());
        System.out.println("framebuffer-fnv1a=" + Integer.toHexString(FramebufferOracle.fnv1a(module)));
        System.out.println("framebuffer-sha256=" + hex(digest.digest()));
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
