package w4me.midp;

/** Test-only MIDlet base that attaches diagnostics to the production session. */
public abstract class DiagnosticW4MeMidlet extends W4MeMidlet {
    /** Creates the session monitor. */
    protected W4SessionMonitor createSessionMonitor(String resource, String title) {
        return new DiagnosticW4SessionMonitor(
                title, replayRoute(resource, title), frameDiagnostics(), benchmarkWarmupFrames());
    }

    /** Performs the replay route operation. */
    protected boolean replayRoute(String resource, String title) {
        return false;
    }

    /** Performs the frame diagnostics operation. */
    protected boolean frameDiagnostics() {
        return "true".equals(getAppProperty("W4ME-Diagnostics"));
    }

    private int benchmarkWarmupFrames() {
        String value = getAppProperty("W4ME-Benchmark-Warmup-Frames");
        if (value == null) {
            return -1;
        }
        try {
            int frames = Integer.parseInt(value);
            if (frames >= 0 && frames <= 600) {
                return frames;
            }
        } catch (NumberFormatException invalid) { // NOPMD -- Invalid optional setting uses the default.
            // Keep the default diagnostic frame count.
            // The diagnostic harness reports no benchmark for an invalid setting.
        }
        return -1;
    }
}
