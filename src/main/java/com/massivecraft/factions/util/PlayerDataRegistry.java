package com.massivecraft.factions.util;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified in-memory registry that replaces {@code Player#setMetadata} / {@code Player#hasMetadata}
 * usage across the plugin. All operations are O(1), lock-free and UUID-keyed.
 *
 * <p>Three kinds of state are tracked:</p>
 * <ul>
 *     <li><b>Flags</b> — simple boolean set keyed by name.</li>
 *     <li><b>Timestamps</b> — expire-at millis (used for cooldowns and short-lived
 *         confirm flags, with automatic expiration by comparison).</li>
 *     <li><b>NPC cache</b> — a {@code Set<UUID>} populated once at join time; no
 *         further {@code hasMetadata} calls in hot paths.</li>
 * </ul>
 *
 * <p>Entries are cleared on player quit via {@link #clearAll(UUID)}.</p>
 */
public final class PlayerDataRegistry {

    private PlayerDataRegistry() {}

    // Flag names (centralised constants — avoid magic strings in callers).
    public static final String FLAG_SHOW_FACTION_TITLE = "showFactionTitle";
    public static final String FLAG_DIED_TO_PLAYER     = "diedToPlayer";
    public static final String FLAG_FRIENDLY_FIRE      = "friendlyFire";
    public static final String FLAG_LOGOUT             = "logout";
    public static final String FLAG_NO_ARMOR_SWAP      = "noArmorSwap";

    // Timestamp keys.
    public static final String TS_DISBAND_CONFIRM      = "disband_confirm";
    public static final String TS_LAST_ARMOR_SWAP      = "lastArmorSwap";
    public static final String TS_ANTILOOT_WARNING     = "antiLoot_nextWarning";

    private static final Map<UUID, Set<String>> FLAGS = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Long>> TIMESTAMPS = new ConcurrentHashMap<>();
    private static final Set<UUID> NPCS = ConcurrentHashMap.newKeySet();

    // ---------------- Flags ----------------

    public static void setFlag(UUID uuid, String name) {
        if (uuid == null) return;
        FLAGS.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(name);
    }

    public static void clearFlag(UUID uuid, String name) {
        if (uuid == null) return;
        Set<String> s = FLAGS.get(uuid);
        if (s != null) s.remove(name);
    }

    public static boolean hasFlag(UUID uuid, String name) {
        if (uuid == null) return false;
        Set<String> s = FLAGS.get(uuid);
        return s != null && s.contains(name);
    }

    public static boolean hasFlag(Player player, String name) {
        return player != null && hasFlag(player.getUniqueId(), name);
    }

    // ---------------- Timestamps (cooldowns / expiring confirms) ----------------

    /** Records an expire-at instant (epoch millis). */
    public static void setExpireAt(UUID uuid, String name, long expireAtMillis) {
        if (uuid == null) return;
        TIMESTAMPS.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(name, expireAtMillis);
    }

    public static long getExpireAt(UUID uuid, String name) {
        if (uuid == null) return 0L;
        Map<String, Long> m = TIMESTAMPS.get(uuid);
        if (m == null) return 0L;
        Long v = m.get(name);
        return v == null ? 0L : v;
    }

    /** @return {@code true} if the named entry exists and hasn't expired yet. */
    public static boolean hasActive(UUID uuid, String name) {
        return getExpireAt(uuid, name) > System.currentTimeMillis();
    }

    public static boolean hasActive(Player player, String name) {
        return player != null && hasActive(player.getUniqueId(), name);
    }

    /** @return remaining millis, or a value {@code <= 0} if expired/absent. */
    public static long getRemaining(UUID uuid, String name) {
        return getExpireAt(uuid, name) - System.currentTimeMillis();
    }

    public static void clearExpire(UUID uuid, String name) {
        if (uuid == null) return;
        Map<String, Long> m = TIMESTAMPS.get(uuid);
        if (m != null) m.remove(name);
    }

    // ---------------- NPC cache ----------------

    public static void markNpc(UUID uuid) {
        if (uuid != null) NPCS.add(uuid);
    }

    public static boolean isNpc(UUID uuid) {
        return uuid != null && NPCS.contains(uuid);
    }

    public static boolean isNpc(Player player) {
        return player != null && NPCS.contains(player.getUniqueId());
    }

    // ---------------- Housekeeping ----------------

    /** Wipes every entry for a player. Call on quit. */
    public static void clearAll(UUID uuid) {
        if (uuid == null) return;
        FLAGS.remove(uuid);
        TIMESTAMPS.remove(uuid);
        NPCS.remove(uuid);
    }
}

