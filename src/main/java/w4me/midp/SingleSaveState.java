package w4me.midp;

import w4me.runtime.Wasm4Runtime;
import w4me.wasm.RuntimeSnapshot;
import w4me.wasm.WasmModule;

/** One atomically replaceable, session-local save state. */
final class SingleSaveState {
    static final int LOAD_OK = 0;
    static final int LOAD_MISSING = 1;
    static final int LOAD_FAILED = 2;

    interface SnapshotFactory {
        RuntimeSnapshot capture(int cartridgeIdentity, int cartridgeLength, WasmModule module, Wasm4Runtime runtime);
    }

    private static final SnapshotFactory DEFAULT_FACTORY = new SnapshotFactory() {
        public RuntimeSnapshot capture(
                int cartridgeIdentity, int cartridgeLength, WasmModule module, Wasm4Runtime runtime) {
            return RuntimeSnapshot.capture(cartridgeIdentity, cartridgeLength, module, runtime);
        }
    };

    private final SnapshotFactory factory;
    private RuntimeSnapshot snapshot;

    SingleSaveState() {
        this(DEFAULT_FACTORY);
    }

    SingleSaveState(SnapshotFactory factory) {
        if (factory == null) {
            throw new NullPointerException();
        }
        this.factory = factory;
    }

    boolean save(int cartridgeIdentity, int cartridgeLength, WasmModule module, Wasm4Runtime runtime) {
        try {
            RuntimeSnapshot replacement = factory.capture(cartridgeIdentity, cartridgeLength, module, runtime);
            if (replacement == null) {
                return false;
            }
            snapshot = replacement;
            return true;
        } catch (OutOfMemoryError unavailable) {
            return false;
        } catch (RuntimeException failure) { // NOPMD -- Java 1.3 lacks multi-catch.
            return false;
        }
    }

    int load(int cartridgeIdentity, int cartridgeLength, WasmModule module, Wasm4Runtime runtime) {
        RuntimeSnapshot current = snapshot;
        if (current == null) {
            return LOAD_MISSING;
        }
        try {
            return current.restore(cartridgeIdentity, cartridgeLength, module, runtime) ? LOAD_OK : LOAD_FAILED;
        } catch (OutOfMemoryError unavailable) {
            return LOAD_FAILED;
        } catch (RuntimeException failure) { // NOPMD -- Java 1.3 lacks multi-catch.
            return LOAD_FAILED;
        }
    }

    void clear() {
        snapshot = null;
    }

    boolean hasState() {
        return snapshot != null;
    }
}
