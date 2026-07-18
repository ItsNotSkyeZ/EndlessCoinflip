package dev.itsnotskyex.storage;

import dev.itsnotskyex.data.PlayerData;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PlayerDataStore {
    void init();
    void close();

    CompletableFuture<PlayerData> load(UUID uuid);
    CompletableFuture<Void> save(PlayerData data);
    CompletableFuture<List<LeaderboardEntry>> getLeaderboard(String sortBy, int limit, int offset);
    CompletableFuture<Integer> getLeaderboardSize();

    default CompletableFuture<LeaderboardPage> getLeaderboardPage(String sortBy, int limit, int offset) {
        return getLeaderboard(sortBy, limit, offset)
                .thenCombine(getLeaderboardSize(), LeaderboardPage::new);
    }

    // Startup-only, single-threaded by nature (plugin enable/migration) — kept synchronous.
    List<UUID> getAllUuids();
    String backup();
}
