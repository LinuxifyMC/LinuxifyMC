package com.opuadm.linuxifymc.machine.clock;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class Timer {
    private static final Map<UUID, AtomicLong> userTimers = new ConcurrentHashMap<>();
    private static final Map<UUID, BukkitTask> userTasks = new ConcurrentHashMap<>();
    private static final long INC_PER_TICK_MICROS = 1L;

    public static synchronized void StartTimer(JavaPlugin plugin, UUID uuid) {
        if (userTasks.containsKey(uuid)) return;
        
        userTimers.put(uuid, new AtomicLong(0L));
        
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin,
                () -> {
                    AtomicLong timer = userTimers.get(uuid);
                    if (timer != null) {
                        timer.addAndGet(INC_PER_TICK_MICROS);
                    }
                },
                0L, 1L
        );
        userTasks.put(uuid, task);
    }

    public static synchronized void StopTimer(UUID uuid) {
        BukkitTask task = userTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        userTimers.remove(uuid);
    }

    public static String getStamp(UUID uuid) {
        AtomicLong timer = userTimers.get(uuid);
        long micros = timer != null ? timer.get() : 0L;
        long secs = micros / 1_000_000L;
        int microPart = (int) (micros % 1_000_000L);
        return secs + "." + String.format("%06d", microPart);
    }
}