package com.massivecraft.factions;

import cc.javajobs.wgbridge.WorldGuardBridge;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.massivecraft.factions.addon.AddonManager;
import com.massivecraft.factions.addon.FactionsAddon;
import com.massivecraft.factions.cmd.CmdAutoHelp;
import com.massivecraft.factions.cmd.CommandContext;
import com.massivecraft.factions.cmd.FCmdRoot;
import com.massivecraft.factions.cmd.FCommand;
import com.massivecraft.factions.cmd.audit.FChestListener;
import com.massivecraft.factions.cmd.ftop.FTopGUIListener;
import com.massivecraft.factions.cmd.audit.FLogManager;
import com.massivecraft.factions.cmd.audit.FLogType;
import com.massivecraft.factions.cmd.chest.AntiChestListener;
import com.massivecraft.factions.cmd.reserve.ReserveAdapter;
import com.massivecraft.factions.cmd.reserve.ReserveObject;
import com.massivecraft.factions.data.helpers.FactionDataHelper;
import com.massivecraft.factions.data.listener.FactionDataListener;
import com.massivecraft.factions.listeners.*;
import com.massivecraft.factions.listeners.vspecific.ChorusFruitListener;
import com.massivecraft.factions.missions.MissionHandler;
import com.massivecraft.factions.missions.TributeInventoryHandler;
import com.massivecraft.factions.missions.impl.MissionHandlerModern;
import com.massivecraft.factions.struct.Relation;
import com.massivecraft.factions.struct.Role;
import com.massivecraft.factions.util.*;
import com.massivecraft.factions.util.adapters.*;
import com.massivecraft.factions.util.flight.FlightEnhance;
import com.massivecraft.factions.util.flight.stuct.AsyncPlayerMap;
import com.massivecraft.factions.util.timer.TimerManager;
import com.massivecraft.factions.zcore.CommandVisibility;
import com.massivecraft.factions.zcore.MPlugin;
import com.massivecraft.factions.zcore.file.impl.FileManager;
import com.massivecraft.factions.zcore.fperms.Access;
import com.massivecraft.factions.zcore.fperms.Permissable;
import com.massivecraft.factions.zcore.frame.fupgrades.UpgradesListener;
import com.massivecraft.factions.zcore.util.ShutdownParameter;
import com.massivecraft.factions.zcore.util.StartupParameter;
import com.massivecraft.factions.zcore.util.TextUtil;
import me.lucko.commodore.CommodoreProvider;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;


public class FactionsPlugin extends MPlugin {

    public static FactionsPlugin instance;
    private final Gson gsonSerializer = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().enableComplexMapKeySerialization().excludeFieldsWithModifiers(Modifier.TRANSIENT, Modifier.VOLATILE)
            .registerTypeAdapter(new TypeToken<Map<Permissable, Map<String, Access>>>() {
            }.getType(), new PermissionsMapTypeAdapter())
            .registerTypeAdapter(LazyLocation.class, new MyLocationTypeAdapter())
            .registerTypeAdapter(new TypeToken<Map<FLocation, Set<String>>>() {
            }.getType(), new MapFLocToStringSetTypeAdapter())
            .registerTypeAdapter(Inventory.class, new InventoryTypeAdapter())
            .registerTypeAdapter(ReserveObject.class, new ReserveAdapter())
            .registerTypeAdapter(Location.class, new LocationTypeAdapter())
            .registerTypeAdapterFactory(EnumTypeAdapter.ENUM_FACTORY)
            .create();

    public static boolean cachedRadiusClaim;

    public static Permission perms = null;
    private FactionDataHelper factionDataHelper;
    private Map<String, FactionsAddon> factionsAddonHashMap;
    private final HashMap<Faction, String> shieldStatMap = new HashMap<>();

    public static boolean startupFinished = false;
    public boolean PlaceholderApi;

    public FCmdRoot cmdBase;
    public CmdAutoHelp cmdAutoHelp;
    public short version;
    public List<String> itemList = getConfig().getStringList("fchest.Items-Not-Allowed");
    public FLogManager fLogManager;
    public List<ReserveObject> reserveObjects;
    public FileManager fileManager;
    public TimerManager timerManager;
    private FactionsPlayerListener factionsPlayerListener;
    private boolean locked = false;
    private Integer AutoLeaveTask = null;
    private ClipPlaceholderAPIManager clipPlaceholderAPIManager;

    public FactionsPlugin() {
        instance = this;
    }

    public static FactionsPlugin getInstance() {
        return instance;
    }

    public static boolean canPlayersJoin() {
        return startupFinished;
    }

    public FileManager getFileManager() {
        return fileManager;
    }

    public boolean getLocked() {
        return this.locked;
    }

    public void setLocked(boolean val) {
        this.locked = val;
        this.setAutoSave(val);
    }

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            Logger.print("You are missing dependencies!", Logger.PrefixType.FAILED);
            Logger.print("Please verify [Vault] is installed!", Logger.PrefixType.FAILED);
            Conf.save();
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.version = Short.parseShort(ReflectionUtils.PackageType.getServerVersion().split("_")[1]);

        if (!preEnable()) {
            this.loadSuccessful = false;
            return;
        }

        Conf.load();

        StartupParameter.initData(this, () -> {
            if (getConfig().getBoolean("enable-faction-flight", true)) {
                Bukkit.getServer().getScheduler().runTaskTimer(FactionsPlugin.getInstance(), new FlightEnhance(), 20L, 20L);
            }

            VersionProtocol.printVerionInfo();
            this.cmdBase = new FCmdRoot();
            this.cmdAutoHelp = new CmdAutoHelp();

            setupPermissions();

            if (Conf.worldGuardChecking || Conf.worldGuardBuildPriority) {
                Plugin plugin = Bukkit.getPluginManager().getPlugin("WorldGuard");
                if (plugin != null) {
                    new WorldGuardBridge().connect(this, true);
                }
            }

            startAutoLeaveTask(false);

            Bukkit.getPluginManager().registerEvents(new SaberGUIListener(), this);
            Bukkit.getPluginManager().registerEvents(new com.massivecraft.factions.listeners.PlayerRegistryListener(), this);
            for (org.bukkit.entity.Player online : Bukkit.getOnlinePlayers()) {
                com.massivecraft.factions.util.PlayerCacheManager.addPlayer(online);
            }

            if (getConfig().getBoolean("scoreboard.default-enabled", true)) {
                for (org.bukkit.entity.Player online : Bukkit.getOnlinePlayers()) {
                    FPlayer fp = FPlayers.getInstance().getByPlayer(online);
                    if (fp != null) {
                        com.massivecraft.factions.scoreboards.FScoreboard.init(fp);
                        com.massivecraft.factions.scoreboards.FScoreboard fsb =
                                com.massivecraft.factions.scoreboards.FScoreboard.get(fp);
                        if (fsb != null) {
                            fsb.setDefaultSidebar(new com.massivecraft.factions.scoreboards.sidebar.FDefaultSidebar());
                            fsb.setSidebarVisibility(fp.showScoreboard());
                        }
                    }
                }
            }
            Bukkit.getPluginManager().registerEvents(factionsPlayerListener = new FactionsPlayerListener(), this);

            if (Conf.userSpawnerChunkSystem) {
                Bukkit.getPluginManager().registerEvents(new SpawnerChunkListener(), this);
            }

            if (FactionsPlugin.getInstance().getConfig().getBoolean("disable-chorus-teleport-in-territory") && this.version > 8) {
                Bukkit.getPluginManager().registerEvents(new ChorusFruitListener(), this);
            }

            this.factionDataHelper = new FactionDataHelper(this.getDataFolder());
            Bukkit.getPluginManager().registerEvents(new FactionDataListener(this.factionDataHelper), this);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                for (Faction faction : Factions.getInstance().getAllNormalFactions()) {
                    this.factionDataHelper.getOrLoadFactionData(faction);
                }

                long ftopIntervalSeconds = getConfig().getLong("ftop.refresh-interval-seconds", 300L);
                long ftopIntervalTicks = Math.max(0L, ftopIntervalSeconds) * 20L;
                com.massivecraft.factions.cmd.ftop.FTopCache.getInstance()
                        .startScheduledRefresh(this, ftopIntervalTicks);

                long baltopIntervalSeconds = getConfig().getLong("baltop.refresh-interval-seconds", 300L);
                long baltopIntervalTicks = Math.max(0L, baltopIntervalSeconds) * 20L;
                com.massivecraft.factions.cmd.baltop.BalTopCache.getInstance()
                        .startScheduledRefresh(this, baltopIntervalTicks);
            }, 10L);

            if (version > 8) {
                Bukkit.getPluginManager().registerEvents(new MissionHandlerModern(), this);
            }

            for (Listener eventListener : new Listener[]{
                    new TributeInventoryHandler(),
                    new FactionsChatListener(),
                    new FactionsEntityListener(),
                    new FactionsExploitListener(),
                    new FactionsBlockListener(),
                    new UpgradesListener(),
                    new MissionHandler(this),
                    new FChestListener(),
                    new MenuListener(),
                    new AntiChestListener(),
                    new FTopGUIListener()
            })
                Bukkit.getPluginManager().registerEvents(eventListener, this);

            if (Conf.useGraceSystem) {
                Bukkit.getPluginManager().registerEvents(timerManager.graceTimer, this);
            }

            new AsyncPlayerMap(this);

            this.setupPlaceholderAPI();
            factionsAddonHashMap = new HashMap<>();
            AddonManager.getAddonManagerInstance().loadAddons();

            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!factionsAddonHashMap.isEmpty()) {
                    FCmdRoot.instance.addVariableCommands();
                    FCmdRoot.instance.rebuild();
                }
            }, 100);

            this.getCommand(refCommand).setExecutor(cmdBase);
            if (!CommodoreProvider.isSupported()) this.getCommand(refCommand).setTabCompleter(this);

            org.bukkit.command.PluginCommand baltopCmd = this.getCommand("baltop");
            if (baltopCmd != null) {
                baltopCmd.setExecutor(new com.massivecraft.factions.cmd.baltop.BalTopCommand());
            }


            this.postEnable();
            this.loadSuccessful = true;
            FactionsPlugin.startupFinished = true;
        });
    }

    private void setupPlaceholderAPI() {
        Plugin clip = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        if (clip != null && clip.isEnabled()) {
            this.clipPlaceholderAPIManager = new ClipPlaceholderAPIManager();
            if (this.clipPlaceholderAPIManager.register()) {
                PlaceholderApi = true;
                Logger.print("Successfully registered placeholders with PlaceholderAPI.", Logger.PrefixType.DEFAULT);
            } else {
                PlaceholderApi = false;
            }
        } else {
            PlaceholderApi = false;
        }

    }


    public HashMap<Faction, String> getShieldStatMap() {
        return shieldStatMap;
    }

    public Map<String, FactionsAddon> getFactionsAddonHashMap() {
        return factionsAddonHashMap;
    }

    public boolean isClipPlaceholderAPIHooked() {
        return this.clipPlaceholderAPIManager != null;
    }


    private void setupPermissions() {
        try {
            RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
            if (rsp != null) perms = rsp.getProvider();
        } catch (NoClassDefFoundError ignored) {
        }
    }

    @Override
    public Gson getGson() {
        return this.gsonSerializer;
    }

    @Override
    public void onDisable() {

        safeShutdown("ShutdownParameter", () -> ShutdownParameter.initShutdown(this));
        safeShutdown("PlayerCacheManager", com.massivecraft.factions.util.PlayerCacheManager::clear);
        safeShutdown("FTopCache",         () -> com.massivecraft.factions.cmd.ftop.FTopCache.getInstance().clear());
        safeShutdown("BalTopCache",       () -> com.massivecraft.factions.cmd.baltop.BalTopCache.getInstance().clear());

        safeShutdown("AutoLeaveTask", () -> {
            if (this.AutoLeaveTask != null) {
                getServer().getScheduler().cancelTask(this.AutoLeaveTask);
                this.AutoLeaveTask = null;
            }
        });

        safeShutdown("Audiences", () -> {
            if (TextUtil.AUDIENCES != null) TextUtil.AUDIENCES.close();
        });

        safeShutdown("FactionDataHelper", () -> {
            if (this.factionDataHelper != null) {
                this.factionDataHelper.saveAllCachedData();
                this.factionDataHelper.shutdown();
            }
        });

        try {
            super.onDisable();
        } catch (Throwable t) {
            getLogger().log(java.util.logging.Level.SEVERE, "super.onDisable() failed", t);
        }
    }

    private void safeShutdown(String label, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            getLogger().log(java.util.logging.Level.SEVERE,
                    "Shutdown step '" + label + "' failed (jar may be corrupt or out of date)", t);
        }
    }

    public void startAutoLeaveTask(boolean restartIfRunning) {
        if (AutoLeaveTask != null) {
            if (!restartIfRunning) return;
            this.getServer().getScheduler().cancelTask(AutoLeaveTask);
        }

        if (Conf.useAutoLeaveAndDisbandSystem) {
            if (Conf.autoLeaveRoutineRunsEveryXMinutes > 0.0) {
                long ticks = (long) (20 * 60 * Conf.autoLeaveRoutineRunsEveryXMinutes);
                AutoLeaveTask = getServer().getScheduler().scheduleSyncRepeatingTask(this, new AutoLeaveTask(), ticks, ticks);
            }
        }
    }

    @Override
    public void postAutoSave() {
        Conf.save();
    }


    public Economy getEcon() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        return rsp.getProvider();
    }


    @Override
    public boolean logPlayerCommands() {
        return Conf.logPlayerCommands;
    }

    @Override
    public boolean handleCommand(CommandSender sender, String commandString, boolean testOnly) {
        return sender instanceof Player && FactionsPlayerListener.preventCommand(commandString, (Player) sender) || super.handleCommand(sender, commandString, testOnly);
    }


    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> argsList = new LinkedList<>(Arrays.asList(args));
        CommandContext context = new CommandContext(sender, argsList, alias);
        List<FCommand> commandsList = cmdBase.getSubCommands();
        FCommand commandsEx = cmdBase;
        List<String> completions = new ArrayList<>();

        if (context.args.get(0).isEmpty()) {
            for (FCommand subCommand : commandsEx.getSubCommands()) {
                if (subCommand.getRequirements().isPlayerOnly()
                        && sender.hasPermission(subCommand.getRequirements().getPermission().node)
                        && subCommand.getVisibility() != CommandVisibility.INVISIBLE) {
                    completions.addAll(subCommand.getAliases());
                }
            }
            return completions;
        }

        if (context.args.size() == 1) {
            for (; !commandsList.isEmpty() && !context.args.isEmpty(); context.args.remove(0)) {
                String cmdName = context.args.get(0).toLowerCase();
                boolean found = false;

                for (FCommand fCommand : commandsList) {
                    for (String s : fCommand.getAliases()) {
                        if (s.startsWith(cmdName)) {
                            commandsList = fCommand.getSubCommands();
                            completions.addAll(fCommand.getAliases());
                            found = true;
                            break;
                        }
                    }
                    if (found) break;
                }
            }

            String lastArg = args[args.length - 1].toLowerCase();
            return completions.stream()
                    .filter(name -> name.toLowerCase().startsWith(lastArg))
                    .collect(Collectors.toList());
        }

        String lastArgName = args.length >= 2 ? args[args.length - 2].toLowerCase() : "";
        String currentArg = args[args.length - 1].toLowerCase();

        if (lastArgName.equals("player")
                || lastArgName.equals("target")
                || lastArgName.equals("name")
                || lastArgName.equals("faction")
                || lastArgName.equals("faction tag")) {
            for (Player player : Bukkit.getServer().getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(currentArg)) {
                    completions.add(player.getName());
                }
            }
            return completions;
        }

        for (Role value : Role.VALUES) completions.add(value.nicename);
        for (Relation value : Relation.VALUES) completions.add(value.nicename);
        for (Player player : Bukkit.getServer().getOnlinePlayers()) completions.add(player.getName());
        for (Faction faction : Factions.getInstance().getAllFactions())
            completions.add(ChatColor.stripColor(faction.getTag()));

        return completions.stream()
                .filter(name -> name.toLowerCase().startsWith(currentArg))
                .collect(Collectors.toList());
    }

    public void handleFactionTagExternally(boolean notByFactions) {
        Conf.chatTagHandledByAnotherPlugin = notByFactions;
    }

    public FLogManager getFlogManager() {
        return fLogManager;
    }

    public void logFactionEvent(Faction faction, FLogType type, String... arguments) {
        this.fLogManager.log(faction, type, arguments);
    }


    public List<ReserveObject> getFactionReserves() {
        return this.reserveObjects;
    }


    public String getPrimaryGroup(OfflinePlayer player) {
        return perms == null || !perms.hasGroupSupport() ? " " : perms.getPrimaryGroup(Bukkit.getWorlds().get(0).toString(), player);
    }

    public FactionDataHelper getFactionDataHelper() {
        return factionDataHelper;
    }

    public TimerManager getTimerManager() {
        return timerManager;
    }


    public FactionsPlayerListener getFactionsPlayerListener() {
        return this.factionsPlayerListener;
    }
}
