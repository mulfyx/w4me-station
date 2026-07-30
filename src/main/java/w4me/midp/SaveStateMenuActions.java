package w4me.midp;

/** Optional capability-owned actions exposed by the native system menu. */
interface SaveStateMenuActions {
    void saveState(W4Canvas source);

    void loadState(W4Canvas source);
}
