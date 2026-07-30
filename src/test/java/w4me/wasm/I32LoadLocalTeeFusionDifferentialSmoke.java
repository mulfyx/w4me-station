package w4me.wasm;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import w4me.runtime.Wasm4Runtime;

/** Focused outer/compact and budget differential for i32.load + local.tee. */
public final class I32LoadLocalTeeFusionDifferentialSmoke {
    private I32LoadLocalTeeFusionDifferentialSmoke() {}

    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("usage: font.bin i32-load-local-tee-fusion.wasm");
        }
        byte[] font = readFile(arguments[0]);
        byte[] cartridge = readFile(arguments[1]);

        Result outerReference = run(font, cartridge, "update", 200000000L, false, false);
        Result outerCandidate = run(font, cartridge, "update", 200000000L, true, false);
        assertEquivalent("outer-success", outerReference, outerCandidate);
        assertSuccessResult(outerCandidate);
        if (outerReference.fusedSites != 0 || outerCandidate.fusedSites != 3) {
            throw new AssertionError("unexpected fused site count: reference="
                    + outerReference.fusedSites
                    + " candidate="
                    + outerCandidate.fusedSites);
        }
        if (outerReference.dispatches - outerCandidate.dispatches != 2) {
            throw new AssertionError("expected two saved outer dispatches: reference="
                    + outerReference.dispatches
                    + " candidate="
                    + outerCandidate.dispatches);
        }

        Result compactReference = run(font, cartridge, "update", 200000000L, false, true);
        Result compactCandidate = run(font, cartridge, "update", 200000000L, true, true);
        assertEquivalent("compact-success", compactReference, compactCandidate);
        if (compactReference.compactInstructions == 0 || compactCandidate.compactInstructions == 0) {
            throw new AssertionError("focused pair did not reach compact execution");
        }

        long firstBudget = outerReference.instructions - 25;
        long budget;
        for (budget = firstBudget; budget <= outerReference.instructions + 1; budget++) {
            assertEquivalent(
                    "outer-budget-" + budget,
                    run(font, cartridge, "update", budget, false, false),
                    run(font, cartridge, "update", budget, true, false));
            assertEquivalent(
                    "compact-budget-" + budget,
                    run(font, cartridge, "update", budget, false, true),
                    run(font, cartridge, "update", budget, true, true));
        }

        assertEquivalent(
                "outer-trap",
                run(font, cartridge, "trap_load", 200000000L, false, false),
                run(font, cartridge, "trap_load", 200000000L, true, false));
        assertEquivalent(
                "compact-trap",
                run(font, cartridge, "trap_load", 200000000L, false, true),
                run(font, cartridge, "trap_load", 200000000L, true, true));

        System.out.println("PASS i32-load-local-tee-fusion sites=3 outer-dispatches-saved=2"
                + " logical="
                + outerReference.instructions
                + " budget-cases=27x2 compact=exact trap=exact");
    }

    private static Result run(
            byte[] font,
            byte[] cartridge,
            String function,
            long instructionLimit,
            boolean loadTeeFusions,
            boolean compact)
            throws Exception {
        WasmModule module = WasmModule.read(cartridge, null, true, loadTeeFusions);
        Wasm4Runtime runtime = new Wasm4Runtime(font);
        runtime.initialize(module);
        WasmInterpreter interpreter = new WasmInterpreter(module, runtime);
        interpreter.setFastPathsEnabled(false);
        interpreter.setCompactExecutorEnabled(compact);
        interpreter.setTraceExecutorEnabled(false);
        interpreter.setIntegerCompactOpcodesEnabled(true);
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
                interpreter.dispatchesExecuted(),
                interpreter.compactInstructionsExecuted(),
                countFusedSites(module));
        module.close();
        runtime.close();
        return result;
    }

    private static int countFusedSites(WasmModule module) {
        int count = 0;
        int function;
        for (function = module.imports.length; function < module.functions.length; function++) {
            WasmModule.FunctionBody body = module.functions[function];
            int instruction;
            for (instruction = 0; instruction < body.instructionCount(); instruction++) {
                int opcode = WasmModule.originalOpcode(body.code[instruction * WasmModule.W4IR_STRIDE] & 0xffff);
                if (opcode == WasmModule.W4IR_I32_LOAD_LOCAL_TEE) {
                    count++;
                }
            }
        }
        return count;
    }

    private static void assertSuccessResult(Result result) {
        if (result.trap != null) {
            throw new AssertionError("unexpected success-path trap: " + result.trap);
        }
        requireInt("sum", 0x9be02467, loadI32(result.memory, 512));
        requireInt("xor", 0x9b9f9b97, loadI32(result.memory, 516));
    }

    private static int loadI32(byte[] memory, int address) {
        return (memory[address] & 0xff)
                | ((memory[address + 1] & 0xff) << 8)
                | ((memory[address + 2] & 0xff) << 16)
                | ((memory[address + 3] & 0xff) << 24);
    }

    private static void requireInt(String label, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquivalent(String label, Result reference, Result candidate) {
        requireEquals(label, "trap", reference.trap, candidate.trap);
        requireEquals(
                label, "instructions", Long.toString(reference.instructions), Long.toString(candidate.instructions));
        compareBytes(label, "memory", reference.memory, candidate.memory);
        compareLongs(label, "globals", reference.globals, candidate.globals);
        compareInts(label, "table", reference.table, candidate.table);
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
        private final long dispatches;
        private final int compactInstructions;
        private final int fusedSites;

        private Result(
                byte[] sourceMemory,
                long[] sourceGlobals,
                int[] sourceTable,
                String trap,
                long instructions,
                long dispatches,
                int compactInstructions,
                int fusedSites) {
            memory = copy(sourceMemory);
            globals = copy(sourceGlobals);
            table = copy(sourceTable);
            this.trap = trap;
            this.instructions = instructions;
            this.dispatches = dispatches;
            this.compactInstructions = compactInstructions;
            this.fusedSites = fusedSites;
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
