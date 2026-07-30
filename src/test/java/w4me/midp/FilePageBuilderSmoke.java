package w4me.midp;

import java.io.IOException;
import java.util.Vector;

/** Provides the file page builder smoke implementation. */
public final class FilePageBuilderSmoke {
    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        verifyLargeDirectory();
        verifyRootsAndFiltering();
        verifyBounds();
        System.out.println("PASS file-page-builder bounded=48 entries=200 directories-first=yes");
    }

    private static void verifyLargeDirectory() throws Exception {
        Vector names = new Vector();
        int index;
        for (index = 4999; index >= 0; index--) {
            if (index < 100) {
                names.addElement(padded("dir", index) + "/");
            } else if (index < 200) {
                names.addElement(padded("cart", index) + (index == 150 ? ".WASM" : ".wasm"));
            } else {
                names.addElement(padded("ignored", index) + ".txt");
            }
        }

        String cursor = null;
        String previousKey = null;
        int eligible = 0;
        int pages = 0;
        boolean sawFile = false;
        while (true) {
            FilePage page = FilePageBuilder.directory(names.elements(), "file:///root/games/", cursor, 48);
            if (page.entries.length > 48) {
                throw new IllegalStateException("page exceeded its memory bound");
            }
            for (index = 0; index < page.entries.length; index++) {
                FileEntry entry = page.entries[index];
                if (previousKey != null && entry.sortKey.compareTo(previousKey) <= 0) {
                    throw new IllegalStateException("directory page order is unstable");
                }
                if (sawFile && entry.directory) {
                    throw new IllegalStateException("directory followed a file");
                }
                if (!entry.directory) {
                    sawFile = true;
                }
                previousKey = entry.sortKey;
                eligible++;
            }
            pages++;
            if (!page.hasMore) {
                break;
            }
            if (page.nextKey == null || page.nextKey.equals(cursor)) {
                throw new IllegalStateException("paging cursor did not advance");
            }
            cursor = page.nextKey;
        }
        if (eligible != 200 || pages != 5) {
            throw new IllegalStateException("unexpected filtered listing: entries=" + eligible + " pages=" + pages);
        }
    }

    private static void verifyRootsAndFiltering() throws Exception {
        Vector roots = new Vector();
        roots.addElement("MemoryCard/");
        roots.addElement("../");
        roots.addElement("Phone/");
        FilePage page = FilePageBuilder.roots(roots.elements(), null, 48);
        if (page.entries.length != 2
                || !"MemoryCard".equals(page.entries[0].name)
                || !"file:///MemoryCard/".equals(page.entries[0].url)
                || !"Phone".equals(page.entries[1].name)) {
            throw new IllegalStateException("root filtering failed");
        }

        Vector files = new Vector();
        files.addElement("game.wasm");
        files.addElement("GAME.WASM");
        files.addElement("game.wasm.zip");
        files.addElement("folder/");
        files.addElement("../");
        FilePage filtered = FilePageBuilder.directory(files.elements(), "file:///root/", null, 48);
        if (filtered.entries.length != 3
                || !filtered.entries[0].directory
                || filtered.entries[1].directory
                || filtered.entries[2].directory) {
            throw new IllegalStateException("extension or directory filtering failed");
        }
    }

    private static void verifyBounds() throws Exception {
        Vector empty = new Vector();
        requireInvalidLimit(empty, 0);
        requireInvalidLimit(empty, 129);
    }

    private static void requireInvalidLimit(Vector names, int limit) throws Exception {
        try {
            FilePageBuilder.directory(names.elements(), "file:///root/", null, limit);
            throw new IllegalStateException("invalid page limit was accepted");
        } catch (IOException expected) {
            // Expected.
        }
    }

    private static String padded(String prefix, int value) {
        String number = Integer.toString(value);
        StringBuffer result = new StringBuffer(prefix);
        int zeros = 4 - number.length();
        while (zeros-- > 0) {
            result.append('0');
        }
        result.append(number);
        return result.toString();
    }
}
