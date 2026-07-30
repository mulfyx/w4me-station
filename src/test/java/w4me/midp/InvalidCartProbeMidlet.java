package w4me.midp;

/** Provides the invalid cart probe midlet implementation. */
public final class InvalidCartProbeMidlet extends DiagnosticW4MeMidlet {
    private static final String URL = "http://127.0.0.1:18386/bad.wasm";

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
