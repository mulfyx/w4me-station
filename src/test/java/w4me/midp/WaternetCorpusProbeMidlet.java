package w4me.midp;

import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Form;
import javax.microedition.midlet.MIDlet;
import w4me.FramebufferOracle;
import w4me.runtime.Wasm4Runtime;
import w4me.runtime.audio.AudioBackend;
import w4me.runtime.audio.Wasm4Apu;
import w4me.runtime.storage.DiskBackend;
import w4me.wasm.WasmInterpreter;
import w4me.wasm.WasmModule;

/** Provides the waternet corpus probe midlet implementation. */
public final class WaternetCorpusProbeMidlet extends MIDlet {
    private static final int[] CHECKPOINT_FRAMES = {0, 1, 11, 12, 23, 24, 35, 36, 41, 42, 47, 48, 59, 60, 81, 82, 93};
    private static final int[] CHECKPOINT_FNV1A = {
        0x188fd725, 0x188fd725, 0x6cb32fd6, 0x6cb32fd6,
        0x90fe245f, 0x90fe245f, 0xf5a48f21, 0xf5a48f21,
        0xfd67eb51, 0xfd67eb51, 0xdec129c9, 0xdec129c9,
        0xce5110d8, 0xce5110d8, 0xcd6585bb, 0xcefe0907,
        0x14e0f616
    };
    private static final int[] CHECKPOINT_PALETTE = {
        0x00004385, 0x004485cf, 0x007dbbff, 0x00ffffff,
        0x00004385, 0x004485cf, 0x007dbbff, 0x00ffffff,
        0x00004385, 0x004485cf, 0x007dbbff, 0x00ffffff,
        0x00004385, 0x004485cf, 0x007dbbff, 0x00ffffff,
        0x00004385, 0x004485cf, 0x007dbbff, 0x00ffffff,
        0x00004385, 0x004485cf, 0x007dbbff, 0x00ffffff,
        0x00004385, 0x004485cf, 0x007dbbff, 0x00ffffff,
        0x00004385, 0x004485cf, 0x007dbbff, 0x00ffffff,
        0x00004385, 0x004485cf, 0x007dbbff, 0x00ffffff,
        0x00004385, 0x004485cf, 0x007dbbff, 0x00ffffff,
        0x00004385, 0x004485cf, 0x007dbbff, 0x00ffffff,
        0x00004385, 0x004485cf, 0x007dbbff, 0x00ffffff,
        0x00004385, 0x007dbbff, 0x004485cf, 0x00ffffff,
        0x00004385, 0x007dbbff, 0x004485cf, 0x00ffffff,
        0x00004385, 0x007dbbff, 0x004485cf, 0x00ffffff,
        0x00004385, 0x007dbbff, 0x004485cf, 0x00ffffff,
        0x00004385, 0x007dbbff, 0x004485cf, 0x00ffffff
    };
    private static final int[] TONE_FRAMES = {3, 3, 13, 19, 25, 37, 43, 49, 50, 61, 63, 78, 83, 93};
    private static final int[] TONE_FREQUENCIES = {262, 0, 900, 262, 900, 1250, 1250, 900, 294, 900, 0, 262, 600, 277};
    private static final int[] TONE_DURATIONS = {50, 0, 8, 50, 8, 8, 8, 8, 50, 8, 50, 50, 8, 50};
    private static final int[] TONE_VOLUMES = {75, 100, 100, 75, 100, 100, 100, 100, 75, 100, 75, 75, 100, 75};
    private static final int[] TONE_FLAGS = {1, 65, 0, 1, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1};

    private boolean started;

    /** Performs the start app operation. */
    protected void startApp() {
        if (started) {
            return;
        }
        started = true;
        Form result = new Form("Waternet corpus probe");
        Display.getDisplay(this).setCurrent(result);
        Wasm4Runtime runtime = null;
        WasmModule module = null;
        try {
            byte[] cartridge = ResourceLoader.read("/cartridges/waternet.wasm");
            if (cartridge.length != 29917) {
                throw new IllegalStateException("Waternet length mismatch: " + cartridge.length);
            }
            module = WasmModule.read(cartridge);
            RecordingBackend audio = new RecordingBackend();
            Wasm4Apu apu = new Wasm4Apu(audio);
            RecordingDisk disk = new RecordingDisk();
            runtime = new Wasm4Runtime(ResourceLoader.read("/w4font.bin"), apu, disk);
            runtime.initialize(module);
            WasmInterpreter interpreter = new WasmInterpreter(module, runtime);
            interpreter.invokeCartridgeLifecycle();

            int checkpoint = 0;
            int frame;
            for (frame = 0; frame < 94; frame++) {
                audio.frame = frame;
                runtime.beginFrame(module, gamepad(frame), 0x7fff, 0x7fff, 0);
                interpreter.invoke("update");
                runtime.endFrame();
                if (checkpoint < CHECKPOINT_FRAMES.length && CHECKPOINT_FRAMES[checkpoint] == frame) {
                    int actual = FramebufferOracle.fnv1a(module);
                    requireEquals("framebuffer at frame " + frame, CHECKPOINT_FNV1A[checkpoint], actual);
                    checkPalette(module.memory(), checkpoint, frame);
                    System.out.println("W4ME_WATERNET_FRAME frame="
                            + frame
                            + " gamepad="
                            + gamepad(frame)
                            + " framebuffer-fnv1a="
                            + hex8(actual));
                    checkpoint++;
                }
            }

            requireEquals("checkpoint count", CHECKPOINT_FRAMES.length, checkpoint);
            requireEquals("APU tone count", TONE_FRAMES.length, apu.toneEventCount());
            audio.check();
            requireEquals("disk reads", 1, disk.readCalls);
            requireEquals("disk read offset", 32072, disk.lastReadOffset);
            requireEquals("disk read size", 16, disk.lastReadSize);
            requireEquals("disk read result", 0, disk.lastReadResult);
            requireEquals("disk writes", 0, disk.writeCalls);
            requireEquals("disk bytes", 0, disk.length);

            System.out.println("W4ME_WATERNET_PROBE frames=94 checkpoints=17 tones=14"
                    + " disk-read=16/0 disk-bytes=0"
                    + " framebuffer-fnv1a=14e0f616");
            result.append("PASS\n94 frames\n17 exact checkpoints\n14 tone events");
        } catch (Throwable failure) { // NOPMD -- Java ME API linkage fallback.
            System.out.println("W4ME_WATERNET_ERROR " + failure.toString());
            failure.printStackTrace();
            result.append("FAIL\n" + failure.toString());
        } finally {
            if (runtime != null) {
                runtime.close();
            }
            if (module != null) {
                module.close();
            }
        }
    }

    /** Performs the pause app operation. */
    protected void pauseApp() {
        /* Intentionally no-op. */
    }

    /** Performs the destroy app operation. */
    protected void destroyApp(boolean unconditional) {
        /* Intentionally no-op. */
    }

    private int gamepad(int frame) {
        if (frame == 0 || frame == 12 || frame == 24 || frame == 48 || frame == 60 || frame == 82) {
            return 1;
        }
        if (frame == 36 || frame == 42) {
            return 64;
        }
        return 0;
    }

    private void checkPalette(byte[] memory, int checkpoint, int frame) {
        int color;
        for (color = 0; color < 4; color++) {
            requireEquals(
                    "palette " + color + " at frame " + frame,
                    CHECKPOINT_PALETTE[checkpoint * 4 + color],
                    readI32(memory, Wasm4Runtime.PALETTE + color * 4));
        }
    }

    private static int readI32(byte[] memory, int address) {
        return (memory[address] & 0xff)
                | ((memory[address + 1] & 0xff) << 8)
                | ((memory[address + 2] & 0xff) << 16)
                | (memory[address + 3] << 24);
    }

    private static void requireEquals(String label, int expected, int actual) {
        if (expected != actual) {
            throw new IllegalStateException(label + ": expected " + hex8(expected) + ", got " + hex8(actual));
        }
    }

    private static String hex8(int value) {
        String text = Integer.toHexString(value);
        StringBuffer result = new StringBuffer(8);
        int index;
        for (index = text.length(); index < 8; index++) {
            result.append('0');
        }
        result.append(text);
        return result.toString();
    }

    private static final class RecordingBackend implements AudioBackend {
        private final int[] frames = new int[32];
        private final int[] frequencies = new int[32];
        private final int[] durations = new int[32];
        private final int[] volumes = new int[32];
        private final int[] flags = new int[32];
        private int frame = -1;
        private int calls;

        public void submitTone(int frequency, int duration, int volume, int flags) {
            if (calls >= frames.length) {
                throw new IllegalStateException("too many tone events");
            }
            frames[calls] = frame;
            frequencies[calls] = frequency;
            durations[calls] = duration;
            volumes[calls] = volume;
            this.flags[calls] = flags;
            calls++;
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

        private void check() {
            requireEquals("tone count", TONE_FRAMES.length, calls);
            int index;
            for (index = 0; index < calls; index++) {
                requireEquals("tone frame " + index, TONE_FRAMES[index], frames[index]);
                requireEquals("tone frequency " + index, TONE_FREQUENCIES[index], frequencies[index]);
                requireEquals("tone duration " + index, TONE_DURATIONS[index], durations[index]);
                requireEquals("tone volume " + index, TONE_VOLUMES[index], volumes[index]);
                requireEquals("tone flags " + index, TONE_FLAGS[index], flags[index]);
            }
        }
    }

    private static final class RecordingDisk implements DiskBackend {
        private final byte[] data = new byte[1024];
        private int length;
        private int readCalls;
        private int writeCalls;
        private int lastReadOffset;
        private int lastReadSize;
        private int lastReadResult;

        public int read(byte[] target, int offset, int size) {
            readCalls++;
            int count = size < length ? size : length;
            System.arraycopy(data, 0, target, offset, count);
            lastReadOffset = offset;
            lastReadSize = size;
            lastReadResult = count;
            return count;
        }

        public int write(byte[] source, int offset, int size) {
            writeCalls++;
            int count = size < data.length ? size : data.length;
            System.arraycopy(source, offset, data, 0, count);
            length = count;
            return count;
        }

        public void close() {
            /* Intentionally no-op. */
        }

        public String grade() {
            return "test";
        }
    }
}
