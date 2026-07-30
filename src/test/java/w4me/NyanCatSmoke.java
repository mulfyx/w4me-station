package w4me;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import w4me.runtime.Wasm4Runtime;
import w4me.runtime.audio.AudioBackend;
import w4me.runtime.audio.Wasm4Apu;
import w4me.wasm.WasmInterpreter;
import w4me.wasm.WasmModule;

/** Exercises the bundled sustained-music cartridge without depending on MMAPI. */
public final class NyanCatSmoke {
    private static final int FRAME_COUNT = 180;
    private static final int EXPECTED_TONES = 48;
    private static final int EXPECTED_CHANNEL_MASK = 0x0f;
    private static final int EXPECTED_FRAMEBUFFER_FNV1A = 0x69daa7ed;

    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("usage: font.bin nyancat.wasm");
        }
        byte[] font = readFile(arguments[0]);
        WasmModule module = WasmModule.read(readFile(arguments[1]));
        RecordingBackend backend = new RecordingBackend();
        Wasm4Apu apu = new Wasm4Apu(backend);
        Wasm4Runtime runtime = new Wasm4Runtime(font, apu);
        runtime.initialize(module);
        WasmInterpreter interpreter = new WasmInterpreter(module, runtime);
        interpreter.invokeCartridgeLifecycle();

        int frame;
        for (frame = 0; frame < FRAME_COUNT; frame++) {
            runtime.beginFrame(module, 0, 0, 0, 0);
            interpreter.invoke("update");
            runtime.endFrame();
        }

        assertEquals("tone event count", EXPECTED_TONES, apu.toneEventCount());
        assertEquals("backend calls", EXPECTED_TONES, backend.calls);
        assertEquals("channel mask", EXPECTED_CHANNEL_MASK, backend.channelMask);
        assertEquals("framebuffer FNV-1a", EXPECTED_FRAMEBUFFER_FNV1A, FramebufferOracle.fnv1a(module));
        System.out.println("PASS nyancat frames="
                + FRAME_COUNT
                + " tones="
                + backend.calls
                + " channels=0x"
                + Integer.toHexString(backend.channelMask)
                + " framebuffer-fnv1a="
                + Integer.toHexString(FramebufferOracle.fnv1a(module)));
        apu.close();
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

    private static void assertEquals(String label, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    private static final class RecordingBackend implements AudioBackend {
        private int calls;
        private int channelMask;

        public void submitTone(int frequency, int duration, int volume, int flags) {
            calls++;
            channelMask |= 1 << (flags & 3);
        }

        public void tick() {
            /* Intentionally no-op. */
        }

        public void close() {
            /* Intentionally no-op. */
        }

        public String grade() {
            return "test";
        }
    }
}
