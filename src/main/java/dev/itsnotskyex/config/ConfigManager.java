package dev.itsnotskyex.config;

import dev.itsnotskyex.EndlessCoinflip;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ConfigManager {

    private final EndlessCoinflip plugin;
    private FileConfiguration messagesConfig;

    public ConfigManager(EndlessCoinflip plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    public boolean reload() {
        plugin.saveDefaultConfig();

        File configFile = new File(plugin.getDataFolder(), "config.yml");
        try {
            new YamlConfiguration().load(configFile);
        } catch (Exception e) {
            plugin.getLogger().severe("config.yml failed to parse — reload aborted, previous settings are still active: " + e.getMessage());
            return false;
        }

        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        try {
            new YamlConfiguration().load(messagesFile);
        } catch (Exception e) {
            plugin.getLogger().severe("messages.yml failed to parse — reload aborted, previous settings are still active: " + e.getMessage());
            return false;
        }

        plugin.reloadConfig();
        plugin.getConfig().options().copyDefaults(false);
        loadMessages();
        return true;
    }

    private void loadMessages() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        InputStream defStream = plugin.getResource("messages.yml");
        if (defStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
            messagesConfig.setDefaults(defConfig);
        }
    }

    public FileConfiguration getMessagesConfig() { return messagesConfig; }

    public String getMessage(String key, String... replacements) {
        String raw = messagesConfig.getString(key);
        if (raw == null) raw = plugin.getConfig().getString("gui." + key);
        if (raw == null) raw = key;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return color(raw);
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public double getMinWager()      { return plugin.getConfig().getDouble("settings.min-wager", 100); }
    public double getMaxWager()      { return plugin.getConfig().getDouble("settings.max-wager", 1_000_000); }
    public int getMaxActiveMatches() { return plugin.getConfig().getInt("settings.max-active-matches", 6); }
    public int getMaxHistoryStored() { return plugin.getConfig().getInt("history.max-stored", 200); }

    public boolean isBotBattlesEnabled()     { return plugin.getConfig().getBoolean("features.bot-battles", true); }
    public boolean isJoinConfirmationEnabled() { return plugin.getConfig().getBoolean("features.join-confirmation", true); }

    public Material getMaterial(String key, Material fallback) {
        String name = plugin.getConfig().getString(key);
        if (name == null) return fallback;
        Material mat;
        try {
            mat = Material.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material '" + name + "' for " + key + " in config.yml, using default.");
            return fallback;
        }
        if (mat.isAir()) {
            plugin.getLogger().warning("Material '" + name + "' for " + key + " in config.yml is an air type and can't be used as an item, using default.");
            return fallback;
        }
        return mat;
    }
}
