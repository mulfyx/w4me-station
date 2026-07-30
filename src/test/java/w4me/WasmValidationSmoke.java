package w4me;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import w4me.wasm.W4IrFunction;
import w4me.wasm.W4IrStore;
import w4me.wasm.WasmException;
import w4me.wasm.WasmModule;

/** Provides the WASM validation smoke implementation. */
public final class WasmValidationSmoke {
    private static final byte[] HEADER = bytes(0x00, 0x61, 0x73, 0x6d, 0x01, 0, 0, 0);

    private WasmValidationSmoke() {}

    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("expected a valid cartridge path");
        }

        WasmModule corpusModule = WasmModule.read(readFile(new File(arguments[0])));
        corpusModule.close();
        WasmModule minimalModule = WasmModule.read(minimalModule(0, normalBody()));
        minimalModule.close();

        expect("invalid WebAssembly magic", replaceHeaderByte(minimalModule(0, normalBody()), 0, 1));
        expect("64 KiB limit", new byte[65537]);
        expect("duplicate section 1", concat(new byte[][] {HEADER, section(1, bytes(0)), section(1, bytes(0))}));
        expect("unsupported function import env.evil", concat(new byte[][] {
            HEADER, typeSection(0), section(2, bytes(1, 3, 'e', 'n', 'v', 4, 'e', 'v', 'i', 'l', 0, 0))
        }));
        expect("invalid signature for function import env.rect", concat(new byte[][] {
            HEADER, typeSection(0), section(2, bytes(1, 3, 'e', 'n', 'v', 4, 'r', 'e', 'c', 't', 0, 0))
        }));
        expect("function export index is out of range", minimalModule(2, normalBody()));
        expect("export update must have signature () -> ()", moduleWithParameter());
        expect("local index is out of range", minimalModule(0, bytes(4, 0, 0x20, 1, 0x0b)));
        expect("branch depth is out of range", minimalModule(0, bytes(4, 0, 0x0c, 1, 0x0b)));
        expect("signed LEB128 overflow", minimalModule(0, bytes(9, 0, 0x41, 0x80, 0x80, 0x80, 0x80, 0x10, 0x1a, 0x0b)));
        expect("duplicate export name: update", moduleWithDuplicateExport());
        expect("memory limits cannot accept", moduleWithInvalidMemoryLimits());
        expect("operand stack underflow", minimalModule(0, bytes(3, 0, 0x1a, 0x0b)));
        expect(
                "operand stack type mismatch: expected i32, got i64",
                minimalModule(0, bytes(5, 0, 0x42, 0, 0x45, 0x0b)));
        expect("operand stack has extra values at control boundary", minimalModule(0, bytes(4, 0, 0x41, 0, 0x0b)));
        verifyInvalidModuleCannotCommitW4Ir();

        System.out.println("PASS wasm validation negative-cases=14 valid-cases=2 cache-commit-gate=PASS");
    }

    private static void verifyInvalidModuleCannotCommitW4Ir() throws Exception {
        RecordingStore store = new RecordingStore();
        try {
            WasmModule.read(moduleWithDuplicateExport(), store);
            throw new AssertionError("invalid module unexpectedly passed validation");
        } catch (WasmException expected) {
            if (!store.began || !store.wroteFunction) {
                throw new AssertionError( // NOPMD -- CLDC 1.1 has no cause chaining.
                        "W4IR staging path was not exercised");
            }
            if (store.committed) {
                throw new AssertionError( // NOPMD -- CLDC 1.1 has no cause chaining.
                        "W4IR committed before whole-module validation");
            }
        }
    }

    private static byte[] minimalModule(int updateIndex, byte[] body) {
        return concat(new byte[][] {
            HEADER,
            typeSection(0),
            section(3, bytes(1, 0)),
            memorySection(bytes(0, 1)),
            updateExportSection(updateIndex),
            section(10, concat(new byte[][] {bytes(1), body}))
        });
    }

    private static byte[] moduleWithParameter() {
        return concat(new byte[][] {
            HEADER,
            typeSection(1),
            section(3, bytes(1, 0)),
            memorySection(bytes(0, 1)),
            updateExportSection(0),
            section(10, concat(new byte[][] {bytes(1), normalBody()}))
        });
    }

    private static byte[] moduleWithDuplicateExport() {
        byte[] entry = bytes(6, 'u', 'p', 'd', 'a', 't', 'e', 0, 0);
        return concat(new byte[][] {
            HEADER,
            typeSection(0),
            section(3, bytes(1, 0)),
            memorySection(bytes(0, 1)),
            section(7, concat(new byte[][] {bytes(2), entry, entry})),
            section(10, concat(new byte[][] {bytes(1), normalBody()}))
        });
    }

    private static byte[] moduleWithInvalidMemoryLimits() {
        return concat(new byte[][] {
            HEADER,
            typeSection(0),
            section(3, bytes(1, 0)),
            memorySection(bytes(1, 0, 0)),
            updateExportSection(0),
            section(10, concat(new byte[][] {bytes(1), normalBody()}))
        });
    }

    private static byte[] typeSection(int parameterCount) {
        if (parameterCount == 0) {
            return section(1, bytes(1, 0x60, 0, 0));
        }
        return section(1, bytes(1, 0x60, 1, 0x7f, 0));
    }

    private static byte[] memorySection(byte[] limits) {
        return section(5, concat(new byte[][] {bytes(1), limits}));
    }

    private static byte[] updateExportSection(int index) {
        return section(7, bytes(1, 6, 'u', 'p', 'd', 'a', 't', 'e', 0, index));
    }

    private static byte[] normalBody() {
        return bytes(2, 0, 0x0b);
    }

    private static byte[] section(int id, byte[] payload) {
        if (payload.length >= 128) {
            throw new IllegalArgumentException("test section is too large");
        }
        return concat(new byte[][] {bytes(id, payload.length), payload});
    }

    private static void expect(String message, byte[] module) throws Exception {
        try {
            WasmModule value = WasmModule.read(module);
            value.close();
            throw new AssertionError("expected validation failure containing: " + message);
        } catch (WasmException expected) {
            if (expected.getMessage().indexOf(message) < 0) {
                throw new AssertionError( // NOPMD -- CLDC 1.1 has no cause chaining.
                        "expected validation failure containing '"
                                + message
                                + "', got '"
                                + expected.getMessage()
                                + "'");
            }
        }
    }

    private static byte[] replaceHeaderByte(byte[] source, int index, int value) {
        byte[] result = new byte[source.length];
        System.arraycopy(source, 0, result, 0, source.length);
        result[index] = (byte) value;
        return result;
    }

    private static byte[] concat(byte[][] parts) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int index;
        for (index = 0; index < parts.length; index++) {
            output.write(parts[index], 0, parts[index].length);
        }
        return output.toByteArray();
    }

    private static byte[] bytes(int a) {
        return new byte[] {(byte) a};
    }

    private static byte[] bytes(int a, int b) {
        return new byte[] {(byte) a, (byte) b};
    }

    private static byte[] bytes(int a, int b, int c) {
        return new byte[] {(byte) a, (byte) b, (byte) c};
    }

    private static byte[] bytes(int a, int b, int c, int d) {
        return new byte[] {(byte) a, (byte) b, (byte) c, (byte) d};
    }

    private static byte[] bytes(int a, int b, int c, int d, int e) {
        return new byte[] {(byte) a, (byte) b, (byte) c, (byte) d, (byte) e};
    }

    private static byte[] bytes(int a, int b, int c, int d, int e, int f) {
        return new byte[] {(byte) a, (byte) b, (byte) c, (byte) d, (byte) e, (byte) f};
    }

    private static byte[] bytes(int a, int b, int c, int d, int e, int f, int g, int h) {
        return new byte[] {
            (byte) a, (byte) b, (byte) c, (byte) d,
            (byte) e, (byte) f, (byte) g, (byte) h
        };
    }

    private static byte[] bytes(int a, int b, int c, int d, int e, int f, int g, int h, int i) {
        return new byte[] {(byte) a, (byte) b, (byte) c, (byte) d, (byte) e, (byte) f, (byte) g, (byte) h, (byte) i};
    }

    private static byte[] bytes(int a, int b, int c, int d, int e, int f, int g, int h, int i, int j) {
        return new byte[] {
            (byte) a, (byte) b, (byte) c, (byte) d, (byte) e,
            (byte) f, (byte) g, (byte) h, (byte) i, (byte) j
        };
    }

    private static byte[] bytes(int a, int b, int c, int d, int e, int f, int g, int h, int i, int j, int k, int l) {
        return new byte[] {
            (byte) a, (byte) b, (byte) c, (byte) d, (byte) e, (byte) f,
            (byte) g, (byte) h, (byte) i, (byte) j, (byte) k, (byte) l
        };
    }

    private static byte[] readFile(File file) throws IOException {
        FileInputStream input = new FileInputStream(file);
        try {
            byte[] result = new byte[(int) file.length()];
            int offset = 0;
            while (offset < result.length) {
                int count = input.read(result, offset, result.length - offset);
                if (count < 0) {
                    throw new IOException("unexpected end of file");
                }
                offset += count;
            }
            return result;
        } finally {
            input.close();
        }
    }

    private static final class RecordingStore implements W4IrStore {
        boolean began;
        boolean wroteFunction;
        boolean committed;

        public boolean isComplete(int functionCount) {
            return false;
        }

        public W4IrFunction loadFunction(int functionIndex) throws WasmException {
            throw new WasmException("recording store has no complete cache");
        }

        public void begin(int functionCount) {
            began = true;
        }

        public void writeFunction(
                int functionIndex,
                int declaredLocalCount,
                int[] code,
                int[][] branchTables,
                int[] branchDescriptors,
                int[] branchDescriptorPcs,
                int[] branchDescriptorIndices,
                int[][] branchDescriptorTables,
                long fingerprint,
                int intrinsic) {
            wroteFunction = true;
        }

        public void commit() {
            committed = true;
        }

        public int[] loadPage(W4IrFunction function, int pageIndex) {
            throw new IllegalStateException("recording store has no pages");
        }

        public int pageFaults() {
            return 0;
        }

        public int pageHits() {
            return 0;
        }

        public void discard() {
            /* Intentionally no-op. */
        }

        public void close() {
            /* Intentionally no-op. */
        }
    }
}
