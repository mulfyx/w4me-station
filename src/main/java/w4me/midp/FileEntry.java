package w4me.midp;

final class FileEntry {
    final String name;
    final String url;
    final String sortKey;
    final boolean directory;

    FileEntry(String name, String url, String sortKey, boolean directory) {
        this.name = name;
        this.url = url;
        this.sortKey = sortKey;
        this.directory = directory;
    }
}
