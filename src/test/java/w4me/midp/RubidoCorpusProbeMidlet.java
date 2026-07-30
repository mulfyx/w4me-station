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

/** Provides the rubido corpus probe midlet implementation. */
public final class RubidoCorpusProbeMidlet extends MIDlet {
    private static final int[] CHECKPOINT_FRAMES = {
        0, 1, 2, 3, 4, 5, 12, 13, 14, 20, 21, 22, 23, 24, 25,
        26, 27, 35, 36, 37, 38, 45, 46, 47, 48, 55, 56, 57, 58, 69
    };
    private static final int[] CHECKPOINT_FNV1A = {
        0x2e4a87e9, 0x2e4a87e9, 0xe6353b91, 0xe6353b91, 0xe6353b91,
        0x727388a6, 0x727388a6, 0x727388a6, 0xf08e1199, 0xf08e1199,
        0xf08e1199, 0xf08e1199, 0xe6353b91, 0x2e4a87e9, 0x2e4a87e9,
        0x2e4a87e9, 0x15b2a17d, 0x15b2a17d, 0x15b2a17d, 0x15b2a17d,
        0x807cc89f, 0x807cc89f, 0x807cc89f, 0x29f51a8f, 0x29f51a8f,
        0x2ded7883, 0x2ded7883, 0xbafb82af, 0xbafb82af, 0x47462cbf
    };
    private static final int[] BLUE_PALETTE = {0x00004385, 0x004485cf, 0x007dbbff, 0x00ffffff};
    private static final int[] AQUA_PALETTE = {0x00002b59, 0x00005f8c, 0x0000b9be, 0x009ff4e5};
    private static final int[] TONE_FRAMES = {0, 4, 14, 22, 26, 37, 48, 58};
    private static final int[] TONE_FREQUENCIES = {0, 900, 900, 1000, 900, 900, 600, 600};
    private static final int[] TONE_DURATIONS = {0, 8, 8, 8, 8, 8, 8, 8};
    private static final int[] TONE_VOLUMES = {0, 100, 100, 100, 100, 100, 100, 100};
    private static final int[] TONE_FLAGS = {0, 0, 0, 0, 0, 0, 0, 0};

    private boolean started;

    /** Performs the start app operation. */
    protected void startApp() {
        if (started) {
            return;
        }
        started = true;
        Form result = new Form("Rubido corpus probe");
        Display.getDisplay(this).setCurrent(result);
        Wasm4Runtime runtime = null;
        WasmModule module = null;
        try {
            byte[] cartridge = ResourceLoader.read("/cartridges/rubido.wasm");
            requireEquals("Rubido length", 27463, cartridge.length);
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
            for (frame = 0; frame < 70; frame++) {
                audio.frame = frame;
                disk.frame = frame;
                runtime.beginFrame(module, gamepad(frame), mouseX(frame), mouseY(frame), mouseButtons(frame));
                interpreter.invoke("update");
                runtime.endFrame();
                if (checkpoint < CHECKPOINT_FRAMES.length && CHECKPOINT_FRAMES[checkpoint] == frame) {
                    int actual = FramebufferOracle.fnv1a(module);
                    requireEquals("framebuffer at frame " + frame, CHECKPOINT_FNV1A[checkpoint], actual);
                    checkPalette(module.memory(), frame);
                    System.out.println("W4ME_RUBIDO_FRAME frame="
                            + frame
                            + " gamepad="
                            + gamepad(frame)
                            + " mouse="
                            + mouseX(frame)
                            + ","
                            + mouseY(frame)
                            + ","
                            + mouseButtons(frame)
                            + " framebuffer-fnv1a="
                            + hex8(actual));
                    checkpoint++;
                }
            }

            requireEquals("checkpoint count", CHECKPOINT_FRAMES.length, checkpoint);
            requireEquals("APU tone count", TONE_FRAMES.length, apu.toneEventCount());
            audio.check();
            disk.check();
            System.out.println("W4ME_RUBIDO_PROBE frames=70 checkpoints=30 tones=8"
                    + " disk-read=20/0 disk-write=20/20"
                    + " palette=blue-to-aqua framebuffer-fnv1a=47462cbf");
            result.append("PASS\n70 frames\n30 exact checkpoints\nmouse + palette + disk");
        } catch (Throwable failure) { // NOPMD -- Java ME API linkage fallback.
            System.out.println("W4ME_RUBIDO_ERROR " + failure.toString());
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

    private static int gamepad(int frame) {
        if (frame == 13) {
            return 1;
        }
        if (frame == 21) {
            return 2;
        }
        return 0;
    }

    private static int mouseX(int frame) {
        if (frame == 0) {
            return 32767;
        }
        if (frame == 1) {
            return 79;
        }
        if (frame <= 23) {
            return 80;
        }
        return 79;
    }

    private static int mouseY(int frame) {
        if (frame == 0) {
            return 32767;
        }
        if (frame == 1) {
            return 85;
        }
        if (frame <= 23) {
            return 86;
        }
        if (frame <= 45) {
            return 65;
        }
        if (frame <= 55) {
            return 53;
        }
        return 85;
    }

    private static int mouseButtons(int frame) {
        if (frame == 3 || frame == 25 || frame == 36 || frame == 47 || frame == 57) {
            return 1;
        }
        return 0;
    }

    private static void checkPalette(byte[] memory, int frame) {
        int[] expected = frame < 14 ? BLUE_PALETTE : AQUA_PALETTE;
        int index;
        for (index = 0; index < expected.length; index++) {
            requireEquals(
                    "palette " + index + " at frame " + frame,
                    expected[index],
                    readI32(memory, Wasm4Runtime.PALETTE + index * 4));
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
        private int frame = -1;
        private int length;
        private int readCalls;
        private int writeCalls;
        private int readFrame;
        private int readOffset;
        private int readSize;
        private int readResult;
        private int writeFrame;
        private int writeOffset;
        private int writeSize;
        private int writeResult;

        public int read(byte[] target, int offset, int size) {
            readCalls++;
            int count = size < length ? size : length;
            System.arraycopy(data, 0, target, offset, count);
            readFrame = frame;
            readOffset = offset;
            readSize = size;
            readResult = count;
            return count;
        }

        public int write(byte[] source, int offset, int size) {
            writeCalls++;
            int count = size < data.length ? size : data.length;
            System.arraycopy(source, offset, data, 0, count);
            length = count;
            writeFrame = frame;
            writeOffset = offset;
            writeSize = size;
            writeResult = count;
            return count;
        }

        public void close() {
            /* Intentionally no-op. */
        }

        public String grade() {
            return "test";
        }

        private void check() {
            requireEquals("disk reads", 1, readCalls);
            requireEquals("disk read frame", -1, readFrame);
            requireEquals("disk read offset", 27304, readOffset);
            requireEquals("disk read size", 20, readSize);
            requireEquals("disk read result", 0, readResult);
            requireEquals("disk writes", 1, writeCalls);
            requireEquals("disk write frame", 14, writeFrame);
            requireEquals("disk write offset", 27304, writeOffset);
            requireEquals("disk write size", 20, writeSize);
            requireEquals("disk write result", 20, writeResult);
            requireEquals("disk bytes", 20, length);
            int index;
            for (index = 0; index < 16; index++) {
                requireEquals("disk zero byte " + index, 0, data[index] & 0xff);
            }
            requireEquals("disk palette", 5, data[16] & 0xff);
            requireEquals("disk sound", 1, data[17] & 0xff);
            requireEquals("disk inverted", 0, data[18] & 0xff);
            requireEquals("disk padding", 0, data[19] & 0xff);
        }
    }
}
