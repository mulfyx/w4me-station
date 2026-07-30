package w4me.wasm;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Properties;
import javax.microedition.rms.RecordEnumeration;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;
import w4me.runtime.storage.RmsW4IrStore;

/** Rejects malformed persisted branch-descriptor metadata at the RMS boundary. */
public final class W4IrMalformedDescriptorCacheSmoke {
    private static final int FUNCTION_MAGIC = 0x5734464e;
    private static final int FUNCTION_LOCAL_COUNT_OFFSET = 8;
    private static final int FUNCTION_INSTRUCTION_COUNT_OFFSET = 12;
    private static final int FUNCTION_INTRINSIC_OFFSET = 24;
    private static final int FUNCTION_DESCRIPTOR_OFFSET = 36;
    private static final String RMS_ROOT = "/tmp/w4me-malformed-descriptor-rms/";

    private W4IrMalformedDescriptorCacheSmoke() {}

    /** Runs this verification entry point. */
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: w4ir-cache-metadata-recovery.wasm");
        }
        installHeadlessKemulatorFrontend();
        byte[] cartridge = {
            (byte) 0x57, (byte) 0x34, (byte) 0x49, (byte) 0x52,
            (byte) 0x2d, (byte) 0x6d, (byte) 0x61, (byte) 0x6c,
            (byte) 0x66, (byte) 0x6f, (byte) 0x72, (byte) 0x6d,
            (byte) 0x65, (byte) 0x64, (byte) 0x2d, (byte) 0x76,
            (byte) 0x31
        };
        String storeName = "w4i11" + hex8(fnv1a(cartridge));
        deleteStore(storeName);

        RmsW4IrStore store = null;
        try {
            store = RmsW4IrStore.open(cartridge, 1);
            store.begin(1);

            expectWriteRejected(
                    store,
                    "direct descriptor index",
                    "invalid W4IR direct branch descriptor mapping",
                    new int[0][],
                    descriptor(0, 0, 0, 0),
                    new int[] {0},
                    new int[] {1},
                    new int[0][]);
            expectWriteRejected(
                    store,
                    "table descriptor index",
                    "W4IR branch descriptor table index is out of range",
                    new int[][] {new int[] {0}},
                    descriptor(0, 0, 0, 0),
                    new int[0],
                    new int[0],
                    new int[][] {new int[] {1}});
            expectWriteRejected(
                    store,
                    "target PC",
                    "invalid W4IR branch descriptor",
                    new int[0][],
                    descriptor(1, 0, 0, 0),
                    new int[0],
                    new int[0],
                    new int[0][]);
            expectWriteRejected(
                    store,
                    "value height",
                    "invalid W4IR branch descriptor",
                    new int[0][],
                    descriptor(0, -1, 0, 0),
                    new int[0],
                    new int[0],
                    new int[0][]);
            expectWriteRejected(
                    store,
                    "arity",
                    "invalid W4IR branch descriptor",
                    new int[0][],
                    descriptor(0, 0, 17, 0),
                    new int[0],
                    new int[0],
                    new int[0][]);
            expectWriteRejected(
                    store,
                    "control depth",
                    "invalid W4IR branch descriptor",
                    new int[0][],
                    descriptor(0, 0, 0, 513),
                    new int[0],
                    new int[0],
                    new int[0][]);

            store.writeFunction(
                    0,
                    0,
                    oneInstruction(),
                    new int[0][],
                    descriptor(0, 0, 0, 0),
                    new int[0],
                    new int[0],
                    new int[0][],
                    0L,
                    0);
            store.commit();
            byte[] validFunctionRecord = readFunctionRecord(storeName);
            corruptFunctionChecksum(storeName);
            expectReadRejected(store);
            restoreFunctionRecord(storeName, validFunctionRecord);
            mutateFunctionInt(storeName, FUNCTION_LOCAL_COUNT_OFFSET, W4IrFunction.MAX_DECLARED_LOCALS + 1);
            expectReadRejected(store);
            restoreFunctionRecord(storeName, validFunctionRecord);
            mutateFunctionInt(storeName, FUNCTION_INSTRUCTION_COUNT_OFFSET, 1431655766);
            expectReadRejected(store);
            restoreFunctionRecord(storeName, validFunctionRecord);
            mutateFunctionInt(storeName, FUNCTION_INTRINSIC_OFFSET, W4IrFunction.MAX_INTRINSIC + 1);
            expectReadRejected(store);
            restoreFunctionRecord(storeName, validFunctionRecord);
            truncateDescriptorRecord(storeName);
            expectReadRejected(store);

            System.out.println("PASS w4ir-malformed-descriptor-cache"
                    + " truncated=PASS"
                    + " direct-index=PASS"
                    + " table-index=PASS"
                    + " target-pc=PASS"
                    + " height=PASS"
                    + " arity=PASS"
                    + " control-depth=PASS"
                    + " checksum=PASS"
                    + " locals=PASS"
                    + " instructions=PASS"
                    + " intrinsic=PASS");
        } finally {
            if (store != null) {
                store.discard();
            } else {
                deleteStore(storeName);
            }
        }
        verifyAutomaticRecovery(readFile(args[0]));
    }

    private static void verifyAutomaticRecovery(byte[] cartridge) throws Exception {
        String storeName = "w4i11" + hex8(fnv1a(cartridge));
        deleteStore(storeName);
        WasmModule module = null;
        try {
            RmsW4IrStore build = RmsW4IrStore.open(cartridge, 1);
            module = WasmModule.read(cartridge, build, true);
            requireStatus("initial build", "RMS-build", module.w4irStatus());
            module.close();
            module = null;

            mutateFunctionInt(storeName, FUNCTION_INTRINSIC_OFFSET, WasmModule.INTRINSIC_F32_FLOOR);

            RmsW4IrStore damaged = RmsW4IrStore.open(cartridge, 1);
            module = WasmModule.read(cartridge, damaged, true);
            requireStatus("damaged recovery", "RAM", module.w4irStatus());
            module.close();
            module = null;

            RmsW4IrStore rebuild = RmsW4IrStore.open(cartridge, 1);
            module = WasmModule.read(cartridge, rebuild, true);
            requireStatus("rebuild", "RMS-build", module.w4irStatus());
            module.close();
            module = null;

            RmsW4IrStore hit = RmsW4IrStore.open(cartridge, 1);
            module = WasmModule.read(cartridge, hit, true);
            requireStatus("rebuilt hit", "RMS-hit", module.w4irStatus());
            module.close();
            module = null;

            System.out.println("PASS w4ir-metadata-cache-recovery"
                    + " invalid-intrinsic-signature=PASS"
                    + " discard=PASS rebuild=PASS hit=PASS");
        } finally {
            if (module != null) {
                module.close();
            }
            deleteStore(storeName);
        }
    }

    private static void requireStatus(String label, String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void expectWriteRejected(
            RmsW4IrStore store,
            String label,
            String expectedMessage,
            int[][] branchTables,
            int[] branchDescriptors,
            int[] branchDescriptorPcs,
            int[] branchDescriptorIndices,
            int[][] branchDescriptorTables)
            throws Exception {
        try {
            store.writeFunction(
                    0,
                    0,
                    oneInstruction(),
                    branchTables,
                    branchDescriptors,
                    branchDescriptorPcs,
                    branchDescriptorIndices,
                    branchDescriptorTables,
                    0L,
                    0);
        } catch (WasmException expected) {
            if (expectedMessage.equals(expected.getMessage())) {
                return;
            }
            throw new AssertionError( // NOPMD -- CLDC 1.1 does not provide portable exception-cause chaining.
                    "malformed " + label + " reached the wrong rejection path: " + expected.getMessage());
        }
        throw new AssertionError("malformed " + label + " was accepted for writing");
    }

    private static void expectReadRejected(RmsW4IrStore store) throws Exception {
        try {
            store.loadFunction(0);
        } catch (WasmException expected) {
            if (expected.getMessage().indexOf("cannot load W4IR function 0") >= 0) {
                return;
            }
            throw new AssertionError( // NOPMD -- CLDC 1.1 has no cause chaining.
                    "truncated record reached the wrong rejection path: " + expected.getMessage());
        }
        throw new AssertionError("truncated branch descriptor record was accepted");
    }

    private static void truncateDescriptorRecord(String storeName) throws Exception {
        mutateFunctionRecord(storeName, -1, 0, true);
    }

    private static void corruptFunctionChecksum(String storeName) throws Exception {
        mutateFunctionRecord(storeName, FUNCTION_LOCAL_COUNT_OFFSET, 1, false);
    }

    private static void mutateFunctionInt(String storeName, int offset, int value) throws Exception {
        mutateFunctionRecord(storeName, offset, value, true);
    }

    private static void mutateFunctionRecord(String storeName, int offset, int value, boolean refreshChecksum)
            throws Exception {
        RecordStore rawStore = RecordStore.openRecordStore(storeName, false);
        RecordEnumeration records = rawStore.enumerateRecords(null, null, false);
        boolean mutated = false;
        try {
            while (records.hasNextElement()) {
                int recordId = records.nextRecordId();
                byte[] record = rawStore.getRecord(recordId);
                if (record != null
                        && record.length >= FUNCTION_DESCRIPTOR_OFFSET + 4
                        && readInt(record, 0) == FUNCTION_MAGIC
                        && readInt(record, 4) == 0) {
                    if (offset < 0) {
                        int payloadLength =
                                FUNCTION_DESCRIPTOR_OFFSET + (W4IrFunction.BRANCH_DESCRIPTOR_STRIDE - 1) * 4;
                        byte[] truncated = new byte[payloadLength + 4];
                        System.arraycopy(record, 0, truncated, 0, payloadLength);
                        writeInt(truncated, payloadLength, checksum(truncated, 0, payloadLength));
                        rawStore.setRecord(recordId, truncated, 0, truncated.length);
                    } else {
                        writeInt(record, offset, value);
                        if (refreshChecksum) {
                            int checksumOffset = record.length - 4;
                            writeInt(record, checksumOffset, checksum(record, 0, checksumOffset));
                        }
                        rawStore.setRecord(recordId, record, 0, record.length);
                    }
                    mutated = true;
                    break;
                }
            }
        } finally {
            records.destroy();
            rawStore.closeRecordStore();
        }
        if (!mutated) {
            throw new AssertionError("persisted W4IR function record was not found");
        }
    }

    private static byte[] readFunctionRecord(String storeName) throws Exception {
        RecordStore rawStore = RecordStore.openRecordStore(storeName, false);
        RecordEnumeration records = rawStore.enumerateRecords(null, null, false);
        try {
            while (records.hasNextElement()) {
                byte[] record = rawStore.getRecord(records.nextRecordId());
                if (record != null
                        && record.length >= FUNCTION_DESCRIPTOR_OFFSET + 4
                        && readInt(record, 0) == FUNCTION_MAGIC
                        && readInt(record, 4) == 0) {
                    return record;
                }
            }
        } finally {
            records.destroy();
            rawStore.closeRecordStore();
        }
        throw new AssertionError("persisted W4IR function record was not found");
    }

    private static void restoreFunctionRecord(String storeName, byte[] validRecord) throws Exception {
        RecordStore rawStore = RecordStore.openRecordStore(storeName, false);
        RecordEnumeration records = rawStore.enumerateRecords(null, null, false);
        boolean restored = false;
        try {
            while (records.hasNextElement()) {
                int recordId = records.nextRecordId();
                byte[] record = rawStore.getRecord(recordId);
                if (record != null
                        && record.length >= FUNCTION_DESCRIPTOR_OFFSET + 4
                        && readInt(record, 0) == FUNCTION_MAGIC
                        && readInt(record, 4) == 0) {
                    rawStore.setRecord(recordId, validRecord, 0, validRecord.length);
                    restored = true;
                    break;
                }
            }
        } finally {
            records.destroy();
            rawStore.closeRecordStore();
        }
        if (!restored) {
            throw new AssertionError("persisted W4IR function record was not found");
        }
    }

    private static int checksum(byte[] data, int offset, int length) {
        int hash = 0x811c9dc5;
        int index;
        for (index = 0; index < length; index++) {
            hash ^= data[offset + index] & 0xff;
            hash *= 0x01000193;
        }
        return hash;
    }

    private static int[] oneInstruction() {
        return new int[] {0x0b, 0, 0};
    }

    private static int[] descriptor(int targetPc, int valueHeight, int arity, int controlDepth) {
        return new int[] {targetPc, valueHeight, arity, controlDepth, 0};
    }

    static void installHeadlessKemulatorFrontend() throws Exception {
        File root = new File(RMS_ROOT);
        if (!root.exists() && !root.mkdirs()) {
            throw new AssertionError("cannot create focused RMS root");
        }
        Class emulatorClass = Class.forName("emulator.Emulator");
        Class frontendType = Class.forName("emulator.ui.IEmulatorFrontend");
        Object frontend = Proxy.newProxyInstance(
                frontendType.getClassLoader(), new Class[] {frontendType}, new HeadlessFrontend());
        Field frontendField = emulatorClass.getDeclaredField("emulatorimpl");
        frontendField.setAccessible(true); // NOPMD -- Differential test inspects private state.
        frontendField.set(null, frontend);
    }

    private static void deleteStore(String storeName) {
        try {
            RecordStore.deleteRecordStore(storeName);
        } catch (RecordStoreException ignored) {
            // The focused test owns this name and accepts an absent store.
        }
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

    private static int fnv1a(byte[] bytes) {
        int hash = 0x811c9dc5;
        int index;
        for (index = 0; index < bytes.length; index++) {
            hash ^= bytes[index] & 0xff;
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

    private static Object defaultValue(Class type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == Boolean.TYPE) {
            return Boolean.FALSE;
        }
        if (type == Character.TYPE) {
            return new Character((char) 0);
        }
        if (type == Byte.TYPE) {
            return new Byte((byte) 0);
        }
        if (type == Short.TYPE) {
            return new Short((short) 0);
        }
        if (type == Integer.TYPE) {
            return new Integer(0);
        }
        if (type == Long.TYPE) {
            return new Long(0L);
        }
        if (type == Float.TYPE) {
            return new Float(0.0f);
        }
        if (type == Double.TYPE) {
            return new Double(0.0d);
        }
        return null;
    }

    private static Object interfaceProxy(Class type, InvocationHandler handler) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class[] {type}, handler);
    }

    private static final class HeadlessFrontend implements InvocationHandler {
        private final Properties appProperties = new Properties();

        HeadlessFrontend() {
            appProperties.setProperty("MIDlet-Vendor", "W4ME");
            appProperties.setProperty("MIDlet-Name", "MalformedDescriptorCache");
        }

        public Object invoke(Object proxy, Method method, Object[] arguments) {
            String name = method.getName();
            final Class returnType = method.getReturnType();
            if ("getRmsFolderPath".equals(name) || "getOldRmsPath".equals(name)) {
                return RMS_ROOT;
            }
            if ("getAppProperties".equals(name)) {
                return appProperties;
            }
            if ("getAppProperty".equals(name)) {
                return appProperties.getProperty((String) arguments[0]);
            }
            if (returnType.isInterface()) {
                return interfaceProxy(returnType, this);
            }
            return defaultValue(returnType);
        }
    }
}
