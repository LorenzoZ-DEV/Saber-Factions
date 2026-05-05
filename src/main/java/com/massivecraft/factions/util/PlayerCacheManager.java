package com.massivecraft.factions.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerCacheManager {

    private PlayerCacheManager() {}

    private static final Map<UUID, Player> CACHE = new ConcurrentHashMap<>();

    public static void addPlayer(Player player) {
        if (player == null) return;
        CACHE.put(player.getUniqueId(), player);
    }

    public static void removePlayer(UUID uuid) {
        if (uuid != null) CACHE.remove(uuid);
    }

    public static Player getPlayer(UUID uuid) {
        if (uuid == null) return null;
        Player cached = CACHE.get(uuid);
        if (cached != null) return cached;
        Player resolved = Bukkit.getPlayer(uuid);
        if (resolved != null) CACHE.put(uuid, resolved);
        return resolved;
    }

    public static Player getPlayerByName(String name) {
        if (name == null || name.isEmpty()) return null;
        for (Player p : CACHE.values()) {
            if (name.equalsIgnoreCase(p.getName())) return p;
        }
        return null;
    }

    public static Collection<Player> online() {
        return Collections.unmodifiableCollection(CACHE.values());
    }

    public static boolean isOnline(UUID uuid) {
        return uuid != null && CACHE.containsKey(uuid);
    }

    public static void clear() {
        CACHE.clear();
    }
}

