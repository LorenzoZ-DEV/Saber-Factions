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

    private static boolean defaultPrefixesEnabled() {
        FileConfiguration c = FactionsPlugin.getInstance().getConfig();
        return c.getBoolean("scoreboard.default-prefixes", false)
                && !c.getBoolean("See-Invisible-Faction-Members", false);
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

        if (Factions.getInstance().getFactionById(faction.getId()) == null) {
            FTeamWrapper removed = wrappers.remove(faction);
            if (removed != null) removed.unregister();
            return;
        }

        FTeamWrapper wrapper = wrappers.computeIfAbsent(faction, FTeamWrapper::new);
        Set<FPlayer> factionMembers = faction.getFPlayers();

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

    @SuppressWarnings("deprecation")
    private void add(FScoreboard fboard) {
        Scoreboard board = fboard.getScoreboard();
        if (board == null) return;
        teams.computeIfAbsent(fboard, k -> {
            Team existing = board.getTeam(teamName);
            Team team = existing != null ? existing : board.registerNewTeam(teamName);
            for (OfflinePlayer player : members) {
                try { team.addPlayer(player); } catch (IllegalArgumentException ignored) {}
            }
            return team;
        });
        updatePrefix(fboard);
    }

    private void remove(FScoreboard fboard) {
        Team team = teams.remove(fboard);
        if (team == null) return;
        for (Team other : teams.values()) {
            if (other == team) return;
        }
        try { team.unregister(); } catch (IllegalStateException ignored) {}
    }

    private void updatePrefixes() {
        if (!defaultPrefixesEnabled()) return;
        for (FScoreboard fboard : teams.keySet()) updatePrefix(fboard);
    }

    private void updatePrefix(FScoreboard fboard) {
        FileConfiguration c = FactionsPlugin.getInstance().getConfig();
        boolean enabled = c.getBoolean("scoreboard.default-prefixes", false)
                && !c.getBoolean("See-Invisible-Faction-Members", false);
        if (!enabled) return;

        FPlayer fplayer = fboard.getFPlayer();
        Team team = teams.get(fboard);
        if (team == null) return;

        if (c.getBoolean("See-Invisible-Faction-Members", false)) {
            team.setCanSeeFriendlyInvisibles(true);
        }

        boolean focused = false;
        if (c.getBoolean("ffocus.Enabled", false) && fplayer.getFaction() != null
                && fplayer.getFaction().getFocused() != null) {
            String focusedName = fplayer.getFaction().getFocused();
            String focusPrefix = TextUtil.parse(c.getString("ffocus.Prefix", "&7\u00BB&b"));
            for (FPlayer fp : faction.getFPlayersWhereOnline(true)) {
                if (focusedName.equalsIgnoreCase(fp.getName())) {
                    if (!focusPrefix.equals(team.getPrefix())) team.setPrefix(focusPrefix);
                    focused = true;
                    break;
                }
            }
        }

        if (!focused) {
            String prefix = TL.DEFAULT_PREFIX.toString();
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

    @SuppressWarnings("deprecation")
    private void addPlayer(OfflinePlayer player) {
        if (members.add(player)) {
            for (Team team : teams.values()) team.addPlayer(player);
        }
    }

    @SuppressWarnings("deprecation")
    private void removePlayer(OfflinePlayer player) {
        if (members.remove(player)) {
            for (Team team : teams.values()) team.removePlayer(player);
        }
    }

    private void unregister() {
        java.util.HashSet<Team> uniq = new java.util.HashSet<>(teams.values());
        for (Team team : uniq) {
            try { team.unregister(); } catch (IllegalStateException ignored) {}
        }
        teams.clear();
        members.clear();
    }
}
