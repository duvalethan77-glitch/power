package com.power;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Déclenche la capacité active : accroupi + clic droit, avec un objet en main. */
public final class AbilityListener implements Listener {

    private final PowerManager manager;
    private final Abilities abilities;

    public AbilityListener(PowerManager manager, Abilities abilities) {
        this.manager = manager;
        this.abilities = abilities;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        if (!p.isSneaking()) return;

        PowerType type = manager.getPower(p);
        if (type == null) return;

        long left = manager.cooldownLeftMs(p, type);
        if (left > 0) {
            int seconds = (int) Math.ceil(left / 1000.0);
            p.sendActionBar(Component.text(Lang.tr(p, "cooldown", seconds), NamedTextColor.RED));
            return;
        }

        if (abilities.cast(p, type)) {
            manager.startCooldown(p, type);
            manager.sendHud(p);
            e.setCancelled(true);
        }
    }
}
