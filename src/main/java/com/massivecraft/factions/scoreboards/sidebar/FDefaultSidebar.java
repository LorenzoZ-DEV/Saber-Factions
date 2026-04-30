package com.massivecraft.factions.scoreboards.sidebar;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.scoreboards.FSidebarProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

public class FDefaultSidebar extends FSidebarProvider {

    private static volatile long cachedAt = 0L;
    private static volatile String cachedTitle = "{name}";
    private static volatile List<String> cachedDefault = Collections.emptyList();
    private static volatile List<String> cachedFactionless = Collections.emptyList();
    private static volatile boolean cachedFactionlessEnabled = false;

    private static void refreshCacheIfStale() {
        long now = System.currentTimeMillis();
        if (now - cachedAt > 20_000L) {
            cachedTitle = FactionsPlugin.getInstance().getConfig()
                    .getString("scoreboard.default-title", "{name}");
            cachedDefault = Collections.unmodifiableList(
                    FactionsPlugin.getInstance().getConfig().getStringList("scoreboard.default"));
            cachedFactionless = Collections.unmodifiableList(
                    FactionsPlugin.getInstance().getConfig().getStringList("scoreboard.factionless"));
            cachedFactionlessEnabled = FactionsPlugin.getInstance().getConfig()
                    .getBoolean("scoreboard.factionless-enabled", false);
            cachedAt = now;
        }
    }

    @Override
    public String getTitle(FPlayer fplayer) {
        refreshCacheIfStale();
        return replaceTags(fplayer, cachedTitle);
    }

    @Override
    public List<String> getLines(FPlayer fplayer) {
        refreshCacheIfStale();
        if (fplayer.hasFaction()) {
            return getOutput(fplayer, cachedDefault);
        } else if (cachedFactionlessEnabled) {
            return getOutput(fplayer, cachedFactionless);
        }
        return getOutput(fplayer, cachedDefault);
    }

    public List<String> getOutput(FPlayer fplayer, List<String> template) {
        if (template == null || template.isEmpty()) {
            return new ArrayList<>(0);
        }

        List<String> lines = new ArrayList<>(template);

        ListIterator<String> it = lines.listIterator();
        while (it.hasNext()) {
            it.set(replaceTags(fplayer, it.next()));
        }
        return lines;
    }
}