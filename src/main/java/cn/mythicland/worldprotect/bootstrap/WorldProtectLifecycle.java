package cn.mythicland.worldprotect.bootstrap;

import cn.mythicland.lib.api.LibApi;
import cn.mythicland.lib.bootstrap.LibPluginLifecycle;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.lib.command.CommandRouter;
import cn.mythicland.lib.config.ConfigSupport;
import cn.mythicland.worldprotect.WorldProtectPlugin;
import cn.mythicland.worldprotect.api.WorldProtectApi;
import cn.mythicland.worldprotect.command.EditCommand;
import cn.mythicland.worldprotect.command.ReloadCommand;
import cn.mythicland.worldprotect.config.WorldProtectSettings;
import cn.mythicland.worldprotect.integration.worldmanager.WorldManagerIntegration;
import cn.mythicland.worldprotect.listener.WorldProtectListener;
import cn.mythicland.worldprotect.service.EditModeTracker;
import cn.mythicland.worldprotect.service.WorldProtectService;
import cn.mythicland.worldprotect.storage.WorldConfigFileResolver;
import cn.mythicland.worldprotect.storage.WorldConfigStore;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.ServicePriority;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Owns WorldProtect construction, listener and command registration, service lifecycle, and reload.
 */
@InjectComponent
public final class WorldProtectLifecycle implements LibPluginLifecycle {

    private static final String DEFAULT_WORLD_CONFIG_DIRECTORY = "worlds";

    private final WorldProtectPlugin plugin;
    private final LibApi lib;
    private WorldConfigStore worldConfigs;
    private EditModeTracker editModes;
    private WorldManagerIntegration worldManager;
    private EditCommand editCommand;
    private WorldProtectApi worldProtectApi;

    /**
     * Creates the lifecycle module from Lib-provided dependencies.
     *
     * @param plugin plugin entry point
     * @param lib shared Lib service
     */
    public WorldProtectLifecycle(WorldProtectPlugin plugin, LibApi lib) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lib = Objects.requireNonNull(lib, "lib");
    }

    /**
     * Loads world policies, registers the listener, commands, and public API.
     */
    @Override
    public void enable() {
        ConfigurationState configuration = loadConfiguration();
        worldConfigs = new WorldConfigStore(
                plugin.getLogger(),
                configuration.settings().worldDefaults(),
                configuration.fileResolver()
        );
        editModes = new EditModeTracker();
        worldManager = new WorldManagerIntegration(plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new WorldProtectListener(worldConfigs, editModes, worldManager),
                plugin
        );

        PluginCommand editPluginCommand = plugin.getCommand("edit");
        if (editPluginCommand == null) {
            throw new IllegalStateException("edit command is missing from plugin.yml");
        }
        CommandRouter editRouter = lib.createCommandRouter(plugin, "edit");
        editCommand = new EditCommand(
                configuration.settings(),
                editModes,
                worldConfigs,
                worldManager
        );
        editRouter.registerDefault(editCommand);
        editPluginCommand.setExecutor(editRouter);
        editPluginCommand.setTabCompleter(editRouter);

        PluginCommand worldProtectPluginCommand = plugin.getCommand("worldprotect");
        if (worldProtectPluginCommand == null) {
            throw new IllegalStateException("worldprotect command is missing from plugin.yml");
        }
        CommandRouter worldProtectRouter = lib.createCommandRouter(plugin, "worldprotect");
        worldProtectRouter.register(new ReloadCommand(plugin));
        worldProtectPluginCommand.setExecutor(worldProtectRouter);
        worldProtectPluginCommand.setTabCompleter(worldProtectRouter);

        for (World world : Bukkit.getWorlds()) {
            worldConfigs.load(world, worldManager.logicalNameOrBukkitName(world));
        }

        WorldProtectService service = new WorldProtectService(worldConfigs);
        plugin.getServer().getServicesManager().register(
                WorldProtectApi.class,
                service,
                plugin,
                ServicePriority.Normal
        );
        worldProtectApi = service;
        plugin.getLogger().info("WorldProtect enabled with per-world protection configuration.");
    }

    /**
     * Reloads world policy files and command settings.
     */
    @Override
    public void reload() {
        reloadConfiguration();
    }

    /**
     * Unregisters the public API and clears in-memory world state.
     */
    @Override
    public void disable() {
        if (worldProtectApi != null) {
            plugin.getServer().getServicesManager().unregister(WorldProtectApi.class, worldProtectApi);
        }
        if (editModes != null) editModes.clear();
        if (worldConfigs != null) worldConfigs.clear();
        worldProtectApi = null;
        editCommand = null;
        worldManager = null;
        editModes = null;
        worldConfigs = null;
    }

    /**
     * Reloads configuration for the existing protection graph.
     */
    public void reloadConfiguration() {
        ConfigurationState configuration = loadConfiguration();
        Objects.requireNonNull(worldConfigs, "WorldProtect world configuration is unavailable").reload(
                configuration.settings().worldDefaults(),
                configuration.fileResolver(),
                Bukkit.getWorlds(),
                Objects.requireNonNull(worldManager, "WorldManager integration is unavailable")
                        ::logicalNameOrBukkitName
        );
        Objects.requireNonNull(editCommand, "WorldProtect edit command is unavailable")
                .updateSettings(configuration.settings());
    }

    private ConfigurationState loadConfiguration() {
        FileConfiguration configuration = ConfigSupport.loadDefault(plugin);
        WorldProtectSettings settings = WorldProtectSettings.load(plugin, configuration);
        Path configRoot = resolveWorldConfigRoot(settings.worldConfigDirectory());
        WorldConfigFileResolver fileResolver = new WorldConfigFileResolver(configRoot);
        try {
            fileResolver.ensureRootDirectory();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not prepare WorldProtect configuration directory",
                    exception
            );
        }
        return new ConfigurationState(settings, fileResolver);
    }

    private Path resolveWorldConfigRoot(String configuredDirectory) {
        Path pluginDataDirectory = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        Path relativeDirectory;
        try {
            relativeDirectory = Path.of(configuredDirectory);
        } catch (InvalidPathException exception) {
            return resetWorldConfigDirectory(pluginDataDirectory, exception.getMessage());
        }

        if (relativeDirectory.isAbsolute()
                || relativeDirectory.getNameCount() != 1
                || relativeDirectory.getFileName().toString().equals(".")
                || relativeDirectory.getFileName().toString().equals("..")) {
            return resetWorldConfigDirectory(
                    pluginDataDirectory,
                    "world-config-directory must be one relative directory segment"
            );
        }

        Path root = pluginDataDirectory.resolve(relativeDirectory).normalize();
        if (!root.startsWith(pluginDataDirectory) || root.equals(pluginDataDirectory)) {
            return resetWorldConfigDirectory(pluginDataDirectory, "directory escapes the plugin data folder");
        }
        if (Files.isSymbolicLink(root)) {
            return resetWorldConfigDirectory(pluginDataDirectory, "directory cannot be a symbolic link");
        }
        return root;
    }

    private Path resetWorldConfigDirectory(Path pluginDataDirectory, String reason) {
        plugin.getLogger().warning(
                "Invalid world-config-directory: " + reason + "; resetting to '"
                        + DEFAULT_WORLD_CONFIG_DIRECTORY + "'."
        );
        plugin.getConfig().set("world-config-directory", DEFAULT_WORLD_CONFIG_DIRECTORY);
        plugin.saveConfig();
        return pluginDataDirectory.resolve(DEFAULT_WORLD_CONFIG_DIRECTORY).normalize();
    }

    private record ConfigurationState(
            WorldProtectSettings settings,
            WorldConfigFileResolver fileResolver
    ) {
    }
}
