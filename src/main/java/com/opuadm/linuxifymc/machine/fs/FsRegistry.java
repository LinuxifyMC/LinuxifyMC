package com.opuadm.linuxifymc.machine.fs;

import com.opuadm.linuxifymc.LinuxifyMC;

import org.bukkit.entity.Player;
import org.bukkit.Bukkit;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FsRegistry {
    private FsRegistry() {
    }

    static final ConcurrentHashMap<UUID, FakeFS> PLAYER_FS = new ConcurrentHashMap<>();

    public static FakeFS getPlayerFS(UUID plrUUID, String username) {
        Objects.requireNonNull(plrUUID, "E: UUID cannot be null.");
        Objects.requireNonNull(username, "E: Username cannot be null.");
        if (FakeFS.DB == null) {
            if (LinuxifyMC.debugMode) {
                FakeFS.logger.warning("E: Database is not initialized.");
            }
            return null;
        }
        return PLAYER_FS.compute(plrUUID, (id, existing) -> {
            if (existing != null && existing.plr.equalsIgnoreCase(username)) return existing;
            return new FakeFS(id, username);
        });
    }

    public static FakeFS forPlayer(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        Player online = Bukkit.getPlayer(playerId);
        String name = online != null ? online.getName() : Bukkit.getOfflinePlayer(playerId).getName();
        if (name == null || name.isEmpty()) {
            return null;
        }
        return getPlayerFS(playerId, name);
    }
}
