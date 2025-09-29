package com.opuadm;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.EventHandler;

import org.bstats.bukkit.Metrics;

import java.util.Objects;
import java.util.UUID;

import com.opuadm.machine.fs.FakeFS;
import com.opuadm.commands.cli.Shell;
import com.opuadm.commands.linuxifymc.LinuxifyMCSettings;

public final class LinuxifyMC extends JavaPlugin implements Listener {
    public static String version = "0.1.1";
    public static String kernelver = "0.1.1-generic";
    public static String kernelname = "LinuxifyMC Kernel";
    public static String shellname = "mcsh";
    public static String shellver = "0.1.1";
    public static String hostname = "linuxifymc";

    int pluginId = 26603;

    private Database database;
    @SuppressWarnings("unused")
    private FakeFS fs;

    @SuppressWarnings("unused")
    public Database getDatabase() {
        return database;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        database = new Database(this);

        getServer().getPluginManager().registerEvents(this, this);

        Objects.requireNonNull(this.getCommand("cli")).setExecutor(new Shell());
        Objects.requireNonNull(this.getCommand("cli")).setTabCompleter(new Shell());
        Objects.requireNonNull(this.getCommand("linuxifymc")).setExecutor(new LinuxifyMCSettings());
        Objects.requireNonNull(this.getCommand("linuxifymc")).setTabCompleter(new LinuxifyMCSettings());

        getLogger().info("LinuxifyMC has been enabled. Version: " + version);
        if (!Bukkit.getVersion().contains("1.21")) {
            getLogger().info("NOTE: You are running a version which isn't an 1.21.x version. Please note that this plugin may not work under other versions of Minecraft that aren't 1.21.x, so proceed with caution.");
            getLogger().info("NOTE: LinuxifyMC native Minecraft version is 1.21.4.");
            getLogger().info("NOTE: Currently tested versions are 1.21.4.");
            getLogger().info("Current Version:" + Bukkit.getVersion());
        }

        @SuppressWarnings("unused") Metrics metrics = new Metrics(this, pluginId);
    }

    @Override
    public void onDisable() {
        // FakeFS.cleanup();
        if (database != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                FakeFS fs = FakeFS.getPlayerFS(player.getUniqueId(), player.getName());
                if (fs != null) {
                    fs.saveFS(player, fs);
                    // FakeFS.removePlayerFS(player.getUniqueId());
                }
            }
            database.close();
        }
        getLogger().info("LinuxifyMC has been disabled.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Object exists = FakeFS.DB == null ? null : FakeFS.DB.singleValueQuery("SELECT 1 FROM fs_saves WHERE player_uuid = ?", uuid.toString());
        boolean isNew = exists == null;
        FakeFS plrFS = FakeFS.getPlayerFS(uuid, player.getName());
        if (plrFS == null) return;
        if (isNew) plrFS.setupSysFiles();
        plrFS.loadFS(uuid);
        plrFS.upgradeFS(plrFS);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        FakeFS fs = FakeFS.getPlayerFS(player.getUniqueId(), player.getName());
        if (fs != null && database != null) {
            fs.saveFS(player, fs);
        }
        // FakeFS.removePlayerFS(player.getUniqueId());
    }
}
