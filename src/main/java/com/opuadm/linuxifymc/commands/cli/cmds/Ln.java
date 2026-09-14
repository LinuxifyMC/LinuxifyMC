package com.opuadm.linuxifymc.commands.cli.cmds;

import com.opuadm.linuxifymc.commands.cli.CliCommand;
import com.opuadm.linuxifymc.machine.fs.FakeFS;
import com.opuadm.linuxifymc.machine.fs.FsPaths;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class Ln implements CliCommand {
    @Override
    public boolean execute(CommandSender sender, Player player, FakeFS fs, String[] args) {
        if (args.length < 4 || !"-s".equals(args[1])) {
            sender.sendMessage("Usage: ln -s TARGET LINK");
            return true;
        }

        String target = args[2];
        String link = args[3];
        if (target.startsWith("~")) {
            target = "/home/" + player.getName().toLowerCase() + target.substring(1);
        }
        if (link.startsWith("~")) {
            link = "/home/" + player.getName().toLowerCase() + link.substring(1);
        }
        if (!link.startsWith("/")) {
            String cwd = fs.getCurrentDir();
            if (cwd == null || cwd.isEmpty()) cwd = "/";
            link = cwd.equals("/") ? "/" + link : cwd + "/" + link;
        }
        link = FsPaths.collapse(link);
        if (fs.getDir(link) != null) {
            String base = target;
            while (base.endsWith("/") && base.length() > 1) base = base.substring(0, base.length() - 1);
            int slash = base.lastIndexOf('/');
            if (slash >= 0) base = base.substring(slash + 1);
            if (base.isEmpty()) {
                sender.sendMessage("ln: missing destination after '" + args[3] + "'");
                return true;
            }
            link = link.equals("/") ? "/" + base : link + "/" + base;
        }
        String parent = link.contains("/") ? link.substring(0, link.lastIndexOf('/')) : "/";
        if (parent.isEmpty()) parent = "/";
        if (fs.getDir(parent) == null) {
            sender.sendMessage("ln: failed to access '" + link + "': No such file or directory");
            return true;
        }
        if (fs.getFile(link) != null || fs.getDir(link) != null || fs.readLink(link) != null) {
            sender.sendMessage("ln: failed to create link '" + link + "': File exists");
            return true;
        }

        String owner = (player != null) ? player.getName() : sender.getName();
        if (!fs.makeLink(link, target, owner)) {
            sender.sendMessage("ln: failed to create link '" + link + "': Permission denied");
            return true;
        }
        sender.sendMessage("");
        return true;
    }
}
