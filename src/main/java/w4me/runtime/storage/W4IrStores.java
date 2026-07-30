package w4me.runtime.storage;

import w4me.wasm.W4IrStore;

/** Provides the W4IR stores implementation. */
public final class W4IrStores {
    private static final int DEFAULT_CACHE_SLOTS = 12;

    private W4IrStores() {}

    /** Performs the create operation. */
    public static W4IrStore create(byte[] cartridge) {
        return create(cartridge, DEFAULT_CACHE_SLOTS);
    }

    /** Performs the create operation. */
    public static W4IrStore create(byte[] cartridge, int cacheSlots) {
        try {
            return RmsW4IrStore.open(cartridge, cacheSlots);
        } catch (Throwable unavailable) { // NOPMD -- Java ME API linkage fallback.
            return null;
        }
    }
}
