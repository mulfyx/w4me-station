package w4me.midp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Form;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordStore;
import w4me.wasm.WasmModule;

/** Provides the file picker probe midlet implementation. */
public final class FilePickerProbeMidlet extends MIDlet {
    private boolean started;

    /** Performs the start app operation. */
    protected void startApp() {
        if (started) {
            return;
        }
        started = true;
        Form result = new Form("File picker probe");
        Display.getDisplay(this).setCurrent(result);
        CartridgeStore store = null;
        try {
            FileSystemAccess actual = FileSystemAccessFactory.create();
            FileEntry root = find(actual.listRoots(null, 48), "root", true);
            FileEntry directory = find(actual.list(root.url, null, 48), "w4me-picker", true);
            FilePage files = actual.list(directory.url, null, 48);
            if (findOrNull(files, "ignored.txt", false) != null) {
                throw new IllegalStateException("unrelated file escaped filtering");
            }
            FileEntry cartridge = find(files, "sound-demo.wasm", false);
            FileSelection selection = actual.inspect(cartridge.url);
            if (selection.size != 1518) {
                throw new IllegalStateException("unexpected selected size " + selection.size);
            }

            store = CartridgeStore.open();
            int before = store.list().length;
            int staged = ResourceLoader.stageFile(store, "Picked Sound Demo", selection.url, actual);
            byte[] bytes = store.readStaged(staged);
            WasmModule module = WasmModule.read(bytes);
            module.close();
            store.commitStaged(staged);
            if (store.list().length != before + 1) {
                throw new IllegalStateException("picked cartridge was not committed");
            }

            verifyPermissionDenial(store);
            verifyChangedStreamCleanup(store, bytes);
            verifyOversizedStreamCleanup(store);

            System.out.println("W4ME_FILE_PICKER_PROBE roots=PASS filter=PASS select=PASS"
                    + " size=1518 stage=PASS validate=PASS commit=PASS"
                    + " denied=PASS changed=PASS oversized=PASS cleanup=PASS");
            result.append("PASS\nJSR-75 file selected\n1518 bytes\nRMS commit\nfailure cleanup");
        } catch (Throwable failure) { // NOPMD -- Java ME API linkage fallback.
            System.out.println("W4ME_FILE_PICKER_ERROR " + failure.toString());
            failure.printStackTrace();
            result.append("FAIL\n" + failure.toString());
        } finally {
            if (store != null) {
                store.close();
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

    private void verifyPermissionDenial(CartridgeStore store) throws Exception {
        int before = store.list().length;
        int recordsBefore = recordCount();
        FakeAccess denied = FakeAccess.denied();
        try {
            ResourceLoader.stageFile(store, "Denied", "file:///root/denied.wasm", denied);
            throw new IllegalStateException("permission denial was ignored");
        } catch (SecurityException expected) {
            // Expected.
        }
        if (denied.inspectCalls != 1
                || denied.openCalls != 0
                || store.list().length != before
                || recordCount() != recordsBefore) {
            throw new IllegalStateException("permission denial retried or changed RMS");
        }
    }

    private void verifyChangedStreamCleanup(CartridgeStore store, byte[] cartridge) throws Exception {
        byte[] changed = new byte[cartridge.length + 1];
        System.arraycopy(cartridge, 0, changed, 0, cartridge.length);
        int before = store.list().length;
        int recordsBefore = recordCount();
        FakeAccess access = FakeAccess.stream(cartridge.length, changed);
        try {
            ResourceLoader.stageFile(store, "Changed", "file:///root/changed.wasm", access);
            throw new IllegalStateException("changed stream was accepted");
        } catch (IOException expected) {
            // Expected.
        }
        if (!access.closed || store.list().length != before || recordCount() != recordsBefore) {
            throw new IllegalStateException("changed stream leaked input or RMS records");
        }
    }

    private void verifyOversizedStreamCleanup(CartridgeStore store) throws Exception {
        int before = store.list().length;
        int recordsBefore = recordCount();
        FakeAccess knownOversized = FakeAccess.stream(CartridgeStore.MAX_CARTRIDGE_BYTES + 1L, new byte[0]);
        try {
            ResourceLoader.stageFile(store, "Known oversized", "file:///root/known-oversized.wasm", knownOversized);
            throw new IllegalStateException("known oversized stream was accepted");
        } catch (IOException expected) {
            // Expected.
        }
        if (knownOversized.openCalls != 0 || store.list().length != before || recordCount() != recordsBefore) {
            throw new IllegalStateException("known oversized file was opened or changed RMS");
        }

        byte[] oversized = new byte[CartridgeStore.MAX_CARTRIDGE_BYTES + 1];
        oversized[0] = 0;
        oversized[1] = 'a';
        oversized[2] = 's';
        oversized[3] = 'm';
        FakeAccess access = FakeAccess.stream(-1, oversized);
        try {
            ResourceLoader.stageFile(store, "Oversized", "file:///root/oversized.wasm", access);
            throw new IllegalStateException("oversized stream was accepted");
        } catch (IOException expected) {
            // Expected.
        }
        if (!access.closed || store.list().length != before || recordCount() != recordsBefore) {
            throw new IllegalStateException("oversized stream leaked input or RMS records");
        }
    }

    private FileEntry find(FilePage page, String name, boolean directory) {
        FileEntry result = findOrNull(page, name, directory);
        if (result == null) {
            throw new IllegalStateException("missing file entry " + name);
        }
        return result;
    }

    private FileEntry findOrNull(FilePage page, String name, boolean directory) {
        int index;
        for (index = 0; index < page.entries.length; index++) {
            FileEntry entry = page.entries[index];
            if (entry.directory == directory && name.equals(entry.name)) {
                return entry;
            }
        }
        return null;
    }

    private int recordCount() throws Exception {
        RecordStore records = RecordStore.openRecordStore("w4lib1", true);
        try {
            return records.getNumRecords();
        } finally {
            records.closeRecordStore();
        }
    }

    private static final class FakeAccess implements FileSystemAccess {
        private final long expectedSize;
        private final byte[] bytes;
        private final boolean deny;
        int inspectCalls;
        int openCalls;
        boolean closed;

        private FakeAccess(long expectedSize, byte[] bytes, boolean deny) {
            this.expectedSize = expectedSize;
            this.bytes = bytes;
            this.deny = deny;
        }

        static FakeAccess denied() {
            return new FakeAccess(0, null, true);
        }

        static FakeAccess stream(long expectedSize, byte[] bytes) {
            return new FakeAccess(expectedSize, bytes, false);
        }

        public FilePage listRoots(String afterKey, int limit) throws IOException {
            throw new IOException("not used");
        }

        public FilePage list(String directoryUrl, String afterKey, int limit) throws IOException {
            throw new IOException("not used");
        }

        public FileSelection inspect(String fileUrl) throws IOException {
            inspectCalls++;
            if (deny) {
                throw new SecurityException("denied by probe");
            }
            return new FileSelection("probe.wasm", fileUrl, expectedSize);
        }

        public InputStream openInputStream(String fileUrl) {
            openCalls++;
            return new TrackingInputStream(bytes, this);
        }
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        private final FakeAccess owner;

        TrackingInputStream(byte[] bytes, FakeAccess owner) {
            super(bytes);
            this.owner = owner;
        }

        public void close() throws IOException {
            owner.closed = true;
            super.close();
        }
    }
}
