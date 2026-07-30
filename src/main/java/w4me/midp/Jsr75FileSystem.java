package w4me.midp;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import javax.microedition.io.Connection;
import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;

final class Jsr75FileSystem implements FileSystemAccess {
    public FilePage listRoots(String afterKey, int limit) throws IOException {
        return FilePageBuilder.roots(FileSystemRegistry.listRoots(), afterKey, limit);
    }

    public FilePage list(String directoryUrl, String afterKey, int limit) throws IOException {
        FileConnection connection = null;
        try {
            connection = (FileConnection) Connector.open(directoryUrl, Connector.READ);
            if (!connection.exists() || !connection.isDirectory()) {
                throw new IOException("directory is no longer available");
            }
            Enumeration names = connection.list();
            return FilePageBuilder.directory(names, connection.getURL(), afterKey, limit);
        } finally {
            close(connection);
        }
    }

    public FileSelection inspect(String fileUrl) throws IOException {
        FileConnection connection = null;
        try {
            connection = (FileConnection) Connector.open(fileUrl, Connector.READ);
            if (!connection.exists()) {
                throw new IOException("file is no longer available");
            }
            if (connection.isDirectory()) {
                throw new IOException("select a .wasm file, not a directory");
            }
            String name = connection.getName();
            if (!FilePageBuilder.endsWithIgnoreCase(name, ".wasm")) {
                throw new IOException("only .wasm files can be installed");
            }
            long size = connection.fileSize();
            if (size > CartridgeStore.MAX_CARTRIDGE_BYTES) {
                throw new IOException("cartridge exceeds the WASM-4 64 KiB limit");
            }
            return new FileSelection(name, connection.getURL(), size);
        } finally {
            close(connection);
        }
    }

    public InputStream openInputStream(String fileUrl) throws IOException {
        FileConnection connection = (FileConnection) Connector.open(fileUrl, Connector.READ);
        try {
            if (!connection.exists() || connection.isDirectory()) {
                throw new IOException("selected .wasm file is no longer available");
            }
            InputStream input = connection.openInputStream();
            return new OwnedInputStream(input, connection);
        } catch (IOException failure) {
            close(connection);
            throw failure;
        } catch (RuntimeException failure) { // NOPMD -- Java 1.3 lacks multi-catch.
            close(connection);
            throw failure;
        } catch (Error failure) { // NOPMD -- Java 1.3 has no multi-catch syntax for the equivalent recovery branches.
            // Optional Java ME APIs and device implementations can fail with linkage or VM
            // errors.
            close(connection);
            throw failure;
        }
    }

    private static void close(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (IOException ignored) {
                // The operation result is already known.
            }
        }
    }

    private static final class OwnedInputStream extends InputStream {
        private InputStream input;
        private Connection connection;

        OwnedInputStream(InputStream input, Connection connection) {
            this.input = input;
            this.connection = connection;
        }

        public int read() throws IOException {
            requireOpen();
            return input.read();
        }

        public int read(byte[] buffer, int offset, int length) throws IOException {
            requireOpen();
            return input.read(buffer, offset, length);
        }

        public long skip(long count) throws IOException {
            requireOpen();
            return input.skip(count);
        }

        public int available() throws IOException {
            requireOpen();
            return input.available();
        }

        public void close() throws IOException {
            IOException failure = null;
            if (input != null) {
                try {
                    input.close();
                } catch (IOException closeFailure) {
                    failure = closeFailure;
                }
                input = null;
            }
            if (connection != null) {
                try {
                    connection.close();
                } catch (IOException closeFailure) {
                    if (failure == null) {
                        failure = closeFailure;
                    }
                }
                connection = null;
            }
            if (failure != null) {
                throw failure;
            }
        }

        private void requireOpen() throws IOException {
            if (input == null) {
                throw new IOException("file stream is closed");
            }
        }
    }
}
