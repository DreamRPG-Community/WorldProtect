package cn.mythicland.worldprotect;

import cn.mythicland.lib.api.LibApi;
import cn.mythicland.lib.bootstrap.PluginBootstrap;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;

/**
 * Minimal Bukkit entry point for the Lib-managed per-world protection plugin.
 */
public final class WorldProtectPlugin extends JavaPlugin {

    private static final String COMPONENT_PACKAGE = "cn.mythicland.worldprotect";

    private PluginBootstrap bootstrap;

    /**
     * Starts the Lib-managed WorldProtect component graph.
     */
    @Override
    @SuppressWarnings("resource")
    public void onEnable() {
        try {
            LibApi lib = LibApi.require(this);
            bootstrap = lib.createPluginBootstrap(this, COMPONENT_PACKAGE);
            bootstrap.enable();
        } catch (RuntimeException exception) {
            getLogger().log(
                    Level.SEVERE,
                    "WorldProtect failed to enable: " + LibApi.rootCauseMessage(exception),
                    exception
            );
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    /**
     * Closes the Lib-managed WorldProtect component graph.
     */
    @Override
    public void onDisable() {
        if (bootstrap != null) bootstrap.disable();
        bootstrap = null;
    }

    /**
     * Reloads the mutable WorldProtect configuration for the existing command binding.
     */
    public void reloadWorldProtect() {
        Objects.requireNonNull(bootstrap, "WorldProtect bootstrap is unavailable").reload();
    }
}
