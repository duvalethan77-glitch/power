package com.power;

import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Effets visuels : aura en orbite autour des joueurs qui ont un pouvoir, traînée quand ils
 * bougent, étincelles quand ils frappent. Contient aussi les outils (anneau, ligne) des capacités.
 */
public final class Effects {

    private static final Particle.DustOptions RED = new Particle.DustOptions(Color.fromRGB(0x9B0000), 1.0f);
    private static final Particle.DustOptions GREEN = new Particle.DustOptions(Color.fromRGB(0x4E9331), 1.0f);

    private final PowerPlugin plugin;
    private final PowerManager manager;
    private final Map<UUID, double[]> last = new HashMap<>();
    private double angle;

    public Effects(PowerPlugin plugin, PowerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 10L, 2L);
    }

    // ------------------------------------------------------------------
    // Outils partagés
    // ------------------------------------------------------------------

    /** Anneau horizontal de particules. */
    public static void ring(World w, Location c, double radius, int points, Particle particle, Object data) {
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            w.spawnParticle(particle, c.getX() + Math.cos(a) * radius, c.getY(), c.getZ() + Math.sin(a) * radius,
                    1, 0, 0, 0, 0, data);
        }
    }

    /** Ligne de particules entre deux points. */
    public static void line(World w, Location from, Location to, double step, Particle particle, Object data) {
        double length = from.distance(to);
        if (length < 0.01) return;
        double dx = (to.getX() - from.getX()) / length * step;
        double dy = (to.getY() - from.getY()) / length * step;
        double dz = (to.getZ() - from.getZ()) / length * step;
        double x = from.getX();
        double y = from.getY();
        double z = from.getZ();
        for (double d = 0; d < length; d += step) {
            w.spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0, data);
            x += dx;
            y += dy;
            z += dz;
        }
    }

    // ------------------------------------------------------------------
    // Aura et traînée (toutes les 2 ticks)
    // ------------------------------------------------------------------

    private void tick() {
        if (!plugin.getConfig().getBoolean("aura.enabled", true)) return;

        angle += 0.35;
        last.keySet().removeIf(id -> plugin.getServer().getPlayer(id) == null);

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            PowerType type = manager.getPower(p);
            if (type == null || p.getGameMode() == GameMode.SPECTATOR) continue;
            if (p.hasPotionEffect(PotionEffectType.INVISIBILITY)) continue;

            orbit(p, type);
            if (ThreadLocalRandom.current().nextInt(10) == 0) {
                flair(p, type);
            }

            Location now = p.getLocation();
            double[] before = last.put(p.getUniqueId(), new double[] {now.getX(), now.getZ()});
            if (before != null) {
                double dx = now.getX() - before[0];
                double dz = now.getZ() - before[1];
                if (dx * dx + dz * dz > 0.01) {
                    trail(p, type);
                }
            }
        }
    }

    /** Deux points qui tournent autour du joueur, à des hauteurs différentes. */
    private void orbit(Player p, PowerType type) {
        World w = p.getWorld();
        Location base = p.getLocation();
        double radius = 0.75;
        double rise = (Math.sin(angle * 0.5) + 1.0) * 0.6; // 0 à 1.2

        Location a = base.clone().add(Math.cos(angle) * radius, 0.3 + rise, Math.sin(angle) * radius);
        Location b = base.clone().add(Math.cos(angle + Math.PI) * radius, 1.5 - rise, Math.sin(angle + Math.PI) * radius);
        emit(w, a, type);
        emit(w, b, type);
    }

    private void emit(World w, Location l, PowerType type) {
        switch (type) {
            case LIGHTNING -> w.spawnParticle(Particle.ELECTRIC_SPARK, l, 1, 0, 0, 0, 0);
            case ICE -> w.spawnParticle(Particle.SNOWFLAKE, l, 1, 0, 0, 0, 0);
            case BLOOD -> w.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, RED);
            case FIRE -> w.spawnParticle(Particle.SMALL_FLAME, l, 1, 0, 0, 0, 0);
            case WIND -> w.spawnParticle(Particle.CLOUD, l, 1, 0, 0, 0, 0);
            case SHADOW -> w.spawnParticle(Particle.SQUID_INK, l, 1, 0, 0, 0, 0);
            case EARTH -> w.spawnParticle(Particle.BLOCK, l, 1, 0, 0, 0, 0, Material.STONE.createBlockData());
            case POISON -> w.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, GREEN);
            case LIGHT -> w.spawnParticle(Particle.END_ROD, l, 1, 0, 0, 0, 0);
            case PHOENIX -> w.spawnParticle(Particle.FLAME, l, 1, 0, 0, 0, 0);
        }
    }

    /** Petit effet aléatoire propre à chaque pouvoir, de temps en temps. */
    private void flair(Player p, PowerType type) {
        World w = p.getWorld();
        Location body = p.getLocation().add(0, 1, 0);
        switch (type) {
            case LIGHTNING -> w.spawnParticle(Particle.ELECTRIC_SPARK, body, 10, 0.5, 0.8, 0.5, 0.2);
            case ICE -> w.spawnParticle(Particle.SNOWFLAKE, body, 8, 0.5, 0.8, 0.5, 0.02);
            case BLOOD -> w.spawnParticle(Particle.CRIMSON_SPORE, body, 6, 0.5, 0.8, 0.5, 0);
            case FIRE -> {
                w.spawnParticle(Particle.LAVA, body, 1, 0.4, 0.6, 0.4, 0);
                w.spawnParticle(Particle.SMALL_FLAME, body, 4, 0.4, 0.8, 0.4, 0.02);
            }
            case WIND -> w.spawnParticle(Particle.WHITE_ASH, body, 8, 0.6, 0.8, 0.6, 0);
            case SHADOW -> {
                w.spawnParticle(Particle.PORTAL, body, 8, 0.4, 0.8, 0.4, 0.5);
                w.spawnParticle(Particle.SMOKE, body, 4, 0.4, 0.8, 0.4, 0.01);
            }
            case EARTH -> w.spawnParticle(Particle.BLOCK, p.getLocation().add(0, 0.1, 0), 6, 0.5, 0.1, 0.5, 0,
                    Material.COARSE_DIRT.createBlockData());
            case POISON -> w.spawnParticle(Particle.SPORE_BLOSSOM_AIR, body, 5, 0.6, 0.8, 0.6, 0);
            case LIGHT -> w.spawnParticle(Particle.GLOW, body, 6, 0.5, 0.8, 0.5, 0.02);
            case PHOENIX -> {
                w.spawnParticle(Particle.FIREWORK, body, 3, 0.4, 0.8, 0.4, 0.03);
                w.spawnParticle(Particle.LAVA, body, 1, 0.4, 0.6, 0.4, 0);
            }
        }
    }

    /** Traînée au sol quand le joueur se déplace. */
    private void trail(Player p, PowerType type) {
        World w = p.getWorld();
        Location feet = p.getLocation().add(0, 0.1, 0);
        switch (type) {
            case LIGHTNING -> w.spawnParticle(Particle.ELECTRIC_SPARK, feet, 3, 0.2, 0.05, 0.2, 0.05);
            case ICE -> w.spawnParticle(Particle.SNOWFLAKE, feet, 4, 0.25, 0.05, 0.25, 0.01);
            case BLOOD -> w.spawnParticle(Particle.DUST, feet, 3, 0.2, 0.05, 0.2, 0, RED);
            case FIRE -> {
                w.spawnParticle(Particle.FLAME, feet, 3, 0.2, 0.05, 0.2, 0.01);
                w.spawnParticle(Particle.SMALL_FLAME, feet, 2, 0.2, 0.05, 0.2, 0.01);
            }
            case WIND -> w.spawnParticle(Particle.CLOUD, feet, 3, 0.25, 0.05, 0.25, 0.01);
            case SHADOW -> w.spawnParticle(Particle.SMOKE, feet, 3, 0.2, 0.05, 0.2, 0.01);
            case EARTH -> w.spawnParticle(Particle.BLOCK, feet, 4, 0.25, 0.05, 0.25, 0,
                    Material.DIRT.createBlockData());
            case POISON -> w.spawnParticle(Particle.DUST, feet, 3, 0.25, 0.05, 0.25, 0, GREEN);
            case LIGHT -> w.spawnParticle(Particle.END_ROD, feet, 2, 0.2, 0.05, 0.2, 0.01);
            case PHOENIX -> {
                w.spawnParticle(Particle.FLAME, feet, 4, 0.25, 0.05, 0.25, 0.01);
                if (ThreadLocalRandom.current().nextInt(4) == 0) {
                    w.spawnParticle(Particle.LAVA, feet, 1, 0.2, 0.05, 0.2, 0);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Effets sur la cible
    // ------------------------------------------------------------------

    /** Effet sur la cible quand un porteur de pouvoir la frappe. */
    public static void hit(LivingEntity victim, PowerType type) {
        Location c = victim.getLocation().add(0, 1, 0);
        World w = victim.getWorld();
        switch (type) {
            case LIGHTNING -> {
                w.spawnParticle(Particle.ELECTRIC_SPARK, c, 25, 0.3, 0.5, 0.3, 0.15);
                w.playSound(c, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.3f, 2.0f);
            }
            case ICE -> {
                w.spawnParticle(Particle.SNOWFLAKE, c, 20, 0.3, 0.5, 0.3, 0.05);
                w.spawnParticle(Particle.BLOCK, c, 15, 0.3, 0.5, 0.3, 0, Material.ICE.createBlockData());
                w.playSound(c, Sound.BLOCK_GLASS_BREAK, 0.4f, 1.8f);
            }
            case BLOOD -> {
                w.spawnParticle(Particle.DUST, c, 15, 0.3, 0.5, 0.3, 0, new Particle.DustOptions(Color.fromRGB(0x9B0000), 1.4f));
                w.spawnParticle(Particle.CRIMSON_SPORE, c, 6, 0.3, 0.5, 0.3, 0);
            }
            case FIRE -> {
                w.spawnParticle(Particle.FLAME, c, 15, 0.3, 0.5, 0.3, 0.05);
                w.spawnParticle(Particle.LAVA, c, 2, 0.3, 0.5, 0.3, 0);
                w.playSound(c, Sound.ITEM_FIRECHARGE_USE, 0.4f, 1.5f);
            }
            case WIND -> {
                w.spawnParticle(Particle.CLOUD, c, 10, 0.3, 0.5, 0.3, 0.05);
                w.spawnParticle(Particle.SWEEP_ATTACK, c, 1, 0, 0, 0, 0);
            }
            case SHADOW -> {
                w.spawnParticle(Particle.SMOKE, c, 10, 0.3, 0.5, 0.3, 0.03);
                w.spawnParticle(Particle.SQUID_INK, c, 6, 0.3, 0.5, 0.3, 0.03);
            }
            case EARTH -> w.spawnParticle(Particle.BLOCK, c, 12, 0.3, 0.5, 0.3, 0, Material.STONE.createBlockData());
            case POISON -> {
                w.spawnParticle(Particle.ITEM_SLIME, c, 12, 0.3, 0.5, 0.3, 0);
                w.spawnParticle(Particle.DUST, c, 10, 0.3, 0.5, 0.3, 0, GREEN);
            }
            case LIGHT -> w.spawnParticle(Particle.END_ROD, c, 12, 0.3, 0.5, 0.3, 0.05);
            case PHOENIX -> {
                w.spawnParticle(Particle.FLAME, c, 12, 0.3, 0.5, 0.3, 0.05);
                w.spawnParticle(Particle.FIREWORK, c, 4, 0.3, 0.5, 0.3, 0.05);
            }
        }
    }

    /** Coup renforcé de Shadow après sa téléportation. */
    public static void backstabHit(LivingEntity victim) {
        Location c = victim.getLocation().add(0, 1, 0);
        World w = victim.getWorld();
        w.spawnParticle(Particle.SQUID_INK, c, 30, 0.4, 0.6, 0.4, 0.1);
        w.spawnParticle(Particle.SWEEP_ATTACK, c, 3, 0.3, 0.4, 0.3, 0);
        w.playSound(c, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.6f);
    }
}
