package w4me.midp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Form;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordStore;
import w4me.runtime.Wasm4Runtime;
import w4me.runtime.audio.AudioBackend;
import w4me.runtime.audio.Wasm4Apu;
import w4me.wasm.WasmInterpreter;
import w4me.wasm.WasmModule;

/** Provides the cartridge store probe midlet implementation. */
public final class CartridgeStoreProbeMidlet extends MIDlet {
    private boolean started;

    /** Performs the start app operation. */
    protected void startApp() {
        if (started) {
            return;
        }
        started = true;
        Form result = new Form("RMS library probe");
        Display.getDisplay(this).setCurrent(result);
        CartridgeStore store = null;
        Wasm4Runtime runtime = null;
        try {
            byte[] cartridge = ResourceLoader.read("/cartridges/sound-demo.wasm");
            WasmModule.read(cartridge);

            final int recordsBeforeRecovery = injectInterruptedDownload();
            store = CartridgeStore.open();
            store.close();
            store = null;
            if (libraryRecordCount() != recordsBeforeRecovery) {
                throw new IllegalStateException("incomplete streamed install was not reclaimed");
            }

            store = CartridgeStore.open();
            final int before = store.list().length;
            int stagedId =
                    store.stageStream("Installed Sound Demo", new ByteArrayInputStream(cartridge), cartridge.length);
            requireEqual(cartridge, store.readStaged(stagedId));
            store.close();
            store = null;

            store = CartridgeStore.open();
            if (store.list().length != before) {
                throw new IllegalStateException("staging record leaked into library");
            }
            final CartridgeStore.CartridgeInfo committed = store.commitStaged(stagedId);
            store.close();
            store = null;

            store = CartridgeStore.open();
            if (store.list().length != before + 1) {
                throw new IllegalStateException("committed record is missing from library");
            }
            byte[] reopened = store.read(committed.recordId);
            requireEqual(cartridge, reopened);
            int countBeforeDedupe = store.list().length;
            CartridgeStore.CartridgeInfo duplicate = store.installValidated("Duplicate", cartridge);
            if (store.list().length != countBeforeDedupe || duplicate.length != cartridge.length) {
                throw new IllegalStateException("duplicate install created another cart");
            }
            int legacyStaged = store.stageValidated("Legacy record", cartridge);
            requireEqual(cartridge, store.readStaged(legacyStaged));
            CartridgeStore.CartridgeInfo legacyDuplicate = store.commitStaged(legacyStaged);
            if (legacyDuplicate.recordId != committed.recordId || store.list().length != countBeforeDedupe) {
                throw new IllegalStateException("legacy record compatibility failed");
            }
            store.close();
            store = null;

            byte[] installed = ResourceLoader.read(CartridgeStore.location(committed.recordId));
            byte[] font = ResourceLoader.read("/w4font.bin");
            WasmModule module = WasmModule.read(installed);
            CountingAudioBackend backend = new CountingAudioBackend();
            Wasm4Apu apu = new Wasm4Apu(backend);
            runtime = new Wasm4Runtime(font, apu);
            runtime.initialize(module);
            WasmInterpreter interpreter = new WasmInterpreter(module, runtime);
            interpreter.invokeCartridgeLifecycle();
            frame(module, runtime, interpreter, 0);
            frame(module, runtime, interpreter, 1);
            frame(module, runtime, interpreter, 0);
            if (apu.toneEventCount() != 1
                    || apu.lastFrequency() != 440
                    || apu.lastDuration() != 60
                    || apu.lastVolume() != 25700
                    || apu.lastFlags() != 0
                    || backend.events != 1) {
                throw new IllegalStateException("installed cartridge did not execute its tone");
            }

            System.out.println("W4ME_LIBRARY_PROBE recovery=PASS stream=PASS hidden=PASS committed=PASS"
                    + " reopen=PASS dedupe=PASS legacy=PASS chunks="
                    + committed.chunks
                    + " bytes="
                    + installed.length
                    + " tones="
                    + backend.events);
            result.append("PASS\nstreamed RMS chunks\nclose/reopen\n1518 bytes\ntone executed");
        } catch (Throwable failure) { // NOPMD -- Java ME API linkage fallback.
            System.out.println("W4ME_LIBRARY_ERROR " + failure.toString());
            failure.printStackTrace();
            result.append("FAIL\n" + failure.toString());
        } finally {
            if (store != null) {
                store.close();
            }
            if (runtime != null) {
                runtime.close();
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

    private void frame(WasmModule module, Wasm4Runtime runtime, WasmInterpreter interpreter, int gamepad)
            throws Exception {
        runtime.beginFrame(module, gamepad, 0, 0, 0);
        interpreter.invoke("update");
        runtime.endFrame();
    }

    private void requireEqual(byte[] expected, byte[] actual) {
        if (expected.length != actual.length) {
            throw new IllegalStateException("installed cartridge length mismatch");
        }
        int index;
        for (index = 0; index < expected.length; index++) {
            if (expected[index] != actual[index]) {
                throw new IllegalStateException("installed cartridge mismatch at " + index);
            }
        }
    }

    private int injectInterruptedDownload() throws Exception {
        RecordStore store = RecordStore.openRecordStore("w4lib1", true);
        final int before = store.getNumRecords();
        ByteArrayOutputStream manifestBytes = new ByteArrayOutputStream();
        DataOutputStream manifest = new DataOutputStream(manifestBytes);
        manifest.writeInt(0x57344331);
        manifest.writeInt(2);
        manifest.writeInt(0x444c4431);
        manifest.writeInt(0);
        manifest.writeInt(0);
        manifest.writeInt(0);
        manifest.writeUTF("interrupted");
        manifest.writeInt(0);
        manifest.flush();
        byte[] manifestRecord = manifestBytes.toByteArray();
        int manifestId = store.addRecord(manifestRecord, 0, manifestRecord.length);

        ByteArrayOutputStream chunkBytes = new ByteArrayOutputStream();
        DataOutputStream chunk = new DataOutputStream(chunkBytes);
        chunk.writeInt(0x57344348);
        chunk.writeInt(2);
        chunk.writeInt(manifestId);
        chunk.writeInt(0);
        chunk.writeInt(1);
        chunk.writeInt(0);
        chunk.writeByte(0);
        chunk.flush();
        byte[] chunkRecord = chunkBytes.toByteArray();
        store.addRecord(chunkRecord, 0, chunkRecord.length);
        store.closeRecordStore();
        return before;
    }

    private int libraryRecordCount() throws Exception {
        RecordStore store = RecordStore.openRecordStore("w4lib1", true);
        int count = store.getNumRecords();
        store.closeRecordStore();
        return count;
    }

    private static final class CountingAudioBackend implements AudioBackend {
        int events;

        public void submitTone(int frequency, int duration, int volume, int flags) {
            if (frequency != 440 || duration != 60 || volume != 25700 || flags != 0) {
                throw new IllegalStateException("unexpected tone event");
            }
            events++;
        }

        public void tick() {
            /* Intentionally no-op. */
        }

        public void close() {
            /* Intentionally no-op. */
        }

        public String grade() {
            return "probe";
        }
    }
}
