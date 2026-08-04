package cn.mythicland.worldprotect.api;

import org.bukkit.World;

import java.util.Optional;

/**
 * Read-only WorldProtect service exposed through Bukkit's service manager.
 *
 * <p>Consumers should obtain this interface from the Bukkit service manager
 * after WorldProtect has enabled. Calls must be made on the server's primary
 * thread because the supplied Bukkit world belongs to the live server state.</p>
 */
public interface WorldProtectApi {

    /**
     * Finds the cached protection policy for a currently loaded world.
     *
     * @param world the loaded Bukkit world to inspect; {@code null} returns an empty result
     * @return the immutable policy for the world, or empty when the world is not managed by
     *         the active WorldProtect instance
     */
    Optional<WorldProtectionPolicy> find(World world);
}
