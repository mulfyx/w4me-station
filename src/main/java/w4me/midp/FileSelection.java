package w4me.midp;

final class FileSelection {
    final String name;
    final String url;
    final long size;

    FileSelection(String name, String url, long size) {
        this.name = name;
        this.url = url;
        this.size = size;
    }
}
