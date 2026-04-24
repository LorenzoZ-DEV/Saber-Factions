package com.massivecraft.factions.util;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.Faction;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Cooldown handling backed by {@link PlayerDataRegistry} — replaces the former
 * {@code setMetadata}/{@code getMetadata} implementation. No per-call allocations.
 * <p>Factions - Developed by Driftay.</p>
 */
public class Cooldown {

    private static final long MILLIS_IN_SECOND = TimeUnit.SECONDS.toMillis(1);

    public static void setCooldown(Player player, String name, int seconds) {
        if (player == null) return;
        PlayerDataRegistry.setExpireAt(player.getUniqueId(), name,
                System.currentTimeMillis() + seconds * MILLIS_IN_SECOND);
    }

    public static void setCooldown(Faction fac, String name, int seconds) {
        long expiration = System.currentTimeMillis() + seconds * MILLIS_IN_SECOND;
        if (fac instanceof com.massivecraft.factions.zcore.persist.MemoryFaction) {
            // Zero-allocation path — no intermediate HashSet.
            ((com.massivecraft.factions.zcore.persist.MemoryFaction) fac).forEachOnline(fp -> {
                Player p = fp.getPlayer();
                if (p != null) PlayerDataRegistry.setExpireAt(p.getUniqueId(), name, expiration);
            });
            return;
        }
        for (FPlayer fPlayer : fac.getFPlayersWhereOnline(true)) {
            Player player = fPlayer.getPlayer();
            if (player == null) continue;
            PlayerDataRegistry.setExpireAt(player.getUniqueId(), name, expiration);
        }
    }

    public static String sendCooldownLeft(Player player, String name) {
        if (player == null) return "";
        long remaining = PlayerDataRegistry.getRemaining(player.getUniqueId(), name);
        if (remaining <= 0L) return "";
        return TimeUtil.formatSeconds((int) (remaining / MILLIS_IN_SECOND));
    }

    public static boolean isOnCooldown(Player player, String name) {
        return player != null && PlayerDataRegistry.hasActive(player.getUniqueId(), name);
    }

    /** @return remaining millis (may be {@code <= 0} when absent/expired). */
    public static long getRemaining(Player player, String name) {
        return player == null ? 0L : PlayerDataRegistry.getRemaining(player.getUniqueId(), name);
    }

    /** @return remaining millis for a raw UUID. */
    public static long getRemaining(UUID uuid, String name) {
        return PlayerDataRegistry.getRemaining(uuid, name);
    }
}