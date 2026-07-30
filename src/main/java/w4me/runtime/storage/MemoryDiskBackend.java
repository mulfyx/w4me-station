package w4me.runtime.storage;

/** Provides the memory disk backend implementation. */
public final class MemoryDiskBackend implements SnapshotDiskBackend {
    private final byte[] data = new byte[1024];
    private int length;

    /** Performs the read operation. */
    public int read(byte[] target, int offset, int size) {
        int count = minimum(size, length);
        System.arraycopy(data, 0, target, offset, count);
        return count;
    }

    /** Performs the write operation. */
    public int write(byte[] source, int offset, int size) {
        int count = minimum(size, data.length);
        System.arraycopy(source, offset, data, 0, count);
        length = count;
        return count;
    }

    /** Performs the snapshot operation. */
    public int snapshot(byte[] target) {
        if (target == null || target.length < data.length) {
            return -1;
        }
        System.arraycopy(data, 0, target, 0, length);
        return length;
    }

    /** Performs the replace operation. */
    public boolean replace(byte[] source, int size) {
        if (source == null || size < 0 || size > data.length || size > source.length) {
            return false;
        }
        System.arraycopy(source, 0, data, 0, size);
        length = size;
        return true;
    }

    /** Performs the close operation. */
    public void close() {
        /* Intentionally no-op. */
    }

    /** Performs the grade operation. */
    public String grade() {
        return "memory";
    }

    private int minimum(int left, int right) {
        return left < right ? left : right;
    }
}
