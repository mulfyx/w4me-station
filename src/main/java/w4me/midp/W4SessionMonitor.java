package w4me.midp;

import w4me.runtime.Wasm4Runtime;
import w4me.wasm.WasmInterpreter;
import w4me.wasm.WasmModule;

/**
 * Optional session instrumentation supplied by diagnostic MIDlets.
 *
 * <p>The release MIDlet never creates a monitor. Test JARs can add an implementation without placing replay routes,
 * framebuffer oracles, or benchmark reporting in the product classes.
 */
interface W4SessionMonitor {
    boolean audioDiagnostics();

    boolean renderEveryFrame();

    boolean resetPresentationAfterFrame(int frame);

    int gamepad(int frame, int current);

    int gamepad2(int frame, int current);

    void onInstallState(String state, int recordId, int bytes, int chunks, int hash);

    void onLoad(
            int cartridgeBytes,
            String source,
            WasmModule module,
            boolean fastPathsEnabled,
            boolean extendedFusionsEnabled,
            boolean compactExecutorEnabled,
            boolean traceExecutorEnabled,
            boolean directNumericIntrinsicsEnabled);

    void onInput(int frame, int gamepad, int gamepad2, int touch, int mouseButtons, int pointerX, int pointerY);

    void onFrame(
            int frame,
            Wasm4Runtime runtime,
            WasmModule module,
            WasmInterpreter interpreter,
            long updateMillis,
            long renderMillis,
            long elapsedMillis,
            boolean presented,
            int presentationDivisor,
            boolean bandRenderer);

    void onLayout(
            int screenWidth,
            int screenHeight,
            int framebufferLeft,
            int framebufferTop,
            int framebufferSide,
            int controlsTop,
            int controlsHeight);

    void onSaveState(String operation, String outcome, WasmModule module);

    void onSessionClosed(String reason);
}
