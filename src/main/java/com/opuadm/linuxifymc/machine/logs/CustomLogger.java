// Custom Logger, all logs will be printed in /var/log/<date>-<time>-<number>.log. Those logs will have CHMOD 777 permissions.
package com.opuadm.linuxifymc.machine.logs;

import com.opuadm.linuxifymc.machine.fs.FakeFS;
import com.opuadm.linuxifymc.machine.fs.ConvertPerms;

import org.bukkit.entity.Player;

public class CustomLogger {
    public static String logFilePath = "/var/log/linuxifymc.log"; // Default log file path
    public static String miscLogFilePath = "/var/log/linuxifymc-misc.log"; // Miscellaneous log file path
    public static String bootLogFilePath = "/var/log/linuxifymc-boot.log"; // Boot log file path

    public static void BootLog(Player player, Levels level, String message) {
        if (player == null) return;
        FakeFS playerFS = FakeFS.getPlayerFS(player.getUniqueId(), player.getName());
        String logMessage = "[ " + level.name() + " ] " + message;
        if (playerFS == null) {
            return;
        }
        if (playerFS.getFile(bootLogFilePath) != null) {
            playerFS.appendFile(bootLogFilePath, "\n" + logMessage);
        } else {
            playerFS.makeFile(bootLogFilePath, player.getName(), ConvertPerms.octalToSymbolic("777"), logMessage);
        }
    }

    public static void Log(Player player, Levels level, String message) {
        if (player == null) return;
        FakeFS playerFS = FakeFS.getPlayerFS(player.getUniqueId(), player.getName());
        String logMessage = "[ " + level.name() + " ] " + message;
        if (playerFS == null) {
            return;
        }
        if (playerFS.getFile(logFilePath) != null) {
            playerFS.appendFile(logFilePath, "\n" + logMessage);
        } else {
            playerFS.makeFile(logFilePath, player.getName(), ConvertPerms.octalToSymbolic("777"), logMessage);
        }
    }

    public static void MiscLog(Player player, Levels level, String message) {
        if (player == null) return;
        FakeFS playerFS = FakeFS.getPlayerFS(player.getUniqueId(), player.getName());
        String logMessage = "[ " + level.name() + " ] " + message;
        if (playerFS == null) {
            return;
        }
        if (playerFS.getFile(miscLogFilePath) != null) {
            playerFS.appendFile(miscLogFilePath, "\n" + logMessage);
        } else {
            playerFS.makeFile(miscLogFilePath, player.getName(), ConvertPerms.octalToSymbolic("777"), logMessage);
        }
    }
}
