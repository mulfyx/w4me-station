package w4me.wasm;

public final class WasmModule {
    public static final int W4IR_FORMAT_VERSION =
            OpcodeBuildConfig.DENSE_OPCODE_DISPATCH ? 17 : 15;
    static final int I32 = 0x7f;
    static final int I64 = 0x7e;
    static final int F32 = 0x7d;
    static final int F64 = 0x7c;
    static final byte[] EMPTY_DATA_SEGMENT = new byte[0];

    static final int BLOCK = 0x02;
    static final int LOOP = 0x03;
    static final int IF = 0x04;
    static final int W4IR_STRIDE = W4IrFunction.INSTRUCTION_STRIDE;
    static final int BRANCH_DESCRIPTOR_STRIDE = 5;
    static final int BRANCH_DESCRIPTOR_TARGET_PC = 0;
    static final int BRANCH_DESCRIPTOR_VALUE_HEIGHT = 1;
    static final int BRANCH_DESCRIPTOR_ARITY = 2;
    static final int BRANCH_DESCRIPTOR_CONTROL_DEPTH = 3;
    static final int BRANCH_DESCRIPTOR_FLAGS = 4;
    static final int BRANCH_DESCRIPTOR_LOOP_TARGET = 1;
    static final int BRANCH_DESCRIPTOR_FUNCTION_RETURN = 2;
    static final int ORIGINAL_BULK_FIRST = 0xfc00;
    static final int ORIGINAL_BULK_LAST = 0xfc0b;
    static final int ORIGINAL_W4IR_FIRST = 0x1000;
    static final int ORIGINAL_W4IR_LAST = 0x1032;
    static final int EXECUTION_BULK_FIRST =
            OpcodeBuildConfig.DENSE_OPCODE_DISPATCH ? 0xc5 : ORIGINAL_BULK_FIRST;
    static final int EXECUTION_BULK_LAST =
            EXECUTION_BULK_FIRST + ORIGINAL_BULK_LAST - ORIGINAL_BULK_FIRST;
    static final int EXECUTION_W4IR_FIRST =
            OpcodeBuildConfig.DENSE_OPCODE_DISPATCH
                    ? EXECUTION_BULK_LAST + 1
                    : ORIGINAL_W4IR_FIRST;
    static final int EXECUTION_W4IR_LAST =
            EXECUTION_W4IR_FIRST + ORIGINAL_W4IR_LAST - ORIGINAL_W4IR_FIRST;
    static final int W4IR_EXECUTION_OFFSET =
            EXECUTION_W4IR_FIRST - ORIGINAL_W4IR_FIRST;
    static final int W4IR_LOCAL_LOCAL_F32_MUL = 0x1000;
    static final int W4IR_LOCAL_F32_CONST_MUL = 0x1001;
    static final int W4IR_F32_CONST_MUL_ADD = 0x1002;
    static final int W4IR_LOCAL_LOCAL_I32_AND = 0x1003;
    static final int W4IR_LOCAL_LOCAL_I32_ADD = 0x1004;
    static final int W4IR_LOCAL_I32_CONST_ADD = 0x1005;
    static final int W4IR_LOCAL_I32_CONST_AND = 0x1006;
    static final int W4IR_LOCAL_SET_LOCAL_LOCAL = 0x1007;
    static final int W4IR_LOCAL_I32_CONST_EQ = 0x1008;
    static final int W4IR_LOCAL_LOCAL_I32_CONST_AND = 0x1009;
    static final int W4IR_LOCAL_LOCAL_F32_DIV = 0x100a;
    static final int W4IR_LOCAL_SET_F32_CONST_SET = 0x100b;
    static final int W4IR_LOCAL_LOCAL_F32_LOAD = 0x100c;
    static final int W4IR_LOCAL_TEE_F32_MUL_ADD = 0x100d;
    static final int W4IR_LOCAL_F32_MUL_ADD = 0x100e;
    static final int W4IR_LOCAL_LOCAL_F32_MUL_ADD = 0x100f;
    static final int W4IR_LOCAL_LOCAL = 0x1010;
    static final int W4IR_LOCAL_I32_CONST = 0x1011;
    static final int W4IR_LOCAL_F32_CONST = 0x1012;
    static final int W4IR_F32_MUL_CONST = 0x1013;
    static final int W4IR_F32_MUL_ADD = 0x1014;
    static final int W4IR_I32_ADD_CONST = 0x1015;
    static final int W4IR_I32_AND_CONST = 0x1016;
    static final int W4IR_LOCAL_SET_GET = 0x1017;
    static final int W4IR_LOCAL_SET_F32_CONST = 0x1018;
    static final int W4IR_F32_MUL_LOCAL = 0x1019;
    static final int W4IR_LOCAL_I32_CONST_ADD_SET = 0x101a;
    static final int W4IR_LOCAL_LOCAL_F32_STORE = 0x101b;
    static final int W4IR_LOCAL_I32_CONST_EQ_BR_IF = 0x101c;
    static final int W4IR_LOCAL_TEE_MUL_ADD_SET_LOCAL_LOCAL = 0x101d;
    static final int W4IR_LOCAL_MUL_ADD_SET = 0x101e;
    static final int W4IR_F32_LOAD_LOCAL_MUL_ADD = 0x101f;
    static final int W4IR_LOCAL_ADD_SET_BR = 0x1020;
    static final int W4IR_LOCAL_SET_ADD_SET = 0x1021;
    static final int W4IR_LOCAL_SET_LOCAL_LOCAL_F32_LOAD = 0x1022;
    static final int W4IR_LOCAL_LOCAL_F32_LOAD_LOCAL = 0x1023;
    static final int W4IR_F32_LOAD_NEG = 0x1024;
    static final int W4IR_F32_LOAD_DIV = 0x1025;
    static final int W4IR_F32_DIV_TEE_MUL_ADD_SET_LOCAL_LOCAL = 0x1026;
    static final int W4IR_F32_LOAD_LOCAL_MUL_ADD_SET = 0x1027;
    static final int W4IR_BR_IF_LOCAL_I32_CONST = 0x1028;
    static final int W4IR_LOCAL_TRIPLE_F32_STORE = 0x1029;
    static final int W4IR_LOCAL4_F32_MUL_ADD_TEE = 0x102a;
    static final int W4IR_F32_LOAD_NEG_INDEX_DIV = 0x102b;
    static final int W4IR_TRIPLE_F32_STORE_LOCAL_LOAD_LOCAL = 0x102c;
    static final int W4IR_LOCAL_TEE_MUL_ADD_SET_LOAD_MUL_ADD = 0x102d;
    static final int W4IR_LOCAL_SET_DUAL_ADD_SET = 0x102e;
    static final int W4IR_COUNTED_F32_TRACE = 0x102f;
    static final int W4IR_F32_FLOOR_INTRINSIC = 0x1030;
    static final int W4IR_F32_SIN_INTRINSIC = 0x1031;
    static final int W4IR_I32_LOAD_LOCAL_TEE = 0x1032;
    static final int INTRINSIC_NONE = 0;
    static final int INTRINSIC_F32_FLOOR = 1;
    static final int INTRINSIC_F32_SIN = 2;
    private static final long F32_FLOOR_FINGERPRINT = 0xfe96e9b0900e7681L;
    private static final long F32_SIN_FINGERPRINT = 0x264e8a2c2b9836beL;

    private static final int PAGE_SIZE = 65536;
    private static final int MAX_TYPES = 4096;
    private static final int MAX_FUNCTIONS = W4IrFunction.MAX_FUNCTIONS;
    private static final int MAX_GLOBALS = 2048;
    private static final int MAX_TABLE_ELEMENTS = 65536;
    private static final int MAX_LOCALS = W4IrFunction.MAX_DECLARED_LOCALS;
    private static final int MAX_VALUE_ARITY = 16;
    private static final int MAX_VALUE_STACK = 4096;
    private static final int MAX_CONTROL_STACK = 512;
    private static final int MAX_BRANCH_TARGETS = W4IrFunction.MAX_BRANCH_TARGETS;
    private static final int MAX_BRANCH_DESCRIPTORS = W4IrFunction.MAX_BRANCH_DESCRIPTORS;
    private static final int MAX_INSTRUCTIONS = W4IrFunction.MAX_INSTRUCTIONS;

    FuncType[] types;
    ImportedFunction[] imports;
    int[] functionTypeIndices;
    FuncType[] functionTypes;
    FunctionBody[] functions;
    long[] globals;
    int[] table;
    Export[] exports;
    byte[][] dataSegments;
    int startFunction = -1;
    final byte[] memory = new byte[PAGE_SIZE];
    private W4IrStore w4irStore;
    private boolean w4irCacheHit;
    private boolean w4irCacheWriting;
    private boolean extendedFusionsEnabled;
    private boolean loadTeeFusionsEnabled;
    private boolean memoryDeclared;
    private boolean tableDeclared;
    private int declaredDataCount = -1;
    private int parsedDataCount = -1;
    private boolean[] dataSegmentPassive;
    private final ObjectList importList = new ObjectList();
    private final ObjectList definedFunctionTypes = new ObjectList();
    private final ObjectList functionBodies = new ObjectList();
    private final ObjectList globalList = new ObjectList();
    private final ObjectList exportList = new ObjectList();
    private final ObjectList passiveDataUses = new ObjectList();

    private WasmModule() {}

    public static WasmModule read(byte[] bytes) throws WasmException {
        return read(bytes, null);
    }

    public static WasmModule read(byte[] bytes, W4IrStore w4irStore) throws WasmException {
        return read(bytes, w4irStore, true, true);
    }

    public static WasmModule read(
            byte[] bytes, W4IrStore w4irStore, boolean extendedFusionsEnabled)
            throws WasmException {
        return read(bytes, w4irStore, extendedFusionsEnabled, true);
    }

    public static WasmModule read(
            byte[] bytes,
            W4IrStore w4irStore,
            boolean extendedFusionsEnabled,
            boolean loadTeeFusionsEnabled)
            throws WasmException {
        if (bytes == null || bytes.length < 8) {
            throw new WasmException("module is shorter than the WebAssembly header");
        }
        if (bytes.length > PAGE_SIZE) {
            throw new WasmException("cartridge exceeds the WASM-4 64 KiB limit");
        }
        WasmModule module = new WasmModule();
        module.w4irStore = w4irStore;
        module.extendedFusionsEnabled = extendedFusionsEnabled;
        module.loadTeeFusionsEnabled = loadTeeFusionsEnabled;
        try {
            module.parse(bytes);
            return module;
        } catch (WasmException failure) {
            module.close();
            throw failure;
        }
    }

    public byte[] memory() {
        return memory;
    }

    public int exportedFunction(String name) throws WasmException {
        int index;
        for (index = 0; index < exports.length; index++) {
            Export value = exports[index];
            if (value.kind == 0 && value.name.equals(name)) {
                return value.index;
            }
        }
        throw new WasmException("missing function export: " + name);
    }

    public boolean hasExportedFunction(String name) {
        int index;
        for (index = 0; index < exports.length; index++) {
            Export value = exports[index];
            if (value.kind == 0 && value.name.equals(name)) {
                return true;
            }
        }
        return false;
    }

    public int functionCount() {
        return functions.length;
    }

    public String w4irStatus() {
        if (w4irStore == null) {
            return "RAM";
        }
        return w4irCacheHit ? "RMS-hit" : "RMS-build";
    }

    public int w4irPageFaults() {
        return w4irStore == null ? 0 : w4irStore.pageFaults();
    }

    public int w4irPageHits() {
        return w4irStore == null ? 0 : w4irStore.pageHits();
    }

    public int w4irPromotedFunctions() {
        if (functions == null) {
            return 0;
        }
        int promoted = 0;
        int index;
        for (index = 0; index < functions.length; index++) {
            if (functions[index] != null && functions[index].isPromoted()) {
                promoted++;
            }
        }
        return promoted;
    }

    public void close() {
        if (w4irStore != null) {
            w4irStore.close();
            w4irStore = null;
        }
    }

    public long functionFingerprint(int functionIndex) {
        if (functionIndex < 0 || functionIndex >= functions.length) {
            throw new IllegalArgumentException("function index is out of range");
        }
        FunctionBody body = functions[functionIndex];
        return body == null ? 0 : body.fingerprint;
    }

    static int executionOpcode(int originalOpcode) {
        if (originalOpcode >= 0 && originalOpcode <= 0xc4) {
            return originalOpcode;
        }
        if (originalOpcode >= ORIGINAL_BULK_FIRST
                && originalOpcode <= ORIGINAL_BULK_LAST) {
            if (!OpcodeBuildConfig.DENSE_OPCODE_DISPATCH) {
                return originalOpcode;
            }
            return EXECUTION_BULK_FIRST
                    + originalOpcode
                    - ORIGINAL_BULK_FIRST;
        }
        if (originalOpcode >= ORIGINAL_W4IR_FIRST
                && originalOpcode <= ORIGINAL_W4IR_LAST) {
            if (!OpcodeBuildConfig.DENSE_OPCODE_DISPATCH) {
                return originalOpcode;
            }
            return EXECUTION_W4IR_FIRST
                    + originalOpcode
                    - ORIGINAL_W4IR_FIRST;
        }
        throw new IllegalArgumentException(
                "opcode is outside the execution map: 0x"
                        + Integer.toHexString(originalOpcode));
    }

    static int originalOpcode(int executionOpcode) {
        if (executionOpcode >= 0 && executionOpcode <= 0xc4) {
            return executionOpcode;
        }
        if (!OpcodeBuildConfig.DENSE_OPCODE_DISPATCH) {
            if ((executionOpcode >= ORIGINAL_BULK_FIRST
                            && executionOpcode <= ORIGINAL_BULK_LAST)
                    || (executionOpcode >= ORIGINAL_W4IR_FIRST
                            && executionOpcode <= ORIGINAL_W4IR_LAST)) {
                return executionOpcode;
            }
            throw new IllegalArgumentException(
                    "unknown execution opcode: 0x"
                            + Integer.toHexString(executionOpcode));
        }
        if (executionOpcode >= EXECUTION_BULK_FIRST
                && executionOpcode <= EXECUTION_BULK_LAST) {
            return ORIGINAL_BULK_FIRST
                    + executionOpcode
                    - EXECUTION_BULK_FIRST;
        }
        if (executionOpcode >= EXECUTION_W4IR_FIRST
                && executionOpcode <= EXECUTION_W4IR_LAST) {
            return ORIGINAL_W4IR_FIRST
                    + executionOpcode
                    - EXECUTION_W4IR_FIRST;
        }
        throw new IllegalArgumentException(
                "unknown execution opcode: 0x"
                        + Integer.toHexString(executionOpcode));
    }

    private void parse(byte[] bytes) throws WasmException {
        ByteReader reader = new ByteReader(bytes);
        if (reader.readU32LE() != 0x6d736100) {
            throw reader.error("invalid WebAssembly magic");
        }
        if (reader.readU32LE() != 1) {
            throw reader.error("unsupported WebAssembly version");
        }

        int previousSectionOrder = 0;
        boolean[] seenSections = new boolean[13];
        while (reader.hasRemaining()) {
            int sectionId = reader.readU8();
            if (sectionId < 0 || sectionId > 12) {
                throw reader.error("unsupported section " + sectionId);
            }
            ByteReader section = reader.readSection("section");
            if (sectionId != 0) {
                if (seenSections[sectionId]) {
                    throw section.error("duplicate section " + sectionId);
                }
                seenSections[sectionId] = true;
                int order = sectionOrder(sectionId);
                if (order <= previousSectionOrder) {
                    throw section.error("section is out of order");
                }
                previousSectionOrder = order;
            }

            switch (sectionId) {
                case 0:
                    parseCustomSection(section);
                    break;
                case 1:
                    parseTypeSection(section);
                    break;
                case 2:
                    parseImportSection(section);
                    break;
                case 3:
                    parseFunctionSection(section);
                    break;
                case 4:
                    parseTableSection(section);
                    break;
                case 5:
                    parseMemorySection(section);
                    break;
                case 6:
                    parseGlobalSection(section);
                    break;
                case 7:
                    parseExportSection(section);
                    break;
                case 8:
                    startFunction = section.readVarUInt32();
                    break;
                case 9:
                    parseElementSection(section);
                    break;
                case 10:
                    parseCodeSection(section);
                    break;
                case 11:
                    parseDataSection(section);
                    break;
                case 12:
                    declaredDataCount = section.readLength("data count", 65535);
                    break;
                default:
                    throw section.error("unsupported section " + sectionId);
            }
            section.requireEnd("section " + sectionId);
        }
        finishModel();
        commitW4IrCache();
    }

    private int sectionOrder(int sectionId) {
        if (sectionId <= 9) {
            return sectionId;
        }
        if (sectionId == 12) {
            return 10;
        }
        if (sectionId == 10) {
            return 11;
        }
        return 12;
    }

    private void parseCustomSection(ByteReader section) throws WasmException {
        if (section.hasRemaining()) {
            section.readName();
            section.skip(section.remaining());
        }
    }

    private void parseTypeSection(ByteReader section) throws WasmException {
        int count = section.readLength("type vector", MAX_TYPES);
        types = new FuncType[count];
        int index;
        for (index = 0; index < count; index++) {
            if (section.readU8() != 0x60) {
                throw section.error("type is not a function");
            }
            int[] parameters = readValueTypeVector(section, "parameter");
            int[] results = readValueTypeVector(section, "result");
            int canonicalId = index;
            int previous;
            for (previous = 0; previous < index; previous++) {
                FuncType candidate = types[previous];
                if (sameTypes(parameters, candidate.parameters)
                        && sameTypes(results, candidate.results)) {
                    canonicalId = candidate.canonicalId;
                    break;
                }
            }
            types[index] = new FuncType(parameters, results, canonicalId);
        }
    }

    private void parseImportSection(ByteReader section) throws WasmException {
        int count = section.readLength("import vector", MAX_FUNCTIONS);
        int index;
        for (index = 0; index < count; index++) {
            String moduleName = section.readName();
            String fieldName = section.readName();
            int kind = section.readU8();
            if (kind == 0) {
                int typeIndex = section.readVarUInt32();
                requireType(typeIndex, section);
                int hostId =
                        validateFunctionImport(moduleName, fieldName, typeIndex, section);
                importList.addElement(
                        new ImportedFunction(moduleName, fieldName, typeIndex, hostId));
            } else if (kind == 1) {
                throw section.error("imported tables are not supported yet");
            } else if (kind == 2) {
                if (!"env".equals(moduleName) || !"memory".equals(fieldName)) {
                    throw section.error(
                            "unsupported memory import " + moduleName + "." + fieldName);
                }
                if (memoryDeclared) {
                    throw section.error("multiple memories are not supported");
                }
                readLimits(section, "memory");
                memoryDeclared = true;
            } else if (kind == 3) {
                throw section.error("imported globals are not supported yet");
            } else {
                throw section.error("unknown import kind " + kind);
            }
        }
    }

    private void parseFunctionSection(ByteReader section) throws WasmException {
        int count = section.readLength("function vector", MAX_FUNCTIONS);
        if (count > MAX_FUNCTIONS - importList.size()) {
            throw section.error("too many functions");
        }
        int index;
        for (index = 0; index < count; index++) {
            int typeIndex = section.readVarUInt32();
            requireType(typeIndex, section);
            definedFunctionTypes.addElement(new Integer(typeIndex));
        }
    }

    private void parseTableSection(ByteReader section) throws WasmException {
        int count = section.readLength("table vector", 1);
        if (count > 1) {
            throw section.error("multiple tables are not supported yet");
        }
        if (count == 1) {
            if (section.readU8() != 0x70) {
                throw section.error("only funcref tables are supported");
            }
            int minimum = readLimits(section, "table");
            if (minimum > MAX_TABLE_ELEMENTS) {
                throw section.error("table exceeds runtime limit " + MAX_TABLE_ELEMENTS);
            }
            table = new int[minimum];
            tableDeclared = true;
            int index;
            for (index = 0; index < table.length; index++) {
                table[index] = -1;
            }
        }
    }

    private void parseMemorySection(ByteReader section) throws WasmException {
        int count = section.readLength("memory vector", 1);
        if (count > 1) {
            throw section.error("multiple memories are not supported");
        }
        if (count == 1) {
            if (memoryDeclared) {
                throw section.error("multiple memories are not supported");
            }
            readLimits(section, "memory");
            memoryDeclared = true;
        }
    }

    private void parseGlobalSection(ByteReader section) throws WasmException {
        int count = section.readLength("global vector", MAX_GLOBALS);
        int index;
        for (index = 0; index < count; index++) {
            int type = readValueType(section);
            int mutability = section.readU8();
            if (mutability != 0 && mutability != 1) {
                throw section.error("invalid global mutability " + mutability);
            }
            boolean mutable = mutability != 0;
            long initialValue = readConstantExpression(section, type);
            globalList.addElement(new Global(type, mutable, initialValue));
        }
    }

    private void parseExportSection(ByteReader section) throws WasmException {
        int count = section.readLength("export vector", 4096);
        int index;
        for (index = 0; index < count; index++) {
            String name = section.readName();
            int kind = section.readU8();
            int itemIndex = section.readVarUInt32();
            exportList.addElement(new Export(name, kind, itemIndex));
        }
    }

    private void parseElementSection(ByteReader section) throws WasmException {
        int count = section.readLength("element vector", 4096);
        int segment;
        for (segment = 0; segment < count; segment++) {
            int flags = section.readVarUInt32();
            if (flags != 0) {
                throw section.error("only active function-index element segments are supported");
            }
            int offset = (int) readConstantExpression(section, I32);
            int functionCount = section.readLength("element function vector", MAX_TABLE_ELEMENTS);
            if (!tableDeclared || offset < 0 || functionCount > table.length - offset) {
                throw section.error("element segment is outside the table");
            }
            int index;
            for (index = 0; index < functionCount; index++) {
                table[offset + index] = section.readVarUInt32();
            }
        }
    }

    private void parseCodeSection(ByteReader section) throws WasmException {
        int count = section.readLength("code vector", MAX_FUNCTIONS);
        if (count != definedFunctionTypes.size()) {
            throw section.error("function and code counts differ");
        }

        W4IrFunction[] cachedFunctions = null;
        if (w4irStore != null && w4irStore.isComplete(count)) {
            try {
                cachedFunctions = new W4IrFunction[count];
                int cachedIndex;
                for (cachedIndex = 0; cachedIndex < count; cachedIndex++) {
                    W4IrFunction cachedFunction =
                            w4irStore.loadFunction(cachedIndex);
                    int typeIndex =
                            ((Integer) definedFunctionTypes.elementAt(cachedIndex))
                                    .intValue();
                    validateCachedFunction(cachedFunction, types[typeIndex]);
                    cachedFunctions[cachedIndex] = cachedFunction;
                }
            } catch (WasmException damagedCache) {
                w4irStore.discard();
                w4irStore = null;
                cachedFunctions = null;
            }
        }

        boolean writingCache = false;
        if (cachedFunctions == null && w4irStore != null) {
            try {
                w4irStore.begin(count);
                writingCache = true;
            } catch (WasmException unavailable) {
                w4irStore.discard();
                w4irStore = null;
            }
        }

        int index;
        for (index = 0; index < count; index++) {
            ByteReader bodyReader = section.readSection("function body");
            FunctionBody body;
            if (cachedFunctions != null) {
                bodyReader.skip(bodyReader.remaining());
                body = new FunctionBody(cachedFunctions[index], w4irStore);
            } else {
                int typeIndex =
                        ((Integer) definedFunctionTypes.elementAt(index)).intValue();
                FuncType functionType = types[typeIndex];
                int parameterCount = functionType.parameters.length;
                int[] localTypes = new int[MAX_LOCALS];
                int localCount = parameterCount;
                int parameter;
                for (parameter = 0; parameter < parameterCount; parameter++) {
                    localTypes[parameter] = functionType.parameters[parameter];
                }
                int localGroupCount = bodyReader.readLength("local group vector", MAX_LOCALS);
                int declaredLocalCount = 0;
                int group;
                for (group = 0; group < localGroupCount; group++) {
                    int amount = bodyReader.readLength("local group", MAX_LOCALS);
                    int localType = readValueType(bodyReader);
                    if (amount > MAX_LOCALS - localCount) {
                        throw bodyReader.error("too many locals");
                    }
                    int local;
                    for (local = 0; local < amount; local++) {
                        localTypes[localCount++] = localType;
                    }
                    declaredLocalCount += amount;
                }
                body = decodeBody(
                        bodyReader,
                        declaredLocalCount,
                        localTypes,
                        localCount,
                        functionType);
            }
            bodyReader.requireEnd("function body");
            functionBodies.addElement(body);
        }

        if (cachedFunctions == null) {
            specializeNumericIntrinsicCalls();
        }
        if (writingCache) {
            for (index = 0; index < functionBodies.size() && writingCache; index++) {
                FunctionBody body = (FunctionBody) functionBodies.elementAt(index);
                try {
                    w4irStore.writeFunction(
                            index,
                            body.declaredLocalCount,
                            body.code,
                            body.branchTables,
                            body.branchDescriptors,
                            body.branchDescriptorPcs,
                            body.branchDescriptorIndices,
                            body.branchDescriptorTables,
                            body.fingerprint,
                            body.intrinsic);
                } catch (WasmException unavailable) {
                    w4irStore.discard();
                    w4irStore = null;
                    writingCache = false;
                }
            }
        }

        if (cachedFunctions != null) {
            w4irCacheHit = true;
        } else if (writingCache) {
            w4irCacheWriting = true;
        }
    }

    private void validateCachedFunction(W4IrFunction function, FuncType type)
            throws WasmException {
        if (function.declaredLocalCount()
                > MAX_LOCALS - type.parameters.length) {
            throw new WasmException("cached function has too many locals");
        }
        int intrinsic = function.intrinsic();
        if ((intrinsic == INTRINSIC_F32_FLOOR
                        || intrinsic == INTRINSIC_F32_SIN)
                && (type.parameters.length != 1
                        || type.parameters[0] != F32
                        || type.results.length != 1
                        || type.results[0] != F32)) {
            throw new WasmException("cached numeric intrinsic has invalid signature");
        }
    }

    private void commitW4IrCache() {
        if (!w4irCacheWriting || w4irStore == null) {
            return;
        }
        try {
            w4irStore.commit();
        } catch (WasmException unavailable) {
            w4irStore.discard();
            w4irStore = null;
        }
        w4irCacheWriting = false;
    }

    private void parseDataSection(ByteReader section) throws WasmException {
        int count = section.readLength("data vector", 65535);
        parsedDataCount = count;
        dataSegments = new byte[count][];
        dataSegmentPassive = new boolean[count];
        int segment;
        for (segment = 0; segment < count; segment++) {
            int flags = section.readVarUInt32();
            if (flags == 1) {
                int passiveLength = section.readLength("passive data segment");
                dataSegments[segment] = section.readBytes(passiveLength);
                dataSegmentPassive[segment] = true;
                continue;
            }
            if (flags == 2) {
                int memoryIndex = section.readVarUInt32();
                if (memoryIndex != 0) {
                    throw section.error("active data segment requires memory 0");
                }
            } else if (flags != 0) {
                throw section.error("unsupported data segment flags " + flags);
            }
            int offset = (int) readConstantExpression(section, I32);
            int length = section.readLength("data segment");
            if (offset < 0 || length > memory.length - offset) {
                throw section.error("data segment is outside WASM-4 memory");
            }
            byte[] data = section.readBytes(length);
            System.arraycopy(data, 0, memory, offset, length);
        }
    }

    private static Instruction[] growInstructionBuffer(
            Instruction[] values, int maximum) {
        int capacity = values.length << 1;
        if (capacity > maximum) {
            capacity = maximum;
        }
        Instruction[] grown = new Instruction[capacity];
        System.arraycopy(values, 0, grown, 0, values.length);
        return grown;
    }

    private FunctionBody decodeBody(
            ByteReader reader,
            int declaredLocalCount,
            int[] localTypes,
            int localCount,
            FuncType functionType)
            throws WasmException {
        Instruction[] code = new Instruction[64];
        int codeSize = 0;
        Instruction[] openBlocks = new Instruction[8];
        int openBlockTop = 0;
        ValidationState validation = new ValidationState(functionType.results);
        boolean complete = false;
        int totalFunctions = importList.size() + definedFunctionTypes.size();

        while (!complete && reader.hasRemaining()) {
            int opcode = reader.readU8();
            Instruction instruction = new Instruction(opcode);
            int instructionIndex = codeSize;

            switch (opcode) {
                case BLOCK:
                case LOOP:
                case IF:
                    readBlockType(reader, instruction);
                    if (openBlockTop < MAX_CONTROL_STACK) {
                        if (openBlockTop >= openBlocks.length) {
                            openBlocks =
                                    growInstructionBuffer(
                                            openBlocks, MAX_CONTROL_STACK);
                        }
                        openBlocks[openBlockTop++] = instruction;
                    }
                    break;
                case 0x05:
                    if (openBlockTop == 0) {
                        throw reader.error("else without if");
                    }
                    Instruction conditional = openBlocks[openBlockTop - 1];
                    if (conditional.opcode != IF || conditional.elsePc >= 0) {
                        throw reader.error("else does not match an if");
                    }
                    conditional.elsePc = instructionIndex;
                    break;
                case 0x0b:
                    if (openBlockTop == 0) {
                        instruction.functionEnd = true;
                        complete = true;
                    } else {
                        Instruction block = openBlocks[--openBlockTop];
                        openBlocks[openBlockTop] = null;
                        block.endPc = instructionIndex;
                        if (block.elsePc >= 0) {
                            Instruction elseInstruction = code[block.elsePc];
                            elseInstruction.endPc = instructionIndex;
                        }
                    }
                    break;
                case 0x0c:
                case 0x0d:
                    instruction.a = reader.readVarUInt32();
                    requireBranchDepth(instruction.a, openBlockTop, reader);
                    break;
                case 0x10:
                    instruction.a = reader.readVarUInt32();
                    if (instruction.a < 0 || instruction.a >= totalFunctions) {
                        throw reader.error("call function index is out of range");
                    }
                    break;
                case 0x20:
                case 0x21:
                case 0x22:
                    instruction.a = reader.readVarUInt32();
                    if (instruction.a < 0 || instruction.a >= localCount) {
                        throw reader.error("local index is out of range");
                    }
                    break;
                case 0x23:
                case 0x24:
                    instruction.a = reader.readVarUInt32();
                    if (instruction.a < 0 || instruction.a >= globalList.size()) {
                        throw reader.error("global index is out of range");
                    }
                    if (opcode == 0x24
                            && !((Global) globalList.elementAt(instruction.a)).mutable) {
                        throw reader.error("global.set targets an immutable global");
                    }
                    break;
                case 0x0e:
                    int targetCount =
                            reader.readLength("branch table", MAX_BRANCH_TARGETS);
                    instruction.vector = new int[targetCount + 1];
                    int target;
                    for (target = 0; target <= targetCount; target++) {
                        instruction.vector[target] = reader.readVarUInt32();
                        requireBranchDepth(
                                instruction.vector[target], openBlockTop, reader);
                    }
                    break;
                case 0x11:
                    instruction.a = reader.readVarUInt32();
                    instruction.b = reader.readVarUInt32();
                    requireType(instruction.a, reader);
                    if (!tableDeclared || instruction.b != 0) {
                        throw reader.error("call_indirect requires table 0");
                    }
                    break;
                case 0x1c:
                    instruction.vector = readValueTypeVector(reader, "typed select");
                    if (instruction.vector.length != 1) {
                        throw reader.error("typed select requires exactly one result type");
                    }
                    break;
                case 0x28:
                case 0x29:
                case 0x2a:
                case 0x2b:
                case 0x2c:
                case 0x2d:
                case 0x2e:
                case 0x2f:
                case 0x30:
                case 0x31:
                case 0x32:
                case 0x33:
                case 0x34:
                case 0x35:
                case 0x36:
                case 0x37:
                case 0x38:
                case 0x39:
                case 0x3a:
                case 0x3b:
                case 0x3c:
                case 0x3d:
                case 0x3e:
                    if (!memoryDeclared) {
                        throw reader.error("memory instruction requires memory 0");
                    }
                    int alignment = reader.readVarUInt32();
                    int maximumAlignment = maximumAlignment(opcode);
                    if (alignment < 0 || alignment > maximumAlignment) {
                        throw reader.error("memory alignment exceeds natural alignment");
                    }
                    instruction.a = reader.readVarUInt32();
                    break;
                case 0x3f:
                case 0x40:
                    instruction.a = reader.readVarUInt32();
                    if (!memoryDeclared || instruction.a != 0) {
                        throw reader.error("memory instruction requires memory 0");
                    }
                    break;
                case 0x41:
                    instruction.longValue = reader.readVarInt32();
                    break;
                case 0x42:
                    instruction.longValue = reader.readVarInt64();
                    break;
                case 0x43:
                    instruction.longValue = reader.readU32LE() & 0xffffffffL;
                    break;
                case 0x44:
                    instruction.longValue = reader.readU64LE();
                    break;
                case 0xfc:
                    int extension = reader.readVarUInt32();
                    instruction.opcode = 0xfc00 | extension;
                    if (extension >= 0 && extension <= 7) {
                        // Saturating float-to-integer conversions have no immediates.
                    } else if (extension == 8) {
                        if (declaredDataCount < 0) {
                            throw reader.error("memory.init requires a data count section");
                        }
                        instruction.a = reader.readVarUInt32();
                        instruction.b = reader.readVarUInt32();
                        if (instruction.a < 0 || instruction.a >= declaredDataCount) {
                            throw reader.error("memory.init data index is out of range");
                        }
                        if (!memoryDeclared || instruction.b != 0) {
                            throw reader.error("memory.init requires memory 0");
                        }
                        passiveDataUses.addElement(new Integer(instruction.a));
                    } else if (extension == 9) {
                        if (declaredDataCount < 0) {
                            throw reader.error("data.drop requires a data count section");
                        }
                        instruction.a = reader.readVarUInt32();
                        if (instruction.a < 0 || instruction.a >= declaredDataCount) {
                            throw reader.error("data.drop index is out of range");
                        }
                        passiveDataUses.addElement(new Integer(instruction.a));
                    } else if (extension == 10) {
                        instruction.a = reader.readVarUInt32();
                        instruction.b = reader.readVarUInt32();
                        if (!memoryDeclared || instruction.a != 0 || instruction.b != 0) {
                            throw reader.error("memory.copy requires memory 0");
                        }
                    } else if (extension == 11) {
                        instruction.a = reader.readVarUInt32();
                        if (!memoryDeclared || instruction.a != 0) {
                            throw reader.error("memory.fill requires memory 0");
                        }
                    } else {
                        throw reader.error("unsupported 0xfc opcode " + extension);
                    }
                    break;
                default:
                    if (!isImmediateFreeOpcode(opcode)) {
                        throw reader.error("unsupported opcode 0x" + Integer.toHexString(opcode));
                    }
                    break;
            }
            validateInstruction(
                    instruction,
                    validation,
                    localTypes,
                    localCount,
                    totalFunctions,
                    reader);
            if (codeSize >= MAX_INSTRUCTIONS) {
                throw reader.error("function exceeds instruction limit " + MAX_INSTRUCTIONS);
            }
            if (codeSize >= code.length) {
                code = growInstructionBuffer(code, MAX_INSTRUCTIONS);
            }
            code[codeSize++] = instruction;
        }

        if (!complete || openBlockTop != 0) {
            throw reader.error("unterminated function body");
        }
        validation.requireComplete(reader);
        Instruction[] instructions = new Instruction[codeSize];
        System.arraycopy(code, 0, instructions, 0, codeSize);
        int[] branchDescriptors =
                buildBranchDescriptors(instructions, functionType.results.length, reader);
        validateBranchDescriptors(
                instructions, branchDescriptors, functionType.results.length, reader);
        return new FunctionBody(
                declaredLocalCount,
                instructions,
                branchDescriptors,
                extendedFusionsEnabled,
                loadTeeFusionsEnabled);
    }

    private int[] buildBranchDescriptors(
            Instruction[] instructions, int functionResultCount, ByteReader reader)
            throws WasmException {
        int descriptorCount = 0;
        int index;
        for (index = 0; index < instructions.length; index++) {
            Instruction instruction = instructions[index];
            if (instruction.opcode == 0x0c || instruction.opcode == 0x0d) {
                descriptorCount++;
            } else if (instruction.opcode == 0x0e) {
                descriptorCount += instruction.vector.length;
            }
            if (descriptorCount > MAX_BRANCH_DESCRIPTORS) {
                throw reader.error(
                        "function exceeds branch descriptor limit "
                                + MAX_BRANCH_DESCRIPTORS);
            }
        }

        int[] descriptors =
                new int[descriptorCount * BRANCH_DESCRIPTOR_STRIDE];
        int[] controls = new int[MAX_CONTROL_STACK];
        int controlTop = 0;
        int nextDescriptor = 0;
        for (index = 0; index < instructions.length; index++) {
            Instruction instruction = instructions[index];
            int opcode = instruction.opcode;
            if (opcode == BLOCK || opcode == LOOP || opcode == IF) {
                if (controlTop >= controls.length) {
                    throw reader.error(
                            "static control stack exceeds runtime limit "
                                    + MAX_CONTROL_STACK);
                }
                controls[controlTop++] = index;
            } else if (opcode == 0x0c || opcode == 0x0d) {
                instruction.branchDescriptorIndex = nextDescriptor;
                writeBranchDescriptor(
                        descriptors,
                        nextDescriptor++,
                        instruction.a,
                        controls,
                        controlTop,
                        instructions,
                        functionResultCount,
                        reader);
            } else if (opcode == 0x0e) {
                instruction.branchDescriptorVector =
                        new int[instruction.vector.length];
                int target;
                for (target = 0; target < instruction.vector.length; target++) {
                    instruction.branchDescriptorVector[target] = nextDescriptor;
                    writeBranchDescriptor(
                            descriptors,
                            nextDescriptor++,
                            instruction.vector[target],
                            controls,
                            controlTop,
                            instructions,
                            functionResultCount,
                            reader);
                }
            } else if (opcode == 0x0b && !instruction.functionEnd) {
                if (controlTop <= 0) {
                    throw reader.error("static control stack underflow");
                }
                controlTop--;
            }
        }
        if (controlTop != 0 || nextDescriptor != descriptorCount) {
            throw reader.error("inconsistent static branch descriptors");
        }
        return descriptors;
    }

    private void writeBranchDescriptor(
            int[] descriptors,
            int descriptorIndex,
            int depth,
            int[] controls,
            int controlTop,
            Instruction[] instructions,
            int functionResultCount,
            ByteReader reader)
            throws WasmException {
        int targetPc;
        int valueHeight;
        int arity;
        int activeControlDepth;
        int flags;
        if (depth == controlTop) {
            targetPc = -1;
            valueHeight = 0;
            arity = functionResultCount;
            activeControlDepth = 0;
            flags = BRANCH_DESCRIPTOR_FUNCTION_RETURN;
        } else {
            if (depth < 0 || depth >= controlTop) {
                throw reader.error("static branch depth is out of range");
            }
            int targetControl = controlTop - 1 - depth;
            int openerPc = controls[targetControl];
            Instruction opener = instructions[openerPc];
            valueHeight = opener.controlHeight;
            if (opener.opcode == LOOP) {
                targetPc = openerPc + 1;
                arity = opener.parameterCount;
                activeControlDepth = targetControl + 1;
                flags = BRANCH_DESCRIPTOR_LOOP_TARGET;
            } else {
                targetPc = opener.endPc + 1;
                arity = opener.resultCount;
                activeControlDepth = targetControl;
                flags = 0;
            }
        }
        if (targetPc < -1
                || targetPc >= instructions.length
                || valueHeight < 0
                || valueHeight > MAX_VALUE_STACK
                || arity < 0
                || arity > MAX_VALUE_ARITY
                || valueHeight > MAX_VALUE_STACK - arity
                || activeControlDepth < 0
                || activeControlDepth > MAX_CONTROL_STACK
                || ((flags & BRANCH_DESCRIPTOR_FUNCTION_RETURN) != 0
                        && targetPc != -1)
                || ((flags & BRANCH_DESCRIPTOR_FUNCTION_RETURN) == 0
                        && targetPc < 0)) {
            throw reader.error("invalid static branch descriptor");
        }
        int offset = descriptorIndex * BRANCH_DESCRIPTOR_STRIDE;
        descriptors[offset + BRANCH_DESCRIPTOR_TARGET_PC] = targetPc;
        descriptors[offset + BRANCH_DESCRIPTOR_VALUE_HEIGHT] = valueHeight;
        descriptors[offset + BRANCH_DESCRIPTOR_ARITY] = arity;
        descriptors[offset + BRANCH_DESCRIPTOR_CONTROL_DEPTH] =
                activeControlDepth;
        descriptors[offset + BRANCH_DESCRIPTOR_FLAGS] = flags;
    }

    private void validateBranchDescriptors(
            Instruction[] instructions,
            int[] descriptors,
            int functionResultCount,
            ByteReader reader)
            throws WasmException {
        if (descriptors.length % BRANCH_DESCRIPTOR_STRIDE != 0) {
            throw reader.error("invalid static branch descriptor data length");
        }
        int[] controls = new int[MAX_CONTROL_STACK];
        int controlTop = 0;
        int expectedDescriptor = 0;
        int index;
        for (index = 0; index < instructions.length; index++) {
            Instruction instruction = instructions[index];
            int opcode = instruction.opcode;
            if (opcode == BLOCK || opcode == LOOP || opcode == IF) {
                if (controlTop >= controls.length) {
                    throw reader.error(
                            "static control stack exceeds runtime limit "
                                    + MAX_CONTROL_STACK);
                }
                controls[controlTop++] = index;
                if (instruction.controlHeight < 0
                        || instruction.controlHeight > MAX_VALUE_STACK) {
                    throw reader.error("invalid static control height");
                }
            } else if (opcode == 0x0c || opcode == 0x0d) {
                if (instruction.branchDescriptorIndex != expectedDescriptor) {
                    throw reader.error("inconsistent direct branch descriptor index");
                }
                verifyBranchDescriptor(
                        descriptors,
                        expectedDescriptor++,
                        instruction.a,
                        controls,
                        controlTop,
                        instructions,
                        functionResultCount,
                        reader);
            } else if (opcode == 0x0e) {
                if (instruction.branchDescriptorVector == null
                        || instruction.branchDescriptorVector.length
                                != instruction.vector.length) {
                    throw reader.error("inconsistent branch descriptor table");
                }
                int target;
                for (target = 0; target < instruction.vector.length; target++) {
                    if (instruction.branchDescriptorVector[target]
                            != expectedDescriptor) {
                        throw reader.error(
                                "inconsistent branch descriptor table index");
                    }
                    verifyBranchDescriptor(
                            descriptors,
                            expectedDescriptor++,
                            instruction.vector[target],
                            controls,
                            controlTop,
                            instructions,
                            functionResultCount,
                            reader);
                }
            } else {
                if (instruction.branchDescriptorIndex != -1
                        || instruction.branchDescriptorVector != null) {
                    throw reader.error(
                            "non-branch instruction has a branch descriptor");
                }
                if (opcode == 0x0b && !instruction.functionEnd) {
                    if (controlTop <= 0) {
                        throw reader.error("static control stack underflow");
                    }
                    controlTop--;
                }
            }
        }
        if (controlTop != 0
                || expectedDescriptor * BRANCH_DESCRIPTOR_STRIDE
                        != descriptors.length) {
            throw reader.error("incomplete static branch descriptor coverage");
        }
    }

    private void verifyBranchDescriptor(
            int[] descriptors,
            int descriptorIndex,
            int depth,
            int[] controls,
            int controlTop,
            Instruction[] instructions,
            int functionResultCount,
            ByteReader reader)
            throws WasmException {
        int expectedTargetPc;
        int expectedValueHeight;
        int expectedArity;
        int expectedControlDepth;
        int expectedFlags;
        if (depth == controlTop) {
            expectedTargetPc = -1;
            expectedValueHeight = 0;
            expectedArity = functionResultCount;
            expectedControlDepth = 0;
            expectedFlags = BRANCH_DESCRIPTOR_FUNCTION_RETURN;
        } else {
            if (depth < 0 || depth >= controlTop) {
                throw reader.error("static branch depth is out of range");
            }
            int targetControl = controlTop - 1 - depth;
            int openerPc = controls[targetControl];
            Instruction opener = instructions[openerPc];
            expectedValueHeight = opener.controlHeight;
            if (opener.opcode == LOOP) {
                expectedTargetPc = openerPc + 1;
                expectedArity = opener.parameterCount;
                expectedControlDepth = targetControl + 1;
                expectedFlags = BRANCH_DESCRIPTOR_LOOP_TARGET;
            } else {
                expectedTargetPc = opener.endPc + 1;
                expectedArity = opener.resultCount;
                expectedControlDepth = targetControl;
                expectedFlags = 0;
            }
        }
        int offset = descriptorIndex * BRANCH_DESCRIPTOR_STRIDE;
        if (offset < 0
                || offset > descriptors.length - BRANCH_DESCRIPTOR_STRIDE
                || descriptors[offset + BRANCH_DESCRIPTOR_TARGET_PC]
                        != expectedTargetPc
                || descriptors[offset + BRANCH_DESCRIPTOR_VALUE_HEIGHT]
                        != expectedValueHeight
                || descriptors[offset + BRANCH_DESCRIPTOR_ARITY]
                        != expectedArity
                || descriptors[offset + BRANCH_DESCRIPTOR_CONTROL_DEPTH]
                        != expectedControlDepth
                || descriptors[offset + BRANCH_DESCRIPTOR_FLAGS]
                        != expectedFlags
                || expectedValueHeight > MAX_VALUE_STACK - expectedArity) {
            throw reader.error("static branch descriptor mismatch");
        }
    }

    private void validateInstruction(
            Instruction instruction,
            ValidationState state,
            int[] localTypes,
            int localCount,
            int totalFunctions,
            ByteReader reader)
            throws WasmException {
        int opcode = instruction.opcode;
        switch (opcode) {
            case 0x00:
                state.markUnreachable();
                return;
            case 0x01:
                return;
            case BLOCK:
            case LOOP:
                instruction.controlHeight =
                        state.pushControl(
                                opcode,
                                instruction.parameterTypes,
                                instruction.resultTypes,
                                reader);
                return;
            case IF:
                state.popExpected(I32, reader);
                instruction.controlHeight =
                        state.pushControl(
                                opcode,
                                instruction.parameterTypes,
                                instruction.resultTypes,
                                reader);
                return;
            case 0x05:
                state.beginElse(reader);
                return;
            case 0x0b:
                state.endControl(reader);
                return;
            case 0x0c:
                state.popTypes(state.labelTypes(instruction.a), reader);
                state.markUnreachable();
                return;
            case 0x0d:
                state.popExpected(I32, reader);
                int[] conditionalLabelTypes = state.labelTypes(instruction.a);
                state.popTypes(conditionalLabelTypes, reader);
                state.pushTypes(conditionalLabelTypes, reader);
                return;
            case 0x0e:
                state.popExpected(I32, reader);
                int[] tableLabelTypes = state.labelTypes(instruction.vector[0]);
                int target;
                for (target = 1; target < instruction.vector.length; target++) {
                    if (!sameTypes(
                            tableLabelTypes, state.labelTypes(instruction.vector[target]))) {
                        throw reader.error("br_table targets have different label types");
                    }
                }
                state.popTypes(tableLabelTypes, reader);
                state.markUnreachable();
                return;
            case 0x0f:
                state.popTypes(state.functionResultTypes(), reader);
                state.markUnreachable();
                return;
            case 0x10:
                if (instruction.a < 0 || instruction.a >= totalFunctions) {
                    throw reader.error("call function index is out of range");
                }
                validateCall(functionTypeDuringParse(instruction.a), state, reader);
                return;
            case 0x11:
                state.popExpected(I32, reader);
                validateCall(types[instruction.a], state, reader);
                return;
            case 0x1a:
                state.popAny(reader);
                return;
            case 0x1b:
                state.validateSelect(reader);
                return;
            case 0x1c:
                state.validateTypedSelect(instruction.vector[0], reader);
                return;
            case 0x20:
                state.push(localTypes[instruction.a], reader);
                return;
            case 0x21:
                state.popExpected(localTypes[instruction.a], reader);
                return;
            case 0x22:
                state.popExpected(localTypes[instruction.a], reader);
                state.push(localTypes[instruction.a], reader);
                return;
            case 0x23:
                state.push(((Global) globalList.elementAt(instruction.a)).type, reader);
                return;
            case 0x24:
                state.popExpected(
                        ((Global) globalList.elementAt(instruction.a)).type, reader);
                return;
            case 0x28:
            case 0x2c:
            case 0x2d:
            case 0x2e:
            case 0x2f:
                validateLoad(I32, state, reader);
                return;
            case 0x29:
            case 0x30:
            case 0x31:
            case 0x32:
            case 0x33:
            case 0x34:
            case 0x35:
                validateLoad(I64, state, reader);
                return;
            case 0x2a:
                validateLoad(F32, state, reader);
                return;
            case 0x2b:
                validateLoad(F64, state, reader);
                return;
            case 0x36:
            case 0x3a:
            case 0x3b:
                validateStore(I32, state, reader);
                return;
            case 0x37:
            case 0x3c:
            case 0x3d:
            case 0x3e:
                validateStore(I64, state, reader);
                return;
            case 0x38:
                validateStore(F32, state, reader);
                return;
            case 0x39:
                validateStore(F64, state, reader);
                return;
            case 0x3f:
                state.push(I32, reader);
                return;
            case 0x40:
                validateUnary(I32, I32, state, reader);
                return;
            case 0x41:
                state.push(I32, reader);
                return;
            case 0x42:
                state.push(I64, reader);
                return;
            case 0x43:
                state.push(F32, reader);
                return;
            case 0x44:
                state.push(F64, reader);
                return;
            case 0xfc0a:
            case 0xfc0b:
                state.popExpected(I32, reader);
                state.popExpected(I32, reader);
                state.popExpected(I32, reader);
                return;
            case 0xfc08:
                state.popExpected(I32, reader);
                state.popExpected(I32, reader);
                state.popExpected(I32, reader);
                return;
            case 0xfc09:
                return;
            default:
                break;
        }

        if (opcode == 0x45) {
            validateUnary(I32, I32, state, reader);
        } else if (opcode >= 0x46 && opcode <= 0x4f) {
            validateBinary(I32, I32, state, reader);
        } else if (opcode == 0x50) {
            validateUnary(I64, I32, state, reader);
        } else if (opcode >= 0x51 && opcode <= 0x5a) {
            validateBinary(I64, I32, state, reader);
        } else if (opcode >= 0x5b && opcode <= 0x60) {
            validateBinary(F32, I32, state, reader);
        } else if (opcode >= 0x61 && opcode <= 0x66) {
            validateBinary(F64, I32, state, reader);
        } else if (opcode >= 0x67 && opcode <= 0x69) {
            validateUnary(I32, I32, state, reader);
        } else if (opcode >= 0x6a && opcode <= 0x78) {
            validateBinary(I32, I32, state, reader);
        } else if (opcode >= 0x79 && opcode <= 0x7b) {
            validateUnary(I64, I64, state, reader);
        } else if (opcode >= 0x7c && opcode <= 0x8a) {
            validateBinary(I64, I64, state, reader);
        } else if (opcode >= 0x8b && opcode <= 0x91) {
            validateUnary(F32, F32, state, reader);
        } else if (opcode >= 0x92 && opcode <= 0x98) {
            validateBinary(F32, F32, state, reader);
        } else if (opcode >= 0x99 && opcode <= 0x9f) {
            validateUnary(F64, F64, state, reader);
        } else if (opcode >= 0xa0 && opcode <= 0xa6) {
            validateBinary(F64, F64, state, reader);
        } else if (opcode == 0xa7) {
            validateUnary(I64, I32, state, reader);
        } else if (opcode == 0xa8 || opcode == 0xa9) {
            validateUnary(F32, I32, state, reader);
        } else if (opcode == 0xaa || opcode == 0xab) {
            validateUnary(F64, I32, state, reader);
        } else if (opcode == 0xac || opcode == 0xad) {
            validateUnary(I32, I64, state, reader);
        } else if (opcode == 0xae || opcode == 0xaf) {
            validateUnary(F32, I64, state, reader);
        } else if (opcode == 0xb0 || opcode == 0xb1) {
            validateUnary(F64, I64, state, reader);
        } else if (opcode == 0xb2 || opcode == 0xb3) {
            validateUnary(I32, F32, state, reader);
        } else if (opcode == 0xb4 || opcode == 0xb5) {
            validateUnary(I64, F32, state, reader);
        } else if (opcode == 0xb6) {
            validateUnary(F64, F32, state, reader);
        } else if (opcode == 0xb7 || opcode == 0xb8) {
            validateUnary(I32, F64, state, reader);
        } else if (opcode == 0xb9 || opcode == 0xba) {
            validateUnary(I64, F64, state, reader);
        } else if (opcode == 0xbb) {
            validateUnary(F32, F64, state, reader);
        } else if (opcode == 0xbc) {
            validateUnary(F32, I32, state, reader);
        } else if (opcode == 0xbd) {
            validateUnary(F64, I64, state, reader);
        } else if (opcode == 0xbe) {
            validateUnary(I32, F32, state, reader);
        } else if (opcode == 0xbf) {
            validateUnary(I64, F64, state, reader);
        } else if (opcode == 0xc0 || opcode == 0xc1) {
            validateUnary(I32, I32, state, reader);
        } else if (opcode >= 0xc2 && opcode <= 0xc4) {
            validateUnary(I64, I64, state, reader);
        } else if (opcode == 0xfc00 || opcode == 0xfc01) {
            validateUnary(F32, I32, state, reader);
        } else if (opcode == 0xfc02 || opcode == 0xfc03) {
            validateUnary(F64, I32, state, reader);
        } else if (opcode == 0xfc04 || opcode == 0xfc05) {
            validateUnary(F32, I64, state, reader);
        } else if (opcode == 0xfc06 || opcode == 0xfc07) {
            validateUnary(F64, I64, state, reader);
        } else {
            throw reader.error(
                    "validator has no type rule for opcode 0x"
                            + Integer.toHexString(opcode));
        }
    }

    private FuncType functionTypeDuringParse(int functionIndex) {
        int typeIndex;
        if (functionIndex < importList.size()) {
            typeIndex = ((ImportedFunction) importList.elementAt(functionIndex)).typeIndex;
        } else {
            typeIndex =
                    ((Integer) definedFunctionTypes.elementAt(
                                    functionIndex - importList.size()))
                            .intValue();
        }
        return types[typeIndex];
    }

    private void validateCall(FuncType type, ValidationState state, ByteReader reader)
            throws WasmException {
        state.popTypes(type.parameters, reader);
        state.pushTypes(type.results, reader);
    }

    private void validateLoad(int resultType, ValidationState state, ByteReader reader)
            throws WasmException {
        state.popExpected(I32, reader);
        state.push(resultType, reader);
    }

    private void validateStore(int valueType, ValidationState state, ByteReader reader)
            throws WasmException {
        state.popExpected(valueType, reader);
        state.popExpected(I32, reader);
    }

    private void validateUnary(
            int inputType, int resultType, ValidationState state, ByteReader reader)
            throws WasmException {
        state.popExpected(inputType, reader);
        state.push(resultType, reader);
    }

    private void validateBinary(
            int inputType, int resultType, ValidationState state, ByteReader reader)
            throws WasmException {
        state.popExpected(inputType, reader);
        state.popExpected(inputType, reader);
        state.push(resultType, reader);
    }

    private static boolean sameTypes(int[] first, int[] second) {
        if (first.length != second.length) {
            return false;
        }
        int index;
        for (index = 0; index < first.length; index++) {
            if (first[index] != second[index]) {
                return false;
            }
        }
        return true;
    }

    static boolean sameFunctionType(FuncType first, FuncType second) {
        return sameTypes(first.parameters, second.parameters)
                && sameTypes(first.results, second.results);
    }

    private void requireBranchDepth(int depth, int openBlockCount, ByteReader reader)
            throws WasmException {
        if (depth < 0 || depth > openBlockCount) {
            throw reader.error("branch depth is out of range");
        }
    }

    private int maximumAlignment(int opcode) {
        if (opcode == 0x29
                || opcode == 0x2b
                || opcode == 0x37
                || opcode == 0x39) {
            return 3;
        }
        if (opcode == 0x28
                || opcode == 0x2a
                || opcode == 0x36
                || opcode == 0x38
                || opcode == 0x34
                || opcode == 0x35
                || opcode == 0x3e) {
            return 2;
        }
        if (opcode == 0x2e
                || opcode == 0x2f
                || opcode == 0x32
                || opcode == 0x33
                || opcode == 0x3b
                || opcode == 0x3c
                || opcode == 0x3d) {
            return 1;
        }
        return 0;
    }

    private boolean isImmediateFreeOpcode(int opcode) {
        return opcode == 0x00
                || opcode == 0x01
                || opcode == 0x0f
                || opcode == 0x1a
                || opcode == 0x1b
                || (opcode >= 0x45 && opcode <= 0xc4);
    }

    private void readBlockType(ByteReader reader, Instruction instruction) throws WasmException {
        int blockType = reader.readVarInt32();
        if (blockType == -64) {
            instruction.parameterTypes = new int[0];
            instruction.resultTypes = new int[0];
        } else if (blockType >= -4 && blockType <= -1) {
            instruction.parameterTypes = new int[0];
            instruction.resultTypes = new int[] {0x80 + blockType};
        } else if (blockType >= 0 && blockType < types.length) {
            instruction.parameterTypes = types[blockType].parameters;
            instruction.resultTypes = types[blockType].results;
        } else {
            throw reader.error("invalid block type " + blockType);
        }
        instruction.parameterCount = instruction.parameterTypes.length;
        instruction.resultCount = instruction.resultTypes.length;
    }

    private long readConstantExpression(ByteReader reader, int expectedType) throws WasmException {
        int opcode = reader.readU8();
        long value;
        if (opcode == 0x41 && expectedType == I32) {
            value = reader.readVarInt32();
        } else if (opcode == 0x42 && expectedType == I64) {
            value = reader.readVarInt64();
        } else if (opcode == 0x43 && expectedType == F32) {
            value = reader.readU32LE() & 0xffffffffL;
        } else if (opcode == 0x44 && expectedType == F64) {
            value = reader.readU64LE();
        } else {
            throw reader.error("unsupported constant expression");
        }
        if (reader.readU8() != 0x0b) {
            throw reader.error("constant expression is not terminated");
        }
        return value;
    }

    private int readLimits(ByteReader reader, String label) throws WasmException {
        int flags = reader.readVarUInt32();
        if ((flags & ~1) != 0) {
            throw reader.error("unsupported " + label + " limits flags");
        }
        int minimum = reader.readVarUInt32();
        if (minimum < 0) {
            throw reader.error(label + " minimum is too large");
        }
        int maximum = -1;
        if ((flags & 1) != 0) {
            maximum = reader.readVarUInt32();
            if (maximum < 0) {
                throw reader.error(label + " maximum is too large");
            }
            if (minimum > maximum) {
                throw reader.error(label + " minimum exceeds maximum");
            }
        }
        if ("memory".equals(label)) {
            if (minimum > 1 || maximum > 65536 || (maximum >= 0 && maximum < 1)) {
                throw reader.error("memory limits cannot accept the WASM-4 one-page memory");
            }
        } else if ("table".equals(label)
                && (minimum > MAX_TABLE_ELEMENTS
                        || (maximum >= 0 && maximum > MAX_TABLE_ELEMENTS))) {
            throw reader.error("table exceeds runtime limit " + MAX_TABLE_ELEMENTS);
        }
        return minimum;
    }

    private int[] readValueTypeVector(ByteReader reader, String label) throws WasmException {
        int count = reader.readLength(label + " type vector", MAX_VALUE_ARITY);
        int[] result = new int[count];
        int index;
        for (index = 0; index < count; index++) {
            result[index] = readValueType(reader);
        }
        return result;
    }

    private int readValueType(ByteReader reader) throws WasmException {
        int type = reader.readU8();
        if (type != I32 && type != I64 && type != F32 && type != F64) {
            throw reader.error("unsupported value type 0x" + Integer.toHexString(type));
        }
        return type;
    }

    private void requireType(int index, ByteReader reader) throws WasmException {
        if (types == null || index < 0 || index >= types.length) {
            throw reader.error("function type index is out of range");
        }
    }

    private int validateFunctionImport(
            String moduleName, String fieldName, int typeIndex, ByteReader reader)
            throws WasmException {
        if (!"env".equals(moduleName)) {
            throw reader.error("unsupported function import " + moduleName + "." + fieldName);
        }

        int parameterCount = -1;
        boolean returnsI32 = false;
        int hostId = -1;
        if ("blit".equals(fieldName)) {
            parameterCount = 6;
            hostId = WasmHost.IMPORT_BLIT;
        } else if ("blitSub".equals(fieldName)) {
            parameterCount = 9;
            hostId = WasmHost.IMPORT_BLIT_SUB;
        } else if ("line".equals(fieldName)) {
            parameterCount = 4;
            hostId = WasmHost.IMPORT_LINE;
        } else if ("oval".equals(fieldName)) {
            parameterCount = 4;
            hostId = WasmHost.IMPORT_OVAL;
        } else if ("rect".equals(fieldName)) {
            parameterCount = 4;
            hostId = WasmHost.IMPORT_RECT;
        } else if ("tone".equals(fieldName)) {
            parameterCount = 4;
            hostId = WasmHost.IMPORT_TONE;
        } else if ("textUtf8".equals(fieldName)) {
            parameterCount = 4;
            hostId = WasmHost.IMPORT_TEXT_UTF8;
        } else if ("textUtf16".equals(fieldName)) {
            parameterCount = 4;
            hostId = WasmHost.IMPORT_TEXT_UTF16;
        } else if ("hline".equals(fieldName)) {
            parameterCount = 3;
            hostId = WasmHost.IMPORT_HLINE;
        } else if ("vline".equals(fieldName)) {
            parameterCount = 3;
            hostId = WasmHost.IMPORT_VLINE;
        } else if ("text".equals(fieldName)) {
            parameterCount = 3;
            hostId = WasmHost.IMPORT_TEXT;
        } else if ("diskr".equals(fieldName)) {
            parameterCount = 2;
            returnsI32 = true;
            hostId = WasmHost.IMPORT_DISK_READ;
        } else if ("diskw".equals(fieldName)) {
            parameterCount = 2;
            returnsI32 = true;
            hostId = WasmHost.IMPORT_DISK_WRITE;
        } else if ("trace".equals(fieldName)) {
            parameterCount = 1;
            hostId = WasmHost.IMPORT_TRACE;
        } else if ("traceUtf8".equals(fieldName)) {
            parameterCount = 2;
            hostId = WasmHost.IMPORT_TRACE_UTF8;
        } else if ("traceUtf16".equals(fieldName)) {
            parameterCount = 2;
            hostId = WasmHost.IMPORT_TRACE_UTF16;
        } else if ("tracef".equals(fieldName)) {
            parameterCount = 2;
            hostId = WasmHost.IMPORT_TRACEF;
        }
        if (parameterCount < 0) {
            throw reader.error("unsupported function import env." + fieldName);
        }

        FuncType type = types[typeIndex];
        if (type.parameters.length != parameterCount) {
            throw reader.error("invalid signature for function import env." + fieldName);
        }
        int index;
        for (index = 0; index < type.parameters.length; index++) {
            if (type.parameters[index] != I32) {
                throw reader.error("invalid signature for function import env." + fieldName);
            }
        }
        if (returnsI32) {
            if (type.results.length != 1 || type.results[0] != I32) {
                throw reader.error("invalid signature for function import env." + fieldName);
            }
        } else if (type.results.length != 0) {
            throw reader.error("invalid signature for function import env." + fieldName);
        }
        return hostId;
    }

    private void finishModel() throws WasmException {
        if (!memoryDeclared) {
            throw new WasmException("WASM-4 cartridge does not declare memory");
        }
        if (definedFunctionTypes.size() != functionBodies.size()) {
            throw new WasmException("function and code counts differ");
        }
        imports = new ImportedFunction[importList.size()];
        importList.copyInto(imports);

        int totalFunctions = imports.length + definedFunctionTypes.size();
        functionTypeIndices = new int[totalFunctions];
        functionTypes = new FuncType[totalFunctions];
        functions = new FunctionBody[totalFunctions];
        int index;
        for (index = 0; index < imports.length; index++) {
            functionTypeIndices[index] = imports[index].typeIndex;
            functionTypes[index] = types[functionTypeIndices[index]];
        }
        for (index = 0; index < definedFunctionTypes.size(); index++) {
            functionTypeIndices[imports.length + index] =
                    ((Integer) definedFunctionTypes.elementAt(index)).intValue();
            functionTypes[imports.length + index] =
                    types[functionTypeIndices[imports.length + index]];
            functions[imports.length + index] = (FunctionBody) functionBodies.elementAt(index);
        }

        globals = new long[globalList.size()];
        for (index = 0; index < globals.length; index++) {
            globals[index] = ((Global) globalList.elementAt(index)).initialValue;
        }
        exports = new Export[exportList.size()];
        exportList.copyInto(exports);

        if (parsedDataCount < 0) {
            parsedDataCount = 0;
            dataSegments = new byte[0][];
            dataSegmentPassive = new boolean[0];
        }
        if (declaredDataCount >= 0 && declaredDataCount != parsedDataCount) {
            throw new WasmException("data count does not match the data section");
        }
        for (index = 0; index < passiveDataUses.size(); index++) {
            int dataIndex = ((Integer) passiveDataUses.elementAt(index)).intValue();
            if (dataIndex < 0
                    || dataIndex >= dataSegmentPassive.length
                    || !dataSegmentPassive[dataIndex]) {
                throw new WasmException("bulk-memory instruction requires a passive data segment");
            }
        }

        for (index = 0; index < exports.length; index++) {
            Export value = exports[index];
            int other;
            for (other = 0; other < index; other++) {
                if (exports[other].name.equals(value.name)) {
                    throw new WasmException("duplicate export name: " + value.name);
                }
            }
            if (value.kind == 0) {
                if (value.index < 0 || value.index >= totalFunctions) {
                    throw new WasmException("function export index is out of range: " + value.name);
                }
            } else if (value.kind == 1) {
                if (!tableDeclared || value.index != 0) {
                    throw new WasmException("table export index is out of range: " + value.name);
                }
            } else if (value.kind == 2) {
                if (value.index != 0) {
                    throw new WasmException("memory export index is out of range: " + value.name);
                }
            } else if (value.kind == 3) {
                if (value.index < 0 || value.index >= globals.length) {
                    throw new WasmException("global export index is out of range: " + value.name);
                }
            } else {
                throw new WasmException("unsupported export kind " + value.kind);
            }
        }

        if (table == null) {
            table = new int[0];
        } else {
            for (index = 0; index < table.length; index++) {
                if (table[index] < -1 || table[index] >= totalFunctions) {
                    throw new WasmException("table function index is out of range");
                }
            }
        }
        if (startFunction < -1 || startFunction >= totalFunctions) {
            throw new WasmException("start function is out of range");
        }
        if (startFunction >= 0) {
            requireNoArgVoidFunction(startFunction, "start section");
        }
        requireNoArgVoidExport("update", true);
        requireNoArgVoidExport("_start", false);
        requireNoArgVoidExport("_initialize", false);
        requireNoArgVoidExport("start", false);
    }

    private void specializeNumericIntrinsicCalls() {
        int importedFunctionCount = importList.size();
        int definedIndex;
        for (definedIndex = 0; definedIndex < functionBodies.size(); definedIndex++) {
            FunctionBody body = (FunctionBody) functionBodies.elementAt(definedIndex);
            int pc;
            for (pc = 0; pc < body.instructionCount(); pc++) {
                int offset = pc * W4IR_STRIDE;
                if ((body.code[offset] & 0xffff) != 0x10) {
                    continue;
                }
                int targetIndex = body.code[offset + 1];
                int targetDefinedIndex = targetIndex - importedFunctionCount;
                if (targetDefinedIndex < 0 || targetDefinedIndex >= functionBodies.size()) {
                    continue;
                }
                int targetTypeIndex =
                        ((Integer) definedFunctionTypes.elementAt(targetDefinedIndex)).intValue();
                FuncType targetType = types[targetTypeIndex];
                if (targetType.parameters.length != 1
                        || targetType.parameters[0] != F32
                        || targetType.results.length != 1
                        || targetType.results[0] != F32) {
                    continue;
                }
                FunctionBody target =
                        (FunctionBody) functionBodies.elementAt(targetDefinedIndex);
                int intrinsicOpcode = 0;
                if (target.intrinsic == INTRINSIC_F32_FLOOR) {
                    intrinsicOpcode = W4IR_F32_FLOOR_INTRINSIC;
                } else if (target.intrinsic == INTRINSIC_F32_SIN) {
                    intrinsicOpcode = W4IR_F32_SIN_INTRINSIC;
                }
                if (intrinsicOpcode != 0) {
                    body.code[offset] =
                            (body.code[offset] & 0xffff0000)
                                    | executionOpcode(intrinsicOpcode);
                }
            }
        }
    }

    private void requireNoArgVoidExport(String name, boolean required) throws WasmException {
        int index;
        for (index = 0; index < exports.length; index++) {
            Export value = exports[index];
            if (value.name.equals(name)) {
                if (value.kind != 0) {
                    throw new WasmException("export is not a function: " + name);
                }
                requireNoArgVoidFunction(value.index, "export " + name);
                return;
            }
        }
        if (required) {
            throw new WasmException("missing function export: " + name);
        }
    }

    private void requireNoArgVoidFunction(int functionIndex, String label) throws WasmException {
        FuncType type = types[functionTypeIndices[functionIndex]];
        if (type.parameters.length != 0 || type.results.length != 0) {
            throw new WasmException(label + " must have signature () -> ()");
        }
    }

    private static final class ValidationState {
        private static final int UNKNOWN = 0;

        private final int[] values = new int[MAX_VALUE_STACK];
        private final ValidationControl[] controls =
                new ValidationControl[MAX_CONTROL_STACK];
        private final int[] functionResults;
        private int valueTop;
        private int controlTop;

        ValidationState(int[] functionResults) {
            this.functionResults = functionResults;
            controls[0] =
                    new ValidationControl(-1, new int[0], functionResults, 0);
            controlTop = 1;
        }

        int[] functionResultTypes() {
            return functionResults;
        }

        int pushControl(int kind, int[] startTypes, int[] endTypes, ByteReader reader)
                throws WasmException {
            if (controlTop >= controls.length) {
                throw reader.error("control stack exceeds runtime limit " + MAX_CONTROL_STACK);
            }
            popTypes(startTypes, reader);
            ValidationControl control =
                    new ValidationControl(kind, startTypes, endTypes, valueTop);
            controls[controlTop++] = control;
            pushTypes(startTypes, reader);
            return control.height;
        }

        void beginElse(ByteReader reader) throws WasmException {
            ValidationControl control = current(reader);
            if (control.kind != IF || control.sawElse) {
                throw reader.error("else does not match an if");
            }
            popTypes(control.endTypes, reader);
            requireControlHeight(control, reader);
            valueTop = control.height;
            control.unreachable = false;
            control.sawElse = true;
            pushTypes(control.startTypes, reader);
        }

        void endControl(ByteReader reader) throws WasmException {
            ValidationControl control = current(reader);
            if (control.kind == IF
                    && !control.sawElse
                    && !sameTypes(control.startTypes, control.endTypes)) {
                throw reader.error("if without else has incompatible result types");
            }
            popTypes(control.endTypes, reader);
            requireControlHeight(control, reader);
            valueTop = control.height;
            controls[--controlTop] = null;
            pushTypes(control.endTypes, reader);
        }

        int[] labelTypes(int depth) {
            ValidationControl control = controls[controlTop - 1 - depth];
            return control.kind == LOOP ? control.startTypes : control.endTypes;
        }

        void markUnreachable() throws WasmException {
            if (controlTop == 0) {
                throw new WasmException("unreachable appears outside a function");
            }
            ValidationControl control = controls[controlTop - 1];
            valueTop = control.height;
            control.unreachable = true;
        }

        void validateSelect(ByteReader reader) throws WasmException {
            popExpected(I32, reader);
            int right = popAny(reader);
            int left = popExpected(right, reader);
            push(right == UNKNOWN ? left : right, reader);
        }

        void validateTypedSelect(int type, ByteReader reader) throws WasmException {
            popExpected(I32, reader);
            popExpected(type, reader);
            popExpected(type, reader);
            push(type, reader);
        }

        void pushTypes(int[] types, ByteReader reader) throws WasmException {
            int index;
            for (index = 0; index < types.length; index++) {
                push(types[index], reader);
            }
        }

        void push(int type, ByteReader reader) throws WasmException {
            if (valueTop >= values.length) {
                throw reader.error("operand stack exceeds runtime limit " + MAX_VALUE_STACK);
            }
            values[valueTop++] = type;
        }

        void popTypes(int[] types, ByteReader reader) throws WasmException {
            int index;
            for (index = types.length - 1; index >= 0; index--) {
                popExpected(types[index], reader);
            }
        }

        int popExpected(int expected, ByteReader reader) throws WasmException {
            ValidationControl control = current(reader);
            if (valueTop == control.height && control.unreachable) {
                return UNKNOWN;
            }
            if (valueTop <= control.height) {
                throw reader.error("operand stack underflow");
            }
            int actual = values[--valueTop];
            if (actual != UNKNOWN && expected != UNKNOWN && actual != expected) {
                throw reader.error(
                        "operand stack type mismatch: expected "
                                + typeName(expected)
                                + ", got "
                                + typeName(actual));
            }
            return actual;
        }

        int popAny(ByteReader reader) throws WasmException {
            return popExpected(UNKNOWN, reader);
        }

        void requireComplete(ByteReader reader) throws WasmException {
            if (controlTop != 0) {
                throw reader.error("function control stack did not close");
            }
            if (valueTop != functionResults.length) {
                throw reader.error("function operand stack has the wrong result arity");
            }
            int index;
            for (index = 0; index < functionResults.length; index++) {
                if (values[index] != functionResults[index]) {
                    throw reader.error("function operand stack has the wrong result type");
                }
            }
        }

        private ValidationControl current(ByteReader reader) throws WasmException {
            if (controlTop == 0) {
                throw reader.error("instruction appears after the function end");
            }
            return controls[controlTop - 1];
        }

        private void requireControlHeight(ValidationControl control, ByteReader reader)
                throws WasmException {
            if (valueTop != control.height) {
                throw reader.error("operand stack has extra values at control boundary");
            }
        }

        private String typeName(int type) {
            if (type == I32) {
                return "i32";
            }
            if (type == I64) {
                return "i64";
            }
            if (type == F32) {
                return "f32";
            }
            if (type == F64) {
                return "f64";
            }
            return "unknown";
        }
    }

    private static final class ValidationControl {
        final int kind;
        final int[] startTypes;
        final int[] endTypes;
        final int height;
        boolean unreachable;
        boolean sawElse;

        ValidationControl(int kind, int[] startTypes, int[] endTypes, int height) {
            this.kind = kind;
            this.startTypes = startTypes;
            this.endTypes = endTypes;
            this.height = height;
        }
    }

    static final class FuncType {
        final int[] parameters;
        final int[] results;
        final int canonicalId;

        FuncType(int[] parameters, int[] results, int canonicalId) {
            this.parameters = parameters;
            this.results = results;
            this.canonicalId = canonicalId;
        }
    }

    static final class ImportedFunction {
        final String module;
        final String name;
        final int typeIndex;
        final int hostId;

        ImportedFunction(String module, String name, int typeIndex, int hostId) {
            this.module = module;
            this.name = name;
            this.typeIndex = typeIndex;
            this.hostId = hostId;
        }
    }

    static final class FunctionBody {
        private static final int PROMOTION_SWITCHES = 64;
        private static final int MAX_PROMOTED_CODE_INTS = 16384;

        final int declaredLocalCount;
        int[] code;
        final int[][] branchTables;
        final int[] branchDescriptors;
        final int[] branchDescriptorPcs;
        final int[] branchDescriptorIndices;
        final int[][] branchDescriptorTables;
        int[] branchFastSiteByPc;
        int[] branchFastTargets;
        int[] branchFastHeights;
        int[] branchFastArities;
        int[] branchFastControls;
        final long fingerprint;
        final int intrinsic;
        private final int instructionCount;
        private final W4IrFunction cachedFunction;
        private final W4IrStore cachedStore;
        private int cachedPageIndex = -1;
        private int[] cachedPage;
        private int pageSwitches;
        private boolean promotionDisabled;
        private boolean promoted;

        FunctionBody(
                int declaredLocalCount,
                Instruction[] instructions,
                int[] branchDescriptors,
                boolean extendedFusionsEnabled,
                boolean loadTeeFusionsEnabled) {
            this.declaredLocalCount = declaredLocalCount;
            code = new int[instructions.length * W4IR_STRIDE];
            instructionCount = instructions.length;
            this.branchDescriptors = branchDescriptors;
            cachedFunction = null;
            cachedStore = null;

            int tableCount = 0;
            int directBranchCount = 0;
            int index;
            for (index = 0; index < instructions.length; index++) {
                if (instructions[index].opcode == 0x0e) {
                    tableCount++;
                } else if (instructions[index].opcode == 0x0c
                        || instructions[index].opcode == 0x0d) {
                    directBranchCount++;
                }
            }
            branchTables = new int[tableCount][];
            branchDescriptorTables = new int[tableCount][];
            branchDescriptorPcs = new int[directBranchCount];
            branchDescriptorIndices = new int[directBranchCount];

            int tableIndex = 0;
            int directBranchIndex = 0;
            for (index = 0; index < instructions.length; index++) {
                Instruction instruction = instructions[index];
                int opcode = instruction.opcode;
                int codeOffset = index * W4IR_STRIDE;
                code[codeOffset] = opcode
                        | (instruction.parameterCount << 16)
                        | (instruction.resultCount << 24);
                if (opcode == BLOCK || opcode == LOOP || opcode == IF) {
                    code[codeOffset + 1] = instruction.endPc;
                    code[codeOffset + 2] = instruction.elsePc;
                } else if (opcode == 0x05) {
                    code[codeOffset + 1] = instruction.endPc;
                } else if (opcode == 0x0e) {
                    code[codeOffset + 1] = tableIndex;
                    branchTables[tableIndex] = instruction.vector;
                    branchDescriptorTables[tableIndex++] =
                            instruction.branchDescriptorVector;
                } else if (opcode >= 0x41 && opcode <= 0x44) {
                    code[codeOffset + 1] = (int) instruction.longValue;
                    code[codeOffset + 2] = (int) (instruction.longValue >>> 32);
                } else {
                    code[codeOffset + 1] = instruction.a;
                    code[codeOffset + 2] = instruction.b;
                }
                if (opcode == 0x0c || opcode == 0x0d) {
                    branchDescriptorPcs[directBranchIndex] = index;
                    branchDescriptorIndices[directBranchIndex++] =
                            instruction.branchDescriptorIndex;
                }
            }
            fingerprint = calculateFingerprint();
            if (fingerprint == F32_FLOOR_FINGERPRINT) {
                intrinsic = INTRINSIC_F32_FLOOR;
            } else if (fingerprint == F32_SIN_FINGERPRINT) {
                intrinsic = INTRINSIC_F32_SIN;
            } else {
                intrinsic = INTRINSIC_NONE;
            }
            fuseInstructions(
                    instructions,
                    extendedFusionsEnabled,
                    loadTeeFusionsEnabled);
            if (OpcodeBuildConfig.DENSE_OPCODE_DISPATCH) {
                remapExecutionOpcodes();
            }
            if (InterpreterBuildConfig.DIRECT_BRANCH_FAST_PATH) {
                buildDirectBranchFastPath();
            }
        }

        FunctionBody(W4IrFunction function, W4IrStore store) {
            declaredLocalCount = function.declaredLocalCount();
            instructionCount = function.instructionCount();
            branchTables = function.branchTables();
            branchDescriptors = function.branchDescriptors();
            branchDescriptorPcs = function.branchDescriptorPcs();
            branchDescriptorIndices = function.branchDescriptorIndices();
            branchDescriptorTables = function.branchDescriptorTables();
            fingerprint = function.fingerprint();
            intrinsic = function.intrinsic();
            code = null;
            cachedFunction = function;
            cachedStore = store;
            if (InterpreterBuildConfig.DIRECT_BRANCH_FAST_PATH) {
                buildDirectBranchFastPath();
            }
        }

        private void buildDirectBranchFastPath() {
            int siteCount = branchDescriptorPcs.length;
            if (siteCount == 0) {
                return;
            }
            int[] siteByPc = new int[instructionCount];
            int[] targets = new int[siteCount];
            int[] heights = new int[siteCount];
            int[] arities = new int[siteCount];
            int[] controls = new int[siteCount];
            int descriptorCount =
                    branchDescriptors.length / BRANCH_DESCRIPTOR_STRIDE;
            int index;
            for (index = 0; index < siteByPc.length; index++) {
                siteByPc[index] = -1;
            }
            for (index = 0; index < siteCount; index++) {
                int sitePc = branchDescriptorPcs[index];
                int descriptorIndex = branchDescriptorIndices[index];
                if (sitePc < 0
                        || sitePc >= instructionCount
                        || descriptorIndex < 0
                        || descriptorIndex >= descriptorCount) {
                    throw new WasmTrap("invalid direct branch descriptor mapping");
                }
                int offset = descriptorIndex * BRANCH_DESCRIPTOR_STRIDE;
                int targetPc =
                        branchDescriptors[offset + BRANCH_DESCRIPTOR_TARGET_PC];
                int height =
                        branchDescriptors[offset + BRANCH_DESCRIPTOR_VALUE_HEIGHT];
                int arity = branchDescriptors[offset + BRANCH_DESCRIPTOR_ARITY];
                int controlDepth =
                        branchDescriptors[
                                offset + BRANCH_DESCRIPTOR_CONTROL_DEPTH];
                int flags =
                        branchDescriptors[offset + BRANCH_DESCRIPTOR_FLAGS];
                if (targetPc < -1
                        || targetPc >= instructionCount
                        || height < 0
                        || arity < 0
                        || controlDepth < 0
                        || (flags
                                        & ~(BRANCH_DESCRIPTOR_LOOP_TARGET
                                                | BRANCH_DESCRIPTOR_FUNCTION_RETURN))
                                != 0
                        || ((flags & BRANCH_DESCRIPTOR_FUNCTION_RETURN) != 0)
                                != (targetPc == -1)
                        || ((flags & BRANCH_DESCRIPTOR_LOOP_TARGET) != 0
                                && targetPc < 0)
                        || siteByPc[sitePc] != -1) {
                    throw new WasmTrap("invalid direct branch descriptor");
                }
                siteByPc[sitePc] = index;
                targets[index] = targetPc;
                heights[index] = height;
                arities[index] = arity;
                controls[index] = controlDepth;
            }
            branchFastSiteByPc = siteByPc;
            branchFastTargets = targets;
            branchFastHeights = heights;
            branchFastArities = arities;
            branchFastControls = controls;
        }

        int instructionCount() {
            return instructionCount;
        }

        int branchDescriptorIndexAt(int pc) {
            int low = 0;
            int high = branchDescriptorPcs.length - 1;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                int candidatePc = branchDescriptorPcs[middle];
                if (candidatePc < pc) {
                    low = middle + 1;
                } else if (candidatePc > pc) {
                    high = middle - 1;
                } else {
                    return branchDescriptorIndices[middle];
                }
            }
            throw new WasmTrap("missing branch descriptor at pc " + pc);
        }

        int[] codePage(int codeOffset) {
            if (code != null) {
                return code;
            }
            int pageIndex = codeOffset / W4IrFunction.PAGE_INTS;
            if (pageIndex != cachedPageIndex) {
                pageSwitches++;
                if (!promotionDisabled && pageSwitches >= PROMOTION_SWITCHES) {
                    int[] promoted = promoteCode();
                    if (promoted != null) {
                        return promoted;
                    }
                }
                cachedPage = cachedStore.loadPage(cachedFunction, pageIndex);
                cachedPageIndex = pageIndex;
            }
            return cachedPage;
        }

        int codePageBase(int codeOffset) {
            if (code != null) {
                return 0;
            }
            return (codeOffset / W4IrFunction.PAGE_INTS) * W4IrFunction.PAGE_INTS;
        }

        boolean isPromoted() {
            return promoted;
        }

        private int[] promoteCode() {
            int codeLength = instructionCount * W4IR_STRIDE;
            if (codeLength > MAX_PROMOTED_CODE_INTS) {
                promotionDisabled = true;
                return null;
            }
            try {
                int[] materialized = new int[codeLength];
                int page;
                for (page = 0; page < cachedFunction.pageCount(); page++) {
                    int[] source = cachedStore.loadPage(cachedFunction, page);
                    int offset = page * W4IrFunction.PAGE_INTS;
                    int length = codeLength - offset;
                    if (length > source.length) {
                        length = source.length;
                    }
                    System.arraycopy(source, 0, materialized, offset, length);
                }
                code = materialized;
                promoted = true;
                cachedPage = null;
                cachedPageIndex = -1;
                return code;
            } catch (OutOfMemoryError unavailable) {
                promotionDisabled = true;
                return null;
            }
        }

        private long calculateFingerprint() {
            long result = 0xcbf29ce484222325L;
            result = fingerprintInt(result, declaredLocalCount);
            int index;
            for (index = 0; index < code.length; index++) {
                result = fingerprintInt(result, code[index]);
            }
            return result;
        }

        private void remapExecutionOpcodes() {
            int index;
            for (index = 0; index < instructionCount; index++) {
                int offset = index * W4IR_STRIDE;
                code[offset] = (code[offset] & 0xffff0000)
                        | executionOpcode(code[offset] & 0xffff);
            }
        }

        private long fingerprintInt(long hash, int value) {
            int shift;
            for (shift = 0; shift < 32; shift += 8) {
                hash ^= (value >>> shift) & 0xff;
                hash *= 0x100000001b3L;
            }
            return hash;
        }

        private void fuseInstructions(
                Instruction[] instructions,
                boolean extendedFusionsEnabled,
                boolean loadTeeFusionsEnabled) {
            boolean[] branchTarget = findBranchTargets(instructions);
            boolean useExtendedFusions = extendedFusionsEnabled
                    && hasExtendedFloatFusionCandidate(instructions, branchTarget);
            boolean useIntegerExtendedFusions =
                    extendedFusionsEnabled;
            int index;
            for (index = 0; index + 2 < instructions.length; index++) {
                if (branchTarget[index + 1] || branchTarget[index + 2]) {
                    continue;
                }
                int first = opcodeAt(index);
                int second = opcodeAt(index + 1);
                int third = opcodeAt(index + 2);
                int replacement = 0;
                if (first == 0x20 && second == 0x20 && third == 0x94) {
                    replacement = W4IR_LOCAL_LOCAL_F32_MUL;
                } else if (first == 0x20 && second == 0x43 && third == 0x94) {
                    replacement = W4IR_LOCAL_F32_CONST_MUL;
                } else if (first == 0x43 && second == 0x94 && third == 0x92) {
                    replacement = W4IR_F32_CONST_MUL_ADD;
                } else if (first == 0x20 && second == 0x20 && third == 0x71) {
                    replacement = W4IR_LOCAL_LOCAL_I32_AND;
                } else if (first == 0x20 && second == 0x20 && third == 0x6a) {
                    replacement = W4IR_LOCAL_LOCAL_I32_ADD;
                } else if (first == 0x20 && second == 0x41 && third == 0x6a) {
                    replacement = W4IR_LOCAL_I32_CONST_ADD;
                } else if (first == 0x20 && second == 0x41 && third == 0x71) {
                    replacement = W4IR_LOCAL_I32_CONST_AND;
                }
                if (replacement != 0) {
                    replaceTriple(index, replacement);
                }
            }

            for (index = 0; index + 1 < instructions.length; index++) {
                if (branchTarget[index + 1]) {
                    continue;
                }
                int first = opcodeAt(index);
                int second = opcodeAt(index + 1);
                int replacement = 0;
                if (first == 0x20 && second == 0x20) {
                    replacement = W4IR_LOCAL_LOCAL;
                } else if (first == 0x20 && second == 0x41) {
                    replacement = W4IR_LOCAL_I32_CONST;
                } else if (first == 0x20 && second == 0x43) {
                    replacement = W4IR_LOCAL_F32_CONST;
                } else if (first == 0x43 && second == 0x94) {
                    replacement = W4IR_F32_MUL_CONST;
                } else if (first == 0x94 && second == 0x92) {
                    replacement = W4IR_F32_MUL_ADD;
                } else if (first == 0x41 && second == 0x6a) {
                    replacement = W4IR_I32_ADD_CONST;
                } else if (first == 0x41 && second == 0x71) {
                    replacement = W4IR_I32_AND_CONST;
                } else if (first == 0x21 && second == 0x20) {
                    replacement = W4IR_LOCAL_SET_GET;
                } else if (first == 0x43 && second == 0x21) {
                    replacement = W4IR_LOCAL_SET_F32_CONST;
                } else if (first == 0x94 && second == 0x20) {
                    replacement = W4IR_F32_MUL_LOCAL;
                } else if (loadTeeFusionsEnabled
                        && first == 0x28
                        && second == 0x22) {
                    replacement = W4IR_I32_LOAD_LOCAL_TEE;
                }
                if (replacement != 0) {
                    replacePair(index, replacement);
                }
            }

            for (index = 0; index < instructions.length; index++) {
                int first = opcodeAt(index);
                int firstOffset = index * W4IR_STRIDE;
                if (first == W4IR_LOCAL_I32_CONST_ADD
                        && index + 3 < instructions.length
                        && !branchTarget[index + 3]
                        && opcodeAt(index + 3) == 0x21) {
                    int secondOffset = firstOffset + 3 * W4IR_STRIDE;
                    int source = code[firstOffset + 1];
                    int target = code[secondOffset + 1];
                    code[firstOffset] = W4IR_LOCAL_I32_CONST_ADD_SET;
                    code[firstOffset + 1] = (source << 16) | target;
                    clearInstruction(secondOffset);
                } else if (first == W4IR_LOCAL_LOCAL
                        && index + 2 < instructions.length
                        && !branchTarget[index + 2]
                        && opcodeAt(index + 2) == 0x38) {
                    int secondOffset = firstOffset + 2 * W4IR_STRIDE;
                    int addressLocal = code[firstOffset + 1];
                    int valueLocal = code[firstOffset + 2];
                    code[firstOffset] = W4IR_LOCAL_LOCAL_F32_STORE;
                    code[firstOffset + 1] = (addressLocal << 16) | valueLocal;
                    code[firstOffset + 2] = code[secondOffset + 1];
                    clearInstruction(secondOffset);
                } else if (useIntegerExtendedFusions
                        && first == W4IR_LOCAL_SET_GET
                        && index + 2 < instructions.length
                        && !branchTarget[index + 2]
                        && opcodeAt(index + 2) == 0x20) {
                    int secondOffset = firstOffset + 2 * W4IR_STRIDE;
                    int setTarget = code[firstOffset + 1];
                    int firstSource = code[firstOffset + 2];
                    int secondSource = code[secondOffset + 1];
                    code[firstOffset] = W4IR_LOCAL_SET_LOCAL_LOCAL;
                    code[firstOffset + 1] = (setTarget << 16) | firstSource;
                    code[firstOffset + 2] = secondSource;
                    clearInstruction(secondOffset);
                } else if (useIntegerExtendedFusions
                        && first == W4IR_LOCAL_I32_CONST
                        && index + 2 < instructions.length
                        && !branchTarget[index + 2]
                        && opcodeAt(index + 2) == 0x46) {
                    int secondOffset = firstOffset + 2 * W4IR_STRIDE;
                    code[firstOffset] = W4IR_LOCAL_I32_CONST_EQ;
                    clearInstruction(secondOffset);
                } else if (useIntegerExtendedFusions
                        && first == 0x20
                        && index + 1 < instructions.length
                        && !branchTarget[index + 1]
                        && opcodeAt(index + 1) == W4IR_LOCAL_I32_CONST_AND) {
                    int secondOffset = firstOffset + W4IR_STRIDE;
                    int firstSource = code[firstOffset + 1];
                    int secondSource = code[secondOffset + 1];
                    code[firstOffset] = W4IR_LOCAL_LOCAL_I32_CONST_AND;
                    code[firstOffset + 1] = (firstSource << 16) | secondSource;
                    code[firstOffset + 2] = code[secondOffset + 2];
                    clearInstruction(secondOffset);
                } else if (useExtendedFusions
                        && first == W4IR_LOCAL_LOCAL
                        && index + 2 < instructions.length
                        && !branchTarget[index + 2]
                        && opcodeAt(index + 2) == 0x95) {
                    int secondOffset = firstOffset + 2 * W4IR_STRIDE;
                    code[firstOffset] = W4IR_LOCAL_LOCAL_F32_DIV;
                    clearInstruction(secondOffset);
                } else if (useExtendedFusions
                        && first == 0x21
                        && index + 1 < instructions.length
                        && !branchTarget[index + 1]
                        && opcodeAt(index + 1) == W4IR_LOCAL_SET_F32_CONST) {
                    int secondOffset = firstOffset + W4IR_STRIDE;
                    int firstTarget = code[firstOffset + 1];
                    int secondTarget = code[secondOffset + 2];
                    code[firstOffset] = W4IR_LOCAL_SET_F32_CONST_SET;
                    code[firstOffset + 1] = (firstTarget << 16) | secondTarget;
                    code[firstOffset + 2] = code[secondOffset + 1];
                    clearInstruction(secondOffset);
                } else if (useExtendedFusions
                        && first == W4IR_LOCAL_LOCAL
                        && index + 2 < instructions.length
                        && !branchTarget[index + 2]
                        && opcodeAt(index + 2) == 0x2a) {
                    int secondOffset = firstOffset + 2 * W4IR_STRIDE;
                    int firstSource = code[firstOffset + 1];
                    int secondSource = code[firstOffset + 2];
                    code[firstOffset] = W4IR_LOCAL_LOCAL_F32_LOAD;
                    code[firstOffset + 1] = (firstSource << 16) | secondSource;
                    code[firstOffset + 2] = code[secondOffset + 1];
                    clearInstruction(secondOffset);
                } else if (useExtendedFusions
                        && first == 0x22
                        && index + 1 < instructions.length
                        && !branchTarget[index + 1]
                        && opcodeAt(index + 1) == W4IR_F32_MUL_ADD) {
                    int secondOffset = firstOffset + W4IR_STRIDE;
                    code[firstOffset] = W4IR_LOCAL_TEE_F32_MUL_ADD;
                    clearInstruction(secondOffset);
                } else if (useExtendedFusions
                        && first == 0x20
                        && index + 1 < instructions.length
                        && !branchTarget[index + 1]
                        && opcodeAt(index + 1) == W4IR_F32_MUL_ADD) {
                    int secondOffset = firstOffset + W4IR_STRIDE;
                    code[firstOffset] = W4IR_LOCAL_F32_MUL_ADD;
                    clearInstruction(secondOffset);
                } else if (useExtendedFusions
                        && first == W4IR_LOCAL_LOCAL_F32_MUL
                        && index + 3 < instructions.length
                        && !branchTarget[index + 3]
                        && opcodeAt(index + 3) == 0x92) {
                    int secondOffset = firstOffset + 3 * W4IR_STRIDE;
                    code[firstOffset] = W4IR_LOCAL_LOCAL_F32_MUL_ADD;
                    clearInstruction(secondOffset);
                }
            }

            if (!useExtendedFusions && !useIntegerExtendedFusions) {
                return;
            }
            for (index = 0; index < instructions.length; index++) {
                int first = opcodeAt(index);
                int firstOffset = index * W4IR_STRIDE;
                if (useIntegerExtendedFusions
                        && first == W4IR_LOCAL_I32_CONST_EQ
                        && index + 3 < instructions.length
                        && !branchTarget[index + 3]
                        && opcodeAt(index + 3) == 0x0d) {
                    int secondOffset = firstOffset + 3 * W4IR_STRIDE;
                    int local = code[firstOffset + 1];
                    code[firstOffset] = W4IR_LOCAL_I32_CONST_EQ_BR_IF | (local << 16);
                    code[firstOffset + 1] = code[firstOffset + 2];
                    code[firstOffset + 2] = code[secondOffset + 1];
                    clearInstruction(secondOffset);
                } else if (useExtendedFusions
                        && first == W4IR_LOCAL_TEE_F32_MUL_ADD
                        && index + 3 < instructions.length
                        && !branchTarget[index + 3]
                        && opcodeAt(index + 3) == W4IR_LOCAL_SET_LOCAL_LOCAL) {
                    int secondOffset = firstOffset + 3 * W4IR_STRIDE;
                    int teeTarget = code[firstOffset + 1];
                    code[firstOffset] =
                            W4IR_LOCAL_TEE_MUL_ADD_SET_LOCAL_LOCAL | (teeTarget << 16);
                    code[firstOffset + 1] = code[secondOffset + 1];
                    code[firstOffset + 2] = code[secondOffset + 2];
                    clearInstruction(secondOffset);
                } else if (useExtendedFusions
                        && first == W4IR_LOCAL_F32_MUL_ADD
                        && index + 3 < instructions.length
                        && !branchTarget[index + 3]
                        && opcodeAt(index + 3) == 0x21) {
                    int secondOffset = firstOffset + 3 * W4IR_STRIDE;
                    int multiplySource = code[firstOffset + 1];
                    code[firstOffset] = W4IR_LOCAL_MUL_ADD_SET | (multiplySource << 16);
                    code[firstOffset + 1] = code[secondOffset + 1];
                    code[firstOffset + 2] = 0;
                    clearInstruction(secondOffset);
                } else if (useExtendedFusions
                        && first == 0x2a
                        && index + 1 < instructions.length
                        && !branchTarget[index + 1]
                        && opcodeAt(index + 1) == W4IR_LOCAL_F32_MUL_ADD) {
                    int secondOffset = firstOffset + W4IR_STRIDE;
                    code[firstOffset] = W4IR_F32_LOAD_LOCAL_MUL_ADD;
                    code[firstOffset + 2] = code[secondOffset + 1];
                    clearInstruction(secondOffset);
                } else if (useExtendedFusions
                        && first == W4IR_LOCAL_I32_CONST_ADD_SET
                        && index + 4 < instructions.length
                        && !branchTarget[index + 4]
                        && opcodeAt(index + 4) == 0x0c) {
                    int secondOffset = firstOffset + 4 * W4IR_STRIDE;
                    int branchDepth = code[secondOffset + 1];
                    code[firstOffset] = W4IR_LOCAL_ADD_SET_BR | (branchDepth << 16);
                    clearInstruction(secondOffset);
                } else if (useExtendedFusions
                        && first == 0x21
                        && index + 1 < instructions.length
                        && !branchTarget[index + 1]
                        && opcodeAt(index + 1) == W4IR_LOCAL_I32_CONST_ADD_SET) {
                    int secondOffset = firstOffset + W4IR_STRIDE;
                    int setTarget = code[firstOffset + 1];
                    code[firstOffset] = W4IR_LOCAL_SET_ADD_SET | (setTarget << 16);
                    code[firstOffset + 1] = code[secondOffset + 1];
                    code[firstOffset + 2] = code[secondOffset + 2];
                    clearInstruction(secondOffset);
                } else if (useExtendedFusions
                        && first == W4IR_LOCAL_SET_LOCAL_LOCAL
                        && index + 3 < instructions.length
                        && !branchTarget[index + 3]
                        && opcodeAt(index + 3) == 0x2a) {
                    int secondOffset = firstOffset + 3 * W4IR_STRIDE;
                    int setTarget = code[firstOffset + 1] >>> 16;
                    int firstSource = code[firstOffset + 1] & 0xffff;
                    int addressSource = code[firstOffset + 2];
                    code[firstOffset] =
                            W4IR_LOCAL_SET_LOCAL_LOCAL_F32_LOAD | (setTarget << 16);
                    code[firstOffset + 1] = (firstSource << 16) | addressSource;
                    code[firstOffset + 2] = code[secondOffset + 1];
                    clearInstruction(secondOffset);
                } else if (useExtendedFusions
                        && first == W4IR_LOCAL_LOCAL_F32_LOAD
                        && index + 3 < instructions.length
                        && !branchTarget[index + 3]
                        && opcodeAt(index + 3) == 0x20) {
                    int secondOffset = firstOffset + 3 * W4IR_STRIDE;
                    int thirdSource = code[secondOffset + 1];
                    code[firstOffset] =
                            W4IR_LOCAL_LOCAL_F32_LOAD_LOCAL | (thirdSource << 16);
                    clearInstruction(secondOffset);
                } else if (useExtendedFusions
                        && first == 0x2a
                        && index + 1 < instructions.length
                        && !branchTarget[index + 1]
                        && opcodeAt(index + 1) == 0x8c) {
                    int secondOffset = firstOffset + W4IR_STRIDE;
                    code[firstOffset] = W4IR_F32_LOAD_NEG;
                    clearInstruction(secondOffset);
                } else if (useExtendedFusions
                        && first == 0x2a
                        && index + 1 < instructions.length
                        && !branchTarget[index + 1]
                        && opcodeAt(index + 1) == 0x95) {
                    int secondOffset = firstOffset + W4IR_STRIDE;
                    code[firstOffset] = W4IR_F32_LOAD_DIV;
                    clearInstruction(secondOffset);
                } else if (useExtendedFusions
                        && first == 0x0d
                        && index + 1 < instructions.length
                        && !branchTarget[index + 1]
                        && opcodeAt(index + 1) == W4IR_LOCAL_I32_CONST) {
                    int secondOffset = firstOffset + W4IR_STRIDE;
                    int local = code[secondOffset + 1];
                    code[firstOffset] = W4IR_BR_IF_LOCAL_I32_CONST | (local << 16);
                    code[firstOffset + 2] = code[firstOffset + 1];
                    code[firstOffset + 1] = code[secondOffset + 2];
                    clearInstruction(secondOffset);
                }
            }

            if (!useExtendedFusions) {
                return;
            }
            for (index = 0; index < instructions.length; index++) {
                int first = opcodeAt(index);
                int firstOffset = index * W4IR_STRIDE;
                if (first == 0x95
                        && index + 1 < instructions.length
                        && !branchTarget[index + 1]
                        && opcodeAt(index + 1)
                                == W4IR_LOCAL_TEE_MUL_ADD_SET_LOCAL_LOCAL) {
                    int secondOffset = firstOffset + W4IR_STRIDE;
                    code[firstOffset] =
                            W4IR_F32_DIV_TEE_MUL_ADD_SET_LOCAL_LOCAL
                                    | (code[secondOffset] & 0xffff0000);
                    code[firstOffset + 1] = code[secondOffset + 1];
                    code[firstOffset + 2] = code[secondOffset + 2];
                    clearInstruction(secondOffset);
                } else if (first == W4IR_F32_LOAD_LOCAL_MUL_ADD
                        && index + 4 < instructions.length
                        && !branchTarget[index + 4]
                        && opcodeAt(index + 4) == 0x21) {
                    int secondOffset = firstOffset + 4 * W4IR_STRIDE;
                    int setTarget = code[secondOffset + 1];
                    code[firstOffset] =
                            W4IR_F32_LOAD_LOCAL_MUL_ADD_SET | (setTarget << 16);
                    clearInstruction(secondOffset);
                }
            }

            for (index = 0; index < instructions.length; index++) {
                int first = opcodeAt(index);
                int firstOffset = index * W4IR_STRIDE;
                if (first == W4IR_LOCAL_LOCAL_F32_STORE
                        && index + 6 < instructions.length
                        && !branchTarget[index + 3]
                        && !branchTarget[index + 6]
                        && opcodeAt(index + 3) == W4IR_LOCAL_LOCAL_F32_STORE
                        && opcodeAt(index + 6) == W4IR_LOCAL_LOCAL_F32_STORE) {
                    int secondOffset = firstOffset + 3 * W4IR_STRIDE;
                    int thirdOffset = firstOffset + 6 * W4IR_STRIDE;
                    int addressLocal = code[firstOffset + 1] >>> 16;
                    int secondAddressLocal = code[secondOffset + 1] >>> 16;
                    int thirdAddressLocal = code[thirdOffset + 1] >>> 16;
                    int baseOffset = code[firstOffset + 2];
                    if (addressLocal == secondAddressLocal
                            && addressLocal == thirdAddressLocal
                            && baseOffset >= 0
                            && baseOffset <= 65527
                            && code[secondOffset + 2] == baseOffset + 4
                            && code[thirdOffset + 2] == baseOffset + 8) {
                        int firstValueLocal = code[firstOffset + 1] & 0xffff;
                        int secondValueLocal = code[secondOffset + 1] & 0xffff;
                        int thirdValueLocal = code[thirdOffset + 1] & 0xffff;
                        code[firstOffset] =
                                W4IR_LOCAL_TRIPLE_F32_STORE | (addressLocal << 16);
                        code[firstOffset + 1] =
                                (firstValueLocal << 16) | secondValueLocal;
                        code[firstOffset + 2] =
                                (thirdValueLocal << 16) | baseOffset;
                        clearInstruction(secondOffset);
                        clearInstruction(thirdOffset);
                    }
                } else if (first == W4IR_LOCAL_LOCAL_F32_MUL
                        && index + 7 < instructions.length
                        && !branchTarget[index + 3]
                        && !branchTarget[index + 7]
                        && opcodeAt(index + 3) == W4IR_LOCAL_LOCAL_F32_MUL_ADD
                        && opcodeAt(index + 7) == 0x22) {
                    int secondOffset = firstOffset + 3 * W4IR_STRIDE;
                    int thirdOffset = firstOffset + 7 * W4IR_STRIDE;
                    int firstSource = code[firstOffset + 1];
                    int secondSource = code[firstOffset + 2];
                    int thirdSource = code[secondOffset + 1];
                    int fourthSource = code[secondOffset + 2];
                    int teeTarget = code[thirdOffset + 1];
                    if (firstSource >= 0
                            && firstSource <= 0xffff
                            && secondSource >= 0
                            && secondSource <= 0xffff
                            && thirdSource >= 0
                            && thirdSource <= 0xffff
                            && fourthSource >= 0
                            && fourthSource <= 0xffff
                            && teeTarget >= 0
                            && teeTarget <= 0xffff) {
                        code[firstOffset] =
                                W4IR_LOCAL4_F32_MUL_ADD_TEE | (firstSource << 16);
                        code[firstOffset + 1] = (secondSource << 16) | thirdSource;
                        code[firstOffset + 2] = (fourthSource << 16) | teeTarget;
                        clearInstruction(secondOffset);
                        clearInstruction(thirdOffset);
                    }
                } else if (first == W4IR_F32_LOAD_NEG
                        && index + 10 < instructions.length
                        && !branchTarget[index + 2]
                        && !branchTarget[index + 6]
                        && !branchTarget[index + 7]
                        && !branchTarget[index + 8]
                        && !branchTarget[index + 9]
                        && opcodeAt(index + 2) == W4IR_LOCAL_LOCAL_I32_CONST_AND
                        && opcodeAt(index + 6) == 0x41
                        && opcodeAt(index + 7) == 0x74
                        && opcodeAt(index + 8) == 0x72
                        && opcodeAt(index + 9) == W4IR_F32_LOAD_DIV) {
                    int indexOffset = firstOffset + 2 * W4IR_STRIDE;
                    int shiftOffset = firstOffset + 6 * W4IR_STRIDE;
                    int shiftInstructionOffset = firstOffset + 7 * W4IR_STRIDE;
                    int orOffset = firstOffset + 8 * W4IR_STRIDE;
                    int divideOffset = firstOffset + 9 * W4IR_STRIDE;
                    int loadOffset = code[firstOffset + 1];
                    int mask = code[indexOffset + 2];
                    int shift = code[shiftOffset + 1];
                    if (loadOffset == code[divideOffset + 1]
                            && loadOffset >= 0
                            && loadOffset <= 0xffff
                            && mask >= 0
                            && mask <= 0xffff
                            && shift >= 0
                            && shift <= 31) {
                        code[firstOffset] =
                                W4IR_F32_LOAD_NEG_INDEX_DIV | (loadOffset << 16);
                        code[firstOffset + 1] = code[indexOffset + 1];
                        code[firstOffset + 2] = (mask << 16) | shift;
                        clearInstruction(indexOffset);
                        clearInstruction(shiftOffset);
                        clearInstruction(shiftInstructionOffset);
                        clearInstruction(orOffset);
                        clearInstruction(divideOffset);
                    }
                }
            }

            for (index = 0; index < instructions.length; index++) {
                int first = opcodeAt(index);
                int firstOffset = index * W4IR_STRIDE;
                if (first == W4IR_LOCAL_TRIPLE_F32_STORE
                        && index + 9 < instructions.length
                        && !branchTarget[index + 9]
                        && opcodeAt(index + 9) == W4IR_LOCAL_LOCAL_F32_LOAD_LOCAL) {
                    int secondOffset = firstOffset + 9 * W4IR_STRIDE;
                    int storeAddress = code[firstOffset] >>> 16;
                    int firstValue = code[firstOffset + 1] >>> 16;
                    int secondValue = code[firstOffset + 1] & 0xffff;
                    int thirdValue = code[firstOffset + 2] >>> 16;
                    int storeOffset = code[firstOffset + 2] & 0xffff;
                    int thirdSource = code[secondOffset] >>> 16;
                    int firstSource = code[secondOffset + 1] >>> 16;
                    int loadAddress = code[secondOffset + 1] & 0xffff;
                    int loadOffset = code[secondOffset + 2];
                    if (storeAddress <= 0xff
                            && firstValue <= 0xff
                            && secondValue <= 0xff
                            && thirdValue <= 0xff
                            && thirdSource <= 0xff
                            && firstSource <= 0xff
                            && loadAddress <= 0xff
                            && storeOffset <= 0xff
                            && loadOffset >= 0
                            && loadOffset <= 0xff) {
                        code[firstOffset] = W4IR_TRIPLE_F32_STORE_LOCAL_LOAD_LOCAL
                                | (((storeAddress << 8) | firstValue) << 16);
                        code[firstOffset + 1] = (secondValue << 24)
                                | (thirdValue << 16)
                                | (firstSource << 8)
                                | loadAddress;
                        code[firstOffset + 2] = (thirdSource << 24)
                                | (storeOffset << 16)
                                | (loadOffset << 8);
                        clearInstruction(secondOffset);
                    }
                } else if (first == W4IR_LOCAL_TEE_MUL_ADD_SET_LOCAL_LOCAL
                        && index + 6 < instructions.length
                        && !branchTarget[index + 6]
                        && opcodeAt(index + 6) == W4IR_F32_LOAD_LOCAL_MUL_ADD) {
                    int secondOffset = firstOffset + 6 * W4IR_STRIDE;
                    int teeTarget = code[firstOffset] >>> 16;
                    int setTarget = code[firstOffset + 1] >>> 16;
                    int firstSource = code[firstOffset + 1] & 0xffff;
                    int secondSource = code[firstOffset + 2];
                    int multiplySource = code[secondOffset + 2];
                    if (teeTarget <= 0xff
                            && setTarget <= 0xff
                            && firstSource <= 0xff
                            && secondSource >= 0
                            && secondSource <= 0xff
                            && multiplySource >= 0
                            && multiplySource <= 0xff) {
                        code[firstOffset] = W4IR_LOCAL_TEE_MUL_ADD_SET_LOAD_MUL_ADD
                                | (((teeTarget << 8) | setTarget) << 16);
                        code[firstOffset + 1] = (firstSource << 24)
                                | (secondSource << 16)
                                | (multiplySource << 8);
                        code[firstOffset + 2] = code[secondOffset + 1];
                        clearInstruction(secondOffset);
                    }
                } else if (first == W4IR_LOCAL_SET_ADD_SET
                        && index + 5 < instructions.length
                        && !branchTarget[index + 5]
                        && opcodeAt(index + 5) == W4IR_LOCAL_I32_CONST_ADD_SET) {
                    int secondOffset = firstOffset + 5 * W4IR_STRIDE;
                    int firstTarget = code[firstOffset] >>> 16;
                    int firstSource = code[firstOffset + 1] >>> 16;
                    int secondTarget = code[firstOffset + 1] & 0xffff;
                    int secondSource = code[secondOffset + 1] >>> 16;
                    int thirdTarget = code[secondOffset + 1] & 0xffff;
                    int firstConstant = code[firstOffset + 2];
                    int secondConstant = code[secondOffset + 2];
                    if (firstTarget <= 0xff
                            && firstSource <= 0xff
                            && secondTarget <= 0xff
                            && secondSource <= 0xff
                            && thirdTarget <= 0xff
                            && firstConstant >= Short.MIN_VALUE
                            && firstConstant <= Short.MAX_VALUE
                            && secondConstant >= Short.MIN_VALUE
                            && secondConstant <= Short.MAX_VALUE) {
                        code[firstOffset] = W4IR_LOCAL_SET_DUAL_ADD_SET
                                | (((firstTarget << 8) | firstSource) << 16);
                        code[firstOffset + 1] = (secondTarget << 24)
                                | (secondSource << 16)
                                | (thirdTarget << 8);
                        code[firstOffset + 2] = ((firstConstant & 0xffff) << 16)
                                | (secondConstant & 0xffff);
                        clearInstruction(secondOffset);
                    }
                }
            }

            for (index = 1; index + 47 < instructions.length; index++) {
                if (!branchTarget[index]
                        || opcodeAt(index - 1) != LOOP
                        || opcodeAt(index) != W4IR_LOCAL_I32_CONST_EQ_BR_IF
                        || opcodeAt(index + 4)
                                != W4IR_TRIPLE_F32_STORE_LOCAL_LOAD_LOCAL
                        || opcodeAt(index + 17) != W4IR_F32_LOAD_NEG_INDEX_DIV
                        || opcodeAt(index + 28)
                                != W4IR_LOCAL_TEE_MUL_ADD_SET_LOAD_MUL_ADD
                        || opcodeAt(index + 38) != W4IR_LOCAL_SET_DUAL_ADD_SET
                        || opcodeAt(index + 47) != W4IR_LOCAL_ADD_SET_BR) {
                    continue;
                }
                int cursor;
                boolean hasInteriorTarget = false;
                for (cursor = index + 1; cursor <= index + 47; cursor++) {
                    if (branchTarget[cursor]) {
                        hasInteriorTarget = true;
                        break;
                    }
                }
                if (hasInteriorTarget) {
                    continue;
                }
                int firstOffset = index * W4IR_STRIDE;
                int backedgeOffset = (index + 47) * W4IR_STRIDE;
                int compareLocal = code[firstOffset] >>> 16;
                int exitDepth = code[firstOffset + 2];
                int backedgeDepth = code[backedgeOffset] >>> 16;
                int backedgeSource = code[backedgeOffset + 1] >>> 16;
                int backedgeTarget = code[backedgeOffset + 1] & 0xffff;
                if (exitDepth > 0
                        && backedgeDepth == 0
                        && backedgeSource == compareLocal
                        && backedgeTarget == compareLocal) {
                    code[firstOffset] = W4IR_COUNTED_F32_TRACE | (compareLocal << 16);
                }
            }
        }

        private boolean hasExtendedFloatFusionCandidate(
                Instruction[] instructions, boolean[] branchTarget) {
            int index;
            for (index = 0; index < instructions.length; index++) {
                int first = instructions[index].opcode;
                if (first == 0x20) {
                    if (straightLineOpcode(instructions, branchTarget, index + 1, 0x20)
                            && (straightLineOpcode(
                                            instructions, branchTarget, index + 2, 0x95)
                                    || straightLineOpcode(
                                            instructions, branchTarget, index + 2, 0x2a))) {
                        return true;
                    }
                    if (straightLineOpcode(instructions, branchTarget, index + 1, 0x94)
                            && straightLineOpcode(
                                    instructions, branchTarget, index + 2, 0x92)) {
                        return true;
                    }
                    if (straightLineOpcode(instructions, branchTarget, index + 1, 0x20)
                            && straightLineOpcode(
                                    instructions, branchTarget, index + 2, 0x94)
                            && straightLineOpcode(
                                    instructions, branchTarget, index + 3, 0x92)) {
                        return true;
                    }
                    if (isTripleF32StoreCandidate(instructions, branchTarget, index)) {
                        return true;
                    }
                } else if (first == 0x21) {
                    if (straightLineOpcode(instructions, branchTarget, index + 1, 0x43)
                            && straightLineOpcode(
                                    instructions, branchTarget, index + 2, 0x21)) {
                        return true;
                    }
                } else if (first == 0x22) {
                    if (straightLineOpcode(instructions, branchTarget, index + 1, 0x94)
                            && straightLineOpcode(
                                    instructions, branchTarget, index + 2, 0x92)) {
                        return true;
                    }
                } else if (first == 0x2a) {
                    if (straightLineOpcode(instructions, branchTarget, index + 1, 0x8c)
                            || straightLineOpcode(
                                    instructions, branchTarget, index + 1, 0x95)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean isTripleF32StoreCandidate(
                Instruction[] instructions, boolean[] branchTarget, int index) {
            if (!straightLineOpcode(instructions, branchTarget, index + 1, 0x20)
                    || !straightLineOpcode(instructions, branchTarget, index + 2, 0x38)
                    || !straightLineOpcode(instructions, branchTarget, index + 3, 0x20)
                    || !straightLineOpcode(instructions, branchTarget, index + 4, 0x20)
                    || !straightLineOpcode(instructions, branchTarget, index + 5, 0x38)
                    || !straightLineOpcode(instructions, branchTarget, index + 6, 0x20)
                    || !straightLineOpcode(instructions, branchTarget, index + 7, 0x20)
                    || !straightLineOpcode(instructions, branchTarget, index + 8, 0x38)) {
                return false;
            }
            int addressLocal = instructions[index].a;
            int baseOffset = instructions[index + 2].a;
            return addressLocal == instructions[index + 3].a
                    && addressLocal == instructions[index + 6].a
                    && baseOffset >= 0
                    && baseOffset <= 65527
                    && instructions[index + 5].a == baseOffset + 4
                    && instructions[index + 8].a == baseOffset + 8;
        }

        private boolean straightLineOpcode(
                Instruction[] instructions,
                boolean[] branchTarget,
                int index,
                int expectedOpcode) {
            return index < instructions.length
                    && !branchTarget[index]
                    && instructions[index].opcode == expectedOpcode;
        }

        private boolean[] findBranchTargets(Instruction[] instructions) {
            boolean[] result = new boolean[instructions.length];
            result[0] = true;
            int index;
            for (index = 0; index < instructions.length; index++) {
                Instruction instruction = instructions[index];
                if (instruction.opcode == LOOP && index + 1 < result.length) {
                    result[index + 1] = true;
                }
                if ((instruction.opcode == BLOCK
                                || instruction.opcode == LOOP
                                || instruction.opcode == IF)
                        && instruction.endPc + 1 < result.length) {
                    result[instruction.endPc + 1] = true;
                }
                if (instruction.opcode == IF
                        && instruction.elsePc >= 0
                        && instruction.elsePc + 1 < result.length) {
                    result[instruction.elsePc + 1] = true;
                }
            }
            return result;
        }

        private int opcodeAt(int instructionIndex) {
            return code[instructionIndex * W4IR_STRIDE] & 0xffff;
        }

        private void replaceTriple(int instructionIndex, int opcode) {
            int first = instructionIndex * W4IR_STRIDE;
            int second = first + W4IR_STRIDE;
            code[first] = opcode;
            code[first + 2] = code[second + 1];
            clearInstruction(second);
            clearInstruction(second + W4IR_STRIDE);
        }

        private void replacePair(int instructionIndex, int opcode) {
            int first = instructionIndex * W4IR_STRIDE;
            int second = first + W4IR_STRIDE;
            code[first] = opcode;
            code[first + 2] = code[second + 1];
            clearInstruction(second);
        }

        private void clearInstruction(int codeOffset) {
            code[codeOffset] = 0x01;
            code[codeOffset + 1] = 0;
            code[codeOffset + 2] = 0;
        }
    }

    private static final class ObjectList {
        private Object[] values = new Object[8];
        private int size;

        void addElement(Object value) {
            if (size == values.length) {
                Object[] grown = new Object[values.length << 1];
                System.arraycopy(values, 0, grown, 0, size);
                values = grown;
            }
            values[size++] = value;
        }

        Object elementAt(int index) {
            if (index < 0 || index >= size) {
                throw new ArrayIndexOutOfBoundsException(index);
            }
            return values[index];
        }

        void removeElementAt(int index) {
            if (index < 0 || index >= size) {
                throw new ArrayIndexOutOfBoundsException(index);
            }
            int moved = size - index - 1;
            if (moved > 0) {
                System.arraycopy(values, index + 1, values, index, moved);
            }
            values[--size] = null;
        }

        void copyInto(Object[] target) {
            System.arraycopy(values, 0, target, 0, size);
        }

        int size() {
            return size;
        }
    }

    static final class Instruction {
        int opcode;
        int a;
        int b;
        long longValue;
        int[] vector;
        int parameterCount;
        int resultCount;
        int[] parameterTypes;
        int[] resultTypes;
        int controlHeight;
        int branchDescriptorIndex = -1;
        int[] branchDescriptorVector;
        int elsePc = -1;
        int endPc = -1;
        boolean functionEnd;

        Instruction(int opcode) {
            this.opcode = opcode;
        }
    }

    private static final class Global {
        final int type;
        final boolean mutable;
        final long initialValue;

        Global(int type, boolean mutable, long initialValue) {
            this.type = type;
            this.mutable = mutable;
            this.initialValue = initialValue;
        }
    }

    static final class Export {
        final String name;
        final int kind;
        final int index;

        Export(String name, int kind, int index) {
            this.name = name;
            this.kind = kind;
            this.index = index;
        }
    }
}
