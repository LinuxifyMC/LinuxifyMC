package com.opuadm.linuxifymc.commands.cli.cmds;

import com.opuadm.linuxifymc.machine.fs.FakeFS;
import com.opuadm.linuxifymc.commands.cli.ArgUtils;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;

@SuppressWarnings("unused")
public class Cat {
    public boolean execute(CommandSender sender, Player player, FakeFS fs, String[] args) {
        String fileName = ArgUtils.getPositional(args, 1);
        if (fileName == null) {
            sender.sendMessage("Usage: cat <file_name>");
            return true;
        }

        String content = fs.getFile(fileName);
        sender.sendMessage(Objects.requireNonNullElseGet(content, () -> "cat: cannot open '" + fileName + "': No such file or directory"));
        return true;
    }
}
