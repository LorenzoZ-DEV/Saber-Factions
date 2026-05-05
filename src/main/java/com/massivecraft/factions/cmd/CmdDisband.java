package com.massivecraft.factions.cmd;

import com.massivecraft.factions.*;
import com.massivecraft.factions.event.FactionDisbandEvent.PlayerDisbandReason;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.struct.Role;
import com.massivecraft.factions.util.ChunkReference;
import com.massivecraft.factions.util.Cooldown;
import com.massivecraft.factions.util.FastChunk;
import com.massivecraft.factions.util.PlayerDataRegistry;
import com.massivecraft.factions.zcore.fperms.Access;
import com.massivecraft.factions.zcore.fperms.PermissableAction;
import com.massivecraft.factions.zcore.frame.fdisband.FDisbandFrame;
import com.massivecraft.factions.zcore.util.TL;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;


public class CmdDisband extends FCommand {

    public CmdDisband() {
        super();
        this.getAliases().addAll(Aliases.disband);
        this.getOptionalArgs().put("faction tag", "yours");
        this.setRequirements(new CommandRequirements.Builder(Permission.DISBAND)
                .build());
    }

    @Override
    public void perform(CommandContext context) {
        Faction faction = context.argAsFaction(0, context.fPlayer == null ? null : context.faction);
        if (faction == null) return;

        boolean isMyFaction = context.fPlayer != null && faction == context.faction;

        if (!isMyFaction && !Permission.DISBAND_ANY.has(context.sender, true)) return;

        if (context.fPlayer != null && !context.fPlayer.isAdminBypassing() && !hasDisbandPermission(context, faction)) {
            context.msg(TL.GENERIC_FPERM_NOPERMISSION, "disband " + faction.getTag());
            return;
        }

        if (!faction.isNormal()) {
            context.msg(TL.COMMAND_DISBAND_IMMUTABLE.toString());
            return;
        }
        if (faction.isPermanent()) {
            context.msg(TL.COMMAND_DISBAND_MARKEDPERMANENT.toString());
            return;
        }

        if(Conf.userSpawnerChunkSystem && !Conf.allowUnclaimSpawnerChunksWithSpawnersInChunk) {
            FastChunk.preloadAll(faction.getSpawnerChunks()).thenRun(() ->
                Bukkit.getScheduler().runTask(FactionsPlugin.getInstance(), () -> {
                    for (FastChunk fastChunk : faction.getSpawnerChunks()) {
                        if (ChunkReference.getSpawnerCount(fastChunk.getChunk()) > 0) {
                            context.msg(TL.COMMAND_DISBAND_SPAWNERS_SPAWNER_CHUNKS_FOUND.toString().replace("{faction}", faction.getTag()));
                            return;
                        }
                    }
                    finalizeDisband(context, faction);
                })
            );
            return;
        }

        finalizeDisband(context, faction);
    }

    private void finalizeDisband(CommandContext context, Faction faction) {
        if (context.player == null) {
            faction.disband(null, PlayerDisbandReason.PLUGIN);
            return;
        }

        if (Cooldown.isOnCooldown(context.fPlayer.getPlayer(), "disbandCooldown") && !context.fPlayer.isAdminBypassing()) {
            context.msg(TL.COMMAND_COOLDOWN);
            return;
        }

        boolean confirmEnabled = FactionsPlugin.getInstance().getConfig().getBoolean("f-disband-gui.enabled", true);
        boolean bypass = context.fPlayer.isAdminBypassing();

        if (confirmEnabled && !bypass) {
            boolean confirmed = PlayerDataRegistry.hasActive(
                    context.player.getUniqueId(),
                    PlayerDataRegistry.TS_DISBAND_CONFIRM);
            if (!confirmed) {
                new FDisbandFrame(context.player).openGUI(FactionsPlugin.getInstance());
                return;
            }

            PlayerDataRegistry.setExpireAt(
                    context.player.getUniqueId(),
                    PlayerDataRegistry.TS_DISBAND_CONFIRM,
                    0L);
        }

        broadcastDisband(context, faction);
        faction.disband(context.player, PlayerDisbandReason.COMMAND);
        Cooldown.setCooldown(context.fPlayer.getPlayer(), "disbandCooldown", FactionsPlugin.getInstance().getConfig().getInt("fcooldowns.f-disband"));
    }

    private boolean hasDisbandPermission(CommandContext context, Faction faction) {
        Access access = faction.getAccess(context.fPlayer, PermissableAction.DISBAND);
        return context.fPlayer.getRole() == Role.LEADER || faction.getFPlayerLeader() == context.fPlayer || access == Access.ALLOW;
    }


    private void broadcastDisband(CommandContext context, Faction faction) {
        if (FactionsPlugin.getInstance().getConfig().getBoolean("faction-disband-broadcast", true)) {

            String yours_message = TL.COMMAND_DISBAND_BROADCAST_YOURS.toString().replace("{claims}", String.valueOf(faction.getAllClaims().size()));
            String notyours_message = TL.COMMAND_DISBAND_BROADCAST_NOTYOURS.toString().replace("{claims}", String.valueOf(faction.getAllClaims().size()));
            String amountString = context.sender instanceof ConsoleCommandSender ? TL.GENERIC_SERVERADMIN.toString() : context.fPlayer.describeTo(null);

            if (yours_message.contains("{player}") || notyours_message.contains("{player}")) {
                amountString = context.sender instanceof ConsoleCommandSender ? TL.GENERIC_SERVERADMIN.toString() : context.fPlayer.getName();
            }
            for (FPlayer follower : FPlayers.getInstance().getOnlinePlayers()) {
                if (follower.getFaction() == faction) {
                    follower.msg(yours_message, amountString);
                } else {
                    follower.msg(notyours_message, amountString, faction.getTag(follower));
                }
            }
        } else {
            context.player.sendMessage(String.valueOf(TL.COMMAND_DISBAND_PLAYER));
        }
    }


    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_DISBAND_DESCRIPTION;
    }
}
