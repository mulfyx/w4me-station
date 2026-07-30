package w4me.runtime.storage;

public final class MemoryDiskBackend implements SnapshotDiskBackend {
    private final byte[] data = new byte[1024];
    private int length;

    public int read(byte[] target, int offset, int size) {
        int count = minimum(size, length);
        System.arraycopy(data, 0, target, offset, count);
        return count;
    }

    public int write(byte[] source, int offset, int size) {
        int count = minimum(size, data.length);
        System.arraycopy(source, offset, data, 0, count);
        length = count;
        return count;
    }

    public int snapshot(byte[] target) {
        if (target == null || target.length < data.length) {
            return -1;
        }
        System.arraycopy(data, 0, target, 0, length);
        return length;
    }

    public boolean replace(byte[] source, int size) {
        if (source == null || size < 0 || size > data.length || size > source.length) {
            return false;
        }
        System.arraycopy(source, 0, data, 0, size);
        length = size;
        return true;
    }

    public void close() {}

    public String grade() {
        return "memory";
    }

    private int minimum(int left, int right) {
        return left < right ? left : right;
    }
}
