package dev.itsnotskyex.gui;

import dev.itsnotskyex.EndlessCoinflip;
import dev.itsnotskyex.data.PlayerData;
import dev.itsnotskyex.manager.CoinflipManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerConnectionListener implements Listener {

    private final EndlessCoinflip plugin;

    public PlayerConnectionListener(EndlessCoinflip plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        if (data.getPendingPayout() > 0) {
            double amount = data.getPendingPayout();
            data.setPendingPayout(0);
            plugin.getPlayerDataManager().save(data);
            plugin.getEconomy().depositPlayer(player, amount);
            player.sendMessage(plugin.getConfigManager().getMessage("pending-payout", "amount", CoinflipManager.fmt(amount)));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getCoinflipGUI().handleDisconnect(player);
        plugin.getCoinflipManager().clearPendingBotBattle(player.getUniqueId());

        if (plugin.getCoinflipManager().hasBotMatch(player.getUniqueId())) {
            double wager = plugin.getCoinflipManager().getBotMatchWager(player.getUniqueId());
            plugin.getCoinflipManager().unregisterBotMatch(player.getUniqueId());
            plugin.getEconomy().depositPlayer(player, wager);
        }

        if (plugin.getCoinflipManager().hasActiveMatch(player.getUniqueId())) {
            plugin.getCoinflipManager().cancelMatch(player);
        }
    }
}
