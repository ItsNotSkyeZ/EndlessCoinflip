package dev.itsnotskyex.config;

import dev.itsnotskyex.EndlessCoinflip;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ConfigManager {

    // Bumped only when a list-valued default (e.g. leaderboard.entry-lore, messages.yml's
    // "stats" list) changes and existing installs need a one-time refresh to pick it up.
    // Tracked in an internal file rather than the user-facing config/messages files so it
    // doesn't clutter what admins see when they open them.
    private static final int CONFIG_VERSION = 1;
    private static final int MESSAGES_VERSION = 1;

    private final EndlessCoinflip plugin;
    private FileConfiguration messagesConfig;

    public ConfigManager(EndlessCoinflip plugin) {
        this.plugin = plugin;
        updateConfigFile();
        loadMessages();
    }

    public boolean reload() {
        plugin.saveDefaultConfig();
        updateConfigFile();

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


    private void updateConfigFile() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        boolean refreshLists = getStoredVersion("config-version") < CONFIG_VERSION;
        ConfigUpdater.update(configFile, plugin, "config.yml", refreshLists, plugin.getLogger());
        if (refreshLists) setStoredVersion("config-version", CONFIG_VERSION);
    }

    private void loadMessages() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
            setStoredVersion("messages-version", MESSAGES_VERSION);
        } else {
            boolean refreshLists = getStoredVersion("messages-version") < MESSAGES_VERSION;
            ConfigUpdater.update(messagesFile, plugin, "messages.yml", refreshLists, plugin.getLogger());
            if (refreshLists) setStoredVersion("messages-version", MESSAGES_VERSION);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        InputStream defStream = plugin.getResource("messages.yml");
        if (defStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
            messagesConfig.setDefaults(defConfig);
        }
    }

    private File internalStateFile() { return new File(plugin.getDataFolder(), "internal-state.yml"); }

    private int getStoredVersion(String key) {
        File file = internalStateFile();
        if (!file.exists()) return 0;
        return YamlConfiguration.loadConfiguration(file).getInt(key, 0);
    }

    private void setStoredVersion(String key, int version) {
        File file = internalStateFile();
        YamlConfiguration yml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        yml.set(key, version);
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save internal-state.yml: " + e.getMessage());
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

    public double getMaxWager(Player player) {
        double max = getMaxWager();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("wager-limits");
        if (section == null) return max;
        for (String perm : section.getKeys(false)) {
            if (!perm.toLowerCase().startsWith("endlesscoinflip.")) continue;
            if (!player.hasPermission(perm)) continue;
            double limit = section.getDouble(perm, max);
            if (limit > max) max = limit;
        }
        return max;
    }

    public double getServerTaxPercent() {
        double percent = plugin.getConfig().getDouble("settings.server-tax-percent", 0);
        if (percent < 0) return 0;
        if (percent > 100) return 100;
        return percent;
    }

    public boolean isBotBattlesEnabled()     { return plugin.getConfig().getBoolean("features.bot-battles", true); }
    public boolean isJoinConfirmationEnabled() { return plugin.getConfig().getBoolean("features.join-confirmation", true); }
    public boolean isPrivateMatchesEnabled() { return plugin.getConfig().getBoolean("features.private-matches", true); }
    public boolean isHistoryEnabled()        { return plugin.getConfig().getBoolean("features.history", true); }
    public boolean isBotBattleStatsEnabled() { return plugin.getConfig().getBoolean("features.bot-battle-stats", true); }

    public boolean isBigWinBroadcastEnabled()   { return plugin.getConfig().getBoolean("broadcast.big-win.enabled", false); }
    public double getBigWinBroadcastThreshold() { return plugin.getConfig().getDouble("broadcast.big-win.threshold", 1_000_000); }

    public String getBigWinBroadcastMessage(String... replacements) {
        String raw = plugin.getConfig().getString("broadcast.big-win.message");
        if (raw == null) return null;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return color(raw);
    }

    public boolean isWinStreakBroadcastEnabled()   { return plugin.getConfig().getBoolean("broadcast.win-streak.enabled", false); }
    public int getWinStreakBroadcastInterval()     { return plugin.getConfig().getInt("broadcast.win-streak.interval", 5); }

    public String getWinStreakBroadcastMessage(String... replacements) {
        String raw = plugin.getConfig().getString("broadcast.win-streak.message");
        if (raw == null) return null;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return color(raw);
    }

    public boolean isPvpMatchWinBroadcastEnabled()   { return plugin.getConfig().getBoolean("broadcast.match-win.pvp.enabled", false); }
    public double  getPvpMatchWinBroadcastMinWager() { return plugin.getConfig().getDouble("broadcast.match-win.pvp.min-wager", 0); }

    public String getPvpMatchWinBroadcastMessage(String... replacements) {
        String raw = plugin.getConfig().getString("broadcast.match-win.pvp.message");
        if (raw == null) return null;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return color(raw);
    }

    public boolean isBotMatchWinBroadcastEnabled()   { return plugin.getConfig().getBoolean("broadcast.match-win.bot.enabled", false); }
    public double  getBotMatchWinBroadcastMinWager() { return plugin.getConfig().getDouble("broadcast.match-win.bot.min-wager", 0); }

    public String getBotMatchWinBroadcastMessage(String... replacements) {
        String raw = plugin.getConfig().getString("broadcast.match-win.bot.message");
        if (raw == null) return null;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return color(raw);
    }

    public boolean isUpdateCheckEnabled() { return plugin.getConfig().getBoolean("update-check.enabled", true); }

    public String getUpdateAvailableMessage(String latestVersion) {
        String raw = plugin.getConfig().getString("update-check.message");
        if (raw == null) return null;
        return color(raw.replace("{version}", latestVersion));
    }

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
