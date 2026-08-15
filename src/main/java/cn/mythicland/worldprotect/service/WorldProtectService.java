package cn.mythicland.worldprotect.service;

import cn.mythicland.worldprotect.api.WorldProtectApi;
import cn.mythicland.worldprotect.api.WorldProtectionPolicy;
import cn.mythicland.worldprotect.storage.WorldConfigStore;
import org.bukkit.World;

import java.util.Optional;
import java.util.UUID;

/**
 * Bukkit service implementation for the public WorldProtect API.
 */
public final class WorldProtectService implements WorldProtectApi {

    private final WorldConfigStore worldConfigs;
    private final EditModeTracker editModes;

    public WorldProtectService(WorldConfigStore worldConfigs) {
        this(worldConfigs, new EditModeTracker());
    }

    public WorldProtectService(WorldConfigStore worldConfigs, EditModeTracker editModes) {
        this.worldConfigs = worldConfigs;
        this.editModes = editModes;
    }

    @Override
    public Optional<WorldProtectionPolicy> find(World world) {
        return worldConfigs.findPolicy(world);
    }

    @Override
    public boolean isEditMode(UUID playerId) {
        return editModes.isEnabled(playerId);
    }
}
