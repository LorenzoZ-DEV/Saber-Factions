package com.massivecraft.factions.scoreboards;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.tag.Tag;
import com.massivecraft.factions.zcore.util.TL;
import com.massivecraft.factions.zcore.util.TextUtil;

import java.util.List;

public abstract class FSidebarProvider {

    /**
     * @author FactionsUUID Team - Modified By onlynelchilling
     */

    public abstract String getTitle(FPlayer fplayer);

    public abstract List<String> getLines(FPlayer fplayer);

    public String replaceTags(FPlayer fPlayer, String s) {
        if (s == null || s.isEmpty()) return s;
        boolean hasBrace = s.indexOf('{') >= 0;
        boolean hasPct = s.indexOf('%') >= 0;
        if (!hasBrace && !hasPct) {
            // No placeholders: only color translation needed.
            return TextUtil.parse(s);
        }
        if (hasPct) s = Tag.parsePlaceholders(fPlayer.getPlayer(), s);
        if (hasBrace) s = Tag.parsePlain(fPlayer, s);
        return qualityAssure(s);
    }

    public String replaceTags(Faction faction, FPlayer fPlayer, String s) {
        if (s == null || s.isEmpty()) return s;
        boolean hasBrace = s.indexOf('{') >= 0;
        boolean hasPct = s.indexOf('%') >= 0;
        if (!hasBrace && !hasPct) {
            return TextUtil.parse(s);
        }
        if (hasPct) s = Tag.parsePlaceholders(fPlayer.getPlayer(), s);
        if (hasBrace) s = Tag.parsePlain(faction, fPlayer, s);
        return qualityAssure(s);
    }

    private String qualityAssure(String line) {
        // qualityAssure is now only called when placeholders existed; cheap checks.
        if (line.indexOf('{') >= 0) {
            if (line.contains("{notFrozen}") || line.contains("{notPermanent}")) {
                return "n/a";
            }
            if (line.contains("{ig}")) {
                return TL.COMMAND_SHOW_NOHOME.toString();
            }
        }
        return TextUtil.parse(line);
    }
}