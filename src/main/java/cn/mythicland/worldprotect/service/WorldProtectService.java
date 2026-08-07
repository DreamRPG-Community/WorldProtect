package cn.mythicland.worldprotect.service;

import cn.mythicland.worldprotect.api.WorldProtectApi;
import cn.mythicland.worldprotect.api.WorldProtectionPolicy;
import cn.mythicland.worldprotect.storage.WorldConfigStore;
import org.bukkit.World;

import java.util.Optional;

/**
 * Bukkit service implementation for the public WorldProtect API.
 */
public final class WorldProtectService implements WorldProtectApi {

    private final WorldConfigStore worldConfigs;

    public WorldProtectService(WorldConfigStore worldConfigs) {
        this.worldConfigs = worldConfigs;
    }

    @Override
    public Optional<WorldProtectionPolicy> find(World world) {
        return worldConfigs.findPolicy(world);
    }
}
