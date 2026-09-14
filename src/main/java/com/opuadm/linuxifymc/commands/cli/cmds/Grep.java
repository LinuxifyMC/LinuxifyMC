package com.opuadm.linuxifymc.commands.cli.cmds;

import com.opuadm.linuxifymc.commands.cli.CliCommand;
import com.opuadm.linuxifymc.machine.fs.FakeFS;
import com.opuadm.linuxifymc.machine.fs.FsPaths;
import com.opuadm.linuxifymc.machine.shell.Shell;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class Grep implements CliCommand {
    @Override
    public boolean execute(CommandSender sender, Player player, FakeFS fs, String[] args) {
        boolean ignoreCase = false;
        boolean invert = false;
        boolean lineNumbers = false;
        boolean quiet = false;
        List<String> operands = new ArrayList<>();
        boolean endOfFlags = false;
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (endOfFlags) {
                operands.add(a);
            } else if (a.equals("--")) {
                endOfFlags = true;
            } else if (a.length() > 1 && a.startsWith("-")) {
                for (int j = 1; j < a.length(); j++) {
                    char flag = a.charAt(j);
                    if (flag == 'i') ignoreCase = true;
                    else if (flag == 'v') invert = true;
                    else if (flag == 'n') lineNumbers = true;
                    else if (flag == 'q') quiet = true;
                    else {
                        sender.sendMessage("grep: invalid option -- '" + flag + "'");
                        return true;
                    }
                }
            } else {
                operands.add(a);
            }
        }
        if (operands.isEmpty()) {
            sender.sendMessage("Usage: grep [-invq] <pattern> [file ...]");
            return true;
        }
        Pattern pattern;
        try {
            pattern = Pattern.compile(operands.getFirst(), ignoreCase ? Pattern.CASE_INSENSITIVE : 0);
        } catch (PatternSyntaxException e) {
            sender.sendMessage("grep: invalid pattern: " + e.getMessage());
            return false;
        }
        List<String> files = operands.subList(1, operands.size());
        int found = 0;
        if (files.isEmpty()) {
            String stdin = Shell.pipeStdin();
            if (stdin == null) {
                sender.sendMessage("Usage: grep [-invq] <pattern> [file ...]");
                return true;
            }
            found += filter(sender, pattern, stdin, null, false, lineNumbers, invert, quiet);
        } else {
            boolean multi = files.size() > 1;
            for (String file : files) {
                String content = fs.getFile(normPath(player, fs, file));
                if (content == null) {
                    sender.sendMessage("grep: " + file + ": No such file or directory");
                    continue;
                }
                found += filter(sender, pattern, content, file, multi, lineNumbers, invert, quiet);
            }
        }
        return found > 0;
    }

    private int filter(CommandSender sender, Pattern pattern, String content, String file, boolean multi, boolean lineNumbers, boolean invert, boolean quiet) {
        String[] lines = content.split("\n");
        int found = 0;
        for (int i = 0; i < lines.length; i++) {
            boolean hit = pattern.matcher(lines[i]).find();
            if (invert) hit = !hit;
            if (!hit) continue;
            found++;
            if (quiet) continue;
            StringBuilder out = new StringBuilder();
            if (multi && file != null) out.append(file).append(':');
            if (lineNumbers) out.append(i + 1).append(':');
            out.append(lines[i]);
            sender.sendMessage(out.toString());
        }
        return found;
    }

    private String normPath(Player player, FakeFS fs, String path) {
        if (path.startsWith("~")) path = "/home/" + player.getName().toLowerCase() + path.substring(1);
        String norm = path.replaceAll("/+", "/");
        if (!norm.startsWith("/")) {
            String cwd = fs.getCurrentDir();
            if (cwd == null || cwd.isEmpty()) cwd = "/";
            cwd = cwd.replaceAll("/+$", "");
            if (!cwd.startsWith("/")) cwd = "/" + cwd;
            norm = cwd.equals("/") ? "/" + norm : cwd + "/" + norm;
        }
        return FsPaths.collapse(norm);
    }
}
