package com.power;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Déclenche la capacité active : clic droit (ou touche F si activée dans la config). */
public final class AbilityListener implements Listener {

    /** Objets qui ont déjà un usage au clic droit : tant qu'on les tient, la capacité ne part pas. */
    private static final Set<Material> OWN_USE = new HashSet<>();

    static {
        String[] names = {
                "POTION", "SPLASH_POTION", "LINGERING_POTION", "EXPERIENCE_BOTTLE", "OMINOUS_BOTTLE",
                "HONEY_BOTTLE", "MILK_BUCKET", "BOW", "CROSSBOW", "TRIDENT", "MACE", "SHIELD",
                "FISHING_ROD", "CARROT_ON_A_STICK", "WARPED_FUNGUS_ON_A_STICK", "ENDER_PEARL", "ENDER_EYE",
                "SNOWBALL", "EGG", "BLUE_EGG", "BROWN_EGG", "FIREWORK_ROCKET", "WIND_CHARGE", "FIRE_CHARGE",
                "FLINT_AND_STEEL", "BONE_MEAL", "ELYTRA", "SPYGLASS", "GOAT_HORN", "WRITTEN_BOOK",
                "WRITABLE_BOOK", "KNOWLEDGE_BOOK", "MAP", "FILLED_MAP", "BRUSH", "SHEARS", "LEAD", "NAME_TAG",
                "SADDLE", "ARMOR_STAND", "PAINTING", "ITEM_FRAME", "GLOW_ITEM_FRAME", "END_CRYSTAL",
                "COMPASS", "RECOVERY_COMPASS", "CLOCK", "TOTEM_OF_UNDYING", "BUNDLE", "SPLASH_WATER_BOTTLE",
                "GLASS_BOTTLE", "FIREWORK_STAR", "LODESTONE", "POWDER_SNOW_BUCKET", "COD_BUCKET"
        };
        for (String n : names) {
            Material m = Material.matchMaterial(n);
            if (m != null) OWN_USE.add(m);
        }
    }

    private static final String[] OWN_USE_SUFFIXES = {
            "_SPEAR", "_BUCKET", "_SPAWN_EGG", "_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS",
            "_BOAT", "_RAFT", "_MINECART", "_HORN", "_BUNDLE", "_HORSE_ARMOR", "_HEAD", "_SKULL",
            "_SIGN", "_BANNER", "_BED", "_CHEST_BOAT", "_CHEST_RAFT", "_WOLF_ARMOR", "_NAUTILUS_ARMOR"
    };

    private static final String[] BLOCK_TOOL_SUFFIXES = {"_HOE", "_AXE", "_SHOVEL"};

    /** Dernière tentative par joueur : évite un double déclenchement (main principale + secondaire). */
    private final Map<UUID, Long> lastTry = new HashMap<>();

    private final PowerPlugin plugin;
    private final PowerManager manager;
    private final Abilities abilities;

    public AbilityListener(PowerPlugin plugin, PowerManager manager, Abilities abilities) {
        this.plugin = plugin;
        this.manager = manager;
        this.abilities = abilities;
    }

    /** Vrai si cet objet sert déjà à quelque chose au clic droit : on laisse faire Minecraft. */
    private boolean hasOwnUse(ItemStack item, Action action) {
        if (item == null) return false;
        Material type = item.getType();
        if (type.isAir()) return false;
        if (type.isEdible() || OWN_USE.contains(type)) return true;
        String name = type.name();
        for (String suffix : OWN_USE_SUFFIXES) {
            if (name.endsWith(suffix)) return true;
        }
        if (action == Action.RIGHT_CLICK_BLOCK) {
            for (String suffix : BLOCK_TOOL_SUFFIXES) {
                if (name.endsWith(suffix)) return true;
            }
            // Un bloc en main sert à construire quand on clique sur un bloc
            return type.isBlock();
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        EquipmentSlot hand = e.getHand();
        if (hand == null) return;
        // Main principale vide + objet en main secondaire : Minecraft n'envoie alors que la main secondaire
        if (hand == EquipmentSlot.OFF_HAND && !e.getPlayer().getInventory().getItemInMainHand().getType().isAir()) {
            return;
        }
        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        if (plugin.getConfig().getBoolean("activation.require-sneak", false) && !p.isSneaking()) return;

        // On regarde les DEUX mains : un objet utilisable dans l'une ou l'autre bloque la capacité
        if (hasOwnUse(p.getInventory().getItemInMainHand(), action)
                || hasOwnUse(p.getInventory().getItemInOffHand(), action)) {
            return;
        }

        // On n'empêche pas d'ouvrir un coffre, une porte, un établi...
        if (action == Action.RIGHT_CLICK_BLOCK && !p.isSneaking()
                && e.getClickedBlock() != null && e.getClickedBlock().getType().isInteractable()) {
            return;
        }

        if (tryCast(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (!plugin.getConfig().getBoolean("activation.swap-key", true)) return;
        Player p = e.getPlayer();
        if (manager.getPower(p) == null) return;

        e.setCancelled(true);
        tryCast(p);
    }

    /** Lance la capacité si le cooldown est fini. Retourne true si elle est partie. */
    private boolean tryCast(Player p) {
        PowerType type = manager.getPower(p);
        if (type == null) return false;

        long now = System.currentTimeMillis();
        Long previous = lastTry.put(p.getUniqueId(), now);
        if (previous != null && now - previous < 200L) return false;

        long left = manager.cooldownLeftMs(p, type);
        if (left > 0) {
            int seconds = (int) Math.ceil(left / 1000.0);
            manager.notice(p, Component.text("⏳ " + Lang.tr(p, "cooldown", seconds), NamedTextColor.RED), 1200);
            return false;
        }

        if (!abilities.cast(p, type)) return false;

        if (type == PowerType.WIND) {
            if (manager.isWindPending(p)) {
                // Second dash : le cooldown démarre maintenant
                manager.endWind(p);
                manager.startCooldown(p, type);
            } else {
                // Premier dash : le cooldown démarre après le second, ou à la fin de la fenêtre
                int window = Math.max(1, plugin.getConfig().getInt("wind.second-dash-window-seconds", 4));
                manager.beginWind(p, window);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (manager.endWind(p)) {
                        manager.startCooldown(p, PowerType.WIND);
                        manager.sendHud(p);
                    }
                }, window * 20L);
            }
        } else {
            manager.startCooldown(p, type);
        }
        manager.sendHud(p);
        return true;
    }
}
