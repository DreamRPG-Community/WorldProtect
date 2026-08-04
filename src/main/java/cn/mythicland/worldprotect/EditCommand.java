package cn.mythicland.worldprotect;

import cn.mythicland.lib.command.CommandUsageException;
import cn.mythicland.lib.command.Subcommand;
import cn.mythicland.lib.command.VanillaCommandMessages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Toggles temporary building mode for a player.
 */
final class EditCommand implements Subcommand {

    private static final String ONLY_PLAYER_MESSAGE = VanillaCommandMessages.red("该命令只能由玩家执行。");
    private static final String ENABLED_MESSAGE = VanillaCommandMessages.green("建筑模式已开启。");
    private static final String DISABLED_MESSAGE = VanillaCommandMessages.red("建筑模式已关闭。");
    private static final String SAVE_REMINDER_TEMPLATE =
            VanillaCommandMessages.yellow(
                    "检测到您安装了 WorldManager 插件, 请在重启前保存您的地图改动。 \n"
                            + "使用 /worldmanager save %world% 以保存世界更改。"
            );
    private final EditModeTracker editModes;
    private final WorldConfigStore worldConfigs;
    private final WorldManagerIntegration worldManager;
    private WorldProtectSettings settings;

    EditCommand(
            WorldProtectSettings settings,
            EditModeTracker editModes,
            WorldConfigStore worldConfigs,
            WorldManagerIntegration worldManager
    ) {
        this.settings = settings;
        this.editModes = editModes;
        this.worldConfigs = worldConfigs;
        this.worldManager = worldManager;
    }

    void updateSettings(WorldProtectSettings newSettings) {
        this.settings = newSettings;
    }

    @Override
    public String name() {
        return "edit";
    }

    @Override
    public String usage() {
        return "/edit";
    }

    @Override
    public String permission() {
        return settings.editPermission();
    }

    @Override
    public void execute(CommandSender sender, List<String> arguments) {
        if (!arguments.isEmpty()) throw new CommandUsageException(usage());
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ONLY_PLAYER_MESSAGE);
            return;
        }

        boolean enabled = editModes.toggle(player.getUniqueId());
        player.sendMessage(enabled ? ENABLED_MESSAGE : DISABLED_MESSAGE);
        if (enabled && worldManager.isEnabled()) {
            String worldName = worldConfigs.logicalName(player.getWorld());
            player.sendMessage(SAVE_REMINDER_TEMPLATE.replace("%world%", worldName));
        }
    }
}
