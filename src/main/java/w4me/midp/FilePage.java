package w4me.midp;

final class FilePage {
    final FileEntry[] entries;
    final boolean hasMore;
    final String nextKey;

    FilePage(FileEntry[] entries, boolean hasMore, String nextKey) {
        this.entries = entries;
        this.hasMore = hasMore;
        this.nextKey = nextKey;
    }
}
