package w4me.midp;

/** Provides the generic W4IR probe midlet implementation. */
public final class GenericW4IrProbeMidlet extends DiagnosticW4MeMidlet {
    /** Returns the app property. */
    public String getAppProperty(String name) {
        if ("W4ME-Cartridge-URL".equals(name)) {
            return "/cartridges/plasma-cube.wasm";
        }
        if ("W4ME-Diagnostics".equals(name) || "W4ME-Disable-Fast-Paths".equals(name)) {
            return "true";
        }
        return super.getAppProperty(name);
    }
}
