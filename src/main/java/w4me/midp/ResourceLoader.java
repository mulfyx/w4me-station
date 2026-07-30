package w4me.midp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;

final class ResourceLoader {
    private ResourceLoader() {}

    static int stage(CartridgeStore store, String title, String location)
            throws IOException, javax.microedition.rms.RecordStoreException {
        if (location == null || location.length() == 0) {
            throw new IOException("empty cartridge location");
        }
        if (startsWithIgnoreCase(location, "http://") || startsWithIgnoreCase(location, "https://")) {
            return stageHttp(store, title, location);
        }
        if (startsWithIgnoreCase(location, "file://") && FileSystemAccessFactory.isAvailable()) {
            return stageFile(store, title, location, FileSystemAccessFactory.create());
        }
        if (location.charAt(0) == '/' || CartridgeStore.isLocation(location)) {
            throw new IOException("only external URL/file cartridges can be staged");
        }
        return store.stageStream(title, Connector.openInputStream(location), -1);
    }

    static int stageFile(CartridgeStore store, String title, String location, FileSystemAccess access)
            throws IOException, javax.microedition.rms.RecordStoreException {
        FileSelection selection = access.inspect(location);
        if (selection.size > CartridgeStore.MAX_CARTRIDGE_BYTES) {
            throw new IOException("cartridge exceeds the WASM-4 64 KiB limit");
        }
        return store.stageStream(title, access.openInputStream(selection.url), selection.size);
    }

    static byte[] read(String location) throws IOException {
        if (location == null || location.length() == 0) {
            throw new IOException("empty cartridge location");
        }
        if (location.charAt(0) == '/') {
            return readResource(location);
        }
        if (CartridgeStore.isLocation(location)) {
            return readInstalled(location);
        }
        if (startsWithIgnoreCase(location, "http://") || startsWithIgnoreCase(location, "https://")) {
            return readHttp(location);
        }
        return readConnection(location);
    }

    private static byte[] readResource(String path) throws IOException {
        InputStream input = ResourceLoader.class.getResourceAsStream(path);
        if (input == null) {
            throw new IOException("resource not found: " + path);
        }
        return readBounded(input, path);
    }

    private static byte[] readHttp(String location) throws IOException {
        HttpConnection connection = null;
        try {
            connection = (HttpConnection) Connector.open(location, Connector.READ);
            connection.setRequestMethod(HttpConnection.GET);
            int response = connection.getResponseCode();
            if (response != HttpConnection.HTTP_OK) {
                throw new IOException("HTTP " + response + " for " + location);
            }
            long length = connection.getLength();
            if (length > CartridgeStore.MAX_CARTRIDGE_BYTES) {
                throw new IOException("cartridge exceeds the WASM-4 64 KiB limit");
            }
            return readBounded(connection.openInputStream(), location);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (IOException ignored) {
                    // The response body has already been consumed.
                }
            }
        }
    }

    private static int stageHttp(CartridgeStore store, String title, String location)
            throws IOException, javax.microedition.rms.RecordStoreException {
        HttpConnection connection = null;
        try {
            connection = (HttpConnection) Connector.open(location, Connector.READ);
            connection.setRequestMethod(HttpConnection.GET);
            int response = connection.getResponseCode();
            if (response != HttpConnection.HTTP_OK) {
                throw new IOException("HTTP " + response + " for " + location);
            }
            long length = connection.getLength();
            if (length > CartridgeStore.MAX_CARTRIDGE_BYTES) {
                throw new IOException("cartridge exceeds the WASM-4 64 KiB limit");
            }
            return store.stageStream(title, connection.openInputStream(), length);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (IOException ignored) {
                    // A verified staged payload must not be orphaned by close failure.
                }
            }
        }
    }

    private static byte[] readConnection(String location) throws IOException {
        if (startsWithIgnoreCase(location, "file://") && FileSystemAccessFactory.isAvailable()) {
            FileSystemAccess access = FileSystemAccessFactory.create();
            FileSelection selection = access.inspect(location);
            return readBounded(access.openInputStream(selection.url), location);
        }
        return readBounded(Connector.openInputStream(location), location);
    }

    private static byte[] readInstalled(String location) throws IOException {
        CartridgeStore store = null;
        try {
            store = CartridgeStore.open();
            return store.read(CartridgeStore.recordIdFromLocation(location));
        } catch (javax.microedition.rms.RecordStoreException failure) {
            throw new IOException( // NOPMD -- CLDC 1.1 has no cause chaining.
                    "cannot read installed cartridge: " + failure.toString());
        } finally {
            if (store != null) {
                store.close();
            }
        }
    }

    private static byte[] readBounded(InputStream input, String location) throws IOException {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            int count;
            int total = 0;
            while ((count = input.read(buffer)) >= 0) {
                total += count;
                if (total > CartridgeStore.MAX_CARTRIDGE_BYTES) {
                    throw new IOException("cartridge exceeds the WASM-4 64 KiB limit: " + location);
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        if (value.length() < prefix.length()) {
            return false;
        }
        int index;
        for (index = 0; index < prefix.length(); index++) {
            char left = value.charAt(index);
            char right = prefix.charAt(index);
            if (left >= 'A' && left <= 'Z') {
                left = (char) (left + 'a' - 'A');
            }
            if (left != right) {
                return false;
            }
        }
        return true;
    }
}
