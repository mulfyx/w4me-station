package w4me.midp;

/** Product library with test-only frame diagnostics and the Duck Maze replay. */
public final class DiagnosticLibraryMidlet extends DiagnosticW4MeMidlet {
    private int systemMenuCount;

    protected boolean frameDiagnostics() {
        return true;
    }

    protected boolean replayRoute(String resource, String title) {
        return "Duck Maze".equals(title);
    }

    protected int initialSystemMenuAction() {
        systemMenuCount++;
        if (systemMenuCount == 2) {
            return SystemMenuModel.ACTION_RESTART;
        }
        if (systemMenuCount == 3) {
            return SystemMenuModel.ACTION_SETTINGS;
        }
        if (systemMenuCount == 4) {
            return SystemMenuModel.ACTION_EXIT;
        }
        return SystemMenuModel.ACTION_CONTINUE;
    }
}
