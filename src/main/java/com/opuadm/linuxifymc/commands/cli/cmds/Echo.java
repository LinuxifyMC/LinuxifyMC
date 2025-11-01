package com.opuadm.linuxifymc.commands.cli.cmds;

import com.opuadm.linuxifymc.machine.fs.FakeFS;
import com.opuadm.linuxifymc.LinuxifyMC;
import com.opuadm.linuxifymc.machine.login.Login;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

@SuppressWarnings("unused")
public class Echo {
    public boolean execute(CommandSender sender, Player player, FakeFS fs, String[] args) {
        String fullCommand = String.join(" ", args);

        int appendPos = fullCommand.indexOf(">>");
        int overwritePos = fullCommand.indexOf(">");
        boolean hasRedirect = appendPos >= 0 || (overwritePos >= 0);

        if (hasRedirect) {
            boolean append = appendPos >= 0;
            int pos = append ? appendPos : overwritePos;

            String textToEcho = fullCommand.substring(5, pos).trim();
            String fileName = fullCommand.substring(pos + (append ? 2 : 1)).trim();

            String userForHome = player.getName();
            var session = Login.getSession(player.getUniqueId());
            if (session != null && session.getCurrentUser() != null && !session.getCurrentUser().isEmpty()) {
                userForHome = session.getCurrentUser();
            }
            if (fileName.startsWith("~")) {
                fileName = fileName.replaceFirst("~", "/home/" + userForHome);
            }

            try {
                if (append) {
                    fs.appendFile(fileName, textToEcho);
                } else {
                    fs.writeFile(fileName, textToEcho);
                }
                sender.sendMessage("");
            } catch (Exception e) {
                sender.sendMessage(LinuxifyMC.shellname + ": echo: Failed to write to file '" + fileName + "'");
            }
        } else {
            if (args.length > 1) {
                String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                sender.sendMessage(message);
            } else {
                sender.sendMessage("");
            }
        }
        return true;
    }
}