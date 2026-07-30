package w4me.runtime;

import w4me.runtime.audio.SilentAudioBackend;
import w4me.runtime.audio.Wasm4Apu;
import w4me.runtime.storage.DiskBackend;
import w4me.runtime.storage.MemoryDiskBackend;
import w4me.wasm.WasmHost;
import w4me.wasm.WasmModule;
import w4me.wasm.WasmTrap;

/** Provides the WASM 4 runtime implementation. */
public final class Wasm4Runtime implements WasmHost {
    public static final int WIDTH = 160;
    public static final int HEIGHT = 160;
    public static final int PALETTE = 0x04;
    public static final int DRAW_COLORS = 0x14;
    public static final int GAMEPAD1 = 0x16;
    public static final int GAMEPAD2 = 0x17;
    public static final int GAMEPAD3 = 0x18;
    public static final int GAMEPAD4 = 0x19;
    public static final int MOUSE_X = 0x1a;
    public static final int MOUSE_Y = 0x1c;
    public static final int MOUSE_BUTTONS = 0x1e;
    public static final int SYSTEM_FLAGS = 0x1f;
    public static final int FRAMEBUFFER = 0x00a0;
    public static final int FRAMEBUFFER_SIZE = 6400;
    private static final int TRACE_OUTPUT_LIMIT = 4096;
    private static final int LINE_STEP_LIMIT = 4096;
    private static final int OVAL_DIMENSION_LIMIT = 512;
    /** Expands four left-to-right 1bpp pixels to a packed 2bpp mask. */
    private static final int[] GLYPH_1BPP_MASK = {
        0x00, 0xc0, 0x30, 0xf0,
        0x0c, 0xcc, 0x3c, 0xfc,
        0x03, 0xc3, 0x33, 0xf3,
        0x0f, 0xcf, 0x3f, 0xff
    };

    private final byte[] font;
    private final int[] palette = new int[4];
    private final int[] argbLookup = new int[1024];
    private final Wasm4Apu apu;
    // Cached mapping for the common opaque, untransformed 2bpp blit shape.
    // Each source byte contains four MSB-first sprite pixels; each lookup value
    // is one LSB-first WASM-4 framebuffer byte.
    private final int[] opaque2bppBlitLookup = new int[256];
    private int opaque2bppBlitColors = -1;
    private final DiskBackend disk;
    private String lastTrace;
    private boolean audioMuted;

    /** Creates a new WASM 4 runtime. */
    public Wasm4Runtime(byte[] font) {
        this(font, new Wasm4Apu(new SilentAudioBackend()), new MemoryDiskBackend());
    }

    /** Creates a new WASM 4 runtime. */
    public Wasm4Runtime(byte[] font, Wasm4Apu apu) {
        this(font, apu, new MemoryDiskBackend());
    }

    /** Creates a new WASM 4 runtime. */
    public Wasm4Runtime(byte[] font, Wasm4Apu apu, DiskBackend disk) {
        if (font == null || font.length != 1792) {
            throw new IllegalArgumentException("WASM-4 font must contain exactly 1792 bytes");
        }
        if (apu == null) {
            throw new IllegalArgumentException("WASM-4 APU is required");
        }
        if (disk == null) {
            throw new IllegalArgumentException("WASM-4 disk is required");
        }
        this.font = font;
        this.apu = apu;
        this.disk = disk;
        audioMuted = apu.muted();
    }

    /** Performs the initialize operation. */
    public void initialize(WasmModule module) {
        byte[] memory = module.memory();
        writeI32(memory, PALETTE, 0xe0f8cf);
        writeI32(memory, PALETTE + 4, 0x86c06c);
        writeI32(memory, PALETTE + 8, 0x306850);
        writeI32(memory, PALETTE + 12, 0x071821);
        writeI16(memory, DRAW_COLORS, 0x1203);
        writeI16(memory, MOUSE_X, 0x7fff);
        writeI16(memory, MOUSE_Y, 0x7fff);
    }

    /** Performs the begin frame operation. */
    public void beginFrame(WasmModule module, int gamepad, int mouseX, int mouseY, int mouseButtons) {
        beginFrame(module, gamepad, 0, mouseX, mouseY, mouseButtons);
    }

    /** Performs the begin frame operation. */
    public void beginFrame(WasmModule module, int gamepad1, int gamepad2, int mouseX, int mouseY, int mouseButtons) {
        byte[] memory = module.memory();
        if ((memory[SYSTEM_FLAGS] & 1) == 0) {
            memory[FRAMEBUFFER] = 0;
            int cleared = 1;
            while (cleared < FRAMEBUFFER_SIZE) {
                int copyLength = cleared;
                if (copyLength > FRAMEBUFFER_SIZE - cleared) {
                    copyLength = FRAMEBUFFER_SIZE - cleared;
                }
                System.arraycopy(memory, FRAMEBUFFER, memory, FRAMEBUFFER + cleared, copyLength);
                cleared += copyLength;
            }
        }
        memory[GAMEPAD1] = (byte) gamepad1;
        memory[GAMEPAD2] = (byte) gamepad2;
        memory[GAMEPAD3] = 0;
        memory[GAMEPAD4] = 0;
        writeI16(memory, MOUSE_X, mouseX);
        writeI16(memory, MOUSE_Y, mouseY);
        memory[MOUSE_BUTTONS] = (byte) mouseButtons;
    }

    /** Performs the invoke operation. */
    public long invoke(int importId, long[] valueStack, int argumentBase, int argumentCount, WasmModule module) {
        byte[] memory = module.memory();
        switch (importId) {
            case WasmHost.IMPORT_TEXT_UTF8:
                textUtf8(
                        memory,
                        (int) valueStack[argumentBase],
                        (int) valueStack[argumentBase + 1],
                        (int) valueStack[argumentBase + 2],
                        (int) valueStack[argumentBase + 3]);
                return 0;
            case WasmHost.IMPORT_TEXT:
                text(memory, (int) valueStack[argumentBase], (int) valueStack[argumentBase + 1], (int)
                        valueStack[argumentBase + 2]);
                return 0;
            case WasmHost.IMPORT_TEXT_UTF16:
                textUtf16(
                        memory,
                        (int) valueStack[argumentBase],
                        (int) valueStack[argumentBase + 1],
                        (int) valueStack[argumentBase + 2],
                        (int) valueStack[argumentBase + 3]);
                return 0;
            case WasmHost.IMPORT_RECT:
                rect(
                        memory,
                        (int) valueStack[argumentBase],
                        (int) valueStack[argumentBase + 1],
                        (int) valueStack[argumentBase + 2],
                        (int) valueStack[argumentBase + 3]);
                return 0;
            case WasmHost.IMPORT_BLIT:
                blit(
                        memory,
                        (int) valueStack[argumentBase],
                        (int) valueStack[argumentBase + 1],
                        (int) valueStack[argumentBase + 2],
                        (int) valueStack[argumentBase + 3],
                        (int) valueStack[argumentBase + 4],
                        (int) valueStack[argumentBase + 5]);
                return 0;
            case WasmHost.IMPORT_BLIT_SUB:
                blitSub(
                        memory,
                        (int) valueStack[argumentBase],
                        (int) valueStack[argumentBase + 1],
                        (int) valueStack[argumentBase + 2],
                        (int) valueStack[argumentBase + 3],
                        (int) valueStack[argumentBase + 4],
                        (int) valueStack[argumentBase + 5],
                        (int) valueStack[argumentBase + 6],
                        (int) valueStack[argumentBase + 7],
                        (int) valueStack[argumentBase + 8]);
                return 0;
            case WasmHost.IMPORT_LINE:
                line(
                        memory,
                        (int) valueStack[argumentBase],
                        (int) valueStack[argumentBase + 1],
                        (int) valueStack[argumentBase + 2],
                        (int) valueStack[argumentBase + 3]);
                return 0;
            case WasmHost.IMPORT_HLINE:
                hline(memory, (int) valueStack[argumentBase], (int) valueStack[argumentBase + 1], (int)
                        valueStack[argumentBase + 2]);
                return 0;
            case WasmHost.IMPORT_VLINE:
                vline(memory, (int) valueStack[argumentBase], (int) valueStack[argumentBase + 1], (int)
                        valueStack[argumentBase + 2]);
                return 0;
            case WasmHost.IMPORT_OVAL:
                oval(
                        memory,
                        (int) valueStack[argumentBase],
                        (int) valueStack[argumentBase + 1],
                        (int) valueStack[argumentBase + 2],
                        (int) valueStack[argumentBase + 3]);
                return 0;
            case WasmHost.IMPORT_DISK_READ:
                return diskRead(memory, (int) valueStack[argumentBase], (int) valueStack[argumentBase + 1]);
            case WasmHost.IMPORT_DISK_WRITE:
                return diskWrite(memory, (int) valueStack[argumentBase], (int) valueStack[argumentBase + 1]);
            case WasmHost.IMPORT_TONE:
                if (!audioMuted) {
                    apu.tone(
                            (int) valueStack[argumentBase],
                            (int) valueStack[argumentBase + 1],
                            (int) valueStack[argumentBase + 2],
                            (int) valueStack[argumentBase + 3]);
                }
                return 0;
            case WasmHost.IMPORT_TRACE:
                lastTrace = readNullTerminatedString(memory, (int) valueStack[argumentBase]);
                return 0;
            case WasmHost.IMPORT_TRACEF:
                lastTrace = formatTrace(memory, (int) valueStack[argumentBase], (int) valueStack[argumentBase + 1]);
                return 0;
            case WasmHost.IMPORT_TRACE_UTF8:
                lastTrace = readString(memory, (int) valueStack[argumentBase], (int) valueStack[argumentBase + 1]);
                return 0;
            case WasmHost.IMPORT_TRACE_UTF16:
                lastTrace = readUtf16String(memory, (int) valueStack[argumentBase], (int) valueStack[argumentBase + 1]);
                return 0;
            default:
                throw new WasmTrap("unsupported import ID: " + importId);
        }
    }

    /** Performs the invoke operation. */
    public long invoke(
            String moduleName, String name, long[] valueStack, int argumentBase, int argumentCount, WasmModule module) {
        if (!"env".equals(moduleName)) {
            throw new WasmTrap("unsupported import module: " + moduleName);
        }
        if ("textUtf8".equals(name) && argumentCount == 4) {
            textUtf8(
                    module.memory(),
                    (int) valueStack[argumentBase],
                    (int) valueStack[argumentBase + 1],
                    (int) valueStack[argumentBase + 2],
                    (int) valueStack[argumentBase + 3]);
            return 0;
        }
        if ("text".equals(name) && argumentCount == 3) {
            text(module.memory(), (int) valueStack[argumentBase], (int) valueStack[argumentBase + 1], (int)
                    valueStack[argumentBase + 2]);
            return 0;
        }
        if ("textUtf16".equals(name) && argumentCount == 4) {
            textUtf16(
                    module.memory(),
                    (int) valueStack[argumentBase],
                    (int) valueStack[argumentBase + 1],
                    (int) valueStack[argumentBase + 2],
                    (int) valueStack[argumentBase + 3]);
            return 0;
        }
        if ("rect".equals(name) && argumentCount == 4) {
            rect(
                    module.memory(),
                    (int) valueStack[argumentBase],
                    (int) valueStack[argumentBase + 1],
                    (int) valueStack[argumentBase + 2],
                    (int) valueStack[argumentBase + 3]);
            return 0;
        }
        if ("blit".equals(name) && argumentCount == 6) {
            blit(
                    module.memory(),
                    (int) valueStack[argumentBase],
                    (int) valueStack[argumentBase + 1],
                    (int) valueStack[argumentBase + 2],
                    (int) valueStack[argumentBase + 3],
                    (int) valueStack[argumentBase + 4],
                    (int) valueStack[argumentBase + 5]);
            return 0;
        }
        if ("blitSub".equals(name) && argumentCount == 9) {
            blitSub(
                    module.memory(),
                    (int) valueStack[argumentBase],
                    (int) valueStack[argumentBase + 1],
                    (int) valueStack[argumentBase + 2],
                    (int) valueStack[argumentBase + 3],
                    (int) valueStack[argumentBase + 4],
                    (int) valueStack[argumentBase + 5],
                    (int) valueStack[argumentBase + 6],
                    (int) valueStack[argumentBase + 7],
                    (int) valueStack[argumentBase + 8]);
            return 0;
        }
        if ("line".equals(name) && argumentCount == 4) {
            line(
                    module.memory(),
                    (int) valueStack[argumentBase],
                    (int) valueStack[argumentBase + 1],
                    (int) valueStack[argumentBase + 2],
                    (int) valueStack[argumentBase + 3]);
            return 0;
        }
        if ("hline".equals(name) && argumentCount == 3) {
            hline(module.memory(), (int) valueStack[argumentBase], (int) valueStack[argumentBase + 1], (int)
                    valueStack[argumentBase + 2]);
            return 0;
        }
        if ("vline".equals(name) && argumentCount == 3) {
            vline(module.memory(), (int) valueStack[argumentBase], (int) valueStack[argumentBase + 1], (int)
                    valueStack[argumentBase + 2]);
            return 0;
        }
        if ("oval".equals(name) && argumentCount == 4) {
            oval(
                    module.memory(),
                    (int) valueStack[argumentBase],
                    (int) valueStack[argumentBase + 1],
                    (int) valueStack[argumentBase + 2],
                    (int) valueStack[argumentBase + 3]);
            return 0;
        }
        if ("diskr".equals(name) && argumentCount == 2) {
            return diskRead(module.memory(), (int) valueStack[argumentBase], (int) valueStack[argumentBase + 1]);
        }
        if ("diskw".equals(name) && argumentCount == 2) {
            return diskWrite(module.memory(), (int) valueStack[argumentBase], (int) valueStack[argumentBase + 1]);
        }
        if ("tone".equals(name) && argumentCount == 4) {
            if (!audioMuted) {
                apu.tone(
                        (int) valueStack[argumentBase],
                        (int) valueStack[argumentBase + 1],
                        (int) valueStack[argumentBase + 2],
                        (int) valueStack[argumentBase + 3]);
            }
            return 0;
        }
        if ("trace".equals(name) && argumentCount == 1) {
            lastTrace = readNullTerminatedString(module.memory(), (int) valueStack[argumentBase]);
            return 0;
        }
        if ("tracef".equals(name) && argumentCount == 2) {
            lastTrace =
                    formatTrace(module.memory(), (int) valueStack[argumentBase], (int) valueStack[argumentBase + 1]);
            return 0;
        }
        if ("traceUtf8".equals(name) && argumentCount == 2) {
            lastTrace = readString(module.memory(), (int) valueStack[argumentBase], (int) valueStack[argumentBase + 1]);
            return 0;
        }
        if ("traceUtf16".equals(name) && argumentCount == 2) {
            lastTrace = readUtf16String(
                    module.memory(), (int) valueStack[argumentBase], (int) valueStack[argumentBase + 1]);
            return 0;
        }
        throw new WasmTrap("unsupported import: " + moduleName + "." + name);
    }

    /** Performs the last trace operation. */
    public String lastTrace() {
        return lastTrace;
    }

    /** Performs the end frame operation. */
    public void endFrame() {
        if (!audioMuted) {
            apu.tick();
        }
    }

    /**
     * Applies the user-facing hard mute at the host-import boundary.
     *
     * <p>The cached flag avoids entering the synchronized APU and its backend while sound is disabled. Audio settings
     * are applied while the game worker is stopped at the native system-menu boundary.
     */
    public void setAudioMuted(boolean value) {
        apu.setMuted(value);
        audioMuted = value;
    }

    /** Performs the apu operation. */
    public Wasm4Apu apu() {
        return apu;
    }

    /** Performs the disk operation. */
    public DiskBackend disk() {
        return disk;
    }

    /** Performs the close operation. */
    public void close() {
        apu.close();
        disk.close();
    }

    /** Copies the ARGB. */
    public void copyArgb(WasmModule module, int[] pixels) {
        if (pixels.length < WIDTH * HEIGHT) {
            throw new IllegalArgumentException("pixel buffer is too small");
        }
        prepareArgb(module);
        byte[] memory = module.memory();
        int packedIndex;
        for (packedIndex = 0; packedIndex < FRAMEBUFFER_SIZE; packedIndex++) {
            int lookup = (memory[FRAMEBUFFER + packedIndex] & 0xff) << 2;
            int pixel = packedIndex << 2;
            pixels[pixel] = argbLookup[lookup];
            pixels[pixel + 1] = argbLookup[lookup + 1];
            pixels[pixel + 2] = argbLookup[lookup + 2];
            pixels[pixel + 3] = argbLookup[lookup + 3];
        }
    }

    /** Performs the prepare ARGB operation. */
    public void prepareArgb(WasmModule module) {
        byte[] memory = module.memory();
        boolean changed = false;
        int index;
        for (index = 0; index < 4; index++) {
            int color = 0xff000000 | (readI32(memory, PALETTE + index * 4) & 0x00ffffff);
            if (palette[index] != color) {
                palette[index] = color;
                changed = true;
            }
        }
        if (changed) {
            int packed;
            for (packed = 0; packed < 256; packed++) {
                int lookup = packed << 2;
                argbLookup[lookup] = palette[packed & 3];
                argbLookup[lookup + 1] = palette[(packed >> 2) & 3];
                argbLookup[lookup + 2] = palette[(packed >> 4) & 3];
                argbLookup[lookup + 3] = palette[(packed >> 6) & 3];
            }
        }
    }

    /** Copies the ARGB band. */
    public void copyArgbBand(
            WasmModule module,
            int[] pixels,
            int width,
            int[] horizontalMap,
            int[] verticalMap,
            int firstRow,
            int rowCount) {
        if (width < 0
                || rowCount < 0
                || horizontalMap.length < width
                || firstRow < 0
                || rowCount > verticalMap.length - firstRow
                || pixels.length < width * rowCount) {
            throw new IllegalArgumentException("invalid ARGB band geometry");
        }
        byte[] memory = module.memory();
        int row;
        for (row = 0; row < rowCount; row++) {
            int sourceRow = verticalMap[firstRow + row];
            int destinationRow = row * width;
            int previousPackedAddress = -1;
            int packed = 0;
            int x;
            for (x = 0; x < width; x++) {
                int mapping = horizontalMap[x];
                int packedAddress = mapping & 0xff;
                if (packedAddress != previousPackedAddress) {
                    packed = memory[FRAMEBUFFER + sourceRow + packedAddress] & 0xff;
                    previousPackedAddress = packedAddress;
                }
                pixels[destinationRow + x] = argbLookup[(packed << 2) | (mapping >>> 8)];
            }
        }
    }

    /** Copies native 160 by 160 rows without scaling-map lookups. */
    public void copyNativeArgbBand(WasmModule module, int[] pixels, int firstRow, int rowCount) {
        if (firstRow < 0
                || rowCount < 0
                || firstRow > HEIGHT
                || rowCount > HEIGHT - firstRow
                || pixels.length < WIDTH * rowCount) {
            throw new IllegalArgumentException("invalid native ARGB band geometry");
        }
        byte[] memory = module.memory();
        int[] lookup = argbLookup;
        int packedIndex = firstRow * (WIDTH >> 2);
        int packedEnd = (firstRow + rowCount) * (WIDTH >> 2);
        int pixel = 0;
        while (packedIndex < packedEnd) {
            int base = (memory[FRAMEBUFFER + packedIndex] & 0xff) << 2;
            pixels[pixel] = lookup[base];
            pixels[pixel + 1] = lookup[base + 1];
            pixels[pixel + 2] = lookup[base + 2];
            pixels[pixel + 3] = lookup[base + 3];
            packedIndex++;
            pixel += 4;
        }
    }

    /** Copies the upscaled ARGB band. */
    public void copyUpscaledArgbBand(
            WasmModule module,
            int[] pixels,
            int width,
            int[] horizontalMap,
            int[] verticalMap,
            int firstRow,
            int rowCount) {
        if (width <= WIDTH
                || rowCount < 0
                || horizontalMap.length < width
                || firstRow < 0
                || rowCount > verticalMap.length - firstRow
                || pixels.length < width * rowCount) {
            throw new IllegalArgumentException("invalid upscaled ARGB band geometry");
        }
        boolean aliasesMap = pixels == horizontalMap // NOPMD -- Intentional identity-token comparison.
                || pixels == verticalMap; // NOPMD -- Intentional identity-token comparison.
        if (aliasesMap) {
            copyArgbBand(module, pixels, width, horizontalMap, verticalMap, firstRow, rowCount);
            return;
        }
        byte[] memory = module.memory();
        int[] lookup = argbLookup;
        int row;
        for (row = 0; row < rowCount; row++) {
            int sourceRow = verticalMap[firstRow + row];
            int destinationRow = row * width;
            if (row > 0 && sourceRow == verticalMap[firstRow + row - 1]) {
                System.arraycopy(pixels, destinationRow - width, pixels, destinationRow, width);
            } else {
                int previousPackedAddress = -1;
                int packed = 0;
                int x;
                for (x = 0; x < width; x++) {
                    int mapping = horizontalMap[x];
                    int packedAddress = mapping & 0xff;
                    if (packedAddress != previousPackedAddress) {
                        packed = memory[FRAMEBUFFER + sourceRow + packedAddress] & 0xff;
                        previousPackedAddress = packedAddress;
                    }
                    pixels[destinationRow + x] = lookup[(packed << 2) | (mapping >>> 8)];
                }
            }
        }
    }

    /** Resolution-selected nearest-neighbour 160 to 240 conversion. */
    public void copyArgb240Band(WasmModule module, int[] pixels, int[] verticalMap, int firstRow, int rowCount) {
        if (rowCount < 0
                || firstRow < 0
                || rowCount > verticalMap.length - firstRow
                || pixels.length < 240 * rowCount) {
            throw new IllegalArgumentException("invalid 240-pixel ARGB band geometry");
        }
        byte[] memory = module.memory();
        int[] lookup = argbLookup;
        int row;
        for (row = 0; row < rowCount; row++) {
            int sourceRow = verticalMap[firstRow + row];
            int destination = row * 240;
            if (row > 0 && sourceRow == verticalMap[firstRow + row - 1]) {
                System.arraycopy(pixels, destination - 240, pixels, destination, 240);
            } else {
                int packedAddress;
                for (packedAddress = 0; packedAddress < 40; packedAddress++) {
                    int source = (memory[FRAMEBUFFER + sourceRow + packedAddress] & 0xff) << 2;
                    pixels[destination] = lookup[source];
                    pixels[destination + 1] = lookup[source];
                    pixels[destination + 2] = lookup[source + 1];
                    pixels[destination + 3] = lookup[source + 2];
                    pixels[destination + 4] = lookup[source + 2];
                    pixels[destination + 5] = lookup[source + 3];
                    destination += 6;
                }
            }
        }
    }

    private void textUtf8(byte[] memory, int pointer, int byteLength, int x, int y) {
        checkRange(memory, pointer, byteLength);
        int currentX = x;
        int index;
        for (index = 0; index < byteLength; index++) {
            int character = memory[pointer + index] & 0xff;
            if (character == 0) {
                break;
            }
            if (character == 10) {
                y += 8;
                currentX = x;
            } else if (character >= 32) {
                drawGlyph(memory, character, currentX, y);
                currentX += 8;
            } else {
                currentX += 8;
            }
        }
    }

    private void text(byte[] memory, int pointer, int x, int y) {
        if (pointer < 0 || pointer >= memory.length) {
            throw new WasmTrap("text pointer is out of bounds");
        }
        int length = 0;
        while (pointer + length < memory.length && memory[pointer + length] != 0) {
            length++;
        }
        if (pointer + length == memory.length) {
            throw new WasmTrap("unterminated text string");
        }
        textUtf8(memory, pointer, length, x, y);
    }

    private void textUtf16(byte[] memory, int pointer, int byteLength, int x, int y) {
        if ((byteLength & 1) != 0) {
            throw new WasmTrap("UTF-16 text byte length is odd");
        }
        checkRange(memory, pointer, byteLength);
        int currentX = x;
        int offset;
        for (offset = 0; offset < byteLength; offset += 2) {
            int character = readU16(memory, pointer + offset);
            if (character == 0) {
                break;
            }
            if (character == 10) {
                y += 8;
                currentX = x;
            } else if (character >= 32 && character <= 255) {
                drawGlyph(memory, character, currentX, y);
                currentX += 8;
            } else {
                currentX += 8;
            }
        }
    }

    private void rect(byte[] memory, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        long unclampedHorizontalEnd = (long) x + width;
        long unclampedVerticalEnd = (long) y + height;
        int startX = clampToScreen(x, WIDTH);
        int startY = clampToScreen(y, HEIGHT);
        int endX = clampToScreen(unclampedHorizontalEnd, WIDTH);
        int endY = clampToScreen(unclampedVerticalEnd, HEIGHT);
        int colors = readU16(memory, DRAW_COLORS);
        int fill = colors & 0x0f;
        int stroke = (colors >> 4) & 0x0f;
        int current;

        if (fill != 0) {
            for (current = startY; current < endY; current++) {
                drawHorizontal(memory, (fill - 1) & 3, startX, current, endX);
            }
        }
        if (stroke == 0) {
            return;
        }
        int strokeColor = (stroke - 1) & 3;
        if (x >= 0 && x < WIDTH) {
            drawVertical(memory, strokeColor, x, startY, endY);
        }
        if (unclampedHorizontalEnd > 0 && unclampedHorizontalEnd <= WIDTH) {
            drawVertical(memory, strokeColor, (int) unclampedHorizontalEnd - 1, startY, endY);
        }
        if (y >= 0 && y < HEIGHT) {
            drawHorizontal(memory, strokeColor, startX, y, endX);
        }
        if (unclampedVerticalEnd > 0 && unclampedVerticalEnd <= HEIGHT) {
            drawHorizontal(memory, strokeColor, startX, (int) unclampedVerticalEnd - 1, endX);
        }
    }

    private void hline(byte[] memory, int x, int y, int length) {
        if (length <= 0 || y < 0 || y >= HEIGHT) {
            return;
        }
        int drawColor = readU16(memory, DRAW_COLORS) & 0x0f;
        if (drawColor == 0) {
            return;
        }
        int startX = clampToScreen(x, WIDTH);
        int endX = clampToScreen((long) x + length, WIDTH);
        drawHorizontal(memory, (drawColor - 1) & 3, startX, y, endX);
    }

    private void vline(byte[] memory, int x, int y, int length) {
        if (length <= 0 || x < 0 || x >= WIDTH) {
            return;
        }
        int drawColor = readU16(memory, DRAW_COLORS) & 0x0f;
        if (drawColor == 0) {
            return;
        }
        int startY = clampToScreen(y, HEIGHT);
        int endY = clampToScreen((long) y + length, HEIGHT);
        int color = (drawColor - 1) & 3;
        drawVertical(memory, color, x, startY, endY);
    }

    private void line(byte[] memory, int x1, int y1, int x2, int y2) {
        int drawColor = readU16(memory, DRAW_COLORS) & 0x0f;
        if (drawColor == 0) {
            return;
        }
        if ((x1 < 0 && x2 < 0)
                || (x1 >= WIDTH && x2 >= WIDTH)
                || (y1 < 0 && y2 < 0)
                || (y1 >= HEIGHT && y2 >= HEIGHT)) {
            return;
        }
        long horizontalSpan = absoluteLong((long) x2 - x1);
        long verticalSpan = absoluteLong((long) y2 - y1);
        if (horizontalSpan > LINE_STEP_LIMIT || verticalSpan > LINE_STEP_LIMIT) {
            throw new WasmTrap("line geometry exceeds runtime step limit " + LINE_STEP_LIMIT);
        }
        int color = (drawColor - 1) & 3;
        if (y1 == y2) {
            int startX = x1 < x2 ? x1 : x2;
            int endX = (x1 < x2 ? x2 : x1) + 1;
            drawHorizontalUnclipped(memory, color, startX, y1, endX);
            return;
        }
        if (x1 == x2) {
            int startY = y1 < y2 ? y1 : y2;
            int endY = (y1 < y2 ? y2 : y1) + 1;
            if (startY < 0) {
                startY = 0;
            }
            if (endY > HEIGHT) {
                endY = HEIGHT;
            }
            if (startY < endY) {
                drawVertical(memory, color, x1, startY, endY);
            }
            return;
        }
        if (y1 > y2) {
            int swap = x1;
            x1 = x2;
            x2 = swap;
            swap = y1;
            y1 = y2;
            y2 = swap;
        }
        int deltaX = (int) horizontalSpan;
        int stepX = x1 < x2 ? 1 : -1;
        int deltaY = y2 - y1;
        int error = (deltaX > deltaY ? deltaX : -deltaY) / 2;
        while (true) {
            drawPointUnclipped(memory, color, x1, y1);
            if (x1 == x2 && y1 == y2) {
                return;
            }
            int previousError = error;
            if (previousError > -deltaX) {
                error -= deltaY;
                x1 += stepX;
            }
            if (previousError < deltaY) {
                error += deltaX;
                y1++;
            }
        }
    }

    private void oval(byte[] memory, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        long eastEdge = (long) x + width;
        long southEdge = (long) y + height;
        if (eastEdge <= 0 || southEdge <= 0 || x >= WIDTH || y >= HEIGHT) {
            return;
        }
        if (width > OVAL_DIMENSION_LIMIT || height > OVAL_DIMENSION_LIMIT) {
            throw new WasmTrap("oval geometry exceeds runtime limit " + OVAL_DIMENSION_LIMIT);
        }
        int colors = readU16(memory, DRAW_COLORS);
        int fill = colors & 0x0f;
        int stroke = (colors >> 4) & 0x0f;
        if (stroke == 0x0f) {
            return;
        }
        int strokeColor = (stroke - 1) & 3;
        int fillColor = (fill - 1) & 3;
        int a = width - 1;
        int b = height - 1;
        int oddHeight = b % 2;
        int north = y + height / 2;
        int west = x;
        int east = x + width - 1;
        int south = north - oddHeight;
        int horizontalRadiusSquared = a * a;
        int verticalRadiusSquared = b * b;
        int deltaX = 4 * (1 - a) * verticalRadiusSquared;
        int deltaY = 4 * (oddHeight + 1) * horizontalRadiusSquared;
        int error = deltaX + deltaY + oddHeight * horizontalRadiusSquared;
        a = 8 * horizontalRadiusSquared;
        oddHeight = 8 * verticalRadiusSquared;
        do {
            drawPointUnclipped(memory, strokeColor, east, north);
            drawPointUnclipped(memory, strokeColor, west, north);
            drawPointUnclipped(memory, strokeColor, west, south);
            drawPointUnclipped(memory, strokeColor, east, south);
            int start = west + 1;
            int length = east - start;
            if (fill != 0 && length > 0) {
                drawHorizontalUnclipped(memory, fillColor, start, north, east);
                drawHorizontalUnclipped(memory, fillColor, start, south, east);
            }
            int doubledError = 2 * error;
            if (doubledError <= deltaY) {
                north++;
                south--;
                deltaY += a;
                error += deltaY;
            }
            if (doubledError >= deltaX || doubledError > deltaY) {
                west++;
                east--;
                deltaX += oddHeight;
                error += deltaX;
            }
        } while (west <= east);
        while (north - south < height) {
            drawPointUnclipped(memory, strokeColor, west - 1, north);
            drawPointUnclipped(memory, strokeColor, east + 1, north);
            north++;
            drawPointUnclipped(memory, strokeColor, west - 1, south);
            drawPointUnclipped(memory, strokeColor, east + 1, south);
            south--;
        }
    }

    private void blit(
            byte[] memory, int pointer, int destinationX, int destinationY, int width, int height, int flags) {
        blitSub(memory, pointer, destinationX, destinationY, width, height, 0, 0, width, flags);
    }

    private void blitSub(
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
        int sourceByteLength = (int) ((bitLength + 7) >> 3);
        checkRange(memory, pointer, sourceByteLength);

        if (rotate) {
            flipX = !flipX;
        }
        long clipHorizontalOrigin = rotate ? destinationY : destinationX;
        long clipVerticalOrigin = rotate ? destinationX : destinationY;
        int clipHorizontalMinimum = clampToRange(-clipHorizontalOrigin, width);
        int clipVerticalMinimum = clampToRange(-clipVerticalOrigin, height);
        int clipHorizontalMaximum = clampToRange(HEIGHT - clipHorizontalOrigin, width);
        int clipVerticalMaximum = clampToRange(WIDTH - clipVerticalOrigin, height);
        int colors = readU16(memory, DRAW_COLORS);

        if ((flags & 15) == 8) {
            int verticalIndex;
            for (verticalIndex = clipVerticalMinimum; verticalIndex < clipVerticalMaximum; verticalIndex++) {
                int targetX = destinationX + verticalIndex;
                int targetY = destinationY + clipHorizontalMinimum;
                int framebufferAddress = FRAMEBUFFER + ((WIDTH * targetY + targetX) >> 2);
                int framebufferShift = (targetX & 3) << 1;
                int framebufferMask = 3 << framebufferShift;
                int bitIndex = (sourceY + verticalIndex) * sourceStride + sourceX + width - clipHorizontalMinimum - 1;
                int horizontalIndex;
                for (horizontalIndex = clipHorizontalMinimum;
                        horizontalIndex < clipHorizontalMaximum;
                        horizontalIndex++) {
                    int packed = memory[pointer + (bitIndex >> 3)] & 0xff;
                    int colorIndex = (packed >> (7 - (bitIndex & 7))) & 1;
                    int drawColor = (colors >> (colorIndex << 2)) & 0x0f;
                    if (drawColor != 0) {
                        memory[framebufferAddress] = (byte) ((((drawColor - 1) & 3) << framebufferShift)
                                | (memory[framebufferAddress] & 0xff & ~framebufferMask));
                    }
                    framebufferAddress += WIDTH >> 2;
                    bitIndex--;
                }
            }
            return;
        }

        if ((flags & 14) == 0) {
            boolean opaque2bpp = twoBitsPerPixel
                    && (colors & 0x000f) != 0
                    && (colors & 0x00f0) != 0
                    && (colors & 0x0f00) != 0
                    && (colors & 0xf000) != 0;
            // Sprite data is allowed to overlap the framebuffer. Preserve the
            // scalar read/write order whenever that is possible.
            boolean sourceOutsideFramebuffer =
                    pointer + sourceByteLength <= FRAMEBUFFER || pointer >= FRAMEBUFFER + FRAMEBUFFER_SIZE;
            if (opaque2bpp && sourceOutsideFramebuffer) {
                prepareOpaque2bppBlitLookup(colors);
            }
            int plainY;
            for (plainY = clipVerticalMinimum; plainY < clipVerticalMaximum; plainY++) {
                int targetX = destinationX + clipHorizontalMinimum;
                int targetY = destinationY + plainY;
                int bitIndex = (sourceY + plainY) * sourceStride + sourceX + clipHorizontalMinimum;
                int framebufferAddress = FRAMEBUFFER + ((WIDTH * targetY + targetX) >> 2);
                int framebufferShift = (targetX & 3) << 1;
                int plainX = clipHorizontalMinimum;
                if (opaque2bpp && sourceOutsideFramebuffer && framebufferShift == 0 && (bitIndex & 3) == 0) {
                    int packedEnd = clipHorizontalMaximum - 3;
                    while (plainX < packedEnd) {
                        int packed = memory[pointer + (bitIndex >> 2)] & 0xff;
                        memory[framebufferAddress] = (byte) opaque2bppBlitLookup[packed];
                        framebufferAddress++;
                        bitIndex += 4;
                        plainX += 4;
                    }
                }
                for (; plainX < clipHorizontalMaximum; plainX++) {
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
                        memory[framebufferAddress] = (byte) ((((drawColor - 1) & 3) << framebufferShift)
                                | (memory[framebufferAddress] & 0xff & ~(3 << framebufferShift)));
                    }
                    framebufferShift += 2;
                    if (framebufferShift == 8) {
                        framebufferShift = 0;
                        framebufferAddress++;
                    }
                    bitIndex++;
                }
            }
            return;
        }

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

    private void prepareOpaque2bppBlitLookup(int colors) {
        if (opaque2bppBlitColors == colors) {
            return;
        }
        int mappedColors = (((colors & 0x000f) - 1) & 3)
                | (((((colors >>> 4) & 0x0f) - 1) & 3) << 2)
                | (((((colors >>> 8) & 0x0f) - 1) & 3) << 4)
                | (((((colors >>> 12) & 0x0f) - 1) & 3) << 6);
        int packed;
        for (packed = 0; packed < 256; packed++) {
            opaque2bppBlitLookup[packed] = ((mappedColors >>> (((packed >>> 6) & 3) << 1)) & 3)
                    | (((mappedColors >>> (((packed >>> 4) & 3) << 1)) & 3) << 2)
                    | (((mappedColors >>> (((packed >>> 2) & 3) << 1)) & 3) << 4)
                    | (((mappedColors >>> ((packed & 3) << 1)) & 3) << 6);
        }
        opaque2bppBlitColors = colors;
    }

    private void drawVertical(byte[] memory, int color, int x, int startY, int endY) {
        if (startY >= endY) {
            return;
        }
        int address = FRAMEBUFFER + ((WIDTH * startY + x) >> 2);
        int shift = (x & 3) << 1;
        int mask = 3 << shift;
        int packedColor = color << shift;
        int row;
        for (row = startY; row < endY; row++) {
            memory[address] = (byte) (packedColor | (memory[address] & 0xff & ~mask));
            address += WIDTH >> 2;
        }
    }

    private void drawHorizontal(byte[] memory, int color, int startX, int y, int endX) {
        if (startX >= endX) {
            return;
        }
        int x = startX;
        int address = FRAMEBUFFER + ((WIDTH * y + x) >> 2);
        if ((x & 3) != 0) {
            int packed = memory[address] & 0xff;
            while (x < endX && (x & 3) != 0) {
                int shift = (x & 3) << 1;
                int mask = 3 << shift;
                packed = (packed & ~mask) | (color << shift);
                x++;
            }
            memory[address] = (byte) packed;
            address++;
        }
        int packedColor = color * 0x55;
        while (endX - x >= 4) {
            memory[address] = (byte) packedColor;
            address++;
            x += 4;
        }
        if (x < endX) {
            int packed = memory[address] & 0xff;
            while (x < endX) {
                int shift = (x & 3) << 1;
                int mask = 3 << shift;
                packed = (packed & ~mask) | (color << shift);
                x++;
            }
            memory[address] = (byte) packed;
        }
    }

    private void drawHorizontalUnclipped(byte[] memory, int color, int startX, int y, int endX) {
        if (y < 0 || y >= HEIGHT) {
            return;
        }
        if (startX < 0) {
            startX = 0;
        }
        if (endX > WIDTH) {
            endX = WIDTH;
        }
        if (startX < endX) {
            drawHorizontal(memory, color, startX, y, endX);
        }
    }

    private void drawGlyph(byte[] memory, int character, int x, int y) {
        int colors = readU16(memory, DRAW_COLORS);
        int glyphOffset = (character - 32) << 3;
        if (x >= 0 && x <= WIDTH - 8 && y < HEIGHT && y + 8 > 0) {
            drawGlyphPacked(memory, glyphOffset, x, y, colors);
            return;
        }
        int row;
        for (row = 0; row < 8; row++) {
            int targetY = y + row;
            if (targetY < 0 || targetY >= HEIGHT) {
                continue;
            }
            int bits = font[glyphOffset + row] & 0xff;
            int column;
            for (column = 0; column < 8; column++) {
                int targetX = x + column;
                if (targetX < 0 || targetX >= WIDTH) {
                    continue;
                }
                int sourceColor = (bits >> (7 - column)) & 1;
                int drawColor = (colors >> (sourceColor << 2)) & 0x0f;
                if (drawColor != 0) {
                    drawPoint(memory, (drawColor - 1) & 3, targetX, targetY);
                }
            }
        }
    }

    private void drawGlyphPacked(byte[] memory, int glyphOffset, int x, int y, int colors) {
        int drawColor0 = colors & 0x0f;
        int drawColor1 = (colors >> 4) & 0x0f;
        if (drawColor0 == 0 && drawColor1 == 0) {
            return;
        }
        int color0 = (drawColor0 - 1) & 3;
        int color1 = (drawColor1 - 1) & 3;
        int packedColor0 = color0 * 0x55;
        int packedColor1 = color1 * 0x55;
        int packedDifference = packedColor0 ^ packedColor1;
        int firstRow = y < 0 ? -y : 0;
        int lastRow = HEIGHT - y;
        if (lastRow > 8) {
            lastRow = 8;
        }
        int address = FRAMEBUFFER + (((y + firstRow) * WIDTH + x) >> 2);
        int shift = (x & 3) << 1;
        int row;
        if (shift == 0) {
            if (drawColor0 != 0 && drawColor1 != 0) {
                for (row = firstRow; row < lastRow; row++) {
                    int bits = font[glyphOffset + row] & 0xff;
                    int highMask = GLYPH_1BPP_MASK[bits >>> 4];
                    int lowMask = GLYPH_1BPP_MASK[bits & 0x0f];
                    memory[address] = (byte) (packedColor0 ^ (highMask & packedDifference));
                    memory[address + 1] = (byte) (packedColor0 ^ (lowMask & packedDifference));
                    address += WIDTH >> 2;
                }
            } else if (drawColor0 != 0) {
                for (row = firstRow; row < lastRow; row++) {
                    int bits = font[glyphOffset + row] & 0xff;
                    int highMask = GLYPH_1BPP_MASK[bits >>> 4];
                    int lowMask = GLYPH_1BPP_MASK[bits & 0x0f];
                    int highOpaque = ~highMask & 0xff;
                    int lowOpaque = ~lowMask & 0xff;
                    memory[address] = (byte) ((memory[address] & 0xff & ~highOpaque) | (packedColor0 & highOpaque));
                    memory[address + 1] =
                            (byte) ((memory[address + 1] & 0xff & ~lowOpaque) | (packedColor0 & lowOpaque));
                    address += WIDTH >> 2;
                }
            } else {
                for (row = firstRow; row < lastRow; row++) {
                    int bits = font[glyphOffset + row] & 0xff;
                    int highMask = GLYPH_1BPP_MASK[bits >>> 4];
                    int lowMask = GLYPH_1BPP_MASK[bits & 0x0f];
                    memory[address] = (byte) ((memory[address] & 0xff & ~highMask) | (packedColor1 & highMask));
                    memory[address + 1] = (byte) ((memory[address + 1] & 0xff & ~lowMask) | (packedColor1 & lowMask));
                    address += WIDTH >> 2;
                }
            }
            return;
        }

        int packedColor0Wide = color0 * 0x5555;
        int packedColor1Wide = color1 * 0x5555;
        int packedDifferenceWide = packedColor0Wide ^ packedColor1Wide;
        for (row = firstRow; row < lastRow; row++) {
            int bits = font[glyphOffset + row] & 0xff;
            int glyphMask = GLYPH_1BPP_MASK[bits >>> 4] | (GLYPH_1BPP_MASK[bits & 0x0f] << 8);
            int writeMask;
            int glyphPixels;
            if (drawColor0 != 0 && drawColor1 != 0) {
                writeMask = 0xffff;
                glyphPixels = packedColor0Wide ^ (glyphMask & packedDifferenceWide);
            } else if (drawColor0 != 0) {
                writeMask = ~glyphMask & 0xffff;
                glyphPixels = packedColor0Wide & writeMask;
            } else {
                writeMask = glyphMask;
                glyphPixels = packedColor1Wide & writeMask;
            }
            writeMask <<= shift;
            glyphPixels <<= shift;
            int previous = (memory[address] & 0xff)
                    | ((memory[address + 1] & 0xff) << 8)
                    | ((memory[address + 2] & 0xff) << 16);
            int result = (previous & ~writeMask) | glyphPixels;
            memory[address] = (byte) result;
            memory[address + 1] = (byte) (result >> 8);
            memory[address + 2] = (byte) (result >> 16);
            address += WIDTH >> 2;
        }
    }

    private void drawPoint(byte[] memory, int color, int x, int y) {
        int pixel = WIDTH * y + x;
        int address = FRAMEBUFFER + (pixel >> 2);
        int shift = (x & 3) << 1;
        int mask = 3 << shift;
        memory[address] = (byte) ((color << shift) | (memory[address] & 0xff & ~mask));
    }

    private void drawPointUnclipped(byte[] memory, int color, int x, int y) {
        if (x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT) {
            drawPoint(memory, color, x, y);
        }
    }

    private int diskRead(byte[] memory, int pointer, int size) {
        int count = diskTransferSize(memory, pointer, size);
        return disk.read(memory, pointer, count);
    }

    private int diskWrite(byte[] memory, int pointer, int size) {
        int count = diskTransferSize(memory, pointer, size);
        return disk.write(memory, pointer, count);
    }

    private int diskTransferSize(byte[] memory, int pointer, int size) {
        if (size < 0) {
            throw new WasmTrap("negative disk transfer size");
        }
        int count = min(size, 1024);
        checkRange(memory, pointer, count);
        return count;
    }

    private String readString(byte[] memory, int pointer, int byteLength) {
        checkRange(memory, pointer, byteLength);
        StringBuffer result = new StringBuffer(byteLength);
        int index;
        for (index = 0; index < byteLength; index++) {
            int value = memory[pointer + index] & 0xff;
            if (value == 0) {
                break;
            }
            result.append((char) value);
        }
        return result.toString();
    }

    private String readNullTerminatedString(byte[] memory, int pointer) {
        if (pointer < 0 || pointer >= memory.length) {
            throw new WasmTrap("trace pointer is out of bounds");
        }
        int length = 0;
        while (pointer + length < memory.length && memory[pointer + length] != 0) {
            length++;
        }
        if (pointer + length == memory.length) {
            throw new WasmTrap("unterminated trace string");
        }
        return readString(memory, pointer, length);
    }

    private String readUtf16String(byte[] memory, int pointer, int byteLength) {
        if ((byteLength & 1) != 0) {
            throw new WasmTrap("UTF-16 trace byte length is odd");
        }
        checkRange(memory, pointer, byteLength);
        StringBuffer result = new StringBuffer(byteLength / 2);
        int offset;
        for (offset = 0; offset < byteLength; offset += 2) {
            int value = readU16(memory, pointer + offset);
            if (value == 0) {
                break;
            }
            result.append((char) value);
        }
        return result.toString();
    }

    private String formatTrace(byte[] memory, int formatPointer, int argumentPointer) {
        if (formatPointer < 0 || formatPointer >= memory.length) {
            throw new WasmTrap("tracef format pointer is out of bounds");
        }
        StringBuffer result = new StringBuffer();
        int pointer = formatPointer;
        while (true) {
            if (pointer >= memory.length) {
                throw new WasmTrap("unterminated tracef format string");
            }
            int character = memory[pointer++] & 0xff; // NOPMD -- Compact Java 1.3 cursor bytecode.
            if (character == 0) {
                return result.toString();
            }
            if (character != '%') {
                appendTraceCharacter(result, (char) character);
                continue;
            }
            if (pointer >= memory.length) {
                throw new WasmTrap("unterminated tracef format string");
            }
            int format = memory[pointer++] & 0xff; // NOPMD -- Compact Java 1.3 cursor bytecode.
            if (format == 0) {
                return result.toString();
            }
            if (format == '%') {
                appendTraceCharacter(result, '%');
            } else if (format == 'c') {
                checkRange(memory, argumentPointer, 4);
                appendTraceCharacter(result, (char) readI32(memory, argumentPointer));
                argumentPointer += 4;
            } else if (format == 'd') {
                checkRange(memory, argumentPointer, 4);
                appendTraceString(result, Integer.toString(readI32(memory, argumentPointer)));
                argumentPointer += 4;
            } else if (format == 'x') {
                checkRange(memory, argumentPointer, 4);
                appendTraceString(result, Integer.toHexString(readI32(memory, argumentPointer)));
                argumentPointer += 4;
            } else if (format == 's') {
                checkRange(memory, argumentPointer, 4);
                int stringPointer = readI32(memory, argumentPointer);
                argumentPointer += 4;
                appendTraceNullTerminatedString(memory, stringPointer, result);
            } else if (format == 'f') {
                checkRange(memory, argumentPointer, 8);
                appendTraceString(result, formatTraceDouble(Double.longBitsToDouble(readI64(memory, argumentPointer))));
                argumentPointer += 8;
            } else {
                appendTraceCharacter(result, '%');
                appendTraceCharacter(result, (char) format);
            }
        }
    }

    private void appendTraceNullTerminatedString(byte[] memory, int pointer, StringBuffer result) {
        if (pointer < 0 || pointer >= memory.length) {
            throw new WasmTrap("tracef string pointer is out of bounds");
        }
        while (pointer < memory.length) {
            int value = memory[pointer++] & 0xff; // NOPMD -- Compact Java 1.3 cursor bytecode.
            if (value == 0) {
                return;
            }
            appendTraceCharacter(result, (char) value);
        }
        throw new WasmTrap("unterminated tracef string argument");
    }

    private void appendTraceString(StringBuffer result, String value) {
        if (value.length() > TRACE_OUTPUT_LIMIT - result.length()) {
            throw new WasmTrap("tracef output exceeds runtime limit " + TRACE_OUTPUT_LIMIT);
        }
        result.append(value);
    }

    private void appendTraceCharacter(StringBuffer result, char value) {
        if (result.length() >= TRACE_OUTPUT_LIMIT) {
            throw new WasmTrap("tracef output exceeds runtime limit " + TRACE_OUTPUT_LIMIT);
        }
        result.append(value);
    }

    private String formatTraceDouble(double value) {
        long bits = Double.doubleToLongBits(value);
        boolean negative = bits < 0;
        double absolute = negative ? -value : value;
        if (Double.isNaN(value)) {
            return "nan";
        }
        if (Double.isInfinite(value)) {
            return negative ? "-inf" : "inf";
        }
        if (absolute == 0.0) {
            return negative ? "-0" : "0";
        }

        int exponent = 0;
        double mantissa = absolute;
        while (mantissa >= 10.0) {
            mantissa /= 10.0;
            exponent++;
        }
        while (mantissa < 1.0) {
            mantissa *= 10.0;
            exponent--;
        }
        long mantissaScaled = (long) Math.floor(mantissa * 100000.0 + 0.5);
        if (mantissaScaled >= 1000000L) {
            mantissaScaled = 100000L;
            exponent++;
        }

        StringBuffer result = new StringBuffer();
        if (negative) {
            result.append('-');
        }
        if (exponent < -4 || exponent >= 6) {
            appendScaledDecimal(result, mantissaScaled, 100000L, 5);
            result.append('e');
            if (exponent < 0) {
                result.append('-');
                exponent = -exponent;
            } else {
                result.append('+');
            }
            if (exponent < 10) {
                result.append('0');
            }
            result.append(exponent);
            return result.toString();
        }

        int fractionDigits = 5 - exponent;
        long fractionScale = powerOfTen(fractionDigits);
        long scaled = (long) Math.floor(absolute * fractionScale + 0.5);
        appendScaledDecimal(result, scaled, fractionScale, fractionDigits);
        return result.toString();
    }

    private void appendScaledDecimal(StringBuffer output, long scaled, long scale, int fractionDigits) {
        output.append(scaled / scale);
        long fraction = scaled % scale;
        if (fraction == 0 || fractionDigits == 0) {
            return;
        }
        String digits = Long.toString(fraction);
        int padding = fractionDigits - digits.length();
        output.append('.');
        while (padding-- > 0) {
            output.append('0');
        }
        int end = digits.length();
        while (end > 0 && digits.charAt(end - 1) == '0') {
            end--;
        }
        output.append(digits.substring(0, end));
    }

    private long powerOfTen(int exponent) {
        long result = 1;
        int index;
        for (index = 0; index < exponent; index++) {
            result *= 10;
        }
        return result;
    }

    private void checkRange(byte[] memory, int pointer, int length) {
        if (pointer < 0 || length < 0 || length > memory.length - pointer) {
            throw new WasmTrap("host function memory range is out of bounds");
        }
    }

    private static int readU16(byte[] memory, int address) {
        return (memory[address] & 0xff) | ((memory[address + 1] & 0xff) << 8);
    }

    private static int readI32(byte[] memory, int address) {
        return (memory[address] & 0xff)
                | ((memory[address + 1] & 0xff) << 8)
                | ((memory[address + 2] & 0xff) << 16)
                | (memory[address + 3] << 24);
    }

    private static long readI64(byte[] memory, int address) {
        long low = readI32(memory, address) & 0xffffffffL;
        long high = readI32(memory, address + 4) & 0xffffffffL;
        return low | (high << 32);
    }

    private static int min(int left, int right) {
        return left < right ? left : right;
    }

    private static int clampToScreen(long value, int size) {
        if (value <= 0) {
            return 0;
        }
        if (value >= size) {
            return size;
        }
        return (int) value;
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

    private static long absoluteLong(long value) {
        return value < 0 ? -value : value;
    }

    private static void writeI16(byte[] memory, int address, int value) {
        memory[address] = (byte) value;
        memory[address + 1] = (byte) (value >>> 8);
    }

    private static void writeI32(byte[] memory, int address, int value) {
        memory[address] = (byte) value;
        memory[address + 1] = (byte) (value >>> 8);
        memory[address + 2] = (byte) (value >>> 16);
        memory[address + 3] = (byte) (value >>> 24);
    }
}
