package com.massivecraft.factions.scoreboards;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.zcore.util.TextUtil;
import fr.mrmicky.fastboard.FastBoard;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Packet-based sidebar wrapper built on top of FastBoard.
 * Single global tick task drives all players at {@value #UPDATE_PERIOD_TICKS} tick intervals.
 */
public class FScoreboard {

    /** Hard limit imposed by the vanilla scoreboard protocol. */
    public static final int MAX_LINES = 15;

    /** Minimum ticks between two consecutive sidebar updates. */
    private static final long UPDATE_PERIOD_TICKS = 10L;

    private static final Map<FPlayer, FScoreboard> fscoreboards = new ConcurrentHashMap<>();
    private static final AtomicLong TICK_COUNTER = new AtomicLong();
    private static volatile BukkitTask updateTask;

    private final Scoreboard scoreboard;
    private final FPlayer fplayer;
    private final FastBoard fastBoard; // created once, never recreated

    private volatile FSidebarProvider defaultProvider;
    private volatile FSidebarProvider temporaryProvider;
    private volatile long temporaryExpireTick = -1L;
    private volatile boolean sidebarVisible = true;
    private volatile boolean removed = false;

    // Cache of the last successfully applied frame; lets us short-circuit when
    // nothing changed, cutting both allocations and packets.
    private volatile String lastAppliedTitle = "";
    private volatile List<String> lastAppliedLines = Collections.emptyList();

    private FScoreboard(FPlayer fplayer) {
        this.fplayer = fplayer;

        Player player = fplayer.getPlayer();
        if (isSupportedByServer() && player != null) {
            this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            // Keep the Bukkit scoreboard attached so FTeamWrapper can register
            // teams for faction tag prefixes. FastBoard works via packets so
            // it does not conflict.
            player.setScoreboard(scoreboard);
            // Objective + all 15 team slots are allocated once here.
            this.fastBoard = new FastBoard(player);
            this.fastBoard.updateTitle(" ");
        } else {
            this.scoreboard = null;
            this.fastBoard = null;
        }
    }

    // Glowstone doesn't support scoreboards.
    public static boolean isSupportedByServer() {
        return Bukkit.getScoreboardManager() != null;
    }

    public static void init(FPlayer fplayer) {
        FScoreboard fboard = fscoreboards.computeIfAbsent(fplayer, FScoreboard::new);

        if (fplayer.hasFaction()) {
            FTeamWrapper.applyUpdates(fplayer.getFaction());
        }
        FTeamWrapper.track(fboard);
        ensureUpdateTask();
    }

    public static void remove(FPlayer fplayer, Player player) {
        FScoreboard fboard = fscoreboards.remove(fplayer);
        if (fboard == null) return;

        fboard.removed = true;
        if (Bukkit.getScoreboardManager() != null && fboard.scoreboard != null
                && fboard.scoreboard == player.getScoreboard()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        if (fboard.fastBoard != null && !fboard.fastBoard.isDeleted()) {
            fboard.fastBoard.delete();
        }
        FTeamWrapper.untrack(fboard);

        if (fscoreboards.isEmpty()) stopUpdateTask();
    }

    public static FScoreboard get(FPlayer fplayer) {
        return fscoreboards.get(fplayer);
    }

    public static FScoreboard get(Player player) {
        return fscoreboards.get(FPlayers.getInstance().getByPlayer(player));
    }

    // ---------------- Global ticker ----------------

    private static void ensureUpdateTask() {
        if (updateTask != null) return;
        synchronized (FScoreboard.class) {
            if (updateTask != null) return;
            updateTask = Bukkit.getScheduler().runTaskTimer(
                    FactionsPlugin.getInstance(),
                    FScoreboard::tickAll,
                    UPDATE_PERIOD_TICKS,
                    UPDATE_PERIOD_TICKS);
        }
    }

    private static void stopUpdateTask() {
        synchronized (FScoreboard.class) {
            if (updateTask != null) {
                updateTask.cancel();
                updateTask = null;
            }
        }
    }

    private static void tickAll() {
        long tick = TICK_COUNTER.incrementAndGet();
        for (FScoreboard board : fscoreboards.values()) {
            board.tick(tick);
        }
    }

    private void tick(long currentTick) {
        if (removed || fastBoard == null) return;

        // Expire the temporary provider without a dedicated Bukkit task.
        if (temporaryProvider != null && currentTick >= temporaryExpireTick) {
            temporaryProvider = null;
        }
        requestUpdate();
    }

    // ---------------- Public API ----------------

    public void setSidebarVisibility(boolean visible) {
        if (fastBoard == null) return;
        if (this.sidebarVisible == visible) return;
        this.sidebarVisible = visible;
        if (!visible) {
            applyHidden();
        } else {
            requestUpdate();
        }
    }

    public void setDefaultSidebar(final FSidebarProvider provider) {
        if (fastBoard == null) return;
        defaultProvider = provider;
        if (temporaryProvider == null) requestUpdate();
    }

    public void setTemporarySidebar(final FSidebarProvider provider) {
        if (fastBoard == null) return;
        int seconds = cachedExpirationSeconds();
        temporaryProvider = provider;
        // Convert seconds to ticker periods (ticker fires every UPDATE_PERIOD_TICKS).
        long periods = Math.max(1L, (seconds * 20L) / UPDATE_PERIOD_TICKS);
        temporaryExpireTick = TICK_COUNTER.get() + periods;
        requestUpdate();
    }

    // Cache of scoreboard.expiration — refreshed lazily every 20s to stay in
    // sync with /f reload without paying a YAML lookup at every finfo.
    private static volatile int cachedExpirationSeconds = -1;
    private static volatile long cachedExpirationAt = 0L;

    private static int cachedExpirationSeconds() {
        long now = System.currentTimeMillis();
        if (cachedExpirationSeconds < 0 || now - cachedExpirationAt > 20_000L) {
            cachedExpirationSeconds = FactionsPlugin.getInstance().getConfig().getInt("scoreboard.expiration", 7);
            cachedExpirationAt = now;
        }
        return cachedExpirationSeconds;
    }

    protected FPlayer getFPlayer() { return fplayer; }

    protected Scoreboard getScoreboard() { return scoreboard; }

    // ---------------- Update pipeline ----------------

    private void requestUpdate() {
        if (fastBoard == null || fastBoard.isDeleted() || removed) return;

        final FSidebarProvider provider = temporaryProvider != null ? temporaryProvider : defaultProvider;
        if (provider == null || !sidebarVisible) {
            applyHidden();
            return;
        }

        try {
            Snapshot snapshot = renderSnapshot(provider);
            applySnapshot(snapshot);
        } catch (Exception ex) {
            FactionsPlugin.getInstance().getLogger().warning(
                    "FScoreboard: render failed for " + fplayer.getName() + ": " + ex.getMessage());
        }
    }

    /** Builds the frame to apply. Runs on the main thread. */
    private Snapshot renderSnapshot(FSidebarProvider provider) {
        String title = provider.getTitle(fplayer);
        List<String> raw = provider.getLines(fplayer);

        // Clamp to MAX_LINES, skip nulls, parse colors, sanitise for legacy
        // (1.8-1.12) team prefix/suffix splitting done by FastBoard.
        int size = Math.min(raw.size(), MAX_LINES);
        ArrayList<String> parsed = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String line = raw.get(i);
            parsed.add(sanitizeLine(line == null ? "" : TextUtil.parse(line)));
        }

        String parsedTitle = title == null ? "" : TextUtil.parse(title);
        return new Snapshot(parsedTitle, parsed);
    }

    /**
     * Makes a single scoreboard line safe for FastBoard's legacy (1.8-1.12)
     * renderer, which splits the line into a 16-char prefix + entry + 16-char
     * suffix across scoreboard team slots. Several edge cases corrupt the split
     * and trigger {@link StringIndexOutOfBoundsException} inside FastBoard; we
     * normalise them upstream:
     * <ol>
     *     <li>Strip trailing dangling {@code §} (no colour code after it).</li>
     *     <li>Hard-cap to 30 characters (the protocol limit is 32 with both
     *         prefix + suffix; leaving some margin avoids off-by-one cases).</li>
     *     <li>If character at index 15 is {@code §}, truncate right before it —
     *         otherwise the prefix/suffix boundary lands on an orphan colour
     *         byte and the split blows up.</li>
     * </ol>
     */
    private static String sanitizeLine(String s) {
        if (s == null || s.isEmpty()) return "";

        s = stripTrailingColourChar(s);
        if (s.length() > 30) s = stripTrailingColourChar(s.substring(0, 30));

        if (s.length() > 16 && s.charAt(15) == org.bukkit.ChatColor.COLOR_CHAR) {
            s = stripTrailingColourChar(s.substring(0, 15));
        } else if (s.length() == 16 && s.charAt(15) == org.bukkit.ChatColor.COLOR_CHAR) {
            s = s.substring(0, 15);
        }
        return s;
    }

    private static String stripTrailingColourChar(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == org.bukkit.ChatColor.COLOR_CHAR) end--;
        return end == s.length() ? s : s.substring(0, end);
    }

    /** Applies a rendered frame, skipping packets when nothing changed. */
    private void applySnapshot(Snapshot snapshot) {
        if (fastBoard == null || fastBoard.isDeleted() || removed) return;

        // Title diff.
        if (!snapshot.title.equals(lastAppliedTitle)) {
            try {
                fastBoard.updateTitle(snapshot.title);
                lastAppliedTitle = snapshot.title;
            } catch (RuntimeException ex) {
                // Don't let a single bad title kill the global ticker.
                FactionsPlugin.getInstance().getLogger().warning(
                        "FScoreboard: failed to apply title '" + snapshot.title + "': " + ex.getMessage());
            }
        }

        // Lines diff — equality check avoids the FastBoard call entirely when
        // the provider produced the same content as last frame (common case).
        if (!snapshot.lines.equals(lastAppliedLines)) {
            try {
                fastBoard.updateLines(snapshot.lines);
                lastAppliedLines = snapshot.lines;
            } catch (RuntimeException ex) {
                // Most likely a legacy-split edge case in FastBoard itself. We
                // already sanitise the lines upstream; still keep the ticker
                // alive if something slips through.
                FactionsPlugin.getInstance().getLogger().warning(
                        "FScoreboard: failed to apply sidebar lines: " + ex.getMessage());
            }
        }
    }

    private void applyHidden() {
        if (fastBoard == null || fastBoard.isDeleted() || removed) return;
        if (lastAppliedLines.isEmpty()) return;
        fastBoard.updateLines(Collections.emptyList());
        lastAppliedLines = Collections.emptyList();
    }

    /** Immutable rendered frame passed from the async render to the sync apply. */
    private static final class Snapshot {
        final String title;
        final List<String> lines;

        Snapshot(String title, List<String> lines) {
            this.title = title;
            this.lines = lines;
        }
    }
}
