package org.saberdev.corex.addons;

import com.massivecraft.factions.util.PlayerDataRegistry;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.saberdev.corex.CoreAddon;

@CoreAddon(configVariable = "Armor-Swap")
public class ArmorSwap implements Listener {
    public boolean isArmor(Material m) {
        String n = m.toString().toLowerCase();
        if (m == null || m == Material.AIR)
            return false;
        return (n.contains("helmet") || n.contains("chestplate") || n.contains("leggings") || n.contains("boots"));
    }

    public void equipArmor(Player player, ItemStack is) {
        if (is == null || is.getType() == Material.AIR)
            return;
        String n = is.getType().toString().toLowerCase();
        PlayerInventory inv = player.getInventory();
        if (n.contains("helmet")) {
            ItemStack old = inv.getHelmet();
            inv.setHelmet(is);
            inv.removeItem(is);
            if (old != null && old.getType() != Material.AIR)
                inv.setItemInHand(old);
        } else if (n.contains("chestplate")) {
            ItemStack old = inv.getChestplate();
            inv.setChestplate(is);
            inv.removeItem(is);
            if (old != null && old.getType() != Material.AIR)
                inv.setItemInHand(old);
        } else if (n.contains("leggings")) {
            ItemStack old = inv.getLeggings();
            inv.setLeggings(is);
            inv.removeItem(is);
            if (old != null && old.getType() != Material.AIR)
                inv.setItemInHand(old);
        } else if (n.contains("boots")) {
            ItemStack old = inv.getBoots();
            inv.setBoots(is);
            inv.removeItem(is);
            if (old != null && old.getType() != Material.AIR)
                inv.setItemInHand(old);
        }
        player.updateInventory();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        if (e.getPlayer().getItemInHand() == null || !isArmor(e.getPlayer().getItemInHand().getType()))
            return;
        if (e.hasBlock() && (e
                .getClickedBlock().getState() instanceof org.bukkit.block.Chest || e
                .getClickedBlock().getState() instanceof org.bukkit.block.Dispenser || e
                .getClickedBlock().getState() instanceof org.bukkit.block.Dropper || e
                .getClickedBlock().getState() instanceof org.bukkit.block.Furnace || e
                .getClickedBlock().getState() instanceof org.bukkit.block.Hopper || e
                .getClickedBlock().getType() == Material.GOLD_BLOCK || e
                .getClickedBlock().getType() == Material.IRON_BLOCK))
            return;
        String worldName = e.getPlayer().getWorld().getName();
        Player p = e.getPlayer();
        long lastSwap = PlayerDataRegistry.getExpireAt(p.getUniqueId(), PlayerDataRegistry.TS_LAST_ARMOR_SWAP);
        if (lastSwap != 0L && System.currentTimeMillis() - lastSwap <= 12L)
            return;
        if (e.isCancelled() && PlayerDataRegistry.hasFlag(p.getUniqueId(), PlayerDataRegistry.FLAG_NO_ARMOR_SWAP))
            return;
        e.setCancelled(true);
        e.setUseInteractedBlock(Event.Result.DENY);
        e.setUseItemInHand(Event.Result.DENY);
        PlayerDataRegistry.setExpireAt(p.getUniqueId(), PlayerDataRegistry.TS_LAST_ARMOR_SWAP, System.currentTimeMillis());
        equipArmor(p, e.getItem());
    }
}
