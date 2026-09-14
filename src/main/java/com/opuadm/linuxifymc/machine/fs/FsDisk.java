package com.opuadm.linuxifymc.machine.fs;

public final class FsDisk {
    private FsDisk() {
    }

    static void updateDiskSpaceUsage(FakeFS fs) {
        fs.totalDiskSpaceUsed = fs.diskSpaceUsed + userFileBytes(fs);
        fs.diskSpaceFree = fs.maxDiskSpace - fs.totalDiskSpaceUsed;
    }

    static void adjustUserFileBytes(FakeFS fs, long delta) {
        fs.diskSpaceUsedByUserFiles.updateAndGet(v -> Math.max(0L, v + delta));
    }

    static long userFileBytes(FakeFS fs) {
        return fs.diskSpaceUsedByUserFiles.get();
    }
}
