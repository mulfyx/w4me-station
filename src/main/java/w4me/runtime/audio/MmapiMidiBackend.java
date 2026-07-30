package w4me.runtime.audio;

import java.io.ByteArrayInputStream;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.media.control.VolumeControl;

/**
 * Approximate four-channel WASM-4 audio as one Standard MIDI File Player.
 *
 * <p>Some physical MMAPI implementations expose {@code device://midi} and accept MIDIControl events while silently
 * dropping them. A data-backed MIDI Player uses the ordinary media playback path instead. Keeping all logical channels
 * in one SMF also avoids requiring concurrent Player mixing.
 */
public final class MmapiMidiBackend implements AudioBackend, AudioControl, AudioBackendStatus, AudioDiagnostics {
    private static final int CHANNEL_COUNT = 4;
    private static final int MIDI_PERCUSSION_CHANNEL = 9;
    private static final int NOTE_ON = 0x90;
    private static final int CONTROL_CHANGE = 0xb0;
    private static final int PROGRAM_CHANGE = 0xc0;
    private static final int CONTROL_CHANNEL_VOLUME = 7;
    private static final int CONTROL_PAN = 10;
    private static final int SYNTH_SQUARE_LEAD = 80;
    private static final int TICKS_PER_QUARTER = 60;

    private static final int[] MIDI_FREQUENCIES = {
        8, 9, 9, 10, 10, 11, 12, 12, 13, 14, 15, 15, 16, 17, 18, 19, 21, 22, 23, 24, 26, 28, 29, 31, 33, 35, 37, 39, 41,
        44, 46, 49, 52, 55, 58, 62, 65, 69, 73, 78, 82, 87, 92, 98, 104, 110, 117, 123, 131, 139, 147, 156, 165, 175,
        185, 196, 208, 220, 233, 247, 262, 277, 294, 311, 330, 349, 370, 392, 415, 440, 466, 494, 523, 554, 587, 622,
        659, 698, 740, 784, 831, 880, 932, 988, 1047, 1109, 1175, 1245, 1319, 1397, 1480, 1568, 1661, 1760, 1865, 1976,
        2093, 2217, 2349, 2489, 2637, 2794, 2960, 3136, 3322, 3520, 3729, 3951, 4186, 4435, 4699, 4978, 5274, 5588,
        5920, 6272, 6645, 7040, 7459, 7902, 8372, 8870, 9397, 9956, 10548, 11175, 11840, 12544
    };

    private final MidiPlayerFactory playerFactory;
    private final MmapiToneBackend fallback = new MmapiToneBackend();
    private final int[] frequencyStart = new int[CHANNEL_COUNT];
    private final int[] frequencyEnd = new int[CHANNEL_COUNT];
    private final int[] totalFrames = new int[CHANNEL_COUNT];
    private final int[] elapsedFrames = new int[CHANNEL_COUNT];
    private final int[] playbackVolumes = new int[CHANNEL_COUNT];
    private final int[] channelFlags = new int[CHANNEL_COUNT];
    private final boolean[] active = new boolean[CHANNEL_COUNT];
    private MidiPlayback playback;
    private boolean midiAvailable;
    private boolean midiStarted;
    private boolean dirty;
    private String midiFailureReason;
    private volatile boolean diagnostic;

    /** Creates a new MMAPI midi backend. */
    public MmapiMidiBackend() {
        playerFactory = new MmapiMidiPlayerFactory();
        midiAvailable = true;
    }

    MmapiMidiBackend(MidiPlayerFactory playerFactory) {
        this.playerFactory = playerFactory;
        midiAvailable = playerFactory != null;
        if (!midiAvailable) {
            midiFailureReason = "MMAPI MIDI playback unavailable";
        }
    }

    /** Performs the submit tone operation. */
    public synchronized void submitTone(int frequency, int duration, int volume, int flags) {
        if (!midiAvailable) {
            fallback.submitTone(frequency, duration, volume, flags);
            return;
        }

        final int channel = flags & 3;
        int sustain = duration & 0xff;
        int release = (duration >>> 8) & 0xff;
        int decay = (duration >>> 16) & 0xff;
        int attack = (duration >>> 24) & 0xff;
        final int frames = attack + decay + sustain + release;
        boolean noteMode = (flags & 0x40) != 0;
        int start = decodeFrequency(frequency & 0xffff, noteMode);
        int end = decodeFrequency((frequency >>> 16) & 0xffff, noteMode);
        if (end == 0) {
            end = start;
        }
        int sustainVolume = clamp(volume & 0xff, 0, 100);
        int peakVolume = (volume >>> 8) & 0xff;
        if (peakVolume == 0) {
            peakVolume = 100;
        }
        peakVolume = clamp(peakVolume, 0, 100);
        final int playbackVolume =
                attack > 0 || decay > 0 ? (peakVolume > sustainVolume ? peakVolume : sustainVolume) : sustainVolume;

        frequencyStart[channel] = start;
        frequencyEnd[channel] = end;
        totalFrames[channel] = frames;
        elapsedFrames[channel] = 0;
        playbackVolumes[channel] = playbackVolume;
        channelFlags[channel] = flags;
        active[channel] = frames > 0 && start > 0 && playbackVolume > 0;
        dirty = true;
    }

    /** Performs the tick operation. */
    public synchronized void tick() {
        if (!midiAvailable) {
            fallback.tick();
            return;
        }

        if (dirty) {
            dirty = false;
            try {
                restartPlayback();
            } catch (Throwable unavailable) { // NOPMD -- Java ME API linkage fallback.
                disableMidi();
                replayActiveOnFallback();
            }
        }

        int channel;
        for (channel = 0; channel < CHANNEL_COUNT; channel++) {
            if (!active[channel]) {
                continue;
            }
            elapsedFrames[channel]++;
            if (elapsedFrames[channel] >= totalFrames[channel]) {
                active[channel] = false;
            }
        }
        fallback.tick();
    }

    /** Performs the close operation. */
    public synchronized void close() {
        closePlayback();
        fallback.close();
    }

    /** Performs the grade operation. */
    public String grade() {
        if (!midiAvailable) {
            return fallback.grade();
        }
        return midiStarted ? "C-smf4" : "C-smf4-ready";
    }

    /** Performs the active profile name operation. */
    public String activeProfileName() {
        return midiAvailable ? AudioBackends.PROFILE_MIDI : AudioBackends.activeProfileName(fallback);
    }

    /** Performs the fallback reason operation. */
    public String fallbackReason() {
        if (midiAvailable) {
            return null;
        }
        String nested = AudioBackends.fallbackReason(fallback);
        return nested == null ? midiFailureReason : midiFailureReason + "; " + nested;
    }

    public void setAudioDiagnostics(boolean enabled) {
        diagnostic = enabled;
    }

    /** Performs the silence operation. */
    public synchronized void silence() {
        int channel;
        for (channel = 0; channel < CHANNEL_COUNT; channel++) {
            active[channel] = false;
            totalFrames[channel] = 0;
            elapsedFrames[channel] = 0;
        }
        dirty = false;
        closePlayback();
        fallback.silence();
    }

    /** Performs the volume capability operation. */
    public int volumeCapability() {
        return midiAvailable ? VOLUME_CONTINUOUS : fallback.volumeCapability();
    }

    int activeChannels() {
        int count = 0;
        int channel;
        for (channel = 0; channel < CHANNEL_COUNT; channel++) {
            if (active[channel]) {
                count++;
            }
        }
        return count;
    }

    private void restartPlayback() throws Exception {
        closePlayback();
        byte[] midi = buildSequence();
        if (midi == null) {
            return;
        }
        MidiPlayback replacement = playerFactory.open(midi);
        if (replacement == null) {
            throw new IllegalStateException("MMAPI MIDI player was not created");
        }
        playback = replacement;
        midiStarted = true;
    }

    private byte[] buildSequence() {
        if (!hasActiveChannels()) {
            return null; // NOPMD -- Null is the established no-result sentinel and avoids a CLDC heap allocation.
        }

        MidiWriter writer = new MidiWriter(192);
        writer.writeAscii("MThd");
        writer.writeInt(6);
        writer.writeShort(1);
        writer.writeShort(2);
        writer.writeShort(TICKS_PER_QUARTER);

        writer.writeAscii("MTrk");
        writer.writeInt(11);
        writer.writeByte(0);
        writer.writeByte(0xff);
        writer.writeByte(0x51);
        writer.writeByte(3);
        writer.writeByte(0x0f);
        writer.writeByte(0x42);
        writer.writeByte(0x40);
        writer.writeByte(0);
        writer.writeByte(0xff);
        writer.writeByte(0x2f);
        writer.writeByte(0);

        writer.writeAscii("MTrk");
        final int trackLengthOffset = writer.position();
        writer.writeInt(0);
        final int trackStart = writer.position();

        int[] notes = new int[CHANNEL_COUNT];
        int[] endTicks = new int[CHANNEL_COUNT];
        int channel;
        for (channel = 0; channel < CHANNEL_COUNT; channel++) {
            if (!active[channel]) {
                notes[channel] = -1;
                continue;
            }
            int midiChannel = midiChannel(channel);
            int frequency = currentFrequency(channel);
            int note = channel == 3 ? percussionNote(frequency) : frequencyToMidi(frequency);
            notes[channel] = note;
            endTicks[channel] = totalFrames[channel] - elapsedFrames[channel];

            if (channel != 3) {
                writer.writeVariable(0);
                writer.writeByte(PROGRAM_CHANGE | midiChannel);
                writer.writeByte(SYNTH_SQUARE_LEAD);
            }
            writer.writeVariable(0);
            writer.writeByte(CONTROL_CHANGE | midiChannel);
            writer.writeByte(CONTROL_CHANNEL_VOLUME);
            writer.writeByte(127);
            writer.writeVariable(0);
            writer.writeByte(CONTROL_CHANGE | midiChannel);
            writer.writeByte(CONTROL_PAN);
            writer.writeByte(panValue((channelFlags[channel] >>> 4) & 3));
            writer.writeVariable(0);
            writer.writeByte(NOTE_ON | midiChannel);
            writer.writeByte(note);
            writer.writeByte((playbackVolumes[channel] * 127 + 50) / 100);
        }

        boolean[] ended = new boolean[CHANNEL_COUNT];
        int previousTick = 0;
        int remaining = activeChannels();
        while (remaining > 0) {
            int nextTick = Integer.MAX_VALUE;
            for (channel = 0; channel < CHANNEL_COUNT; channel++) {
                if (notes[channel] >= 0 && !ended[channel] && endTicks[channel] < nextTick) {
                    nextTick = endTicks[channel];
                }
            }
            boolean firstAtTick = true;
            for (channel = 0; channel < CHANNEL_COUNT; channel++) {
                if (notes[channel] < 0 || ended[channel] || endTicks[channel] != nextTick) {
                    continue;
                }
                writer.writeVariable(firstAtTick ? nextTick - previousTick : 0);
                writer.writeByte(NOTE_ON | midiChannel(channel));
                writer.writeByte(notes[channel]);
                writer.writeByte(0);
                ended[channel] = true;
                remaining--;
                firstAtTick = false;
            }
            previousTick = nextTick;
        }

        writer.writeVariable(0);
        writer.writeByte(0xff);
        writer.writeByte(0x2f);
        writer.writeByte(0);
        writer.patchInt(trackLengthOffset, writer.position() - trackStart);
        return writer.toByteArray();
    }

    private int currentFrequency(int channel) {
        int start = frequencyStart[channel];
        int end = frequencyEnd[channel];
        int total = totalFrames[channel];
        if (end == start || total == 0) {
            return start;
        }
        return start + (int) ((long) (end - start) * elapsedFrames[channel] / total);
    }

    private boolean hasActiveChannels() {
        int channel;
        for (channel = 0; channel < CHANNEL_COUNT; channel++) {
            if (active[channel]) {
                return true;
            }
        }
        return false;
    }

    private void replayActiveOnFallback() {
        int channel;
        for (channel = 0; channel < CHANNEL_COUNT; channel++) {
            if (!active[channel]) {
                continue;
            }
            int remaining = totalFrames[channel] - elapsedFrames[channel];
            fallback.submitTone(
                    currentFrequency(channel),
                    packDuration(remaining),
                    playbackVolumes[channel],
                    channelFlags[channel] & ~0x40);
        }
    }

    private int packDuration(int frames) {
        int packed = 0;
        int shift = 0;
        while (frames > 0 && shift < 32) {
            int part = frames > 255 ? 255 : frames;
            packed |= part << shift;
            frames -= part;
            shift += 8;
        }
        return packed;
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

    private int frequencyToMidi(int frequency) {
        if (frequency <= MIDI_FREQUENCIES[0]) {
            return 0;
        }
        int last = MIDI_FREQUENCIES.length - 1;
        if (frequency >= MIDI_FREQUENCIES[last]) {
            return last;
        }
        int low = 0;
        int high = last;
        while (low + 1 < high) {
            int middle = (low + high) >>> 1;
            if (MIDI_FREQUENCIES[middle] <= frequency) {
                low = middle;
            } else {
                high = middle;
            }
        }
        int midpoint = (MIDI_FREQUENCIES[low] + MIDI_FREQUENCIES[high]) >>> 1;
        return frequency < midpoint ? low : high;
    }

    private int percussionNote(int frequency) {
        if (frequency < 400) {
            return 36;
        }
        if (frequency < 700) {
            return 38;
        }
        if (frequency < 1000) {
            return 42;
        }
        return 46;
    }

    private int midiChannel(int channel) {
        return channel == 3 ? MIDI_PERCUSSION_CHANNEL : channel;
    }

    private int panValue(int pan) {
        if (pan == 1) {
            return 0;
        }
        if (pan == 2) {
            return 127;
        }
        return 64;
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

    private void disableMidi() {
        midiAvailable = false;
        midiFailureReason = "MMAPI MIDI Player failed";
        dirty = false;
        closePlayback();
    }

    private void closePlayback() {
        MidiPlayback current = playback;
        playback = null;
        if (current != null) {
            try {
                current.close();
            } catch (Throwable ignored) { // NOPMD -- Java ME API linkage fallback.
                // Best effort after a replacement, failure, or MIDlet shutdown.
            }
        }
    }

    interface MidiPlayerFactory {
        MidiPlayback open(byte[] midi) throws Exception;
    }

    interface MidiPlayback {
        void close();
    }

    private final class MmapiMidiPlayerFactory implements MidiPlayerFactory {
        public MidiPlayback open(byte[] midi) throws Exception {
            Player player = null;
            String phase = "create";
            long started = diagnostic ? System.currentTimeMillis() : 0;
            long created;
            long realized;
            long prefetched;
            try {
                player = Manager.createPlayer(new ByteArrayInputStream(midi), "audio/midi");
                created = diagnostic ? System.currentTimeMillis() : 0;
                phase = "realize";
                player.realize();
                realized = diagnostic ? System.currentTimeMillis() : 0;
                phase = "prefetch";
                player.prefetch();
                prefetched = diagnostic ? System.currentTimeMillis() : 0;
                Object control = player.getControl("VolumeControl");
                if (control instanceof VolumeControl) {
                    VolumeControl volume = (VolumeControl) control;
                    volume.setMute(false);
                    volume.setLevel(100);
                }
                phase = "start";
                player.start();
                long playerStarted = diagnostic ? System.currentTimeMillis() : 0;
                if (player.getState() != Player.STARTED) {
                    throw new IllegalStateException("MMAPI MIDI player did not start");
                }
                if (diagnostic) {
                    System.out.println("W4ME_MIDI_LIFECYCLE bytes="
                            + midi.length
                            + " create-ms="
                            + (created - started)
                            + " realize-ms="
                            + (realized - created)
                            + " prefetch-ms="
                            + (prefetched - realized)
                            + " start-ms="
                            + (playerStarted - prefetched)
                            + " total-ms="
                            + (playerStarted - started));
                }
                return new MmapiMidiPlayback(player);
            } catch (Exception failure) {
                reportMidiLifecycleFailure(phase, failure);
                closePlayer(player);
                throw failure;
            } catch (Error failure) { // NOPMD -- Java 1.3 has no multi-catch syntax for the equivalent recovery
                // branches. Optional Java ME APIs and device implementations can fail with
                // linkage or VM errors.
                reportMidiLifecycleFailure(phase, failure);
                closePlayer(player);
                throw failure;
            }
        }
    }

    private void reportMidiLifecycleFailure(String phase, Throwable failure) {
        if (diagnostic) {
            System.out.println("W4ME_MIDI_LIFECYCLE_FAILURE phase=" + phase + " error=" + failure.toString());
        }
    }

    private static final class MmapiMidiPlayback implements MidiPlayback {
        private final Player player;

        private MmapiMidiPlayback(Player player) {
            this.player = player;
        }

        public void close() {
            closePlayer(player);
        }
    }

    private static void closePlayer(Player player) {
        if (player == null) {
            return;
        }
        try {
            player.stop();
        } catch (Throwable ignored) { // NOPMD -- Java ME API linkage fallback.
            // Some implementations already stop a player when media ends.
        }
        try {
            player.close();
        } catch (Throwable ignored) { // NOPMD -- Java ME API linkage fallback.
            // Best effort after an incomplete MMAPI state transition.
        }
    }

    private static final class MidiWriter {
        private byte[] data;
        private int length;

        private MidiWriter(int capacity) {
            data = new byte[capacity];
        }

        private int position() {
            return length;
        }

        private void writeAscii(String value) {
            int index;
            for (index = 0; index < value.length(); index++) {
                writeByte(value.charAt(index));
            }
        }

        private void writeByte(int value) {
            ensure(1);
            data[length++] = (byte) value; // NOPMD -- Compact Java 1.3 cursor bytecode.
        }

        private void writeShort(int value) {
            writeByte(value >>> 8);
            writeByte(value);
        }

        private void writeInt(int value) {
            writeByte(value >>> 24);
            writeByte(value >>> 16);
            writeByte(value >>> 8);
            writeByte(value);
        }

        private void writeVariable(int value) {
            int buffer = value & 0x7f;
            while ((value >>>= 7) != 0) {
                buffer <<= 8;
                buffer |= (value & 0x7f) | 0x80;
            }
            while (true) {
                writeByte(buffer);
                if ((buffer & 0x80) == 0) {
                    return;
                }
                buffer >>>= 8;
            }
        }

        private void patchInt(int offset, int value) {
            data[offset] = (byte) (value >>> 24);
            data[offset + 1] = (byte) (value >>> 16);
            data[offset + 2] = (byte) (value >>> 8);
            data[offset + 3] = (byte) value;
        }

        private byte[] toByteArray() {
            byte[] result = new byte[length];
            System.arraycopy(data, 0, result, 0, length);
            return result;
        }

        private void ensure(int count) {
            if (length + count <= data.length) {
                return;
            }
            byte[] replacement = new byte[data.length * 2];
            System.arraycopy(data, 0, replacement, 0, length);
            data = replacement;
        }
    }
}
