package cn.mythicland.worldprotect;

import cn.mythicland.lib.config.ConfigSupport;
import cn.mythicland.lib.policy.ListMode;
import cn.mythicland.lib.policy.ListPolicy;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Global WorldProtect settings and the template used for new worlds.
 */
final class WorldProtectSettings {

    private final String worldConfigDirectory;
    private final String editPermission;
    private final WorldProtectionRules worldDefaults;

    private WorldProtectSettings(Builder builder) {
        worldConfigDirectory = requireNonBlank(builder.worldConfigDirectory, "worldConfigDirectory");
        editPermission = requireNonBlank(builder.editPermission, "editPermission");
        worldDefaults = Objects.requireNonNull(builder.worldDefaults, "worldDefaults");
    }

    static Builder builder() {
        return new Builder();
    }

    static WorldProtectSettings load(JavaPlugin plugin, FileConfiguration configuration) {
        String worldConfigDirectory = ConfigSupport.getString(
                plugin,
                configuration,
                "world-config-directory",
                "worlds"
        );
        String editPermission = ConfigSupport.getString(
                plugin,
                configuration,
                "edit-mode.permission",
                "worldprotect.edit"
        );

        WorldProtectionRules defaults = WorldProtectionRules.builder()
                .blockPlace(readBoolean(
                        plugin, configuration, "world-defaults.rules.block-placement", true))
                .blockBreak(readBoolean(
                        plugin, configuration, "world-defaults.rules.block-breaking", true))
                .interactionPolicy(readIntegerPolicy(
                        plugin,
                        configuration,
                        new ListPolicy<>(ListMode.DISABLED, defaultInteractIds())
                ))
                .bucket(readBoolean(
                        plugin, configuration, "world-defaults.rules.bucket-actions", true))
                .leafDecay(readBoolean(
                        plugin, configuration, "world-defaults.rules.leaf-decay", true))
                .blockFade(readBoolean(
                        plugin, configuration, "world-defaults.rules.block-fade", true))
                .blockIgnite(readBoolean(
                        plugin, configuration, "world-defaults.rules.block-ignition", true))
                .fireSpread(readBoolean(
                        plugin, configuration, "world-defaults.rules.fire-spread", true))
                .explosions(readBoolean(
                        plugin, configuration, "world-defaults.rules.explosion-block-damage", true))
                .fallDamage(readBoolean(
                        plugin, configuration, "world-defaults.rules.player-fall-damage", true))
                .enderPearl(readBoolean(
                        plugin, configuration, "world-defaults.rules.ender-pearl-launch", false))
                .commandPolicy(readCommandPolicy(
                        plugin,
                        configuration,
                        new ListPolicy<>(ListMode.BLACKLIST, defaultBlockedCommands())
                ))
                .build();

        Builder settingsBuilder = builder();
        settingsBuilder.worldConfigDirectory(worldConfigDirectory);
        settingsBuilder.editPermission(editPermission);
        settingsBuilder.worldDefaults(defaults);
        return settingsBuilder.build();
    }

    private static boolean readBoolean(
            JavaPlugin plugin,
            FileConfiguration configuration,
            String currentPath,
            boolean defaultValue
    ) {
        return ConfigSupport.getBoolean(plugin, configuration, currentPath, defaultValue);
    }

    private static Set<Integer> readIntegerSet(
            JavaPlugin plugin,
            FileConfiguration configuration,
            String path,
            Set<Integer> defaultValue
    ) {
        if (!configuration.contains(path)) {
            configuration.set(path, List.copyOf(defaultValue));
            plugin.saveConfig();
            return defaultValue;
        }

        List<?> values = configuration.getList(path);
        if (values == null) {
            ConfigSupport.resetToDefault(
                    plugin,
                    configuration,
                    path,
                    List.copyOf(defaultValue),
                    "expected a list of integer block IDs"
            );
            return defaultValue;
        }

        Set<Integer> parsed = new LinkedHashSet<>();
        for (Object value : values) {
            if (!(value instanceof Number number)) {
                ConfigSupport.resetToDefault(
                        plugin,
                        configuration,
                        path,
                        List.copyOf(defaultValue),
                        "expected a list of integer block IDs"
                );
                return defaultValue;
            }
            parsed.add(number.intValue());
        }
        return Set.copyOf(parsed);
    }

    private static ListPolicy<Integer> readIntegerPolicy(
            JavaPlugin plugin,
            FileConfiguration configuration,
            ListPolicy<Integer> defaultPolicy
    ) {
        ListMode mode = readMode(
                plugin,
                configuration,
                "world-defaults.interact.mode",
                defaultPolicy.mode()
        );
        Set<Integer> entries = readIntegerSet(
                plugin,
                configuration,
                "world-defaults.interact.block-ids",
                defaultPolicy.entries()
        );
        return new ListPolicy<>(mode, entries);
    }

    private static ListPolicy<String> readCommandPolicy(
            JavaPlugin plugin,
            FileConfiguration configuration,
            ListPolicy<String> defaultPolicy
    ) {
        ListMode mode = readMode(
                plugin,
                configuration,
                "world-defaults.commands.mode",
                defaultPolicy.mode()
        );
        Set<String> entries = readCommandSet(
                plugin,
                configuration,
                "world-defaults.commands.names",
                defaultPolicy.entries()
        );
        return new ListPolicy<>(mode, entries);
    }

    private static ListMode readMode(
            JavaPlugin plugin,
            FileConfiguration configuration,
            String currentPath,
            ListMode defaultValue
    ) {
        Object rawValue = configuration.get(currentPath);
        ListMode currentMode = rawValue instanceof String value
                ? ListMode.parse(value).orElse(null)
                : null;
        if (currentMode == null) {
            ConfigSupport.resetToDefault(
                    plugin,
                    configuration,
                    currentPath,
                    defaultValue.configValue(),
                    "expected disabled, blacklist, or whitelist"
            );
            currentMode = defaultValue;
        }
        return currentMode;
    }

    private static Set<String> readCommandSet(
            JavaPlugin plugin,
            FileConfiguration configuration,
            String path,
            Set<String> defaultValue
    ) {
        if (!configuration.contains(path)) {
            configuration.set(path, List.copyOf(defaultValue));
            plugin.saveConfig();
            return defaultValue;
        }

        List<?> values = configuration.getList(path);
        if (values == null) {
            ConfigSupport.resetToDefault(
                    plugin,
                    configuration,
                    path,
                    List.copyOf(defaultValue),
                    "expected a list of command names"
            );
            return defaultValue;
        }

        Set<String> parsed = new LinkedHashSet<>();
        for (Object value : values) {
            if (!(value instanceof String command) || command.isBlank()) {
                ConfigSupport.resetToDefault(
                        plugin,
                        configuration,
                        path,
                        List.copyOf(defaultValue),
                        "expected a list of non-empty command names"
                );
                return defaultValue;
            }
            parsed.add(command.trim());
        }
        return Set.copyOf(parsed);
    }


    static Set<Integer> defaultInteractIds() {
        return Set.of(0, 54, 58, 61, 62, 63, 68, 116, 117, 118, 130, 146, 323, 397, 330, 70, 148, 72, 147, 33, 23, 77);
    }

    static Set<String> defaultBlockedCommands() {
        return Set.of("pl", "plugins");
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    String worldConfigDirectory() {
        return worldConfigDirectory;
    }

    String editPermission() {
        return editPermission;
    }

    WorldProtectionRules worldDefaults() {
        return worldDefaults;
    }

    /**
     * Named construction API for global settings and the world defaults template.
     */
    static final class Builder {

        private String worldConfigDirectory;
        private String editPermission;
        private WorldProtectionRules worldDefaults;

        Builder worldConfigDirectory(String value) {
            worldConfigDirectory = value;
            return this;
        }

        Builder editPermission(String value) {
            editPermission = value;
            return this;
        }

        Builder worldDefaults(WorldProtectionRules value) {
            worldDefaults = value;
            return this;
        }

        WorldProtectSettings build() {
            return new WorldProtectSettings(this);
        }
    }
}
