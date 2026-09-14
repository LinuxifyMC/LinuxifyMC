package com.opuadm.linuxifymc.machine.bin;

import com.opuadm.linuxifymc.machine.fs.FakeFS;

public interface BinContext {
    void out(String line);
    FakeFS fs();
    String stdin();
    String user();
    long time();
}
