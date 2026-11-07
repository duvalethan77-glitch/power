package com.power;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;

/** Formes de particules réutilisables : explosion, sphère, hélice, colonne, onde de choc, éclair. */
final class Fx {

    interface RingFn {
        void ring(World w, Location center, double radius);
    }

    private Fx() {}

    /** Les particules partent dans toutes les directions (ne marche pas avec DUST). */
    static void burst(World w, Location c, Particle particle, int count, double speed, Object data) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            double theta = r.nextDouble() * Math.PI * 2;
            double phi = Math.acos(2 * r.nextDouble() - 1);
            double dx = Math.sin(phi) * Math.cos(theta);
            double dy = Math.cos(phi);
            double dz = Math.sin(phi) * Math.sin(theta);
            w.spawnParticle(particle, c, 0, dx, dy, dz, speed, data);
        }
    }

    /** Sphère de particules fixes. */
    static void sphere(World w, Location c, double radius, int points, Particle particle, Object data) {
        for (int i = 0; i < points; i++) {
            double y = 1 - 2 * (i + 0.5) / points;
            double rr = Math.sqrt(Math.max(0, 1 - y * y));
            double a = i * 2.399963;
            w.spawnParticle(particle, c.getX() + Math.cos(a) * rr * radius, c.getY() + y * radius,
                    c.getZ() + Math.sin(a) * rr * radius, 1, 0, 0, 0, 0, data);
        }
    }

    /** Hélice verticale. */
    static void helix(World w, Location base, double radius, double height, double turns, int points,
                      double phase, Particle particle, Object data) {
        for (int i = 0; i < points; i++) {
            double u = i / (double) Math.max(1, points - 1);
            double a = phase + u * turns * Math.PI * 2;
            w.spawnParticle(particle, base.getX() + Math.cos(a) * radius, base.getY() + u * height,
                    base.getZ() + Math.sin(a) * radius, 1, 0, 0, 0, 0, data);
        }
    }

    /** Tornade : hélice qui s'évase vers le haut. */
    static void tornado(World w, Location base, double height, double topRadius, double phase,
                        Particle particle, Object data) {
        int points = 26;
        for (int i = 0; i < points; i++) {
            double u = i / (double) (points - 1);
            double a = phase + u * 3 * Math.PI * 2;
            double rad = 0.2 + topRadius * u;
            w.spawnParticle(particle, base.getX() + Math.cos(a) * rad, base.getY() + u * height,
                    base.getZ() + Math.sin(a) * rad, 1, 0, 0, 0, 0, data);
        }
    }

    /** Colonne verticale. */
    static void column(World w, Location base, double height, double step, double spread,
                       Particle particle, int count, Object data) {
        for (double y = 0; y <= height; y += step) {
            w.spawnParticle(particle, base.getX(), base.getY() + y, base.getZ(), count, spread, 0.0, spread, 0, data);
        }
    }

    /** Coque cylindrique (cage autour d'une cible). */
    static void shell(World w, Location base, double radius, double height, int points, Particle particle, Object data) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < points; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            w.spawnParticle(particle, base.getX() + Math.cos(a) * radius, base.getY() + r.nextDouble() * height,
                    base.getZ() + Math.sin(a) * radius, 1, 0, 0, 0, 0, data);
        }
    }

    /** Anneau qui grandit à chaque tick jusqu'à maxRadius. */
    static void wave(PowerPlugin plugin, World w, Location center, double maxRadius, double step, RingFn fn) {
        Location c = center.clone();
        new BukkitRunnable() {
            double radius = step;

            @Override
            public void run() {
                if (radius > maxRadius) {
                    cancel();
                    return;
                }
                fn.ring(w, c, radius);
                radius += step;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Éclair qui zigzague entre deux points. */
    static void bolt(World w, Location from, Location to) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double length = from.distance(to);
        int segs = Math.max(4, (int) (length / 1.5));
        Location prev = from.clone();
        for (int i = 1; i <= segs; i++) {
            double u = i / (double) segs;
            Location next = from.clone().add(to.clone().subtract(from).toVector().multiply(u));
            if (i < segs) {
                double j = 0.9 * (1 - u) + 0.15;
                next.add(r.nextDouble(-j, j), 0, r.nextDouble(-j, j));
            }
            Effects.line(w, prev, next, 0.2, Particle.ELECTRIC_SPARK, null);
            w.spawnParticle(Particle.END_ROD, next, 1, 0, 0, 0, 0);
            prev = next;
        }
    }
}
