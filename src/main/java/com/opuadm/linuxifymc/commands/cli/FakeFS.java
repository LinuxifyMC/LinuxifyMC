// those who try to mimic the ext4 filesystem
/* package com.opuadm.linuxifymc.commands.cli;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.opuadm.linuxifymc.LinuxifyMC;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class FakeFS {
    private static final Map<UUID, FakeFS> PLAYER_FS = new ConcurrentHashMap<>();
    private static final Logger LOGGER = Logger.getLogger(FakeFS.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_GROUP = "users";
    public static final String CURRENT_VERSION = "0.1.0";

    static { MAPPER.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY); }

    private final String username;
    private String currentDirectory;
    private FSNode root;
    private final ReentrantReadWriteLock fsLock = new ReentrantReadWriteLock();

    // FSNode record definition
    public record FSNode(String name, FSNode parent, String owner, String group, NodeType type,
                         Map<String, FSNode> children, byte[] content, long creationTime, String permissions) {
        public FSNode(String name, FSNode parent, String owner, String group, NodeType type) {
            this(name, parent, owner, group, type, new ConcurrentHashMap<>(), null,
                    System.currentTimeMillis() / 1000, "644");
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FSNode that)) return false;
            return Objects.equals(name, that.name) && Objects.equals(owner, that.owner) &&
                    Objects.equals(group, that.group) && type == that.type;
        }
        @Override
        public int hashCode() { return Objects.hash(name, owner, group, type); }
    }

    public enum NodeType { FILE, DIRECTORY }

    // Constructor and initialization
    public FakeFS(String username) {
        this.username = Objects.requireNonNull(username, "Username cannot be null");
        this.root = new FSNode("", null, "root", "root", NodeType.DIRECTORY);
        this.currentDirectory = "/home/" + username;
        initializeFS();
    }

    private void initializeFS() {
        fsLock.writeLock().lock();
        try {
            List<String> directories = List.of("/home", "/home/" + username, "/usr", "/usr/bin", "/bin");
            for (String dir : directories) {
                if (!createDirectory(dir, username, "755")) {
                    LOGGER.warning("Failed to create directory: " + dir);
                }
            }
            Map<String, String> files = new HashMap<>();
            for (String cmd : CommandVarsCLI.cmds) {
                files.put("/usr/bin/" + cmd, cmd + " command");
            }
            files.put("/home/" + username + "/.mcshrc", "PS1='[" + username + "@mc ~]$ '\nPATH=/usr/bin:/bin");
            files.forEach((path, content) -> {
                if (!createFile(path, content)) {  // Using the two-parameter version
                    LOGGER.warning("Failed to create file: " + path);
                }
            });
        } finally {
            fsLock.writeLock().unlock();
        }
    }

    // Path and navigation methods
    public String getCurrentDirectory() {
        fsLock.readLock().lock();
        try { return currentDirectory; }
        finally { fsLock.readLock().unlock(); }
    }

    public String getFile(String path) {
        Objects.requireNonNull(path, "Path cannot be null");
        fsLock.readLock().lock();
        try {
            FSNode node = resolvePath(path);
            if (node == null || node.type() != NodeType.FILE || node.content() == null) {
                return null;
            }
            return new String(node.content(), StandardCharsets.UTF_8);
        } finally {
            fsLock.readLock().unlock();
        }
    }

    public boolean setCurrentDirectory(String path) {
        Objects.requireNonNull(path, "Path cannot be null");
        fsLock.writeLock().lock();
        try {
            FSNode node = resolvePath(path);
            if (node != null && node.type == NodeType.DIRECTORY) {
                currentDirectory = getFullPath(node);
                return true;
            }
            return false;
        } finally { fsLock.writeLock().unlock(); }
    }

    public boolean directoryExists(String path) {
        Objects.requireNonNull(path, "Path cannot be null");
        fsLock.readLock().lock();
        try {
            FSNode node = resolvePath(path);
            return node != null && node.type == NodeType.DIRECTORY;
        } finally { fsLock.readLock().unlock(); }
    }

    private FSNode resolvePath(String path) {
        if (path == null || path.isEmpty()) return root;
        String absolutePath = path.startsWith("/") ? path :
                currentDirectory.equals("/") ? "/" + path : currentDirectory + "/" + path;
        String[] parts = absolutePath.replaceAll("^/+", "").split("/");
        FSNode current = root;

        for (String part : parts) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                current = current.parent != null ? current.parent : root;
                continue;
            }

            FSNode next = current.children.get(part);
            if (next == null && getFullPath(current).equals("/home")) {
                for (String childName : current.children.keySet()) {
                    if (childName.equalsIgnoreCase(part)) {
                        next = current.children.get(childName);
                        break;
                    }
                }
            }
            current = next;
            if (current == null) return null;
        }
        return current;
    }

    private String getFullPath(FSNode node) {
        if (node == root) return "/";
        List<String> parts = new ArrayList<>();
        FSNode current = node;
        while (current != root) {
            parts.add(current.name);
            current = current.parent;
        }
        Collections.reverse(parts);
        return "/" + String.join("/", parts);
    }

    public boolean deleteNode(String path, boolean recursive) {
        Objects.requireNonNull(path, "Path cannot be null");
        fsLock.writeLock().lock();
        try {
            FSNode node = resolvePath(path);
            if (node == null || node == root) {
                return false;
            }

            if (node.type == NodeType.DIRECTORY && !node.children.isEmpty() && !recursive) {
                return false;
            }

            if (node.parent != null) {
                node.parent.children.remove(node.name);
                return true;
            }
            return false;
        } finally {
            fsLock.writeLock().unlock();
        }
    }

    // File system operations
    public String listDirectory(String path, boolean showHidden, boolean showDetails) {
        Objects.requireNonNull(path, "Path cannot be null");
        fsLock.readLock().lock();
        try {
            FSNode dir = resolvePath(path);
            if (dir == null || dir.type != NodeType.DIRECTORY)
                return LinuxifyMC.shellname + ": ls: " + path + ": No such file or directory";

            List<FSNode> entries = new ArrayList<>(dir.children.values());
            if (showHidden) {
                entries.add(new FSNode(".", dir, dir.owner, dir.group, NodeType.DIRECTORY));
                entries.add(new FSNode("..", dir.parent != null ? dir.parent : dir,
                        dir.owner, dir.group, NodeType.DIRECTORY));
            }

            entries.sort((a, b) -> a.type != b.type ?
                    (a.type == NodeType.DIRECTORY ? -1 : 1) :
                    a.name.compareToIgnoreCase(b.name));

            if (entries.isEmpty() && !showHidden) return "";

            StringBuilder output = new StringBuilder();
            if (showDetails) output.append("total ").append(entries.size()).append("\n");

            for (FSNode node : entries) {
                if (!showHidden && node.name.startsWith(".")) continue;

                if (showDetails) {
                    String permsStr = formatPermissions(node);
                    long size = node.content != null ? node.content.length : 0;

                    java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("MMM d HH:mm");
                    dateFormat.setTimeZone(java.util.TimeZone.getDefault());
                    String date = dateFormat.format(new java.util.Date(node.creationTime * 1000));

                    output.append(String.format("%s 1 %-8s %-8s %4d %s %s%s\n",
                            permsStr, node.owner, node.group, size, date,
                            node.name, node.type == NodeType.DIRECTORY ? "/" : ""));
                } else {
                    output.append(node.name)
                            .append(node.type == NodeType.DIRECTORY ? "/" : "")
                            .append("  ");
                }
            }
            return output.toString().trim();
        } finally {
            fsLock.readLock().unlock();
        }
    }

    public boolean appendToFile(String path, String content) {
        if (content == null) content = "";

        String currentContent = getFile(path);
        if (currentContent == null) {
            return createFile(path, content);
        }

        return createFile(path, currentContent + content);
    }

    // Node update helper
    private boolean updateNode(FSNode node, Function<FSNode, FSNode> updater) {
        if (node == null) return false;
        FSNode newNode = updater.apply(node);
        if (node.parent() != null) {
            node.parent().children().put(node.name(), newNode);
        } else if (node == root) {
            root = newNode;
        }
        return true;
    }

    // Permission operations
    public boolean chmod(String path, String permissions) {
        Objects.requireNonNull(path, "Path cannot be null");
        Objects.requireNonNull(permissions, "Permissions cannot be null");
        fsLock.writeLock().lock();
        try {
            FSNode node = resolvePath(path);
            if (node == null) return false;
            String newPerms = node.permissions();

            if (permissions.matches("^[0-7]{3}$")) {
                newPerms = permissions;
            } else if (permissions.matches("^[ugo]+[-+=][rwx]+$")) {
                char op = findOperator(permissions);
                if (op == '\0') return false;

                int opIndex = permissions.indexOf(op);
                String who = permissions.substring(0, opIndex);
                String perms = permissions.substring(opIndex + 1);

                boolean[] bits = getBitsFromOctal(newPerms);
                applyPermissionsChange(bits, who, perms, op);
                newPerms = convertBitsToOctal(bits);
            } else return false;

            final String finalPerms = newPerms;
            return updateNode(node, n -> new FSNode(n.name(), n.parent(), n.owner(), n.group(),
                    n.type(), n.children(), n.content(), n.creationTime(), finalPerms));
        } finally {
            fsLock.writeLock().unlock();
        }
    }

    public boolean chown(String path, String newOwner) {
        Objects.requireNonNull(path, "Path cannot be null");
        Objects.requireNonNull(newOwner, "New owner cannot be null");
        fsLock.writeLock().lock();
        try {
            FSNode node = resolvePath(path);
            return updateNode(node, n -> new FSNode(n.name(), n.parent(), newOwner, n.group(),
                    n.type(), n.children(), n.content(), n.creationTime(), n.permissions()));
        } finally {
            fsLock.writeLock().unlock();
        }
    }

    private String formatPermissions(FSNode node) {
        int value = Integer.parseInt(node.permissions(), 8);
        StringBuilder result = new StringBuilder(10);

        result.append(node.type == NodeType.DIRECTORY ? 'd' : '-');

        for (int i = 0; i < 3; i++) {
            int shift = 6 - (i * 3);
            result.append(((value >> shift) & 4) != 0 ? 'r' : '-')
                    .append(((value >> shift) & 2) != 0 ? 'w' : '-')
                    .append(((value >> shift) & 1) != 0 ? 'x' : '-');
        }

        return result.toString();
    }

    // Permission helpers
    private char findOperator(String permissions) {
        for (char op : new char[]{'+', '-', '='}) {
            if (permissions.indexOf(op) != -1) return op;
        }
        return '\0';
    }

    private boolean[] getBitsFromOctal(String octalPerms) {
        boolean[] bits = new boolean[9];
        int value = Integer.parseInt(octalPerms, 8);

        for (int i = 0; i < 9; i++) {
            bits[i] = ((value >> (8 - i)) & 1) != 0;
        }

        return bits;
    }

    private void applyPermissionsChange(boolean[] bits, String who, String perms, char op) {
        for (char w : who.toCharArray()) {
            for (char perm : perms.toCharArray()) {
                int pos = getPermissionPosition(w, perm);
                if (pos == -1) continue;

                if (op == '+') {
                    bits[pos] = true;
                } else if (op == '-') {
                    bits[pos] = false;
                } else {
                    int startPos = pos - (pos % 3);
                    for (int i = 0; i < 3; i++) {
                        bits[startPos + i] = false;
                    }
                    bits[pos] = true;
                }
            }
        }
    }

    private String convertBitsToOctal(boolean[] bits) {
        int octalValue = 0;
        for (int i = 0; i < 9; i++) {
            if (bits[i]) {
                octalValue |= (1 << (8 - i));
            }
        }
        return String.format("%03o", octalValue);
    }

    private int getPermissionPosition(char who, char perm) {
        if (who == 'u') {
            if (perm == 'r') return 0;
            if (perm == 'w') return 1;
            if (perm == 'x') return 2;
        } else if (who == 'g') {
            if (perm == 'r') return 3;
            if (perm == 'w') return 4;
            if (perm == 'x') return 5;
        } else if (who == 'o') {
            if (perm == 'r') return 6;
            if (perm == 'w') return 7;
            if (perm == 'x') return 8;
        }
        return -1;
    }

    // Serialization methods
    private void serialize() {
        fsLock.readLock().lock();
        try {
            Map<String, Object> state = new HashMap<>();
            state.put("version", CURRENT_VERSION);
            serializeNode(root, "/", state);
            String json = MAPPER.writeValueAsString(state);
            Player player = Bukkit.getPlayer(username);
            if (player != null) {
                LinuxifyMC plugin = (LinuxifyMC) Bukkit.getPluginManager().getPlugin("LinuxifyMC");
                if (plugin != null && plugin.getDatabase() != null) {
                    plugin.getDatabase().saveData(player, json);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to serialize filesystem for " + username, e);
            throw new RuntimeException("Filesystem serialization failed", e);
        } finally { fsLock.readLock().unlock(); }
    }

    private void serializeNode(FSNode node, String path, Map<String, Object> state) {
        if (node == null) return;
        Map<String, Object> nodeData = new HashMap<>(5); // Specify initial capacity
        nodeData.put("o", node.owner);
        nodeData.put("g", node.group);
        nodeData.put("t", node.type.name());
        nodeData.put("ct", node.creationTime);
        nodeData.put("p", node.permissions);
        if (node.content != null) {
            nodeData.put("c", new String(node.content, StandardCharsets.UTF_8));
        }
        state.put(path, nodeData);
        if (node.type == NodeType.DIRECTORY) {
            node.children.forEach((name, child) -> {
                String childPath = path.equals("/") ? "/" + name : path + "/" + name;
                serializeNode(child, childPath, state);
            });
        }
    }

    // Static utility methods
    public static FakeFS getPlayerFS(UUID playerUUID, String username) {
        Objects.requireNonNull(playerUUID, "Player UUID cannot be null");
        Objects.requireNonNull(username, "Username cannot be null");
        return PLAYER_FS.computeIfAbsent(playerUUID, uuid -> {
            try {
                LinuxifyMC plugin = (LinuxifyMC) Bukkit.getPluginManager().getPlugin("LinuxifyMC");
                if (plugin != null && plugin.getDatabase() != null) {
                    String fsData = plugin.getDatabase().loadFSData(playerUUID);
                    if (fsData != null) return deserialize(fsData, username);
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to load filesystem for " + username, e);
            }
            return new FakeFS(username);
        });
    }

    public static void removePlayerFS(UUID playerUUID) {
        PLAYER_FS.remove(Objects.requireNonNull(playerUUID, "Player UUID cannot be null"));
    }

    public static void cleanup() {
        PLAYER_FS.forEach((uuid, fs) -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) saveFS(player, fs);
        });
        PLAYER_FS.clear();
    }

    public static boolean saveFS(Player player, FakeFS fs) {
        if (player == null || fs == null) return false;
        try {
            fs.serialize();
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to save filesystem for player " + player.getName(), e);
            return false;
        }
    }

    public static FakeFS deserialize(String json, String username) {
        FakeFS fs = new FakeFS(username);
        try {
            TypeReference<Map<String, Object>> typeRef = new TypeReference<>() {};
            Map<String, Object> state = MAPPER.readValue(json, typeRef);
            fs.fsLock.writeLock().lock();
            try {
                for (Map.Entry<String, Object> entry : state.entrySet()) {
                    if (entry.getKey().equals("version")) continue;
                    String path = entry.getKey();
                    if (path.equals("/")) continue;

                    Object value = entry.getValue();
                    if (!(value instanceof Map)) continue;

                    Map<String, Object> nodeData = MAPPER.convertValue(value,
                            new TypeReference<>() {});

                    NodeType type = NodeType.valueOf((String) nodeData.get("t"));
                    String owner = (String) nodeData.get("o");
                    String group = (String) nodeData.get("g");
                    String permissions = (String) nodeData.get("p");
                    // Extract the stored creation time
                    long creationTime = ((Number) nodeData.get("ct")).longValue();

                    if (type == NodeType.FILE) {
                        fs.createFile(path, (String) nodeData.get("c"), owner, group, permissions, creationTime);
                    } else {
                        fs.createDirectory(path, owner, group, permissions, creationTime);
                    }
                }
            } finally { fs.fsLock.writeLock().unlock(); }
            return fs;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to deserialize filesystem", e);
            return fs;
        }
    }

    public static void checkAndUpgradeFilesystems() {
        LOGGER.info("Checking filesystem compatibility...");
        LinuxifyMC plugin = (LinuxifyMC) Bukkit.getPluginManager().getPlugin("LinuxifyMC");
        if (plugin == null || plugin.getDatabase() == null) return;

        try (Connection conn = plugin.getDatabase().connection;
             PreparedStatement stmt = conn.prepareStatement("SELECT uuid, fs_data FROM player_data");
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String uuid = rs.getString("uuid");
                String fsData = rs.getString("fs_data");
                if (fsData == null || fsData.isEmpty()) continue;

                try {
                    Map<String, Object> state = MAPPER.readValue(fsData, new TypeReference<>() {});
                    String version = (String) state.get("version");
                    if (version != null && version.equals(CURRENT_VERSION)) continue;

                    LOGGER.info("Upgrading filesystem for player " + uuid + " from " + version + " to " + CURRENT_VERSION);
                    state.put("version", CURRENT_VERSION);

                    try (PreparedStatement update = conn.prepareStatement(
                            "UPDATE player_data SET fs_data = ? WHERE uuid = ?")) {
                        update.setString(1, MAPPER.writeValueAsString(state));
                        update.setString(2, uuid);
                        update.executeUpdate();
                    }
                } catch (Exception e) {
                    LOGGER.warning("Failed to upgrade filesystem for " + uuid + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error during filesystem version check", e);
        }
    }

    // Create things (Dirs, Files, etc.)
    public boolean createDirectory(String path, String owner, String group, String permissions, long creationTime) {
        Objects.requireNonNull(path, "Path cannot be null");
        fsLock.writeLock().lock();
        try {
            String absolutePath = path.startsWith("/") ? path :
                    currentDirectory.equals("/") ? "/" + path : currentDirectory + "/" + path;

            String actualOwner = owner != null ? owner : username;
            String actualGroup = group != null ? group : DEFAULT_GROUP;
            String actualPermissions = permissions != null ? permissions : "755";
            long timestamp = creationTime > 0 ? creationTime : System.currentTimeMillis() / 1000;

            String[] parts = absolutePath.replaceAll("^/+", "").split("/");
            FSNode current = root;

            for (String part : parts) {
                if (part.isEmpty()) continue;
                final FSNode parent = current;
                current.children.computeIfAbsent(part,
                        k -> new FSNode(k, parent, actualOwner, actualGroup, NodeType.DIRECTORY,
                                new ConcurrentHashMap<>(), null, timestamp, actualPermissions));
                current = current.children.get(part);
                if (current.type != NodeType.DIRECTORY) return false;
            }
            return true;
        } finally {
            fsLock.writeLock().unlock();
        }
    }

    public boolean createDirectory(String path, String owner, String permissions) {
        return createDirectory(path, owner, DEFAULT_GROUP, permissions, 0);
    }

    public boolean createFile(String path, String content, String owner, String group, String permissions, long creationTime) {
        Objects.requireNonNull(path, "Path cannot be null");
        if (content == null) content = "";
        fsLock.writeLock().lock();
        try {
            String absolutePath = path.startsWith("/") ? path :
                    currentDirectory.equals("/") ? "/" + path : currentDirectory + "/" + path;

            String actualOwner = owner != null ? owner : username;
            String actualGroup = group != null ? group : DEFAULT_GROUP;
            String actualPermissions = permissions != null ? permissions : "644";
            long timestamp = creationTime > 0 ? creationTime : System.currentTimeMillis() / 1000;

            int lastSlash = absolutePath.lastIndexOf('/');
            if (lastSlash == -1) return false;
            String dirPath = lastSlash > 0 ? absolutePath.substring(0, lastSlash) : "/";
            String fileName = absolutePath.substring(lastSlash + 1);
            if (fileName.isEmpty()) return false;

            FSNode parent = resolvePath(dirPath);
            if (parent == null || parent.type != NodeType.DIRECTORY) return false;
            FSNode file = new FSNode(fileName, parent, actualOwner, actualGroup, NodeType.FILE,
                    new HashMap<>(), content.getBytes(StandardCharsets.UTF_8),
                    timestamp, actualPermissions);
            parent.children.put(fileName, file);
            return true;
        } finally {
            fsLock.writeLock().unlock();
        }
    }

    public boolean createFile(String path, String content) {
        return createFile(path, content, null, null, null, 0);
    }

    public boolean createEmptyFile(String path) {
        return createFile(path, "");
    }
} */