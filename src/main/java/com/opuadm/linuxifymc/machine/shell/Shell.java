package com.opuadm.linuxifymc.machine.shell;

import com.opuadm.linuxifymc.machine.fs.FakeFS;
import com.opuadm.linuxifymc.machine.states.Power;
import com.opuadm.linuxifymc.machine.login.Login;
import com.opuadm.linuxifymc.commands.cli.cmds.Sudo;

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

import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Shell implements CommandExecutor, TabCompleter {
    private static final Logger LOG = Logger.getLogger("LinuxifyMC");
    private static final String CMDS_PKG = "com.opuadm.linuxifymc.commands.cli.cmds.";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        Power pwr = Power.getFor(player);

        if (pwr.checkPowerStatus() == 1 && Login.isLoggedIn(Objects.requireNonNull(player.getPlayer()).getUniqueId())) {
            FakeFS fs = FakeFS.getPlayerFS(player.getUniqueId(), player.getName());
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
        if (args.length == 0) return true;

        String full = String.join(" ", args);
        String originalFull = full;
        boolean redirect = full.contains(" > ") || full.contains(" >> ");
        boolean append = full.contains(" >> ");
        String target = null;

        if (redirect) {
            String[] parts = append ? full.split(" >> ", 2) : full.split(" > ", 2);
            full = parts[0].trim();
            target = normalizeRedirectPath(parts[1].trim(), player.getName());
            args = full.split("\\s+");
        }

        sender.sendMessage(prompt(player, fs) + originalFull);

        StringBuilder buf = new StringBuilder();
        CommandSender eff = redirect ? new OutputCapturingSender(sender, player, buf) : sender;

        boolean success = full.contains("&&")
                ? execChain(eff, player, fs, full)
                : execSingle(eff, player, fs, args);

        if (redirect) {
            if (append) fs.appendFile(target, buf.toString());
            else fs.makeFile(target, player.getName(), "777", buf.toString());
        }
        return success;
    }

    private boolean execChain(CommandSender sender, Player player, FakeFS fs, String full) {
        String[] parts = full.split("\\s*&&\\s*");
        for (String p : parts) {
            if (p.isBlank()) continue;
            String[] a = p.trim().split("\\s+");
            if (a.length > 0 && a[0].equalsIgnoreCase("sudo")) {
                Sudo sudo = new Sudo();
                if (!sudo.execute(sender, player, fs, a)) return false;
                } else {
                if (!execSingle(sender, player, fs, a)) return false;
            }
        }
        return true;
    }

    private boolean execSingle(CommandSender sender, Player player, FakeFS fs, String[] args) {
        String name = args[0];
        if (!Arrays.asList(ShellVars.cmds).contains(name)) {
            sender.sendMessage(name + ": command not found");
            return false;
        }
        try {
            Class<?> clazz = resolveCommandClass(name);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            Method m = clazz.getMethod("execute", CommandSender.class, Player.class, FakeFS.class, String[].class);
            return (boolean) m.invoke(instance, sender, player, fs, args);
        } catch (ClassNotFoundException e) {
            sender.sendMessage(name + ": implementation not found");
            return false;
        } catch (Exception e) {
            sender.sendMessage(name + ": error: " + e.getMessage());
            LOG.log(Level.SEVERE, "Error executing command " + name, e);
            return false;
        }
    }

    private Class<?> resolveCommandClass(String cmd) throws ClassNotFoundException {
        String cap = capitalize(cmd);
        String[] candidates = {CMDS_PKG + cap, CMDS_PKG + cmd.toUpperCase(), CMDS_PKG + cmd.toLowerCase()};
        ClassNotFoundException last = null;
        for (String c : candidates) {
            try { return Class.forName(c); }
            catch (ClassNotFoundException e) { last = e; }
        }
        throw last;
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

    private String normalizeRedirectPath(String path, String user) {
        return path.startsWith("~") ? path.replaceFirst("~", "/home/" + user) : path;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
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

        @Override
        public void sendMessage(UUID uuid, String message) {
            if (player.getUniqueId().equals(uuid)) sendMessage(message);
        }

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
        List<String> available = new ArrayList<>();
        for (String c : ShellVars.cmds) {
            if ((c.equals("test") || c.equals("serverfetch"))) {
                if (sender.hasPermission("linuxifymc.command.cli.nonlinuxcmds")) available.add(c);
            } else available.add(c);
        }

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

            @Override
            public void sendMessage(UUID uuid, String message) {
                boolean match = player.getUniqueId().equals(uuid);
                if (match) delegate.sendMessage(message);
            }

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