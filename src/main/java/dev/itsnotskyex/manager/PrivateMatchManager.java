package dev.itsnotskyex.manager;

import dev.itsnotskyex.EndlessCoinflip;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class PrivateMatchManager {

    private final EndlessCoinflip plugin;
    private final Map<UUID, PrivateInvite> byHost = new HashMap<>();
    private final Map<UUID, PrivateInvite> byTarget = new HashMap<>();

    public PrivateMatchManager(EndlessCoinflip plugin) {
        this.plugin = plugin;
    }

    public static class PrivateInvite {
        public final UUID hostUuid;
        public final String hostName;
        public final UUID targetUuid;
        public final String targetName;
        public final double wager;
        public final long createdAt;
        public WoolColor hostColor;
        public boolean accepted;
        public BukkitTask expiryTask;

        public PrivateInvite(UUID hostUuid, String hostName, UUID targetUuid, String targetName, double wager) {
            this.hostUuid = hostUuid;
            this.hostName = hostName;
            this.targetUuid = targetUuid;
            this.targetName = targetName;
            this.wager = wager;
            this.createdAt = System.currentTimeMillis();
        }
    }

    private String cfg(String key, String... r) { return plugin.getConfigManager().getMessage(key, r); }

    public boolean hasPendingAsHost(UUID uuid)   { return byHost.containsKey(uuid); }
    public boolean hasPendingAsTarget(UUID uuid) { return byTarget.containsKey(uuid); }

    public void createInvite(Player host, Player target, double wager) {
        plugin.getEconomy().withdrawPlayer(host, wager);
        PrivateInvite invite = new PrivateInvite(host.getUniqueId(), host.getName(), target.getUniqueId(), target.getName(), wager);
        byHost.put(invite.hostUuid, invite);
        byTarget.put(invite.targetUuid, invite);
        plugin.getCoinflipManager().startCooldown(host.getUniqueId());

        if (plugin.getConfig().getBoolean("match-expiry.enabled", true)) {
            long minutes = plugin.getConfig().getInt("match-expiry.minutes", 10);
            invite.expiryTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> expireInvite(invite), minutes * 60 * 20L);
        }

        host.sendMessage(cfg("private-invite-sent", "target", target.getName(), "wager", CoinflipManager.fmt(wager)));
        sendInviteChat(target, invite);
        plugin.getCoinflipGUI().openPrivateColorPicker(host, invite);
    }

    private void sendInviteChat(Player target, PrivateInvite invite) {
        target.sendMessage(cfg("private-invite-received", "host", invite.hostName, "wager", CoinflipManager.fmt(invite.wager)));

        TextComponent accept = new TextComponent(cfg("private-invite-accept-text"));
        accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cf accept"));
        accept.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(cfg("private-invite-accept-hover"))));

        TextComponent space = new TextComponent("  ");

        TextComponent deny = new TextComponent(cfg("private-invite-deny-text"));
        deny.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cf deny"));
        deny.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(cfg("private-invite-deny-hover"))));

        target.spigot().sendMessage(accept, space, deny);
    }

    public void handleAccept(Player target) {
        PrivateInvite invite = byTarget.get(target.getUniqueId());
        if (invite == null) { target.sendMessage(cfg("no-pending-invite")); return; }
        if (!plugin.getEconomy().has(target, invite.wager)) { target.sendMessage(cfg("insufficient-funds-join")); return; }

        invite.accepted = true;
        if (invite.hostColor != null) {
            plugin.getCoinflipGUI().openPrivateJoinColorPicker(target, invite);
        } else {
            target.sendMessage(cfg("private-invite-waiting-color", "host", invite.hostName));
            Player host = plugin.getServer().getPlayer(invite.hostUuid);
            if (host != null) host.sendMessage(cfg("private-invite-accepted-waiting-you", "target", invite.targetName));
        }
    }

    public boolean cancelByHost(UUID hostUuid) {
        PrivateInvite invite = byHost.get(hostUuid);
        if (invite == null) return false;
        Player host = plugin.getServer().getPlayer(invite.hostUuid);
        Player target = plugin.getServer().getPlayer(invite.targetUuid);
        refundAndCloseGuis(invite);
        if (host != null) host.sendMessage(cfg("private-invite-cancelled", "target", invite.targetName));
        if (target != null) target.sendMessage(cfg("private-invite-cancelled-host", "host", invite.hostName));
        return true;
    }

    public boolean cancelByTarget(UUID targetUuid) {
        PrivateInvite invite = byTarget.get(targetUuid);
        if (invite == null) return false;
        Player host = plugin.getServer().getPlayer(invite.hostUuid);
        Player target = plugin.getServer().getPlayer(invite.targetUuid);
        refundAndCloseGuis(invite);
        if (target != null) target.sendMessage(cfg("private-invite-denied-self", "host", invite.hostName));
        if (host != null) host.sendMessage(cfg("private-invite-denied", "target", invite.targetName));
        return true;
    }

    private void expireInvite(PrivateInvite invite) {
        if (byHost.get(invite.hostUuid) != invite) return;
        Player host = plugin.getServer().getPlayer(invite.hostUuid);
        Player target = plugin.getServer().getPlayer(invite.targetUuid);
        refundAndCloseGuis(invite);
        if (host != null) host.sendMessage(cfg("private-invite-expired", "target", invite.targetName));
        if (target != null) target.sendMessage(cfg("private-invite-expired-target", "host", invite.hostName));
    }

    private void abortInsufficientFunds(PrivateInvite invite) {
        Player host = plugin.getServer().getPlayer(invite.hostUuid);
        Player target = plugin.getServer().getPlayer(invite.targetUuid);
        refundAndCloseGuis(invite);
        if (host != null) host.sendMessage(cfg("match-no-longer-exists"));
        if (target != null) target.sendMessage(cfg("insufficient-funds-join"));
    }

    public void handleDisconnect(Player player) {
        UUID uuid = player.getUniqueId();
        PrivateInvite asHost = byHost.get(uuid);
        if (asHost != null) {
            Player target = plugin.getServer().getPlayer(asHost.targetUuid);
            refundAndCloseGuis(asHost);
            if (target != null) target.sendMessage(cfg("private-invite-cancelled-host", "host", asHost.hostName));
        }
        PrivateInvite asTarget = byTarget.get(uuid);
        if (asTarget != null) {
            Player host = plugin.getServer().getPlayer(asTarget.hostUuid);
            refundAndCloseGuis(asTarget);
            if (host != null) host.sendMessage(cfg("private-invite-denied", "target", asTarget.targetName));
        }
    }

    public void onHostColorChosen(PrivateInvite invite, WoolColor color) {
        if (byHost.get(invite.hostUuid) != invite) return;
        invite.hostColor = color;
        Player host = plugin.getServer().getPlayer(invite.hostUuid);
        if (host != null) host.sendMessage(cfg("private-invite-color-chosen", "target", invite.targetName, "color", color.chatColor + color.displayName));

        if (invite.accepted) {
            Player target = plugin.getServer().getPlayer(invite.targetUuid);
            if (target == null) { refundAndCloseGuis(invite); return; }
            if (!plugin.getEconomy().has(target, invite.wager)) { abortInsufficientFunds(invite); return; }
            plugin.getCoinflipGUI().openPrivateJoinColorPicker(target, invite);
        }
    }

    public void onTargetColorChosen(PrivateInvite invite, Player target, WoolColor targetColor) {
        if (byHost.get(invite.hostUuid) != invite) {
            target.sendMessage(cfg("match-no-longer-exists"));
            return;
        }
        Player host = plugin.getServer().getPlayer(invite.hostUuid);
        if (host == null) { abortInsufficientFunds(invite); return; }
        if (!plugin.getEconomy().has(target, invite.wager)) { abortInsufficientFunds(invite); return; }

        remove(invite);
        plugin.getCoinflipManager().startCooldown(host.getUniqueId());
        plugin.getCoinflipManager().startCooldown(target.getUniqueId());

        CoinflipManager.CoinflipMatch match = new CoinflipManager.CoinflipMatch(invite.hostUuid, invite.hostName, invite.wager, invite.hostColor);
        boolean joinerWins = new Random().nextBoolean();
        plugin.getCoinflipGUI().openMatchUI(target, match, target, targetColor, false, joinerWins);
        plugin.getCoinflipGUI().openMatchUI(host, match, target, targetColor, true, joinerWins);
    }

    private void remove(PrivateInvite invite) {
        if (invite.expiryTask != null) invite.expiryTask.cancel();
        byHost.remove(invite.hostUuid);
        byTarget.remove(invite.targetUuid);
    }

    private void refundAndCloseGuis(PrivateInvite invite) {
        remove(invite);
        plugin.getEconomy().depositPlayer(plugin.getServer().getOfflinePlayer(invite.hostUuid), invite.wager);
        Player host = plugin.getServer().getPlayer(invite.hostUuid);
        Player target = plugin.getServer().getPlayer(invite.targetUuid);
        if (host != null) plugin.getCoinflipGUI().closePrivateColorPicker(host);
        if (target != null) plugin.getCoinflipGUI().closePrivateTargetPicker(target);
    }
}
