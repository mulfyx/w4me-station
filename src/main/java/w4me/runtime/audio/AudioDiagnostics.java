package w4me.runtime.audio;

/** Optional MMAPI lifecycle diagnostics enabled by a monitor or MIDlet property. */
public interface AudioDiagnostics {
    /** Updates the audio diagnostics. */
    void setAudioDiagnostics(boolean enabled);
}
