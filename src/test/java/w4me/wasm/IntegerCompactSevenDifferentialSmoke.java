package w4me.wasm;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import w4me.runtime.Wasm4Runtime;

/** Focused same-build differential for the seven integer compact opcodes. */
public final class IntegerCompactSevenDifferentialSmoke {
    private IntegerCompactSevenDifferentialSmoke() {}

    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("usage: font.bin integer-compact-seven.wasm");
        }
        byte[] font = readFile(arguments[0]);
        byte[] cartridge = readFile(arguments[1]);

        Result current = run(font, cartridge, "update", 200000000L, false);
        Result candidate = run(font, cartridge, "update", 200000000L, true);
        assertEquivalent("success", current, candidate);
        if (current.trap != null) {
            throw new AssertionError("unexpected success-path trap: " + current.trap);
        }
        if (candidate.compactInstructions <= current.compactInstructions) {
            throw new AssertionError("seven-opcode candidate did not expand compact execution: current="
                    + current.compactInstructions
                    + " candidate="
                    + candidate.compactInstructions);
        }

        long firstBudget = current.instructions - 40;
        long budget;
        for (budget = firstBudget; budget <= current.instructions + 1; budget++) {
            Result budgetCurrent = run(font, cartridge, "update", budget, false);
            Result budgetCandidate = run(font, cartridge, "update", budget, true);
            assertEquivalent("budget-" + budget, budgetCurrent, budgetCandidate);
        }

        assertEquivalent(
                "trap-load",
                run(font, cartridge, "trap_load", 200000000L, false),
                run(font, cartridge, "trap_load", 200000000L, true));
        assertEquivalent(
                "trap-store",
                run(font, cartridge, "trap_store", 200000000L, false),
                run(font, cartridge, "trap_store", 200000000L, true));

        System.out.println("PASS integer-compact-seven logical="
                + current.instructions
                + " current-compact="
                + current.compactInstructions
                + " candidate-compact="
                + candidate.compactInstructions
                + " budget-cases=42 traps=load,store state=exact");
    }

    private static Result run(
            byte[] font, byte[] cartridge, String function, long instructionLimit, boolean integerCompactOpcodes)
            throws Exception {
        WasmModule module = WasmModule.read(cartridge, null, false);
        Wasm4Runtime runtime = new Wasm4Runtime(font);
        runtime.initialize(module);
        WasmInterpreter interpreter = new WasmInterpreter(module, runtime);
        interpreter.setFastPathsEnabled(false);
        interpreter.setTraceExecutorEnabled(false);
        interpreter.setIntegerCompactOpcodesEnabled(integerCompactOpcodes);
        interpreter.setInstructionLimit(instructionLimit);
        String trap = null;
        try {
            interpreter.invoke(function);
        } catch (WasmTrap error) {
            trap = error.getMessage();
        }
        Result result = new Result(
                module.memory,
                module.globals,
                module.table,
                trap,
                interpreter.instructionsExecuted(),
                interpreter.compactInstructionsExecuted());
        module.close();
        runtime.close();
        return result;
    }

    private static void assertEquivalent(String label, Result current, Result candidate) {
        requireEquals(label, "trap", current.trap, candidate.trap);
        requireEquals(
                label, "instructions", Long.toString(current.instructions), Long.toString(candidate.instructions));
        compareBytes(label, "memory", current.memory, candidate.memory);
        compareLongs(label, "globals", current.globals, candidate.globals);
        compareInts(label, "table", current.table, candidate.table);
    }

    private static void compareBytes(String label, String field, byte[] left, byte[] right) {
        if (left.length != right.length) {
            throw new AssertionError(label + " " + field + " length mismatch");
        }
        int index;
        for (index = 0; index < left.length; index++) {
            if (left[index] != right[index]) {
                throw new AssertionError(label + " " + field + " mismatch at " + index);
            }
        }
    }

    private static void compareLongs(String label, String field, long[] left, long[] right) {
        if (left.length != right.length) {
            throw new AssertionError(label + " " + field + " length mismatch");
        }
        int index;
        for (index = 0; index < left.length; index++) {
            if (left[index] != right[index]) {
                throw new AssertionError(label + " " + field + " mismatch at " + index);
            }
        }
    }

    private static void compareInts(String label, String field, int[] left, int[] right) {
        if (left.length != right.length) {
            throw new AssertionError(label + " " + field + " length mismatch");
        }
        int index;
        for (index = 0; index < left.length; index++) {
            if (left[index] != right[index]) {
                throw new AssertionError(label + " " + field + " mismatch at " + index);
            }
        }
    }

    private static void requireEquals(String label, String field, String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(label + " " + field + " expected=" + expected + " actual=" + actual);
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

    private static final class Result {
        private final byte[] memory;
        private final long[] globals;
        private final int[] table;
        private final String trap;
        private final long instructions;
        private final int compactInstructions;

        private Result(
                byte[] sourceMemory,
                long[] sourceGlobals,
                int[] sourceTable,
                String trap,
                long instructions,
                int compactInstructions) {
            memory = copy(sourceMemory);
            globals = copy(sourceGlobals);
            table = copy(sourceTable);
            this.trap = trap;
            this.instructions = instructions;
            this.compactInstructions = compactInstructions;
        }

        private static byte[] copy(byte[] source) {
            byte[] result = new byte[source.length];
            System.arraycopy(source, 0, result, 0, source.length);
            return result;
        }

        private static long[] copy(long[] source) {
            long[] result = new long[source.length];
            System.arraycopy(source, 0, result, 0, source.length);
            return result;
        }

        private static int[] copy(int[] source) {
            int[] result = new int[source.length];
            System.arraycopy(source, 0, result, 0, source.length);
            return result;
        }
    }
}
