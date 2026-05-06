package com.massivecraft.factions.scoreboards;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.event.FPlayerEnteredFactionEvent;
import com.massivecraft.factions.event.FPlayerJoinEvent;
import com.massivecraft.factions.event.FPlayerLeaveEvent;
import com.massivecraft.factions.event.FPlayerRoleChangeEvent;
import com.massivecraft.factions.event.FactionDisbandEvent;
import com.massivecraft.factions.event.FactionRelationEvent;
import com.massivecraft.factions.event.FactionRenameEvent;
import com.massivecraft.factions.event.LandClaimEvent;
import com.massivecraft.factions.event.LandUnclaimAllEvent;
import com.massivecraft.factions.event.LandUnclaimEvent;
import com.massivecraft.factions.event.PowerLossEvent;
import com.massivecraft.factions.event.PowerRegenEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class FScoreboardListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        FScoreboard.updateAll();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        FScoreboard.updateAll();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFactionJoin(FPlayerJoinEvent event) {
        FScoreboard.updateForFaction(event.getFaction());
        FScoreboard.update(event.getfPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFactionLeave(FPlayerLeaveEvent event) {
        FScoreboard.updateForFaction(event.getFaction());
        FScoreboard.update(event.getfPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLandClaim(LandClaimEvent event) {
        FScoreboard.updateForFaction(event.getFaction());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLandUnclaim(LandUnclaimEvent event) {
        FScoreboard.updateForFaction(event.getFaction());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLandUnclaimAll(LandUnclaimAllEvent event) {
        FScoreboard.updateForFaction(event.getFaction());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPowerLoss(PowerLossEvent event) {
        FScoreboard.updateForFaction(event.getFaction());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPowerRegen(PowerRegenEvent event) {
        FPlayer fp = event.getfPlayer();
        if (fp != null) {
            FScoreboard.update(fp);
            if (fp.hasFaction()) FScoreboard.updateForFaction(fp.getFaction());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRelation(FactionRelationEvent event) {
        FScoreboard.updateForFaction(event.getFaction());
        FScoreboard.updateForFaction(event.getTargetFaction());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRename(FactionRenameEvent event) {
        FScoreboard.updateForFaction(event.getFaction());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDisband(FactionDisbandEvent event) {
        FScoreboard.updateForFaction(event.getFaction());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRoleChange(FPlayerRoleChangeEvent event) {
        FPlayer fp = event.getfPlayer();
        if (fp != null) {
            FScoreboard.update(fp);
            if (fp.hasFaction()) FScoreboard.updateForFaction(fp.getFaction());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTerritoryChange(FPlayerEnteredFactionEvent event) {
        FScoreboard.update(event.getfPlayer());
    }
}

