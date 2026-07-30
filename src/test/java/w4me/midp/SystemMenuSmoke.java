package w4me.midp;

public final class SystemMenuSmoke {
    private SystemMenuSmoke() {}

    public static void main(String[] args) {
        verifyActionOrdering();
        verifyFrameBoundaryState();
        verifySettingsOrdering();
        System.out.println(
                "PASS system-menu ordering=PASS state=PASS input=PASS settings=PASS");
    }

    private static void verifyActionOrdering() {
        SystemMenuModel base = new SystemMenuModel(false);
        assertEquals("base size", 4, base.size());
        assertEquals("base continue", SystemMenuModel.ACTION_CONTINUE, base.actionAt(0));
        assertEquals("base settings", SystemMenuModel.ACTION_SETTINGS, base.actionAt(1));
        assertEquals("base restart", SystemMenuModel.ACTION_RESTART, base.actionAt(2));
        assertEquals("base exit", SystemMenuModel.ACTION_EXIT, base.actionAt(3));
        assertEquals("base exit label", "Exit", base.labelAt(base.size() - 1));

        SystemMenuModel saveState = new SystemMenuModel(true);
        assertEquals("save size", 6, saveState.size());
        assertEquals("save action", SystemMenuModel.ACTION_SAVE_STATE, saveState.actionAt(1));
        assertEquals("load action", SystemMenuModel.ACTION_LOAD_STATE, saveState.actionAt(2));
        assertEquals(
                "save exit", SystemMenuModel.ACTION_EXIT, saveState.actionAt(saveState.size() - 1));
    }

    private static void verifyFrameBoundaryState() {
        SystemMenuState state = new SystemMenuState();
        int updates = 0;
        updates += state.state() == SystemMenuState.RUNNING ? 1 : 0;
        assertTrue("menu request accepted", state.requestMenu());
        assertEquals(
                "request waits for boundary",
                SystemMenuState.MENU_REQUESTED,
                state.state());
        updates += state.state() == SystemMenuState.RUNNING ? 1 : 0;
        assertTrue("worker opens at boundary", state.acceptMenuAtFrameBoundary());
        updates += state.state() == SystemMenuState.RUNNING ? 1 : 0;
        assertEquals("paused updates do not catch up", 1, updates);
        assertTrue("continue accepted", state.requestContinue());
        assertTrue("first resumed input suppressed", state.consumeInputSuppression());
        assertTrue("suppression consumed once", !state.consumeInputSuppression());
        updates += state.state() == SystemMenuState.RUNNING ? 1 : 0;
        assertEquals("one normal update resumes", 2, updates);

        assertTrue("second menu request", state.requestMenu());
        assertTrue("second boundary", state.acceptMenuAtFrameBoundary());
        assertTrue("restart request", state.requestRestart());
        state.stop();
        state.completeRestart();
        assertTrue("lifecycle stop has priority", state.isStopped());
        assertTrue("stopped state rejects menu", !state.requestMenu());
    }

    private static void verifySettingsOrdering() {
        SettingsMenuModel settings = new SettingsMenuModel();
        settings.register(SettingsMenuModel.DISPLAY_TOUCH);
        settings.register(SettingsMenuModel.AUDIO);
        settings.register(SettingsMenuModel.CONTROLS);
        assertEquals("settings size", 3, settings.size());
        assertEquals("audio first", "Audio", settings.labelAt(0));
        assertEquals("controls second", "Controls", settings.labelAt(1));
        assertEquals("display third", "Display & Touch", settings.labelAt(2));
    }

    private static void assertTrue(String message, boolean value) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(String message, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(String message, String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }
}
