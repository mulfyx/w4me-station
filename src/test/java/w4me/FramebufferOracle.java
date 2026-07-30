package w4me;

import w4me.runtime.Wasm4Runtime;
import w4me.wasm.WasmModule;

/** Test-only framebuffer checksum shared by host and MIDP oracle harnesses. */
public final class FramebufferOracle {
    private FramebufferOracle() {}

    /** Performs the FNV 1a operation. */
    public static int fnv1a(WasmModule module) {
        byte[] memory = module.memory();
        int hash = 0x811c9dc5;
        int index;
        for (index = 0; index < Wasm4Runtime.FRAMEBUFFER_SIZE; index++) {
            hash ^= memory[Wasm4Runtime.FRAMEBUFFER + index] & 0xff;
            hash *= 0x01000193;
        }
        return hash;
    }
}
