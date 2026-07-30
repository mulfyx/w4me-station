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

/** Provides the untangle corpus probe midlet implementation. */
public final class UntangleCorpusProbeMidlet extends MIDlet {
    private static final int[] CHECKPOINT_FRAMES = {
        0, 1, 2, 3, 4, 5, 6, 69, 70, 71, 149, 150, 151, 152, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164,
        165, 166, 167, 168, 169, 173, 178, 183, 193, 194, 250, 300, 350, 367, 368, 369, 370, 384, 385, 386, 400
    };
    private static final int[] CHECKPOINT_FNV1A = {
        0x324b94f5, 0x1052deb9, 0x342c89ed, 0x5c9c6fd6, 0xccb694fc,
        0x8ad47191, 0x8d5455c5, 0x11102c95, 0x30ea83d5, 0x30ea83d5,
        0x8c84f92c, 0x6872058c, 0xd791ae2c, 0xd791ae2c, 0xf7a0f7c8,
        0xe4c88f45, 0x687eeb25, 0xa40710dd, 0x15b75455, 0x821e7faf,
        0x08bfe757, 0xf8726484, 0xd783c8cb, 0x146621dd, 0xb31923da,
        0x941dfb8d, 0x795a3d47, 0xa7ea657f, 0xa1073b03, 0x9cb2ddb3,
        0x9cb2ddb3, 0x117df007, 0xc82187ab, 0x9cb2ddb3, 0xc82187ab,
        0xc82187ab, 0x8932507d, 0xf99b5ffd, 0x32d545c1, 0xc82187ab,
        0xc82187ab, 0xc82187ab, 0xc82187ab, 0x4e4a617b, 0x64061e59,
        0xbc0231d9, 0xbc0231d9
    };
    private static final int[] PALETTE = {0x00e0f8cf, 0x0086c06c, 0x00306850, 0x00071821};

    private boolean started;

    /** Performs the start app operation. */
    protected void startApp() {
        if (started) {
            return;
        }
        started = true;
        Form result = new Form("Untangle corpus probe");
        Display.getDisplay(this).setCurrent(result);
        Wasm4Runtime runtime = null;
        WasmModule module = null;
        try {
            byte[] cartridge = ResourceLoader.read("/cartridges/untangle.wasm");
            requireEquals("Untangle length", 56236, cartridge.length);
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
            for (frame = 0; frame < 401; frame++) {
                audio.frame = frame;
                disk.frame = frame;
                runtime.beginFrame(module, 0, mouseX(frame), mouseY(frame), mouseButtons(frame));
                interpreter.invoke("update");
                runtime.endFrame();
                if (checkpoint < CHECKPOINT_FRAMES.length && CHECKPOINT_FRAMES[checkpoint] == frame) {
                    int actual = FramebufferOracle.fnv1a(module);
                    requireEquals("framebuffer at frame " + frame, CHECKPOINT_FNV1A[checkpoint], actual);
                    checkPalette(module.memory(), frame);
                    checkInput(module.memory(), frame);
                    System.out.println("W4ME_UNTANGLE_FRAME frame="
                            + frame
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
            requireEquals("APU tone count", 0, apu.toneEventCount());
            audio.check();
            disk.check();
            System.out.println("W4ME_UNTANGLE_PROBE frames=401 checkpoints=47 tones=0"
                    + " disk-read=1/0 disk-write=1/1"
                    + " framebuffer-fnv1a=bc0231d9");
            result.append("PASS\n401 frames\n47 exact checkpoints\nlevel 0 solved");
        } catch (Throwable failure) { // NOPMD -- Java ME API linkage fallback.
            System.out.println("W4ME_UNTANGLE_ERROR " + failure.toString());
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

    private static int mouseX(int frame) {
        if (frame == 0) {
            return 32767;
        }
        if (frame <= 2) {
            return 80;
        }
        if (frame <= 152) {
            return 20;
        }
        if (frame == 153 || frame == 156) {
            return 28;
        }
        if (frame <= 158) {
            return 120;
        }
        if (frame == 159 || frame == 165) {
            return 131;
        }
        if (frame <= 162) {
            return 80;
        }
        return 40;
    }

    private static int mouseY(int frame) {
        if (frame == 0) {
            return 32767;
        }
        if (frame <= 2) {
            return 80;
        }
        if (frame <= 152) {
            return 72;
        }
        if (frame == 153) {
            return 49;
        }
        if (frame <= 155) {
            return 50;
        }
        if (frame == 156) {
            return 109;
        }
        if (frame <= 159) {
            return 110;
        }
        if (frame <= 161) {
            return 130;
        }
        if (frame == 162) {
            return 140;
        }
        if (frame <= 164) {
            return 110;
        }
        return 50;
    }

    private static int mouseButtons(int frame) {
        if (frame == 1 || frame == 4) {
            return 1;
        }
        if ((frame >= 153 && frame <= 154)
                || (frame >= 156 && frame <= 157)
                || (frame >= 159 && frame <= 160)
                || (frame >= 162 && frame <= 163)
                || (frame >= 165 && frame <= 166)) {
            return 1;
        }
        return 0;
    }

    private static void checkPalette(byte[] memory, int frame) {
        int index;
        for (index = 0; index < PALETTE.length; index++) {
            requireEquals(
                    "palette " + index + " at frame " + frame,
                    PALETTE[index],
                    readI32(memory, Wasm4Runtime.PALETTE + index * 4));
        }
    }

    private static void checkInput(byte[] memory, int frame) {
        requireEquals("gamepad at frame " + frame, 0, memory[Wasm4Runtime.GAMEPAD1] & 0xff);
        requireEquals("mouse x at frame " + frame, mouseX(frame), readI16(memory, Wasm4Runtime.MOUSE_X));
        requireEquals("mouse y at frame " + frame, mouseY(frame), readI16(memory, Wasm4Runtime.MOUSE_Y));
        requireEquals(
                "mouse buttons at frame " + frame, mouseButtons(frame), memory[Wasm4Runtime.MOUSE_BUTTONS] & 0xff);
    }

    private static int readI32(byte[] memory, int address) {
        return (memory[address] & 0xff)
                | ((memory[address + 1] & 0xff) << 8)
                | ((memory[address + 2] & 0xff) << 16)
                | (memory[address + 3] << 24);
    }

    private static int readI16(byte[] memory, int address) {
        return (short) ((memory[address] & 0xff) | (memory[address + 1] << 8));
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
        private int frame = -1;
        private int calls;

        public void submitTone(int frequency, int duration, int volume, int flags) {
            calls++;
            throw new IllegalStateException("unexpected tone at frame " + frame);
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
            requireEquals("tone calls", 0, calls);
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
            requireEquals("disk read frame", 3, readFrame);
            requireEquals("disk read offset", 14708, readOffset);
            requireEquals("disk read size", 1, readSize);
            requireEquals("disk read result", 0, readResult);
            requireEquals("disk writes", 1, writeCalls);
            requireEquals("disk write frame", 168, writeFrame);
            requireEquals("disk write offset", 14648, writeOffset);
            requireEquals("disk write size", 1, writeSize);
            requireEquals("disk write result", 1, writeResult);
            requireEquals("disk bytes", 1, length);
            requireEquals("disk unlock byte", 1, data[0] & 0xff);
        }
    }
}
