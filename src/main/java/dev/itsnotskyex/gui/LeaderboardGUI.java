package dev.itsnotskyex.gui;

import dev.itsnotskyex.EndlessCoinflip;
import dev.itsnotskyex.config.ConfigManager;
import dev.itsnotskyex.manager.CoinflipManager;
import dev.itsnotskyex.storage.LeaderboardEntry;
import dev.itsnotskyex.storage.LeaderboardPage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LeaderboardGUI implements Listener {

    private final EndlessCoinflip plugin;
    private final Map<Inventory, int[]> state = new HashMap<>();

    public LeaderboardGUI(EndlessCoinflip plugin) { this.plugin = plugin; }

    private static String c(String s) { return ConfigManager.color(s); }

    private String sortBy() { return plugin.getConfig().getString("leaderboard.sort-by", "total-won"); }

    public void open(Player player, int page) {
        int size = clampSize(plugin.getConfig().getInt("leaderboard.gui-size", 54));
        int entriesPerPage = Math.max(size - 9, 9);
        int requestedPage = Math.max(0, page);

        LeaderboardPage requested = plugin.getPlayerDataManager().getLeaderboardPage(sortBy(), entriesPerPage, requestedPage * entriesPerPage);
        int totalPages = Math.max(1, (int) Math.ceil(requested.total / (double) entriesPerPage));
        page = Math.min(requestedPage, totalPages - 1);

        List<LeaderboardEntry> entries = page == requestedPage
                ? requested.entries
                : plugin.getPlayerDataManager().getLeaderboardPage(sortBy(), entriesPerPage, page * entriesPerPage).entries;

        String title = c(plugin.getConfig().getString("leaderboard.gui-title", "&0Leaderboard &8({page}/{pages})")
                .replace("{page}", String.valueOf(page + 1))
                .replace("{pages}", String.valueOf(totalPages)));

        Inventory inv = Bukkit.createInventory(null, size, title);

        Material emptyMat = plugin.getConfigManager().getMaterial("leaderboard.empty-material", Material.BLACK_STAINED_GLASS_PANE);
        fill(inv, emptyMat);

        int rankOffset = page * entriesPerPage;
        for (int i = 0; i < entries.size(); i++) {
            inv.setItem(i, makeEntryItem(entries.get(i), rankOffset + i + 1));
        }

        int prevSlot = navSlot("previous-slot", size - 9, size);
        int nextSlot = navSlot("next-slot", size - 1, size);
        inv.setItem(prevSlot, makeNavItem("previous"));
        inv.setItem(nextSlot, makeNavItem("next"));

        state.put(inv, new int[]{page, totalPages});
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory inv = event.getInventory();
        int[] data = state.get(inv);
        if (data == null) return;
        event.setCancelled(true);

        int page = data[0];
        int totalPages = data[1];
        int size = inv.getSize();
        int prevSlot = navSlot("previous-slot", size - 9, size);
        int nextSlot = navSlot("next-slot", size - 1, size);
        int slot = event.getRawSlot();

        if (slot == prevSlot) {
            if (page > 0) {
                open(player, page - 1);
            } else {
                player.sendMessage(c(plugin.getConfig().getString("leaderboard.no-previous-page", "&cYou're already on the first page.")));
            }
        } else if (slot == nextSlot) {
            if (page < totalPages - 1) {
                open(player, page + 1);
            } else {
                player.sendMessage(c(plugin.getConfig().getString("leaderboard.no-next-page", "&cYou're already on the last page.")));
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        state.remove(event.getInventory());
    }

    private ItemStack makeEntryItem(LeaderboardEntry entry, int rank) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();

        OfflinePlayer offline = Bukkit.getOfflinePlayer(entry.uuid);
        String name = offline.getName() != null ? offline.getName() : entry.uuid.toString().substring(0, 8);
        meta.setOwningPlayer(offline);

        String nameTemplate = plugin.getConfig().getString("leaderboard.entry-name", "&b&l#{rank} &f{name}");
        meta.setDisplayName(c(nameTemplate
                .replace("{rank}", String.valueOf(rank))
                .replace("{name}", name)));

        List<String> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("leaderboard.entry-lore")) {
            lore.add(c(line
                    .replace("{wins}", String.valueOf(entry.wins))
                    .replace("{losses}", String.valueOf(entry.losses))
                    .replace("{wagered}", CoinflipManager.fmt(entry.totalWagered))
                    .replace("{won}", CoinflipManager.fmt(entry.totalWon))
                    .replace("{biggestWin}", CoinflipManager.fmt(entry.biggestWin))
                    .replace("{biggestLoss}", CoinflipManager.fmt(entry.biggestLoss))));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeNavItem(String type) {
        Material mat = plugin.getConfigManager().getMaterial("leaderboard." + type + "-material", Material.ARROW);
        String name = plugin.getConfig().getString("leaderboard." + type + "-name",
                type.equals("previous") ? "&b&l« Previous Page" : "&b&lNext Page »");
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(c(name));
        item.setItemMeta(meta);
        return item;
    }

    private void fill(Inventory inv, Material mat) {
        ItemStack pane = new ItemStack(mat);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
    }

    private int navSlot(String key, int fallback, int size) {
        if (!plugin.getConfig().contains("leaderboard." + key)) return fallback;
        int slot = plugin.getConfig().getInt("leaderboard." + key);
        if (slot < 0 || slot >= size) {
            return fallback;
        }
        return slot;
    }

    private int clampSize(int size) {
        if (size < 9) size = 9;
        if (size > 54) size = 54;
        int rem = size % 9;
        if (rem != 0) size = Math.min(54, size + (9 - rem));
        return size;
    }
}
