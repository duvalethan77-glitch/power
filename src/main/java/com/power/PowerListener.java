package com.power;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PowerListener implements Listener {

    private final PowerPlugin plugin;
    private final PowerManager manager;

    public PowerListener(PowerPlugin plugin, PowerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // --- Boire une potion de pouvoir ---------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        PowerType type = manager.potionType(e.getItem());
        if (type == null) return;

        Player p = e.getPlayer();
        if (manager.getPower(p) == type) {
            e.setCancelled(true);
            p.sendMessage(Component.text(Lang.tr(p, "already_has", type.display), NamedTextColor.RED));
            return;
        }

        // Une fois la boisson terminée, on change de pouvoir
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) {
                manager.setPower(p, type);
            }
        });
    }

    // --- Mort : la potion tombe avec le stuff ------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        PowerType type = manager.getPower(p);
        if (type == null) return;

        manager.clearPower(p);
        manager.dropPotion(p.getLocation(), type);
    }

    // --- Potion au sol ------------------------------------------------------

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        manager.removeDisplays(e.getItem());
    }

    @EventHandler
    public void onDespawn(ItemDespawnEvent e) {
        manager.removeDisplays(e.getEntity());
    }

    // --- Connexion ----------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        manager.refresh(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        manager.forget(e.getPlayer());
    }
}
