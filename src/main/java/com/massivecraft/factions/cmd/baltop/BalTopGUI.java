package com.massivecraft.factions.cmd.baltop;

import com.cryptomorin.xseries.XMaterial;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.util.ItemBuilder;
import com.massivecraft.factions.util.SaberGUI;
import com.massivecraft.factions.util.serializable.InventoryItem;
import com.massivecraft.factions.zcore.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BalTopGUI extends SaberGUI {

    private static final int PAGE_SIZE = 45;
    private static final int OWN_HEAD_SLOT = 49;
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.##");

    private final int page;
    private final ConfigurationSection cfg;

    public BalTopGUI(Player player, int page) {
        super(player,
                TextUtil.parse(
                        FactionsPlugin.getInstance().getConfig()
                                .getString("baltop-gui.name", "&8&lBaltop")
                                .replace("{page}", String.valueOf(page))
                ),
                54);
        this.page = Math.max(1, page);
        this.cfg = FactionsPlugin.getInstance().getConfig().getConfigurationSection("baltop-gui");
    }

    public BalTopGUI(Player player) {
        this(player, 1);
    }

    @Override
    public void openGUI(org.bukkit.plugin.java.JavaPlugin owning) {
        super.openGUI(owning);

        BalTopCache.getInstance().refreshAsyncThen(owning, () -> {
            if (SaberGUI.getActiveGUI(player.getUniqueId()) == this) {
                try {
                    redraw();
                } catch (Throwable ignored) {
                }
            }
        });

        long intervalSeconds = Math.max(5L,
                FactionsPlugin.getInstance().getConfig().getLong("baltop.gui-refresh-seconds", 30L));
        enableAutoRefresh(owning, intervalSeconds * 20L);
    }

    @Override
    public void redraw() {
        List<BalTopCache.Entry> entries = BalTopCache.getInstance().getSorted();

        ItemStack border = buildItem("border-item");
        for (int i = PAGE_SIZE; i < 54; i++) {
            setItem(i, new InventoryItem(border));
        }

        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, entries.size());

        for (int i = start; i < end; i++) {
            BalTopCache.Entry entry = entries.get(i);
            int slot = i - start;
            int rank = i + 1;
            boolean isOwn = entry.uuid != null && entry.uuid.equals(player.getUniqueId());
            setItem(slot, new InventoryItem(buildPlayerHead(entry, rank, isOwn)));
        }

        if (page > 1) {
            final int prevPage = page - 1;
            ItemStack prev = buildNavItem("prev-item", prevPage);
            setItem(48, new InventoryItem(prev).click(() ->
                    new BalTopGUI(player, prevPage).openGUI(FactionsPlugin.getInstance())));
        }

        int ownRank = BalTopCache.getInstance().findRank(player.getUniqueId());
        if (ownRank <= 0) ownRank = entries.size() + 1;
        double ownBalance = BalTopCache.getInstance().getBalance(player.getUniqueId());
        setItem(OWN_HEAD_SLOT, new InventoryItem(buildSelfHead(ownRank, ownBalance)));

        if (end < entries.size()) {
            final int nextPage = page + 1;
            ItemStack next = buildNavItem("next-item", nextPage);
            setItem(50, new InventoryItem(next).click(() ->
                    new BalTopGUI(player, nextPage).openGUI(FactionsPlugin.getInstance())));
        }
    }

    private ItemStack buildItem(String section) {
        String type = cfgString(section + ".Type");
        String name = cfgString(section + ".Name");
        ItemStack item = parseItem(type);
        return new ItemBuilder(item).name(name != null ? TextUtil.parse(name) : "").build();
    }

    private ItemStack buildNavItem(String section, int targetPage) {
        String type = cfgString(section + ".Type");
        String name = cfgString(section + ".Name");
        List<String> lore = cfgStringList(section + ".Lore");
        lore = replacePage(lore, targetPage);
        ItemStack item = parseItem(type);
        return new ItemBuilder(item).name(name != null ? TextUtil.parse(name) : "").lore(lore).build();
    }

    private ItemStack buildPlayerHead(BalTopCache.Entry entry, int rank, boolean isOwn) {
        String section = isOwn ? "own-head-item" : "player-item";
        String name = cfgString(section + ".Name");
        if (name == null) name = cfgString("player-item.Name");
        if (name == null) name = "&f#{rank} &6{name}";
        List<String> lore = cfgStringList(section + ".Lore");
        if ((lore == null || lore.isEmpty()) && !"player-item".equals(section)) {
            lore = cfgStringList("player-item.Lore");
        }

        ItemStack skull = XMaterial.PLAYER_HEAD.parseItem();
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            if (entry.uuid != null) {
                applySkullOwner(meta, Bukkit.getOfflinePlayer(entry.uuid), entry.name);
            }
            meta.setDisplayName(TextUtil.parse(replacePlaceholders(name, entry.name, rank, entry.balance)));
            List<String> parsed = new ArrayList<>();
            if (lore != null) {
                for (String line : lore) {
                    parsed.add(TextUtil.parse(replacePlaceholders(line, entry.name, rank, entry.balance)));
                }
            }
            meta.setLore(parsed);
            skull.setItemMeta(meta);
        }
        return skull;
    }

    private ItemStack buildSelfHead(int rank, double balance) {
        String name = cfgString("self-head-item.Name");
        if (name == null) name = "&6{name}";
        List<String> lore = cfgStringList("self-head-item.Lore");
        if (lore == null || lore.isEmpty()) {
            lore = new ArrayList<>();
            lore.add("&7Posizione: &e#{rank}");
            lore.add("&7Saldo: &a${balance}");
        }

        ItemStack skull = XMaterial.PLAYER_HEAD.parseItem();
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            applySkullOwner(meta, Bukkit.getOfflinePlayer(player.getUniqueId()), player.getName());
            meta.setDisplayName(TextUtil.parse(replacePlaceholders(name, player.getName(), rank, balance)));
            List<String> parsed = new ArrayList<>(lore.size());
            for (String line : lore) {
                parsed.add(TextUtil.parse(replacePlaceholders(line, player.getName(), rank, balance)));
            }
            meta.setLore(parsed);
            skull.setItemMeta(meta);
        }
        return skull;
    }

    @SuppressWarnings("deprecation")
    private static void applySkullOwner(SkullMeta meta, OfflinePlayer player, String fallbackName) {
        if (player != null) {
            try {
                SkullMeta.class.getMethod("setOwningPlayer", OfflinePlayer.class).invoke(meta, player);
                return;
            } catch (NoSuchMethodException ignored) {
            } catch (ReflectiveOperationException ignored) {
            }
        }
        String name = (player != null && player.getName() != null) ? player.getName() : fallbackName;
        if (name != null && !name.isEmpty()) {
            try {
                meta.setOwner(name);
            } catch (Throwable ignored) {
            }
        }
    }

    private String replacePlaceholders(String s, String name, int rank, double balance) {
        if (s == null) return "";
        return s.replace("{rank}", String.valueOf(rank))
                .replace("{name}", name == null ? "?" : name)
                .replace("{player}", name == null ? "?" : name)
                .replace("{balance}", MONEY.format(balance))
                .replace("{balance-raw}", String.valueOf(balance));
    }

    private List<String> replacePage(List<String> list, int targetPage) {
        if (list == null) return Collections.emptyList();
        List<String> out = new ArrayList<>(list.size());
        for (String line : list) out.add(line.replace("{page}", String.valueOf(targetPage)));
        return out;
    }

    private ItemStack parseItem(String type) {
        if (type == null || type.isEmpty()) return XMaterial.STONE.parseItem();
        return XMaterial.matchXMaterial(type).orElse(XMaterial.STONE).parseItem();
    }

    private String cfgString(String path) {
        if (cfg == null) return null;
        return cfg.getString(path);
    }

    private List<String> cfgStringList(String path) {
        if (cfg == null) return Collections.emptyList();
        return cfg.getStringList(path);
    }
}

