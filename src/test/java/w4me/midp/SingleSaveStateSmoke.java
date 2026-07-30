package w4me.midp;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;

import w4me.runtime.Wasm4Runtime;
import w4me.wasm.RuntimeSnapshot;
import w4me.wasm.WasmModule;

public final class SingleSaveStateSmoke {
    private SingleSaveStateSmoke() {}

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException(
                    "usage: font.bin save-state-roundtrip.wasm");
        }

        final int identity = 0x13572468;
        final int cartridgeLength = 789;
        WasmModule module = WasmModule.read(readFile(arguments[1]));
        Wasm4Runtime runtime = new Wasm4Runtime(readFile(arguments[0]));
        runtime.initialize(module);

        SingleSaveState missing = new SingleSaveState();
        assertEquals(
                "load before save",
                SingleSaveState.LOAD_MISSING,
                missing.load(identity, cartridgeLength, module, runtime));

        SingleSaveState slot =
                new SingleSaveState(
                        new SingleSaveState.SnapshotFactory() {
                            private int calls;

                            public RuntimeSnapshot capture(
                                    int sourceIdentity,
                                    int sourceLength,
                                    WasmModule sourceModule,
                                    Wasm4Runtime sourceRuntime) {
                                calls++;
                                if (calls == 2) {
                                    throw new OutOfMemoryError(
                                            "injected low-heap failure");
                                }
                                return RuntimeSnapshot.capture(
                                        sourceIdentity,
                                        sourceLength,
                                        sourceModule,
                                        sourceRuntime);
                            }
                        });

        module.memory()[31000] = 21;
        assertTrue(
                "initial save",
                slot.save(identity, cartridgeLength, module, runtime));
        module.memory()[31000] = 84;
        assertTrue(
                "failed replacement",
                !slot.save(identity, cartridgeLength, module, runtime));
        assertTrue("old snapshot retained", slot.hasState());
        assertEquals(
                "identity mismatch",
                SingleSaveState.LOAD_FAILED,
                slot.load(identity + 1, cartridgeLength, module, runtime));
        assertEquals(
                "mismatch leaves current memory",
                84,
                module.memory()[31000] & 0xff);
        assertEquals(
                "load after OOM",
                SingleSaveState.LOAD_OK,
                slot.load(identity, cartridgeLength, module, runtime));
        assertEquals(
                "old snapshot restored",
                21,
                module.memory()[31000] & 0xff);

        slot.clear();
        assertTrue("clear removes snapshot", !slot.hasState());
        assertEquals(
                "load after clear",
                SingleSaveState.LOAD_MISSING,
                slot.load(identity, cartridgeLength, module, runtime));
        runtime.close();
        module.close();
        System.out.println(
                "PASS single-save-state missing OOM atomic-replacement identity clear");
    }

    private static byte[] readFile(String path) throws Exception {
        InputStream input = new FileInputStream(path);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private static void assertTrue(String label, boolean value) {
        if (!value) {
            throw new AssertionError(label);
        }
    }

    private static void assertEquals(String label, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(
                    label + ": expected " + expected + ", got " + actual);
        }
    }
}
