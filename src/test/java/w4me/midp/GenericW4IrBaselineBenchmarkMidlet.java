package w4me.midp;

/** Provides the generic W4IR baseline benchmark midlet implementation. */
public final class GenericW4IrBaselineBenchmarkMidlet extends DiagnosticW4MeMidlet {
    /** Returns the app property. */
    public String getAppProperty(String name) {
        if ("W4ME-Cartridge-URL".equals(name)) {
            return "/cartridges/plasma-cube.wasm";
        }
        if ("W4ME-Disable-Fast-Paths".equals(name)
                || "W4ME-Disable-Extended-Fusions".equals(name)
                || "W4ME-Disable-Compact-Executor".equals(name)
                || "W4ME-Disable-Direct-Numeric-Intrinsics".equals(name)) {
            return "true";
        }
        if ("W4ME-Benchmark-Warmup-Frames".equals(name)) {
            return "30";
        }
        return super.getAppProperty(name);
    }
}
