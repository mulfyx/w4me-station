package w4me.midp;

/** Test-only library MIDlet that enables production-path benchmark receipts. */
public final class DiagnosticBenchmarkLibraryMidlet extends DiagnosticW4MeMidlet {
    private boolean started;

    /** Performs the start app operation. */
    protected void startApp() {
        if (!started) {
            started = true;
            openPlasma();
            return;
        }
        super.startApp();
    }

    void showLibrary() {
        openPlasma();
    }

    /** Returns the app property. */
    public String getAppProperty(String name) {
        if ("W4ME-Benchmark-Warmup-Frames".equals(name)) {
            return "0";
        }
        return super.getAppProperty(name);
    }

    private void openPlasma() {
        openCartridge("/cartridges/plasma-cube.wasm", "Plasma Cube");
    }
}
