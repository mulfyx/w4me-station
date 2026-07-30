package w4me;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import w4me.runtime.Wasm4Runtime;
import w4me.wasm.WasmInterpreter;
import w4me.wasm.WasmModule;

/** Provides the runtime benchmark implementation. */
public final class RuntimeBenchmark {
    private static final int WARMUP_FRAMES = 5;
    private static final int SAMPLE_FRAMES = 30;
    private static final int RENDER_SAMPLES = 200;

    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 4) {
            throw new IllegalArgumentException("usage: font.bin mandelbrot.wasm duck-maze.wasm plasma-cube.wasm");
        }
        byte[] font = readFile(arguments[0]);
        byte[] mandelbrot = readFile(arguments[1]);
        benchmarkMandelbrotFirstUpdate(font, mandelbrot);
        benchmark("mandelbrot-steady-state", font, mandelbrot, true, true, true, true, true, false);
        benchmark("duck-maze-gameplay", font, readFile(arguments[2]), true, true, true, true, true, true);
        byte[] plasma = readFile(arguments[3]);
        benchmark("plasma-cube", font, plasma, true, true, true, true, true, false);
        benchmark("plasma-cube-generic", font, plasma, false, true, true, true, true, false);
        benchmark("plasma-cube-generic-direct-intrinsics-off", font, plasma, false, true, true, true, false, false);
        benchmark("plasma-cube-generic-trace-off", font, plasma, false, true, true, false, true, false);
        benchmark("plasma-cube-generic-fusion-only", font, plasma, false, true, false, true, true, false);
        benchmark("plasma-cube-generic-baseline", font, plasma, false, false, false, true, false, false);
    }

    private static void benchmarkMandelbrotFirstUpdate(byte[] font, byte[] cartridge) throws Exception {
        long parseStarted = System.nanoTime();
        WasmModule module = WasmModule.read(cartridge);
        long parseNanos = System.nanoTime() - parseStarted;
        Wasm4Runtime runtime = new Wasm4Runtime(font);
        try {
            runtime.initialize(module);
            WasmInterpreter interpreter = new WasmInterpreter(module, runtime);
            interpreter.setInstructionLimit(200000000L);
            interpreter.invokeCartridgeLifecycle();
            long updateStarted = System.nanoTime();
            update(module, runtime, interpreter, 0);
            long updateNanos = System.nanoTime() - updateStarted;
            System.out.println("BENCH cart=mandelbrot-first-update"
                    + " parse-us="
                    + nanosToMicros(parseNanos)
                    + " update-us="
                    + nanosToMicros(updateNanos)
                    + " instructions="
                    + interpreter.instructionsExecuted()
                    + " dispatches="
                    + interpreter.dispatchesExecuted()
                    + " fast-paths="
                    + interpreter.fastPathCalls()
                    + " compact-blocks="
                    + interpreter.compactBlockCalls()
                    + " compact-instructions="
                    + interpreter.compactInstructionsExecuted()
                    + " trace-loops="
                    + interpreter.traceLoopCalls()
                    + " trace-iterations="
                    + interpreter.traceLoopIterations()
                    + " framebuffer-fnv1a="
                    + Integer.toHexString(FramebufferOracle.fnv1a(module)));
        } finally {
            runtime.close();
            module.close();
        }
    }

    private static void benchmark(
            String name,
            byte[] font,
            byte[] cartridge,
            boolean fastPathsEnabled,
            boolean extendedFusionsEnabled,
            boolean compactExecutorEnabled,
            boolean traceExecutorEnabled,
            boolean directNumericIntrinsicsEnabled,
            boolean duckGameplay)
            throws Exception {
        long parseStarted = System.nanoTime();
        WasmModule module = WasmModule.read(cartridge, null, extendedFusionsEnabled);
        final long parseNanos = System.nanoTime() - parseStarted;
        Wasm4Runtime runtime = new Wasm4Runtime(font);
        runtime.initialize(module);
        WasmInterpreter interpreter = new WasmInterpreter(module, runtime);
        interpreter.setFastPathsEnabled(fastPathsEnabled);
        interpreter.setCompactExecutorEnabled(compactExecutorEnabled);
        interpreter.setTraceExecutorEnabled(traceExecutorEnabled);
        interpreter.setDirectNumericIntrinsicsEnabled(directNumericIntrinsicsEnabled);
        interpreter.setInstructionLimit(200000000L);
        interpreter.invokeCartridgeLifecycle();

        int gamepad = 0;
        if (duckGameplay) {
            update(module, runtime, interpreter, 0);
            update(module, runtime, interpreter, 1);
            update(module, runtime, interpreter, 0);
            updates(module, runtime, interpreter, 128, 32);
            updates(module, runtime, interpreter, 32, 24);
            updates(module, runtime, interpreter, 128, 16);
            updates(module, runtime, interpreter, 32, 32);
            gamepad = 64;
        }

        int index;
        for (index = 0; index < WARMUP_FRAMES; index++) {
            update(module, runtime, interpreter, gamepad);
        }

        long totalNanos = 0;
        long minimumNanos = Long.MAX_VALUE;
        long maximumNanos = 0;
        long instructions = 0;
        long dispatches = 0;
        long fastPaths = 0;
        long compactBlocks = 0;
        long compactInstructions = 0;
        long traceLoops = 0;
        long traceIterations = 0;
        for (index = 0; index < SAMPLE_FRAMES; index++) {
            long started = System.nanoTime();
            update(module, runtime, interpreter, gamepad);
            long elapsed = System.nanoTime() - started;
            totalNanos += elapsed;
            if (elapsed < minimumNanos) {
                minimumNanos = elapsed;
            }
            if (elapsed > maximumNanos) {
                maximumNanos = elapsed;
            }
            instructions += interpreter.instructionsExecuted();
            dispatches += interpreter.dispatchesExecuted();
            fastPaths += interpreter.fastPathCalls();
            compactBlocks += interpreter.compactBlockCalls();
            compactInstructions += interpreter.compactInstructionsExecuted();
            traceLoops += interpreter.traceLoopCalls();
            traceIterations += interpreter.traceLoopIterations();
        }

        int[] pixels = new int[Wasm4Runtime.WIDTH * Wasm4Runtime.HEIGHT];
        long renderStarted = System.nanoTime();
        for (index = 0; index < RENDER_SAMPLES; index++) {
            runtime.copyArgb(module, pixels);
        }
        long renderNanos = System.nanoTime() - renderStarted;

        interpreter.setProfilingEnabled(true);
        update(module, runtime, interpreter, gamepad);

        System.out.println("BENCH cart="
                + name
                + " parse-us="
                + nanosToMicros(parseNanos)
                + " update-avg-us="
                + nanosToMicros(totalNanos / SAMPLE_FRAMES)
                + " update-min-us="
                + nanosToMicros(minimumNanos)
                + " update-max-us="
                + nanosToMicros(maximumNanos)
                + " instructions-avg="
                + instructions / SAMPLE_FRAMES
                + " dispatches-avg="
                + dispatches / SAMPLE_FRAMES
                + " fast-paths-avg="
                + fastPaths / SAMPLE_FRAMES
                + " compact-blocks-avg="
                + compactBlocks / SAMPLE_FRAMES
                + " compact-instructions-avg="
                + compactInstructions / SAMPLE_FRAMES
                + " trace-loops-avg="
                + traceLoops / SAMPLE_FRAMES
                + " trace-iterations-avg="
                + traceIterations / SAMPLE_FRAMES
                + " vm-mips="
                + mips(instructions, totalNanos)
                + " unpack-avg-us="
                + nanosToMicros(renderNanos / RENDER_SAMPLES));
        printHotOpcodes(interpreter);
        printHotW4IrOpcodes(interpreter);
        printHotOpcodePairs(interpreter);
        printHotW4IrOpcodePairs(interpreter);
        printHotFunctions(module, interpreter);
        printHotFunctionDispatches(module, interpreter);
        printFunctionFingerprints(module);
    }

    private static void updates(
            WasmModule module, Wasm4Runtime runtime, WasmInterpreter interpreter, int gamepad, int count)
            throws Exception {
        int index;
        for (index = 0; index < count; index++) {
            update(module, runtime, interpreter, gamepad);
        }
    }

    private static void update(WasmModule module, Wasm4Runtime runtime, WasmInterpreter interpreter, int gamepad)
            throws Exception {
        runtime.beginFrame(module, gamepad, 0, 0, 0);
        interpreter.invoke("update");
        runtime.endFrame();
    }

    private static void printHotOpcodes(WasmInterpreter interpreter) {
        StringBuffer output = new StringBuffer("PROFILE opcodes=");
        int rank;
        for (rank = 0; rank < 8; rank++) {
            int bestOpcode = -1;
            long bestCount = 0;
            int opcode;
            for (opcode = 0; opcode < 256; opcode++) {
                long count = interpreter.opcodeCount(opcode);
                if (count > bestCount && !containsOpcode(output, opcode)) {
                    bestOpcode = opcode;
                    bestCount = count;
                }
            }
            if (bestOpcode < 0) {
                break;
            }
            if (rank != 0) {
                output.append(',');
            }
            output.append(hex2(bestOpcode));
            output.append(':');
            output.append(bestCount);
        }
        System.out.println(output.toString());
    }

    private static void printHotW4IrOpcodes(WasmInterpreter interpreter) {
        StringBuffer output = new StringBuffer("PROFILE w4ir-opcodes=");
        int rank;
        for (rank = 0; rank < 8; rank++) {
            int bestOpcode = -1;
            long bestCount = 0;
            int opcode;
            for (opcode = 0x1000; opcode < 0x1040; opcode++) {
                long count = interpreter.opcodeCount(opcode);
                if (count > bestCount && !containsOpcode(output, opcode)) {
                    bestOpcode = opcode;
                    bestCount = count;
                }
            }
            if (bestOpcode < 0) {
                break;
            }
            if (rank != 0) {
                output.append(',');
            }
            output.append(Integer.toHexString(bestOpcode));
            output.append(':');
            output.append(bestCount);
        }
        System.out.println(output.toString());
    }

    private static boolean containsOpcode(StringBuffer output, int opcode) {
        String marker = (opcode < 256 ? hex2(opcode) : Integer.toHexString(opcode)) + ":";
        return output.toString().indexOf(marker) >= 0;
    }

    private static void printHotOpcodePairs(WasmInterpreter interpreter) {
        StringBuffer output = new StringBuffer("PROFILE pairs=");
        boolean[] emitted = new boolean[65536];
        int rank;
        for (rank = 0; rank < 12; rank++) {
            int bestPair = -1;
            long bestCount = 0;
            int pair;
            for (pair = 0; pair < emitted.length; pair++) {
                long count = interpreter.opcodePairCount(pair >>> 8, pair & 0xff);
                if (!emitted[pair] && count > bestCount) {
                    bestPair = pair;
                    bestCount = count;
                }
            }
            if (bestPair < 0) {
                break;
            }
            emitted[bestPair] = true;
            if (rank != 0) {
                output.append(',');
            }
            output.append(hex2(bestPair >>> 8));
            output.append('+');
            output.append(hex2(bestPair));
            output.append(':');
            output.append(bestCount);
        }
        System.out.println(output.toString());
    }

    private static void printHotW4IrOpcodePairs(WasmInterpreter interpreter) {
        StringBuffer output = new StringBuffer("PROFILE w4ir-pairs=");
        int[] emittedFirst = new int[16];
        int[] emittedSecond = new int[16];
        int rank;
        for (rank = 0; rank < emittedFirst.length; rank++) {
            int bestFirst = -1;
            int bestSecond = -1;
            long bestCount = 0;
            int first;
            for (first = 0; first < 0x1040; first++) {
                if (first >= 256 && first < 0x1000) {
                    first = 0x0fff;
                    continue;
                }
                int second;
                for (second = 0; second < 0x1040; second++) {
                    if (second >= 256 && second < 0x1000) {
                        second = 0x0fff;
                        continue;
                    }
                    long count = interpreter.w4irOpcodePairCount(first, second);
                    if (count > bestCount && !containsPair(emittedFirst, emittedSecond, rank, first, second)) {
                        bestFirst = first;
                        bestSecond = second;
                        bestCount = count;
                    }
                }
            }
            if (bestFirst < 0) {
                break;
            }
            emittedFirst[rank] = bestFirst;
            emittedSecond[rank] = bestSecond;
            if (rank != 0) {
                output.append(',');
            }
            output.append(Integer.toHexString(bestFirst));
            output.append('+');
            output.append(Integer.toHexString(bestSecond));
            output.append(':');
            output.append(bestCount);
        }
        System.out.println(output.toString());
    }

    private static boolean containsPair(int[] firstValues, int[] secondValues, int count, int first, int second) {
        int index;
        for (index = 0; index < count; index++) {
            if (firstValues[index] == first && secondValues[index] == second) {
                return true;
            }
        }
        return false;
    }

    private static void printHotFunctions(WasmModule module, WasmInterpreter interpreter) {
        StringBuffer output = new StringBuffer("PROFILE functions=");
        int rank;
        boolean[] emitted = new boolean[module.functionCount()];
        for (rank = 0; rank < 8; rank++) {
            int bestFunction = -1;
            long bestCount = 0;
            int functionIndex;
            for (functionIndex = 0; functionIndex < module.functionCount(); functionIndex++) {
                long count = interpreter.functionCallCount(functionIndex);
                if (!emitted[functionIndex] && count > bestCount) {
                    bestFunction = functionIndex;
                    bestCount = count;
                }
            }
            if (bestFunction < 0) {
                break;
            }
            emitted[bestFunction] = true;
            if (rank != 0) {
                output.append(',');
            }
            output.append(bestFunction);
            output.append(':');
            output.append(bestCount);
        }
        System.out.println(output.toString());
    }

    private static void printHotFunctionDispatches(WasmModule module, WasmInterpreter interpreter) {
        StringBuffer output = new StringBuffer("PROFILE function-dispatches=");
        int rank;
        boolean[] emitted = new boolean[module.functionCount()];
        for (rank = 0; rank < 8; rank++) {
            int bestFunction = -1;
            long bestCount = 0;
            int functionIndex;
            for (functionIndex = 0; functionIndex < module.functionCount(); functionIndex++) {
                long count = interpreter.functionDispatchCount(functionIndex);
                if (!emitted[functionIndex] && count > bestCount) {
                    bestFunction = functionIndex;
                    bestCount = count;
                }
            }
            if (bestFunction < 0) {
                break;
            }
            emitted[bestFunction] = true;
            if (rank != 0) {
                output.append(',');
            }
            output.append(bestFunction);
            output.append(':');
            output.append(bestCount);
        }
        System.out.println(output.toString());
    }

    private static void printFunctionFingerprints(WasmModule module) {
        StringBuffer output = new StringBuffer("PROFILE fingerprints=");
        int functionIndex;
        for (functionIndex = 0; functionIndex < module.functionCount(); functionIndex++) {
            long fingerprint = module.functionFingerprint(functionIndex);
            if (fingerprint == 0) {
                continue;
            }
            if (output.charAt(output.length() - 1) != '=') {
                output.append(',');
            }
            output.append(functionIndex);
            output.append(':');
            output.append(Long.toHexString(fingerprint));
        }
        System.out.println(output.toString());
    }

    private static long nanosToMicros(long nanos) {
        return nanos / 1000L;
    }

    private static long mips(long instructions, long nanos) {
        if (nanos == 0) {
            return 0;
        }
        return instructions * 1000L / nanos;
    }

    private static String hex2(int value) {
        String result = Integer.toHexString(value & 0xff);
        return result.length() == 1 ? "0" + result : result;
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
