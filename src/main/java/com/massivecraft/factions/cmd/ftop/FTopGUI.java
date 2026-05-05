package com.massivecraft.factions.cmd.ftop;

import com.cryptomorin.xseries.XMaterial;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class FTopGUI extends SaberGUI {

    private static final int PAGE_SIZE = 45;
    private static final int OWN_HEAD_SLOT = 49;

    private final String criteria;
    private final int page;
    private final ConfigurationSection cfg;

    public FTopGUI(Player player, String criteria, int page) {
        super(player,
                TextUtil.parse(
                        FactionsPlugin.getInstance().getConfig()
                                .getString("ftop-gui.name")
                                .replace("{criteria}", criteria.toUpperCase())
                ),
                54);
        this.criteria = criteria.toLowerCase();
        this.page = page;
        this.cfg = FactionsPlugin.getInstance().getConfig().getConfigurationSection("ftop-gui");
    }

    public FTopGUI(Player player) {
        this(player, FTopCache.CRITERIA.isEmpty() ? "balance" : FTopCache.CRITERIA.keySet().iterator().next(), 1);
    }

    @Override
    public void openGUI(org.bukkit.plugin.java.JavaPlugin owning) {

        FTopCache.getInstance().refreshIfStale(owning);
        super.openGUI(owning);

        long intervalSeconds = Math.max(5L,
                FactionsPlugin.getInstance().getConfig().getLong("ftop.gui-refresh-seconds", 30L));
        enableAutoRefresh(owning, intervalSeconds * 20L);
    }

    @Override
    public void redraw() {
        List<Faction> factions = FTopCache.getInstance().getSorted(criteria);
        FPlayer fme = FPlayers.getInstance().getByPlayer(player);

        if (fme == null) return;

        ItemStack border = buildItem("border-item");
        for (int i = PAGE_SIZE; i < 54; i++) {
            setItem(i, new InventoryItem(border));
        }

        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, factions.size());

        for (int i = start; i < end; i++) {
            Faction faction = factions.get(i);
            int slot = i - start;
            int rank = i + 1;
            boolean isOwn = fme.hasFaction() && fme.getFaction().getId().equals(faction.getId());
            setItem(slot, new InventoryItem(isOwn ? buildOwnHead(fme, faction, rank) : buildFactionHead(faction, rank)));
        }

        if (page > 1) {
            final int prevPage = page - 1;
            ItemStack prev = buildNavItem("prev-item", prevPage);
            setItem(48, new InventoryItem(prev).click(() ->
                    new FTopGUI(player, criteria, prevPage).openGUI(FactionsPlugin.getInstance())));
        }

        Faction ownFaction = fme.hasFaction() ? fme.getFaction() : null;
        if (ownFaction != null) {
            int ownRank = findRank(factions, ownFaction);
            if (ownRank <= 0) ownRank = factions.size() + 1;
            setItem(OWN_HEAD_SLOT, new InventoryItem(buildOwnHead(fme, ownFaction, ownRank)));
        } else {
            // Player has no faction: show a head prompting them to create one.
            setItem(OWN_HEAD_SLOT, new InventoryItem(buildNoFactionHead(fme)));
        }

        if (end < factions.size()) {
            final int nextPage = page + 1;
            ItemStack next = buildNavItem("next-item", nextPage);
            setItem(50, new InventoryItem(next).click(() ->
                    new FTopGUI(player, criteria, nextPage).openGUI(FactionsPlugin.getInstance())));
        }
    }

    private int findRank(List<Faction> sorted, Faction target) {
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getId().equals(target.getId())) return i + 1;
        }
        return -1;
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

    private ItemStack buildFactionHead(Faction faction, int rank) {
        String name = cfgString("faction-item.Name");
        List<String> lore = cfgStringList("faction-item.Lore");

        ItemStack skull = XMaterial.PLAYER_HEAD.parseItem();
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            setFactionOwner(meta, faction);
            meta.setDisplayName(TextUtil.parse(replacePlaceholders(name != null ? name : "", faction, rank, null)));
            List<String> parsed = new ArrayList<>();
            for (String line : replacePlaceholdersList(lore, faction, rank, null)) {
                parsed.add(TextUtil.parse(line));
            }
            meta.setLore(parsed);
            skull.setItemMeta(meta);
        }
        return skull;
    }

    private ItemStack buildOwnHead(FPlayer fme, Faction faction, int rank) {
        String name = cfgString("own-head-item.Name");
        List<String> lore = cfgStringList("own-head-item.Lore");

        ItemStack skull = XMaterial.PLAYER_HEAD.parseItem();
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            Player p = fme.getPlayer();
            if (p != null) {
                applySkullOwner(meta, Bukkit.getOfflinePlayer(p.getUniqueId()), p.getName());
            }
            meta.setDisplayName(TextUtil.parse(replacePlaceholders(name != null ? name : "", faction, rank, fme)));
            List<String> parsed = new ArrayList<>();
            for (String line : replacePlaceholdersList(lore, faction, rank, fme)) {
                parsed.add(TextUtil.parse(line));
            }
            meta.setLore(parsed);
            skull.setItemMeta(meta);
        }
        return skull;
    }

    private ItemStack buildNoFactionHead(FPlayer fme) {
        String name = cfgString("no-faction-head-item.Name");
        if (name == null) name = "&c&lNessuna Fazione";
        List<String> lore = cfgStringList("no-faction-head-item.Lore");
        if (lore == null || lore.isEmpty()) {
            lore = new ArrayList<>();
            lore.add("&7Non sei in nessuna fazione.");
            lore.add("");
            lore.add("&eUsa &f/f create <nome> &eper crearne una,");
            lore.add("&eoppure &f/f join <nome> &eper unirti.");
        }

        ItemStack skull = XMaterial.PLAYER_HEAD.parseItem();
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            Player p = fme != null ? fme.getPlayer() : null;
            if (p != null) {
                applySkullOwner(meta, Bukkit.getOfflinePlayer(p.getUniqueId()), p.getName());
            }
            meta.setDisplayName(TextUtil.parse(name));
            List<String> parsed = new ArrayList<>(lore.size());
            for (String line : lore) {
                parsed.add(TextUtil.parse(line == null ? "" : line));
            }
            meta.setLore(parsed);
            skull.setItemMeta(meta);
        }
        return skull;
    }

    private void setFactionOwner(SkullMeta meta, Faction faction) {
        UUID uuid = getFactionLeaderUuid(faction);
        if (uuid != null) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            applySkullOwner(meta, op, op.getName() != null ? op.getName() : getFactionLeaderName(faction));
            return;
        }

        String leaderName = getFactionLeaderName(faction);
        if (leaderName == null || leaderName.isEmpty()) return;

        OfflinePlayer owner = getOfflinePlayerIfCached(leaderName);
        applySkullOwner(meta, owner, leaderName);
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

    @SuppressWarnings("deprecation")
    private static OfflinePlayer getOfflinePlayerIfCached(String name) {
        try {
            return (OfflinePlayer) Bukkit.class
                    .getMethod("getOfflinePlayerIfCached", String.class)
                    .invoke(null, name);
        } catch (NoSuchMethodException ignored) {
            Player online = Bukkit.getPlayerExact(name);
            if (online != null) return online;
            return Bukkit.getOfflinePlayer(name);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private String getFactionLeaderName(Faction faction) {
        try {
            FPlayer leader = faction.getFPlayerLeader();
            if (leader != null && leader.getName() != null) {
                return leader.getName();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private UUID getFactionLeaderUuid(Faction faction) {
        try {
            FPlayer leader = faction.getFPlayerLeader();
            if (leader != null && leader.getId() != null) {
                return UUID.fromString(leader.getId());
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private String replacePlaceholders(String s, Faction f, int rank, FPlayer fme) {
        s = s.replace("{rank}", String.valueOf(rank))
                .replace("{faction}", f.getTag())
                .replace("{members}", String.valueOf(f.getFPlayers().size()))
                .replace("{power}", String.valueOf(f.getPowerRounded()))
                .replace("{balance}", String.valueOf(f.getFactionBalance()));
        if (fme != null && fme.getRole() != null) s = s.replace("{role}", fme.getRole().nicename);
        return s;
    }

    private List<String> replacePlaceholdersList(List<String> list, Faction f, int rank, FPlayer fme) {
        List<String> out = new ArrayList<>(list.size());
        for (String line : list) out.add(replacePlaceholders(line, f, rank, fme));
        return out;
    }

    private List<String> replacePage(List<String> list, int targetPage) {
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