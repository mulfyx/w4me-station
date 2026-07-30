package w4me.midp;

/** Direct KEmulator Save -> mutate -> Load and lifetime probe. */
public abstract class SaveStateProbeMidlet extends DiagnosticW4MeMidlet {
    private boolean started;
    private int menuCount;

    protected void startApp() {
        if (!started) {
            started = true;
            openCartridge(cartridgeResource(), cartridgeTitle());
            return;
        }
        super.startApp();
    }

    protected boolean frameDiagnostics() {
        return true;
    }

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

    protected abstract String cartridgeResource();

    protected abstract String cartridgeTitle();

    public static final class Plasma extends SaveStateProbeMidlet {
        protected String cartridgeResource() {
            return "/cartridges/plasma-cube.wasm";
        }

        protected String cartridgeTitle() {
            return "Plasma Cube";
        }
    }

    public static final class NyanCat extends SaveStateProbeMidlet {
        protected String cartridgeResource() {
            return "/cartridges/nyancat.wasm";
        }

        protected String cartridgeTitle() {
            return "Nyan Cat";
        }
    }
}
