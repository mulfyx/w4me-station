package w4me.midp;

import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Form;
import javax.microedition.midlet.MIDlet;
import w4me.FramebufferOracle;
import w4me.UntangleBenchmarkRoute;
import w4me.runtime.Wasm4Runtime;
import w4me.wasm.WasmInterpreter;
import w4me.wasm.WasmModule;

/** Provides the untangle W4IR benchmark midlet implementation. */
public class UntangleW4IrBenchmarkMidlet extends MIDlet {
    private static final String[] COMPARISON_MODES = {"optimized", "trace-off", "fusion-only", "baseline"};
    private static final int PHASES = 6;
    private static final int MEASURED_ROUTES = 8;
    private boolean started;

    /** Performs the start app operation. */
    protected void startApp() {
        if (started) {
            return;
        }
        started = true;
        Form result = new Form("Untangle W4IR benchmark");
        Display.getDisplay(this).setCurrent(result);
        try {
            String mode = benchmarkMode();
            requireMode(mode);
            byte[] cartridge = ResourceLoader.read("/cartridges/untangle.wasm");
            byte[] font = ResourceLoader.read("/w4font.bin");
            if ("comparison".equals(mode)) {
                runComparison(cartridge, font);
                result.append("PASS\ncomparison\n4 x 8 x 401 exact frames");
            } else {
                runSingleMode(mode, cartridge, font);
                result.append("PASS\n" + mode + "\n8 x 401 exact frames");
            }
        } catch (Throwable failure) { // NOPMD -- Java ME API linkage fallback.
            System.out.println("W4ME_UNTANGLE_BENCH_ERROR " + failure.toString());
            failure.printStackTrace();
            result.append("FAIL\n" + failure.toString());
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

    /** Performs the benchmark mode operation. */
    protected String benchmarkMode() {
        return "optimized";
    }

    private static void runSingleMode(String mode, byte[] cartridge, byte[] font) throws Exception {
        warmUpMode(mode, cartridge, font);
        Measurement total = new Measurement();
        int route;
        for (route = 0; route < MEASURED_ROUTES; route++) {
            Measurement sample = measureMode(mode, cartridge, font);
            total.add(sample, route);
            printSample(mode, route, sample);
        }
        printTotal(mode, total);
    }

    private static void runComparison(byte[] cartridge, byte[] font) throws Exception {
        Measurement[] totals = new Measurement[COMPARISON_MODES.length];
        int modeIndex;
        for (modeIndex = 0; modeIndex < COMPARISON_MODES.length; modeIndex++) {
            warmUpMode(COMPARISON_MODES[modeIndex], cartridge, font);
            totals[modeIndex] = new Measurement();
        }
        int route;
        for (route = 0; route < MEASURED_ROUTES; route++) {
            int position;
            for (position = 0; position < COMPARISON_MODES.length; position++) {
                modeIndex = (route + position) % COMPARISON_MODES.length;
                String mode = COMPARISON_MODES[modeIndex];
                Measurement sample = measureMode(mode, cartridge, font);
                totals[modeIndex].add(sample, route);
                printSample(mode, route, sample);
            }
        }
        for (modeIndex = 0; modeIndex < COMPARISON_MODES.length; modeIndex++) {
            printTotal(COMPARISON_MODES[modeIndex], totals[modeIndex]);
        }
    }

    private static void warmUpMode(String mode, byte[] cartridge, byte[] font) throws Exception {
        warmUp(
                cartridge,
                font,
                !"baseline".equals(mode),
                "optimized".equals(mode) || "trace-off".equals(mode),
                "optimized".equals(mode) || "fusion-only".equals(mode));
    }

    private static Measurement measureMode(String mode, byte[] cartridge, byte[] font) throws Exception {
        return measureRoute(
                cartridge,
                font,
                !"baseline".equals(mode),
                "optimized".equals(mode) || "trace-off".equals(mode),
                "optimized".equals(mode) || "fusion-only".equals(mode));
    }

    private static void printSample(String mode, int route, Measurement sample) {
        System.out.println("W4ME_UNTANGLE_SAMPLE mode="
                + mode
                + " route="
                + route
                + " frames="
                + UntangleBenchmarkRoute.FRAMES
                + " update-total-ms="
                + sample.updateMillis
                + " update-maximum-ms="
                + sample.maximumMillis
                + " maximum-frame="
                + sample.maximumFrame
                + " fast-paths="
                + sample.fastPaths
                + " framebuffer-fnv1a=bc0231d9");
    }

    private static void printTotal(String mode, Measurement total) {
        int measuredFrames = UntangleBenchmarkRoute.FRAMES * MEASURED_ROUTES;
        int phase;
        for (phase = 0; phase < PHASES; phase++) {
            System.out.println("W4ME_UNTANGLE_PHASE mode="
                    + mode
                    + " phase="
                    + UntangleBenchmarkRoute.phaseName(phase)
                    + " frames="
                    + total.phaseFrames[phase]
                    + " update-total-ms="
                    + total.phaseMillis[phase]
                    + " update-average-us-estimate="
                    + total.phaseMillis[phase] * 1000L / total.phaseFrames[phase]
                    + " instructions-average="
                    + total.phaseInstructions[phase] / total.phaseFrames[phase]
                    + " dispatches-average="
                    + total.phaseDispatches[phase] / total.phaseFrames[phase]);
        }
        System.out.println("W4ME_UNTANGLE_BENCH mode="
                + mode
                + " frames="
                + measuredFrames
                + " routes="
                + MEASURED_ROUTES
                + " parse-total-ms="
                + total.parseMillis
                + " lifecycle-total-ms="
                + total.lifecycleMillis
                + " update-total-ms="
                + total.updateMillis
                + " update-average-us-estimate="
                + total.updateMillis * 1000L / measuredFrames
                + " update-route-median-ms="
                + total.routeMedianMillis()
                + " update-maximum-ms="
                + total.maximumMillis
                + " maximum-route="
                + total.maximumRoute
                + " maximum-frame="
                + total.maximumFrame
                + " instructions-average="
                + total.instructions / measuredFrames
                + " dispatches-average="
                + total.dispatches / measuredFrames
                + " compact-blocks-average="
                + total.compactBlocks / measuredFrames
                + " compact-instructions-average="
                + total.compactInstructions / measuredFrames
                + " compact-frames="
                + total.compactFrames
                + " maximum-instructions="
                + total.maximumInstructions
                + " maximum-instructions-route="
                + total.maximumInstructionsRoute
                + " maximum-instructions-frame="
                + total.maximumInstructionsFrame
                + " trace-loops-average="
                + total.traceLoops / measuredFrames
                + " trace-iterations-average="
                + total.traceIterations / measuredFrames
                + " fast-paths="
                + total.fastPaths
                + " framebuffer-fnv1a=bc0231d9"
                + " free-heap="
                + Runtime.getRuntime().freeMemory());
    }

    private static Measurement measureRoute(
            byte[] cartridge, byte[] font, boolean extended, boolean compact, boolean trace) throws Exception {
        Measurement result = new Measurement();
        long parseStarted = System.currentTimeMillis();
        WasmModule module = WasmModule.read(cartridge, null, extended);
        result.parseMillis = System.currentTimeMillis() - parseStarted;
        Wasm4Runtime runtime = new Wasm4Runtime(font);
        try {
            runtime.initialize(module);
            WasmInterpreter interpreter = new WasmInterpreter(module, runtime);
            interpreter.setFastPathsEnabled(false);
            interpreter.setCompactExecutorEnabled(compact);
            interpreter.setTraceExecutorEnabled(trace);
            interpreter.setInstructionLimit(200000000L);
            long lifecycleStarted = System.currentTimeMillis();
            interpreter.invokeCartridgeLifecycle();
            result.lifecycleMillis = System.currentTimeMillis() - lifecycleStarted;

            int frame;
            for (frame = 0; frame < UntangleBenchmarkRoute.FRAMES; frame++) {
                int phase = UntangleBenchmarkRoute.phase(frame);
                long frameStarted = System.currentTimeMillis();
                update(module, runtime, interpreter, frame);
                long elapsed = System.currentTimeMillis() - frameStarted;
                result.updateMillis += elapsed;
                result.phaseMillis[phase] += elapsed;
                result.phaseFrames[phase]++;
                long frameInstructions = interpreter.instructionsExecuted();
                final int frameCompactBlocks = interpreter.compactBlockCalls();
                result.instructions += frameInstructions;
                result.dispatches += interpreter.dispatchesExecuted();
                result.phaseInstructions[phase] += frameInstructions;
                result.phaseDispatches[phase] += interpreter.dispatchesExecuted();
                result.compactBlocks += frameCompactBlocks;
                result.compactInstructions += interpreter.compactInstructionsExecuted();
                result.traceLoops += interpreter.traceLoopCalls();
                result.traceIterations += interpreter.traceLoopIterations();
                result.fastPaths += interpreter.fastPathCalls();
                if (elapsed > result.maximumMillis) {
                    result.maximumMillis = elapsed;
                    result.maximumFrame = frame;
                }
                if (frameCompactBlocks != 0) {
                    result.compactFrames++;
                }
                if (frameInstructions > result.maximumInstructions) {
                    result.maximumInstructions = frameInstructions;
                    result.maximumInstructionsFrame = frame;
                }
            }
            requireExactResult(module, result.fastPaths);
            return result;
        } finally {
            runtime.close();
            module.close();
        }
    }

    private static void warmUp(byte[] cartridge, byte[] font, boolean extended, boolean compact, boolean trace)
            throws Exception {
        WasmModule module = WasmModule.read(cartridge, null, extended);
        Wasm4Runtime runtime = new Wasm4Runtime(font);
        try {
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
        } finally {
            runtime.close();
            module.close();
        }
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
        int framebuffer = FramebufferOracle.fnv1a(module);
        if (framebuffer != UntangleBenchmarkRoute.FINAL_FRAMEBUFFER_FNV1A || fastPaths != 0) {
            throw new IllegalStateException("Untangle route was not exact generic execution");
        }
    }

    private static void requireMode(String mode) {
        if (!"optimized".equals(mode)
                && !"trace-off".equals(mode)
                && !"fusion-only".equals(mode)
                && !"baseline".equals(mode)
                && !"comparison".equals(mode)) {
            throw new IllegalArgumentException("unknown benchmark mode: " + mode);
        }
    }

    private static final class Measurement {
        long parseMillis;
        long lifecycleMillis;
        long updateMillis;
        long instructions;
        long dispatches;
        long compactBlocks;
        long compactInstructions;
        int compactFrames;
        long maximumInstructions;
        int maximumInstructionsRoute;
        int maximumInstructionsFrame = -1;
        long traceLoops;
        long traceIterations;
        int fastPaths;
        long maximumMillis;
        int maximumRoute;
        int maximumFrame = -1;
        final long[] phaseMillis = new long[PHASES];
        final long[] phaseInstructions = new long[PHASES];
        final long[] phaseDispatches = new long[PHASES];
        final int[] phaseFrames = new int[PHASES];
        final long[] routeUpdateMillis = new long[MEASURED_ROUTES];

        void add(Measurement sample, int route) {
            parseMillis += sample.parseMillis;
            lifecycleMillis += sample.lifecycleMillis;
            updateMillis += sample.updateMillis;
            routeUpdateMillis[route] = sample.updateMillis;
            instructions += sample.instructions;
            dispatches += sample.dispatches;
            compactBlocks += sample.compactBlocks;
            compactInstructions += sample.compactInstructions;
            compactFrames += sample.compactFrames;
            traceLoops += sample.traceLoops;
            traceIterations += sample.traceIterations;
            fastPaths += sample.fastPaths;
            if (sample.maximumMillis > maximumMillis) {
                maximumMillis = sample.maximumMillis;
                maximumRoute = route;
                maximumFrame = sample.maximumFrame;
            }
            if (sample.maximumInstructions > maximumInstructions) {
                maximumInstructions = sample.maximumInstructions;
                maximumInstructionsRoute = route;
                maximumInstructionsFrame = sample.maximumInstructionsFrame;
            }
            int phase;
            for (phase = 0; phase < PHASES; phase++) {
                phaseMillis[phase] += sample.phaseMillis[phase];
                phaseInstructions[phase] += sample.phaseInstructions[phase];
                phaseDispatches[phase] += sample.phaseDispatches[phase];
                phaseFrames[phase] += sample.phaseFrames[phase];
            }
        }

        long routeMedianMillis() {
            long[] sorted = new long[routeUpdateMillis.length];
            System.arraycopy(routeUpdateMillis, 0, sorted, 0, routeUpdateMillis.length);
            int first;
            for (first = 0; first < sorted.length - 1; first++) {
                int minimum = first;
                int candidate;
                for (candidate = first + 1; candidate < sorted.length; candidate++) {
                    if (sorted[candidate] < sorted[minimum]) {
                        minimum = candidate;
                    }
                }
                long swap = sorted[first];
                sorted[first] = sorted[minimum];
                sorted[minimum] = swap;
            }
            int upper = sorted.length / 2;
            return (sorted[upper - 1] + sorted[upper]) / 2L;
        }
    }
}
