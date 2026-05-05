package com.massivecraft.factions.cmd.ftop;

import com.massivecraft.factions.Faction;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.Factions;
import com.massivecraft.factions.integration.Econ;
import com.massivecraft.factions.util.FastMath;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FTopCache {

    private static final FTopCache INSTANCE = new FTopCache();

    private final Map<String, List<Faction>> cache = new HashMap<>();
    private BukkitTask refreshTask;
    private volatile long lastRefreshAt = 0L;
    private volatile long refreshIntervalMs = 0L;

    static final Map<String, Comparator<Faction>> CRITERIA = new HashMap<String, Comparator<Faction>>() {{
        put("members", (f1, f2) -> Integer.compare(f2.getFPlayers().size(), f1.getFPlayers().size()));
        put("start",   (f1, f2) -> Long.compare(f2.getFoundedDate(), f1.getFoundedDate()));
        put("power",   (f1, f2) -> Integer.compare(f2.getPowerRounded(), f1.getPowerRounded()));
        put("land",    (f1, f2) -> Integer.compare(f2.getLandRounded(), f1.getLandRounded()));
        put("online",  (f1, f2) -> Integer.compare(f2.getFPlayersWhereOnline(true).size(), f1.getFPlayersWhereOnline(true).size()));
        put("money",   (f1, f2) -> Double.compare(totalBalance(f2), totalBalance(f1)));
    }};

    private FTopCache() {}

    public static FTopCache getInstance() {
        return INSTANCE;
    }

    public void startScheduledRefresh(JavaPlugin plugin, long intervalTicks) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::refresh);
        if (intervalTicks <= 0) return;
        if (refreshTask != null) refreshTask.cancel();
        this.refreshIntervalMs = intervalTicks * 50L;
        refreshTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin, this::refresh, intervalTicks, intervalTicks);
    }

    public void refreshIfStale(JavaPlugin plugin) {
        long interval = refreshIntervalMs;
        if (interval <= 0) return;
        if (System.currentTimeMillis() - lastRefreshAt < interval) return;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::refresh);
    }

    public synchronized void refresh() {
        List<Faction> all = new ArrayList<>(Factions.getInstance().getAllNormalFactions());
        for (Map.Entry<String, Comparator<Faction>> entry : CRITERIA.entrySet()) {
            List<Faction> sorted = new ArrayList<>(all);
            sorted.sort(entry.getValue());
            cache.put(entry.getKey(), sorted);
        }
        lastRefreshAt = System.currentTimeMillis();
    }

    public synchronized void invalidate() {
        cache.clear();
        lastRefreshAt = 0L;
    }

    public synchronized List<Faction> getSorted(String criteria) {
        String key = criteria.toLowerCase();
        List<Faction> cached = cache.get(key);
        if (cached != null) {
            List<Faction> filtered = new ArrayList<>(cached.size());
            for (Faction f : cached) {
                if (isAlive(f)) filtered.add(f);
            }
            if (filtered.size() != cached.size()) {
                cache.put(key, new ArrayList<>(filtered));
            }
            return filtered;
        }

        List<Faction> fresh = new ArrayList<>(Factions.getInstance().getAllNormalFactions());
        fresh.sort(CRITERIA.getOrDefault(key, CRITERIA.get("power")));
        return fresh;
    }

    private static boolean isAlive(Faction f) {
        if (f == null) return false;
        try {
            Faction live = Factions.getInstance().getFactionById(f.getId());
            return live != null && live.isNormal();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public synchronized void clear() {
        cache.clear();
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    private static double totalBalance(Faction f) {
        double total = f.getFactionBalance();
        for (FPlayer fp : f.getFPlayers()) {
            total = FastMath.round(total + Econ.getBalance(fp.getAccountId()));
        }
        return total;
    }
}
