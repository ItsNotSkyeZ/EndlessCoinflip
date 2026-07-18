package dev.itsnotskyex.data;

import dev.itsnotskyex.EndlessCoinflip;
import dev.itsnotskyex.storage.FileDataStore;
import dev.itsnotskyex.storage.LeaderboardEntry;
import dev.itsnotskyex.storage.LeaderboardPage;
import dev.itsnotskyex.storage.MysqlDataStore;
import dev.itsnotskyex.storage.PlayerDataStore;
import dev.itsnotskyex.storage.SqliteDataStore;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class PlayerDataManager {

    private final EndlessCoinflip plugin;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final PlayerDataStore store;
    /**
     * SQLite runs on a single persistent connection, so every load/save/leaderboard
     * query against it is routed through this one dedicated thread. That guarantees
     * the connection is never touched concurrently, and never touched by the main
     * thread, without needing to synchronize the storage layer itself.
     */
    private final boolean requiresDedicatedIo;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "EndlessCoinflip-Save");
        thread.setDaemon(true);
        return thread;
    });

    public PlayerDataManager(EndlessCoinflip plugin) {
        this.plugin = plugin;
        String currentType = resolveType();
        this.store = buildStore(currentType);
        this.requiresDedicatedIo = store instanceof SqliteDataStore;
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

    /**
     * Loads a player's data off the calling thread and populates the cache, so that a
     * later {@link #get(UUID)} call is a pure cache hit. Intended to be called from
     * AsyncPlayerPreLoginEvent (already off the main thread) before the player joins.
     */
    public void preload(UUID uuid) {
        if (cache.containsKey(uuid)) return;
        if (requiresDedicatedIo) {
            try {
                cache.put(uuid, ioExecutor.submit(() -> store.load(uuid)).get());
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to preload player data for " + uuid + ": " + e.getMessage());
            }
        } else {
            cache.put(uuid, store.load(uuid));
        }
    }

    public PlayerData get(UUID uuid) {
        PlayerData cached = cache.get(uuid);
        if (cached != null) return cached;

        if (requiresDedicatedIo) {
            plugin.getLogger().warning("Player data for " + uuid + " was requested before it finished preloading; " +
                    "returning transient defaults and loading it in the background.");
            ioExecutor.execute(() -> cache.putIfAbsent(uuid, store.load(uuid)));
            return new PlayerData(uuid);
        }

        return cache.computeIfAbsent(uuid, store::load);
    }

    public void save(PlayerData data) {
        ioExecutor.execute(() -> store.save(data));
        if (plugin.getServer().getPlayer(data.getUuid()) == null) {
            cache.remove(data.getUuid());
        }
    }

    public void saveAll() {
        List<Future<?>> saves = cache.values().stream()
                .map(data -> ioExecutor.submit(() -> store.save(data)))
                .toList();
        for (Future<?> future : saves) {
            try {
                future.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to save a player's data during shutdown: " + e.getMessage());
            }
        }
    }

    public List<LeaderboardEntry> getLeaderboard(String sortBy, int limit, int offset) {
        return store.getLeaderboard(sortBy, limit, offset);
    }

    public int getLeaderboardSize() {
        return store.getLeaderboardSize();
    }

    public LeaderboardPage getLeaderboardPage(String sortBy, int limit, int offset) {
        return store.getLeaderboardPage(sortBy, limit, offset);
    }

    /**
     * Fetches a leaderboard page without ever touching the main thread's storage
     * connection. For non-dedicated-IO stores (File, MySQL) this resolves immediately
     * on the calling thread, identical to {@link #getLeaderboardPage}.
     */
    public CompletableFuture<LeaderboardPage> getLeaderboardPageAsync(String sortBy, int limit, int offset) {
        if (!requiresDedicatedIo) {
            return CompletableFuture.completedFuture(store.getLeaderboardPage(sortBy, limit, offset));
        }
        CompletableFuture<LeaderboardPage> future = new CompletableFuture<>();
        ioExecutor.execute(() -> {
            try {
                future.complete(store.getLeaderboardPage(sortBy, limit, offset));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public void close() {
        ioExecutor.shutdown();
        try {
            ioExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        store.close();
    }
}
