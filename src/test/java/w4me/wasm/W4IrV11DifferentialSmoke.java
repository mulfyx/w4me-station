package w4me.wasm;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import w4me.runtime.Wasm4Runtime;

/** Provides the W4IR v 11 differential smoke implementation. */
public final class W4IrV11DifferentialSmoke {
    private static final int FRAMES = 60;

    private W4IrV11DifferentialSmoke() {}

    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("usage: font.bin plasma.wasm");
        }
        byte[] font = readFile(arguments[0]);
        byte[] cartridge = readFile(arguments[1]);
        WasmModule tracedModule = WasmModule.read(cartridge, null, true);
        WasmModule ordinaryModule = WasmModule.read(cartridge, null, true);
        Wasm4Runtime tracedRuntime = new Wasm4Runtime(font);
        Wasm4Runtime ordinaryRuntime = new Wasm4Runtime(font);
        tracedRuntime.initialize(tracedModule);
        ordinaryRuntime.initialize(ordinaryModule);
        WasmInterpreter traced = new WasmInterpreter(tracedModule, tracedRuntime);
        WasmInterpreter ordinary = new WasmInterpreter(ordinaryModule, ordinaryRuntime);
        traced.setFastPathsEnabled(false);
        ordinary.setFastPathsEnabled(false);
        ordinary.setTraceExecutorEnabled(false);
        traced.setInstructionLimit(200000000L);
        ordinary.setInstructionLimit(200000000L);
        traced.invokeCartridgeLifecycle();
        ordinary.invokeCartridgeLifecycle();

        int traceCalls = 0;
        int traceIterations = 0;
        int frame;
        for (frame = 0; frame < FRAMES; frame++) {
            update(tracedModule, tracedRuntime, traced);
            update(ordinaryModule, ordinaryRuntime, ordinary);
            assertState(frame, tracedModule, ordinaryModule);
            if (traced.instructionsExecuted() != ordinary.instructionsExecuted()) {
                throw new AssertionError("instruction accounting mismatch at frame "
                        + frame
                        + ": traced="
                        + traced.instructionsExecuted()
                        + ", ordinary="
                        + ordinary.instructionsExecuted());
            }
            if (ordinary.traceLoopCalls() != 0 || ordinary.traceLoopIterations() != 0) {
                throw new AssertionError("disabled trace executor recorded trace work");
            }
            traceCalls += traced.traceLoopCalls();
            traceIterations += traced.traceLoopIterations();
        }
        if (traceCalls == 0 || traceIterations == 0) {
            throw new AssertionError("W4IR v11 trace did not execute");
        }
        tracedModule.close();
        ordinaryModule.close();
        tracedRuntime.close();
        ordinaryRuntime.close();
        System.out.println("PASS W4IR-v11 differential frames="
                + FRAMES
                + " memory=65536 globals=exact calls="
                + traceCalls
                + " iterations="
                + traceIterations);
    }

    private static void update(WasmModule module, Wasm4Runtime runtime, WasmInterpreter interpreter) throws Exception {
        runtime.beginFrame(module, 0, 0, 0, 0);
        interpreter.invoke("update");
        runtime.endFrame();
    }

    private static void assertState(int frame, WasmModule traced, WasmModule ordinary) {
        int index;
        for (index = 0; index < traced.memory.length; index++) {
            if (traced.memory[index] != ordinary.memory[index]) {
                throw new AssertionError("memory mismatch at frame " + frame + ", address " + index);
            }
        }
        if (traced.globals.length != ordinary.globals.length) {
            throw new AssertionError("global count mismatch");
        }
        for (index = 0; index < traced.globals.length; index++) {
            if (traced.globals[index] != ordinary.globals[index]) {
                throw new AssertionError("global mismatch at frame " + frame + ", index " + index);
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
