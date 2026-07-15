package dev.itsnotskyex.config;

import dev.itsnotskyex.EndlessCoinflip;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.logging.Level;

public class SoundManager {

    private final EndlessCoinflip plugin;

    public SoundManager(EndlessCoinflip plugin) {
        this.plugin = plugin;
    }

    public void play(Player player, String key) {
        play(player, key, 0);
    }

    public void play(Player player, String key, int step) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("sounds." + key);
        if (section == null) return;

        Sound sound = resolveSound(section.getString("sound"), key);
        if (sound == null) return;

        float volume = (float) section.getDouble("volume", 1.0);
        float basePitch = (float) section.getDouble("pitch", 1.0);
        float pitchStep = (float) section.getDouble("pitch-step", 0.0);
        float pitch = clampPitch(basePitch + (pitchStep * step));

        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private float clampPitch(float pitch) {
        return Math.max(0.5f, Math.min(2.0f, pitch));
    }

    private Sound resolveSound(String name, String key) {
        if (name == null) return null;
        try {
            return Sound.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().log(Level.WARNING, "Invalid sound '" + name + "' for sounds." + key + " in config.yml");
            return null;
        }
    }
}
