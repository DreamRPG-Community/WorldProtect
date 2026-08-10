package cn.mythicland.worldprotect.bootstrap;

import cn.mythicland.lib.bootstrap.LibPluginLifecycle;
import cn.mythicland.lib.bootstrap.annotation.LifecycleComponent;
import cn.mythicland.lib.bootstrap.annotation.ServiceComponent;
import cn.mythicland.worldprotect.WorldProtectPlugin;
import cn.mythicland.worldprotect.api.WorldProtectApi;
import cn.mythicland.worldprotect.api.WorldProtectionPolicy;
import cn.mythicland.worldprotect.config.WorldProtectConfiguration;
import cn.mythicland.worldprotect.config.WorldProtectSettings;
import cn.mythicland.worldprotect.integration.worldmanager.WorldManagerIntegration;
import cn.mythicland.worldprotect.listener.WorldProtectListener;
import cn.mythicland.worldprotect.service.EditModeTracker;
import cn.mythicland.worldprotect.storage.WorldConfigFileResolver;
import cn.mythicland.worldprotect.storage.WorldConfigStore;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Owns WorldProtect construction, listener and command registration, service lifecycle, and reload.
 */
@LifecycleComponent
@ServiceComponent(WorldProtectApi.class)
public final class WorldProtectLifecycle implements LibPluginLifecycle, WorldProtectApi {

    private static final String DEFAULT_WORLD_CONFIG_DIRECTORY = "worlds";

    private final WorldProtectPlugin plugin;
    private final WorldProtectConfiguration configuration;
    private WorldConfigStore worldConfigs;
    private EditModeTracker editModes;
    private WorldManagerIntegration worldManager;
    private WorldProtectSettings settings;

    /**
     * Creates the lifecycle module from Lib-provided dependencies.
     *
     * @param plugin plugin entry point
     */
    public WorldProtectLifecycle(
            WorldProtectPlugin plugin,
            WorldProtectConfiguration configuration
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    /**
     * Loads world policies, registers the listener, commands, and public API.
     */
    @Override
    public void enable() {
        ConfigurationState current = loadConfiguration();
        settings = current.settings();
        worldConfigs = new WorldConfigStore(
                plugin.getLogger(),
                current.settings().worldDefaults(),
                current.fileResolver()
        );
        editModes = new EditModeTracker();
        worldManager = new WorldManagerIntegration(plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new WorldProtectListener(worldConfigs, editModes, worldManager),
                plugin
        );

        for (World world : Bukkit.getWorlds()) {
            worldConfigs.load(world, worldManager.logicalNameOrBukkitName(world));
        }

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
        if (editModes != null) editModes.clear();
        if (worldConfigs != null) worldConfigs.clear();
        settings = null;
        worldManager = null;
        editModes = null;
        worldConfigs = null;
    }

    /**
     * Reloads configuration for the existing protection graph.
     */
    public void reloadConfiguration() {
        ConfigurationState current = loadConfiguration();
        settings = current.settings();
        Objects.requireNonNull(worldConfigs, "WorldProtect world configuration is unavailable").reload(
                current.settings().worldDefaults(),
                current.fileResolver(),
                Bukkit.getWorlds(),
                Objects.requireNonNull(worldManager, "WorldManager integration is unavailable")
                        ::logicalNameOrBukkitName
        );
    }

    /**
     * Returns the active global settings to annotation-driven commands.
     *
     * @return active settings
     */
    public WorldProtectSettings settings() {
        return Objects.requireNonNull(settings, "WorldProtect settings are unavailable");
    }

    /**
     * Returns the in-memory edit state.
     *
     * @return edit mode tracker
     */
    public EditModeTracker editModes() {
        return Objects.requireNonNull(editModes, "WorldProtect edit modes are unavailable");
    }

    /**
     * Returns the per-world configuration store.
     *
     * @return world configuration store
     */
    public WorldConfigStore worldConfigs() {
        return Objects.requireNonNull(worldConfigs, "WorldProtect world configurations are unavailable");
    }

    /**
     * Returns the optional WorldManager integration.
     *
     * @return WorldManager integration
     */
    public WorldManagerIntegration worldManager() {
        return Objects.requireNonNull(worldManager, "WorldManager integration is unavailable");
    }

    @Override
    public Optional<WorldProtectionPolicy> find(World world) {
        return worldConfigs().findPolicy(world);
    }

    private ConfigurationState loadConfiguration() {
        WorldProtectSettings settings = configuration.snapshot();
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
            return fallbackWorldConfigDirectory(pluginDataDirectory, exception.getMessage());
        }

        if (relativeDirectory.isAbsolute()
                || relativeDirectory.getNameCount() != 1
                || relativeDirectory.getFileName().toString().equals(".")
                || relativeDirectory.getFileName().toString().equals("..")) {
            return fallbackWorldConfigDirectory(
                    pluginDataDirectory,
                    "world-config-directory must be one relative directory segment"
            );
        }

        Path root = pluginDataDirectory.resolve(relativeDirectory).normalize();
        if (!root.startsWith(pluginDataDirectory) || root.equals(pluginDataDirectory)) {
            return fallbackWorldConfigDirectory(pluginDataDirectory, "directory escapes the plugin data folder");
        }
        if (Files.isSymbolicLink(root)) {
            return fallbackWorldConfigDirectory(pluginDataDirectory, "directory cannot be a symbolic link");
        }
        return root;
    }

    private Path fallbackWorldConfigDirectory(Path pluginDataDirectory, String reason) {
        plugin.getLogger().warning(
                "Invalid world-config-directory: " + reason + "; resetting to '"
                        + DEFAULT_WORLD_CONFIG_DIRECTORY + "' for this configuration snapshot."
        );
        return pluginDataDirectory.resolve(DEFAULT_WORLD_CONFIG_DIRECTORY).normalize();
    }

    private record ConfigurationState(
            WorldProtectSettings settings,
            WorldConfigFileResolver fileResolver
    ) {
    }
}
