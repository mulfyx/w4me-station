package w4me.midp;

/**
 * Test-only direct cartridge launcher.
 *
 * <p>KEmulator injects raw Canvas keys and cannot navigate high-level LCDUI Lists. These nested MIDlets keep cartridge
 * scenarios independent from the product launcher's presentation while still exercising the production cartridge
 * runtime.
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

    /** Performs the replay route operation. */
    protected boolean replayRoute(String resource, String title) {
        return replayEnabled();
    }

    /** Returns the app property. */
    public String getAppProperty(String name) {
        if ("W4ME-Audio-Backend".equals(name) && compatibilityAudioEnabled()) {
            return "midi";
        }
        return super.getAppProperty(name);
    }

    /** Performs the replay enabled operation. */
    protected boolean replayEnabled() {
        return false;
    }

    /** Performs the compatibility audio enabled operation. */
    protected boolean compatibilityAudioEnabled() {
        return false;
    }

    /** Performs the cartridge resource operation. */
    protected abstract String cartridgeResource();

    /** Performs the cartridge title operation. */
    protected abstract String cartridgeTitle();

    /** Provides the duck implementation. */
    public static final class Duck extends DirectCartridgeProbeMidlet {
        /** Performs the cartridge resource operation. */
        protected String cartridgeResource() {
            return "/cartridges/duck-maze.wasm";
        }

        /** Performs the cartridge title operation. */
        protected String cartridgeTitle() {
            return "Duck Maze";
        }

        /** Performs the replay enabled operation. */
        protected boolean replayEnabled() {
            return true;
        }
    }

    /** Provides the plasma implementation. */
    public static final class Plasma extends DirectCartridgeProbeMidlet {
        /** Performs the cartridge resource operation. */
        protected String cartridgeResource() {
            return "/cartridges/plasma-cube.wasm";
        }

        /** Performs the cartridge title operation. */
        protected String cartridgeTitle() {
            return "Plasma Cube";
        }
    }

    /** Provides the sound demo implementation. */
    public static final class SoundDemo extends DirectCartridgeProbeMidlet {
        /** Performs the cartridge resource operation. */
        protected String cartridgeResource() {
            return "/cartridges/sound-demo.wasm";
        }

        /** Performs the cartridge title operation. */
        protected String cartridgeTitle() {
            return "Sound Demo";
        }

        /** Performs the compatibility audio enabled operation. */
        protected boolean compatibilityAudioEnabled() {
            return true;
        }
    }

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
