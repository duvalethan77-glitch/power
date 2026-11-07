package com.power;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ShapedRecipe;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Recettes des potions de pouvoir, en grille 3x3 :
 *
 *   coin  côté  coin
 *   côté centre côté
 *   coin  côté  coin
 *
 * Les 4 coins sont 4 armor trims (smithing templates) DIFFÉRENTS, donc 4 structures à explorer.
 * Les côtés sont des objets rares et le centre est un objet de boss ou de trésor.
 * Aucun objet de l'End.
 */
public final class Recipes {

    public record Def(Material[] corners, Material[] edges, Material center) {
        /** Les 9 cases de la grille, ligne par ligne. */
        public Material[] grid() {
            return new Material[] {
                    corners[0], edges[0], corners[1],
                    edges[1], center, edges[2],
                    corners[2], edges[3], corners[3]
            };
        }
    }

    private static final Map<PowerType, Def> DEFS = new EnumMap<>(PowerType.class);

    static {
        // Lightning : chambre d'épreuve, avant-poste, forteresse
        DEFS.put(PowerType.LIGHTNING, def(
                new String[] {"BOLT_ARMOR_TRIM_SMITHING_TEMPLATE", "FLOW_ARMOR_TRIM_SMITHING_TEMPLATE",
                        "SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE", "EYE_ARMOR_TRIM_SMITHING_TEMPLATE"},
                new String[] {"NETHERITE_INGOT", "ENCHANTED_GOLDEN_APPLE", "ENCHANTED_GOLDEN_APPLE", "NETHERITE_INGOT"},
                "NETHER_STAR"));

        // Ice : océan, épave, jungle, désert
        DEFS.put(PowerType.ICE, def(
                new String[] {"COAST_ARMOR_TRIM_SMITHING_TEMPLATE", "TIDE_ARMOR_TRIM_SMITHING_TEMPLATE",
                        "WILD_ARMOR_TRIM_SMITHING_TEMPLATE", "DUNE_ARMOR_TRIM_SMITHING_TEMPLATE"},
                new String[] {"DIAMOND_BLOCK", "NAUTILUS_SHELL", "NAUTILUS_SHELL", "DIAMOND_BLOCK"},
                "HEART_OF_THE_SEA"));

        // Blood : forteresse du Nether, bastion, manoir, avant-poste
        DEFS.put(PowerType.BLOOD, def(
                new String[] {"RIB_ARMOR_TRIM_SMITHING_TEMPLATE", "SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE",
                        "VEX_ARMOR_TRIM_SMITHING_TEMPLATE", "SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE"},
                new String[] {"GHAST_TEAR", "WITHER_ROSE", "WITHER_ROSE", "GHAST_TEAR"},
                "WITHER_SKELETON_SKULL"));

        // Fire : bastion, forteresse, désert, jungle
        DEFS.put(PowerType.FIRE, def(
                new String[] {"SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE", "RIB_ARMOR_TRIM_SMITHING_TEMPLATE",
                        "DUNE_ARMOR_TRIM_SMITHING_TEMPLATE", "WILD_ARMOR_TRIM_SMITHING_TEMPLATE"},
                new String[] {"NETHERITE_UPGRADE_SMITHING_TEMPLATE", "NETHERITE_INGOT", "NETHERITE_INGOT",
                        "NETHERITE_UPGRADE_SMITHING_TEMPLATE"},
                "MUSIC_DISC_PIGSTEP"));

        // Wind : chambres d'épreuve et ruines de sentier
        DEFS.put(PowerType.WIND, def(
                new String[] {"FLOW_ARMOR_TRIM_SMITHING_TEMPLATE", "BOLT_ARMOR_TRIM_SMITHING_TEMPLATE",
                        "WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE", "HOST_ARMOR_TRIM_SMITHING_TEMPLATE"},
                new String[] {"BREEZE_ROD", "OMINOUS_TRIAL_KEY", "OMINOUS_TRIAL_KEY", "BREEZE_ROD"},
                "HEAVY_CORE"));

        // Shadow : cité antique et ruines de sentier
        DEFS.put(PowerType.SHADOW, def(
                new String[] {"SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE", "WARD_ARMOR_TRIM_SMITHING_TEMPLATE",
                        "RAISER_ARMOR_TRIM_SMITHING_TEMPLATE", "SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE"},
                new String[] {"ECHO_SHARD", "SCULK_CATALYST", "SCULK_CATALYST", "ECHO_SHARD"},
                "RECOVERY_COMPASS"));

        // Earth : désert, jungle, avant-poste, océan
        DEFS.put(PowerType.EARTH, def(
                new String[] {"DUNE_ARMOR_TRIM_SMITHING_TEMPLATE", "WILD_ARMOR_TRIM_SMITHING_TEMPLATE",
                        "SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE", "TIDE_ARMOR_TRIM_SMITHING_TEMPLATE"},
                new String[] {"EMERALD_BLOCK", "ANCIENT_DEBRIS", "ANCIENT_DEBRIS", "EMERALD_BLOCK"},
                "ENCHANTED_GOLDEN_APPLE"));

        // Poison : manoir, jungle, forteresse, forteresse enfouie
        DEFS.put(PowerType.POISON, def(
                new String[] {"VEX_ARMOR_TRIM_SMITHING_TEMPLATE", "WILD_ARMOR_TRIM_SMITHING_TEMPLATE",
                        "RIB_ARMOR_TRIM_SMITHING_TEMPLATE", "EYE_ARMOR_TRIM_SMITHING_TEMPLATE"},
                new String[] {"WITHER_ROSE", "GHAST_TEAR", "GHAST_TEAR", "WITHER_ROSE"},
                "TRIDENT"));

        // Light : les quatre trims des ruines de sentier
        DEFS.put(PowerType.LIGHT, def(
                new String[] {"WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE", "HOST_ARMOR_TRIM_SMITHING_TEMPLATE",
                        "RAISER_ARMOR_TRIM_SMITHING_TEMPLATE", "SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE"},
                new String[] {"GOLD_BLOCK", "ENCHANTED_GOLDEN_APPLE", "ENCHANTED_GOLDEN_APPLE", "GOLD_BLOCK"},
                "BEACON"));

        // Phoenix : bastion, forteresse, chambre d'épreuve, ruines de sentier
        DEFS.put(PowerType.PHOENIX, def(
                new String[] {"HOST_ARMOR_TRIM_SMITHING_TEMPLATE", "SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE",
                        "RIB_ARMOR_TRIM_SMITHING_TEMPLATE", "BOLT_ARMOR_TRIM_SMITHING_TEMPLATE"},
                new String[] {"NETHERITE_INGOT", "ENCHANTED_GOLDEN_APPLE", "ENCHANTED_GOLDEN_APPLE", "NETHERITE_INGOT"},
                "TOTEM_OF_UNDYING"));
    }

    static {
        // Smoke : cité antique, manoir, forteresse enfouie, épave
        DEFS.put(PowerType.SMOKE, def(
                new String[] {"SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE", "VEX_ARMOR_TRIM_SMITHING_TEMPLATE",
                        "EYE_ARMOR_TRIM_SMITHING_TEMPLATE", "TIDE_ARMOR_TRIM_SMITHING_TEMPLATE"},
                new String[] {"DISC_FRAGMENT_5", "WITHER_ROSE", "WITHER_ROSE", "DISC_FRAGMENT_5"},
                "MUSIC_DISC_OTHERSIDE"));
    }

    private Recipes() {}

    private static Def def(String[] corners, String[] edges, String center) {
        Material[] c = new Material[4];
        Material[] e = new Material[4];
        for (int i = 0; i < 4; i++) {
            c[i] = mat(corners[i]);
            e[i] = mat(edges[i]);
        }
        return new Def(c, e, mat(center));
    }

    /** Cherche l'objet par son nom ; s'il n'existe pas sur cette version, on met un bloc de diamant. */
    private static Material mat(String name) {
        Material m = Material.matchMaterial(name);
        if (m == null) {
            Bukkit.getLogger().warning("[Power] Objet introuvable pour une recette : " + name);
            return Material.DIAMOND_BLOCK;
        }
        return m;
    }

    public static Def get(PowerType type) {
        return DEFS.get(type);
    }

    public static NamespacedKey key(PowerPlugin plugin, PowerType type) {
        return new NamespacedKey(plugin, "potion_" + type.configKey());
    }

    public static void register(PowerPlugin plugin, PowerManager manager) {
        for (PowerType type : PowerType.values()) {
            Def d = DEFS.get(type);
            if (d == null) continue;

            NamespacedKey key = key(plugin, type);
            Bukkit.removeRecipe(key);

            // Une lettre par objet différent
            Material[] grid = d.grid();
            Map<Material, Character> letters = new LinkedHashMap<>();
            StringBuilder[] rows = {new StringBuilder(), new StringBuilder(), new StringBuilder()};
            char next = 'A';
            for (int i = 0; i < 9; i++) {
                Character letter = letters.get(grid[i]);
                if (letter == null) {
                    letter = next++;
                    letters.put(grid[i], letter);
                }
                rows[i / 3].append(letter.charValue());
            }

            ShapedRecipe recipe = new ShapedRecipe(key, manager.createPotion(type));
            recipe.shape(rows[0].toString(), rows[1].toString(), rows[2].toString());
            for (Map.Entry<Material, Character> entry : letters.entrySet()) {
                recipe.setIngredient(entry.getValue().charValue(), entry.getKey());
            }
            Bukkit.addRecipe(recipe);
        }
    }

    /** Pouvoir correspondant à une recette de potion (null si ce n'est pas une des nôtres). */
    public static PowerType typeOf(PowerPlugin plugin, org.bukkit.Keyed recipe) {
        if (recipe == null) return null;
        for (PowerType type : PowerType.values()) {
            if (key(plugin, type).equals(recipe.getKey())) return type;
        }
        return null;
    }

    public static void unregister(PowerPlugin plugin) {
        for (PowerType type : PowerType.values()) {
            Bukkit.removeRecipe(key(plugin, type));
        }
    }

    /** BOLT_ARMOR_TRIM_SMITHING_TEMPLATE -> "Bolt Trim", NETHER_STAR -> "Nether Star" */
    public static String pretty(Material m) {
        String name = m.name()
                .replace("_ARMOR_TRIM_SMITHING_TEMPLATE", "_TRIM")
                .replace("_SMITHING_TEMPLATE", "_TEMPLATE");
        StringBuilder out = new StringBuilder();
        for (String part : name.split("_")) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(part.charAt(0)).append(part.substring(1).toLowerCase());
        }
        return out.toString();
    }
}
