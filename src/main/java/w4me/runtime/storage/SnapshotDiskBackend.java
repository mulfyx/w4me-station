package w4me.runtime.storage;

/**
 * Logical-disk snapshot support used by the temporary in-memory save state.
 *
 * <p>A replacement either commits the complete logical disk or leaves the
 * previous disk unchanged.
 */
public interface SnapshotDiskBackend extends DiskBackend {
    int snapshot(byte[] target);

    boolean replace(byte[] source, int length);
}
