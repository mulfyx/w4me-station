package w4me.wasm;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import w4me.runtime.Wasm4Runtime;

/** Provides the W4IR direct intrinsic differential smoke implementation. */
public final class W4IrDirectIntrinsicDifferentialSmoke {
    private static final int FRAMES = 60;

    private W4IrDirectIntrinsicDifferentialSmoke() {}

    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("usage: font.bin plasma.wasm");
        }
        byte[] font = readFile(arguments[0]);
        byte[] cartridge = readFile(arguments[1]);
        assertDenseOpcodeMap();
        final int[] storedSites = verifyCachedSpecialization(font, cartridge);

        WasmModule directModule = WasmModule.read(cartridge, null, true);
        WasmModule ordinaryModule = WasmModule.read(cartridge, null, true);
        Wasm4Runtime directRuntime = new Wasm4Runtime(font);
        Wasm4Runtime ordinaryRuntime = new Wasm4Runtime(font);
        directRuntime.initialize(directModule);
        ordinaryRuntime.initialize(ordinaryModule);
        WasmInterpreter direct = new WasmInterpreter(directModule, directRuntime);
        WasmInterpreter ordinary = new WasmInterpreter(ordinaryModule, ordinaryRuntime);
        direct.setFastPathsEnabled(false);
        ordinary.setFastPathsEnabled(false);
        ordinary.setDirectNumericIntrinsicsEnabled(false);
        direct.setInstructionLimit(200000000L);
        ordinary.setInstructionLimit(200000000L);
        direct.invokeCartridgeLifecycle();
        ordinary.invokeCartridgeLifecycle();
        assertState(-1, directModule, ordinaryModule);

        long directDispatches = 0;
        long ordinaryDispatches = 0;
        int frame;
        for (frame = 0; frame < FRAMES; frame++) {
            update(directModule, directRuntime, direct);
            update(ordinaryModule, ordinaryRuntime, ordinary);
            assertState(frame, directModule, ordinaryModule);
            if (direct.instructionsExecuted() != ordinary.instructionsExecuted()) {
                throw new AssertionError("instruction accounting mismatch at frame "
                        + frame
                        + ": direct="
                        + direct.instructionsExecuted()
                        + ", ordinary="
                        + ordinary.instructionsExecuted());
            }
            if (direct.fastPathCalls() != 0 || ordinary.fastPathCalls() != 0) {
                throw new AssertionError("numeric intrinsic differential used a fast path");
            }
            directDispatches += direct.dispatchesExecuted();
            ordinaryDispatches += ordinary.dispatchesExecuted();
        }
        if (directDispatches >= ordinaryDispatches) {
            throw new AssertionError("direct numeric intrinsics did not reduce dispatches: direct="
                    + directDispatches
                    + ", ordinary="
                    + ordinaryDispatches);
        }

        directModule.close();
        ordinaryModule.close();
        directRuntime.close();
        ordinaryRuntime.close();
        System.out.println("PASS W4IR-direct-intrinsic differential frames="
                + FRAMES
                + " dense-map=exact cached-stream=exact cached-frames="
                + FRAMES
                + " memory=65536 globals=exact floor-sites="
                + storedSites[0]
                + " sin-sites="
                + storedSites[1]
                + " direct-dispatches="
                + directDispatches
                + " ordinary-dispatches="
                + ordinaryDispatches);
    }

    private static int[] verifyCachedSpecialization(byte[] font, byte[] cartridge) throws Exception {
        MemoryW4IrStore store = new MemoryW4IrStore();
        WasmModule built = WasmModule.read(cartridge, store, true);
        if (!"RMS-build".equals(built.w4irStatus())) {
            throw new AssertionError("first in-memory W4IR parse did not build the cache");
        }
        int[] sites = store.numericIntrinsicSites();
        if (sites[0] == 0 || sites[1] == 0) {
            throw new AssertionError(
                    "staged W4IR is missing numeric intrinsic sites: floor=" + sites[0] + ", sin=" + sites[1]);
        }
        built.close();

        WasmModule cached = WasmModule.read(cartridge, store, true);
        WasmModule resident = WasmModule.read(cartridge, null, true);
        if (!"RMS-hit".equals(cached.w4irStatus())) {
            throw new AssertionError("second in-memory W4IR parse did not hit the cache");
        }
        assertDecodedStreams(resident, cached);
        Wasm4Runtime cachedRuntime = new Wasm4Runtime(font);
        Wasm4Runtime residentRuntime = new Wasm4Runtime(font);
        cachedRuntime.initialize(cached);
        residentRuntime.initialize(resident);
        WasmInterpreter cachedInterpreter = new WasmInterpreter(cached, cachedRuntime);
        WasmInterpreter residentInterpreter = new WasmInterpreter(resident, residentRuntime);
        cachedInterpreter.setFastPathsEnabled(false);
        residentInterpreter.setFastPathsEnabled(false);
        cachedInterpreter.setInstructionLimit(200000000L);
        residentInterpreter.setInstructionLimit(200000000L);
        cachedInterpreter.invokeCartridgeLifecycle();
        residentInterpreter.invokeCartridgeLifecycle();
        assertState(-1, cached, resident);
        int frame;
        for (frame = 0; frame < FRAMES; frame++) {
            update(cached, cachedRuntime, cachedInterpreter);
            update(resident, residentRuntime, residentInterpreter);
            assertState(frame, cached, resident);
            if (cachedInterpreter.instructionsExecuted() != residentInterpreter.instructionsExecuted()) {
                throw new AssertionError("cached intrinsic instruction accounting mismatch at frame " + frame);
            }
        }
        if (store.pageFaults() == 0) {
            throw new AssertionError("cached intrinsic W4IR executed without loading a page");
        }
        cached.close();
        resident.close();
        cachedRuntime.close();
        residentRuntime.close();
        return sites;
    }

    private static void assertDenseOpcodeMap() {
        int opcode;
        for (opcode = 0; opcode <= 0xc4; opcode++) {
            assertOpcodeRoundTrip(opcode, opcode);
        }
        for (opcode = WasmModule.ORIGINAL_BULK_FIRST; opcode <= WasmModule.ORIGINAL_BULK_LAST; opcode++) {
            assertOpcodeRoundTrip(opcode, WasmModule.EXECUTION_BULK_FIRST + opcode - WasmModule.ORIGINAL_BULK_FIRST);
        }
        for (opcode = WasmModule.ORIGINAL_W4IR_FIRST; opcode <= WasmModule.ORIGINAL_W4IR_LAST; opcode++) {
            assertOpcodeRoundTrip(opcode, WasmModule.EXECUTION_W4IR_FIRST + opcode - WasmModule.ORIGINAL_W4IR_FIRST);
        }
        if (WasmModule.EXECUTION_BULK_FIRST != 0xc5
                || WasmModule.EXECUTION_W4IR_FIRST != WasmModule.EXECUTION_BULK_LAST + 1
                || WasmModule.EXECUTION_W4IR_LAST != 0x103) {
            throw new AssertionError("execution opcode domains are not contiguous");
        }
    }

    private static void assertOpcodeRoundTrip(int original, int execution) {
        int actualExecution = WasmModule.executionOpcode(original);
        if (actualExecution != execution) {
            throw new AssertionError("execution opcode mismatch for 0x"
                    + Integer.toHexString(original)
                    + ": expected 0x"
                    + Integer.toHexString(execution)
                    + ", got 0x"
                    + Integer.toHexString(actualExecution));
        }
        int actualOriginal = WasmModule.originalOpcode(actualExecution);
        if (actualOriginal != original) {
            throw new AssertionError("original opcode mismatch for 0x"
                    + Integer.toHexString(actualExecution)
                    + ": expected 0x"
                    + Integer.toHexString(original)
                    + ", got 0x"
                    + Integer.toHexString(actualOriginal));
        }
    }

    private static void assertDecodedStreams(WasmModule resident, WasmModule cached) {
        if (resident.functions.length != cached.functions.length) {
            throw new AssertionError("cached W4IR function count mismatch");
        }
        int functionIndex;
        for (functionIndex = 0; functionIndex < resident.functions.length; functionIndex++) {
            WasmModule.FunctionBody residentBody = resident.functions[functionIndex];
            WasmModule.FunctionBody cachedBody = cached.functions[functionIndex];
            if (residentBody == null || cachedBody == null) {
                if (residentBody != cachedBody) {
                    throw new AssertionError("cached W4IR imported-function mismatch at " + functionIndex);
                }
                continue;
            }
            if (residentBody.declaredLocalCount != cachedBody.declaredLocalCount
                    || residentBody.instructionCount() != cachedBody.instructionCount()
                    || residentBody.fingerprint != cachedBody.fingerprint
                    || residentBody.intrinsic != cachedBody.intrinsic) {
                throw new AssertionError("cached W4IR metadata mismatch at function " + functionIndex);
            }
            assertBranchTables(functionIndex, residentBody.branchTables, cachedBody.branchTables);
            assertInts(
                    functionIndex, "branch descriptors", residentBody.branchDescriptors, cachedBody.branchDescriptors);
            assertInts(
                    functionIndex,
                    "branch descriptor PCs",
                    residentBody.branchDescriptorPcs,
                    cachedBody.branchDescriptorPcs);
            assertInts(
                    functionIndex,
                    "branch descriptor indices",
                    residentBody.branchDescriptorIndices,
                    cachedBody.branchDescriptorIndices);
            assertBranchTables(functionIndex, residentBody.branchDescriptorTables, cachedBody.branchDescriptorTables);
            int codeLength = residentBody.instructionCount() * WasmModule.W4IR_STRIDE;
            int offset;
            for (offset = 0; offset < codeLength; offset++) {
                int[] cachedPage = cachedBody.codePage(offset);
                int cachedValue = cachedPage[offset - cachedBody.codePageBase(offset)];
                if (residentBody.code[offset] != cachedValue) {
                    throw new AssertionError(
                            "cached W4IR code mismatch at function " + functionIndex + ", int " + offset);
                }
            }
        }
    }

    private static void assertBranchTables(int functionIndex, int[][] resident, int[][] cached) {
        if (resident.length != cached.length) {
            throw new AssertionError("cached W4IR branch-table count mismatch at function " + functionIndex);
        }
        int table;
        for (table = 0; table < resident.length; table++) {
            if (resident[table].length != cached[table].length) {
                throw new AssertionError(
                        "cached W4IR branch-table length mismatch at function " + functionIndex + ", table " + table);
            }
            int index;
            for (index = 0; index < resident[table].length; index++) {
                if (resident[table][index] != cached[table][index]) {
                    throw new AssertionError("cached W4IR branch-table mismatch at function "
                            + functionIndex
                            + ", table "
                            + table
                            + ", index "
                            + index);
                }
            }
        }
    }

    private static void assertInts(int functionIndex, String label, int[] resident, int[] cached) {
        if (resident.length != cached.length) {
            throw new AssertionError("cached W4IR " + label + " length mismatch at function " + functionIndex);
        }
        int index;
        for (index = 0; index < resident.length; index++) {
            if (resident[index] != cached[index]) {
                throw new AssertionError(
                        "cached W4IR " + label + " mismatch at function " + functionIndex + ", index " + index);
            }
        }
    }

    private static void update(WasmModule module, Wasm4Runtime runtime, WasmInterpreter interpreter) throws Exception {
        runtime.beginFrame(module, 0, 0, 0, 0);
        interpreter.invoke("update");
        runtime.endFrame();
    }

    private static void assertState(int frame, WasmModule expected, WasmModule actual) {
        int index;
        for (index = 0; index < expected.memory.length; index++) {
            if (expected.memory[index] != actual.memory[index]) {
                throw new AssertionError("memory mismatch at frame " + frame + ", address " + index);
            }
        }
        if (expected.globals.length != actual.globals.length) {
            throw new AssertionError("global count mismatch");
        }
        for (index = 0; index < expected.globals.length; index++) {
            if (expected.globals[index] != actual.globals[index]) {
                throw new AssertionError("global mismatch at frame " + frame + ", index " + index);
            }
        }
        if (expected.table.length != actual.table.length) {
            throw new AssertionError("table length mismatch");
        }
        for (index = 0; index < expected.table.length; index++) {
            if (expected.table[index] != actual.table[index]) {
                throw new AssertionError("table mismatch at frame " + frame + ", index " + index);
            }
        }
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

    private static final class MemoryW4IrStore implements W4IrStore {
        private W4IrFunction[] functions;
        private int[][][] pages;
        private boolean complete;
        private int faults;

        public boolean isComplete(int functionCount) {
            return complete && functions != null && functions.length == functionCount;
        }

        public W4IrFunction loadFunction(int functionIndex) throws WasmException {
            if (!complete || functionIndex < 0 || functionIndex >= functions.length) {
                throw new WasmException("in-memory W4IR function is unavailable");
            }
            return functions[functionIndex];
        }

        public void begin(int functionCount) {
            functions = new W4IrFunction[functionCount];
            pages = new int[functionCount][][];
            complete = false;
            faults = 0;
        }

        public void writeFunction(
                int functionIndex,
                int declaredLocalCount,
                int[] code,
                int[][] branchTables,
                int[] branchDescriptors,
                int[] branchDescriptorPcs,
                int[] branchDescriptorIndices,
                int[][] branchDescriptorTables,
                long fingerprint,
                int intrinsic) {
            int pageCount = (code.length + W4IrFunction.PAGE_INTS - 1) / W4IrFunction.PAGE_INTS;
            pages[functionIndex] = new int[pageCount][];
            int[] pageIds = new int[pageCount];
            int page;
            for (page = 0; page < pageCount; page++) {
                int offset = page * W4IrFunction.PAGE_INTS;
                int length = code.length - offset;
                if (length > W4IrFunction.PAGE_INTS) {
                    length = W4IrFunction.PAGE_INTS;
                }
                pages[functionIndex][page] = new int[length];
                System.arraycopy(code, offset, pages[functionIndex][page], 0, length);
                pageIds[page] = page + 1;
            }
            functions[functionIndex] = new W4IrFunction(
                    functionIndex,
                    declaredLocalCount,
                    code.length / WasmModule.W4IR_STRIDE,
                    cloneTables(branchTables),
                    (int[]) branchDescriptors.clone(),
                    (int[]) branchDescriptorPcs.clone(),
                    (int[]) branchDescriptorIndices.clone(),
                    cloneTables(branchDescriptorTables),
                    fingerprint,
                    intrinsic,
                    pageIds);
        }

        public void commit() throws WasmException {
            int index;
            for (index = 0; index < functions.length; index++) {
                if (functions[index] == null) {
                    throw new WasmException("in-memory W4IR function was not written");
                }
            }
            complete = true;
        }

        public int[] loadPage(W4IrFunction function, int pageIndex) {
            faults++;
            return pages[function.functionIndex()][pageIndex];
        }

        public int pageFaults() {
            return faults;
        }

        public int pageHits() {
            return 0;
        }

        public void discard() {
            functions = null;
            pages = null;
            complete = false;
        }

        public void close() {
            /* Intentionally no-op. */
        }

        int[] numericIntrinsicSites() {
            int floor = 0;
            int sine = 0;
            int functionIndex;
            for (functionIndex = 0; functionIndex < pages.length; functionIndex++) {
                int page;
                for (page = 0; page < pages[functionIndex].length; page++) {
                    int[] code = pages[functionIndex][page];
                    int offset;
                    for (offset = 0; offset < code.length; offset += WasmModule.W4IR_STRIDE) {
                        int opcode = WasmModule.originalOpcode(code[offset] & 0xffff);
                        if (opcode == WasmModule.W4IR_F32_FLOOR_INTRINSIC) {
                            floor++;
                        } else if (opcode == WasmModule.W4IR_F32_SIN_INTRINSIC) {
                            sine++;
                        }
                    }
                }
            }
            return new int[] {floor, sine};
        }

        private static int[][] cloneTables(int[][] source) {
            int[][] result = new int[source.length][];
            int index;
            for (index = 0; index < source.length; index++) {
                result[index] = (int[]) source[index].clone();
            }
            return result;
        }
    }
}
