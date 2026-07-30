package w4me.wasm;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.StringTokenizer;

/** Exact deterministic routes shared by whole-corpus profiling and differential checks. */
final class CorpusWorkload {
    final String name;
    final String route;
    final byte[] cartridge;
    final String cartridgeSha256;
    final InputState[] inputs;

    private CorpusWorkload(String name, String route, byte[] cartridge, String cartridgeSha256, InputState[] inputs) {
        this.name = name;
        this.route = route;
        this.cartridge = cartridge;
        this.cartridgeSha256 = cartridgeSha256;
        this.inputs = inputs;
    }

    static CorpusWorkload[] readAll(String[] arguments, int firstArgument) throws Exception {
        if (arguments.length - firstArgument != 10) {
            throw new IllegalArgumentException("expected font, five carts, three input traces, and Game of Life cart");
        }
        byte[] plasma = readFile(arguments[firstArgument + 1]);
        byte[] duck = readFile(arguments[firstArgument + 2]);
        byte[] waternet = readFile(arguments[firstArgument + 3]);
        byte[] rubido = readFile(arguments[firstArgument + 5]);
        byte[] untangle = readFile(arguments[firstArgument + 7]);
        byte[] gameOfLife = readFile(arguments[firstArgument + 9]);
        InputState[] waternetRoute = readInputTrace(arguments[firstArgument + 4]);
        InputState[] rubidoRoute = readInputTrace(arguments[firstArgument + 6]);
        InputState[] untangleRoute = readInputTrace(arguments[firstArgument + 8]);
        return new CorpusWorkload[] {
            workload("plasma-cube", "idle-60-v1", plasma, idle(60, 0, 32767, 32767)),
            workload("duck-maze", "level-1-v1", duck, duckLevelOne()),
            workload(
                    "waternet",
                    "browser-route-" + sha256(readFile(arguments[firstArgument + 4])),
                    waternet,
                    waternetRoute),
            workload("waternet", "idle-60-v1", waternet, idle(60, 0, 32767, 32767)),
            workload("rubido", "browser-route-" + sha256(readFile(arguments[firstArgument + 6])), rubido, rubidoRoute),
            workload(
                    "untangle",
                    "browser-route-" + sha256(readFile(arguments[firstArgument + 8])),
                    untangle,
                    untangleRoute),
            workload("game-of-life-zig-edition", "idle-1-v1", gameOfLife, idle(1, 0, 32767, 32767))
        };
    }

    static byte[] readFont(String[] arguments, int firstArgument) throws Exception {
        return readFile(arguments[firstArgument]);
    }

    private static CorpusWorkload workload(String name, String route, byte[] cartridge, InputState[] inputs)
            throws Exception {
        return new CorpusWorkload(name, route, cartridge, sha256(cartridge), inputs);
    }

    private static InputState[] idle(int frames, int gamepad, int mouseX, int mouseY) {
        InputState[] result = new InputState[frames];
        int frame;
        for (frame = 0; frame < result.length; frame++) {
            result[frame] = new InputState(gamepad, 0, mouseX, mouseY, 0);
        }
        return result;
    }

    private static InputState[] duckLevelOne() {
        InputState[] result = new InputState[155];
        int frame = 0;
        result[frame++] = new InputState(0, 0, 0, 0, 0); // NOPMD -- Compact Java 1.3 cursor bytecode.
        result[frame++] = new InputState(1, 0, 0, 0, 0); // NOPMD -- Compact Java 1.3 cursor bytecode.
        result[frame++] = new InputState(0, 0, 0, 0, 0); // NOPMD -- Compact Java 1.3 cursor bytecode.
        frame = fillGamepad(result, frame, 128, 32);
        frame = fillGamepad(result, frame, 32, 24);
        frame = fillGamepad(result, frame, 128, 16);
        frame = fillGamepad(result, frame, 32, 32);
        frame = fillGamepad(result, frame, 64, 48);
        if (frame != result.length) {
            throw new IllegalStateException("invalid Duck Maze route length");
        }
        return result;
    }

    private static int fillGamepad(InputState[] result, int frame, int gamepad, int count) {
        int index;
        for (index = 0; index < count; index++) {
            result[frame++] = new InputState(gamepad, 0, 0, 0, 0); // NOPMD -- Compact Java 1.3 cursor bytecode.
        }
        return frame;
    }

    private static InputState[] readInputTrace(String path) throws Exception {
        BufferedReader input = new BufferedReader(new FileReader(path));
        try {
            requireEquals("input trace header", "frame,gamepad,mouse_x,mouse_y,mouse_buttons,action", input.readLine());
            InputState[] events = new InputState[1024];
            int[] frames = new int[events.length];
            int count = 0;
            int previousFrame = -1;
            String line;
            while ((line = input.readLine()) != null) {
                if (line.length() == 0) {
                    continue;
                }
                StringTokenizer fields = new StringTokenizer(line, ",");
                if (fields.countTokens() != 6 || count >= events.length) {
                    throw new IllegalArgumentException("invalid input trace row: " + line);
                }
                int frame = Integer.parseInt(fields.nextToken());
                final int gamepad = Integer.parseInt(fields.nextToken());
                final int mouseX = Integer.parseInt(fields.nextToken());
                final int mouseY = Integer.parseInt(fields.nextToken());
                final int mouseButtons = Integer.parseInt(fields.nextToken());
                fields.nextToken();
                if (frame <= previousFrame) {
                    throw new IllegalArgumentException("invalid input frame: " + frame);
                }
                frames[count] = frame;
                events[count] = new InputState(gamepad, 0, mouseX, mouseY, mouseButtons);
                count++;
                previousFrame = frame;
            }
            if (count == 0 || frames[0] != 0) {
                throw new IllegalArgumentException("input trace must start at frame zero");
            }
            InputState[] result = new InputState[previousFrame + 1];
            int event = 0;
            InputState current = null;
            int frame;
            for (frame = 0; frame < result.length; frame++) {
                if (event < count && frames[event] == frame) {
                    current = events[
                            event++]; // NOPMD -- Cursor mutation stays adjacent to the access to preserve compact Java
                    // 1.3 bytecode.
                }
                result[frame] = current;
            }
            return result;
        } finally {
            input.close();
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

    static String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return hex(digest.digest(bytes));
    }

    private static String hex(byte[] bytes) {
        StringBuffer result = new StringBuffer(bytes.length * 2);
        int index;
        for (index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            if (value < 16) {
                result.append('0');
            }
            result.append(Integer.toHexString(value));
        }
        return result.toString();
    }

    private static void requireEquals(String label, String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new IllegalArgumentException(label + ": expected " + expected + ", got " + actual);
        }
    }

    static final class InputState {
        final int gamepad1;
        final int gamepad2;
        final int mouseX;
        final int mouseY;
        final int mouseButtons;

        private InputState(int gamepad1, int gamepad2, int mouseX, int mouseY, int mouseButtons) {
            this.gamepad1 = gamepad1;
            this.gamepad2 = gamepad2;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.mouseButtons = mouseButtons;
        }
    }
}
