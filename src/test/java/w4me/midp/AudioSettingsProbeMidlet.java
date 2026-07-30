package w4me.midp;

import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Gauge;
import javax.microedition.lcdui.StringItem;
import javax.microedition.rms.RecordStore;
import w4me.runtime.audio.AudioBackend;
import w4me.runtime.audio.AudioControl;
import w4me.runtime.audio.Wasm4Apu;

/** KEmulator probe for CLDC/RMS audio-setting behavior. */
public final class AudioSettingsProbeMidlet extends W4MeMidlet {
    private boolean started;

    /** Performs the start app operation. */
    protected void startApp() {
        if (started) {
            return;
        }
        started = true;
        Form result = new Form("Audio settings probe");
        Display.getDisplay(this).setCurrent(result);
        try {
            resetStore();
            AudioPreferences.Settings stored = new AudioPreferences.Settings(AudioPreferences.PROFILE_MIDI, true, 50);
            if (!AudioPreferences.save(stored)) {
                throw new AssertionError("RMS save failed");
            }

            AudioPreferences.Settings loaded = AudioPreferences.load();
            assertSettings(loaded, AudioPreferences.PROFILE_MIDI, true, 50);
            assertFormState(AudioPreferences.PROFILE_WAV, 100, 0, "100%");
            assertFormState(AudioPreferences.PROFILE_MIDI, 50, 1, "50%");
            assertFormState(AudioPreferences.PROFILE_TONE, 75, 2, "75%");

            RecordingBackend backend = new RecordingBackend();
            Wasm4Apu apu = new Wasm4Apu(backend);
            apu.setMasterGain(loaded.gain);
            apu.tone(440, 30, 100, 0);
            if (backend.lastVolume != 0x3232) {
                throw new AssertionError("gain was not applied");
            }
            apu.setMuted(loaded.muted);
            int submitted = backend.submitCount;
            apu.tone(330, 30, 100, 0);
            if (backend.submitCount != submitted || backend.silenceCount == 0) {
                throw new AssertionError("active mute did not silence and suppress");
            }

            AudioPreferences.Settings restarted = AudioPreferences.load();
            RecordingBackend restartedBackend = new RecordingBackend();
            Wasm4Apu restartedApu = new Wasm4Apu(restartedBackend);
            restartedApu.setMasterGain(restarted.gain);
            restartedApu.setMuted(restarted.muted);
            restartedApu.tone(262, 30, 100, 0);
            if (restartedBackend.submitCount != 0) {
                throw new AssertionError("persisted mute was not applied before first tone");
            }

            Wasm4Apu muteOnly = new Wasm4Apu(new MuteOnlyBackend());
            if (muteOnly.volumeCapability() != AudioControl.MUTE_ONLY) {
                throw new AssertionError("mute-only capability was not forwarded");
            }

            System.out.println("W4ME_AUDIO_SETTINGS_PROBE active-mute=PASS persisted-mute=PASS"
                    + " gain=50 scaled=12850 capability=MUTE_ONLY"
                    + " form-gain=100 form-mode=PASS");
            resetStore();
            showAudioSettings(null);
        } catch (Throwable failure) { // NOPMD -- Java ME API linkage fallback.
            System.out.println("W4ME_AUDIO_SETTINGS_ERROR " + failure.toString());
            result.append("FAIL\n" + failure.toString());
        }
    }

    /** Performs the pause app operation. */
    protected void pauseApp() {
        /* Intentionally no-op. */
    }

    /** Performs the destroy app operation. */
    protected void destroyApp(boolean unconditional) {
        /* Intentionally no-op. */
    }

    private void resetStore() {
        try {
            RecordStore.deleteRecordStore("w4audio1");
        } catch (Throwable missing) { // NOPMD -- Java ME API linkage fallback.
            // The expected missing-backend path has no cleanup work.
            // A fresh emulator profile has no settings store yet.
        }
    }

    private void assertSettings(AudioPreferences.Settings settings, int profile, boolean muted, int gain) {
        if (settings.profile != profile || settings.muted != muted || settings.gain != gain) {
            throw new AssertionError("RMS settings did not round-trip");
        }
    }

    private void assertFormState(int profile, int gain, int expectedMode, String expectedText) {
        AudioSettingsForm form =
                new AudioSettingsForm(null, null, AudioControl.VOLUME_CONTINUOUS, profile, false, gain);
        ChoiceGroup mode = (ChoiceGroup) form.get(0);
        Gauge volume = (Gauge) form.get(3);
        StringItem value = (StringItem) form.get(4);
        if (mode.getSelectedIndex() != expectedMode
                || volume.getValue() != gain
                || !expectedText.equals(value.getText())) {
            throw new AssertionError("settings form state mismatch: mode="
                    + mode.getSelectedIndex()
                    + " gain="
                    + volume.getValue()
                    + " text="
                    + value.getText());
        }
    }

    private static class MuteOnlyBackend implements AudioBackend, AudioControl {
        public void submitTone(int frequency, int duration, int volume, int flags) {
            /* Intentionally no-op. */
        }

        public void tick() {
            /* Intentionally no-op. */
        }

        public void close() {
            /* Intentionally no-op. */
        }

        public String grade() {
            return "test-mute-only";
        }

        public int volumeCapability() {
            return MUTE_ONLY;
        }

        public void silence() {
            /* Intentionally no-op. */
        }
    }

    private static final class RecordingBackend extends MuteOnlyBackend {
        private int submitCount;
        private int silenceCount;
        private int lastVolume;

        public void submitTone(int frequency, int duration, int volume, int flags) {
            submitCount++;
            lastVolume = volume;
        }

        public int volumeCapability() {
            return VOLUME_CONTINUOUS;
        }

        public void silence() {
            silenceCount++;
        }
    }
}
