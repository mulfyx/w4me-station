package w4me.wasm;

/** Provides the WASM trap implementation. */
public final class WasmTrap extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** Creates a new WASM trap. */
    public WasmTrap(String message) {
        super(message);
    }
}
