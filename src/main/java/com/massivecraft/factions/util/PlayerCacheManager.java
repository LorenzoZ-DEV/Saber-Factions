package com.massivecraft.factions.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central UUID → {@link Player} lookup table, updated on
 * {@code PlayerJoinEvent} and {@code PlayerQuitEvent}. Replaces the large
 * majority of {@link Bukkit#getPlayer(UUID)} calls in hot paths.
 *
 * <p>Why: {@code Bukkit.getPlayer(UUID)} walks the player list and is cheaper
 * than the name-based variant but still more expensive than a hash-map lookup,
 * especially on forks that synchronise on the online-player list. A dedicated
 * {@link ConcurrentHashMap} is O(1), lock-free and can safely be read from
 * async contexts.</p>
 */
public final class PlayerCacheManager {

    private PlayerCacheManager() {}

    private static final Map<UUID, Player> CACHE = new ConcurrentHashMap<>();

    /**
     * Registers a player. Call on {@code PlayerJoinEvent}.
     * No-op if {@code player} is {@code null}.
     */
    public static void addPlayer(Player player) {
        if (player == null) return;
        CACHE.put(player.getUniqueId(), player);
    }

    /** Removes a player. Call on {@code PlayerQuitEvent}. */
    public static void removePlayer(UUID uuid) {
        if (uuid != null) CACHE.remove(uuid);
    }

    /** @return the cached {@link Player}, or {@code null} if offline. */
    public static Player getPlayer(UUID uuid) {
        if (uuid == null) return null;
        Player cached = CACHE.get(uuid);
        if (cached != null) return cached;
        // Fallback for races during join/startup: consult Bukkit and back-fill
        // the cache so subsequent calls are free.
        Player resolved = Bukkit.getPlayer(uuid);
        if (resolved != null) CACHE.put(uuid, resolved);
        return resolved;
    }

    /**
     * Name-based variant. Prefer {@link #getPlayer(UUID)} whenever possible —
     * this one iterates the cache.
     */
    public static Player getPlayerByName(String name) {
        if (name == null || name.isEmpty()) return null;
        for (Player p : CACHE.values()) {
            if (name.equalsIgnoreCase(p.getName())) return p;
        }
        return null;
    }

    /** @return an unmodifiable snapshot view of all currently online players. */
    public static Collection<Player> online() {
        return Collections.unmodifiableCollection(CACHE.values());
    }

    /** @return {@code true} if the UUID has an online {@link Player} in cache. */
    public static boolean isOnline(UUID uuid) {
        return uuid != null && CACHE.containsKey(uuid);
    }

    /** Wipes the cache. Only intended for plugin disable / full reload. */
    public static void clear() {
        CACHE.clear();
    }
}

