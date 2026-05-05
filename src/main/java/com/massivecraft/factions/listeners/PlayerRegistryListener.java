package com.massivecraft.factions.listeners;

import com.massivecraft.factions.util.PlayerCacheManager;
import com.massivecraft.factions.util.PlayerDataRegistry;
import com.massivecraft.factions.util.flight.EnemyProximityCache;
import com.massivecraft.factions.util.flight.FlightCache;
import com.massivecraft.factions.util.flight.FlightEnhance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Keeps {@link PlayerDataRegistry} in sync with player sessions:
 * <ul>
 *     <li>On join, reads Citizens' {@code NPC} metadata <i>once</i> and caches
 *         the UUID so no hot path ever has to call {@code hasMetadata} again.</li>
 *     <li>On quit, drops every entry belonging to the player.</li>
 * </ul>
 */
public final class PlayerRegistryListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        // Warm up the central Player cache first so every other listener sees
        // an O(1) Bukkit.getPlayer(uuid) replacement.
        PlayerCacheManager.addPlayer(p);

        // One and only hasMetadata call per session. Citizens populates this
        // tag before join fires, so the check is reliable here.
        if (p.hasMetadata("NPC")) {
            PlayerDataRegistry.markNpc(p.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        java.util.UUID uuid = event.getPlayer().getUniqueId();
        PlayerDataRegistry.clearAll(uuid);
        FlightCache.invalidate(uuid);
        EnemyProximityCache.invalidate(uuid);
        FlightEnhance.invalidate(uuid);
        PlayerCacheManager.removePlayer(uuid);
    }
}

