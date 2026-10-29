package com.power;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Rend le menu /power info en lecture seule et gère la navigation. */
public final class InfoGuiListener implements Listener {

    private final PowerPlugin plugin;
    private final InfoGui gui;

    public InfoGuiListener(PowerPlugin plugin, InfoGui gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof InfoGui.Holder holder)) return;
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) return;

        int slot = e.getSlot();
        if (holder.type == null) {
            PowerType type = holder.slots.get(slot);
            if (type != null) {
                plugin.getServer().getScheduler().runTask(plugin, () -> gui.openDetail(p, type));
            }
        } else if (slot == InfoGui.BACK_SLOT) {
            plugin.getServer().getScheduler().runTask(plugin, () -> gui.openMenu(p));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof InfoGui.Holder) {
            e.setCancelled(true);
        }
    }
}
