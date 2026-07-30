package w4me.wasm;

import w4me.runtime.Wasm4Runtime;
import w4me.runtime.audio.Wasm4Apu;
import w4me.runtime.storage.DiskBackend;
import w4me.runtime.storage.SnapshotDiskBackend;

/**
 * One in-memory snapshot of the mutable state for an active cartridge session.
 *
 * <p>The snapshot intentionally excludes the interpreter call stack and
 * derived W4IR caches. Capture and restore are valid only between exported
 * cartridge lifecycle calls.
 */
public final class RuntimeSnapshot {
    private static final int FORMAT_VERSION = 1;
    private static final int DISK_CAPACITY = 1024;

    private final int version;
    private final int cartridgeIdentity;
    private final int cartridgeLength;
    private final byte[] memory;
    private final long[] globals;
    private final int[] table;
    private final byte[][] dataSegments;
    private final byte[] disk;
    private final int diskLength;
    private final int[] apu;

    private RuntimeSnapshot(
            int cartridgeIdentity,
            int cartridgeLength,
            byte[] memory,
            long[] globals,
            int[] table,
            byte[][] dataSegments,
            byte[] disk,
            int diskLength,
            int[] apu) {
        version = FORMAT_VERSION;
        this.cartridgeIdentity = cartridgeIdentity;
        this.cartridgeLength = cartridgeLength;
        this.memory = memory;
        this.globals = globals;
        this.table = table;
        this.dataSegments = dataSegments;
        this.disk = disk;
        this.diskLength = diskLength;
        this.apu = apu;
    }

    public static RuntimeSnapshot capture(
            int cartridgeIdentity,
            int cartridgeLength,
            WasmModule module,
            Wasm4Runtime runtime) {
        if (module == null || runtime == null || cartridgeLength < 0) {
            throw new IllegalArgumentException("invalid snapshot source");
        }
        SnapshotDiskBackend diskBackend = snapshotDisk(runtime.disk());

        byte[] memory = new byte[module.snapshotMemoryLength()];
        long[] globals = new long[module.snapshotGlobalCount()];
        int tableLength = module.snapshotTableLength();
        int[] table = tableLength < 0 ? null : new int[tableLength];
        byte[][] dataSegments =
                new byte[module.snapshotDataSegmentCount()][];
        byte[] disk = new byte[DISK_CAPACITY];
        int[] apu = runtime.apu().snapshotState();

        module.captureMutableState(memory, globals, table, dataSegments);
        int diskLength = diskBackend.snapshot(disk);
        if (diskLength < 0 || diskLength > disk.length) {
            throw new IllegalStateException("logical disk snapshot failed");
        }
        return new RuntimeSnapshot(
                cartridgeIdentity,
                cartridgeLength,
                memory,
                globals,
                table,
                dataSegments,
                disk,
                diskLength,
                apu);
    }

    public boolean restore(
            int expectedCartridgeIdentity,
            int expectedCartridgeLength,
            WasmModule module,
            Wasm4Runtime runtime) {
        if (!matches(
                        expectedCartridgeIdentity,
                        expectedCartridgeLength,
                        module,
                        runtime)
                || !snapshotDisk(runtime.disk()).replace(disk, diskLength)) {
            return false;
        }

        module.restoreMutableState(memory, globals, table, dataSegments);
        runtime.apu().restoreState(apu);
        return true;
    }

    public boolean matches(
            int expectedCartridgeIdentity,
            int expectedCartridgeLength,
            WasmModule module,
            Wasm4Runtime runtime) {
        return version == FORMAT_VERSION
                && expectedCartridgeIdentity == cartridgeIdentity
                && expectedCartridgeLength == cartridgeLength
                && module != null
                && runtime != null
                && diskLength >= 0
                && diskLength <= disk.length
                && module.canRestoreMutableState(
                        memory, globals, table, dataSegments)
                && runtime.apu().canRestoreState(apu)
                && runtime.disk() instanceof SnapshotDiskBackend;
    }

    private static SnapshotDiskBackend snapshotDisk(DiskBackend disk) {
        if (!(disk instanceof SnapshotDiskBackend)) {
            throw new IllegalStateException(
                    "logical disk does not support save states");
        }
        return (SnapshotDiskBackend) disk;
    }
}
