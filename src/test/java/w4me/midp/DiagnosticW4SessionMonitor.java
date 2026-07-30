package w4me.midp;

import w4me.FramebufferOracle;
import w4me.runtime.Wasm4Runtime;
import w4me.wasm.WasmInterpreter;
import w4me.wasm.WasmModule;

/** Frame oracles, replay input, and benchmark receipts for diagnostic JARs. */
final class DiagnosticW4SessionMonitor implements W4SessionMonitor {
    private static final int BUTTON_1 = 1;
    private static final int BUTTON_LEFT = 16;
    private static final int BUTTON_RIGHT = 32;
    private static final int BUTTON_UP = 64;
    private static final int BUTTON_DOWN = 128;

    private final String title;
    private final boolean replay;
    private final boolean frameDiagnostics;
    private final int benchmarkWarmupFrames;
    private boolean extendedFusionsEnabled;
    private boolean traceExecutorEnabled;
    private boolean directNumericIntrinsicsEnabled;
    private int previousGamepad = -1;
    private int previousGamepad2 = -1;
    private long totalUpdateMillis;
    private long totalRenderMillis;
    private long maximumFrameMillis;
    private int presentedFrames;
    private long benchmarkStartedAt;
    private int closedSessions;

    DiagnosticW4SessionMonitor(
            String title,
            boolean replay,
            boolean frameDiagnostics,
            int benchmarkWarmupFrames) {
        this.title = title;
        this.replay = replay;
        this.frameDiagnostics = frameDiagnostics;
        this.benchmarkWarmupFrames = benchmarkWarmupFrames;
    }

    public boolean audioDiagnostics() {
        return frameDiagnostics;
    }

    public boolean renderEveryFrame() {
        return frameDiagnostics;
    }

    public boolean resetPresentationAfterFrame(int frame) {
        return benchmarkWarmupFrames > 0
                && frame + 1 == benchmarkWarmupFrames;
    }

    public int gamepad(int frame, int current) {
        return replay ? replayGamepad(frame) : current;
    }

    public int gamepad2(int frame, int current) {
        return replay ? 0 : current;
    }

    public void onInstallState(
            String state, int recordId, int bytes, int chunks, int hash) {
        StringBuffer receipt = new StringBuffer("W4ME_INSTALL state=");
        receipt.append(state);
        if ("RECEIVED".equals(state)) {
            receipt.append(" id=").append(recordId);
            receipt.append(" bytes=").append(bytes);
            receipt.append(" chunks=").append(chunks);
        } else if ("COMMITTED".equals(state)) {
            receipt.append(" id=").append(recordId);
            receipt.append(" bytes=").append(bytes);
            receipt.append(" chunks=").append(chunks);
            receipt.append(" hash=").append(hex8(hash));
        }
        System.out.println(receipt.toString());
    }

    public void onLoad(
            int cartridgeBytes,
            String source,
            WasmModule module,
            boolean fastPathsEnabled,
            boolean extendedFusionsEnabled,
            boolean compactExecutorEnabled,
            boolean traceExecutorEnabled,
            boolean directNumericIntrinsicsEnabled) {
        this.extendedFusionsEnabled = extendedFusionsEnabled;
        this.traceExecutorEnabled = traceExecutorEnabled;
        this.directNumericIntrinsicsEnabled = directNumericIntrinsicsEnabled;
        if (!frameDiagnostics) {
            return;
        }
        System.out.println(
                "W4ME_LOAD cart="
                        + title
                        + " bytes="
                        + cartridgeBytes
                        + " source="
                        + source
                        + " w4ir="
                        + module.w4irStatus()
                        + " fast-paths="
                        + enabled(fastPathsEnabled)
                        + " extended-fusions="
                        + enabled(extendedFusionsEnabled)
                        + " compact-executor="
                        + enabled(compactExecutorEnabled)
                        + " trace-executor="
                        + enabled(traceExecutorEnabled)
                        + " direct-numeric-intrinsics="
                        + enabled(directNumericIntrinsicsEnabled));
    }

    public void onInput(
            int frame,
            int gamepad,
            int gamepad2,
            int touch,
            int mouseButtons,
            int pointerX,
            int pointerY) {
        if (!frameDiagnostics
                || (gamepad == previousGamepad && gamepad2 == previousGamepad2)) {
            return;
        }
        System.out.println(
                "W4ME_INPUT cart="
                        + title
                        + " frame="
                        + frame
                        + " gamepad="
                        + gamepad
                        + " touch="
                        + touch
                        + " mouse="
                        + mouseButtons
                        + " pointer="
                        + pointerX
                        + ","
                        + pointerY
                        + " gamepad2="
                        + gamepad2);
        previousGamepad = gamepad;
        previousGamepad2 = gamepad2;
    }

    public void onFrame(
            int frame,
            Wasm4Runtime runtime,
            WasmModule module,
            WasmInterpreter interpreter,
            long updateMillis,
            long renderMillis,
            long elapsedMillis,
            boolean presented,
            int presentationDivisor,
            boolean bandRenderer) {
        if (frameDiagnostics && (frame < 60 || frame % 60 == 0)) {
            System.out.println(
                    "W4ME_FRAME cart="
                            + title
                            + " frame="
                            + frame
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
                            + " framebuffer-fnv1a="
                            + hex8(FramebufferOracle.fnv1a(module))
                            + " elapsed-ms="
                            + elapsedMillis
                            + " free-heap="
                            + Runtime.getRuntime().freeMemory());
        }
        if (frameDiagnostics && replay && frame == 154) {
            System.out.println(
                    "W4ME_REPLAY_COMPLETE cart="
                            + title
                            + " frame="
                            + frame
                            + " framebuffer-fnv1a="
                            + hex8(FramebufferOracle.fnv1a(module)));
        }
        recordBenchmark(
                frame,
                runtime,
                module,
                interpreter,
                updateMillis,
                renderMillis,
                elapsedMillis,
                presented,
                presentationDivisor,
                bandRenderer);
    }

    public void onLayout(
            int screenWidth,
            int screenHeight,
            int framebufferLeft,
            int framebufferTop,
            int framebufferSide,
            int controlsTop,
            int controlsHeight) {
        if (!frameDiagnostics) {
            return;
        }
        System.out.println(
                "W4ME_LAYOUT screen="
                        + screenWidth
                        + "x"
                        + screenHeight
                        + " framebuffer="
                        + framebufferLeft
                        + ","
                        + framebufferTop
                        + ","
                        + framebufferSide
                        + " controls="
                        + controlsTop
                        + ","
                        + controlsHeight
                        + " overlap=0");
    }

    public void onSessionClosed(String reason) {
        closedSessions++;
        System.out.println(
                "W4ME_SESSION_CLOSED cart="
                        + title
                        + " reason="
                        + reason
                        + " count="
                        + closedSessions);
    }

    public void onSaveState(
            String operation, String outcome, WasmModule module) {
        System.out.println(
                "W4ME_SAVE_STATE cart="
                        + title
                        + " operation="
                        + operation
                        + " outcome="
                        + outcome
                        + " framebuffer-fnv1a="
                        + hex8(FramebufferOracle.fnv1a(module)));
    }

    private void recordBenchmark(
            int frame,
            Wasm4Runtime runtime,
            WasmModule module,
            WasmInterpreter interpreter,
            long updateMillis,
            long renderMillis,
            long elapsedMillis,
            boolean presented,
            int presentationDivisor,
            boolean bandRenderer) {
        if (benchmarkWarmupFrames < 0) {
            return;
        }
        if (benchmarkStartedAt == 0) {
            benchmarkStartedAt =
                    System.currentTimeMillis() - elapsedMillis;
        }
        totalUpdateMillis += updateMillis;
        totalRenderMillis += renderMillis;
        if (elapsedMillis > maximumFrameMillis) {
            maximumFrameMillis = elapsedMillis;
        }
        if (presented) {
            presentedFrames++;
        }
        if (benchmarkWarmupFrames > 0
                && frame + 1 == benchmarkWarmupFrames) {
            totalUpdateMillis = 0;
            totalRenderMillis = 0;
            maximumFrameMillis = 0;
            presentedFrames = 0;
            benchmarkStartedAt = System.currentTimeMillis();
            return;
        }
        if (frame != benchmarkWarmupFrames + 119) {
            return;
        }
        long benchmarkElapsed =
                System.currentTimeMillis() - benchmarkStartedAt;
        if (benchmarkElapsed < 1) {
            benchmarkElapsed = 1;
        }
        int rendered = presentedFrames == 0 ? 1 : presentedFrames;
        System.out.println(
                "W4ME_BENCH cart="
                        + title
                        + " frames=120 warmup-frames="
                        + benchmarkWarmupFrames
                        + " update-average-ms="
                        + totalUpdateMillis / 120
                        + " render-average-ms="
                        + totalRenderMillis / rendered
                        + " renderer="
                        + (bandRenderer ? "band" : "full")
                        + " logic-fps="
                        + 120000L / benchmarkElapsed
                        + " presentation-fps="
                        + presentedFrames * 1000L / benchmarkElapsed
                        + " presentation-divisor="
                        + presentationDivisor
                        + " presented="
                        + presentedFrames
                        + " frame-maximum-ms="
                        + maximumFrameMillis
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
                        + " extended-fusions="
                        + enabled(extendedFusionsEnabled)
                        + " trace-executor="
                        + enabled(traceExecutorEnabled)
                        + " direct-numeric-intrinsics="
                        + enabled(directNumericIntrinsicsEnabled)
                        + " audio="
                        + runtime.apu().grade()
                        + " disk="
                        + runtime.disk().grade()
                        + " w4ir="
                        + module.w4irStatus()
                        + " code-faults="
                        + module.w4irPageFaults()
                        + " code-hits="
                        + module.w4irPageHits()
                        + " code-promoted="
                        + module.w4irPromotedFunctions()
                        + " free-heap="
                        + Runtime.getRuntime().freeMemory());
    }

    private int replayGamepad(int frame) {
        if (frame == 1) {
            return BUTTON_1;
        }
        int pathFrame = frame - 3;
        if (pathFrame < 0) {
            return 0;
        }
        if (pathFrame < 32) {
            return BUTTON_DOWN;
        }
        pathFrame -= 32;
        if (pathFrame < 24) {
            return BUTTON_RIGHT;
        }
        pathFrame -= 24;
        if (pathFrame < 16) {
            return BUTTON_DOWN;
        }
        pathFrame -= 16;
        if (pathFrame < 32) {
            return BUTTON_RIGHT;
        }
        pathFrame -= 32;
        if (pathFrame < 48) {
            return BUTTON_UP;
        }
        return 0;
    }

    private static String enabled(boolean value) {
        return value ? "enabled" : "disabled";
    }

    private static String hex8(int value) {
        String hex = Integer.toHexString(value);
        StringBuffer result = new StringBuffer(8);
        int padding;
        for (padding = hex.length(); padding < 8; padding++) {
            result.append('0');
        }
        result.append(hex);
        return result.toString();
    }
}
