package com.opuadm.linuxifymc.machine.bin;

import com.opuadm.linuxifymc.LinuxifyMC;
import com.opuadm.linuxifymc.machine.fs.FakeFS;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class BinCompiler {
    private BinCompiler() {
    }

    public static final long MAX_RUN_MILLIS = 10_000L;
    public static final int MAX_OUT_LINES = 128;
    public static final int MAX_SOURCE_BYTES = 64 * 1024;

    static final List<String> DEFAULT_DENIED = List.of(
            "java/lang/System",
            "java/lang/Runtime",
            "java/lang/ProcessBuilder",
            "java/lang/Process",
            "java/io/",
            "java/nio/",
            "java/net/",
            "java/lang/reflect/",
            "lookup",
            "publicLookup",
            "LambdaMetafactory",
            "java/lang/Thread",
            "java/util/concurrent/",
            "java/lang/ClassLoader",
            "java/lang/ThreadGroup",
            "jdk/",
            "sun/",
            "org/bukkit/",
            "org/spigot/",
            "com/opuadm/linuxifymc/Database",
            "com/opuadm/linuxifymc/LinuxifyMC",
            "com/opuadm/linuxifymc/commands/",
            "com/opuadm/linuxifymc/machine/bin/BinCompiler",
            "com/opuadm/linuxifymc/machine/fs/FsRegistry",
            "com/opuadm/linuxifymc/machine/login/",
            "com/opuadm/linuxifymc/machine/shell/Shell",
            "com/opuadm/linuxifymc/machine/shell/SudoContext",
            "com/opuadm/linuxifymc/machine/states/",
            "com/opuadm/linuxifymc/machine/logs/"
    );

    static final List<String> DEFAULT_ALLOWED = List.of(
            "com/opuadm/linuxifymc/machine/bin/BinContext",
            "com/opuadm/linuxifymc/machine/fs/FakeFS",
            "java/lang/invoke/StringConcatFactory",
            "java/lang/invoke/MethodType",
            "java/lang/invoke/MethodHandles$Lookup",
            "java/lang/invoke/MethodHandles",
            "java/lang/invoke/CallSite",
            "org/bukkit/ChatColor",
            "org/bukkit/Color",
            "org/bukkit/util/Vector",
            "java/net/URI",
            "java/net/URLEncoder",
            "java/net/URLDecoder"
    );

    private static final ExecutorService RUNNERS = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r);
        thread.setDaemon(true);
        return thread;
    });

    private static final Map<String, byte[]> CORE_CACHE = new ConcurrentHashMap<>();

    private static final String CORE_PACKAGE = "com/opuadm/linuxifymc/commands/cli/cmds/";

    public static final String CORE_PACKAGE_DOTS = "com.opuadm.linuxifymc.commands.cli.cmds.";

    public static String[] headerOf(String content) {
        if (content == null) return new String[]{"", ""};
        String[] parts = content.split("\n", 2);
        if (parts.length < 2) return new String[]{parts[0], ""};
        return parts;
    }

    public static byte[] ownBytes(String className) {
        byte[] hit = CORE_CACHE.get(className);
        if (hit != null) return hit;
        try (InputStream in = BinCompiler.class.getClassLoader().getResourceAsStream(CORE_PACKAGE + className + ".class")) {
            if (in == null) return null;
            byte[] loaded = in.readAllBytes();
            if (loaded.length == 0) return null;
            CORE_CACHE.put(className, loaded);
            return loaded;
        } catch (Exception e) {
            return null;
        }
    }

    public static String coreClass(String cmd) {
        if (cmd == null || cmd.isEmpty()) return null;
        String lower = cmd.toLowerCase();
        String cap = Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        for (String candidate : new String[]{cap, lower.toUpperCase(), lower}) {
            if (ownBytes(candidate) != null) return candidate;
        }
        return null;
    }

    public static BinContext context(CommandSender sender, FakeFS fs, String stdin, String user) {
        return new BinContext() {
            int lines = 0;
            boolean cut = false;

            @Override
            public void out(String line) {
                if (line == null || cut) return;
                if (lines >= MAX_OUT_LINES) {
                    cut = true;
                    sender.sendMessage("[output truncated]");
                    return;
                }
                lines++;
                sender.sendMessage(line);
            }

            @Override
            public FakeFS fs() {
                return fs;
            }

            @Override
            public String stdin() {
                return stdin;
            }

            @Override
            public String user() {
                return user;
            }

            @Override
            public long time() {
                return System.currentTimeMillis();
            }
        };
    }

    public static byte[] compile(String className, String source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("building requires a JDK host");
        }
        String classpath;
        try {
            classpath = new java.io.File(LinuxifyMC.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath();
        } catch (Exception e) {
            throw new IllegalStateException("cannot locate plugin classes: " + e.getMessage());
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager standard = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        Map<String, ByteArrayOutputStream> classes = new HashMap<>();
        JavaFileManager files = new ForwardingJavaFileManager<>(standard) {
            @Override
            public JavaFileObject getJavaFileForOutput(Location location, String name, JavaFileObject.Kind kind, FileObject sibling) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                classes.put(name, buffer);
                return new SimpleJavaFileObject(URI.create("mem:///" + name + ".class"), kind) {
                    @Override
                    public OutputStream openOutputStream() {
                        return buffer;
                    }
                };
            }
        };
        JavaFileObject unit = new SimpleJavaFileObject(URI.create("mem:///" + className + ".java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        boolean ok;
        try {
            ok = compiler.getTask(null, files, diagnostics, List.of("-proc:none", "-cp", classpath), null, List.of(unit)).call();
        } catch (Exception e) {
            throw new IllegalArgumentException("compile failed: " + e.getMessage());
        } finally {
            try {
                files.close();
            } catch (Exception ignored) {
            }
        }
        if (!ok) {
            StringBuilder errors = new StringBuilder();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                    errors.append("error: line ").append(diagnostic.getLineNumber()).append(": ").append(diagnostic.getMessage(null)).append('\n');
                }
            }
            throw new IllegalArgumentException(errors.toString().trim());
        }
        if (classes.size() != 1 || !classes.containsKey(className)) {
            throw new IllegalArgumentException("one top-level class per file, no inner classes or lambdas");
        }
        ByteArrayOutputStream compiled = classes.get(className);
        if (compiled.size() == 0) {
            throw new IllegalArgumentException("compile produced no output for " + className);
        }
        return compiled.toByteArray();
    }

    static List<String> sandboxList(String key, List<String> fallback) {
        try {
            FileConfiguration config = JavaPlugin.getPlugin(LinuxifyMC.class).getConfig();
            if (config.isSet(key)) {
                return config.getStringList(key);
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    public static void verify(byte[] bytecode) {
        List<String> allowed = sandboxList("binaries.sandbox.allowed", DEFAULT_ALLOWED);
        List<String> denied = sandboxList("binaries.sandbox.denied", DEFAULT_DENIED);
        String text = new String(bytecode, StandardCharsets.ISO_8859_1);
        for (String entry : allowed) {
            if (entry != null && !entry.isEmpty()) text = text.replace(entry, "");
        }
        for (String entry : denied) {
            if (entry == null || entry.isEmpty()) continue;
            int at = text.indexOf(entry);
            if (at >= 0) {
                int from = Math.max(0, at - 24);
                int to = Math.min(text.length(), at + entry.length() + 24);
                String context = text.substring(from, to).replace('\n', ' ').replace('\r', ' ');
                throw new SecurityException("blocked reference: " + entry + " near [" + context + "]");
            }
        }
    }

    public static boolean isClassBytes(byte[] bytecode) {
        return bytecode != null && bytecode.length > 4 &&
                (bytecode[0] & 0xFF) == 0xCA && (bytecode[1] & 0xFF) == 0xFE &&
                (bytecode[2] & 0xFF) == 0xBA && (bytecode[3] & 0xFF) == 0xBE;
    }

    public static byte[] decodeBody(String program) {
        try {
            return Base64.getMimeDecoder().decode(program.trim());
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }

    public static Class<?> load(String className, byte[] bytecode) throws ClassNotFoundException {
        ClassLoader parent = BinCompiler.class.getClassLoader();
        ClassLoader loader = new ClassLoader(parent) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (name.equals(className)) {
                    return defineClass(name, bytecode, 0, bytecode.length);
                }
                throw new ClassNotFoundException(name);
            }
        };
        return Class.forName(className, true, loader);
    }

    public static Class<?> loadCore(String className, byte[] bytecode) throws ClassNotFoundException {
        return load(CORE_PACKAGE_DOTS + className, bytecode);
    }

    public static int execute(Class<?> binary, BinContext ctx, String[] argv, long timeoutMillis) throws Exception {
        Method main;
        try {
            main = binary.getMethod("main", BinContext.class, String[].class);
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodException("binary has no main(BinContext, String[])");
        }
        if (!Modifier.isStatic(main.getModifiers()) || !Modifier.isPublic(main.getModifiers())) {
            throw new IllegalAccessException("binary main must be public static");
        }
        if (main.getReturnType() != int.class && main.getReturnType() != Integer.class) {
            throw new IllegalArgumentException("binary main must return int");
        }
        Callable<Integer> task = () -> (Integer) main.invoke(null, ctx, argv);
        Future<Integer> future = RUNNERS.submit(task);
        try {
            Integer code = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
            return code != null ? code : 0;
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        }
    }
}
