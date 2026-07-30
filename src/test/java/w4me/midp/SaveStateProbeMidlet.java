package w4me.midp;

/** Direct KEmulator Save -> mutate -> Load and lifetime probe. */
public abstract class SaveStateProbeMidlet extends DiagnosticW4MeMidlet {
    private boolean started;
    private int menuCount;

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

    /** Performs the initial system menu action operation. */
    protected int initialSystemMenuAction() {
        menuCount++;
        if (menuCount == 1 || menuCount == 3 || menuCount == 5) {
            return SystemMenuModel.ACTION_LOAD_STATE;
        }
        if (menuCount == 2) {
            return SystemMenuModel.ACTION_SAVE_STATE;
        }
        if (menuCount == 4) {
            return SystemMenuModel.ACTION_RESTART;
        }
        return SystemMenuModel.ACTION_EXIT;
    }

    /** Performs the cartridge resource operation. */
    protected abstract String cartridgeResource();

    /** Performs the cartridge title operation. */
    protected abstract String cartridgeTitle();

    /** Provides the plasma implementation. */
    public static final class Plasma extends SaveStateProbeMidlet {
        /** Performs the cartridge resource operation. */
        protected String cartridgeResource() {
            return "/cartridges/plasma-cube.wasm";
        }

        /** Performs the cartridge title operation. */
        protected String cartridgeTitle() {
            return "Plasma Cube";
        }
    }

    /** Provides the nyan cat implementation. */
    public static final class NyanCat extends SaveStateProbeMidlet {
        /** Performs the cartridge resource operation. */
        protected String cartridgeResource() {
            return "/cartridges/nyancat.wasm";
        }

        /** Performs the cartridge title operation. */
        protected String cartridgeTitle() {
            return "Nyan Cat";
        }
    }
}
