package w4me.midp;

public final class AudioPreferencesSmoke {
    public static void main(String[] arguments) {
        AudioPreferences.Settings defaults = AudioPreferences.decode(null);
        assertSettings(
                "null defaults",
                defaults,
                AudioPreferences.PROFILE_WAV,
                false,
                100);
        assertSettings(
                "legacy automatic",
                AudioPreferences.decode(new byte[] {0}),
                AudioPreferences.PROFILE_WAV,
                false,
                100);
        assertSettings(
                "legacy compatible",
                AudioPreferences.decode(new byte[] {1}),
                AudioPreferences.PROFILE_MIDI,
                false,
                100);
        assertSettings(
                "version two automatic",
                AudioPreferences.decode(
                        new byte[] {0x57, 0x34, 2, 0, 75}),
                AudioPreferences.PROFILE_WAV,
                false,
                75);
        assertSettings(
                "version two compatible",
                AudioPreferences.decode(
                        new byte[] {0x57, 0x34, 2, 3, 40}),
                AudioPreferences.PROFILE_MIDI,
                true,
                40);

        AudioPreferences.Settings expected =
                new AudioPreferences.Settings(
                        AudioPreferences.PROFILE_TONE,
                        true,
                        40);
        byte[] encoded = AudioPreferences.encode(expected);
        assertSettings(
                "versioned round trip",
                AudioPreferences.decode(encoded),
                AudioPreferences.PROFILE_TONE,
                true,
                40);

        assertSettings(
                "corrupt record defaults",
                AudioPreferences.decode(new byte[] {0x57, 0x34, 2, 3, (byte) 101}),
                AudioPreferences.PROFILE_WAV,
                false,
                100);
        assertSettings(
                "unknown version defaults",
                AudioPreferences.decode(new byte[] {0x57, 0x34, 3, 0, 50}),
                AudioPreferences.PROFILE_WAV,
                false,
                100);
        assertSettings(
                "unknown profile defaults",
                AudioPreferences.decode(
                        new byte[] {0x57, 0x34, 3, 3, 0, 50}),
                AudioPreferences.PROFILE_WAV,
                false,
                100);

        System.out.println(
                "PASS audio-preferences legacy-v1-v2-migration"
                        + " profiles=wav,midi,tone corrupt-default");
    }

    private static void assertSettings(
            String label,
            AudioPreferences.Settings settings,
            int profile,
            boolean muted,
            int gain) {
        if (settings.profile != profile
                || settings.muted != muted
                || settings.gain != gain) {
            throw new AssertionError(
                    label
                            + ": got profile="
                            + settings.profile
                            + " muted="
                            + settings.muted
                            + " gain="
                            + settings.gain);
        }
    }
}
