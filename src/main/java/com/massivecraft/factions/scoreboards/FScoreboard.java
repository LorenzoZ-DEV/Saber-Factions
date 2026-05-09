package com.massivecraft.factions.scoreboards;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.zcore.util.TextUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class FScoreboard {

    public static final int MAX_LINES = 15;
    private static final int MAX_LINE_LENGTH = 128;
    private static final int MAX_TITLE_LENGTH = 128;
    private static final String OBJECTIVE_NAME = "f_sb";

    private static final Map<FPlayer, FScoreboard> fscoreboards = new ConcurrentHashMap<>();

    private static volatile int scheduledUpdateTaskId = -1;
    private static volatile long batchIntervalTicks = 20L;
    private static final java.util.concurrent.atomic.AtomicInteger batchCursor = new java.util.concurrent.atomic.AtomicInteger(0);
    private static volatile List<FScoreboard> batchSnapshot = Collections.emptyList();

    private static final long CONTENT_TTL_MS = 750L;
    private static final Cache<FPlayer, RenderedContent> CONTENT_CACHE = Caffeine.newBuilder()
            .expireAfterWrite(CONTENT_TTL_MS, TimeUnit.MILLISECONDS)
            .maximumSize(8192)
            .build();

    private static final class RenderedContent {
        final String title;
        final List<String> lines;
        RenderedContent(String title, List<String> lines) {
            this.title = title;
            this.lines = lines;
        }
    }

    public static void invalidateCache(FPlayer fplayer) {
        if (fplayer != null) CONTENT_CACHE.invalidate(fplayer);
    }

    public static void invalidateCache(Faction faction) {
        if (faction == null) return;
        for (FPlayer fp : faction.getFPlayersWhereOnline(true)) {
            CONTENT_CACHE.invalidate(fp);
        }
    }

    private static volatile Set<String> disabledWorlds = Collections.emptySet();

    private final FPlayer fplayer;
    private volatile Scoreboard scoreboard;
    private volatile Objective objective;

    private volatile FSidebarProvider defaultProvider;
    private volatile FSidebarProvider temporaryProvider;
    private volatile int temporaryTaskId = -1;
    private volatile boolean sidebarVisible = true;
    private volatile boolean removed = false;

    private volatile List<String> lastLines = Collections.emptyList();
    private volatile String lastTitle = null;

    private FScoreboard(FPlayer fplayer) {
        this.fplayer = fplayer;

        Player player = fplayer.getPlayer();
        if (player != null && player.isOnline()) {
            try {
                ensureBoard0(player);
            } catch (Throwable ex) {
                FactionsPlugin.getInstance().getLogger().log(Level.SEVERE,
                        "[FScoreboard] failed to create Bukkit scoreboard for " + player.getName(), ex);
                this.scoreboard = null;
                this.objective = null;
            }
        }
    }

    public static boolean isSupportedByServer() {
        return Bukkit.getScoreboardManager() != null;
    }

    public static void init(FPlayer fplayer) {
        if (fplayer == null || fplayer.getPlayer() == null) return;

        FScoreboard fboard = fscoreboards.computeIfAbsent(fplayer, FScoreboard::new);

        if (fplayer.hasFaction()) {
            FTeamWrapper.applyUpdates(fplayer.getFaction());
        }
        FTeamWrapper.track(fboard);
    }

    public static void remove(FPlayer fplayer, Player player) {
        FScoreboard fboard = fscoreboards.remove(fplayer);
        if (fboard == null) return;

        fboard.removed = true;
        fboard.cancelTemporaryTask();

        Player p = player != null ? player : fplayer.getPlayer();
        if (p != null && p.isOnline()) {
            try {
                ScoreboardManager mgr = Bukkit.getScoreboardManager();
                if (mgr != null) p.setScoreboard(mgr.getMainScoreboard());
            } catch (Throwable ignored) {
            }
        }
        fboard.unregisterObjective();

        FTeamWrapper.untrack(fboard);
    }

    public static FScoreboard get(FPlayer fplayer) {
        return fscoreboards.get(fplayer);
    }

    public static FScoreboard get(Player player) {
        return fscoreboards.get(FPlayers.getInstance().getByPlayer(player));
    }

    public static void update(FPlayer fplayer) {
        if (fplayer == null) return;
        FScoreboard b = fscoreboards.get(fplayer);
        if (b != null) {
            CONTENT_CACHE.invalidate(fplayer);
            b.requestUpdate();
        }
    }

    public static void updateForFaction(Faction faction) {
        if (faction == null) return;
        for (FPlayer fp : faction.getFPlayersWhereOnline(true)) {
            CONTENT_CACHE.invalidate(fp);
            update(fp);
        }
    }

    public static void updateAll() {
        if (fscoreboards.isEmpty()) return;
        for (FScoreboard b : fscoreboards.values()) {
            b.requestUpdate();
        }
    }

    public static synchronized void startScheduledUpdate(org.bukkit.plugin.Plugin plugin, long intervalTicks) {
        stopScheduledUpdate();
        if (plugin == null || intervalTicks <= 0) return;
        batchIntervalTicks = intervalTicks;
        batchCursor.set(0);
        batchSnapshot = Collections.emptyList();
        scheduledUpdateTaskId = Bukkit.getScheduler().runTaskTimer(plugin, FScoreboard::tickBatch,
                1L, 1L).getTaskId();
    }

    private static void tickBatch() {
        if (fscoreboards.isEmpty()) return;

        List<FScoreboard> snapshot = batchSnapshot;
        int cursor = batchCursor.get();

        if (snapshot.isEmpty() || cursor >= snapshot.size()) {

            snapshot = new ArrayList<>(fscoreboards.values());
            batchSnapshot = snapshot;
            cursor = 0;
            batchCursor.set(0);
            if (snapshot.isEmpty()) return;
        }

        long interval = Math.max(1L, batchIntervalTicks);
        int total = snapshot.size();
        int slice = (int) Math.max(1L, (total + interval - 1) / interval);
        int end = Math.min(cursor + slice, total);

        for (int i = cursor; i < end; i++) {
            FScoreboard b = snapshot.get(i);
            if (b == null || b.removed || !b.sidebarVisible) continue;

            FPlayer fp = b.fplayer;
            if (fp == null) continue;
            Player player = fp.getPlayer();
            if (player == null || !player.isOnline()) continue;
            if (!disabledWorlds.isEmpty()
                    && player.getWorld() != null
                    && disabledWorlds.contains(player.getWorld().getName().toLowerCase(Locale.ROOT))) {
                continue;
            }

            try {
                b.requestUpdate();
            } catch (Throwable t) {
            }
        }
        batchCursor.set(end);
    }

    public static synchronized void stopScheduledUpdate() {
        int id = scheduledUpdateTaskId;
        if (id != -1) {
            try { Bukkit.getScheduler().cancelTask(id); } catch (Throwable ignored) {}
            scheduledUpdateTaskId = -1;
        }
        batchSnapshot = Collections.emptyList();
        batchCursor.set(0);
    }

    public static void reloadDisabledWorlds(org.bukkit.plugin.Plugin plugin) {
        Set<String> newSet;
        if (plugin == null) {
            newSet = Collections.emptySet();
        } else {
            HashSet<String> tmp = new HashSet<>();
            List<String> raw = plugin.getConfig().getStringList("scoreboard.disabled-worlds");
            if (raw != null && !raw.isEmpty()) {
                for (String s : raw) {
                    if (s != null && !s.isEmpty()) tmp.add(s.toLowerCase(Locale.ROOT));
                }
            } else {

                Object single = plugin.getConfig().get("scoreboard.disabled-worlds");
                if (single instanceof String) {
                    String s = (String) single;
                    if (!s.isEmpty()) tmp.add(s.toLowerCase(Locale.ROOT));
                }
            }
            newSet = tmp.isEmpty() ? Collections.<String>emptySet() : tmp;
        }
        disabledWorlds = newSet;
        CONTENT_CACHE.invalidateAll();
        updateAll();
    }

    public static boolean isWorldDisabled(String worldName) {
        if (worldName == null) return false;
        Set<String> set = disabledWorlds;
        return !set.isEmpty() && set.contains(worldName.toLowerCase(Locale.ROOT));
    }

    public void setSidebarVisibility(boolean visible) {
        if (this.sidebarVisible == visible) return;
        this.sidebarVisible = visible;

        if (!visible) {
            unregisterObjective();
            Player player = fplayer.getPlayer();
            if (player != null && player.isOnline()) {
                try {
                    ScoreboardManager mgr = Bukkit.getScoreboardManager();
                    if (mgr != null) player.setScoreboard(mgr.getMainScoreboard());
                } catch (Throwable ignored) {
                }
            }
        } else {
            requestUpdate();
        }
    }

    public void setDefaultSidebar(final FSidebarProvider provider) {
        this.defaultProvider = provider;
        if (temporaryProvider == null) requestUpdate();
    }

    public void setTemporarySidebar(final FSidebarProvider provider) {
        cancelTemporaryTask();
        this.temporaryProvider = provider;
        requestUpdate();

        int seconds = FactionsPlugin.getInstance().getConfig().getInt("scoreboard.expiration", 7);
        long delay = Math.max(1L, seconds) * 20L;
        this.temporaryTaskId = Bukkit.getScheduler().runTaskLater(FactionsPlugin.getInstance(), () -> {
            this.temporaryTaskId = -1;
            this.temporaryProvider = null;
            requestUpdate();
        }, delay).getTaskId();
    }

    private void cancelTemporaryTask() {
        int id = this.temporaryTaskId;
        if (id != -1) {
            try { Bukkit.getScheduler().cancelTask(id); } catch (Throwable ignored) {}
            this.temporaryTaskId = -1;
        }
    }

    protected FPlayer getFPlayer() {
        return fplayer;
    }

    protected Scoreboard getScoreboard() {
        Scoreboard sb = scoreboard;
        if (sb != null) return sb;
        Player player = fplayer.getPlayer();
        if (player == null) return null;
        ensureBoard0(player);
        return scoreboard;
    }

    private synchronized void ensureBoard0(Player player) {
        if (scoreboard != null) return;
        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        if (mgr == null) return;
        Scoreboard sb = mgr.getNewScoreboard();
        this.scoreboard = sb;
        try {
            player.setScoreboard(sb);
        } catch (Throwable ex) {
            FactionsPlugin.getInstance().getLogger().log(Level.WARNING,
                    "[FScoreboard] could not assign scoreboard to " + player.getName(), ex);
        }
    }

    private synchronized Objective ensureObjective() {
        if (scoreboard == null) {
            Player p = fplayer.getPlayer();
            if (p != null) ensureBoard0(p);
            if (scoreboard == null) return null;
        }
        Objective obj = objective;
        if (obj != null) {
            try {
                if (scoreboard.getObjective(OBJECTIVE_NAME) == obj) return obj;
            } catch (Throwable ignored) {
            }
        }
        try {
            Objective existing = scoreboard.getObjective(OBJECTIVE_NAME);
            if (existing != null) {
                this.objective = existing;
                if (existing.getDisplaySlot() != DisplaySlot.SIDEBAR) {
                    existing.setDisplaySlot(DisplaySlot.SIDEBAR);
                }
                return existing;
            }
            Objective fresh;
            try {
                fresh = scoreboard.registerNewObjective(OBJECTIVE_NAME, "dummy", " ");
            } catch (NoSuchMethodError ex) {
                fresh = scoreboard.registerNewObjective(OBJECTIVE_NAME, "dummy");
            }
            fresh.setDisplaySlot(DisplaySlot.SIDEBAR);
            this.objective = fresh;
            return fresh;
        } catch (Throwable ex) {
            FactionsPlugin.getInstance().getLogger().log(Level.WARNING,
                    "[FScoreboard] could not (re)create objective for " + fplayer.getName(), ex);
            this.objective = null;
            return null;
        }
    }

    private synchronized void unregisterObjective() {
        Objective obj = objective;
        if (obj != null) {
            try { obj.unregister(); } catch (Throwable ignored) {}
        }
        this.objective = null;
        this.lastLines = Collections.emptyList();
        this.lastTitle = null;
    }

    private void requestUpdate() {
        if (removed) return;

        Player player = fplayer.getPlayer();
        if (player == null || !player.isOnline()) return;

        if (player.getWorld() != null && isWorldDisabled(player.getWorld().getName())) {
            unregisterObjective();
            try {
                ScoreboardManager mgr = Bukkit.getScoreboardManager();
                if (mgr != null && player.getScoreboard() != mgr.getMainScoreboard()) {
                    player.setScoreboard(mgr.getMainScoreboard());
                }
            } catch (Throwable ignored) {
            }
            return;
        }


        try {
            if (scoreboard != null && player.getScoreboard() != scoreboard) {
                player.setScoreboard(scoreboard);
            }
        } catch (Throwable ignored) {
        }

        final FSidebarProvider provider = temporaryProvider != null ? temporaryProvider : defaultProvider;
        if (provider == null || !sidebarVisible) {
            unregisterObjective();
            return;
        }

        Objective obj = ensureObjective();
        if (obj == null) return;

        try {
            renderAndApply(obj, provider);
        } catch (Exception ex) {
            FactionsPlugin.getInstance().getLogger().log(Level.WARNING,
                    "[FScoreboard] render failed for " + fplayer.getName(), ex);
        }
    }

    private static final String[] DUPE_SUFFIXES = {
            "", "\u00A70", "\u00A71", "\u00A72", "\u00A73", "\u00A74", "\u00A75", "\u00A76",
            "\u00A77", "\u00A78", "\u00A79", "\u00A7a", "\u00A7b", "\u00A7c", "\u00A7d", "\u00A7e", "\u00A7f"
    };

    private static String emptyPlaceholder(int index) {
        char first = "0123456789abcdef".charAt(index & 0xF);
        char second = "0123456789abcdef".charAt((index >> 4) & 0xF);
        return "\u00A7" + first + "\u00A7" + second + "\u00A7r ";
    }

    private static boolean isVisuallyBlank(String s) {
        if (s == null || s.isEmpty()) return true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00A7' && i + 1 < s.length()) {
                i++;
                continue;
            }
            if (!Character.isWhitespace(c)) return false;
        }
        return true;
    }

    private synchronized void renderAndApply(Objective obj, FSidebarProvider provider) {
        RenderedContent cached = CONTENT_CACHE.getIfPresent(fplayer);
        String parsedTitle;
        ArrayList<String> parsed;

        if (cached != null) {
            parsedTitle = cached.title;
            parsed = (ArrayList<String>) cached.lines;
        } else {
            String title = provider.getTitle(fplayer);
            List<String> raw = provider.getLines(fplayer);
            if (raw == null) raw = Collections.emptyList();

            int size = Math.min(raw.size(), MAX_LINES);
            parsed = new ArrayList<>(size);
            HashSet<String> seen = new HashSet<>();
            int blankCounter = 0;
            for (int i = 0; i < size; i++) {
                String line = raw.get(i);
                String parsedLine = line == null ? "" : TextUtil.parse(line);

                if (isVisuallyBlank(parsedLine)) {
                    parsedLine = emptyPlaceholder(blankCounter++);
                }

                if (parsedLine.length() > MAX_LINE_LENGTH) {
                    parsedLine = parsedLine.substring(0, MAX_LINE_LENGTH);
                }
                String unique = parsedLine;
                int pad = 0;
                while (!seen.add(unique) && pad < DUPE_SUFFIXES.length) {
                    unique = parsedLine + DUPE_SUFFIXES[pad++];
                    if (unique.length() > MAX_LINE_LENGTH) {
                        unique = unique.substring(0, MAX_LINE_LENGTH);
                    }
                }
                parsed.add(unique);
            }

            parsedTitle = title == null ? " " : TextUtil.parse(title);
            if (parsedTitle.length() > MAX_TITLE_LENGTH) parsedTitle = parsedTitle.substring(0, MAX_TITLE_LENGTH);
            if (parsedTitle.isEmpty()) parsedTitle = " ";

            CONTENT_CACHE.put(fplayer, new RenderedContent(parsedTitle, parsed));
        }

        if (parsedTitle.equals(lastTitle) && parsed.equals(lastLines)) {
            return;
        }

        if (lastTitle == null || !lastTitle.equals(parsedTitle)) {
            try {
                obj.setDisplayName(parsedTitle);
            } catch (Throwable ex) {
                FactionsPlugin.getInstance().getLogger().log(Level.WARNING,
                        "[FScoreboard] setDisplayName failed for " + fplayer.getName(), ex);
            }
            lastTitle = parsedTitle;
        }

        List<String> previous = lastLines;
        if (!previous.equals(parsed)) {
            HashSet<String> newSet = new HashSet<>(parsed);
            for (String old : previous) {
                if (!newSet.contains(old)) {
                    try { scoreboard.resetScores(old); } catch (Throwable ignored) {}
                }
            }
            int n = parsed.size();
            int prevN = previous.size();
            for (int i = 0; i < n; i++) {
                String line = parsed.get(i);
                int score = n - i;

                int prevIdx = previous.indexOf(line);
                if (prevIdx >= 0 && (prevN - prevIdx) == score) {
                    continue;
                }

                try {
                    obj.getScore(line).setScore(score);
                } catch (Throwable ex) {
                    FactionsPlugin.getInstance().getLogger().log(Level.WARNING,
                            "[FScoreboard] setScore failed for line '" + line + "'", ex);
                }
            }
            lastLines = new ArrayList<>(parsed);
        }
    }
}

