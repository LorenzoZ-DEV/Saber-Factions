package com.massivecraft.factions.util.flight;

import com.massivecraft.factions.Conf;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.struct.Relation;
import com.massivecraft.factions.util.PlayerDataRegistry;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Pure, side-effect-free detector that answers "is there an enemy close enough
 * that this player should not be flying?". Extracted from
 * {@code MemoryFPlayer#checkIfNearbyEnemies} so it can be unit-tested and
 * reused.
 *
 * <p>Implementation notes:</p>
 * <ul>
 *     <li>Uses {@link Player#getNearbyEntities(double, double, double)} and
 *         <b>never</b> iterates every online player. The search radius is
 *         clamped to [{@value #MIN_RADIUS}, {@value #MAX_RADIUS}] blocks so a
 *         misconfigured {@code Conf.stealthFlyCheckRadius} can't silently
 *         turn this into an O(world) scan.</li>
 *     <li>Reuses a single {@link Location} object for the distance check to
 *         avoid per-iteration allocations.</li>
 *     <li>Consults {@link PlayerDataRegistry} to skip NPCs without ever
 *         invoking {@code Entity#hasMetadata}.</li>
 * </ul>
 */
public final class EnemyDetector {

    private EnemyDetector() {}

    /** Hard lower bound for the proximity radius. */
    public static final int MIN_RADIUS = 16;
    /** Hard upper bound for the proximity radius. */
    public static final int MAX_RADIUS = 32;

    /**
     * @return {@code true} if the player has an enemy inside the clamped
     *         stealth-fly radius (and can see them).
     */
    public static boolean hasEnemyNearby(FPlayer me, Player mePlayer) {
        if (me == null || mePlayer == null) return false;

        int radius = clamp(Conf.stealthFlyCheckRadius, MIN_RADIUS, MAX_RADIUS);
        int r2 = radius * radius;

        Location myLoc = mePlayer.getLocation();

        for (Entity entity : mePlayer.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Player)) continue;
            Player other = (Player) entity;
            if (other == mePlayer) continue;
            if (PlayerDataRegistry.isNpc(other)) continue;
            if (!mePlayer.canSee(other)) continue;

            // Spherical distance (getNearbyEntities only constrains the box).
            if (other.getLocation().distanceSquared(myLoc) > r2) continue;

            FPlayer efp = FPlayers.getInstance().getByPlayer(other);
            if (efp == null || efp.isStealthEnabled()) continue;

            if (me.getRelationTo(efp) == Relation.ENEMY) {
                return true;
            }
        }
        return false;
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }
}

