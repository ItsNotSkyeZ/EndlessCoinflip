package dev.itsnotskyex.storage;

import dev.itsnotskyex.data.PlayerData;

import java.util.List;
import java.util.UUID;

public interface PlayerDataStore {
    void init();
    void close();
    PlayerData load(UUID uuid);
    void save(PlayerData data);
    List<LeaderboardEntry> getLeaderboard(String sortBy, int limit, int offset);
    int getLeaderboardSize();

    default LeaderboardPage getLeaderboardPage(String sortBy, int limit, int offset) {
        return new LeaderboardPage(getLeaderboard(sortBy, limit, offset), getLeaderboardSize());
    }
    List<UUID> getAllUuids();
    String backup();
}
