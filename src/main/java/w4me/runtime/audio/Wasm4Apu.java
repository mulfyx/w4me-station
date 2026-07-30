package w4me.runtime.audio;

/** Provides the WASM 4 apu implementation. */
public final class Wasm4Apu {
    private static final int CHANNEL_COUNT = 4;
    private static final int CHANNEL_STATE_FIELDS = 11;
    private static final int SCALAR_STATE_FIELDS = 5;
    private static final int SNAPSHOT_LENGTH = CHANNEL_COUNT * CHANNEL_STATE_FIELDS + SCALAR_STATE_FIELDS;
    private final AudioBackend backend;
    private final AudioControl control;
    private final int[] frequencyStart = new int[4];
    private final int[] frequencyEnd = new int[4];
    private final int[] totalFrames = new int[4];
    private final int[] elapsedFrames = new int[4];
    private final int[] attackFrames = new int[4];
    private final int[] decayFrames = new int[4];
    private final int[] sustainFrames = new int[4];
    private final int[] releaseFrames = new int[4];
    private final int[] sustainVolume = new int[4];
    private final int[] peakVolume = new int[4];
    private final int[] channelFlags = new int[4];
    private boolean diagnostic;
    private int toneEventCount;
    private int lastFrequency;
    private int lastDuration;
    private int lastVolume;
    private int lastFlags;
    private int masterGain = 100;
    private boolean muted;
    private boolean suspended;

    /** Creates a new WASM 4 apu. */
    public Wasm4Apu(AudioBackend backend) {
        if (backend == null) {
            throw new IllegalArgumentException("audio backend is required");
        }
        this.backend = backend;
        control = backend instanceof AudioControl ? (AudioControl) backend : null;
    }

    /** Updates the diagnostic. */
    public void setDiagnostic(boolean diagnostic) {
        this.diagnostic = diagnostic;
        if (backend instanceof AudioDiagnostics) {
            ((AudioDiagnostics) backend).setAudioDiagnostics(diagnostic);
        }
    }

    /** Performs the tone operation. */
    public synchronized void tone(int frequency, int duration, int volume, int flags) {
        if (muted) {
            return;
        }
        int channel = flags & 3;
        int start = decodeFrequency(frequency & 0xffff, (flags & 0x40) != 0);
        final int end = decodeFrequency((frequency >>> 16) & 0xffff, (flags & 0x40) != 0);
        int sustain = duration & 0xff;
        int release = (duration >>> 8) & 0xff;
        int decay = (duration >>> 16) & 0xff;
        int attack = (duration >>> 24) & 0xff;
        final int frames = sustain + release + decay + attack;
        final int sustainLevel = clamp(volume & 0xff, 0, 100);
        int peakLevel = (volume >>> 8) & 0xff;
        if (peakLevel == 0) {
            peakLevel = 100;
        }
        peakLevel = clamp(peakLevel, 0, 100);

        frequencyStart[channel] = start;
        frequencyEnd[channel] = end;
        totalFrames[channel] = frames;
        elapsedFrames[channel] = 0;
        attackFrames[channel] = attack;
        decayFrames[channel] = decay;
        sustainFrames[channel] = sustain;
        releaseFrames[channel] = release;
        sustainVolume[channel] = sustainLevel;
        peakVolume[channel] = peakLevel;
        channelFlags[channel] = flags;
        toneEventCount++;
        lastFrequency = frequency;
        lastDuration = duration;
        lastVolume = volume;
        lastFlags = flags;

        int gain = effectiveGain();
        if (gain > 0) {
            backend.submitTone(frequency, duration, scalePackedVolume(volume, gain), flags);
        }
        if (diagnostic) {
            System.out.println("W4ME_TONE frequency="
                    + unsignedString(frequency)
                    + " duration="
                    + unsignedString(duration)
                    + " volume="
                    + unsignedString(volume)
                    + " flags="
                    + unsignedString(flags)
                    + " backend="
                    + backend.grade());
        }
    }

    /** Performs the tick operation. */
    public void tick() {
        if (muted) {
            return;
        }
        int channel;
        for (channel = 0; channel < 4; channel++) {
            if (elapsedFrames[channel] < totalFrames[channel]) {
                elapsedFrames[channel]++;
            }
        }
        backend.tick();
    }

    /** Performs the close operation. */
    public void close() {
        backend.close();
    }

    /** Updates the master gain. */
    public synchronized void setMasterGain(int gain) {
        if (gain < 0 || gain > 100) {
            throw new IllegalArgumentException("master gain is out of range");
        }
        if (masterGain == gain) {
            return;
        }
        masterGain = gain;
        silence();
    }

    /** Performs the master gain operation. */
    public synchronized int masterGain() {
        return masterGain;
    }

    /** Updates the muted. */
    public synchronized void setMuted(boolean value) {
        if (muted == value) {
            return;
        }
        muted = value;
        if (value) {
            silence();
            clearActiveChannels();
        }
    }

    /** Performs the muted operation. */
    public synchronized boolean muted() {
        return muted;
    }

    /** Updates the suspended. */
    public synchronized void setSuspended(boolean value) {
        suspended = value;
        if (value) {
            silence();
        }
    }

    /** Performs the suspend output operation. */
    public synchronized void suspendOutput() {
        suspended = true;
        silence();
    }

    /** Performs the resume output operation. */
    public synchronized void resumeOutput() {
        suspended = false;
    }

    /** Performs the suspended operation. */
    public synchronized boolean suspended() {
        return suspended;
    }

    /** Performs the volume capability operation. */
    public int volumeCapability() {
        return control == null ? AudioControl.VOLUME_CONTINUOUS : control.volumeCapability();
    }

    /** Performs the grade operation. */
    public String grade() {
        return backend.grade();
    }

    /** Performs the active profile name operation. */
    public String activeProfileName() {
        return AudioBackends.activeProfileName(backend);
    }

    /** Performs the audio fallback reason operation. */
    public String audioFallbackReason() {
        return AudioBackends.fallbackReason(backend);
    }

    /** Performs the tone event count operation. */
    public int toneEventCount() {
        return toneEventCount;
    }

    /** Performs the last frequency operation. */
    public int lastFrequency() {
        return lastFrequency;
    }

    /** Performs the last duration operation. */
    public int lastDuration() {
        return lastDuration;
    }

    /** Performs the last volume operation. */
    public int lastVolume() {
        return lastVolume;
    }

    /** Performs the last flags operation. */
    public int lastFlags() {
        return lastFlags;
    }

    /** Captures cartridge-owned channel, envelope, and diagnostic tone state. */
    public synchronized int[] snapshotState() {
        int[] state = new int[SNAPSHOT_LENGTH];
        int offset = 0;
        offset = copyToState(frequencyStart, state, offset);
        offset = copyToState(frequencyEnd, state, offset);
        offset = copyToState(totalFrames, state, offset);
        offset = copyToState(elapsedFrames, state, offset);
        offset = copyToState(attackFrames, state, offset);
        offset = copyToState(decayFrames, state, offset);
        offset = copyToState(sustainFrames, state, offset);
        offset = copyToState(releaseFrames, state, offset);
        offset = copyToState(sustainVolume, state, offset);
        offset = copyToState(peakVolume, state, offset);
        offset = copyToState(channelFlags, state, offset);
        state[offset++] = toneEventCount; // NOPMD -- Compact Java 1.3 cursor bytecode.
        state[offset++] = lastFrequency; // NOPMD -- Compact Java 1.3 cursor bytecode.
        state[offset++] = lastDuration; // NOPMD -- Compact Java 1.3 cursor bytecode.
        state[offset++] = lastVolume; // NOPMD -- Compact Java 1.3 cursor bytecode.
        state[offset] = lastFlags;
        return state;
    }

    /** Reports whether restore state. */
    public synchronized boolean canRestoreState(int[] state) {
        return state != null && state.length == SNAPSHOT_LENGTH;
    }

    /**
     * Restores cartridge-owned APU state without changing user mute, gain, or menu suspension. An already playing
     * backend tone stays silent until the cartridge submits its next tone.
     */
    public synchronized void restoreState(int[] state) {
        if (!canRestoreState(state)) {
            throw new IllegalArgumentException("APU snapshot shape mismatch");
        }
        silence();
        int offset = 0;
        offset = copyFromState(state, offset, frequencyStart);
        offset = copyFromState(state, offset, frequencyEnd);
        offset = copyFromState(state, offset, totalFrames);
        offset = copyFromState(state, offset, elapsedFrames);
        offset = copyFromState(state, offset, attackFrames);
        offset = copyFromState(state, offset, decayFrames);
        offset = copyFromState(state, offset, sustainFrames);
        offset = copyFromState(state, offset, releaseFrames);
        offset = copyFromState(state, offset, sustainVolume);
        offset = copyFromState(state, offset, peakVolume);
        offset = copyFromState(state, offset, channelFlags);
        toneEventCount = state[offset++]; // NOPMD -- Compact Java 1.3 cursor bytecode.
        lastFrequency = state[offset++]; // NOPMD -- Compact Java 1.3 cursor bytecode.
        lastDuration = state[offset++]; // NOPMD -- Compact Java 1.3 cursor bytecode.
        lastVolume = state[offset++]; // NOPMD -- Compact Java 1.3 cursor bytecode.
        lastFlags = state[offset];
    }

    /** Performs the channel frequency operation. */
    public int channelFrequency(int channel) {
        requireChannel(channel);
        int end = frequencyEnd[channel];
        int total = totalFrames[channel];
        if (end == 0 || total == 0) {
            return frequencyStart[channel];
        }
        int elapsed = elapsedFrames[channel];
        return frequencyStart[channel] + (int) ((long) (end - frequencyStart[channel]) * elapsed / total);
    }

    /** Performs the channel volume operation. */
    public int channelVolume(int channel) {
        requireChannel(channel);
        int elapsed = elapsedFrames[channel];
        int attack = attackFrames[channel];
        if (elapsed < attack) {
            return attack == 0 ? peakVolume[channel] : peakVolume[channel] * elapsed / attack;
        }
        elapsed -= attack;
        int decay = decayFrames[channel];
        if (elapsed < decay) {
            int peak = peakVolume[channel];
            return peak + (sustainVolume[channel] - peak) * elapsed / decay;
        }
        elapsed -= decay;
        if (elapsed < sustainFrames[channel]) {
            return sustainVolume[channel];
        }
        elapsed -= sustainFrames[channel];
        int release = releaseFrames[channel];
        if (elapsed < release) {
            return release == 0 ? 0 : sustainVolume[channel] * (release - elapsed) / release;
        }
        return 0;
    }

    static int scalePackedVolume(int volume, int gain) {
        if (gain >= 100) {
            return volume;
        }
        if (gain <= 0) {
            return volume & 0xffff0000;
        }

        int sustain = volume & 0xff;
        int peak = (volume >>> 8) & 0xff;
        if (sustain > 100) {
            sustain = 100;
        }
        if (peak == 0 || peak > 100) {
            peak = 100;
        }
        sustain = (sustain * gain + 50) / 100;
        peak = (peak * gain + 50) / 100;
        return (volume & 0xffff0000) | (peak << 8) | sustain;
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

    private int effectiveGain() {
        return muted || suspended ? 0 : masterGain;
    }

    private int copyToState(int[] source, int[] target, int offset) {
        System.arraycopy(source, 0, target, offset, CHANNEL_COUNT);
        return offset + CHANNEL_COUNT;
    }

    private int copyFromState(int[] source, int offset, int[] target) {
        System.arraycopy(source, offset, target, 0, CHANNEL_COUNT);
        return offset + CHANNEL_COUNT;
    }

    private void silence() {
        if (control != null) {
            control.silence();
        }
    }

    private void clearActiveChannels() {
        int channel;
        for (channel = 0; channel < CHANNEL_COUNT; channel++) {
            frequencyStart[channel] = 0;
            frequencyEnd[channel] = 0;
            totalFrames[channel] = 0;
            elapsedFrames[channel] = 0;
            attackFrames[channel] = 0;
            decayFrames[channel] = 0;
            sustainFrames[channel] = 0;
            releaseFrames[channel] = 0;
            sustainVolume[channel] = 0;
            peakVolume[channel] = 0;
            channelFlags[channel] = 0;
        }
    }

    private void requireChannel(int channel) {
        if (channel < 0 || channel >= 4) {
            throw new IllegalArgumentException("audio channel is out of range");
        }
    }

    private String unsignedString(int value) {
        if (value >= 0) {
            return Integer.toString(value);
        }
        long unsigned = (value & 0x7fffffffL) + 2147483648L;
        return Long.toString(unsigned);
    }
}
