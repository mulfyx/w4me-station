package w4me.wasm;

public final class WasmInterpreter {
    private static final int PROFILE_OPCODE_LIMIT = 0x10000;
    private static final int PROFILE_MAX_COMPACT_DISPATCHES = 32;
    private static final int PROFILE_MAX_COMPACT_INSTRUCTIONS = 512;
    public static final int COMPACT_BREAK_END = 0;
    public static final int COMPACT_BREAK_BRANCH_TARGET = 1;
    public static final int COMPACT_BREAK_INELIGIBLE_OPCODE = 2;
    public static final int COMPACT_BREAK_INVALID_SPAN = 3;
    public static final int COMPACT_BREAK_DISPATCH_LIMIT = 4;
    public static final int COMPACT_BREAK_REASON_COUNT = 5;
    private static final int VALUE_STACK_LIMIT = 4096;
    private static final int CONTROL_STACK_LIMIT = 512;
    private static final int CALL_STACK_LIMIT = 64;
    private static final int COMPACT_HOT_INVOCATION_THRESHOLD = 65536;
    private static final float SIN_4_OVER_PI = Float.intBitsToFloat(0x3fa2f983);
    private static final float SIN_PI_OVER_4_PART1 = Float.intBitsToFloat(0xbf490fda);
    private static final float SIN_PI_OVER_4_PART2 = Float.intBitsToFloat(0xb3222168);
    private static final float SIN_PI_OVER_4_PART3 = Float.intBitsToFloat(0xa74234c5);
    private static final float SIN_I32_THRESHOLD = Float.intBitsToFloat(0x4f000000);
    private static final float SIN_SIN_CORRECTION_A = Float.intBitsToFloat(0x2f2ec7e9);
    private static final float SIN_SIN_CORRECTION_B = Float.intBitsToFloat(0xb2d72f2d);
    private static final float SIN_SIN_COEFFICIENT1 = Float.intBitsToFloat(0xbe2aaaab);
    private static final float SIN_SIN_COEFFICIENT2 = Float.intBitsToFloat(0x3c088889);
    private static final float SIN_SIN_COEFFICIENT3 = Float.intBitsToFloat(0xb9500d01);
    private static final float SIN_SIN_COEFFICIENT4 = Float.intBitsToFloat(0x3638ef1b);
    private static final float SIN_COS_CORRECTION_A = Float.intBitsToFloat(0xad47d24d);
    private static final float SIN_COS_CORRECTION_B = Float.intBitsToFloat(0x310f74ec);
    private static final float SIN_COS_COEFFICIENT1 = Float.intBitsToFloat(0x3d2aaaab);
    private static final float SIN_COS_COEFFICIENT2 = Float.intBitsToFloat(0xbab60b61);
    private static final float SIN_COS_COEFFICIENT3 = Float.intBitsToFloat(0x37d00d01);
    private static final float SIN_COS_COEFFICIENT4 = Float.intBitsToFloat(0xb493f27c);

    private final WasmModule module;
    private final WasmHost host;
    private final long[] values = new long[VALUE_STACK_LIMIT];
    private int valueTop;

    private final int[] controlWord = new int[CONTROL_STACK_LIMIT];
    private final int[] controlStart = new int[CONTROL_STACK_LIMIT];
    private final int[] controlEnd = new int[CONTROL_STACK_LIMIT];
    private final int[] controlBase = new int[CONTROL_STACK_LIMIT];
    private int controlTop;

    private final long[][] localFrames = new long[CALL_STACK_LIMIT][];
    private int callDepth;
    private final long[] transferValues = new long[16];
    private int instructionsExecuted;
    private int dispatchesExecuted;
    private int instructionLimit = 100000000;
    private boolean profilingEnabled;
    private long[] opcodeCounts;
    private LongCountTable opcodePairCounts;
    private LongCountTable opcodeTripleCounts;
    private long[] functionCallCounts;
    private long[] functionDispatchCounts;
    private byte[][] compactProfileDispatchLengths;
    private short[][] compactProfileInstructionLengths;
    private byte[][] compactProfileBreakReasons;
    private char[][] compactProfileBreakOpcodes;
    private long[] compactProfileBreakCounts;
    private long[] compactProfileBreakOpcodeCounts;
    private long[] compactProfileRejectionCounts;
    private long[] compactProfileCandidateDispatchLengths;
    private long[] compactProfileAcceptedDispatchLengths;
    private long[] compactProfileCandidateInstructionLengths;
    private long[] compactProfileAcceptedInstructionLengths;
    private long compactProfileCandidates;
    private long compactProfileAcceptedCandidates;
    private boolean compactExecutorEnabled = true;
    private boolean traceExecutorEnabled = true;
    private boolean directNumericIntrinsicsEnabled = true;
    private boolean integerCompactOpcodesEnabled = true;
    private boolean numericHostImportDispatchEnabled = true;
    private final int[][] compactBlockEnds;
    private int compactBlockCalls;
    private int compactInstructionsExecuted;
    private int traceLoopCalls;
    private int traceLoopIterations;

    public WasmInterpreter(WasmModule module, WasmHost host) {
        this.module = module;
        this.host = host;
        compactBlockEnds = new int[module.functions.length][];
    }

    public void setInstructionLimit(long instructionLimit) {
        if (instructionLimit < 0L) {
            this.instructionLimit = -1;
        } else if (instructionLimit > 2147483644L) {
            this.instructionLimit = 2147483644;
        } else {
            this.instructionLimit = (int) instructionLimit;
        }
    }

    public long instructionsExecuted() {
        return instructionsExecuted;
    }

    public long dispatchesExecuted() {
        return dispatchesExecuted;
    }

    public int fastPathCalls() {
        return 0;
    }

    public int compactBlockCalls() {
        return compactBlockCalls;
    }

    public int compactInstructionsExecuted() {
        return compactInstructionsExecuted;
    }

    public int traceLoopCalls() {
        return traceLoopCalls;
    }

    public int traceLoopIterations() {
        return traceLoopIterations;
    }

    public int directBranchFastPathIntCount() {
        int count = 0;
        int functionIndex;
        for (functionIndex = 0; functionIndex < module.functions.length; functionIndex++) {
            WasmModule.FunctionBody body = module.functions[functionIndex];
            if (body == null || body.branchFastSiteByPc == null) {
                continue;
            }
            count += body.branchFastSiteByPc.length;
            count += body.branchFastTargets.length;
            count += body.branchFastHeights.length;
            count += body.branchFastArities.length;
            count += body.branchFastControls.length;
        }
        return count;
    }

    public int directBranchFastPathArrayCount() {
        int count = 0;
        int functionIndex;
        for (functionIndex = 0; functionIndex < module.functions.length; functionIndex++) {
            WasmModule.FunctionBody body = module.functions[functionIndex];
            if (body != null && body.branchFastSiteByPc != null) {
                count += 5;
            }
        }
        return count;
    }

    public void setFastPathsEnabled(boolean enabled) {
        // Retained for diagnostic probe compatibility. The universal
        // interpreter has no cartridge-specific execution paths.
    }

    public void setCompactExecutorEnabled(boolean enabled) {
        compactExecutorEnabled = enabled;
    }

    public void setTraceExecutorEnabled(boolean enabled) {
        traceExecutorEnabled = enabled;
    }

    public void setDirectNumericIntrinsicsEnabled(boolean enabled) {
        if (directNumericIntrinsicsEnabled == enabled) {
            return;
        }
        directNumericIntrinsicsEnabled = enabled;
        int index;
        for (index = 0; index < compactBlockEnds.length; index++) {
            compactBlockEnds[index] = null;
        }
        if (InterpreterBuildConfig.PROFILING_SUPPORT && profilingEnabled) {
            prepareCompactProfileMetadata();
        }
    }

    public void setIntegerCompactOpcodesEnabled(boolean enabled) {
        if (integerCompactOpcodesEnabled == enabled) {
            return;
        }
        integerCompactOpcodesEnabled = enabled;
        int index;
        for (index = 0; index < compactBlockEnds.length; index++) {
            compactBlockEnds[index] = null;
        }
        if (InterpreterBuildConfig.PROFILING_SUPPORT && profilingEnabled) {
            prepareCompactProfileMetadata();
        }
    }

    public void setNumericHostImportDispatchEnabled(boolean enabled) {
        numericHostImportDispatchEnabled = enabled;
    }

    public void setProfilingEnabled(boolean enabled) {
        if (!InterpreterBuildConfig.PROFILING_SUPPORT) {
            if (enabled) {
                throw new IllegalStateException("profiling is disabled in this build");
            }
            return;
        }
        profilingEnabled = enabled;
        if (enabled && opcodeCounts == null) {
            opcodeCounts = new long[PROFILE_OPCODE_LIMIT];
            opcodePairCounts = new LongCountTable(1024);
            opcodeTripleCounts = new LongCountTable(2048);
            functionCallCounts = new long[module.functions.length];
            functionDispatchCounts = new long[module.functions.length];
            compactProfileDispatchLengths = new byte[module.functions.length][];
            compactProfileInstructionLengths = new short[module.functions.length][];
            compactProfileBreakReasons = new byte[module.functions.length][];
            compactProfileBreakOpcodes = new char[module.functions.length][];
            compactProfileBreakCounts = new long[COMPACT_BREAK_REASON_COUNT];
            compactProfileBreakOpcodeCounts = new long[PROFILE_OPCODE_LIMIT];
            compactProfileRejectionCounts = new long[COMPACT_BREAK_REASON_COUNT];
            compactProfileCandidateDispatchLengths =
                    new long[PROFILE_MAX_COMPACT_DISPATCHES + 1];
            compactProfileAcceptedDispatchLengths =
                    new long[PROFILE_MAX_COMPACT_DISPATCHES + 1];
            compactProfileCandidateInstructionLengths =
                    new long[PROFILE_MAX_COMPACT_INSTRUCTIONS + 1];
            compactProfileAcceptedInstructionLengths =
                    new long[PROFILE_MAX_COMPACT_INSTRUCTIONS + 1];
        }
        if (enabled) {
            prepareCompactProfileMetadata();
        }
    }

    public int opcodeProfileLimit() {
        return PROFILE_OPCODE_LIMIT;
    }

    public long opcodeCount(int opcode) {
        if (opcodeCounts == null || opcode < 0 || opcode >= opcodeCounts.length) {
            return 0;
        }
        return opcodeCounts[opcode];
    }

    public long functionCallCount(int functionIndex) {
        if (functionCallCounts == null
                || functionIndex < 0
                || functionIndex >= functionCallCounts.length) {
            return 0;
        }
        return functionCallCounts[functionIndex];
    }

    public long functionDispatchCount(int functionIndex) {
        if (functionDispatchCounts == null
                || functionIndex < 0
                || functionIndex >= functionDispatchCounts.length) {
            return 0;
        }
        return functionDispatchCounts[functionIndex];
    }

    public long opcodePairCount(int firstOpcode, int secondOpcode) {
        if (opcodePairCounts == null
                || firstOpcode < 0
                || firstOpcode >= PROFILE_OPCODE_LIMIT
                || secondOpcode < 0
                || secondOpcode >= PROFILE_OPCODE_LIMIT) {
            return 0;
        }
        return opcodePairCounts.get(pairKey(firstOpcode, secondOpcode));
    }

    public long w4irOpcodePairCount(int firstOpcode, int secondOpcode) {
        return opcodePairCount(firstOpcode, secondOpcode);
    }

    public long opcodeTripleCount(int firstOpcode, int secondOpcode, int thirdOpcode) {
        if (opcodeTripleCounts == null
                || firstOpcode < 0
                || firstOpcode >= PROFILE_OPCODE_LIMIT
                || secondOpcode < 0
                || secondOpcode >= PROFILE_OPCODE_LIMIT
                || thirdOpcode < 0
                || thirdOpcode >= PROFILE_OPCODE_LIMIT) {
            return 0;
        }
        return opcodeTripleCounts.get(tripleKey(firstOpcode, secondOpcode, thirdOpcode));
    }

    public int opcodePairSlotCount() {
        return opcodePairCounts == null ? 0 : opcodePairCounts.capacity();
    }

    public boolean opcodePairSlotUsed(int slot) {
        return opcodePairCounts != null && opcodePairCounts.used(slot);
    }

    public int opcodePairFirstAtSlot(int slot) {
        return (int) ((opcodePairCounts.key(slot) >>> 16) & 0xffffL);
    }

    public int opcodePairSecondAtSlot(int slot) {
        return (int) (opcodePairCounts.key(slot) & 0xffffL);
    }

    public long opcodePairCountAtSlot(int slot) {
        return opcodePairCounts.count(slot);
    }

    public int opcodeTripleSlotCount() {
        return opcodeTripleCounts == null ? 0 : opcodeTripleCounts.capacity();
    }

    public boolean opcodeTripleSlotUsed(int slot) {
        return opcodeTripleCounts != null && opcodeTripleCounts.used(slot);
    }

    public int opcodeTripleFirstAtSlot(int slot) {
        return (int) ((opcodeTripleCounts.key(slot) >>> 32) & 0xffffL);
    }

    public int opcodeTripleSecondAtSlot(int slot) {
        return (int) ((opcodeTripleCounts.key(slot) >>> 16) & 0xffffL);
    }

    public int opcodeTripleThirdAtSlot(int slot) {
        return (int) (opcodeTripleCounts.key(slot) & 0xffffL);
    }

    public long opcodeTripleCountAtSlot(int slot) {
        return opcodeTripleCounts.count(slot);
    }

    public long compactProfileCandidateCount() {
        return compactProfileCandidates;
    }

    public long compactProfileAcceptedCount() {
        return compactProfileAcceptedCandidates;
    }

    public long compactProfileBreakCount(int reason) {
        return profileArrayValue(compactProfileBreakCounts, reason);
    }

    public long compactProfileBreakOpcodeCount(int opcode) {
        return profileArrayValue(compactProfileBreakOpcodeCounts, opcode);
    }

    public long compactProfileRejectionCount(int reason) {
        return profileArrayValue(compactProfileRejectionCounts, reason);
    }

    public long compactProfileCandidateDispatchLengthCount(int length) {
        return profileArrayValue(compactProfileCandidateDispatchLengths, length);
    }

    public long compactProfileAcceptedDispatchLengthCount(int length) {
        return profileArrayValue(compactProfileAcceptedDispatchLengths, length);
    }

    public long compactProfileCandidateInstructionLengthCount(int length) {
        return profileArrayValue(compactProfileCandidateInstructionLengths, length);
    }

    public long compactProfileAcceptedInstructionLengthCount(int length) {
        return profileArrayValue(compactProfileAcceptedInstructionLengths, length);
    }

    public void invoke(String exportName) throws WasmException {
        int functionIndex = module.exportedFunction(exportName);
        resetInvocation();
        callFunction(functionIndex);
        if (valueTop != 0 || controlTop != 0 || callDepth != 0) {
            throw new WasmTrap("interpreter stack did not unwind");
        }
    }

    public void invokeStartIfPresent() {
        if (module.startFunction >= 0) {
            resetInvocation();
            callFunction(module.startFunction);
        }
    }

    public void invokeCartridgeLifecycle() throws WasmException {
        invokeStartIfPresent();
        if (module.hasExportedFunction("_start")) {
            invoke("_start");
        }
        if (module.hasExportedFunction("_initialize")) {
            invoke("_initialize");
        }
        if (module.hasExportedFunction("start")) {
            invoke("start");
        }
    }

    private void resetInvocation() {
        valueTop = 0;
        controlTop = 0;
        callDepth = 0;
        instructionsExecuted = 0;
        if (InterpreterBuildConfig.DIAGNOSTIC_COUNTERS) {
            dispatchesExecuted = 0;
        }
        if (InterpreterBuildConfig.DIAGNOSTIC_COUNTERS) {
            compactBlockCalls = 0;
            compactInstructionsExecuted = 0;
        }
        traceLoopCalls = 0;
        traceLoopIterations = 0;
        if (InterpreterBuildConfig.PROFILING_SUPPORT && profilingEnabled) {
            int index;
            for (index = 0; index < opcodeCounts.length; index++) {
                opcodeCounts[index] = 0;
            }
            opcodePairCounts.clear();
            opcodeTripleCounts.clear();
            for (index = 0; index < functionCallCounts.length; index++) {
                functionCallCounts[index] = 0;
                functionDispatchCounts[index] = 0;
            }
            clearProfileArray(compactProfileBreakCounts);
            clearProfileArray(compactProfileBreakOpcodeCounts);
            clearProfileArray(compactProfileRejectionCounts);
            clearProfileArray(compactProfileCandidateDispatchLengths);
            clearProfileArray(compactProfileAcceptedDispatchLengths);
            clearProfileArray(compactProfileCandidateInstructionLengths);
            clearProfileArray(compactProfileAcceptedInstructionLengths);
            compactProfileCandidates = 0;
            compactProfileAcceptedCandidates = 0;
        }
    }

    private void callFunction(int functionIndex) {
        if (functionIndex < 0 || functionIndex >= module.functions.length) {
            throw new WasmTrap("function index is out of range: " + functionIndex);
        }
        if (InterpreterBuildConfig.PROFILING_SUPPORT && profilingEnabled) {
            functionCallCounts[functionIndex]++;
        }
        WasmModule.FunctionBody body = module.functions[functionIndex];
        if (body != null) {
            if (callDepth >= CALL_STACK_LIMIT) {
                throw new WasmTrap("call stack exhausted");
            }
            if (body.intrinsic == WasmModule.INTRINSIC_F32_FLOOR
                    || body.intrinsic == WasmModule.INTRINSIC_F32_SIN) {
                WasmModule.FuncType intrinsicType = module.functionTypes[functionIndex];
                if (intrinsicType.parameters.length != 1
                        || intrinsicType.parameters[0] != WasmModule.F32
                        || intrinsicType.results.length != 1
                        || intrinsicType.results[0] != WasmModule.F32) {
                    throw new WasmTrap("invalid numeric intrinsic signature");
                }
                if (valueTop < 1) {
                    throw new WasmTrap("not enough function arguments");
                }
                int argument = popI32();
                pushI32(body.intrinsic == WasmModule.INTRINSIC_F32_FLOOR
                        ? floorF32Bits(argument)
                        : sinF32Bits(argument));
                return;
            }
        }
        WasmModule.FuncType type = module.functionTypes[functionIndex];
        int argumentCount = type.parameters.length;
        int argumentBase = valueTop - argumentCount;
        if (argumentBase < 0) {
            throw new WasmTrap("not enough function arguments");
        }

        if (body == null) {
            WasmModule.ImportedFunction imported = module.imports[functionIndex];
            long result;
            if (numericHostImportDispatchEnabled) {
                result = host.invoke(
                        imported.hostId,
                        values,
                        argumentBase,
                        argumentCount,
                        module);
            } else {
                result = host.invoke(
                        imported.module,
                        imported.name,
                        values,
                        argumentBase,
                        argumentCount,
                        module);
            }
            valueTop = argumentBase;
            if (type.results.length == 1) {
                push(result);
            } else if (type.results.length != 0) {
                throw new WasmTrap("multi-value host results are not supported");
            }
            return;
        }

        int localCount = argumentCount + body.declaredLocalCount;
        long[] locals = localFrames[callDepth];
        if (locals == null || locals.length < localCount) {
            locals = new long[localCount];
            localFrames[callDepth] = locals;
        }
        int index;
        for (index = 0; index < localCount; index++) {
            locals[index] = 0;
        }
        if (argumentCount == 1) {
            locals[0] = values[argumentBase];
        } else if (argumentCount > 1) {
            System.arraycopy(values, argumentBase, locals, 0, argumentCount);
        }
        valueTop = argumentBase;

        int functionStackBase = valueTop;
        int functionControlBase = controlTop;
        callDepth++;
        try {
            execute(functionIndex, body, type, locals, functionStackBase, functionControlBase);
        } finally {
            callDepth--;
            controlTop = functionControlBase;
        }
    }

    private void execute(
            int functionIndex,
            WasmModule.FunctionBody body,
            WasmModule.FuncType functionType,
            long[] locals,
            int functionStackBase,
            int functionControlBase) {
        int instructionCount = body.instructionCount();
        int[] code = body.codePage(0);
        int codePageBase = body.codePageBase(0);
        int pc = 0;
        int previousOpcode = -1;
        int previousPreviousOpcode = -1;
        boolean residentCode = body.code != null;
        boolean compactEligible =
                compactExecutorEnabled
                        && (!InterpreterBuildConfig.PROFILING_SUPPORT || !profilingEnabled)
                        && residentCode;
        int[] compactEnds = null;
        int budgetCheckLimit = instructionLimit;
        int executed = this.instructionsExecuted;
        boolean counterInField = false;
        executeBody:
        try {
        if (compactEligible) {
            if (executed >= COMPACT_HOT_INVOCATION_THRESHOLD) {
                compactEnds = compactBlockEnds(functionIndex, body);
            } else if (COMPACT_HOT_INVOCATION_THRESHOLD < budgetCheckLimit) {
                budgetCheckLimit = COMPACT_HOT_INVOCATION_THRESHOLD;
            }
        }
        int[] branchSiteByPc = null;
        int[] branchTargetBySite = null;
        int[] branchHeightBySite = null;
        int[] branchArityBySite = null;
        int[] branchControlBySite = null;
        if (InterpreterBuildConfig.DIRECT_BRANCH_FAST_PATH) {
            branchSiteByPc = body.branchFastSiteByPc;
            branchTargetBySite = body.branchFastTargets;
            branchHeightBySite = body.branchFastHeights;
            branchArityBySite = body.branchFastArities;
            branchControlBySite = body.branchFastControls;
        }
        while (pc < instructionCount) {
            if (compactEnds != null) {
                int compactEnd = compactEnds[pc];
                if (compactEnd > pc) {
                    if (InterpreterBuildConfig.DIAGNOSTIC_COUNTERS) {
                        dispatchesExecuted++;
                        compactBlockCalls++;
                    }
                    this.instructionsExecuted = executed;
                    counterInField = true;
                    executeCompactBlock(body.code, pc, compactEnd, locals);
                    executed = this.instructionsExecuted;
                    counterInField = false;
                    pc = compactEnd;
                    continue;
                }
            }
            int codeOffset = pc * WasmModule.W4IR_STRIDE;
            if ((!InterpreterBuildConfig.RESIDENT_CODE_FAST_PATH || !residentCode)
                    && (codeOffset < codePageBase
                            || codeOffset + 2 >= codePageBase + code.length)) {
                code = body.codePage(codeOffset);
                codePageBase = body.codePageBase(codeOffset);
            }
            int pageOffset = codeOffset - codePageBase;
            int instruction = code[pageOffset];
            int opcode = instruction & 0xffff;
            int auxiliary;
            if (executed >= budgetCheckLimit) {
                if (executed >= instructionLimit) {
                    executed++;
                    throw new WasmTrap("instruction budget exhausted");
                }
                compactEnds = compactBlockEnds(functionIndex, body);
                budgetCheckLimit = instructionLimit;
                continue;
            }
            executed++;
            int operand = code[pageOffset + 1];
            if (InterpreterBuildConfig.DIAGNOSTIC_COUNTERS) {
                dispatchesExecuted++;
            }
            if (InterpreterBuildConfig.PROFILING_SUPPORT && profilingEnabled) {
                profileInstruction(
                        functionIndex,
                        pc,
                        previousPreviousOpcode,
                        previousOpcode,
                        WasmModule.originalOpcode(opcode));
                previousPreviousOpcode = previousOpcode;
                previousOpcode = WasmModule.originalOpcode(opcode);
            }
            switch (opcode) {
                case 0x00:
                    throw new WasmTrap("unreachable instruction executed");
                case 0x01:
                    pc++;
                    break;
                case WasmModule.BLOCK:
                case WasmModule.LOOP: {
                    if (controlTop >= CONTROL_STACK_LIMIT) {
                        throw new WasmTrap("control stack exhausted");
                    }
                    int controlParameterCount = (instruction >>> 16) & 0xff;
                    int controlBaseIndex = valueTop - controlParameterCount;
                    if (controlBaseIndex < 0) {
                        throw new WasmTrap("not enough block parameters");
                    }
                    int controlEntryFrame = controlTop;
                    controlWord[controlEntryFrame] = instruction;
                    controlStart[controlEntryFrame] = pc;
                    controlEnd[controlEntryFrame] = operand;
                    controlBase[controlEntryFrame] = controlBaseIndex;
                    controlTop = controlEntryFrame + 1;
                    pc++;
                    break;
                }
                case WasmModule.IF: {
                    auxiliary = code[pageOffset + 2];
                    int condition = popI32();
                    if (controlTop >= CONTROL_STACK_LIMIT) {
                        throw new WasmTrap("control stack exhausted");
                    }
                    int ifParameterCount = (instruction >>> 16) & 0xff;
                    int ifControlBase = valueTop - ifParameterCount;
                    if (ifControlBase < 0) {
                        throw new WasmTrap("not enough block parameters");
                    }
                    int ifEntryFrame = controlTop;
                    controlWord[ifEntryFrame] = instruction;
                    controlStart[ifEntryFrame] = pc;
                    controlEnd[ifEntryFrame] = operand;
                    controlBase[ifEntryFrame] = ifControlBase;
                    controlTop = ifEntryFrame + 1;
                    if (condition != 0) {
                        pc++;
                    } else if (auxiliary >= 0) {
                        pc = auxiliary + 1;
                    } else {
                        if (controlTop <= 0) {
                            throw new WasmTrap("control stack underflow");
                        }
                        int ifExitFrame = controlTop - 1;
                        transfer(instruction >>> 24, controlBase[ifExitFrame]);
                        controlTop = ifExitFrame;
                        pc = operand + 1;
                    }
                    break;
                }
                case 0x05: {
                    if (controlTop <= 0) {
                        throw new WasmTrap("control stack underflow");
                    }
                    int elseExitFrame = controlTop - 1;
                    transfer(controlWord[elseExitFrame] >>> 24, controlBase[elseExitFrame]);
                    controlTop = elseExitFrame;
                    pc = operand + 1;
                    break;
                }
                case 0x0b: {
                    if (pc == instructionCount - 1) {
                        finishFunction(functionStackBase, functionType.results.length);
                        break executeBody;
                    }
                    if (controlTop <= 0) {
                        throw new WasmTrap("control stack underflow");
                    }
                    int endExitFrame = controlTop - 1;
                    transfer(controlWord[endExitFrame] >>> 24, controlBase[endExitFrame]);
                    controlTop = endExitFrame;
                    pc++;
                    break;
                }
                case 0x0c:
                    if (InterpreterBuildConfig.DIRECT_BRANCH_FAST_PATH) {
                        int directBranchSite = branchSiteByPc[pc];
                        if (directBranchSite >= 0) {
                            int directBranchTarget =
                                    branchTargetBySite[directBranchSite];
                            int directBranchArity =
                                    branchArityBySite[directBranchSite];
                            int directBranchDestination = functionStackBase
                                    + branchHeightBySite[directBranchSite];
                            if (directBranchTarget >= 0
                                    && directBranchArity <= 1
                                    && directBranchDestination + directBranchArity
                                            <= valueTop) {
                                if (directBranchArity != 0) {
                                    values[directBranchDestination] =
                                            values[valueTop - 1];
                                }
                                valueTop =
                                        directBranchDestination + directBranchArity;
                                controlTop = functionControlBase
                                        + branchControlBySite[directBranchSite];
                                pc = directBranchTarget;
                                break;
                            }
                        } else {
                            body.branchDescriptorIndexAt(pc);
                        }
                    }
                    if (InterpreterBuildConfig.INLINE_BRANCH_FAST_PATH) {
                        int inlineBranchAvailable =
                                controlTop - functionControlBase;
                        if (operand >= 0 && operand < inlineBranchAvailable) {
                            int inlineBranchTarget =
                                    controlTop - 1 - operand;
                            int inlineBranchWord =
                                    controlWord[inlineBranchTarget];
                            boolean inlineBranchLoop =
                                    (inlineBranchWord & 0xffff) == WasmModule.LOOP;
                            int inlineBranchArity = inlineBranchLoop
                                    ? (inlineBranchWord >>> 16) & 0xff
                                    : inlineBranchWord >>> 24;
                            int inlineBranchDestination =
                                    controlBase[inlineBranchTarget];
                            if (inlineBranchArity <= 1
                                    && inlineBranchDestination + inlineBranchArity
                                            <= valueTop) {
                                if (inlineBranchArity != 0) {
                                    values[inlineBranchDestination] =
                                            values[valueTop - 1];
                                }
                                valueTop =
                                        inlineBranchDestination + inlineBranchArity;
                                if (inlineBranchLoop) {
                                    controlTop = inlineBranchTarget + 1;
                                    pc = controlStart[inlineBranchTarget] + 1;
                                } else {
                                    controlTop = inlineBranchTarget;
                                    pc = controlEnd[inlineBranchTarget] + 1;
                                }
                                break;
                            }
                        }
                    }
                    if (InterpreterBuildConfig.DESCRIPTOR_SHADOW) {
                        pc = branchWithDescriptorShadow(
                                body,
                                body.branchDescriptorIndexAt(pc),
                                operand,
                                functionStackBase,
                                functionType.results.length,
                                functionControlBase);
                    } else {
                        pc = branch(
                                operand,
                                functionStackBase,
                                functionType.results.length,
                                functionControlBase);
                    }
                    if (pc < 0) {
                        break executeBody;
                    }
                    break;
                case 0x0d:
                    if (popI32() != 0) {
                        if (InterpreterBuildConfig.DIRECT_BRANCH_FAST_PATH) {
                            int takenBranchSite = branchSiteByPc[pc];
                            if (takenBranchSite >= 0) {
                                int takenBranchTarget =
                                        branchTargetBySite[takenBranchSite];
                                int takenBranchArity =
                                        branchArityBySite[takenBranchSite];
                                int takenBranchDestination = functionStackBase
                                        + branchHeightBySite[takenBranchSite];
                                if (takenBranchTarget >= 0
                                        && takenBranchArity <= 1
                                        && takenBranchDestination
                                                        + takenBranchArity
                                                <= valueTop) {
                                    if (takenBranchArity != 0) {
                                        values[takenBranchDestination] =
                                                values[valueTop - 1];
                                    }
                                    valueTop = takenBranchDestination
                                            + takenBranchArity;
                                    controlTop = functionControlBase
                                            + branchControlBySite[takenBranchSite];
                                    pc = takenBranchTarget;
                                    break;
                                }
                            } else {
                                body.branchDescriptorIndexAt(pc);
                            }
                        }
                        if (InterpreterBuildConfig.INLINE_BRANCH_FAST_PATH) {
                            int inlineTakenAvailable =
                                    controlTop - functionControlBase;
                            if (operand >= 0 && operand < inlineTakenAvailable) {
                                int inlineTakenTarget =
                                        controlTop - 1 - operand;
                                int inlineTakenWord =
                                        controlWord[inlineTakenTarget];
                                boolean inlineTakenLoop =
                                        (inlineTakenWord & 0xffff) == WasmModule.LOOP;
                                int inlineTakenArity = inlineTakenLoop
                                        ? (inlineTakenWord >>> 16) & 0xff
                                        : inlineTakenWord >>> 24;
                                int inlineTakenDestination =
                                        controlBase[inlineTakenTarget];
                                if (inlineTakenArity <= 1
                                        && inlineTakenDestination
                                                        + inlineTakenArity
                                                <= valueTop) {
                                    if (inlineTakenArity != 0) {
                                        values[inlineTakenDestination] =
                                                values[valueTop - 1];
                                    }
                                    valueTop = inlineTakenDestination
                                            + inlineTakenArity;
                                    if (inlineTakenLoop) {
                                        controlTop = inlineTakenTarget + 1;
                                        pc = controlStart[inlineTakenTarget] + 1;
                                    } else {
                                        controlTop = inlineTakenTarget;
                                        pc = controlEnd[inlineTakenTarget] + 1;
                                    }
                                    break;
                                }
                            }
                        }
                        if (InterpreterBuildConfig.DESCRIPTOR_SHADOW) {
                            pc = branchWithDescriptorShadow(
                                    body,
                                    body.branchDescriptorIndexAt(pc),
                                    operand,
                                    functionStackBase,
                                    functionType.results.length,
                                    functionControlBase);
                        } else {
                            pc = branch(
                                    operand,
                                    functionStackBase,
                                    functionType.results.length,
                                    functionControlBase);
                        }
                        if (pc < 0) {
                            break executeBody;
                        }
                    } else {
                        if (InterpreterBuildConfig.DESCRIPTOR_SHADOW) {
                            verifyDescriptorFallthrough(
                                    body,
                                    body.branchDescriptorIndexAt(pc),
                                    operand,
                                    functionStackBase,
                                    functionType.results.length,
                                    functionControlBase,
                                    valueTop,
                                    controlTop,
                                    pc + 1,
                                    pc + 1);
                        }
                        pc++;
                    }
                    break;
                case 0x0e:
                    int selector = popI32();
                    int[] branchTable = body.branchTables[operand];
                    int selected = branchTable.length - 1;
                    if (selector >= 0 && selector < selected) {
                        selected = selector;
                    }
                    if (InterpreterBuildConfig.DESCRIPTOR_SHADOW) {
                        pc = branchWithDescriptorShadow(
                                body,
                                body.branchDescriptorTables[operand][selected],
                                branchTable[selected],
                                functionStackBase,
                                functionType.results.length,
                                functionControlBase);
                    } else {
                        pc = branch(
                                branchTable[selected],
                                functionStackBase,
                                functionType.results.length,
                                functionControlBase);
                    }
                    if (pc < 0) {
                        break executeBody;
                    }
                    break;
                case 0x0f:
                    finishFunction(functionStackBase, functionType.results.length);
                    break executeBody;
                case 0x10:
                    this.instructionsExecuted = executed;
                    counterInField = true;
                    callFunction(operand);
                    executed = this.instructionsExecuted;
                    counterInField = false;
                    pc++;
                    break;
                case 0x11:
                    auxiliary = code[pageOffset + 2];
                    int tableIndex = popI32();
                    if (auxiliary != 0
                            || tableIndex < 0
                            || tableIndex >= module.table.length
                            || module.table[tableIndex] < 0) {
                        throw new WasmTrap("undefined table element");
                    }
                    int indirectFunction = module.table[tableIndex];
                    WasmModule.FuncType actualIndirectType =
                            module.functionTypes[indirectFunction];
                    WasmModule.FuncType expectedIndirectType = module.types[operand];
                    if (actualIndirectType.canonicalId
                            != expectedIndirectType.canonicalId) {
                        throw new WasmTrap("indirect call type mismatch");
                    }
                    this.instructionsExecuted = executed;
                    counterInField = true;
                    callFunction(indirectFunction);
                    executed = this.instructionsExecuted;
                    counterInField = false;
                    pc++;
                    break;
                case 0x1a:
                    pop();
                    pc++;
                    break;
                case 0x1b:
                case 0x1c:
                    if (valueTop < 3) {
                        throw new WasmTrap("value stack underflow");
                    }
                    int genericSelectCondition = (int) values[--valueTop];
                    long genericSelectSecond = values[--valueTop];
                    if (genericSelectCondition == 0) {
                        values[valueTop - 1] = genericSelectSecond;
                    }
                    pc++;
                    break;
                case 0x20:
                    push(locals[operand]);
                    pc++;
                    break;
                case 0x21:
                    locals[operand] = pop();
                    pc++;
                    break;
                case 0x22:
                    locals[operand] = peek();
                    pc++;
                    break;
                case 0x23:
                    push(module.globals[operand]);
                    pc++;
                    break;
                case 0x24:
                    module.globals[operand] = pop();
                    pc++;
                    break;
                case 0x28:
                    pushI32(loadI32(address(operand, 4)));
                    pc++;
                    break;
                case 0x29:
                    push(loadI64(address(operand, 8)));
                    pc++;
                    break;
                case 0x2a:
                    push(loadI32(address(operand, 4)) & 0xffffffffL);
                    pc++;
                    break;
                case 0x2b:
                    push(loadI64(address(operand, 8)));
                    pc++;
                    break;
                case 0x2c:
                    pushI32((byte) module.memory[address(operand, 1)]);
                    pc++;
                    break;
                case 0x2d:
                    if (valueTop <= 0) {
                        throw new WasmTrap("value stack underflow");
                    }
                    int genericLoad8Base = (int) values[--valueTop];
                    byte[] genericLoad8Memory = module.memory;
                    int genericLoad8MaximumBase = genericLoad8Memory.length - 1;
                    if (genericLoad8Base < 0
                            || operand < 0
                            || genericLoad8Base > genericLoad8MaximumBase
                            || operand > genericLoad8MaximumBase - genericLoad8Base) {
                        throw new WasmTrap("out-of-bounds memory access");
                    }
                    values[valueTop++] =
                            genericLoad8Memory[genericLoad8Base + operand] & 0xff;
                    pc++;
                    break;
                case 0x2e:
                    pushI32((short) loadU16(address(operand, 2)));
                    pc++;
                    break;
                case 0x2f:
                    pushI32(loadU16(address(operand, 2)));
                    pc++;
                    break;
                case 0x30:
                    push((byte) module.memory[address(operand, 1)]);
                    pc++;
                    break;
                case 0x31:
                    push(module.memory[address(operand, 1)] & 0xffL);
                    pc++;
                    break;
                case 0x32:
                    push((short) loadU16(address(operand, 2)));
                    pc++;
                    break;
                case 0x33:
                    push(loadU16(address(operand, 2)) & 0xffffL);
                    pc++;
                    break;
                case 0x34:
                    push(loadI32(address(operand, 4)));
                    pc++;
                    break;
                case 0x35:
                    push(loadI32(address(operand, 4)) & 0xffffffffL);
                    pc++;
                    break;
                case 0x36:
                    int i32StoreValue = popI32();
                    storeI32(address(operand, 4), i32StoreValue);
                    pc++;
                    break;
                case 0x37:
                    long i64StoreValue = pop();
                    storeI64(address(operand, 8), i64StoreValue);
                    pc++;
                    break;
                case 0x38:
                    int f32StoreValue = popI32();
                    storeI32(address(operand, 4), f32StoreValue);
                    pc++;
                    break;
                case 0x39:
                    long f64StoreValue = pop();
                    storeI64(address(operand, 8), f64StoreValue);
                    pc++;
                    break;
                case 0x3a:
                    int store8Value = popI32();
                    module.memory[address(operand, 1)] = (byte) store8Value;
                    pc++;
                    break;
                case 0x3b:
                    int store16Value = popI32();
                    storeU16(address(operand, 2), store16Value);
                    pc++;
                    break;
                case 0x3c:
                    long i64Store8Value = pop();
                    module.memory[address(operand, 1)] = (byte) i64Store8Value;
                    pc++;
                    break;
                case 0x3d:
                    long i64Store16Value = pop();
                    storeU16(address(operand, 2), (int) i64Store16Value);
                    pc++;
                    break;
                case 0x3e:
                    long i64Store32Value = pop();
                    storeI32(address(operand, 4), (int) i64Store32Value);
                    pc++;
                    break;
                case 0x3f:
                    pushI32(1);
                    pc++;
                    break;
                case 0x40:
                    int growth = popI32();
                    pushI32(growth == 0 ? 1 : -1);
                    pc++;
                    break;
                case 0x41:
                case 0x42:
                case 0x43:
                case 0x44:
                    auxiliary = code[pageOffset + 2];
                    push((operand & 0xffffffffL) | ((long) auxiliary << 32));
                    pc++;
                    break;
                case 0x45:
                    if (valueTop <= 0) {
                        throw new WasmTrap("value stack underflow");
                    }
                    values[valueTop - 1] =
                            (int) values[valueTop - 1] == 0 ? 1 : 0;
                    pc++;
                    break;
                case 0x46:
                case 0x47:
                case 0x48:
                case 0x49:
                case 0x4a:
                case 0x4b:
                case 0x4c:
                case 0x4d:
                case 0x4e:
                case 0x4f:
                    if (valueTop < 2) {
                        throw new WasmTrap("value stack underflow");
                    }
                    int genericI32CompareRight = (int) values[--valueTop];
                    int genericI32CompareLeft = (int) values[valueTop - 1];
                    boolean genericI32Comparison;
                    switch (opcode) {
                        case 0x46:
                            genericI32Comparison =
                                    genericI32CompareLeft
                                            == genericI32CompareRight;
                            break;
                        case 0x47:
                            genericI32Comparison =
                                    genericI32CompareLeft
                                            != genericI32CompareRight;
                            break;
                        case 0x48:
                            genericI32Comparison =
                                    genericI32CompareLeft
                                            < genericI32CompareRight;
                            break;
                        case 0x49:
                            genericI32Comparison =
                                    (genericI32CompareLeft & 0xffffffffL)
                                            < (genericI32CompareRight
                                                    & 0xffffffffL);
                            break;
                        case 0x4a:
                            genericI32Comparison =
                                    genericI32CompareLeft
                                            > genericI32CompareRight;
                            break;
                        case 0x4b:
                            genericI32Comparison =
                                    (genericI32CompareLeft & 0xffffffffL)
                                            > (genericI32CompareRight
                                                    & 0xffffffffL);
                            break;
                        case 0x4c:
                            genericI32Comparison =
                                    genericI32CompareLeft
                                            <= genericI32CompareRight;
                            break;
                        case 0x4d:
                            genericI32Comparison =
                                    (genericI32CompareLeft & 0xffffffffL)
                                            <= (genericI32CompareRight
                                                    & 0xffffffffL);
                            break;
                        case 0x4e:
                            genericI32Comparison =
                                    genericI32CompareLeft
                                            >= genericI32CompareRight;
                            break;
                        default:
                            genericI32Comparison =
                                    (genericI32CompareLeft & 0xffffffffL)
                                            >= (genericI32CompareRight
                                                    & 0xffffffffL);
                            break;
                    }
                    values[valueTop - 1] =
                            genericI32Comparison ? 1 : 0;
                    pc++;
                    break;
                case 0x50:
                    pushI32(pop() == 0 ? 1 : 0);
                    pc++;
                    break;
                case 0x51:
                case 0x52:
                case 0x53:
                case 0x54:
                case 0x55:
                case 0x56:
                case 0x57:
                case 0x58:
                case 0x59:
                case 0x5a:
                    compareI64(opcode);
                    pc++;
                    break;
                case 0x5b:
                case 0x5c:
                case 0x5d:
                case 0x5e:
                case 0x5f:
                case 0x60:
                    compareF32(opcode);
                    pc++;
                    break;
                case 0x61:
                case 0x62:
                case 0x63:
                case 0x64:
                case 0x65:
                case 0x66:
                    compareF64(opcode);
                    pc++;
                    break;
                case 0x67:
                    pushI32(countLeadingZerosI32(popI32()));
                    pc++;
                    break;
                case 0x68:
                    pushI32(countTrailingZerosI32(popI32()));
                    pc++;
                    break;
                case 0x69:
                    pushI32(populationCountI32(popI32()));
                    pc++;
                    break;
                case 0x6a:
                    pushI32(popI32Second() + popI32First());
                    pc++;
                    break;
                case 0x6b:
                    if (valueTop < 2) {
                        throw new WasmTrap("value stack underflow");
                    }
                    int genericSubtractRight = (int) values[--valueTop];
                    values[valueTop - 1] =
                            (int) values[valueTop - 1] - genericSubtractRight;
                    pc++;
                    break;
                case 0x6c:
                    pushI32(popI32Second() * popI32First());
                    pc++;
                    break;
                case 0x6d:
                case 0x6e:
                case 0x6f:
                case 0x70:
                    executeI32DivRem(opcode);
                    pc++;
                    break;
                case 0x71:
                    pushI32(popI32Second() & popI32First());
                    pc++;
                    break;
                case 0x72:
                    if (valueTop < 2) {
                        throw new WasmTrap("value stack underflow");
                    }
                    int genericOrRight = (int) values[--valueTop];
                    values[valueTop - 1] =
                            (int) values[valueTop - 1] | genericOrRight;
                    pc++;
                    break;
                case 0x73:
                    pushI32(popI32Second() ^ popI32First());
                    pc++;
                    break;
                case 0x74:
                    if (valueTop < 2) {
                        throw new WasmTrap("value stack underflow");
                    }
                    int genericShiftLeft = (int) values[--valueTop];
                    values[valueTop - 1] =
                            (int) values[valueTop - 1]
                                    << (genericShiftLeft & 31);
                    pc++;
                    break;
                case 0x75:
                    int i32ShiftSigned = popI32();
                    pushI32(popI32() >> (i32ShiftSigned & 31));
                    pc++;
                    break;
                case 0x76:
                    int i32ShiftUnsigned = popI32();
                    pushI32(popI32() >>> (i32ShiftUnsigned & 31));
                    pc++;
                    break;
                case 0x77:
                    int i32RotateLeft = popI32() & 31;
                    int i32RotateLeftValue = popI32();
                    pushI32((i32RotateLeftValue << i32RotateLeft)
                            | (i32RotateLeftValue >>> (32 - i32RotateLeft)));
                    pc++;
                    break;
                case 0x78:
                    int i32RotateRight = popI32() & 31;
                    int i32RotateRightValue = popI32();
                    pushI32((i32RotateRightValue >>> i32RotateRight)
                            | (i32RotateRightValue << (32 - i32RotateRight)));
                    pc++;
                    break;
                case 0x79:
                    push(countLeadingZerosI64(pop()));
                    pc++;
                    break;
                case 0x7a:
                    push(countTrailingZerosI64(pop()));
                    pc++;
                    break;
                case 0x7b:
                    push(populationCountI64(pop()));
                    pc++;
                    break;
                case 0x7c:
                    push(popSecond() + popFirst());
                    pc++;
                    break;
                case 0x7d:
                    long i64SubRight = pop();
                    push(pop() - i64SubRight);
                    pc++;
                    break;
                case 0x7e:
                    push(popSecond() * popFirst());
                    pc++;
                    break;
                case 0x7f:
                case 0x80:
                case 0x81:
                case 0x82:
                    executeI64DivRem(opcode);
                    pc++;
                    break;
                case 0x83:
                    push(popSecond() & popFirst());
                    pc++;
                    break;
                case 0x84:
                    push(popSecond() | popFirst());
                    pc++;
                    break;
                case 0x85:
                    push(popSecond() ^ popFirst());
                    pc++;
                    break;
                case 0x86:
                    long i64Shift = pop();
                    push(pop() << ((int) i64Shift & 63));
                    pc++;
                    break;
                case 0x87:
                    long i64ShiftSigned = pop();
                    push(pop() >> ((int) i64ShiftSigned & 63));
                    pc++;
                    break;
                case 0x88:
                    long i64ShiftUnsigned = pop();
                    push(pop() >>> ((int) i64ShiftUnsigned & 63));
                    pc++;
                    break;
                case 0x89:
                    int i64RotateLeft = (int) pop() & 63;
                    long i64RotateLeftValue = pop();
                    push((i64RotateLeftValue << i64RotateLeft)
                            | (i64RotateLeftValue >>> (64 - i64RotateLeft)));
                    pc++;
                    break;
                case 0x8a:
                    int i64RotateRight = (int) pop() & 63;
                    long i64RotateRightValue = pop();
                    push((i64RotateRightValue >>> i64RotateRight)
                            | (i64RotateRightValue << (64 - i64RotateRight)));
                    pc++;
                    break;
                case 0x8b:
                case 0x8c:
                case 0x8d:
                case 0x8e:
                case 0x8f:
                case 0x90:
                case 0x91:
                    unaryF32(opcode);
                    pc++;
                    break;
                case 0x92:
                case 0x93:
                case 0x94:
                case 0x95:
                    binaryF32(opcode);
                    pc++;
                    break;
                case 0x96:
                    minimumF32();
                    pc++;
                    break;
                case 0x97:
                    maximumF32();
                    pc++;
                    break;
                case 0x98:
                    copySignF32();
                    pc++;
                    break;
                case 0x99:
                case 0x9a:
                case 0x9b:
                case 0x9c:
                case 0x9d:
                case 0x9e:
                case 0x9f:
                    unaryF64(opcode);
                    pc++;
                    break;
                case 0xa0:
                case 0xa1:
                case 0xa2:
                case 0xa3:
                    binaryF64(opcode);
                    pc++;
                    break;
                case 0xa4:
                    minimumF64();
                    pc++;
                    break;
                case 0xa5:
                    maximumF64();
                    pc++;
                    break;
                case 0xa6:
                    copySignF64();
                    pc++;
                    break;
                case 0xa7:
                    pushI32((int) pop());
                    pc++;
                    break;
                case 0xa8:
                    pushI32(truncateF32Signed(Float.intBitsToFloat(popI32())));
                    pc++;
                    break;
                case 0xa9:
                    pushI32(truncateF32Unsigned(Float.intBitsToFloat(popI32())));
                    pc++;
                    break;
                case 0xaa:
                    pushI32(truncateF64Signed(Double.longBitsToDouble(pop())));
                    pc++;
                    break;
                case 0xab:
                    pushI32(truncateF64Unsigned(Double.longBitsToDouble(pop())));
                    pc++;
                    break;
                case 0xac:
                    push(popI32());
                    pc++;
                    break;
                case 0xad:
                    push(popI32() & 0xffffffffL);
                    pc++;
                    break;
                case 0xae:
                    push(truncateI64Signed((double) Float.intBitsToFloat(popI32())));
                    pc++;
                    break;
                case 0xaf:
                    push(truncateI64Unsigned((double) Float.intBitsToFloat(popI32())));
                    pc++;
                    break;
                case 0xb0:
                    push(truncateI64Signed(Double.longBitsToDouble(pop())));
                    pc++;
                    break;
                case 0xb1:
                    push(truncateI64Unsigned(Double.longBitsToDouble(pop())));
                    pc++;
                    break;
                case 0xb2:
                    pushI32(Float.floatToIntBits((float) popI32()));
                    pc++;
                    break;
                case 0xb3:
                    pushI32(Float.floatToIntBits((float) (popI32() & 0xffffffffL)));
                    pc++;
                    break;
                case 0xb4:
                    pushI32(Float.floatToIntBits((float) pop()));
                    pc++;
                    break;
                case 0xb5:
                    pushI32(Float.floatToIntBits(unsignedI64ToFloat(pop())));
                    pc++;
                    break;
                case 0xb6:
                    pushI32(Float.floatToIntBits((float) Double.longBitsToDouble(pop())));
                    pc++;
                    break;
                case 0xb7:
                    push(Double.doubleToLongBits((double) popI32()));
                    pc++;
                    break;
                case 0xb8:
                    push(Double.doubleToLongBits((double) (popI32() & 0xffffffffL)));
                    pc++;
                    break;
                case 0xb9:
                    push(Double.doubleToLongBits((double) pop()));
                    pc++;
                    break;
                case 0xba:
                    push(Double.doubleToLongBits(unsignedI64ToDouble(pop())));
                    pc++;
                    break;
                case 0xbb:
                    push(Double.doubleToLongBits((double) Float.intBitsToFloat(popI32())));
                    pc++;
                    break;
                case 0xbc:
                case 0xbe:
                    pc++;
                    break;
                case 0xbd:
                case 0xbf:
                    pc++;
                    break;
                case 0xc0:
                    pushI32((byte) popI32());
                    pc++;
                    break;
                case 0xc1:
                    pushI32((short) popI32());
                    pc++;
                    break;
                case 0xc2:
                    push((byte) pop());
                    pc++;
                    break;
                case 0xc3:
                    push((short) pop());
                    pc++;
                    break;
                case 0xc4:
                    push((int) pop());
                    pc++;
                    break;
                case WasmModule.EXECUTION_BULK_FIRST + 0x0:
                    pushI32(saturateF32ToI32Signed(Float.intBitsToFloat(popI32())));
                    pc++;
                    break;
                case WasmModule.EXECUTION_BULK_FIRST + 0x1:
                    pushI32(saturateF32ToI32Unsigned(Float.intBitsToFloat(popI32())));
                    pc++;
                    break;
                case WasmModule.EXECUTION_BULK_FIRST + 0x2:
                    pushI32(saturateF64ToI32Signed(Double.longBitsToDouble(pop())));
                    pc++;
                    break;
                case WasmModule.EXECUTION_BULK_FIRST + 0x3:
                    pushI32(saturateF64ToI32Unsigned(Double.longBitsToDouble(pop())));
                    pc++;
                    break;
                case WasmModule.EXECUTION_BULK_FIRST + 0x4:
                    push(saturateF64ToI64Signed((double) Float.intBitsToFloat(popI32())));
                    pc++;
                    break;
                case WasmModule.EXECUTION_BULK_FIRST + 0x5:
                    push(saturateF64ToI64Unsigned((double) Float.intBitsToFloat(popI32())));
                    pc++;
                    break;
                case WasmModule.EXECUTION_BULK_FIRST + 0x6:
                    push(saturateF64ToI64Signed(Double.longBitsToDouble(pop())));
                    pc++;
                    break;
                case WasmModule.EXECUTION_BULK_FIRST + 0x7:
                    push(saturateF64ToI64Unsigned(Double.longBitsToDouble(pop())));
                    pc++;
                    break;
                case WasmModule.EXECUTION_BULK_FIRST + 0x8:
                case WasmModule.EXECUTION_BULK_FIRST + 0x9:
                case WasmModule.EXECUTION_BULK_FIRST + 0xa:
                case WasmModule.EXECUTION_BULK_FIRST + 0xb:
                    executeBulkMemory(opcode, operand);
                    pc++;
                    break;
                case WasmModule.W4IR_LOCAL_LOCAL_F32_MUL + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    pushI32(Float.floatToIntBits(
                            Float.intBitsToFloat((int) locals[operand])
                                    * Float.intBitsToFloat((int) locals[auxiliary])));
                    executed += 2;
                    pc += 3;
                    break;
                case WasmModule.W4IR_LOCAL_F32_CONST_MUL + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    pushI32(Float.floatToIntBits(
                            Float.intBitsToFloat((int) locals[operand])
                                    * Float.intBitsToFloat(auxiliary)));
                    executed += 2;
                    pc += 3;
                    break;
                case WasmModule.W4IR_F32_CONST_MUL_ADD + WasmModule.W4IR_EXECUTION_OFFSET:
                    float multiplyRight = Float.intBitsToFloat(popI32());
                    float multiplyProduct = multiplyRight * Float.intBitsToFloat(operand);
                    float addLeft = Float.intBitsToFloat(popI32());
                    pushI32(Float.floatToIntBits(addLeft + multiplyProduct));
                    executed += 2;
                    pc += 3;
                    break;
                case WasmModule.W4IR_LOCAL_LOCAL_I32_AND + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    pushI32((int) locals[operand] & (int) locals[auxiliary]);
                    executed += 2;
                    pc += 3;
                    break;
                case WasmModule.W4IR_LOCAL_LOCAL_I32_ADD + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    pushI32((int) locals[operand] + (int) locals[auxiliary]);
                    executed += 2;
                    pc += 3;
                    break;
                case WasmModule.W4IR_LOCAL_I32_CONST_ADD + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    pushI32((int) locals[operand] + auxiliary);
                    executed += 2;
                    pc += 3;
                    break;
                case WasmModule.W4IR_LOCAL_I32_CONST_AND + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    pushI32((int) locals[operand] & auxiliary);
                    executed += 2;
                    pc += 3;
                    break;
                case WasmModule.W4IR_LOCAL_SET_LOCAL_LOCAL + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    int setLocalTarget = operand >>> 16;
                    int setLocalFirstSource = operand & 0xffff;
                    locals[setLocalTarget] = pop();
                    push(locals[setLocalFirstSource]);
                    push(locals[auxiliary]);
                    executed += 2;
                    pc += 3;
                    break;
                case WasmModule.W4IR_LOCAL_I32_CONST_EQ + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    pushI32((int) locals[operand] == auxiliary ? 1 : 0);
                    executed += 2;
                    pc += 3;
                    break;
                case WasmModule.W4IR_LOCAL_LOCAL_I32_CONST_AND + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    int andFirstSource = operand >>> 16;
                    int andSecondSource = operand & 0xffff;
                    push(locals[andFirstSource]);
                    pushI32((int) locals[andSecondSource] & auxiliary);
                    executed += 3;
                    pc += 4;
                    break;
                case WasmModule.W4IR_LOCAL_LOCAL_F32_DIV + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    float divideLeft = Float.intBitsToFloat((int) locals[operand]);
                    float divideRight = Float.intBitsToFloat((int) locals[auxiliary]);
                    pushI32(Float.floatToIntBits(divideLeft / divideRight));
                    executed += 2;
                    pc += 3;
                    break;
                case WasmModule.W4IR_LOCAL_SET_F32_CONST_SET + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    int setConstFirstTarget = operand >>> 16;
                    int setConstSecondTarget = operand & 0xffff;
                    locals[setConstFirstTarget] = pop();
                    locals[setConstSecondTarget] = auxiliary & 0xffffffffL;
                    executed += 2;
                    pc += 3;
                    break;
                case WasmModule.W4IR_LOCAL_LOCAL_F32_LOAD + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    int loadFirstSource = operand >>> 16;
                    int loadAddressSource = operand & 0xffff;
                    push(locals[loadFirstSource]);
                    int loadBase = (int) locals[loadAddressSource];
                    int loadMaximumBase = module.memory.length - 4;
                    if (loadBase < 0
                            || auxiliary < 0
                            || loadBase > loadMaximumBase
                            || auxiliary > loadMaximumBase - loadBase) {
                        throw new WasmTrap("out-of-bounds memory access");
                    }
                    push(loadI32(loadBase + auxiliary) & 0xffffffffL);
                    executed += 2;
                    pc += 3;
                    break;
                case WasmModule.W4IR_LOCAL_TEE_F32_MUL_ADD + WasmModule.W4IR_EXECUTION_OFFSET:
                    locals[operand] = peek();
                    float teeMultiplyRight = Float.intBitsToFloat(popI32());
                    float teeMultiplyLeft = Float.intBitsToFloat(popI32());
                    float teeAddLeft = Float.intBitsToFloat(popI32());
                    pushI32(Float.floatToIntBits(
                            teeAddLeft + teeMultiplyLeft * teeMultiplyRight));
                    executed += 2;
                    pc += 3;
                    break;
                case WasmModule.W4IR_LOCAL_F32_MUL_ADD + WasmModule.W4IR_EXECUTION_OFFSET:
                    float localMultiplyRight = Float.intBitsToFloat((int) locals[operand]);
                    float localMultiplyLeft = Float.intBitsToFloat(popI32());
                    float localAddLeft = Float.intBitsToFloat(popI32());
                    pushI32(Float.floatToIntBits(
                            localAddLeft + localMultiplyLeft * localMultiplyRight));
                    executed += 2;
                    pc += 3;
                    break;
                case WasmModule.W4IR_LOCAL_LOCAL_F32_MUL_ADD + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    float localsMultiplyLeft = Float.intBitsToFloat((int) locals[operand]);
                    float localsMultiplyRight = Float.intBitsToFloat((int) locals[auxiliary]);
                    float localsAddLeft = Float.intBitsToFloat(popI32());
                    pushI32(Float.floatToIntBits(
                            localsAddLeft + localsMultiplyLeft * localsMultiplyRight));
                    executed += 3;
                    pc += 4;
                    break;
                case WasmModule.W4IR_LOCAL_I32_CONST_EQ_BR_IF + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    executed += 3;
                    int compareBranchLocal = instruction >>> 16;
                    if ((int) locals[compareBranchLocal] == operand) {
                        if (InterpreterBuildConfig.DESCRIPTOR_SHADOW) {
                            pc = branchWithDescriptorShadow(
                                    body,
                                    body.branchDescriptorIndexAt(pc + 3),
                                    auxiliary,
                                    functionStackBase,
                                    functionType.results.length,
                                    functionControlBase);
                        } else {
                            pc = branch(
                                    auxiliary,
                                    functionStackBase,
                                    functionType.results.length,
                                    functionControlBase);
                        }
                        if (pc < 0) {
                            break executeBody;
                        }
                    } else {
                        if (InterpreterBuildConfig.DESCRIPTOR_SHADOW) {
                            verifyDescriptorFallthrough(
                                    body,
                                    body.branchDescriptorIndexAt(pc + 3),
                                    auxiliary,
                                    functionStackBase,
                                    functionType.results.length,
                                    functionControlBase,
                                    valueTop,
                                    controlTop,
                                    pc + 4,
                                    pc + 4);
                        }
                        pc += 4;
                    }
                    break;
                case WasmModule.W4IR_LOCAL_TEE_MUL_ADD_SET_LOCAL_LOCAL + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    int teeCombinedTarget = instruction >>> 16;
                    locals[teeCombinedTarget] = peek();
                    float teeCombinedMultiplyRight = Float.intBitsToFloat(popI32());
                    float teeCombinedMultiplyLeft = Float.intBitsToFloat(popI32());
                    float teeCombinedAddLeft = Float.intBitsToFloat(popI32());
                    int teeCombinedResult = Float.floatToIntBits(
                            teeCombinedAddLeft
                                    + teeCombinedMultiplyLeft * teeCombinedMultiplyRight);
                    int teeCombinedSetTarget = operand >>> 16;
                    int teeCombinedFirstSource = operand & 0xffff;
                    int teeCombinedSecondSource = auxiliary;
                    locals[teeCombinedSetTarget] = teeCombinedResult & 0xffffffffL;
                    push(locals[teeCombinedFirstSource]);
                    push(locals[teeCombinedSecondSource]);
                    executed += 5;
                    pc += 6;
                    break;
                case WasmModule.W4IR_LOCAL_MUL_ADD_SET + WasmModule.W4IR_EXECUTION_OFFSET:
                    int localSetMultiplySource = instruction >>> 16;
                    float localSetMultiplyRight =
                            Float.intBitsToFloat((int) locals[localSetMultiplySource]);
                    float localSetMultiplyLeft = Float.intBitsToFloat(popI32());
                    float localSetAddLeft = Float.intBitsToFloat(popI32());
                    int localSetResult = Float.floatToIntBits(
                            localSetAddLeft + localSetMultiplyLeft * localSetMultiplyRight);
                    locals[operand] = localSetResult & 0xffffffffL;
                    executed += 3;
                    pc += 4;
                    break;
                case WasmModule.W4IR_F32_LOAD_LOCAL_MUL_ADD + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    int loadMultiplyAddress = address(operand, 4);
                    float loadMultiplyLeft =
                            Float.intBitsToFloat(loadI32(loadMultiplyAddress));
                    float loadMultiplyRight = Float.intBitsToFloat((int) locals[auxiliary]);
                    float loadAddLeft = Float.intBitsToFloat(popI32());
                    pushI32(Float.floatToIntBits(
                            loadAddLeft + loadMultiplyLeft * loadMultiplyRight));
                    executed += 3;
                    pc += 4;
                    break;
                case WasmModule.W4IR_LOCAL_ADD_SET_BR + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    executed += 4;
                    if (InterpreterBuildConfig.DESCRIPTOR_SHADOW) {
                        pc = executeLocalAddSetBranchShadow(
                                body,
                                pc + 4,
                                instruction,
                                operand,
                                auxiliary,
                                locals,
                                functionStackBase,
                                functionType.results.length,
                                functionControlBase);
                    } else {
                        pc = executeLocalAddSetBranch(
                                instruction,
                                operand,
                                auxiliary,
                                locals,
                                functionStackBase,
                                functionType.results.length,
                                functionControlBase);
                    }
                    if (pc < 0) {
                        break executeBody;
                    }
                    break;
                case WasmModule.W4IR_LOCAL_SET_ADD_SET + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    executeLocalSetAddSet(instruction, operand, auxiliary, locals);
                    executed += 4;
                    pc += 5;
                    break;
                case WasmModule.W4IR_LOCAL_SET_LOCAL_LOCAL_F32_LOAD + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    executeLocalSetLocalLocalF32Load(
                            instruction, operand, auxiliary, locals);
                    executed += 3;
                    pc += 4;
                    break;
                case WasmModule.W4IR_LOCAL_LOCAL_F32_LOAD_LOCAL + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    executeLocalLocalF32LoadLocal(
                            instruction, operand, auxiliary, locals);
                    executed += 3;
                    pc += 4;
                    break;
                case WasmModule.W4IR_F32_LOAD_NEG + WasmModule.W4IR_EXECUTION_OFFSET:
                    executeF32LoadNeg(operand);
                    executed++;
                    pc += 2;
                    break;
                case WasmModule.W4IR_F32_LOAD_DIV + WasmModule.W4IR_EXECUTION_OFFSET:
                    executeF32LoadDiv(operand);
                    executed++;
                    pc += 2;
                    break;
                case WasmModule.W4IR_F32_DIV_TEE_MUL_ADD_SET_LOCAL_LOCAL + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    executeF32DivTeeMulAddSetLocalLocal(
                            instruction, operand, auxiliary, locals);
                    executed += 6;
                    pc += 7;
                    break;
                case WasmModule.W4IR_F32_LOAD_LOCAL_MUL_ADD_SET + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    executeF32LoadLocalMulAddSet(
                            instruction, operand, auxiliary, locals);
                    executed += 4;
                    pc += 5;
                    break;
                case WasmModule.W4IR_BR_IF_LOCAL_I32_CONST + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    this.instructionsExecuted = executed;
                    counterInField = true;
                    if (InterpreterBuildConfig.DESCRIPTOR_SHADOW) {
                        pc = executeBranchIfLocalI32ConstShadow(
                                body,
                                instruction,
                                operand,
                                auxiliary,
                                locals,
                                pc,
                                functionStackBase,
                                functionType.results.length,
                                functionControlBase);
                    } else {
                        pc = executeBranchIfLocalI32Const(
                                instruction,
                                operand,
                                auxiliary,
                                locals,
                                pc,
                                functionStackBase,
                                functionType.results.length,
                                functionControlBase);
                    }
                    executed = this.instructionsExecuted;
                    counterInField = false;
                    if (pc < 0) {
                        break executeBody;
                    }
                    break;
                case WasmModule.W4IR_LOCAL_TRIPLE_F32_STORE + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    executeLocalTripleF32Store(instruction, operand, auxiliary, locals);
                    executed += 8;
                    pc += 9;
                    break;
                case WasmModule.W4IR_LOCAL4_F32_MUL_ADD_TEE + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    executeLocal4F32MulAddTee(instruction, operand, auxiliary, locals);
                    executed += 7;
                    pc += 8;
                    break;
                case WasmModule.W4IR_F32_LOAD_NEG_INDEX_DIV + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    executeF32LoadNegIndexDiv(instruction, operand, auxiliary, locals);
                    executed += 10;
                    pc += 11;
                    break;
                case WasmModule.W4IR_TRIPLE_F32_STORE_LOCAL_LOAD_LOCAL + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    executeTripleF32StoreLocalLoadLocal(
                            instruction, operand, auxiliary, locals);
                    executed += 12;
                    pc += 13;
                    break;
                case WasmModule.W4IR_LOCAL_TEE_MUL_ADD_SET_LOAD_MUL_ADD + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    executeLocalTeeMulAddSetLoadMulAdd(
                            instruction, operand, auxiliary, locals);
                    executed += 9;
                    pc += 10;
                    break;
                case WasmModule.W4IR_LOCAL_SET_DUAL_ADD_SET + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    executeLocalSetDualAddSet(instruction, operand, auxiliary, locals);
                    executed += 8;
                    pc += 9;
                    break;
                case WasmModule.W4IR_COUNTED_F32_TRACE + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    this.instructionsExecuted = executed;
                    counterInField = true;
                    if (traceExecutorEnabled) {
                        pc = executeCountedF32Trace(
                                body,
                                pc,
                                instruction,
                                operand,
                                auxiliary,
                                locals,
                                functionStackBase,
                                functionType.results.length,
                                functionControlBase);
                    } else {
                        if (InterpreterBuildConfig.DESCRIPTOR_SHADOW) {
                            pc = executeCountedF32TraceCompareShadow(
                                    body,
                                    pc,
                                    instruction,
                                    operand,
                                    auxiliary,
                                    locals,
                                    functionStackBase,
                                    functionType.results.length,
                                    functionControlBase);
                        } else {
                            pc = executeCountedF32TraceCompare(
                                    pc,
                                    instruction,
                                    operand,
                                    auxiliary,
                                    locals,
                                    functionStackBase,
                                    functionType.results.length,
                                    functionControlBase);
                        }
                    }
                    executed = this.instructionsExecuted;
                    counterInField = false;
                    if (pc < 0) {
                        break executeBody;
                    }
                    break;
                case WasmModule.W4IR_F32_FLOOR_INTRINSIC + WasmModule.W4IR_EXECUTION_OFFSET:
                case WasmModule.W4IR_F32_SIN_INTRINSIC + WasmModule.W4IR_EXECUTION_OFFSET:
                    this.instructionsExecuted = executed;
                    counterInField = true;
                    executeNumericIntrinsicInstruction(
                            operand,
                            opcode == WasmModule.W4IR_F32_SIN_INTRINSIC + WasmModule.W4IR_EXECUTION_OFFSET);
                    executed = this.instructionsExecuted;
                    counterInField = false;
                    pc++;
                    break;
                case WasmModule.W4IR_I32_LOAD_LOCAL_TEE + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    pushI32(loadI32(address(operand, 4)));
                    executed++;
                    if (executed > instructionLimit) {
                        throw new WasmTrap("instruction budget exhausted");
                    }
                    locals[auxiliary] = peek();
                    pc += 2;
                    break;
                case WasmModule.W4IR_LOCAL_LOCAL + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    push(locals[operand]);
                    push(locals[auxiliary]);
                    executed++;
                    pc += 2;
                    break;
                case WasmModule.W4IR_LOCAL_I32_CONST + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    push(locals[operand]);
                    push(auxiliary);
                    executed++;
                    pc += 2;
                    break;
                case WasmModule.W4IR_LOCAL_F32_CONST + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    push(locals[operand]);
                    push(auxiliary & 0xffffffffL);
                    executed++;
                    pc += 2;
                    break;
                case WasmModule.W4IR_F32_MUL_CONST + WasmModule.W4IR_EXECUTION_OFFSET:
                    pushI32(Float.floatToIntBits(
                            Float.intBitsToFloat(popI32()) * Float.intBitsToFloat(operand)));
                    executed++;
                    pc += 2;
                    break;
                case WasmModule.W4IR_F32_MUL_ADD + WasmModule.W4IR_EXECUTION_OFFSET:
                    float pairMultiplyRight = Float.intBitsToFloat(popI32());
                    float pairMultiplyLeft = Float.intBitsToFloat(popI32());
                    float pairProduct = pairMultiplyLeft * pairMultiplyRight;
                    float pairAddLeft = Float.intBitsToFloat(popI32());
                    pushI32(Float.floatToIntBits(pairAddLeft + pairProduct));
                    executed++;
                    pc += 2;
                    break;
                case WasmModule.W4IR_I32_ADD_CONST + WasmModule.W4IR_EXECUTION_OFFSET:
                    pushI32(popI32() + operand);
                    executed++;
                    pc += 2;
                    break;
                case WasmModule.W4IR_I32_AND_CONST + WasmModule.W4IR_EXECUTION_OFFSET:
                    pushI32(popI32() & operand);
                    executed++;
                    pc += 2;
                    break;
                case WasmModule.W4IR_LOCAL_SET_GET + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    locals[operand] = pop();
                    push(locals[auxiliary]);
                    executed++;
                    pc += 2;
                    break;
                case WasmModule.W4IR_LOCAL_SET_F32_CONST + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    locals[auxiliary] = operand & 0xffffffffL;
                    executed++;
                    pc += 2;
                    break;
                case WasmModule.W4IR_F32_MUL_LOCAL + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    binaryF32(0x94);
                    push(locals[auxiliary]);
                    executed++;
                    pc += 2;
                    break;
                case WasmModule.W4IR_LOCAL_I32_CONST_ADD_SET + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    while (true) {
                        int addSource = operand >>> 16;
                        int addTarget = operand & 0xffff;
                        locals[addTarget] = (int) locals[addSource] + auxiliary;
                        executed += 3;
                        pc += 4;
                        if (pc >= instructionCount) {
                            break;
                        }
                        int nextAddOffset = pc * WasmModule.W4IR_STRIDE;
                        if (nextAddOffset < codePageBase
                                || nextAddOffset + 2 >= codePageBase + code.length) {
                            code = body.codePage(nextAddOffset);
                            codePageBase = body.codePageBase(nextAddOffset);
                        }
                        int nextAddPageOffset = nextAddOffset - codePageBase;
                        if ((code[nextAddPageOffset] & 0xffff)
                                != WasmModule.W4IR_LOCAL_I32_CONST_ADD_SET + WasmModule.W4IR_EXECUTION_OFFSET) {
                            break;
                        }
                        operand = code[nextAddPageOffset + 1];
                        auxiliary = code[nextAddPageOffset + 2];
                        executed++;
                        if (executed > instructionLimit) {
                            throw new WasmTrap("instruction budget exhausted");
                        }
                    }
                    break;
                case WasmModule.W4IR_LOCAL_LOCAL_F32_STORE + WasmModule.W4IR_EXECUTION_OFFSET:
                    auxiliary = code[pageOffset + 2];
                    while (true) {
                        int storeAddressLocal = operand >>> 16;
                        int storeValueLocal = operand & 0xffff;
                        int storeBase = (int) locals[storeAddressLocal];
                        int storeMaximumBase = module.memory.length - 4;
                        if (storeBase < 0
                                || auxiliary < 0
                                || storeBase > storeMaximumBase
                                || auxiliary > storeMaximumBase - storeBase) {
                            throw new WasmTrap("out-of-bounds memory access");
                        }
                        storeI32(storeBase + auxiliary, (int) locals[storeValueLocal]);
                        executed += 2;
                        pc += 3;
                        if (pc >= instructionCount) {
                            break;
                        }
                        int nextStoreOffset = pc * WasmModule.W4IR_STRIDE;
                        if (nextStoreOffset < codePageBase
                                || nextStoreOffset + 2 >= codePageBase + code.length) {
                            code = body.codePage(nextStoreOffset);
                            codePageBase = body.codePageBase(nextStoreOffset);
                        }
                        int nextStorePageOffset = nextStoreOffset - codePageBase;
                        if ((code[nextStorePageOffset] & 0xffff)
                                != WasmModule.W4IR_LOCAL_LOCAL_F32_STORE + WasmModule.W4IR_EXECUTION_OFFSET) {
                            break;
                        }
                        operand = code[nextStorePageOffset + 1];
                        auxiliary = code[nextStorePageOffset + 2];
                        executed++;
                        if (executed > instructionLimit) {
                            throw new WasmTrap("instruction budget exhausted");
                        }
                    }
                    break;
                default:
                    throw new WasmTrap(
                            "opcode not implemented: 0x"
                                    + Integer.toHexString(
                                            WasmModule.originalOpcode(opcode)));
            }
        }
        throw new WasmTrap("function ended without end opcode");
        } finally {
            if (!counterInField) {
                this.instructionsExecuted = executed;
            }
        }
    }

    private void profileInstruction(
            int functionIndex,
            int pc,
            int previousPreviousOpcode,
            int previousOpcode,
            int opcode) {
        functionDispatchCounts[functionIndex]++;
        opcodeCounts[opcode]++;
        if (previousOpcode >= 0) {
            opcodePairCounts.add(pairKey(previousOpcode, opcode));
            if (previousPreviousOpcode >= 0) {
                opcodeTripleCounts.add(
                        tripleKey(previousPreviousOpcode, previousOpcode, opcode));
            }
        }
        profileCompactCandidate(functionIndex, pc);
    }

    private void profileCompactCandidate(int functionIndex, int pc) {
        byte[] dispatchLengths = compactProfileDispatchLengths[functionIndex];
        if (dispatchLengths == null || pc < 0 || pc >= dispatchLengths.length) {
            return;
        }
        int dispatchLength = dispatchLengths[pc] & 0xff;
        int instructionLength = compactProfileInstructionLengths[functionIndex][pc] & 0xffff;
        int reason = compactProfileBreakReasons[functionIndex][pc] & 0xff;
        int breakOpcode = compactProfileBreakOpcodes[functionIndex][pc];
        compactProfileCandidates++;
        compactProfileBreakCounts[reason]++;
        if (breakOpcode != 0xffff) {
            compactProfileBreakOpcodeCounts[breakOpcode]++;
        }
        compactProfileCandidateDispatchLengths[dispatchLength]++;
        compactProfileCandidateInstructionLengths[
                boundedProfileInstructionLength(instructionLength)]++;
        if (compactBlockEnds[functionIndex][pc] > pc) {
            compactProfileAcceptedCandidates++;
            compactProfileAcceptedDispatchLengths[dispatchLength]++;
            compactProfileAcceptedInstructionLengths[
                    boundedProfileInstructionLength(instructionLength)]++;
        } else {
            compactProfileRejectionCounts[reason]++;
        }
    }

    private void prepareCompactProfileMetadata() {
        int functionIndex;
        for (functionIndex = module.imports.length;
                functionIndex < module.functions.length;
                functionIndex++) {
            WasmModule.FunctionBody body = module.functions[functionIndex];
            if (body == null || body.code == null) {
                continue;
            }
            int instructionCount = body.instructionCount();
            byte[] dispatchLengths = new byte[instructionCount];
            short[] instructionLengths = new short[instructionCount];
            byte[] reasons = new byte[instructionCount];
            char[] breakOpcodes = new char[instructionCount];
            compactBlockEnds[functionIndex] =
                    buildCompactBlockEnds(
                            body,
                            dispatchLengths,
                            instructionLengths,
                            reasons,
                            breakOpcodes);
            compactProfileDispatchLengths[functionIndex] = dispatchLengths;
            compactProfileInstructionLengths[functionIndex] = instructionLengths;
            compactProfileBreakReasons[functionIndex] = reasons;
            compactProfileBreakOpcodes[functionIndex] = breakOpcodes;
        }
    }

    private int[] compactBlockEnds(
            int functionIndex, WasmModule.FunctionBody body) {
        int[] ends = compactBlockEnds[functionIndex];
        if (ends == null) {
            ends = buildCompactBlockEnds(body, null, null, null, null);
            compactBlockEnds[functionIndex] = ends;
        }
        return ends;
    }

    private int[] buildCompactBlockEnds(
            WasmModule.FunctionBody body,
            byte[] profileDispatchLengths,
            short[] profileInstructionLengths,
            byte[] profileBreakReasons,
            char[] profileBreakOpcodes) {
        int instructionCount = body.instructionCount();
        int[] ends = new int[instructionCount];
        boolean[] branchTargets = new boolean[instructionCount];
        if (instructionCount != 0) {
            branchTargets[0] = true;
        }
        int pc;
        for (pc = 0; pc < instructionCount; pc++) {
            int offset = pc * WasmModule.W4IR_STRIDE;
            int opcode =
                    WasmModule.originalOpcode(body.code[offset] & 0xffff);
            if (opcode == WasmModule.LOOP && pc + 1 < instructionCount) {
                branchTargets[pc + 1] = true;
            }
            if (opcode == WasmModule.BLOCK
                    || opcode == WasmModule.LOOP
                    || opcode == WasmModule.IF) {
                int end = body.code[offset + 1] + 1;
                if (end >= 0 && end < instructionCount) {
                    branchTargets[end] = true;
                }
                if (opcode == WasmModule.IF) {
                    int otherwise = body.code[offset + 2] + 1;
                    if (otherwise > 0 && otherwise < instructionCount) {
                        branchTargets[otherwise] = true;
                    }
                }
            }
        }

        for (pc = 0; pc < instructionCount; pc++) {
            int cursor = pc;
            int dispatchCount = 0;
            int breakReason;
            int breakOpcode = -1;
            while (true) {
                if (cursor >= instructionCount) {
                    breakReason = COMPACT_BREAK_END;
                    break;
                }
                if (dispatchCount >= PROFILE_MAX_COMPACT_DISPATCHES) {
                    breakReason = COMPACT_BREAK_DISPATCH_LIMIT;
                    breakOpcode =
                            WasmModule.originalOpcode(
                                    body.code[cursor * WasmModule.W4IR_STRIDE]
                                            & 0xffff);
                    break;
                }
                if (cursor != pc && branchTargets[cursor]) {
                    breakReason = COMPACT_BREAK_BRANCH_TARGET;
                    breakOpcode =
                            WasmModule.originalOpcode(
                                    body.code[cursor * WasmModule.W4IR_STRIDE]
                                            & 0xffff);
                    break;
                }
                int executionOpcode =
                        body.code[cursor * WasmModule.W4IR_STRIDE] & 0xffff;
                int opcode = WasmModule.originalOpcode(executionOpcode);
                if (!isCompactOpcode(executionOpcode)) {
                    breakReason = COMPACT_BREAK_INELIGIBLE_OPCODE;
                    breakOpcode = opcode;
                    break;
                }
                int span = compactOpcodeSpan(executionOpcode);
                if (span <= 0 || span > instructionCount - cursor) {
                    breakReason = COMPACT_BREAK_INVALID_SPAN;
                    breakOpcode = opcode;
                    break;
                }
                cursor += span;
                dispatchCount++;
            }
            if (dispatchCount >= 4) {
                ends[pc] = cursor;
            }
            if (profileDispatchLengths != null) {
                profileDispatchLengths[pc] = (byte) dispatchCount;
                profileInstructionLengths[pc] = (short) (cursor - pc);
                profileBreakReasons[pc] = (byte) breakReason;
                profileBreakOpcodes[pc] =
                        (char) (breakOpcode < 0 ? 0xffff : breakOpcode);
            }
        }
        return ends;
    }

    private static int boundedProfileInstructionLength(int length) {
        return length > PROFILE_MAX_COMPACT_INSTRUCTIONS
                ? PROFILE_MAX_COMPACT_INSTRUCTIONS
                : length;
    }

    private static long pairKey(int firstOpcode, int secondOpcode) {
        return ((long) firstOpcode << 16) | (long) secondOpcode;
    }

    private static long tripleKey(
            int firstOpcode, int secondOpcode, int thirdOpcode) {
        return ((long) firstOpcode << 32)
                | ((long) secondOpcode << 16)
                | (long) thirdOpcode;
    }

    private static long profileArrayValue(long[] values, int index) {
        if (values == null || index < 0 || index >= values.length) {
            return 0;
        }
        return values[index];
    }

    private static void clearProfileArray(long[] values) {
        int index;
        for (index = 0; index < values.length; index++) {
            values[index] = 0;
        }
    }

    private boolean isCompactOpcode(int opcode) {
        if ((opcode >= WasmModule.EXECUTION_W4IR_FIRST
                        && opcode
                                <= WasmModule.W4IR_LOCAL_LOCAL_F32_STORE
                                        + WasmModule.W4IR_EXECUTION_OFFSET)
                || (opcode
                                >= WasmModule.W4IR_LOCAL_TEE_MUL_ADD_SET_LOCAL_LOCAL
                                        + WasmModule.W4IR_EXECUTION_OFFSET
                        && opcode
                                <= WasmModule.W4IR_F32_LOAD_LOCAL_MUL_ADD
                                        + WasmModule.W4IR_EXECUTION_OFFSET)
                || (opcode
                                >= WasmModule.W4IR_LOCAL_SET_ADD_SET
                                        + WasmModule.W4IR_EXECUTION_OFFSET
                        && opcode
                                <= WasmModule.W4IR_F32_LOAD_LOCAL_MUL_ADD_SET
                                        + WasmModule.W4IR_EXECUTION_OFFSET)
                || (opcode
                                >= WasmModule.W4IR_LOCAL_TRIPLE_F32_STORE
                                        + WasmModule.W4IR_EXECUTION_OFFSET
                        && opcode
                                <= WasmModule.W4IR_LOCAL_SET_DUAL_ADD_SET
                                        + WasmModule.W4IR_EXECUTION_OFFSET)
                || opcode
                        == WasmModule.W4IR_I32_LOAD_LOCAL_TEE
                                + WasmModule.W4IR_EXECUTION_OFFSET) {
            return true;
        }
        if (directNumericIntrinsicsEnabled
                && (opcode == WasmModule.W4IR_F32_FLOOR_INTRINSIC + WasmModule.W4IR_EXECUTION_OFFSET
                        || opcode == WasmModule.W4IR_F32_SIN_INTRINSIC + WasmModule.W4IR_EXECUTION_OFFSET)) {
            return true;
        }
        if (integerCompactOpcodesEnabled) {
            switch (opcode) {
                case 0x28:
                case 0x36:
                case 0x47:
                case 0x48:
                case 0x4c:
                case 0x4d:
                case 0x4e:
                    return true;
                default:
                    break;
            }
        }
        switch (opcode) {
            case 0x1b:
            case 0x20:
            case 0x21:
            case 0x22:
            case 0x23:
            case 0x24:
            case 0x2a:
            case 0x2d:
            case 0x2f:
            case 0x37:
            case 0x38:
            case 0x3a:
            case 0x3b:
            case 0x41:
            case 0x42:
            case 0x43:
            case 0x45:
            case 0x46:
            case 0x49:
            case 0x4a:
            case 0x4b:
            case 0x4f:
            case 0x5d:
            case 0x5e:
            case 0x60:
            case 0x6a:
            case 0x6b:
            case 0x6c:
            case 0x71:
            case 0x72:
            case 0x73:
            case 0x74:
            case 0x75:
            case 0x76:
            case 0x84:
            case 0x86:
            case 0x8b:
            case 0x8c:
            case 0x92:
            case 0x93:
            case 0x94:
            case 0x95:
            case 0x97:
            case 0xa8:
            case 0xa9:
            case 0xad:
            case 0xb2:
            case 0xbc:
            case 0xbe:
                return true;
            default:
                return false;
        }
    }

    private int compactOpcodeSpan(int opcode) {
        switch (opcode) {
            case WasmModule.W4IR_LOCAL_LOCAL_F32_MUL + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_LOCAL_F32_CONST_MUL + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_F32_CONST_MUL_ADD + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_LOCAL_LOCAL_I32_AND + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_LOCAL_LOCAL_I32_ADD + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_LOCAL_I32_CONST_ADD + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_LOCAL_I32_CONST_AND + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_LOCAL_SET_LOCAL_LOCAL + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_LOCAL_I32_CONST_EQ + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_LOCAL_LOCAL_F32_DIV + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_LOCAL_SET_F32_CONST_SET + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_LOCAL_LOCAL_F32_LOAD + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_LOCAL_TEE_F32_MUL_ADD + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_LOCAL_F32_MUL_ADD + WasmModule.W4IR_EXECUTION_OFFSET:
                return 3;
            case WasmModule.W4IR_LOCAL_LOCAL_I32_CONST_AND + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_LOCAL_LOCAL_F32_MUL_ADD + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_LOCAL_I32_CONST_ADD_SET + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_LOCAL_MUL_ADD_SET + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_F32_LOAD_LOCAL_MUL_ADD + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_LOCAL_SET_LOCAL_LOCAL_F32_LOAD + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_LOCAL_LOCAL_F32_LOAD_LOCAL + WasmModule.W4IR_EXECUTION_OFFSET:
                return 4;
            case WasmModule.W4IR_LOCAL_LOCAL + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_LOCAL_I32_CONST + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_LOCAL_F32_CONST + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_F32_MUL_CONST + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_F32_MUL_ADD + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_I32_ADD_CONST + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_I32_AND_CONST + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_LOCAL_SET_GET + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_LOCAL_SET_F32_CONST + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_F32_MUL_LOCAL + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_F32_LOAD_NEG + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_F32_LOAD_DIV + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_I32_LOAD_LOCAL_TEE + WasmModule.W4IR_EXECUTION_OFFSET:
                return 2;
            case WasmModule.W4IR_LOCAL_LOCAL_F32_STORE + WasmModule.W4IR_EXECUTION_OFFSET:
                return 3;
            case WasmModule.W4IR_LOCAL_TEE_MUL_ADD_SET_LOCAL_LOCAL + WasmModule.W4IR_EXECUTION_OFFSET:
                return 6;
            case WasmModule.W4IR_LOCAL_SET_ADD_SET + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_F32_LOAD_LOCAL_MUL_ADD_SET + WasmModule.W4IR_EXECUTION_OFFSET:
                return 5;
            case WasmModule.W4IR_F32_DIV_TEE_MUL_ADD_SET_LOCAL_LOCAL + WasmModule.W4IR_EXECUTION_OFFSET:
                return 7;
            case WasmModule.W4IR_LOCAL_TRIPLE_F32_STORE + WasmModule.W4IR_EXECUTION_OFFSET:
                return 9;
            case WasmModule.W4IR_LOCAL4_F32_MUL_ADD_TEE + WasmModule.W4IR_EXECUTION_OFFSET:
                return 8;
            case WasmModule.W4IR_F32_LOAD_NEG_INDEX_DIV + WasmModule.W4IR_EXECUTION_OFFSET:
                return 11;
            case WasmModule.W4IR_TRIPLE_F32_STORE_LOCAL_LOAD_LOCAL + WasmModule.W4IR_EXECUTION_OFFSET:
                return 13;
            case WasmModule.W4IR_LOCAL_TEE_MUL_ADD_SET_LOAD_MUL_ADD + WasmModule.W4IR_EXECUTION_OFFSET:
                return 10;
            case WasmModule.W4IR_LOCAL_SET_DUAL_ADD_SET + WasmModule.W4IR_EXECUTION_OFFSET:
                return 9;
            case WasmModule.W4IR_F32_FLOOR_INTRINSIC + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_F32_SIN_INTRINSIC + WasmModule.W4IR_EXECUTION_OFFSET:
                return 1;
            default:
                return 1;
        }
    }

    private void executeCompactBlock(int[] code, int pc, int end, long[] locals) {
        while (pc < end) {
            int offset = pc * WasmModule.W4IR_STRIDE;
            int instruction = code[offset];
            int opcode = instruction & 0xffff;
            int operand = code[offset + 1];
            int auxiliary = code[offset + 2];
            // Standard tokens have span one; fused handlers return their fixed span.
            int span = 1;
            boolean accountAfterHandler =
                    opcode >= WasmModule.EXECUTION_W4IR_FIRST
                            && opcode
                                    < WasmModule.W4IR_I32_LOAD_LOCAL_TEE
                                            + WasmModule.W4IR_EXECUTION_OFFSET;
            if (!accountAfterHandler) {
                instructionsExecuted++;
                if (InterpreterBuildConfig.DIAGNOSTIC_COUNTERS) {
                    compactInstructionsExecuted++;
                }
                if (instructionsExecuted > instructionLimit) {
                    throw new WasmTrap("instruction budget exhausted");
                }
            }
            switch (opcode) {
                case 0x1b:
                    if (valueTop < 3) {
                        throw new WasmTrap("value stack underflow");
                    }
                    int selectCondition = (int) values[--valueTop];
                    long selectSecond = values[--valueTop];
                    long selectFirst = values[valueTop - 1];
                    values[valueTop - 1] =
                            selectCondition != 0 ? selectFirst : selectSecond;
                    break;
                case 0x20:
                    if (valueTop >= values.length) {
                        throw new WasmTrap("value stack exhausted");
                    }
                    values[valueTop++] = locals[operand];
                    break;
                case 0x21:
                    if (valueTop <= 0) {
                        throw new WasmTrap("value stack underflow");
                    }
                    locals[operand] = values[--valueTop];
                    break;
                case 0x22:
                    if (valueTop <= 0) {
                        throw new WasmTrap("value stack underflow");
                    }
                    locals[operand] = values[valueTop - 1];
                    break;
                case 0x23:
                    push(module.globals[operand]);
                    break;
                case 0x24:
                    module.globals[operand] = pop();
                    break;
                case 0x28:
                    if (valueTop <= 0) {
                        throw new WasmTrap("value stack underflow");
                    }
                    int loadI32Address =
                            checkedAddress((int) values[valueTop - 1], operand, 4);
                    values[valueTop - 1] = loadI32(loadI32Address);
                    break;
                case WasmModule.W4IR_I32_LOAD_LOCAL_TEE
                        + WasmModule.W4IR_EXECUTION_OFFSET:
                    if (valueTop <= 0) {
                        throw new WasmTrap("value stack underflow");
                    }
                    int fusedLoadAddress =
                            checkedAddress((int) values[valueTop - 1], operand, 4);
                    values[valueTop - 1] = loadI32(fusedLoadAddress);
                    instructionsExecuted++;
                    if (InterpreterBuildConfig.DIAGNOSTIC_COUNTERS) {
                        compactInstructionsExecuted++;
                    }
                    if (instructionsExecuted > instructionLimit) {
                        throw new WasmTrap("instruction budget exhausted");
                    }
                    locals[auxiliary] = values[valueTop - 1];
                    span = 2;
                    break;
                case 0x2a:
                    if (valueTop <= 0) {
                        throw new WasmTrap("value stack underflow");
                    }
                    int loadF32Address =
                            checkedAddress((int) values[valueTop - 1], operand, 4);
                    values[valueTop - 1] = loadI32(loadF32Address) & 0xffffffffL;
                    break;
                case 0x2d:
                    pushI32(module.memory[address(operand, 1)] & 0xff);
                    break;
                case 0x2f:
                    pushI32(loadU16(address(operand, 2)));
                    break;
                case 0x36:
                    int compactI32StoreValue = popI32();
                    storeI32(address(operand, 4), compactI32StoreValue);
                    break;
                case 0x37:
                    long storeI64Value = pop();
                    storeI64(address(operand, 8), storeI64Value);
                    break;
                case 0x38:
                    int storeF32Value = popI32();
                    storeI32(address(operand, 4), storeF32Value);
                    break;
                case 0x3a:
                    int storeI8Value = popI32();
                    module.memory[address(operand, 1)] = (byte) storeI8Value;
                    break;
                case 0x3b:
                    int storeI16Value = popI32();
                    storeU16(address(operand, 2), storeI16Value);
                    break;
                case 0x41:
                case 0x42:
                case 0x43:
                    if (valueTop >= values.length) {
                        throw new WasmTrap("value stack exhausted");
                    }
                    values[valueTop++] =
                            (operand & 0xffffffffL) | ((long) auxiliary << 32);
                    break;
                case 0x45:
                    pushI32(popI32() == 0 ? 1 : 0);
                    break;
                case 0x46:
                case 0x47:
                case 0x48:
                case 0x49:
                case 0x4a:
                case 0x4b:
                case 0x4c:
                case 0x4d:
                case 0x4e:
                case 0x4f:
                    if (valueTop < 2) {
                        throw new WasmTrap("value stack underflow");
                    }
                    int compactI32Right = (int) values[--valueTop];
                    int compactI32Left = (int) values[valueTop - 1];
                    boolean compactI32Comparison;
                    switch (opcode) {
                        case 0x46:
                            compactI32Comparison =
                                    compactI32Left == compactI32Right;
                            break;
                        case 0x47:
                            compactI32Comparison =
                                    compactI32Left != compactI32Right;
                            break;
                        case 0x48:
                            compactI32Comparison =
                                    compactI32Left < compactI32Right;
                            break;
                        case 0x49:
                            compactI32Comparison =
                                    (compactI32Left & 0xffffffffL)
                                            < (compactI32Right & 0xffffffffL);
                            break;
                        case 0x4a:
                            compactI32Comparison =
                                    compactI32Left > compactI32Right;
                            break;
                        case 0x4b:
                            compactI32Comparison =
                                    (compactI32Left & 0xffffffffL)
                                            > (compactI32Right & 0xffffffffL);
                            break;
                        case 0x4c:
                            compactI32Comparison =
                                    compactI32Left <= compactI32Right;
                            break;
                        case 0x4d:
                            compactI32Comparison =
                                    (compactI32Left & 0xffffffffL)
                                            <= (compactI32Right & 0xffffffffL);
                            break;
                        case 0x4e:
                            compactI32Comparison =
                                    compactI32Left >= compactI32Right;
                            break;
                        default:
                            compactI32Comparison =
                                    (compactI32Left & 0xffffffffL)
                                            >= (compactI32Right & 0xffffffffL);
                            break;
                    }
                    values[valueTop - 1] =
                            compactI32Comparison ? 1 : 0;
                    break;
                case 0x5d:
                case 0x5e:
                case 0x60:
                    compareF32(opcode);
                    break;
                case 0x6a:
                    if (valueTop < 2) {
                        throw new WasmTrap("value stack underflow");
                    }
                    int addRight = (int) values[--valueTop];
                    values[valueTop - 1] = (int) values[valueTop - 1] + addRight;
                    break;
                case 0x6b:
                    if (valueTop < 2) {
                        throw new WasmTrap("value stack underflow");
                    }
                    int subtractRight = (int) values[--valueTop];
                    values[valueTop - 1] =
                            (int) values[valueTop - 1] - subtractRight;
                    break;
                case 0x6c:
                    if (valueTop < 2) {
                        throw new WasmTrap("value stack underflow");
                    }
                    int multiplyRightI32 = (int) values[--valueTop];
                    values[valueTop - 1] =
                            (int) values[valueTop - 1] * multiplyRightI32;
                    break;
                case 0x71:
                    if (valueTop < 2) {
                        throw new WasmTrap("value stack underflow");
                    }
                    int andRight = (int) values[--valueTop];
                    values[valueTop - 1] = (int) values[valueTop - 1] & andRight;
                    break;
                case 0x72:
                    if (valueTop < 2) {
                        throw new WasmTrap("value stack underflow");
                    }
                    int orRight = (int) values[--valueTop];
                    values[valueTop - 1] = (int) values[valueTop - 1] | orRight;
                    break;
                case 0x73:
                    if (valueTop < 2) {
                        throw new WasmTrap("value stack underflow");
                    }
                    int xorRight = (int) values[--valueTop];
                    values[valueTop - 1] = (int) values[valueTop - 1] ^ xorRight;
                    break;
                case 0x74:
                    if (valueTop < 2) {
                        throw new WasmTrap("value stack underflow");
                    }
                    int shiftLeft = (int) values[--valueTop];
                    values[valueTop - 1] =
                            (int) values[valueTop - 1] << (shiftLeft & 31);
                    break;
                case 0x75:
                    if (valueTop < 2) {
                        throw new WasmTrap("value stack underflow");
                    }
                    int shiftRightSigned = (int) values[--valueTop];
                    values[valueTop - 1] =
                            (int) values[valueTop - 1] >> (shiftRightSigned & 31);
                    break;
                case 0x76:
                    if (valueTop < 2) {
                        throw new WasmTrap("value stack underflow");
                    }
                    int shiftRightUnsigned = (int) values[--valueTop];
                    values[valueTop - 1] =
                            (int) values[valueTop - 1] >>> (shiftRightUnsigned & 31);
                    break;
                case 0x84:
                    push(popSecond() | popFirst());
                    break;
                case 0x86:
                    long shiftI64 = pop();
                    push(pop() << ((int) shiftI64 & 63));
                    break;
                case 0x8b:
                case 0x8c:
                    unaryF32(opcode);
                    break;
                case 0x92:
                case 0x93:
                case 0x94:
                case 0x95:
                    if (valueTop < 2) {
                        throw new WasmTrap("value stack underflow");
                    }
                    float compactF32Right =
                            Float.intBitsToFloat((int) values[--valueTop]);
                    float compactF32Left =
                            Float.intBitsToFloat((int) values[valueTop - 1]);
                    float compactF32Result;
                    if (opcode == 0x92) {
                        compactF32Result = compactF32Left + compactF32Right;
                    } else if (opcode == 0x93) {
                        compactF32Result = compactF32Left - compactF32Right;
                    } else if (opcode == 0x94) {
                        compactF32Result = compactF32Left * compactF32Right;
                    } else {
                        compactF32Result = compactF32Left / compactF32Right;
                    }
                    values[valueTop - 1] = Float.floatToIntBits(compactF32Result);
                    break;
                case 0x97:
                    maximumF32();
                    break;
                case 0xa8:
                    pushI32(truncateF32Signed(Float.intBitsToFloat(popI32())));
                    break;
                case 0xa9:
                    pushI32(truncateF32Unsigned(Float.intBitsToFloat(popI32())));
                    break;
                case 0xad:
                    push(popI32() & 0xffffffffL);
                    break;
                case 0xb2:
                    pushI32(Float.floatToIntBits((float) popI32()));
                    break;
                case 0xbc:
                case 0xbe:
                    break;
                default:
                    span = executeCompactFused(
                            opcode, instruction, operand, auxiliary, locals);
                    break;
            }
            if (accountAfterHandler) {
                instructionsExecuted += span;
                if (InterpreterBuildConfig.DIAGNOSTIC_COUNTERS) {
                    compactInstructionsExecuted += span;
                }
                if (instructionsExecuted > instructionLimit) {
                    throw new WasmTrap("instruction budget exhausted");
                }
            }
            pc += span;
        }
    }

    private int executeCompactFused(
            int opcode, int instruction, int operand, int auxiliary, long[] locals) {
        switch (opcode) {
            case WasmModule.W4IR_LOCAL_LOCAL_F32_MUL + WasmModule.W4IR_EXECUTION_OFFSET:
                pushI32(Float.floatToIntBits(
                        Float.intBitsToFloat((int) locals[operand])
                                * Float.intBitsToFloat((int) locals[auxiliary])));
                return 3;
            case WasmModule.W4IR_LOCAL_F32_CONST_MUL + WasmModule.W4IR_EXECUTION_OFFSET:
                pushI32(Float.floatToIntBits(
                        Float.intBitsToFloat((int) locals[operand])
                                * Float.intBitsToFloat(auxiliary)));
                return 3;
            case WasmModule.W4IR_F32_CONST_MUL_ADD + WasmModule.W4IR_EXECUTION_OFFSET:
                float constantMultiplyRight = Float.intBitsToFloat(popI32());
                float constantProduct =
                        constantMultiplyRight * Float.intBitsToFloat(operand);
                float constantAddLeft = Float.intBitsToFloat(popI32());
                pushI32(Float.floatToIntBits(constantAddLeft + constantProduct));
                return 3;
            case WasmModule.W4IR_LOCAL_LOCAL_I32_AND + WasmModule.W4IR_EXECUTION_OFFSET:
                pushI32((int) locals[operand] & (int) locals[auxiliary]);
                return 3;
            case WasmModule.W4IR_LOCAL_LOCAL_I32_ADD + WasmModule.W4IR_EXECUTION_OFFSET:
                pushI32((int) locals[operand] + (int) locals[auxiliary]);
                return 3;
            case WasmModule.W4IR_LOCAL_I32_CONST_ADD + WasmModule.W4IR_EXECUTION_OFFSET:
                pushI32((int) locals[operand] + auxiliary);
                return 3;
            case WasmModule.W4IR_LOCAL_I32_CONST_AND + WasmModule.W4IR_EXECUTION_OFFSET:
                pushI32((int) locals[operand] & auxiliary);
                return 3;
            case WasmModule.W4IR_LOCAL_SET_LOCAL_LOCAL + WasmModule.W4IR_EXECUTION_OFFSET:
                int setTarget = operand >>> 16;
                int firstSource = operand & 0xffff;
                locals[setTarget] = pop();
                push(locals[firstSource]);
                push(locals[auxiliary]);
                return 3;
            case WasmModule.W4IR_LOCAL_I32_CONST_EQ + WasmModule.W4IR_EXECUTION_OFFSET:
                pushI32((int) locals[operand] == auxiliary ? 1 : 0);
                return 3;
            case WasmModule.W4IR_LOCAL_LOCAL_I32_CONST_AND + WasmModule.W4IR_EXECUTION_OFFSET:
                int andFirst = operand >>> 16;
                int andSecond = operand & 0xffff;
                push(locals[andFirst]);
                pushI32((int) locals[andSecond] & auxiliary);
                return 4;
            case WasmModule.W4IR_LOCAL_LOCAL_F32_DIV + WasmModule.W4IR_EXECUTION_OFFSET:
                pushI32(Float.floatToIntBits(
                        Float.intBitsToFloat((int) locals[operand])
                                / Float.intBitsToFloat((int) locals[auxiliary])));
                return 3;
            case WasmModule.W4IR_LOCAL_SET_F32_CONST_SET + WasmModule.W4IR_EXECUTION_OFFSET:
                int firstTarget = operand >>> 16;
                int secondTarget = operand & 0xffff;
                locals[firstTarget] = pop();
                locals[secondTarget] = auxiliary & 0xffffffffL;
                return 3;
            case WasmModule.W4IR_LOCAL_LOCAL_F32_LOAD + WasmModule.W4IR_EXECUTION_OFFSET:
                int loadFirst = operand >>> 16;
                int loadAddress = operand & 0xffff;
                push(locals[loadFirst]);
                push(loadI32(checkedAddress((int) locals[loadAddress], auxiliary, 4))
                        & 0xffffffffL);
                return 3;
            case WasmModule.W4IR_LOCAL_TEE_F32_MUL_ADD + WasmModule.W4IR_EXECUTION_OFFSET:
                locals[operand] = peek();
                float teeRight = Float.intBitsToFloat(popI32());
                float teeLeft = Float.intBitsToFloat(popI32());
                float teeAdd = Float.intBitsToFloat(popI32());
                pushI32(Float.floatToIntBits(teeAdd + teeLeft * teeRight));
                return 3;
            case WasmModule.W4IR_LOCAL_F32_MUL_ADD + WasmModule.W4IR_EXECUTION_OFFSET:
                float localRight = Float.intBitsToFloat((int) locals[operand]);
                float localLeft = Float.intBitsToFloat(popI32());
                float localAdd = Float.intBitsToFloat(popI32());
                pushI32(Float.floatToIntBits(localAdd + localLeft * localRight));
                return 3;
            case WasmModule.W4IR_LOCAL_LOCAL_F32_MUL_ADD + WasmModule.W4IR_EXECUTION_OFFSET:
                float localsLeft = Float.intBitsToFloat((int) locals[operand]);
                float localsRight = Float.intBitsToFloat((int) locals[auxiliary]);
                float localsAdd = Float.intBitsToFloat(popI32());
                pushI32(Float.floatToIntBits(localsAdd + localsLeft * localsRight));
                return 4;
            case WasmModule.W4IR_LOCAL_LOCAL + WasmModule.W4IR_EXECUTION_OFFSET:
                push(locals[operand]);
                push(locals[auxiliary]);
                return 2;
            case WasmModule.W4IR_LOCAL_I32_CONST + WasmModule.W4IR_EXECUTION_OFFSET:
                push(locals[operand]);
                push(auxiliary);
                return 2;
            case WasmModule.W4IR_LOCAL_F32_CONST + WasmModule.W4IR_EXECUTION_OFFSET:
                push(locals[operand]);
                push(auxiliary & 0xffffffffL);
                return 2;
            case WasmModule.W4IR_F32_MUL_CONST + WasmModule.W4IR_EXECUTION_OFFSET:
                pushI32(Float.floatToIntBits(
                        Float.intBitsToFloat(popI32()) * Float.intBitsToFloat(operand)));
                return 2;
            case WasmModule.W4IR_F32_MUL_ADD + WasmModule.W4IR_EXECUTION_OFFSET:
                float pairRight = Float.intBitsToFloat(popI32());
                float pairLeft = Float.intBitsToFloat(popI32());
                float pairAdd = Float.intBitsToFloat(popI32());
                pushI32(Float.floatToIntBits(pairAdd + pairLeft * pairRight));
                return 2;
            case WasmModule.W4IR_I32_ADD_CONST + WasmModule.W4IR_EXECUTION_OFFSET:
                pushI32(popI32() + operand);
                return 2;
            case WasmModule.W4IR_I32_AND_CONST + WasmModule.W4IR_EXECUTION_OFFSET:
                pushI32(popI32() & operand);
                return 2;
            case WasmModule.W4IR_LOCAL_SET_GET + WasmModule.W4IR_EXECUTION_OFFSET:
                locals[operand] = pop();
                push(locals[auxiliary]);
                return 2;
            case WasmModule.W4IR_LOCAL_SET_F32_CONST + WasmModule.W4IR_EXECUTION_OFFSET:
                locals[auxiliary] = operand & 0xffffffffL;
                return 2;
            case WasmModule.W4IR_F32_MUL_LOCAL + WasmModule.W4IR_EXECUTION_OFFSET:
                binaryF32(0x94);
                push(locals[auxiliary]);
                return 2;
            case WasmModule.W4IR_LOCAL_I32_CONST_ADD_SET + WasmModule.W4IR_EXECUTION_OFFSET:
                int addSource = operand >>> 16;
                int addTarget = operand & 0xffff;
                locals[addTarget] = (int) locals[addSource] + auxiliary;
                return 4;
            case WasmModule.W4IR_LOCAL_LOCAL_F32_STORE + WasmModule.W4IR_EXECUTION_OFFSET:
                int storeAddress = operand >>> 16;
                int storeValue = operand & 0xffff;
                storeI32(
                        checkedAddress((int) locals[storeAddress], auxiliary, 4),
                        (int) locals[storeValue]);
                return 3;
            case WasmModule.W4IR_LOCAL_TEE_MUL_ADD_SET_LOCAL_LOCAL + WasmModule.W4IR_EXECUTION_OFFSET:
                int teeTarget = instruction >>> 16;
                locals[teeTarget] = peek();
                float combinedRight = Float.intBitsToFloat(popI32());
                float combinedLeft = Float.intBitsToFloat(popI32());
                float combinedAdd = Float.intBitsToFloat(popI32());
                int combinedResult =
                        Float.floatToIntBits(combinedAdd + combinedLeft * combinedRight);
                int combinedTarget = operand >>> 16;
                int combinedFirst = operand & 0xffff;
                locals[combinedTarget] = combinedResult & 0xffffffffL;
                push(locals[combinedFirst]);
                push(locals[auxiliary]);
                return 6;
            case WasmModule.W4IR_LOCAL_MUL_ADD_SET + WasmModule.W4IR_EXECUTION_OFFSET:
                int multiplySource = instruction >>> 16;
                float multiplyRight = Float.intBitsToFloat((int) locals[multiplySource]);
                float multiplyLeft = Float.intBitsToFloat(popI32());
                float multiplyAdd = Float.intBitsToFloat(popI32());
                int multiplyResult =
                        Float.floatToIntBits(multiplyAdd + multiplyLeft * multiplyRight);
                locals[operand] = multiplyResult & 0xffffffffL;
                return 4;
            case WasmModule.W4IR_F32_LOAD_LOCAL_MUL_ADD + WasmModule.W4IR_EXECUTION_OFFSET:
                int multiplyAddress = address(operand, 4);
                float loadedLeft = Float.intBitsToFloat(loadI32(multiplyAddress));
                float loadedRight = Float.intBitsToFloat((int) locals[auxiliary]);
                float loadedAdd = Float.intBitsToFloat(popI32());
                pushI32(Float.floatToIntBits(loadedAdd + loadedLeft * loadedRight));
                return 4;
            case WasmModule.W4IR_LOCAL_SET_ADD_SET + WasmModule.W4IR_EXECUTION_OFFSET:
                executeLocalSetAddSet(instruction, operand, auxiliary, locals);
                return 5;
            case WasmModule.W4IR_LOCAL_SET_LOCAL_LOCAL_F32_LOAD + WasmModule.W4IR_EXECUTION_OFFSET:
                executeLocalSetLocalLocalF32Load(instruction, operand, auxiliary, locals);
                return 4;
            case WasmModule.W4IR_LOCAL_LOCAL_F32_LOAD_LOCAL + WasmModule.W4IR_EXECUTION_OFFSET:
                executeLocalLocalF32LoadLocal(instruction, operand, auxiliary, locals);
                return 4;
            case WasmModule.W4IR_F32_LOAD_NEG + WasmModule.W4IR_EXECUTION_OFFSET:
                executeF32LoadNeg(operand);
                return 2;
            case WasmModule.W4IR_F32_LOAD_DIV + WasmModule.W4IR_EXECUTION_OFFSET:
                executeF32LoadDiv(operand);
                return 2;
            case WasmModule.W4IR_F32_DIV_TEE_MUL_ADD_SET_LOCAL_LOCAL + WasmModule.W4IR_EXECUTION_OFFSET:
                executeF32DivTeeMulAddSetLocalLocal(
                        instruction, operand, auxiliary, locals);
                return 7;
            case WasmModule.W4IR_F32_LOAD_LOCAL_MUL_ADD_SET + WasmModule.W4IR_EXECUTION_OFFSET:
                executeF32LoadLocalMulAddSet(instruction, operand, auxiliary, locals);
                return 5;
            case WasmModule.W4IR_LOCAL_TRIPLE_F32_STORE + WasmModule.W4IR_EXECUTION_OFFSET:
                executeLocalTripleF32Store(instruction, operand, auxiliary, locals);
                return 9;
            case WasmModule.W4IR_LOCAL4_F32_MUL_ADD_TEE + WasmModule.W4IR_EXECUTION_OFFSET:
                executeLocal4F32MulAddTee(instruction, operand, auxiliary, locals);
                return 8;
            case WasmModule.W4IR_F32_LOAD_NEG_INDEX_DIV + WasmModule.W4IR_EXECUTION_OFFSET:
                executeF32LoadNegIndexDiv(instruction, operand, auxiliary, locals);
                return 11;
            case WasmModule.W4IR_TRIPLE_F32_STORE_LOCAL_LOAD_LOCAL + WasmModule.W4IR_EXECUTION_OFFSET:
                executeTripleF32StoreLocalLoadLocal(
                        instruction, operand, auxiliary, locals);
                return 13;
            case WasmModule.W4IR_LOCAL_TEE_MUL_ADD_SET_LOAD_MUL_ADD + WasmModule.W4IR_EXECUTION_OFFSET:
                executeLocalTeeMulAddSetLoadMulAdd(
                        instruction, operand, auxiliary, locals);
                return 10;
            case WasmModule.W4IR_LOCAL_SET_DUAL_ADD_SET + WasmModule.W4IR_EXECUTION_OFFSET:
                executeLocalSetDualAddSet(instruction, operand, auxiliary, locals);
                return 9;
            case WasmModule.W4IR_F32_FLOOR_INTRINSIC + WasmModule.W4IR_EXECUTION_OFFSET:
            case WasmModule.W4IR_F32_SIN_INTRINSIC + WasmModule.W4IR_EXECUTION_OFFSET:
                executeDirectNumericIntrinsic(
                        operand,
                        opcode == WasmModule.W4IR_F32_SIN_INTRINSIC + WasmModule.W4IR_EXECUTION_OFFSET);
                return 1;
            default:
                throw new WasmTrap(
                        "unsupported compact opcode: 0x" + Integer.toHexString(opcode));
        }
    }

    private void executeDirectNumericIntrinsic(int functionIndex, boolean sine) {
        if (InterpreterBuildConfig.PROFILING_SUPPORT && profilingEnabled) {
            functionCallCounts[functionIndex]++;
        }
        int argument = popI32();
        pushI32(sine ? sinF32Bits(argument) : floorF32Bits(argument));
    }

    private void executeNumericIntrinsicInstruction(int functionIndex, boolean sine) {
        if (directNumericIntrinsicsEnabled) {
            executeDirectNumericIntrinsic(functionIndex, sine);
        } else {
            callFunction(functionIndex);
        }
    }

    private int executeLocalAddSetBranch(
            int instruction,
            int operand,
            int auxiliary,
            long[] locals,
            int functionStackBase,
            int functionResultCount,
            int functionControlBase) {
        int source = operand >>> 16;
        int target = operand & 0xffff;
        locals[target] = (int) locals[source] + auxiliary;
        return branch(
                instruction >>> 16,
                functionStackBase,
                functionResultCount,
                functionControlBase);
    }

    private int executeLocalAddSetBranchShadow(
            WasmModule.FunctionBody body,
            int branchPc,
            int instruction,
            int operand,
            int auxiliary,
            long[] locals,
            int functionStackBase,
            int functionResultCount,
            int functionControlBase) {
        int source = operand >>> 16;
        int target = operand & 0xffff;
        locals[target] = (int) locals[source] + auxiliary;
        return branchWithDescriptorShadow(
                body,
                body.branchDescriptorIndexAt(branchPc),
                instruction >>> 16,
                functionStackBase,
                functionResultCount,
                functionControlBase);
    }

    private void executeLocalSetAddSet(
            int instruction, int operand, int auxiliary, long[] locals) {
        int firstTarget = instruction >>> 16;
        int source = operand >>> 16;
        int secondTarget = operand & 0xffff;
        locals[firstTarget] = pop();
        locals[secondTarget] = (int) locals[source] + auxiliary;
    }

    private void executeLocalSetLocalLocalF32Load(
            int instruction, int operand, int auxiliary, long[] locals) {
        int setTarget = instruction >>> 16;
        int firstSource = operand >>> 16;
        int addressSource = operand & 0xffff;
        locals[setTarget] = pop();
        push(locals[firstSource]);
        int loadAddress = checkedAddress((int) locals[addressSource], auxiliary, 4);
        push(loadI32(loadAddress) & 0xffffffffL);
    }

    private void executeLocalLocalF32LoadLocal(
            int instruction, int operand, int auxiliary, long[] locals) {
        int thirdSource = instruction >>> 16;
        int firstSource = operand >>> 16;
        int addressSource = operand & 0xffff;
        push(locals[firstSource]);
        int loadAddress = checkedAddress((int) locals[addressSource], auxiliary, 4);
        push(loadI32(loadAddress) & 0xffffffffL);
        push(locals[thirdSource]);
    }

    private void executeF32LoadNeg(int offset) {
        pushI32(loadI32(address(offset, 4)) ^ 0x80000000);
    }

    private void executeF32LoadDiv(int offset) {
        float right = Float.intBitsToFloat(loadI32(address(offset, 4)));
        float left = Float.intBitsToFloat(popI32());
        pushI32(Float.floatToIntBits(left / right));
    }

    private void executeF32DivTeeMulAddSetLocalLocal(
            int instruction, int operand, int auxiliary, long[] locals) {
        float divideRight = Float.intBitsToFloat(popI32());
        float divideLeft = Float.intBitsToFloat(popI32());
        int divideBits = Float.floatToIntBits(divideLeft / divideRight);
        int teeTarget = instruction >>> 16;
        locals[teeTarget] = divideBits & 0xffffffffL;
        float multiplyRight = Float.intBitsToFloat(divideBits);
        float multiplyLeft = Float.intBitsToFloat(popI32());
        float addLeft = Float.intBitsToFloat(popI32());
        int result = Float.floatToIntBits(addLeft + multiplyLeft * multiplyRight);
        int setTarget = operand >>> 16;
        int firstSource = operand & 0xffff;
        locals[setTarget] = result & 0xffffffffL;
        push(locals[firstSource]);
        push(locals[auxiliary]);
    }

    private void executeF32LoadLocalMulAddSet(
            int instruction, int operand, int auxiliary, long[] locals) {
        int setTarget = instruction >>> 16;
        float multiplyLeft = Float.intBitsToFloat(loadI32(address(operand, 4)));
        float multiplyRight = Float.intBitsToFloat((int) locals[auxiliary]);
        float addLeft = Float.intBitsToFloat(popI32());
        int result = Float.floatToIntBits(addLeft + multiplyLeft * multiplyRight);
        locals[setTarget] = result & 0xffffffffL;
    }

    private void executeLocalTripleF32Store(
            int instruction, int operand, int auxiliary, long[] locals) {
        int addressLocal = instruction >>> 16;
        int firstValueLocal = operand >>> 16;
        int secondValueLocal = operand & 0xffff;
        int thirdValueLocal = auxiliary >>> 16;
        int baseOffset = auxiliary & 0xffff;
        int baseAddress = (int) locals[addressLocal];
        storeI32(
                checkedAddress(baseAddress, baseOffset, 4),
                (int) locals[firstValueLocal]);
        storeI32(
                checkedAddress(baseAddress, baseOffset + 4, 4),
                (int) locals[secondValueLocal]);
        storeI32(
                checkedAddress(baseAddress, baseOffset + 8, 4),
                (int) locals[thirdValueLocal]);
    }

    private void executeLocal4F32MulAddTee(
            int instruction, int operand, int auxiliary, long[] locals) {
        int firstSource = instruction >>> 16;
        int secondSource = operand >>> 16;
        int thirdSource = operand & 0xffff;
        int fourthSource = auxiliary >>> 16;
        int teeTarget = auxiliary & 0xffff;
        int firstProductBits = Float.floatToIntBits(
                Float.intBitsToFloat((int) locals[firstSource])
                        * Float.intBitsToFloat((int) locals[secondSource]));
        float firstProduct = Float.intBitsToFloat(firstProductBits);
        float secondProduct =
                Float.intBitsToFloat((int) locals[thirdSource])
                        * Float.intBitsToFloat((int) locals[fourthSource]);
        int result = Float.floatToIntBits(firstProduct + secondProduct);
        locals[teeTarget] = result & 0xffffffffL;
        pushI32(result);
    }

    private void executeF32LoadNegIndexDiv(
            int instruction, int operand, int auxiliary, long[] locals) {
        int offset = instruction >>> 16;
        int numeratorBits = loadI32(address(offset, 4)) ^ 0x80000000;
        int firstSource = operand >>> 16;
        int secondSource = operand & 0xffff;
        int mask = auxiliary >>> 16;
        int shift = auxiliary & 0xffff;
        int index =
                (int) locals[firstSource]
                        | (((int) locals[secondSource] & mask) << shift);
        int denominatorBits = loadI32(checkedAddress(index, offset, 4));
        float numerator = Float.intBitsToFloat(numeratorBits);
        float denominator = Float.intBitsToFloat(denominatorBits);
        pushI32(Float.floatToIntBits(numerator / denominator));
    }

    private void executeTripleF32StoreLocalLoadLocal(
            int instruction, int operand, int auxiliary, long[] locals) {
        int packedTargets = instruction >>> 16;
        int storeAddressLocal = packedTargets >>> 8;
        int firstValueLocal = packedTargets & 0xff;
        int secondValueLocal = operand >>> 24;
        int thirdValueLocal = (operand >>> 16) & 0xff;
        int firstSource = (operand >>> 8) & 0xff;
        int loadAddressLocal = operand & 0xff;
        int thirdSource = auxiliary >>> 24;
        int storeOffset = (auxiliary >>> 16) & 0xff;
        int loadOffset = (auxiliary >>> 8) & 0xff;
        int storeBase = (int) locals[storeAddressLocal];
        storeI32(
                checkedAddress(storeBase, storeOffset, 4),
                (int) locals[firstValueLocal]);
        storeI32(
                checkedAddress(storeBase, storeOffset + 4, 4),
                (int) locals[secondValueLocal]);
        storeI32(
                checkedAddress(storeBase, storeOffset + 8, 4),
                (int) locals[thirdValueLocal]);
        push(locals[firstSource]);
        int loadAddress = checkedAddress((int) locals[loadAddressLocal], loadOffset, 4);
        push(loadI32(loadAddress) & 0xffffffffL);
        push(locals[thirdSource]);
    }

    private void executeLocalTeeMulAddSetLoadMulAdd(
            int instruction, int operand, int auxiliary, long[] locals) {
        int packedTargets = instruction >>> 16;
        int teeTarget = packedTargets >>> 8;
        int setTarget = packedTargets & 0xff;
        int firstSource = operand >>> 24;
        int secondSource = (operand >>> 16) & 0xff;
        int multiplySource = (operand >>> 8) & 0xff;
        locals[teeTarget] = peek();
        float firstMultiplyRight = Float.intBitsToFloat(popI32());
        float firstMultiplyLeft = Float.intBitsToFloat(popI32());
        float firstAdd = Float.intBitsToFloat(popI32());
        int firstResult = Float.floatToIntBits(
                firstAdd + firstMultiplyLeft * firstMultiplyRight);
        locals[setTarget] = firstResult & 0xffffffffL;
        int loadAddress = checkedAddress((int) locals[secondSource], auxiliary, 4);
        float secondMultiplyLeft = Float.intBitsToFloat(loadI32(loadAddress));
        float secondMultiplyRight = Float.intBitsToFloat((int) locals[multiplySource]);
        float secondAdd = Float.intBitsToFloat((int) locals[firstSource]);
        pushI32(Float.floatToIntBits(
                secondAdd + secondMultiplyLeft * secondMultiplyRight));
    }

    private void executeLocalSetDualAddSet(
            int instruction, int operand, int auxiliary, long[] locals) {
        int packedLocals = instruction >>> 16;
        int firstTarget = packedLocals >>> 8;
        int firstSource = packedLocals & 0xff;
        int secondTarget = operand >>> 24;
        int secondSource = (operand >>> 16) & 0xff;
        int thirdTarget = (operand >>> 8) & 0xff;
        int firstConstant = (short) (auxiliary >>> 16);
        int secondConstant = (short) auxiliary;
        locals[firstTarget] = pop();
        locals[secondTarget] = (int) locals[firstSource] + firstConstant;
        locals[thirdTarget] = (int) locals[secondSource] + secondConstant;
    }

    private int executeCountedF32Trace(
            WasmModule.FunctionBody body,
            int pc,
            int instruction,
            int limit,
            int exitDepth,
            long[] locals,
            int functionStackBase,
            int functionResultCount,
            int functionControlBase) {
        int storePc = pc + 4;
        int dividePc = pc + 17;
        int multiplyPc = pc + 28;
        int addPc = pc + 38;
        int backedgePc = pc + 47;
        int storeInstruction = w4irWord(body, storePc, 0);
        int storeOperand = w4irWord(body, storePc, 1);
        int storeAuxiliary = w4irWord(body, storePc, 2);
        int divideInstruction = w4irWord(body, dividePc, 0);
        int divideOperand = w4irWord(body, dividePc, 1);
        int divideAuxiliary = w4irWord(body, dividePc, 2);
        int multiplyInstruction = w4irWord(body, multiplyPc, 0);
        int multiplyOperand = w4irWord(body, multiplyPc, 1);
        int multiplyAuxiliary = w4irWord(body, multiplyPc, 2);
        int addInstruction = w4irWord(body, addPc, 0);
        int addOperand = w4irWord(body, addPc, 1);
        int addAuxiliary = w4irWord(body, addPc, 2);
        int backedgeInstruction = w4irWord(body, backedgePc, 0);
        int backedgeOperand = w4irWord(body, backedgePc, 1);
        int backedgeAuxiliary = w4irWord(body, backedgePc, 2);
        if ((storeInstruction & 0xffff)
                        != WasmModule.W4IR_TRIPLE_F32_STORE_LOCAL_LOAD_LOCAL + WasmModule.W4IR_EXECUTION_OFFSET
                || (divideInstruction & 0xffff)
                        != WasmModule.W4IR_F32_LOAD_NEG_INDEX_DIV + WasmModule.W4IR_EXECUTION_OFFSET
                || (multiplyInstruction & 0xffff)
                        != WasmModule.W4IR_LOCAL_TEE_MUL_ADD_SET_LOAD_MUL_ADD + WasmModule.W4IR_EXECUTION_OFFSET
                || (addInstruction & 0xffff)
                        != WasmModule.W4IR_LOCAL_SET_DUAL_ADD_SET + WasmModule.W4IR_EXECUTION_OFFSET
                || (backedgeInstruction & 0xffff)
                        != WasmModule.W4IR_LOCAL_ADD_SET_BR + WasmModule.W4IR_EXECUTION_OFFSET) {
            throw new WasmTrap("invalid W4IR counted trace");
        }

        int compareLocal = instruction >>> 16;
        traceLoopCalls++;
        instructionsExecuted += 3;
        while ((int) locals[compareLocal] != limit) {
            if (InterpreterBuildConfig.DESCRIPTOR_SHADOW) {
                verifyDescriptorFallthrough(
                        body,
                        body.branchDescriptorIndexAt(pc + 3),
                        exitDepth,
                        functionStackBase,
                        functionResultCount,
                        functionControlBase,
                        valueTop,
                        controlTop,
                        pc + 4,
                        pc + 4);
            }
            beginTraceInstruction();
            executeTripleF32StoreLocalLoadLocal(
                    storeInstruction, storeOperand, storeAuxiliary, locals);
            instructionsExecuted += 12;

            beginTraceInstruction();
            executeF32LoadNegIndexDiv(
                    divideInstruction, divideOperand, divideAuxiliary, locals);
            instructionsExecuted += 10;

            beginTraceInstruction();
            executeLocalTeeMulAddSetLoadMulAdd(
                    multiplyInstruction, multiplyOperand, multiplyAuxiliary, locals);
            instructionsExecuted += 9;

            beginTraceInstruction();
            executeLocalSetDualAddSet(
                    addInstruction, addOperand, addAuxiliary, locals);
            instructionsExecuted += 8;

            beginTraceInstruction();
            instructionsExecuted += 4;
            int targetPc;
            if (InterpreterBuildConfig.DESCRIPTOR_SHADOW) {
                targetPc = executeLocalAddSetBranchShadow(
                        body,
                        backedgePc + 4,
                        backedgeInstruction,
                        backedgeOperand,
                        backedgeAuxiliary,
                        locals,
                        functionStackBase,
                        functionResultCount,
                        functionControlBase);
            } else {
                targetPc = executeLocalAddSetBranch(
                        backedgeInstruction,
                        backedgeOperand,
                        backedgeAuxiliary,
                        locals,
                        functionStackBase,
                        functionResultCount,
                        functionControlBase);
            }
            if (targetPc != pc) {
                throw new WasmTrap("W4IR counted trace backedge changed target");
            }
            traceLoopIterations++;

            beginTraceInstruction();
            instructionsExecuted += 3;
        }
        if (InterpreterBuildConfig.DESCRIPTOR_SHADOW) {
            return branchWithDescriptorShadow(
                    body,
                    body.branchDescriptorIndexAt(pc + 3),
                    exitDepth,
                    functionStackBase,
                    functionResultCount,
                    functionControlBase);
        }
        return branch(
                exitDepth,
                functionStackBase,
                functionResultCount,
                functionControlBase);
    }

    private int executeCountedF32TraceCompare(
            int pc,
            int instruction,
            int limit,
            int exitDepth,
            long[] locals,
            int functionStackBase,
            int functionResultCount,
            int functionControlBase) {
        instructionsExecuted += 3;
        if ((int) locals[instruction >>> 16] != limit) {
            return pc + 4;
        }
        return branch(
                exitDepth,
                functionStackBase,
                functionResultCount,
                functionControlBase);
    }

    private int executeCountedF32TraceCompareShadow(
            WasmModule.FunctionBody body,
            int pc,
            int instruction,
            int limit,
            int exitDepth,
            long[] locals,
            int functionStackBase,
            int functionResultCount,
            int functionControlBase) {
        instructionsExecuted += 3;
        if ((int) locals[instruction >>> 16] != limit) {
            verifyDescriptorFallthrough(
                    body,
                    body.branchDescriptorIndexAt(pc + 3),
                    exitDepth,
                    functionStackBase,
                    functionResultCount,
                    functionControlBase,
                    valueTop,
                    controlTop,
                    pc + 4,
                    pc + 4);
            return pc + 4;
        }
        return branchWithDescriptorShadow(
                body,
                body.branchDescriptorIndexAt(pc + 3),
                exitDepth,
                functionStackBase,
                functionResultCount,
                functionControlBase);
    }

    private void beginTraceInstruction() {
        instructionsExecuted++;
        if (instructionsExecuted > instructionLimit) {
            throw new WasmTrap("instruction budget exhausted");
        }
    }

    private int w4irWord(WasmModule.FunctionBody body, int pc, int word) {
        int codeOffset = pc * WasmModule.W4IR_STRIDE + word;
        int[] page = body.codePage(codeOffset);
        return page[codeOffset - body.codePageBase(codeOffset)];
    }

    private int executeBranchIfLocalI32Const(
            int instruction,
            int operand,
            int auxiliary,
            long[] locals,
            int pc,
            int functionStackBase,
            int functionResultCount,
            int functionControlBase) {
        if (popI32() != 0) {
            return branch(
                    auxiliary,
                    functionStackBase,
                    functionResultCount,
                    functionControlBase);
        }
        instructionsExecuted += 2;
        push(locals[instruction >>> 16]);
        push(operand);
        return pc + 3;
    }

    private int executeBranchIfLocalI32ConstShadow(
            WasmModule.FunctionBody body,
            int instruction,
            int operand,
            int auxiliary,
            long[] locals,
            int pc,
            int functionStackBase,
            int functionResultCount,
            int functionControlBase) {
        if (popI32() != 0) {
            return branchWithDescriptorShadow(
                    body,
                    body.branchDescriptorIndexAt(pc),
                    auxiliary,
                    functionStackBase,
                    functionResultCount,
                    functionControlBase);
        }
        instructionsExecuted += 2;
        push(locals[instruction >>> 16]);
        push(operand);
        verifyDescriptorFallthrough(
                body,
                body.branchDescriptorIndexAt(pc),
                auxiliary,
                functionStackBase,
                functionResultCount,
                functionControlBase,
                valueTop,
                controlTop,
                pc + 3,
                pc + 3);
        return pc + 3;
    }

    private int branchWithDescriptorShadow(
            WasmModule.FunctionBody body,
            int descriptorIndex,
            int depth,
            int functionStackBase,
            int functionResultCount,
            int functionControlBase) {
        verifyDescriptorAgainstLegacy(
                body,
                descriptorIndex,
                depth,
                functionStackBase,
                functionResultCount,
                functionControlBase);
        int offset = descriptorIndex * WasmModule.BRANCH_DESCRIPTOR_STRIDE;
        int destination =
                functionStackBase
                        + body.branchDescriptors[
                                offset + WasmModule.BRANCH_DESCRIPTOR_VALUE_HEIGHT];
        int arity =
                body.branchDescriptors[offset + WasmModule.BRANCH_DESCRIPTOR_ARITY];
        int expectedControlTop =
                functionControlBase
                        + body.branchDescriptors[
                                offset + WasmModule.BRANCH_DESCRIPTOR_CONTROL_DEPTH];
        int expectedPc =
                body.branchDescriptors[offset + WasmModule.BRANCH_DESCRIPTOR_TARGET_PC];
        int actualPc =
                branch(
                        depth,
                        functionStackBase,
                        functionResultCount,
                        functionControlBase);
        if (actualPc != expectedPc
                || valueTop != destination + arity
                || controlTop != expectedControlTop) {
            throw new WasmTrap("branch descriptor outcome mismatch");
        }
        int index;
        for (index = 0; index < arity; index++) {
            if (values[destination + index] != transferValues[index]) {
                throw new WasmTrap("branch descriptor value order mismatch");
            }
        }
        return actualPc;
    }

    private void verifyDescriptorFallthrough(
            WasmModule.FunctionBody body,
            int descriptorIndex,
            int depth,
            int functionStackBase,
            int functionResultCount,
            int functionControlBase,
            int expectedValueTop,
            int expectedControlTop,
            int expectedPc,
            int actualPc) {
        verifyDescriptorAgainstLegacy(
                body,
                descriptorIndex,
                depth,
                functionStackBase,
                functionResultCount,
                functionControlBase);
        if (valueTop != expectedValueTop
                || controlTop != expectedControlTop
                || actualPc != expectedPc) {
            throw new WasmTrap("branch descriptor fallthrough mismatch");
        }
    }

    private void verifyDescriptorAgainstLegacy(
            WasmModule.FunctionBody body,
            int descriptorIndex,
            int depth,
            int functionStackBase,
            int functionResultCount,
            int functionControlBase) {
        int offset = descriptorIndex * WasmModule.BRANCH_DESCRIPTOR_STRIDE;
        if (descriptorIndex < 0
                || offset > body.branchDescriptors.length
                        - WasmModule.BRANCH_DESCRIPTOR_STRIDE) {
            throw new WasmTrap("branch descriptor index is out of range");
        }
        int available = controlTop - functionControlBase;
        int targetPc;
        int destinationHeight;
        int arity;
        int activeControlDepth;
        int flags;
        if (depth == available) {
            targetPc = -1;
            destinationHeight = 0;
            arity = functionResultCount;
            activeControlDepth = 0;
            flags = WasmModule.BRANCH_DESCRIPTOR_FUNCTION_RETURN;
        } else {
            if (depth < 0 || depth >= available) {
                throw new WasmTrap("branch depth is out of range");
            }
            int target = controlTop - 1 - depth;
            int targetWord = controlWord[target];
            boolean loop = (targetWord & 0xffff) == WasmModule.LOOP;
            targetPc = loop ? controlStart[target] + 1 : controlEnd[target] + 1;
            destinationHeight = controlBase[target] - functionStackBase;
            arity = loop ? (targetWord >>> 16) & 0xff : targetWord >>> 24;
            activeControlDepth =
                    loop
                            ? target + 1 - functionControlBase
                            : target - functionControlBase;
            flags = loop ? WasmModule.BRANCH_DESCRIPTOR_LOOP_TARGET : 0;
        }
        if (body.branchDescriptors[
                                offset + WasmModule.BRANCH_DESCRIPTOR_TARGET_PC]
                        != targetPc
                || body.branchDescriptors[
                                offset + WasmModule.BRANCH_DESCRIPTOR_VALUE_HEIGHT]
                        != destinationHeight
                || body.branchDescriptors[
                                offset + WasmModule.BRANCH_DESCRIPTOR_ARITY]
                        != arity
                || body.branchDescriptors[
                                offset + WasmModule.BRANCH_DESCRIPTOR_CONTROL_DEPTH]
                        != activeControlDepth
                || body.branchDescriptors[
                                offset + WasmModule.BRANCH_DESCRIPTOR_FLAGS]
                        != flags) {
            throw new WasmTrap("branch descriptor metadata mismatch");
        }
    }

    private int branch(
            int depth,
            int functionStackBase,
            int functionResultCount,
            int functionControlBase) {
        int available = controlTop - functionControlBase;
        if (depth == available) {
            finishFunction(functionStackBase, functionResultCount);
            controlTop = functionControlBase;
            return -1;
        }
        if (depth < 0 || depth >= available) {
            throw new WasmTrap("branch depth is out of range");
        }
        int target = controlTop - 1 - depth;
        int word = controlWord[target];
        boolean loop = (word & 0xffff) == WasmModule.LOOP;
        int arity = loop ? (word >>> 16) & 0xff : word >>> 24;
        transfer(arity, controlBase[target]);
        if (loop) {
            controlTop = target + 1;
            return controlStart[target] + 1;
        }
        controlTop = target;
        return controlEnd[target] + 1;
    }

    private void finishFunction(int stackBase, int resultCount) {
        transfer(resultCount, stackBase);
    }

    private void transfer(int count, int destinationBase) {
        if (count < 0 || count > transferValues.length || valueTop - count < destinationBase) {
            throw new WasmTrap("invalid stack transfer");
        }
        if (count == 0) {
            valueTop = destinationBase;
            return;
        }
        if (count == 1) {
            long value = values[valueTop - 1];
            valueTop = destinationBase;
            push(value);
            return;
        }
        int index;
        for (index = 0; index < count; index++) {
            transferValues[index] = values[valueTop - count + index];
        }
        valueTop = destinationBase;
        for (index = 0; index < count; index++) {
            push(transferValues[index]);
        }
    }

    private int address(int offset, int size) {
        int base = popI32();
        return checkedAddress(base, offset, size);
    }

    private int checkedAddress(int base, int offset, int size) {
        int maximumBase = module.memory.length - size;
        if (base < 0
                || offset < 0
                || base > maximumBase
                || offset > maximumBase - base) {
            throw new WasmTrap("out-of-bounds memory access");
        }
        return base + offset;
    }

    private void checkMemoryRange(int address, int length) {
        if (address < 0 || length < 0 || length > module.memory.length - address) {
            throw new WasmTrap("out-of-bounds memory range");
        }
    }

    private void checkDataRange(byte[] data, int address, int length) {
        if (address < 0 || length < 0 || length > data.length - address) {
            throw new WasmTrap("out-of-bounds passive data range");
        }
    }

    private void executeBulkMemory(int opcode, int operand) {
        switch (opcode) {
            case WasmModule.EXECUTION_BULK_FIRST + 0x8:
                int initializeLength = popI32();
                int initializeSource = popI32();
                int initializeDestination = popI32();
                byte[] dataSegment = module.dataSegments[operand];
                checkMemoryRange(initializeDestination, initializeLength);
                checkDataRange(dataSegment, initializeSource, initializeLength);
                System.arraycopy(
                        dataSegment,
                        initializeSource,
                        module.memory,
                        initializeDestination,
                        initializeLength);
                return;
            case WasmModule.EXECUTION_BULK_FIRST + 0x9:
                module.dataSegments[operand] = WasmModule.EMPTY_DATA_SEGMENT;
                return;
            case WasmModule.EXECUTION_BULK_FIRST + 0xa:
                int copyLength = popI32();
                int copySource = popI32();
                int copyDestination = popI32();
                checkMemoryRange(copySource, copyLength);
                checkMemoryRange(copyDestination, copyLength);
                System.arraycopy(
                        module.memory,
                        copySource,
                        module.memory,
                        copyDestination,
                        copyLength);
                return;
            default:
                int fillLength = popI32();
                int fillValue = popI32();
                int fillDestination = popI32();
                checkMemoryRange(fillDestination, fillLength);
                int fillIndex;
                for (fillIndex = 0; fillIndex < fillLength; fillIndex++) {
                    module.memory[fillDestination + fillIndex] = (byte) fillValue;
                }
        }
    }

    private int loadU16(int address) {
        return (module.memory[address] & 0xff) | ((module.memory[address + 1] & 0xff) << 8);
    }

    private int loadI32(int address) {
        byte[] memory = module.memory;
        return (memory[address] & 0xff)
                | ((memory[address + 1] & 0xff) << 8)
                | ((memory[address + 2] & 0xff) << 16)
                | (memory[address + 3] << 24);
    }

    private long loadI64(int address) {
        long low = loadI32(address) & 0xffffffffL;
        long high = loadI32(address + 4) & 0xffffffffL;
        return low | (high << 32);
    }

    private void storeU16(int address, int value) {
        module.memory[address] = (byte) value;
        module.memory[address + 1] = (byte) (value >>> 8);
    }

    private void storeI32(int address, int value) {
        byte[] memory = module.memory;
        memory[address] = (byte) value;
        memory[address + 1] = (byte) (value >>> 8);
        memory[address + 2] = (byte) (value >>> 16);
        memory[address + 3] = (byte) (value >>> 24);
    }

    private void storeI64(int address, long value) {
        storeI32(address, (int) value);
        storeI32(address + 4, (int) (value >>> 32));
    }

    private void compareI32(int operation) {
        int right = popI32();
        int left = popI32();
        boolean result;
        switch (operation) {
            case 0:
                result = left == right;
                break;
            case 1:
                result = left != right;
                break;
            case 2:
                result = left < right;
                break;
            case 3:
                result = (left & 0xffffffffL) < (right & 0xffffffffL);
                break;
            case 4:
                result = left > right;
                break;
            case 5:
                result = (left & 0xffffffffL) > (right & 0xffffffffL);
                break;
            case 6:
                result = left <= right;
                break;
            case 7:
                result = (left & 0xffffffffL) <= (right & 0xffffffffL);
                break;
            case 8:
                result = left >= right;
                break;
            default:
                result = (left & 0xffffffffL) >= (right & 0xffffffffL);
                break;
        }
        pushI32(result ? 1 : 0);
    }

    private void compareI64(int opcode) {
        long right = pop();
        long left = pop();
        boolean result;
        switch (opcode) {
            case 0x51:
                result = left == right;
                break;
            case 0x52:
                result = left != right;
                break;
            case 0x53:
                result = left < right;
                break;
            case 0x54:
                result = compareUnsigned(left, right) < 0;
                break;
            case 0x55:
                result = left > right;
                break;
            case 0x56:
                result = compareUnsigned(left, right) > 0;
                break;
            case 0x57:
                result = left <= right;
                break;
            case 0x58:
                result = compareUnsigned(left, right) <= 0;
                break;
            case 0x59:
                result = left >= right;
                break;
            default:
                result = compareUnsigned(left, right) >= 0;
                break;
        }
        pushI32(result ? 1 : 0);
    }

    private int compareUnsigned(long left, long right) {
        long normalizedLeft = left ^ Long.MIN_VALUE;
        long normalizedRight = right ^ Long.MIN_VALUE;
        if (normalizedLeft < normalizedRight) {
            return -1;
        }
        if (normalizedLeft > normalizedRight) {
            return 1;
        }
        return 0;
    }

    private void executeI32DivRem(int opcode) {
        int right = popI32();
        int left = popI32();
        if (right == 0) {
            throw new WasmTrap("integer divide by zero");
        }
        switch (opcode) {
            case 0x6d:
                if (left == Integer.MIN_VALUE && right == -1) {
                    throw new WasmTrap("integer overflow");
                }
                pushI32(left / right);
                return;
            case 0x6e:
                pushI32((int) ((left & 0xffffffffL) / (right & 0xffffffffL)));
                return;
            case 0x6f:
                pushI32(left % right);
                return;
            default:
                pushI32((int) ((left & 0xffffffffL) % (right & 0xffffffffL)));
        }
    }

    private void executeI64DivRem(int opcode) {
        long right = pop();
        long left = pop();
        if (right == 0) {
            throw new WasmTrap("integer divide by zero");
        }
        switch (opcode) {
            case 0x7f:
                if (left == Long.MIN_VALUE && right == -1) {
                    throw new WasmTrap("integer overflow");
                }
                push(left / right);
                return;
            case 0x80:
                push(divideUnsignedI64(left, right));
                return;
            case 0x81:
                push(left % right);
                return;
            default:
                push(remainderUnsignedI64(left, right));
        }
    }

    private long divideUnsignedI64(long dividend, long divisor) {
        if (divisor < 0) {
            return compareUnsigned(dividend, divisor) >= 0 ? 1 : 0;
        }
        long quotient = 0;
        long remainder = 0;
        int bit;
        for (bit = 63; bit >= 0; bit--) {
            remainder = (remainder << 1) | ((dividend >>> bit) & 1L);
            if (compareUnsigned(remainder, divisor) >= 0) {
                remainder -= divisor;
                quotient |= 1L << bit;
            }
        }
        return quotient;
    }

    private long remainderUnsignedI64(long dividend, long divisor) {
        if (divisor < 0) {
            return compareUnsigned(dividend, divisor) >= 0 ? dividend - divisor : dividend;
        }
        long remainder = 0;
        int bit;
        for (bit = 63; bit >= 0; bit--) {
            remainder = (remainder << 1) | ((dividend >>> bit) & 1L);
            if (compareUnsigned(remainder, divisor) >= 0) {
                remainder -= divisor;
            }
        }
        return remainder;
    }

    private int countLeadingZerosI32(int value) {
        if (value == 0) {
            return 32;
        }
        int count = 0;
        int mask = 0x80000000;
        while ((value & mask) == 0) {
            count++;
            mask >>>= 1;
        }
        return count;
    }

    private int countTrailingZerosI32(int value) {
        if (value == 0) {
            return 32;
        }
        int count = 0;
        while ((value & 1) == 0) {
            count++;
            value >>>= 1;
        }
        return count;
    }

    private int populationCountI32(int value) {
        int count = 0;
        while (value != 0) {
            value &= value - 1;
            count++;
        }
        return count;
    }

    private int countLeadingZerosI64(long value) {
        if (value == 0) {
            return 64;
        }
        int high = (int) (value >>> 32);
        return high != 0 ? countLeadingZerosI32(high) : 32 + countLeadingZerosI32((int) value);
    }

    private int countTrailingZerosI64(long value) {
        if (value == 0) {
            return 64;
        }
        int low = (int) value;
        return low != 0
                ? countTrailingZerosI32(low)
                : 32 + countTrailingZerosI32((int) (value >>> 32));
    }

    private int populationCountI64(long value) {
        return populationCountI32((int) value) + populationCountI32((int) (value >>> 32));
    }

    private void compareF32(int opcode) {
        float right = Float.intBitsToFloat(popI32());
        float left = Float.intBitsToFloat(popI32());
        boolean result;
        switch (opcode) {
            case 0x5b:
                result = left == right;
                break;
            case 0x5c:
                result = left != right;
                break;
            case 0x5d:
                result = left < right;
                break;
            case 0x5e:
                result = left > right;
                break;
            case 0x5f:
                result = left <= right;
                break;
            default:
                result = left >= right;
                break;
        }
        pushI32(result ? 1 : 0);
    }

    private void compareF64(int opcode) {
        double right = Double.longBitsToDouble(pop());
        double left = Double.longBitsToDouble(pop());
        boolean result;
        switch (opcode) {
            case 0x61:
                result = left == right;
                break;
            case 0x62:
                result = left != right;
                break;
            case 0x63:
                result = left < right;
                break;
            case 0x64:
                result = left > right;
                break;
            case 0x65:
                result = left <= right;
                break;
            default:
                result = left >= right;
                break;
        }
        pushI32(result ? 1 : 0);
    }

    private void binaryF64(int opcode) {
        double right = Double.longBitsToDouble(pop());
        double left = Double.longBitsToDouble(pop());
        double result;
        if (opcode == 0xa0) {
            result = left + right;
        } else if (opcode == 0xa1) {
            result = left - right;
        } else if (opcode == 0xa2) {
            result = left * right;
        } else {
            result = left / right;
        }
        push(Double.doubleToLongBits(result));
    }

    private void unaryF64(int opcode) {
        long bits = pop();
        if (opcode == 0x99) {
            push(bits & 0x7fffffffffffffffL);
            return;
        }
        if (opcode == 0x9a) {
            push(bits ^ Long.MIN_VALUE);
            return;
        }
        double value = Double.longBitsToDouble(bits);
        double result;
        if (opcode == 0x9b) {
            result = Math.ceil(value);
        } else if (opcode == 0x9c) {
            result = Math.floor(value);
        } else if (opcode == 0x9d) {
            result = value < 0.0 ? Math.ceil(value) : Math.floor(value);
        } else if (opcode == 0x9e) {
            result = nearestF64(value);
        } else {
            result = Math.sqrt(value);
        }
        push(Double.doubleToLongBits(result));
    }

    private void minimumF64() {
        long rightBits = pop();
        long leftBits = pop();
        double right = Double.longBitsToDouble(rightBits);
        double left = Double.longBitsToDouble(leftBits);
        if (left != left || right != right) {
            push(0x7ff8000000000000L);
        } else if (left == 0.0 && right == 0.0) {
            push(leftBits | rightBits);
        } else {
            push(Double.doubleToLongBits(left < right ? left : right));
        }
    }

    private void maximumF64() {
        long rightBits = pop();
        long leftBits = pop();
        double right = Double.longBitsToDouble(rightBits);
        double left = Double.longBitsToDouble(leftBits);
        if (left != left || right != right) {
            push(0x7ff8000000000000L);
        } else if (left == 0.0 && right == 0.0) {
            push(leftBits & rightBits);
        } else {
            push(Double.doubleToLongBits(left > right ? left : right));
        }
    }

    private void copySignF64() {
        long sign = pop() & Long.MIN_VALUE;
        long magnitude = pop() & 0x7fffffffffffffffL;
        push(magnitude | sign);
    }

    private void binaryF32(int opcode) {
        float right = Float.intBitsToFloat(popI32());
        float left = Float.intBitsToFloat(popI32());
        float result;
        if (opcode == 0x92) {
            result = left + right;
        } else if (opcode == 0x93) {
            result = left - right;
        } else if (opcode == 0x94) {
            result = left * right;
        } else {
            result = left / right;
        }
        pushI32(Float.floatToIntBits(result));
    }

    private void unaryF32(int opcode) {
        int bits = popI32();
        if (opcode == 0x8b) {
            pushI32(bits & 0x7fffffff);
            return;
        }
        if (opcode == 0x8c) {
            pushI32(bits ^ 0x80000000);
            return;
        }
        float value = Float.intBitsToFloat(bits);
        float result;
        if (opcode == 0x8d) {
            result = (float) Math.ceil((double) value);
        } else if (opcode == 0x8e) {
            result = (float) Math.floor((double) value);
        } else if (opcode == 0x8f) {
            result = (float) (value < 0.0f
                    ? Math.ceil((double) value)
                    : Math.floor((double) value));
        } else if (opcode == 0x90) {
            result = nearestF32(value);
        } else {
            result = (float) Math.sqrt((double) value);
        }
        pushI32(Float.floatToIntBits(result));
    }

    private void minimumF32() {
        int rightBits = popI32();
        int leftBits = popI32();
        float right = Float.intBitsToFloat(rightBits);
        float left = Float.intBitsToFloat(leftBits);
        if (left != left || right != right) {
            pushI32(0x7fc00000);
        } else if (left == 0.0f && right == 0.0f) {
            pushI32(leftBits | rightBits);
        } else {
            pushI32(Float.floatToIntBits(left < right ? left : right));
        }
    }

    private void maximumF32() {
        int rightBits = popI32();
        int leftBits = popI32();
        float right = Float.intBitsToFloat(rightBits);
        float left = Float.intBitsToFloat(leftBits);
        if (left != left || right != right) {
            pushI32(0x7fc00000);
        } else if (left == 0.0f && right == 0.0f) {
            pushI32(leftBits & rightBits);
        } else {
            pushI32(Float.floatToIntBits(left > right ? left : right));
        }
    }

    private void copySignF32() {
        int sign = popI32() & 0x80000000;
        int magnitude = popI32() & 0x7fffffff;
        pushI32(magnitude | sign);
    }

    private float nearestF32(float value) {
        if (value != value || value == 0.0f || value >= 8388608.0f || value <= -8388608.0f) {
            return value;
        }
        double floor = Math.floor((double) value);
        double difference = (double) value - floor;
        double rounded;
        if (difference < 0.5) {
            rounded = floor;
        } else if (difference > 0.5) {
            rounded = floor + 1.0;
        } else {
            rounded = (((long) floor & 1L) == 0) ? floor : floor + 1.0;
        }
        if (rounded == 0.0 && value < 0.0f) {
            return Float.intBitsToFloat(0x80000000);
        }
        return (float) rounded;
    }

    private double nearestF64(double value) {
        if (value != value
                || value == 0.0
                || value >= 4503599627370496.0
                || value <= -4503599627370496.0) {
            return value;
        }
        double floor = Math.floor(value);
        double difference = value - floor;
        double rounded;
        if (difference < 0.5) {
            rounded = floor;
        } else if (difference > 0.5) {
            rounded = floor + 1.0;
        } else {
            rounded = (((long) floor & 1L) == 0) ? floor : floor + 1.0;
        }
        if (rounded == 0.0 && value < 0.0) {
            return Double.longBitsToDouble(Long.MIN_VALUE);
        }
        return rounded;
    }

    private int truncateF32Signed(float value) {
        if (value != value || value < -2147483648.0f || value >= 2147483648.0f) {
            throw new WasmTrap("invalid conversion to integer");
        }
        return (int) value;
    }

    private int truncateF64Signed(double value) {
        if (value != value || value <= -2147483649.0 || value >= 2147483648.0) {
            throw new WasmTrap("invalid conversion to integer");
        }
        return (int) value;
    }

    private int truncateF64Unsigned(double value) {
        if (value != value || value <= -1.0 || value >= 4294967296.0) {
            throw new WasmTrap("invalid conversion to integer");
        }
        if (value < 2147483648.0) {
            return (int) value;
        }
        return ((int) (value - 2147483648.0)) ^ 0x80000000;
    }

    private long truncateI64Signed(double value) {
        if (value != value || value < -9223372036854775808.0 || value >= 9223372036854775808.0) {
            throw new WasmTrap("invalid conversion to integer");
        }
        return (long) value;
    }

    private long truncateI64Unsigned(double value) {
        if (value != value || value <= -1.0 || value >= 18446744073709551616.0) {
            throw new WasmTrap("invalid conversion to integer");
        }
        if (value < 9223372036854775808.0) {
            return (long) value;
        }
        return ((long) (value - 9223372036854775808.0)) ^ Long.MIN_VALUE;
    }

    private int saturateF32ToI32Signed(float value) {
        if (value != value) {
            return 0;
        }
        if (value <= -2147483648.0f) {
            return Integer.MIN_VALUE;
        }
        if (value >= 2147483648.0f) {
            return Integer.MAX_VALUE;
        }
        return (int) value;
    }

    private int saturateF32ToI32Unsigned(float value) {
        if (value != value || value <= 0.0f) {
            return 0;
        }
        if (value >= 4294967296.0f) {
            return -1;
        }
        if (value < 2147483648.0f) {
            return (int) value;
        }
        return ((int) (value - 2147483648.0f)) ^ Integer.MIN_VALUE;
    }

    private int saturateF64ToI32Signed(double value) {
        if (value != value) {
            return 0;
        }
        if (value <= -2147483648.0) {
            return Integer.MIN_VALUE;
        }
        if (value >= 2147483648.0) {
            return Integer.MAX_VALUE;
        }
        return (int) value;
    }

    private int saturateF64ToI32Unsigned(double value) {
        if (value != value || value <= 0.0) {
            return 0;
        }
        if (value >= 4294967296.0) {
            return -1;
        }
        if (value < 2147483648.0) {
            return (int) value;
        }
        return ((int) (value - 2147483648.0)) ^ Integer.MIN_VALUE;
    }

    private long saturateF64ToI64Signed(double value) {
        if (value != value) {
            return 0;
        }
        if (value <= -9223372036854775808.0) {
            return Long.MIN_VALUE;
        }
        if (value >= 9223372036854775808.0) {
            return Long.MAX_VALUE;
        }
        return (long) value;
    }

    private long saturateF64ToI64Unsigned(double value) {
        if (value != value || value <= 0.0) {
            return 0;
        }
        if (value >= 18446744073709551616.0) {
            return -1L;
        }
        if (value < 9223372036854775808.0) {
            return (long) value;
        }
        return ((long) (value - 9223372036854775808.0)) ^ Long.MIN_VALUE;
    }

    private double unsignedI64ToDouble(long value) {
        if (value >= 0) {
            return (double) value;
        }
        // Preserve the discarded low bit as a sticky bit before converting.
        // Converting the low 63 bits and then adding 2^63 can round twice.
        return (double) ((value >>> 1) | (value & 1L)) * 2.0;
    }

    private float unsignedI64ToFloat(long value) {
        if (value >= 0) {
            return (float) value;
        }
        return (float) ((value >>> 1) | (value & 1L)) * 2.0f;
    }

    private int floorF32Bits(int bits) {
        int magnitude = bits & 0x7fffffff;
        if (magnitude == 0 || magnitude >= 0x7f800000) {
            return bits;
        }
        float value = Float.intBitsToFloat(bits);
        return Float.floatToIntBits((float) Math.floor((double) value));
    }

    static strictfp int sinF32BitsForFastPath(int bits) {
        float original = Float.intBitsToFloat(bits);
        if (!(original > 0.0f || original < 0.0f)) {
            return bits;
        }

        int magnitudeBits = bits & 0x7fffffff;
        if (magnitudeBits == 0x7f800000) {
            return 0x7fc00001;
        }
        float magnitude = Float.intBitsToFloat(magnitudeBits);
        float quadrantFloat = magnitude * SIN_4_OVER_PI;
        int quadrant;
        if (quadrantFloat < SIN_I32_THRESHOLD) {
            quadrant = (int) quadrantFloat;
            quadrantFloat = (float) quadrant;
        } else {
            quadrantFloat = Float.intBitsToFloat(floorF32BitsStatic(
                    Float.floatToIntBits(quadrantFloat)));
            quadrant = 0x80000000;
        }

        int odd = quadrant & 1;
        float selectedQuadrant = odd != 0 ? quadrantFloat + 1.0f : quadrantFloat;
        float reduced = magnitude + selectedQuadrant * SIN_PI_OVER_4_PART1;
        reduced = reduced + selectedQuadrant * SIN_PI_OVER_4_PART2;
        reduced = reduced + selectedQuadrant * SIN_PI_OVER_4_PART3;
        float squared = reduced * reduced;
        int octant = (odd + quadrant) & 7;
        boolean negate = (original < 0.0f) ^ (octant > 3);
        int selectedOctant = octant > 3 ? octant - 4 : octant;
        boolean sinePolynomial = selectedOctant - 1 < 0 || selectedOctant - 1 > 1;

        float correction;
        float base;
        float coefficient1;
        float coefficient2;
        float coefficient3;
        float coefficient4;
        float outer;
        if (sinePolynomial) {
            correction = squared * SIN_SIN_CORRECTION_A + SIN_SIN_CORRECTION_B;
            coefficient1 = SIN_SIN_COEFFICIENT1;
            coefficient2 = SIN_SIN_COEFFICIENT2;
            coefficient3 = SIN_SIN_COEFFICIENT3;
            coefficient4 = SIN_SIN_COEFFICIENT4;
            base = reduced;
            outer = reduced;
        } else {
            correction = squared * SIN_COS_CORRECTION_A + SIN_COS_CORRECTION_B;
            base = squared * -0.5f + 1.0f;
            coefficient1 = SIN_COS_COEFFICIENT1;
            coefficient2 = SIN_COS_COEFFICIENT2;
            coefficient3 = SIN_COS_COEFFICIENT3;
            coefficient4 = SIN_COS_COEFFICIENT4;
            outer = squared;
        }

        float polynomial = squared * correction + coefficient4;
        polynomial = squared * polynomial + coefficient3;
        polynomial = squared * polynomial + coefficient2;
        polynomial = squared * polynomial + coefficient1;
        polynomial = outer * squared * polynomial;
        float result = base + polynomial;
        if (negate) {
            result = -result;
        }
        return Float.floatToIntBits(result);
    }

    private strictfp int sinF32Bits(int bits) {
        return sinF32BitsForFastPath(bits);
    }

    private static int floorF32BitsStatic(int bits) {
        int magnitude = bits & 0x7fffffff;
        if (magnitude == 0 || magnitude >= 0x7f800000) {
            return bits;
        }
        float value = Float.intBitsToFloat(bits);
        return Float.floatToIntBits((float) Math.floor((double) value));
    }

    private int truncateF32Unsigned(float value) {
        if (value != value || value <= -1.0f || value >= 4294967296.0f) {
            throw new WasmTrap("invalid conversion to integer");
        }
        if (value < 2147483648.0f) {
            return (int) value;
        }
        return ((int) (value - 2147483648.0f)) ^ 0x80000000;
    }

    private void pushI32(int value) {
        try {
            values[valueTop++] = value;
            return;
        } catch (ArrayIndexOutOfBoundsException failure) {
            if (valueTop > values.length || valueTop == Integer.MIN_VALUE) {
                valueTop--;
                throw new WasmTrap("value stack exhausted");
            }
            throw failure;
        }
    }

    private void push(long value) {
        try {
            values[valueTop++] = value;
            return;
        } catch (ArrayIndexOutOfBoundsException failure) {
            if (valueTop > values.length || valueTop == Integer.MIN_VALUE) {
                valueTop--;
                throw new WasmTrap("value stack exhausted");
            }
            throw failure;
        }
    }

    private long pop() {
        try {
            return values[--valueTop];
        } catch (ArrayIndexOutOfBoundsException failure) {
            valueTop++;
            if (valueTop <= 0) {
                throw new WasmTrap("value stack underflow");
            }
            valueTop--;
            throw failure;
        }
    }

    private int popI32() {
        try {
            return (int) values[--valueTop];
        } catch (ArrayIndexOutOfBoundsException failure) {
            valueTop++;
            if (valueTop <= 0) {
                throw new WasmTrap("value stack underflow");
            }
            valueTop--;
            throw failure;
        }
    }

    private long peek() {
        try {
            return values[valueTop - 1];
        } catch (ArrayIndexOutOfBoundsException failure) {
            if (valueTop <= 0) {
                throw new WasmTrap("value stack underflow");
            }
            throw failure;
        }
    }

    private int popI32First() {
        return popI32();
    }

    private int popI32Second() {
        return popI32();
    }

    private long popFirst() {
        return pop();
    }

    private long popSecond() {
        return pop();
    }

    private static final class LongCountTable {
        private long[] keys;
        private long[] counts;
        private boolean[] used;
        private int size;
        private int threshold;

        private LongCountTable(int initialCapacity) {
            int capacity = 16;
            while (capacity < initialCapacity) {
                capacity <<= 1;
            }
            keys = new long[capacity];
            counts = new long[capacity];
            used = new boolean[capacity];
            threshold = capacity / 2;
        }

        private void add(long key) {
            int slot = findSlot(key, keys, used);
            if (used[slot]) {
                counts[slot]++;
                return;
            }
            if (size >= threshold) {
                resize();
                slot = findSlot(key, keys, used);
            }
            used[slot] = true;
            keys[slot] = key;
            counts[slot] = 1;
            size++;
        }

        private long get(long key) {
            int slot = findSlot(key, keys, used);
            return used[slot] ? counts[slot] : 0;
        }

        private int capacity() {
            return keys.length;
        }

        private boolean used(int slot) {
            return used[slot];
        }

        private long key(int slot) {
            return keys[slot];
        }

        private long count(int slot) {
            return counts[slot];
        }

        private void clear() {
            if (size == 0) {
                return;
            }
            int slot;
            for (slot = 0; slot < used.length; slot++) {
                if (used[slot]) {
                    used[slot] = false;
                    counts[slot] = 0;
                }
            }
            size = 0;
        }

        private void resize() {
            long[] oldKeys = keys;
            long[] oldCounts = counts;
            boolean[] oldUsed = used;
            keys = new long[oldKeys.length << 1];
            counts = new long[keys.length];
            used = new boolean[keys.length];
            threshold = keys.length / 2;
            int slot;
            for (slot = 0; slot < oldUsed.length; slot++) {
                if (!oldUsed[slot]) {
                    continue;
                }
                int target = findSlot(oldKeys[slot], keys, used);
                used[target] = true;
                keys[target] = oldKeys[slot];
                counts[target] = oldCounts[slot];
            }
        }

        private static int findSlot(long key, long[] keys, boolean[] used) {
            int mask = keys.length - 1;
            int mixed = (int) (key ^ (key >>> 32));
            mixed ^= mixed >>> 16;
            mixed *= 0x7feb352d;
            mixed ^= mixed >>> 15;
            mixed *= 0x846ca68b;
            mixed ^= mixed >>> 16;
            int slot = mixed & mask;
            while (used[slot] && keys[slot] != key) {
                slot = (slot + 1) & mask;
            }
            return slot;
        }
    }
}
