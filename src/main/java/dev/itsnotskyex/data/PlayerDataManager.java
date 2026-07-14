package dev.itsnotskyex.data;

import dev.itsnotskyex.EndlessCoinflip;
import dev.itsnotskyex.storage.FileDataStore;
import dev.itsnotskyex.storage.LeaderboardEntry;
import dev.itsnotskyex.storage.MysqlDataStore;
import dev.itsnotskyex.storage.PlayerDataStore;
import dev.itsnotskyex.storage.SqliteDataStore;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlayerDataManager {

    private final EndlessCoinflip plugin;
    private final Map<UUID, PlayerData> cache = new HashMap<>();
    private final PlayerDataStore store;

    public PlayerDataManager(EndlessCoinflip plugin) {
        this.plugin = plugin;
        String currentType = resolveType();
        this.store = buildStore(currentType);
        this.store.init();

        String previousType = readMarker();
        if (previousType == null) {
            File legacyFolder = new File(plugin.getDataFolder(), "playerdata");
            previousType = legacyFolder.isDirectory() ? "FILE" : null;
        }

        if (previousType == null || previousType.equals(currentType)) {
            writeMarker(currentType);
        } else if (migrate(previousType, currentType)) {
            writeMarker(currentType);
        }
    }

    private String resolveType() {
        return plugin.getConfig().getString("storage.type", "FILE").trim().toUpperCase();
    }

    private PlayerDataStore buildStore(String type) {
        return switch (type) {
            case "SQLITE" -> new SqliteDataStore(plugin);
            case "MYSQL" -> new MysqlDataStore(plugin);
            default -> new FileDataStore(plugin);
        };
    }

    private File markerFile() {
        return new File(plugin.getDataFolder(), ".storage-type");
    }

    private String readMarker() {
        File file = markerFile();
        if (!file.exists()) return null;
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            return lines.isEmpty() ? null : lines.get(0).trim().toUpperCase();
        } catch (IOException e) {
            return null;
        }
    }

    private void writeMarker(String type) {
        try {
            Files.writeString(markerFile().toPath(), type, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write .storage-type: " + e.getMessage());
        }
    }

    private boolean migrate(String fromType, String toType) {
        PlayerDataStore oldStore = buildStore(fromType);
        oldStore.init();

        List<UUID> uuids;
        try {
            uuids = oldStore.getAllUuids();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to read data from previous " + fromType + " storage: " + e.getMessage());
            oldStore.close();
            return false;
        }

        if (uuids.isEmpty()) {
            oldStore.close();
            return true;
        }

        plugin.getLogger().info("Migrating " + uuids.size() + " player(s) from " + fromType + " to " + toType + "...");
        int migrated = 0;
        int failed = 0;
        for (UUID uuid : uuids) {
            try {
                PlayerData data = oldStore.load(uuid);
                store.save(data);
                migrated++;
            } catch (Exception e) {
                failed++;
                plugin.getLogger().warning("Failed to migrate player " + uuid + ": " + e.getMessage());
            }
        }

        if (failed > 0) {
            plugin.getLogger().warning("Migrated " + migrated + " player(s), but " + failed
                    + " failed (see warnings above). Will retry on next restart.");
            oldStore.close();
            return false;
        }

        String backupInfo = oldStore.backup();
        oldStore.close();
        if (backupInfo != null) {
            plugin.getLogger().info("Migrated " + migrated + " player(s) from " + fromType + " to " + toType + ". Old data backed up: " + backupInfo);
        } else {
            plugin.getLogger().warning("Migrated " + migrated + " player(s) from " + fromType + " to " + toType
                    + ", but failed to back up the old data — please check/clean it up manually.");
        }
        return true;
    }

    public PlayerData get(UUID uuid) {
        return cache.computeIfAbsent(uuid, store::load);
    }

    public void save(PlayerData data) {
        store.save(data);
    }

    public void saveAll() {
        cache.values().forEach(store::save);
    }

    public List<LeaderboardEntry> getLeaderboard(String sortBy, int limit, int offset) {
        return store.getLeaderboard(sortBy, limit, offset);
    }

    public int getLeaderboardSize() {
        return store.getLeaderboardSize();
    }

    public void close() {
        store.close();
    }
}
