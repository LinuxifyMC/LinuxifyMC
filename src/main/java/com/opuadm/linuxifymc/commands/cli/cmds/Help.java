package com.opuadm.linuxifymc.commands.cli.cmds;

import com.opuadm.linuxifymc.LinuxifyMC;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.opuadm.linuxifymc.machine.fs.FakeFS;
import com.opuadm.linuxifymc.commands.cli.ArgUtils;

@SuppressWarnings("unused")
public class Help {
    public boolean execute(CommandSender sender, Player player, FakeFS fs, String[] args) {
        int page = 1;
        String pageArg = ArgUtils.getPositional(args, 1);
        if (pageArg != null) {
            try {
                page = Integer.parseInt(pageArg);
            } catch (NumberFormatException e) {
                sender.sendMessage("Invalid page number");
            }
        }

        HelpSend(sender, page);
        return true;
    }

    private void HelpSend(CommandSender sender, int page) {
        if (page == 1) {
            sender.sendMessage(LinuxifyMC.shellname + ", version " + LinuxifyMC.shellver);
            sender.sendMessage("");
            sender.sendMessage("These shell commands are defined internally. Type `help' to see this list.");
            sender.sendMessage("");
            if (sender.hasPermission("linuxifymc.command.cli.nonlinuxcmds")) {
                sender.sendMessage("  test                       uname [-s] [-v]");
                sender.sendMessage("  ls [-a] [-l] [path]        cd <path>");
                sender.sendMessage("  chmod <perms> <path>       chown <owner> <path>");
                sender.sendMessage("  mkdir <directory>          rm [-r] <path>");
            } else {
                sender.sendMessage("  uname [-s] [-v]            ls [-a] [-l] [path]");
                sender.sendMessage("  cd <path>                  chmod <perms> <path>");
                sender.sendMessage("  chown <owner> <path>       mkdir <directory>");
            }
        } else if (page == 2) {
            if (sender.hasPermission("linuxifymc.command.cli.nonlinuxcmds")) {
                sender.sendMessage("  cat <filename>             touch <filename>");
                sender.sendMessage("  echo [text] [>> file]      neofetch");
                sender.sendMessage("  serverfetch                su [user]");
                sender.sendMessage("  sudo <command>             exit");
            } else {
                sender.sendMessage("  cat <filename>             touch <filename>");
                sender.sendMessage("  echo [text] [>> file]      neofetch");
                sender.sendMessage("  su [user]                  sudo <command>");
                sender.sendMessage("  exit");
            }
        }
    }
}