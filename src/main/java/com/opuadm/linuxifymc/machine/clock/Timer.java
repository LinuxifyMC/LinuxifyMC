package com.opuadm.linuxifymc.machine.clock;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.atomic.AtomicLong;

public final class Timer {
    public static final AtomicLong timeSinceTimerStart = new AtomicLong(0L);

    private static BukkitTask task;
    private static final long INC_PER_TICK_MICROS = 50_000L; // 50 ms

    public static synchronized void StartTimer(JavaPlugin plugin) {
        if (task != null) return;
        timeSinceTimerStart.set(0L);
        task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin,
                () -> timeSinceTimerStart.addAndGet(INC_PER_TICK_MICROS),
                0L, 1L
        );
    }

    public static synchronized void StopTimer() {
        if (task != null) { task.cancel(); task = null; }
    }

    public static String getStamp() {
        long micros = timeSinceTimerStart.get();
        long secs = micros / 1_000_000L;
        int microPart = (int) (micros % 1_000_000L);
        return secs + "." + String.format("%06d", microPart);
    }
}