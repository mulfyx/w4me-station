package w4me.midp;

import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Form;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordEnumeration;
import javax.microedition.rms.RecordStore;
import w4me.runtime.storage.DiskBackend;
import w4me.runtime.storage.DiskBackends;
import w4me.runtime.storage.RmsDiskBackend;

/** Provides the RMS disk probe midlet implementation. */
public final class RmsDiskProbeMidlet extends MIDlet {
    private boolean started;

    /** Performs the start app operation. */
    protected void startApp() {
        if (started) {
            return;
        }
        started = true;
        Form result = new Form("RMS disk probe");
        Display.getDisplay(this).setCurrent(result);
        try {
            byte[] cartridge = ResourceLoader.read("/cartridges/sound-demo.wasm");
            byte[] expected = new byte[1024];
            int index;
            for (index = 0; index < expected.length; index++) {
                expected[index] = (byte) (index * 73 + 19);
            }
            DiskBackend writer = DiskBackends.create(cartridge);
            String writeGrade = writer.grade();
            final int written = writer.write(expected, 0, expected.length);
            writer.close();

            byte[] actual = new byte[1024];
            DiskBackend reader = DiskBackends.create(cartridge);
            String readGrade = reader.grade();
            int read = reader.read(actual, 0, actual.length);
            reader.close();
            if (!"RMS".equals(writeGrade) || !"RMS".equals(readGrade)) {
                throw new IllegalStateException("RMS unavailable: write=" + writeGrade + " read=" + readGrade);
            }
            if (written != expected.length || read != expected.length) {
                throw new IllegalStateException("RMS length mismatch: wrote=" + written + " read=" + read);
            }
            for (index = 0; index < expected.length; index++) {
                if (expected[index] != actual[index]) {
                    throw new IllegalStateException("RMS byte mismatch at " + index);
                }
            }
            testRecovery(expected);
            testLegacyMigration(expected);
            System.out.println(
                    "W4ME_RMS_PROBE grade=RMS bytes=1024 reopen=PASS" + " recovery=PASS legacy=PASS records=2");
            result.append("PASS\n1024 bytes\nA/B recovery\nlegacy migration");
        } catch (Throwable failure) { // NOPMD -- Java ME API linkage fallback.
            System.out.println("W4ME_RMS_ERROR " + failure.toString());
            failure.printStackTrace();
            result.append("FAIL\n" + failure.toString());
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

    private void testRecovery(byte[] seed) throws Exception {
        String name = "w4dprobe1";
        deleteStore(name);
        byte[] first = transformed(seed, 0x11);
        byte[] second = transformed(seed, 0x22);
        final byte[] third = transformed(seed, 0x33);
        final byte[] fourth = transformed(seed, 0x44);

        RmsDiskBackend backend = new RmsDiskBackend();
        backend.open(name);
        requireWritten("first generation", first.length, backend.write(first, 0, first.length));
        requireWritten("second generation", second.length, backend.write(second, 0, second.length));
        backend.close();

        corruptNewest(name);
        backend = new RmsDiskBackend();
        backend.open(name);
        requireRead("damaged newest generation", first, backend);
        requireWritten("recovered generation", third.length, backend.write(third, 0, third.length));
        backend.close();

        addInterruptedRecord(name);
        backend = new RmsDiskBackend();
        backend.open(name);
        requireRead("interrupted generation", third, backend);
        requireWritten("post-recovery generation", fourth.length, backend.write(fourth, 0, fourth.length));
        backend.close();

        backend = new RmsDiskBackend();
        backend.open(name);
        requireRead("final generation", fourth, backend);
        backend.close();
        RecordStore store = RecordStore.openRecordStore(name, false);
        int records = store.getNumRecords();
        store.closeRecordStore();
        if (records != 2) {
            throw new IllegalStateException("expected two retained save generations, got " + records);
        }
        deleteStore(name);
    }

    private void testLegacyMigration(byte[] seed) throws Exception {
        String name = "w4dprobe2";
        deleteStore(name);
        byte[] legacy = transformed(seed, 0x55);
        final byte[] migrated = transformed(seed, 0x66);
        RecordStore store = RecordStore.openRecordStore(name, true);
        store.addRecord(legacy, 0, legacy.length);
        store.closeRecordStore();

        RmsDiskBackend backend = new RmsDiskBackend();
        backend.open(name);
        requireRead("legacy save", legacy, backend);
        requireWritten("migrated save", migrated.length, backend.write(migrated, 0, migrated.length));
        backend.close();

        backend = new RmsDiskBackend();
        backend.open(name);
        requireRead("reopened migrated save", migrated, backend);
        backend.close();
        deleteStore(name);
    }

    private void corruptNewest(String name) throws Exception {
        RecordStore store = RecordStore.openRecordStore(name, false);
        RecordEnumeration records = store.enumerateRecords(null, null, false);
        int newest = 0;
        try {
            while (records.hasNextElement()) {
                int recordId = records.nextRecordId();
                if (recordId > newest) {
                    newest = recordId;
                }
            }
        } finally {
            records.destroy();
        }
        byte[] record = store.getRecord(newest);
        record[record.length - 1] ^= 0x5a;
        store.setRecord(newest, record, 0, record.length);
        store.closeRecordStore();
    }

    private void addInterruptedRecord(String name) throws Exception {
        RecordStore store = RecordStore.openRecordStore(name, false);
        byte[] partial = {0x57, 0x34, 0x53, 0x56, 0, 0, 0, 1, 0, 0, 0};
        store.addRecord(partial, 0, partial.length);
        store.closeRecordStore();
    }

    private void requireRead(String label, byte[] expected, RmsDiskBackend backend) {
        byte[] actual = new byte[expected.length];
        int count = backend.read(actual, 0, actual.length);
        if (count != expected.length) {
            throw new IllegalStateException(label + " length mismatch: " + count);
        }
        int index;
        for (index = 0; index < expected.length; index++) {
            if (expected[index] != actual[index]) {
                throw new IllegalStateException(label + " byte mismatch at " + index);
            }
        }
    }

    private void requireWritten(String label, int expected, int actual) {
        if (expected != actual) {
            throw new IllegalStateException(label + " write mismatch: " + actual);
        }
    }

    private byte[] transformed(byte[] source, int xor) {
        byte[] result = new byte[source.length];
        int index;
        for (index = 0; index < result.length; index++) {
            result[index] = (byte) (source[index] ^ xor);
        }
        return result;
    }

    private void deleteStore(String name) {
        try {
            RecordStore.deleteRecordStore(name);
        } catch (Throwable ignored) { // NOPMD -- Java ME API linkage fallback.
            // The test store may not exist on a fresh KEmulator profile.
        }
    }
}
