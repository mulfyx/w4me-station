package w4me.runtime.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import javax.microedition.rms.RecordEnumeration;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

import w4me.wasm.W4IrFunction;
import w4me.wasm.W4IrStore;
import w4me.wasm.WasmException;
import w4me.wasm.WasmModule;
import w4me.wasm.WasmTrap;

/** RMS-backed W4IR function metadata and fixed-size code pages. */
public final class RmsW4IrStore implements W4IrStore {
    private static final int MANIFEST_MAGIC = 0x57344952;
    private static final int FUNCTION_MAGIC = 0x5734464e;
    private static final int PAGE_MAGIC = 0x57345047;
    private static final int STATE_STAGING = 0x53544731;
    private static final int STATE_COMMITTED = 0x434d5431;

    private final String storeName;
    private final int cartLength;
    private final int cartCrc;
    private final int cartHash;
    private final int[][] cachedPages;
    private final int[] cachedRecordIds;
    private RecordStore store;
    private boolean ready;
    private int functionCount;
    private int manifestRecordId;
    private int[] functionRecordIds;
    private int replacementSlot;
    private int faults;
    private int hits;

    private RmsW4IrStore(byte[] cartridge, int cacheSlots) {
        cartLength = cartridge.length;
        cartCrc = crc32(cartridge);
        cartHash = fnv1a(cartridge);
        storeName = "w4i11" + hex8(cartHash);
        cachedPages = new int[cacheSlots][];
        cachedRecordIds = new int[cacheSlots];
    }

    public static RmsW4IrStore open(byte[] cartridge, int cacheSlots)
            throws RecordStoreException {
        if (cacheSlots < 1 || cacheSlots > 32) {
            throw new IllegalArgumentException("W4IR cache slots must be from 1 to 32");
        }
        RmsW4IrStore result = new RmsW4IrStore(cartridge, cacheSlots);
        result.store = RecordStore.openRecordStore(result.storeName, true);
        result.inspectManifest();
        if (!result.ready && result.store.getNumRecords() != 0) {
            result.resetStore();
        }
        return result;
    }

    public boolean isComplete(int expectedFunctionCount) {
        return ready && functionCount == expectedFunctionCount;
    }

    public W4IrFunction loadFunction(int functionIndex) throws WasmException {
        if (!ready || functionIndex < 0 || functionIndex >= functionCount) {
            throw new WasmException("W4IR function is outside the committed cache");
        }
        try {
            byte[] record = store.getRecord(functionRecordIds[functionIndex]);
            int payloadLength = verifyFunctionChecksum(record);
            DataInputStream input =
                    new DataInputStream(
                            new ByteArrayInputStream(record, 0, payloadLength));
            if (input.readInt() != FUNCTION_MAGIC || input.readInt() != functionIndex) {
                throw new IOException("W4IR function header mismatch");
            }
            int localCount =
                    readBoundedCount(
                            input,
                            W4IrFunction.MAX_DECLARED_LOCALS,
                            "declared local count");
            int instructionCount =
                    readBoundedCount(
                            input,
                            W4IrFunction.MAX_INSTRUCTIONS,
                            "instruction count");
            if (instructionCount == 0) {
                throw new IOException("invalid instruction count");
            }
            long fingerprint = input.readLong();
            int intrinsic =
                    readBoundedCount(
                            input, W4IrFunction.MAX_INTRINSIC, "numeric intrinsic");
            int tableCount =
                    readBoundedCount(
                            input, instructionCount, "branch table count");
            int[][] branchTables = new int[tableCount][];
            int table;
            for (table = 0; table < tableCount; table++) {
                int length =
                        readBoundedCount(
                                input,
                                W4IrFunction.MAX_BRANCH_TARGETS,
                                "branch table length");
                branchTables[table] = new int[length];
                int index;
                for (index = 0; index < length; index++) {
                    branchTables[table][index] = input.readInt();
                }
            }
            int descriptorIntCount =
                    readBoundedCount(
                            input,
                            W4IrFunction.MAX_BRANCH_DESCRIPTORS
                                    * W4IrFunction.BRANCH_DESCRIPTOR_STRIDE,
                            "branch descriptor data length");
            if (descriptorIntCount % W4IrFunction.BRANCH_DESCRIPTOR_STRIDE != 0) {
                throw new IOException("invalid branch descriptor data length");
            }
            int[] branchDescriptors = new int[descriptorIntCount];
            int descriptorInt;
            for (descriptorInt = 0;
                    descriptorInt < descriptorIntCount;
                    descriptorInt++) {
                branchDescriptors[descriptorInt] = input.readInt();
            }
            int descriptorCount =
                    descriptorIntCount / W4IrFunction.BRANCH_DESCRIPTOR_STRIDE;
            int directCount =
                    readBoundedCount(
                            input, instructionCount, "direct branch descriptor count");
            int[] branchDescriptorPcs = new int[directCount];
            int[] branchDescriptorIndices = new int[directCount];
            int direct;
            for (direct = 0; direct < directCount; direct++) {
                branchDescriptorPcs[direct] = input.readInt();
                branchDescriptorIndices[direct] = input.readInt();
                if (branchDescriptorPcs[direct] < 0
                        || branchDescriptorPcs[direct] >= instructionCount
                        || (direct > 0
                                && branchDescriptorPcs[direct]
                                        <= branchDescriptorPcs[direct - 1])
                        || branchDescriptorIndices[direct] < 0
                        || branchDescriptorIndices[direct] >= descriptorCount) {
                    throw new IOException("invalid direct branch descriptor mapping");
                }
            }
            int descriptorTableCount =
                    readBoundedCount(
                            input, tableCount, "branch descriptor table count");
            if (descriptorTableCount != tableCount) {
                throw new IOException("branch descriptor table count mismatch");
            }
            int[][] branchDescriptorTables =
                    new int[descriptorTableCount][];
            for (table = 0; table < descriptorTableCount; table++) {
                int length =
                        readBoundedCount(
                                input,
                                branchTables[table].length,
                                "branch descriptor table length");
                if (length != branchTables[table].length) {
                    throw new IOException("branch descriptor table length mismatch");
                }
                branchDescriptorTables[table] = new int[length];
                int index;
                for (index = 0; index < length; index++) {
                    int descriptorIndex = input.readInt();
                    if (descriptorIndex < 0 || descriptorIndex >= descriptorCount) {
                        throw new IOException(
                                "branch descriptor table index is out of range");
                    }
                    branchDescriptorTables[table][index] = descriptorIndex;
                }
            }
            validateBranchMetadata(
                    instructionCount,
                    branchTables,
                    branchDescriptors,
                    branchDescriptorPcs,
                    branchDescriptorIndices,
                    branchDescriptorTables);
            int instructionsPerPage =
                    W4IrFunction.PAGE_INTS / W4IrFunction.INSTRUCTION_STRIDE;
            int maximumPages =
                    (W4IrFunction.MAX_INSTRUCTIONS + instructionsPerPage - 1)
                            / instructionsPerPage;
            int pageCount =
                    readBoundedCount(input, maximumPages, "W4IR page count");
            int expectedPages =
                    (instructionCount + instructionsPerPage - 1)
                            / instructionsPerPage;
            if (pageCount != expectedPages) {
                throw new IOException("W4IR function page count mismatch");
            }
            int[] pageRecordIds = new int[pageCount];
            int page;
            for (page = 0; page < pageCount; page++) {
                pageRecordIds[page] = input.readInt();
                if (pageRecordIds[page] <= 0) {
                    throw new IOException("invalid W4IR page record id");
                }
            }
            if (input.available() != 0) {
                throw new IOException("trailing W4IR function metadata");
            }
            return new W4IrFunction(
                    functionIndex,
                    localCount,
                    instructionCount,
                    branchTables,
                    branchDescriptors,
                    branchDescriptorPcs,
                    branchDescriptorIndices,
                    branchDescriptorTables,
                    fingerprint,
                    intrinsic,
                    pageRecordIds);
        } catch (Throwable failure) {
            throw wasmFailure("cannot load W4IR function " + functionIndex, failure);
        }
    }

    public void begin(int count) throws WasmException {
        if (ready || count < 0) {
            throw new WasmException("invalid W4IR cache build state");
        }
        functionCount = count;
        functionRecordIds = new int[count];
        try {
            byte[] manifest = encodeManifest(STATE_STAGING);
            manifestRecordId = store.addRecord(manifest, 0, manifest.length);
        } catch (Throwable failure) {
            throw wasmFailure("cannot begin W4IR cache build", failure);
        }
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
            int intrinsic) throws WasmException {
        if (ready
                || functionRecordIds == null
                || functionIndex < 0
                || functionIndex >= functionRecordIds.length
                || functionRecordIds[functionIndex] != 0) {
            throw new WasmException("invalid W4IR function write state");
        }
        if (declaredLocalCount < 0
                || declaredLocalCount > W4IrFunction.MAX_DECLARED_LOCALS
                || code == null
                || code.length == 0
                || code.length % W4IrFunction.INSTRUCTION_STRIDE != 0
                || code.length / W4IrFunction.INSTRUCTION_STRIDE
                        > W4IrFunction.MAX_INSTRUCTIONS
                || intrinsic < 0
                || intrinsic > W4IrFunction.MAX_INTRINSIC) {
            throw new WasmException("invalid W4IR function metadata");
        }
        validateBranchMetadata(
                code.length / 3,
                branchTables,
                branchDescriptors,
                branchDescriptorPcs,
                branchDescriptorIndices,
                branchDescriptorTables);
        try {
            int pageCount =
                    (code.length + W4IrFunction.PAGE_INTS - 1) / W4IrFunction.PAGE_INTS;
            int[] pageRecordIds = new int[pageCount];
            int page;
            for (page = 0; page < pageCount; page++) {
                int offset = page * W4IrFunction.PAGE_INTS;
                int length = code.length - offset;
                if (length > W4IrFunction.PAGE_INTS) {
                    length = W4IrFunction.PAGE_INTS;
                }
                byte[] record = encodePage(functionIndex, page, code, offset, length);
                pageRecordIds[page] = store.addRecord(record, 0, record.length);
            }
            byte[] metadata =
                    encodeFunction(
                            functionIndex,
                            declaredLocalCount,
                            code.length / 3,
                            branchTables,
                            branchDescriptors,
                            branchDescriptorPcs,
                            branchDescriptorIndices,
                            branchDescriptorTables,
                            fingerprint,
                            intrinsic,
                            pageRecordIds);
            functionRecordIds[functionIndex] =
                    store.addRecord(metadata, 0, metadata.length);
        } catch (Throwable failure) {
            throw wasmFailure("cannot persist W4IR function " + functionIndex, failure);
        }
    }

    public void commit() throws WasmException {
        if (ready || manifestRecordId <= 0 || functionRecordIds == null) {
            throw new WasmException("invalid W4IR cache commit state");
        }
        int index;
        for (index = 0; index < functionRecordIds.length; index++) {
            if (functionRecordIds[index] <= 0) {
                throw new WasmException("W4IR function " + index + " was not persisted");
            }
        }
        try {
            byte[] manifest = encodeManifest(STATE_COMMITTED);
            store.setRecord(manifestRecordId, manifest, 0, manifest.length);
            ready = true;
        } catch (Throwable failure) {
            throw wasmFailure("cannot commit W4IR cache", failure);
        }
    }

    public int[] loadPage(W4IrFunction function, int pageIndex) {
        if (!ready || pageIndex < 0 || pageIndex >= function.pageCount()) {
            throw new WasmTrap("W4IR code page is outside the committed cache");
        }
        int recordId = function.pageRecordId(pageIndex);
        int slot;
        for (slot = 0; slot < cachedRecordIds.length; slot++) {
            if (cachedRecordIds[slot] == recordId && cachedPages[slot] != null) {
                hits++;
                return cachedPages[slot];
            }
        }
        try {
            int[] page = decodePage(store.getRecord(recordId), function.functionIndex(), pageIndex);
            slot = replacementSlot;
            replacementSlot = (replacementSlot + 1) % cachedPages.length;
            cachedRecordIds[slot] = recordId;
            cachedPages[slot] = page;
            faults++;
            return page;
        } catch (Throwable failure) {
            discard();
            throw new WasmTrap(
                    "cannot load W4IR page "
                            + function.functionIndex()
                            + ":"
                            + pageIndex
                            + ": "
                            + failure.toString());
        }
    }

    public int pageFaults() {
        return faults;
    }

    public int pageHits() {
        return hits;
    }

    public void discard() {
        close();
        try {
            RecordStore.deleteRecordStore(storeName);
        } catch (RecordStoreException ignored) {
            // Cache deletion is best effort; cartridge and save records are separate stores.
        }
        ready = false;
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

    private void inspectManifest() throws RecordStoreException {
        RecordEnumeration records = store.enumerateRecords(null, null, false);
        try {
            while (records.hasNextElement()) {
                int recordId = records.nextRecordId();
                try {
                    if (decodeManifest(recordId, store.getRecord(recordId))) {
                        return;
                    }
                } catch (IOException ignored) {
                    // Incomplete or damaged cache records are rebuilt below.
                }
            }
        } finally {
            records.destroy();
        }
    }

    private boolean decodeManifest(int recordId, byte[] record) throws IOException {
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(record));
        if (input.readInt() != MANIFEST_MAGIC
                || input.readInt() != WasmModule.W4IR_FORMAT_VERSION
                || input.readInt() != STATE_COMMITTED
                || input.readInt() != cartLength
                || input.readInt() != cartCrc
                || input.readInt() != cartHash) {
            return false;
        }
        int count =
                readBoundedCount(
                        input, W4IrFunction.MAX_FUNCTIONS, "function count");
        int[] recordIds = new int[count];
        int index;
        for (index = 0; index < count; index++) {
            recordIds[index] = input.readInt();
            if (recordIds[index] <= 0) {
                throw new IOException("invalid W4IR function record id");
            }
        }
        if (input.available() != 0) {
            throw new IOException("trailing W4IR manifest bytes");
        }
        manifestRecordId = recordId;
        functionCount = count;
        functionRecordIds = recordIds;
        ready = true;
        return true;
    }

    private void resetStore() throws RecordStoreException {
        store.closeRecordStore();
        try {
            RecordStore.deleteRecordStore(storeName);
        } catch (RecordStoreException ignored) {
            // Reopening below will still expose the original error if deletion truly failed.
        }
        store = RecordStore.openRecordStore(storeName, true);
        ready = false;
        functionCount = 0;
        manifestRecordId = 0;
        functionRecordIds = null;
    }

    private byte[] encodeManifest(int state) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(MANIFEST_MAGIC);
        output.writeInt(WasmModule.W4IR_FORMAT_VERSION);
        output.writeInt(state);
        output.writeInt(cartLength);
        output.writeInt(cartCrc);
        output.writeInt(cartHash);
        output.writeInt(functionCount);
        int index;
        for (index = 0; index < functionCount; index++) {
            output.writeInt(functionRecordIds[index]);
        }
        output.flush();
        return bytes.toByteArray();
    }

    private byte[] encodeFunction(
            int functionIndex,
            int declaredLocalCount,
            int instructionCount,
            int[][] branchTables,
            int[] branchDescriptors,
            int[] branchDescriptorPcs,
            int[] branchDescriptorIndices,
            int[][] branchDescriptorTables,
            long fingerprint,
            int intrinsic,
            int[] pageRecordIds) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(FUNCTION_MAGIC);
        output.writeInt(functionIndex);
        output.writeInt(declaredLocalCount);
        output.writeInt(instructionCount);
        output.writeLong(fingerprint);
        output.writeInt(intrinsic);
        output.writeInt(branchTables.length);
        int table;
        for (table = 0; table < branchTables.length; table++) {
            output.writeInt(branchTables[table].length);
            int index;
            for (index = 0; index < branchTables[table].length; index++) {
                output.writeInt(branchTables[table][index]);
            }
        }
        output.writeInt(branchDescriptors.length);
        int descriptorInt;
        for (descriptorInt = 0;
                descriptorInt < branchDescriptors.length;
                descriptorInt++) {
            output.writeInt(branchDescriptors[descriptorInt]);
        }
        output.writeInt(branchDescriptorPcs.length);
        int direct;
        for (direct = 0; direct < branchDescriptorPcs.length; direct++) {
            output.writeInt(branchDescriptorPcs[direct]);
            output.writeInt(branchDescriptorIndices[direct]);
        }
        output.writeInt(branchDescriptorTables.length);
        for (table = 0; table < branchDescriptorTables.length; table++) {
            output.writeInt(branchDescriptorTables[table].length);
            int index;
            for (index = 0;
                    index < branchDescriptorTables[table].length;
                    index++) {
                output.writeInt(branchDescriptorTables[table][index]);
            }
        }
        output.writeInt(pageRecordIds.length);
        int page;
        for (page = 0; page < pageRecordIds.length; page++) {
            output.writeInt(pageRecordIds[page]);
        }
        output.flush();
        byte[] payload = bytes.toByteArray();
        byte[] record = new byte[payload.length + 4];
        System.arraycopy(payload, 0, record, 0, payload.length);
        writeInt(record, payload.length, checksum(payload, 0, payload.length));
        return record;
    }

    private byte[] encodePage(
            int functionIndex, int pageIndex, int[] code, int offset, int length)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(length * 4 + 20);
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(PAGE_MAGIC);
        output.writeInt(functionIndex);
        output.writeInt(pageIndex);
        output.writeInt(length);
        output.writeInt(checksum(code, offset, length));
        int index;
        for (index = 0; index < length; index++) {
            output.writeInt(code[offset + index]);
        }
        output.flush();
        return bytes.toByteArray();
    }

    private static void validateBranchMetadata(
            int instructionCount,
            int[][] branchTables,
            int[] branchDescriptors,
            int[] branchDescriptorPcs,
            int[] branchDescriptorIndices,
            int[][] branchDescriptorTables)
            throws WasmException {
        if (branchDescriptors == null
                || branchDescriptorPcs == null
                || branchDescriptorIndices == null
                || branchDescriptorTables == null
                || branchDescriptorPcs.length != branchDescriptorIndices.length
                || branchDescriptorTables.length != branchTables.length
                || branchDescriptors.length % W4IrFunction.BRANCH_DESCRIPTOR_STRIDE != 0
                || branchDescriptors.length
                        > 65536 * W4IrFunction.BRANCH_DESCRIPTOR_STRIDE) {
            throw new WasmException("invalid W4IR branch descriptor metadata");
        }
        int descriptorCount =
                branchDescriptors.length / W4IrFunction.BRANCH_DESCRIPTOR_STRIDE;
        int descriptor;
        for (descriptor = 0; descriptor < descriptorCount; descriptor++) {
            int offset = descriptor * W4IrFunction.BRANCH_DESCRIPTOR_STRIDE;
            int targetPc = branchDescriptors[offset];
            int valueHeight = branchDescriptors[offset + 1];
            int arity = branchDescriptors[offset + 2];
            int controlDepth = branchDescriptors[offset + 3];
            int flags = branchDescriptors[offset + 4];
            boolean functionReturn = (flags & 2) != 0;
            if (targetPc < -1
                    || targetPc >= instructionCount
                    || valueHeight < 0
                    || valueHeight > 4096
                    || arity < 0
                    || arity > 16
                    || valueHeight > 4096 - arity
                    || controlDepth < 0
                    || controlDepth > 512
                    || flags < 0
                    || flags > 2
                    || (functionReturn
                            && (targetPc != -1
                                    || valueHeight != 0
                                    || controlDepth != 0))
                    || (!functionReturn && targetPc < 0)) {
                throw new WasmException("invalid W4IR branch descriptor");
            }
        }
        int direct;
        for (direct = 0; direct < branchDescriptorPcs.length; direct++) {
            if (branchDescriptorPcs[direct] < 0
                    || branchDescriptorPcs[direct] >= instructionCount
                    || (direct > 0
                            && branchDescriptorPcs[direct]
                                    <= branchDescriptorPcs[direct - 1])
                    || branchDescriptorIndices[direct] < 0
                    || branchDescriptorIndices[direct] >= descriptorCount) {
                throw new WasmException(
                        "invalid W4IR direct branch descriptor mapping");
            }
        }
        int table;
        for (table = 0; table < branchTables.length; table++) {
            if (branchDescriptorTables[table] == null
                    || branchDescriptorTables[table].length
                            != branchTables[table].length) {
                throw new WasmException(
                        "invalid W4IR branch descriptor table length");
            }
            int index;
            for (index = 0;
                    index < branchDescriptorTables[table].length;
                    index++) {
                int descriptorIndex = branchDescriptorTables[table][index];
                if (descriptorIndex < 0 || descriptorIndex >= descriptorCount) {
                    throw new WasmException(
                            "W4IR branch descriptor table index is out of range");
                }
            }
        }
    }

    private int[] decodePage(byte[] record, int functionIndex, int pageIndex)
            throws IOException {
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(record));
        if (input.readInt() != PAGE_MAGIC
                || input.readInt() != functionIndex
                || input.readInt() != pageIndex) {
            throw new IOException("W4IR page header mismatch");
        }
        int length = readBoundedCount(input, W4IrFunction.PAGE_INTS, "W4IR page length");
        int expectedChecksum = input.readInt();
        if (input.available() != length * 4) {
            throw new IOException("W4IR page length mismatch");
        }
        int[] result = new int[length];
        int index;
        for (index = 0; index < length; index++) {
            result[index] = input.readInt();
        }
        if (checksum(result, 0, result.length) != expectedChecksum) {
            throw new IOException("W4IR page checksum mismatch");
        }
        return result;
    }

    private int readBoundedCount(DataInputStream input, int maximum, String label)
            throws IOException {
        int value = input.readInt();
        if (value < 0 || value > maximum) {
            throw new IOException("invalid " + label);
        }
        return value;
    }

    private int verifyFunctionChecksum(byte[] record) throws IOException {
        if (record == null || record.length < 4) {
            throw new IOException("truncated W4IR function metadata");
        }
        int payloadLength = record.length - 4;
        int expected = readInt(record, payloadLength);
        if (checksum(record, 0, payloadLength) != expected) {
            throw new IOException("W4IR function metadata checksum mismatch");
        }
        return payloadLength;
    }

    private static int checksum(byte[] values, int offset, int length) {
        int hash = 0x811c9dc5;
        int index;
        for (index = 0; index < length; index++) {
            hash ^= values[offset + index] & 0xff;
            hash *= 0x01000193;
        }
        return hash;
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
                | ((bytes[offset + 1] & 0xff) << 16)
                | ((bytes[offset + 2] & 0xff) << 8)
                | (bytes[offset + 3] & 0xff);
    }

    private static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }

    private static int checksum(int[] values, int offset, int length) {
        int hash = 0x811c9dc5;
        int index;
        for (index = 0; index < length; index++) {
            int value = values[offset + index];
            int shift;
            for (shift = 0; shift < 32; shift += 8) {
                hash ^= (value >>> shift) & 0xff;
                hash *= 0x01000193;
            }
        }
        return hash;
    }

    private static int crc32(byte[] data) {
        int crc = 0xffffffff;
        int index;
        for (index = 0; index < data.length; index++) {
            crc ^= data[index] & 0xff;
            int bit;
            for (bit = 0; bit < 8; bit++) {
                crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1));
            }
        }
        return ~crc;
    }

    private static int fnv1a(byte[] data) {
        int hash = 0x811c9dc5;
        int index;
        for (index = 0; index < data.length; index++) {
            hash ^= data[index] & 0xff;
            hash *= 0x01000193;
        }
        return hash;
    }

    private static String hex8(int value) {
        String hex = Integer.toHexString(value);
        StringBuffer result = new StringBuffer(8);
        int padding;
        for (padding = hex.length(); padding < 8; padding++) {
            result.append('0');
        }
        result.append(hex);
        return result.toString();
    }

    private static WasmException wasmFailure(String message, Throwable failure) {
        return new WasmException(message + ": " + failure.toString());
    }
}
