package com.opuadm.linuxifymc;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.FixedValue;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.opuadm.linuxifymc.commands.cli.CliCommand;
import com.opuadm.linuxifymc.commands.cli.cmds.Help;
import com.opuadm.linuxifymc.commands.cli.cmds.Uname;
import com.opuadm.linuxifymc.commands.cli.cmds.CD;
import com.opuadm.linuxifymc.commands.cli.cmds.Ls;
import com.opuadm.linuxifymc.commands.cli.cmds.Chmod;
import com.opuadm.linuxifymc.commands.cli.cmds.Chown;
import com.opuadm.linuxifymc.commands.cli.cmds.Mkdir;
import com.opuadm.linuxifymc.commands.cli.cmds.RM;
import com.opuadm.linuxifymc.commands.cli.cmds.Cat;
import com.opuadm.linuxifymc.commands.cli.cmds.Serverfetch;
import com.opuadm.linuxifymc.commands.cli.cmds.Neofetch;
import com.opuadm.linuxifymc.commands.cli.cmds.Echo;
import com.opuadm.linuxifymc.commands.cli.cmds.Touch;
import com.opuadm.linuxifymc.commands.cli.cmds.Sudo;
import com.opuadm.linuxifymc.commands.cli.cmds.Su;
import com.opuadm.linuxifymc.commands.cli.cmds.Exit;
import com.opuadm.linuxifymc.commands.cli.cmds.Ln;
import com.opuadm.linuxifymc.commands.cli.cmds.Grep;
import com.opuadm.linuxifymc.commands.cli.cmds.Build;
import com.opuadm.linuxifymc.machine.bin.BinCompiler;
import com.opuadm.linuxifymc.machine.bin.BinContext;
import com.opuadm.linuxifymc.machine.fs.FakeFS;
import com.opuadm.linuxifymc.machine.fs.FsRegistry;
import com.opuadm.linuxifymc.machine.shell.Shell;
import com.opuadm.linuxifymc.machine.shell.ShellVars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinuxifyMCTest {
    private Plugin plugin;
    private Class<?> mockBukkit;

    @BeforeEach
    void setUp() throws Exception {
        File testPluginJar;
        Method loadJar;
        org.bukkit.Server server;

        mockBukkit = findMockBukkit();
        server = createServer();
        testPluginJar = new File(System.getProperty("testPluginJar"));
        loadJar = mockBukkit.getMethod("loadJar", File.class);
        loadJar.invoke(null, testPluginJar);
        plugin = server.getPluginManager().getPlugin("LinuxifyMC");
        assertNotNull(plugin);
        if (!plugin.isEnabled()) {
            prepareDataFolder(testPluginJar, plugin);
            server.getPluginManager().enablePlugin(plugin);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        Method unmock;

        unmock = mockBukkit.getMethod("unmock");
        unmock.invoke(null);
    }

    private Player mockShellPlayer(UUID playerId, List<String> messages) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, arguments) -> {
                    switch (method.getName()) {
                        case "getUniqueId":
                            return playerId;
                        case "getName":
                            return "TestPlayer";
                        case "hasPermission":
                        case "isOp":
                            return true;
                        case "sendMessage":
                            if (arguments != null) {
                                for (Object argument : arguments) {
                                    if (argument instanceof String text) {
                                        messages.add(text);
                                    } else if (argument instanceof String[] texts) {
                                        messages.addAll(Arrays.asList(texts));
                                    }
                                }
                            }
                            return null;
                        default:
                            break;
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType == boolean.class) {
                        return false;
                    }
                    if (returnType == int.class) {
                        return 0;
                    }
                    if (returnType == long.class) {
                        return 0L;
                    }
                    return null;
                });
    }

    @Test
    void coreBinariesLoadAsCommands() {
        List<String> failures = new ArrayList<>();
        for (String cmd : ShellVars.cmds) {
            try {
                String className = BinCompiler.coreClass(cmd);
                if (className == null) {
                    failures.add(cmd + ": no class");
                    continue;
                }
                byte[] own = BinCompiler.ownBytes(className);
                if (own == null) {
                    failures.add(cmd + ": no bytes");
                    continue;
                }
                Class<?> clazz = BinCompiler.loadCore(className, own);
                Object instance = clazz.getDeclaredConstructor().newInstance();
                if (!(instance instanceof CliCommand)) {
                    failures.add(cmd + ": not a CliCommand");
                }
            } catch (Throwable failure) {
                failures.add(cmd + ": " + failure);
            }
        }
        assertTrue(failures.isEmpty(), String.join(", ", failures));
    }

    @Test
    void commandClassesInstantiate() {
        List<CliCommand> commands = Arrays.asList(
                new Help(),
                new Uname(),
                new CD(),
                new Ls(),
                new Chmod(),
                new Chown(),
                new Mkdir(),
                new RM(),
                new Ln(),
                new Grep(),
                new Cat(),
                new Serverfetch(),
                new Neofetch(),
                new Echo(),
                new Touch(),
                new Sudo(),
                new Su(),
                new Exit(),
                new Build()
        );
        assertEquals(ShellVars.cmds.length, commands.size());
        for (CliCommand cmd : commands) {
            assertNotNull(cmd);
        }
    }

    @Test
    void dispatchedBinariesExecuteWithoutThrowing() {
        UUID playerId = UUID.randomUUID();
        List<String> messages = new ArrayList<>();
        Player player = mockShellPlayer(playerId, messages);
        FakeFS fs = FsRegistry.getPlayerFS(playerId, player.getName());
        assertNotNull(fs);
        fs.setupSysFiles();
        List<String> failures = new ArrayList<>();
        for (String cmd : ShellVars.cmds) {
            try {
                new Shell().runBinary(player, player, fs, "/usr/bin/" + cmd, cmd, new String[]{cmd});
            } catch (Throwable failure) {
                failures.add(cmd + ": " + failure);
            }
        }
        assertTrue(failures.isEmpty(), String.join(", ", failures));
    }

    @Test
    void binaryContextSnapshots() {
        UUID playerId = UUID.randomUUID();
        List<String> messages = new ArrayList<>();
        Player player = mockShellPlayer(playerId, messages);
        BinContext ctx = BinCompiler.context(player, null, "piped in", "bob");
        assertEquals("piped in", ctx.stdin());
        assertEquals("bob", ctx.user());
        assertTrue(ctx.time() > 0L);
        assertNull(ctx.fs());
        ctx.out("hello");
        for (int i = 0; i < 200; i++) {
            ctx.out("line");
        }
        assertEquals("hello", messages.getFirst());
        assertTrue(messages.contains("[output truncated]"));
    }

    @Test
    void enablesAndRegistersCommands() throws Exception {
        Method getCommand;

        getCommand = plugin.getClass().getMethod("getCommand", String.class);
        assertTrue(plugin.isEnabled());
        assertNotNull(getCommand.invoke(plugin, "cli"));
        assertNotNull(getCommand.invoke(plugin, "linuxifymc"));
        assertNotNull(getCommand.invoke(plugin, "vcomputer"));
    }

    @Test
    void createsPlayerAndRootUsersWhenPlayerJoins() throws Exception {
        Method getDatabase;
        Method ensurePlayerUsers;
        Method singleValueQuery;
        Object database;
        Player player;
        UUID playerId;
        Number playerCount;
        Number rootCount;

        playerId = UUID.randomUUID();
        player = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, arguments) -> {
                    String methodName;

                    methodName = method.getName();
                    if (methodName.equals("getUniqueId")) {
                        return playerId;
                    }
                    if (methodName.equals("getName")) {
                        return "TestPlayer";
                    }
                    throw new UnsupportedOperationException(methodName);
                });
        getDatabase = plugin.getClass().getMethod("getDatabase");
        database = getDatabase.invoke(plugin);
        singleValueQuery = database.getClass().getMethod("singleValueQuery", String.class, Object[].class);
        ensurePlayerUsers = plugin.getClass().getDeclaredMethod("ensurePlayerUsers", Player.class);
        ensurePlayerUsers.setAccessible(true);
        ensurePlayerUsers.invoke(plugin, player);
        playerCount = (Number) singleValueQuery.invoke(database,
                "SELECT COUNT(*) FROM vm_users WHERE player_uuid = ? AND username = ?",
                new Object[]{player.getUniqueId().toString(), player.getName()});
        rootCount = (Number) singleValueQuery.invoke(database,
                "SELECT COUNT(*) FROM vm_users WHERE player_uuid = ? AND username = ?",
                new Object[]{player.getUniqueId().toString(), "root"});

        assertEquals(1L, playerCount.longValue());
        assertEquals(1L, rootCount.longValue());
    }

    private org.bukkit.Server createServer() throws Exception {
        Class<?> compatibleServerType;
        Class<?> serverMockType;
        File pluginsFolder;
        MethodHandles.Lookup lookup;
        Method mock;
        Object compatibleServer;
        DynamicType.Unloaded<?> unloaded;

        if (mockBukkit.getName().startsWith("org.mockbukkit")) {
            mock = mockBukkit.getMethod("mock");
            return (org.bukkit.Server) mock.invoke(null);
        }

        serverMockType = Class.forName("be.seeseemelk.mockbukkit.ServerMock");
        pluginsFolder = Files.createTempDirectory("MockBukkit-plugins-").toFile();
        lookup = MethodHandles.privateLookupIn(serverMockType, MethodHandles.lookup());
        unloaded = new ByteBuddy()
                .subclass(serverMockType)
                .defineMethod("getPluginsFolder", File.class, Visibility.PUBLIC)
                .intercept(FixedValue.value(pluginsFolder))
                .make();
        try (unloaded) {
            compatibleServerType = unloaded
                    .load(serverMockType.getClassLoader(), ClassLoadingStrategy.UsingLookup.of(lookup))
                    .getLoaded();
        }
        compatibleServer = compatibleServerType.getConstructor().newInstance();
        mock = mockBukkit.getMethod("mock", serverMockType);
        return (org.bukkit.Server) mock.invoke(null, compatibleServer);
    }

    private void prepareDataFolder(File testPluginJar, Plugin loadedPlugin) throws Exception {
        InputStream configInput;
        Path configPath;
        ZipEntry configEntry;
        ZipFile pluginArchive;

        pluginArchive = new ZipFile(testPluginJar);
        configEntry = pluginArchive.getEntry("config.yml");
        assertNotNull(configEntry);
        configPath = loadedPlugin.getDataFolder().toPath().resolve("config.yml");
        Files.createDirectories(configPath.getParent());
        configInput = pluginArchive.getInputStream(configEntry);
        try (pluginArchive; configInput) {
            Files.copy(configInput, configPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Class<?> findMockBukkit() throws ClassNotFoundException {
        Class<?> type;

        try {
            type = Class.forName("org.mockbukkit.mockbukkit.MockBukkit");
        } catch (ClassNotFoundException exception) {
            type = Class.forName("be.seeseemelk.mockbukkit.MockBukkit");
        }
        return type;
    }
}
