package dev.itsnotskyex.integration;

import dev.itsnotskyex.EndlessCoinflip;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UpdateChecker {

    private static final int RESOURCE_ID = 137052;
    private static final String DOWNLOAD_URL = "https://www.spigotmc.org/resources/" + RESOURCE_ID + "/";

    private final EndlessCoinflip plugin;
    private volatile String latestVersion;

    public UpdateChecker(EndlessCoinflip plugin) {
        this.plugin = plugin;
    }

    public void checkAsync() {
        if (!plugin.getConfigManager().isUpdateCheckEnabled()) return;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::check);
    }

    private void check() {
        String latest;
        try {
            URL url = new URL("https://api.spigotmc.org/legacy/update.php?resource=" + RESOURCE_ID);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "EndlessCoinflip-UpdateChecker");

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                latest = reader.readLine();
            }
        } catch (Exception e) {
            plugin.getLogger().fine("Update check failed: " + e.getMessage());
            return;
        }

        if (latest == null || latest.isBlank()) return;
        latest = latest.trim();
        if (latest.equals(plugin.getDescription().getVersion())) return;

        latestVersion = latest;
        plugin.getServer().getScheduler().runTask(plugin, () -> notify(plugin.getServer().getConsoleSender()));
    }

    public void notifyIfAvailable(Player player) {
        if (latestVersion == null || !player.hasPermission("endlesscoinflip.admin")) return;
        notify(player);
    }

    private void notify(CommandSender sender) {
        String rawMessage = plugin.getConfigManager().getUpdateAvailableMessage(latestVersion);
        if (rawMessage == null) return;

        List<BaseComponent> parts = new ArrayList<>();
        Collections.addAll(parts, TextComponent.fromLegacyText(rawMessage));
        parts.add(new TextComponent(" "));

        TextComponent link = new TextComponent("[Download]");
        link.setColor(ChatColor.AQUA);
        link.setUnderlined(true);
        link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, DOWNLOAD_URL));
        link.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to open " + DOWNLOAD_URL)));
        parts.add(link);

        sender.spigot().sendMessage(parts.toArray(new BaseComponent[0]));
    }
}
