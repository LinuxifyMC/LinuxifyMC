package com.opuadm.linuxifymc.machine.shell;

import com.opuadm.linuxifymc.machine.bin.BinCompiler;
import com.opuadm.linuxifymc.machine.bin.BinContext;
import com.opuadm.linuxifymc.machine.fs.FakeFS;
import com.opuadm.linuxifymc.machine.fs.FsRegistry;
import com.opuadm.linuxifymc.machine.fs.FsNodes;
import com.opuadm.linuxifymc.machine.fs.FsPaths;
import com.opuadm.linuxifymc.commands.cli.CliCommand;
import com.opuadm.linuxifymc.machine.states.Power;
import com.opuadm.linuxifymc.machine.login.Login;
import com.opuadm.linuxifymc.LinuxifyMC;

import net.kyori.adventure.text.Component;

import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.StringUtil;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Shell implements CommandExecutor, TabCompleter {
    private static final Logger LOG = Logger.getLogger(LinuxifyMC.pluginName);
    private static final ThreadLocal<Integer> SCRIPT_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final int MAX_SCRIPT_DEPTH = 16;
    private static final ThreadLocal<String> PIPE_STDIN = new ThreadLocal<>();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        Power pwr = Power.getFor(player);

        if (pwr.checkPowerStatus() == 1 || Login.isLoggedIn(Objects.requireNonNull(player.getPlayer()).getUniqueId())) {
            FakeFS fs = FsRegistry.getPlayerFS(player.getUniqueId(), player.getName());
            if (fs == null) {
                sender.sendMessage("Filesystem unavailable");
                return true;
            }

            if (args.length == 0) {
                String prompt = prompt(player, fs);
                sender.sendMessage(prompt);
                if (!fs.saveFS(player, fs)) sender.sendMessage("Warning: Failed to save filesystem state");
                return true;
            }

            return executeCommand(sender, player, fs, args);
        }

        return true;
    }

    public boolean executeCommand(CommandSender sender, Player player, FakeFS fs, String[] args) {
        return executeCommand(sender, player, fs, args, true);
    }

    private boolean executeCommand(CommandSender sender, Player player, FakeFS fs, String[] args, boolean echo) {
        if (args.length == 0) return true;

        String full = String.join(" ", args);
        String originalFull = full;
        boolean redirect = full.contains(" > ") || full.contains(" >> ");
        boolean append = full.contains(" >> ");
        String target = null;

        if (redirect) {
            String[] parts = append ? full.split(" >> ", 2) : full.split(" > ", 2);
            full = parts[0].trim();
            target = parts[1].trim();
            if (target.startsWith("~")) target = target.replaceFirst("~", "/home/" + player.getName());
            args = full.split("\\s+");
        }

        if (echo) sender.sendMessage(prompt(player, fs) + originalFull);

        StringBuilder buf = new StringBuilder();
        CommandSender eff = redirect ? new OutputCapturingSender(sender, player, buf) : sender;

        boolean success;
        if (full.contains("|")) success = execPipe(eff, player, fs, full);
        else if (full.contains("&&")) success = execChain(eff, player, fs, full);
        else success = execSingle(eff, player, fs, args);

        if (redirect) {
            if (append) fs.appendFile(target, buf.toString());
            else if (fs.getFile(target) != null) fs.writeFile(target, buf.toString());
            else fs.makeFile(target, player.getName(), "777", buf.toString());
        }
        return success;
    }

    private boolean execChain(CommandSender sender, Player player, FakeFS fs, String full) {
        String[] parts = full.split("\\s*&&\\s*");
        for (String p : parts) {
            if (p.isBlank()) continue;
            if (p.contains("|")) {
                if (!execPipe(sender, player, fs, p.trim())) return false;
                continue;
            }
            String[] a = p.trim().split("\\s+");
            if (a.length > 0 && a[0].equalsIgnoreCase("sudo")) {
                String sudobin = resolveBinary(fs, "sudo");
                if (sudobin == null) {
                    sender.sendMessage("sudo: command not found");
                    return false;
                }
                if (!runBinary(new ElevatedSender(sender, player), player, fs, sudobin, "sudo", a)) return false;
                } else {
                if (!execSingle(sender, player, fs, a)) return false;
            }
        }
        return true;
    }

    public static String pipeStdin() {
        return PIPE_STDIN.get();
    }

    static String pipeStdin(String next) {
        String prev = PIPE_STDIN.get();
        if (next == null) PIPE_STDIN.remove();
        else PIPE_STDIN.set(next);
        return prev;
    }

    private boolean execPipe(CommandSender sender, Player player, FakeFS fs, String full) {
        String[] segments = full.split("\\s*\\|\\s*");
        String stdin = null;
        boolean ok = true;
        for (int i = 0; i < segments.length; i++) {
            String seg = segments[i].trim();
            if (seg.isEmpty()) continue;
            boolean last = true;
            for (int j = i + 1; j < segments.length; j++) {
                if (!segments[j].trim().isEmpty()) {
                    last = false;
                    break;
                }
            }
            StringBuilder buf = new StringBuilder();
            CommandSender eff = new OutputCapturingSender(sender, player, buf);
            String prev = pipeStdin(stdin);
            try {
                boolean r = seg.contains("&&") ? execChain(eff, player, fs, seg) : execSingle(eff, player, fs, seg.split("\\s+"));
                if (!r) ok = false;
            } finally {
                pipeStdin(prev);
            }
            stdin = buf.toString();
            if (last) {
                String out = stdin;
                while (out.endsWith("\n")) out = out.substring(0, out.length() - 1);
                if (!out.isEmpty()) sender.sendMessage(out);
            }
        }
        return ok;
    }

    private boolean execSingle(CommandSender sender, Player player, FakeFS fs, String[] args) {
        String name = args[0];
        String binary = resolveBinary(fs, name);
        if (binary == null) {
            String dirCheck = name;
            if (!dirCheck.startsWith("/")) {
                String cwd = fs.getCurrentDir();
                if (cwd == null || cwd.isEmpty()) cwd = "/";
                dirCheck = FsPaths.collapse(cwd + "/" + dirCheck);
            }
            if (name.contains("/") && fs.getDir(dirCheck) != null) {
                sender.sendMessage(args[0] + ": Is a directory");
                return false;
            }
            sender.sendMessage(args[0] + ": command not found");
            return false;
        }
        return runBinary(sender, player, fs, binary, args[0], args);
    }

    public static String resolveBinary(FakeFS fs, String name) {
        if (name.startsWith("/")) {
            return fs.getFile(name) != null ? name : null;
        }
        if (name.contains("/")) {
            String cwd = fs.getCurrentDir();
            if (cwd == null || cwd.isEmpty()) cwd = "/";
            String full = FsPaths.collapse(cwd + "/" + name);
            return fs.getFile(full) != null ? full : null;
        }
        if (fs.getFile("/usr/bin/" + name) != null) return "/usr/bin/" + name;
        if (fs.getFile("/bin/" + name) != null) return "/bin/" + name;
        return null;
    }

    public boolean runBinary(CommandSender sender, Player player, FakeFS fs, String binary, String displayName, String[] args) {
        if (!fs.canExecute(binary)) {
            sender.sendMessage(displayName + ": Permission denied");
            return false;
        }
        String content = fs.getFile(binary);
        if (content != null && !content.isEmpty()) {
            String[] header = BinCompiler.headerOf(content);
            String headerLine = header[0].trim();
            String prefix = FsNodes.BINARY_MAGIC + "|" + FsNodes.BINARY_VERSION;
            String className = null;
            boolean compiled;
            if (headerLine.equals(prefix)) {
                compiled = false;
            } else if (headerLine.startsWith(prefix + "|class=")) {
                compiled = true;
                String candidate = headerLine.substring(prefix.length() + 7);
                if (candidate.matches("[A-Za-z_$][A-Za-z0-9_$]*")) className = candidate;
            } else {
                sender.sendMessage(displayName + ": cannot execute binary file");
                return false;
            }
            String program = header[1];
            if (compiled) {
                byte[] code = BinCompiler.decodeBody(program);
                if (className == null || !BinCompiler.isClassBytes(code)) {
                    sender.sendMessage(displayName + ": cannot execute binary file");
                    return false;
                }
                try {
                    byte[] own = BinCompiler.ownBytes(className);
                    if (own != null && Arrays.equals(code, own)) {
                        Class<?> trusted = BinCompiler.loadCore(className, code);
                        Object instance = trusted.getDeclaredConstructor().newInstance();
                        if (instance instanceof CliCommand cmd) {
                            return cmd.execute(sender, player, fs, args);
                        }
                        sender.sendMessage(displayName + ": cannot execute binary file");
                        return false;
                    }
                    BinCompiler.verify(code);
                    Class<?> clazz = BinCompiler.load(className, code);
                    BinContext ctx = BinCompiler.context(sender, fs, pipeStdin(), SudoContext.getEffectiveUser(player.getUniqueId(), player.getName()));
                    int exit = BinCompiler.execute(clazz, ctx, args, BinCompiler.MAX_RUN_MILLIS);
                    return exit == 0;
                } catch (SecurityException e) {
                    sender.sendMessage(displayName + ": blocked: " + e.getMessage());
                    return false;
                } catch (TimeoutException e) {
                    sender.sendMessage(displayName + ": timed out");
                    return false;
                } catch (LinkageError e) {
                    sender.sendMessage(displayName + ": cannot execute binary file");
                    return false;
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    boolean userCode = false;
                    if (cause instanceof InvocationTargetException ite && ite.getTargetException() != null) {
                        cause = ite.getTargetException();
                        userCode = true;
                    }
                    String detail = cause.getMessage() != null ? cause.getMessage() : cause.toString();
                    sender.sendMessage(displayName + ": error: " + detail);
                    if (userCode) {
                        LOG.fine("Error executing binary " + binary + ": " + detail);
                    } else {
                        LOG.log(Level.SEVERE, "Error executing binary " + binary, e);
                    }
                    return false;
                }
            }
            if (program.trim().isEmpty()) {
                sender.sendMessage(displayName + ": cannot execute binary file");
                return false;
            }
            return runScript(sender, player, fs, program, args);
        }
        sender.sendMessage(displayName + ": cannot execute binary file");
        return false;
    }

    public boolean runScript(CommandSender sender, Player player, FakeFS fs, String program, String[] argv) {
        int depth = SCRIPT_DEPTH.get();
        if (depth >= MAX_SCRIPT_DEPTH) {
            sender.sendMessage("mcsh: script nesting too deep");
            return false;
        }
        SCRIPT_DEPTH.set(depth + 1);
        String previousDir = fs.getCurrentDir();
        try {
            boolean ok = true;
            for (String rawLine : program.split("\n")) {
                String line = expandArgs(rawLine.trim(), argv);
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (!executeCommand(sender, player, fs, line.split("\\s+"), false)) ok = false;
            }
            return ok;
        } finally {
            SCRIPT_DEPTH.set(depth);
            if (previousDir != null) fs.setCurrentDir(previousDir);
        }
    }

    private static String expandArgs(String line, String[] argv) {
        if (argv == null || argv.length == 0) return line;
        String out = line.replace("$@", String.join(" ", Arrays.copyOfRange(argv, 1, argv.length)));
        out = out.replace("$#", String.valueOf(argv.length - 1));
        out = out.replace("$0", argv[0]);
        for (int i = 9; i >= 1; i--) {
            out = out.replace("$" + i, i < argv.length ? argv[i] : "");
        }
        return out;
    }

    private String prompt(Player p, FakeFS fs) {
        String user = p.getName().toLowerCase();
        com.opuadm.linuxifymc.machine.login.Login session = com.opuadm.linuxifymc.machine.login.Login.getSession(p.getUniqueId());
        if (session != null) {
            String curUser = session.getCurrentUser();
            if (curUser != null && !curUser.isEmpty()) user = curUser.toLowerCase();
        }
        return user + "@" + com.opuadm.linuxifymc.LinuxifyMC.hostname + ":" + fs.getCurrentDir() + "$ ";
    }

    private record OutputCapturingSender(CommandSender delegate, Player player,
                                         StringBuilder out) implements CommandSender {

        @Override
        public void sendMessage(String message) {
            out.append(message).append("\n");
        }

        @Override
        public void sendMessage(String[] messages) {
            for (String m : messages) sendMessage(m);
        }

        @Deprecated
        @Override
        public void sendMessage(UUID uuid, String message) {
            if (player.getUniqueId().equals(uuid)) sendMessage(message);
        }

        @Deprecated
        @Override
        public void sendMessage(UUID uuid, String... messages) {
            if (player.getUniqueId().equals(uuid)) sendMessage(messages);
        }

        @Override
        public @NotNull Component name() {
            return Component.text(player.getName());
        }

        @Override
        public String getName() {
            return player.getName();
        }

        @Override
        public Server getServer() {
            return delegate.getServer();
        }

        @Override
        public Spigot spigot() {
            return delegate.spigot();
        }

        @Override
        public boolean isPermissionSet(String name) {
            return player.isPermissionSet(name);
        }

        @Override
        public boolean isPermissionSet(Permission perm) {
            return player.isPermissionSet(perm);
        }

        @Override
        public boolean hasPermission(String name) {
            return player.hasPermission(name);
        }

        @Override
        public boolean hasPermission(Permission perm) {
            return player.hasPermission(perm);
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
            return player.addAttachment(plugin, name, value);
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin) {
            return player.addAttachment(plugin);
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) {
            return player.addAttachment(plugin, name, value, ticks);
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, int ticks) {
            return player.addAttachment(plugin, ticks);
        }

        @Override
        public void removeAttachment(PermissionAttachment attachment) {
            player.removeAttachment(attachment);
        }

        @Override
        public void recalculatePermissions() {
            player.recalculatePermissions();
        }

        @Override
        public Set<PermissionAttachmentInfo> getEffectivePermissions() {
            return player.getEffectivePermissions();
        }

        @Override
        public boolean isOp() {
            return player.isOp();
        }

        @Override
        public void setOp(boolean value) {
            player.setOp(value);
        }
        }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        List<String> names = null;
        if (sender instanceof Player p) {
            try {
                FakeFS fs = FsRegistry.getPlayerFS(p.getUniqueId(), p.getName());
                if (fs != null) names = fs.binaryNames();
            } catch (Exception ignored) {
            }
        }
        if (names == null || names.isEmpty()) names = Arrays.asList(ShellVars.cmds);
        List<String> available = new ArrayList<>(names);

        String full = String.join(" ", args);
        if (full.contains("&&")) {
            String[] parts = full.split("&&");
            String last = parts[parts.length - 1].trim();
            if (last.isEmpty() && full.endsWith("&&")) {
                StringUtil.copyPartialMatches("", available, out);
                return out;
            }

            String[] chainArgs = last.split("\\s+");
            if (chainArgs.length == 1) {
                StringUtil.copyPartialMatches(chainArgs[0], available, out);
            } else {
                String cmd = chainArgs[0];
                String arg = chainArgs[chainArgs.length - 1];
                List<String> options = switch (cmd.toLowerCase()) {
                    case "ls" -> ShellVars.LsOpts();
                    case "uname" -> chainArgs.length == 3 && chainArgs[1].equalsIgnoreCase("-s") ? ShellVars.UnameOptsS() : ShellVars.UnameOpts();
                    case "chmod" -> ShellVars.ChmodPerms();
                    case "rm" -> ShellVars.RMOpts();
                    default -> Collections.emptyList();
                };
                if (!options.isEmpty()) {
                    StringUtil.copyPartialMatches(arg, options, out);
                }
            }
            return out;
        }

        String cmd = args[0].toLowerCase();
        String arg = args.length > 1 ? args[1] : "";

        switch (args.length) {
            case 1 -> StringUtil.copyPartialMatches(args[0], available, out);
            case 2 -> {
                switch (cmd) {
                    case "ls" -> StringUtil.copyPartialMatches(arg, ShellVars.LsOpts(), out);
                    case "uname" -> StringUtil.copyPartialMatches(arg, ShellVars.UnameOpts(), out);
                    case "chmod" -> StringUtil.copyPartialMatches(arg, ShellVars.ChmodPerms(), out);
                    case "mkdir" -> StringUtil.copyPartialMatches(arg, ShellVars.MkdirOpts(), out);
                    case "rm" -> StringUtil.copyPartialMatches(arg, ShellVars.RMOpts(), out);
                }
            }
            case 3 -> {
                if (cmd.equals("uname") && args[1].equalsIgnoreCase("-s")) {
                    StringUtil.copyPartialMatches(args[2], ShellVars.UnameOptsS(), out);
                }
            }
        }
        return out;
    }

    public record ElevatedSender(CommandSender delegate, Player player) implements CommandSender {

        @Override
            public void sendMessage(String message) {
                delegate.sendMessage(message);
            }

            @Override
            public void sendMessage(String[] messages) {
                delegate.sendMessage(messages);
            }

            @Deprecated
            @Override
            public void sendMessage(UUID uuid, String message) {
                boolean match = player.getUniqueId().equals(uuid);
                if (match) delegate.sendMessage(message);
            }

            @Deprecated
            @Override
            public void sendMessage(UUID uuid, String... messages) {
                boolean match = player.getUniqueId().equals(uuid);
                if (match) delegate.sendMessage(messages);
            }

            @Override
            public @NotNull Component name() {
                return delegate.name();
            }

            @Override
            public String getName() {
                return delegate.getName();
            }

            @Override
            public Server getServer() {
                return delegate.getServer();
            }

            @Override
            public Spigot spigot() {
                return delegate.spigot();
            }

            @Override
            public boolean isPermissionSet(String name) {
                if (name == null) return true;
                if (name.startsWith("linuxifymc.command.cli") || name.startsWith("linuxifymc.command")) return true;
                return delegate.isPermissionSet(name);
            }

            @Override
            public boolean isPermissionSet(Permission perm) {
                if (perm == null) return true;
                if (perm.getName().startsWith("linuxifymc.command.cli") || perm.getName().startsWith("linuxifymc.command"))
                    return true;
                return delegate.isPermissionSet(perm);
            }

            @Override
            public boolean hasPermission(String name) {
                if (name == null) return true;
                if (name.startsWith("linuxifymc.command.cli") || name.startsWith("linuxifymc.command")) return true;
                return delegate.hasPermission(name);
            }

            @Override
            public boolean hasPermission(Permission perm) {
                if (perm == null) return true;
                if (perm.getName().startsWith("linuxifymc.command.cli") || perm.getName().startsWith("linuxifymc.command"))
                    return true;
                return delegate.hasPermission(perm);
            }

            @Override
            public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
                return delegate.addAttachment(plugin, name, value);
            }

            @Override
            public PermissionAttachment addAttachment(Plugin plugin) {
                return delegate.addAttachment(plugin);
            }

            @Override
            public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) {
                return delegate.addAttachment(plugin, name, value, ticks);
            }

            @Override
            public PermissionAttachment addAttachment(Plugin plugin, int ticks) {
                return delegate.addAttachment(plugin, ticks);
            }

            @Override
            public void removeAttachment(PermissionAttachment attachment) {
                delegate.removeAttachment(attachment);
            }

            @Override
            public void recalculatePermissions() {
                delegate.recalculatePermissions();
            }

            @Override
            public Set<PermissionAttachmentInfo> getEffectivePermissions() {
                return delegate.getEffectivePermissions();
            }

            @Override
            public boolean isOp() {
                return delegate.isOp();
            }

            @Override
            public void setOp(boolean value) {

            }
        }

}
