package w4me.wasm;

import w4me.runtime.Wasm4Runtime;

/** Explicit artifact configuration used by differential and benchmark evidence. */
final class InterpreterVariant {
    static final InterpreterVariant REFERENCE =
            new InterpreterVariant("reference", false, false, false, false, false, false, false, false);
    static final InterpreterVariant CURRENT =
            new InterpreterVariant("current", true, false, true, true, true, false, false, true);
    static final InterpreterVariant SEVEN_OPCODE =
            new InterpreterVariant("seven-opcode", true, false, true, true, true, true, false, true);
    static final InterpreterVariant HOST_IMPORT_ID =
            new InterpreterVariant("host-import-id", true, false, true, true, true, true, true, true);
    static final InterpreterVariant LOAD_TEE_BASELINE =
            new InterpreterVariant("load-tee-baseline", true, false, true, true, true, true, true, false);
    static final InterpreterVariant LOAD_TEE =
            new InterpreterVariant("load-tee", true, false, true, true, true, true, true, true);
    static final InterpreterVariant PROFILE =
            new InterpreterVariant("profile", true, false, false, false, true, true, true, true);

    final String name;
    final boolean extendedFusions;
    final boolean fastPaths;
    final boolean compactExecutor;
    final boolean traceExecutor;
    final boolean directNumericIntrinsics;
    final boolean integerCompactOpcodes;
    final boolean numericHostImportDispatch;
    final boolean loadTeeFusions;

    private InterpreterVariant(
            String name,
            boolean extendedFusions,
            boolean fastPaths,
            boolean compactExecutor,
            boolean traceExecutor,
            boolean directNumericIntrinsics,
            boolean integerCompactOpcodes,
            boolean numericHostImportDispatch,
            boolean loadTeeFusions) {
        this.name = name;
        this.extendedFusions = extendedFusions;
        this.fastPaths = fastPaths;
        this.compactExecutor = compactExecutor;
        this.traceExecutor = traceExecutor;
        this.directNumericIntrinsics = directNumericIntrinsics;
        this.integerCompactOpcodes = integerCompactOpcodes;
        this.numericHostImportDispatch = numericHostImportDispatch;
        this.loadTeeFusions = loadTeeFusions;
    }

    WasmModule read(byte[] cartridge) throws Exception {
        return WasmModule.read(cartridge, null, extendedFusions, loadTeeFusions);
    }

    WasmInterpreter interpreter(WasmModule module, Wasm4Runtime runtime) {
        WasmInterpreter result = new WasmInterpreter(module, runtime);
        result.setFastPathsEnabled(fastPaths);
        result.setCompactExecutorEnabled(compactExecutor);
        result.setTraceExecutorEnabled(traceExecutor);
        result.setDirectNumericIntrinsicsEnabled(directNumericIntrinsics);
        result.setIntegerCompactOpcodesEnabled(integerCompactOpcodes);
        result.setNumericHostImportDispatchEnabled(numericHostImportDispatch);
        result.setInstructionLimit(200000000L);
        return result;
    }

    String configuration() {
        return "extended-fusions="
                + onOff(extendedFusions)
                + ",fast-paths="
                + onOff(fastPaths)
                + ",compact="
                + onOff(compactExecutor)
                + ",trace="
                + onOff(traceExecutor)
                + ",direct-intrinsics="
                + onOff(directNumericIntrinsics)
                + ",integer-compact="
                + onOff(integerCompactOpcodes)
                + ",host-import-id="
                + onOff(numericHostImportDispatch)
                + ",load-tee-fusions="
                + onOff(loadTeeFusions);
    }

    private static String onOff(boolean value) {
        return value ? "on" : "off";
    }
}
