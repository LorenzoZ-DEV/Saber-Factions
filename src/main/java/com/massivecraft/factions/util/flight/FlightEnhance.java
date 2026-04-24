package com.massivecraft.factions.util.flight;

import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.listeners.FactionsEntityListener;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/**
 * SaberFactions - Developed by Driftay.
 * All rights reserved 2020.
 * Creation Date: 9/15/2020
 */
public class FlightEnhance implements Runnable {

        // Cache of the ffly.AutoEnable flag — refreshed lazily every 20 s so the
    // scheduler body never hits the YAML in hot path.
    private static volatile long autoEnableCachedAt = 0L;
    private static volatile boolean autoEnableCached = false;

    private static boolean isAutoEnable() {
        long now = System.currentTimeMillis();
        if (now - autoEnableCachedAt > 20_000L) {
            autoEnableCached = FactionsPlugin.getInstance().getConfig().getBoolean("ffly.AutoEnable");
            autoEnableCachedAt = now;
        }
        return autoEnableCached;
    }

    @Override
    public void run() {
        // Evaluate once per tick, not once per player.
        boolean autoEnable = isAutoEnable();

        for (FPlayer player : FPlayers.getInstance().getOnlinePlayers()) {
            if (shouldSkipPlayer(player)) continue;

            // Use lastStoodAt (set by the move listener) instead of allocating
            // a fresh Location + FLocation on every tick.
            FLocation fLocation = player.getLastStoodAt();
            player.checkIfNearbyEnemies();

            if (!player.hasEnemiesNearby()) {
                handleFlightStatusForPlayer(player, fLocation, autoEnable);
            }
        }
    }

    private boolean shouldSkipPlayer(FPlayer player) {
        Player p = player.getPlayer();
        if (p == null) return true;
        if (player.isAdminBypassing()) return true;
        if (p.isOp()) return true;
        GameMode gm = p.getGameMode();
        return gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR;
    }

    private void handleFlightStatusForPlayer(FPlayer player, FLocation fLocation, boolean autoEnable) {
        boolean flying = player.isFlying();
        // canFlyAtLocation is now cached per-chunk; this is the happy path.
        boolean canFly = player.canFlyAtLocation(fLocation);

        if (flying && !canFly) {
            player.setFlying(false, false);
            return;
        }

        if (!flying && canFly && autoEnable
                && !FactionsEntityListener.combatList.contains(player.getPlayer().getUniqueId())) {
            player.setFlying(true);
        }
    }
}