package w4me.midp;

/** Product library with test-only frame diagnostics and the Duck Maze replay. */
public final class DiagnosticLibraryMidlet extends DiagnosticW4MeMidlet {
    /** Performs the frame diagnostics operation. */
    protected boolean frameDiagnostics() {
        return true;
    }

    /** Performs the replay route operation. */
    protected boolean replayRoute(String resource, String title) {
        return "Duck Maze".equals(title);
    }
}
