package com.power;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

/**
 * Envoie les particules en mode « forcé » : elles s'affichent même si le joueur a réglé ses
 * particules sur « Réduites » ou « Minimales », et de plus loin (config : particles.force).
 */
final class Pt {

    static volatile boolean force = true;

    private Pt() {}

    static void init(PowerPlugin plugin) {
        force = plugin.getConfig().getBoolean("particles.force", true);
    }

    private static double num(Object o) {
        return ((Number) o).doubleValue();
    }

    /**
     * Mêmes arguments que World#spawnParticle, après la particule :
     * (Location | x, y, z), [count], [offsetX, offsetY, offsetZ], [extra], [data]
     */
    static void spawn(World w, Particle particle, Object... a) {
        int i = 0;
        double x;
        double y;
        double z;
        if (a[0] instanceof Location l) {
            x = l.getX();
            y = l.getY();
            z = l.getZ();
            i = 1;
        } else {
            x = num(a[0]);
            y = num(a[1]);
            z = num(a[2]);
            i = 3;
        }
        int count = 1;
        if (a.length > i && a[i] instanceof Number n) {
            count = n.intValue();
            i++;
        }
        double ox = 0;
        double oy = 0;
        double oz = 0;
        if (a.length >= i + 3 && a[i] instanceof Number && a[i + 1] instanceof Number && a[i + 2] instanceof Number) {
            ox = num(a[i]);
            oy = num(a[i + 1]);
            oz = num(a[i + 2]);
            i += 3;
        }
        double extra = 0;
        if (a.length > i && a[i] instanceof Number) {
            extra = num(a[i]);
            i++;
        }
        Object data = a.length > i ? a[i] : null;
        w.spawnParticle(particle, x, y, z, count, ox, oy, oz, extra, data, force);
    }
}
