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
import dev.itsnotskyex.manager.CoinflipManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class EndlessCoinflip extends JavaPlugin {

    private static EndlessCoinflip instance;

    private ConfigManager configManager;
    private SoundManager soundManager;
    private PlayerDataManager playerDataManager;
    private CoinflipManager coinflipManager;
    private CoinflipGUI coinflipGUI;
    private HistoryGUI historyGUI;
    private LeaderboardGUI leaderboardGUI;
    private Economy economy;

    @Override
    public void onEnable() {
        instance = this;

        if (!setupEconomy()) {
            getLogger().severe("Vault economy not found — disabling EndlessCoinflip.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        getConfig().options().copyDefaults(false);
        configManager   = new ConfigManager(this);
        soundManager    = new SoundManager(this);
        playerDataManager = new PlayerDataManager(this);
        coinflipManager = new CoinflipManager(this);
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

        getLogger().info("EndlessCoinflip v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) {
            playerDataManager.saveAll();
            playerDataManager.close();
        }
        getLogger().info("EndlessCoinflip disabled.");
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
    public CoinflipGUI getCoinflipGUI()         { return coinflipGUI; }
    public HistoryGUI getHistoryGUI()           { return historyGUI; }
    public LeaderboardGUI getLeaderboardGUI()   { return leaderboardGUI; }
    public Economy getEconomy()                 { return economy; }
}