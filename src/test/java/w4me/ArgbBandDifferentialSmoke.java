package w4me;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import w4me.runtime.Wasm4Runtime;
import w4me.wasm.WasmModule;

/** Provides the ARGB band differential smoke implementation. */
public final class ArgbBandDifferentialSmoke {
    private static final int SENTINEL = 0x13579bdf;

    private int cases;

    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("usage: font cartridge");
        }
        ArgbBandDifferentialSmoke smoke = new ArgbBandDifferentialSmoke();
        smoke.run(read(arguments[0]), read(arguments[1]));
        System.out.println("argb-band-differential:pass cases=" + smoke.cases);
    }

    private void run(byte[] font, byte[] cartridge) throws Exception {
        WasmModule module = WasmModule.read(cartridge);
        Wasm4Runtime runtime = new Wasm4Runtime(font);
        runtime.initialize(module);
        try {
            initializeFramebuffer(module.memory());
            int[] sides = {161, 176, 240, 320};
            int paletteSeed;
            for (paletteSeed = 0; paletteSeed < 2; paletteSeed++) {
                initializePalette(module.memory(), paletteSeed);
                runtime.prepareArgb(module);
                verifyCanonicalReference(runtime, module);
                verifyNative(runtime, module);
                int sideIndex;
                for (sideIndex = 0; sideIndex < sides.length; sideIndex++) {
                    verifyScale(runtime, module, sides[sideIndex]);
                }
            }
            verifyArbitraryMaps(runtime, module);
            verifyAliases(runtime, module);
            verifyValidation(runtime, module);
        } finally {
            runtime.close();
            module.close();
        }
    }

    private void verifyNative(Wasm4Runtime runtime, WasmModule module) {
        int[] horizontalMap = createHorizontalMap(Wasm4Runtime.WIDTH);
        int[] verticalMap = createVerticalMap(Wasm4Runtime.HEIGHT);
        int[] heights = {1, 2, 15, 16, 17, Wasm4Runtime.HEIGHT};
        int heightIndex;
        for (heightIndex = 0; heightIndex < heights.length; heightIndex++) {
            int bandHeight = heights[heightIndex];
            int[] expected = new int[Wasm4Runtime.WIDTH * bandHeight + 7];
            int[] actual = new int[expected.length];
            int firstRow;
            for (firstRow = 0; firstRow < Wasm4Runtime.HEIGHT; firstRow += bandHeight) {
                int rowCount = Wasm4Runtime.HEIGHT - firstRow;
                if (rowCount > bandHeight) {
                    rowCount = bandHeight;
                }
                fill(expected, SENTINEL);
                fill(actual, SENTINEL);
                runtime.copyArgbBand(
                        module, expected, Wasm4Runtime.WIDTH, horizontalMap, verticalMap, firstRow, rowCount);
                runtime.copyNativeArgbBand(module, actual, firstRow, rowCount);
                assertArray(expected, actual, "native ARGB band");
                assertSentinel(expected, Wasm4Runtime.WIDTH * rowCount);
                assertSentinel(actual, Wasm4Runtime.WIDTH * rowCount);
                cases++;
            }
        }
        expectNativeIllegalArgument(runtime, module, new int[Wasm4Runtime.WIDTH], -1, 1);
        expectNativeIllegalArgument(runtime, module, new int[Wasm4Runtime.WIDTH], 0, -1);
        expectNativeIllegalArgument(runtime, module, new int[Wasm4Runtime.WIDTH], Wasm4Runtime.HEIGHT, 1);
        expectNativeIllegalArgument(runtime, module, new int[Wasm4Runtime.WIDTH - 1], 0, 1);
    }

    private void verifyScale(Wasm4Runtime runtime, WasmModule module, int side) {
        int[] horizontalMap = createHorizontalMap(side);
        int[] verticalMap = createVerticalMap(side);
        int[] heights = {1, 2, 15, 16, 17, side};
        int heightIndex;
        for (heightIndex = 0; heightIndex < heights.length; heightIndex++) {
            verifyFrame(runtime, module, side, horizontalMap, verticalMap, heights[heightIndex]);
        }

        verifyBand(runtime, module, side, horizontalMap, verticalMap, 0, 0);
        verifyBand(runtime, module, side, horizontalMap, verticalMap, 0, 1);
        verifyBand(runtime, module, side, horizontalMap, verticalMap, 1, 1);
        verifyBand(runtime, module, side, horizontalMap, verticalMap, side - 1, 1);
        verifyBand(runtime, module, side, horizontalMap, verticalMap, side - 3, 3);
    }

    private void verifyFrame(
            Wasm4Runtime runtime,
            WasmModule module,
            int width,
            int[] horizontalMap,
            int[] verticalMap,
            int bandHeight) {
        int[] expected = new int[width * verticalMap.length];
        int[] actual = new int[expected.length];
        int[] referenceBand = new int[width * bandHeight + 7];
        int[] candidateBand = new int[width * bandHeight + 7];
        int firstRow;
        for (firstRow = 0; firstRow < verticalMap.length; firstRow += bandHeight) {
            int rowCount = verticalMap.length - firstRow;
            if (rowCount > bandHeight) {
                rowCount = bandHeight;
            }
            fill(referenceBand, SENTINEL);
            fill(candidateBand, SENTINEL);
            runtime.copyArgbBand(module, referenceBand, width, horizontalMap, verticalMap, firstRow, rowCount);
            runtime.copyUpscaledArgbBand(module, candidateBand, width, horizontalMap, verticalMap, firstRow, rowCount);
            assertArray(referenceBand, candidateBand, "band pixels");
            assertSentinel(referenceBand, width * rowCount);
            assertSentinel(candidateBand, width * rowCount);
            System.arraycopy(referenceBand, 0, expected, firstRow * width, width * rowCount);
            System.arraycopy(candidateBand, 0, actual, firstRow * width, width * rowCount);
            cases++;
        }
        assertArray(expected, actual, "full frame");
    }

    private void verifyBand(
            Wasm4Runtime runtime,
            WasmModule module,
            int width,
            int[] horizontalMap,
            int[] verticalMap,
            int firstRow,
            int rowCount) {
        int[] expected = new int[width * rowCount + 7];
        int[] actual = new int[width * rowCount + 7];
        fill(expected, SENTINEL);
        fill(actual, SENTINEL);
        runtime.copyArgbBand(module, expected, width, horizontalMap, verticalMap, firstRow, rowCount);
        runtime.copyUpscaledArgbBand(module, actual, width, horizontalMap, verticalMap, firstRow, rowCount);
        assertArray(expected, actual, "direct band");
        assertSentinel(expected, width * rowCount);
        assertSentinel(actual, width * rowCount);
        cases++;
    }

    private void verifyArbitraryMaps(Wasm4Runtime runtime, WasmModule module) {
        int width = 173;
        int[] horizontalMap = new int[width];
        int x;
        for (x = 0; x < width; x++) {
            int source = (x * 97 + 31) % Wasm4Runtime.WIDTH;
            horizontalMap[x] = (source >> 2) | ((source & 3) << 8);
        }
        int[] verticalMap = new int[29];
        int row;
        for (row = 0; row < verticalMap.length; row++) {
            int source = (row * 43 + 7) % Wasm4Runtime.HEIGHT;
            if (row == 1 || row == 8 || row == 9 || row == 15 || row == verticalMap.length - 1) {
                source = verticalMap[row - 1] / (Wasm4Runtime.WIDTH >> 2);
            }
            verticalMap[row] = source * (Wasm4Runtime.WIDTH >> 2);
        }
        verifyFrame(runtime, module, width, horizontalMap, verticalMap, 7);
        verifyFrame(runtime, module, width, horizontalMap, verticalMap, 16);
    }

    private void verifyCanonicalReference(Wasm4Runtime runtime, WasmModule module) {
        int[] sides = {128, 160};
        int sideIndex;
        for (sideIndex = 0; sideIndex < sides.length; sideIndex++) {
            int side = sides[sideIndex];
            int[] horizontalMap = createHorizontalMap(side);
            int[] verticalMap = createVerticalMap(side);
            int[] heights = {1, 16, side};
            int heightIndex;
            for (heightIndex = 0; heightIndex < heights.length; heightIndex++) {
                verifyCanonicalFrame(runtime, module, side, horizontalMap, verticalMap, heights[heightIndex]);
            }
        }

        int width = 137;
        int[] horizontalMap = new int[width];
        int x;
        for (x = 0; x < width; x++) {
            int source = (x * 97 + 31) % Wasm4Runtime.WIDTH;
            horizontalMap[x] = (source >> 2) | ((source & 3) << 8);
        }
        int[] verticalMap = new int[29];
        int row;
        for (row = 0; row < verticalMap.length; row++) {
            int source = (row * 43 + 7) % Wasm4Runtime.HEIGHT;
            if (row == 1 || row == 8 || row == 9 || row == 15 || row == verticalMap.length - 1) {
                source = verticalMap[row - 1] / (Wasm4Runtime.WIDTH >> 2);
            }
            verticalMap[row] = source * (Wasm4Runtime.WIDTH >> 2);
        }
        verifyCanonicalFrame(runtime, module, width, horizontalMap, verticalMap, 7);
        verifyCanonicalAliases(runtime, module);
    }

    private void verifyCanonicalFrame(
            Wasm4Runtime runtime,
            WasmModule module,
            int width,
            int[] horizontalMap,
            int[] verticalMap,
            int bandHeight) {
        int[] expected = new int[width * bandHeight + 7];
        int[] actual = new int[expected.length];
        int firstRow;
        for (firstRow = 0; firstRow < verticalMap.length; firstRow += bandHeight) {
            int rowCount = verticalMap.length - firstRow;
            if (rowCount > bandHeight) {
                rowCount = bandHeight;
            }
            fill(expected, SENTINEL);
            fill(actual, SENTINEL);
            referenceArgbBand(module.memory(), expected, width, horizontalMap, verticalMap, firstRow, rowCount);
            runtime.copyArgbBand(module, actual, width, horizontalMap, verticalMap, firstRow, rowCount);
            assertArray(expected, actual, "canonical reference");
            assertSentinel(expected, width * rowCount);
            assertSentinel(actual, width * rowCount);
            cases++;
        }
    }

    private void verifyCanonicalAliases(Wasm4Runtime runtime, WasmModule module) {
        int width = 160;
        int[] expectedX = createHorizontalMap(width);
        int[] actualX = copy(expectedX);
        int[] expectedY = createVerticalMap(width);
        int[] actualY = copy(expectedY);
        referenceArgbBand(module.memory(), expectedX, width, expectedX, expectedY, 0, 1);
        runtime.copyArgbBand(module, actualX, width, actualX, actualY, 0, 1);
        assertArray(expectedX, actualX, "canonical horizontalMap alias");
        cases++;

        int[] horizontalMap = createHorizontalMap(width);
        expectedY = createVerticalMap(width);
        actualY = copy(expectedY);
        referenceArgbBand(module.memory(), expectedY, width, horizontalMap, expectedY, 0, 1);
        runtime.copyArgbBand(module, actualY, width, horizontalMap, actualY, 0, 1);
        assertArray(expectedY, actualY, "canonical verticalMap alias");
        cases++;
    }

    private static void referenceArgbBand(
            byte[] memory,
            int[] pixels,
            int width,
            int[] horizontalMap,
            int[] verticalMap,
            int firstRow,
            int rowCount) {
        int row;
        for (row = 0; row < rowCount; row++) {
            int sourceRow = verticalMap[firstRow + row];
            int destinationRow = row * width;
            int x;
            for (x = 0; x < width; x++) {
                int mapping = horizontalMap[x];
                int packed = memory[Wasm4Runtime.FRAMEBUFFER + sourceRow + (mapping & 0xff)] & 0xff;
                int lane = mapping >>> 8;
                int color = (packed >>> (lane << 1)) & 3;
                pixels[destinationRow + x] =
                        0xff000000 | (readI32(memory, Wasm4Runtime.PALETTE + color * 4) & 0x00ffffff);
            }
        }
    }

    private static int readI32(byte[] memory, int address) {
        return (memory[address] & 0xff)
                | ((memory[address + 1] & 0xff) << 8)
                | ((memory[address + 2] & 0xff) << 16)
                | ((memory[address + 3] & 0xff) << 24);
    }

    private void verifyAliases(Wasm4Runtime runtime, WasmModule module) {
        int width = 161;
        int[] expectedX = createHorizontalMap(width);
        int[] actualX = copy(expectedX);
        int[] expectedY = createVerticalMap(width);
        int[] actualY = copy(expectedY);
        runtime.copyArgbBand(module, expectedX, width, expectedX, expectedY, 0, 1);
        runtime.copyUpscaledArgbBand(module, actualX, width, actualX, actualY, 0, 1);
        assertArray(expectedX, actualX, "horizontalMap alias");
        cases++;

        int[] horizontalMap = createHorizontalMap(width);
        expectedY = createVerticalMap(width);
        actualY = copy(expectedY);
        runtime.copyArgbBand(module, expectedY, width, horizontalMap, expectedY, 0, 1);
        runtime.copyUpscaledArgbBand(module, actualY, width, horizontalMap, actualY, 0, 1);
        assertArray(expectedY, actualY, "verticalMap alias");
        cases++;
    }

    private void verifyValidation(Wasm4Runtime runtime, WasmModule module) {
        expectIllegalArgument(runtime, module, 160, new int[160], new int[160], new int[160], 0, 1);
        expectIllegalArgument(runtime, module, 161, new int[161], new int[161], new int[161], 0, -1);
        expectIllegalArgument(runtime, module, 161, new int[161], new int[160], new int[161], 0, 1);
        expectIllegalArgument(runtime, module, 161, new int[161], new int[161], new int[161], -1, 1);
        expectIllegalArgument(runtime, module, 161, new int[322], new int[161], new int[1], 0, 2);
        expectIllegalArgument(runtime, module, 161, new int[321], new int[161], new int[2], 0, 2);
    }

    private void expectIllegalArgument(
            Wasm4Runtime runtime,
            WasmModule module,
            int width,
            int[] pixels,
            int[] horizontalMap,
            int[] verticalMap,
            int firstRow,
            int rowCount) {
        try {
            runtime.copyUpscaledArgbBand(module, pixels, width, horizontalMap, verticalMap, firstRow, rowCount);
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            cases++;
        }
    }

    private void expectNativeIllegalArgument(
            Wasm4Runtime runtime, WasmModule module, int[] pixels, int firstRow, int rowCount) {
        try {
            runtime.copyNativeArgbBand(module, pixels, firstRow, rowCount);
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            cases++;
        }
    }

    private static int[] createHorizontalMap(int width) {
        int[] result = new int[width];
        int x;
        for (x = 0; x < width; x++) {
            int source = x * Wasm4Runtime.WIDTH / width;
            result[x] = (source >> 2) | ((source & 3) << 8);
        }
        return result;
    }

    private static int[] createVerticalMap(int height) {
        int[] result = new int[height];
        int y;
        for (y = 0; y < height; y++) {
            int source = y * Wasm4Runtime.HEIGHT / height;
            result[y] = source * (Wasm4Runtime.WIDTH >> 2);
        }
        return result;
    }

    private static void initializePalette(byte[] memory, int seed) {
        int[] colors = seed == 0
                ? new int[] {0x071821, 0x306850, 0x86c06c, 0xe0f8cf}
                : new int[] {0x040404, 0x574248, 0xd6b97b, 0xfff4d2};
        int index;
        for (index = 0; index < colors.length; index++) {
            int address = Wasm4Runtime.PALETTE + index * 4;
            int color = colors[index];
            memory[address] = (byte) color;
            memory[address + 1] = (byte) (color >>> 8);
            memory[address + 2] = (byte) (color >>> 16);
            memory[address + 3] = (byte) (color >>> 24);
        }
    }

    private static void initializeFramebuffer(byte[] memory) {
        int index;
        for (index = 0; index < Wasm4Runtime.FRAMEBUFFER_SIZE; index++) {
            memory[Wasm4Runtime.FRAMEBUFFER + index] = (byte) (index * 73 + (index >>> 2));
        }
    }

    private static int[] copy(int[] source) {
        int[] result = new int[source.length];
        System.arraycopy(source, 0, result, 0, source.length);
        return result;
    }

    private static void fill(int[] values, int value) {
        int index;
        for (index = 0; index < values.length; index++) {
            values[index] = value;
        }
    }

    private static void assertSentinel(int[] values, int first) {
        int index;
        for (index = first; index < values.length; index++) {
            if (values[index] != SENTINEL) {
                throw new AssertionError("tail overwritten at " + index);
            }
        }
    }

    private static void assertArray(int[] expected, int[] actual, String label) {
        if (expected.length != actual.length) {
            throw new AssertionError(label + " length mismatch");
        }
        int index;
        for (index = 0; index < expected.length; index++) {
            if (expected[index] != actual[index]) {
                throw new AssertionError(
                        label + " mismatch at " + index + ": " + expected[index] + " != " + actual[index]);
            }
        }
    }

    private static byte[] read(String path) throws Exception {
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
