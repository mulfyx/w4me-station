package w4me.wasm;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import w4me.FramebufferOracle;
import w4me.UntangleBenchmarkRoute;
import w4me.runtime.Wasm4Runtime;

/** Deterministic per-function profile of extended W4IR fusion coverage. */
public final class W4IrFusionProfile {
    private static final int PLASMA_FRAMES = 60;
    private static final int PLASMA_FINAL_FNV1A = 0xd71ce5dc;
    private static final int W4IR_FIRST = 0x1000;
    private static final int W4IR_LAST = 0x102f;
    private static final int FUNCTION_RANKS = 16;

    private W4IrFusionProfile() {}

    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 3) {
            throw new IllegalArgumentException("usage: font.bin plasma-cube.wasm untangle.wasm");
        }
        byte[] font = readFile(arguments[0]);
        profile("plasma-cube", font, readFile(arguments[1]), PLASMA_FRAMES);
        profile("untangle", font, readFile(arguments[2]), UntangleBenchmarkRoute.FRAMES);
    }

    private static void profile(String name, byte[] font, byte[] cartridge, int frames) throws Exception {
        WasmModule extendedModule = WasmModule.read(cartridge, null, true, false);
        WasmModule baselineModule = WasmModule.read(cartridge, null, false, false);
        Wasm4Runtime extendedRuntime = new Wasm4Runtime(font);
        Wasm4Runtime baselineRuntime = new Wasm4Runtime(font);
        try {
            extendedRuntime.initialize(extendedModule);
            baselineRuntime.initialize(baselineModule);
            WasmInterpreter extended = interpreter(extendedModule, extendedRuntime);
            WasmInterpreter baseline = interpreter(baselineModule, baselineRuntime);
            extended.invokeCartridgeLifecycle();
            baseline.invokeCartridgeLifecycle();
            assertState(name, -1, extendedModule, baselineModule);

            long[] extendedDispatches = new long[extendedModule.functionCount()];
            long[] baselineDispatches = new long[baselineModule.functionCount()];
            long[] extendedCalls = new long[extendedModule.functionCount()];
            long[] baselineCalls = new long[baselineModule.functionCount()];
            long[] opcodeTotals = new long[W4IR_LAST - W4IR_FIRST + 1];
            long extendedTotal = 0;
            long baselineTotal = 0;
            long logicalTotal = 0;
            int frame;
            for (frame = 0; frame < frames; frame++) {
                update(name, frame, extendedModule, extendedRuntime, extended);
                update(name, frame, baselineModule, baselineRuntime, baseline);
                assertState(name, frame, extendedModule, baselineModule);
                if (extended.instructionsExecuted() != baseline.instructionsExecuted()) {
                    throw new AssertionError(name
                            + " logical instruction mismatch at frame "
                            + frame
                            + ": extended="
                            + extended.instructionsExecuted()
                            + ", baseline="
                            + baseline.instructionsExecuted());
                }
                if (extended.fastPathCalls() != 0 || baseline.fastPathCalls() != 0) {
                    throw new AssertionError(name + " used a cartridge-specific fast path");
                }
                logicalTotal += extended.instructionsExecuted();
                extendedTotal += extended.dispatchesExecuted();
                baselineTotal += baseline.dispatchesExecuted();
                collectFunctions(extendedModule, extended, extendedDispatches, extendedCalls);
                collectFunctions(baselineModule, baseline, baselineDispatches, baselineCalls);
                collectOpcodes(extended, opcodeTotals);
            }
            int expected =
                    "plasma-cube".equals(name) ? PLASMA_FINAL_FNV1A : UntangleBenchmarkRoute.FINAL_FRAMEBUFFER_FNV1A;
            int actual = FramebufferOracle.fnv1a(extendedModule);
            if (actual != expected) {
                throw new AssertionError(name + " final framebuffer mismatch: " + Integer.toHexString(actual));
            }
            assertTierSelection(name, extendedModule, baselineModule, extendedTotal, baselineTotal);

            System.out.println("W4IR_FUSION_PROFILE cart="
                    + name
                    + " frames="
                    + frames
                    + " tier=pattern-f32 memory=65536 globals=exact fast-paths=0 logical="
                    + logicalTotal
                    + " extended-dispatches="
                    + extendedTotal
                    + " baseline-dispatches="
                    + baselineTotal
                    + " dispatches-saved="
                    + (baselineTotal - extendedTotal));
            printOpcodeTotals(name, opcodeTotals);
            printFunctions(
                    name,
                    extendedModule,
                    baselineModule,
                    extendedDispatches,
                    baselineDispatches,
                    extendedCalls,
                    baselineCalls);
        } finally {
            extendedRuntime.close();
            baselineRuntime.close();
            extendedModule.close();
            baselineModule.close();
        }
    }

    private static void assertTierSelection(
            String name,
            WasmModule tieredModule,
            WasmModule baselineModule,
            long tieredDispatches,
            long baselineDispatches) {
        int tieredOpcodes = countStaticExtended(tieredModule);
        int baselineOpcodes = countStaticExtended(baselineModule);
        if (baselineOpcodes != 0) {
            throw new AssertionError(name + " baseline contains extended W4IR opcodes");
        }
        if ("plasma-cube".equals(name)) {
            if (tieredOpcodes == 0 || baselineDispatches - tieredDispatches < 52000000L) {
                throw new AssertionError("Plasma pattern tier did not retain the float fusion hot path");
            }
        } else if ("untangle".equals(name)) {
            if (tieredOpcodes == 0 || tieredDispatches >= baselineDispatches) {
                throw new AssertionError("Untangle integer/control functions did not enter the integer fusion tier");
            }
        }
    }

    private static int countStaticExtended(WasmModule module) {
        int count = 0;
        int functionIndex;
        for (functionIndex = 0; functionIndex < module.functions.length; functionIndex++) {
            WasmModule.FunctionBody body = module.functions[functionIndex];
            if (body == null || body.code == null) {
                continue;
            }
            int instructionIndex;
            for (instructionIndex = 0; instructionIndex < body.instructionCount(); instructionIndex++) {
                int opcode = WasmModule.originalOpcode(body.code[instructionIndex * WasmModule.W4IR_STRIDE] & 0xffff);
                if (isExtendedFusion(opcode)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static WasmInterpreter interpreter(WasmModule module, Wasm4Runtime runtime) {
        WasmInterpreter result = new WasmInterpreter(module, runtime);
        result.setFastPathsEnabled(false);
        result.setCompactExecutorEnabled(false);
        result.setTraceExecutorEnabled(false);
        result.setInstructionLimit(200000000L);
        result.setProfilingEnabled(true);
        return result;
    }

    private static void update(
            String name, int frame, WasmModule module, Wasm4Runtime runtime, WasmInterpreter interpreter)
            throws Exception {
        int mouseX = 0;
        int mouseY = 0;
        int mouseButtons = 0;
        if ("untangle".equals(name)) {
            mouseX = UntangleBenchmarkRoute.mouseX(frame);
            mouseY = UntangleBenchmarkRoute.mouseY(frame);
            mouseButtons = UntangleBenchmarkRoute.mouseButtons(frame);
        }
        runtime.beginFrame(module, 0, mouseX, mouseY, mouseButtons);
        interpreter.invoke("update");
        runtime.endFrame();
    }

    private static void collectFunctions(
            WasmModule module, WasmInterpreter interpreter, long[] dispatches, long[] calls) {
        int index;
        for (index = 0; index < module.functionCount(); index++) {
            dispatches[index] += interpreter.functionDispatchCount(index);
            calls[index] += interpreter.functionCallCount(index);
        }
    }

    private static void collectOpcodes(WasmInterpreter interpreter, long[] totals) {
        int opcode;
        for (opcode = W4IR_FIRST; opcode <= W4IR_LAST; opcode++) {
            totals[opcode - W4IR_FIRST] += interpreter.opcodeCount(opcode);
        }
    }

    private static void printOpcodeTotals(String name, long[] totals) {
        StringBuffer output = new StringBuffer("W4IR_FUSION_OPCODES cart=");
        output.append(name);
        output.append(" extended=");
        boolean first = true;
        int index;
        for (index = 0; index < totals.length; index++) {
            int opcode = W4IR_FIRST + index;
            if (totals[index] == 0 || !isExtendedFusion(opcode)) {
                continue;
            }
            if (!first) {
                output.append(',');
            }
            first = false;
            output.append(Integer.toHexString(opcode));
            output.append(':');
            output.append(totals[index]);
        }
        if (first) {
            output.append("none");
        }
        System.out.println(output.toString());
    }

    private static void printFunctions(
            String name,
            WasmModule extendedModule,
            WasmModule baselineModule,
            long[] extendedDispatches,
            long[] baselineDispatches,
            long[] extendedCalls,
            long[] baselineCalls) {
        boolean[] emitted = new boolean[baselineDispatches.length];
        int rank;
        for (rank = 0; rank < FUNCTION_RANKS; rank++) {
            int best = -1;
            long bestCount = 0;
            int index;
            for (index = 0; index < baselineDispatches.length; index++) {
                if (!emitted[index] && baselineDispatches[index] > bestCount) {
                    best = index;
                    bestCount = baselineDispatches[index];
                }
            }
            if (best < 0) {
                break;
            }
            emitted[best] = true;
            WasmModule.FunctionBody extendedBody = extendedModule.functions[best];
            WasmModule.FunctionBody baselineBody = baselineModule.functions[best];
            if (extendedBody == null || baselineBody == null) {
                continue;
            }
            StringBuffer output = new StringBuffer("W4IR_FUSION_FUNCTION cart=");
            output.append(name);
            output.append(" rank=");
            output.append(rank + 1);
            output.append(" function=");
            output.append(best);
            output.append(" instructions=");
            output.append(extendedBody.instructionCount());
            output.append(" calls=");
            output.append(extendedCalls[best]);
            output.append('/');
            output.append(baselineCalls[best]);
            output.append(" dispatches=");
            output.append(extendedDispatches[best]);
            output.append('/');
            output.append(baselineDispatches[best]);
            output.append(" saved=");
            output.append(baselineDispatches[best] - extendedDispatches[best]);
            output.append(" static-extended=");
            appendStaticExtended(output, extendedBody);
            System.out.println(output.toString());
        }
    }

    private static void appendStaticExtended(StringBuffer output, WasmModule.FunctionBody body) {
        int[] counts = new int[W4IR_LAST - W4IR_FIRST + 1];
        int index;
        for (index = 0; index < body.instructionCount(); index++) {
            int opcode = WasmModule.originalOpcode(body.code[index * WasmModule.W4IR_STRIDE] & 0xffff);
            if (opcode >= W4IR_FIRST && opcode <= W4IR_LAST && isExtendedFusion(opcode)) {
                counts[opcode - W4IR_FIRST]++;
            }
        }
        boolean first = true;
        for (index = 0; index < counts.length; index++) {
            if (counts[index] == 0) {
                continue;
            }
            if (!first) {
                output.append(',');
            }
            first = false;
            output.append(Integer.toHexString(W4IR_FIRST + index));
            output.append(':');
            output.append(counts[index]);
        }
        if (first) {
            output.append("none");
        }
    }

    private static boolean isExtendedFusion(int opcode) {
        return (opcode >= 0x1007 && opcode <= 0x100f) || (opcode >= 0x101c && opcode <= 0x102f);
    }

    private static void assertState(String name, int frame, WasmModule extended, WasmModule baseline) {
        int index;
        for (index = 0; index < extended.memory.length; index++) {
            if (extended.memory[index] != baseline.memory[index]) {
                throw new AssertionError(name + " memory mismatch at frame " + frame + ", address " + index);
            }
        }
        if (extended.globals.length != baseline.globals.length) {
            throw new AssertionError(name + " global count mismatch");
        }
        for (index = 0; index < extended.globals.length; index++) {
            if (extended.globals[index] != baseline.globals[index]) {
                throw new AssertionError(name + " global mismatch at frame " + frame + ", index " + index);
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
}
