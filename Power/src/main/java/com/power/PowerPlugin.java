package com.power;

import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PowerPlugin extends JavaPlugin {

    private PowerManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new PowerManager(this);

        getServer().getPluginManager().registerEvents(new PowerListener(manager), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this, manager), this);

        PluginCommand cmd = getCommand("power");
        if (cmd != null) {
            PowerCommand handler = new PowerCommand(this, manager);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        getServer().getScheduler().runTaskTimer(this, manager::tick, 20L, 20L);

        for (Player p : getServer().getOnlinePlayers()) {
            manager.refresh(p);
        }
    }

    public PowerManager manager() {
        return manager;
    }
}
