package w4me;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import w4me.runtime.Wasm4Runtime;
import w4me.wasm.WasmInterpreter;
import w4me.wasm.WasmModule;

/** Provides the untangle runtime benchmark implementation. */
public final class UntangleRuntimeBenchmark {
    private static final String[] MODES = {"optimized", "trace-off", "fusion-only", "baseline"};
    private static final int PHASES = 6;
    private static final int[] PROFILE_FRAMES = {70, 153, 168, 386};

    private UntangleRuntimeBenchmark() {}

    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("usage: font.bin untangle.wasm");
        }
        byte[] font = readFile(arguments[0]);
        byte[] cartridge = readFile(arguments[1]);
        int mode;
        for (mode = 0; mode < MODES.length; mode++) {
            warmUp(MODES[mode], font, cartridge);
        }
        for (mode = 0; mode < MODES.length; mode++) {
            benchmark(MODES[mode], font, cartridge);
        }
        profileRepresentativeFrames(font, cartridge);
    }

    private static void warmUp(String mode, byte[] font, byte[] cartridge) throws Exception {
        boolean extended = !"baseline".equals(mode);
        boolean compact = "optimized".equals(mode) || "trace-off".equals(mode);
        final boolean trace = "optimized".equals(mode) || "fusion-only".equals(mode);
        WasmModule module = WasmModule.read(cartridge, null, extended);
        Wasm4Runtime runtime = new Wasm4Runtime(font);
        runtime.initialize(module);
        WasmInterpreter interpreter = new WasmInterpreter(module, runtime);
        interpreter.setFastPathsEnabled(false);
        interpreter.setCompactExecutorEnabled(compact);
        interpreter.setTraceExecutorEnabled(trace);
        interpreter.setInstructionLimit(200000000L);
        interpreter.invokeCartridgeLifecycle();
        int fastPaths = 0;
        int frame;
        for (frame = 0; frame < UntangleBenchmarkRoute.FRAMES; frame++) {
            update(module, runtime, interpreter, frame);
            fastPaths += interpreter.fastPathCalls();
        }
        requireExactResult(module, fastPaths);
        runtime.close();
        module.close();
    }

    private static void benchmark(String mode, byte[] font, byte[] cartridge) throws Exception {
        boolean extended = !"baseline".equals(mode);
        boolean compact = "optimized".equals(mode) || "trace-off".equals(mode);
        final boolean trace = "optimized".equals(mode) || "fusion-only".equals(mode);

        long parseStarted = System.nanoTime();
        WasmModule module = WasmModule.read(cartridge, null, extended);
        final long parseNanos = System.nanoTime() - parseStarted;
        Wasm4Runtime runtime = new Wasm4Runtime(font);
        runtime.initialize(module);
        WasmInterpreter interpreter = new WasmInterpreter(module, runtime);
        interpreter.setFastPathsEnabled(false);
        interpreter.setCompactExecutorEnabled(compact);
        interpreter.setTraceExecutorEnabled(trace);
        interpreter.setInstructionLimit(200000000L);
        long lifecycleStarted = System.nanoTime();
        interpreter.invokeCartridgeLifecycle();
        final long lifecycleNanos = System.nanoTime() - lifecycleStarted;

        long[] phaseNanos = new long[PHASES];
        long[] phaseInstructions = new long[PHASES];
        long[] phaseDispatches = new long[PHASES];
        int[] phaseFrames = new int[PHASES];
        long totalNanos = 0;
        long instructions = 0;
        long dispatches = 0;
        long compactBlocks = 0;
        long compactInstructions = 0;
        long traceLoops = 0;
        long traceIterations = 0;
        int compactFrames = 0;
        long maximumInstructions = 0;
        int maximumInstructionsFrame = -1;
        int fastPaths = 0;
        long maximumNanos = 0;
        int maximumFrame = -1;
        int frame;
        for (frame = 0; frame < UntangleBenchmarkRoute.FRAMES; frame++) {
            int phase = UntangleBenchmarkRoute.phase(frame);
            long started = System.nanoTime();
            update(module, runtime, interpreter, frame);
            long elapsed = System.nanoTime() - started;
            totalNanos += elapsed;
            phaseNanos[phase] += elapsed;
            phaseFrames[phase]++;
            long frameInstructions = interpreter.instructionsExecuted();
            final int frameCompactBlocks = interpreter.compactBlockCalls();
            instructions += frameInstructions;
            dispatches += interpreter.dispatchesExecuted();
            phaseInstructions[phase] += frameInstructions;
            phaseDispatches[phase] += interpreter.dispatchesExecuted();
            compactBlocks += frameCompactBlocks;
            compactInstructions += interpreter.compactInstructionsExecuted();
            traceLoops += interpreter.traceLoopCalls();
            traceIterations += interpreter.traceLoopIterations();
            fastPaths += interpreter.fastPathCalls();
            if (elapsed > maximumNanos) {
                maximumNanos = elapsed;
                maximumFrame = frame;
            }
            if (frameCompactBlocks != 0) {
                compactFrames++;
            }
            if (frameInstructions > maximumInstructions) {
                maximumInstructions = frameInstructions;
                maximumInstructionsFrame = frame;
            }
        }
        requireExactResult(module, fastPaths);
        if (compact && compactFrames != 0) {
            throw new AssertionError("short Untangle route entered compact tier on " + compactFrames + " frame(s)");
        }

        System.out.println("UNTANGLE_BENCH mode="
                + mode
                + " frames="
                + UntangleBenchmarkRoute.FRAMES
                + " parse-us="
                + micros(parseNanos)
                + " lifecycle-us="
                + micros(lifecycleNanos)
                + " update-average-us="
                + micros(totalNanos / UntangleBenchmarkRoute.FRAMES)
                + " update-maximum-us="
                + micros(maximumNanos)
                + " maximum-frame="
                + maximumFrame
                + " instructions-average="
                + instructions / UntangleBenchmarkRoute.FRAMES
                + " dispatches-average="
                + dispatches / UntangleBenchmarkRoute.FRAMES
                + " compact-blocks-average="
                + compactBlocks / UntangleBenchmarkRoute.FRAMES
                + " compact-instructions-average="
                + compactInstructions / UntangleBenchmarkRoute.FRAMES
                + " compact-frames="
                + compactFrames
                + " maximum-instructions="
                + maximumInstructions
                + " maximum-instructions-frame="
                + maximumInstructionsFrame
                + " trace-loops-average="
                + traceLoops / UntangleBenchmarkRoute.FRAMES
                + " trace-iterations-average="
                + traceIterations / UntangleBenchmarkRoute.FRAMES
                + " fast-paths="
                + fastPaths
                + " framebuffer-fnv1a=bc0231d9");
        int phase;
        for (phase = 0; phase < PHASES; phase++) {
            System.out.println("UNTANGLE_PHASE mode="
                    + mode
                    + " phase="
                    + UntangleBenchmarkRoute.phaseName(phase)
                    + " frames="
                    + phaseFrames[phase]
                    + " update-average-us="
                    + micros(phaseNanos[phase] / phaseFrames[phase])
                    + " instructions-average="
                    + phaseInstructions[phase] / phaseFrames[phase]
                    + " dispatches-average="
                    + phaseDispatches[phase] / phaseFrames[phase]);
        }
        runtime.close();
        module.close();
    }

    private static void profileRepresentativeFrames(byte[] font, byte[] cartridge) throws Exception {
        WasmModule module = WasmModule.read(cartridge, null, true);
        Wasm4Runtime runtime = new Wasm4Runtime(font);
        runtime.initialize(module);
        WasmInterpreter interpreter = new WasmInterpreter(module, runtime);
        interpreter.setFastPathsEnabled(false);
        interpreter.setInstructionLimit(200000000L);
        interpreter.invokeCartridgeLifecycle();
        int target = 0;
        int frame;
        for (frame = 0; frame < UntangleBenchmarkRoute.FRAMES; frame++) {
            boolean profile = target < PROFILE_FRAMES.length && PROFILE_FRAMES[target] == frame;
            interpreter.setProfilingEnabled(profile);
            update(module, runtime, interpreter, frame);
            if (profile) {
                printHotW4IrOpcodes(frame, interpreter);
                printHotFunctions(frame, module, interpreter);
                target++;
            }
        }
        requireExactResult(module, 0);
        runtime.close();
        module.close();
    }

    private static void printHotW4IrOpcodes(int frame, WasmInterpreter interpreter) {
        StringBuffer output = new StringBuffer("UNTANGLE_PROFILE frame=");
        output.append(frame);
        output.append(" w4ir-opcodes=");
        boolean[] emitted = new boolean[64];
        int rank;
        for (rank = 0; rank < 8; rank++) {
            int best = -1;
            long bestCount = 0;
            int opcode;
            for (opcode = 0x1000; opcode < 0x1040; opcode++) {
                long count = interpreter.opcodeCount(opcode);
                if (!emitted[opcode - 0x1000] && count > bestCount) {
                    best = opcode;
                    bestCount = count;
                }
            }
            if (best < 0) {
                break;
            }
            emitted[best - 0x1000] = true;
            if (rank != 0) {
                output.append(',');
            }
            output.append(Integer.toHexString(best));
            output.append(':');
            output.append(bestCount);
        }
        System.out.println(output.toString());
    }

    private static void printHotFunctions(int frame, WasmModule module, WasmInterpreter interpreter) {
        StringBuffer output = new StringBuffer("UNTANGLE_PROFILE frame=");
        output.append(frame);
        output.append(" functions=");
        boolean[] emitted = new boolean[module.functionCount()];
        int rank;
        for (rank = 0; rank < 8; rank++) {
            int best = -1;
            long bestCount = 0;
            int function;
            for (function = 0; function < module.functionCount(); function++) {
                long count = interpreter.functionDispatchCount(function);
                if (!emitted[function] && count > bestCount) {
                    best = function;
                    bestCount = count;
                }
            }
            if (best < 0) {
                break;
            }
            emitted[best] = true;
            if (rank != 0) {
                output.append(',');
            }
            output.append(best);
            output.append(':');
            output.append(bestCount);
        }
        System.out.println(output.toString());
    }

    private static void update(WasmModule module, Wasm4Runtime runtime, WasmInterpreter interpreter, int frame)
            throws Exception {
        runtime.beginFrame(
                module,
                0,
                UntangleBenchmarkRoute.mouseX(frame),
                UntangleBenchmarkRoute.mouseY(frame),
                UntangleBenchmarkRoute.mouseButtons(frame));
        interpreter.invoke("update");
        runtime.endFrame();
    }

    private static void requireExactResult(WasmModule module, int fastPaths) {
        int actual = FramebufferOracle.fnv1a(module);
        if (actual != UntangleBenchmarkRoute.FINAL_FRAMEBUFFER_FNV1A) {
            throw new AssertionError("Untangle final framebuffer mismatch: " + Integer.toHexString(actual));
        }
        if (fastPaths != 0) {
            throw new AssertionError("Untangle used cartridge-specific fast paths");
        }
    }

    private static long micros(long nanos) {
        return nanos / 1000L;
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
