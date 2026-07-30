package w4me.runtime.audio;

import java.io.ByteArrayInputStream;

import javax.microedition.media.Manager;
import javax.microedition.media.Player;

/** Four independent synthesized WASM-4 channels over MMAPI WAV players. */
public final class MmapiPcmBackend
        implements AudioBackend,
                AudioControl,
                AudioBackendStatus,
                AudioDiagnostics {
    private final Player[] players = new Player[4];
    private AudioBackend fallback;
    private boolean pcmAvailable;
    private boolean pcmStarted;
    private String pcmFailureReason;
    private boolean diagnostic;

    public MmapiPcmBackend() {
        boolean mixing = supportsMixing();
        boolean wav = supportsWav();
        pcmAvailable = mixing && wav;
        if (!mixing) {
            pcmFailureReason = "Concurrent WAV playback unavailable";
        } else if (!wav) {
            pcmFailureReason = "MMAPI WAV playback unavailable";
        }
    }

    public synchronized void submitTone(int frequency, int duration, int volume, int flags) {
        int channel = flags & 3;
        closeChannel(channel, "replace");
        if (!pcmAvailable) {
            fallback().submitTone(frequency, duration, volume, flags);
            return;
        }

        Player player = null;
        String phase = "synthesize";
        long started = diagnostic ? System.currentTimeMillis() : 0;
        long synthesized = started;
        long created = started;
        long realized = started;
        long prefetched = started;
        try {
            byte[] wav = Wasm4Pcm.synthesize(frequency, duration, volume, flags);
            synthesized = diagnostic ? System.currentTimeMillis() : 0;
            if (wav == null) {
                return;
            }
            phase = "create";
            player = Manager.createPlayer(new ByteArrayInputStream(wav), "audio/x-wav");
            created = diagnostic ? System.currentTimeMillis() : 0;
            phase = "realize";
            player.realize();
            realized = diagnostic ? System.currentTimeMillis() : 0;
            phase = "prefetch";
            player.prefetch();
            prefetched = diagnostic ? System.currentTimeMillis() : 0;
            phase = "start";
            player.start();
            long playerStarted = diagnostic ? System.currentTimeMillis() : 0;
            if (player.getState() != Player.STARTED) {
                throw new IllegalStateException("MMAPI PCM player did not start");
            }
            players[channel] = player;
            pcmStarted = true;
            if (diagnostic) {
                System.out.println(
                        "W4ME_PCM_LIFECYCLE channel="
                                + channel
                                + " bytes="
                                + wav.length
                                + " synth-ms="
                                + (synthesized - started)
                                + " create-ms="
                                + (created - synthesized)
                                + " realize-ms="
                                + (realized - created)
                                + " prefetch-ms="
                                + (prefetched - realized)
                                + " start-ms="
                                + (playerStarted - prefetched)
                                + " total-ms="
                                + (playerStarted - started));
            }
        } catch (Throwable unavailable) {
            if (diagnostic) {
                System.out.println(
                        "W4ME_PCM_LIFECYCLE_FAILURE channel="
                                + channel
                                + " phase="
                                + phase
                                + " error="
                                + unavailable.toString());
            }
            closePlayer(player, "failed-open");
            disablePcm();
            fallback().submitTone(frequency, duration, volume, flags);
        }
    }

    public synchronized void tick() {
        int channel;
        for (channel = 0; channel < players.length; channel++) {
            Player player = players[channel];
            if (player != null && player.getState() != Player.STARTED) {
                closeChannel(channel, "ended");
            }
        }
        if (fallback != null) {
            fallback.tick();
        }
    }

    public synchronized void close() {
        int channel;
        for (channel = 0; channel < players.length; channel++) {
            closeChannel(channel, "shutdown");
        }
        if (fallback != null) {
            fallback.close();
        }
    }

    public String grade() {
        if (!pcmAvailable) {
            return fallback().grade();
        }
        return pcmStarted ? "C-pcm4" : "C-pcm4-ready";
    }

    public String activeProfileName() {
        return pcmAvailable
                ? AudioBackends.PROFILE_WAV
                : AudioBackends.activeProfileName(fallback());
    }

    public String fallbackReason() {
        if (pcmAvailable) {
            return null;
        }
        String nested = AudioBackends.fallbackReason(fallback());
        return nested == null
                ? pcmFailureReason
                : pcmFailureReason + "; " + nested;
    }

    public void setAudioDiagnostics(boolean enabled) {
        diagnostic = enabled;
        if (fallback instanceof AudioDiagnostics) {
            ((AudioDiagnostics) fallback).setAudioDiagnostics(enabled);
        }
    }

    public synchronized void silence() {
        int channel;
        for (channel = 0; channel < players.length; channel++) {
            closeChannel(channel, "silence");
        }
        silence(fallback);
    }

    public int volumeCapability() {
        if (pcmAvailable) {
            return VOLUME_CONTINUOUS;
        }
        return capability(fallback());
    }

    public int activeChannels() {
        int active = 0;
        int channel;
        for (channel = 0; channel < players.length; channel++) {
            Player player = players[channel];
            if (player != null && player.getState() == Player.STARTED) {
                active++;
            }
        }
        return active;
    }

    private boolean supportsWav() {
        try {
            String[] types = Manager.getSupportedContentTypes(null);
            int index;
            for (index = 0; index < types.length; index++) {
                String type = types[index].toLowerCase();
                if (type.equals("audio/x-wav")
                        || type.equals("audio/wav")
                        || type.equals("audio/wave")) {
                    return true;
                }
            }
        } catch (Throwable unavailable) {
            return false;
        }
        return false;
    }

    private boolean supportsMixing() {
        try {
            // Four WASM-4 channels require concurrent sampled-audio Players.
            // A WAV MIME entry alone does not promise that the device can mix them.
            return "true".equals(System.getProperty("supports.mixing"));
        } catch (Throwable unavailable) {
            return false;
        }
    }

    private AudioBackend fallback() {
        if (fallback == null) {
            fallback = AudioBackends.createMidiFallback();
            if (fallback instanceof AudioDiagnostics) {
                ((AudioDiagnostics) fallback).setAudioDiagnostics(diagnostic);
            }
        }
        return fallback;
    }

    private void disablePcm() {
        pcmAvailable = false;
        pcmFailureReason = "MMAPI WAV Player failed";
        int channel;
        for (channel = 0; channel < players.length; channel++) {
            closeChannel(channel, "fallback");
        }
    }

    private void closeChannel(int channel, String reason) {
        Player player = players[channel];
        players[channel] = null;
        closePlayer(player, reason);
    }

    private void closePlayer(Player player, String reason) {
        if (player == null) {
            return;
        }
        long started = diagnostic ? System.currentTimeMillis() : 0;
        try {
            player.stop();
        } catch (Throwable ignored) {
            // Some implementations already stop a player when media ends.
        }
        long stopped = diagnostic ? System.currentTimeMillis() : 0;
        try {
            player.close();
        } catch (Throwable ignored) {
            // Best effort during channel replacement or MIDlet shutdown.
        }
        if (diagnostic) {
            long closed = System.currentTimeMillis();
            System.out.println(
                    "W4ME_PCM_CLOSE reason="
                            + reason
                            + " stop-ms="
                            + (stopped - started)
                            + " close-ms="
                            + (closed - stopped)
                            + " total-ms="
                            + (closed - started));
        }
    }

    private static int capability(AudioBackend backend) {
        if (backend instanceof AudioControl) {
            return ((AudioControl) backend).volumeCapability();
        }
        return VOLUME_CONTINUOUS;
    }

    private static void silence(AudioBackend backend) {
        if (backend instanceof AudioControl) {
            ((AudioControl) backend).silence();
        }
    }
}
