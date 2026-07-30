package w4me.runtime.audio;

/** Provides the audio backend implementation. */
public interface AudioBackend {
    /** Receives the four packed WASM-4 tone arguments without lossy conversion. */
    void submitTone(int frequency, int duration, int volume, int flags);

    /** Performs the tick operation. */
    void tick();

    /** Performs the close operation. */
    void close();

    /** Performs the grade operation. */
    String grade();
}
