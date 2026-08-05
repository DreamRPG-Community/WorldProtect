package cn.mythicland.worldprotect;

import cn.mythicland.lib.policy.ListMode;
import cn.mythicland.lib.policy.ListPolicy;
import cn.mythicland.worldprotect.api.WorldProtectionPolicy;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads and caches the independent YAML policy for every loaded world.
 */
final class WorldConfigStore {

    private final Logger logger;
    private final Map<UUID, WorldProtectionRules> rulesByWorld = new ConcurrentHashMap<>();
    private final Map<UUID, String> logicalNamesByWorld = new ConcurrentHashMap<>();
    private volatile WorldProtectionRules defaults;
    private volatile WorldConfigFileResolver fileResolver;

    WorldConfigStore(
            Logger logger,
            WorldProtectionRules defaults,
            WorldConfigFileResolver fileResolver
    ) {
        this.logger = logger;
        this.defaults = defaults;
        this.fileResolver = fileResolver;
    }

    void ensureRootDirectory() throws IOException {
        fileResolver.ensureRootDirectory();
    }

    WorldProtectionRules load(World world, String logicalName) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(logicalName, "logicalName");
        String resolvedName = logicalName.isBlank() ? world.getName() : logicalName;
        logicalNamesByWorld.put(world.getUID(), resolvedName);

        try {
            Path file = fileResolver.resolve(world, resolvedName);
            WorldProtectionRules rules = loadFile(file);
            rulesByWorld.put(world.getUID(), rules);
            return rules;
        } catch (IOException exception) {
            logger.log(
                    Level.SEVERE,
                    "Could not load protection configuration for world '" + resolvedName
                            + "'; using the default policy.",
                    exception
            );
            rulesByWorld.put(world.getUID(), defaults);
            return defaults;
        }
    }

    WorldProtectionRules get(World world) {
        WorldProtectionRules cached = rulesByWorld.get(world.getUID());
        return cached == null ? load(world, world.getName()) : cached;
    }

    void reload(
            WorldProtectionRules newDefaults,
            WorldConfigFileResolver newFileResolver,
            Collection<World> loadedWorlds,
            Function<World, String> logicalNameResolver
    ) {
        defaults = Objects.requireNonNull(newDefaults, "newDefaults");
        fileResolver = Objects.requireNonNull(newFileResolver, "newFileResolver");
        Objects.requireNonNull(loadedWorlds, "loadedWorlds");
        Objects.requireNonNull(logicalNameResolver, "logicalNameResolver");

        for (World world : loadedWorlds) {
            load(world, logicalNameResolver.apply(world));
        }
    }

    Optional<WorldProtectionPolicy> findPolicy(World world) {
        if (world == null) return Optional.empty();

        WorldProtectionRules rules = rulesByWorld.get(world.getUID());
        if (rules == null) return Optional.empty();
        return Optional.of(new WorldProtectionPolicyView(logicalName(world), rules));
    }

    String logicalName(World world) {
        return logicalNamesByWorld.getOrDefault(world.getUID(), world.getName());
    }

    void unload(World world) {
        rulesByWorld.remove(world.getUID());
        logicalNamesByWorld.remove(world.getUID());
    }

    void clear() {
        rulesByWorld.clear();
        logicalNamesByWorld.clear();
    }

    private WorldProtectionRules loadFile(Path file) throws IOException {
        if (Files.isSymbolicLink(file)) {
            throw new IOException("World protection configuration cannot be a symbolic link: " + file);
        }
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("World protection configuration is not a regular file: " + file);
        }
        boolean existed = Files.exists(file);
        Files.createDirectories(file.getParent());
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file.toFile());
        boolean[] changed = {!existed};

        boolean blockPlace = readBoolean(
                configuration,
                "rules.block-placement",
                defaults.blockPlace(),
                changed
        );
        boolean blockBreak = readBoolean(
                configuration,
                "rules.block-breaking",
                defaults.blockBreak(),
                changed
        );
        ListPolicy<Integer> interactionPolicy = readInteractionPolicy(
                configuration,
                defaults.interactionPolicy(),
                changed
        );
        boolean bucket = readBoolean(
                configuration,
                "rules.bucket-actions",
                defaults.bucket(),
                changed
        );
        boolean leafDecay = readBoolean(
                configuration,
                "rules.leaf-decay",
                defaults.leafDecay(),
                changed
        );
        boolean blockFade = readBoolean(
                configuration,
                "rules.block-fade",
                defaults.blockFade(),
                changed
        );
        boolean blockIgnite = readBoolean(
                configuration,
                "rules.block-ignition",
                defaults.blockIgnite(),
                changed
        );
        boolean fireSpread = readBoolean(
                configuration,
                "rules.fire-spread",
                defaults.fireSpread(),
                changed
        );
        boolean explosions = readBoolean(
                configuration,
                "rules.explosion-block-damage",
                defaults.explosions(),
                changed
        );
        boolean fallDamage = readBoolean(
                configuration,
                "rules.player-fall-damage",
                defaults.fallDamage(),
                changed
        );
        boolean enderPearl = readBoolean(
                configuration,
                "rules.ender-pearl-launch",
                defaults.enderPearl(),
                changed
        );
        ListPolicy<String> commandPolicy = readCommandPolicy(
                configuration,
                defaults.commandPolicy(),
                changed
        );

        WorldProtectionRules rules = WorldProtectionRules.builder()
                .blockPlace(blockPlace)
                .blockBreak(blockBreak)
                .interactionPolicy(interactionPolicy)
                .bucket(bucket)
                .leafDecay(leafDecay)
                .blockFade(blockFade)
                .blockIgnite(blockIgnite)
                .fireSpread(fireSpread)
                .explosions(explosions)
                .fallDamage(fallDamage)
                .enderPearl(enderPearl)
                .commandPolicy(commandPolicy)
                .build();
        if (changed[0]) save(file, configuration);
        return rules;
    }

    private ListPolicy<Integer> readInteractionPolicy(
            YamlConfiguration configuration,
            ListPolicy<Integer> defaultPolicy,
            boolean[] changed
    ) {
        ListMode mode = readMode(
                configuration,
                "interact.mode",
                defaultPolicy.mode(),
                changed
        );
        Set<Integer> entries = readIntegerSet(
                configuration,
                defaultPolicy.entries(),
                changed
        );
        return new ListPolicy<>(mode, entries);
    }

    private ListPolicy<String> readCommandPolicy(
            YamlConfiguration configuration,
            ListPolicy<String> defaultPolicy,
            boolean[] changed
    ) {
        ListMode mode = readMode(
                configuration,
                "commands.mode",
                defaultPolicy.mode(),
                changed
        );
        Set<String> entries = readCommandSet(
                configuration,
                defaultPolicy.entries(),
                changed
        );
        return new ListPolicy<>(mode, entries);
    }

    private ListMode readMode(
            YamlConfiguration configuration,
            String currentPath,
            ListMode defaultValue,
            boolean[] changed
    ) {
        Object rawValue = configuration.get(currentPath);
        ListMode currentMode = rawValue instanceof String value
                ? ListMode.parse(value).orElse(null)
                : null;
        if (currentMode == null) {
            reset(configuration, currentPath, defaultValue.configValue(), changed,
                    "expected disabled, blacklist, or whitelist");
            currentMode = defaultValue;
        }
        return currentMode;
    }

    private boolean readBoolean(
            YamlConfiguration configuration,
            String path,
            boolean defaultValue,
            boolean[] changed
    ) {
        Object rawValue = configuration.get(path);
        if (!(rawValue instanceof Boolean value)) {
            reset(configuration, path, defaultValue, changed, "expected true or false");
            return defaultValue;
        }
        return value;
    }

    private Set<Integer> readIntegerSet(
            YamlConfiguration configuration,
            Set<Integer> defaultValue,
            boolean[] changed
    ) {
        String path = "interact.block-ids";
        Object rawValue = configuration.get(path);
        if (!(rawValue instanceof List<?> values)) {
            reset(configuration, path, List.copyOf(defaultValue), changed,
                    "expected a list of integer block IDs");
            return defaultValue;
        }

        Set<Integer> parsed = new LinkedHashSet<>();
        for (Object value : values) {
            if (!(value instanceof Number number)) {
                reset(configuration, path, List.copyOf(defaultValue), changed,
                        "expected a list of integer block IDs");
                return defaultValue;
            }
            parsed.add(number.intValue());
        }
        return Set.copyOf(parsed);
    }

    private Set<String> readCommandSet(
            YamlConfiguration configuration,
            Set<String> defaultValue,
            boolean[] changed
    ) {
        String path = "commands.names";
        Object rawValue = configuration.get(path);
        if (!(rawValue instanceof List<?> values)) {
            reset(configuration, path, List.copyOf(defaultValue), changed,
                    "expected a list of command names");
            return defaultValue;
        }

        Set<String> parsed = new LinkedHashSet<>();
        for (Object value : values) {
            if (!(value instanceof String command) || command.isBlank()) {
                reset(configuration, path, List.copyOf(defaultValue), changed,
                        "expected a list of non-empty command names");
                return defaultValue;
            }
            parsed.add(command.trim());
        }
        return Set.copyOf(parsed);
    }

    private <T> void reset(
            YamlConfiguration configuration,
            String path,
            T persistedValue,
            boolean[] changed,
            String reason
    ) {
        Object existing = configuration.get(path);
        if (existing != null) {
            logger.warning(
                    "Invalid world protection configuration '" + path + "': " + reason
                            + "; resetting to the default value."
            );
        }
        configuration.set(path, persistedValue);
        changed[0] = true;
    }

    private void save(Path file, YamlConfiguration configuration) {
        try {
            configuration.save(file.toFile());
        } catch (IOException exception) {
            logger.log(
                    Level.SEVERE,
                    "Could not save world protection configuration: " + file,
                    exception
            );
        }
    }
}
