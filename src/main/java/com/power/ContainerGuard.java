package com.power;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Une potion de pouvoir ne peut pas être rangée : ni coffre, ni ender chest, ni entonnoir, ni cadre. */
public final class ContainerGuard implements Listener {

    private final PowerManager manager;

    public ContainerGuard(PowerManager manager) {
        this.manager = manager;
    }

    private boolean isPower(ItemStack item) {
        return item != null && !item.getType().isAir() && manager.potionType(item) != null;
    }

    /** Vrai si l'inventaire du haut sert à ranger des objets (tout sauf l'inventaire du joueur). */
    private boolean storage(Inventory top) {
        InventoryType t = top.getType();
        return t != InventoryType.CRAFTING && t != InventoryType.CREATIVE
                && !(top.getHolder() instanceof InfoGui.Holder);
    }

    private void warn(Object who) {
        if (who instanceof Player p) {
            manager.notice(p, Component.text("✖ " + Lang.tr(p, "no_container"), NamedTextColor.RED), 2000);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!storage(top)) return;

        boolean block = false;
        if (e.getClickedInventory() == top) {
            // On pose la potion du curseur dans le conteneur
            if (isPower(e.getCursor())) block = true;
            // Touche numérique ou main secondaire : la potion est dans la barre rapide
            if (e.getClick() == ClickType.NUMBER_KEY) {
                int slot = e.getHotbarButton();
                if (slot >= 0 && isPower(e.getWhoClicked().getInventory().getItem(slot))) block = true;
            }
            if (e.getClick() == ClickType.SWAP_OFFHAND
                    && isPower(e.getWhoClicked().getInventory().getItemInOffHand())) {
                block = true;
            }
        } else if (e.getClickedInventory() != null && e.isShiftClick() && isPower(e.getCurrentItem())) {
            // Clic + shift depuis ton inventaire vers le conteneur
            block = true;
        }

        if (block) {
            e.setCancelled(true);
            warn(e.getWhoClicked());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!storage(top) || !isPower(e.getOldCursor())) return;
        for (int raw : e.getRawSlots()) {
            if (raw < top.getSize()) {
                e.setCancelled(true);
                warn(e.getWhoClicked());
                return;
            }
        }
    }

    /** Entonnoirs et autres transferts automatiques. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent e) {
        if (isPower(e.getItem())) {
            e.setCancelled(true);
        }
    }

    /** Cadres et porte-armures. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntity(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof ItemFrame) && !(e.getRightClicked() instanceof ArmorStand)) return;
        Player p = e.getPlayer();
        ItemStack held = e.getHand() == EquipmentSlot.HAND
                ? p.getInventory().getItemInMainHand()
                : p.getInventory().getItemInOffHand();
        if (isPower(held)) {
            e.setCancelled(true);
            warn(p);
        }
    }
}
