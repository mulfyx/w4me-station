package w4me.midp;

import java.io.IOException;
import java.io.InputStream;

interface FileSystemAccess {
    FilePage listRoots(String afterKey, int limit) throws IOException;

    FilePage list(String directoryUrl, String afterKey, int limit) throws IOException;

    FileSelection inspect(String fileUrl) throws IOException;

    InputStream openInputStream(String fileUrl) throws IOException;
}
