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
        Abilities abilities = new Abilities(this, manager);
        InfoGui gui = new InfoGui(manager);

        getServer().getPluginManager().registerEvents(new PowerListener(this, manager), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this, manager, abilities), this);
        getServer().getPluginManager().registerEvents(new AbilityListener(this, manager, abilities), this);
        getServer().getPluginManager().registerEvents(new InfoGuiListener(this, gui), this);

        PluginCommand cmd = getCommand("power");
        if (cmd != null) {
            PowerCommand handler = new PowerCommand(this, manager, gui);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        if (getConfig().getBoolean("recipes.enabled", true)) {
            Recipes.register(this, manager);
        }

        getServer().getScheduler().runTaskTimer(this, manager::tick, 20L, 20L);
        new Effects(this, manager).start();

        for (Player p : getServer().getOnlinePlayers()) {
            manager.refresh(p);
        }
    }

    @Override
    public void onDisable() {
        Recipes.unregister(this);
    }

    public PowerManager manager() {
        return manager;
    }
}
