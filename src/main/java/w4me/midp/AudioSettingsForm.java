package w4me.midp;

import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Gauge;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.ItemStateListener;
import javax.microedition.lcdui.StringItem;
import w4me.runtime.audio.AudioBackends;
import w4me.runtime.audio.AudioControl;

/** Global sound controls shared by all cartridges. */
final class AudioSettingsForm extends Form implements CommandListener, ItemStateListener {
    private final W4MeMidlet midlet;
    private final W4Canvas source;
    private final SettingsList settings;
    private final int capability;
    private final ChoiceGroup modeChoice;
    private final ChoiceGroup soundChoice;
    private final Gauge volumeGauge;
    private final StringItem volumeValue;
    private final Command saveCommand = new Command("Save", Command.OK, 1);
    private final Command cancelCommand = new Command("Cancel", Command.CANCEL, 2);

    AudioSettingsForm(
            W4MeMidlet midlet, W4Canvas source, int capability, boolean compatibilityMode, boolean muted, int gain) {
        this(
                midlet,
                source,
                null,
                capability,
                compatibilityMode ? AudioPreferences.PROFILE_MIDI : AudioPreferences.PROFILE_WAV,
                muted,
                gain);
    }

    AudioSettingsForm(W4MeMidlet midlet, W4Canvas source, int capability, int profile, boolean muted, int gain) {
        this(midlet, source, null, capability, profile, muted, gain);
    }

    AudioSettingsForm(
            W4MeMidlet midlet,
            W4Canvas source,
            SettingsList settings,
            int capability,
            int profile,
            boolean muted,
            int gain) {
        super("Audio");
        this.midlet = midlet;
        this.source = source;
        this.settings = settings;
        this.capability = capability;

        modeChoice = new ChoiceGroup(
                "Audio mode",
                ChoiceGroup.EXCLUSIVE,
                new String[] {AudioBackends.PROFILE_WAV, AudioBackends.PROFILE_MIDI, AudioBackends.PROFILE_TONE},
                null);
        modeChoice.setSelectedIndex(AudioPreferences.isProfile(profile) ? profile : AudioPreferences.PROFILE_WAV, true);
        append(modeChoice);
        append(new StringItem(
                null,
                "WAV synthesis: generated WASM-4 waveforms through"
                        + " MMAPI WAV Players.\n"
                        + "MIDI synthesis: approximate instruments through"
                        + " one Standard MIDI File Player.\n"
                        + "Simple tones: monophonic Manager.playTone output."));
        if (source != null) {
            String active = source.activeAudioProfileName();
            final String reason = source.audioFallbackReason();
            StringBuffer status = new StringBuffer();
            status.append("Preferred: ");
            status.append(profileName(profile));
            status.append("\nActive: ");
            status.append(active == null ? "Not initialized" : active);
            if (reason != null && reason.length() != 0) {
                status.append("\nReason: ");
                status.append(reason);
            }
            append(new StringItem(
                    "Output", status.toString() + "\nProfile changes apply when the cartridge is reopened."));
        }

        if (capability == AudioControl.SILENT) {
            soundChoice = null;
            volumeGauge = null;
            volumeValue = null;
            append(new StringItem("Sound", "Unavailable on the current audio backend."));
        } else {
            soundChoice = new ChoiceGroup("Sound", ChoiceGroup.MULTIPLE, new String[] {"Enabled"}, null);
            soundChoice.setSelectedIndex(0, !muted);
            append(soundChoice);

            if (capability == AudioControl.VOLUME_CONTINUOUS) {
                int initialGain = clampGain(gain);
                volumeGauge = new Gauge("Volume", true, 100, initialGain);
                volumeValue = new StringItem(null, "");
                append(volumeGauge);
                volumeGauge.setValue(initialGain);
                append(volumeValue);
                updateVolumeValue();
            } else {
                volumeGauge = null;
                volumeValue = null;
                append(new StringItem("Volume", "This backend supports On/Off only."));
            }
        }

        addCommand(saveCommand);
        addCommand(cancelCommand);
        setCommandListener(this);
        setItemStateListener(this);
    }

    public void commandAction(Command command, Displayable displayable) {
        if (command == saveCommand) {
            boolean muted = capability == AudioControl.SILENT ? midlet.soundMuted() : !soundChoice.isSelected(0);
            int gain = volumeGauge == null ? midlet.audioGain() : volumeGauge.getValue();
            midlet.finishAudioSettings(source, settings, true, modeChoice.getSelectedIndex(), muted, gain);
        } else {
            midlet.finishAudioSettings(
                    source, settings, false, midlet.preferredAudioProfile(), midlet.soundMuted(), midlet.audioGain());
        }
    }

    public void itemStateChanged(Item item) {
        if (item == volumeGauge) {
            updateVolumeValue();
        }
    }

    private void updateVolumeValue() {
        volumeValue.setText(Integer.toString(volumeGauge.getValue()) + "%");
    }

    private int clampGain(int gain) {
        if (gain < 0) {
            return 0;
        }
        return gain > 100 ? 100 : gain;
    }

    private String profileName(int profile) {
        if (profile == AudioPreferences.PROFILE_MIDI) {
            return AudioBackends.PROFILE_MIDI;
        }
        if (profile == AudioPreferences.PROFILE_TONE) {
            return AudioBackends.PROFILE_TONE;
        }
        return AudioBackends.PROFILE_WAV;
    }
}
