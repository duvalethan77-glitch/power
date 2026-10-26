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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class PowerListener implements Listener {

    private final PowerManager manager;
    private final Plugin plugin;

    public PowerListener(PowerManager manager) {
        this.manager = manager;
        this.plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(PowerPlugin.class);
    }

    // --- Boire une potion de pouvoir ---------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        PowerType type = manager.potionType(e.getItem());
        if (type == null) return;

        Player p = e.getPlayer();
        if (manager.getPower(p) == type) {
            e.setCancelled(true);
            p.sendMessage(Component.text("Tu as déjà le pouvoir " + type.display + ".", NamedTextColor.RED));
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

        e.getDrops().removeIf(manager::isPowerItem);
        manager.clearPower(p);
        manager.dropPotion(p.getLocation(), type);
    }

    // --- Protection des objets de pouvoir ----------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (manager.isPowerItem(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getType() == InventoryType.CRAFTING) return;

        ItemStack current = e.getCurrentItem();
        ItemStack cursor = e.getCursor();

        if (e.isShiftClick() && manager.isPowerItem(current)) {
            e.setCancelled(true);
            return;
        }

        if (e.getClickedInventory() != null && e.getClickedInventory().getType() != InventoryType.PLAYER) {
            if (manager.isPowerItem(cursor)) {
                e.setCancelled(true);
                return;
            }
            if (e.getClick() == ClickType.NUMBER_KEY && e.getHotbarButton() >= 0) {
                ItemStack hotbar = e.getWhoClicked().getInventory().getItem(e.getHotbarButton());
                if (manager.isPowerItem(hotbar)) {
                    e.setCancelled(true);
                    return;
                }
            }
            if (e.getClick() == ClickType.SWAP_OFFHAND
                    && manager.isPowerItem(e.getWhoClicked().getInventory().getItemInOffHand())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!manager.isPowerItem(e.getOldCursor())) return;
        int topSize = e.getView().getTopInventory().getSize();
        if (e.getView().getTopInventory().getType() == InventoryType.CRAFTING) return;
        for (int raw : e.getRawSlots()) {
            if (raw < topSize) {
                e.setCancelled(true);
                return;
            }
        }
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
