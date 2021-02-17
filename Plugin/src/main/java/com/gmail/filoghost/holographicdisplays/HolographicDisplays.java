package com.gmail.filoghost.holographicdisplays;

import com.comphenix.protocol.ProtocolLibrary;
import com.gmail.filoghost.holographicdisplays.api.internal.BackendAPI;
import com.gmail.filoghost.holographicdisplays.bridge.bungeecord.BungeeServerTracker;
import com.gmail.filoghost.holographicdisplays.bridge.protocollib.ProtocolLibHook;
import com.gmail.filoghost.holographicdisplays.commands.main.HologramsCommandHandler;
import com.gmail.filoghost.holographicdisplays.disk.Configuration;
import com.gmail.filoghost.holographicdisplays.disk.HologramDatabase;
import com.gmail.filoghost.holographicdisplays.disk.UnicodeSymbols;
import com.gmail.filoghost.holographicdisplays.exception.HologramNotFoundException;
import com.gmail.filoghost.holographicdisplays.exception.InvalidFormatException;
import com.gmail.filoghost.holographicdisplays.exception.WorldNotFoundException;
import com.gmail.filoghost.holographicdisplays.listener.HologramsPacketListener;
import com.gmail.filoghost.holographicdisplays.listener.MainListener;
import com.gmail.filoghost.holographicdisplays.nms.interfaces.NMSManager;
import com.gmail.filoghost.holographicdisplays.object.*;
import com.gmail.filoghost.holographicdisplays.placeholder.AnimationsRegister;
import com.gmail.filoghost.holographicdisplays.placeholder.PlaceholdersManager;
import com.gmail.filoghost.holographicdisplays.task.BungeeCleanupTask;
import com.gmail.filoghost.holographicdisplays.task.StartupLoadHologramsTask;
import com.gmail.filoghost.holographicdisplays.task.WorldPlayerCounterTask;
import com.gmail.filoghost.holographicdisplays.util.MinecraftVersion;
import com.gmail.filoghost.holographicdisplays.util.VersionUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.craftbukkit.v1_7_R4.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HolographicDisplays extends JavaPlugin implements Listener {
    private static Set<Player> newProtocolPlayers;

    // The main instance of the plugin.
    private static HolographicDisplays instance;

    // The manager for net.minecraft.server access.
    private static NMSManager nmsManager;

    // The listener for all the Bukkit and NMS events.
    private static MainListener mainListener;
    // Not null if ProtocolLib is installed and successfully loaded.
    private static ProtocolLibHook protocolLibHook;
    // The command handler, just in case a plugin wants to register more commands.
    private HologramsCommandHandler commandHandler;

    public static boolean hasNewProtocol(Player player) {
        return newProtocolPlayers.contains(player);
    }

    public static NMSManager getNMSManager() {
        return nmsManager;
    }

    public static MainListener getMainListener() {
        return mainListener;
    }

    private static void printWarnAndDisable(String... messages) {
        StringBuffer buffer = new StringBuffer("\n ");
        for (String message : messages) {
            buffer.append('\n');
            buffer.append(message);
        }
        buffer.append('\n');
        System.out.println(buffer.toString());
        try {
            Thread.sleep(5000);
        } catch (InterruptedException ex) {
        }
        instance.setEnabled(false);
    }

    public static HolographicDisplays getInstance() {
        return instance;
    }

    public static boolean hasProtocolLibHook() {
        return protocolLibHook != null;
    }

    public static ProtocolLibHook getProtocolLibHook() {
        return protocolLibHook;
    }

    @Override
    public void onEnable() {

        // Warn about plugin reloaders and the /reload command.
        if (instance != null || System.getProperty("HolographicDisplaysLoaded") != null) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[HolographicDisplays] Please do not use /reload or plugin reloaders. Use the command \"/holograms reload\" instead. You will receive no support for doing this operation.");
        }

        System.setProperty("HolographicDisplaysLoaded", "true");
        instance = this;

        // Load placeholders.yml.
        UnicodeSymbols.load(this);

        // Load the configuration.
        Configuration.load(this);

        //Patch
        newProtocolPlayers = Collections.synchronizedSet(new HashSet<Player>());
        ProtocolLibrary.getProtocolManager().addPacketListener(new HologramsPacketListener(this));
        Bukkit.getPluginManager().registerEvents(this, this);

        String version = VersionUtils.getBukkitVersion();

        if (version == null) {
            version = VersionUtils.getMinecraftVersion();

            if ("1.7.10".equals(version)) {
                version = "v1_7_R4";
            } else if ("1.8.8".equals(version)) {
                version = "v1_8_R3";
            } else {
                // Cannot definitely get the version. This will cause the plugin to disable itself.
                version = null;
            }
        }

        // It's simple, we don't need reflection.
        if ("v1_7_R4".equals(version)) {
            MinecraftVersion.set(MinecraftVersion.v1_7);
        } else if ("v1_8_R3".equals(version)) {
            MinecraftVersion.set(MinecraftVersion.v1_8);

        } else {
            printWarnAndDisable(
                    "******************************************************",
                    "     This version of HolographicDisplays only",
                    "     works on server versions from 1.7 to 1.8.",
                    "     The plugin will be disabled.",
                    "******************************************************"
            );
            return;
        }

        try {
            nmsManager = (NMSManager) Class.forName("com.gmail.filoghost.holographicdisplays.nms." + version + ".NmsManagerImpl").getConstructor().newInstance();
        } catch (Throwable t) {
            t.printStackTrace();
            printWarnAndDisable(
                    "******************************************************",
                    "     HolographicDisplays was unable to instantiate",
                    "     the NMS manager. The plugin will be disabled.",
                    "******************************************************"
            );
            return;
        }

        try {
            if (VersionUtils.isMCPCOrCauldron()) {
                getLogger().info("Trying to enable Cauldron/MCPC+ support...");
            }

            nmsManager.setup();

            if (VersionUtils.isMCPCOrCauldron()) {
                getLogger().info("Successfully added support for Cauldron/MCPC+!");
            }

        } catch (Exception e) {
            e.printStackTrace();
            printWarnAndDisable(
                    "******************************************************",
                    "     HolographicDisplays was unable to register",
                    "     custom entities, the plugin will be disabled.",
                    "     Are you using the correct Bukkit/Spigot version?",
                    "******************************************************"
            );
            return;
        }

        // ProtocolLib check.
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {

                String requiredVersionError = null;

                try {
                    String protocolVersion = Bukkit.getPluginManager().getPlugin("ProtocolLib").getDescription().getVersion();
                    Matcher versionNumbersMatcher = Pattern.compile("([0-9\\.])+").matcher(protocolVersion);

                    if (versionNumbersMatcher.find()) {
                        String versionNumbers = versionNumbersMatcher.group();

                        if (MinecraftVersion.get() == MinecraftVersion.v1_7) {
                            if (!VersionUtils.isVersionBetweenEqual(versionNumbers, "3.6.4", "3.7.0")) {
                                requiredVersionError = "between 3.6.4 and 3.7.0";
                            }
                        } else if (MinecraftVersion.get() == MinecraftVersion.v1_8) {
                            if (!VersionUtils.isVersionBetweenEqual(versionNumbers, "3.6.4", "3.6.5") && !VersionUtils.isVersionGreaterEqual(versionNumbers, "4.1")) {
                                requiredVersionError = "between 3.6.4 and 3.6.5 or higher than 4.1";
                            }
                        } else {
                            if (!VersionUtils.isVersionGreaterEqual(versionNumbers, "4.0")) {
                                requiredVersionError = "higher than 4.0";
                            }
                        }

                    } else {
                        throw new RuntimeException("could not find version numbers pattern");
                    }

                } catch (Exception e) {
                    getLogger().warning("Could not check ProtocolLib version (" + e.getClass().getName() + ": " + e.getMessage() + "), enabling support anyway and hoping for the best. If you get errors, please contact the author.");
                }

                if (requiredVersionError == null) {
                    ProtocolLibHook protocolLibHook;

                    if (VersionUtils.classExists("com.comphenix.protocol.wrappers.WrappedDataWatcher$WrappedDataWatcherObject")) {
                        // Only the new version contains this class
                        getLogger().info("Found ProtocolLib, using new version.");
                        protocolLibHook = new com.gmail.filoghost.holographicdisplays.bridge.protocollib.current.ProtocolLibHookImpl();
                    } else {
                        getLogger().info("Found ProtocolLib, using old version.");
                        protocolLibHook = new com.gmail.filoghost.holographicdisplays.bridge.protocollib.old.ProtocolLibHookImpl();
                    }

                    if (protocolLibHook.hook(this, nmsManager)) {
                        HolographicDisplays.protocolLibHook = protocolLibHook;
                        getLogger().info("Enabled player relative placeholders with ProtocolLib.");
                    }

                } else {
                    Bukkit.getConsoleSender().sendMessage(
                            ChatColor.RED + "[Holographic Displays] Detected incompatible version of ProtocolLib, support disabled. " +
                                    "For this server version you must be using a ProtocolLib version " + requiredVersionError + ".");
                }
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            getLogger().warning("Failed to load ProtocolLib support. Is it updated?");
        }

        // Load animation files and the placeholder manager.
        PlaceholdersManager.load(this);
        try {
            AnimationsRegister.loadAnimations(this);
        } catch (Exception ex) {
            ex.printStackTrace();
            getLogger().warning("Failed to load animation files!");
        }

        // Initalize other static classes.
        HologramDatabase.loadYamlFile(this);
        BungeeServerTracker.startTask(Configuration.bungeeRefreshSeconds);

        // Start repeating tasks.
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new BungeeCleanupTask(), 5 * 60 * 20, 5 * 60 * 20);
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new WorldPlayerCounterTask(), 0L, 3 * 20);

        Set<String> savedHologramsNames = HologramDatabase.getHolograms();
        if (savedHologramsNames != null && savedHologramsNames.size() > 0) {
            for (String singleHologramName : savedHologramsNames) {
                try {
                    NamedHologram singleHologram = HologramDatabase.loadHologram(singleHologramName);
                    NamedHologramManager.addHologram(singleHologram);
                } catch (HologramNotFoundException e) {
                    getLogger().warning("Hologram '" + singleHologramName + "' not found, skipping it.");
                } catch (InvalidFormatException e) {
                    getLogger().warning("Hologram '" + singleHologramName + "' has an invalid location format.");
                } catch (WorldNotFoundException e) {
                    getLogger().warning("Hologram '" + singleHologramName + "' was in the world '" + e.getMessage() + "' but it wasn't loaded.");
                } catch (Exception e) {
                    e.printStackTrace();
                    getLogger().warning("Unhandled exception while loading the hologram '" + singleHologramName + "'. Please contact the developer.");
                }
            }
        }

        if (getCommand("holograms") == null) {
            printWarnAndDisable(
                    "******************************************************",
                    "     HolographicDisplays was unable to register",
                    "     the command \"holograms\". Do not modify",
                    "     plugin.yml removing commands, if this is",
                    "     the case.",
                    "******************************************************"
            );
            return;
        }

        getCommand("holograms").setExecutor(commandHandler = new HologramsCommandHandler());
        Bukkit.getPluginManager().registerEvents(mainListener = new MainListener(nmsManager), this);

        // The entities are loaded when the server is ready.
        Bukkit.getScheduler().scheduleSyncDelayedTask(this, new StartupLoadHologramsTask(), 10L);

        // Enable the API.
        BackendAPI.setImplementation(new DefaultBackendAPI());
    }

    @Override
    public void onDisable() {
        for (NamedHologram hologram : NamedHologramManager.getHolograms()) {
            hologram.despawnEntities();
        }
        for (PluginHologram hologram : PluginHologramManager.getHolograms()) {
            hologram.despawnEntities();
        }
    }

    //PATCH
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if ((((CraftPlayer) event.getPlayer()).getHandle()).playerConnection.networkManager.getVersion() > 5)
            newProtocolPlayers.add(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        newProtocolPlayers.remove(event.getPlayer());
    }

    public HologramsCommandHandler getCommandHandler() {
        return commandHandler;
    }

}
