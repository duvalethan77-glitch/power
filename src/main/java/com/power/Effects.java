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
    private int auraTick;
    private boolean auraWarned;

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
            Pt.spawn(w, particle, c.getX() + Math.cos(a) * radius, c.getY(), c.getZ() + Math.sin(a) * radius,
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
            Pt.spawn(w, particle, x, y, z, 1, 0, 0, 0, 0, data);
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

            // Par défaut le porteur ne voit pas sa propre aura (elle le gêne en 1re personne) ;
            // les autres joueurs la voient. /power aura permet de la voir soi-même (F5).
            Pt.skip(manager.isSelfAura(p) ? null : p);
            try {
                orbit(p, type);
                try {
                    Auras.render(p, type, angle, ++auraTick);
                } catch (RuntimeException ex) {
                    if (!auraWarned) {
                        auraWarned = true;
                        plugin.getLogger().warning("Aura " + type.display + " : " + ex);
                    }
                }
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
            } finally {
                Pt.skip(null);
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

    public static void emit(World w, Location l, PowerType type) {
        switch (type) {
            case LIGHTNING -> Pt.spawn(w, Particle.ELECTRIC_SPARK, l, 1, 0, 0, 0, 0);
            case ICE -> Pt.spawn(w, Particle.SNOWFLAKE, l, 1, 0, 0, 0, 0);
            case BLOOD -> Pt.spawn(w, Particle.DUST, l, 1, 0, 0, 0, 0, RED);
            case FIRE -> Pt.spawn(w, Particle.SMALL_FLAME, l, 1, 0, 0, 0, 0);
            case WIND -> Pt.spawn(w, Particle.CLOUD, l, 1, 0, 0, 0, 0);
            case SHADOW -> Pt.spawn(w, Particle.SQUID_INK, l, 1, 0, 0, 0, 0);
            case EARTH -> Pt.spawn(w, Particle.BLOCK, l, 1, 0, 0, 0, 0, Material.STONE.createBlockData());
            case POISON -> Pt.spawn(w, Particle.DUST, l, 1, 0, 0, 0, 0, GREEN);
            case LIGHT -> Pt.spawn(w, Particle.END_ROD, l, 1, 0, 0, 0, 0);
            case PHOENIX -> Pt.spawn(w, Particle.FLAME, l, 1, 0, 0, 0, 0);
            case SMOKE -> Pt.spawn(w, Particle.LARGE_SMOKE, l, 1, 0, 0, 0, 0);
        }
    }

    /** Petit effet aléatoire propre à chaque pouvoir, de temps en temps. */
    private void flair(Player p, PowerType type) {
        World w = p.getWorld();
        Location body = p.getLocation().add(0, 1, 0);
        switch (type) {
            case LIGHTNING -> Pt.spawn(w, Particle.ELECTRIC_SPARK, body, 10, 0.5, 0.8, 0.5, 0.2);
            case ICE -> Pt.spawn(w, Particle.SNOWFLAKE, body, 8, 0.5, 0.8, 0.5, 0.02);
            case BLOOD -> Pt.spawn(w, Particle.CRIMSON_SPORE, body, 6, 0.5, 0.8, 0.5, 0);
            case FIRE -> {
                Pt.spawn(w, Particle.LAVA, body, 1, 0.4, 0.6, 0.4, 0);
                Pt.spawn(w, Particle.SMALL_FLAME, body, 4, 0.4, 0.8, 0.4, 0.02);
            }
            case WIND -> Pt.spawn(w, Particle.WHITE_ASH, body, 8, 0.6, 0.8, 0.6, 0);
            case SHADOW -> {
                Pt.spawn(w, Particle.PORTAL, body, 8, 0.4, 0.8, 0.4, 0.5);
                Pt.spawn(w, Particle.SMOKE, body, 4, 0.4, 0.8, 0.4, 0.01);
            }
            case EARTH -> Pt.spawn(w, Particle.BLOCK, p.getLocation().add(0, 0.1, 0), 6, 0.5, 0.1, 0.5, 0,
                    Material.COARSE_DIRT.createBlockData());
            case POISON -> Pt.spawn(w, Particle.SPORE_BLOSSOM_AIR, body, 5, 0.6, 0.8, 0.6, 0);
            case LIGHT -> Pt.spawn(w, Particle.GLOW, body, 6, 0.5, 0.8, 0.5, 0.02);
            case SMOKE -> {
                Pt.spawn(w, Particle.LARGE_SMOKE, body, 4, 0.5, 0.8, 0.5, 0.01);
                Pt.spawn(w, Particle.CAMPFIRE_COSY_SMOKE, body, 0, 0.0, 1.0, 0.0, 0.03);
            }
            case PHOENIX -> {
                Pt.spawn(w, Particle.FIREWORK, body, 3, 0.4, 0.8, 0.4, 0.03);
                Pt.spawn(w, Particle.LAVA, body, 1, 0.4, 0.6, 0.4, 0);
            }
        }
    }

    /** Traînée au sol quand le joueur se déplace. */
    private void trail(Player p, PowerType type) {
        World w = p.getWorld();
        Location feet = p.getLocation().add(0, 0.1, 0);
        switch (type) {
            case LIGHTNING -> Pt.spawn(w, Particle.ELECTRIC_SPARK, feet, 3, 0.2, 0.05, 0.2, 0.05);
            case ICE -> Pt.spawn(w, Particle.SNOWFLAKE, feet, 4, 0.25, 0.05, 0.25, 0.01);
            case BLOOD -> Pt.spawn(w, Particle.DUST, feet, 3, 0.2, 0.05, 0.2, 0, RED);
            case FIRE -> {
                Pt.spawn(w, Particle.FLAME, feet, 3, 0.2, 0.05, 0.2, 0.01);
                Pt.spawn(w, Particle.SMALL_FLAME, feet, 2, 0.2, 0.05, 0.2, 0.01);
            }
            case WIND -> Pt.spawn(w, Particle.CLOUD, feet, 3, 0.25, 0.05, 0.25, 0.01);
            case SHADOW -> Pt.spawn(w, Particle.SMOKE, feet, 3, 0.2, 0.05, 0.2, 0.01);
            case EARTH -> Pt.spawn(w, Particle.BLOCK, feet, 4, 0.25, 0.05, 0.25, 0,
                    Material.DIRT.createBlockData());
            case POISON -> Pt.spawn(w, Particle.DUST, feet, 3, 0.25, 0.05, 0.25, 0, GREEN);
            case LIGHT -> Pt.spawn(w, Particle.END_ROD, feet, 2, 0.2, 0.05, 0.2, 0.01);
            case SMOKE -> Pt.spawn(w, Particle.LARGE_SMOKE, feet, 3, 0.25, 0.05, 0.25, 0.01);
            case PHOENIX -> {
                Pt.spawn(w, Particle.FLAME, feet, 4, 0.25, 0.05, 0.25, 0.01);
                if (ThreadLocalRandom.current().nextInt(4) == 0) {
                    Pt.spawn(w, Particle.LAVA, feet, 1, 0.2, 0.05, 0.2, 0);
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
                Pt.spawn(w, Particle.ELECTRIC_SPARK, c, 25, 0.3, 0.5, 0.3, 0.15);
                w.playSound(c, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.3f, 2.0f);
            }
            case ICE -> {
                Pt.spawn(w, Particle.SNOWFLAKE, c, 20, 0.3, 0.5, 0.3, 0.05);
                Pt.spawn(w, Particle.BLOCK, c, 15, 0.3, 0.5, 0.3, 0, Material.ICE.createBlockData());
                w.playSound(c, Sound.BLOCK_GLASS_BREAK, 0.4f, 1.8f);
            }
            case BLOOD -> {
                Pt.spawn(w, Particle.DUST, c, 15, 0.3, 0.5, 0.3, 0, new Particle.DustOptions(Color.fromRGB(0x9B0000), 1.4f));
                Pt.spawn(w, Particle.CRIMSON_SPORE, c, 6, 0.3, 0.5, 0.3, 0);
            }
            case FIRE -> {
                Pt.spawn(w, Particle.FLAME, c, 15, 0.3, 0.5, 0.3, 0.05);
                Pt.spawn(w, Particle.LAVA, c, 2, 0.3, 0.5, 0.3, 0);
                w.playSound(c, Sound.ITEM_FIRECHARGE_USE, 0.4f, 1.5f);
            }
            case WIND -> {
                Pt.spawn(w, Particle.CLOUD, c, 10, 0.3, 0.5, 0.3, 0.05);
                Pt.spawn(w, Particle.SWEEP_ATTACK, c, 1, 0, 0, 0, 0);
            }
            case SHADOW -> {
                Pt.spawn(w, Particle.SMOKE, c, 10, 0.3, 0.5, 0.3, 0.03);
                Pt.spawn(w, Particle.SQUID_INK, c, 6, 0.3, 0.5, 0.3, 0.03);
            }
            case EARTH -> Pt.spawn(w, Particle.BLOCK, c, 12, 0.3, 0.5, 0.3, 0, Material.STONE.createBlockData());
            case POISON -> {
                Pt.spawn(w, Particle.ITEM_SLIME, c, 12, 0.3, 0.5, 0.3, 0);
                Pt.spawn(w, Particle.DUST, c, 10, 0.3, 0.5, 0.3, 0, GREEN);
            }
            case LIGHT -> Pt.spawn(w, Particle.END_ROD, c, 12, 0.3, 0.5, 0.3, 0.05);
            case SMOKE -> {
                Pt.spawn(w, Particle.LARGE_SMOKE, c, 12, 0.3, 0.5, 0.3, 0.03);
                Pt.spawn(w, Particle.SMOKE, c, 10, 0.3, 0.5, 0.3, 0.02);
            }
            case PHOENIX -> {
                Pt.spawn(w, Particle.FLAME, c, 12, 0.3, 0.5, 0.3, 0.05);
                Pt.spawn(w, Particle.FIREWORK, c, 4, 0.3, 0.5, 0.3, 0.05);
            }
        }
    }

    /** Coup renforcé de Shadow après sa téléportation. */
    public static void backstabHit(LivingEntity victim) {
        Location c = victim.getLocation().add(0, 1, 0);
        World w = victim.getWorld();
        Pt.spawn(w, Particle.SQUID_INK, c, 30, 0.4, 0.6, 0.4, 0.1);
        Pt.spawn(w, Particle.SWEEP_ATTACK, c, 3, 0.3, 0.4, 0.3, 0);
        w.playSound(c, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.6f);
    }

    // ------------------------------------------------------------------
    // Nuage toxique (Poison)
    // ------------------------------------------------------------------

    private static final Particle.DustTransition TOXIC_A =
            new Particle.DustTransition(Color.fromRGB(0x7FD43A), Color.fromRGB(0x2E5A1C), 2.4f);
    private static final Particle.DustTransition TOXIC_B =
            new Particle.DustTransition(Color.fromRGB(0xB6E04A), Color.fromRGB(0x3D6B22), 1.8f);

    /** Explosion de gaz au moment où le nuage se forme. */
    public static void toxicBurst(World w, Location c, double radius) {
        Pt.spawn(w, Particle.DUST_COLOR_TRANSITION, c.clone().add(0, 0.6, 0), 120,
                radius * 0.5, 0.5, radius * 0.5, 0.0, TOXIC_A);
        Pt.spawn(w, Particle.SNEEZE, c.clone().add(0, 0.5, 0), 60, radius * 0.5, 0.3, radius * 0.5, 0.08);
        Pt.spawn(w, Particle.ITEM_SLIME, c.clone().add(0, 0.4, 0), 40, radius * 0.5, 0.3, radius * 0.5, 0.1);
        Pt.spawn(w, Particle.COMPOSTER, c.clone().add(0, 0.4, 0), 25, radius * 0.5, 0.4, radius * 0.5, 0.1);
        ring(w, c.clone().add(0, 0.15, 0), radius, 48, Particle.DUST, GREEN);
        w.playSound(c, Sound.ENTITY_SPLASH_POTION_BREAK, 1.0f, 0.7f);
        w.playSound(c, Sound.BLOCK_BREWING_STAND_BREW, 1.0f, 0.6f);
    }

    /** Un « battement » du nuage : dôme de brume, volutes, spores et bulles. À appeler toutes les 2 ticks. */
    public static void toxicCloud(World w, Location c, double radius, int tick) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        // Dôme de brume épaisse
        for (int i = 0; i < 14; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(rnd.nextDouble()) * radius;
            double maxH = Math.sqrt(Math.max(0.0, 1.0 - (d * d) / (radius * radius))) * radius * 0.55;
            Location l = c.clone().add(Math.cos(a) * d, 0.1 + rnd.nextDouble() * maxH, Math.sin(a) * d);
            Pt.spawn(w, Particle.DUST_COLOR_TRANSITION, l, 1, 0.15, 0.1, 0.15, 0, rnd.nextBoolean() ? TOXIC_A : TOXIC_B);
        }

        // Voiles translucides
        try {
            for (int i = 0; i < 3; i++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                double d = Math.sqrt(rnd.nextDouble()) * radius;
                Location l = c.clone().add(Math.cos(a) * d, 0.2 + rnd.nextDouble() * 0.9, Math.sin(a) * d);
                Pt.spawn(w, Particle.ENTITY_EFFECT, l, 1, 0, 0, 0, 0, Color.fromARGB(160, 90, 190, 50));
            }
        } catch (RuntimeException ignored) {
            // Format de particule différent sur cette version : on s'en passe
        }

        // Trois volutes qui montent en spirale
        double t = tick * 0.12;
        for (int arm = 0; arm < 3; arm++) {
            double a = t + arm * (Math.PI * 2 / 3);
            double r = radius * (0.35 + 0.5 * ((Math.sin(t * 0.7 + arm) + 1) / 2));
            double h = ((tick * 0.05 + arm * 0.5) % 1.5);
            Pt.spawn(w, Particle.SNEEZE, c.clone().add(Math.cos(a) * r, 0.2 + h, Math.sin(a) * r),
                    1, 0.05, 0.05, 0.05, 0.0);
        }

        // Bulles et spores
        if (rnd.nextInt(2) == 0) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(rnd.nextDouble()) * radius;
            Pt.spawn(w, Particle.COMPOSTER, c.clone().add(Math.cos(a) * d, 0.2, Math.sin(a) * d), 2, 0.1, 0.2, 0.1, 0);
        }
        Pt.spawn(w, Particle.SPORE_BLOSSOM_AIR, c.clone().add(0, 0.8, 0), 3, radius * 0.5, 0.5, radius * 0.5, 0);

        // Anneau au sol, qui respire
        if (tick % 6 == 0) {
            double pulse = radius * (0.9 + 0.1 * Math.sin(tick * 0.2));
            ring(w, c.clone().add(0, 0.1, 0), pulse, 40, Particle.DUST, GREEN);
        }
        if (tick % 20 == 0) {
            w.playSound(c, Sound.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, 0.6f, 0.6f);
        }
    }
}
