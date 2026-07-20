package dev.itsnotskyex.gui;

import dev.itsnotskyex.EndlessCoinflip;
import dev.itsnotskyex.config.ConfigManager;
import dev.itsnotskyex.manager.CoinflipManager;
import dev.itsnotskyex.manager.CoinflipManager.CoinflipMatch;
import dev.itsnotskyex.manager.PrivateMatchManager;
import dev.itsnotskyex.manager.WoolColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class CoinflipGUI implements Listener {

    private final EndlessCoinflip plugin;

    private final Map<Inventory, GuiType>      guiTypes      = new HashMap<>();
    private final Map<Inventory, Player>       owners        = new HashMap<>();
    private final Map<Inventory, Double>       pendingWagers = new HashMap<>();
    private final Map<Inventory, MatchUIData>  matchUIs      = new HashMap<>();
    private final Map<UUID, CoinflipMatch>     pendingJoin   = new HashMap<>();
    private final Map<UUID, Double>            pendingBot    = new HashMap<>();
    private final Map<Inventory, WoolColor>    excludedColors = new HashMap<>();
    private final Set<UUID>                    transitioning = new HashSet<>();
    private final Map<Inventory, PrivateMatchManager.PrivateInvite> pendingPrivateHost   = new HashMap<>();
    private final Map<Inventory, PrivateMatchManager.PrivateInvite> pendingPrivateTarget = new HashMap<>();

    private enum GuiType { MAIN, COLOR_PICKER, MATCH, BOT_MATCH, JOIN_CONFIRM }

    private static class MatchUIData {
        final CoinflipMatch match;
        final WoolColor joinerColor;
        final boolean isHost;
        final boolean joinerWins;
        final UUID joinerUuid;
        boolean animating = true;

        MatchUIData(CoinflipMatch match, WoolColor joinerColor, boolean isHost, boolean joinerWins, UUID joinerUuid) {
            this.match = match; this.joinerColor = joinerColor;
            this.isHost = isHost; this.joinerWins = joinerWins;
            this.joinerUuid = joinerUuid;
        }
    }

    public CoinflipGUI(EndlessCoinflip plugin) { this.plugin = plugin; }

    private String cfg(String key, String... r) { return plugin.getConfigManager().getMessage(key, r); }
    private static String c(String s)           { return ConfigManager.color(s); }

    private void announceBigWin(String winnerName, String loserName, double payout) {
        ConfigManager cm = plugin.getConfigManager();
        if (!cm.isBigWinBroadcastEnabled() || payout < cm.getBigWinBroadcastThreshold()) return;
        String message = cm.getBigWinBroadcastMessage("winner", winnerName, "loser", loserName, "amount", CoinflipManager.fmt(payout));
        if (message != null) plugin.getServer().broadcastMessage(message);
    }

    private void announceWinStreak(String winnerName, int streak) {
        ConfigManager cm = plugin.getConfigManager();
        int interval = cm.getWinStreakBroadcastInterval();
        if (!cm.isWinStreakBroadcastEnabled() || interval <= 0 || streak <= 0 || streak % interval != 0) return;
        String message = cm.getWinStreakBroadcastMessage("winner", winnerName, "streak", String.valueOf(streak));
        if (message != null) plugin.getServer().broadcastMessage(message);
    }

    private int matchSlotCount() {
        return plugin.getConfigManager().isBotBattlesEnabled() ? 6 : 7;
    }

    public void openMain(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, c(cfg("main-title")));
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);

        int matchSlots = matchSlotCount();
        List<CoinflipMatch> matches = plugin.getCoinflipManager().getActiveMatches();
        for (int i = 0; i < Math.min(matches.size(), matchSlots); i++) inv.setItem(i, makeMatchSlot(matches.get(i), player));

        if (plugin.getConfigManager().isBotBattlesEnabled()) {
            inv.setItem(6, makeItem(Material.SKELETON_SKULL, cfg("bot-battle-name"), cfg("bot-battle-lore")));
        }
        inv.setItem(7, makeHelpItem());
        inv.setItem(8, makeItem(Material.CHEST, cfg("refresh-name"), cfg("refresh-lore")));

        track(inv, GuiType.MAIN, player);
        player.openInventory(inv);
    }

    public void openColorPicker(Player player, double wager) {
        openColorPicker(player, wager, null);
    }

    public void openColorPicker(Player player, double wager, WoolColor exclude) {
        Inventory inv = Bukkit.createInventory(null, 9, c(cfg("color-picker-title")));
        WoolColor[] colors = WoolColor.values();
        for (int i = 0; i < colors.length; i++) inv.setItem(i, makeColorItem(colors[i], wager, colors[i] == exclude));
        track(inv, GuiType.COLOR_PICKER, player);
        pendingWagers.put(inv, wager);
        if (exclude != null) excludedColors.put(inv, exclude);
        player.openInventory(inv);
    }

    public void openBotColorPicker(Player player, double wager) {
        pendingBot.put(player.getUniqueId(), wager);
        openColorPicker(player, wager);
    }

    public void openPrivateColorPicker(Player host, PrivateMatchManager.PrivateInvite invite) {
        Inventory inv = Bukkit.createInventory(null, 9, c(cfg("color-picker-title")));
        WoolColor[] colors = WoolColor.values();
        for (int i = 0; i < colors.length; i++) inv.setItem(i, makeColorItem(colors[i], invite.wager, false));
        track(inv, GuiType.COLOR_PICKER, host);
        pendingWagers.put(inv, invite.wager);
        pendingPrivateHost.put(inv, invite);
        host.openInventory(inv);
    }

    public void openPrivateJoinColorPicker(Player target, PrivateMatchManager.PrivateInvite invite) {
        Inventory inv = Bukkit.createInventory(null, 9, c(cfg("color-picker-title")));
        WoolColor[] colors = WoolColor.values();
        for (int i = 0; i < colors.length; i++) inv.setItem(i, makeColorItem(colors[i], invite.wager, colors[i] == invite.hostColor));
        track(inv, GuiType.COLOR_PICKER, target);
        pendingWagers.put(inv, invite.wager);
        excludedColors.put(inv, invite.hostColor);
        pendingPrivateTarget.put(inv, invite);
        target.openInventory(inv);
    }

    public void closePrivateColorPicker(Player host) {
        Inventory inv = findInventoryFor(host.getUniqueId());
        if (inv == null || pendingPrivateHost.remove(inv) == null) return;
        transitioning.add(host.getUniqueId());
        host.closeInventory();
        transitioning.remove(host.getUniqueId());
    }

    public void closePrivateTargetPicker(Player target) {
        Inventory inv = findInventoryFor(target.getUniqueId());
        if (inv == null || pendingPrivateTarget.remove(inv) == null) return;
        transitioning.add(target.getUniqueId());
        target.closeInventory();
        transitioning.remove(target.getUniqueId());
    }

    public void openJoinConfirm(Player player, CoinflipMatch match) {
        Inventory inv = Bukkit.createInventory(null, 9, c(plugin.getConfig().getString("join-confirm.title", "&0Confirm Join")));
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);

        int infoSlot = plugin.getConfig().getInt("join-confirm.info-slot", 4);
        int confirmSlot = plugin.getConfig().getInt("join-confirm.confirm-slot", 3);
        int cancelSlot = plugin.getConfig().getInt("join-confirm.cancel-slot", 5);

        inv.setItem(infoSlot, makeConfirmInfoItem(match));
        inv.setItem(confirmSlot, makeConfirmItem("confirm"));
        inv.setItem(cancelSlot, makeConfirmItem("cancel"));

        track(inv, GuiType.JOIN_CONFIRM, player);
        pendingJoin.put(player.getUniqueId(), match);
        player.openInventory(inv);
    }

    public void openMatchUI(Player viewer, CoinflipMatch match, Player joiner, WoolColor joinerColor, boolean isHost, boolean joinerWins) {
        Inventory inv = Bukkit.createInventory(null, 9, c(cfg("match-title", "wager", CoinflipManager.fmt(match.wager))));
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);

        inv.setItem(0, makeSkull(match.hostName, match.hostUuid, match.hostColor.chatColor, ratio(match.hostUuid)));
        inv.setItem(1, silentPane(match.hostColor.paneMaterial));
        inv.setItem(7, silentPane(joinerColor.paneMaterial));
        inv.setItem(8, makeSkull(joiner.getName(), joiner.getUniqueId(), joinerColor.chatColor, ratio(joiner.getUniqueId())));
        inv.setItem(4, makeCountdown(5));

        MatchUIData data = new MatchUIData(match, joinerColor, isHost, joinerWins, joiner.getUniqueId());
        track(inv, GuiType.MATCH, viewer);
        matchUIs.put(inv, data);
        viewer.openInventory(inv);
        startMatchAnim(viewer, inv, match, joiner, joinerColor, isHost, joinerWins);
    }

    public void openBotMatchUI(Player player, double wager, WoolColor playerColor) {
        if (!plugin.getEconomy().has(player, wager)) {
            player.sendMessage(plugin.getConfigManager().getMessage("insufficient-funds-create"));
            return;
        }
        plugin.getEconomy().withdrawPlayer(player, wager);
        plugin.getCoinflipManager().registerBotMatch(player.getUniqueId(), wager);
        plugin.getCoinflipManager().startCooldown(player.getUniqueId());
        WoolColor botColor = randomExcluding(playerColor);
        CoinflipMatch fake = new CoinflipMatch(UUID.randomUUID(), "Coinflip Bot", wager, botColor);

        Inventory inv = Bukkit.createInventory(null, 9, c(cfg("match-title", "wager", CoinflipManager.fmt(wager))));
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);

        inv.setItem(0, makeItem(Material.SKELETON_SKULL, botColor.chatColor + cfg("bot-skull-name")));
        inv.setItem(1, silentPane(botColor.paneMaterial));
        inv.setItem(7, silentPane(playerColor.paneMaterial));
        inv.setItem(8, makeSkull(player.getName(), player.getUniqueId(), playerColor.chatColor, ratio(player.getUniqueId())));
        inv.setItem(4, makeCountdown(5));

        track(inv, GuiType.BOT_MATCH, player);
        matchUIs.put(inv, new MatchUIData(fake, playerColor, false, false, player.getUniqueId()));
        player.openInventory(inv);
        startBotAnim(player, inv, wager, playerColor, botColor);
    }

    private void startMatchAnim(Player viewer, Inventory inv, CoinflipMatch match, Player joiner,
                                WoolColor joinerColor, boolean isHost, boolean joinerWins) {
        countdown(viewer, inv);
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                colorFlip(viewer, inv, match.hostColor, match.hostName, joinerColor, joiner.getName(), 0, 30, () -> {
                    WoolColor winner = joinerWins ? joinerColor : match.hostColor;
                    String winnerName = joinerWins ? joiner.getName() : match.hostName;
                    inv.setItem(4, namedPane(winner.paneMaterial, c(cfg("winner-pane", "color", winner.chatColor, "name", winnerName))));
                    boolean thisPlayerWins = isHost != joinerWins;
                    boolean resolved = true;
                    if (!isHost) {
                        CoinflipManager.ResolveResult result = plugin.getCoinflipManager().resolveMatch(viewer, match, joinerWins);
                        resolved = result.success;
                        Player host = plugin.getServer().getPlayer(match.hostUuid);
                        if (!resolved) {
                            viewer.sendMessage(cfg("insufficient-funds-join"));
                            if (host != null) host.sendMessage(cfg("match-no-longer-exists"));
                        } else {
                            if (host != null) host.sendMessage(cfg(joinerWins ? "you-lost" : "you-won", "opponent", viewer.getName(), "wager", CoinflipManager.fmt(match.wager)));
                            viewer.sendMessage(cfg(joinerWins ? "you-won" : "you-lost", "opponent", match.hostName, "wager", CoinflipManager.fmt(match.wager)));

                            String loserName = joinerWins ? match.hostName : viewer.getName();
                            announceBigWin(winnerName, loserName, result.payout);
                            announceWinStreak(winnerName, result.winnerStreak);
                        }
                    }
                    if (resolved) plugin.getSoundManager().play(viewer, thisPlayerWins ? "win" : "lose");
                    MatchUIData md = matchUIs.get(inv); if (md != null) md.animating = false;
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> { if (guiTypes.containsKey(inv)) viewer.closeInventory(); }, 60L);
                }), 100L);
    }

    private void startBotAnim(Player player, Inventory inv, double wager, WoolColor playerColor, WoolColor botColor) {
        countdown(player, inv);
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                colorFlip(player, inv, botColor, "Coinflip Bot", playerColor, player.getName(), 0, 30, () -> {
                    boolean wins = new Random().nextBoolean();
                    WoolColor winner = wins ? playerColor : botColor;
                    String winnerName = wins ? player.getName() : "Coinflip Bot";
                    inv.setItem(4, namedPane(winner.paneMaterial, c(cfg("winner-pane", "color", winner.chatColor, "name", winnerName))));
                    plugin.getCoinflipManager().unregisterBotMatch(player.getUniqueId());
                    CoinflipManager.ResolveResult result = plugin.getCoinflipManager().resolveBotMatch(player, wager, wins);
                    player.sendMessage(cfg(wins ? "you-won" : "you-lost", "opponent", "Coinflip Bot", "wager", CoinflipManager.fmt(wager)));
                    if (wins) {
                        announceBigWin(player.getName(), "Coinflip Bot", result.payout);
                        announceWinStreak(player.getName(), result.winnerStreak);
                    }
                    plugin.getSoundManager().play(player, wins ? "win" : "lose");
                    MatchUIData md = matchUIs.get(inv); if (md != null) md.animating = false;
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> { if (guiTypes.containsKey(inv)) player.closeInventory(); }, 60L);
                }), 100L);
    }

    private void countdown(Player player, Inventory inv) {
        for (int i = 5; i >= 1; i--) {
            final int c = i;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!guiTypes.containsKey(inv)) return;
                inv.setItem(4, makeCountdown(c));
                plugin.getSoundManager().play(player, "countdown", 5 - c);
            }, (5 - i) * 20L + 1L);
        }
    }

    private void colorFlip(Player player, Inventory inv, WoolColor a, String nameA,
                           WoolColor b, String nameB, int frame, int total, Runnable done) {
        if (frame >= total) { plugin.getServer().getScheduler().runTaskLater(plugin, done, 1L); return; }
        long delay = frame < 10 ? 2L : frame < 16 ? 4L : 7L;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!guiTypes.containsKey(inv)) return;
            boolean showA = (frame % 2 == 0);
            WoolColor cur = showA ? a : b; String curName = showA ? nameA : nameB;
            inv.setItem(4, namedPane(cur.paneMaterial, cur.chatColor + curName));
            plugin.getSoundManager().play(player, "roll", frame);
            colorFlip(player, inv, a, nameA, b, nameB, frame + 1, total, done);
        }, delay);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        GuiType type = guiTypes.get(event.getInventory());
        if (type == null) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        switch (type) {
            case MAIN         -> handleMain(player, event.getInventory(), slot);
            case COLOR_PICKER -> handleColorPicker(player, event.getInventory(), slot);
            case JOIN_CONFIRM -> handleJoinConfirm(player, event.getInventory(), slot);
            default           -> {}
        }
    }

    private boolean checkCooldown(Player player) {
        long remaining = plugin.getCoinflipManager().getCooldownRemainingSeconds(player.getUniqueId());
        if (remaining <= 0) return true;
        player.sendMessage(cfg("cooldown-active", "time", String.valueOf(remaining)));
        return false;
    }

    private void handleMain(Player player, Inventory inv, int slot) {
        int matchSlots = matchSlotCount();

        if (slot == 8) {
            List<CoinflipMatch> matches = plugin.getCoinflipManager().getActiveMatches();
            ItemStack black = namedPane(Material.BLACK_STAINED_GLASS_PANE, " ");
            for (int i = 0; i < matchSlots; i++) inv.setItem(i, black);
            for (int i = 0; i < Math.min(matches.size(), matchSlots); i++) inv.setItem(i, makeMatchSlot(matches.get(i), player));
            return;
        }
        if (slot == 6 && plugin.getConfigManager().isBotBattlesEnabled()) {
            if (!checkCooldown(player)) return;
            player.closeInventory();
            player.sendMessage(cfg("bot-battle-prompt"));
            plugin.getCoinflipManager().setPendingBotBattle(player.getUniqueId());
            return;
        }
        if (slot >= 0 && slot < matchSlots) {
            List<CoinflipMatch> matches = plugin.getCoinflipManager().getActiveMatches();
            if (slot >= matches.size()) return;
            CoinflipMatch match = matches.get(slot);
            if (match.hostUuid.equals(player.getUniqueId())) return;
            if (!plugin.getEconomy().has(player, match.wager)) {
                player.sendMessage(cfg("insufficient-funds-join"));
                return;
            }
            if (!checkCooldown(player)) return;
            player.closeInventory();
            if (plugin.getConfigManager().isJoinConfirmationEnabled()) {
                openJoinConfirm(player, match);
            } else {
                openColorPicker(player, match.wager, match.hostColor);
                pendingJoin.put(player.getUniqueId(), match);
            }
        }
    }

    private void handleJoinConfirm(Player player, Inventory inv, int slot) {
        int confirmSlot = plugin.getConfig().getInt("join-confirm.confirm-slot", 3);
        int cancelSlot = plugin.getConfig().getInt("join-confirm.cancel-slot", 5);

        if (slot == cancelSlot) {
            pendingJoin.remove(player.getUniqueId());
            player.closeInventory();
            openMain(player);
            return;
        }
        if (slot != confirmSlot) return;

        CoinflipMatch pending = pendingJoin.get(player.getUniqueId());
        if (pending == null) { player.closeInventory(); return; }

        CoinflipMatch match = plugin.getCoinflipManager().getMatchByHost(pending.hostUuid);
        if (match == null || match != pending) {
            pendingJoin.remove(player.getUniqueId());
            player.sendMessage(cfg("match-no-longer-exists"));
            player.closeInventory();
            return;
        }
        if (!plugin.getEconomy().has(player, match.wager)) {
            pendingJoin.remove(player.getUniqueId());
            player.sendMessage(cfg("insufficient-funds-join"));
            player.closeInventory();
            return;
        }
        if (!checkCooldown(player)) {
            pendingJoin.remove(player.getUniqueId());
            player.closeInventory();
            return;
        }

        transitioning.add(player.getUniqueId());
        player.closeInventory();
        transitioning.remove(player.getUniqueId());
        openColorPicker(player, match.wager, match.hostColor);
    }

    private void handleColorPicker(Player player, Inventory inv, int slot) {
        WoolColor[] colors = WoolColor.values();
        if (slot < 0 || slot >= colors.length) return;
        WoolColor chosen = colors[slot];
        WoolColor excluded = excludedColors.get(inv);
        if (excluded != null && chosen == excluded) {
            player.sendMessage(cfg("color-taken"));
            return;
        }
        double wager = pendingWagers.getOrDefault(inv, 0.0);
        transitioning.add(player.getUniqueId());
        player.closeInventory();
        transitioning.remove(player.getUniqueId());

        PrivateMatchManager.PrivateInvite hostInvite = pendingPrivateHost.remove(inv);
        if (hostInvite != null) {
            plugin.getPrivateMatchManager().onHostColorChosen(hostInvite, chosen);
            return;
        }

        PrivateMatchManager.PrivateInvite targetInvite = pendingPrivateTarget.remove(inv);
        if (targetInvite != null) {
            plugin.getPrivateMatchManager().onTargetColorChosen(targetInvite, player, chosen);
            return;
        }

        Double botWager = pendingBot.remove(player.getUniqueId());
        if (botWager != null) { openBotMatchUI(player, botWager, chosen); return; }

        CoinflipMatch pending = pendingJoin.remove(player.getUniqueId());
        if (pending != null) {
            CoinflipMatch match = plugin.getCoinflipManager().getMatchByHost(pending.hostUuid);
            if (match == null || match != pending) { player.sendMessage(cfg("match-no-longer-exists")); return; }
            if (chosen == match.hostColor) { player.sendMessage(cfg("color-taken")); return; }
            plugin.getCoinflipManager().removeMatch(pending.hostUuid);
            plugin.getCoinflipManager().startCooldown(player.getUniqueId());
            plugin.getCoinflipManager().startCooldown(pending.hostUuid);
            boolean joinerWins = new Random().nextBoolean();
            openMatchUI(player, match, player, chosen, false, joinerWins);
            Player host = plugin.getServer().getPlayer(pending.hostUuid);
            if (host != null) openMatchUI(host, match, player, chosen, true, joinerWins);
        } else {
            if (!plugin.getCoinflipManager().createMatch(player, wager, chosen)) {
                player.sendMessage(cfg("insufficient-funds-create")); return;
            }
            player.sendMessage(cfg("match-created", "wager", CoinflipManager.fmt(wager)));
            openMain(player);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory inv = event.getInventory();
        GuiType type = guiTypes.get(inv);

        if ((type == GuiType.MATCH || type == GuiType.BOT_MATCH) && player.isOnline()) {
            MatchUIData md = matchUIs.get(inv);
            if (md != null && md.animating) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> { if (player.isOnline()) player.openInventory(inv); }, 1L);
                return;
            }
        }

        cleanup(inv);
    }

    public void handleDisconnect(Player player) {
        Inventory inv = findInventoryFor(player.getUniqueId());
        if (inv == null) return;

        GuiType type = guiTypes.get(inv);
        MatchUIData md = matchUIs.get(inv);

        if (type == GuiType.MATCH && md != null && md.animating && !md.isHost) {
            plugin.getEconomy().depositPlayer(plugin.getServer().getOfflinePlayer(md.match.hostUuid), md.match.wager);
            Player host = plugin.getServer().getPlayer(md.match.hostUuid);
            if (host != null) {
                host.sendMessage(cfg("match-no-longer-exists"));
                Inventory hostInv = findInventoryFor(host.getUniqueId());
                if (hostInv != null) {
                    MatchUIData hostData = matchUIs.get(hostInv);
                    if (hostData != null && hostData.match == md.match) {
                        cleanup(hostInv);
                        host.closeInventory();
                    }
                }
            }
        } else if (type == GuiType.MATCH && md != null && md.animating && md.isHost) {
            Player joiner = plugin.getServer().getPlayer(md.joinerUuid);
            if (joiner != null) joiner.sendMessage(cfg("match-no-longer-exists"));
        }

        cleanup(inv);
    }

    private Inventory findInventoryFor(UUID uuid) {
        for (Map.Entry<Inventory, Player> e : owners.entrySet()) {
            if (e.getValue().getUniqueId().equals(uuid)) return e.getKey();
        }
        return null;
    }

    private void cleanup(Inventory inv) {
        GuiType type = guiTypes.get(inv);
        Player owner = owners.get(inv);
        if (owner != null && (type == GuiType.JOIN_CONFIRM || type == GuiType.COLOR_PICKER)
                && !transitioning.contains(owner.getUniqueId())) {
            pendingJoin.remove(owner.getUniqueId());
            pendingBot.remove(owner.getUniqueId());
            if (pendingPrivateHost.remove(inv) != null) plugin.getPrivateMatchManager().cancelByHost(owner.getUniqueId());
            if (pendingPrivateTarget.remove(inv) != null) plugin.getPrivateMatchManager().cancelByTarget(owner.getUniqueId());
        }

        guiTypes.remove(inv);
        owners.remove(inv);
        pendingWagers.remove(inv);
        matchUIs.remove(inv);
        excludedColors.remove(inv);
    }

    private ItemStack makeMatchSlot(CoinflipMatch match, Player viewer) {
        ItemStack item = new ItemStack(match.hostColor.woolMaterial);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(c(cfg("match-slot-name", "color", match.hostColor.chatColor, "host", match.hostName)));
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(cfg("match-slot-wager", "wager", CoinflipManager.fmt(match.wager)));
        lore.add(cfg("match-slot-color", "color", match.hostColor.chatColor, "colorName", match.hostColor.displayName));
        lore.add("");
        lore.add(cfg(viewer.getUniqueId().equals(match.hostUuid) ? "match-slot-waiting" : "match-slot-join"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeHelpItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(c(cfg("help-name")));
        List<String> lore = new ArrayList<>(List.of("", cfg("help-cmd-main"), "", cfg("help-cmd-wager")));
        if (plugin.getConfigManager().isPrivateMatchesEnabled()) lore.addAll(List.of("", cfg("help-cmd-private"), "", cfg("help-cmd-toggle")));
        if (plugin.getConfigManager().isBotBattlesEnabled()) lore.addAll(List.of("", cfg("help-cmd-bot")));
        lore.addAll(List.of("", cfg("help-cmd-cancel"), "", cfg("help-cmd-stats")));
        if (plugin.getConfigManager().isHistoryEnabled()) lore.addAll(List.of("", cfg("help-cmd-history")));
        lore.addAll(List.of("", cfg("help-cmd-top")));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeConfirmInfoItem(CoinflipMatch match) {
        ItemStack item = new ItemStack(match.hostColor.woolMaterial);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(c(plugin.getConfig().getString("join-confirm.info-name", "{color}&l{host}'s Match")
                .replace("{color}", match.hostColor.chatColor)
                .replace("{host}", match.hostName)));
        List<String> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("join-confirm.info-lore")) {
            lore.add(c(line
                    .replace("{wager}", CoinflipManager.fmt(match.wager))
                    .replace("{color}", match.hostColor.chatColor)
                    .replace("{colorName}", match.hostColor.displayName)
                    .replace("{host}", match.hostName)));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeConfirmItem(String type) {
        Material mat = plugin.getConfigManager().getMaterial("join-confirm." + type + "-material",
                type.equals("confirm") ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE);
        String name = plugin.getConfig().getString("join-confirm." + type + "-name",
                type.equals("confirm") ? "&a&lConfirm" : "&c&lCancel");
        return namedPane(mat, c(name));
    }

    private ItemStack makeColorItem(WoolColor color, double wager, boolean taken) {
        if (taken) {
            ItemStack item = new ItemStack(Material.BARRIER);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(c(cfg("color-item-taken-name", "color", color.chatColor, "colorName", color.displayName)));
            meta.setLore(List.of(cfg("color-item-taken-lore")));
            item.setItemMeta(meta);
            return item;
        }
        ItemStack item = new ItemStack(color.woolMaterial);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(c(cfg("color-item-name", "color", color.chatColor, "colorName", color.displayName)));
        meta.setLore(List.of(cfg("color-item-lore-1"), cfg("color-item-lore-2", "wager", CoinflipManager.fmt(wager))));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeCountdown(int count) {
        ItemStack item = new ItemStack(Material.LIME_STAINED_GLASS_PANE, count);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(c(cfg("countdown-pane", "count", String.valueOf(count))));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeSkull(String name, UUID uuid, String chatColor, String ratio) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
        meta.setDisplayName(chatColor + org.bukkit.ChatColor.BOLD + name);
        meta.setLore(List.of(cfg("player-skull-lore", "ratio", ratio)));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeItem(Material mat, String name, String... loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(c(name));
        if (loreLines.length > 0) meta.setLore(Arrays.asList(loreLines));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack namedPane(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack silentPane(Material mat) { return namedPane(mat, " "); }

    private void fill(Inventory inv, Material mat) {
        ItemStack pane = namedPane(mat, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
    }

    private void track(Inventory inv, GuiType type, Player player) {
        guiTypes.put(inv, type); owners.put(inv, player);
    }

    private String ratio(UUID uuid) {
        var data = plugin.getPlayerDataManager().get(uuid);
        return data.getWins() + ":" + data.getLosses();
    }

    private WoolColor randomExcluding(WoolColor exclude) {
        WoolColor[] colors = WoolColor.values(); WoolColor pick; Random rand = new Random();
        do { pick = colors[rand.nextInt(colors.length)]; } while (pick == exclude);
        return pick;
    }
}