package com.power;

import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/** Effets visuels : aura autour des joueurs qui ont un pouvoir, et étincelles quand ils frappent. */
public final class Effects {

    private final PowerPlugin plugin;
    private final PowerManager manager;

    public Effects(PowerPlugin plugin, PowerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 10L, 5L);
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("aura.enabled", true)) return;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            PowerType type = manager.getPower(p);
            if (type == null || p.getGameMode() == GameMode.SPECTATOR) continue;
            if (p.hasPotionEffect(PotionEffectType.INVISIBILITY)) continue;
            aura(p, type);
        }
    }

    private void aura(Player p, PowerType type) {
        Location c = p.getLocation().add(0, 1, 0);
        World w = p.getWorld();
        switch (type) {
            case LIGHTNING -> w.spawnParticle(Particle.ELECTRIC_SPARK, c, 2, 0.4, 0.7, 0.4, 0.02);
            case ICE -> w.spawnParticle(Particle.SNOWFLAKE, c, 2, 0.4, 0.7, 0.4, 0.01);
            case BLOOD -> w.spawnParticle(Particle.DUST, c, 2, 0.4, 0.7, 0.4, 0,
                    new Particle.DustOptions(Color.fromRGB(0x9B0000), 1.0f));
            case FIRE -> w.spawnParticle(Particle.SMALL_FLAME, c, 2, 0.4, 0.7, 0.4, 0.01);
            case WIND -> w.spawnParticle(Particle.CLOUD, c, 1, 0.4, 0.7, 0.4, 0.01);
            case SHADOW -> w.spawnParticle(Particle.SMOKE, c, 2, 0.4, 0.7, 0.4, 0.01);
            case EARTH -> w.spawnParticle(Particle.BLOCK, c, 2, 0.4, 0.7, 0.4, 0,
                    Material.COARSE_DIRT.createBlockData());
            case POISON -> w.spawnParticle(Particle.ITEM_SLIME, c, 2, 0.4, 0.7, 0.4, 0);
            case LIGHT -> w.spawnParticle(Particle.END_ROD, c, 1, 0.4, 0.7, 0.4, 0.01);
            case PHOENIX -> w.spawnParticle(Particle.FLAME, c, 2, 0.4, 0.7, 0.4, 0.01);
        }
    }

    /** Étincelles sur la cible quand un porteur de pouvoir la frappe. */
    public static void hit(LivingEntity victim, PowerType type) {
        Location c = victim.getLocation().add(0, 1, 0);
        World w = victim.getWorld();
        switch (type) {
            case LIGHTNING -> w.spawnParticle(Particle.ELECTRIC_SPARK, c, 15, 0.3, 0.5, 0.3, 0.1);
            case ICE -> w.spawnParticle(Particle.SNOWFLAKE, c, 12, 0.3, 0.5, 0.3, 0.05);
            case BLOOD -> w.spawnParticle(Particle.DUST, c, 10, 0.3, 0.5, 0.3, 0,
                    new Particle.DustOptions(Color.fromRGB(0x9B0000), 1.4f));
            case FIRE -> w.spawnParticle(Particle.FLAME, c, 12, 0.3, 0.5, 0.3, 0.05);
            case POISON -> w.spawnParticle(Particle.ITEM_SLIME, c, 10, 0.3, 0.5, 0.3, 0);
            default -> { }
        }
    }
}
