package com.power;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Alliés de chaque joueur (/power ally) : ils voient à travers la fumée de Smoke. */
public final class Allies {

    private final PowerPlugin plugin;
    private final File file;
    private final Map<UUID, Set<UUID>> map = new HashMap<>();

    public Allies(PowerPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "allies.yml");
        load();
    }

    public boolean add(UUID owner, UUID ally) {
        boolean changed = map.computeIfAbsent(owner, k -> new LinkedHashSet<>()).add(ally);
        if (changed) save();
        return changed;
    }

    public boolean remove(UUID owner, UUID ally) {
        Set<UUID> set = map.get(owner);
        boolean changed = set != null && set.remove(ally);
        if (changed) save();
        return changed;
    }

    public Set<UUID> list(UUID owner) {
        return map.getOrDefault(owner, Set.of());
    }

    /** Le propriétaire est toujours son propre allié. */
    public boolean isAlly(UUID owner, UUID other) {
        if (owner.equals(other)) return true;
        Set<UUID> set = map.get(owner);
        return set != null && set.contains(other);
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Set<UUID>> e : map.entrySet()) {
            List<String> ids = new ArrayList<>();
            for (UUID id : e.getValue()) ids.add(id.toString());
            yaml.set(e.getKey().toString(), ids);
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Impossible de sauvegarder allies.yml : " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                UUID owner = UUID.fromString(key);
                Set<UUID> set = new LinkedHashSet<>();
                for (String s : yaml.getStringList(key)) {
                    set.add(UUID.fromString(s));
                }
                map.put(owner, set);
            } catch (IllegalArgumentException ignored) {
                // entrée invalide : on l'ignore
            }
        }
    }
}
