package cn.mythicland.worldprotect.command;

import cn.mythicland.lib.command.CommandUsageException;
import cn.mythicland.lib.command.Subcommand;
import cn.mythicland.lib.command.VanillaCommandMessages;
import cn.mythicland.worldprotect.WorldProtectPlugin;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Reloads the global and per-world WorldProtect configuration.
 */
public final class ReloadCommand implements Subcommand {

    private static final String ADMIN_PERMISSION = "worldprotect.admin";
    private static final String SUCCESS_MESSAGE = VanillaCommandMessages.green(
            "WorldProtect 配置已重载。"
    );

    private final WorldProtectPlugin plugin;

    public ReloadCommand(WorldProtectPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "reload";
    }

    @Override
    public String usage() {
        return "/worldprotect reload";
    }

    @Override
    public String permission() {
        return ADMIN_PERMISSION;
    }

    @Override
    public void execute(CommandSender sender, List<String> arguments) {
        if (!arguments.isEmpty()) throw new CommandUsageException(usage());
        plugin.reloadWorldProtect();
        sender.sendMessage(SUCCESS_MESSAGE);
    }
}
