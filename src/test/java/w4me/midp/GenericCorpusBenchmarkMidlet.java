package w4me.midp;

import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Form;
import javax.microedition.midlet.MIDlet;
import w4me.FramebufferOracle;
import w4me.runtime.Wasm4Runtime;
import w4me.wasm.WasmInterpreter;
import w4me.wasm.WasmModule;

/** Workload-specific, exact generic-interpreter benchmark for the phone profile. */
public abstract class GenericCorpusBenchmarkMidlet extends MIDlet {
    protected static final int WATERNET = 0;
    protected static final int RUBIDO = 1;
    protected static final int DUCK_MAZE = 2;
    protected static final int GAME_OF_LIFE = 3;
    private static final int SAMPLES = 1;

    private boolean started;

    /** Performs the workload operation. */
    protected abstract int workload();

    /** Performs the start app operation. */
    protected void startApp() {
        if (started) {
            return;
        }
        started = true;
        Form form = new Form("Generic corpus benchmark");
        Display.getDisplay(this).setCurrent(form);
        try {
            byte[] font = ResourceLoader.read("/w4font.bin");
            byte[] cartridge = ResourceLoader.read("/benchmark-cart.wasm");
            runRoute(font, cartridge);
            Result[] samples = new Result[SAMPLES];
            int sample;
            for (sample = 0; sample < samples.length; sample++) {
                samples[sample] = runRoute(font, cartridge);
                System.out.println("W4ME_CORPUS_BENCH_SAMPLE workload="
                        + workloadName()
                        + " route="
                        + routeName()
                        + " sample="
                        + (sample + 1)
                        + " elapsed-ms="
                        + samples[sample].elapsedMillis
                        + " logical="
                        + samples[sample].instructions
                        + " outer-dispatches="
                        + samples[sample].dispatches
                        + " compact-calls="
                        + samples[sample].compactCalls
                        + " compact-instructions="
                        + samples[sample].compactInstructions
                        + " framebuffer-fnv1a="
                        + hex8(samples[sample].framebuffer));
            }
            requireEquivalent(samples);
            long median = medianElapsed(samples);
            System.out.println("W4ME_CORPUS_BENCH workload="
                    + workloadName()
                    + " route="
                    + routeName()
                    + " frames="
                    + frameCount()
                    + " samples="
                    + SAMPLES
                    + " median-elapsed-ms="
                    + median
                    + " logical="
                    + samples[0].instructions
                    + " outer-dispatches="
                    + samples[0].dispatches
                    + " compact-calls="
                    + samples[0].compactCalls
                    + " compact-instructions="
                    + samples[0].compactInstructions
                    + " fast-paths=0 extended-fusions=enabled"
                    + " compact=enabled trace=enabled direct-intrinsics=enabled"
                    + " w4ir=RAM framebuffer-fnv1a="
                    + hex8(samples[0].framebuffer));
            form.append("PASS\n" + workloadName() + "\nmedian " + median + " ms");
        } catch (Throwable failure) { // NOPMD -- Java ME API linkage fallback.
            System.out.println("W4ME_CORPUS_BENCH_ERROR " + failure.toString());
            failure.printStackTrace();
            form.append("FAIL\n" + failure.toString());
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

    private Result runRoute(byte[] font, byte[] cartridge) throws Exception {
        WasmModule module = WasmModule.read(cartridge);
        Wasm4Runtime runtime = new Wasm4Runtime(font);
        try {
            runtime.initialize(module);
            WasmInterpreter interpreter = new WasmInterpreter(module, runtime);
            interpreter.setFastPathsEnabled(false);
            interpreter.setInstructionLimit(200000000L);
            interpreter.invokeCartridgeLifecycle();
            long instructions = 0;
            long dispatches = 0;
            long compactCalls = 0;
            long compactInstructions = 0;
            long startedAt = System.currentTimeMillis();
            int frame;
            for (frame = 0; frame < frameCount(); frame++) {
                runtime.beginFrame(module, gamepad(frame), mouseX(frame), mouseY(frame), mouseButtons(frame));
                interpreter.invoke("update");
                runtime.endFrame();
                instructions += interpreter.instructionsExecuted();
                dispatches += interpreter.dispatchesExecuted();
                compactCalls += interpreter.compactBlockCalls();
                compactInstructions += interpreter.compactInstructionsExecuted();
                if (interpreter.fastPathCalls() != 0) {
                    throw new IllegalStateException("cartridge-specific fast path executed");
                }
            }
            long elapsed = System.currentTimeMillis() - startedAt;
            int framebuffer = FramebufferOracle.fnv1a(module);
            requireEquals("final framebuffer", expectedFramebuffer(), framebuffer);
            return new Result(elapsed, instructions, dispatches, compactCalls, compactInstructions, framebuffer);
        } finally {
            runtime.close();
            module.close();
        }
    }

    private int frameCount() {
        switch (workload()) {
            case WATERNET:
                return 94;
            case RUBIDO:
                return 70;
            case DUCK_MAZE:
                return 155;
            case GAME_OF_LIFE:
                return 1;
            default:
                throw new IllegalStateException("unknown workload");
        }
    }

    private String workloadName() {
        switch (workload()) {
            case WATERNET:
                return "waternet";
            case RUBIDO:
                return "rubido";
            case DUCK_MAZE:
                return "duck-maze";
            case GAME_OF_LIFE:
                return "game-of-life-zig-edition";
            default:
                throw new IllegalStateException("unknown workload");
        }
    }

    private String routeName() {
        switch (workload()) {
            case WATERNET:
                return "browser-route-v1";
            case RUBIDO:
                return "browser-route-v1";
            case DUCK_MAZE:
                return "level-1-v1";
            case GAME_OF_LIFE:
                return "idle-1-v1";
            default:
                throw new IllegalStateException("unknown workload");
        }
    }

    private int expectedFramebuffer() {
        switch (workload()) {
            case WATERNET:
                return 0x14e0f616;
            case RUBIDO:
                return 0x47462cbf;
            case DUCK_MAZE:
                return 0x1ae224ce;
            case GAME_OF_LIFE:
                return 0xa9255758;
            default:
                throw new IllegalStateException("unknown workload");
        }
    }

    private int gamepad(int frame) {
        if (workload() == WATERNET) {
            if (frame == 0 || frame == 12 || frame == 24 || frame == 48 || frame == 60 || frame == 82) {
                return 1;
            }
            if (frame == 36 || frame == 42) {
                return 64;
            }
            return 0;
        }
        if (workload() == RUBIDO) {
            if (frame == 13) {
                return 1;
            }
            return frame == 21 ? 2 : 0;
        }
        if (workload() == DUCK_MAZE) {
            if (frame == 1) {
                return 1;
            }
            if (frame >= 3 && frame < 35) {
                return 128;
            }
            if (frame >= 35 && frame < 59) {
                return 32;
            }
            if (frame >= 59 && frame < 75) {
                return 128;
            }
            if (frame >= 75 && frame < 107) {
                return 32;
            }
            if (frame >= 107) {
                return 64;
            }
        }
        return 0;
    }

    private int mouseX(int frame) {
        if (workload() != RUBIDO) {
            return workload() == DUCK_MAZE ? 0 : 32767;
        }
        if (frame == 0) {
            return 32767;
        }
        if (frame == 1) {
            return 79;
        }
        return frame <= 23 ? 80 : 79;
    }

    private int mouseY(int frame) {
        if (workload() != RUBIDO) {
            return workload() == DUCK_MAZE ? 0 : 32767;
        }
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

    private int mouseButtons(int frame) {
        if (workload() != RUBIDO) {
            return 0;
        }
        return frame == 3 || frame == 25 || frame == 36 || frame == 47 || frame == 57 ? 1 : 0;
    }

    private static void requireEquivalent(Result[] samples) {
        int sample;
        for (sample = 1; sample < samples.length; sample++) {
            requireEquals("logical instructions", samples[0].instructions, samples[sample].instructions);
            requireEquals("outer dispatches", samples[0].dispatches, samples[sample].dispatches);
            requireEquals("compact calls", samples[0].compactCalls, samples[sample].compactCalls);
            requireEquals("compact instructions", samples[0].compactInstructions, samples[sample].compactInstructions);
            requireEquals("framebuffer", samples[0].framebuffer, samples[sample].framebuffer);
        }
    }

    private static long medianElapsed(Result[] samples) {
        long[] values = new long[samples.length];
        int index;
        for (index = 0; index < samples.length; index++) {
            values[index] = samples[index].elapsedMillis;
        }
        int left;
        for (left = 0; left < values.length; left++) {
            int right;
            for (right = left + 1; right < values.length; right++) {
                if (values[right] < values[left]) {
                    long temporary = values[left];
                    values[left] = values[right];
                    values[right] = temporary;
                }
            }
        }
        return values[values.length / 2];
    }

    private static void requireEquals(String label, int expected, int actual) {
        if (expected != actual) {
            throw new IllegalStateException(label + ": expected " + hex8(expected) + ", got " + hex8(actual));
        }
    }

    private static void requireEquals(String label, long expected, long actual) {
        if (expected != actual) {
            throw new IllegalStateException(label + ": expected " + expected + ", got " + actual);
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

    private static final class Result {
        private final long elapsedMillis;
        private final long instructions;
        private final long dispatches;
        private final long compactCalls;
        private final long compactInstructions;
        private final int framebuffer;

        private Result(
                long elapsedMillis,
                long instructions,
                long dispatches,
                long compactCalls,
                long compactInstructions,
                int framebuffer) {
            this.elapsedMillis = elapsedMillis;
            this.instructions = instructions;
            this.dispatches = dispatches;
            this.compactCalls = compactCalls;
            this.compactInstructions = compactInstructions;
            this.framebuffer = framebuffer;
        }
    }
}
