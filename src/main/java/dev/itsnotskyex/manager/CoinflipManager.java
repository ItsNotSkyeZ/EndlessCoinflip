package dev.itsnotskyex.manager;

import dev.itsnotskyex.EndlessCoinflip;
import dev.itsnotskyex.data.MatchHistoryEntry;
import dev.itsnotskyex.data.PlayerData;
import org.bukkit.entity.Player;

import java.text.NumberFormat;
import java.util.*;

public class CoinflipManager {

    private final EndlessCoinflip plugin;
    private final List<CoinflipMatch> activeMatches = new ArrayList<>();
    private final Set<UUID> pendingBotBattles = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<UUID, Double> activeBotMatches = new HashMap<>();
    private final Map<UUID, Long> lastMatchStart = new HashMap<>();

    public CoinflipManager(EndlessCoinflip plugin) {
        this.plugin = plugin;
    }

    public static class CoinflipMatch {
        public final UUID hostUuid;
        public final String hostName;
        public final double wager;
        public final WoolColor hostColor;
        public final long createdAt;

        public CoinflipMatch(UUID hostUuid, String hostName, double wager, WoolColor hostColor) {
            this.hostUuid  = hostUuid;
            this.hostName  = hostName;
            this.wager     = wager;
            this.hostColor = hostColor;
            this.createdAt = System.currentTimeMillis();
        }
    }

    public List<CoinflipMatch> getActiveMatches()     { return Collections.unmodifiableList(activeMatches); }

    public boolean isFull() {
        int count = activeMatches.size() + plugin.getPrivateMatchManager().pendingCount();
        return count >= plugin.getConfigManager().getMaxActiveMatches();
    }

    public boolean hasActiveMatch(UUID uuid)          { return activeMatches.stream().anyMatch(m -> m.hostUuid.equals(uuid)); }

    public CoinflipMatch getMatchByHost(UUID uuid) {
        return activeMatches.stream().filter(m -> m.hostUuid.equals(uuid)).findFirst().orElse(null);
    }

    public boolean createMatch(Player host, double wager, WoolColor color) {
        if (!plugin.getEconomy().has(host, wager)) return false;
        plugin.getEconomy().withdrawPlayer(host, wager);
        activeMatches.add(new CoinflipMatch(host.getUniqueId(), host.getName(), wager, color));
        startCooldown(host.getUniqueId());
        return true;
    }

    public void checkExpiredMatches() {
        if (!plugin.getConfig().getBoolean("match-expiry.enabled", true)) return;
        long expiryMillis = plugin.getConfig().getInt("match-expiry.minutes", 10) * 60_000L;
        long now = System.currentTimeMillis();

        Iterator<CoinflipMatch> it = activeMatches.iterator();
        while (it.hasNext()) {
            CoinflipMatch match = it.next();
            if (now - match.createdAt < expiryMillis) continue;
            it.remove();
            plugin.getEconomy().depositPlayer(plugin.getServer().getOfflinePlayer(match.hostUuid), match.wager);
            Player host = plugin.getServer().getPlayer(match.hostUuid);
            if (host != null) {
                host.sendMessage(plugin.getConfigManager().getMessage("match-expired", "wager", fmt(match.wager)));
            }
        }
    }

    public boolean cancelMatch(Player host) {
        CoinflipMatch match = getMatchByHost(host.getUniqueId());
        if (match == null) return false;
        activeMatches.remove(match);
        plugin.getEconomy().depositPlayer(host, match.wager);
        return true;
    }

    public void refundAllActive() {
        for (CoinflipMatch match : activeMatches) {
            plugin.getEconomy().depositPlayer(plugin.getServer().getOfflinePlayer(match.hostUuid), match.wager);
        }
        activeMatches.clear();
        for (Map.Entry<UUID, Double> entry : activeBotMatches.entrySet()) {
            plugin.getEconomy().depositPlayer(plugin.getServer().getOfflinePlayer(entry.getKey()), entry.getValue());
        }
        activeBotMatches.clear();
    }

    public CoinflipMatch removeMatch(UUID hostUuid) {
        Iterator<CoinflipMatch> it = activeMatches.iterator();
        while (it.hasNext()) {
            CoinflipMatch m = it.next();
            if (m.hostUuid.equals(hostUuid)) { it.remove(); return m; }
        }
        return null;
    }

    public static class ResolveResult {
        public final boolean success;
        public final double payout; // winner's total payout after server tax, 0 if there is no winner payout to report
        public final double taxAmount;

        public ResolveResult(boolean success, double payout, double taxAmount) {
            this.success   = success;
            this.payout    = payout;
            this.taxAmount = taxAmount;
        }
    }

    private double[] applyTax(double total) {
        double taxPercent = plugin.getConfigManager().getServerTaxPercent();
        double taxAmount = total * (taxPercent / 100.0);
        return new double[]{total - taxAmount, taxAmount};
    }

    public ResolveResult resolveMatch(Player joiner, CoinflipMatch match, boolean joinerWon) {
        if (!plugin.getEconomy().has(joiner, match.wager)) {
            plugin.getEconomy().depositPlayer(plugin.getServer().getOfflinePlayer(match.hostUuid), match.wager);
            return new ResolveResult(false, 0, 0);
        }
        plugin.getEconomy().withdrawPlayer(joiner, match.wager);

        double[] taxed = applyTax(match.wager * 2);
        double payout = taxed[0];
        double taxAmount = taxed[1];
        int maxHistory = plugin.getConfigManager().getMaxHistoryStored();
        long now = System.currentTimeMillis();
        PlayerData joinerData = plugin.getPlayerDataManager().get(joiner.getUniqueId());
        joinerData.setTotalWagered(joinerData.getTotalWagered() + match.wager);

        Player onlineHost = plugin.getServer().getPlayer(match.hostUuid);

        if (joinerWon) {
            plugin.getEconomy().depositPlayer(joiner, payout);
            joinerData.setWins(joinerData.getWins() + 1);
            joinerData.setTotalWon(joinerData.getTotalWon() + payout);
            if (payout > joinerData.getBiggestWin()) joinerData.setBiggestWin(payout);
            joinerData.addHistoryEntry(new MatchHistoryEntry(match.hostName, match.wager, true, false, now), maxHistory);
            plugin.getPlayerDataManager().save(joinerData);

            PlayerData hd = plugin.getPlayerDataManager().get(match.hostUuid);
            hd.setLosses(hd.getLosses() + 1);
            hd.setTotalWagered(hd.getTotalWagered() + match.wager);
            if (match.wager > hd.getBiggestLoss()) hd.setBiggestLoss(match.wager);
            hd.addHistoryEntry(new MatchHistoryEntry(joiner.getName(), match.wager, false, false, now), maxHistory);
            plugin.getPlayerDataManager().save(hd);
        } else {
            joinerData.setLosses(joinerData.getLosses() + 1);
            if (match.wager > joinerData.getBiggestLoss()) joinerData.setBiggestLoss(match.wager);
            joinerData.addHistoryEntry(new MatchHistoryEntry(match.hostName, match.wager, false, false, now), maxHistory);
            plugin.getPlayerDataManager().save(joinerData);

            PlayerData hd = plugin.getPlayerDataManager().get(match.hostUuid);
            hd.setWins(hd.getWins() + 1);
            hd.setTotalWagered(hd.getTotalWagered() + match.wager);
            hd.setTotalWon(hd.getTotalWon() + payout);
            if (payout > hd.getBiggestWin()) hd.setBiggestWin(payout);
            if (onlineHost != null) {
                plugin.getEconomy().depositPlayer(onlineHost, payout);
            } else {
                hd.setPendingPayout(hd.getPendingPayout() + payout);
            }
            hd.addHistoryEntry(new MatchHistoryEntry(joiner.getName(), match.wager, true, false, now), maxHistory);
            plugin.getPlayerDataManager().save(hd);
        }
        return new ResolveResult(true, payout, taxAmount);
    }

    public ResolveResult resolveBotMatch(Player player, double wager, boolean playerWon) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        data.setTotalWagered(data.getTotalWagered() + wager);
        ResolveResult result;
        if (playerWon) {
            double[] taxed = applyTax(wager * 2);
            double payout = taxed[0];
            plugin.getEconomy().depositPlayer(player, payout);
            data.setWins(data.getWins() + 1);
            data.setTotalWon(data.getTotalWon() + payout);
            if (payout > data.getBiggestWin()) data.setBiggestWin(payout);
            result = new ResolveResult(true, payout, taxed[1]);
        } else {
            data.setLosses(data.getLosses() + 1);
            if (wager > data.getBiggestLoss()) data.setBiggestLoss(wager);
            result = new ResolveResult(true, 0, 0);
        }
        data.addHistoryEntry(new MatchHistoryEntry("Coinflip Bot", wager, playerWon, true, System.currentTimeMillis()), plugin.getConfigManager().getMaxHistoryStored());
        plugin.getPlayerDataManager().save(data);
        return result;
    }

    public void setPendingBotBattle(UUID uuid)    { pendingBotBattles.add(uuid); }
    public boolean isPendingBotBattle(UUID uuid)  { return pendingBotBattles.contains(uuid); }
    public void clearPendingBotBattle(UUID uuid)  { pendingBotBattles.remove(uuid); }

    public void registerBotMatch(UUID uuid, double wager)  { activeBotMatches.put(uuid, wager); }
    public void unregisterBotMatch(UUID uuid)              { activeBotMatches.remove(uuid); }
    public boolean hasBotMatch(UUID uuid)                  { return activeBotMatches.containsKey(uuid); }
    public double getBotMatchWager(UUID uuid)              { return activeBotMatches.getOrDefault(uuid, 0.0); }

    public void startCooldown(UUID uuid) {
        lastMatchStart.put(uuid, System.currentTimeMillis());
    }

    public boolean isOnCooldown(UUID uuid) {
        return getCooldownRemainingSeconds(uuid) > 0;
    }

    public long getCooldownRemainingSeconds(UUID uuid) {
        if (!plugin.getConfig().getBoolean("cooldowns.enabled", true)) return 0;
        Long last = lastMatchStart.get(uuid);
        if (last == null) return 0;
        long cooldownMillis = plugin.getConfig().getInt("cooldowns.seconds", 5) * 1000L;
        long remaining = cooldownMillis - (System.currentTimeMillis() - last);
        return remaining <= 0 ? 0 : (remaining + 999) / 1000;
    }

    public double parseWager(String input, Player player) {
        if (input == null || input.isEmpty()) return Double.NaN;
        String s = input.trim();

        if (s.equalsIgnoreCase("all"))  return plugin.getEconomy().getBalance(player);
        if (s.equalsIgnoreCase("half")) return plugin.getEconomy().getBalance(player) / 2.0;

        s = s.replace(",", "");
        if (s.isEmpty()) return Double.NaN;

        double multiplier = 1;
        char last = Character.toLowerCase(s.charAt(s.length() - 1));
        if (last == 'k' || last == 'm' || last == 'b') {
            multiplier = last == 'k' ? 1_000 : last == 'm' ? 1_000_000 : 1_000_000_000;
            s = s.substring(0, s.length() - 1);
        }

        try {
            return Double.parseDouble(s) * multiplier;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    public static String fmt(double amount) {
        NumberFormat nf = NumberFormat.getInstance(java.util.Locale.US);
        nf.setMaximumFractionDigits(2);
        nf.setMinimumFractionDigits(0);
        return "$" + nf.format(amount);
    }
}
