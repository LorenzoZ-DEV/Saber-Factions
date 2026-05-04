package com.massivecraft.factions.cmd.baltop;

import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.integration.Econ;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BalTopCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }
        Player player = (Player) sender;

        if (!player.hasPermission("factions.baltop")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (Econ.getEconomy() == null) {
            player.sendMessage(ChatColor.RED + "Economy (Vault) is not available.");
            return true;
        }

        int page = 1;
        if (args.length >= 1) {
            try {
                page = Math.max(1, Integer.parseInt(args[0]));
            } catch (NumberFormatException ignored) {
            }
        }

        new BalTopGUI(player, page).openGUI(FactionsPlugin.getInstance());
        return true;
    }
}

