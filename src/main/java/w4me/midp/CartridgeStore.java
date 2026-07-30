package w4me.midp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Vector;
import javax.microedition.rms.RecordEnumeration;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

/** Transactional RMS library for validated WASM-4 cartridges. */
final class CartridgeStore {
    static final int MAX_CARTRIDGE_BYTES = 64 * 1024;

    private static final String STORE_NAME = "w4lib1";
    private static final int MAGIC = 0x57344331;
    private static final int CHUNK_MAGIC = 0x57344348;
    private static final int LEGACY_VERSION = 1;
    private static final int STREAM_VERSION = 2;
    private static final int STATE_DOWNLOADING = 0x444c4431;
    private static final int STATE_STAGING = 0x53544731;
    private static final int STATE_COMMITTED = 0x434d5431;
    private static final int STATE_OFFSET = 8;
    private static final int CHUNK_BYTES = 2048;
    private static final int MAX_CHUNKS = MAX_CARTRIDGE_BYTES / CHUNK_BYTES;

    private RecordStore store;

    private CartridgeStore(RecordStore store) {
        this.store = store;
    }

    static CartridgeStore open() throws RecordStoreException {
        CartridgeStore result = new CartridgeStore(RecordStore.openRecordStore(STORE_NAME, true));
        try {
            result.recoverIncompleteDownloads();
            return result;
        } catch (RecordStoreException failure) {
            result.close();
            throw failure;
        }
    }

    synchronized CartridgeInfo[] list() throws RecordStoreException {
        requireOpen();
        Vector entries = new Vector();
        RecordEnumeration records = store.enumerateRecords(null, null, false);
        try {
            while (records.hasNextElement()) {
                int recordId = records.nextRecordId();
                try {
                    CartridgeInfo info = decode(recordId, store.getRecord(recordId), STATE_COMMITTED, false);
                    entries.addElement(info);
                } catch (IOException ignored) {
                    // Staging, chunk, and damaged records stay invisible to the library.
                }
            }
        } finally {
            records.destroy();
        }

        CartridgeInfo[] result = new CartridgeInfo[entries.size()];
        entries.copyInto(result);
        sortByRecordId(result);
        return result;
    }

    synchronized CartridgeInfo installValidated(String title, byte[] cartridge)
            throws IOException, RecordStoreException {
        requireCartridge(cartridge);
        int length = cartridge.length;
        int crc = crc32(cartridge);
        int hash = fnv1a(cartridge);
        CartridgeInfo duplicate = findDuplicate(length, crc, hash);
        if (duplicate != null) {
            return duplicate;
        }
        return commitStaged(stageValidated(title, cartridge));
    }

    synchronized int stageValidated(String title, byte[] cartridge) throws IOException, RecordStoreException {
        requireOpen();
        requireCartridge(cartridge);
        byte[] record = encodeLegacy(title, cartridge, STATE_STAGING);
        int recordId = store.addRecord(record, 0, record.length);

        // Read the record back before committing. If the process stops here, list() ignores it.
        decode(recordId, store.getRecord(recordId), STATE_STAGING, false);
        return recordId;
    }

    synchronized int stageStream(String title, InputStream input, long expectedLength)
            throws IOException, RecordStoreException {
        requireOpen();
        if (input == null) {
            throw new IOException("cartridge stream is missing");
        }
        if (expectedLength > MAX_CARTRIDGE_BYTES) {
            throw new IOException("cartridge exceeds the WASM-4 64 KiB limit");
        }

        String safeTitle = safeTitle(title);
        int[] chunkRecordIds = new int[MAX_CHUNKS];
        int chunkCount = 0;
        int manifestRecordId = 0;
        try {
            byte[] downloading = encodeManifest(safeTitle, STATE_DOWNLOADING, 0, 0, 0, chunkRecordIds, 0);
            manifestRecordId = store.addRecord(downloading, 0, downloading.length);

            byte[] buffer = new byte[CHUNK_BYTES];
            byte[] header = new byte[8];
            int total = 0;
            int crc = 0xffffffff;
            int hash = 0x811c9dc5;
            while (true) {
                int count = readChunk(input, buffer);
                if (count == 0) {
                    break;
                }
                if (total + count > MAX_CARTRIDGE_BYTES || chunkCount >= MAX_CHUNKS) {
                    throw new IOException("cartridge exceeds the WASM-4 64 KiB limit");
                }
                int headerCount = 8 - total;
                if (headerCount > count) {
                    headerCount = count;
                }
                if (headerCount > 0) {
                    System.arraycopy(buffer, 0, header, total, headerCount);
                }
                crc = crc32Update(crc, buffer, 0, count);
                hash = fnv1aUpdate(hash, buffer, 0, count);
                byte[] chunk = encodeChunk(manifestRecordId, chunkCount, buffer, 0, count);
                chunkRecordIds[chunkCount] = store.addRecord(chunk, 0, chunk.length);
                chunkCount++;
                total += count;
            }
            if (expectedLength >= 0 && expectedLength != total) {
                throw new IOException("cartridge stream length mismatch");
            }
            requireCartridge(header, 0, total);
            byte[] received = encodeManifest(safeTitle, STATE_STAGING, total, ~crc, hash, chunkRecordIds, chunkCount);
            store.setRecord(manifestRecordId, received, 0, received.length);
            decode(manifestRecordId, store.getRecord(manifestRecordId), STATE_STAGING, false);
            return manifestRecordId;
        } catch (IOException failure) {
            discardRecords(manifestRecordId, chunkRecordIds, chunkCount);
            throw failure;
        } catch (RecordStoreException failure) { // NOPMD -- Java 1.3 lacks multi-catch.
            discardRecords(manifestRecordId, chunkRecordIds, chunkCount);
            throw failure;
        } catch (RuntimeException failure) { // NOPMD -- Java 1.3 lacks multi-catch.
            discardRecords(manifestRecordId, chunkRecordIds, chunkCount);
            throw failure;
        } finally {
            try {
                input.close();
            } catch (IOException ignored) {
                // The complete staged payload was already read back and verified.
            }
        }
    }

    synchronized byte[] readStaged(int recordId) throws IOException, RecordStoreException {
        requireOpen();
        return decode(recordId, store.getRecord(recordId), STATE_STAGING, true).cartridge;
    }

    synchronized CartridgeInfo commitStaged(int recordId) throws IOException, RecordStoreException {
        requireOpen();
        byte[] record = store.getRecord(recordId);
        CartridgeInfo staged = decode(recordId, record, STATE_STAGING, false);
        CartridgeInfo duplicate = findDuplicate(staged.length, staged.crc, staged.hash);
        if (duplicate != null) {
            discardStaged(recordId);
            return duplicate;
        }
        writeInt(record, STATE_OFFSET, STATE_COMMITTED);
        store.setRecord(recordId, record, 0, record.length);
        return decode(recordId, store.getRecord(recordId), STATE_COMMITTED, false);
    }

    synchronized void discardStaged(int recordId) {
        if (store == null || recordId <= 0) {
            return;
        }
        try {
            int[] chunks = manifestChunkRecordIds(store.getRecord(recordId));
            discardRecords(recordId, chunks, chunks.length);
        } catch (Throwable failure) { // NOPMD -- Java ME API linkage fallback.
            try {
                store.deleteRecord(recordId);
            } catch (Throwable ignored) { // NOPMD -- Java ME API linkage fallback.
                // An incomplete record remains invisible and can be reclaimed later.
            }
        }
    }

    synchronized byte[] read(int recordId) throws IOException, RecordStoreException {
        requireOpen();
        return decode(recordId, store.getRecord(recordId), STATE_COMMITTED, true).cartridge;
    }

    synchronized void close() {
        if (store != null) {
            try {
                store.closeRecordStore();
            } catch (RecordStoreException ignored) {
                // Closing is best effort during MIDlet lifecycle changes.
            }
            store = null;
        }
    }

    static String location(int recordId) {
        return "rms:" + Integer.toString(recordId);
    }

    static int recordIdFromLocation(String location) throws IOException {
        if (location == null || !startsWith(location, "rms:")) {
            throw new IOException("invalid installed cartridge location");
        }
        try {
            int recordId = Integer.parseInt(location.substring(4));
            if (recordId <= 0) {
                throw new NumberFormatException();
            }
            return recordId;
        } catch (NumberFormatException invalid) {
            throw new IOException( // NOPMD -- CLDC 1.1 has no cause chaining.
                    "invalid installed cartridge id");
        }
    }

    static boolean isLocation(String location) {
        return location != null && startsWith(location, "rms:");
    }

    private CartridgeInfo findDuplicate(int length, int crc, int hash) throws RecordStoreException {
        CartridgeInfo[] current = list();
        int index;
        for (index = 0; index < current.length; index++) {
            CartridgeInfo info = current[index];
            if (info.length == length && info.crc == crc && info.hash == hash) {
                return info;
            }
        }
        return null;
    }

    private void recoverIncompleteDownloads() throws RecordStoreException {
        int capacity = store.getNumRecords();
        if (capacity == 0) {
            return;
        }
        int[] deleteIds = new int[capacity];
        int deleteCount = 0;
        RecordEnumeration records = store.enumerateRecords(null, null, false);
        try {
            while (records.hasNextElement()) {
                int recordId = records.nextRecordId();
                byte[] record = store.getRecord(recordId);
                int parentId = chunkParentRecordId(record);
                if (parentId > 0) {
                    int parentState = 0;
                    try {
                        parentState = manifestState(store.getRecord(parentId));
                    } catch (RecordStoreException missing) {
                        parentState = 0;
                    }
                    if (parentState != STATE_STAGING && parentState != STATE_COMMITTED) {
                        deleteIds[deleteCount++] = recordId; // NOPMD -- Compact Java 1.3 cursor bytecode.
                    }
                } else if (manifestState(record) == STATE_DOWNLOADING) {
                    deleteIds[deleteCount] = recordId;
                    deleteCount++;
                }
            }
        } finally {
            records.destroy();
        }
        int index;
        for (index = 0; index < deleteCount; index++) {
            try {
                store.deleteRecord(deleteIds[index]);
            } catch (RecordStoreException ignored) {
                // Recovery is retried on the next open.
            }
        }
    }

    private int manifestState(byte[] record) {
        if (record.length < 12 || readInt(record, 0) != MAGIC || readInt(record, 4) != STREAM_VERSION) {
            return 0;
        }
        return readInt(record, STATE_OFFSET);
    }

    private int chunkParentRecordId(byte[] record) {
        if (record.length < 12 || readInt(record, 0) != CHUNK_MAGIC || readInt(record, 4) != STREAM_VERSION) {
            return 0;
        }
        return readInt(record, 8);
    }

    private byte[] encodeLegacy(String title, byte[] cartridge, int state) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(cartridge.length + 128);
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(MAGIC);
        output.writeInt(LEGACY_VERSION);
        output.writeInt(state);
        output.writeInt(cartridge.length);
        output.writeInt(crc32(cartridge));
        output.writeInt(fnv1a(cartridge));
        output.writeUTF(safeTitle(title));
        output.write(cartridge);
        output.flush();
        return bytes.toByteArray();
    }

    private byte[] encodeManifest(
            String title, int state, int length, int crc, int hash, int[] chunkRecordIds, int chunkCount)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(128 + chunkCount * 4);
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(MAGIC);
        output.writeInt(STREAM_VERSION);
        output.writeInt(state);
        output.writeInt(length);
        output.writeInt(crc);
        output.writeInt(hash);
        output.writeUTF(title);
        output.writeInt(chunkCount);
        int index;
        for (index = 0; index < chunkCount; index++) {
            output.writeInt(chunkRecordIds[index]);
        }
        output.flush();
        return bytes.toByteArray();
    }

    private byte[] encodeChunk(int manifestRecordId, int chunkIndex, byte[] data, int offset, int length)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(length + 24);
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(CHUNK_MAGIC);
        output.writeInt(STREAM_VERSION);
        output.writeInt(manifestRecordId);
        output.writeInt(chunkIndex);
        output.writeInt(length);
        output.writeInt(crc32(data, offset, length));
        output.write(data, offset, length);
        output.flush();
        return bytes.toByteArray();
    }

    private CartridgeInfo decode(int recordId, byte[] record, int requiredState, boolean includeCartridge)
            throws IOException {
        if (record.length < 12) {
            throw new IOException("cartridge record is truncated");
        }
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(record));
        if (input.readInt() != MAGIC) {
            throw new IOException("invalid cartridge record magic");
        }
        int version = input.readInt();
        if (version == LEGACY_VERSION) {
            return decodeLegacy(recordId, record, input, requiredState, includeCartridge);
        }
        if (version == STREAM_VERSION) {
            return decodeStream(recordId, input, requiredState, includeCartridge);
        }
        throw new IOException("unsupported cartridge record version");
    }

    private CartridgeInfo decodeLegacy(
            int recordId, byte[] record, DataInputStream input, int requiredState, boolean includeCartridge)
            throws IOException {
        int state = input.readInt();
        if (state != requiredState) {
            throw new IOException("cartridge record is not in the required state");
        }
        int length = input.readInt();
        int crc = input.readInt();
        final int hash = input.readInt();
        final String title = input.readUTF();
        if (length < 8 || length > MAX_CARTRIDGE_BYTES || length != input.available()) {
            throw new IOException("invalid cartridge record length");
        }
        int payloadOffset = record.length - length;
        requireCartridge(record, payloadOffset, length);
        if (crc32(record, payloadOffset, length) != crc) {
            throw new IOException("cartridge record CRC mismatch");
        }
        if (fnv1a(record, payloadOffset, length) != hash) {
            throw new IOException("cartridge record hash mismatch");
        }
        byte[] cartridge = null;
        if (includeCartridge) {
            cartridge = new byte[length];
            System.arraycopy(record, payloadOffset, cartridge, 0, length);
        }
        return new CartridgeInfo(recordId, title, length, crc, hash, cartridge, 1);
    }

    private CartridgeInfo decodeStream(int recordId, DataInputStream input, int requiredState, boolean includeCartridge)
            throws IOException {
        int state = input.readInt();
        if (state != requiredState) {
            throw new IOException("cartridge manifest is not in the required state");
        }
        int length = input.readInt();
        final int expectedCrc = input.readInt();
        final int expectedHash = input.readInt();
        final String title = input.readUTF();
        int chunkCount = input.readInt();
        int expectedChunks = (length + CHUNK_BYTES - 1) / CHUNK_BYTES;
        if (length < 8
                || length > MAX_CARTRIDGE_BYTES
                || chunkCount < 1
                || chunkCount > MAX_CHUNKS
                || chunkCount != expectedChunks) {
            throw new IOException("invalid cartridge manifest size");
        }
        int[] chunkRecordIds = new int[chunkCount];
        int index;
        for (index = 0; index < chunkCount; index++) {
            chunkRecordIds[index] = input.readInt();
            if (chunkRecordIds[index] <= 0) {
                throw new IOException("invalid cartridge chunk record id");
            }
        }
        if (input.available() != 0) {
            throw new IOException("trailing cartridge manifest bytes");
        }

        byte[] cartridge = includeCartridge ? new byte[length] : null;
        byte[] header = new byte[8];
        int total = 0;
        int crc = 0xffffffff;
        int hash = 0x811c9dc5;
        for (index = 0; index < chunkCount; index++) {
            byte[] chunk = decodeChunk(recordId, index, readRecord(chunkRecordIds[index]));
            if (index + 1 < chunkCount && chunk.length != CHUNK_BYTES) {
                throw new IOException("short non-final cartridge chunk");
            }
            if (total + chunk.length > length) {
                throw new IOException("cartridge chunks exceed manifest length");
            }
            int headerCount = 8 - total;
            if (headerCount > chunk.length) {
                headerCount = chunk.length;
            }
            if (headerCount > 0) {
                System.arraycopy(chunk, 0, header, total, headerCount);
            }
            if (cartridge != null) {
                System.arraycopy(chunk, 0, cartridge, total, chunk.length);
            }
            crc = crc32Update(crc, chunk, 0, chunk.length);
            hash = fnv1aUpdate(hash, chunk, 0, chunk.length);
            total += chunk.length;
        }
        if (total != length || ~crc != expectedCrc || hash != expectedHash) {
            throw new IOException("cartridge chunk checksum mismatch");
        }
        requireCartridge(header, 0, length);
        return new CartridgeInfo(recordId, title, length, expectedCrc, expectedHash, cartridge, chunkCount);
    }

    private byte[] decodeChunk(int manifestRecordId, int chunkIndex, byte[] record) throws IOException {
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(record));
        if (input.readInt() != CHUNK_MAGIC
                || input.readInt() != STREAM_VERSION
                || input.readInt() != manifestRecordId
                || input.readInt() != chunkIndex) {
            throw new IOException("cartridge chunk header mismatch");
        }
        int length = input.readInt();
        int crc = input.readInt();
        if (length < 1 || length > CHUNK_BYTES || input.available() != length) {
            throw new IOException("invalid cartridge chunk length");
        }
        byte[] data = new byte[length];
        input.readFully(data);
        if (crc32(data) != crc) {
            throw new IOException("cartridge chunk CRC mismatch");
        }
        return data;
    }

    private int[] manifestChunkRecordIds(byte[] record) throws IOException {
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(record));
        if (input.readInt() != MAGIC || input.readInt() != STREAM_VERSION) {
            return new int[0];
        }
        input.readInt();
        input.readInt();
        input.readInt();
        input.readInt();
        input.readUTF();
        int count = input.readInt();
        if (count < 0 || count > MAX_CHUNKS) {
            throw new IOException("invalid cartridge chunk count");
        }
        int[] result = new int[count];
        int index;
        for (index = 0; index < count; index++) {
            result[index] = input.readInt();
        }
        return result;
    }

    private byte[] readRecord(int recordId) throws IOException {
        try {
            return store.getRecord(recordId);
        } catch (RecordStoreException failure) {
            throw new IOException( // NOPMD -- CLDC 1.1 has no cause chaining.
                    "cannot read cartridge chunk " + recordId);
        }
    }

    private void discardRecords(int manifestRecordId, int[] chunkRecordIds, int chunkCount) {
        int index;
        for (index = 0; index < chunkCount; index++) {
            if (chunkRecordIds[index] > 0) {
                try {
                    store.deleteRecord(chunkRecordIds[index]);
                } catch (Throwable ignored) { // NOPMD -- Java ME API linkage fallback.
                    // Best effort rollback; any orphan chunk remains invisible.
                }
            }
        }
        if (manifestRecordId > 0) {
            try {
                store.deleteRecord(manifestRecordId);
            } catch (Throwable ignored) { // NOPMD -- Java ME API linkage fallback.
                // A non-committed manifest remains invisible.
            }
        }
    }

    private int readChunk(InputStream input, byte[] buffer) throws IOException {
        int total = 0;
        while (total < buffer.length) {
            int count = input.read(buffer, total, buffer.length - total);
            if (count < 0) {
                break;
            }
            if (count == 0) {
                int value = input.read();
                if (value < 0) {
                    break;
                }
                buffer[total++] = (byte) value; // NOPMD -- Compact Java 1.3 cursor bytecode.
            } else {
                total += count;
            }
        }
        return total;
    }

    private String safeTitle(String title) {
        String result = title == null ? "Installed cartridge" : title.trim();
        if (result.length() == 0) {
            result = "Installed cartridge";
        }
        if (result.length() > 80) {
            result = result.substring(0, 80);
        }
        return result;
    }

    private void requireOpen() throws RecordStoreException {
        if (store == null) {
            throw new RecordStoreException("cartridge store is closed");
        }
    }

    private static void requireCartridge(byte[] cartridge) throws IOException {
        if (cartridge == null) {
            throw new IOException("cartridge is missing");
        }
        requireCartridge(cartridge, 0, cartridge.length);
    }

    private static void requireCartridge(byte[] cartridge, int offset, int length) throws IOException {
        if (length < 8) {
            throw new IOException("cartridge is shorter than a WebAssembly header");
        }
        if (length > MAX_CARTRIDGE_BYTES) {
            throw new IOException("cartridge exceeds the WASM-4 64 KiB limit");
        }
        if ((cartridge[offset] & 0xff) != 0x00
                || (cartridge[offset + 1] & 0xff) != 0x61
                || (cartridge[offset + 2] & 0xff) != 0x73
                || (cartridge[offset + 3] & 0xff) != 0x6d
                || (cartridge[offset + 4] & 0xff) != 0x01
                || cartridge[offset + 5] != 0
                || cartridge[offset + 6] != 0
                || cartridge[offset + 7] != 0) {
            throw new IOException("invalid WebAssembly header");
        }
    }

    private static int crc32(byte[] data) {
        return crc32(data, 0, data.length);
    }

    private static int crc32(byte[] data, int offset, int length) {
        return ~crc32Update(0xffffffff, data, offset, length);
    }

    private static int crc32Update(int crc, byte[] data, int offset, int length) {
        int index;
        for (index = 0; index < length; index++) {
            crc ^= data[offset + index] & 0xff;
            int bit;
            for (bit = 0; bit < 8; bit++) {
                crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1));
            }
        }
        return crc;
    }

    private static int fnv1a(byte[] data) {
        return fnv1a(data, 0, data.length);
    }

    private static int fnv1a(byte[] data, int offset, int length) {
        return fnv1aUpdate(0x811c9dc5, data, offset, length);
    }

    private static int fnv1aUpdate(int hash, byte[] data, int offset, int length) {
        int index;
        for (index = 0; index < length; index++) {
            hash ^= data[offset + index] & 0xff;
            hash *= 0x01000193;
        }
        return hash;
    }

    private static void writeInt(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >>> 24);
        data[offset + 1] = (byte) (value >>> 16);
        data[offset + 2] = (byte) (value >>> 8);
        data[offset + 3] = (byte) value;
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xff) << 24)
                | ((data[offset + 1] & 0xff) << 16)
                | ((data[offset + 2] & 0xff) << 8)
                | (data[offset + 3] & 0xff);
    }

    private static void sortByRecordId(CartridgeInfo[] entries) {
        int index;
        for (index = 1; index < entries.length; index++) {
            CartridgeInfo value = entries[index];
            int insert = index;
            while (insert > 0 && entries[insert - 1].recordId > value.recordId) {
                entries[insert] = entries[insert - 1];
                insert--;
            }
            entries[insert] = value;
        }
    }

    private static boolean startsWith(String value, String prefix) {
        return value.length() >= prefix.length()
                && value.substring(0, prefix.length()).equals(prefix);
    }

    static final class CartridgeInfo {
        final int recordId;
        final String title;
        final int length;
        final int crc;
        final int hash;
        final byte[] cartridge;
        final int chunks;

        CartridgeInfo(int recordId, String title, int length, int crc, int hash, byte[] cartridge, int chunks) {
            this.recordId = recordId;
            this.title = title;
            this.length = length;
            this.crc = crc;
            this.hash = hash;
            this.cartridge = cartridge;
            this.chunks = chunks;
        }
    }
}
