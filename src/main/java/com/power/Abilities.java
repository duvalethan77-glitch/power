package com.power;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

/** Les capacités actives (clic droit) de chaque pouvoir. */
public final class Abilities {

    private static final Particle.DustOptions BLOOD_DUST = new Particle.DustOptions(Color.fromRGB(0x9B0000), 1.6f);
    private static final Particle.DustOptions POISON_DUST = new Particle.DustOptions(Color.fromRGB(0x4E9331), 1.5f);

    private static final Particle.DustOptions ICE_DUST = new Particle.DustOptions(Color.fromRGB(0xBFF3FF), 1.4f);
    private static final Particle.DustTransition FIRE_T =
            new Particle.DustTransition(Color.fromRGB(0xFFB000), Color.fromRGB(0xC02000), 1.6f);
    private static final Particle.DustTransition VOID_T =
            new Particle.DustTransition(Color.fromRGB(0x7A5FB8), Color.fromRGB(0x0A0A12), 1.5f);

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
            case WIND -> wind(p);
            case SHADOW -> shadow(p);
            case EARTH -> earth(p);
            case POISON -> poison(p);
            case LIGHT -> light(p);
            case PHOENIX -> phoenix(p);
        };
    }

    // ------------------------------------------------------------------
    // Outils
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
        manager.notice(p, Component.text(Lang.tr(p, "no_target"), NamedTextColor.RED), 1500);
    }

    /** Vecteur horizontal normalisé, ou vecteur nul si le regard est vertical. */
    private Vector horizontal(Vector v) {
        Vector h = v.clone().setY(0);
        if (h.lengthSquared() < 1.0E-4) return new Vector(0, 0, 0);
        return h.normalize();
    }

    private double maxHealth(LivingEntity e) {
        AttributeInstance attr = e.getAttribute(Attribute.MAX_HEALTH);
        return attr != null ? attr.getValue() : 20.0;
    }

    private void hurt(Player caster, LivingEntity target, double damage) {
        target.setNoDamageTicks(0);
        manager.runAbility(() -> target.damage(damage, caster));
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

        // Charge électrique : un arc part de toi vers la cible
        World world = p.getWorld();
        Location hand = p.getEyeLocation().subtract(0, 0.3, 0);
        for (int i = 0; i < 3; i++) {
            Fx.bolt(world, hand, target.getEyeLocation());
        }
        Fx.wave(plugin, world, p.getLocation().add(0, 0.1, 0), 3.0, 0.75,
                (w2, c2, rad) -> Effects.ring(w2, c2, rad, (int) (rad * 9), Particle.ELECTRIC_SPARK, null));
        Fx.burst(world, p.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 60, 0.4, null);
        Fx.helix(world, p.getLocation(), 0.9, 2.2, 2, 24, 0, Particle.ELECTRIC_SPARK, null);
        world.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.8f);

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
        // Éclair visible depuis le ciel, avec ses ramifications
        Fx.bolt(world, loc.clone().add(0, 16, 0), loc);
        Fx.bolt(world, loc.clone().add(0, 16, 0), loc.clone().add(0.4, 0, 0.4));
        Fx.bolt(world, loc.clone().add(0, 7, 0), loc.clone().add(2.0, 0, -1.5));
        Fx.sphere(world, loc.clone().add(0, 1, 0), 1.3, 40, Particle.ELECTRIC_SPARK, null);
        Fx.burst(world, loc.clone().add(0, 1, 0), Particle.ELECTRIC_SPARK, 70, 0.5, null);
        Fx.burst(world, loc.clone().add(0, 0.3, 0), Particle.END_ROD, 20, 0.2, null);
        Fx.wave(plugin, world, loc.clone().add(0, 0.1, 0), 4.0, 0.8,
                (w2, c2, rad) -> Effects.ring(w2, c2, rad, (int) (rad * 8), Particle.ELECTRIC_SPARK, null));
        world.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.0f, 1.2f);
        hurt(caster, target, damage);
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

        World world = target.getWorld();

        // Rayon de givre épais de toi vers la cible
        Location from = p.getEyeLocation().subtract(0, 0.3, 0);
        Location to = target.getEyeLocation();
        Vector along = to.toVector().subtract(from.toVector());
        double length = along.length();
        if (length > 0.1) {
            Vector step = along.clone().normalize().multiply(0.35);
            Location cur = from.clone();
            for (double d = 0; d < length; d += 0.35) {
                world.spawnParticle(Particle.SNOWFLAKE, cur, 3, 0.12, 0.12, 0.12, 0.01);
                world.spawnParticle(Particle.DUST, cur, 1, 0.05, 0.05, 0.05, 0, ICE_DUST);
                cur.add(step);
            }
        }

        // Explosion de glace, coque de cristal et pointes qui sortent du sol
        Location base = target.getLocation();
        Location center = base.clone().add(0, 1, 0);
        Fx.burst(world, center, Particle.SNOWFLAKE, 90, 0.3, null);
        Fx.burst(world, center, Particle.BLOCK, 50, 0.35, Material.PACKED_ICE.createBlockData());
        Fx.sphere(world, center, 1.4, 70, Particle.DUST, ICE_DUST);
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            Location spike = base.clone().add(Math.cos(a) * 1.1, 0, Math.sin(a) * 1.1);
            double h = 1.6 + (k % 2) * 0.7;
            Fx.column(world, spike, h, 0.25, 0.06, Particle.BLOCK, 2, Material.BLUE_ICE.createBlockData());
            world.spawnParticle(Particle.SNOWFLAKE, spike.clone().add(0, h, 0), 4, 0.05, 0.1, 0.05, 0.01);
        }
        Fx.wave(plugin, world, base.clone().add(0, 0.1, 0), 5.0, 0.8, (w2, c2, rad) -> {
            Effects.ring(w2, c2, rad, (int) (rad * 8), Particle.SNOWFLAKE, null);
            Effects.ring(w2, c2, rad, (int) (rad * 5), Particle.BLOCK, Material.BLUE_ICE.createBlockData());
        });
        world.playSound(center, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.8f);
        world.playSound(center, Sound.BLOCK_POWDER_SNOW_PLACE, 1.0f, 0.6f);
        world.playSound(center, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 0.8f);

        new BukkitRunnable() {
            int left = ticks;
            double spin = 0;

            @Override
            public void run() {
                if (!target.isValid() || target.isDead()) {
                    cancel();
                    return;
                }
                Location foot = target.getLocation();
                Location c = foot.clone().add(0, 1, 0);
                if (left <= 0) {
                    // La glace se brise
                    Fx.burst(target.getWorld(), c, Particle.BLOCK, 120, 0.45, Material.ICE.createBlockData());
                    Fx.burst(target.getWorld(), c, Particle.SNOWFLAKE, 60, 0.3, null);
                    Fx.sphere(target.getWorld(), c, 1.5, 50, Particle.DUST, ICE_DUST);
                    target.getWorld().playSound(c, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.4f);
                    target.getWorld().playSound(c, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 0.8f);
                    cancel();
                    return;
                }
                // Cage de glace qui tourne autour de la cible
                target.setFreezeTicks(target.getMaxFreezeTicks());
                spin += 0.5;
                Fx.shell(target.getWorld(), foot, 0.85, 2.0, 22, Particle.BLOCK, Material.PACKED_ICE.createBlockData());
                Fx.helix(target.getWorld(), foot, 0.9, 2.0, 2, 20, spin, Particle.DUST, ICE_DUST);
                Fx.helix(target.getWorld(), foot, 0.9, 2.0, 2, 20, spin + Math.PI, Particle.SNOWFLAKE, null);
                target.getWorld().spawnParticle(Particle.SNOWFLAKE, c, 10, 0.5, 0.7, 0.5, 0.02);
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

        World world = p.getWorld();
        world.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 0.8f);
        world.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.4f);
        world.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.4f, 1.6f);

        // Explosion de sang : éclats, sphère rouge et onde de choc qui goutte
        Location body0 = p.getLocation().add(0, 1, 0);
        Fx.burst(world, body0, Particle.BLOCK, 80, 0.4, Material.REDSTONE_BLOCK.createBlockData());
        Fx.burst(world, body0, Particle.CRIMSON_SPORE, 40, 0.3, null);
        Fx.burst(world, body0, Particle.DAMAGE_INDICATOR, 15, 0.3, null);
        Fx.sphere(world, body0, 2.0, 90, Particle.DUST, BLOOD_DUST);
        Fx.wave(plugin, world, p.getLocation().add(0, 0.2, 0), 7.0, 0.9, (w2, c2, rad) -> {
            Effects.ring(w2, c2, rad, (int) (rad * 10), Particle.DUST, BLOOD_DUST);
            Effects.ring(w2, c2.clone().add(0, 0.8, 0), rad, (int) (rad * 3), Particle.FALLING_DUST,
                    Material.REDSTONE_BLOCK.createBlockData());
        });

        // Colonne de sang qui monte en spirale au début
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ > 14 || !p.isOnline()) {
                    cancel();
                    return;
                }
                Location l = p.getLocation();
                Fx.helix(world, l, 1.0, 3.0, 2.5, 26, t * 0.8, Particle.DUST, BLOOD_DUST);
                Fx.helix(world, l, 1.0, 3.0, 2.5, 26, t * 0.8 + Math.PI, Particle.DUST, BLOOD_DUST);
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Pendant le berserk : tourbillon de sang, gouttes, battements de coeur
        new BukkitRunnable() {
            int beat = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || !manager.isBerserk(p)) {
                    cancel();
                    return;
                }
                Location foot = p.getLocation();
                Location body = foot.clone().add(0, 1, 0);
                double phase = beat * 0.7;
                Fx.helix(world, foot, 0.85, 2.0, 2, 18, phase, Particle.DUST, BLOOD_DUST);
                Fx.helix(world, foot, 0.85, 2.0, 2, 18, phase + Math.PI, Particle.DUST, BLOOD_DUST);
                world.spawnParticle(Particle.DUST, body, 10, 0.45, 0.8, 0.45, 0, BLOOD_DUST);
                world.spawnParticle(Particle.FALLING_DUST, body, 3, 0.4, 0.8, 0.4, 0,
                        Material.REDSTONE_BLOCK.createBlockData());
                world.spawnParticle(Particle.CRIMSON_SPORE, body, 4, 0.45, 0.8, 0.45, 0);
                if (beat % 3 == 0) {
                    world.spawnParticle(Particle.DAMAGE_INDICATOR, body, 2, 0.4, 0.6, 0.4, 0.1);
                }
                if (beat % 10 == 0) {
                    world.playSound(foot, Sound.ENTITY_WARDEN_HEARTBEAT, 0.7f, 1.0f);
                    Fx.wave(plugin, world, foot.clone().add(0, 0.2, 0), 3.0, 0.75,
                            (w2, c2, rad) -> Effects.ring(w2, c2, rad, (int) (rad * 8), Particle.DUST, BLOOD_DUST));
                }
                beat++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
        return true;
    }

    // ------------------------------------------------------------------
    // Fire : boule de feu avec traînée
    // ------------------------------------------------------------------

    private boolean fire(Player p) {
        Vector direction = p.getEyeLocation().getDirection();
        LargeFireball fireball = p.launchProjectile(LargeFireball.class, direction.clone().multiply(1.2));
        fireball.setDirection(direction);
        fireball.setYield((float) cfg().getDouble("fire.fireball-yield", 1.5));
        fireball.setIsIncendiary(cfg().getBoolean("fire.incendiary", false));
        fireball.getPersistentDataContainer().set(manager.fireballKey(), PersistentDataType.BYTE, (byte) 1);

        World world = p.getWorld();
        world.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.9f);
        Location muzzle = p.getEyeLocation().add(direction.clone().multiply(1.2));
        Fx.burst(world, muzzle, Particle.FLAME, 60, 0.25, null);
        Fx.burst(world, muzzle, Particle.LAVA, 6, 0.1, null);
        Fx.wave(plugin, world, p.getLocation().add(0, 0.1, 0), 3.0, 0.75,
                (w2, c2, rad) -> Effects.ring(w2, c2, rad, (int) (rad * 8), Particle.FLAME, null));

        // Traînée de flammes derrière la boule de feu
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!fireball.isValid() || fireball.isDead()) {
                    cancel();
                    return;
                }
                Location l = fireball.getLocation();
                world.spawnParticle(Particle.FLAME, l, 14, 0.3, 0.3, 0.3, 0.03);
                world.spawnParticle(Particle.DUST_COLOR_TRANSITION, l, 10, 0.4, 0.4, 0.4, 0, FIRE_T);
                world.spawnParticle(Particle.SMALL_FLAME, l, 6, 0.5, 0.5, 0.5, 0.01);
                world.spawnParticle(Particle.LAVA, l, 2, 0.2, 0.2, 0.2, 0);
                world.spawnParticle(Particle.LARGE_SMOKE, l, 2, 0.2, 0.2, 0.2, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    // ------------------------------------------------------------------
    // Wind : gros dash + poussée des ennemis proches
    // ------------------------------------------------------------------

    private boolean wind(Player p) {
        Vector dash = horizontal(p.getLocation().getDirection()).multiply(cfg().getDouble("wind.dash-speed", 2.0));
        dash.setY(0.25);
        p.setVelocity(dash);

        double radius = cfg().getDouble("wind.repel-radius", 4);
        double strength = cfg().getDouble("wind.repel-strength", 1.2);
        for (Entity e : p.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof LivingEntity living) || e instanceof ArmorStand) continue;
            Vector away = horizontal(living.getLocation().toVector().subtract(p.getLocation().toVector()));
            away.multiply(strength).setY(0.5);
            living.setVelocity(away);
        }

        World world = p.getWorld();
        Location start = p.getLocation();
        world.playSound(start, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.2f);
        world.playSound(start, Sound.ENTITY_PHANTOM_FLAP, 1.0f, 1.5f);
        world.playSound(start, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.8f, 1.4f);

        // Souffle : deux ondes de nuages qui s'étendent et une explosion d'air
        Fx.burst(world, start.clone().add(0, 1, 0), Particle.CLOUD, 70, 0.55, null);
        Fx.burst(world, start.clone().add(0, 0.5, 0), Particle.POOF, 25, 0.3, null);
        Fx.wave(plugin, world, start.clone().add(0, 0.3, 0), radius + 1.5, 0.7, (w2, c2, rad) -> {
            Effects.ring(w2, c2, rad, (int) (rad * 9), Particle.CLOUD, null);
            Effects.ring(w2, c2.clone().add(0, 0.7, 0), rad * 0.8, (int) (rad * 6), Particle.WHITE_ASH, null);
            Effects.ring(w2, c2.clone().add(0, 1.4, 0), rad * 0.6, (int) (rad * 4), Particle.CLOUD, null);
        });
        Fx.tornado(world, start, 3.0, 1.6, 0, Particle.CLOUD, null);

        Vector dir = horizontal(dash);
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());

        // Pendant le dash : tornade autour de toi, lignes de vitesse et coups de vent
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ >= 14 || !p.isOnline()) {
                    cancel();
                    return;
                }
                Location l = p.getLocation();
                Fx.tornado(world, l, 2.4, 1.0, ticks * 0.9, Particle.CLOUD, null);
                Fx.tornado(world, l, 2.4, 1.0, ticks * 0.9 + Math.PI, Particle.WHITE_ASH, null);
                world.spawnParticle(Particle.SWEEP_ATTACK, l.clone().add(0, 1, 0), 2, 0.4, 0.4, 0.4, 0);
                if (dir.lengthSquared() > 0) {
                    for (int k = -2; k <= 2; k++) {
                        Location a = l.clone().add(0, 1.0 + k * 0.3, 0).add(side.clone().multiply(k * 0.45));
                        Location b = a.clone().subtract(dir.clone().multiply(3.5));
                        Effects.line(world, a, b, 0.5, Particle.CLOUD, null);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    // ------------------------------------------------------------------
    // Shadow : téléportation dans le dos de la cible
    // ------------------------------------------------------------------

    private boolean shadow(Player p) {
        LivingEntity target = target(p, cfg().getDouble("shadow.range", 20));
        if (target == null) {
            noTarget(p);
            return false;
        }

        Location from = p.getLocation();
        Vector facing = horizontal(target.getLocation().getDirection());
        Location behind = target.getLocation().clone();
        if (facing.lengthSquared() > 0) {
            behind.subtract(facing.clone().multiply(1.5));
            behind.setDirection(facing);
        }
        boolean free = behind.getBlock().isPassable() && behind.clone().add(0, 1, 0).getBlock().isPassable();
        if (!free) {
            behind = target.getLocation().clone();
            behind.setDirection(target.getLocation().toVector().subtract(from.toVector()));
        }

        World world = from.getWorld();
        if (world != null) {
            Location chest = from.clone().add(0, 1, 0);
            Fx.burst(world, chest, Particle.SQUID_INK, 60, 0.2, null);
            Fx.burst(world, chest, Particle.LARGE_SMOKE, 25, 0.15, null);
            Fx.sphere(world, chest, 1.3, 60, Particle.DUST_COLOR_TRANSITION, VOID_T);
            Fx.column(world, from, 2.2, 0.3, 0.3, Particle.SQUID_INK, 2, null);
        }
        p.teleport(behind);
        World dest = p.getWorld();
        Location arrive = behind.clone().add(0, 1, 0);
        if (world != null && world.equals(dest)) {
            Effects.line(dest, from.clone().add(0, 1, 0), arrive, 0.3, Particle.SQUID_INK, null);
            Effects.line(dest, from.clone().add(0, 1, 0), arrive, 0.5, Particle.DUST_COLOR_TRANSITION, VOID_T);
        }
        dest.spawnParticle(Particle.PORTAL, arrive, 80, 0.4, 0.8, 0.4, 0.8);
        Fx.burst(dest, arrive, Particle.LARGE_SMOKE, 30, 0.2, null);
        Fx.sphere(dest, arrive, 1.3, 60, Particle.DUST_COLOR_TRANSITION, VOID_T);
        Fx.wave(plugin, dest, behind.clone().add(0, 0.1, 0), 3.5, 0.8,
                (w2, c2, rad) -> Effects.ring(w2, c2, rad, (int) (rad * 8), Particle.SQUID_INK, null));
        // Trois griffures sur la cible
        Location claw = target.getLocation().add(0, 1.2, 0);
        for (int i = 0; i < 3; i++) {
            dest.spawnParticle(Particle.SWEEP_ATTACK, claw.clone().add(0, i * 0.25 - 0.25, 0), 1, 0.2, 0.05, 0.2, 0);
        }
        dest.playSound(behind, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);

        manager.startBackstab(p, Math.max(1, cfg().getInt("shadow.backstab-seconds", 4)));

        // Tant que le coup renforcé est prêt, une ombre t'entoure
        new BukkitRunnable() {
            double spinShadow = 0;

            @Override
            public void run() {
                if (!p.isOnline() || !manager.hasBackstab(p)) {
                    cancel();
                    return;
                }
                Location body = p.getLocation().add(0, 1, 0);
                Location foot = p.getLocation();
                spinShadow += 0.6;
                Fx.helix(p.getWorld(), foot, 0.85, 2.0, 2, 18, spinShadow, Particle.DUST_COLOR_TRANSITION, VOID_T);
                Fx.helix(p.getWorld(), foot, 0.85, 2.0, 2, 18, spinShadow + Math.PI, Particle.SQUID_INK, null);
                p.getWorld().spawnParticle(Particle.SQUID_INK, body, 5, 0.4, 0.7, 0.4, 0.01);
                p.getWorld().spawnParticle(Particle.PORTAL, body, 6, 0.4, 0.7, 0.4, 0.3);
            }
        }.runTaskTimer(plugin, 0L, 3L);
        return true;
    }

    // ------------------------------------------------------------------
    // Earth : piliers de pierre devant toi
    // ------------------------------------------------------------------

    private boolean earth(Player p) {
        Vector dir = horizontal(p.getLocation().getDirection());
        if (dir.lengthSquared() == 0) {
            noTarget(p);
            return false;
        }

        int count = Math.max(1, cfg().getInt("earth.pillars", 5));
        double damage = cfg().getDouble("earth.pillar-damage", 4.0);
        Location base = p.getLocation();

        // Le sol tremble : explosion de terre, onde de choc et fissure jusqu'au dernier pilier
        World world = p.getWorld();
        Fx.burst(world, base.clone().add(0, 0.3, 0), Particle.BLOCK, 60, 0.35, Material.DIRT.createBlockData());
        Fx.wave(plugin, world, base.clone().add(0, 0.1, 0), 4.0, 0.7, (w2, c2, rad) -> {
            Effects.ring(w2, c2, rad, (int) (rad * 9), Particle.BLOCK, Material.COARSE_DIRT.createBlockData());
            Effects.ring(w2, c2.clone().add(0, 0.3, 0), rad, (int) (rad * 4), Particle.CLOUD, null);
        });
        Location end = base.clone().add(dir.clone().multiply(count * 1.5 + 1.0));
        Effects.line(world, base.clone().add(0, 0.1, 0), end.add(0, 0.1, 0), 0.25, Particle.BLOCK,
                Material.DEEPSLATE.createBlockData());
        world.playSound(base, Sound.ENTITY_IRON_GOLEM_DAMAGE, 1.0f, 0.5f);
        world.playSound(base, Sound.BLOCK_DEEPSLATE_BREAK, 1.0f, 0.5f);
        world.playSound(base, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.6f);

        for (int i = 1; i <= count; i++) {
            Location at = base.clone().add(dir.clone().multiply(i * 1.5));
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> pillar(p, at, damage), i * 2L);
        }
        return true;
    }

    /** Descend jusqu'au sol (4 blocs max) pour poser le pilier dessus. */
    private Location groundAt(Location at) {
        Location l = at.clone();
        for (int i = 0; i < 5; i++) {
            if (!l.clone().subtract(0, 1, 0).getBlock().isPassable()) return l;
            l.subtract(0, 1, 0);
        }
        return at.clone();
    }

    private void pillar(Player caster, Location at0, double damage) {
        World world = at0.getWorld();
        if (world == null) return;
        Location at = groundAt(at0);

        // Un vrai pilier de pierre qui sort du sol, puis qui éclate
        Material[] mats = {Material.STONE, Material.ANDESITE, Material.COBBLESTONE, Material.DEEPSLATE, Material.TUFF};
        Material mat = mats[ThreadLocalRandom.current().nextInt(mats.length)];
        float height = 3.2f;
        BlockDisplay display = world.spawn(at.clone().add(-0.5, -0.3, -0.5), BlockDisplay.class, d -> {
            d.setBlock(mat.createBlockData());
            d.setPersistent(false);
            d.setTransformation(new Transformation(new Vector3f(0f, 0f, 0f), new AxisAngle4f(),
                    new Vector3f(1f, 0.1f, 1f), new AxisAngle4f()));
        });
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!display.isValid()) return;
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(4);
            display.setTransformation(new Transformation(new Vector3f(0f, 0f, 0f), new AxisAngle4f(),
                    new Vector3f(1f, height, 1f), new AxisAngle4f()));
        }, 1L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (display.isValid()) {
                Location top = display.getLocation().add(0.5, 1.6, 0.5);
                Fx.burst(world, top, Particle.BLOCK, 50, 0.3, mat.createBlockData());
                world.playSound(top, Sound.BLOCK_STONE_BREAK, 1.0f, 0.6f);
                display.remove();
            }
        }, 30L);

        // Éclats, poussière et anneau au moment où il jaillit
        Fx.burst(world, at.clone().add(0, 0.4, 0), Particle.BLOCK, 45, 0.3, mat.createBlockData());
        Fx.burst(world, at.clone().add(0, 0.3, 0), Particle.CLOUD, 15, 0.15, null);
        Effects.ring(world, at.clone().add(0, 0.1, 0), 1.3, 20, Particle.BLOCK, Material.COARSE_DIRT.createBlockData());
        world.playSound(at, Sound.BLOCK_STONE_BREAK, 1.0f, 0.7f);
        world.playSound(at, Sound.BLOCK_GRAVEL_BREAK, 1.0f, 0.6f);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 0.8f);

        for (Entity e : world.getNearbyEntities(at, 1.3, 2.2, 1.3)) {
            if (!(e instanceof LivingEntity living) || e == caster || e instanceof ArmorStand) continue;
            hurt(caster, living, damage);
            Vector v = living.getVelocity();
            living.setVelocity(new Vector(v.getX(), 1.0, v.getZ()));
        }
    }

    // ------------------------------------------------------------------
    // Poison : nuage toxique
    // ------------------------------------------------------------------

    private boolean poison(Player p) {
        double range = cfg().getDouble("poison.range", 15);
        Location eye = p.getEyeLocation();
        World world = p.getWorld();

        RayTraceResult hit = world.rayTraceBlocks(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true);
        Location where = hit != null
                ? hit.getHitPosition().toLocation(world).add(0, 0.1, 0)
                : eye.clone().add(eye.getDirection().multiply(range));

        float radius = (float) cfg().getDouble("poison.cloud-radius", 3.0);
        int duration = Math.max(1, cfg().getInt("poison.cloud-seconds", 6)) * 20;
        int poisonTicks = Math.max(1, cfg().getInt("poison.cloud-poison-seconds", 5)) * 20;

        world.spawn(where, AreaEffectCloud.class, cloud -> {
            cloud.setSource(p);
            cloud.setRadius(radius);
            cloud.setRadiusPerTick(0f);
            cloud.setRadiusOnUse(0f);
            cloud.setDuration(duration);
            cloud.setWaitTime(0);
            cloud.setReapplicationDelay(20);
            cloud.setColor(Color.fromRGB(0x4E9331));
            cloud.addCustomEffect(new PotionEffect(PotionEffectType.POISON, poisonTicks, 1), true);
        });

        // Projectile de gaz de toi vers le nuage, puis éclaboussure
        Effects.line(world, eye.clone().subtract(0, 0.3, 0), where, 0.5, Particle.DUST, POISON_DUST);
        world.spawnParticle(Particle.DUST, where.clone().add(0, 0.5, 0), 60, radius / 2, 0.5, radius / 2, 0, POISON_DUST);
        world.playSound(where, Sound.ENTITY_SPLASH_POTION_BREAK, 1.0f, 0.8f);
        world.playSound(where, Sound.ENTITY_SLIME_SQUISH, 1.0f, 0.6f);

        // Le nuage toxique : explosion de gaz puis brume épaisse pendant toute sa durée
        Effects.toxicBurst(world, where, radius);
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick * 2 >= duration) {
                    cancel();
                    return;
                }
                Effects.toxicCloud(world, where, radius, tick);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
        return true;
    }

    // ------------------------------------------------------------------
    // Light : une lumière du ciel te soigne
    // ------------------------------------------------------------------

    private boolean light(Player p) {
        double heal = cfg().getDouble("light.heal", 8.0);
        int regenTicks = Math.max(0, cfg().getInt("light.regen-seconds", 5)) * 20;
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.4f);

        // Une colonne de lumière descend du ciel jusqu'au joueur, avec des anneaux qui pulsent
        new BukkitRunnable() {
            double y = 20.0;
            double angle = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                Location base = p.getLocation();
                World world = p.getWorld();
                for (double offset = 0; offset < 2.0; offset += 0.5) {
                    Location point = base.clone().add(0, y + offset, 0);
                    world.spawnParticle(Particle.END_ROD, point, 3, 0.15, 0.0, 0.15, 0.0);
                }
                // Hélice lumineuse autour de la colonne
                angle += 0.9;
                world.spawnParticle(Particle.END_ROD,
                        base.clone().add(Math.cos(angle) * 0.9, y, Math.sin(angle) * 0.9), 1, 0, 0, 0, 0);
                world.spawnParticle(Particle.END_ROD,
                        base.clone().add(Math.cos(angle + Math.PI) * 0.9, y, Math.sin(angle + Math.PI) * 0.9), 1, 0, 0, 0, 0);
                Effects.ring(world, base.clone().add(0, 0.1, 0), 1.5 + (20.0 - y) * 0.05, 20, Particle.END_ROD, null);

                y -= 2.0;
                if (y <= 0) {
                    p.setHealth(Math.min(maxHealth(p), p.getHealth() + heal));
                    if (regenTicks > 0) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, regenTicks, 1));
                    }
                    world.spawnParticle(Particle.HEART, base.clone().add(0, 2, 0), 8, 0.4, 0.4, 0.4, 0);
                    world.spawnParticle(Particle.END_ROD, base.clone().add(0, 1, 0), 60, 0.5, 0.9, 0.5, 0.15);
                    world.spawnParticle(Particle.TOTEM_OF_UNDYING, base.clone().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.3);
                    Fx.burst(world, base.clone().add(0, 1, 0), Particle.END_ROD, 60, 0.35, null);
                    Fx.sphere(world, base.clone().add(0, 1, 0), 1.6, 60, Particle.GLOW, null);
                    Fx.wave(plugin, world, base.clone().add(0, 0.1, 0), 6.0, 0.9, (w2, c2, rad) -> {
                        Effects.ring(w2, c2, rad, (int) (rad * 9), Particle.END_ROD, null);
                        Effects.ring(w2, c2.clone().add(0, 0.3, 0), rad, (int) (rad * 5), Particle.GLOW, null);
                    });
                    Fx.helix(world, base, 1.0, 3.5, 3, 30, 0, Particle.END_ROD, null);
                    world.playSound(base, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                    world.playSound(base, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    // ------------------------------------------------------------------
    // Phoenix : vol enflammé temporaire avec des ailes de feu
    // ------------------------------------------------------------------

    private boolean phoenix(Player p) {
        GameMode gm = p.getGameMode();
        if (gm != GameMode.SURVIVAL && gm != GameMode.ADVENTURE) return false;

        int ticks = Math.max(1, cfg().getInt("phoenix.flight-seconds", 4)) * 20;
        int burn = Math.max(1, cfg().getInt("phoenix.burn-seconds", 4)) * 20;

        manager.setPhoenixFlying(p, true);
        p.setAllowFlight(true);
        p.setFlying(true);
        p.setVelocity(new Vector(0, 0.6, 0));

        // Explosion de flammes au décollage
        World start = p.getWorld();
        Location body = p.getLocation().add(0, 1, 0);
        start.spawnParticle(Particle.FLAME, body, 80, 0.6, 0.8, 0.6, 0.15);
        start.spawnParticle(Particle.LAVA, body, 12, 0.6, 0.8, 0.6, 0);
        start.spawnParticle(Particle.FIREWORK, body, 30, 0.5, 0.8, 0.5, 0.1);
        Fx.burst(start, body, Particle.FLAME, 70, 0.4, null);
        Fx.column(start, p.getLocation(), 4.0, 0.3, 0.4, Particle.FLAME, 3, null);
        Fx.wave(plugin, start, p.getLocation().add(0, 0.1, 0), 6.0, 0.9, (w2, c2, rad) -> {
            Effects.ring(w2, c2, rad, (int) (rad * 9), Particle.FLAME, null);
            Effects.ring(w2, c2.clone().add(0, 0.4, 0), rad, (int) (rad * 4), Particle.LAVA, null);
        });
        start.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.6f);
        start.playSound(p.getLocation(), Sound.ENTITY_GHAST_SHOOT, 0.8f, 1.2f);

        new BukkitRunnable() {
            int left = ticks;
            double spinFx = 0;

            @Override
            public void run() {
                boolean stop = left <= 0 || !p.isOnline() || p.isDead()
                        || manager.getPower(p) != PowerType.PHOENIX;
                if (stop) {
                    manager.setPhoenixFlying(p, false);
                    if (p.isOnline()) {
                        manager.resetFlight(p);
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0));
                        p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 40, 0.6, 0.8, 0.6, 0.1);
                    }
                    cancel();
                    return;
                }

                Location feet = p.getLocation();
                World world = p.getWorld();
                world.spawnParticle(Particle.FLAME, feet, 10, 0.3, 0.1, 0.3, 0.02);

                // Grandes ailes de feu, spirale de flammes et plumes qui tombent
                spinFx += 0.5;
                Auras.wings(world, feet, feet.clone().add(0, 1, 0), spinFx, (int) (spinFx * 2),
                        ThreadLocalRandom.current());
                Auras.wings(world, feet.clone().add(0, 0.2, 0), feet.clone().add(0, 1, 0), spinFx + 1.0,
                        (int) (spinFx * 2) + 1, ThreadLocalRandom.current());
                Fx.helix(world, feet.clone().subtract(0, 0.5, 0), 0.7, 1.8, 2, 16, spinFx, Particle.DUST_COLOR_TRANSITION, FIRE_T);
                world.spawnParticle(Particle.FIREWORK, feet.clone().add(0, 1.8, 0), 0, 0.0, -1.0, 0.0, 0.08);

                for (Entity e : p.getNearbyEntities(1.5, 4.0, 1.5)) {
                    if (!(e instanceof LivingEntity living) || e instanceof ArmorStand) continue;
                    if (living.getLocation().getY() < feet.getY()) {
                        living.setFireTicks(Math.max(living.getFireTicks(), burn));
                    }
                }
                left -= 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
        return true;
    }
}
