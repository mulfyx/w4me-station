package w4me.runtime.audio;

import javax.microedition.media.Manager;

public final class MmapiToneBackend
        implements AudioBackend, AudioControl, AudioBackendStatus {
    private boolean available = true;

    public void submitTone(int frequency, int duration, int volume, int flags) {
        int startFrequency = decodeFrequency(frequency & 0xffff, (flags & 0x40) != 0);
        int totalFrames = (duration & 0xff)
                + ((duration >>> 8) & 0xff)
                + ((duration >>> 16) & 0xff)
                + ((duration >>> 24) & 0xff);
        int sustainVolume = clamp(volume & 0xff, 0, 100);
        int peakVolume = (volume >>> 8) & 0xff;
        if (peakVolume == 0) {
            peakVolume = 100;
        }
        peakVolume = clamp(peakVolume, 0, 100);
        int attack = (duration >>> 24) & 0xff;
        int decay = (duration >>> 16) & 0xff;
        int initialVolume = attack > 0 || decay > 0 ? peakVolume : sustainVolume;
        if (!available
                || startFrequency <= 0
                || totalFrames <= 0
                || initialVolume <= 0) {
            return;
        }
        int note = frequencyToMidi(startFrequency);
        try {
            Manager.playTone(note, totalFrames * 1000 / 60, initialVolume);
        } catch (Throwable unavailable) {
            available = false;
        }
    }

    public void tick() {}

    public void close() {}

    public void silence() {
        if (!available) {
            return;
        }
        try {
            // MIDP exposes no handle for Manager.playTone. On the common
            // single-tone implementations this replaces the active tone.
            Manager.playTone(0, 1, 0);
        } catch (Throwable unavailable) {
            available = false;
        }
    }

    public int volumeCapability() {
        return available ? VOLUME_CONTINUOUS : SILENT;
    }

    public String grade() {
        return available ? "D-playTone" : "E-silent";
    }

    public String activeProfileName() {
        return available
                ? AudioBackends.PROFILE_TONE
                : AudioBackends.PROFILE_SILENT;
    }

    public String fallbackReason() {
        return available ? null : "Manager.playTone failed";
    }

    private int frequencyToMidi(int frequency) {
        double semitones = 12.0 * CldcMath.logBase2((double) frequency / 440.0);
        int note = (int) Math.floor(69.0 + semitones + 0.5);
        return clamp(note, 0, 127);
    }

    private int decodeFrequency(int encoded, boolean noteMode) {
        if (encoded == 0 || !noteMode) {
            return encoded;
        }
        int note = encoded & 0xff;
        int bend = (encoded >>> 8) & 0xff;
        double semitones = (double) note - 69.0 + (double) bend / 256.0;
        return (int) Math.floor(440.0 * CldcMath.powerOfTwo(semitones / 12.0) + 0.5);
    }

    private int clamp(int value, int minimum, int maximum) {
        if (value < minimum) {
            return minimum;
        }
        if (value > maximum) {
            return maximum;
        }
        return value;
    }
}
