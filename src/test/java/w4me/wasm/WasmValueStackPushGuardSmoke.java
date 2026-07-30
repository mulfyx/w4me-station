package w4me.wasm;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import w4me.runtime.storage.RmsW4IrStore;

/** Exact edge and malformed-cache coverage for the generic value-stack push guard. */
public final class WasmValueStackPushGuardSmoke {
    private static final int VALUE_STACK_LIMIT = 4096;
    private static final int MALFORMED_PUSH_COUNT = VALUE_STACK_LIMIT + 1;

    private static final WasmHost NO_HOST = new WasmHost() {
        public long invoke(int importId, long[] valueStack, int argumentBase, int argumentCount, WasmModule module) {
            throw new AssertionError("unexpected numeric host call");
        }

        public long invoke(
                String moduleName,
                String name,
                long[] valueStack,
                int argumentBase,
                int argumentCount,
                WasmModule module) {
            throw new AssertionError("unexpected string host call");
        }
    };

    private WasmValueStackPushGuardSmoke() {}

    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        verifyPrivateHelperEdges();
        verifyPrivatePopEdges();
        verifyValidatedNestedOverflow();
        verifyMalformedCachedOverflow();
        System.out.println("PASS value-stack-guards"
                + " helper-edges=49"
                + " nested-overflow=PASS"
                + " cached-paged-promoted=PASS");
    }

    private static void verifyPrivateHelperEdges() throws Exception {
        WasmModule module = WasmModule.read(minimalModule());
        try {
            final WasmInterpreter interpreter = new WasmInterpreter(module, NO_HOST);
            Field topField = WasmInterpreter.class.getDeclaredField("valueTop");
            topField.setAccessible(true); // NOPMD -- Differential test inspects private state.
            Field valuesField = WasmInterpreter.class.getDeclaredField("values");
            valuesField.setAccessible(true); // NOPMD -- Differential test inspects private state.
            Method pushMethod = WasmInterpreter.class.getDeclaredMethod("push", new Class[] {Long.TYPE});
            pushMethod.setAccessible(true); // NOPMD -- Differential test inspects private state.

            int[] tops = new int[] {
                0,
                VALUE_STACK_LIMIT - 1,
                VALUE_STACK_LIMIT,
                VALUE_STACK_LIMIT + 1,
                Integer.MAX_VALUE,
                -1,
                Integer.MIN_VALUE
            };
            int index;
            for (index = 0; index < tops.length; index++) {
                int oldTop = tops[index];
                topField.setInt(interpreter, oldTop);
                Throwable failure = invokePush(pushMethod, interpreter, 0x1122334455667788L);
                int actualTop = topField.getInt(interpreter);
                if (oldTop >= 0 && oldTop < VALUE_STACK_LIMIT) {
                    if (failure != null) {
                        throw new AssertionError("valid push failed at top " + oldTop + ": " + failure);
                    }
                    if (actualTop != oldTop + 1) {
                        throw new AssertionError("valid push top mismatch at " + oldTop + ": " + actualTop);
                    }
                    long[] values = (long[]) valuesField.get(interpreter);
                    if (values[oldTop] != 0x1122334455667788L) {
                        throw new AssertionError("valid push value mismatch at " + oldTop);
                    }
                } else if (oldTop >= VALUE_STACK_LIMIT) {
                    requireExhausted(failure, "high top " + oldTop);
                    if (actualTop != oldTop) {
                        throw new AssertionError("overflow changed top " + oldTop + " to " + actualTop);
                    }
                } else {
                    if (!(failure instanceof ArrayIndexOutOfBoundsException)) {
                        throw new AssertionError("negative top changed failure at " + oldTop + ": " + failure);
                    }
                    if (actualTop != oldTop + 1) {
                        throw new AssertionError("negative top changed mutation at " + oldTop + ": " + actualTop);
                    }
                }
            }
        } finally {
            module.close();
        }
    }

    private static void verifyPrivatePopEdges() throws Exception {
        WasmModule module = WasmModule.read(minimalModule());
        try {
            WasmInterpreter interpreter = new WasmInterpreter(module, NO_HOST);
            Field topField = WasmInterpreter.class.getDeclaredField("valueTop");
            topField.setAccessible(true); // NOPMD -- Differential test inspects private state.
            Field valuesField = WasmInterpreter.class.getDeclaredField("values");
            valuesField.setAccessible(true); // NOPMD -- Differential test inspects private state.
            final long[] values = (long[]) valuesField.get(interpreter);
            Method popMethod = WasmInterpreter.class.getDeclaredMethod("pop", new Class[0]);
            Method popI32Method = WasmInterpreter.class.getDeclaredMethod("popI32", new Class[0]);
            Method peekMethod = WasmInterpreter.class.getDeclaredMethod("peek", new Class[0]);
            popMethod.setAccessible(true); // NOPMD -- Differential test inspects private state.
            popI32Method.setAccessible(true); // NOPMD -- Differential test inspects private state.
            peekMethod.setAccessible(true); // NOPMD -- Differential test inspects private state.

            int[] tops = new int[] {
                Integer.MIN_VALUE,
                Integer.MIN_VALUE + 1,
                -2,
                -1,
                0,
                1,
                2,
                VALUE_STACK_LIMIT - 2,
                VALUE_STACK_LIMIT - 1,
                VALUE_STACK_LIMIT,
                VALUE_STACK_LIMIT + 1,
                VALUE_STACK_LIMIT + 2,
                Integer.MAX_VALUE - 1,
                Integer.MAX_VALUE
            };
            int index;
            for (index = 0; index < tops.length; index++) {
                verifyPopEdge("pop", popMethod, interpreter, topField, values, tops[index], false);
                verifyPopEdge("popI32", popI32Method, interpreter, topField, values, tops[index], false);
                verifyPopEdge("peek", peekMethod, interpreter, topField, values, tops[index], true);
            }
        } finally {
            module.close();
        }
    }

    private static void verifyPopEdge(
            String label,
            Method method,
            WasmInterpreter interpreter,
            Field topField,
            long[] values,
            int oldTop,
            boolean peek)
            throws Exception {
        int index;
        for (index = 0; index < values.length; index++) {
            values[index] = 0x0f0f0f0f0f0f0f0fL;
        }
        topField.setInt(interpreter, oldTop);
        Throwable failure = invokeNoArgs(method, interpreter);
        int expectedTop = peek || oldTop <= 0 ? oldTop : oldTop - 1;
        int actualTop = topField.getInt(interpreter);
        if (actualTop != expectedTop) {
            throw new AssertionError(label + " top mismatch at " + oldTop + ": " + actualTop);
        }
        if (oldTop <= 0) {
            requireUnderflow(failure, label + " top " + oldTop);
        } else if (oldTop <= VALUE_STACK_LIMIT) {
            if (failure != null) {
                throw new AssertionError(label + " valid access failed at " + oldTop + ": " + failure);
            }
        } else if (!(failure instanceof ArrayIndexOutOfBoundsException)) {
            throw new AssertionError(label + " malformed high top changed failure at " + oldTop + ": " + failure);
        }
    }

    private static Throwable invokePush(Method pushMethod, WasmInterpreter interpreter, long value) throws Exception {
        try {
            pushMethod.invoke(interpreter, new Object[] {new Long(value)});
            return null;
        } catch (InvocationTargetException wrapped) {
            return wrapped.getTargetException();
        }
    }

    private static Throwable invokeNoArgs(Method method, WasmInterpreter interpreter) throws Exception {
        try {
            method.invoke(interpreter, new Object[0]);
            return null;
        } catch (InvocationTargetException wrapped) {
            return wrapped.getTargetException();
        }
    }

    private static void verifyValidatedNestedOverflow() throws Exception {
        WasmModule module = WasmModule.read(nestedOverflowModule());
        try {
            WasmInterpreter interpreter = new WasmInterpreter(module, NO_HOST);
            Throwable failure = invokeUpdate(interpreter);
            requireExhausted(failure, "validated nested stack");
            if (readValueTop(interpreter) != VALUE_STACK_LIMIT) {
                throw new AssertionError("validated overflow did not retain full stack: " + readValueTop(interpreter));
            }
        } finally {
            module.close();
        }
    }

    private static void verifyMalformedCachedOverflow() throws Exception {
        W4IrMalformedDescriptorCacheSmoke.installHeadlessKemulatorFrontend();
        byte[] cartridge = minimalModule();
        RmsW4IrStore staleStore = RmsW4IrStore.open(cartridge, 2);
        staleStore.discard();
        RmsW4IrStore store = RmsW4IrStore.open(cartridge, 2);
        WasmModule module = null;
        try {
            store.begin(1);
            store.writeFunction(
                    0, 0, malformedPushCode(), new int[0][], new int[0], new int[0], new int[0], new int[0][], 0L, 0);
            store.commit();
            module = WasmModule.read(cartridge, store);
            WasmInterpreter interpreter = new WasmInterpreter(module, NO_HOST);
            int invocation;
            for (invocation = 0; invocation < 6; invocation++) {
                Throwable failure = invokeUpdate(interpreter);
                requireExhausted(failure, "malformed cached stack " + invocation);
                if (readValueTop(interpreter) != VALUE_STACK_LIMIT) {
                    throw new AssertionError("cached overflow did not retain full stack: " + readValueTop(interpreter));
                }
            }
            if (!module.functions[0].isPromoted()) {
                throw new AssertionError("malformed cached function was not promoted");
            }
        } finally {
            if (module != null) {
                module.close();
            } else {
                store.discard();
            }
        }
    }

    private static Throwable invokeUpdate(WasmInterpreter interpreter) {
        try {
            interpreter.invoke("update");
            return null;
        } catch (Throwable failure) { // NOPMD -- Java ME API linkage fallback.
            return failure;
        }
    }

    private static int readValueTop(WasmInterpreter interpreter) throws Exception {
        Field topField = WasmInterpreter.class.getDeclaredField("valueTop");
        topField.setAccessible(true); // NOPMD -- Differential test inspects private state.
        return topField.getInt(interpreter);
    }

    private static void requireExhausted(Throwable failure, String label) {
        if (!(failure instanceof WasmTrap) || !"value stack exhausted".equals(failure.getMessage())) {
            throw new AssertionError(label + " reached the wrong failure: " + failure);
        }
    }

    private static void requireUnderflow(Throwable failure, String label) {
        if (!(failure instanceof WasmTrap) || !"value stack underflow".equals(failure.getMessage())) {
            throw new AssertionError(label + " reached the wrong failure: " + failure);
        }
    }

    private static int[] malformedPushCode() {
        int[] code = new int[(MALFORMED_PUSH_COUNT + 1) * WasmModule.W4IR_STRIDE];
        int instruction;
        for (instruction = 0; instruction < MALFORMED_PUSH_COUNT; instruction++) {
            int offset = instruction * WasmModule.W4IR_STRIDE;
            code[offset] = 0x41;
            code[offset + 1] = instruction;
        }
        code[MALFORMED_PUSH_COUNT * WasmModule.W4IR_STRIDE] = 0x0b;
        return code;
    }

    private static byte[] minimalModule() {
        ByteArrayOutputStream module = new ByteArrayOutputStream();
        writeHeader(module);
        writeSection(module, 1, bytes(1, 0x60, 0, 0));
        writeSection(module, 3, bytes(1, 0));
        writeSection(module, 5, bytes(1, 0, 1));
        writeSection(module, 7, bytes(1, 6, 'u', 'p', 'd', 'a', 't', 'e', 0, 0));
        writeSection(module, 10, bytes(1, 2, 0, 0x0b));
        return module.toByteArray();
    }

    private static byte[] nestedOverflowModule() {
        ByteArrayOutputStream module = new ByteArrayOutputStream();
        writeHeader(module);
        writeSection(module, 1, bytes(1, 0x60, 0, 0));
        writeSection(module, 3, bytes(2, 0, 0));
        writeSection(module, 5, bytes(1, 0, 1));
        writeSection(module, 7, bytes(1, 6, 'u', 'p', 'd', 'a', 't', 'e', 0, 0));

        ByteArrayOutputStream code = new ByteArrayOutputStream();
        writeVariableUnsignedInteger(code, 2);
        byte[] caller = bytes(0, 0x41, 0, 0x10, 1, 0x1a, 0x0b);
        writeVariableUnsignedInteger(code, caller.length);
        code.write(caller, 0, caller.length);

        ByteArrayOutputStream callee = new ByteArrayOutputStream();
        callee.write(0);
        int index;
        for (index = 0; index < VALUE_STACK_LIMIT; index++) {
            callee.write(0x41);
            callee.write(0);
        }
        for (index = 0; index < VALUE_STACK_LIMIT; index++) {
            callee.write(0x1a);
        }
        callee.write(0x0b);
        byte[] calleeBody = callee.toByteArray();
        writeVariableUnsignedInteger(code, calleeBody.length);
        code.write(calleeBody, 0, calleeBody.length);
        writeSection(module, 10, code.toByteArray());
        return module.toByteArray();
    }

    private static void writeHeader(ByteArrayOutputStream output) {
        byte[] header = bytes(0x00, 0x61, 0x73, 0x6d, 0x01, 0, 0, 0);
        output.write(header, 0, header.length);
    }

    private static void writeSection(ByteArrayOutputStream output, int id, byte[] payload) {
        output.write(id);
        writeVariableUnsignedInteger(output, payload.length);
        output.write(payload, 0, payload.length);
    }

    private static void writeVariableUnsignedInteger(ByteArrayOutputStream output, int value) {
        do {
            int next = value & 0x7f;
            value >>>= 7;
            if (value != 0) {
                next |= 0x80;
            }
            output.write(next);
        } while (value != 0);
    }

    private static byte[] bytes(int a, int b) {
        return new byte[] {(byte) a, (byte) b};
    }

    private static byte[] bytes(int a, int b, int c) {
        return new byte[] {(byte) a, (byte) b, (byte) c};
    }

    private static byte[] bytes(int a, int b, int c, int d) {
        return new byte[] {(byte) a, (byte) b, (byte) c, (byte) d};
    }

    private static byte[] bytes(int a, int b, int c, int d, int e, int f, int g) {
        return new byte[] {(byte) a, (byte) b, (byte) c, (byte) d, (byte) e, (byte) f, (byte) g};
    }

    private static byte[] bytes(int a, int b, int c, int d, int e, int f, int g, int h) {
        return new byte[] {
            (byte) a, (byte) b, (byte) c, (byte) d,
            (byte) e, (byte) f, (byte) g, (byte) h
        };
    }

    private static byte[] bytes(int a, int b, int c, int d, int e, int f, int g, int h, int i, int j) {
        return new byte[] {
            (byte) a, (byte) b, (byte) c, (byte) d, (byte) e,
            (byte) f, (byte) g, (byte) h, (byte) i, (byte) j
        };
    }
}
