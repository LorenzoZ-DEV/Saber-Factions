package com.massivecraft.factions.util;

import com.cryptomorin.xseries.XSound;
import com.massivecraft.factions.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

public final class SoundUtil {

    private static final Map<String, Sound> CACHE = new HashMap<>();

    private SoundUtil() {}

    public static void play(String configPath, Player initiator) {
        ConfigurationSection sec = FactionsPlugin.getInstance().getConfig().getConfigurationSection(configPath);
        if (sec == null || !sec.getBoolean("enabled", true)) return;

        String soundName = sec.getString("sound", "");
        if (soundName.isEmpty()) return;

        Sound sound = resolve(soundName);
        if (sound == null) return;

        float volume = (float) sec.getDouble("volume", 1.0D);
        float pitch = (float) sec.getDouble("pitch", 1.0D);
        boolean broadcast = sec.getBoolean("broadcast", true);

        if (broadcast) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.playSound(online.getLocation(), sound, volume, pitch);
            }
        } else if (initiator != null && initiator.isOnline()) {
            initiator.playSound(initiator.getLocation(), sound, volume, pitch);
        }
    }

    private static Sound resolve(String name) {
        String key = name.toUpperCase();
        Sound cached = CACHE.get(key);
        if (cached != null) return cached;
        if (CACHE.containsKey(key)) return null;

        Optional<XSound> xs = XSound.matchXSound(key);
        if (xs.isPresent()) {
            Sound parsed = xs.get().parseSound();
            if (parsed != null) {
                CACHE.put(key, parsed);
                return parsed;
            }
        }

        FactionsPlugin.getInstance().getLogger().log(Level.WARNING,
                "[SoundUtil] Unknown / unsupported sound on this server version: '" + name + "'. Skipping.");
        CACHE.put(key, null);
        return null;
    }
}
