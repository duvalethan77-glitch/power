package com.power;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PowerManager {

    private final PowerPlugin plugin;

    private final NamespacedKey powerKey;    // pouvoir stocké sur le joueur
    private final NamespacedKey itemKey;     // objet d'équipement lié à un pouvoir
    private final NamespacedKey potionKey;   // potion de pouvoir
    private final NamespacedKey displayKey;  // affichage géant d'une potion au sol
    private final NamespacedKey healthKey;   // modificateur de vie max
    private final NamespacedKey absorbKey;   // modificateur d'absorption max

    private final Map<UUID, Double> absorbOurs = new HashMap<>();
    private final Map<UUID, Long> absorbNext = new HashMap<>();
    private final Set<String> warned = new HashSet<>();

    public PowerManager(PowerPlugin plugin) {
        this.plugin = plugin;
        this.powerKey = new NamespacedKey(plugin, "power");
        this.itemKey = new NamespacedKey(plugin, "power_item");
        this.potionKey = new NamespacedKey(plugin, "power_potion");
        this.displayKey = new NamespacedKey(plugin, "power_display");
        this.healthKey = new NamespacedKey(plugin, "power_health");
        this.absorbKey = new NamespacedKey(plugin, "power_absorption");
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
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
            strip(p, old);
            giveBack(p, createPotion(old));
        }
        store(p, type);
        apply(p, type);
    }

    /** Retire le pouvoir sans rendre la potion. */
    public void clearPower(Player p) {
        PowerType old = getPower(p);
        if (old != null) {
            strip(p, old);
        }
        store(p, null);
    }

    /** Réapplique l'état du joueur (connexion, rechargement). */
    public void refresh(Player p) {
        PowerType type = getPower(p);
        cleanupForeignItems(p, type);
        if (type != null) {
            ensureEffects(p, type);
            ensureModifiers(p, type);
        }
    }

    private void apply(Player p, PowerType type) {
        ensureEffects(p, type);
        ensureModifiers(p, type);
        switch (type) {
            case STRENGTH, SPEED -> equipWeapon(p, buildSword(type));
            case VOID -> equipArmor(p);
            case SATURATION -> {
                absorbOurs.put(p.getUniqueId(), 0.0);
                long interval = cfg().getLong("saturation.absorption-interval-seconds", 30) * 1000L;
                absorbNext.put(p.getUniqueId(), System.currentTimeMillis() + interval);
            }
            default -> { }
        }
        sendHud(p, type);
    }

    private void strip(Player p, PowerType type) {
        removeEffects(p, type);
        removeModifier(p, Attribute.MAX_HEALTH, healthKey);
        removeModifier(p, Attribute.MAX_ABSORPTION, absorbKey);
        removePowerItems(p);
        absorbOurs.remove(p.getUniqueId());
        absorbNext.remove(p.getUniqueId());
        p.sendActionBar(Component.empty());
    }

    public void forget(Player p) {
        absorbOurs.remove(p.getUniqueId());
        absorbNext.remove(p.getUniqueId());
    }

    // ------------------------------------------------------------------
    // Effets et attributs
    // ------------------------------------------------------------------

    private void infinite(Player p, PotionEffectType effect, int amplifier) {
        PotionEffect cur = p.getPotionEffect(effect);
        if (cur != null && cur.getAmplifier() > amplifier) return;
        if (cur != null && cur.getAmplifier() == amplifier && cur.isInfinite()) return;
        p.addPotionEffect(new PotionEffect(effect, PotionEffect.INFINITE_DURATION, amplifier, true, false, true));
    }

    private void removeInfinite(Player p, PotionEffectType effect) {
        PotionEffect cur = p.getPotionEffect(effect);
        if (cur != null && cur.isInfinite()) {
            p.removePotionEffect(effect);
        }
    }

    private void ensureEffects(Player p, PowerType type) {
        switch (type) {
            case STRENGTH -> infinite(p, PotionEffectType.STRENGTH, cfg().getInt("strength.effect-amplifier", 0));
            case SPEED -> infinite(p, PotionEffectType.SPEED, cfg().getInt("speed.effect-amplifier", 1));
            case SATURATION -> infinite(p, PotionEffectType.SATURATION, 0);
            default -> { }
        }
    }

    private void removeEffects(Player p, PowerType type) {
        switch (type) {
            case STRENGTH -> removeInfinite(p, PotionEffectType.STRENGTH);
            case SPEED -> removeInfinite(p, PotionEffectType.SPEED);
            case SATURATION -> removeInfinite(p, PotionEffectType.SATURATION);
            default -> { }
        }
    }

    private void ensureModifiers(Player p, PowerType type) {
        if (type == PowerType.HEALTH) {
            double hp = cfg().getDouble("health.bonus-hearts", 2) * 2.0;
            setModifier(p, Attribute.MAX_HEALTH, healthKey, hp);
        } else if (type == PowerType.SATURATION) {
            double cap = cfg().getDouble("saturation.absorption-max-hearts", 4) * 2.0;
            setModifier(p, Attribute.MAX_ABSORPTION, absorbKey, cap);
        }
    }

    private void setModifier(Player p, Attribute attribute, NamespacedKey key, double amount) {
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst == null) return;
        AttributeModifier existing = null;
        for (AttributeModifier m : inst.getModifiers()) {
            if (key.equals(m.getKey())) {
                existing = m;
                break;
            }
        }
        if (existing != null && existing.getAmount() == amount) return;
        if (existing != null) inst.removeModifier(existing);
        inst.addModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER));
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

    // ------------------------------------------------------------------
    // Tick (toutes les secondes)
    // ------------------------------------------------------------------

    public void tick() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            PowerType type = getPower(p);
            cleanupForeignItems(p, type);
            if (type == null) continue;
            ensureEffects(p, type);
            ensureModifiers(p, type);
            if (type == PowerType.SATURATION) {
                absorptionPulse(p);
            }
            sendHud(p, type);
        }
        cleanupDisplays();
    }

    private void absorptionPulse(Player p) {
        UUID id = p.getUniqueId();
        double current = p.getAbsorptionAmount();
        double ours = Math.min(absorbOurs.getOrDefault(id, 0.0), current);

        long now = System.currentTimeMillis();
        if (now >= absorbNext.getOrDefault(id, 0L)) {
            double pulse = cfg().getDouble("saturation.absorption-pulse-hearts", 2) * 2.0;
            double max = cfg().getDouble("saturation.absorption-max-hearts", 4) * 2.0;
            double add = Math.min(pulse, max - ours);
            if (add > 0) {
                p.setAbsorptionAmount(current + add);
                ours += add;
            }
            long interval = cfg().getLong("saturation.absorption-interval-seconds", 30) * 1000L;
            absorbNext.put(id, now + interval);
        }
        absorbOurs.put(id, ours);
    }

    public void sendHud(Player p, PowerType type) {
        p.sendActionBar(Component.text("■ ", type.textColor)
                .append(Component.text(type.display, NamedTextColor.WHITE)));
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
        return PowerType.parse(item.getItemMeta().getPersistentDataContainer()
                .get(potionKey, PersistentDataType.STRING));
    }

    // ------------------------------------------------------------------
    // Objets : équipement de pouvoir
    // ------------------------------------------------------------------

    public PowerType itemPower(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return PowerType.parse(item.getItemMeta().getPersistentDataContainer()
                .get(itemKey, PersistentDataType.STRING));
    }

    public boolean isPowerItem(ItemStack item) {
        return itemPower(item) != null;
    }

    public Enchantment ench(String key) {
        Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
        if (e == null && warned.add(key)) {
            plugin.getLogger().warning("Enchantement introuvable sur cette version : " + key);
        }
        return e;
    }

    private void addEnchant(ItemMeta meta, String key, int level) {
        Enchantment e = ench(key);
        if (e != null) {
            meta.addEnchant(e, level, true);
        }
    }

    private ItemStack buildSword(PowerType type) {
        Material mat = type == PowerType.STRENGTH ? Material.NETHERITE_SWORD : Material.DIAMOND_SWORD;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(type.display + " Blade", type.textColor)
                .decoration(TextDecoration.ITALIC, false));

        addEnchant(meta, "sharpness", 5);
        addEnchant(meta, "fire_aspect", 2);
        addEnchant(meta, "sweeping_edge", 3);
        addEnchant(meta, "vanishing_curse", 1);
        if (type == PowerType.STRENGTH) {
            addEnchant(meta, "wind_burst", 1);
            addEnchant(meta, "density", 5);
        } else {
            addEnchant(meta, "lunge", 1);
        }
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildArmorPiece(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Void " + niceName(mat), PowerType.VOID.textColor)
                .decoration(TextDecoration.ITALIC, false));
        addEnchant(meta, "binding_curse", 1);
        addEnchant(meta, "protection", 4);
        addEnchant(meta, "unbreaking", 3);
        addEnchant(meta, "vanishing_curse", 1);
        if (meta instanceof Damageable dmg) {
            int base = mat.getMaxDurability();
            int mult = Math.max(1, cfg().getInt("void.armor-durability-multiplier", 2));
            dmg.setMaxDamage(base * mult);
            dmg.setDamage(0);
        }
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, PowerType.VOID.name());
        item.setItemMeta(meta);
        return item;
    }

    private String niceName(Material mat) {
        String n = mat.name().replace("NETHERITE_", "");
        return n.charAt(0) + n.substring(1).toLowerCase();
    }

    private boolean isPlainSword(ItemStack item) {
        return item != null && item.getType().name().endsWith("_SWORD") && !isPowerItem(item);
    }

    private void equipWeapon(Player p, ItemStack weapon) {
        PlayerInventory inv = p.getInventory();
        int slot = -1;
        int held = inv.getHeldItemSlot();
        if (isPlainSword(inv.getItem(held))) {
            slot = held;
        } else {
            for (int i = 0; i < 36; i++) {
                if (isPlainSword(inv.getItem(i))) {
                    slot = i;
                    break;
                }
            }
        }
        if (slot >= 0) {
            ItemStack old = inv.getItem(slot);
            inv.setItem(slot, weapon);
            if (cfg().getBoolean("return-replaced-items", true)) {
                giveBack(p, old);
            }
        } else {
            giveBack(p, weapon);
        }
    }

    private void equipArmor(Player p) {
        PlayerInventory inv = p.getInventory();
        Map<EquipmentSlot, Material> pieces = new java.util.LinkedHashMap<>();
        pieces.put(EquipmentSlot.HEAD, Material.NETHERITE_HELMET);
        pieces.put(EquipmentSlot.CHEST, Material.NETHERITE_CHESTPLATE);
        pieces.put(EquipmentSlot.LEGS, Material.NETHERITE_LEGGINGS);
        pieces.put(EquipmentSlot.FEET, Material.NETHERITE_BOOTS);

        boolean giveOld = cfg().getBoolean("return-replaced-items", true);
        for (Map.Entry<EquipmentSlot, Material> entry : pieces.entrySet()) {
            ItemStack old = inv.getItem(entry.getKey());
            inv.setItem(entry.getKey(), buildArmorPiece(entry.getValue()));
            if (giveOld && old != null && !old.getType().isAir() && !isPowerItem(old)) {
                giveBack(p, old);
            }
        }
    }

    public void giveBack(Player p, ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        Map<Integer, ItemStack> left = p.getInventory().addItem(item);
        for (ItemStack rest : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }

    private void removePowerItems(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isPowerItem(contents[i])) {
                inv.setItem(i, null);
            }
        }
        if (isPowerItem(p.getItemOnCursor())) {
            p.setItemOnCursor(null);
        }
    }

    /** Supprime les objets de pouvoir qui ne correspondent pas au pouvoir actuel du joueur. */
    private void cleanupForeignItems(Player p, PowerType current) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            PowerType ip = itemPower(contents[i]);
            if (ip != null && ip != current) {
                inv.setItem(i, null);
            }
        }
        PowerType cursor = itemPower(p.getItemOnCursor());
        if (cursor != null && cursor != current) {
            p.setItemOnCursor(null);
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
