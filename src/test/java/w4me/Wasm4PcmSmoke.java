package w4me;

import w4me.runtime.audio.Wasm4Pcm;

public final class Wasm4PcmSmoke {
    private static final int WAV_HEADER_SIZE = 44;

    public static void main(String[] arguments) {
        byte[] pulse = Wasm4Pcm.synthesize(440, 60, 25700, 0);
        assertEquals("pulse WAV bytes", 8044, pulse.length);
        assertAscii("RIFF", pulse, 0);
        assertAscii("WAVE", pulse, 8);
        assertAscii("data", pulse, 36);
        assertEquals("PCM format", 1, readShortLe(pulse, 20));
        assertEquals("mono channels", 1, readShortLe(pulse, 22));
        assertEquals("sample rate", 8000, readIntLe(pulse, 24));
        assertEquals("PCM bits", 8, readShortLe(pulse, 34));
        assertEquals("PCM data bytes", 8000, readIntLe(pulse, 40));
        assertSilentEdges("pulse", pulse);

        byte[] pulseAgain = Wasm4Pcm.synthesize(440, 60, 25700, 0);
        assertBytes("deterministic pulse", pulse, pulseAgain);
        byte[] noteMode = Wasm4Pcm.synthesize(69, 60, 25700, 0x40);
        assertBytes("note mode A4", pulse, noteMode);

        byte[] mode2 = Wasm4Pcm.synthesize(440, 60, 25700, 4);
        byte[] pulse2 = Wasm4Pcm.synthesize(440, 60, 25700, 1);
        byte[] triangle = Wasm4Pcm.synthesize(440, 60, 25700, 2);
        byte[] noise = Wasm4Pcm.synthesize(440, 60, 25700, 3);
        byte[] noiseAgain = Wasm4Pcm.synthesize(440, 60, 25700, 3);
        byte[] retrigger = Wasm4Pcm.synthesize(660, 30, 25700, 0);
        assertDifferent("pulse duty", pulse, mode2);
        assertBytes("second pulse channel", pulse, pulse2);
        assertDifferent("triangle waveform", pulse, triangle);
        assertDifferent("noise waveform", pulse, noise);
        assertBytes("deterministic noise", noise, noiseAgain);
        assertSilentEdges("triangle", triangle);
        assertSilentEdges("noise", noise);
        assertSequentialBoundary("same-channel retrigger", pulse, retrigger);

        byte[] slide = Wasm4Pcm.synthesize(440 | (880 << 16), 60, 25700, 0);
        assertDifferent("pitch slide", pulse, slide);

        int envelopeDuration = (2 << 24) | (2 << 16) | (2 << 8) | 2;
        byte[] envelope = Wasm4Pcm.synthesize(440, envelopeDuration, (100 << 8) | 80, 0);
        assertEquals("ADSR starts silent", 128, envelope[WAV_HEADER_SIZE] & 0xff);
        int attackPeakDistance = distanceFromCenter(envelope, sampleAtFrame(2));
        int releaseTailDistance = distanceFromCenter(envelope, sampleAtFrame(7));
        assertTrue("ADSR attack reaches peak", attackPeakDistance > 80);
        assertTrue("ADSR release fades", releaseTailDistance < attackPeakDistance);
        assertSilentEdges("ADSR", envelope);

        byte[] left = Wasm4Pcm.synthesize(440, 60, 25700, 0x10);
        byte[] right = Wasm4Pcm.synthesize(440, 60, 25700, 0x20);
        assertEquals("left stereo channels", 2, readShortLe(left, 22));
        assertEquals("left stereo bytes", 16000, readIntLe(left, 40));
        assertEquals("left silent right lane", 128, left[WAV_HEADER_SIZE + 1] & 0xff);
        assertTrue(
                "left audible left lane",
                (left[WAV_HEADER_SIZE + 8 * 2] & 0xff) != 128);
        assertEquals("right silent left lane", 128, right[WAV_HEADER_SIZE] & 0xff);
        assertTrue(
                "right audible right lane",
                (right[WAV_HEADER_SIZE + 8 * 2 + 1] & 0xff) != 128);
        assertSilentEdges("left pan", left);
        assertSilentEdges("right pan", right);

        assertNull("zero frequency", Wasm4Pcm.synthesize(0, 60, 100, 0));
        assertNull("zero duration", Wasm4Pcm.synthesize(440, 0, 100, 0));
        assertNull("inaudible envelope", Wasm4Pcm.synthesize(440, 60, 0, 0));

        System.out.println(
                "PASS pcm waveforms=pulse,triangle,noise channels=4 "
                        + "ADSR=exact slide=exact pan=stereo note-mode=exact "
                        + "edge-ramp=1ms-in-duration");
    }

    private static void assertSilentEdges(String label, byte[] wav) {
        int channels = readShortLe(wav, 22);
        int dataLength = readIntLe(wav, 40);
        int channel;
        for (channel = 0; channel < channels; channel++) {
            assertEquals(
                    label + " start channel " + channel,
                    128,
                    wav[WAV_HEADER_SIZE + channel] & 0xff);
            assertEquals(
                    label + " end channel " + channel,
                    128,
                    wav[WAV_HEADER_SIZE + dataLength - channels + channel] & 0xff);
        }
    }

    private static void assertSequentialBoundary(
            String label, byte[] first, byte[] second) {
        int firstChannels = readShortLe(first, 22);
        int secondChannels = readShortLe(second, 22);
        assertEquals(label + " first channels", 1, firstChannels);
        assertEquals(label + " second channels", 1, secondChannels);
        int firstEnd =
                first[WAV_HEADER_SIZE + readIntLe(first, 40) - 1] & 0xff;
        int secondStart = second[WAV_HEADER_SIZE] & 0xff;
        assertEquals(label + " first end", 128, firstEnd);
        assertEquals(label + " second start", 128, secondStart);
        assertEquals(
                label + " boundary step",
                0,
                firstEnd > secondStart
                        ? firstEnd - secondStart
                        : secondStart - firstEnd);
    }

    private static int sampleAtFrame(int frame) {
        return (frame * 8000 + 59) / 60;
    }

    private static int distanceFromCenter(byte[] wav, int sample) {
        int value = wav[WAV_HEADER_SIZE + sample] & 0xff;
        int distance = value - 128;
        return distance < 0 ? -distance : distance;
    }

    private static int readShortLe(byte[] data, int offset) {
        return (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8);
    }

    private static int readIntLe(byte[] data, int offset) {
        return (data[offset] & 0xff)
                | ((data[offset + 1] & 0xff) << 8)
                | ((data[offset + 2] & 0xff) << 16)
                | ((data[offset + 3] & 0xff) << 24);
    }

    private static void assertAscii(String expected, byte[] actual, int offset) {
        int index;
        for (index = 0; index < expected.length(); index++) {
            if (actual[offset + index] != (byte) expected.charAt(index)) {
                throw new AssertionError(expected + " header mismatch at byte " + index);
            }
        }
    }

    private static void assertBytes(String label, byte[] expected, byte[] actual) {
        assertEquals(label + " length", expected.length, actual.length);
        int index;
        for (index = 0; index < expected.length; index++) {
            if (expected[index] != actual[index]) {
                throw new AssertionError(label + " differs at byte " + index);
            }
        }
    }

    private static void assertDifferent(String label, byte[] first, byte[] second) {
        if (first.length != second.length) {
            return;
        }
        int index;
        for (index = 0; index < first.length; index++) {
            if (first[index] != second[index]) {
                return;
            }
        }
        throw new AssertionError(label + " unexpectedly produced identical PCM");
    }

    private static void assertNull(String label, Object value) {
        if (value != null) {
            throw new AssertionError(label + ": expected null");
        }
    }

    private static void assertTrue(String label, boolean condition) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    private static void assertEquals(String label, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
