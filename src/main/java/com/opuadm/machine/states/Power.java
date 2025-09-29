package com.opuadm.machine.states;

import org.bukkit.entity.Player;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Power {
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

    public Serializable TurnOn() {
        if (isOn) {
            return "E: The machine is already on!";
        } else if (isBooting) {
            return "E: The machine is currently booting!";
        } else {
            isOff = false;
            isBooting = true;

            org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(playerId);

            if (p == null) {
                isBooting = false;
                isOff = true;
                return "E: Player not online to boot the machine.";
            }

            Boot.Init(p);
        }
        return 0;
    }

    public Serializable TurnOff() {
        if (isOff) {
            return "E: The machine is already off!";
        } else if (isBooting) {
            return "E: The machine is currently booting!";
        } else {
            isOn = false;
            isOff = true;
        }
        return 0;
    }

    public synchronized void TurnOnVarChange(Boolean value) {
        if (Boolean.TRUE.equals(value)) {
            isOn = true;
            isOff = false;
            isBooting = false;
        } else {
            isOn = false;
        }
    }

    public synchronized void TurnOffVarChange(Boolean value) {
        if (Boolean.TRUE.equals(value)) {
            isOff = true;
            isOn = false;
            isBooting = false;
        } else {
            isOff = false;
        }
    }

    public synchronized void IsBootingVarChange(Boolean value) {
        if (Boolean.TRUE.equals(value)) {
            isBooting = true;
            isOn = false;
            isOff = false;
        } else {
            isBooting = false;
            if (!isOn && !isOff) {
                isOff = true;
            }
        }
    }
}