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
        put("cooldown", "Recharge : %ds", "Cooldown: %ds");
        put("no_target", "Aucune cible en vue.", "No target in sight.");

        // --- Menu /power info ---
        put("gui_menu_title", "Pouvoirs", "Powers");
        put("gui_detail_title", "Recette : %s", "Recipe: %s");
        put("gui_passive", "Passif", "Passive");
        put("gui_active", "Clic droit", "Right click");
        put("gui_cooldown", "Recharge : %ds", "Cooldown: %ds");
        put("gui_recipe", "Recette", "Recipe");
        put("gui_corners", "Coins", "Corners");
        put("gui_edges", "Côtés", "Edges");
        put("gui_center", "Centre", "Center");
        put("gui_click", "Clique pour voir la recette", "Click to see the recipe");
        put("gui_current", "Ton pouvoir", "Your power");
        put("gui_back", "Retour", "Back");

        // --- Descriptions des pouvoirs (p_ = passif, a_ = clic droit) ---
        put("p_lightning", "10 % de chance qu'un éclair frappe la cible touchée.",
                "10% chance that a lightning bolt strikes the target you hit.");
        put("a_lightning", "Fait tomber 3 éclairs sur la cible visée, un par seconde.",
                "Calls down 3 lightning bolts on the aimed target, one per second.");
        put("p_ice", "Tes coups ralentissent la cible.",
                "Your hits slow the target.");
        put("a_ice", "Gèle la cible visée pendant 3 secondes.",
                "Freezes the aimed target for 3 seconds.");
        put("p_blood", "Tu récupères 15 % des dégâts infligés en vie.",
                "You heal 15% of the damage you deal.");
        put("a_blood", "Mode berserk pendant 8 secondes : plus ta vie est basse, plus tu frappes fort.",
                "Berserk for 8 seconds: the lower your health, the harder you hit.");
        put("p_fire", "Tes coups enflamment la cible.",
                "Your hits set the target on fire.");
        put("a_fire", "Lance une boule de feu.",
                "Launches a fireball.");
        put("p_wind", "Aucun dégât de chute.",
                "No fall damage.");
        put("a_wind", "Deux gros dashs d'affilée (le second dans les 4 secondes) qui repoussent les ennemis proches.",
                "Two big dashes in a row (the second within 4 seconds) that push nearby enemies away.");
        put("p_shadow", "Invisible dans le noir.",
                "Invisible in the dark.");
        put("a_shadow", "Te téléporte dans le dos de la cible visée, ton premier coup fait +50 %.",
                "Teleports you behind the aimed target, your first hit deals +50%.");
        put("p_earth", "15 % de dégâts en moins et résistance au recul.",
                "15% less damage taken and knockback resistance.");
        put("a_earth", "Fait sortir 5 piliers de pierre devant toi qui blessent et projettent en l'air.",
                "Raises 5 stone pillars in front of you that hurt and launch enemies.");
        put("p_poison", "Tes coups empoisonnent. Tu es immunisé au poison.",
                "Your hits poison. You are immune to poison.");
        put("a_poison", "Crée un nuage toxique là où tu vises.",
                "Creates a toxic cloud where you aim.");
        put("p_light", "Vision nocturne permanente.",
                "Permanent night vision.");
        put("a_light", "Une lumière tombe du ciel et te soigne de 4 coeurs.",
                "A beam of light falls from the sky and heals you for 4 hearts.");
        put("p_phoenix", "Immunisé au feu et à la lave.",
                "Immune to fire and lava.");
        put("a_phoenix", "Vol de 4 secondes avec une traînée de flammes qui brûle les ennemis en dessous.",
                "4 seconds of flight with a flame trail that burns enemies below.");
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
