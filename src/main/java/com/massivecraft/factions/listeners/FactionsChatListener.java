package com.massivecraft.factions.listeners;

import com.massivecraft.factions.*;
import com.massivecraft.factions.struct.ChatMode;
import com.massivecraft.factions.struct.Relation;
import com.massivecraft.factions.struct.Role;
import com.massivecraft.factions.util.Logger;
import com.massivecraft.factions.util.WarmUpUtil;
import com.massivecraft.factions.zcore.util.TL;
import com.massivecraft.factions.zcore.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Collection;
import java.util.UnknownFormatConversionException;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FactionsChatListener implements Listener {

    private static final Pattern CHAT_PATTERN = Pattern.compile("(?s).");
    private static final Pattern COLOR_ONLY_PATTERN = Pattern.compile("(?i)&([0-9A-FR])");
    private static final Pattern FULL_FORMAT_PATTERN = Pattern.compile("(?i)&([0-9A-FK-OR])");
    private static final Pattern FORMAT_TOKEN_PATTERN = Pattern.compile("%(?:\\d+\\$)?[sdf]");

    private static String sanitizeChatFormat(String input) {
        if (input == null || input.indexOf('%') < 0) return input;
        Matcher m = FORMAT_TOKEN_PATTERN.matcher(input);
        StringBuilder out = new StringBuilder(input.length() + 8);
        int last = 0;
        while (m.find()) {

            out.append(input, last, m.start());
            int segStart = out.length() - (m.start() - last);

            String escaped = out.substring(segStart).replace("%", "%%");

            out.setLength(segStart);
            out.append(escaped);
            out.append(m.group());
            last = m.end();
        }
        out.append(input.substring(last).replace("%", "%%"));
        return out.toString();
    }

    private static String translateChatColors(Player player, String message) {
        FileConfiguration cfg = FactionsPlugin.getInstance().getConfig();
        if (!cfg.getBoolean("chat.allow-color-codes.enabled", true)) return message;
        String permission = cfg.getString("chat.allow-color-codes.permission", "factions.chat.color");
        if (permission != null && !permission.isEmpty() && !player.hasPermission(permission)) return message;
        boolean allowFormat = cfg.getBoolean("chat.allow-color-codes.allow-format-codes", true);
        Pattern pattern = allowFormat ? FULL_FORMAT_PATTERN : COLOR_ONLY_PATTERN;
        return pattern.matcher(message).replaceAll("\u00A7$1");
    }

    private static String resolvePapi(Player player, String input) {
        if (input == null || player == null || input.indexOf('%') < 0) return input;
        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, input);
        } catch (NoClassDefFoundError | Exception ignored) {
            return input;
        }
    }

    private static boolean applyCustomFormat(AsyncPlayerChatEvent event, Player player, String rawFormat, String msg, String configPath) {
        String coloured = ChatColor.translateAlternateColorCodes('&', rawFormat);
        String safe = sanitizeChatFormat(coloured);
        String displayName = ChatColor.translateAlternateColorCodes('&', player.getDisplayName());
        String colouredMsg = ChatColor.translateAlternateColorCodes('&', msg);
        String formatted;
        try {
            formatted = String.format(safe, displayName, colouredMsg);
        } catch (java.util.IllegalFormatException ex) {
            Logger.print("Invalid " + configPath + ": " + ex.getMessage(), Logger.PrefixType.FAILED);
            return false;
        }
        String finalLine = resolvePapi(player, formatted);
        finalLine = ChatColor.translateAlternateColorCodes('&', finalLine);
        Bukkit.broadcastMessage(finalLine);
        event.setCancelled(true);
        return true;
    }

    private static String buildChatLine(Player player, String configPath, String fallback, Object... args) {
        FileConfiguration cfg = FactionsPlugin.getInstance().getConfig();
        String raw = configPath != null ? cfg.getString(configPath, fallback) : fallback;
        if (raw == null || raw.isEmpty()) raw = fallback;
        String coloured = ChatColor.translateAlternateColorCodes('&', raw);
        String safe = sanitizeChatFormat(coloured);
        String formatted;
        try {
            formatted = String.format(safe, args);
        } catch (java.util.IllegalFormatException ex) {
            Logger.print("Invalid chat format at '" + configPath + "': " + ex.getMessage(), Logger.PrefixType.FAILED);
            StringBuilder sb = new StringBuilder();
            for (Object a : args) sb.append(a).append(' ');
            return sb.toString().trim();
        }
        String parsed = com.massivecraft.factions.tag.Tag.parsePlaceholders(player, formatted);
        return ChatColor.translateAlternateColorCodes('&', parsed);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerEarlyChat(AsyncPlayerChatEvent event) {
        Player talkingPlayer = event.getPlayer();
        String msg = translateChatColors(talkingPlayer, event.getMessage());
        FPlayer me = FPlayers.getInstance().getByPlayer(talkingPlayer);
        ChatMode chat = me.getChatMode();
        Faction myFaction = me.getFaction();
        String nameAndTag = ChatColor.stripColor(me.getNameAndTag());

        if (!msg.equals(event.getMessage())) {
            event.setMessage(msg);
        }

        if (me.isEnteringPassword()) {
            event.setCancelled(true);
            String censoredMessage = ChatColor.DARK_GRAY + CHAT_PATTERN.matcher(msg).replaceAll("*");
            me.sendMessage(censoredMessage);

            if (myFaction.isWarpPassword(me.getEnteringWarp(), msg)) {
                doWarmup(me.getEnteringWarp(), me);
            } else {
                me.msg(TL.COMMAND_FWARP_INVALID_PASSWORD);
            }

            me.setEnteringPassword(false, "");
            return;
        }

        if (chat == ChatMode.MOD && me.getRole().isAtLeast(Role.MODERATOR)) {
            String modMessage = buildChatLine(talkingPlayer, "chat.mod.format", Conf.modChatFormat, nameAndTag, msg);
            Collection<FPlayer> modPlayers = myFaction.getFPlayers().stream()
                    .filter(fplayer -> fplayer.getRole().isAtLeast(Role.MODERATOR))
                    .collect(Collectors.toList());

            for (FPlayer fplayer : modPlayers) {
                fplayer.sendMessage(modMessage);
            }

            Collection<FPlayer> spyingPlayers = myFaction.getFPlayers().stream()
                    .filter(fplayer -> fplayer.isSpyingChat() && me != fplayer)
                    .collect(Collectors.toList());

            for (FPlayer fplayer : spyingPlayers) {
                fplayer.sendMessage("[MCspy]: " + modMessage);
            }

            Bukkit.getLogger().log(Level.INFO, "Mod Chat: " + modMessage);
            event.setCancelled(true);
        } else if (chat == ChatMode.FACTION) {
            String factionMessage = buildChatLine(talkingPlayer, "chat.faction.format", Conf.factionChatFormat, me.describeTo(myFaction), msg);
            myFaction.sendMessage(factionMessage);

            Collection<FPlayer> spyingPlayers = FPlayers.getInstance().getOnlinePlayers().stream()
                    .filter(fplayer -> fplayer.isSpyingChat() && fplayer.getFaction() != myFaction && me != fplayer)
                    .collect(Collectors.toList());

            for (FPlayer fplayer : spyingPlayers) {
                fplayer.sendMessage("[FCspy] " + myFaction.getTag() + ": " + factionMessage);
            }

            Bukkit.getLogger().log(Level.INFO, "FactionChat " + myFaction.getTag() + ": " + factionMessage);
            event.setCancelled(true);
        } else if (chat == ChatMode.ALLIANCE) {
            String allianceMessage = buildChatLine(talkingPlayer, "chat.alliance.format", Conf.allianceChatFormat, nameAndTag, msg);
            myFaction.sendMessage(allianceMessage);

            Collection<FPlayer> alliancePlayers = FPlayers.getInstance().getOnlinePlayers().stream()
                    .filter(fplayer -> myFaction.getRelationTo(fplayer) == Relation.ALLY && !fplayer.isIgnoreAllianceChat())
                    .collect(Collectors.toList());

            for (FPlayer fplayer : alliancePlayers) {
                fplayer.sendMessage(allianceMessage);
            }

            Collection<FPlayer> spyingPlayers = FPlayers.getInstance().getOnlinePlayers().stream()
                    .filter(fplayer -> fplayer.isSpyingChat() && me != fplayer)
                    .collect(Collectors.toList());

            for (FPlayer fplayer : spyingPlayers) {
                fplayer.sendMessage("[ACspy]: " + allianceMessage);
            }

            Bukkit.getLogger().log(Level.INFO, "AllianceChat: " + allianceMessage);
            event.setCancelled(true);
        } else if (chat == ChatMode.TRUCE) {
            String truceMessage = buildChatLine(talkingPlayer, "chat.truce.format", Conf.truceChatFormat, nameAndTag, msg);
            myFaction.sendMessage(truceMessage);

            Collection<FPlayer> trucePlayers = FPlayers.getInstance().getOnlinePlayers().stream()
                    .filter(fplayer -> myFaction.getRelationTo(fplayer) == Relation.TRUCE)
                    .collect(Collectors.toList());

            for (FPlayer fplayer : trucePlayers) {
                fplayer.sendMessage(truceMessage);
            }

            Collection<FPlayer> spyingPlayers = FPlayers.getInstance().getOnlinePlayers().stream()
                    .filter(fplayer -> fplayer.isSpyingChat() && fplayer != me)
                    .collect(Collectors.toList());

            for (FPlayer fplayer : spyingPlayers) {
                fplayer.sendMessage("[TCspy]: " + truceMessage);
            }

            Bukkit.getLogger().log(Level.INFO, "TruceChat: " + truceMessage);
            event.setCancelled(true);
        }
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {

        if (!Conf.chatTagEnabled || Conf.chatTagHandledByAnotherPlugin) {
            return;
        }

        Player talkingPlayer = event.getPlayer();
        String msg = event.getMessage();
        String eventFormat = event.getFormat();
        FPlayer me = FPlayers.getInstance().getByPlayer(talkingPlayer);
        int insertIndex;

        if (!me.hasFaction()) {
            FileConfiguration cfg = FactionsPlugin.getInstance().getConfig();
            if (cfg.getBoolean("chat.no-faction.enabled", true)) {
                String rawFormat = cfg.getString("chat.no-faction.format", "");
                if (rawFormat != null && !rawFormat.isEmpty()) {
                    if (applyCustomFormat(event, talkingPlayer, rawFormat, msg, "chat.no-faction.format")) {
                        return;
                    }
                }
            }
        } else {
            FileConfiguration cfg = FactionsPlugin.getInstance().getConfig();
            if (cfg.getBoolean("chat.public.enabled", true)) {
                String rawFormat = cfg.getString("chat.public.format", "");
                if (rawFormat != null && !rawFormat.isEmpty()) {
                    if (applyCustomFormat(event, talkingPlayer, rawFormat, msg, "chat.public.format")) {
                        return;
                    }
                }
            }
        }

        if (!Conf.chatTagReplaceString.isEmpty() && eventFormat.contains(Conf.chatTagReplaceString)) {
            eventFormat = TextUtil.replace(eventFormat, "[FACTION_TITLE]", me.getTitle());

            insertIndex = eventFormat.indexOf(Conf.chatTagReplaceString);
            eventFormat = TextUtil.replace(eventFormat, Conf.chatTagReplaceString, "");
            Conf.chatTagPadAfter = false;
            Conf.chatTagPadBefore = false;
        } else if (!Conf.chatTagInsertAfterString.isEmpty() && eventFormat.contains(Conf.chatTagInsertAfterString)) {
            insertIndex = eventFormat.indexOf(Conf.chatTagInsertAfterString) + Conf.chatTagInsertAfterString.length();
        } else if (!Conf.chatTagInsertBeforeString.isEmpty() && eventFormat.contains(Conf.chatTagInsertBeforeString)) {
            insertIndex = eventFormat.indexOf(Conf.chatTagInsertBeforeString);
        } else {
            insertIndex = Conf.chatTagInsertIndex;
            if (insertIndex > eventFormat.length()) {
                return;
            }
        }

        String formatStart = eventFormat.substring(0, insertIndex) + ((Conf.chatTagPadBefore && !me.getChatTag().isEmpty()) ? " " : "");
        String formatEnd = ((Conf.chatTagPadAfter && !me.getChatTag().isEmpty()) ? " " : "") + eventFormat.substring(insertIndex);

        String nonColoredMsgFormat = formatStart + me.getChatTag().trim() + formatEnd;

        if (Conf.chatTagRelationColored) {
            for (Player listeningPlayer : event.getRecipients()) {
                FPlayer you = FPlayers.getInstance().getByPlayer(listeningPlayer);
                String yourFormat = formatStart + me.getChatTag(you).trim() + formatEnd;
                try {
                    listeningPlayer.sendMessage(String.format(yourFormat, talkingPlayer.getDisplayName(), msg));
                } catch (UnknownFormatConversionException ex) {
                    Conf.chatTagInsertIndex = 0;
                    Logger.print("Critical error in chat message formatting!", Logger.PrefixType.FAILED);
                    Logger.print("NOTE: This has been automatically fixed right now by setting chatTagInsertIndex to 0.", Logger.PrefixType.FAILED);
                    Logger.print("For a more proper fix, please read this regarding chat configuration: http://massivecraft.com/plugins/factions/config#Chat_configuration", Logger.PrefixType.FAILED);
                    return;
                }
            }

            event.getRecipients().clear();
        }
        event.setFormat(nonColoredMsgFormat);
    }

    private void doWarmup(final String warp, final FPlayer fme) {
        WarmUpUtil.process(fme, WarmUpUtil.Warmup.WARP, TL.WARMUPS_NOTIFY_TELEPORT, warp, () -> {
            Player player = fme.getPlayer();
            if (player != null) {
                player.teleport(fme.getFaction().getWarp(warp).getLocation());
                fme.msg(TL.COMMAND_FWARP_WARPED, warp);
            }
        }, FactionsPlugin.getInstance().getConfig().getLong("warmups.f-warp", 10));
    }

}
