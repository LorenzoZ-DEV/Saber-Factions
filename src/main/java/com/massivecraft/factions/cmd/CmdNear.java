package com.massivecraft.factions.cmd;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.zcore.util.TL;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class CmdNear extends FCommand {

    /**
     * @author FactionsUUID Team - Modified By CmdrKittens
     */

    public CmdNear() {
        super();
        this.getAliases().addAll(Aliases.near);

        this.setRequirements(new CommandRequirements.Builder(Permission.NEAR)
                .playerOnly()
                .memberOnly()
                .build());
    }

    @Override
    public void perform(CommandContext context) {
        if (!FactionsPlugin.getInstance().getConfig().getBoolean("fnear.Enabled")) {
            context.msg(TL.COMMAND_NEAR_DISABLED_MSG);
            return;
        }

        double range = FactionsPlugin.getInstance().getConfig().getInt("fnear.Radius");
        String format = TL.COMMAND_NEAR_FORMAT.toString();
        context.msg(TL.COMMAND_NEAR_USE_MSG);

        // Reuse a single Location reference instead of reallocating inside the loop.
        org.bukkit.Location myLoc = context.player.getLocation();
        for (Entity e : context.player.getNearbyEntities(range, 255, range)) {
            if (!(e instanceof Player)) continue;
            Player player = (Player) e;
            if (com.massivecraft.factions.util.PlayerDataRegistry.isNpc(player)) continue;
            FPlayer fplayer = FPlayers.getInstance().getByPlayer(player);
            if (context.faction != fplayer.getFaction()) continue;

            double distance = player.getLocation().distance(myLoc);
            context.sendMessage(format.replace("{playername}", player.getDisplayName()).replace("{distance}", (int) distance + ""));
        }
    }


    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_NEAR_DESCRIPTION;
    }
}
