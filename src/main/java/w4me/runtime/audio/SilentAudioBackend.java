package w4me.runtime.audio;

/** Provides the silent audio backend implementation. */
public final class SilentAudioBackend implements AudioBackend, AudioControl, AudioBackendStatus {
    /** Performs the submit tone operation. */
    public void submitTone(int frequency, int durationMillis, int volume, int flags) {
        /* Intentionally no-op. */
    }

    /** Performs the tick operation. */
    public void tick() {
        /* Intentionally no-op. */
    }

    /** Performs the close operation. */
    public void close() {
        /* Intentionally no-op. */
    }

    /** Performs the silence operation. */
    public void silence() {
        /* Intentionally no-op. */
    }

    /** Performs the volume capability operation. */
    public int volumeCapability() {
        return SILENT;
    }

    /** Performs the grade operation. */
    public String grade() {
        return "E-silent";
    }

    /** Performs the active profile name operation. */
    public String activeProfileName() {
        return AudioBackends.PROFILE_SILENT;
    }

    /** Performs the fallback reason operation. */
    public String fallbackReason() {
        return null;
    }
}
