package dev.itsnotskyex.commands;

import dev.itsnotskyex.EndlessCoinflip;
import dev.itsnotskyex.data.PlayerData;
import dev.itsnotskyex.manager.CoinflipManager;
import dev.itsnotskyex.storage.LeaderboardEntry;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class CoinflipCommand implements CommandExecutor, TabCompleter {

    private final EndlessCoinflip plugin;

    public CoinflipCommand(EndlessCoinflip plugin) {
        this.plugin = plugin;
    }

    private void msg(CommandSender sender, String key, String... replacements) {
        sender.sendMessage(plugin.getConfigManager().getMessage(key, replacements));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length > 0) {
            String sub = args[0].toLowerCase();

            if (sub.equals("reload")) {
                if (!sender.hasPermission("endlesscoinflip.admin")) {
                    msg(sender, "no-permission");
                    return true;
                }
                if (plugin.getConfigManager().reload()) {
                    msg(sender, "reload-success");
                } else {
                    msg(sender, "reload-failed");
                }
                return true;
            }

            if (sub.equals("help")) {
                for (String line : plugin.getConfigManager().getMessagesConfig().getStringList("help")) {
                    sender.sendMessage(plugin.getConfigManager().color(line));
                }
                return true;
            }

            if (sub.equals("top")) {
                if (!plugin.getConfig().getBoolean("leaderboard.enabled", true)) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("feature-disabled"));
                    return true;
                }
                int page = 1;
                if (args.length > 1) {
                    try { page = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
                }
                if (plugin.getConfig().getBoolean("leaderboard.gui", false)) {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(plugin.getConfigManager().getMessage("player-only"));
                        return true;
                    }
                    plugin.getLeaderboardGUI().open(player, page - 1);
                } else {
                    sendLeaderboard(sender, page);
                }
                return true;
            }
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("player-only"));
            return true;
        }

        if (!player.hasPermission("endlesscoinflip.use")) {
            msg(player, "no-permission");
            return true;
        }

        if (args.length == 0) {
            plugin.getCoinflipGUI().openMain(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("cancel")) {
            if (plugin.getCoinflipManager().cancelMatch(player)) {
                msg(player, "match-cancelled");
            } else if (!plugin.getPrivateMatchManager().cancelByHost(player.getUniqueId())) {
                msg(player, "no-active-match");
            }
            return true;
        }

        if (sub.equals("accept")) {
            plugin.getPrivateMatchManager().handleAccept(player);
            return true;
        }

        if (sub.equals("deny")) {
            if (!plugin.getPrivateMatchManager().cancelByTarget(player.getUniqueId())) {
                msg(player, "no-pending-invite");
            }
            return true;
        }

        if (sub.equals("stats")) {
            PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
            for (String line : plugin.getConfigManager().getMessagesConfig().getStringList("stats")) {
                line = line
                        .replace("{wins}",    String.valueOf(data.getWins()))
                        .replace("{losses}",  String.valueOf(data.getLosses()))
                        .replace("{wagered}", CoinflipManager.fmt(data.getTotalWagered()))
                        .replace("{won}",     CoinflipManager.fmt(data.getTotalWon()))
                        .replace("{win}",     CoinflipManager.fmt(data.getBiggestWin()))
                        .replace("{loss}",    CoinflipManager.fmt(data.getBiggestLoss()));
                player.sendMessage(plugin.getConfigManager().color(line));
            }
            return true;
        }

        if (sub.equals("history")) {
            plugin.getHistoryGUI().open(player, 0);
            return true;
        }

        if (sub.equals("bot")) {
            if (!plugin.getConfigManager().isBotBattlesEnabled()) { msg(player, "feature-disabled"); return true; }
            if (args.length < 2) { msg(player, "invalid-amount"); return true; }
            double wager = plugin.getCoinflipManager().parseWager(args[1], player);
            if (Double.isNaN(wager)) { msg(player, "invalid-amount"); return true; }

            if (wager <= 0) { msg(player, "wager-must-be-positive"); return true; }
            double min = plugin.getConfigManager().getMinWager();
            double max = plugin.getConfigManager().getMaxWager(player);
            if (wager < min) { msg(player, "wager-too-low",  "min", CoinflipManager.fmt(min)); return true; }
            if (wager > max) { msg(player, "wager-too-high", "max", CoinflipManager.fmt(max)); return true; }
            if (!plugin.getEconomy().has(player, wager)) { msg(player, "insufficient-funds-need", "wager", CoinflipManager.fmt(wager)); return true; }
            if (!checkCooldown(player)) return true;

            plugin.getCoinflipGUI().openBotColorPicker(player, wager);
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target != null || args.length >= 2) {
            if (!plugin.getConfigManager().isPrivateMatchesEnabled()) { msg(player, "feature-disabled"); return true; }
            if (target == null) { msg(player, "player-not-found", "player", args[0]); return true; }
            if (args.length < 2) { msg(player, "invalid-amount"); return true; }
            handlePrivateInvite(player, target, args[1]);
            return true;
        }

        double wager = plugin.getCoinflipManager().parseWager(sub, player);
        if (Double.isNaN(wager)) { msg(player, "invalid-amount"); return true; }

        if (wager <= 0)                                       { msg(player, "wager-must-be-positive"); return true; }
        double min = plugin.getConfigManager().getMinWager();
        double max = plugin.getConfigManager().getMaxWager(player);
        if (wager < min) { msg(player, "wager-too-low",  "min", CoinflipManager.fmt(min)); return true; }
        if (wager > max) { msg(player, "wager-too-high", "max", CoinflipManager.fmt(max)); return true; }
        if (plugin.getCoinflipManager().hasActiveMatch(player.getUniqueId())) { msg(player, "already-has-match"); return true; }
        if (plugin.getCoinflipManager().isFull())             { msg(player, "lobby-full"); return true; }
        if (!checkCooldown(player)) return true;

        plugin.getCoinflipGUI().openColorPicker(player, wager);
        return true;
    }

    private void handlePrivateInvite(Player host, Player target, String wagerInput) {
        if (target.getUniqueId().equals(host.getUniqueId())) { msg(host, "cannot-invite-self"); return; }

        double wager = plugin.getCoinflipManager().parseWager(wagerInput, host);
        if (Double.isNaN(wager)) { msg(host, "invalid-amount"); return; }
        if (wager <= 0) { msg(host, "wager-must-be-positive"); return; }
        double min = plugin.getConfigManager().getMinWager();
        double max = plugin.getConfigManager().getMaxWager(host);
        if (wager < min) { msg(host, "wager-too-low",  "min", CoinflipManager.fmt(min)); return; }
        if (wager > max) { msg(host, "wager-too-high", "max", CoinflipManager.fmt(max)); return; }

        if (plugin.getCoinflipManager().hasActiveMatch(host.getUniqueId()))                     { msg(host, "already-has-match"); return; }
        if (plugin.getPrivateMatchManager().hasPendingAsHost(host.getUniqueId()))                { msg(host, "invite-already-pending"); return; }
        if (plugin.getPrivateMatchManager().hasPendingAsTarget(target.getUniqueId()))            { msg(host, "target-has-pending-invite"); return; }
        if (!checkCooldown(host)) return;
        if (!plugin.getEconomy().has(host, wager)) { msg(host, "insufficient-funds-need", "wager", CoinflipManager.fmt(wager)); return; }

        plugin.getPrivateMatchManager().createInvite(host, target, wager);
    }

    private boolean checkCooldown(Player player) {
        long remaining = plugin.getCoinflipManager().getCooldownRemainingSeconds(player.getUniqueId());
        if (remaining <= 0) return true;
        msg(player, "cooldown-active", "time", String.valueOf(remaining));
        return false;
    }

    private void sendLeaderboard(CommandSender sender, int pageArg) {
        int entriesPerPage = plugin.getConfig().getInt("leaderboard.entries-per-page", 10);
        String sortBy = plugin.getConfig().getString("leaderboard.sort-by", "total-won");
        int total = plugin.getPlayerDataManager().getLeaderboardSize();
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) entriesPerPage));
        int page = Math.max(1, Math.min(pageArg, totalPages)) - 1;

        List<LeaderboardEntry> entries = plugin.getPlayerDataManager().getLeaderboard(sortBy, entriesPerPage, page * entriesPerPage);

        for (String line : plugin.getConfig().getStringList("leaderboard.chat-header")) {
            sender.sendMessage(plugin.getConfigManager().color(line
                    .replace("{page}", String.valueOf(page + 1))
                    .replace("{pages}", String.valueOf(totalPages))));
        }

        if (entries.isEmpty()) {
            sender.sendMessage(plugin.getConfigManager().color(plugin.getConfig().getString("leaderboard.chat-empty", "&cNo leaderboard data yet.")));
        } else {
            String template = plugin.getConfig().getString("leaderboard.chat-entry", "  &7#{rank} &f{name} &8— &b{value}");
            int rank = page * entriesPerPage + 1;
            for (LeaderboardEntry entry : entries) {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(entry.uuid);
                String name = offline.getName() != null ? offline.getName() : entry.uuid.toString().substring(0, 8);
                String value = sortBy.equals("wins") || sortBy.equals("losses")
                        ? String.valueOf((long) entry.valueFor(sortBy))
                        : CoinflipManager.fmt(entry.valueFor(sortBy));
                sender.sendMessage(plugin.getConfigManager().color(template
                        .replace("{rank}", String.valueOf(rank++))
                        .replace("{name}", name)
                        .replace("{value}", value)));
            }
        }

        for (String line : plugin.getConfig().getStringList("leaderboard.chat-footer")) {
            sender.sendMessage(plugin.getConfigManager().color(line));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> wagers = List.of("all", "half", "100", "1000", "10k", "200k", "1m", "10m", "100m");
        boolean botEnabled = plugin.getConfigManager().isBotBattlesEnabled();
        boolean privateEnabled = plugin.getConfigManager().isPrivateMatchesEnabled();

        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("help", "cancel", "stats", "history", "top", "reload"));
            if (botEnabled) options.add("bot");
            if (privateEnabled) {
                options.add("accept");
                options.add("deny");
                for (Player online : Bukkit.getOnlinePlayers()) options.add(online.getName());
            }
            options.addAll(wagers);
            return options;
        }
        if (args.length == 2 && botEnabled && args[0].equalsIgnoreCase("bot")) return wagers;
        if (args.length == 2 && args[0].equalsIgnoreCase("top")) return List.of("1", "2", "3");
        if (args.length == 2 && privateEnabled && Bukkit.getPlayerExact(args[0]) != null) return wagers;
        return List.of();
    }
}