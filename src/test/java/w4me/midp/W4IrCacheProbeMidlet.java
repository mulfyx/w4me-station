package w4me.midp;

import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Form;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordEnumeration;
import javax.microedition.rms.RecordStore;
import w4me.FramebufferOracle;
import w4me.runtime.Wasm4Runtime;
import w4me.runtime.storage.W4IrStores;
import w4me.wasm.W4IrFunction;
import w4me.wasm.W4IrStore;
import w4me.wasm.WasmInterpreter;
import w4me.wasm.WasmModule;

/** Provides the W4IR cache probe midlet implementation. */
public final class W4IrCacheProbeMidlet extends MIDlet {
    private static final int MANIFEST_MAGIC = 0x57344952;
    private static final int EXPECTED_FRAME_FNV = 0x2e572184;
    private static final int EXPECTED_FRAME_10_FNV = 0xf90becd4;
    private boolean started;

    /** Performs the start app operation. */
    protected void startApp() {
        if (started) {
            return;
        }
        started = true;
        Form result = new Form("W4IR cache probe");
        Display.getDisplay(this).setCurrent(result);
        WasmModule first = null;
        WasmModule second = null;
        Wasm4Runtime runtime = null;
        try {
            byte[] cartridge = ResourceLoader.read("/cartridges/plasma-cube.wasm");
            W4IrStore interrupted = W4IrStores.create(cartridge, 12);
            if (interrupted == null) {
                throw new IllegalStateException("RMS W4IR store is unavailable");
            }
            interrupted.begin(1);
            interrupted.close();

            W4IrStore firstStore = W4IrStores.create(cartridge, 12);
            if (firstStore == null) {
                throw new IllegalStateException("RMS W4IR store is unavailable");
            }
            first = WasmModule.read(cartridge, firstStore);
            if (!"RMS-build".equals(first.w4irStatus())) {
                throw new IllegalStateException("first parse did not build W4IR cache");
            }
            first.close();
            first = null;

            downgradeManifest(cartridge);
            W4IrStore migratedStore = W4IrStores.create(cartridge, 12);
            if (migratedStore == null) {
                throw new IllegalStateException("RMS W4IR migration store is unavailable");
            }
            first = WasmModule.read(cartridge, migratedStore);
            if (!"RMS-build".equals(first.w4irStatus())) {
                throw new IllegalStateException("old W4IR format was not rebuilt");
            }
            final int descriptorHash = descriptorHash(migratedStore, 10);
            first.close();
            first = null;

            W4IrStore secondStore = W4IrStores.create(cartridge, 12);
            if (secondStore == null) {
                throw new IllegalStateException("RMS W4IR store cannot reopen");
            }
            second = WasmModule.read(cartridge, secondStore);
            if (!"RMS-hit".equals(second.w4irStatus())) {
                throw new IllegalStateException("second parse did not hit W4IR cache");
            }
            int reopenedDescriptorHash = descriptorHash(secondStore, 10);
            if (reopenedDescriptorHash != descriptorHash) {
                throw new IllegalStateException("reopened W4IR branch descriptors changed");
            }

            runtime = new Wasm4Runtime(ResourceLoader.read("/w4font.bin"));
            runtime.initialize(second);
            WasmInterpreter interpreter = new WasmInterpreter(second, runtime);
            interpreter.setFastPathsEnabled(false);
            interpreter.invokeCartridgeLifecycle();
            final long firstStarted = System.currentTimeMillis();
            runtime.beginFrame(second, 0, 0, 0, 0);
            interpreter.invoke("update");
            runtime.endFrame();
            final long firstMillis = System.currentTimeMillis() - firstStarted;
            long compactBlocks = interpreter.compactBlockCalls();
            long compactInstructions = interpreter.compactInstructionsExecuted();
            long traceLoops = interpreter.traceLoopCalls();
            long traceIterations = interpreter.traceLoopIterations();
            int fastPaths = interpreter.fastPathCalls();
            int frameHash = FramebufferOracle.fnv1a(second);
            if (frameHash != EXPECTED_FRAME_FNV) {
                throw new IllegalStateException("cached W4IR framebuffer mismatch: " + hex8(frameHash));
            }
            if (second.w4irPageFaults() <= 0) {
                throw new IllegalStateException("cached W4IR executed without a code page fault");
            }
            final int faultsAfterFirst = second.w4irPageFaults();
            long warmedStarted = System.currentTimeMillis();
            int frame;
            for (frame = 1; frame <= 10; frame++) {
                runtime.beginFrame(second, 0, 0, 0, 0);
                interpreter.invoke("update");
                runtime.endFrame();
                compactBlocks += interpreter.compactBlockCalls();
                compactInstructions += interpreter.compactInstructionsExecuted();
                traceLoops += interpreter.traceLoopCalls();
                traceIterations += interpreter.traceLoopIterations();
                fastPaths += interpreter.fastPathCalls();
            }
            final long warmedAverage = (System.currentTimeMillis() - warmedStarted) / 10;
            int frame10Hash = FramebufferOracle.fnv1a(second);
            if (frame10Hash != EXPECTED_FRAME_10_FNV) {
                throw new IllegalStateException("cached W4IR frame 10 mismatch: " + hex8(frame10Hash));
            }
            if (second.w4irPromotedFunctions() <= 0) {
                throw new IllegalStateException("hot W4IR function was not promoted");
            }
            boolean compactCounters = compactBlocks != 0 || compactInstructions != 0;
            if (compactCounters && (compactBlocks <= 0 || compactInstructions <= 0)) {
                throw new IllegalStateException("diagnostic compact counters are incomplete");
            }
            if (traceLoops <= 0 || traceIterations <= 0) {
                throw new IllegalStateException("cached W4IR did not execute through counted traces");
            }
            if (fastPaths != 0) {
                throw new IllegalStateException("cartridge fast path was executed");
            }

            System.out.println("W4ME_W4IR_PROBE recovery=PASS old-format=PASS build=PASS hit=PASS"
                    + " descriptors=PASS descriptor-hash="
                    + hex8(descriptorHash)
                    + " slots=12 faults="
                    + second.w4irPageFaults()
                    + " warm-faults="
                    + (second.w4irPageFaults() - faultsAfterFirst)
                    + " hits="
                    + second.w4irPageHits()
                    + " promoted="
                    + second.w4irPromotedFunctions()
                    + " compact-counters="
                    + (compactCounters ? "on" : "off")
                    + " compact-blocks="
                    + compactBlocks
                    + " compact-instructions="
                    + compactInstructions
                    + " trace-loops="
                    + traceLoops
                    + " trace-iterations="
                    + traceIterations
                    + " fast-paths="
                    + fastPaths
                    + " first-ms="
                    + firstMillis
                    + " warm-average-ms="
                    + warmedAverage
                    + " frame0-fnv1a="
                    + hex8(frameHash)
                    + " frame10-fnv1a="
                    + hex8(frame10Hash));
            result.append("PASS\nbuild -> RMS\nreopen -> cache hit\n12 page slots\nfaults: "
                    + second.w4irPageFaults()
                    + "\nwarm avg: "
                    + warmedAverage
                    + " ms\npromoted: "
                    + second.w4irPromotedFunctions()
                    + "\ncompact blocks: "
                    + compactBlocks
                    + "\ntrace loops: "
                    + traceLoops
                    + "\nframe 10: "
                    + hex8(frame10Hash));
        } catch (Throwable failure) { // NOPMD -- Java ME API linkage fallback.
            System.out.println("W4ME_W4IR_ERROR " + failure.toString());
            failure.printStackTrace();
            result.append("FAIL\n" + failure.toString());
        } finally {
            if (runtime != null) {
                runtime.close();
            }
            if (first != null) {
                first.close();
            }
            if (second != null) {
                second.close();
            }
        }
    }

    /** Performs the pause app operation. */
    protected void pauseApp() {
        /* Intentionally no-op. */
    }

    /** Performs the destroy app operation. */
    protected void destroyApp(boolean unconditional) {
        /* Intentionally no-op. */
    }

    private int descriptorHash(W4IrStore store, int functionCount) throws Exception {
        if (!store.isComplete(functionCount)) {
            throw new IllegalStateException("W4IR descriptor cache function count mismatch");
        }
        int hash = 0x811c9dc5;
        int descriptorInts = 0;
        int function;
        for (function = 0; function < functionCount; function++) {
            W4IrFunction metadata = store.loadFunction(function);
            int[] descriptors = metadata.branchDescriptors();
            descriptorInts += descriptors.length;
            hash = hashInts(hash, descriptors);
            hash = hashInts(hash, metadata.branchDescriptorPcs());
            hash = hashInts(hash, metadata.branchDescriptorIndices());
            int[][] tables = metadata.branchDescriptorTables();
            int table;
            for (table = 0; table < tables.length; table++) {
                hash = hashInts(hash, tables[table]);
            }
        }
        if (descriptorInts == 0) {
            throw new IllegalStateException("W4IR cache contains no branch descriptors");
        }
        return hash;
    }

    private int hashInts(int hash, int[] values) {
        int index;
        for (index = 0; index < values.length; index++) {
            int value = values[index];
            hash ^= value;
            hash *= 0x01000193;
        }
        return hash;
    }

    private void downgradeManifest(byte[] cartridge) throws Exception {
        String storeName = "w4i11" + hex8(fnv1a(cartridge));
        RecordStore rawStore = RecordStore.openRecordStore(storeName, false);
        RecordEnumeration records = rawStore.enumerateRecords(null, null, false);
        boolean changed = false;
        try {
            while (records.hasNextElement()) {
                int recordId = records.nextRecordId();
                byte[] record = rawStore.getRecord(recordId);
                if (record.length >= 8
                        && readInt(record, 0) == MANIFEST_MAGIC
                        && readInt(record, 4) == WasmModule.W4IR_FORMAT_VERSION) {
                    writeInt(record, 4, WasmModule.W4IR_FORMAT_VERSION - 1);
                    rawStore.setRecord(recordId, record, 0, record.length);
                    changed = true;
                    break;
                }
            }
        } finally {
            records.destroy();
            rawStore.closeRecordStore();
        }
        if (!changed) {
            throw new IllegalStateException("current W4IR manifest was not found");
        }
    }

    private int fnv1a(byte[] bytes) {
        int hash = 0x811c9dc5;
        int index;
        for (index = 0; index < bytes.length; index++) {
            hash ^= bytes[index] & 0xff;
            hash *= 0x01000193;
        }
        return hash;
    }

    private int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
                | ((bytes[offset + 1] & 0xff) << 16)
                | ((bytes[offset + 2] & 0xff) << 8)
                | (bytes[offset + 3] & 0xff);
    }

    private void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }

    private String hex8(int value) {
        String hex = Integer.toHexString(value);
        StringBuffer result = new StringBuffer(8);
        int padding;
        for (padding = hex.length(); padding < 8; padding++) {
            result.append('0');
        }
        result.append(hex);
        return result.toString();
    }
}
