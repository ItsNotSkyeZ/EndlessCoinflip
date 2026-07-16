package dev.itsnotskyex.gui;

import dev.itsnotskyex.EndlessCoinflip;
import dev.itsnotskyex.manager.CoinflipManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class CoinflipChatListener implements Listener {

    private final EndlessCoinflip plugin;

    public CoinflipChatListener(EndlessCoinflip plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getCoinflipManager().isPendingBotBattle(player.getUniqueId())) return;

        event.setCancelled(true);
        plugin.getCoinflipManager().clearPendingBotBattle(player.getUniqueId());

        if (!plugin.getConfigManager().isBotBattlesEnabled()) {
            player.sendMessage(plugin.getConfigManager().getMessage("feature-disabled"));
            return;
        }

        String input = event.getMessage().trim();
        if (input.equalsIgnoreCase("cancel")) {
            player.sendMessage(plugin.getConfigManager().getMessage("bot-battle-cancelled"));
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            double wager = plugin.getCoinflipManager().parseWager(input, player);
            if (Double.isNaN(wager)) {
                player.sendMessage(plugin.getConfigManager().getMessage("bot-battle-invalid-amount"));
                return;
            }

            if (wager <= 0) {
                player.sendMessage(plugin.getConfigManager().getMessage("wager-must-be-positive"));
                return;
            }

            double min = plugin.getConfigManager().getMinWager();
            double max = plugin.getConfigManager().getMaxWager(player);
            if (wager < min) {
                player.sendMessage(plugin.getConfigManager().getMessage("wager-too-low", "min", CoinflipManager.fmt(min)));
                return;
            }
            if (wager > max) {
                player.sendMessage(plugin.getConfigManager().getMessage("wager-too-high", "max", CoinflipManager.fmt(max)));
                return;
            }
            if (!plugin.getEconomy().has(player, wager)) {
                player.sendMessage(plugin.getConfigManager().getMessage("insufficient-funds-need", "wager", CoinflipManager.fmt(wager)));
                return;
            }
            long remaining = plugin.getCoinflipManager().getCooldownRemainingSeconds(player.getUniqueId());
            if (remaining > 0) {
                player.sendMessage(plugin.getConfigManager().getMessage("cooldown-active", "time", String.valueOf(remaining)));
                return;
            }

            plugin.getCoinflipGUI().openBotColorPicker(player, wager);
        });
    }
}
