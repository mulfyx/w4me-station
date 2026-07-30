package w4me.runtime.audio;

public final class AudioBackends {
    public static final String PREFERENCE_WAV = "wav";
    public static final String PREFERENCE_MIDI = "midi";
    public static final String PREFERENCE_TONE = "tone";

    public static final String PROFILE_WAV = "WAV synthesis";
    public static final String PROFILE_MIDI = "MIDI synthesis";
    public static final String PROFILE_TONE = "Simple tones";
    public static final String PROFILE_SILENT = "Silent";

    private AudioBackends() {}

    public static AudioBackend create() {
        return create(null);
    }

    public static AudioBackend create(String preference) {
        if (PREFERENCE_TONE.equals(preference)) {
            return createTone();
        }
        if (PREFERENCE_MIDI.equals(preference)) {
            return createMidiFallback();
        }
        try {
            Object backend = Class.forName("w4me.runtime.audio.MmapiPcmBackend").newInstance();
            return (AudioBackend) backend;
        } catch (Throwable unavailable) {
            return createMidiFallback();
        }
    }

    static AudioBackend createMidiFallback() {
        try {
            Object backend =
                    Class.forName("w4me.runtime.audio.MmapiMidiBackend").newInstance();
            return (AudioBackend) backend;
        } catch (Throwable unavailable) {
            return createTone();
        }
    }

    private static AudioBackend createTone() {
        try {
            Object backend =
                    Class.forName("w4me.runtime.audio.MmapiToneBackend").newInstance();
            return (AudioBackend) backend;
        } catch (Throwable unavailable) {
            return new SilentAudioBackend();
        }
    }

    public static String activeProfileName(AudioBackend backend) {
        if (backend instanceof AudioBackendStatus) {
            return ((AudioBackendStatus) backend).activeProfileName();
        }
        return backend == null ? PROFILE_SILENT : backend.grade();
    }

    public static String fallbackReason(AudioBackend backend) {
        if (backend instanceof AudioBackendStatus) {
            return ((AudioBackendStatus) backend).fallbackReason();
        }
        return null;
    }
}
