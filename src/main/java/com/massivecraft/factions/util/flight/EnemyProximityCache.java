package com.massivecraft.factions.util.flight;

import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.FPlayer;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived cache for the {@code enemies nearby} decision.
 *
 * <p>The cached result is considered still valid when <b>both</b> conditions
 * are met:</p>
 * <ol>
 *     <li>The player is still in the same chunk ({@link FLocation#equals}).</li>
 *     <li>The last evaluation happened less than {@link #TTL_MS} ms ago.</li>
 * </ol>
 *
 * <p>This means a stationary player is checked at most every {@value #TTL_MS} ms
 * (≈ 15 ticks), while a moving player forces a re-check only when they cross
 * into a new chunk. Combined, the two conditions eliminate redundant scans
 * almost completely.</p>
 */
public final class EnemyProximityCache {

    private EnemyProximityCache() {}

    /** How long a successful detection stays authoritative. */
    public static final long TTL_MS = 750L;

    private static final class Entry {
        FLocation chunk;
        boolean enemiesNearby;
        long evaluatedAt;
    }

    private static final Map<UUID, Entry> CACHE = new ConcurrentHashMap<>();

    /**
     * Resolves the {@code enemiesNearby} status for the player, using the
     * cache when allowed and otherwise falling back to a live
     * {@link EnemyDetector#hasEnemyNearby scan}.
     *
     * @param me           the FPlayer driving the check
     * @param mePlayer     the Bukkit handle already resolved (no extra lookup)
     * @param currentChunk the chunk the player is currently in
     * @return {@code true} if an enemy is nearby
     */
    public static boolean resolve(FPlayer me, Player mePlayer, FLocation currentChunk) {
        if (mePlayer == null) return false;

        UUID uuid = mePlayer.getUniqueId();
        Entry e = CACHE.computeIfAbsent(uuid, k -> new Entry());

        long now = System.currentTimeMillis();
        boolean sameChunk = currentChunk != null && currentChunk.equals(e.chunk);
        boolean fresh     = (now - e.evaluatedAt) < TTL_MS;

        if (sameChunk && fresh) {
            return e.enemiesNearby;
        }

        boolean result = EnemyDetector.hasEnemyNearby(me, mePlayer);

        e.chunk = currentChunk;
        e.enemiesNearby = result;
        e.evaluatedAt = now;
        return result;
    }

    /** Drops the entry for the given player. Call on quit. */
    public static void invalidate(UUID uuid) {
        if (uuid != null) CACHE.remove(uuid);
    }
}

