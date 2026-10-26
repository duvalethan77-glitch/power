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

    private void msg(CommandSender s, String text, NamedTextColor color) {
        s.sendMessage(Component.text(text, color));
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission(ADMIN)) return true;
        msg(sender, "Tu n'as pas la permission.", NamedTextColor.RED);
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender.hasPermission(ADMIN)) {
                msg(sender, "/" + label + " give <joueur> <pouvoir> | remove <joueur> | premove | info [joueur] | reload",
                        NamedTextColor.YELLOW);
            } else {
                msg(sender, "/" + label + " premove | info", NamedTextColor.YELLOW);
            }
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "premove" -> {
                // Ouvert à tous : le joueur se défait de son pouvoir et récupère la potion
                if (!(sender instanceof Player p)) {
                    msg(sender, "Cette commande doit être utilisée en jeu.", NamedTextColor.RED);
                    return true;
                }
                PowerType type = manager.getPower(p);
                if (type == null) {
                    msg(sender, "Tu n'as aucun pouvoir.", NamedTextColor.YELLOW);
                    return true;
                }
                manager.releasePower(p);
                msg(sender, "Tu as retiré ton pouvoir " + type.display
                        + ". La potion est dans ton inventaire, tu peux la donner à un ami.", NamedTextColor.GREEN);
            }
            case "give" -> {
                if (!requireAdmin(sender)) return true;
                if (args.length != 3) {
                    msg(sender, "Usage : /" + label + " give <joueur> <pouvoir>", NamedTextColor.YELLOW);
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    msg(sender, "Joueur introuvable : " + args[1], NamedTextColor.RED);
                    return true;
                }
                PowerType type = PowerType.parse(args[2]);
                if (type == null) {
                    msg(sender, "Pouvoir inconnu : " + args[2], NamedTextColor.RED);
                    return true;
                }
                manager.giveBack(target, manager.createPotion(type));
                msg(sender, "Potion " + type.display + " donnée à " + target.getName() + ".", NamedTextColor.GREEN);
            }
            case "remove" -> {
                if (!requireAdmin(sender)) return true;
                if (args.length != 2) {
                    msg(sender, "Usage : /" + label + " remove <joueur>", NamedTextColor.YELLOW);
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    msg(sender, "Joueur introuvable : " + args[1], NamedTextColor.RED);
                    return true;
                }
                if (manager.getPower(target) == null) {
                    msg(sender, target.getName() + " n'a aucun pouvoir.", NamedTextColor.YELLOW);
                    return true;
                }
                manager.clearPower(target);
                msg(sender, "Pouvoir retiré à " + target.getName() + ".", NamedTextColor.GREEN);
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
                    msg(sender, "Joueur introuvable.", NamedTextColor.RED);
                    return true;
                }
                PowerType type = manager.getPower(target);
                msg(sender, target.getName() + " : " + (type == null ? "aucun pouvoir" : type.display),
                        NamedTextColor.AQUA);
            }
            case "reload" -> {
                if (!requireAdmin(sender)) return true;
                plugin.reloadConfig();
                msg(sender, "Config rechargée.", NamedTextColor.GREEN);
            }
            default -> msg(sender, "Sous-commande inconnue.", NamedTextColor.RED);
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
