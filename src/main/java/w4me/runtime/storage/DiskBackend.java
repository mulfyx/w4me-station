package w4me.runtime.storage;

/** Provides the disk backend implementation. */
public interface DiskBackend {
    /** Performs the read operation. */
    int read(byte[] target, int offset, int size);

    /** Performs the write operation. */
    int write(byte[] source, int offset, int size);

    /** Performs the close operation. */
    void close();

    /** Performs the grade operation. */
    String grade();
}
