package com.power;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class CombatListener implements Listener {

    private final PowerPlugin plugin;
    private final PowerManager manager;

    /** Dégâts récents infligés à des joueurs par un porteur de Health : {timestamp, dégâts}. */
    private final Map<UUID, Deque<double[]>> recentDamage = new HashMap<>();

    public CombatListener(PowerPlugin plugin, PowerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private static Player resolvePlayer(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    // --- Strength : smash de mace sur l'épée ---------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSmash(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (manager.itemPower(hand) != PowerType.STRENGTH) return;
        if (manager.getPower(p) != PowerType.STRENGTH) return;

        double fall = p.getFallDistance();
        double minFall = plugin.getConfig().getDouble("strength.smash-min-fall", 1.5);
        if (fall <= minFall) return;
        if (p.getAttackCooldown() < 0.9f) return;

        double bonus = Math.min(fall, 3.0) * 4.0
                + Math.min(Math.max(fall - 3.0, 0.0), 5.0) * 2.0
                + Math.max(fall - 8.0, 0.0);

        Enchantment density = manager.ench("density");
        if (density != null) {
            bonus += fall * 0.5 * hand.getEnchantmentLevel(density);
        }

        e.setDamage(e.getDamage() + bonus);
        p.setFallDistance(0f);

        // Wind Burst : le porteur est propulsé vers le haut
        double up = plugin.getConfig().getDouble("strength.windburst-velocity", 1.1);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && !p.isDead()) {
                p.setVelocity(new Vector(p.getVelocity().getX(), up, p.getVelocity().getZ()));
            }
        });
    }

    // --- Health et Void : effets sur coups ------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        double finalDamage = e.getFinalDamage();
        if (finalDamage <= 0) return;

        Player attacker = resolvePlayer(e.getDamager());

        // Health : 20 % de chance de récupérer les dégâts infligés ces 2 dernières secondes
        if (attacker != null && e.getEntity() instanceof Player && e.getEntity() != attacker
                && manager.getPower(attacker) == PowerType.HEALTH) {
            handleHealthHit(attacker, finalDamage);
        }

        // Void : 10 % de chance, par coup reçu, d'infliger Weakness + Blindness à l'attaquant
        if (e.getEntity() instanceof Player victim && attacker != null && attacker != victim
                && manager.getPower(victim) == PowerType.VOID) {
            double chance = plugin.getConfig().getDouble("void.curse-chance", 0.10);
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                int ticks = plugin.getConfig().getInt("void.curse-duration-seconds", 30) * 20;
                attacker.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ticks, 0));
                attacker.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks, 0));
            }
        }
    }

    private void handleHealthHit(Player attacker, double damage) {
        long now = System.currentTimeMillis();
        long window = plugin.getConfig().getLong("health.heal-window-seconds", 2) * 1000L;

        Deque<double[]> log = recentDamage.computeIfAbsent(attacker.getUniqueId(), k -> new ArrayDeque<>());
        log.addLast(new double[] {now, damage});
        while (!log.isEmpty() && now - (long) log.peekFirst()[0] > window) {
            log.removeFirst();
        }

        double chance = plugin.getConfig().getDouble("health.heal-chance", 0.20);
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;

        double heal = 0;
        for (double[] entry : log) {
            heal += entry[1];
        }

        AttributeInstance maxAttr = attacker.getAttribute(Attribute.MAX_HEALTH);
        double max = maxAttr != null ? maxAttr.getValue() : 20.0;
        attacker.setHealth(Math.min(max, attacker.getHealth() + heal));
        log.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        recentDamage.remove(e.getPlayer().getUniqueId());
    }
}
