package w4me.runtime.audio;

import java.security.MessageDigest;
import java.util.Random;

/** Byte-exact comparison of production PCM synthesis against the frozen scalar implementation. */
public final class Wasm4PcmDifferentialSmoke {
    private static final int SAMPLE_RATE = 8000;
    private static final int WAV_HEADER_SIZE = 44;
    private static int cases;
    private static long comparedBytes;

    private Wasm4PcmDifferentialSmoke() {}

    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        int[] frequencies = {
            0,
            1,
            440,
            440 | (880 << 16),
            880 | (440 << 16),
            1 | (65535 << 16),
            65535 | (1 << 16),
            69,
            60 | (72 << 16),
            0xffff | (0xffff << 16)
        };
        int[] durations = {0, 1, 2, 60, 255, (1 << 24) | 1, (2 << 24) | (2 << 16) | (2 << 8) | 2};
        int[] volumes = {0, 1, 100, 101, 255, 100 << 8, (100 << 8) | 80, 0xffff};
        int[] flags = {0, 1, 2, 3, 4, 8, 12, 0x10, 0x20, 0x30, 0x40, 0x43, 0x4c, 0x70};

        int frequencyIndex;
        int durationIndex;
        int volumeIndex;
        int flagIndex;
        for (frequencyIndex = 0; frequencyIndex < frequencies.length; frequencyIndex++) {
            for (durationIndex = 0; durationIndex < durations.length; durationIndex++) {
                for (volumeIndex = 0; volumeIndex < volumes.length; volumeIndex++) {
                    for (flagIndex = 0; flagIndex < flags.length; flagIndex++) {
                        check(
                                frequencies[frequencyIndex],
                                durations[durationIndex],
                                volumes[volumeIndex],
                                flags[flagIndex]);
                    }
                }
            }
        }

        int maximumDuration = (255 << 24) | (255 << 16) | (255 << 8) | 255;
        check(1, maximumDuration, (100 << 8) | 80, 0);
        check(440 | (880 << 16), maximumDuration, (100 << 8) | 80, 0x10);
        check(880 | (440 << 16), maximumDuration, (100 << 8) | 80, 0x20);
        check(69 | (60 << 16), maximumDuration, (100 << 8) | 80, 0x40);
        check(60 | (69 << 16), maximumDuration, (100 << 8) | 80, 0x43);

        Random random = new Random(0x4e4a4954303130L);
        int randomCase;
        for (randomCase = 0; randomCase < 4096; randomCase++) {
            int frequency = random.nextInt();
            int duration = random.nextInt(9)
                    | (random.nextInt(9) << 8)
                    | (random.nextInt(9) << 16)
                    | (random.nextInt(9) << 24);
            int volume = random.nextInt(65536);
            int flag = random.nextInt(128);
            check(frequency, duration, volume, flag);
        }

        assertEquals("case count", 11941, cases);
        assertEquals("compared bytes", 52525193L, comparedBytes);
        checkFixture(
                "waternet-262",
                262,
                50,
                75,
                1,
                6711,
                "d6dc04c5bc7b1aa9c793c080ceab0898c8987521eeaca982969b9c75b90f2d44");
        checkFixture(
                "rubido-900", 900, 8, 100, 0, 1111, "9ad2332010bba68eb0327810a9207553d733bb7779a5bab615d4092095991555");
        checkFixture(
                "slide-up",
                440 | (880 << 16),
                60,
                25700,
                0,
                8044,
                "cfcc05ec2bb21fbf502b1d455b43053ae5e43a72736c77fc5e59b14d926a86b9");
        checkFixture(
                "slide-down",
                880 | (440 << 16),
                60,
                25700,
                0,
                8044,
                "dc4e708dfd6d7d62f8ad8b4558a4cf88bfdab789c11d75f79875f284d8728e76");
        checkFixture(
                "adsr",
                440,
                (2 << 24) | (2 << 16) | (2 << 8) | 2,
                (100 << 8) | 80,
                0,
                1111,
                "fa1bd7378969a53908cc1d294c6aa74e7f88c351c819f5a35fd21025d21fff95");
        checkFixture(
                "note-bend-slide",
                69 | (128 << 8) | ((72 | (64 << 8)) << 16),
                (3 << 24) | (4 << 16) | (5 << 8) | 6,
                (100 << 8) | 80,
                0x40,
                2444,
                "cae99bc6534a107144b6fce99f04358353c16c8d9496445548ccbcf3a2627c3f");
        checkFixture(
                "maximum",
                1 | (65535 << 16),
                maximumDuration,
                (100 << 8) | 80,
                0x20,
                272044,
                "eebdb0b1dd1ad9f674ce644e023e04f9b92ca3a6ce2062b6526e4bb4380378cc");

        System.out.println("PASS pcm-differential cases=" + cases + " compared-bytes=" + comparedBytes + " fixtures=7");
    }

    private static void check(int frequency, int duration, int volume, int flags) {
        byte[] expected = referenceSynthesize(frequency, duration, volume, flags);
        byte[] actual = Wasm4Pcm.synthesize(frequency, duration, volume, flags);
        if (expected == null || actual == null) {
            if (expected != actual) { // NOPMD -- Intentional identity-token comparison.
                fail(-1, frequency, duration, volume, flags);
            }
        } else {
            if (expected.length != actual.length) {
                fail(-2, frequency, duration, volume, flags);
            }
            int index;
            for (index = 0; index < expected.length; index++) {
                if (expected[index] != actual[index]) {
                    fail(index, frequency, duration, volume, flags);
                }
            }
            comparedBytes += expected.length;
        }
        cases++;
    }

    private static void checkFixture(
            String label, int frequency, int duration, int volume, int flags, int expectedLength, String expectedSha256)
            throws Exception {
        byte[] wav = Wasm4Pcm.synthesize(frequency, duration, volume, flags);
        assertEquals(label + " length", expectedLength, wav.length);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(wav);
        StringBuffer value = new StringBuffer(64);
        int index;
        for (index = 0; index < hash.length; index++) {
            int item = hash[index] & 0xff;
            if (item < 16) {
                value.append('0');
            }
            value.append(Integer.toHexString(item));
        }
        if (!expectedSha256.equals(value.toString())) {
            throw new AssertionError(label + " SHA-256: expected " + expectedSha256 + ", got " + value);
        }
    }

    /*
     * Frozen pre-NJIT-010 scalar implementation. Keep this independent of
     * production loop helpers so a fast-path edit cannot update its oracle.
     */
    private static byte[] referenceSynthesize(int frequency, int duration, int volume, int flags) {
        int sustain = duration & 0xff;
        int release = (duration >>> 8) & 0xff;
        int decay = (duration >>> 16) & 0xff;
        int attack = (duration >>> 24) & 0xff;
        final int totalFrames = attack + decay + sustain + release;
        final int sustainVolume = referenceClamp(volume & 0xff, 0, 100);
        int peakVolume = (volume >>> 8) & 0xff;
        if (peakVolume == 0) {
            peakVolume = 100;
        }
        peakVolume = referenceClamp(peakVolume, 0, 100);

        boolean noteMode = (flags & 0x40) != 0;
        int startFrequency = referenceDecodeFrequency(frequency & 0xffff, noteMode);
        int endFrequency = referenceDecodeFrequency((frequency >>> 16) & 0xffff, noteMode);
        if (endFrequency == 0) {
            endFrequency = startFrequency;
        }
        boolean peakAudible = peakVolume > 0 && (attack > 0 || decay > 0);
        boolean sustainAudible = sustainVolume > 0 && (sustain > 0 || release > 0);
        if (totalFrames == 0 || startFrequency <= 0 || (!peakAudible && !sustainAudible)) {
            return null; // NOPMD -- Null is the established no-result sentinel and avoids a CLDC heap allocation.
        }

        int pan = (flags >>> 4) & 3;
        int channels = pan == 1 || pan == 2 ? 2 : 1;
        int sampleCount = (totalFrames * SAMPLE_RATE + 59) / 60;
        int dataLength = sampleCount * channels;
        byte[] wav = new byte[WAV_HEADER_SIZE + dataLength];
        referenceWriteWavHeader(wav, dataLength, channels);

        int channel = flags & 3;
        int mode = (flags >>> 2) & 3;
        double duty;
        if (mode == 0) {
            duty = 0.125;
        } else if (mode == 1) {
            duty = 0.25;
        } else if (mode == 2) {
            duty = 0.5;
        } else {
            duty = 0.75;
        }
        double phase = 0.0;
        int noise = 0x7fff ^ (frequency & 0x7fff);
        int sample;
        for (sample = 0; sample < sampleCount; sample++) {
            int elapsedFrame = sample * 60 / SAMPLE_RATE;
            int currentVolume =
                    referenceEnvelopeVolume(elapsedFrame, attack, decay, sustain, release, sustainVolume, peakVolume);
            int currentFrequency =
                    startFrequency + (int) ((long) (endFrequency - startFrequency) * sample / sampleCount);
            double wave;
            phase += (double) currentFrequency / SAMPLE_RATE;
            if (channel == 3) {
                while (phase >= 1.0) {
                    int feedback = (noise ^ (noise >>> 1)) & 1;
                    noise = (noise >>> 1) | (feedback << 14);
                    phase -= 1.0;
                }
                wave = (noise & 1) == 0 ? -1.0 : 1.0;
            } else {
                phase -= (int) phase;
                if (channel == 2) {
                    wave = 1.0 - 4.0 * Math.abs(phase - 0.5);
                } else {
                    wave = phase < duty ? 1.0 : -1.0;
                }
            }
            int pcm = referenceClamp(128 + (int) (wave * currentVolume * 127.0 / 100.0), 0, 255);
            int offset = WAV_HEADER_SIZE + sample * channels;
            if (pan == 1) {
                wav[offset] = (byte) pcm;
                wav[offset + 1] = (byte) 128;
            } else if (pan == 2) {
                wav[offset] = (byte) 128;
                wav[offset + 1] = (byte) pcm;
            } else {
                wav[offset] = (byte) pcm;
            }
        }
        referenceApplyEdgeRamp(wav, sampleCount, channels, attack == 0);
        return wav;
    }

    private static void referenceApplyEdgeRamp(byte[] wav, int sampleCount, int channels, boolean rampStart) {
        int rampSamples = SAMPLE_RATE / 1000;
        if (rampSamples * 2 > sampleCount) {
            rampSamples = sampleCount / 2;
        }
        if (rampSamples < 2) {
            int channel;
            for (channel = 0; channel < channels; channel++) {
                wav[WAV_HEADER_SIZE + channel] = (byte) 128;
                wav[WAV_HEADER_SIZE + (sampleCount - 1) * channels + channel] = (byte) 128;
            }
            return;
        }

        int sample;
        for (sample = 0; sample < rampSamples; sample++) {
            int endSample = sampleCount - rampSamples + sample;
            int channel;
            for (channel = 0; channel < channels; channel++) {
                if (rampStart) {
                    int startOffset = WAV_HEADER_SIZE + sample * channels + channel;
                    wav[startOffset] = (byte) (128 + (((wav[startOffset] & 0xff) - 128) * sample >> 3));
                }
                int endOffset = WAV_HEADER_SIZE + endSample * channels + channel;
                wav[endOffset] = (byte) (128 + (((wav[endOffset] & 0xff) - 128) * (rampSamples - 1 - sample) >> 3));
            }
        }
    }

    private static int referenceEnvelopeVolume(
            int elapsed, int attack, int decay, int sustain, int release, int sustainVolume, int peakVolume) {
        if (elapsed < attack) {
            return attack == 0 ? peakVolume : peakVolume * elapsed / attack;
        }
        elapsed -= attack;
        if (elapsed < decay) {
            return peakVolume + (sustainVolume - peakVolume) * elapsed / decay;
        }
        elapsed -= decay;
        if (elapsed < sustain) {
            return sustainVolume;
        }
        elapsed -= sustain;
        if (elapsed < release) {
            return release == 0 ? 0 : sustainVolume * (release - elapsed) / release;
        }
        return 0;
    }

    private static int referenceDecodeFrequency(int encoded, boolean noteMode) {
        if (encoded == 0 || !noteMode) {
            return encoded;
        }
        int note = encoded & 0xff;
        int bend = (encoded >>> 8) & 0xff;
        double semitones = (double) note - 69.0 + (double) bend / 256.0;
        return (int) Math.floor(440.0 * CldcMath.powerOfTwo(semitones / 12.0) + 0.5);
    }

    private static void referenceWriteWavHeader(byte[] wav, int dataLength, int channels) {
        final int bytesPerSecond = SAMPLE_RATE * channels;
        referenceWriteAscii(wav, 0, "RIFF");
        referenceWriteIntLe(wav, 4, 36 + dataLength);
        referenceWriteAscii(wav, 8, "WAVE");
        referenceWriteAscii(wav, 12, "fmt ");
        referenceWriteIntLe(wav, 16, 16);
        referenceWriteShortLe(wav, 20, 1);
        referenceWriteShortLe(wav, 22, channels);
        referenceWriteIntLe(wav, 24, SAMPLE_RATE);
        referenceWriteIntLe(wav, 28, bytesPerSecond);
        referenceWriteShortLe(wav, 32, channels);
        referenceWriteShortLe(wav, 34, 8);
        referenceWriteAscii(wav, 36, "data");
        referenceWriteIntLe(wav, 40, dataLength);
    }

    private static void referenceWriteAscii(byte[] data, int offset, String value) {
        int index;
        for (index = 0; index < value.length(); index++) {
            data[offset + index] = (byte) value.charAt(index);
        }
    }

    private static void referenceWriteShortLe(byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >>> 8);
    }

    private static void referenceWriteIntLe(byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >>> 8);
        data[offset + 2] = (byte) (value >>> 16);
        data[offset + 3] = (byte) (value >>> 24);
    }

    private static int referenceClamp(int value, int minimum, int maximum) {
        if (value < minimum) {
            return minimum;
        }
        if (value > maximum) {
            return maximum;
        }
        return value;
    }

    private static void fail(int index, int frequency, int duration, int volume, int flags) {
        throw new AssertionError("PCM mismatch index="
                + index
                + " frequency="
                + frequency
                + " duration="
                + duration
                + " volume="
                + volume
                + " flags="
                + flags);
    }

    private static void assertEquals(String label, long expected, long actual) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
