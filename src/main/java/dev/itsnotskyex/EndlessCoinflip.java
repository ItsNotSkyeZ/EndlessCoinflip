package dev.itsnotskyex;

import dev.itsnotskyex.commands.CoinflipCommand;
import dev.itsnotskyex.config.ConfigManager;
import dev.itsnotskyex.config.SoundManager;
import dev.itsnotskyex.data.PlayerDataManager;
import dev.itsnotskyex.gui.CoinflipGUI;
import dev.itsnotskyex.gui.CoinflipChatListener;
import dev.itsnotskyex.gui.HistoryGUI;
import dev.itsnotskyex.gui.LeaderboardGUI;
import dev.itsnotskyex.gui.PlayerConnectionListener;
import dev.itsnotskyex.integration.CoinflipPlaceholders;
import dev.itsnotskyex.integration.UpdateChecker;
import dev.itsnotskyex.manager.CoinflipManager;
import dev.itsnotskyex.manager.PrivateMatchManager;
import net.milkbowl.vault.economy.Economy;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class EndlessCoinflip extends JavaPlugin {

    private static final int BSTATS_PLUGIN_ID = 32766;

    private static EndlessCoinflip instance;

    private ConfigManager configManager;
    private SoundManager soundManager;
    private PlayerDataManager playerDataManager;
    private CoinflipManager coinflipManager;
    private PrivateMatchManager privateMatchManager;
    private CoinflipGUI coinflipGUI;
    private HistoryGUI historyGUI;
    private LeaderboardGUI leaderboardGUI;
    private Economy economy;
    private CoinflipPlaceholders placeholders;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        instance = this;

        if (!setupEconomy()) {
            getLogger().severe("Vault economy not found — disabling EndlessCoinflip.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        configManager   = new ConfigManager(this);
        getConfig().options().copyDefaults(false);
        soundManager    = new SoundManager(this);
        playerDataManager = new PlayerDataManager(this);
        coinflipManager = new CoinflipManager(this);
        privateMatchManager = new PrivateMatchManager(this);
        coinflipGUI     = new CoinflipGUI(this);
        historyGUI      = new HistoryGUI(this);
        leaderboardGUI  = new LeaderboardGUI(this);

        CoinflipCommand cmd = new CoinflipCommand(this);
        getCommand("coinflip").setExecutor(cmd);
        getCommand("coinflip").setTabCompleter(cmd);

        getServer().getPluginManager().registerEvents(coinflipGUI, this);
        getServer().getPluginManager().registerEvents(historyGUI, this);
        getServer().getPluginManager().registerEvents(leaderboardGUI, this);
        getServer().getPluginManager().registerEvents(new CoinflipChatListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);

        long expiryCheckTicks = 20L * getConfig().getInt("match-expiry.check-interval-seconds", 30);
        getServer().getScheduler().runTaskTimer(this, () -> coinflipManager.checkExpiredMatches(), expiryCheckTicks, expiryCheckTicks);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholders = new CoinflipPlaceholders(this);
            placeholders.register();
            long rankRefreshTicks = 20L * getConfig().getInt("placeholders.rank-refresh-seconds", 30);
            getServer().getScheduler().runTaskTimerAsynchronously(this, playerDataManager::refreshRanks, 0L, rankRefreshTicks);
            getLogger().info("Hooked into PlaceholderAPI.");
        }

        setupMetrics();

        updateChecker = new UpdateChecker(this);
        updateChecker.checkAsync();

        getLogger().info("EndlessCoinflip v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (placeholders != null) {
            placeholders.unregister();
        }
        if (coinflipManager != null) {
            coinflipManager.refundAllActive();
        }
        if (privateMatchManager != null) {
            privateMatchManager.refundAllPending();
        }
        if (playerDataManager != null) {
            playerDataManager.saveAll();
            playerDataManager.close();
        }
        getLogger().info("EndlessCoinflip disabled.");
    }

    private void setupMetrics() {
        Metrics metrics = new Metrics(this, BSTATS_PLUGIN_ID);
        metrics.addCustomChart(new SimplePie("storage_type", () -> getConfig().getString("storage.type", "FILE").toUpperCase()));
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    public static EndlessCoinflip getInstance() { return instance; }
    public ConfigManager getConfigManager()     { return configManager; }
    public SoundManager getSoundManager()       { return soundManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public CoinflipManager getCoinflipManager() { return coinflipManager; }
    public PrivateMatchManager getPrivateMatchManager() { return privateMatchManager; }
    public CoinflipGUI getCoinflipGUI()         { return coinflipGUI; }
    public HistoryGUI getHistoryGUI()           { return historyGUI; }
    public LeaderboardGUI getLeaderboardGUI()   { return leaderboardGUI; }
    public Economy getEconomy()                 { return economy; }
    public UpdateChecker getUpdateChecker()     { return updateChecker; }
}