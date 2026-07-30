package w4me;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.StringTokenizer;
import w4me.runtime.Wasm4Runtime;
import w4me.runtime.audio.AudioBackend;
import w4me.runtime.audio.Wasm4Apu;
import w4me.runtime.storage.DiskBackend;
import w4me.wasm.WasmInterpreter;
import w4me.wasm.WasmModule;

/** Provides the WASM 4 corpus replay smoke implementation. */
public final class Wasm4CorpusReplaySmoke {
    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 8) {
            throw new IllegalArgumentException(
                    "usage: font.bin cart.wasm input.csv oracle.csv tone.csv disk.csv sha256 label");
        }

        byte[] cartridge = readFile(arguments[1]);
        assertEquals(arguments[7] + " cartridge SHA-256", arguments[6], sha256(cartridge));
        InputTrace trace = readInputTrace(arguments[2]);
        OracleReceipt[] oracle = readOracle(arguments[3]);
        final ToneReceipt[] tones = readTones(arguments[4]);
        final DiskReceipt[] disks = readDisks(arguments[5]);

        WasmModule module = WasmModule.read(cartridge);
        RecordingBackend audio = new RecordingBackend();
        Wasm4Apu apu = new Wasm4Apu(audio);
        RecordingDisk disk = new RecordingDisk();
        Wasm4Runtime runtime = new Wasm4Runtime(readFile(arguments[0]), apu, disk);
        runtime.initialize(module);
        WasmInterpreter interpreter = new WasmInterpreter(module, runtime);
        interpreter.invokeCartridgeLifecycle();

        int oracleIndex = 0;
        int frame;
        for (frame = 0; frame < trace.length; frame++) {
            InputState state = trace.states[frame];
            audio.frame = frame;
            disk.frame = frame;
            runtime.beginFrame(module, state.gamepad, state.mouseX, state.mouseY, state.mouseButtons);
            interpreter.invoke("update");
            runtime.endFrame();

            if (oracleIndex < oracle.length && oracle[oracleIndex].frame == frame) {
                checkReceipt(module, state, oracle[oracleIndex]);
                oracleIndex++;
            }
        }

        assertEquals("oracle checkpoints consumed", oracle.length, oracleIndex);
        assertEquals("tone events", tones.length, apu.toneEventCount());
        audio.check(tones);
        disk.check(disks);
        runtime.close();
        module.close();

        System.out.println("PASS corpus="
                + arguments[7]
                + " frames="
                + trace.length
                + " checkpoints="
                + oracle.length
                + " tones="
                + tones.length
                + " disk-events="
                + disks.length
                + " framebuffer=exact");
    }

    private static void checkReceipt(WasmModule module, InputState state, OracleReceipt expected) throws Exception {
        final byte[] memory = module.memory();
        assertEquals("gamepad at frame " + expected.frame, expected.gamepad, state.gamepad);
        assertEquals("mouse x at frame " + expected.frame, expected.mouseX, state.mouseX);
        assertEquals("mouse y at frame " + expected.frame, expected.mouseY, state.mouseY);
        assertEquals("mouse buttons at frame " + expected.frame, expected.mouseButtons, state.mouseButtons);
        assertEquals("framebuffer SHA-256 at frame " + expected.frame, expected.sha256, framebufferSha256(module));
        assertEquals(
                "framebuffer FNV-1a at frame " + expected.frame,
                expected.fnv1a,
                hex32(FramebufferOracle.fnv1a(module)));
        int index;
        for (index = 0; index < 4; index++) {
            assertEquals(
                    "palette " + index + " at frame " + expected.frame,
                    expected.palette[index],
                    readI32(memory, Wasm4Runtime.PALETTE + index * 4));
        }
        assertEquals("gamepad memory at frame " + expected.frame, state.gamepad, memory[Wasm4Runtime.GAMEPAD1] & 0xff);
        assertEquals("mouse x memory at frame " + expected.frame, state.mouseX, readI16(memory, Wasm4Runtime.MOUSE_X));
        assertEquals("mouse y memory at frame " + expected.frame, state.mouseY, readI16(memory, Wasm4Runtime.MOUSE_Y));
        assertEquals(
                "mouse buttons memory at frame " + expected.frame,
                state.mouseButtons,
                memory[Wasm4Runtime.MOUSE_BUTTONS] & 0xff);
    }

    private static InputTrace readInputTrace(String path) throws Exception {
        BufferedReader input = new BufferedReader(new FileReader(path));
        try {
            assertEquals("input trace header", "frame,gamepad,mouse_x,mouse_y,mouse_buttons,action", input.readLine());
            InputState[] events = new InputState[128];
            int[] frames = new int[128];
            int count = 0;
            int previousFrame = -1;
            String line;
            while ((line = input.readLine()) != null) {
                if (line.length() == 0) {
                    continue;
                }
                StringTokenizer fields = fields(line, 6, "input trace");
                int frame = Integer.parseInt(fields.nextToken());
                int gamepad = Integer.parseInt(fields.nextToken());
                int mouseX = Integer.parseInt(fields.nextToken());
                int mouseY = Integer.parseInt(fields.nextToken());
                int mouseButtons = Integer.parseInt(fields.nextToken());
                fields.nextToken();
                if (frame <= previousFrame || count >= events.length) {
                    throw new IllegalArgumentException("invalid input frame: " + frame);
                }
                checkInput(gamepad, mouseX, mouseY, mouseButtons);
                frames[count] = frame;
                events[count] = new InputState(gamepad, mouseX, mouseY, mouseButtons);
                count++;
                previousFrame = frame;
            }
            if (count == 0 || frames[0] != 0) {
                throw new IllegalArgumentException("input trace must start at frame zero");
            }
            InputState[] states = new InputState[previousFrame + 1];
            int event = 0;
            InputState current = null;
            int frame;
            for (frame = 0; frame < states.length; frame++) {
                if (event < count && frames[event] == frame) {
                    current = events[
                            event++]; // NOPMD -- Cursor mutation stays adjacent to the access to preserve compact Java
                    // 1.3 bytecode.
                }
                states[frame] = current;
            }
            return new InputTrace(states);
        } finally {
            input.close();
        }
    }

    private static void checkInput(int gamepad, int mouseX, int mouseY, int mouseButtons) {
        if (gamepad < 0 || gamepad > 255 || mouseButtons < 0 || mouseButtons > 255) {
            throw new IllegalArgumentException("input button value is out of range");
        }
        if (mouseX < -32768 || mouseX > 32767 || mouseY < -32768 || mouseY > 32767) {
            throw new IllegalArgumentException("input mouse coordinate is out of range");
        }
    }

    private static OracleReceipt[] readOracle(String path) throws Exception {
        BufferedReader input = new BufferedReader(new FileReader(path));
        try {
            assertEquals(
                    "oracle header",
                    "frame,gamepad,mouse_x,mouse_y,mouse_buttons,framebuffer_sha256,framebuffer_fnv1a,palette0,palette1,palette2,palette3,checkpoint",
                    input.readLine());
            OracleReceipt[] receipts = new OracleReceipt[128];
            int count = 0;
            int previousFrame = -1;
            String line;
            while ((line = input.readLine()) != null) {
                if (line.length() == 0) {
                    continue;
                }
                StringTokenizer fields = fields(line, 12, "corpus oracle");
                int frame = Integer.parseInt(fields.nextToken());
                final int gamepad = Integer.parseInt(fields.nextToken());
                final int mouseX = Integer.parseInt(fields.nextToken());
                final int mouseY = Integer.parseInt(fields.nextToken());
                final int mouseButtons = Integer.parseInt(fields.nextToken());
                final String sha256 = fields.nextToken();
                final String fnv1a = fields.nextToken();
                int[] palette = new int[4];
                int index;
                for (index = 0; index < palette.length; index++) {
                    palette[index] = (int) Long.parseLong(fields.nextToken(), 16);
                }
                fields.nextToken();
                if (frame <= previousFrame || count >= receipts.length) {
                    throw new IllegalArgumentException("invalid oracle frame: " + frame);
                }
                checkInput(gamepad, mouseX, mouseY, mouseButtons);
                receipts[count++] = // NOPMD -- Compact Java 1.3 cursor bytecode.
                        new OracleReceipt(frame, gamepad, mouseX, mouseY, mouseButtons, sha256, fnv1a, palette);
                previousFrame = frame;
            }
            return exactOracle(receipts, count);
        } finally {
            input.close();
        }
    }

    private static ToneReceipt[] readTones(String path) throws Exception {
        BufferedReader input = new BufferedReader(new FileReader(path));
        try {
            assertEquals("tone header", "frame,frequency,duration,volume,flags", input.readLine());
            ToneReceipt[] receipts = new ToneReceipt[128];
            int count = 0;
            String line;
            while ((line = input.readLine()) != null) {
                if (line.length() == 0) {
                    continue;
                }
                StringTokenizer fields = fields(line, 5, "tone oracle");
                receipts[count++] = new ToneReceipt( // NOPMD -- Compact Java 1.3 cursor bytecode.
                        Integer.parseInt(fields.nextToken()),
                        Integer.parseInt(fields.nextToken()),
                        Integer.parseInt(fields.nextToken()),
                        Integer.parseInt(fields.nextToken()),
                        Integer.parseInt(fields.nextToken()));
            }
            ToneReceipt[] exact = new ToneReceipt[count];
            System.arraycopy(receipts, 0, exact, 0, count);
            return exact;
        } finally {
            input.close();
        }
    }

    private static DiskReceipt[] readDisks(String path) throws Exception {
        BufferedReader input = new BufferedReader(new FileReader(path));
        try {
            assertEquals("disk header", "frame,operation,address,size,result,data_hex", input.readLine());
            DiskReceipt[] receipts = new DiskReceipt[32];
            int count = 0;
            String line;
            while ((line = input.readLine()) != null) {
                if (line.length() == 0) {
                    continue;
                }
                StringTokenizer fields = fields(line, 6, "disk oracle");
                receipts[count++] = new DiskReceipt( // NOPMD -- Compact Java 1.3 cursor bytecode.
                        Integer.parseInt(fields.nextToken()),
                        fields.nextToken(),
                        Integer.parseInt(fields.nextToken()),
                        Integer.parseInt(fields.nextToken()),
                        Integer.parseInt(fields.nextToken()),
                        fields.nextToken());
            }
            DiskReceipt[] exact = new DiskReceipt[count];
            System.arraycopy(receipts, 0, exact, 0, count);
            return exact;
        } finally {
            input.close();
        }
    }

    private static OracleReceipt[] exactOracle(OracleReceipt[] receipts, int count) {
        OracleReceipt[] exact = new OracleReceipt[count];
        System.arraycopy(receipts, 0, exact, 0, count);
        return exact;
    }

    private static StringTokenizer fields(String line, int expected, String label) {
        StringTokenizer fields = new StringTokenizer(line, ",");
        if (fields.countTokens() != expected) {
            throw new IllegalArgumentException("invalid " + label + " row: " + line);
        }
        return fields;
    }

    private static String framebufferSha256(WasmModule module) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(module.memory(), Wasm4Runtime.FRAMEBUFFER, Wasm4Runtime.FRAMEBUFFER_SIZE);
        return hex(digest.digest());
    }

    private static String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return hex(digest.digest(bytes));
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
        return hex(bytes, 0, bytes.length);
    }

    private static String hex(byte[] bytes, int offset, int length) {
        StringBuffer result = new StringBuffer(length * 2);
        int index;
        for (index = 0; index < length; index++) {
            int value = bytes[offset + index] & 0xff;
            if (value < 16) {
                result.append('0');
            }
            result.append(Integer.toHexString(value));
        }
        return result.toString();
    }

    private static String hex32(int value) {
        String text = Integer.toHexString(value);
        StringBuffer result = new StringBuffer(8);
        int padding;
        for (padding = text.length(); padding < 8; padding++) {
            result.append('0');
        }
        result.append(text);
        return result.toString();
    }

    private static void assertEquals(String label, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(String label, String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    private static final class InputTrace {
        private final InputState[] states;
        private final int length;

        private InputTrace(InputState[] states) {
            this.states = states;
            this.length = states.length;
        }
    }

    private static final class InputState {
        private final int gamepad;
        private final int mouseX;
        private final int mouseY;
        private final int mouseButtons;

        private InputState(int gamepad, int mouseX, int mouseY, int mouseButtons) {
            this.gamepad = gamepad;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.mouseButtons = mouseButtons;
        }
    }

    private static final class OracleReceipt {
        private final int frame;
        private final int gamepad;
        private final int mouseX;
        private final int mouseY;
        private final int mouseButtons;
        private final String sha256;
        private final String fnv1a;
        private final int[] palette;

        private OracleReceipt(
                int frame,
                int gamepad,
                int mouseX,
                int mouseY,
                int mouseButtons,
                String sha256,
                String fnv1a,
                int[] palette) {
            this.frame = frame;
            this.gamepad = gamepad;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.mouseButtons = mouseButtons;
            this.sha256 = sha256;
            this.fnv1a = fnv1a;
            this.palette = palette;
        }
    }

    private static final class ToneReceipt {
        private final int frame;
        private final int frequency;
        private final int duration;
        private final int volume;
        private final int flags;

        private ToneReceipt(int frame, int frequency, int duration, int volume, int flags) {
            this.frame = frame;
            this.frequency = frequency;
            this.duration = duration;
            this.volume = volume;
            this.flags = flags;
        }
    }

    private static final class DiskReceipt {
        private final int frame;
        private final String operation;
        private final int address;
        private final int size;
        private final int result;
        private final String dataHex;

        private DiskReceipt(int frame, String operation, int address, int size, int result, String dataHex) {
            this.frame = frame;
            this.operation = operation;
            this.address = address;
            this.size = size;
            this.result = result;
            this.dataHex = dataHex;
        }
    }

    private static final class RecordingBackend implements AudioBackend {
        private final int[] frames = new int[128];
        private final int[] frequencies = new int[128];
        private final int[] durations = new int[128];
        private final int[] volumes = new int[128];
        private final int[] flags = new int[128];
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

        private void check(ToneReceipt[] expected) {
            assertEquals("audio backend calls", expected.length, calls);
            int index;
            for (index = 0; index < expected.length; index++) {
                ToneReceipt tone = expected[index];
                assertEquals("tone frame " + index, tone.frame, frames[index]);
                assertEquals("tone frequency " + index, tone.frequency, frequencies[index]);
                assertEquals("tone duration " + index, tone.duration, durations[index]);
                assertEquals("tone volume " + index, tone.volume, volumes[index]);
                assertEquals("tone flags " + index, tone.flags, flags[index]);
            }
        }
    }

    private static final class RecordingDisk implements DiskBackend {
        private final byte[] data = new byte[1024];
        private final DiskReceipt[] events = new DiskReceipt[32];
        private int frame = -1;
        private int length;
        private int eventCount;

        public int read(byte[] target, int offset, int size) {
            int count = size < length ? size : length;
            System.arraycopy(data, 0, target, offset, count);
            record("read", offset, size, count, "empty");
            return count;
        }

        public int write(byte[] source, int offset, int size) {
            int count = size < data.length ? size : data.length;
            System.arraycopy(source, offset, data, 0, count);
            length = count;
            record("write", offset, size, count, hex(source, offset, count));
            return count;
        }

        public void close() {
            /* Intentionally no-op. */
        }

        public String grade() {
            return "test";
        }

        private void record(String operation, int address, int size, int result, String dataHex) {
            if (eventCount >= events.length) {
                throw new IllegalStateException("too many disk events");
            }
            events[eventCount] = new DiskReceipt(frame, operation, address, size, result, dataHex);
            eventCount++;
        }

        private void check(DiskReceipt[] expected) {
            assertEquals("disk event count", expected.length, eventCount);
            int index;
            for (index = 0; index < expected.length; index++) {
                DiskReceipt left = expected[index];
                DiskReceipt right = events[index];
                assertEquals("disk frame " + index, left.frame, right.frame);
                assertEquals("disk operation " + index, left.operation, right.operation);
                assertEquals("disk address " + index, left.address, right.address);
                assertEquals("disk size " + index, left.size, right.size);
                assertEquals("disk result " + index, left.result, right.result);
                assertEquals("disk data " + index, left.dataHex, right.dataHex);
            }
            if (expected.length == 0 || !"write".equals(expected[expected.length - 1].operation)) {
                assertEquals("final disk bytes", 0, length);
            } else {
                assertEquals("final disk data", expected[expected.length - 1].dataHex, hex(data, 0, length));
            }
        }
    }
}
