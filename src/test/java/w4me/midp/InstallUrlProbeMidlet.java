package w4me.midp;

/** Supplies the deterministic HTTP fixture URL while retaining product library navigation. */
public final class InstallUrlProbeMidlet extends DiagnosticW4MeMidlet {
    private static final String URL = "http://127.0.0.1:18385/sound-demo.wasm";

    /** Returns the app property. */
    public String getAppProperty(String name) {
        if ("W4ME-Cartridge-URL".equals(name)) {
            return URL;
        }
        if ("W4ME-Diagnostics".equals(name)) {
            return "true";
        }
        return super.getAppProperty(name);
    }
}
