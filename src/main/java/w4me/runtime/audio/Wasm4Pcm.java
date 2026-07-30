package w4me.runtime.audio;

/** Deterministic WASM-4 waveform and envelope synthesis into an in-memory WAV. */
public final class Wasm4Pcm {
    private static final int SAMPLE_RATE = 8000;
    private static final int WAV_HEADER_SIZE = 44;
    private static final int DECLICK_SAMPLES = SAMPLE_RATE / 1000;

    private Wasm4Pcm() {}

    /** Performs the synthesize operation. */
    public static byte[] synthesize(int frequency, int duration, int volume, int flags) {
        int sustain = duration & 0xff;
        int release = (duration >>> 8) & 0xff;
        int decay = (duration >>> 16) & 0xff;
        int attack = (duration >>> 24) & 0xff;
        final int totalFrames = attack + decay + sustain + release;
        final int sustainVolume = clamp(volume & 0xff, 0, 100);
        int peakVolume = (volume >>> 8) & 0xff;
        if (peakVolume == 0) {
            peakVolume = 100;
        }
        peakVolume = clamp(peakVolume, 0, 100);

        boolean noteMode = (flags & 0x40) != 0;
        int startFrequency = decodeFrequency(frequency & 0xffff, noteMode);
        int endFrequency = decodeFrequency((frequency >>> 16) & 0xffff, noteMode);
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
        writeWavHeader(wav, dataLength, channels);

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
        if (endFrequency == startFrequency && attack == 0 && decay == 0 && release == 0) {
            synthesizeConstantTone(
                    wav, sampleCount, channels, pan, channel, duty, startFrequency, sustainVolume, frequency);
            applyEdgeRamp(wav, sampleCount, channels, true);
            return wav;
        }
        double phase = 0.0;
        int noise = 0x7fff ^ (frequency & 0x7fff);
        int lastElapsedFrame = -1;
        int currentVolume = 0;
        int sample;
        for (sample = 0; sample < sampleCount; sample++) {
            int elapsedFrame = sample * 60 / SAMPLE_RATE;
            if (elapsedFrame != lastElapsedFrame) {
                currentVolume =
                        envelopeVolume(elapsedFrame, attack, decay, sustain, release, sustainVolume, peakVolume);
                lastElapsedFrame = elapsedFrame;
            }
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
            int pcm = clamp(128 + (int) (wave * currentVolume * 127.0 / 100.0), 0, 255);
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
        applyEdgeRamp(wav, sampleCount, channels, attack == 0);
        return wav;
    }

    /**
     * Removes the discontinuity between unsigned 8-bit silence and a finite MMAPI WAV without extending the WASM-4
     * tone.
     *
     * <p>The one-millisecond ramps live inside the requested duration. A real attack already starts at silence, while
     * every finite WAV must return to silence before its Player reaches end-of-media.
     */
    private static void applyEdgeRamp(byte[] wav, int sampleCount, int channels, boolean rampStart) {
        int rampSamples = DECLICK_SAMPLES;
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
                    int startValue = wav[startOffset] & 0xff;
                    wav[startOffset] = (byte) (128 + (((startValue - 128) * sample) >> 3));
                }
                int endOffset = WAV_HEADER_SIZE + endSample * channels + channel;
                int endValue = wav[endOffset] & 0xff;
                wav[endOffset] = (byte) (128 + (((endValue - 128) * (rampSamples - 1 - sample)) >> 3));
            }
        }
    }

    private static void synthesizeConstantTone(
            byte[] wav,
            int sampleCount,
            int channels,
            int pan,
            int channel,
            double duty,
            int currentFrequency,
            int currentVolume,
            int encodedFrequency) {
        if (channels == 1 && channel != 2 && channel != 3) {
            synthesizeConstantPulseMono(wav, sampleCount, duty, currentFrequency, currentVolume);
            return;
        }
        double phase = 0.0;
        double phaseStep = (double) currentFrequency / SAMPLE_RATE;
        int noise = 0x7fff ^ (encodedFrequency & 0x7fff);
        int sample;
        for (sample = 0; sample < sampleCount; sample++) {
            double wave;
            phase += phaseStep;
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
            int pcm = clamp(128 + (int) (wave * currentVolume * 127.0 / 100.0), 0, 255);
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
    }

    private static void synthesizeConstantPulseMono(
            byte[] wav, int sampleCount, double duty, int currentFrequency, int currentVolume) {
        double phase = 0.0;
        double phaseStep = (double) currentFrequency / SAMPLE_RATE;
        int pcmHigh = 128 + (int) ((double) currentVolume * 127.0 / 100.0);
        int pcmLow = 128 + (int) ((double) -currentVolume * 127.0 / 100.0);
        int sample;
        for (sample = 0; sample < sampleCount; sample++) {
            phase += phaseStep;
            phase -= (int) phase;
            wav[WAV_HEADER_SIZE + sample] = (byte) (phase < duty ? pcmHigh : pcmLow);
        }
    }

    private static int envelopeVolume(
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

    private static int decodeFrequency(int encoded, boolean noteMode) {
        if (encoded == 0 || !noteMode) {
            return encoded;
        }
        int note = encoded & 0xff;
        int bend = (encoded >>> 8) & 0xff;
        double semitones = (double) note - 69.0 + (double) bend / 256.0;
        return (int) Math.floor(440.0 * CldcMath.powerOfTwo(semitones / 12.0) + 0.5);
    }

    private static void writeWavHeader(byte[] wav, int dataLength, int channels) {
        final int bytesPerSecond = SAMPLE_RATE * channels;
        writeAscii(wav, 0, "RIFF");
        writeIntLe(wav, 4, 36 + dataLength);
        writeAscii(wav, 8, "WAVE");
        writeAscii(wav, 12, "fmt ");
        writeIntLe(wav, 16, 16);
        writeShortLe(wav, 20, 1);
        writeShortLe(wav, 22, channels);
        writeIntLe(wav, 24, SAMPLE_RATE);
        writeIntLe(wav, 28, bytesPerSecond);
        writeShortLe(wav, 32, channels);
        writeShortLe(wav, 34, 8);
        writeAscii(wav, 36, "data");
        writeIntLe(wav, 40, dataLength);
    }

    private static void writeAscii(byte[] data, int offset, String value) {
        int index;
        for (index = 0; index < value.length(); index++) {
            data[offset + index] = (byte) value.charAt(index);
        }
    }

    private static void writeShortLe(byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >>> 8);
    }

    private static void writeIntLe(byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >>> 8);
        data[offset + 2] = (byte) (value >>> 16);
        data[offset + 3] = (byte) (value >>> 24);
    }

    private static int clamp(int value, int minimum, int maximum) {
        if (value < minimum) {
            return minimum;
        }
        if (value > maximum) {
            return maximum;
        }
        return value;
    }
}
