package com.power;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Messages traduits selon la langue du client Minecraft (français, sinon anglais). */
public final class Lang {

    private static final Map<String, String> FR = new HashMap<>();
    private static final Map<String, String> EN = new HashMap<>();

    static {
        put("no_permission", "Tu n'as pas la permission.", "You don't have permission.");
        put("player_only", "Cette commande doit être utilisée en jeu.", "This command must be used in-game.");
        put("usage_admin",
                "/%s give <joueur> <pouvoir> | remove <joueur> | premove | info [joueur] | reload",
                "/%s give <player> <power> | remove <player> | premove | info [player] | reload");
        put("usage_player", "/%s premove | info", "/%s premove | info");
        put("usage_give", "Usage : /%s give <joueur> <pouvoir>", "Usage: /%s give <player> <power>");
        put("usage_remove", "Usage : /%s remove <joueur>", "Usage: /%s remove <player>");
        put("player_not_found", "Joueur introuvable : %s", "Player not found: %s");
        put("player_not_found_short", "Joueur introuvable.", "Player not found.");
        put("unknown_power", "Pouvoir inconnu : %s", "Unknown power: %s");
        put("unknown_sub", "Sous-commande inconnue.", "Unknown subcommand.");
        put("potion_given", "Potion %s donnée à %s.", "Gave the %s potion to %s.");
        put("target_no_power", "%s n'a aucun pouvoir.", "%s has no power.");
        put("power_removed", "Pouvoir retiré à %s.", "Removed the power from %s.");
        put("no_power", "Tu n'as aucun pouvoir.", "You have no power.");
        put("premove_ok",
                "Tu as retiré ton pouvoir %s. La potion est dans ton inventaire, tu peux la donner à un ami.",
                "You removed your %s power. The potion is in your inventory, you can give it to a friend.");
        put("info_none", "%s : aucun pouvoir", "%s: no power");
        put("info_has", "%s : %s", "%s: %s");
        put("reloaded", "Config rechargée.", "Config reloaded.");
        put("already_has", "Tu as déjà le pouvoir %s.", "You already have the %s power.");
    }

    private Lang() {}

    private static void put(String key, String fr, String en) {
        FR.put(key, fr);
        EN.put(key, en);
    }

    private static boolean isFrench(CommandSender sender) {
        if (sender instanceof Player p) {
            Locale l = p.locale();
            return l != null && "fr".equalsIgnoreCase(l.getLanguage());
        }
        return true; // console : français
    }

    public static String tr(CommandSender sender, String key, Object... args) {
        Map<String, String> table = isFrench(sender) ? FR : EN;
        String template = table.getOrDefault(key, key);
        return args.length == 0 ? template : String.format(template, args);
    }
}
