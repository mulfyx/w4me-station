package w4me.midp;

public final class InstallFlowProbeMidlet extends DiagnosticW4MeMidlet {
    private static final String URL = "http://127.0.0.1:18385/sound-demo.wasm";
    private boolean offlineRelaunch;

    public String getAppProperty(String name) {
        if ("W4ME-Cartridge-URL".equals(name)) {
            return URL;
        }
        if ("W4ME-Diagnostics".equals(name)) {
            return "true";
        }
        return super.getAppProperty(name);
    }

    protected int initialSystemMenuAction() {
        return SystemMenuModel.ACTION_EXIT;
    }

    protected boolean replayRoute(String resource, String title) {
        return offlineRelaunch;
    }

    void finishCanvasExit(W4Canvas source) {
        showLibrary();
    }

    void showLibrary() {
        if (offlineRelaunch) {
            super.showLibrary();
            return;
        }
        CartridgeStore store = null;
        try {
            store = CartridgeStore.open();
            CartridgeStore.CartridgeInfo[] installed = store.list();
            int index;
            for (index = installed.length - 1; index >= 0; index--) {
                if ("sound-demo".equals(installed[index].title)) {
                    offlineRelaunch = true;
                    openCartridge(
                            CartridgeStore.location(installed[index].recordId),
                            installed[index].title);
                    return;
                }
            }
        } catch (Throwable failure) {
            System.out.println("W4ME_INSTALL_RELAUNCH_ERROR " + failure);
        } finally {
            if (store != null) {
                store.close();
            }
        }
        super.showLibrary();
    }
}
