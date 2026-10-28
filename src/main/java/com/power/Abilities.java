package com.power;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** Les capacités actives (clic droit accroupi) de chaque pouvoir. */
public final class Abilities {

    private final PowerPlugin plugin;
    private final PowerManager manager;

    public Abilities(PowerPlugin plugin, PowerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    /** Lance la capacité. Retourne false si elle n'a pas pu partir (pas de cooldown dans ce cas). */
    public boolean cast(Player p, PowerType type) {
        return switch (type) {
            case LIGHTNING -> lightning(p);
            case ICE -> ice(p);
            case BLOOD -> blood(p);
            case FIRE -> fire(p);
        };
    }

    // ------------------------------------------------------------------
    // Visée
    // ------------------------------------------------------------------

    private LivingEntity target(Player p, double range) {
        Location eye = p.getEyeLocation();
        RayTraceResult hit = p.getWorld().rayTraceEntities(eye, eye.getDirection(), range, 0.6,
                e -> e instanceof LivingEntity
                        && e != p
                        && !(e instanceof ArmorStand)
                        && !e.isDead()
                        && !(e instanceof Player other && other.getGameMode() == GameMode.SPECTATOR));
        if (hit == null || !(hit.getHitEntity() instanceof LivingEntity target)) return null;
        if (!p.hasLineOfSight(target)) return null;
        return target;
    }

    private void noTarget(Player p) {
        p.sendActionBar(Component.text(Lang.tr(p, "no_target"), NamedTextColor.RED));
    }

    // ------------------------------------------------------------------
    // Lightning : plusieurs éclairs sur la cible visée
    // ------------------------------------------------------------------

    private boolean lightning(Player p) {
        LivingEntity target = target(p, cfg().getDouble("lightning.range", 30));
        if (target == null) {
            noTarget(p);
            return false;
        }

        int strikes = Math.max(1, cfg().getInt("lightning.strikes", 3));
        long interval = Math.max(1L, Math.round(cfg().getDouble("lightning.interval-seconds", 1.0) * 20));
        double damage = cfg().getDouble("lightning.damage", 6.0);

        for (int i = 0; i < strikes; i++) {
            long delay = i * interval;
            Runnable strike = () -> strike(p, target, damage);
            if (delay == 0L) {
                strike.run();
            } else {
                plugin.getServer().getScheduler().runTaskLater(plugin, strike, delay);
            }
        }
        return true;
    }

    private void strike(Player caster, LivingEntity target, double damage) {
        if (!target.isValid() || target.isDead()) return;
        Location loc = target.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        world.strikeLightningEffect(loc);
        target.setNoDamageTicks(0);
        manager.runAbility(() -> target.damage(damage, caster));
    }

    /** Passif : un éclair supplémentaire tombe sur la cible touchée. */
    public void passiveLightning(Player attacker, LivingEntity victim) {
        double damage = cfg().getDouble("lightning.passive-damage", 4.0);
        plugin.getServer().getScheduler().runTask(plugin, () -> strike(attacker, victim, damage));
    }

    // ------------------------------------------------------------------
    // Ice : gèle la cible visée
    // ------------------------------------------------------------------

    private boolean ice(Player p) {
        LivingEntity target = target(p, cfg().getDouble("ice.range", 25));
        if (target == null) {
            noTarget(p);
            return false;
        }

        int ticks = Math.max(1, cfg().getInt("ice.freeze-seconds", 3)) * 20;
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 6, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, ticks, 128, false, false, false));
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 0.8f);

        new BukkitRunnable() {
            int left = ticks;

            @Override
            public void run() {
                if (left <= 0 || !target.isValid() || target.isDead()) {
                    cancel();
                    return;
                }
                target.setFreezeTicks(target.getMaxFreezeTicks());
                target.getWorld().spawnParticle(Particle.SNOWFLAKE,
                        target.getLocation().add(0, 1, 0), 12, 0.4, 0.6, 0.4, 0.02);
                left -= 4;
            }
        }.runTaskTimer(plugin, 0L, 4L);
        return true;
    }

    // ------------------------------------------------------------------
    // Blood : mode berserk
    // ------------------------------------------------------------------

    private boolean blood(Player p) {
        int seconds = Math.max(1, cfg().getInt("blood.berserk-seconds", 8));
        manager.startBerserk(p, seconds);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.4f);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || !manager.isBerserk(p)) {
                    cancel();
                    return;
                }
                p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0),
                        8, 0.4, 0.7, 0.4, 0, new Particle.DustOptions(Color.fromRGB(0x9B0000), 1.4f));
            }
        }.runTaskTimer(plugin, 0L, 4L);
        return true;
    }

    // ------------------------------------------------------------------
    // Fire : boule de feu
    // ------------------------------------------------------------------

    private boolean fire(Player p) {
        Vector direction = p.getEyeLocation().getDirection();
        LargeFireball fireball = p.launchProjectile(LargeFireball.class, direction.clone().multiply(1.2));
        fireball.setDirection(direction);
        fireball.setYield((float) cfg().getDouble("fire.fireball-yield", 1.5));
        fireball.setIsIncendiary(cfg().getBoolean("fire.incendiary", false));
        fireball.getPersistentDataContainer().set(manager.fireballKey(), PersistentDataType.BYTE, (byte) 1);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.9f);
        return true;
    }
}
