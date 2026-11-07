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
                "/%s give <joueur> <pouvoir> | remove <joueur> | free <pouvoir> | status | premove | ally | info [pouvoir|joueur] | reload",
                "/%s give <player> <power> | remove <player> | free <power> | status | premove | ally | info [power|player] | reload");
        put("usage_player", "/%s premove | ally | info", "/%s premove | ally | info");
        put("usage_ally", "Usage : /%s ally add <joueur> | remove <joueur> | list",
                "Usage: /%s ally add <player> | remove <player> | list");
        put("ally_added", "%s voit maintenant à travers ta fumée.", "%s can now see through your smoke.");
        put("ally_added_to", "%s t'a ajouté comme allié : sa fumée ne t'aveugle plus.",
                "%s added you as an ally: their smoke no longer blinds you.");
        put("ally_removed", "%s n'est plus ton allié.", "%s is no longer your ally.");
        put("ally_already", "%s est déjà ton allié.", "%s is already your ally.");
        put("ally_not", "%s n'est pas dans ta liste d'alliés.", "%s is not in your ally list.");
        put("ally_self", "Tu vois toujours à travers ta propre fumée.", "You always see through your own smoke.");
        put("ally_list", "Alliés : %s", "Allies: %s");
        put("ally_none", "Tu n'as aucun allié.", "You have no allies.");
        put("usage_give", "Usage : /%s give <joueur> <pouvoir>", "Usage: /%s give <player> <power>");
        put("usage_remove", "Usage : /%s remove <joueur>", "Usage: /%s remove <player>");
        put("usage_free", "Usage : /%s free <pouvoir>", "Usage: /%s free <power>");
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

        // --- Pouvoirs uniques / registre ---
        put("power_exists", "Le pouvoir %s existe déjà sur le serveur.", "The %s power already exists on the server.");
        put("power_held", "Ce pouvoir est déjà porté par un autre joueur.", "This power is already held by another player.");
        put("power_free_ok", "Le pouvoir %s est de nouveau disponible.", "The %s power is available again.");
        put("power_lost", "Le pouvoir %s a disparu. Il peut de nouveau être obtenu.",
                "The %s power has vanished. It can be obtained again.");
        put("free_offline", "Le porteur est hors ligne : impossible de libérer ce pouvoir maintenant.",
                "The holder is offline: this power cannot be freed right now.");
        put("status_header", "État des pouvoirs :", "Power status:");
        put("status_line", "%s : %s", "%s: %s");
        put("st_free", "Disponible", "Available");
        put("st_ritual", "Rituel en cours (%s) - %s", "Ritual in progress (%s) - %s");
        put("st_ground", "Au sol - %s", "On the ground - %s");
        put("st_carried", "En jeu (potion détenue)", "In play (potion held)");
        put("st_held", "Porté par %s", "Held by %s");

        // --- Rituel ---
        put("ritual_started_self",
                "Le rituel du pouvoir %s commence ! Dans %s, la potion tombera au sol.",
                "The ritual of the %s power begins! In %s, the potion will fall to the ground.");
        put("ritual_started_all", "%s a lancé le rituel du pouvoir %s !", "%s has started the ritual of the %s power!");
        put("ritual_title", "✦ Rituel : %s ✦", "✦ Ritual: %s ✦");
        put("ritual_reveal", "Coordonnée %s : %d", "Coordinate %s: %d");
        put("ritual_full", "Coordonnées : X %d, Y %d, Z %d", "Coordinates: X %d, Y %d, Z %d");
        put("ritual_bar", "✦ %s ✦  [%s]  %s  —  %s", "✦ %s ✦  [%s]  %s  —  %s");
        put("ritual_bar_wait", "✦ %s ✦  Le rituel s'achève…", "✦ %s ✦  The ritual is ending…");
        put("ground_bar", "✦ %s est au sol ✦  [%s]  X: %d  Y: %d  Z: %d", "✦ %s is on the ground ✦  [%s]  X: %d  Y: %d  Z: %d");
        put("ritual_dropped", "Le pouvoir %s est tombé au sol ! [%s] X %d, Y %d, Z %d",
                "The %s power has fallen to the ground! [%s] X %d, Y %d, Z %d");
        put("no_container", "Un pouvoir ne peut pas être rangé dans un coffre.",
                "A power cannot be stored in a chest.");
        put("world_normal", "Overworld", "Overworld");
        put("world_nether", "Nether", "Nether");
        put("world_end", "End", "End");
        put("ritual_won", "%s a récupéré le pouvoir %s !", "%s has claimed the %s power!");
        put("craft_blocked", "Ce pouvoir existe déjà : tu ne peux pas le crafter.",
                "This power already exists: you cannot craft it.");

        // --- Affichage au-dessus de la hotbar ---
        put("hud_ready", "PRÊT", "READY");
        put("hud_berserk", "BERSERK", "BERSERK");
        put("hud_backstab", "Coup dans le dos", "Backstab");
        put("hud_dash2", "2e dash", "2nd dash");

        // --- Menu /power info ---
        put("gui_menu_title", "Pouvoirs", "Powers");
        put("gui_detail_title", "Recette : %s", "Recipe: %s");
        put("gui_passive", "Passif", "Passive");
        put("gui_active", "Clic droit", "Right click");
        put("gui_cooldown", "Recharge : %ds", "Cooldown: %ds");
        put("gui_recipe", "Recette", "Recipe");
        put("gui_status", "Statut", "Status");
        put("gui_click", "Clique pour voir la recette", "Click to see the recipe");
        put("gui_current", "Ton pouvoir", "Your power");
        put("gui_back", "Retour", "Back");
        put("gui_ritual_title", "✦ Le rituel ✦", "✦ The ritual ✦");
        put("ri_1", "1. Crafter une potion de pouvoir lance un rituel de %d minutes à l'endroit du craft.",
                "1. Crafting a power potion starts a %d-minute ritual where it was crafted.");
        put("ri_2", "2. La potion reste suspendue dans les airs pendant tout le rituel.",
                "2. The potion stays suspended in the air for the whole ritual.");
        put("ri_3", "3. Toutes les %s, une coordonnée est révélée à tous les joueurs : X, puis Y, puis Z.",
                "3. Every %s, one coordinate is revealed to every player: X, then Y, then Z.");
        put("ri_4", "4. À la fin, la potion tombe au sol : le premier qui la ramasse la gagne.",
                "4. At the end the potion falls to the ground: the first to pick it up wins it.");
        put("ri_5", "5. Il n'existe que %d exemplaires de chaque pouvoir, un porteur chacun. Si l'un est détruit, brûlé ou disparaît, il redevient disponible.",
                "5. Only %d copies of each power exist, one holder each. If one is destroyed, burned or vanishes, it becomes available again.");
        put("ri_6", "6. Le monde du rituel (Overworld, Nether ou End) est affiché dans la barre et les annonces.",
                "6. The ritual's world (Overworld, Nether or End) is shown in the bar and the announcements.");
        put("ri_7", "7. Une potion de pouvoir ne peut pas être rangée dans un coffre ni dans un ender chest.",
                "7. A power potion cannot be stored in a chest or an ender chest.");

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
        put("p_smoke", "10 % de chance par coup d'aveugler la cible pendant 1 seconde.",
                "10% chance per hit to blind the target for 1 second.");
        put("a_smoke", "Crée un domaine de fumée pendant 3 secondes : toi et tes alliés (/power ally) voyez normalement, les autres sont aveuglés.",
                "Creates a smoke domain for 3 seconds: you and your allies (/power ally) see normally, everyone else is blinded.");
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

    /** Vrai si le client du joueur est en français (la console est considérée en français). */
    public static boolean french(CommandSender sender) {
        if (sender instanceof Player p) {
            Locale l = p.locale();
            return l != null && "fr".equalsIgnoreCase(l.getLanguage());
        }
        return true;
    }

    public static String tr(boolean french, String key, Object... args) {
        Map<String, String> table = french ? FR : EN;
        String template = table.getOrDefault(key, key);
        return args.length == 0 ? template : String.format(template, args);
    }

    public static String tr(CommandSender sender, String key, Object... args) {
        return tr(french(sender), key, args);
    }

    /** Nom du monde : Overworld, Nether ou End (sinon le nom brut). */
    public static String world(boolean french, String name) {
        if (name == null) return "?";
        org.bukkit.World w = org.bukkit.Bukkit.getWorld(name);
        if (w == null) return name;
        return switch (w.getEnvironment()) {
            case NORMAL -> tr(french, "world_normal");
            case NETHER -> tr(french, "world_nether");
            case THE_END -> tr(french, "world_end");
            default -> name;
        };
    }

    /** 125 -> "2:05" */
    public static String clock(long totalSeconds) {
        long s = Math.max(0, totalSeconds);
        return String.format("%d:%02d", s / 60, s % 60);
    }

    /** 150 -> "2 min 30 s", 120 -> "2 min", 45 -> "45 s" */
    public static String duration(long totalSeconds) {
        long s = Math.max(0, totalSeconds);
        long m = s / 60;
        long r = s % 60;
        if (m == 0) return r + " s";
        return r == 0 ? m + " min" : m + " min " + r + " s";
    }
}
