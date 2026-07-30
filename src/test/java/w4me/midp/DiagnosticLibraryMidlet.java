package w4me.midp;

/** Product library with test-only frame diagnostics and the Duck Maze replay. */
public final class DiagnosticLibraryMidlet extends DiagnosticW4MeMidlet {
    private int systemMenuCount;

    /** Performs the frame diagnostics operation. */
    protected boolean frameDiagnostics() {
        return true;
    }

    /** Performs the replay route operation. */
    protected boolean replayRoute(String resource, String title) {
        return "Duck Maze".equals(title);
    }

    /** Performs the initial system menu action operation. */
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
