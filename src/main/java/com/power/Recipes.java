package com.power;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ShapedRecipe;

import java.util.EnumMap;
import java.util.Map;

/**
 * Recettes des potions de pouvoir. Forme (3x3) : coins A, côtés B, centre C.
 * Aucun objet de l'End n'est utilisé.
 */
public final class Recipes {

    public record Def(Material corner, Material edge, Material center) { }

    private static final Map<PowerType, Def> DEFS = new EnumMap<>(PowerType.class);

    static {
        DEFS.put(PowerType.LIGHTNING, new Def(Material.LIGHTNING_ROD, Material.COPPER_BLOCK, Material.NETHER_STAR));
        DEFS.put(PowerType.ICE, new Def(Material.BLUE_ICE, Material.DIAMOND_BLOCK, Material.HEART_OF_THE_SEA));
        DEFS.put(PowerType.BLOOD, new Def(Material.REDSTONE_BLOCK, Material.GHAST_TEAR, Material.WITHER_SKELETON_SKULL));
        DEFS.put(PowerType.FIRE, new Def(Material.BLAZE_ROD, Material.MAGMA_BLOCK, Material.NETHERITE_INGOT));
        DEFS.put(PowerType.WIND, new Def(Material.BREEZE_ROD, Material.PHANTOM_MEMBRANE, Material.HEAVY_CORE));
        DEFS.put(PowerType.SHADOW, new Def(Material.ECHO_SHARD, Material.SCULK_CATALYST, Material.SCULK_SHRIEKER));
        DEFS.put(PowerType.EARTH, new Def(Material.EMERALD_BLOCK, Material.ANCIENT_DEBRIS, Material.ENCHANTED_GOLDEN_APPLE));
        DEFS.put(PowerType.POISON, new Def(Material.WITHER_ROSE, Material.SLIME_BLOCK, Material.TRIDENT));
        DEFS.put(PowerType.LIGHT, new Def(Material.SEA_LANTERN, Material.GOLD_BLOCK, Material.BEACON));
        DEFS.put(PowerType.PHOENIX, new Def(Material.NETHERITE_INGOT, Material.GOLD_BLOCK, Material.TOTEM_OF_UNDYING));
    }

    private Recipes() {}

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

            ShapedRecipe recipe = new ShapedRecipe(key, manager.createPotion(type));
            recipe.shape("ABA", "BCB", "ABA");
            recipe.setIngredient('A', d.corner());
            recipe.setIngredient('B', d.edge());
            recipe.setIngredient('C', d.center());
            Bukkit.addRecipe(recipe);
        }
    }

    public static void unregister(PowerPlugin plugin) {
        for (PowerType type : PowerType.values()) {
            Bukkit.removeRecipe(key(plugin, type));
        }
    }

    /** NETHER_STAR -> "Nether Star" */
    public static String pretty(Material m) {
        StringBuilder out = new StringBuilder();
        for (String part : m.name().split("_")) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(part.charAt(0)).append(part.substring(1).toLowerCase());
        }
        return out.toString();
    }
}
