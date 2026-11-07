package com.power;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Aura propre à chaque pouvoir, dessinée autour du porteur (appelée toutes les 2 ticks).
 * En plus des orbes communes, chaque pouvoir a sa signature : arcs électriques, givre,
 * sang qui goutte, flammes en spirale, tourbillons, tentacules d'ombre, roches, bulles toxiques,
 * halo de lumière et ailes de phénix.
 */
final class Auras {

    private static final Particle.DustOptions RED = new Particle.DustOptions(Color.fromRGB(0x9B0000), 1.2f);
    private static final Particle.DustOptions FROST = new Particle.DustOptions(Color.fromRGB(0xBFF3FF), 0.9f);
    private static final Particle.DustOptions HALO = new Particle.DustOptions(Color.fromRGB(0xFFE680), 1.0f);
    private static final Particle.DustTransition TOXIC =
            new Particle.DustTransition(Color.fromRGB(0x7FD43A), Color.fromRGB(0x2E5A1C), 1.2f);
    private static final Particle.DustTransition VOID =
            new Particle.DustTransition(Color.fromRGB(0x7A5FB8), Color.fromRGB(0x0A0A12), 1.3f);

    private Auras() {}

    /** t : angle qui avance, tick : compteur d'appels. */
    static void render(Player p, PowerType type, double t, int tick) {
        World w = p.getWorld();
        Location feet = p.getLocation();
        Location body = feet.clone().add(0, 1.0, 0);
        ThreadLocalRandom r = ThreadLocalRandom.current();

        // Petit cercle de runes au sol, à la couleur du pouvoir
        Particle.DustOptions rune = new Particle.DustOptions(type.potionColor, 0.9f);
        for (int k = 0; k < 6; k++) {
            double a = t * 0.8 + k * (Math.PI / 3);
            w.spawnParticle(Particle.DUST, feet.getX() + Math.cos(a) * 0.95, feet.getY() + 0.05,
                    feet.getZ() + Math.sin(a) * 0.95, 1, 0, 0, 0, 0, rune);
        }

        switch (type) {
            case LIGHTNING -> {
                if (r.nextInt(3) == 0) arc(w, body, r);
                for (int k = 0; k < 4; k++) {
                    double a = t * 2 + k * Math.PI / 2;
                    w.spawnParticle(Particle.ELECTRIC_SPARK, feet.getX() + Math.cos(a) * 0.35, feet.getY() + 2.25,
                            feet.getZ() + Math.sin(a) * 0.35, 1, 0, 0, 0, 0);
                }
            }
            case ICE -> {
                for (int k = 0; k < 6; k++) {
                    double a = -t + k * (Math.PI / 3);
                    w.spawnParticle(Particle.SNOWFLAKE, feet.getX() + Math.cos(a) * 1.1, feet.getY() + 0.15,
                            feet.getZ() + Math.sin(a) * 1.1, 1, 0, 0, 0, 0);
                }
                for (int k = 0; k < 8; k++) {
                    double a = t * 1.2 + k * (Math.PI / 4);
                    w.spawnParticle(Particle.DUST, feet.getX() + Math.cos(a) * 0.35, feet.getY() + 2.3,
                            feet.getZ() + Math.sin(a) * 0.35, 1, 0, 0, 0, 0, FROST);
                }
                if (r.nextInt(2) == 0) {
                    // Neige qui tombe autour du porteur
                    w.spawnParticle(Particle.SNOWFLAKE, body.getX() + r.nextDouble(-0.8, 0.8), feet.getY() + 2.6,
                            body.getZ() + r.nextDouble(-0.8, 0.8), 0, 0.0, -1.0, 0.0, 0.12);
                }
            }
            case BLOOD -> {
                if (r.nextInt(3) == 0) {
                    w.spawnParticle(Particle.FALLING_DUST,
                            body.getX() + r.nextDouble(-0.4, 0.4), feet.getY() + r.nextDouble(0.8, 1.8),
                            body.getZ() + r.nextDouble(-0.4, 0.4), 1, 0, 0, 0, 0,
                            Material.REDSTONE_BLOCK.createBlockData());
                }
                // Battement de coeur : une onde rouge qui s'étend
                int phase = tick % 30;
                if (phase < 12) {
                    Effects.ring(w, feet.clone().add(0, 0.1, 0), 0.3 + phase * 0.08, 20, Particle.DUST, RED);
                }
                if (r.nextInt(30) == 0) {
                    w.spawnParticle(Particle.HEART, feet.getX(), feet.getY() + 2.3, feet.getZ(), 1, 0.2, 0.1, 0.2, 0);
                }
            }
            case FIRE -> {
                for (int k = 0; k < 2; k++) {
                    double a = t * 1.6 + k * Math.PI;
                    double h = (tick * 0.06 + k) % 2.0;
                    double rad = 0.75 - h * 0.2;
                    w.spawnParticle(Particle.FLAME, feet.getX() + Math.cos(a) * rad, feet.getY() + h,
                            feet.getZ() + Math.sin(a) * rad, 1, 0.02, 0.02, 0.02, 0);
                }
                if (r.nextInt(8) == 0) {
                    w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, feet.getX(), feet.getY() + 2.2, feet.getZ(),
                            0, 0.0, 1.0, 0.0, 0.05);
                }
                if (r.nextInt(6) == 0) {
                    w.spawnParticle(Particle.LAVA, body, 1, 0.5, 0.6, 0.5, 0);
                }
            }
            case WIND -> {
                for (int ring = 0; ring < 2; ring++) {
                    double dir = ring == 0 ? 1 : -1;
                    for (int k = 0; k < 3; k++) {
                        double a = t * 1.4 * dir + k * (Math.PI * 2 / 3);
                        w.spawnParticle(Particle.CLOUD, feet.getX() + Math.cos(a) * 0.85, feet.getY() + 0.5 + ring * 0.9,
                                feet.getZ() + Math.sin(a) * 0.85, 1, 0, 0, 0, 0);
                    }
                }
                double h = (tick * 0.08) % 2.0;
                double a2 = t * 2;
                w.spawnParticle(Particle.WHITE_ASH, feet.getX() + Math.cos(a2) * 0.6, feet.getY() + h,
                        feet.getZ() + Math.sin(a2) * 0.6, 2, 0.05, 0.05, 0.05, 0);
                if (r.nextInt(12) == 0) {
                    w.spawnParticle(Particle.POOF, feet.clone().add(0, 0.1, 0), 3, 0.4, 0.05, 0.4, 0.02);
                }
            }
            case SHADOW -> {
                // Trois tentacules d'ombre qui montent en tournant
                for (int k = 0; k < 3; k++) {
                    double h = (tick * 0.05 + k * 0.7) % 2.0;
                    double a = t * 0.7 + k * (Math.PI * 2 / 3) + h;
                    double rad = 1.0 - 0.3 * h;
                    Location l = feet.clone().add(Math.cos(a) * rad, h, Math.sin(a) * rad);
                    w.spawnParticle(Particle.SQUID_INK, l, 1, 0, 0, 0, 0);
                    w.spawnParticle(Particle.SMOKE, l, 1, 0.02, 0.02, 0.02, 0);
                }
                for (int k = 0; k < 4; k++) {
                    double a = -t * 1.1 + k * Math.PI / 2;
                    w.spawnParticle(Particle.DUST_COLOR_TRANSITION, feet.getX() + Math.cos(a) * 0.8, feet.getY() + 1.0,
                            feet.getZ() + Math.sin(a) * 0.8, 1, 0, 0, 0, 0, VOID);
                }
                w.spawnParticle(Particle.PORTAL, feet.clone().add(0, 0.3, 0), 2, 0.5, 0.1, 0.5, 0.3);
            }
            case EARTH -> {
                Material[] rocks = {Material.STONE, Material.DEEPSLATE, Material.MOSS_BLOCK};
                for (int k = 0; k < 3; k++) {
                    double a = t * 0.9 + k * (Math.PI * 2 / 3);
                    double y = 0.8 + Math.sin(t + k * 2) * 0.5;
                    w.spawnParticle(Particle.BLOCK, feet.getX() + Math.cos(a) * 1.2, feet.getY() + y,
                            feet.getZ() + Math.sin(a) * 1.2, 2, 0.04, 0.04, 0.04, 0, rocks[k].createBlockData());
                }
                if (r.nextInt(2) == 0) {
                    w.spawnParticle(Particle.BLOCK, feet.clone().add(0, 0.1, 0), 3, 0.7, 0.05, 0.7, 0,
                            Material.COARSE_DIRT.createBlockData());
                }
                if (r.nextInt(10) == 0) {
                    w.spawnParticle(Particle.FALLING_DUST, body.getX() + r.nextDouble(-0.5, 0.5), feet.getY() + 2.3,
                            body.getZ() + r.nextDouble(-0.5, 0.5), 1, 0, 0, 0, 0, Material.STONE.createBlockData());
                }
            }
            case POISON -> {
                if (r.nextInt(2) == 0) {
                    w.spawnParticle(Particle.COMPOSTER, body.getX() + r.nextDouble(-0.5, 0.5),
                            feet.getY() + r.nextDouble(0.2, 2.0), body.getZ() + r.nextDouble(-0.5, 0.5), 1, 0, 0, 0, 0);
                }
                if (r.nextInt(5) == 0) {
                    w.spawnParticle(Particle.SNEEZE, body.getX() + r.nextDouble(-0.6, 0.6),
                            feet.getY() + r.nextDouble(0.2, 1.6), body.getZ() + r.nextDouble(-0.6, 0.6), 1, 0, 0, 0, 0);
                }
                for (int k = 0; k < 4; k++) {
                    double a = -t * 1.2 + k * Math.PI / 2;
                    w.spawnParticle(Particle.DUST_COLOR_TRANSITION, feet.getX() + Math.cos(a) * 0.85, feet.getY() + 0.9,
                            feet.getZ() + Math.sin(a) * 0.85, 1, 0, 0, 0, 0, TOXIC);
                }
                if (r.nextInt(4) == 0) {
                    w.spawnParticle(Particle.ITEM_SLIME, feet.clone().add(0, 0.1, 0), 2, 0.5, 0.05, 0.5, 0);
                }
            }
            case LIGHT -> {
                // Halo au-dessus de la tête
                for (int k = 0; k < 10; k++) {
                    double a = t * 0.8 + k * (Math.PI / 5);
                    w.spawnParticle(Particle.DUST, feet.getX() + Math.cos(a) * 0.45, feet.getY() + 2.35,
                            feet.getZ() + Math.sin(a) * 0.45, 1, 0, 0, 0, 0, HALO);
                }
                // Rayons qui tournent autour du corps
                if (tick % 2 == 0) {
                    for (int k = 0; k < 4; k++) {
                        double a = t * 0.4 + k * Math.PI / 2;
                        Location to = body.clone().add(Math.cos(a) * 1.3, 0.3, Math.sin(a) * 1.3);
                        Effects.line(w, body, to, 0.3, Particle.END_ROD, null);
                    }
                }
                if (r.nextInt(3) == 0) {
                    w.spawnParticle(Particle.GLOW, body, 2, 0.5, 0.7, 0.5, 0.02);
                }
            }
            case PHOENIX -> wings(w, feet, body, t, tick, r);
        }
    }

    /** Un arc électrique qui zigzague près du corps. */
    private static void arc(World w, Location body, ThreadLocalRandom r) {
        Location cur = body.clone().add(r.nextDouble(-0.5, 0.5), r.nextDouble(-0.6, 0.8), r.nextDouble(-0.5, 0.5));
        for (int i = 0; i < 4; i++) {
            Location next = cur.clone().add(r.nextDouble(-0.35, 0.35), r.nextDouble(-0.35, 0.35), r.nextDouble(-0.35, 0.35));
            Effects.line(w, cur, next, 0.12, Particle.ELECTRIC_SPARK, null);
            cur = next;
        }
    }

    /** Deux ailes de flammes dans le dos, qui battent doucement. */
    private static void wings(World w, Location feet, Location body, double t, int tick, ThreadLocalRandom r) {
        double yaw = Math.toRadians(feet.getYaw());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double rx = -Math.cos(yaw);
        double rz = -Math.sin(yaw);
        double flap = 1.0 + 0.25 * Math.sin(t * 0.9);

        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i <= 6; i++) {
                double u = i / 6.0;
                double lateral = (0.25 + u * 1.5) * flap * side;
                double up = 1.15 + Math.sin(u * Math.PI) * 0.55 - u * 0.25;
                double back = 0.35 + u * 0.25;
                Location l = feet.clone().add(rx * lateral - fx * back, up, rz * lateral - fz * back);
                w.spawnParticle(i % 2 == 0 ? Particle.FLAME : Particle.SMALL_FLAME, l, 1, 0.02, 0.02, 0.02, 0);
            }
            if (tick % 3 == 0) {
                // Plumes : du dos jusqu'au bout de l'aile
                double lateral = 1.75 * flap * side;
                Location shoulder = feet.clone().add(-fx * 0.35, 1.2, -fz * 0.35);
                Location tip = feet.clone().add(rx * lateral - fx * 0.6, 1.0, rz * lateral - fz * 0.6);
                Effects.line(w, shoulder, tip, 0.3, Particle.SMALL_FLAME, null);
            }
        }
        if (r.nextInt(6) == 0) {
            w.spawnParticle(Particle.LAVA, body, 1, 0.5, 0.6, 0.5, 0);
        }
        if (r.nextInt(8) == 0) {
            w.spawnParticle(Particle.FIREWORK, body.getX(), feet.getY() + 2.0, body.getZ(), 0, 0.0, -1.0, 0.0, 0.05);
        }
    }
}
