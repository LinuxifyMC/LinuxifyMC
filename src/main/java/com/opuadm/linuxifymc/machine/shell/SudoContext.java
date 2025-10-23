package com.opuadm.linuxifymc.machine.shell;

public final class SudoContext {
    private static final ThreadLocal<Boolean> TL = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private SudoContext() {}

    public static void enter() { TL.set(Boolean.TRUE); }

    public static void exit() { TL.set(Boolean.FALSE); }

    public static boolean isSudo() { return Boolean.TRUE.equals(TL.get()); }
}