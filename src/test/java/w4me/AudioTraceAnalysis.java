package w4me;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.StringTokenizer;
import w4me.runtime.Wasm4Runtime;
import w4me.runtime.audio.AudioBackend;
import w4me.runtime.audio.Wasm4Apu;
import w4me.runtime.audio.Wasm4Pcm;
import w4me.wasm.WasmInterpreter;
import w4me.wasm.WasmModule;

/**
 * Emits cartridge tone calls together with PCM boundary-discontinuity metrics.
 *
 * <p>The optional input uses the sparse browser-route CSV format. Frames after the last input row keep its state.
 */
public final class AudioTraceAnalysis {
    private static final int WAV_HEADER_SIZE = 44;

    private AudioTraceAnalysis() {}

    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length < 3 || arguments.length > 4) {
            throw new IllegalArgumentException("usage: font.bin cart.wasm frames [input.csv]");
        }
        int frameCount = Integer.parseInt(arguments[2]);
        if (frameCount <= 0) {
            throw new IllegalArgumentException("frames must be positive");
        }

        final InputState[] inputs =
                arguments.length == 4 ? readInputTrace(arguments[3], frameCount) : emptyInputTrace(frameCount);
        WasmModule module = WasmModule.read(readFile(arguments[1]));
        RecordingBackend backend = new RecordingBackend(System.out);
        Wasm4Apu apu = new Wasm4Apu(backend);
        Wasm4Runtime runtime = new Wasm4Runtime(readFile(arguments[0]), apu);
        runtime.initialize(module);
        WasmInterpreter interpreter = new WasmInterpreter(module, runtime);
        interpreter.invokeCartridgeLifecycle();

        backend.printHeader();
        int frame;
        for (frame = 0; frame < frameCount; frame++) {
            InputState input = inputs[frame];
            backend.frame = frame;
            runtime.beginFrame(module, input.gamepad, input.mouseX, input.mouseY, input.mouseButtons);
            interpreter.invoke("update");
            runtime.endFrame();
        }
        backend.printSummary(frameCount, FramebufferOracle.fnv1a(module));
        runtime.close();
        module.close();
    }

    private static InputState[] emptyInputTrace(int frameCount) {
        InputState[] states = new InputState[frameCount];
        InputState empty = new InputState(0, 0, 0, 0);
        int frame;
        for (frame = 0; frame < frameCount; frame++) {
            states[frame] = empty;
        }
        return states;
    }

    private static InputState[] readInputTrace(String path, int frameCount) throws Exception {
        BufferedReader input = new BufferedReader(new FileReader(path));
        try {
            String header = input.readLine();
            if (!"frame,gamepad,mouse_x,mouse_y,mouse_buttons,action".equals(header)) {
                throw new IllegalArgumentException("invalid input trace header");
            }
            int[] eventFrames = new int[256];
            InputState[] events = new InputState[256];
            int eventCount = 0;
            int previousFrame = -1;
            String line;
            while ((line = input.readLine()) != null) {
                if (line.length() == 0) {
                    continue;
                }
                StringTokenizer fields = new StringTokenizer(line, ",");
                if (fields.countTokens() != 6 || eventCount >= events.length) {
                    throw new IllegalArgumentException("invalid input row: " + line);
                }
                int frame = Integer.parseInt(fields.nextToken());
                final int gamepad = Integer.parseInt(fields.nextToken());
                final int mouseX = Integer.parseInt(fields.nextToken());
                final int mouseY = Integer.parseInt(fields.nextToken());
                final int mouseButtons = Integer.parseInt(fields.nextToken());
                fields.nextToken();
                if (frame <= previousFrame || frame < 0 || frame >= frameCount) {
                    throw new IllegalArgumentException("invalid input frame: " + frame);
                }
                eventFrames[eventCount] = frame;
                events[eventCount] = new InputState(gamepad, mouseX, mouseY, mouseButtons);
                eventCount++;
                previousFrame = frame;
            }
            if (eventCount == 0 || eventFrames[0] != 0) {
                throw new IllegalArgumentException("input trace must start at frame zero");
            }

            InputState[] states = new InputState[frameCount];
            InputState current = events[0];
            int event = 1;
            int frame;
            for (frame = 0; frame < frameCount; frame++) {
                if (event < eventCount && eventFrames[event] == frame) {
                    current = events[
                            event++]; // NOPMD -- Cursor mutation stays adjacent to the access to preserve compact Java
                    // 1.3 bytecode.
                }
                states[frame] = current;
            }
            return states;
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

    private static int readShortLe(byte[] data, int offset) {
        return (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8);
    }

    private static int readIntLe(byte[] data, int offset) {
        return (data[offset] & 0xff)
                | ((data[offset + 1] & 0xff) << 8)
                | ((data[offset + 2] & 0xff) << 16)
                | (data[offset + 3] << 24);
    }

    private static int distanceFromSilence(int value) {
        int distance = value - 128;
        return distance < 0 ? -distance : distance;
    }

    private static final class InputState {
        private final int gamepad;
        private final int mouseX;
        private final int mouseY;
        private final int mouseButtons;

        private InputState(int gamepad, int mouseX, int mouseY, int mouseButtons) {
            this.gamepad = gamepad;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.mouseButtons = mouseButtons;
        }
    }

    private static final class RecordingBackend implements AudioBackend {
        private final PrintStream output;
        private final int[] channelEndFrames = {-1, -1, -1, -1};
        private int frame = -1;
        private int eventCount;
        private int zeroReleaseCount;
        private int overlappingReplacementCount;
        private int referenceContinuityCount;
        private int nonSilentStarts;
        private int nonSilentEnds;
        private int maximumAdjacentStep;

        private RecordingBackend(PrintStream output) {
            this.output = output;
        }

        public void submitTone(int frequency, int duration, int volume, int flags) {
            int channel = flags & 3;
            int sustain = duration & 0xff;
            int release = (duration >>> 8) & 0xff;
            int decay = (duration >>> 16) & 0xff;
            int attack = (duration >>> 24) & 0xff;
            int totalFrames = attack + decay + sustain + release;
            int previousEndFrame = channelEndFrames[channel];
            boolean overlappingReplacement = previousEndFrame >= 0 && frame < previousEndFrame;
            boolean referenceContinuity = previousEndFrame >= 0 && frame <= previousEndFrame;
            byte[] wav = Wasm4Pcm.synthesize(frequency, duration, volume, flags);
            PcmMetrics metrics = PcmMetrics.measure(wav);

            output.println(eventCount
                    + ","
                    + frame
                    + ","
                    + frequency
                    + ","
                    + duration
                    + ","
                    + volume
                    + ","
                    + flags
                    + ","
                    + channel
                    + ","
                    + attack
                    + ","
                    + decay
                    + ","
                    + sustain
                    + ","
                    + release
                    + ","
                    + totalFrames
                    + ","
                    + previousEndFrame
                    + ","
                    + (overlappingReplacement ? 1 : 0)
                    + ","
                    + (referenceContinuity ? 1 : 0)
                    + ","
                    + metrics.pcmBytes
                    + ","
                    + metrics.firstDistance
                    + ","
                    + metrics.lastDistance
                    + ","
                    + metrics.maximumAdjacentStep);

            eventCount++;
            if (release == 0) {
                zeroReleaseCount++;
            }
            if (overlappingReplacement) {
                overlappingReplacementCount++;
            }
            if (referenceContinuity) {
                referenceContinuityCount++;
            }
            if (metrics.firstDistance > 0) {
                nonSilentStarts++;
            }
            if (metrics.lastDistance > 0) {
                nonSilentEnds++;
            }
            if (metrics.maximumAdjacentStep > maximumAdjacentStep) {
                maximumAdjacentStep = metrics.maximumAdjacentStep;
            }
            channelEndFrames[channel] = frame + totalFrames;
        }

        public void tick() {
            /* Intentionally no-op. */
        }

        public void close() {
            /* Intentionally no-op. */
        }

        public String grade() {
            return "trace";
        }

        private void printHeader() {
            output.println("event,frame,frequency,duration,volume,flags,channel,"
                    + "attack,decay,sustain,release,total_frames,"
                    + "previous_end_frame,overlapping_replacement,"
                    + "web_phase_continuity,pcm_bytes,first_distance,"
                    + "last_distance,max_adjacent_step");
        }

        private void printSummary(int frames, int framebufferFnv1a) {
            output.println("SUMMARY frames="
                    + frames
                    + " events="
                    + eventCount
                    + " zero-release="
                    + zeroReleaseCount
                    + " overlapping-replacements="
                    + overlappingReplacementCount
                    + " web-phase-continuity="
                    + referenceContinuityCount
                    + " non-silent-starts="
                    + nonSilentStarts
                    + " non-silent-ends="
                    + nonSilentEnds
                    + " max-adjacent-step="
                    + maximumAdjacentStep
                    + " framebuffer-fnv1a="
                    + Integer.toHexString(framebufferFnv1a));
        }
    }

    private static final class PcmMetrics {
        private static final PcmMetrics EMPTY = new PcmMetrics(0, 0, 0, 0);

        private final int pcmBytes;
        private final int firstDistance;
        private final int lastDistance;
        private final int maximumAdjacentStep;

        private PcmMetrics(int pcmBytes, int firstDistance, int lastDistance, int maximumAdjacentStep) {
            this.pcmBytes = pcmBytes;
            this.firstDistance = firstDistance;
            this.lastDistance = lastDistance;
            this.maximumAdjacentStep = maximumAdjacentStep;
        }

        private static PcmMetrics measure(byte[] wav) {
            if (wav == null) {
                return EMPTY;
            }
            int channels = readShortLe(wav, 22);
            int dataLength = readIntLe(wav, 40);
            if (channels <= 0 || dataLength <= 0) {
                return EMPTY;
            }
            int samples = dataLength / channels;
            int firstDistance = 0;
            int lastDistance = 0;
            int maximumAdjacentStep = 0;
            int channel;
            for (channel = 0; channel < channels; channel++) {
                int first = wav[WAV_HEADER_SIZE + channel] & 0xff;
                int last = wav[WAV_HEADER_SIZE + (samples - 1) * channels + channel] & 0xff;
                int distance = distanceFromSilence(first);
                if (distance > firstDistance) {
                    firstDistance = distance;
                }
                distance = distanceFromSilence(last);
                if (distance > lastDistance) {
                    lastDistance = distance;
                }
                int previous = first;
                int sample;
                for (sample = 1; sample < samples; sample++) {
                    int current = wav[WAV_HEADER_SIZE + sample * channels + channel] & 0xff;
                    int step = current - previous;
                    if (step < 0) {
                        step = -step;
                    }
                    if (step > maximumAdjacentStep) {
                        maximumAdjacentStep = step;
                    }
                    previous = current;
                }
            }
            return new PcmMetrics(dataLength, firstDistance, lastDistance, maximumAdjacentStep);
        }
    }
}
