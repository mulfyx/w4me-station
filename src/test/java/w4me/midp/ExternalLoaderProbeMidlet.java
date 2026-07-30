package w4me.midp;

import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Form;
import javax.microedition.midlet.MIDlet;
import w4me.runtime.Wasm4Runtime;
import w4me.runtime.audio.AudioBackends;
import w4me.runtime.audio.Wasm4Apu;
import w4me.wasm.WasmInterpreter;
import w4me.wasm.WasmModule;

/** Provides the external loader probe midlet implementation. */
public final class ExternalLoaderProbeMidlet extends MIDlet {
    private static final String URL = "http://127.0.0.1:18384/sound-demo.wasm";
    private boolean started;

    /** Performs the start app operation. */
    protected void startApp() {
        if (started) {
            return;
        }
        started = true;
        Form result = new Form("External .wasm probe");
        result.append("Loading over HTTP...");
        Display.getDisplay(this).setCurrent(result);
        Wasm4Apu apu = null;
        try {
            byte[] cartridge = ResourceLoader.read(URL);
            byte[] font = ResourceLoader.read("/w4font.bin");
            WasmModule module = WasmModule.read(cartridge);
            apu = new Wasm4Apu(AudioBackends.create());
            apu.setDiagnostic(true);
            Wasm4Runtime runtime = new Wasm4Runtime(font, apu);
            runtime.initialize(module);
            WasmInterpreter interpreter = new WasmInterpreter(module, runtime);
            interpreter.invokeCartridgeLifecycle();
            frame(module, runtime, interpreter, 0);
            frame(module, runtime, interpreter, 1);
            frame(module, runtime, interpreter, 0);
            if (apu.toneEventCount() != 1) {
                throw new IllegalStateException("expected one tone event, got " + apu.toneEventCount());
            }
            System.out.println("W4ME_EXTERNAL_PROBE bytes="
                    + cartridge.length
                    + " tones="
                    + apu.toneEventCount()
                    + " backend="
                    + apu.grade());
            result.append("\nPASS: " + cartridge.length + " bytes\nAudio: " + apu.grade());
        } catch (Throwable failure) { // NOPMD -- Java ME API linkage fallback.
            System.out.println("W4ME_EXTERNAL_ERROR " + failure.toString());
            failure.printStackTrace();
            result.append("\nFAIL: " + failure.toString());
        } finally {
            if (apu != null) {
                apu.close();
            }
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

    private void frame(WasmModule module, Wasm4Runtime runtime, WasmInterpreter interpreter, int gamepad)
            throws Exception {
        runtime.beginFrame(module, gamepad, 0, 0, 0);
        interpreter.invoke("update");
        runtime.endFrame();
    }
}
