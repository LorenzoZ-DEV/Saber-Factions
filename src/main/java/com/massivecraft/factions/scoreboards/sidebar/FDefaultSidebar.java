package com.massivecraft.factions.scoreboards.sidebar;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.scoreboards.FSidebarProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FDefaultSidebar extends FSidebarProvider {

    // Cached config templates (read once per reload, not every tick)
    private static volatile List<String> cachedDefaultLines;
    private static volatile List<String> cachedFactionlessLines;
    private static volatile String cachedDefaultTitle;
    private static volatile String cachedFactionlessTitle;
    private static volatile boolean cachedFactionlessEnabled;
    private static volatile boolean loaded = false;

    /** Call from /f reload to refresh templates without restarting. */
    public static void invalidateCache() {
        loaded = false;
    }

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        org.bukkit.configuration.file.FileConfiguration cfg =
                FactionsPlugin.getInstance().getConfig();

        cachedFactionlessEnabled = cfg.getBoolean("scoreboard.factionless-enabled");
        cachedDefaultTitle = cfg.getString("scoreboard.default-title");
        cachedFactionlessTitle = cfg.getString("scoreboard.factionless-title", cachedDefaultTitle);

        List<String> def = cfg.getStringList("scoreboard.default");
        cachedDefaultLines = def != null ? Collections.unmodifiableList(new ArrayList<>(def)) : Collections.emptyList();

        List<String> fless = cfg.getStringList("scoreboard.factionless");
        cachedFactionlessLines = fless != null ? Collections.unmodifiableList(new ArrayList<>(fless)) : Collections.emptyList();

        loaded = true;
    }

    @Override
    public String getTitle(FPlayer fplayer) {
        ensureLoaded();
        String title = (!fplayer.hasFaction() && cachedFactionlessEnabled)
                ? cachedFactionlessTitle
                : cachedDefaultTitle;
        return replaceTags(fplayer, title);
    }

    @Override
    public List<String> getLines(FPlayer fplayer) {
        ensureLoaded();
        List<String> template = (!fplayer.hasFaction() && cachedFactionlessEnabled)
                ? cachedFactionlessLines
                : cachedDefaultLines;

        if (template.isEmpty()) return Collections.emptyList();

        int size = template.size();
        ArrayList<String> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            out.add(replaceTags(fplayer, template.get(i)));
        }
        return out;
    }
}