package w4me.runtime.audio;

public final class AudioSettingsSmoke {
    public static void main(String[] arguments) {
        RecordingBackend backend = new RecordingBackend();
        Wasm4Apu apu = new Wasm4Apu(backend);

        int originalVolume = 0xcafe0064;
        apu.tone(440, 30, originalVolume, 0x82);
        assertEquals("gain 100 keeps packed volume", originalVolume, backend.lastVolume);
        assertEquals("frequency unchanged", 440, backend.lastFrequency);
        assertEquals("duration unchanged", 30, backend.lastDuration);
        assertEquals("flags unchanged", 0x82, backend.lastFlags);

        apu.setMasterGain(50);
        assertEquals("gain change silences active output", 1, backend.silenceCount);
        apu.tone(330, 20, originalVolume, 1);
        assertEquals("gain scales implicit peak and sustain", 0xcafe3232, backend.lastVolume);

        apu.tone(220, 10, 0x00000101, 3);
        assertEquals("gain uses deterministic half-up rounding", 0x00000101, backend.lastVolume);

        int submittedBeforeMute = backend.submitCount;
        int logicalBeforeMute = apu.toneEventCount();
        apu.setMuted(true);
        apu.tone(262, 10, 100, 0);
        assertEquals("mute suppresses backend submission", submittedBeforeMute, backend.submitCount);
        assertEquals("mute preserves logical APU events", logicalBeforeMute + 1, apu.toneEventCount());

        apu.setMuted(false);
        apu.tone(262, 10, 100, 0);
        assertEquals("unmute resumes at stored gain", submittedBeforeMute + 1, backend.submitCount);
        assertEquals("stored gain remains active", 0x3232, backend.lastVolume);

        apu.suspendOutput();
        int submittedBeforeSuspend = backend.submitCount;
        apu.tone(294, 10, 100, 0);
        assertEquals(
                "temporary suspension suppresses output",
                submittedBeforeSuspend,
                backend.submitCount);
        apu.resumeOutput();
        if (apu.muted()) {
            throw new AssertionError("temporary suspension changed persistent mute");
        }
        apu.tone(294, 10, 100, 0);
        assertEquals(
                "resume restores persistent gain",
                submittedBeforeSuspend + 1,
                backend.submitCount);

        apu.setMasterGain(0);
        int submittedBeforeZero = backend.submitCount;
        apu.tone(330, 10, 100, 0);
        assertEquals("zero gain suppresses output", submittedBeforeZero, backend.submitCount);
        assertEquals(
                "backend capability forwarded",
                AudioControl.VOLUME_CONTINUOUS,
                apu.volumeCapability());

        requireInvalidGain(apu, -1);
        requireInvalidGain(apu, 101);
        System.out.println(
                "PASS audio-settings gain-boundaries rounding mute resume unchanged-fields");
    }

    private static void requireInvalidGain(Wasm4Apu apu, int gain) {
        try {
            apu.setMasterGain(gain);
            throw new AssertionError("invalid gain was accepted: " + gain);
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void assertEquals(String label, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(
                    label + ": expected " + expected + ", got " + actual);
        }
    }

    private static final class RecordingBackend implements AudioBackend, AudioControl {
        private int submitCount;
        private int silenceCount;
        private int lastFrequency;
        private int lastDuration;
        private int lastVolume;
        private int lastFlags;

        public void submitTone(int frequency, int duration, int volume, int flags) {
            submitCount++;
            lastFrequency = frequency;
            lastDuration = duration;
            lastVolume = volume;
            lastFlags = flags;
        }

        public void tick() {}

        public void close() {}

        public String grade() {
            return "test";
        }

        public int volumeCapability() {
            return VOLUME_CONTINUOUS;
        }

        public void silence() {
            silenceCount++;
        }
    }
}
