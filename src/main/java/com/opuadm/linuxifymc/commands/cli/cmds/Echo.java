package com.opuadm.linuxifymc.commands.cli.cmds;

import com.opuadm.linuxifymc.machine.fs.FakeFS;
import com.opuadm.linuxifymc.machine.login.Login;
import com.opuadm.linuxifymc.commands.cli.ArgUtils;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;


@SuppressWarnings("unused")
public class Echo {
    public boolean execute(CommandSender sender, Player player, FakeFS fs, String[] args) {
        String fullCommand = ArgUtils.joinAllArgs(args);

        int appendPos = fullCommand.indexOf(">>");
        int overwritePos = fullCommand.indexOf(">");
        boolean hasRedirect = appendPos >= 0 || (overwritePos >= 0);

        if (hasRedirect) {
            boolean append = appendPos >= 0;
            int pos = append ? appendPos : overwritePos;

            String textToEcho = fullCommand.substring(0, pos).trim();
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
            } catch (Exception e) {
                sender.sendMessage("echo: cannot write to '" + fileName + "': " + e.getMessage());
            }
        } else {
            String[] rest = ArgUtils.argsAfterCommand(args);
            if (rest.length > 0) {
                String message = String.join(" ", rest);
                sender.sendMessage(message);
            } else {
                sender.sendMessage("");
            }
        }
        return true;
    }
}