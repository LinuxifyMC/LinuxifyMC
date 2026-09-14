package com.opuadm.linuxifymc.machine.fs;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.opuadm.linuxifymc.machine.logs.CustomLogger;
import com.opuadm.linuxifymc.machine.logs.Levels;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

public final class FsChannels {
    public static final String CHANNEL = "linuxifymc:fs-v1";

    private static volatile Plugin channelPlugin;

    private FsChannels() {
    }

    public static void registerChannels(Plugin plugin) {
        channelPlugin = plugin;
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL,
                FsChannels::handleChannelMessage);
    }

    public static void unregisterChannels(Plugin plugin) {
        try {
            plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL);
        } catch (Exception ignored) {
        }
        try {
            plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        } catch (Exception ignored) {
        }
        if (channelPlugin == plugin) {
            channelPlugin = null;
        }
    }

    private static String readChannelOptional(DataInputStream in) {
        try {
            if (in.available() <= 0) {
                return null;
            }
            return in.readUTF();
        } catch (Exception e) {
            return null;
        }
    }

    private static void replyChannel(Player player, String op, boolean ok, String payload) {
        Plugin plugin = channelPlugin;
        if (plugin == null) {
            return;
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(out);
            data.writeUTF(op);
            data.writeBoolean(ok);
            data.writeUTF(payload != null ? payload : "");
            data.flush();
            player.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
        } catch (Exception ignored) {
        }
    }

    private static void handleChannelMessage(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) {
            return;
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            String op = in.readUTF();
            String path = readChannelOptional(in);
            String arg1 = readChannelOptional(in);
            String arg2 = readChannelOptional(in);
            FakeFS fs = FsRegistry.forPlayer(player.getUniqueId());
            if (fs == null) {
                replyChannel(player, op, false, "no-fs");
                return;
            }
            switch (op) {
                case "read" -> {
                    String content = fs.getFile(path);
                    replyChannel(player, op, content != null, content != null ? content : "");
                }
                case "ls" -> replyChannel(player, op, true, fs.listCurrentDir(path, false, false));
                case "pwd" -> replyChannel(player, op, true, fs.getCurrentDir());
                case "exists" -> replyChannel(player, op, true,
                        (fs.getFile(path) != null || fs.getDir(path) != null) ? "1" : "0");
                case "mkdir" -> {
                    fs.makeDir(path, null, arg1);
                    CustomLogger.MiscLog(player, Levels.GENERAL, "I: remote mkdir " + path);
                    replyChannel(player, op, fs.getDir(path) != null, "");
                }
                case "mkfile" -> {
                    fs.makeFile(path, null, arg1, arg2);
                    CustomLogger.MiscLog(player, Levels.GENERAL, "I: remote mkfile " + path);
                    replyChannel(player, op, fs.getFile(path) != null, "");
                }
                case "write" -> {
                    fs.writeFile(path, arg1);
                    CustomLogger.MiscLog(player, Levels.GENERAL, "I: remote write " + path);
                    replyChannel(player, op, true, "");
                }
                case "append" -> {
                    fs.appendFile(path, arg1);
                    CustomLogger.MiscLog(player, Levels.GENERAL, "I: remote append " + path);
                    replyChannel(player, op, true, "");
                }
                case "rm" -> {
                    fs.deleteFile(path);
                    CustomLogger.MiscLog(player, Levels.GENERAL, "I: remote rm " + path);
                    replyChannel(player, op, fs.getFile(path) == null, "");
                }
                case "rmdir" -> {
                    fs.deleteDir(path, "1".equals(arg1), false);
                    CustomLogger.MiscLog(player, Levels.GENERAL, "I: remote rmdir " + path);
                    replyChannel(player, op, fs.getDir(path) == null, "");
                }
                case "readlink" -> {
                    String linkTarget = fs.readLink(path);
                    replyChannel(player, op, linkTarget != null, linkTarget != null ? linkTarget : "");
                }
                case "ln" -> {
                    fs.makeLink(path, arg1, null);
                    CustomLogger.MiscLog(player, Levels.GENERAL, "I: remote ln " + path);
                    replyChannel(player, op, fs.readLink(path) != null, "");
                }
                default -> replyChannel(player, op, false, "unknown-op");
            }
        } catch (Exception e) {
            replyChannel(player, "error", false, e.getMessage() != null ? e.getMessage() : "bad-request");
        }
    }
}
