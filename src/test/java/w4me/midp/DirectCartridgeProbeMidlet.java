package w4me.midp;

/**
 * Test-only launcher for cartridges that are not part of the product catalog.
 *
 * <p>Bundled-cartridge scenarios must launch through the real LCDUI library with KEmulator's revision-gated list API.
 */
public abstract class DirectCartridgeProbeMidlet extends DiagnosticW4MeMidlet {
    private boolean started;

    /** Performs the start app operation. */
    protected void startApp() {
        if (!started) {
            started = true;
            openCartridge(cartridgeResource(), cartridgeTitle());
            return;
        }
        super.startApp();
    }

    /** Performs the frame diagnostics operation. */
    protected boolean frameDiagnostics() {
        return true;
    }

    /** Performs the cartridge resource operation. */
    protected abstract String cartridgeResource();

    /** Performs the cartridge title operation. */
    protected abstract String cartridgeTitle();

    /** Provides the sound test implementation. */
    public static final class SoundTest extends DirectCartridgeProbeMidlet {
        /** Performs the cartridge resource operation. */
        protected String cartridgeResource() {
            return "/cartridges/sound-test.wasm";
        }

        /** Performs the cartridge title operation. */
        protected String cartridgeTitle() {
            return "Sound Test";
        }
    }

    /** Provides the tankle implementation. */
    public static final class Tankle extends DirectCartridgeProbeMidlet {
        /** Performs the cartridge resource operation. */
        protected String cartridgeResource() {
            return "/cartridges/tankle.wasm";
        }

        /** Performs the cartridge title operation. */
        protected String cartridgeTitle() {
            return "Tankle";
        }
    }
}
