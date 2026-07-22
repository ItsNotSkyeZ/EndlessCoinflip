package dev.itsnotskyex.integration;

import dev.itsnotskyex.EndlessCoinflip;
import dev.itsnotskyex.data.PlayerData;
import dev.itsnotskyex.manager.CoinflipManager;
import dev.itsnotskyex.storage.LeaderboardEntry;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.List;

public class CoinflipPlaceholders extends PlaceholderExpansion {

    private static final List<String> TOP_STATS = List.of(
            "total_wagered", "total_won", "biggest_win", "biggest_loss", "wins", "losses", "streak");
    private static final String NO_DATA = "Awaiting data";

    private final EndlessCoinflip plugin;

    public CoinflipPlaceholders(EndlessCoinflip plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "coinflip";
    }

    @Override
    public String getAuthor() {
        return "ItsNotSkye";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String lower = params.toLowerCase();

        if (lower.startsWith("top_")) {
            return topPlaceholder(lower);
        }

        if (player == null) return NO_DATA;
        PlayerData data = plugin.getPlayerDataManager().peek(player.getUniqueId());
        if (data == null) return NO_DATA;

        return switch (lower) {
            case "wins" -> String.valueOf(data.getWins());
            case "losses" -> String.valueOf(data.getLosses());
            case "total_wagered" -> CoinflipManager.fmt(data.getTotalWagered());
            case "total_won" -> CoinflipManager.fmt(data.getTotalWon());
            case "biggest_win" -> CoinflipManager.fmt(data.getBiggestWin());
            case "biggest_loss" -> CoinflipManager.fmt(data.getBiggestLoss());
            case "streak" -> String.valueOf(data.getCurrentStreak());
            case "best_streak" -> String.valueOf(data.getBestStreak());
            case "ratio" -> ratio(data.getWins(), data.getLosses());
            case "total_matches" -> String.valueOf(data.getWins() + data.getLosses());
            case "net_profit" -> CoinflipManager.fmt(data.getTotalWon() - data.getTotalWagered());
            case "rank" -> rank(player);
            default -> null;
        };
    }

    private String topPlaceholder(String params) {
        String rest = params.substring("top_".length());

        for (String stat : TOP_STATS) {
            if (!rest.startsWith(stat + "_")) continue;

            String remainder = rest.substring(stat.length() + 1);
            int split = remainder.indexOf('_');
            String positionPart = split == -1 ? remainder : remainder.substring(0, split);
            String field = split == -1 ? "name" : remainder.substring(split + 1);

            int position;
            try {
                position = Integer.parseInt(positionPart);
            } catch (NumberFormatException e) {
                return null;
            }

            LeaderboardEntry entry = plugin.getPlayerDataManager().getTopEntry(stat.replace('_', '-'), position);
            if (entry == null) return NO_DATA;

            return switch (field) {
                case "name" -> Bukkit.getOfflinePlayer(entry.uuid).getName() != null
                        ? Bukkit.getOfflinePlayer(entry.uuid).getName() : "Unknown";
                case "wins" -> String.valueOf(entry.wins);
                case "losses" -> String.valueOf(entry.losses);
                case "total_wagered" -> CoinflipManager.fmt(entry.totalWagered);
                case "total_won" -> CoinflipManager.fmt(entry.totalWon);
                case "biggest_win" -> CoinflipManager.fmt(entry.biggestWin);
                case "biggest_loss" -> CoinflipManager.fmt(entry.biggestLoss);
                case "streak" -> String.valueOf(entry.bestStreak);
                case "ratio" -> ratio(entry.wins, entry.losses);
                default -> null;
            };
        }

        return null;
    }

    private String ratio(int wins, int losses) {
        if (losses == 0) return String.valueOf(wins);
        return String.format("%.2f", wins / (double) losses);
    }

    private String rank(OfflinePlayer player) {
        int rank = plugin.getPlayerDataManager().getRank(player.getUniqueId());
        return rank > 0 ? String.valueOf(rank) : NO_DATA;
    }
}
