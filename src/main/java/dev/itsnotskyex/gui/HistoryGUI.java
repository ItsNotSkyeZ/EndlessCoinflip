package dev.itsnotskyex.gui;

import dev.itsnotskyex.EndlessCoinflip;
import dev.itsnotskyex.config.ConfigManager;
import dev.itsnotskyex.data.MatchHistoryEntry;
import dev.itsnotskyex.manager.CoinflipManager;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HistoryGUI implements Listener {

    private final EndlessCoinflip plugin;
    private final Map<Inventory, int[]> state = new HashMap<>();

    public HistoryGUI(EndlessCoinflip plugin) { this.plugin = plugin; }

    private static String c(String s) { return ConfigManager.color(s); }

    public void open(Player player, int page) {
        List<MatchHistoryEntry> history = plugin.getPlayerDataManager().get(player.getUniqueId()).getHistory();

        int size = clampSize(plugin.getConfig().getInt("history.gui-size", 54));
        int entriesPerPage = Math.max(size - 9, 9);
        int totalPages = Math.max(1, (int) Math.ceil(history.size() / (double) entriesPerPage));
        page = Math.max(0, Math.min(page, totalPages - 1));

        String title = c(plugin.getConfig().getString("history.title", "&0Coinflip History &8({page}/{pages})")
                .replace("{page}", String.valueOf(page + 1))
                .replace("{pages}", String.valueOf(totalPages)));

        Inventory inv = Bukkit.createInventory(null, size, title);

        Material emptyMat = plugin.getConfigManager().getMaterial("history.empty-material", Material.BLACK_STAINED_GLASS_PANE);
        fill(inv, emptyMat);

        int start = page * entriesPerPage;
        for (int i = 0; i < entriesPerPage && (start + i) < history.size(); i++) {
            inv.setItem(i, makeEntryItem(history.get(start + i)));
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
                player.sendMessage(c(plugin.getConfig().getString("history.no-previous-page", "&cYou're already on the first page.")));
            }
        } else if (slot == nextSlot) {
            if (page < totalPages - 1) {
                open(player, page + 1);
            } else {
                player.sendMessage(c(plugin.getConfig().getString("history.no-next-page", "&cYou're already on the last page.")));
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        state.remove(event.getInventory());
    }

    private ItemStack makeEntryItem(MatchHistoryEntry entry) {
        Material mat = entry.won
                ? plugin.getConfigManager().getMaterial("history.win-material", Material.LIME_STAINED_GLASS_PANE)
                : plugin.getConfigManager().getMaterial("history.lose-material", Material.RED_STAINED_GLASS_PANE);

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        String nameTemplate = plugin.getConfig().getString(entry.won ? "history.entry-win-name" : "history.entry-lose-name",
                entry.won ? "&a&lWON &8vs &f{opponent}" : "&c&lLOST &8vs &f{opponent}");
        meta.setDisplayName(c(nameTemplate.replace("{opponent}", entry.opponentName)));

        String dateFormat = plugin.getConfig().getString("history.date-format", "dd/MM/yyyy HH:mm");
        String date;
        try {
            date = new SimpleDateFormat(dateFormat).format(new Date(entry.timestamp));
        } catch (IllegalArgumentException e) {
            date = String.valueOf(entry.timestamp);
        }

        List<String> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("history.entry-lore")) {
            lore.add(c(line
                    .replace("{wager}", CoinflipManager.fmt(entry.wager))
                    .replace("{opponent}", entry.opponentName)
                    .replace("{type}", entry.vsBot ? "Coinflip Bot" : "Player")
                    .replace("{date}", date)));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeNavItem(String type) {
        Material mat = plugin.getConfigManager().getMaterial("history." + type + "-material", Material.ARROW);
        String name = plugin.getConfig().getString("history." + type + "-name",
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
        if (!plugin.getConfig().contains("history." + key)) return fallback;
        int slot = plugin.getConfig().getInt("history." + key);
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
