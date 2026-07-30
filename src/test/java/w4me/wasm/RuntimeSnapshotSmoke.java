package w4me.wasm;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import w4me.runtime.Wasm4Runtime;
import w4me.runtime.audio.AudioBackend;
import w4me.runtime.audio.Wasm4Apu;
import w4me.runtime.storage.MemoryDiskBackend;

/** Provides the runtime snapshot smoke implementation. */
public final class RuntimeSnapshotSmoke {
    private RuntimeSnapshotSmoke() {}

    /** Runs this verification entry point. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("usage: font.bin save-state-roundtrip.wasm");
        }

        WasmModule module = WasmModule.read(readFile(arguments[1]));
        MemoryDiskBackend disk = new MemoryDiskBackend();
        Wasm4Apu apu = new Wasm4Apu(new RecordingAudio());
        Wasm4Runtime runtime = new Wasm4Runtime(readFile(arguments[0]), apu, disk);
        runtime.initialize(module);

        final byte[] originalData = module.dataSegments[0];
        module.memory[30000] = 42;
        module.globals[0] = 0x1122334455667788L;
        module.table[0] = 0;
        byte[] diskBytes = {9, 8, 7, 6};
        assertEquals("disk write", 4, disk.write(diskBytes, 0, diskBytes.length));
        apu.tone((660 << 16) | 440, 0x01020304, 0x6450, 0);
        apu.tick();
        apu.tick();

        final int savedFrequency = apu.channelFrequency(0);
        final int savedVolume = apu.channelVolume(0);
        final int savedEvents = apu.toneEventCount();
        final RuntimeSnapshot snapshot = RuntimeSnapshot.capture(0x12345678, 321, module, runtime);

        module.memory[30000] = 99;
        module.globals[0] = -1L;
        module.table[0] = -1;
        module.dataSegments[0] = WasmModule.EMPTY_DATA_SEGMENT;
        byte[] replacementDisk = {1, 2};
        disk.write(replacementDisk, 0, replacementDisk.length);
        apu.tone(220, 1, 10, 0);
        apu.tick();

        module.memory[30001] = 55;
        assertTrue("identity mismatch rejected", !snapshot.restore(0x12345679, 321, module, runtime));
        assertEquals("identity mismatch leaves memory", 55, module.memory[30001] & 0xff);

        assertTrue("snapshot restore", snapshot.restore(0x12345678, 321, module, runtime));
        assertEquals("memory", 42, module.memory[30000] & 0xff);
        assertEquals("memory neighboring byte", 0, module.memory[30001] & 0xff);
        assertEquals("global", 0x1122334455667788L, module.globals[0]);
        assertEquals("table", 0, module.table[0]);
        assertTrue("passive segment reference restored", module.dataSegments[0] == originalData);
        assertEquals("passive segment size", 5, module.dataSegments[0].length);
        assertEquals("passive byte", 'S', module.dataSegments[0][0] & 0xff);

        byte[] restoredDisk = new byte[1024];
        int restoredLength = disk.snapshot(restoredDisk);
        assertEquals("disk length", 4, restoredLength);
        assertEquals("disk byte 0", 9, restoredDisk[0] & 0xff);
        assertEquals("disk byte 3", 6, restoredDisk[3] & 0xff);
        assertEquals("APU frequency", savedFrequency, apu.channelFrequency(0));
        assertEquals("APU volume", savedVolume, apu.channelVolume(0));
        assertEquals("APU events", savedEvents, apu.toneEventCount());
        assertEquals("APU last frequency", (660 << 16) | 440, apu.lastFrequency());

        runtime.close();
        module.close();
        System.out.println("PASS save-state memory globals table passive disk APU identity");
    }

    private static byte[] readFile(String path) throws Exception {
        InputStream input = new FileInputStream(path);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private static void assertTrue(String label, boolean value) {
        if (!value) {
            throw new AssertionError(label);
        }
    }

    private static void assertEquals(String label, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(String label, long expected, long actual) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    private static final class RecordingAudio implements AudioBackend {
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
            return "snapshot-test";
        }
    }
}
