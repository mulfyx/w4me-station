package w4me;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import w4me.runtime.Wasm4Runtime;
import w4me.wasm.WasmModule;

/** Provides the phone me ARGB band bench implementation. */
public final class PhoneMeArgbBandBench {
    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 4) {
            throw new IllegalArgumentException("usage: side band-height frames sample");
        }
        int side = Integer.parseInt(arguments[0]);
        int bandHeight = Integer.parseInt(arguments[1]);
        int frames = Integer.parseInt(arguments[2]);
        final int sample = Integer.parseInt(arguments[3]);
        if (side <= 0 || bandHeight <= 0 || frames <= 0) {
            throw new IllegalArgumentException("positive arguments required");
        }

        WasmModule module = WasmModule.read(readResource("/waternet.wasm"));
        Wasm4Runtime runtime = new Wasm4Runtime(readResource("/w4font.bin"));
        runtime.initialize(module);
        initializeFramebuffer(module.memory());

        int[] horizontalMap = new int[side];
        int[] verticalMap = new int[side];
        int index;
        for (index = 0; index < side; index++) {
            int source = index * Wasm4Runtime.WIDTH / side;
            horizontalMap[index] = (source >> 2) | ((source & 3) << 8);
            verticalMap[index] = source * (Wasm4Runtime.WIDTH >> 2);
        }
        int rowsPerBand = bandHeight > side ? side : bandHeight;
        int[] pixels = new int[side * rowsPerBand];

        for (index = 0; index < 8; index++) {
            convert(runtime, module, pixels, side, horizontalMap, verticalMap, rowsPerBand);
        }
        long started = System.currentTimeMillis();
        for (index = 0; index < frames; index++) {
            convert(runtime, module, pixels, side, horizontalMap, verticalMap, rowsPerBand);
        }
        final long elapsed = System.currentTimeMillis() - started;

        int[] exact = new int[side * side];
        exact(runtime, module, pixels, exact, side, horizontalMap, verticalMap, rowsPerBand);
        int hash = fnv1a(exact);
        runtime.close();
        module.close();

        System.out.println("argb-band:pass side="
                + side
                + " band-height="
                + rowsPerBand
                + " frames="
                + frames
                + " sample="
                + sample
                + " wall-ms="
                + elapsed
                + " us-per-frame="
                + (elapsed * 1000 / frames)
                + " output-fnv1a="
                + Integer.toHexString(hash));
    }

    private static void exact(
            Wasm4Runtime runtime,
            WasmModule module,
            int[] pixels,
            int[] exact,
            int side,
            int[] horizontalMap,
            int[] verticalMap,
            int rowsPerBand) {
        runtime.prepareArgb(module);
        int firstRow;
        for (firstRow = 0; firstRow < side; firstRow += rowsPerBand) {
            int rowCount = side - firstRow;
            if (rowCount > rowsPerBand) {
                rowCount = rowsPerBand;
            }
            copyBand(runtime, module, pixels, side, horizontalMap, verticalMap, firstRow, rowCount);
            System.arraycopy(pixels, 0, exact, firstRow * side, rowCount * side);
        }
    }

    private static void convert(
            Wasm4Runtime runtime,
            WasmModule module,
            int[] pixels,
            int side,
            int[] horizontalMap,
            int[] verticalMap,
            int rowsPerBand) {
        runtime.prepareArgb(module);
        int firstRow;
        for (firstRow = 0; firstRow < side; firstRow += rowsPerBand) {
            int rowCount = side - firstRow;
            if (rowCount > rowsPerBand) {
                rowCount = rowsPerBand;
            }
            copyBand(runtime, module, pixels, side, horizontalMap, verticalMap, firstRow, rowCount);
        }
    }

    private static void copyBand(
            Wasm4Runtime runtime,
            WasmModule module,
            int[] pixels,
            int side,
            int[] horizontalMap,
            int[] verticalMap,
            int firstRow,
            int rowCount) {
        if (side == Wasm4Runtime.WIDTH) {
            runtime.copyNativeArgbBand(module, pixels, firstRow, rowCount);
        } else if (side > Wasm4Runtime.WIDTH) {
            runtime.copyUpscaledArgbBand(module, pixels, side, horizontalMap, verticalMap, firstRow, rowCount);
        } else {
            runtime.copyArgbBand(module, pixels, side, horizontalMap, verticalMap, firstRow, rowCount);
        }
    }

    private static void initializeFramebuffer(byte[] memory) {
        int[] colors = {0x071821, 0x306850, 0x86c06c, 0xe0f8cf};
        int index;
        for (index = 0; index < colors.length; index++) {
            int address = Wasm4Runtime.PALETTE + index * 4;
            int color = colors[index];
            memory[address] = (byte) color;
            memory[address + 1] = (byte) (color >>> 8);
            memory[address + 2] = (byte) (color >>> 16);
            memory[address + 3] = (byte) (color >>> 24);
        }
        for (index = 0; index < Wasm4Runtime.FRAMEBUFFER_SIZE; index++) {
            memory[Wasm4Runtime.FRAMEBUFFER + index] = (byte) (index * 73 + (index >>> 2));
        }
    }

    private static int fnv1a(int[] values) {
        int hash = 0x811c9dc5;
        int index;
        for (index = 0; index < values.length; index++) {
            int value = values[index];
            hash = (hash ^ value) * 0x01000193;
        }
        return hash;
    }

    private static byte[] readResource(String path) throws Exception {
        Class owner = PhoneMeArgbBandBench.class;
        InputStream input = owner.getResourceAsStream(path); // NOPMD -- Closed in finally.
        if (input == null) {
            throw new RuntimeException("missing resource: " + path);
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }
}
