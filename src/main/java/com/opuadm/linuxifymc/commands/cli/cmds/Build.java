package com.opuadm.linuxifymc.commands.cli.cmds;

import com.opuadm.linuxifymc.commands.cli.ArgUtils;
import com.opuadm.linuxifymc.commands.cli.CliCommand;
import com.opuadm.linuxifymc.machine.bin.BinCompiler;
import com.opuadm.linuxifymc.machine.fs.FakeFS;
import com.opuadm.linuxifymc.machine.fs.FsNodes;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Base64;
import java.util.regex.Pattern;

public class Build implements CliCommand {
    @Override
    public boolean execute(CommandSender sender, Player player, FakeFS fs, String[] args) {
        if (!sender.hasPermission("linuxifymc.binaries.build")) {
            sender.sendMessage("build: Permission denied");
            return false;
        }
        String src = ArgUtils.getPositional(args, 1);
        String dest = null;
        for (int i = 2; i < args.length; i++) {
            if (args[i].equals("-o") && i + 1 < args.length) dest = args[++i];
        }
        if (src == null) {
            sender.sendMessage("Usage: build <source.java> [-o <binary>]");
            return true;
        }
        if (!src.endsWith(".java")) {
            sender.sendMessage("build: source must be a .java file");
            return true;
        }
        if (src.startsWith("~")) src = "/home/" + player.getName().toLowerCase() + src.substring(1);
        String content = fs.getFile(src);
        if (content == null) {
            sender.sendMessage("build: " + args[1] + ": No such file");
            return false;
        }
        if (content.length() > BinCompiler.MAX_SOURCE_BYTES) {
            sender.sendMessage("build: source too large");
            return false;
        }
        if (Pattern.compile("(?m)^\\s*package\\s").matcher(content).find()) {
            sender.sendMessage("build: packages not supported");
            return false;
        }
        String base = src.substring(src.lastIndexOf('/') + 1);
        String className = base.substring(0, base.length() - 5);
        if (!className.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
            sender.sendMessage("build: invalid class name: " + className);
            return false;
        }
        if (dest == null) {
            dest = src.substring(0, src.length() - 5);
        } else if (dest.startsWith("~")) {
            dest = "/home/" + player.getName().toLowerCase() + dest.substring(1);
        }
        byte[] code;
        try {
            code = BinCompiler.compile(className, content);
            BinCompiler.verify(code);
        } catch (IllegalStateException e) {
            sender.sendMessage("build: " + e.getMessage());
            return false;
        } catch (IllegalArgumentException | SecurityException e) {
            sender.sendMessage("build failed:\n" + e.getMessage());
            return false;
        }
        String row = FsNodes.BINARY_MAGIC + "|" + FsNodes.BINARY_VERSION + "|class=" + className + "\n" + Base64.getEncoder().encodeToString(code);
        fs.deleteFile(dest);
        fs.makeFile(dest, player.getName(), "755", row);
        if (fs.getFile(dest) != null) sender.sendMessage("Built " + dest);
        else sender.sendMessage("build: could not write " + dest);
        return true;
    }
}
