package w4me.wasm;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import w4me.runtime.Wasm4Runtime;

/** Focused descriptor and legacy-execution coverage for structured branches. */
public final class StaticBranchDescriptorSmoke {
    private static final String[] EXPECTED_DESCRIPTORS = {
        "direct=2:0 d0=5,0,1,0,0",
        "direct=8:0 d0=2,0,1,1,1",
        "direct=3:0 d0=8,0,1,0,0",
        "direct=1:0 d0=-1,0,1,0,2",
        "direct=3:0 d0=7,0,1,0,0",
        "direct=2:0 direct=4:1 d0=6,0,1,0,0 d1=6,0,1,0,0",
        "table0=0,1,2,3 d0=9,0,1,0,0 d1=9,0,1,0,0" + " d2=6,0,1,1,0 d3=9,0,1,0,0",
        "table0=0,1,2,3 d0=9,0,1,0,0 d1=9,0,1,0,0" + " d2=6,0,1,1,0 d3=9,0,1,0,0",
        "table0=0,1,2,3 d0=9,0,1,0,0 d1=9,0,1,0,0" + " d2=6,0,1,1,0 d3=9,0,1,0,0",
        "table0=0,1,2 d0=3,0,1,2,1 d1=13,0,1,0,0 d2=13,0,1,0,0",
        "table0=0,1 d0=-1,0,1,0,2 d1=-1,0,1,0,2",
        "table0=0,1 d0=20,0,16,0,0 d1=20,0,16,0,0"
    };
    private static final String[] CASE_EXPORTS = {
        "case_block_result",
        "case_loop_parameter",
        "case_if_result",
        "case_function_branch",
        "case_nested_depth",
        "case_unreachable_polymorphic",
        "case_table_block_repeated",
        "case_table_block_inner",
        "case_table_block_default",
        "case_table_loop",
        "case_table_function",
        "case_max_arity"
    };
    private static final int[] CASE_LOGICAL_INSTRUCTIONS = {8, 29, 9, 6, 9, 8, 10, 13, 10, 24, 7, 38};

    private StaticBranchDescriptorSmoke() {}

    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("usage: font.bin static-branch-descriptors.wasm");
        }
        byte[] font = readFile(arguments[0]);
        byte[] cartridge = readFile(arguments[1]);
        WasmModule module = WasmModule.read(cartridge);
        assertDescriptors(module);

        Wasm4Runtime runtime = new Wasm4Runtime(font);
        runtime.initialize(module);
        WasmInterpreter interpreter = new WasmInterpreter(module, runtime);
        interpreter.setFastPathsEnabled(false);
        interpreter.setCompactExecutorEnabled(false);
        interpreter.setTraceExecutorEnabled(false);
        int fixture;
        for (fixture = 0; fixture < CASE_EXPORTS.length; fixture++) {
            interpreter.invoke(CASE_EXPORTS[fixture]);
            if (interpreter.instructionsExecuted() != CASE_LOGICAL_INSTRUCTIONS[fixture]) {
                throw new AssertionError(CASE_EXPORTS[fixture]
                        + " logical instruction count changed: expected "
                        + CASE_LOGICAL_INSTRUCTIONS[fixture]
                        + ", got "
                        + interpreter.instructionsExecuted());
            }
        }
        interpreter.invoke("update");
        requireI32(module.memory, 0, 11);
        requireI32(module.memory, 4, 0);
        requireI32(module.memory, 8, 22);
        requireI32(module.memory, 12, 33);
        requireI32(module.memory, 16, 44);
        requireI64(module.memory, 20, 55L);
        requireI32(module.memory, 28, 66);
        requireI32(module.memory, 32, 77);
        requireI32(module.memory, 36, 86);
        requireI32(module.memory, 40, 0);
        requireI32(module.memory, 44, 99);
        if (interpreter.instructionsExecuted() != 160) {
            throw new AssertionError("legacy logical instruction count changed: expected 160, got "
                    + interpreter.instructionsExecuted());
        }
        System.out.println("PASS static-branch-descriptors functions="
                + module.functions.length
                + " cases="
                + CASE_EXPORTS.length
                + " traps=none"
                + " logical="
                + interpreter.instructionsExecuted());
        module.close();
        runtime.close();
    }

    private static void assertDescriptors(WasmModule module) {
        if (module.functions.length < EXPECTED_DESCRIPTORS.length) {
            throw new AssertionError("unexpected focused function count: " + module.functions.length);
        }
        int function;
        for (function = 0; function < module.functions.length; function++) {
            WasmModule.FunctionBody body = module.functions[function];
            StringBuffer line = new StringBuffer();
            if (body != null) {
                int direct;
                for (direct = 0; direct < body.branchDescriptorPcs.length; direct++) {
                    appendSeparator(line);
                    line.append("direct=")
                            .append(body.branchDescriptorPcs[direct])
                            .append(':')
                            .append(body.branchDescriptorIndices[direct]);
                }
                int table;
                for (table = 0; table < body.branchDescriptorTables.length; table++) {
                    appendSeparator(line);
                    line.append("table").append(table).append('=');
                    int entry;
                    for (entry = 0; entry < body.branchDescriptorTables[table].length; entry++) {
                        if (entry != 0) {
                            line.append(',');
                        }
                        line.append(body.branchDescriptorTables[table][entry]);
                    }
                }
                int descriptorCount = body.branchDescriptors.length / WasmModule.BRANCH_DESCRIPTOR_STRIDE;
                int descriptor;
                for (descriptor = 0; descriptor < descriptorCount; descriptor++) {
                    int offset = descriptor * WasmModule.BRANCH_DESCRIPTOR_STRIDE;
                    appendSeparator(line);
                    line.append('d').append(descriptor).append('=');
                    int field;
                    for (field = 0; field < WasmModule.BRANCH_DESCRIPTOR_STRIDE; field++) {
                        if (field != 0) {
                            line.append(',');
                        }
                        line.append(body.branchDescriptors[offset + field]);
                    }
                }
            }
            String expected = function < EXPECTED_DESCRIPTORS.length ? EXPECTED_DESCRIPTORS[function] : "";
            if (!expected.equals(line.toString())) {
                throw new AssertionError("descriptor mismatch at function "
                        + function
                        + ": expected '"
                        + expected
                        + "', got '"
                        + line
                        + "'");
            }
        }
    }

    private static void appendSeparator(StringBuffer value) {
        if (value.length() != 0) {
            value.append(' ');
        }
    }

    private static void requireI32(byte[] memory, int address, int expected) {
        int actual = (memory[address] & 0xff)
                | ((memory[address + 1] & 0xff) << 8)
                | ((memory[address + 2] & 0xff) << 16)
                | ((memory[address + 3] & 0xff) << 24);
        if (actual != expected) {
            throw new AssertionError("i32 mismatch at " + address + ": expected " + expected + ", got " + actual);
        }
    }

    private static void requireI64(byte[] memory, int address, long expected) {
        long actual = 0;
        int index;
        for (index = 0; index < 8; index++) {
            actual |= ((long) memory[address + index] & 0xffL) << (index * 8);
        }
        if (actual != expected) {
            throw new AssertionError("i64 mismatch at " + address + ": expected " + expected + ", got " + actual);
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
}
