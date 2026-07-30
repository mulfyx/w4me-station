package w4me.wasm;

import java.util.Arrays;
import w4me.runtime.Wasm4Runtime;

/** Complete deterministic opcode and compact-region profile for the generic corpus. */
public final class GenericCorpusProfiler {
    private GenericCorpusProfiler() {}

    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 11) {
            throw new IllegalArgumentException("usage: build-id font plasma duck waternet waternet-input"
                    + " rubido rubido-input untangle untangle-input game-of-life");
        }
        String buildIdentity = arguments[0];
        byte[] font = CorpusWorkload.readFont(arguments, 1);
        CorpusWorkload[] workloads = CorpusWorkload.readAll(arguments, 1);
        int index;
        for (index = 0; index < workloads.length; index++) {
            profile(buildIdentity, font, workloads[index]);
        }
        System.out.println("GENERIC_CORPUS_PROFILE_COMPLETE build="
                + buildIdentity
                + " w4ir-format="
                + WasmModule.W4IR_FORMAT_VERSION
                + " workloads="
                + workloads.length
                + " status=PASS");
    }

    private static void profile(String buildIdentity, byte[] font, CorpusWorkload workload) throws Exception {
        ProfileTotals profile = collectProfile(font, workload);
        TierTotals tier = collectTier(font, workload);
        System.out.println("GENERIC_CORPUS_PROFILE build="
                + buildIdentity
                + " w4ir-format="
                + WasmModule.W4IR_FORMAT_VERSION
                + " workload="
                + workload.name
                + " route="
                + workload.route
                + " cartridge-sha256="
                + workload.cartridgeSha256
                + " frames="
                + workload.inputs.length
                + " profile-config={"
                + InterpreterVariant.PROFILE.configuration()
                + ",profiling=on} tier-config={"
                + InterpreterVariant.HOST_IMPORT_ID.configuration()
                + ",profiling=off} logical="
                + profile.logicalInstructions
                + " outer-dispatches="
                + profile.outerDispatches
                + " tier-logical="
                + tier.logicalInstructions
                + " tier-outer-dispatches="
                + tier.outerDispatches
                + " compact-calls="
                + tier.compactCalls
                + " compact-instructions="
                + tier.compactInstructions
                + " compact-candidates="
                + profile.compactCandidates
                + " compact-accepted="
                + profile.compactAccepted);
        printOpcodes(workload, profile);
        printSequences(workload, "pair", profile.pairs, 2);
        printSequences(workload, "triple", profile.triples, 3);
        printFunctions(workload, profile);
        printCompact(workload, profile);
    }

    private static ProfileTotals collectProfile(byte[] font, CorpusWorkload workload) throws Exception {
        WasmModule module = InterpreterVariant.PROFILE.read(workload.cartridge);
        Wasm4Runtime runtime = new Wasm4Runtime(font);
        try {
            runtime.initialize(module);
            WasmInterpreter interpreter = InterpreterVariant.PROFILE.interpreter(module, runtime);
            interpreter.invokeCartridgeLifecycle();
            interpreter.setProfilingEnabled(true);
            ProfileTotals totals = new ProfileTotals(module.functionCount());
            int frame;
            for (frame = 0; frame < workload.inputs.length; frame++) {
                update(module, runtime, interpreter, workload.inputs[frame]);
                totals.collect(interpreter);
            }
            return totals;
        } finally {
            runtime.close();
            module.close();
        }
    }

    private static TierTotals collectTier(byte[] font, CorpusWorkload workload) throws Exception {
        WasmModule module = InterpreterVariant.HOST_IMPORT_ID.read(workload.cartridge);
        Wasm4Runtime runtime = new Wasm4Runtime(font);
        try {
            runtime.initialize(module);
            WasmInterpreter interpreter = InterpreterVariant.HOST_IMPORT_ID.interpreter(module, runtime);
            interpreter.invokeCartridgeLifecycle();
            TierTotals totals = new TierTotals();
            int frame;
            for (frame = 0; frame < workload.inputs.length; frame++) {
                update(module, runtime, interpreter, workload.inputs[frame]);
                totals.logicalInstructions += interpreter.instructionsExecuted();
                totals.outerDispatches += interpreter.dispatchesExecuted();
                totals.compactCalls += interpreter.compactBlockCalls();
                totals.compactInstructions += interpreter.compactInstructionsExecuted();
            }
            return totals;
        } finally {
            runtime.close();
            module.close();
        }
    }

    private static void update(
            WasmModule module, Wasm4Runtime runtime, WasmInterpreter interpreter, CorpusWorkload.InputState input)
            throws Exception {
        runtime.beginFrame(module, input.gamepad1, input.gamepad2, input.mouseX, input.mouseY, input.mouseButtons);
        interpreter.invoke("update");
        runtime.endFrame();
    }

    private static void printOpcodes(CorpusWorkload workload, ProfileTotals profile) {
        int opcode;
        for (opcode = 0; opcode < profile.opcodes.length; opcode++) {
            if (profile.opcodes[opcode] == 0) {
                continue;
            }
            System.out.println("GENERIC_CORPUS_OPCODE workload="
                    + workload.name
                    + " route="
                    + workload.route
                    + " opcode=0x"
                    + OpcodeNames.hex4(opcode)
                    + " label="
                    + OpcodeNames.label(opcode)
                    + " count="
                    + profile.opcodes[opcode]);
        }
    }

    private static void printSequences(CorpusWorkload workload, String kind, SequenceTotals totals, int width) {
        long[] keys = totals.sortedKeys();
        int index;
        for (index = 0; index < keys.length; index++) {
            long key = keys[index];
            int first;
            int second;
            int third = -1;
            if (width == 2) {
                first = (int) ((key >>> 16) & 0xffffL);
                second = (int) (key & 0xffffL);
            } else {
                first = (int) ((key >>> 32) & 0xffffL);
                second = (int) ((key >>> 16) & 0xffffL);
                third = (int) (key & 0xffffL);
            }
            StringBuffer labels = new StringBuffer();
            labels.append(OpcodeNames.label(first));
            labels.append('+');
            labels.append(OpcodeNames.label(second));
            StringBuffer opcodes = new StringBuffer();
            opcodes.append("0x");
            opcodes.append(OpcodeNames.hex4(first));
            opcodes.append("+0x");
            opcodes.append(OpcodeNames.hex4(second));
            if (width == 3) {
                labels.append('+');
                labels.append(OpcodeNames.label(third));
                opcodes.append("+0x");
                opcodes.append(OpcodeNames.hex4(third));
            }
            System.out.println("GENERIC_CORPUS_SEQUENCE workload="
                    + workload.name
                    + " route="
                    + workload.route
                    + " kind="
                    + kind
                    + " opcodes="
                    + opcodes
                    + " labels="
                    + labels
                    + " count="
                    + totals.get(key));
        }
    }

    private static void printFunctions(CorpusWorkload workload, ProfileTotals profile) {
        int function;
        for (function = 0; function < profile.functionCalls.length; function++) {
            if (profile.functionCalls[function] == 0 && profile.functionDispatches[function] == 0) {
                continue;
            }
            System.out.println("GENERIC_CORPUS_FUNCTION workload="
                    + workload.name
                    + " route="
                    + workload.route
                    + " function="
                    + function
                    + " entries="
                    + profile.functionCalls[function]
                    + " dispatches="
                    + profile.functionDispatches[function]);
        }
    }

    private static void printCompact(CorpusWorkload workload, ProfileTotals profile) {
        int reason;
        for (reason = 0; reason < WasmInterpreter.COMPACT_BREAK_REASON_COUNT; reason++) {
            System.out.println("GENERIC_CORPUS_COMPACT_BREAK workload="
                    + workload.name
                    + " route="
                    + workload.route
                    + " reason="
                    + OpcodeNames.breakReason(reason)
                    + " ended="
                    + profile.compactBreaks[reason]
                    + " rejected="
                    + profile.compactRejections[reason]);
        }
        int opcode;
        for (opcode = 0; opcode < profile.compactBreakOpcodes.length; opcode++) {
            if (profile.compactBreakOpcodes[opcode] == 0) {
                continue;
            }
            System.out.println("GENERIC_CORPUS_COMPACT_BREAK_OPCODE workload="
                    + workload.name
                    + " route="
                    + workload.route
                    + " opcode=0x"
                    + OpcodeNames.hex4(opcode)
                    + " label="
                    + OpcodeNames.label(opcode)
                    + " count="
                    + profile.compactBreakOpcodes[opcode]);
        }
        printLengths(
                workload,
                "dispatches",
                profile.compactCandidateDispatchLengths,
                profile.compactAcceptedDispatchLengths);
        printLengths(
                workload,
                "instructions",
                profile.compactCandidateInstructionLengths,
                profile.compactAcceptedInstructionLengths);
    }

    private static void printLengths(CorpusWorkload workload, String unit, long[] candidates, long[] accepted) {
        int length;
        for (length = 0; length < candidates.length; length++) {
            if (candidates[length] == 0 && accepted[length] == 0) {
                continue;
            }
            System.out.println("GENERIC_CORPUS_COMPACT_LENGTH workload="
                    + workload.name
                    + " route="
                    + workload.route
                    + " unit="
                    + unit
                    + " length="
                    + length
                    + " candidates="
                    + candidates[length]
                    + " accepted="
                    + accepted[length]);
        }
    }

    private static final class ProfileTotals {
        private final long[] opcodes = new long[0x10000];
        private final SequenceTotals pairs = new SequenceTotals(2048);
        private final SequenceTotals triples = new SequenceTotals(4096);
        private final long[] functionCalls;
        private final long[] functionDispatches;
        private final long[] compactBreaks = new long[WasmInterpreter.COMPACT_BREAK_REASON_COUNT];
        private final long[] compactBreakOpcodes = new long[0x10000];
        private final long[] compactRejections = new long[WasmInterpreter.COMPACT_BREAK_REASON_COUNT];
        private final long[] compactCandidateDispatchLengths = new long[33];
        private final long[] compactAcceptedDispatchLengths = new long[33];
        private final long[] compactCandidateInstructionLengths = new long[513];
        private final long[] compactAcceptedInstructionLengths = new long[513];
        private long logicalInstructions;
        private long outerDispatches;
        private long compactCandidates;
        private long compactAccepted;

        private ProfileTotals(int functionCount) {
            functionCalls = new long[functionCount];
            functionDispatches = new long[functionCount];
        }

        private void collect(WasmInterpreter interpreter) {
            logicalInstructions += interpreter.instructionsExecuted();
            outerDispatches += interpreter.dispatchesExecuted();
            int opcode;
            for (opcode = 0; opcode < opcodes.length; opcode++) {
                long count = interpreter.opcodeCount(opcode);
                if (count != 0) {
                    opcodes[opcode] += count;
                }
            }
            int slot;
            for (slot = 0; slot < interpreter.opcodePairSlotCount(); slot++) {
                if (!interpreter.opcodePairSlotUsed(slot)) {
                    continue;
                }
                long key = ((long) interpreter.opcodePairFirstAtSlot(slot) << 16)
                        | (long) interpreter.opcodePairSecondAtSlot(slot);
                pairs.add(key, interpreter.opcodePairCountAtSlot(slot));
            }
            for (slot = 0; slot < interpreter.opcodeTripleSlotCount(); slot++) {
                if (!interpreter.opcodeTripleSlotUsed(slot)) {
                    continue;
                }
                long key = ((long) interpreter.opcodeTripleFirstAtSlot(slot) << 32)
                        | ((long) interpreter.opcodeTripleSecondAtSlot(slot) << 16)
                        | (long) interpreter.opcodeTripleThirdAtSlot(slot);
                triples.add(key, interpreter.opcodeTripleCountAtSlot(slot));
            }
            int function;
            for (function = 0; function < functionCalls.length; function++) {
                functionCalls[function] += interpreter.functionCallCount(function);
                functionDispatches[function] += interpreter.functionDispatchCount(function);
            }
            long frameBreaks = 0;
            int reason;
            for (reason = 0; reason < compactBreaks.length; reason++) {
                long count = interpreter.compactProfileBreakCount(reason);
                compactBreaks[reason] += count;
                frameBreaks += count;
                compactRejections[reason] += interpreter.compactProfileRejectionCount(reason);
            }
            for (opcode = 0; opcode < compactBreakOpcodes.length; opcode++) {
                long count = interpreter.compactProfileBreakOpcodeCount(opcode);
                if (count != 0) {
                    compactBreakOpcodes[opcode] += count;
                }
            }
            if (frameBreaks != interpreter.compactProfileCandidateCount()) {
                throw new AssertionError("compact break counts do not cover every candidate");
            }
            compactCandidates += interpreter.compactProfileCandidateCount();
            compactAccepted += interpreter.compactProfileAcceptedCount();
            int length;
            for (length = 0; length < compactCandidateDispatchLengths.length; length++) {
                compactCandidateDispatchLengths[length] +=
                        interpreter.compactProfileCandidateDispatchLengthCount(length);
                compactAcceptedDispatchLengths[length] += interpreter.compactProfileAcceptedDispatchLengthCount(length);
            }
            for (length = 0; length < compactCandidateInstructionLengths.length; length++) {
                compactCandidateInstructionLengths[length] +=
                        interpreter.compactProfileCandidateInstructionLengthCount(length);
                compactAcceptedInstructionLengths[length] +=
                        interpreter.compactProfileAcceptedInstructionLengthCount(length);
            }
        }
    }

    private static final class TierTotals {
        private long logicalInstructions;
        private long outerDispatches;
        private long compactCalls;
        private long compactInstructions;
    }

    private static final class SequenceTotals {
        private long[] keys;
        private long[] counts;
        private boolean[] used;
        private int size;
        private int threshold;

        private SequenceTotals(int initialCapacity) {
            int capacity = 16;
            while (capacity < initialCapacity) {
                capacity <<= 1;
            }
            keys = new long[capacity];
            counts = new long[capacity];
            used = new boolean[capacity];
            threshold = capacity / 2;
        }

        private void add(long key, long count) {
            int slot = findSlot(key);
            if (used[slot]) {
                counts[slot] += count;
                return;
            }
            if (size >= threshold) {
                resize();
                slot = findSlot(key);
            }
            used[slot] = true;
            keys[slot] = key;
            counts[slot] = count;
            size++;
        }

        private long get(long key) {
            int slot = findSlot(key);
            return used[slot] ? counts[slot] : 0;
        }

        private long[] sortedKeys() {
            long[] result = new long[size];
            int output = 0;
            int slot;
            for (slot = 0; slot < used.length; slot++) {
                if (used[slot]) {
                    result[output++] = keys[slot]; // NOPMD -- Compact Java 1.3 cursor bytecode.
                }
            }
            Arrays.sort(result);
            return result;
        }

        private int findSlot(long key) {
            final int mask = keys.length - 1;
            int mixed = (int) (key ^ (key >>> 32));
            mixed ^= mixed >>> 16;
            mixed *= 0x7feb352d;
            mixed ^= mixed >>> 15;
            mixed *= 0x846ca68b;
            mixed ^= mixed >>> 16;
            int slot = mixed & mask;
            while (used[slot] && keys[slot] != key) {
                slot = (slot + 1) & mask;
            }
            return slot;
        }

        private void resize() {
            long[] oldKeys = keys;
            final long[] oldCounts = counts;
            final boolean[] oldUsed = used;
            keys = new long[oldKeys.length << 1];
            counts = new long[keys.length];
            used = new boolean[keys.length];
            threshold = keys.length / 2;
            int oldSize = size;
            size = 0;
            int slot;
            for (slot = 0; slot < oldUsed.length; slot++) {
                if (oldUsed[slot]) {
                    add(oldKeys[slot], oldCounts[slot]);
                }
            }
            if (size != oldSize) {
                throw new IllegalStateException("profile sequence table lost entries");
            }
        }
    }
}
