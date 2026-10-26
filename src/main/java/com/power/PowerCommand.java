package com.power;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PowerCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "power.admin";

    private final PowerPlugin plugin;
    private final PowerManager manager;

    public PowerCommand(PowerPlugin plugin, PowerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private void msg(CommandSender s, NamedTextColor color, String key, Object... args) {
        s.sendMessage(Component.text(Lang.tr(s, key, args), color));
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission(ADMIN)) return true;
        msg(sender, NamedTextColor.RED, "no_permission");
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            msg(sender, NamedTextColor.YELLOW,
                    sender.hasPermission(ADMIN) ? "usage_admin" : "usage_player", label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "premove" -> {
                // Ouvert à tous : le joueur se défait de son pouvoir et récupère la potion
                if (!(sender instanceof Player p)) {
                    msg(sender, NamedTextColor.RED, "player_only");
                    return true;
                }
                PowerType type = manager.getPower(p);
                if (type == null) {
                    msg(sender, NamedTextColor.YELLOW, "no_power");
                    return true;
                }
                manager.releasePower(p);
                msg(sender, NamedTextColor.GREEN, "premove_ok", type.display);
            }
            case "give" -> {
                if (!requireAdmin(sender)) return true;
                if (args.length != 3) {
                    msg(sender, NamedTextColor.YELLOW, "usage_give", label);
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    msg(sender, NamedTextColor.RED, "player_not_found", args[1]);
                    return true;
                }
                PowerType type = PowerType.parse(args[2]);
                if (type == null) {
                    msg(sender, NamedTextColor.RED, "unknown_power", args[2]);
                    return true;
                }
                manager.giveBack(target, manager.createPotion(type));
                msg(sender, NamedTextColor.GREEN, "potion_given", type.display, target.getName());
            }
            case "remove" -> {
                if (!requireAdmin(sender)) return true;
                if (args.length != 2) {
                    msg(sender, NamedTextColor.YELLOW, "usage_remove", label);
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    msg(sender, NamedTextColor.RED, "player_not_found", args[1]);
                    return true;
                }
                if (manager.getPower(target) == null) {
                    msg(sender, NamedTextColor.YELLOW, "target_no_power", target.getName());
                    return true;
                }
                manager.clearPower(target);
                msg(sender, NamedTextColor.GREEN, "power_removed", target.getName());
            }
            case "info" -> {
                Player target;
                if (args.length >= 2) {
                    if (!requireAdmin(sender)) return true;
                    target = Bukkit.getPlayerExact(args[1]);
                } else {
                    target = sender instanceof Player p ? p : null;
                }
                if (target == null) {
                    msg(sender, NamedTextColor.RED, "player_not_found_short");
                    return true;
                }
                PowerType type = manager.getPower(target);
                if (type == null) {
                    msg(sender, NamedTextColor.AQUA, "info_none", target.getName());
                } else {
                    msg(sender, NamedTextColor.AQUA, "info_has", target.getName(), type.display);
                }
            }
            case "reload" -> {
                if (!requireAdmin(sender)) return true;
                plugin.reloadConfig();
                msg(sender, NamedTextColor.GREEN, "reloaded");
            }
            default -> msg(sender, NamedTextColor.RED, "unknown_sub");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission(ADMIN);

        if (args.length == 1) {
            List<String> subs = admin
                    ? List.of("give", "remove", "premove", "info", "reload")
                    : List.of("premove", "info");
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
        } else if (admin && args.length == 2
                && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("remove")
                || args[0].equalsIgnoreCase("info"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(p.getName());
                }
            }
        } else if (admin && args.length == 3 && args[0].equalsIgnoreCase("give")) {
            for (PowerType t : PowerType.values()) {
                String n = t.name().toLowerCase(Locale.ROOT);
                if (n.startsWith(args[2].toLowerCase(Locale.ROOT))) out.add(n);
            }
        }
        return out;
    }
}
