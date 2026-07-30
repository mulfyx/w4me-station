package w4me.midp;

import java.io.IOException;

final class FileSystemAccessFactory {
    private static final String FILE_CONNECTION = "javax.microedition.io.file.FileConnection";
    private static final String FILE_REGISTRY = "javax.microedition.io.file.FileSystemRegistry";
    private static final String IMPLEMENTATION = "w4me.midp.Jsr75FileSystem";

    private FileSystemAccessFactory() {}

    static boolean isAvailable() {
        try {
            Class.forName(FILE_CONNECTION);
            Class.forName(FILE_REGISTRY);
            Class.forName(IMPLEMENTATION);
            return true;
        } catch (Throwable unavailable) { // NOPMD -- Java ME API linkage fallback.
            return false;
        }
    }

    static FileSystemAccess create() throws IOException {
        if (!isAvailable()) {
            throw new IOException("local file browser is not available");
        }
        try {
            return (FileSystemAccess) Class.forName(IMPLEMENTATION).newInstance();
        } catch (Throwable failure) { // NOPMD -- Java ME API linkage fallback.
            throw new IOException( // NOPMD -- CLDC 1.1 has no cause chaining.
                    "cannot start local file browser: " + failure.toString());
        }
    }
}
