package w4me.midp;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;
import javax.microedition.midlet.MIDlet;
import w4me.runtime.audio.AudioBackends;
import w4me.runtime.audio.AudioControl;
import w4me.runtime.audio.Wasm4Apu;

/** Provides the w 4 me midlet implementation. */
public class W4MeMidlet extends MIDlet implements CommandListener {
    private LibraryList library;
    private W4Canvas canvas;
    private TextBox locationEntry;
    private boolean autostartChecked;
    private boolean audioPreferenceLoaded;
    private int audioProfile = AudioPreferences.PROFILE_WAV;
    private boolean soundMuted;
    private int audioGain = 100;
    private final SettingsMenuModel settingsModel = new SettingsMenuModel();
    private final SettingsCategory[] settingsCategories = new SettingsCategory[SettingsMenuModel.CATEGORY_COUNT];
    private SaveStateMenuActions saveStateMenuActions;
    private final Command runLocationCommand = new Command("Run", Command.OK, 1);
    private final Command cancelLocationCommand = new Command("Cancel", Command.CANCEL, 2);

    /** Creates a new w 4 me midlet. */
    public W4MeMidlet() {
        registerSettingsCategory(new SettingsCategory() {
            public int id() {
                return SettingsMenuModel.AUDIO;
            }

            public void open(W4MeMidlet midlet, SettingsList settings, W4Canvas source) {
                midlet.showAudioSettings(source, settings);
            }
        });
        registerSaveStateMenuActions(new SaveStateMenuActions() {
            public void saveState(W4Canvas source) {
                returnToCanvasForSaveState(source, true);
            }

            public void loadState(W4Canvas source) {
                returnToCanvasForSaveState(source, false);
            }
        });
    }

    /** Performs the start app operation. */
    protected void startApp() {
        if (!autostartChecked) {
            autostartChecked = true;
            String location = getAppProperty("W4ME-Cartridge-URL");
            if (location != null && location.trim().length() != 0) {
                location = location.trim();
                openCartridge(location, titleFromLocation(location));
                return;
            }
        }
        if (canvas != null) {
            Display.getDisplay(this).setCurrent(canvas);
            canvas.start();
        } else {
            showLibrary();
        }
    }

    /** Performs the pause app operation. */
    protected void pauseApp() {
        if (canvas != null) {
            canvas.stop();
        }
    }

    /** Performs the destroy app operation. */
    protected void destroyApp(boolean unconditional) {
        if (canvas != null) {
            canvas.stop();
        }
    }

    void showLibrary() {
        if (canvas != null) {
            canvas.stop();
            canvas = null;
        }
        showLibraryDisplayable();
    }

    void finishCanvasExit(final W4Canvas source) {
        Display.getDisplay(this).callSerially(new Runnable() {
            public void run() {
                if (canvas != source) {
                    return;
                }
                canvas = null;
                showLibraryDisplayable();
            }
        });
    }

    void showCartridgeFailure(W4Canvas source, String title, Throwable failure) {
        if (canvas != source) {
            return;
        }
        source.stop();
        canvas = null;
        LibraryList returnTo = libraryDisplayable();
        String message = explain(failure);
        if (message.length() > 512) {
            message = message.substring(0, 512);
        }
        Alert alert = new Alert("Cart cannot run: " + title, message, null, AlertType.ERROR);
        alert.setTimeout(Alert.FOREVER);
        Display.getDisplay(this).setCurrent(alert, returnTo);
    }

    /**
     * Turns a runtime failure into something a phone user can act on. The internal trap text stays as-is for logs and
     * tests; only what reaches the Alert changes.
     */
    private static String explain(Throwable failure) {
        String raw = failure.toString();
        if (raw.indexOf("instruction budget exhausted") >= 0) {
            return "One frame of this cartridge needed more work than a single frame"
                    + " is allowed, so it was stopped. This is a limit of running"
                    + " WebAssembly on this phone, not a damaged cartridge. Some"
                    + " cartridges compute a whole move or level inside one frame"
                    + " and cannot finish in time.";
        }
        return raw;
    }

    private void showLibraryDisplayable() {
        Display.getDisplay(this).setCurrent(libraryDisplayable());
    }

    private LibraryList libraryDisplayable() {
        if (library == null) {
            library = new LibraryList(this);
        } else {
            library.reloadInstalled();
        }
        return library;
    }

    void showLocationEntry() {
        if (locationEntry == null) {
            locationEntry = new TextBox("Enter URL/file location", "", 512, TextField.URL);
            locationEntry.addCommand(runLocationCommand);
            locationEntry.addCommand(cancelLocationCommand);
            locationEntry.setCommandListener(this);
        }
        Display.getDisplay(this).setCurrent(locationEntry);
    }

    void showInstallOptions() {
        if (!FileSystemAccessFactory.isAvailable()) {
            showLocationEntry();
            return;
        }
        showFileBrowser();
    }

    private void showFileBrowser() {
        try {
            FileBrowserList browser = new FileBrowserList(this, FileSystemAccessFactory.create());
            browser.show();
        } catch (Throwable failure) { // NOPMD -- Java ME API linkage fallback.
            String message = failure.toString();
            Alert alert = new Alert("Local files unavailable", message, null, AlertType.ERROR);
            alert.setTimeout(Alert.FOREVER);
            Display.getDisplay(this).setCurrent(alert, locationEntryDisplayable());
        }
    }

    void showFileSelection(FileBrowserList browser, FileSelection selection) {
        Display.getDisplay(this).setCurrent(new FileSelectionForm(this, browser, selection));
    }

    private TextBox locationEntryDisplayable() {
        if (locationEntry == null) {
            locationEntry = new TextBox("Enter URL/file location", "", 512, TextField.URL);
            locationEntry.addCommand(runLocationCommand);
            locationEntry.addCommand(cancelLocationCommand);
            locationEntry.setCommandListener(this);
        }
        return locationEntry;
    }

    /** Performs the command action operation. */
    public void commandAction(Command command, Displayable displayable) {
        if (command == runLocationCommand) {
            String location = locationEntry.getString().trim();
            if (location.length() != 0) {
                openCartridge(location, titleFromLocation(location));
                return;
            }
        }
        showLibrary();
    }

    void openCartridge(String resource, String title) {
        canvas = new W4Canvas(this, resource, title, createSessionMonitor(resource, title));
        Display.getDisplay(this).setCurrent(canvas);
        canvas.start();
    }

    String audioBackendPreference() {
        loadAudioPreference();
        if (audioProfile == AudioPreferences.PROFILE_MIDI) {
            return AudioBackends.PREFERENCE_MIDI;
        }
        if (audioProfile == AudioPreferences.PROFILE_TONE) {
            return AudioBackends.PREFERENCE_TONE;
        }
        return AudioBackends.PREFERENCE_WAV;
    }

    boolean compatibilityAudioEnabled() {
        loadAudioPreference();
        return audioProfile == AudioPreferences.PROFILE_MIDI;
    }

    int preferredAudioProfile() {
        loadAudioPreference();
        return audioProfile;
    }

    boolean soundMuted() {
        loadAudioPreference();
        return soundMuted;
    }

    int audioGain() {
        loadAudioPreference();
        return audioGain;
    }

    void configureAudio(Wasm4Apu audio) {
        loadAudioPreference();
        audio.setMasterGain(audioGain);
        audio.setMuted(soundMuted);
    }

    void showSystemMenu(final W4Canvas source) {
        final SaveStateMenuActions actions = saveStateMenuActions;
        final int initialAction = initialSystemMenuAction();
        Display.getDisplay(this).callSerially(new Runnable() {
            public void run() {
                if (canvas == source && source.isSystemMenuOpen()) {
                    Display.getDisplay(W4MeMidlet.this)
                            .setCurrent(new SystemMenuList(W4MeMidlet.this, source, actions, initialAction));
                }
            }
        });
    }

    /** Performs the initial system menu action operation. */
    protected int initialSystemMenuAction() {
        return SystemMenuModel.ACTION_CONTINUE;
    }

    private void registerSaveStateMenuActions(SaveStateMenuActions actions) {
        if (actions == null) {
            throw new NullPointerException();
        }
        if (saveStateMenuActions != null) {
            throw new IllegalStateException("save-state menu actions already registered");
        }
        saveStateMenuActions = actions;
    }

    private void returnToCanvasForSaveState(W4Canvas source, boolean save) {
        if (canvas != source || !source.isSystemMenuOpen()) {
            return;
        }
        boolean accepted = save ? source.saveFromSystemMenu() : source.loadFromSystemMenu();
        if (accepted) {
            Display.getDisplay(this).setCurrent(source);
        }
    }

    void continueFromSystemMenu(SystemMenuList menu, W4Canvas source) {
        Display display = Display.getDisplay(this);
        if (canvas != source || display.getCurrent() != menu || !source.isSystemMenuOpen()) {
            return;
        }
        display.setCurrent(source);
        source.continueFromSystemMenu();
    }

    void restartFromSystemMenu(SystemMenuList menu, W4Canvas source) {
        Display display = Display.getDisplay(this);
        if (canvas != source || display.getCurrent() != menu || !source.isSystemMenuOpen()) {
            return;
        }
        display.setCurrent(source);
        source.restartFromSystemMenu();
    }

    void showSettings(W4Canvas source) {
        showSettings(source, null);
    }

    void showSettings(W4Canvas source, SystemMenuList systemMenu) {
        if (source != null) {
            if (canvas != source || !source.isSystemMenuOpen()) {
                return;
            }
        }
        Display.getDisplay(this).setCurrent(new SettingsList(this, source, systemMenu, settingsModel));
    }

    void showSettingsCategory(SettingsList settings, W4Canvas source, int category) {
        SettingsCategory handler =
                category >= 0 && category < settingsCategories.length ? settingsCategories[category] : null;
        if (handler != null) {
            handler.open(this, settings, source);
            return;
        }
        Alert alert = new Alert("Settings", "This settings category is unavailable.", null, AlertType.WARNING);
        alert.setTimeout(Alert.FOREVER);
        Display.getDisplay(this).setCurrent(alert, settings);
    }

    void finishSettings(SettingsList settings, W4Canvas source, SystemMenuList systemMenu) {
        if (source != null && canvas == source && source.isSystemMenuOpen() && systemMenu != null) {
            Display.getDisplay(this).setCurrent(systemMenu);
        } else if (source != null) {
            showLibrary();
        } else {
            showLibraryDisplayable();
        }
    }

    private void registerSettingsCategory(SettingsCategory category) {
        if (category == null) {
            throw new NullPointerException();
        }
        int id = category.id();
        if (id < 0 || id >= settingsCategories.length) {
            throw new IllegalArgumentException("unknown settings category");
        }
        if (settingsCategories[id] != null) {
            throw new IllegalStateException("settings category already registered");
        }
        settingsCategories[id] = category;
        settingsModel.register(id);
    }

    void showAudioSettings(W4Canvas source) {
        showAudioSettings(source, null);
    }

    private void showAudioSettings(W4Canvas source, SettingsList settings) {
        loadAudioPreference();
        int capability = source == null ? AudioControl.VOLUME_CONTINUOUS : source.audioVolumeCapability();
        Display.getDisplay(this)
                .setCurrent(
                        new AudioSettingsForm(this, source, settings, capability, audioProfile, soundMuted, audioGain));
    }

    void finishAudioSettings(
            W4Canvas source, SettingsList settings, boolean apply, int profile, boolean muted, int gain) {
        boolean saved = true;
        if (apply) {
            audioProfile = AudioPreferences.isProfile(profile) ? profile : AudioPreferences.PROFILE_WAV;
            soundMuted = muted;
            audioGain = gain;
            saved = AudioPreferences.save(new AudioPreferences.Settings(audioProfile, soundMuted, audioGain));
        }

        Displayable target;
        if (source != null && canvas == source) {
            source.applyAudioSettings(apply, soundMuted, audioGain);
            if (settings != null) {
                target = settings;
            } else {
                target = source;
                source.continueFromSystemMenu();
            }
        } else if (settings != null) {
            target = settings;
        } else {
            target = libraryDisplayable();
        }

        if (apply && !saved) {
            Alert alert = new Alert(
                    "Audio", "Settings are active for this session, but could not be saved.", null, AlertType.WARNING);
            alert.setTimeout(Alert.FOREVER);
            Display.getDisplay(this).setCurrent(alert, target);
        } else {
            Display.getDisplay(this).setCurrent(target);
        }
    }

    /** Creates the session monitor. */
    protected W4SessionMonitor createSessionMonitor(String resource, String title) {
        return null;
    }

    boolean audioDiagnosticsEnabled() {
        String value = getAppProperty("W4ME-Audio-Diagnostics");
        return "true".equals(value) || "1".equals(value);
    }

    void exit() {
        notifyDestroyed();
    }

    private void loadAudioPreference() {
        if (audioPreferenceLoaded) {
            return;
        }
        audioPreferenceLoaded = true;
        AudioPreferences.Settings saved = AudioPreferences.load();
        String preference = getAppProperty("W4ME-Audio-Backend");
        audioProfile = saved.profile;
        if (AudioBackends.PREFERENCE_MIDI.equals(preference)) {
            audioProfile = AudioPreferences.PROFILE_MIDI;
        } else if (AudioBackends.PREFERENCE_TONE.equals(preference)) {
            audioProfile = AudioPreferences.PROFILE_TONE;
        } else if (AudioBackends.PREFERENCE_WAV.equals(preference)) {
            audioProfile = AudioPreferences.PROFILE_WAV;
        }
        soundMuted = saved.muted;
        audioGain = saved.gain;
    }

    private String titleFromLocation(String location) {
        int end = location.length();
        int query = location.indexOf('?');
        if (query >= 0 && query < end) {
            end = query;
        }
        int fragment = location.indexOf('#');
        if (fragment >= 0 && fragment < end) {
            end = fragment;
        }
        int slash = location.lastIndexOf('/', end - 1);
        int backslash = location.lastIndexOf('\\', end - 1);
        int start = (slash > backslash ? slash : backslash) + 1;
        if (start >= end) {
            return "External cartridge";
        }
        String title = location.substring(start, end);
        if (FilePageBuilder.endsWithIgnoreCase(title, ".wasm")) {
            title = title.substring(0, title.length() - 5);
        }
        return title.length() == 0 ? "External cartridge" : title;
    }
}
