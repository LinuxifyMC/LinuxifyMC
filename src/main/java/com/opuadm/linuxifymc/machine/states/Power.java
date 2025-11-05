package com.opuadm.linuxifymc.machine.states;

import org.bukkit.entity.Player;
import org.bukkit.Bukkit;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class Power {
    private static final Logger logger = Logger.getLogger(Power.class.getName());

    private final UUID playerId;
    private boolean isOn = false;
    private boolean isOff = true;
    private boolean isBooting = false;

    // Status 1 = On | Status 0 = Off | Status 2 = Booting | Status -1 = Error

    private static final Map<UUID, Power> registry = new ConcurrentHashMap<>();

    public static Power getFor(@NotNull Player player) {
        return getFor(player.getUniqueId());
    }

    public static Power getFor(@NotNull UUID uuid) {
        return registry.computeIfAbsent(uuid, k -> new Power(uuid));
    }

    public static void removeFor(UUID playerId) {
        if (playerId == null) return;
        registry.remove(playerId);
    }

    private Power(@NotNull UUID uuid) {
        this.playerId = java.util.Objects.requireNonNull(uuid, "player UUID");
    }

    public int checkPowerStatus() {
        if (isOn && !isOff && !isBooting) {
            return 1; // Status 1 = On
        } else if (isOff && !isOn && !isBooting) {
            return 0; // Status 0 = Off
        } else if (isBooting && !isOn && !isOff) {
            return 2; // Status 2 = Booting
        } else {
            return -1;
        }
    }

    public void TurnOn() {
        Player p = Bukkit.getPlayer(playerId);

        if (isOn) {
            if (p != null) p.sendMessage("E: The machine is already on!");
            return;
        }

        if (isBooting) {
            if (p != null) p.sendMessage("E: The machine is currently booting!");
            return;
        }

        isOff = false;
        isBooting = true;

        if (p == null) {
            isBooting = false;
            isOff = true;
            logger.warning("E: Player " + playerId + " not online to boot the machine.");
            return;
        }

        p.sendMessage("Starting boot...");
        Boot.Init(p);
    }

    public void TurnOff() {
        if (isOff) return;

        if (isBooting) {
            isBooting = false;
            isOn = false;
            isOff = true;
            return;
        }

        isOn = false;
        isOff = true;
    }

    public synchronized void ChangeStateVar(Integer status) {
        if (status == 0) {
            isOff = true;
            isOn = false;
            isBooting = false;
        } else if (status == 1) {
            isOn = true;
            isOff = false;
            isBooting = false;
        } else if (status == 2) {
            isBooting = true;
            isOff = false;
            isOn = false;
        }
    }
}