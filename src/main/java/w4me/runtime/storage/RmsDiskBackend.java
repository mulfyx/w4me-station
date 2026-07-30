package w4me.runtime.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import javax.microedition.rms.RecordEnumeration;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

/** Recoverable two-generation RMS storage for the WASM-4 1024-byte disk. */
public final class RmsDiskBackend implements SnapshotDiskBackend {
    private static final int MAGIC = 0x57345356;
    private static final int FORMAT_VERSION = 1;
    private static final int HEADER_BYTES = 24;
    private static final int MAX_BYTES = 1024;

    private RecordStore store;
    private boolean available = true;

    public void open(String name) throws RecordStoreException {
        store = RecordStore.openRecordStore(name, true);
    }

    public int read(byte[] target, int offset, int size) {
        if (!available || store == null || size == 0) {
            return 0;
        }
        try {
            SaveRecord latest = findLatest();
            if (latest == null) {
                return 0;
            }
            int count = minimum(size, latest.data.length);
            System.arraycopy(latest.data, 0, target, offset, count);
            return count;
        } catch (Throwable failure) {
            available = false;
            return 0;
        }
    }

    public int write(byte[] source, int offset, int size) {
        if (!available || store == null) {
            return 0;
        }
        int count = minimum(size, MAX_BYTES);
        return replaceRange(source, offset, count) ? count : 0;
    }

    public int snapshot(byte[] target) {
        if (!available || store == null || target == null || target.length < MAX_BYTES) {
            return -1;
        }
        try {
            SaveRecord latest = findLatest();
            if (latest == null) {
                return 0;
            }
            System.arraycopy(latest.data, 0, target, 0, latest.data.length);
            return latest.data.length;
        } catch (OutOfMemoryError unavailable) {
            return -1;
        } catch (Throwable failure) {
            available = false;
            return -1;
        }
    }

    public boolean replace(byte[] source, int length) {
        if (source == null
                || length < 0
                || length > MAX_BYTES
                || length > source.length
                || !available
                || store == null) {
            return false;
        }
        return replaceRange(source, 0, length);
    }

    private boolean replaceRange(byte[] source, int offset, int count) {
        try {
            SaveRecord previous = findLatest();
            long generation = previous == null ? 1L : previous.generation + 1L;
            if (generation <= 0) {
                generation = 1L;
            }
            byte[] record = encode(generation, source, offset, count);
            int recordId = store.addRecord(record, 0, record.length);

            SaveRecord committed = decode(recordId, store.getRecord(recordId));
            if (committed.generation != generation || committed.data.length != count) {
                throw new IOException("RMS save readback mismatch");
            }
            cleanup(recordId, previous == null ? 0 : previous.recordId);
            return true;
        } catch (Throwable failure) {
            available = false;
            return false;
        }
    }

    public void close() {
        if (store != null) {
            try {
                store.closeRecordStore();
            } catch (RecordStoreException ignored) {
                // Best effort during MIDlet shutdown.
            }
            store = null;
        }
    }

    public String grade() {
        return available && store != null ? "RMS" : "memory-unavailable";
    }

    private SaveRecord findLatest() throws RecordStoreException {
        SaveRecord latest = null;
        byte[] legacy = null;
        int legacyRecordId = 0;
        int recordCount = store.getNumRecords();
        RecordEnumeration records = store.enumerateRecords(null, null, false);
        try {
            while (records.hasNextElement()) {
                int recordId = records.nextRecordId();
                byte[] record = store.getRecord(recordId);
                try {
                    SaveRecord candidate = decode(recordId, record);
                    if (latest == null || candidate.generation > latest.generation) {
                        latest = candidate;
                    }
                } catch (IOException damaged) {
                    if (recordCount == 1
                            && recordId == 1
                            && record.length <= MAX_BYTES
                            && !looksStructured(record)) {
                        legacy = record;
                        legacyRecordId = recordId;
                    }
                }
            }
        } finally {
            records.destroy();
        }
        if (latest != null) {
            return latest;
        }
        if (legacy != null) {
            byte[] data = new byte[legacy.length];
            System.arraycopy(legacy, 0, data, 0, legacy.length);
            return new SaveRecord(legacyRecordId, 0L, data);
        }
        return null;
    }

    private byte[] encode(long generation, byte[] source, int offset, int length)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(HEADER_BYTES + length);
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(MAGIC);
        output.writeInt(FORMAT_VERSION);
        output.writeLong(generation);
        output.writeInt(length);
        output.writeInt(crc32(source, offset, length));
        output.write(source, offset, length);
        output.flush();
        return bytes.toByteArray();
    }

    private SaveRecord decode(int recordId, byte[] record) throws IOException {
        if (record.length < HEADER_BYTES) {
            throw new IOException("RMS save record is truncated");
        }
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(record));
        if (input.readInt() != MAGIC || input.readInt() != FORMAT_VERSION) {
            throw new IOException("RMS save header mismatch");
        }
        long generation = input.readLong();
        int length = input.readInt();
        int crc = input.readInt();
        if (generation <= 0
                || length < 0
                || length > MAX_BYTES
                || input.available() != length) {
            throw new IOException("RMS save metadata is invalid");
        }
        byte[] data = new byte[length];
        input.readFully(data);
        if (crc32(data, 0, data.length) != crc) {
            throw new IOException("RMS save CRC mismatch");
        }
        return new SaveRecord(recordId, generation, data);
    }

    private boolean looksStructured(byte[] record) {
        if (record.length < HEADER_BYTES) {
            return false;
        }
        return readInt(record, 4) == FORMAT_VERSION
                && readInt(record, 16) >= 0
                && readInt(record, 16) <= MAX_BYTES
                && HEADER_BYTES + readInt(record, 16) == record.length;
    }

    private void cleanup(int committedRecordId, int previousRecordId) {
        try {
            int[] recordIds = new int[store.getNumRecords()];
            int count = 0;
            RecordEnumeration records = store.enumerateRecords(null, null, false);
            try {
                while (records.hasNextElement() && count < recordIds.length) {
                    recordIds[count++] = records.nextRecordId();
                }
            } finally {
                records.destroy();
            }
            int index;
            for (index = 0; index < count; index++) {
                int recordId = recordIds[index];
                if (recordId != committedRecordId && recordId != previousRecordId) {
                    try {
                        store.deleteRecord(recordId);
                    } catch (RecordStoreException ignored) {
                        // Invalid leftovers are ignored by findLatest() on the next open.
                    }
                }
            }
        } catch (RecordStoreException ignored) {
            // The newly committed record is already durable; cleanup is best effort.
        }
    }

    private int crc32(byte[] data, int offset, int length) {
        int crc = 0xffffffff;
        int index;
        for (index = 0; index < length; index++) {
            crc ^= data[offset + index] & 0xff;
            int bit;
            for (bit = 0; bit < 8; bit++) {
                crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1));
            }
        }
        return ~crc;
    }

    private int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xff) << 24)
                | ((data[offset + 1] & 0xff) << 16)
                | ((data[offset + 2] & 0xff) << 8)
                | (data[offset + 3] & 0xff);
    }

    private int minimum(int left, int right) {
        return left < right ? left : right;
    }

    private static final class SaveRecord {
        final int recordId;
        final long generation;
        final byte[] data;

        SaveRecord(int recordId, long generation, byte[] data) {
            this.recordId = recordId;
            this.generation = generation;
            this.data = data;
        }
    }
}
