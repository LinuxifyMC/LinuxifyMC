package com.opuadm.linuxifymc;

import com.opuadm.linuxifymc.commands.linuxifymc.VM;
import com.opuadm.linuxifymc.machine.fs.FakeFS;
import com.opuadm.linuxifymc.machine.fs.FsRegistry;
import com.opuadm.linuxifymc.machine.shell.Shell;
import com.opuadm.linuxifymc.commands.linuxifymc.Settings;
import com.opuadm.linuxifymc.machine.states.Power;
import com.opuadm.linuxifymc.machine.login.LoginPrompt;

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

public final class LinuxifyMC extends JavaPlugin implements Listener {
    private static final Package PACKAGE_METADATA = LinuxifyMC.class.getPackage();
    private static final String NATIVE_MINECRAFT_VERSION = requireBuildValue(
            PACKAGE_METADATA.getSpecificationVersion(),
            "linuxifymc.nativeMinecraftVersion",
            "Specification-Version");
    private LoginPrompt loginPrompt;

    public static final boolean debugMode = true;

    public static String pluginName = "LinuxifyMC";
    public static String version = requireBuildValue(
            PACKAGE_METADATA.getImplementationVersion(),
            "linuxifymc.version",
            "Implementation-Version");
    public static String kernelver = version + "-generic";
    public static String kernelname = pluginName + " Kernel";
    public static String shellname = "mcsh";
    public static String shellver = version;
    public static String hostname = "linuxifymc";

    int pluginId = 33533;

    private Database database;

    public Database getDatabase() {
        return database;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        database = new Database(this);
        FakeFS.DB = database;

        getServer().getPluginManager().registerEvents(this, this);
        this.loginPrompt = new com.opuadm.linuxifymc.machine.login.LoginPrompt(this);
        getServer().getPluginManager().registerEvents(this.loginPrompt, this);

        Objects.requireNonNull(this.getCommand("cli")).setExecutor(new Shell());
        Objects.requireNonNull(this.getCommand("cli")).setTabCompleter(new Shell());
        Objects.requireNonNull(this.getCommand("linuxifymc")).setExecutor(new Settings());
        Objects.requireNonNull(this.getCommand("linuxifymc")).setTabCompleter(new Settings());
        Objects.requireNonNull(this.getCommand("vcomputer")).setExecutor(new VM());
        Objects.requireNonNull(this.getCommand("vcomputer")).setTabCompleter(new VM());

        FakeFS.registerChannels(this);

        getLogger().info(pluginName + " has been enabled. Version: " + version);
        if (!Bukkit.getBukkitVersion().equals(NATIVE_MINECRAFT_VERSION)
                && !Bukkit.getBukkitVersion().startsWith(NATIVE_MINECRAFT_VERSION + "-")) {
            getLogger().info("NOTE: Current Native Version is " + NATIVE_MINECRAFT_VERSION + ".");
            getLogger().info("Current Version:" + Bukkit.getVersion());
        }

        if (!Bukkit.getVersion().contains("MockBukkit")) {
            new Metrics(this, pluginId);
        }
    }

    @Override
    public void onDisable() {
        FakeFS.unregisterChannels(this);
        if (database != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                FakeFS fs = FsRegistry.getPlayerFS(player.getUniqueId(), player.getName());
                if (fs != null) {
                    fs.saveFS(player, fs);
                    // FakeFS.removePlayerFS(player.getUniqueId());
                }
                try { Power.removeFor(player.getUniqueId()); } catch (Exception ignore) {}
            }
            database.close();
            FakeFS.DB = null;
        }
        getLogger().info(pluginName + " has been disabled.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        ensurePlayerUsers(player);
    }

    void ensurePlayerUsers(Player player) {
        UUID uuid;

        uuid = player.getUniqueId();

        try {
            if (database != null) {
                database.executeUpdate(
                        "INSERT OR IGNORE INTO vm_users (player_uuid, username) VALUES (?, ?)",
                        uuid.toString(), player.getName());

                database.executeUpdate(
                        "INSERT OR IGNORE INTO vm_users (player_uuid, username) VALUES (?, ?)",
                        uuid.toString(), "root");
                database.executeUpdate(
                        "INSERT OR IGNORE INTO vm_disabled_users (player_uuid, username) VALUES (?, ?)",
                        uuid.toString(), "root");
            }
        } catch (Exception e) {
            getLogger().warning("Failed to ensure vm_users/vm_disabled_users for " + player.getName() + ": " + e.getMessage());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        FakeFS fs = FsRegistry.getPlayerFS(player.getUniqueId(), player.getName());
        if (fs != null && database != null) {
            fs.saveFS(player, fs);
        }

        try {
            Power.getFor(player.getUniqueId()).TurnOff();
        } catch (Exception e) {
            getLogger().warning("Failed to fully shutdown VM for " + player.getName() + ": " + e.getMessage());
        } finally {
            try { Power.removeFor(player.getUniqueId()); } catch (Exception ignore) {}
        }
    }

    public com.opuadm.linuxifymc.machine.login.LoginPrompt getLoginPrompt() {
        return loginPrompt;
    }

    private static String requireBuildValue(String manifestValue, String propertyName, String name) {
        String value;

        value = manifestValue;
        if (value == null || value.isBlank()) {
            value = System.getProperty(propertyName);
        }
        if (value == null || value.isBlank()) {
            throw new ExceptionInInitializerError("Missing " + name);
        }
        return value;
    }
}
