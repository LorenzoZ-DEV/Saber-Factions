package com.massivecraft.factions.scoreboards.sidebar;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.scoreboards.FSidebarProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class FDefaultSidebar extends FSidebarProvider {

    @Override
    public String getTitle(FPlayer fplayer) {
        org.bukkit.configuration.file.FileConfiguration cfg =
                FactionsPlugin.getInstance().getConfig();

        String title;
        if (!fplayer.hasFaction() && cfg.getBoolean("scoreboard.factionless-enabled", true)) {
            title = cfg.getString("scoreboard.factionless-title",
                    cfg.getString("scoreboard.default-title"));
        } else {
            title = cfg.getString("scoreboard.default-title");
        }
        return replaceTags(fplayer, title);
    }

    @Override
    public List<String> getLines(FPlayer fplayer) {
        if (!fplayer.hasFaction() && FactionsPlugin.getInstance().getConfig()
                .getBoolean("scoreboard.factionless-enabled", true)) {
            return getOutput(fplayer, "scoreboard.factionless");
        }
        return getOutput(fplayer, "scoreboard.default");
    }

    private List<String> getOutput(FPlayer fplayer, String path) {
        List<String> template = FactionsPlugin.getInstance().getConfig().getStringList(path);
        if (template == null || template.isEmpty()) return new ArrayList<>(0);

        List<String> lines = new ArrayList<>(template);

        ListIterator<String> it = lines.listIterator();
        while (it.hasNext()) {
            it.set(replaceTags(fplayer, it.next()));
        }
        return lines;
    }
}