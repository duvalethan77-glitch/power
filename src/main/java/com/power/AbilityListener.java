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
import org.bukkit.event.player.PlayerSwapHandItemEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/** Déclenche la capacité active : clic droit (ou touche F si activée dans la config). */
public final class AbilityListener implements Listener {

    /** Objets qui ont déjà un usage au clic droit : on ne les détourne pas. */
    private static final Set<Material> OWN_USE = Set.of(
            Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION,
            Material.BOW, Material.CROSSBOW, Material.TRIDENT, Material.SHIELD,
            Material.FISHING_ROD, Material.ENDER_PEARL, Material.ENDER_EYE,
            Material.SNOWBALL, Material.EGG, Material.FIREWORK_ROCKET, Material.WIND_CHARGE,
            Material.FLINT_AND_STEEL, Material.FIRE_CHARGE, Material.BONE_MEAL,
            Material.ELYTRA, Material.SPYGLASS, Material.GOAT_HORN, Material.WRITTEN_BOOK,
            Material.WRITABLE_BOOK, Material.MILK_BUCKET);

    private final PowerPlugin plugin;
    private final PowerManager manager;
    private final Abilities abilities;

    public AbilityListener(PowerPlugin plugin, PowerManager manager, Abilities abilities) {
        this.plugin = plugin;
        this.manager = manager;
        this.abilities = abilities;
    }

    private boolean hasOwnUse(Material type, Action action) {
        if (type.isEdible() || OWN_USE.contains(type)) return true;
        String name = type.name();
        if (name.endsWith("_BUCKET") || name.endsWith("_SPAWN_EGG") || name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
                || name.endsWith("_BOAT") || name.endsWith("_MINECART") || name.endsWith("_SIGN")) {
            return true;
        }
        // Un bloc en main sert à construire quand on clique sur un bloc
        return action == Action.RIGHT_CLICK_BLOCK && type.isBlock();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        if (plugin.getConfig().getBoolean("activation.require-sneak", false) && !p.isSneaking()) return;

        ItemStack item = e.getItem();
        if (item != null && hasOwnUse(item.getType(), action)) return;

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
    public void onSwap(PlayerSwapHandItemEvent e) {
        if (!plugin.getConfig().getBoolean("activation.swap-key", false)) return;
        Player p = e.getPlayer();
        if (manager.getPower(p) == null) return;

        e.setCancelled(true);
        tryCast(p);
    }

    /** Lance la capacité si le cooldown est fini. Retourne true si elle est partie. */
    private boolean tryCast(Player p) {
        PowerType type = manager.getPower(p);
        if (type == null) return false;

        long left = manager.cooldownLeftMs(p, type);
        if (left > 0) {
            int seconds = (int) Math.ceil(left / 1000.0);
            p.sendActionBar(Component.text(Lang.tr(p, "cooldown", seconds), NamedTextColor.RED));
            return false;
        }

        if (abilities.cast(p, type)) {
            manager.startCooldown(p, type);
            manager.sendHud(p);
            return true;
        }
        return false;
    }
}
