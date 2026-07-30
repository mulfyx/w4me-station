package w4me.runtime.audio;

import java.io.ByteArrayInputStream;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;

/** Provides the MMAPI midi backend smoke implementation. */
public final class MmapiMidiBackendSmoke {
    /** Runs this verification entry point. */
    public static void main(String[] arguments) {
        FakeMidiPlayerFactory factory = new FakeMidiPlayerFactory();
        MmapiMidiBackend backend = new MmapiMidiBackend(factory);

        assertEquals("ready grade", "C-smf4-ready", backend.grade());
        assertEquals("active technology", AudioBackends.PROFILE_MIDI, backend.activeProfileName());
        if (backend.fallbackReason() != null) {
            throw new AssertionError("available MIDI reported a fallback");
        }
        backend.submitTone(440, 50, 100, 0);
        backend.submitTone(262, 20, 75, 1);
        assertEquals("events are coalesced until frame end", 0, factory.openCount);

        backend.tick();
        assertEquals("one Player for two channels", 1, factory.openCount);
        assertEquals("started grade", "C-smf4", backend.grade());
        assertEquals("two active channels", 2, backend.activeChannels());
        requireHeader(factory.lastMidi);
        requireDesktopMidiParser(factory.lastMidi);
        requireEvent(factory.lastMidi, 0x90, 69, 127);
        requireEvent(factory.lastMidi, 0x91, 60, 95);
        requireEvent(factory.lastMidi, 0x90, 69, 0);
        requireEvent(factory.lastMidi, 0x91, 60, 0);

        final FakeMidiPlayback first = factory.lastPlayback;
        backend.submitTone(900, 8, 100, 3);
        backend.tick();
        assertEquals("replacement Player count", 2, factory.openCount);
        if (!first.closed) {
            throw new AssertionError("previous MIDI Player was not closed");
        }
        assertEquals("three active channels", 3, backend.activeChannels());
        requireEvent(factory.lastMidi, 0x90, 69, 127);
        requireEvent(factory.lastMidi, 0x91, 60, 95);
        requireEvent(factory.lastMidi, 0x99, 42, 127);

        final FakeMidiPlayback second = factory.lastPlayback;
        backend.submitTone(0, 0, 0, 0);
        backend.submitTone(0, 0, 0, 1);
        backend.submitTone(0, 0, 0, 3);
        backend.tick();
        assertEquals("stopping all channels opens no Player", 2, factory.openCount);
        assertEquals("all channels stopped", 0, backend.activeChannels());
        if (!second.closed) {
            throw new AssertionError("MIDI Player was not closed after all-channel stop");
        }

        backend.close();
        MmapiMidiBackend unavailable = new MmapiMidiBackend(null);
        assertEquals(
                "unavailable MIDI falls back to tones", AudioBackends.PROFILE_TONE, unavailable.activeProfileName());
        assertEquals("unavailable MIDI reason", "MMAPI MIDI playback unavailable", unavailable.fallbackReason());
        System.out.println(
                "PASS mmapi-smf one-player polyphony frame-coalescing" + " lifecycle explicit-profile-status");
    }

    private static void requireHeader(byte[] midi) {
        requireAscii(midi, 0, "MThd");
        assertEquals("header length", 6, readInt(midi, 4));
        assertEquals("SMF format", 1, readShort(midi, 8));
        assertEquals("track count", 2, readShort(midi, 10));
        assertEquals("ticks per quarter", 60, readShort(midi, 12));
        requireAscii(midi, 14, "MTrk");
        assertEquals("tempo track length", 11, readInt(midi, 18));
        requireAscii(midi, 33, "MTrk");
        int musicLength = readInt(midi, 37);
        assertEquals("music track reaches EOF", midi.length, 41 + musicLength);
        requireEvent(midi, 0xff, 0x51, 0x03);
        requireEvent(midi, 0xff, 0x2f, 0x00);
    }

    private static void requireDesktopMidiParser(byte[] midi) {
        try {
            Sequence sequence = MidiSystem.getSequence(new ByteArrayInputStream(midi));
            assertEquals("parsed MIDI tracks", 2, sequence.getTracks().length);
            assertEquals("parsed MIDI resolution", 60, sequence.getResolution());
        } catch (Exception failure) {
            throw new AssertionError( // NOPMD -- CLDC 1.1 has no cause chaining.
                    "desktop MIDI parser rejected SMF: " + failure);
        }
    }

    private static void requireEvent(byte[] data, int first, int second, int third) {
        int index;
        for (index = 0; index + 2 < data.length; index++) {
            if ((data[index] & 0xff) == first
                    && (data[index + 1] & 0xff) == second
                    && (data[index + 2] & 0xff) == third) {
                return;
            }
        }
        throw new AssertionError("missing MIDI bytes " + first + "," + second + "," + third);
    }

    private static void requireAscii(byte[] data, int offset, String expected) {
        int index;
        for (index = 0; index < expected.length(); index++) {
            if ((data[offset + index] & 0xff) != expected.charAt(index)) {
                throw new AssertionError("missing " + expected + " at " + offset);
            }
        }
    }

    private static int readShort(byte[] data, int offset) {
        return ((data[offset] & 0xff) << 8) | (data[offset + 1] & 0xff);
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xff) << 24)
                | ((data[offset + 1] & 0xff) << 16)
                | ((data[offset + 2] & 0xff) << 8)
                | (data[offset + 3] & 0xff);
    }

    private static void assertEquals(String label, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(String label, String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    private static final class FakeMidiPlayerFactory implements MmapiMidiBackend.MidiPlayerFactory {
        private int openCount;
        private byte[] lastMidi;
        private FakeMidiPlayback lastPlayback;

        public MmapiMidiBackend.MidiPlayback open(byte[] midi) {
            openCount++;
            lastMidi = midi;
            lastPlayback = new FakeMidiPlayback();
            return lastPlayback;
        }
    }

    private static final class FakeMidiPlayback implements MmapiMidiBackend.MidiPlayback {
        private boolean closed;

        public void close() {
            closed = true;
        }
    }
}
