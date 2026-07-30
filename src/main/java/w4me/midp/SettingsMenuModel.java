package w4me.midp;

/** Stable category registry shared by the native Settings list and host tests. */
final class SettingsMenuModel {
    static final int AUDIO = 0;
    static final int CONTROLS = 1;
    static final int DISPLAY_TOUCH = 2;
    static final int CATEGORY_COUNT = 3;

    private static final String[] LABELS = {"Audio", "Controls", "Display & Touch"};

    private final boolean[] registered = new boolean[CATEGORY_COUNT];

    void register(int category) {
        requireCategory(category);
        registered[category] = true;
    }

    int size() {
        int count = 0;
        int category;
        for (category = 0; category < registered.length; category++) {
            if (registered[category]) {
                count++;
            }
        }
        return count;
    }

    int categoryAt(int index) {
        if (index < 0) {
            throw new IndexOutOfBoundsException();
        }
        int current = 0;
        int category;
        for (category = 0; category < registered.length; category++) {
            if (registered[category]) {
                if (current == index) {
                    return category;
                }
                current++;
            }
        }
        throw new IndexOutOfBoundsException();
    }

    String labelAt(int index) {
        return LABELS[categoryAt(index)];
    }

    boolean isRegistered(int category) {
        requireCategory(category);
        return registered[category];
    }

    private void requireCategory(int category) {
        if (category < 0 || category >= registered.length) {
            throw new IllegalArgumentException("unknown settings category");
        }
    }
}
