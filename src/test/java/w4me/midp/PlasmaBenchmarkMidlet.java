package w4me.midp;

/** Test-only production-path Plasma Cube benchmark. */
public final class PlasmaBenchmarkMidlet extends DiagnosticW4MeMidlet {
    private boolean started;

    /** Performs the start app operation. */
    protected void startApp() {
        if (!started) {
            started = true;
            openCartridge("/cartridges/plasma-cube.wasm", "Plasma Cube");
            return;
        }
        super.startApp();
    }

    /** Returns the app property. */
    public String getAppProperty(String name) {
        if ("W4ME-Benchmark-Warmup-Frames".equals(name)) {
            return "0";
        }
        return super.getAppProperty(name);
    }
}
