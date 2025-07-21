package com.opuadm;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.EventHandler;


import java.util.Objects;

import com.opuadm.commands.cli.FakeFS;
import com.opuadm.commands.cli.CommandCLI;
import com.opuadm.commands.linuxifymc.LinuxifyMCSettings;

public final class LinuxifyMC extends JavaPlugin implements Listener {
    public static String version = "0.1.0";
    public static String kernelver = "0.1.0-generic";
    public static String kernelname = "LinuxifyMC Kernel";
    public static String shellname = "MCSH";
    public static String shellver = "0.1.0";
    public static String hostname = "linuxifymc";
    private Database database;

    public Database getDatabase() {
        return database;
    }

    @Override
    public void onEnable() {
        database = new Database(this);
        FakeFS.checkAndUpgradeFilesystems();
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(this.getCommand("cli")).setExecutor(new CommandCLI());
        Objects.requireNonNull(this.getCommand("cli")).setTabCompleter(new CommandCLI());
        Objects.requireNonNull(this.getCommand("linuxifymc")).setExecutor(new LinuxifyMCSettings());
        Objects.requireNonNull(this.getCommand("linuxifymc")).setTabCompleter(new LinuxifyMCSettings());
        getLogger().info("LinuxifyMC has been enabled. Version: " + version);
        if (!Bukkit.getVersion().contains("1.21")) {
            getLogger().info("NOTE: You are running a version which isn't an 1.21.x version. Please note that this plugin may not work under other versions of Minecraft that aren't 1.21.x, so proceed with caution.");
            getLogger().info("NOTE: LinuxifyMC native Minecraft version is 1.21.4.");
            getLogger().info("Current Version:" + Bukkit.getVersion());
            public class ExamplePlugin extends JavaPlugin {
        int pluginId = 26603; // <-- Replace with the id of your plugin!
        Metrics metrics = new Metrics(this, pluginId);
        }
    }

    @Override
    public void onDisable() {
        FakeFS.cleanup();
        if (database != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                FakeFS fs = FakeFS.getPlayerFS(player.getUniqueId(), player.getName());
                if (fs != null) {
                    FakeFS.saveFS(player, fs);
                    FakeFS.removePlayerFS(player.getUniqueId());
                }
            }
            database.close();
        }
        getLogger().info("LinuxifyMC has been disabled.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        FakeFS.getPlayerFS(player.getUniqueId(), player.getName());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        FakeFS fs = FakeFS.getPlayerFS(player.getUniqueId(), player.getName());
        if (fs != null && database != null) {
            FakeFS.saveFS(player, fs);
        }
        FakeFS.removePlayerFS(player.getUniqueId());
    }
}
