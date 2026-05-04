package com.massivecraft.factions.cmd;

import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.cmd.ftop.FTopGUI;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.zcore.util.TL;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class CmdTop extends FCommand {

    /**
     * @author FactionsUUID Team - Modified By CmdrKittens
     * 
     */

    private static final Set<String> VALID_CRITERIA = new HashSet<>(Arrays.asList(
            "members", "start", "power", "land", "online", "money"
    ));

    public CmdTop() {
        super();
        this.getAliases().addAll(Aliases.top);
        this.getRequiredArgs().add("criteria");
        this.getOptionalArgs().put("page", "1");

        this.setRequirements(new CommandRequirements.Builder(Permission.TOP)
                .build());
    }


    @Override
    public void perform(CommandContext context) {
        if (!(context.sender instanceof Player)) {
            context.msg(TL.GENERIC_PLAYERONLY);
            return;
        }

        String criteria = context.argAsString(0).toLowerCase();
        if (!VALID_CRITERIA.contains(criteria)) {
            context.msg(TL.COMMAND_TOP_INVALID, criteria);
            return;
        }

        int page = Math.max(1, context.argAsInt(1, 1));
        Player player = (Player) context.sender;
        FTopGUI gui = new FTopGUI(player, criteria, page);
        gui.openGUI(FactionsPlugin.getInstance());
    }

    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_TOP_DESCRIPTION;
    }
}
