package w4me.midp;

/** Stable action ordering for the native in-game system menu. */
final class SystemMenuModel {
    static final int ACTION_CONTINUE = 0;
    static final int ACTION_SAVE_STATE = 1;
    static final int ACTION_LOAD_STATE = 2;
    static final int ACTION_SETTINGS = 3;
    static final int ACTION_RESTART = 4;
    static final int ACTION_EXIT = 5;

    private static final int[] BASE_ACTIONS = {
        ACTION_CONTINUE, ACTION_SETTINGS, ACTION_RESTART, ACTION_EXIT
    };
    private static final int[] SAVE_STATE_ACTIONS = {
        ACTION_CONTINUE,
        ACTION_SAVE_STATE,
        ACTION_LOAD_STATE,
        ACTION_SETTINGS,
        ACTION_RESTART,
        ACTION_EXIT
    };
    private static final String[] LABELS = {
        "Continue", "Save State", "Load State", "Settings", "Restart Cart", "Exit"
    };

    private final int[] actions;

    SystemMenuModel(boolean saveStateAvailable) {
        actions = saveStateAvailable ? SAVE_STATE_ACTIONS : BASE_ACTIONS;
    }

    int size() {
        return actions.length;
    }

    int actionAt(int index) {
        requireIndex(index);
        return actions[index];
    }

    String labelAt(int index) {
        return LABELS[actionAt(index)];
    }

    int indexOfAction(int action) {
        int index;
        for (index = 0; index < actions.length; index++) {
            if (actions[index] == action) {
                return index;
            }
        }
        return -1;
    }

    private void requireIndex(int index) {
        if (index < 0 || index >= actions.length) {
            throw new IndexOutOfBoundsException();
        }
    }
}
