package w4me;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import w4me.runtime.Wasm4Runtime;
import w4me.runtime.audio.AudioBackend;
import w4me.runtime.audio.Wasm4Apu;
import w4me.wasm.WasmInterpreter;
import w4me.wasm.WasmModule;

/** Provides the sound demo smoke implementation. */
public final class SoundDemoSmoke {
    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("usage: font.bin sound-demo.wasm");
        }
        byte[] font = readFile(arguments[0]);
        WasmModule module = WasmModule.read(readFile(arguments[1]));
        RecordingBackend backend = new RecordingBackend();
        Wasm4Apu apu = new Wasm4Apu(backend);
        Wasm4Runtime runtime = new Wasm4Runtime(font, apu);
        runtime.initialize(module);
        WasmInterpreter interpreter = new WasmInterpreter(module, runtime);
        interpreter.invokeCartridgeLifecycle();

        frame(module, runtime, interpreter, 0);
        frame(module, runtime, interpreter, 1);
        frame(module, runtime, interpreter, 0);

        assertEquals("tone event count", 1, apu.toneEventCount());
        assertEquals("raw frequency", 440, apu.lastFrequency());
        assertEquals("raw duration", 60, apu.lastDuration());
        assertEquals("raw volume", 25700, apu.lastVolume());
        assertEquals("raw flags", 0, apu.lastFlags());
        assertEquals("backend calls", 1, backend.calls);
        assertEquals("backend frequency", 440, backend.frequency);
        assertEquals("backend duration", 60, backend.duration);
        assertEquals("backend volume", 25700, backend.volume);
        assertEquals("backend flags", 0, backend.flags);
        testEnvelopeAndSlide();
        System.out.println("PASS sound-demo tone=440,60,25700,0 channels=4 envelope=ADSR slide=440-880");
    }

    private static void testEnvelopeAndSlide() {
        RecordingBackend backend = new RecordingBackend();
        Wasm4Apu apu = new Wasm4Apu(backend);
        int frequency = 440 | (880 << 16);
        int duration = (2 << 24) | (2 << 16) | (2 << 8) | 2;
        int volume = (100 << 8) | 80;
        apu.tone(frequency, duration, volume, 0);
        assertChannel(apu, 0, 440, 0);
        apu.tick();
        assertChannel(apu, 0, 495, 50);
        apu.tick();
        assertChannel(apu, 0, 550, 100);
        apu.tick();
        assertChannel(apu, 0, 605, 90);
        apu.tick();
        assertChannel(apu, 0, 660, 80);
        apu.tick();
        assertChannel(apu, 0, 715, 80);
        apu.tick();
        assertChannel(apu, 0, 770, 80);
        apu.tick();
        assertChannel(apu, 0, 825, 40);
        apu.tick();
        assertChannel(apu, 0, 880, 0);

        apu.tone(60, 1, 50, 1 | 0x40);
        assertChannel(apu, 1, 262, 50);
        assertEquals("independent channel 0", 880, apu.channelFrequency(0));
        assertEquals("backend packed calls", 2, backend.calls);
        assertEquals("backend packed frequency", 60, backend.frequency);
        assertEquals("backend packed duration", 1, backend.duration);
        assertEquals("backend packed volume", 50, backend.volume);
        assertEquals("backend packed flags", 1 | 0x40, backend.flags);

        apu.tone(330, 2, 70, 2);
        apu.tone(220, 3, 60, 3);
        assertEquals("independent channel 0 after four tones", 880, apu.channelFrequency(0));
        assertEquals("independent channel 1 after four tones", 262, apu.channelFrequency(1));
        assertEquals("independent channel 2", 330, apu.channelFrequency(2));
        assertEquals("independent channel 3", 220, apu.channelFrequency(3));
        assertEquals("four packed backend calls", 4, backend.calls);
        apu.close();
    }

    private static void assertChannel(Wasm4Apu apu, int channel, int expectedFrequency, int expectedVolume) {
        assertEquals("channel frequency", expectedFrequency, apu.channelFrequency(channel));
        assertEquals("channel volume", expectedVolume, apu.channelVolume(channel));
    }

    private static void frame(WasmModule module, Wasm4Runtime runtime, WasmInterpreter interpreter, int gamepad)
            throws Exception {
        runtime.beginFrame(module, gamepad, 0, 0, 0);
        interpreter.invoke("update");
        runtime.endFrame();
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
        private int frequency;
        private int duration;
        private int volume;
        private int flags;

        public void submitTone(int frequency, int duration, int volume, int flags) {
            calls++;
            this.frequency = frequency;
            this.duration = duration;
            this.volume = volume;
            this.flags = flags;
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
