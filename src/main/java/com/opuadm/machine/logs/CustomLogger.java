// Custom Logger, all logs will be printed in /var/log/<date>-<time>-<number>.log. Those logs will have CHMOD 777 permissions.
package com.opuadm.machine.logs;

import com.opuadm.commands.cli.FakeFS;

import org.bukkit.entity.Player;

public class CustomLogger {
    public static String logFilePath = "/var/log/linuxifymc.log"; // Default log file path
    public static String miscLogFilePath = "/var/log/linuxifymc-misc.log"; // Miscellaneous log file path
    public static String bootLogFilePath = "/var/log/linuxifymc-boot.log"; // Boot log file path

    public static void BootLog(Player player, Levels level, String message) {
        if (player == null) return;
        FakeFS playerFS = FakeFS.getPlayerFS(player.getUniqueId(), player.getName());
        String logMessage = "[ " + level.name() + " ] " + message;
        if (playerFS.getFile(bootLogFilePath) != null) {
            playerFS.appendToFile(bootLogFilePath, "\n" + logMessage);
        } else {
            playerFS.createFile(bootLogFilePath, logMessage);
        }
    }

    public static void Log(Player player, Levels level, String message) {
        if (player == null) return;
        FakeFS playerFS = FakeFS.getPlayerFS(player.getUniqueId(), player.getName());
        String logMessage = "[ " + level.name() + " ] " + message;
        if (playerFS.getFile(logFilePath) != null) {
            playerFS.appendToFile(logFilePath, "\n" + logMessage);
        } else {
            playerFS.createFile(logFilePath, logMessage);
        }
    }

    public static void MiscLog(Player player, Levels level, String message) {
        if (player == null) return;
        FakeFS playerFS = FakeFS.getPlayerFS(player.getUniqueId(), player.getName());
        String logMessage = "[ " + level.name() + " ] " + message;
        if (playerFS.getFile(miscLogFilePath) != null) {
            playerFS.appendToFile(miscLogFilePath, "\n" + logMessage);
        } else {
            playerFS.createFile(miscLogFilePath, logMessage);
        }
    }
}
