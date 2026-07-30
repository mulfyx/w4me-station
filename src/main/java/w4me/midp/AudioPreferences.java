package w4me.midp;

import javax.microedition.rms.RecordEnumeration;
import javax.microedition.rms.RecordStore;

/** Versioned, fail-open RMS preferences for audio mode and user volume. */
final class AudioPreferences {
    static final int PROFILE_WAV = 0;
    static final int PROFILE_MIDI = 1;
    static final int PROFILE_TONE = 2;

    private static final String STORE_NAME = "w4audio1";
    private static final int MAGIC_W = 0x57;
    private static final int MAGIC_4 = 0x34;
    private static final int VERSION = 3;
    private static final int VERSION_BOOLEAN_MODE = 2;
    private static final int FLAG_V2_COMPATIBILITY = 1;
    private static final int FLAG_V2_MUTED = 2;
    private static final int FLAG_MUTED = 1;

    private AudioPreferences() {}

    static Settings load() {
        RecordStore store = null;
        RecordEnumeration records = null;
        try {
            store = RecordStore.openRecordStore(STORE_NAME, false);
            records = store.enumerateRecords(null, null, false);
            if (!records.hasNextElement()) {
                return Settings.defaults();
            }
            return decode(store.getRecord(records.nextRecordId()));
        } catch (Throwable unavailable) {
            return Settings.defaults();
        } finally {
            destroy(records);
            close(store);
        }
    }

    static boolean save(Settings settings) {
        RecordStore store = null;
        RecordEnumeration records = null;
        try {
            store = RecordStore.openRecordStore(STORE_NAME, true);
            byte[] value = encode(settings);
            records = store.enumerateRecords(null, null, false);
            if (records.hasNextElement()) {
                store.setRecord(records.nextRecordId(), value, 0, value.length);
            } else {
                store.addRecord(value, 0, value.length);
            }
            return true;
        } catch (Throwable unavailable) {
            return false;
        } finally {
            destroy(records);
            close(store);
        }
    }

    static byte[] encode(Settings settings) {
        int flags = settings.muted ? FLAG_MUTED : 0;
        return new byte[] {
            (byte) MAGIC_W,
            (byte) MAGIC_4,
            (byte) VERSION,
            (byte) settings.profile,
            (byte) flags,
            (byte) clampGain(settings.gain)
        };
    }

    static Settings decode(byte[] value) {
        if (value == null) {
            return Settings.defaults();
        }
        if (value.length == 1) {
            return new Settings(
                    value[0] == 1 ? PROFILE_MIDI : PROFILE_WAV,
                    false,
                    100);
        }
        if (value.length < 3) {
            return Settings.defaults();
        }
        if ((value[0] & 0xff) != MAGIC_W
                || (value[1] & 0xff) != MAGIC_4) {
            return Settings.defaults();
        }
        int version = value[2] & 0xff;
        if (version == VERSION_BOOLEAN_MODE && value.length == 5) {
            int legacyFlags = value[3] & 0xff;
            int legacyGain = value[4] & 0xff;
            if (legacyGain > 100) {
                return Settings.defaults();
            }
            return new Settings(
                    (legacyFlags & FLAG_V2_COMPATIBILITY) != 0
                            ? PROFILE_MIDI
                            : PROFILE_WAV,
                    (legacyFlags & FLAG_V2_MUTED) != 0,
                    legacyGain);
        }
        if (version != VERSION || value.length != 6) {
            return Settings.defaults();
        }
        int profile = value[3] & 0xff;
        int flags = value[4] & 0xff;
        int gain = value[5] & 0xff;
        if (!isProfile(profile) || gain > 100) {
            return Settings.defaults();
        }
        return new Settings(
                profile,
                (flags & FLAG_MUTED) != 0,
                gain);
    }

    static boolean isProfile(int profile) {
        return profile >= PROFILE_WAV && profile <= PROFILE_TONE;
    }

    private static int clampGain(int gain) {
        if (gain < 0) {
            return 0;
        }
        return gain > 100 ? 100 : gain;
    }

    private static void close(RecordStore store) {
        if (store == null) {
            return;
        }
        try {
            store.closeRecordStore();
        } catch (Throwable ignored) {
            // Best effort for an optional preference.
        }
    }

    private static void destroy(RecordEnumeration records) {
        if (records == null) {
            return;
        }
        try {
            records.destroy();
        } catch (Throwable ignored) {
            // Best effort after reading or writing an optional preference.
        }
    }

    static final class Settings {
        final int profile;
        final boolean compatibilityMode;
        final boolean muted;
        final int gain;

        Settings(boolean compatibilityMode, boolean muted, int gain) {
            this(
                    compatibilityMode ? PROFILE_MIDI : PROFILE_WAV,
                    muted,
                    gain);
        }

        Settings(int profile, boolean muted, int gain) {
            this.profile = isProfile(profile) ? profile : PROFILE_WAV;
            this.compatibilityMode = this.profile == PROFILE_MIDI;
            this.muted = muted;
            this.gain = clampGain(gain);
        }

        static Settings defaults() {
            return new Settings(PROFILE_WAV, false, 100);
        }
    }
}
