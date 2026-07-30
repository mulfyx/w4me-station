package w4me.runtime.audio;

/** Optional user-control contract implemented by production audio backends. */
public interface AudioControl {
    int SILENT = 0;
    int MUTE_ONLY = 1;
    int VOLUME_CONTINUOUS = 2;

    /** Performs the volume capability operation. */
    int volumeCapability();

    /** Stops current output as completely as the backend permits. */
    void silence();
}
