package com.massivecraft.factions.util.flight;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.event.FPlayerStoppedFlying;
import com.massivecraft.factions.zcore.util.TL;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Side-effects wrapper for flight state transitions. Kept separate from
 * {@link EnemyDetector} so detection is pure/testable and this module owns the
 * Bukkit-interaction side.
 */
public final class FlightEffects {

    private FlightEffects() {}

    /**
     * Disables flight on the player because enemies were detected nearby.
     * Emits {@code TL.COMMAND_FLY_ENEMY_NEAR} and fires
     * {@link FPlayerStoppedFlying}. No-op if the player isn't actually flying.
     */
    public static void stopForEnemyNearby(FPlayer me, Player mePlayer) {
        if (me == null || mePlayer == null) return;
        if (!mePlayer.isFlying()) return;

        me.setFlying(false);
        me.msg(TL.COMMAND_FLY_ENEMY_NEAR);
        Bukkit.getPluginManager().callEvent(new FPlayerStoppedFlying(me));
    }
}

