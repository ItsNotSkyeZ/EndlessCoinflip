package dev.itsnotskyex.storage;

import dev.itsnotskyex.EndlessCoinflip;
import dev.itsnotskyex.data.MatchHistoryEntry;
import dev.itsnotskyex.data.PlayerData;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FileDataStore implements PlayerDataStore {

    private final EndlessCoinflip plugin;
    private final File dataFolder;

    public FileDataStore(EndlessCoinflip plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
    }

    @Override
    public void init() {
        dataFolder.mkdirs();
    }

    @Override
    public void close() {
    }

    @Override
    public PlayerData load(UUID uuid) {
        File file = new File(dataFolder, uuid + ".yml");
        PlayerData data = new PlayerData(uuid);
        if (!file.exists()) return data;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        data.setWins(yml.getInt("wins", 0));
        data.setLosses(yml.getInt("losses", 0));
        data.setTotalWagered(yml.getDouble("total-wagered", 0));
        data.setTotalWon(yml.getDouble("total-won", 0));
        data.setBiggestWin(yml.getDouble("biggest-win", 0));
        data.setBiggestLoss(yml.getDouble("biggest-loss", 0));
        data.setPendingPayout(yml.getDouble("pending-payout", 0));
        data.setReceivingPrivateInvites(yml.getBoolean("receiving-private-invites", true));

        List<MatchHistoryEntry> history = new ArrayList<>();
        for (Map<?, ?> m : yml.getMapList("history")) {
            Object opponent = m.get("opponent");
            Object wager = m.get("wager");
            Object won = m.get("won");
            Object bot = m.get("bot");
            Object time = m.get("time");
            history.add(new MatchHistoryEntry(
                    opponent != null ? opponent.toString() : "Unknown",
                    wager instanceof Number ? ((Number) wager).doubleValue() : 0,
                    Boolean.TRUE.equals(won),
                    Boolean.TRUE.equals(bot),
                    time instanceof Number ? ((Number) time).longValue() : 0L
            ));
        }
        data.loadHistory(history);
        return data;
    }

    @Override
    public void save(PlayerData data) {
        File file = new File(dataFolder, data.getUuid() + ".yml");
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("wins", data.getWins());
        yml.set("losses", data.getLosses());
        yml.set("total-wagered", data.getTotalWagered());
        yml.set("total-won", data.getTotalWon());
        yml.set("biggest-win", data.getBiggestWin());
        yml.set("biggest-loss", data.getBiggestLoss());
        yml.set("pending-payout", data.getPendingPayout());
        yml.set("receiving-private-invites", data.isReceivingPrivateInvites());

        List<Map<String, Object>> historyList = new ArrayList<>();
        for (MatchHistoryEntry e : data.getHistory()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("opponent", e.opponentName);
            m.put("wager", e.wager);
            m.put("won", e.won);
            m.put("bot", e.vsBot);
            m.put("time", e.timestamp);
            historyList.add(m);
        }
        yml.set("history", historyList);

        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save player data: " + data.getUuid());
        }
    }

    @Override
    public List<LeaderboardEntry> getLeaderboard(String sortBy, int limit, int offset) {
        List<LeaderboardEntry> all = readAllEntries();
        all.sort(Comparator.comparingDouble((LeaderboardEntry e) -> e.valueFor(sortBy)).reversed());
        int from = Math.min(offset, all.size());
        int to = Math.min(offset + limit, all.size());
        return new ArrayList<>(all.subList(from, to));
    }

    @Override
    public int getLeaderboardSize() {
        return readAllEntries().size();
    }

    private List<LeaderboardEntry> readAllEntries() {
        List<LeaderboardEntry> entries = new ArrayList<>();
        File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return entries;
        for (File file : files) {
            String name = file.getName();
            UUID uuid;
            try {
                uuid = UUID.fromString(name.substring(0, name.length() - 4));
            } catch (IllegalArgumentException e) {
                continue;
            }
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            entries.add(new LeaderboardEntry(
                    uuid,
                    yml.getInt("wins", 0),
                    yml.getInt("losses", 0),
                    yml.getDouble("total-wagered", 0),
                    yml.getDouble("total-won", 0),
                    yml.getDouble("biggest-win", 0),
                    yml.getDouble("biggest-loss", 0)
            ));
        }
        return entries;
    }

    @Override
    public List<UUID> getAllUuids() {
        List<UUID> uuids = new ArrayList<>();
        File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return uuids;
        for (File file : files) {
            String name = file.getName();
            try {
                uuids.add(UUID.fromString(name.substring(0, name.length() - 4)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return uuids;
    }

    @Override
    public String backup() {
        if (!dataFolder.isDirectory()) return null;
        File backup = new File(plugin.getDataFolder(), "playerdata_backup");
        int suffix = 1;
        while (backup.exists()) {
            backup = new File(plugin.getDataFolder(), "playerdata_backup_" + suffix);
            suffix++;
        }
        return dataFolder.renameTo(backup) ? backup.getName() + "/" : null;
    }
}
