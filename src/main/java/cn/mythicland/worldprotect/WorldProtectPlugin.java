package cn.mythicland.worldprotect;

import cn.mythicland.lib.api.LibApi;
import cn.mythicland.lib.command.CommandRouter;
import cn.mythicland.lib.config.ConfigSupport;
import cn.mythicland.worldprotect.api.WorldProtectApi;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Main entry point for the per-world protection plugin.
 */
public final class WorldProtectPlugin extends JavaPlugin {

    private static final String DEFAULT_WORLD_CONFIG_DIRECTORY = "worlds";

    private WorldConfigStore worldConfigs;
    private EditModeTracker editModes;
    private WorldManagerIntegration worldManager;
    private EditCommand editCommand;
    private WorldProtectApi worldProtectApi;

    // Lib owns and closes the service; this plugin only borrows it.
    @SuppressWarnings("resource")
    @Override
    public void onEnable() {
        LibApi libApi = LibApi.require(this);
        ConfigurationState configuration = loadConfiguration();

        worldConfigs = new WorldConfigStore(
                getLogger(),
                configuration.settings().worldDefaults(),
                configuration.fileResolver()
        );
        editModes = new EditModeTracker();
        worldManager = new WorldManagerIntegration(this);
        getServer().getPluginManager().registerEvents(
                new WorldProtectListener(worldConfigs, editModes, worldManager),
                this
        );

        PluginCommand editPluginCommand = getCommand("edit");
        if (editPluginCommand == null) {
            throw new IllegalStateException("edit command is missing from plugin.yml");
        }
        CommandRouter editRouter = libApi.createCommandRouter(this, "edit");
        editCommand = new EditCommand(
                configuration.settings(),
                editModes,
                worldConfigs,
                worldManager
        );
        editRouter.registerDefault(editCommand);
        editPluginCommand.setExecutor(editRouter);
        editPluginCommand.setTabCompleter(editRouter);

        PluginCommand worldProtectPluginCommand = getCommand("worldprotect");
        if (worldProtectPluginCommand == null) {
            throw new IllegalStateException("worldprotect command is missing from plugin.yml");
        }
        CommandRouter worldProtectRouter = libApi.createCommandRouter(this, "worldprotect");
        worldProtectRouter.register(new ReloadCommand(this));
        worldProtectPluginCommand.setExecutor(worldProtectRouter);
        worldProtectPluginCommand.setTabCompleter(worldProtectRouter);

        for (World world : Bukkit.getWorlds()) {
            worldConfigs.load(world, worldManager.logicalNameOrBukkitName(world));
        }

        WorldProtectService service = new WorldProtectService(worldConfigs);
        getServer().getServicesManager().register(
                WorldProtectApi.class,
                service,
                this,
                ServicePriority.Normal
        );
        worldProtectApi = service;
        getLogger().info("WorldProtect enabled with per-world protection configuration.");
    }

    @Override
    public void onDisable() {
        if (worldProtectApi != null) {
            getServer().getServicesManager().unregister(WorldProtectApi.class, worldProtectApi);
        }
        if (editModes != null) editModes.clear();
        if (worldConfigs != null) worldConfigs.clear();
        worldProtectApi = null;
        editCommand = null;
        worldManager = null;
        editModes = null;
        worldConfigs = null;
    }

    void reloadConfiguration() {
        ConfigurationState configuration = loadConfiguration();

        worldConfigs.reload(
                configuration.settings().worldDefaults(),
                configuration.fileResolver(),
                Bukkit.getWorlds(),
                worldManager::logicalNameOrBukkitName
        );
        editCommand.updateSettings(configuration.settings());
    }

    private ConfigurationState loadConfiguration() {
        FileConfiguration configuration = ConfigSupport.loadDefault(this);
        WorldProtectSettings settings = WorldProtectSettings.load(this, configuration);
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
        Path pluginDataDirectory = getDataFolder().toPath().toAbsolutePath().normalize();
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
        getLogger().warning(
                "Invalid world-config-directory: " + reason + "; resetting to '"
                        + DEFAULT_WORLD_CONFIG_DIRECTORY + "'."
        );
        getConfig().set("world-config-directory", DEFAULT_WORLD_CONFIG_DIRECTORY);
        saveConfig();
        return pluginDataDirectory.resolve(DEFAULT_WORLD_CONFIG_DIRECTORY).normalize();
    }

    private record ConfigurationState(
            WorldProtectSettings settings,
            WorldConfigFileResolver fileResolver
    ) {
    }
}
