package com.massivecraft.factions.scoreboards;

import com.massivecraft.factions.*;
import com.massivecraft.factions.zcore.util.TL;
import com.massivecraft.factions.zcore.util.TextUtil;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class FTeamWrapper {

    /**
     * @author FactionsUUID Team - Modified By CmdrKittens
     */

    private static final Map<Faction, FTeamWrapper> wrappers = new ConcurrentHashMap<>();
    private static final Set<FScoreboard> tracking = ConcurrentHashMap.newKeySet();
    private static final Set<Faction> updating = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger factionTeamPtr = new AtomicInteger();

    private final Map<FScoreboard, Team> teams = new ConcurrentHashMap<>();
    private final Set<OfflinePlayer> members = ConcurrentHashMap.newKeySet();
    private final String teamName;
    private final Faction faction;

    private FTeamWrapper(Faction faction) {
        this.teamName = "faction_" + factionTeamPtr.getAndIncrement();
        this.faction = faction;

        for (FScoreboard fboard : tracking) {
            add(fboard);
        }
    }

    // --- cached config flags (re-evaluated at most every 20s, refreshed lazily
    //     to stay in sync with /f reload without hitting YAML in hot path) ---
    private static volatile long cachedConfigAt = 0L;
    private static volatile boolean cachedDefaultPrefixes = false;
    private static volatile boolean cachedSeeInvisible = false;
    private static volatile boolean cachedFocusEnabled = false;
    private static volatile String cachedFocusPrefix = "";
    private static volatile String cachedDefaultPrefixTemplate = "";

    private static void refreshConfigCacheIfStale() {
        long now = System.currentTimeMillis();
        if (now - cachedConfigAt <= 20_000L) return;
        FileConfiguration c = FactionsPlugin.getInstance().getConfig();
        cachedDefaultPrefixes = c.getBoolean("scoreboard.default-prefixes", false);
        cachedSeeInvisible    = c.getBoolean("See-Invisible-Faction-Members", false);
        cachedFocusEnabled    = c.getBoolean("ffocus.Enabled", false);
        cachedFocusPrefix     = TextUtil.parse(c.getString("ffocus.Prefix", "&7»&b"));
        cachedDefaultPrefixTemplate = TL.DEFAULT_PREFIX.toString();
        cachedConfigAt        = now;
    }

    private static boolean defaultPrefixesEnabled() {
        refreshConfigCacheIfStale();
        return cachedDefaultPrefixes && !cachedSeeInvisible;
    }

    public static void applyUpdatesLater(final Faction faction) {
        if (!FScoreboard.isSupportedByServer()) return;
        if (faction.isWilderness()) return;
        if (!defaultPrefixesEnabled()) return;

        if (updating.add(faction)) {
            Bukkit.getScheduler().runTask(FactionsPlugin.getInstance(), () -> {
                updating.remove(faction);
                applyUpdates(faction);
            });
        }
    }

    public static void applyUpdates(Faction faction) {
        if (!FScoreboard.isSupportedByServer()) return;
        if (faction.isWilderness()) return;
        if (!defaultPrefixesEnabled()) return;
        if (updating.contains(faction)) return;

        // Remove wrapper if the faction was disbanded.
        if (Factions.getInstance().getFactionById(faction.getId()) == null) {
            FTeamWrapper removed = wrappers.remove(faction);
            if (removed != null) removed.unregister();
            return;
        }

        FTeamWrapper wrapper = wrappers.computeIfAbsent(faction, FTeamWrapper::new);
        Set<FPlayer> factionMembers = faction.getFPlayers();

        // Drop offline / ex-members (snapshot to avoid CME).
        for (OfflinePlayer player : wrapper.members.toArray(new OfflinePlayer[0])) {
            if (!player.isOnline() || !factionMembers.contains(FPlayers.getInstance().getByOfflinePlayer(player))) {
                wrapper.removePlayer(player);
            }
        }

        for (FPlayer fmember : factionMembers) {
            if (!fmember.isOnline()) continue;
            wrapper.addPlayer(fmember.getPlayer());
        }
        wrapper.updatePrefixes();
    }

    public static void updatePrefixes(Faction faction) {
        if (!FScoreboard.isSupportedByServer()) return;

        FTeamWrapper wrapper = wrappers.get(faction);
        if (wrapper == null) {
            applyUpdates(faction);
        } else {
            wrapper.updatePrefixes();
        }
    }

    protected static void track(FScoreboard fboard) {
        if (!FScoreboard.isSupportedByServer()) return;
        if (!tracking.add(fboard)) return;
        for (FTeamWrapper wrapper : wrappers.values()) wrapper.add(fboard);
    }

    protected static void untrack(FScoreboard fboard) {
        if (!FScoreboard.isSupportedByServer()) return;
        if (!tracking.remove(fboard)) return;
        for (FTeamWrapper wrapper : wrappers.values()) wrapper.remove(fboard);
    }

    private void add(FScoreboard fboard) {
        Scoreboard board = fboard.getScoreboard();
        if (board == null) return;
        teams.computeIfAbsent(fboard, k -> {
            Team team = board.registerNewTeam(teamName);
            for (OfflinePlayer player : members) team.addPlayer(player);
            return team;
        });
        updatePrefix(fboard);
    }

    private void remove(FScoreboard fboard) {
        Team team = teams.remove(fboard);
        if (team != null) {
            try { team.unregister(); } catch (IllegalStateException ignored) {}
        }
    }

    private void updatePrefixes() {
        if (!defaultPrefixesEnabled()) return;
        for (FScoreboard fboard : teams.keySet()) updatePrefix(fboard);
    }

    private void updatePrefix(FScoreboard fboard) {
        if (!defaultPrefixesEnabled()) return;

        FPlayer fplayer = fboard.getFPlayer();
        Team team = teams.get(fboard);
        if (team == null) return;

        // Cached config values (refreshed by defaultPrefixesEnabled()).
        if (cachedSeeInvisible) {
            team.setCanSeeFriendlyInvisibles(true);
        }

        boolean focused = false;
        if (cachedFocusEnabled && fplayer.getFaction() != null
                && fplayer.getFaction().getFocused() != null) {
            String focusedName = fplayer.getFaction().getFocused();
            for (FPlayer fp : faction.getFPlayersWhereOnline(true)) {
                if (focusedName.equalsIgnoreCase(fp.getName())) {
                    if (!cachedFocusPrefix.equals(team.getPrefix())) team.setPrefix(cachedFocusPrefix);
                    focused = true;
                    break;
                }
            }
        }

        if (!focused) {
            String prefix = cachedDefaultPrefixTemplate;
            prefix = PlaceholderAPI.setPlaceholders(fplayer.getPlayer(), prefix);
            prefix = PlaceholderAPI.setBracketPlaceholders(fplayer.getPlayer(), prefix);
            prefix = prefix.replace("{relationcolor}", faction.getRelationTo(fplayer).getColor().toString());
            String tag = faction.getTag();
            int maxTagLen = Math.min("{faction}".length() + 16 - prefix.length(), tag.length());
            if (maxTagLen < 0) maxTagLen = 0;
            prefix = prefix.replace("{faction}", tag.substring(0, maxTagLen));
            if (!prefix.equals(team.getPrefix())) {
                team.setPrefix(prefix);
            }
        }
    }

    private void addPlayer(OfflinePlayer player) {
        if (members.add(player)) {
            for (Team team : teams.values()) team.addPlayer(player);
        }
    }

    private void removePlayer(OfflinePlayer player) {
        if (members.remove(player)) {
            for (Team team : teams.values()) team.removePlayer(player);
        }
    }

    private void unregister() {
        for (Team team : teams.values()) {
            try { team.unregister(); } catch (IllegalStateException ignored) {}
        }
        teams.clear();
        members.clear();
    }
}
