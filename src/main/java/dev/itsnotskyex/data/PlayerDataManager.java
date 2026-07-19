package dev.itsnotskyex.data;

import dev.itsnotskyex.EndlessCoinflip;
import dev.itsnotskyex.storage.FileDataStore;
import dev.itsnotskyex.storage.LeaderboardPage;
import dev.itsnotskyex.storage.MysqlDataStore;
import dev.itsnotskyex.storage.PlayerDataStore;
import dev.itsnotskyex.storage.SqliteDataStore;

import dev.itsnotskyex.storage.LeaderboardEntry;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class PlayerDataManager {

    private final EndlessCoinflip plugin;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final PlayerDataStore store;
    private static final List<String> LEADERBOARD_STATS = List.of(
            "wins", "losses", "total-wagered", "total-won", "biggest-win", "biggest-loss");

    private volatile Map<UUID, Integer> rankCache = Map.of();
    private volatile Map<String, List<LeaderboardEntry>> topEntriesByStat = Map.of();

    public PlayerDataManager(EndlessCoinflip plugin) {
        this.plugin = plugin;
        String configuredType = resolveType();

        PlayerDataStore builtStore;
        String currentType;
        try {
            builtStore = buildStore(configuredType);
            builtStore.init();
            currentType = configuredType;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to initialize " + configuredType + " storage: " + e.getMessage()
                    + ". Falling back to FILE storage for now — check your storage settings in config.yml.");
            builtStore = new FileDataStore(plugin);
            builtStore.init();
            currentType = "FILE";
        }
        this.store = builtStore;

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
        PlayerDataStore oldStore;
        try {
            oldStore = buildStore(fromType);
            oldStore.init();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to connect to previous " + fromType + " storage for migration: "
                    + e.getMessage() + ". Skipping migration, will retry on next restart.");
            return false;
        }

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
                PlayerData data = oldStore.load(uuid).get(10, TimeUnit.SECONDS);
                data.markHistoryDirty();
                store.save(data).get(10, TimeUnit.SECONDS);
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

    public void preload(UUID uuid) {
        if (cache.containsKey(uuid)) return;
        try {
            cache.put(uuid, store.load(uuid).get(10, TimeUnit.SECONDS));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to preload player data for " + uuid + ": " + e.getMessage());
        }
    }

    public PlayerData get(UUID uuid) {
        PlayerData cached = cache.get(uuid);
        if (cached != null) return cached;

        plugin.getLogger().warning("Player data for " + uuid + " was requested before it finished preloading; " +
                "returning transient defaults and loading it in the background.");
        store.load(uuid).thenAccept(data -> cache.putIfAbsent(uuid, data));
        return new PlayerData(uuid);
    }

    public PlayerData peek(UUID uuid) {
        return cache.get(uuid);
    }

    public int getRank(UUID uuid) {
        return rankCache.getOrDefault(uuid, -1);
    }

    public LeaderboardEntry getTopEntry(String statSortKey, int position) {
        List<LeaderboardEntry> entries = topEntriesByStat.getOrDefault(statSortKey, List.of());
        return position >= 1 && position <= entries.size() ? entries.get(position - 1) : null;
    }

    public void refreshRanks() {
        String sortBy = plugin.getConfig().getString("leaderboard.sort-by", "total-won");
        store.getLeaderboardSize().thenCompose(size -> {
            int limit = Math.max(size, 1);
            List<CompletableFuture<Map.Entry<String, List<LeaderboardEntry>>>> futures = LEADERBOARD_STATS.stream()
                    .map(stat -> store.getLeaderboard(stat, limit, 0)
                            .thenApply(entries -> Map.entry(stat, entries)))
                    .toList();
            return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .thenApply(v -> futures.stream()
                            .map(CompletableFuture::join)
                            .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        }).thenAccept(byStat -> {
            topEntriesByStat = byStat;

            List<LeaderboardEntry> ranked = byStat.getOrDefault(sortBy, List.of());
            Map<UUID, Integer> ranks = new HashMap<>();
            for (int i = 0; i < ranked.size(); i++) {
                ranks.put(ranked.get(i).uuid, i + 1);
            }
            rankCache = ranks;
        }).exceptionally(e -> {
            plugin.getLogger().warning("Failed to refresh leaderboard ranks: " + e.getMessage());
            return null;
        });
    }

    public void save(PlayerData data) {
        store.save(data).exceptionally(e -> {
            plugin.getLogger().warning("Failed to save player data for " + data.getUuid() + ": " + e.getMessage());
            return null;
        });
        if (plugin.getServer().getPlayer(data.getUuid()) == null) {
            cache.remove(data.getUuid());
        }
    }

    public void saveAll() {
        List<CompletableFuture<Void>> saves = cache.values().stream().map(store::save).toList();
        try {
            CompletableFuture.allOf(saves.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save all player data during shutdown: " + e.getMessage());
        }
    }

    public CompletableFuture<LeaderboardPage> getLeaderboardPageAsync(String sortBy, int limit, int offset) {
        return store.getLeaderboardPage(sortBy, limit, offset);
    }

    public void close() {
        store.close();
    }
}
