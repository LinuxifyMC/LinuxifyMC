package com.opuadm.commands.cli.cmds;

import com.opuadm.LinuxifyMC;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.opuadm.machine.fs.FakeFS;

@SuppressWarnings("unused")
public class Help {
    public boolean execute(CommandSender sender, Player player, FakeFS fs, String[] args) {
        int page = 1;
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage("Invalid page number");
            }
        }

        HelpSend(sender, page);
        return true;
    }

    private void HelpSend(CommandSender sender, int page) {
        // NOTE: 7 Commands per page
        if (page == 1) {
            sender.sendMessage("LinuxifyMC " + LinuxifyMC.shellname + ", version " + LinuxifyMC.shellver + " (Minecraft)");
            sender.sendMessage("[] = optional, <> = required, () = info");
            sender.sendMessage("help [int]");
            if (sender.hasPermission("linuxifymc.command.cli.nonlinuxcmds")) {
                sender.sendMessage("test");
                sender.sendMessage("uname [-s] [-v]");
                sender.sendMessage("ls [-a] [-o] [path]");
                sender.sendMessage("cd <path>");
                sender.sendMessage("chmod <perms> <path>");
                sender.sendMessage("chown <new_owner> <path>");
            } else {
                sender.sendMessage("uname [-s] [-v]");
                sender.sendMessage("ls [-a] [-o] [path]");
                sender.sendMessage("cd <path>");
                sender.sendMessage("chmod <perms> <path>");
                sender.sendMessage("chown <new_owner> <path>");
                sender.sendMessage("mkdir <directory_name>");
            }
        } else if (page == 2) {
            if (sender.hasPermission("linuxifymc.command.cli.nonlinuxcmds")) {
                sender.sendMessage("mkdir <directory_name>");
                sender.sendMessage("rm [-r] <path>");
                sender.sendMessage("cat <filename>");
                sender.sendMessage("touch <filename>");
                sender.sendMessage("echo [text] [>> file]");
                sender.sendMessage("serverfetch");
                sender.sendMessage("neofetch");
            } else {
                sender.sendMessage("rm [-r] <path>");
                sender.sendMessage("cat <filename>");
                sender.sendMessage("touch <filename>");
                sender.sendMessage("echo [text] [>> file]");
                sender.sendMessage("neofetch");
            }
        }
    }
}