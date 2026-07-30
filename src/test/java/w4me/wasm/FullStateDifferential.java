package w4me.wasm;

import java.security.MessageDigest;
import w4me.FramebufferOracle;
import w4me.runtime.Wasm4Runtime;
import w4me.runtime.audio.AudioBackend;
import w4me.runtime.audio.Wasm4Apu;
import w4me.runtime.storage.DiskBackend;

/** Frame-complete generic interpreter differential over the exact optimization corpus. */
public final class FullStateDifferential {
    private static final int INPUT_START = Wasm4Runtime.GAMEPAD1;
    private static final int INPUT_END = Wasm4Runtime.SYSTEM_FLAGS + 1;

    private FullStateDifferential() {}

    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 11 && arguments.length != 12) {
            throw new IllegalArgumentException("usage: build-id font plasma duck waternet waternet-input"
                    + " rubido rubido-input untangle untangle-input game-of-life"
                    + " [reference-current|current-seven|seven-host-import-id"
                    + "|reference-host-import-id|host-import-id-load-tee]");
        }
        String buildIdentity = arguments[0];
        String comparison = arguments.length == 12 ? arguments[11] : "reference-current";
        InterpreterVariant referenceVariant;
        InterpreterVariant candidateVariant;
        if ("reference-current".equals(comparison)) {
            referenceVariant = InterpreterVariant.REFERENCE;
            candidateVariant = InterpreterVariant.CURRENT;
        } else if ("current-seven".equals(comparison)) {
            referenceVariant = InterpreterVariant.CURRENT;
            candidateVariant = InterpreterVariant.SEVEN_OPCODE;
        } else if ("seven-host-import-id".equals(comparison)) {
            referenceVariant = InterpreterVariant.SEVEN_OPCODE;
            candidateVariant = InterpreterVariant.HOST_IMPORT_ID;
        } else if ("reference-host-import-id".equals(comparison)) {
            referenceVariant = InterpreterVariant.REFERENCE;
            candidateVariant = InterpreterVariant.HOST_IMPORT_ID;
        } else if ("host-import-id-load-tee".equals(comparison)) {
            referenceVariant = InterpreterVariant.LOAD_TEE_BASELINE;
            candidateVariant = InterpreterVariant.LOAD_TEE;
        } else {
            throw new IllegalArgumentException("unknown comparison: " + comparison);
        }
        String[] workloadArguments = arguments;
        if (arguments.length == 12) {
            workloadArguments = new String[11];
            System.arraycopy(arguments, 0, workloadArguments, 0, workloadArguments.length);
        }
        byte[] font = CorpusWorkload.readFont(workloadArguments, 1);
        CorpusWorkload[] workloads = CorpusWorkload.readAll(workloadArguments, 1);
        int index;
        for (index = 0; index < workloads.length; index++) {
            run(buildIdentity, comparison, referenceVariant, candidateVariant, font, workloads[index]);
        }
        System.out.println("FULL_STATE_DIFFERENTIAL_COMPLETE build="
                + buildIdentity
                + " w4ir-format="
                + WasmModule.W4IR_FORMAT_VERSION
                + " comparison="
                + comparison
                + " workloads="
                + workloads.length
                + " status=PASS");
    }

    private static void run(
            String buildIdentity,
            String comparison,
            InterpreterVariant referenceVariant,
            InterpreterVariant candidateVariant,
            byte[] font,
            CorpusWorkload workload)
            throws Exception {
        Side reference = new Side(referenceVariant, font, workload.cartridge);
        Side candidate = new Side(candidateVariant, font, workload.cartridge);
        try {
            reference.interpreter.invokeCartridgeLifecycle();
            candidate.interpreter.invokeCartridgeLifecycle();
            assertState(workload.name, -1, reference, candidate);

            long referenceInstructions = 0;
            long candidateInstructions = 0;
            int frame;
            for (frame = 0; frame < workload.inputs.length; frame++) {
                CorpusWorkload.InputState input = workload.inputs[frame];
                reference.frame(frame, input);
                candidate.frame(frame, input);
                assertState(workload.name, frame, reference, candidate);
                referenceInstructions += reference.interpreter.instructionsExecuted();
                candidateInstructions += candidate.interpreter.instructionsExecuted();
            }
            if (referenceInstructions != candidateInstructions) {
                fail(
                        workload.name,
                        workload.inputs.length - 1,
                        "logical-instructions",
                        Long.toString(referenceInstructions),
                        Long.toString(candidateInstructions));
            }
            System.out.println("FULL_STATE_DIFFERENTIAL build="
                    + buildIdentity
                    + " w4ir-format="
                    + WasmModule.W4IR_FORMAT_VERSION
                    + " comparison="
                    + comparison
                    + " workload="
                    + workload.name
                    + " route="
                    + workload.route
                    + " cartridge-sha256="
                    + workload.cartridgeSha256
                    + " frames="
                    + workload.inputs.length
                    + " reference={"
                    + referenceVariant.configuration()
                    + "} candidate={"
                    + candidateVariant.configuration()
                    + "} logical="
                    + candidateInstructions
                    + " memory-sha256="
                    + sha256(candidate.module.memory)
                    + " framebuffer-fnv1a="
                    + hex32(FramebufferOracle.fnv1a(candidate.module))
                    + " state=exact");
        } finally {
            reference.close();
            candidate.close();
        }
    }

    private static void assertState(String workload, int frame, Side reference, Side candidate) {
        compareBytes(
                workload,
                frame,
                "palette",
                reference.module.memory,
                candidate.module.memory,
                Wasm4Runtime.PALETTE,
                Wasm4Runtime.DRAW_COLORS - Wasm4Runtime.PALETTE);
        compareBytes(
                workload,
                frame,
                "input",
                reference.module.memory,
                candidate.module.memory,
                INPUT_START,
                INPUT_END - INPUT_START);
        compareBytes(
                workload,
                frame,
                "framebuffer",
                reference.module.memory,
                candidate.module.memory,
                Wasm4Runtime.FRAMEBUFFER,
                Wasm4Runtime.FRAMEBUFFER_SIZE);
        compareLongs(workload, frame, "globals", reference.module.globals, candidate.module.globals);
        compareInts(workload, frame, "table", reference.module.table, candidate.module.table);
        reference.audio.assertEquals(workload, frame, candidate.audio);
        reference.disk.assertEquals(workload, frame, candidate.disk);
        compareBytes(workload, frame, "linear-memory", reference.module.memory, candidate.module.memory, 0, 65536);
    }

    private static void compareBytes(
            String workload, int frame, String category, byte[] expected, byte[] actual, int offset, int length) {
        int index;
        for (index = 0; index < length; index++) {
            int left = expected[offset + index] & 0xff;
            int right = actual[offset + index] & 0xff;
            if (left != right) {
                fail(
                        workload,
                        frame,
                        category + "@" + (offset + index),
                        Integer.toString(left),
                        Integer.toString(right));
            }
        }
    }

    private static void compareLongs(String workload, int frame, String category, long[] expected, long[] actual) {
        if (expected.length != actual.length) {
            fail(
                    workload,
                    frame,
                    category + "-length",
                    Integer.toString(expected.length),
                    Integer.toString(actual.length));
        }
        int index;
        for (index = 0; index < expected.length; index++) {
            if (expected[index] != actual[index]) {
                fail(
                        workload,
                        frame,
                        category + "@" + index,
                        Long.toString(expected[index]),
                        Long.toString(actual[index]));
            }
        }
    }

    private static void compareInts(String workload, int frame, String category, int[] expected, int[] actual) {
        if (expected.length != actual.length) {
            fail(
                    workload,
                    frame,
                    category + "-length",
                    Integer.toString(expected.length),
                    Integer.toString(actual.length));
        }
        int index;
        for (index = 0; index < expected.length; index++) {
            if (expected[index] != actual[index]) {
                fail(
                        workload,
                        frame,
                        category + "@" + index,
                        Integer.toString(expected[index]),
                        Integer.toString(actual[index]));
            }
        }
    }

    private static void fail(String workload, int frame, String category, String expected, String actual) {
        throw new AssertionError(workload
                + " first divergence frame="
                + frame
                + " category="
                + category
                + " reference="
                + expected
                + " candidate="
                + actual);
    }

    private static String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(bytes);
        StringBuffer result = new StringBuffer(hash.length * 2);
        int index;
        for (index = 0; index < hash.length; index++) {
            int value = hash[index] & 0xff;
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
        int index;
        for (index = text.length(); index < 8; index++) {
            result.append('0');
        }
        result.append(text);
        return result.toString();
    }

    private static final class Side {
        private final WasmModule module;
        private final RecordingAudio audio;
        private final RecordingDisk disk;
        private final Wasm4Runtime runtime;
        private final WasmInterpreter interpreter;

        private Side(InterpreterVariant variant, byte[] font, byte[] cartridge) throws Exception {
            module = variant.read(cartridge);
            audio = new RecordingAudio();
            disk = new RecordingDisk();
            runtime = new Wasm4Runtime(font, new Wasm4Apu(audio), disk);
            runtime.initialize(module);
            interpreter = variant.interpreter(module, runtime);
        }

        private void frame(int frame, CorpusWorkload.InputState input) throws Exception {
            audio.frame = frame;
            disk.frame = frame;
            runtime.beginFrame(module, input.gamepad1, input.gamepad2, input.mouseX, input.mouseY, input.mouseButtons);
            interpreter.invoke("update");
            runtime.endFrame();
        }

        private void close() {
            runtime.close();
            module.close();
        }
    }

    private static final class RecordingAudio implements AudioBackend {
        private int[] events = new int[64 * 5];
        private int eventCount;
        private int frame = -1;

        public void submitTone(int frequency, int duration, int volume, int flags) {
            ensure(eventCount + 1);
            int offset = eventCount * 5;
            events[offset] = frame;
            events[offset + 1] = frequency;
            events[offset + 2] = duration;
            events[offset + 3] = volume;
            events[offset + 4] = flags;
            eventCount++;
        }

        public void tick() {
            /* Intentionally no-op. */
        }

        public void close() {
            /* Intentionally no-op. */
        }

        public String grade() {
            return "differential";
        }

        private void ensure(int count) {
            if (count * 5 <= events.length) {
                return;
            }
            int[] replacement = new int[events.length * 2];
            System.arraycopy(events, 0, replacement, 0, events.length);
            events = replacement;
        }

        private void assertEquals(String workload, int frame, RecordingAudio other) {
            if (eventCount != other.eventCount) {
                fail(
                        workload,
                        frame,
                        "tone-events-count",
                        Integer.toString(eventCount),
                        Integer.toString(other.eventCount));
            }
            int index;
            for (index = 0; index < eventCount * 5; index++) {
                if (events[index] != other.events[index]) {
                    fail(
                            workload,
                            frame,
                            "tone-events@" + index,
                            Integer.toString(events[index]),
                            Integer.toString(other.events[index]));
                }
            }
        }
    }

    private static final class RecordingDisk implements DiskBackend {
        private final byte[] data = new byte[1024];
        private int length;
        private int[] events = new int[64 * 5];
        private byte[][] eventData = new byte[64][];
        private int eventCount;
        private int frame = -1;

        public int read(byte[] target, int offset, int size) {
            int count = size < length ? size : length;
            System.arraycopy(data, 0, target, offset, count);
            record(0, offset, size, count, target, offset);
            return count;
        }

        public int write(byte[] source, int offset, int size) {
            int count = size < data.length ? size : data.length;
            System.arraycopy(source, offset, data, 0, count);
            length = count;
            record(1, offset, size, count, source, offset);
            return count;
        }

        public void close() {
            /* Intentionally no-op. */
        }

        public String grade() {
            return "differential";
        }

        private void record(int operation, int address, int size, int result, byte[] bytes, int bytesOffset) {
            ensure(eventCount + 1);
            int offset = eventCount * 5;
            events[offset] = frame;
            events[offset + 1] = operation;
            events[offset + 2] = address;
            events[offset + 3] = size;
            events[offset + 4] = result;
            eventData[eventCount] = new byte[result];
            System.arraycopy(bytes, bytesOffset, eventData[eventCount], 0, result);
            eventCount++;
        }

        private void ensure(int count) {
            if (count * 5 <= events.length) {
                return;
            }
            int[] replacement = new int[events.length * 2];
            System.arraycopy(events, 0, replacement, 0, events.length);
            events = replacement;
            byte[][] replacementData = new byte[eventData.length * 2][];
            System.arraycopy(eventData, 0, replacementData, 0, eventData.length);
            eventData = replacementData;
        }

        private void assertEquals(String workload, int frame, RecordingDisk other) {
            if (length != other.length) {
                fail(workload, frame, "disk-length", Integer.toString(length), Integer.toString(other.length));
            }
            compareBytes(workload, frame, "disk", data, other.data, 0, data.length);
            if (eventCount != other.eventCount) {
                fail(
                        workload,
                        frame,
                        "disk-events-count",
                        Integer.toString(eventCount),
                        Integer.toString(other.eventCount));
            }
            int index;
            for (index = 0; index < eventCount * 5; index++) {
                if (events[index] != other.events[index]) {
                    fail(
                            workload,
                            frame,
                            "disk-events@" + index,
                            Integer.toString(events[index]),
                            Integer.toString(other.events[index]));
                }
            }
            for (index = 0; index < eventCount; index++) {
                compareBytes(
                        workload,
                        frame,
                        "disk-event-data@" + index,
                        eventData[index],
                        other.eventData[index],
                        0,
                        eventData[index].length);
            }
        }
    }
}
