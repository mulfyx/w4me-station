package w4me.runtime.storage;

/** Provides the disk backends implementation. */
public final class DiskBackends {
    private DiskBackends() {}

    /** Performs the create operation. */
    public static DiskBackend create(byte[] cartridge) {
        return create(cartridgeIdentity(cartridge));
    }

    /** Performs the create operation. */
    public static DiskBackend create(int cartridgeIdentity) {
        String name = "w4d" + hex8(cartridgeIdentity);
        try {
            RmsDiskBackend backend = new RmsDiskBackend();
            backend.open(name);
            return backend;
        } catch (Throwable unavailable) { // NOPMD -- Java ME API linkage fallback.
            return new MemoryDiskBackend();
        }
    }

    /** Performs the cartridge identity operation. */
    public static int cartridgeIdentity(byte[] bytes) {
        if (bytes == null) {
            throw new NullPointerException();
        }
        int hash = 0x811c9dc5;
        int index;
        for (index = 0; index < bytes.length; index++) {
            hash ^= bytes[index] & 0xff;
            hash *= 0x01000193;
        }
        return hash;
    }

    private static String hex8(int value) {
        String hex = Integer.toHexString(value);
        StringBuffer result = new StringBuffer(8);
        int padding;
        for (padding = hex.length(); padding < 8; padding++) {
            result.append('0');
        }
        result.append(hex);
        return result.toString();
    }
}
