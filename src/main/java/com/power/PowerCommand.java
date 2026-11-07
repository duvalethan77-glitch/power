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
    private final InfoGui gui;
    private final PowerRegistry registry;
    private final RitualManager ritual;
    private final Allies allies;

    public PowerCommand(PowerPlugin plugin, PowerManager manager, InfoGui gui,
                        PowerRegistry registry, RitualManager ritual, Allies allies) {
        this.allies = allies;
        this.plugin = plugin;
        this.manager = manager;
        this.gui = gui;
        this.registry = registry;
        this.ritual = ritual;
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
                if (!registry.isFree(type)) {
                    msg(sender, NamedTextColor.RED, "power_exists", type.display);
                    return true;
                }
                registry.setCarried(type, null);
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
            case "status" -> {
                if (!requireAdmin(sender)) return true;
                msg(sender, NamedTextColor.GOLD, "status_header");
                for (PowerType t : PowerType.values()) {
                    sender.sendMessage(Component.text(t.icon + " " + t.display, t.textColor)
                            .append(Component.text(" : " + registry.describe(sender, t), NamedTextColor.GRAY)));
                }
            }
            case "free" -> {
                if (!requireAdmin(sender)) return true;
                if (args.length != 2) {
                    msg(sender, NamedTextColor.YELLOW, "usage_free", label);
                    return true;
                }
                PowerType type = PowerType.parse(args[1]);
                if (type == null) {
                    msg(sender, NamedTextColor.RED, "unknown_power", args[1]);
                    return true;
                }
                for (PowerRegistry.Entry en : registry.list(type)) {
                    if (en.state == PowerRegistry.State.HELD && en.holder != null
                            && Bukkit.getPlayer(en.holder) == null) {
                        msg(sender, NamedTextColor.RED, "free_offline");
                        return true;
                    }
                }
                for (PowerRegistry.Entry en : registry.list(type)) {
                    if (en.state == PowerRegistry.State.HELD && en.holder != null) {
                        Player holder = Bukkit.getPlayer(en.holder);
                        if (holder != null) manager.clearPower(holder);
                    }
                }
                ritual.forceFree(type);
                msg(sender, NamedTextColor.GREEN, "power_free_ok", type.display);
            }
            case "ally", "allies" -> ally(sender, label, args);
            case "info" -> info(sender, args);
            case "reload" -> {
                if (!requireAdmin(sender)) return true;
                plugin.reloadConfig();
                Pt.init(plugin);
                msg(sender, NamedTextColor.GREEN, "reloaded");
            }
            default -> msg(sender, NamedTextColor.RED, "unknown_sub");
        }
        return true;
    }

    /** /power ally add|remove <joueur> | list : les alliés voient à travers ta fumée. */
    private void ally(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            msg(sender, NamedTextColor.RED, "player_only");
            return;
        }
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        if (sub.equals("list")) {
            if (allies.list(p.getUniqueId()).isEmpty()) {
                msg(sender, NamedTextColor.YELLOW, "ally_none");
                return;
            }
            List<String> names = new ArrayList<>();
            for (java.util.UUID id : allies.list(p.getUniqueId())) {
                String n = Bukkit.getOfflinePlayer(id).getName();
                names.add(n != null ? n : id.toString());
            }
            msg(sender, NamedTextColor.AQUA, "ally_list", String.join(", ", names));
            return;
        }

        String targetName;
        boolean add;
        if (sub.equals("add") || sub.equals("remove")) {
            if (args.length != 3) {
                msg(sender, NamedTextColor.YELLOW, "usage_ally", label);
                return;
            }
            add = sub.equals("add");
            targetName = args[2];
        } else if (args.length == 2) {
            add = true;            // /power ally <joueur>
            targetName = args[1];
        } else {
            msg(sender, NamedTextColor.YELLOW, "usage_ally", label);
            return;
        }

        org.bukkit.OfflinePlayer target = Bukkit.getPlayerExact(targetName);
        if (target == null) target = Bukkit.getOfflinePlayerIfCached(targetName);
        if (target == null) {
            msg(sender, NamedTextColor.RED, "player_not_found", targetName);
            return;
        }
        if (target.getUniqueId().equals(p.getUniqueId())) {
            msg(sender, NamedTextColor.YELLOW, "ally_self");
            return;
        }
        String shown = target.getName() != null ? target.getName() : targetName;
        if (add) {
            if (!allies.add(p.getUniqueId(), target.getUniqueId())) {
                msg(sender, NamedTextColor.YELLOW, "ally_already", shown);
                return;
            }
            msg(sender, NamedTextColor.GREEN, "ally_added", shown);
            Player online = target.getPlayer();
            if (online != null) {
                online.sendMessage(Component.text(Lang.tr(online, "ally_added_to", p.getName()), NamedTextColor.GRAY));
            }
        } else if (allies.remove(p.getUniqueId(), target.getUniqueId())) {
            msg(sender, NamedTextColor.GREEN, "ally_removed", shown);
        } else {
            msg(sender, NamedTextColor.YELLOW, "ally_not", shown);
        }
    }

    /**
     * /power info            : ouvre le menu des pouvoirs (descriptions + recettes)
     * /power info <pouvoir>  : ouvre directement la recette de ce pouvoir
     * /power info <joueur>   : (admin) affiche le pouvoir d'un joueur
     */
    private void info(CommandSender sender, String[] args) {
        if (args.length == 1) {
            if (sender instanceof Player p) {
                gui.openMenu(p);
            } else {
                msg(sender, NamedTextColor.RED, "player_only");
            }
            return;
        }

        PowerType asPower = PowerType.parse(args[1]);
        if (asPower != null) {
            if (sender instanceof Player p) {
                gui.openDetail(p, asPower);
            } else {
                msg(sender, NamedTextColor.RED, "player_only");
            }
            return;
        }

        if (!requireAdmin(sender)) return;
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg(sender, NamedTextColor.RED, "player_not_found", args[1]);
            return;
        }
        PowerType type = manager.getPower(target);
        if (type == null) {
            msg(sender, NamedTextColor.AQUA, "info_none", target.getName());
        } else {
            msg(sender, NamedTextColor.AQUA, "info_has", target.getName(), type.display);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission(ADMIN);

        if (args.length == 1) {
            List<String> subs = admin
                    ? List.of("give", "remove", "free", "status", "premove", "ally", "info", "reload")
                    : List.of("premove", "ally", "info");
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("ally")) {
            for (String s : List.of("add", "remove", "list")) {
                if (s.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(s);
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("ally")) {
            for (Player pl : Bukkit.getOnlinePlayers()) {
                if (pl.getName().toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))) out.add(pl.getName());
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            for (PowerType t : PowerType.values()) {
                String n = t.name().toLowerCase(Locale.ROOT);
                if (n.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(n);
            }
            if (admin) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                        out.add(p.getName());
                    }
                }
            }
        } else if (admin && args.length == 2
                && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("remove"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(p.getName());
                }
            }
        } else if (admin && args.length == 2 && args[0].equalsIgnoreCase("free")) {
            for (PowerType t : PowerType.values()) {
                String n = t.name().toLowerCase(Locale.ROOT);
                if (n.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(n);
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
