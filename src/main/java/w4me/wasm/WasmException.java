package w4me.wasm;

/** Provides the WASM exception implementation. */
public final class WasmException extends Exception {
    private static final long serialVersionUID = 1L;

    /** Creates a new WASM exception. */
    public WasmException(String message) {
        super(message);
    }
}
