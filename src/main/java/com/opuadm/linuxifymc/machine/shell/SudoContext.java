package com.opuadm.linuxifymc.machine.shell;

import com.opuadm.linuxifymc.machine.login.Login;

import java.util.UUID;

public final class SudoContext {
    private static final ThreadLocal<UUID> PLAYER = new ThreadLocal<>();
    private static final ThreadLocal<String> USER = new ThreadLocal<>();

    private SudoContext() {}

    public static void enter(UUID playerId, String user) {
        if (playerId == null) {
            exit();
            return;
        }
        PLAYER.set(playerId);
        USER.set(user);
    }

    public static void exit() {
        PLAYER.remove();
        USER.remove();
    }

    public static boolean lacksSudoPrivileges(UUID playerId) {
        return !"root".equalsIgnoreCase(getEffectiveUser(playerId, null));
    }

    public static String getEffectiveUser(UUID playerId, String fallback) {
        UUID elevatedPlayer;
        Login session;
        String sessionUser;

        if (playerId == null) return fallback;
        elevatedPlayer = PLAYER.get();
        if (playerId.equals(elevatedPlayer)) return USER.get();
        session = Login.getSession(playerId);
        if (session == null) return fallback;
        sessionUser = session.getCurrentUser();
        return sessionUser == null || sessionUser.isEmpty() ? fallback : sessionUser;
    }
}
