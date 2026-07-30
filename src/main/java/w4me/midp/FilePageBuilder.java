package w4me.midp;

import java.io.IOException;
import java.util.Enumeration;

final class FilePageBuilder {
    private FilePageBuilder() {}

    static FilePage roots(Enumeration roots, String afterKey, int limit) throws IOException {
        return build(roots, "file:///", afterKey, limit, true);
    }

    static FilePage directory(Enumeration names, String directoryUrl, String afterKey, int limit) throws IOException {
        if (directoryUrl == null || !directoryUrl.endsWith("/")) {
            throw new IOException("invalid directory URL");
        }
        return build(names, directoryUrl, afterKey, limit, false);
    }

    private static FilePage build(Enumeration names, String baseUrl, String afterKey, int limit, boolean roots)
            throws IOException {
        if (names == null) {
            throw new IOException("file system returned no directory listing");
        }
        if (limit < 1 || limit > 128) {
            throw new IOException("invalid file page size");
        }
        FileEntry[] selected = new FileEntry[limit + 1];
        int count = 0;
        while (names.hasMoreElements()) {
            Object raw = names.nextElement();
            if (!(raw instanceof String)) {
                continue;
            }
            FileEntry entry = entry((String) raw, baseUrl, roots);
            if (entry == null || (afterKey != null && entry.sortKey.compareTo(afterKey) <= 0)) {
                continue;
            }
            int insertAt = count;
            if (insertAt > limit) {
                insertAt = limit;
            }
            while (insertAt > 0 && entry.sortKey.compareTo(selected[insertAt - 1].sortKey) < 0) {
                if (insertAt <= limit) {
                    selected[insertAt] = selected[insertAt - 1];
                }
                insertAt--;
            }
            if (insertAt <= limit) {
                selected[insertAt] = entry;
                if (count <= limit) {
                    count++;
                }
            }
        }

        boolean hasMore = count > limit;
        int resultCount = hasMore ? limit : count;
        FileEntry[] result = new FileEntry[resultCount];
        System.arraycopy(selected, 0, result, 0, resultCount);
        String nextKey = hasMore && resultCount != 0 ? result[resultCount - 1].sortKey : null;
        return new FilePage(result, hasMore, nextKey);
    }

    private static FileEntry entry(String rawName, String baseUrl, boolean root) {
        if (rawName == null || rawName.length() == 0) {
            return null;
        }
        boolean directory = root || rawName.endsWith("/");
        String name = directory && rawName.length() > 1 ? rawName.substring(0, rawName.length() - 1) : rawName;
        if (name.length() == 0
                || ".".equals(name)
                || "..".equals(name)
                || name.indexOf('/') >= 0
                || name.indexOf('\\') >= 0) {
            return null;
        }
        if (!directory && !endsWithIgnoreCase(name, ".wasm")) {
            return null;
        }
        String sortKey = (directory ? "0" : "1") + foldCase(name) + '\u0000' + name;
        return new FileEntry(name, baseUrl + rawName, sortKey, directory);
    }

    private static String foldCase(String value) {
        char[] folded = new char[value.length()];
        int index;
        for (index = 0; index < folded.length; index++) {
            folded[index] = Character.toLowerCase(value.charAt(index));
        }
        return new String(folded);
    }

    static boolean endsWithIgnoreCase(String value, String suffix) {
        return value.length() >= suffix.length()
                && value.regionMatches(true, value.length() - suffix.length(), suffix, 0, suffix.length());
    }
}
