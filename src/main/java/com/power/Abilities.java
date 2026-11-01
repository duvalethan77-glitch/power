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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/** Les capacités actives (clic droit) de chaque pouvoir. */
public final class Abilities {

    private static final Particle.DustOptions BLOOD_DUST = new Particle.DustOptions(Color.fromRGB(0x9B0000), 1.6f);
    private static final Particle.DustOptions POISON_DUST = new Particle.DustOptions(Color.fromRGB(0x4E9331), 1.5f);

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
        p.sendActionBar(Component.text(Lang.tr(p, "no_target"), NamedTextColor.RED));
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
        Effects.line(world, p.getEyeLocation().subtract(0, 0.3, 0), target.getEyeLocation(), 0.5,
                Particle.ELECTRIC_SPARK, null);
        Effects.ring(world, p.getLocation().add(0, 0.1, 0), 1.5, 24, Particle.ELECTRIC_SPARK, null);
        world.spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.3);
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
        Effects.ring(world, loc.clone().add(0, 0.1, 0), 2.5, 32, Particle.ELECTRIC_SPARK, null);
        world.spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0, 1, 0), 40, 0.5, 1.0, 0.5, 0.25);
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

        // Rayon de givre de toi vers la cible, puis explosion de glace
        World world = target.getWorld();
        Effects.line(world, p.getEyeLocation().subtract(0, 0.3, 0), target.getEyeLocation(), 0.4,
                Particle.SNOWFLAKE, null);
        Location center = target.getLocation().add(0, 1, 0);
        world.spawnParticle(Particle.BLOCK, center, 60, 0.5, 0.9, 0.5, 0, Material.ICE.createBlockData());
        world.spawnParticle(Particle.SNOWFLAKE, center, 50, 0.6, 0.9, 0.6, 0.05);
        Effects.ring(world, target.getLocation().add(0, 0.1, 0), 1.3, 24, Particle.SNOWFLAKE, null);
        world.playSound(center, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.8f);
        world.playSound(center, Sound.BLOCK_POWDER_SNOW_PLACE, 1.0f, 0.6f);

        new BukkitRunnable() {
            int left = ticks;

            @Override
            public void run() {
                if (!target.isValid() || target.isDead()) {
                    cancel();
                    return;
                }
                if (left <= 0) {
                    // L'ice se brise
                    Location c = target.getLocation().add(0, 1, 0);
                    target.getWorld().spawnParticle(Particle.BLOCK, c, 50, 0.4, 0.8, 0.4, 0,
                            Material.ICE.createBlockData());
                    target.getWorld().playSound(c, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.4f);
                    cancel();
                    return;
                }
                target.setFreezeTicks(target.getMaxFreezeTicks());
                Location c = target.getLocation().add(0, 1, 0);
                target.getWorld().spawnParticle(Particle.SNOWFLAKE, c, 12, 0.4, 0.6, 0.4, 0.02);
                target.getWorld().spawnParticle(Particle.BLOCK, c, 6, 0.35, 0.8, 0.35, 0,
                        Material.PACKED_ICE.createBlockData());
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

        // Onde de choc rouge qui s'étend autour de toi
        new BukkitRunnable() {
            double radius = 1.0;

            @Override
            public void run() {
                if (radius > 6.0 || !p.isOnline()) {
                    cancel();
                    return;
                }
                Effects.ring(world, p.getLocation().add(0, 0.2, 0), radius, (int) (radius * 10),
                        Particle.DUST, BLOOD_DUST);
                radius += 1.0;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Pendant le berserk : brume de sang et battements de coeur
        new BukkitRunnable() {
            int beat = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || !manager.isBerserk(p)) {
                    cancel();
                    return;
                }
                Location body = p.getLocation().add(0, 1, 0);
                world.spawnParticle(Particle.DUST, body, 10, 0.45, 0.8, 0.45, 0, BLOOD_DUST);
                world.spawnParticle(Particle.CRIMSON_SPORE, body, 4, 0.45, 0.8, 0.45, 0);
                if (beat++ % 8 == 0) {
                    world.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.7f, 1.0f);
                }
            }
        }.runTaskTimer(plugin, 0L, 4L);
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
        world.spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.08);

        // Traînée de flammes derrière la boule de feu
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!fireball.isValid() || fireball.isDead()) {
                    cancel();
                    return;
                }
                Location l = fireball.getLocation();
                world.spawnParticle(Particle.FLAME, l, 8, 0.25, 0.25, 0.25, 0.02);
                world.spawnParticle(Particle.LAVA, l, 1, 0.1, 0.1, 0.1, 0);
                world.spawnParticle(Particle.SMOKE, l, 3, 0.15, 0.15, 0.15, 0.01);
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
        Effects.ring(world, p.getLocation().add(0, 0.3, 0), radius, 36, Particle.CLOUD, null);
        world.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.2f);
        world.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1.0f, 1.5f);

        // Traînée de vent pendant le dash
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ >= 10 || !p.isOnline()) {
                    cancel();
                    return;
                }
                Location l = p.getLocation().add(0, 1, 0);
                world.spawnParticle(Particle.CLOUD, l, 8, 0.3, 0.4, 0.3, 0.03);
                world.spawnParticle(Particle.SWEEP_ATTACK, l, 1, 0, 0, 0, 0);
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
            world.spawnParticle(Particle.SQUID_INK, from.clone().add(0, 1, 0), 40, 0.3, 0.8, 0.3, 0.05);
            world.spawnParticle(Particle.LARGE_SMOKE, from.clone().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.02);
        }
        p.teleport(behind);
        World dest = p.getWorld();
        Location arrive = behind.clone().add(0, 1, 0);
        dest.spawnParticle(Particle.PORTAL, arrive, 60, 0.4, 0.8, 0.4, 0.8);
        dest.spawnParticle(Particle.LARGE_SMOKE, arrive, 25, 0.3, 0.6, 0.3, 0.02);
        Effects.ring(dest, behind.clone().add(0, 0.1, 0), 1.5, 24, Particle.SQUID_INK, null);
        dest.playSound(behind, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);

        manager.startBackstab(p, Math.max(1, cfg().getInt("shadow.backstab-seconds", 4)));

        // Tant que le coup renforcé est prêt, une ombre t'entoure
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || !manager.hasBackstab(p)) {
                    cancel();
                    return;
                }
                Location body = p.getLocation().add(0, 1, 0);
                p.getWorld().spawnParticle(Particle.SQUID_INK, body, 3, 0.4, 0.7, 0.4, 0.01);
                p.getWorld().spawnParticle(Particle.PORTAL, body, 4, 0.4, 0.7, 0.4, 0.3);
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

        // Le sol tremble autour de toi
        World world = p.getWorld();
        Effects.ring(world, base.clone().add(0, 0.1, 0), 1.6, 24, Particle.BLOCK, Material.DIRT.createBlockData());
        world.playSound(base, Sound.ENTITY_IRON_GOLEM_DAMAGE, 1.0f, 0.5f);

        for (int i = 1; i <= count; i++) {
            Location at = base.clone().add(dir.clone().multiply(i * 1.5));
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> pillar(p, at, damage), i * 2L);
        }
        return true;
    }

    private void pillar(Player caster, Location at, double damage) {
        World world = at.getWorld();
        if (world == null) return;

        // Colonne de roche, anneau de poussière et éclats
        for (double y = 0; y <= 3.0; y += 0.5) {
            world.spawnParticle(Particle.BLOCK, at.clone().add(0, y, 0), 16, 0.3, 0.1, 0.3, 0,
                    Material.STONE.createBlockData());
        }
        Effects.ring(world, at.clone().add(0, 0.1, 0), 1.2, 16, Particle.CLOUD, null);
        world.spawnParticle(Particle.BLOCK, at.clone().add(0, 3.2, 0), 12, 0.4, 0.2, 0.4, 0.1,
                Material.COBBLESTONE.createBlockData());
        world.playSound(at, Sound.BLOCK_STONE_BREAK, 1.0f, 0.7f);
        world.playSound(at, Sound.BLOCK_GRAVEL_BREAK, 1.0f, 0.6f);

        for (Entity e : world.getNearbyEntities(at, 1.2, 2.0, 1.2)) {
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

        // Le nuage bouillonne pendant toute sa durée
        new BukkitRunnable() {
            int left = duration;

            @Override
            public void run() {
                if (left <= 0) {
                    cancel();
                    return;
                }
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                for (int i = 0; i < 10; i++) {
                    double angle = rnd.nextDouble() * Math.PI * 2;
                    double dist = Math.sqrt(rnd.nextDouble()) * radius;
                    Location l = where.clone().add(Math.cos(angle) * dist, rnd.nextDouble() * 0.8, Math.sin(angle) * dist);
                    world.spawnParticle(Particle.DUST, l, 1, 0.1, 0.1, 0.1, 0, POISON_DUST);
                }
                world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, where.clone().add(0, 0.5, 0), 4, radius / 2, 0.4, radius / 2, 0);
                left -= 3;
            }
        }.runTaskTimer(plugin, 0L, 3L);
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
        Effects.ring(start, p.getLocation().add(0, 0.1, 0), 2.0, 30, Particle.FLAME, null);
        start.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.6f);
        start.playSound(p.getLocation(), Sound.ENTITY_GHAST_SHOOT, 0.8f, 1.2f);

        new BukkitRunnable() {
            int left = ticks;

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

                // Deux ailes de feu dans le dos
                Vector forward = horizontal(feet.getDirection());
                if (forward.lengthSquared() > 0) {
                    Vector right = new Vector(-forward.getZ(), 0, forward.getX());
                    for (int i = 0; i < 6; i++) {
                        double t = i * 0.35;
                        for (int side = -1; side <= 1; side += 2) {
                            Location wing = feet.clone()
                                    .add(right.clone().multiply(side * (0.4 + t)))
                                    .add(0, 0.9 + t * 0.6, 0)
                                    .subtract(forward.clone().multiply(0.3 + t * 0.2));
                            world.spawnParticle(Particle.FLAME, wing, 1, 0.03, 0.03, 0.03, 0.0);
                        }
                    }
                    world.spawnParticle(Particle.LAVA, feet.clone().add(0, 1, 0), 1, 0.3, 0.4, 0.3, 0);
                }

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
