package w4me;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import w4me.runtime.Wasm4Runtime;
import w4me.wasm.WasmModule;

/** Provides the horizontal span differential smoke implementation. */
public final class HorizontalSpanDifferentialSmoke {
    private HorizontalSpanDifferentialSmoke() {}

    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("usage: font.bin cartridge.wasm");
        }
        WasmModule module = WasmModule.read(readFile(arguments[1]));
        Wasm4Runtime runtime = new Wasm4Runtime(readFile(arguments[0]));
        runtime.initialize(module);
        byte[] actual = module.memory();
        byte[] expected = new byte[Wasm4Runtime.FRAMEBUFFER_SIZE];
        int cases = 0;
        int color;
        for (color = 0; color < 4; color++) {
            int start;
            for (start = 0; start < 8; start++) {
                int end;
                for (end = start; end <= 12; end++) {
                    cases += verify(runtime, module, actual, expected, color, start, 37, end - start, cases);
                }
            }
            cases += verify(runtime, module, actual, expected, color, 0, 0, 160, cases);
            cases += verify(runtime, module, actual, expected, color, 157, 159, 8, cases);
            cases += verify(runtime, module, actual, expected, color, -3, 80, 9, cases);
            cases += verify(runtime, module, actual, expected, color, 158, 80, 9, cases);
        }
        runtime.close();
        module.close();
        System.out.println(
                "PASS horizontal-span differential cases=" + cases + " colors=4 alignments=all boundaries=exact");
    }

    private static int verify(
            Wasm4Runtime runtime,
            WasmModule module,
            byte[] actual,
            byte[] expected,
            int color,
            int start,
            int y,
            int length,
            int seed) {
        int index;
        for (index = 0; index < expected.length; index++) {
            byte value = (byte) (index * 37 + seed * 13 + 0x5a);
            expected[index] = value;
            actual[Wasm4Runtime.FRAMEBUFFER + index] = value;
        }
        actual[Wasm4Runtime.DRAW_COLORS] = (byte) (color + 1);
        actual[Wasm4Runtime.DRAW_COLORS + 1] = 0;
        runtime.invoke("env", "hline", new long[] {start, y, length}, 0, 3, module);
        drawReference(expected, color, start, y, length);
        for (index = 0; index < expected.length; index++) {
            int found = actual[Wasm4Runtime.FRAMEBUFFER + index] & 0xff;
            int wanted = expected[index] & 0xff;
            if (found != wanted) {
                throw new AssertionError("horizontal span mismatch color="
                        + color
                        + " start="
                        + start
                        + " y="
                        + y
                        + " length="
                        + length
                        + " framebuffer-byte="
                        + index
                        + " expected="
                        + wanted
                        + " actual="
                        + found);
            }
        }
        return 1;
    }

    private static void drawReference(byte[] framebuffer, int color, int start, int y, int length) {
        if (length <= 0 || y < 0 || y >= Wasm4Runtime.HEIGHT) {
            return;
        }
        int clippedStart = clamp(start, Wasm4Runtime.WIDTH);
        int clippedEnd = clamp((long) start + length, Wasm4Runtime.WIDTH);
        int x;
        for (x = clippedStart; x < clippedEnd; x++) {
            int pixel = Wasm4Runtime.WIDTH * y + x;
            int address = pixel >> 2;
            int shift = (x & 3) << 1;
            int mask = 3 << shift;
            framebuffer[address] = (byte) ((color << shift) | (framebuffer[address] & 0xff & ~mask));
        }
    }

    private static int clamp(long value, int maximum) {
        if (value <= 0) {
            return 0;
        }
        if (value >= maximum) {
            return maximum;
        }
        return (int) value;
    }

    private static byte[] readFile(String path) throws Exception {
        InputStream input = new FileInputStream(path);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        try {
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
        } finally {
            input.close();
        }
        return output.toByteArray();
    }
}
