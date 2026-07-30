package w4me.runtime.audio;

/** Optional human-readable status for a production audio backend and its fallbacks. */
public interface AudioBackendStatus {
    String activeProfileName();

    /**
     * Explains why the preferred backend is not active, or returns {@code null}
     * when no fallback has occurred.
     */
    String fallbackReason();
}
