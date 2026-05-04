package com.massivecraft.factions.cmd.baltop;

import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.integration.Econ;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class BalTopCache {

    public static final class Entry {
        public final UUID uuid;
        public final String name;
        public final double balance;

        Entry(UUID uuid, String name, double balance) {
            this.uuid = uuid;
            this.name = name;
            this.balance = balance;
        }
    }

    private static final BalTopCache INSTANCE = new BalTopCache();

    private volatile List<Entry> sorted = Collections.emptyList();
    private volatile long lastRefreshAt = 0L;
    private volatile long refreshIntervalMs = 0L;
    private volatile boolean refreshing = false;
    private BukkitTask refreshTask;

    private BalTopCache() {}

    public static BalTopCache getInstance() {
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

    /**
     * Trigger an asynchronous refresh and run {@code afterRefresh} on the main
     * thread once it completes. Used by the GUI to make sure the data shown is
     * fresh every time a player opens it.
     */
    public void refreshAsyncThen(JavaPlugin plugin, Runnable afterRefresh) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                refresh();
            } finally {
                if (afterRefresh != null) {
                    plugin.getServer().getScheduler().runTask(plugin, afterRefresh);
                }
            }
        });
    }

    public synchronized void refresh() {
        if (refreshing) return;
        refreshing = true;
        try {
            Economy econ = Econ.getEconomy();
            if (econ == null) {
                this.sorted = Collections.emptyList();
                this.lastRefreshAt = System.currentTimeMillis();
                return;
            }

            int max = Math.max(1, FactionsPlugin.getInstance().getConfig().getInt("baltop.max-entries", 100));

            OfflinePlayer[] all = Bukkit.getOfflinePlayers();

            // Dedupe first by UUID (in case the server returns multiple
            // OfflinePlayer instances for the same player), then by lower-cased
            // name (some servers end up with two profiles - online + cracked -
            // sharing the same display name).
            Map<UUID, Entry> byUuid = new LinkedHashMap<>();
            for (OfflinePlayer op : all) {
                if (op == null) continue;
                UUID id = op.getUniqueId();
                if (id == null) continue;
                if (byUuid.containsKey(id)) continue;
                String name = op.getName();
                if (name == null || name.isEmpty()) continue;
                double bal;
                try {
                    bal = econ.hasAccount(op) ? econ.getBalance(op) : 0D;
                } catch (Throwable ex) {
                    continue;
                }
                byUuid.put(id, new Entry(id, name, bal));
            }

            Map<String, Entry> byNameLower = new HashMap<>(byUuid.size());
            for (Entry e : byUuid.values()) {
                String key = e.name.toLowerCase();
                Entry existing = byNameLower.get(key);
                if (existing == null || e.balance > existing.balance) {
                    byNameLower.put(key, e);
                }
            }

            List<Entry> list = new ArrayList<>(byNameLower.values());
            list.sort((a, b) -> Double.compare(b.balance, a.balance));
            if (list.size() > max) list = new ArrayList<>(list.subList(0, max));
            this.sorted = Collections.unmodifiableList(list);
            this.lastRefreshAt = System.currentTimeMillis();
        } catch (Throwable ex) {
            FactionsPlugin.getInstance().getLogger().log(Level.WARNING, "[BalTopCache] refresh failed", ex);
        } finally {
            refreshing = false;
        }
    }

    public List<Entry> getSorted() {
        return sorted;
    }

    public int findRank(UUID uuid) {
        if (uuid == null) return -1;
        List<Entry> list = sorted;
        for (int i = 0; i < list.size(); i++) {
            if (uuid.equals(list.get(i).uuid)) return i + 1;
        }
        return -1;
    }

    public double getBalance(UUID uuid) {
        if (uuid == null) return 0D;
        for (Entry e : sorted) {
            if (uuid.equals(e.uuid)) return e.balance;
        }
        Economy econ = Econ.getEconomy();
        if (econ == null) return 0D;
        try {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            return econ.hasAccount(op) ? econ.getBalance(op) : 0D;
        } catch (Throwable ex) {
            return 0D;
        }
    }

    public synchronized void clear() {
        sorted = Collections.emptyList();
        lastRefreshAt = 0L;
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }
}

