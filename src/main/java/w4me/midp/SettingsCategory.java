package w4me.midp;

/** Capability-owned entry that can be registered in the native Settings hub. */
interface SettingsCategory {
    int id();

    void open(W4MeMidlet midlet, SettingsList settings, W4Canvas source);
}
