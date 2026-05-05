package com.massivecraft.factions.cmd.ftop;

import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.cmd.Aliases;
import com.massivecraft.factions.cmd.CommandContext;
import com.massivecraft.factions.cmd.CommandRequirements;
import com.massivecraft.factions.cmd.FCommand;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.zcore.util.TL;
import org.bukkit.entity.Player;

public class CmdFTop extends FCommand {

    public CmdFTop() {
        super();
        this.getAliases().addAll(Aliases.ftop);

        this.setRequirements(new CommandRequirements.Builder(Permission.TOP)
                .build());
    }

    @Override
    public void perform(CommandContext context) {
        if (!(context.sender instanceof Player)) {
            context.msg(TL.GENERIC_PLAYERONLY);
            return;
        }

        Player player = (Player) context.sender;
        new FTopGUI(player).openGUI(FactionsPlugin.getInstance());
    }

    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_TOP_DESCRIPTION;
    }
}