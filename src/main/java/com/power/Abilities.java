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
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** Les capacités actives (clic droit) de chaque pouvoir. */
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

    // ------------------------------------------------------------------
    // Wind : dash + poussée des ennemis proches
    // ------------------------------------------------------------------

    private boolean wind(Player p) {
        Vector dash = horizontal(p.getLocation().getDirection()).multiply(cfg().getDouble("wind.dash-speed", 1.6));
        dash.setY(0.35);
        p.setVelocity(dash);

        double radius = cfg().getDouble("wind.repel-radius", 5);
        double strength = cfg().getDouble("wind.repel-strength", 1.4);
        for (Entity e : p.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof LivingEntity living) || e instanceof ArmorStand) continue;
            Vector away = horizontal(living.getLocation().toVector().subtract(p.getLocation().toVector()));
            away.multiply(strength).setY(0.5);
            living.setVelocity(away);
        }

        World world = p.getWorld();
        world.spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 40, radius / 3, 0.5, radius / 3, 0.1);
        world.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.2f);
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
            world.spawnParticle(Particle.LARGE_SMOKE, from.clone().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.02);
        }
        p.teleport(behind);
        p.getWorld().spawnParticle(Particle.LARGE_SMOKE, behind.clone().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.02);
        p.getWorld().playSound(behind, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);

        manager.startBackstab(p, Math.max(1, cfg().getInt("shadow.backstab-seconds", 4)));
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

        for (int i = 1; i <= count; i++) {
            Location at = base.clone().add(dir.clone().multiply(i * 1.5));
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> pillar(p, at, damage), i * 2L);
        }
        return true;
    }

    private void pillar(Player caster, Location at, double damage) {
        World world = at.getWorld();
        if (world == null) return;

        for (double y = 0; y <= 2.0; y += 0.5) {
            world.spawnParticle(Particle.BLOCK, at.clone().add(0, y, 0), 14, 0.3, 0.1, 0.3, 0,
                    Material.STONE.createBlockData());
        }
        world.playSound(at, Sound.BLOCK_STONE_BREAK, 1.0f, 0.7f);

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
        world.playSound(where, Sound.ENTITY_SPLASH_POTION_BREAK, 1.0f, 0.8f);
        return true;
    }

    // ------------------------------------------------------------------
    // Light : rayon qui blesse un ennemi ou soigne un allié
    // ------------------------------------------------------------------

    private boolean light(Player p) {
        LivingEntity target = target(p, cfg().getDouble("light.range", 30));
        if (target == null) {
            noTarget(p);
            return false;
        }

        Location start = p.getEyeLocation().subtract(0, 0.2, 0);
        Vector toTarget = target.getEyeLocation().toVector().subtract(start.toVector());
        double length = toTarget.length();
        if (length > 0) {
            Vector step = toTarget.clone().normalize().multiply(0.5);
            Location point = start.clone();
            for (double d = 0; d < length; d += 0.5) {
                p.getWorld().spawnParticle(Particle.END_ROD, point, 1, 0, 0, 0, 0);
                point.add(step);
            }
        }

        if (isAlly(p, target)) {
            double heal = cfg().getDouble("light.heal", 6.0);
            target.setHealth(Math.min(maxHealth(target), target.getHealth() + heal));
            target.getWorld().spawnParticle(Particle.HEART, target.getLocation().add(0, 1.5, 0), 6, 0.3, 0.3, 0.3, 0);
        } else {
            hurt(p, target, cfg().getDouble("light.damage", 6.0));
        }
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.6f);
        return true;
    }

    /** Alliés = joueurs de la même équipe du scoreboard (/team). */
    private boolean isAlly(Player p, LivingEntity target) {
        Scoreboard board = plugin.getServer().getScoreboardManager().getMainScoreboard();
        Team mine = board.getEntryTeam(p.getName());
        if (mine == null) return false;
        String entry = target instanceof Player other ? other.getName() : target.getUniqueId().toString();
        return mine.equals(board.getEntryTeam(entry));
    }

    // ------------------------------------------------------------------
    // Phoenix : vol enflammé temporaire
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
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.6f);

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
                    }
                    cancel();
                    return;
                }

                Location feet = p.getLocation();
                p.getWorld().spawnParticle(Particle.FLAME, feet, 10, 0.3, 0.1, 0.3, 0.02);
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
