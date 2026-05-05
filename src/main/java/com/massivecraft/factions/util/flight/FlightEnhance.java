package com.massivecraft.factions.util.flight;

import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.listeners.FactionsEntityListener;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FlightEnhance implements Runnable {

        private static volatile long autoEnableCachedAt = 0L;
    private static volatile boolean autoEnableCached = false;

    private static volatile long enemyCheckCachedAt = 0L;
    private static volatile long enemyCheckIntervalMs = 1000L;

    private static final Map<UUID, Long> NEXT_ENEMY_SCAN = new ConcurrentHashMap<>();

    private static boolean isAutoEnable() {
        long now = System.currentTimeMillis();
        if (now - autoEnableCachedAt > 20_000L) {
            autoEnableCached = FactionsPlugin.getInstance().getConfig().getBoolean("ffly.AutoEnable");
            autoEnableCachedAt = now;
        }
        return autoEnableCached;
    }

    private static long getEnemyCheckIntervalMs() {
        long now = System.currentTimeMillis();
        if (now - enemyCheckCachedAt > 20_000L) {
            int seconds = FactionsPlugin.getInstance().getConfig().getInt("ffly.enemy-radius-check", 1);
            enemyCheckIntervalMs = seconds <= 0 ? 0L : seconds * 1000L;
            enemyCheckCachedAt = now;
        }
        return enemyCheckIntervalMs;
    }

    public static void invalidate(UUID uuid) {
        if (uuid != null) NEXT_ENEMY_SCAN.remove(uuid);
    }

    @Override
    public void run() {
        boolean autoEnable = isAutoEnable();
        long intervalMs = getEnemyCheckIntervalMs();
        long now = System.currentTimeMillis();

        for (FPlayer player : FPlayers.getInstance().getOnlinePlayers()) {
            if (shouldSkipPlayer(player)) continue;

            FLocation fLocation = player.getLastStoodAt();
            boolean flying = player.isFlying();

            if (flying && intervalMs > 0) {
                UUID uuid = player.getPlayer().getUniqueId();
                Long nextAllowed = NEXT_ENEMY_SCAN.get(uuid);
                if (nextAllowed == null || now >= nextAllowed) {
                    player.checkIfNearbyEnemies();
                    NEXT_ENEMY_SCAN.put(uuid, now + intervalMs);
                }
                if (player.hasEnemiesNearby()) continue;
            }

            handleFlightStatusForPlayer(player, fLocation, flying, autoEnable);
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

    private void handleFlightStatusForPlayer(FPlayer player, FLocation fLocation, boolean flying, boolean autoEnable) {
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