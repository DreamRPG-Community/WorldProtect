package cn.mythicland.worldprotect;

import cn.mythicland.worldprotect.api.WorldProtectApi;
import cn.mythicland.worldprotect.api.WorldProtectionPolicy;
import org.bukkit.World;

import java.util.Optional;

/**
 * Bukkit service implementation for the public WorldProtect API.
 */
final class WorldProtectService implements WorldProtectApi {

    private final WorldConfigStore worldConfigs;

    WorldProtectService(WorldConfigStore worldConfigs) {
        this.worldConfigs = worldConfigs;
    }

    @Override
    public Optional<WorldProtectionPolicy> find(World world) {
        return worldConfigs.findPolicy(world);
    }
}
