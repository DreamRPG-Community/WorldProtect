package cn.mythicland.worldprotect.command;

import cn.mythicland.lib.bootstrap.annotation.CommandComponent;
import cn.mythicland.lib.bootstrap.annotation.CommandHandler;
import cn.mythicland.lib.command.CommandContext;
import cn.mythicland.lib.command.VanillaCommandMessages;
import cn.mythicland.worldprotect.WorldProtectPlugin;

import java.util.Objects;

/**
 * Reloads the global and per-world WorldProtect configuration.
 */
@CommandComponent("worldprotect")
public final class ReloadCommand {

    private static final String ADMIN_PERMISSION = "worldprotect.admin";
    private static final String SUCCESS_MESSAGE = VanillaCommandMessages.green(
            "WorldProtect 配置已重载。"
    );

    private final WorldProtectPlugin plugin;

    public ReloadCommand(WorldProtectPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @CommandHandler(value = "reload", permission = ADMIN_PERMISSION)
    void reload(CommandContext context) {
        context.requireArguments(0);
        plugin.reloadWorldProtect();
        context.sender().sendMessage(SUCCESS_MESSAGE);
    }
}
