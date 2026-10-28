package com.power;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PowerManager {

    private final PowerPlugin plugin;

    private final NamespacedKey powerKey;    // pouvoir stocké sur le joueur
    private final NamespacedKey itemKey;     // (ancienne version) objets d'équipement
    private final NamespacedKey potionKey;   // potion de pouvoir
    private final NamespacedKey displayKey;  // affichage géant d'une potion au sol
    private final NamespacedKey healthKey;   // (ancienne version) modificateur de vie max
    private final NamespacedKey absorbKey;   // (ancienne version) modificateur d'absorption
    private final NamespacedKey fireballKey; // boule de feu lancée par un pouvoir

    private final Map<UUID, EnumMap<PowerType, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Long> berserkUntil = new HashMap<>();
    private boolean inAbility;

    public PowerManager(PowerPlugin plugin) {
        this.plugin = plugin;
        this.powerKey = new NamespacedKey(plugin, "power");
        this.itemKey = new NamespacedKey(plugin, "power_item");
        this.potionKey = new NamespacedKey(plugin, "power_potion");
        this.displayKey = new NamespacedKey(plugin, "power_display");
        this.healthKey = new NamespacedKey(plugin, "power_health");
        this.absorbKey = new NamespacedKey(plugin, "power_absorption");
        this.fireballKey = new NamespacedKey(plugin, "power_fireball");
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public NamespacedKey fireballKey() {
        return fireballKey;
    }

    // ------------------------------------------------------------------
    // État du joueur
    // ------------------------------------------------------------------

    public PowerType getPower(Player p) {
        return PowerType.parse(p.getPersistentDataContainer().get(powerKey, PersistentDataType.STRING));
    }

    private void store(Player p, PowerType type) {
        if (type == null) {
            p.getPersistentDataContainer().remove(powerKey);
        } else {
            p.getPersistentDataContainer().set(powerKey, PersistentDataType.STRING, type.name());
        }
    }

    /** Boit une potion : remplace l'ancien pouvoir, qui est rendu sous forme de potion. */
    public void setPower(Player p, PowerType type) {
        PowerType old = getPower(p);
        if (old != null) {
            strip(p);
            giveBack(p, createPotion(old));
        }
        store(p, type);
        sendHud(p);
    }

    /** Retire le pouvoir sans rendre la potion. */
    public void clearPower(Player p) {
        if (getPower(p) != null) {
            strip(p);
        }
        store(p, null);
    }

    /** Le joueur se défait de son pouvoir : il récupère la potion correspondante. */
    public boolean releasePower(Player p) {
        PowerType old = getPower(p);
        if (old == null) return false;
        strip(p);
        store(p, null);
        giveBack(p, createPotion(old));
        return true;
    }

    /** Réapplique l'état du joueur (connexion, rechargement). */
    public void refresh(Player p) {
        migrateLegacyPower(p);
        removeLegacyItems(p);
        sendHud(p);
    }

    private void strip(Player p) {
        berserkUntil.remove(p.getUniqueId());
        p.sendActionBar(Component.empty());
    }

    public void forget(Player p) {
        berserkUntil.remove(p.getUniqueId());
    }

    // ------------------------------------------------------------------
    // Cooldowns
    // ------------------------------------------------------------------

    public long cooldownLeftMs(Player p, PowerType type) {
        EnumMap<PowerType, Long> map = cooldowns.get(p.getUniqueId());
        if (map == null) return 0L;
        Long until = map.get(type);
        if (until == null) return 0L;
        return Math.max(0L, until - System.currentTimeMillis());
    }

    public void startCooldown(Player p, PowerType type) {
        long seconds = cfg().getLong(type.configKey() + ".cooldown-seconds", type.defaultCooldown);
        cooldowns.computeIfAbsent(p.getUniqueId(), k -> new EnumMap<>(PowerType.class))
                .put(type, System.currentTimeMillis() + seconds * 1000L);
    }

    // ------------------------------------------------------------------
    // Blood : mode berserk
    // ------------------------------------------------------------------

    public void startBerserk(Player p, int seconds) {
        berserkUntil.put(p.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
    }

    public boolean isBerserk(Player p) {
        Long until = berserkUntil.get(p.getUniqueId());
        if (until == null) return false;
        if (until < System.currentTimeMillis()) {
            berserkUntil.remove(p.getUniqueId());
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Garde-fou : les dégâts d'un pouvoir ne déclenchent pas les passifs
    // ------------------------------------------------------------------

    public void runAbility(Runnable action) {
        boolean previous = inAbility;
        inAbility = true;
        try {
            action.run();
        } finally {
            inAbility = previous;
        }
    }

    public boolean isInAbility() {
        return inAbility;
    }

    // ------------------------------------------------------------------
    // Tick (toutes les secondes) et affichage
    // ------------------------------------------------------------------

    public void tick() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            migrateLegacyPower(p);
            sendHud(p);
        }
        cleanupDisplays();
    }

    public void sendHud(Player p) {
        PowerType type = getPower(p);
        if (type == null) return;

        long left = cooldownLeftMs(p, type);
        Component hud = Component.text("■ ", type.textColor)
                .append(Component.text(type.display, NamedTextColor.WHITE));
        if (left > 0) {
            hud = hud.append(Component.text(" " + (int) Math.ceil(left / 1000.0) + "s", NamedTextColor.GRAY));
        } else {
            hud = hud.append(Component.text(" ✔", NamedTextColor.GREEN));
        }
        p.sendActionBar(hud);
    }

    // ------------------------------------------------------------------
    // Migration depuis l'ancienne version (Strength, Speed, Saturation, Health, Void)
    // ------------------------------------------------------------------

    private void migrateLegacyPower(Player p) {
        String raw = p.getPersistentDataContainer().get(powerKey, PersistentDataType.STRING);
        if (raw == null || PowerType.parse(raw) != null) return;

        p.getPersistentDataContainer().remove(powerKey);
        removeModifier(p, Attribute.MAX_HEALTH, healthKey);
        removeModifier(p, Attribute.MAX_ABSORPTION, absorbKey);
        removeInfinite(p, PotionEffectType.STRENGTH);
        removeInfinite(p, PotionEffectType.SPEED);
        removeInfinite(p, PotionEffectType.SATURATION);
    }

    private void removeInfinite(Player p, PotionEffectType effect) {
        PotionEffect cur = p.getPotionEffect(effect);
        if (cur != null && cur.isInfinite()) {
            p.removePotionEffect(effect);
        }
    }

    private void removeModifier(Player p, Attribute attribute, NamespacedKey key) {
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst == null) return;
        for (AttributeModifier m : List.copyOf(inst.getModifiers())) {
            if (key.equals(m.getKey())) {
                inst.removeModifier(m);
            }
        }
    }

    private boolean isLegacy(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        if (pdc.has(itemKey, PersistentDataType.STRING)) return true;
        String potion = pdc.get(potionKey, PersistentDataType.STRING);
        return potion != null && PowerType.parse(potion) == null;
    }

    private void removeLegacyItems(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isLegacy(contents[i])) {
                inv.setItem(i, null);
            }
        }
        if (isLegacy(p.getItemOnCursor())) {
            p.setItemOnCursor(null);
        }
    }

    // ------------------------------------------------------------------
    // Objets : potions
    // ------------------------------------------------------------------

    public ItemStack createPotion(PowerType type) {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setColor(type.potionColor);
        meta.displayName(Component.text(type.display, type.textColor)
                .decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(potionKey, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    public PowerType potionType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        return PowerType.parse(meta.getPersistentDataContainer().get(potionKey, PersistentDataType.STRING));
    }

    public void giveBack(Player p, ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        Map<Integer, ItemStack> left = p.getInventory().addItem(item);
        for (ItemStack rest : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }

    // ------------------------------------------------------------------
    // Potion au sol (brille et devient grande)
    // ------------------------------------------------------------------

    public void dropPotion(Location loc, PowerType type) {
        World world = loc.getWorld();
        if (world == null) return;

        Item dropped = world.dropItem(loc, createPotion(type), item -> {
            item.setGlowing(true);
            item.customName(Component.text(type.display + " Power", type.textColor));
            item.setCustomNameVisible(true);
        });

        ItemDisplay display = world.spawn(loc, ItemDisplay.class, d -> {
            d.setItemStack(createPotion(type));
            d.setTransformation(new Transformation(
                    new Vector3f(0f, 0.1f, 0f),
                    new AxisAngle4f(),
                    new Vector3f(2.5f, 2.5f, 2.5f),
                    new AxisAngle4f()));
            d.setGlowing(true);
            d.setGlowColorOverride(type.potionColor);
            d.setPersistent(false);
            d.getPersistentDataContainer().set(displayKey, PersistentDataType.STRING, type.name());
        });
        dropped.addPassenger(display);
    }

    /** Retire l'affichage géant lié à une potion ramassée ou disparue. */
    public void removeDisplays(Entity vehicle) {
        for (Entity passenger : List.copyOf(vehicle.getPassengers())) {
            if (passenger.getPersistentDataContainer().has(displayKey, PersistentDataType.STRING)) {
                passenger.remove();
            }
        }
    }

    private void cleanupDisplays() {
        for (World w : plugin.getServer().getWorlds()) {
            for (ItemDisplay d : w.getEntitiesByClass(ItemDisplay.class)) {
                if (!d.getPersistentDataContainer().has(displayKey, PersistentDataType.STRING)) continue;
                Entity v = d.getVehicle();
                if (v == null || !v.isValid()) {
                    d.remove();
                }
            }
        }
    }
}
