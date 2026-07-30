package w4me;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import w4me.runtime.Wasm4Runtime;
import w4me.wasm.WasmHost;
import w4me.wasm.WasmModule;
import w4me.wasm.WasmTrap;

/** Provides the blit plain geometry smoke implementation. */
public final class BlitPlainGeometrySmoke {
    private static final int SOURCE_POINTER = 4096;

    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("usage: font.bin cartridge.wasm");
        }
        WasmModule module = WasmModule.read(readFile(arguments[1]));
        Wasm4Runtime runtime = new Wasm4Runtime(readFile(arguments[0]));
        runtime.initialize(module);

        int[][] geometries = {
            {0, 0, 1, 1, 0, 0, 1},
            {11, 17, 13, 9, 3, 2, 23},
            {1, 3, 9, 3, 0, 0, 9},
            {-5, -4, 14, 12, 1, 1, 19},
            {154, 156, 12, 9, 4, 3, 25},
            {Integer.MIN_VALUE, 0, 1, 1, 0, 0, 1},
            {Integer.MAX_VALUE, 0, 1, 1, 0, 0, 1},
            {0, Integer.MIN_VALUE, 1, 1, 0, 0, 1},
            {0, Integer.MAX_VALUE, 1, 1, 0, 0, 1}
        };
        int[] colors = {0x1234, 0x0000, 0xffff, 0x5091, 0x0010, 0x2010};
        int caseCount = 0;
        int flags;
        for (flags = 0; flags < 16; flags++) {
            int geometryIndex;
            for (geometryIndex = 0; geometryIndex < geometries.length; geometryIndex++) {
                int colorIndex;
                for (colorIndex = 0; colorIndex < colors.length; colorIndex++) {
                    verifyCase(
                            runtime,
                            module,
                            SOURCE_POINTER,
                            geometries[geometryIndex],
                            flags,
                            colors[colorIndex],
                            caseCount);
                    caseCount++;
                }
            }
        }
        verifyCase(
                runtime,
                module,
                SOURCE_POINTER,
                geometries[1],
                16,
                0x1234,
                caseCount++); // NOPMD -- Compact Java 1.3 cursor bytecode.
        verifyCase(
                runtime,
                module,
                SOURCE_POINTER,
                geometries[1],
                17,
                0x5091,
                caseCount++); // NOPMD -- Compact Java 1.3 cursor bytecode.
        verifyCase(
                runtime,
                module,
                SOURCE_POINTER,
                geometries[1],
                31,
                0xffff,
                caseCount++); // NOPMD -- Compact Java 1.3 cursor bytecode.
        verifyOverlap(runtime, module, 0, caseCount++); // NOPMD -- Compact Java 1.3 cursor bytecode.
        verifyOverlap(runtime, module, 1, caseCount++); // NOPMD -- Compact Java 1.3 cursor bytecode.
        verifyDestructiveOverlap(runtime, module, 0, caseCount++); // NOPMD -- Compact Java 1.3 cursor bytecode.
        verifyDestructiveOverlap(runtime, module, 1, caseCount++); // NOPMD -- Compact Java 1.3 cursor bytecode.
        verifyOffsetDestructiveOverlap(runtime, module, 0, caseCount++); // NOPMD -- Compact Java 1.3 cursor bytecode.
        verifyOffsetDestructiveOverlap(runtime, module, 1, caseCount++); // NOPMD -- Compact Java 1.3 cursor bytecode.
        verifyBlitDelegation(runtime, module, caseCount++); // NOPMD -- Compact Java 1.3 cursor bytecode.
        verifyNoOpAndTraps(runtime, module);
        runtime.close();

        System.out.println("PASS blit-plain-geometry cases="
                + caseCount
                + " flags=0..17,31 clipping=all-sides overlap=exact traps=exact");
    }

    private static void verifyCase(
            Wasm4Runtime runtime, WasmModule module, int pointer, int[] geometry, int flags, int colors, int salt) {
        byte[] actual = module.memory();
        initialize(actual, salt);
        writeU16(actual, Wasm4Runtime.DRAW_COLORS, colors);
        byte[] expected = copy(actual);
        referenceBlitSub(
                expected,
                pointer,
                geometry[0],
                geometry[1],
                geometry[2],
                geometry[3],
                geometry[4],
                geometry[5],
                geometry[6],
                flags);
        long[] values = {
            pointer, geometry[0], geometry[1], geometry[2], geometry[3], geometry[4], geometry[5], geometry[6], flags
        };
        runtime.invoke(WasmHost.IMPORT_BLIT_SUB, values, 0, values.length, module);
        requireMemory("case " + salt + " flags=" + flags, expected, actual);
    }

    private static void verifyOverlap(Wasm4Runtime runtime, WasmModule module, int flags, int salt) {
        int[] geometry = {3, 5, 17, 7, 1, 1, 23};
        verifyCase(runtime, module, Wasm4Runtime.FRAMEBUFFER, geometry, flags, 0x5091, salt);
    }

    private static void verifyBlitDelegation(Wasm4Runtime runtime, WasmModule module, int salt) {
        byte[] actual = module.memory();
        initialize(actual, salt);
        writeU16(actual, Wasm4Runtime.DRAW_COLORS, 0x1234);
        byte[] expected = copy(actual);
        int width = 13;
        int height = 9;
        referenceBlitSub(expected, SOURCE_POINTER, -3, 7, width, height, 0, 0, width, 1);
        long[] values = {SOURCE_POINTER, -3, 7, width, height, 1};
        runtime.invoke(WasmHost.IMPORT_BLIT, values, 0, values.length, module);
        requireMemory("blit delegation", expected, actual);
    }

    private static void verifyDestructiveOverlap(Wasm4Runtime runtime, WasmModule module, int flags, int salt) {
        int[] geometry = {0, 0, 8, 1, 0, 0, 8};
        verifyCase(runtime, module, Wasm4Runtime.FRAMEBUFFER, geometry, flags, 0x5091, salt);
    }

    private static void verifyOffsetDestructiveOverlap(Wasm4Runtime runtime, WasmModule module, int flags, int salt) {
        int[] geometry = {1, 0, 8, 1, 0, 0, 8};
        verifyCase(runtime, module, Wasm4Runtime.FRAMEBUFFER, geometry, flags, 0x0010, salt);
    }

    private static void verifyNoOpAndTraps(Wasm4Runtime runtime, WasmModule module) {
        byte[] memory = module.memory();
        initialize(memory, 991);
        byte[] before = copy(memory);
        invokeBlitSub(runtime, module, SOURCE_POINTER, 0, 0, 0, 1, -1, -1, 0, 0);
        requireMemory("zero-width early return", before, memory);

        expectTrap(
                runtime, module, new long[] {SOURCE_POINTER, 0, 0, 1, 1, -1, 0, 1, 0}, "invalid blit source geometry");
        expectTrap(
                runtime, module, new long[] {SOURCE_POINTER, 0, 0, 1, 1, 0, 0, 0, 1}, "invalid blit source geometry");
        expectTrap(
                runtime,
                module,
                new long[] {SOURCE_POINTER, 0, 0, 1, 1, 0, Integer.MAX_VALUE, 1, 0},
                "blit source is too large");
        expectTrap(
                runtime,
                module,
                new long[] {memory.length, 0, 0, 1, 1, 0, 0, 1, 0},
                "host function memory range is out of bounds");
    }

    private static void expectTrap(Wasm4Runtime runtime, WasmModule module, long[] values, String expectedMessage) {
        byte[] memory = module.memory();
        initialize(memory, expectedMessage.length());
        byte[] before = copy(memory);
        try {
            runtime.invoke(WasmHost.IMPORT_BLIT_SUB, values, 0, values.length, module);
            throw new AssertionError("accepted invalid blit: " + expectedMessage);
        } catch (WasmTrap expected) {
            if (!expectedMessage.equals(expected.getMessage())) {
                throw new AssertionError( // NOPMD -- CLDC 1.1 does not provide portable exception-cause chaining.
                        "wrong trap: expected '" + expectedMessage + "', got '" + expected.getMessage() + "'");
            }
        }
        requireMemory("trap rollback " + expectedMessage, before, memory);
    }

    private static void invokeBlitSub(
            Wasm4Runtime runtime,
            WasmModule module,
            int pointer,
            int destinationX,
            int destinationY,
            int width,
            int height,
            int sourceX,
            int sourceY,
            int sourceStride,
            int flags) {
        long[] values = {pointer, destinationX, destinationY, width, height, sourceX, sourceY, sourceStride, flags};
        runtime.invoke(WasmHost.IMPORT_BLIT_SUB, values, 0, values.length, module);
    }

    private static void referenceBlitSub(
            byte[] memory,
            int pointer,
            int destinationX,
            int destinationY,
            int width,
            int height,
            int sourceX,
            int sourceY,
            int sourceStride,
            int flags) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (sourceX < 0 || sourceY < 0 || sourceStride <= 0) {
            throw new WasmTrap("invalid blit source geometry");
        }
        boolean twoBitsPerPixel = (flags & 1) != 0;
        boolean flipX = (flags & 2) != 0;
        final boolean flipY = (flags & 4) != 0;
        boolean rotate = (flags & 8) != 0;
        long lastPixel = ((long) sourceY + height - 1L) * sourceStride + sourceX + width - 1L;
        long bitLength = (lastPixel + 1L) * (twoBitsPerPixel ? 2L : 1L);
        if (lastPixel < 0 || bitLength > ((long) memory.length << 3)) {
            throw new WasmTrap("blit source is too large");
        }
        int byteLength = (int) ((bitLength + 7) >> 3);
        if (pointer < 0 || byteLength < 0 || byteLength > memory.length - pointer) {
            throw new WasmTrap("host function memory range is out of bounds");
        }

        if (rotate) {
            flipX = !flipX;
        }
        long clipHorizontalOrigin = rotate ? destinationY : destinationX;
        long clipVerticalOrigin = rotate ? destinationX : destinationY;
        int clipHorizontalMinimum = clampToRange(-clipHorizontalOrigin, width);
        int clipVerticalMinimum = clampToRange(-clipVerticalOrigin, height);
        int clipHorizontalMaximum = clampToRange(Wasm4Runtime.HEIGHT - clipHorizontalOrigin, width);
        int clipVerticalMaximum = clampToRange(Wasm4Runtime.WIDTH - clipVerticalOrigin, height);
        int colors = readU16(memory, Wasm4Runtime.DRAW_COLORS);

        int verticalIndex;
        for (verticalIndex = clipVerticalMinimum; verticalIndex < clipVerticalMaximum; verticalIndex++) {
            int horizontalIndex;
            for (horizontalIndex = clipHorizontalMinimum; horizontalIndex < clipHorizontalMaximum; horizontalIndex++) {
                int targetX = destinationX + (rotate ? verticalIndex : horizontalIndex);
                int targetY = destinationY + (rotate ? horizontalIndex : verticalIndex);
                int sampledX = sourceX + (flipX ? width - horizontalIndex - 1 : horizontalIndex);
                int sampledY = sourceY + (flipY ? height - verticalIndex - 1 : verticalIndex);
                int bitIndex = sampledY * sourceStride + sampledX;
                int colorIndex;
                if (twoBitsPerPixel) {
                    int packed = memory[pointer + (bitIndex >> 2)] & 0xff;
                    colorIndex = (packed >> (6 - ((bitIndex & 3) << 1))) & 3;
                } else {
                    int packed = memory[pointer + (bitIndex >> 3)] & 0xff;
                    colorIndex = (packed >> (7 - (bitIndex & 7))) & 1;
                }
                int drawColor = (colors >> (colorIndex << 2)) & 0x0f;
                if (drawColor != 0) {
                    drawPoint(memory, (drawColor - 1) & 3, targetX, targetY);
                }
            }
        }
    }

    private static void drawPoint(byte[] memory, int color, int x, int y) {
        int pixel = Wasm4Runtime.WIDTH * y + x;
        int address = Wasm4Runtime.FRAMEBUFFER + (pixel >> 2);
        int shift = (x & 3) << 1;
        int mask = 3 << shift;
        memory[address] = (byte) ((color << shift) | (memory[address] & 0xff & ~mask));
    }

    private static int clampToRange(long value, int maximum) {
        if (value <= 0) {
            return 0;
        }
        if (value >= maximum) {
            return maximum;
        }
        return (int) value;
    }

    private static int readU16(byte[] memory, int address) {
        return (memory[address] & 0xff) | ((memory[address + 1] & 0xff) << 8);
    }

    private static void writeU16(byte[] memory, int address, int value) {
        memory[address] = (byte) value;
        memory[address + 1] = (byte) (value >>> 8);
    }

    private static void initialize(byte[] memory, int salt) {
        int index;
        for (index = 0; index < memory.length; index++) {
            memory[index] = (byte) (index * 73 + salt * 29);
        }
    }

    private static byte[] copy(byte[] source) {
        byte[] copy = new byte[source.length];
        System.arraycopy(source, 0, copy, 0, source.length);
        return copy;
    }

    private static void requireMemory(String label, byte[] expected, byte[] actual) {
        int index;
        for (index = 0; index < expected.length; index++) {
            if (expected[index] != actual[index]) {
                throw new AssertionError(label
                        + " memory["
                        + index
                        + "]: expected "
                        + (expected[index] & 0xff)
                        + ", got "
                        + (actual[index] & 0xff));
            }
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
