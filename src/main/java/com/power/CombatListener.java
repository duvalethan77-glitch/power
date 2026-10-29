package com.power;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.ThreadLocalRandom;

public final class CombatListener implements Listener {

    private final PowerPlugin plugin;
    private final PowerManager manager;
    private final Abilities abilities;

    public CombatListener(PowerPlugin plugin, PowerManager manager, Abilities abilities) {
        this.plugin = plugin;
        this.manager = manager;
        this.abilities = abilities;
    }

    private boolean isMelee(EntityDamageByEntityEvent e) {
        return e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || e.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK;
    }

    // --- Dégâts reçus : Earth (réduction) et Phoenix (immunité au feu) -------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        PowerType type = manager.getPower(victim);
        if (type == null) return;

        if (type == PowerType.EARTH) {
            double reduction = Math.max(0.0, Math.min(0.9, plugin.getConfig().getDouble("earth.damage-reduction", 0.15)));
            e.setDamage(e.getDamage() * (1.0 - reduction));
        } else if (type == PowerType.WIND) {
            // Wind : aucun dégât de chute
            if (e.getCause() == EntityDamageEvent.DamageCause.FALL) {
                e.setCancelled(true);
            }
        } else if (type == PowerType.PHOENIX) {
            switch (e.getCause()) {
                case FIRE, FIRE_TICK, LAVA, HOT_FLOOR -> {
                    e.setCancelled(true);
                    victim.setFireTicks(0);
                }
                default -> { }
            }
        }
    }

    // --- Poison : immunisé au poison (donc à son propre nuage) ---------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        PotionEffect incoming = e.getNewEffect();
        if (incoming == null || incoming.getType() != PotionEffectType.POISON) return;
        if (manager.getPower(p) == PowerType.POISON) {
            e.setCancelled(true);
        }
    }

    // --- Avant les dégâts : boule de feu, Blood, Shadow -----------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        // Ta propre boule de feu ne te blesse pas
        if (e.getDamager() instanceof LargeFireball fireball
                && fireball.getPersistentDataContainer().has(manager.fireballKey(), PersistentDataType.BYTE)
                && fireball.getShooter() instanceof Entity shooter
                && shooter.equals(e.getEntity())) {
            e.setCancelled(true);
            return;
        }

        if (manager.isInAbility() || !(e.getDamager() instanceof Player attacker)) return;
        if (!isMelee(e)) return;

        // Blood : en mode berserk, plus ta vie est basse, plus tu frappes fort
        if (manager.isBerserk(attacker)) {
            AttributeInstance maxAttr = attacker.getAttribute(Attribute.MAX_HEALTH);
            double max = maxAttr != null ? maxAttr.getValue() : 20.0;
            double missing = Math.max(0.0, Math.min(1.0, 1.0 - attacker.getHealth() / max));
            double bonus = plugin.getConfig().getDouble("blood.berserk-max-bonus", 1.0);
            e.setDamage(e.getDamage() * (1.0 + missing * bonus));
        }

        // Shadow : le premier coup après la téléportation est renforcé
        if (manager.consumeBackstab(attacker)) {
            double bonus = plugin.getConfig().getDouble("shadow.backstab-bonus", 0.5);
            e.setDamage(e.getDamage() * (1.0 + bonus));
        }
    }

    // --- Après les dégâts : effets passifs ------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (manager.isInAbility()) return;
        if (!(e.getDamager() instanceof Player attacker)) return;
        if (!(e.getEntity() instanceof LivingEntity victim) || victim == attacker) return;
        if (!isMelee(e)) return;

        double damage = e.getFinalDamage();
        if (damage <= 0) return;

        PowerType type = manager.getPower(attacker);
        if (type == null) return;

        Effects.hit(victim, type);

        switch (type) {
            case LIGHTNING -> {
                double chance = plugin.getConfig().getDouble("lightning.passive-chance", 0.10);
                if (ThreadLocalRandom.current().nextDouble() < chance) {
                    abilities.passiveLightning(attacker, victim);
                }
            }
            case ICE -> {
                int ticks = plugin.getConfig().getInt("ice.passive-slow-seconds", 2) * 20;
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 0));
            }
            case BLOOD -> {
                double lifesteal = plugin.getConfig().getDouble("blood.lifesteal", 0.15);
                AttributeInstance maxAttr = attacker.getAttribute(Attribute.MAX_HEALTH);
                double max = maxAttr != null ? maxAttr.getValue() : 20.0;
                attacker.setHealth(Math.min(max, attacker.getHealth() + damage * lifesteal));
            }
            case FIRE -> {
                int ticks = plugin.getConfig().getInt("fire.passive-burn-seconds", 3) * 20;
                victim.setFireTicks(Math.max(victim.getFireTicks(), ticks));
            }
            case POISON -> {
                int ticks = plugin.getConfig().getInt("poison.passive-seconds", 3) * 20;
                victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, ticks, 0));
            }
            default -> { }
        }
    }

    // --- Boule de feu : ne détruit pas les blocs (réglable) ---------------------

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (!e.getEntity().getPersistentDataContainer().has(manager.fireballKey(), PersistentDataType.BYTE)) return;
        if (!plugin.getConfig().getBoolean("fire.break-blocks", false)) {
            e.blockList().clear();
        }
    }
}
