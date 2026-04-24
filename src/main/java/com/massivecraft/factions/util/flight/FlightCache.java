package com.massivecraft.factions.util.flight;

import com.massivecraft.factions.Board;
import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.struct.Relation;
import com.massivecraft.factions.zcore.fperms.Access;
import com.massivecraft.factions.zcore.fperms.PermissableAction;
import com.massivecraft.factions.zcore.persist.MemoryFPlayer;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player cache for flight permission evaluation. Replaces the fully
 * recomputed {@code canFlyAtLocation} path (which did 7×{@code hasPermission}
 * + 1×{@code getFactionAt} on every call) with a chunk-scoped cache.
 *
 * <p>Caches:</p>
 * <ul>
 *     <li>Last chunk {@link FLocation} evaluated.</li>
 *     <li>Last {@code canFly} result for that chunk.</li>
 *     <li>Last faction id seen at that chunk (lets us short-circuit when the
 *         player moves across chunks that belong to the same faction without
 *         redoing the full permission dance).</li>
 *     <li>Every {@code Permission.FLY_*} boolean, refreshed at most every
 *         {@link #PERMS_TTL_MS} ms (5 s by default). This is the single
 *         biggest win, because most permission plugins make {@code hasPermission}
 *         quite expensive under contention.</li>
 * </ul>
 *
 * <p>Entries are cleared on player quit via {@code PlayerRegistryListener}.</p>
 */
public final class FlightCache {

    private FlightCache() {}

    /** Max age of the cached {@code hasPermission} snapshot. */
    private static final long PERMS_TTL_MS = 5_000L;

    private static final class Entry {
        FLocation chunk;        // last chunk we computed for (null until first eval)
        String factionId;       // faction that owned that chunk (optional hint)
        boolean canFly;         // last computed result for that chunk

        long permsRefreshedAt;  // epoch ms
        boolean pFlyWilderness, pFlySafezone, pFlyWarzone;
        boolean pFlyEnemy, pFlyAlly, pFlyTruce, pFlyNeutral;
    }

    private static final Map<UUID, Entry> CACHE = new ConcurrentHashMap<>();

    /**
     * Returns whether the player can fly at the given chunk location. The
     * result is cached until either the player moves to a different chunk
     * <i>or</i> the faction id at that chunk changes (detected by the caller
     * via {@link #invalidate(UUID)} on faction events).
     */
    public static boolean canFlyAt(MemoryFPlayer fp, FLocation loc) {
        if (fp == null || loc == null) return false;
        Player p = fp.getPlayer();
        if (p == null) return false;

        Entry e = CACHE.computeIfAbsent(p.getUniqueId(), k -> new Entry());
        refreshPermsIfStale(p, e);

        // Same chunk as last evaluation → reuse the cached answer.
        if (loc.equals(e.chunk)) {
            return e.canFly;
        }

        Faction faction = Board.getInstance().getFactionAt(loc);
        boolean result = compute(fp, faction, e);

        e.chunk = loc;
        e.factionId = faction.getId();
        e.canFly = result;
        return result;
    }

    private static void refreshPermsIfStale(Player p, Entry e) {
        long now = System.currentTimeMillis();
        if (now - e.permsRefreshedAt <= PERMS_TTL_MS) return;
        e.pFlyWilderness = Permission.FLY_WILDERNESS.has(p);
        e.pFlySafezone   = Permission.FLY_SAFEZONE.has(p);
        e.pFlyWarzone    = Permission.FLY_WARZONE.has(p);
        e.pFlyEnemy      = Permission.FLY_ENEMY.has(p);
        e.pFlyAlly       = Permission.FLY_ALLY.has(p);
        e.pFlyTruce      = Permission.FLY_TRUCE.has(p);
        e.pFlyNeutral    = Permission.FLY_NEUTRAL.has(p);
        e.permsRefreshedAt = now;
    }

    private static boolean compute(MemoryFPlayer fp, Faction faction, Entry e) {
        if (faction.isWilderness()) return e.pFlyWilderness;
        if (faction.isSafeZone())   return e.pFlySafezone;
        if (faction.isWarZone())    return e.pFlyWarzone;

        Relation rel = faction.getRelationTo(fp.getFaction());
        if (rel == Relation.ENEMY   && e.pFlyEnemy)   return true;
        if (rel == Relation.ALLY    && e.pFlyAlly)    return true;
        if (rel == Relation.TRUCE   && e.pFlyTruce)   return true;
        if (rel == Relation.NEUTRAL && !faction.isSystemFaction() && e.pFlyNeutral) return true;

        if (fp.isAdminBypassing()) return true;

        return faction.getAccess(fp, PermissableAction.FLY) == Access.ALLOW;
    }

    /** Drops the entry for the given player. Call on quit and on faction changes. */
    public static void invalidate(UUID uuid) {
        if (uuid != null) CACHE.remove(uuid);
    }

    /** Invalidates just the chunk result, keeping the cached perm snapshot. */
    public static void invalidateChunk(UUID uuid) {
        if (uuid == null) return;
        Entry e = CACHE.get(uuid);
        if (e != null) e.chunk = null;
    }
}

