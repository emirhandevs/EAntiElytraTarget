package com.eantitarget.command;

import com.eantitarget.EAntiTargetPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class AntiTargetCommand implements CommandExecutor, TabCompleter {

    private final EAntiTargetPlugin plugin;

    public AntiTargetCommand(EAntiTargetPlugin plugin) {
        this.plugin = plugin;
    }

    private void msg(CommandSender sender, String key) {
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(plugin.getConfigManager().msg(key)));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (sender.hasPermission("eantitarget.admin") || sender.hasPermission("eantitarget.alerts")) {
                msg(sender, "usage");
            } else {
                msg(sender, "no-permission");
            }
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("alerts")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Players only.");
                return true;
            }
            if (!player.hasPermission("eantitarget.alerts") && !player.hasPermission("eantitarget.admin")) {
                msg(sender, "no-permission");
                return true;
            }
            boolean nowOn = plugin.toggleAlerts(player.getUniqueId());
            msg(player, nowOn ? "alerts-enabled" : "alerts-disabled");
            return true;
        }

        if (!sender.hasPermission("eantitarget.admin")) {
            msg(sender, "no-permission");
            return true;
        }

        if (sub.equals("reload")) {
            plugin.getConfigManager().loadConfig();
            if (plugin.getAutoFireworkCheck() != null) plugin.getAutoFireworkCheck().cleanup();
            if (plugin.getElytraTargetCheck() != null) plugin.getElytraTargetCheck().cleanup();
            msg(sender, "reload-success");
            return true;
        }

        if (sub.equals("logs")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Players only.");
                return true;
            }
            int page = 1;
            if (args.length >= 2) {
                try { page = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
            }
            plugin.getLogsGui().open(player, page);
            return true;
        }

        msg(sender, "usage");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String p = args[0].toLowerCase();
            if (sender.hasPermission("eantitarget.admin")) {
                for (String s : List.of("reload", "logs", "alerts")) {
                    if (s.startsWith(p)) out.add(s);
                }
            } else if (sender.hasPermission("eantitarget.alerts")) {
                if ("alerts".startsWith(p)) out.add("alerts");
            }
        }
        return out;
    }
}
