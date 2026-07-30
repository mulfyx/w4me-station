package w4me;

import w4me.runtime.audio.Wasm4Pcm;

/** CLDC-only component benchmark for production PCM synthesis. */
public final class PhoneMePcmBench {
    private static final int FNV_OFFSET = 0x811c9dc5;
    private static final int FNV_PRIME = 0x01000193;

    private static final int[] WATERNET_FREQUENCIES = {
        262, 0, 900, 262, 900, 1250, 1250, 900, 294, 900, 0, 262, 600, 277
    };
    private static final int[] WATERNET_DURATIONS = {50, 0, 8, 50, 8, 8, 8, 8, 50, 8, 50, 50, 8, 50};
    private static final int[] WATERNET_VOLUMES = {75, 100, 100, 75, 100, 100, 100, 100, 75, 100, 75, 75, 100, 75};
    private static final int[] WATERNET_FLAGS = {1, 65, 0, 1, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1};

    private static final int[] RUBIDO_FREQUENCIES = {0, 900, 900, 1000, 900, 900, 600, 600};
    private static final int[] RUBIDO_DURATIONS = {0, 8, 8, 8, 8, 8, 8, 8};
    private static final int[] RUBIDO_VOLUMES = {0, 100, 100, 100, 100, 100, 100, 100};
    private static final int[] RUBIDO_FLAGS = {0, 0, 0, 0, 0, 0, 0, 0};

    private static final int[] SLIDE_FREQUENCIES = {
        440 | (880 << 16), 880 | (440 << 16), 69 | (72 << 16), 60 | (69 << 16)
    };
    private static final int[] SLIDE_DURATIONS = {60, 60, 60, 60};
    private static final int[] SLIDE_VOLUMES = {25700, 25700, 25700, 25700};
    private static final int[] SLIDE_FLAGS = {0, 0, 0x40, 0x43};

    private static final int[] ADSR_FREQUENCIES = {440, 900, 262, 600};
    private static final int[] ADSR_DURATIONS = {
        (2 << 24) | (2 << 16) | (2 << 8) | 2,
        (1 << 24) | (3 << 16) | (5 << 8) | 7,
        (4 << 24) | (2 << 16) | (6 << 8) | 3,
        (3 << 24) | (4 << 16) | (5 << 8) | 6
    };
    private static final int[] ADSR_VOLUMES = {(100 << 8) | 80, (70 << 8) | 50, (100 << 8) | 75, (90 << 8) | 60};
    private static final int[] ADSR_FLAGS = {0, 1, 2, 3};

    private static int sink; // NOPMD -- The observable sink prevents benchmark dead-code elimination.

    private PhoneMePcmBench() {}

    /** Runs this verification entry point. */
    public static void main(String[] arguments) {
        if (arguments.length != 3) {
            throw new IllegalArgumentException("usage: workload cycles sample");
        }
        String workload = arguments[0];
        int cycles = Integer.parseInt(arguments[1]);
        int sample = Integer.parseInt(arguments[2]);
        if (cycles <= 0 || sample < 0) {
            throw new IllegalArgumentException("cycles must be positive and sample non-negative");
        }

        int[] frequencies;
        int[] durations;
        int[] volumes;
        int[] flags;
        if ("waternet".equals(workload)) {
            frequencies = WATERNET_FREQUENCIES;
            durations = WATERNET_DURATIONS;
            volumes = WATERNET_VOLUMES;
            flags = WATERNET_FLAGS;
        } else if ("rubido".equals(workload)) {
            frequencies = RUBIDO_FREQUENCIES;
            durations = RUBIDO_DURATIONS;
            volumes = RUBIDO_VOLUMES;
            flags = RUBIDO_FLAGS;
        } else if ("slide".equals(workload)) {
            frequencies = SLIDE_FREQUENCIES;
            durations = SLIDE_DURATIONS;
            volumes = SLIDE_VOLUMES;
            flags = SLIDE_FLAGS;
        } else if ("adsr".equals(workload)) {
            frequencies = ADSR_FREQUENCIES;
            durations = ADSR_DURATIONS;
            volumes = ADSR_VOLUMES;
            flags = ADSR_FLAGS;
        } else {
            throw new IllegalArgumentException("unknown workload: " + workload);
        }

        long bytes = 0;
        int calls = 0;
        int timedGuard = 0;
        long started = System.currentTimeMillis();
        int cycle;
        for (cycle = 0; cycle < cycles; cycle++) {
            int index;
            for (index = 0; index < frequencies.length; index++) {
                byte[] wav = Wasm4Pcm.synthesize(frequencies[index], durations[index], volumes[index], flags[index]);
                calls++;
                if (wav == null) {
                    timedGuard = timedGuard * 31 + 1;
                } else {
                    bytes += wav.length;
                    timedGuard = timedGuard * 31 + wav.length;
                }
            }
        }
        long elapsed = System.currentTimeMillis() - started;
        int hash = hashSequence(frequencies, durations, volumes, flags);
        sink = hash ^ (int) bytes ^ calls ^ timedGuard ^ sample;
        System.out.println("pcm-bench:pass workload="
                + workload
                + " cycles="
                + cycles
                + " calls="
                + calls
                + " bytes="
                + bytes
                + " output-fnv1a="
                + hex8(hash)
                + " wall-ms="
                + elapsed
                + " us-per-sequence="
                + (elapsed * 1000L / cycles)
                + " sink="
                + sink);
    }

    private static int hashSequence(int[] frequencies, int[] durations, int[] volumes, int[] flags) {
        int hash = FNV_OFFSET;
        int index;
        for (index = 0; index < frequencies.length; index++) {
            byte[] wav = Wasm4Pcm.synthesize(frequencies[index], durations[index], volumes[index], flags[index]);
            if (wav == null) {
                hash ^= 0xff;
                hash *= FNV_PRIME;
            } else {
                int byteIndex;
                for (byteIndex = 0; byteIndex < wav.length; byteIndex++) {
                    hash ^= wav[byteIndex] & 0xff;
                    hash *= FNV_PRIME;
                }
            }
        }
        return hash;
    }

    private static String hex8(int value) {
        String text = Integer.toHexString(value);
        StringBuffer result = new StringBuffer(8);
        int index;
        for (index = text.length(); index < 8; index++) {
            result.append('0');
        }
        result.append(text);
        return result.toString();
    }
}
