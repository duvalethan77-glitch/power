package com.power;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Il n'existe qu'un exemplaire de chaque pouvoir. Ce registre retient où il est :
 * libre, en rituel, au sol, en possession d'un joueur (potion) ou porté par un joueur.
 * Il est sauvegardé dans plugins/Power/registry.yml.
 */
public final class PowerRegistry {

    public enum State { FREE, RITUAL, GROUND, CARRIED, HELD }

    public static final class Entry {
        public State state = State.FREE;
        public UUID holder;          // HELD : le porteur
        public UUID itemId;          // RITUAL / GROUND : l'entité de la potion
        public String world;         // RITUAL / GROUND : dernière position connue
        public double x;
        public double y;
        public double z;
        public long startMs;         // RITUAL
        public long endMs;           // RITUAL
        public int announced;        // RITUAL : annonces de coordonnées déjà faites
        public boolean fromRitual;   // GROUND : tombée à la fin d'un rituel
    }

    private final PowerPlugin plugin;
    private final File file;
    private final Map<PowerType, Entry> entries = new EnumMap<>(PowerType.class);

    public PowerRegistry(PowerPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "registry.yml");
        for (PowerType type : PowerType.values()) {
            entries.put(type, new Entry());
        }
        load();
    }

    public Entry get(PowerType type) {
        return entries.get(type);
    }

    public boolean isFree(PowerType type) {
        return get(type).state == State.FREE;
    }

    public boolean heldByOther(PowerType type, UUID who) {
        Entry e = get(type);
        return e.state == State.HELD && e.holder != null && !e.holder.equals(who);
    }

    public boolean isHeldBy(PowerType type, UUID who) {
        Entry e = get(type);
        return e.state == State.HELD && who.equals(e.holder);
    }

    // ------------------------------------------------------------------
    // Changements d'état
    // ------------------------------------------------------------------

    public void setFree(PowerType type) {
        entries.put(type, new Entry());
        save();
    }

    public void setCarried(PowerType type) {
        Entry e = new Entry();
        e.state = State.CARRIED;
        entries.put(type, e);
        save();
    }

    public void setHeld(PowerType type, UUID holder) {
        Entry e = new Entry();
        e.state = State.HELD;
        e.holder = holder;
        entries.put(type, e);
        save();
    }

    public void setGround(PowerType type, UUID item, Location loc) {
        Entry e = new Entry();
        e.state = State.GROUND;
        e.itemId = item;
        place(e, loc);
        entries.put(type, e);
        save();
    }

    public void setRitual(PowerType type, UUID item, Location loc, long startMs, long endMs) {
        Entry e = new Entry();
        e.state = State.RITUAL;
        e.itemId = item;
        e.startMs = startMs;
        e.endMs = endMs;
        place(e, loc);
        entries.put(type, e);
        save();
    }

    /** La potion du rituel tombe au sol. */
    public void toGround(PowerType type) {
        Entry e = get(type);
        e.state = State.GROUND;
        e.fromRitual = true;
        save();
    }

    /** Met à jour la position en mémoire (pas de sauvegarde à chaque seconde). */
    public void touch(PowerType type, Location loc) {
        place(get(type), loc);
    }

    private void place(Entry e, Location loc) {
        if (loc.getWorld() != null) {
            e.world = loc.getWorld().getName();
        }
        e.x = loc.getX();
        e.y = loc.getY();
        e.z = loc.getZ();
    }

    // ------------------------------------------------------------------
    // Texte d'état
    // ------------------------------------------------------------------

    public String describe(CommandSender sender, PowerType type) {
        Entry e = get(type);
        switch (e.state) {
            case RITUAL:
                long left = (e.endMs - System.currentTimeMillis()) / 1000L;
                return Lang.tr(sender, "st_ritual", Lang.clock(left));
            case GROUND:
                return Lang.tr(sender, "st_ground");
            case CARRIED:
                return Lang.tr(sender, "st_carried");
            case HELD:
                String name = "?";
                if (e.holder != null) {
                    OfflinePlayer holder = Bukkit.getOfflinePlayer(e.holder);
                    if (holder.getName() != null) name = holder.getName();
                }
                return Lang.tr(sender, "st_held", name);
            default:
                return Lang.tr(sender, "st_free");
        }
    }

    // ------------------------------------------------------------------
    // Sauvegarde
    // ------------------------------------------------------------------

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (PowerType type : PowerType.values()) {
            Entry e = entries.get(type);
            String p = type.name() + ".";
            yaml.set(p + "state", e.state.name());
            if (e.holder != null) yaml.set(p + "holder", e.holder.toString());
            if (e.itemId != null) yaml.set(p + "item", e.itemId.toString());
            if (e.world != null) yaml.set(p + "world", e.world);
            yaml.set(p + "x", e.x);
            yaml.set(p + "y", e.y);
            yaml.set(p + "z", e.z);
            yaml.set(p + "start", e.startMs);
            yaml.set(p + "end", e.endMs);
            yaml.set(p + "announced", e.announced);
            yaml.set(p + "from-ritual", e.fromRitual);
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Impossible de sauvegarder registry.yml : " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (PowerType type : PowerType.values()) {
            ConfigurationSection s = yaml.getConfigurationSection(type.name());
            if (s == null) continue;

            Entry e = new Entry();
            try {
                e.state = State.valueOf(s.getString("state", "FREE"));
            } catch (IllegalArgumentException ex) {
                e.state = State.FREE;
            }
            e.holder = uuid(s.getString("holder"));
            e.itemId = uuid(s.getString("item"));
            e.world = s.getString("world");
            e.x = s.getDouble("x");
            e.y = s.getDouble("y");
            e.z = s.getDouble("z");
            e.startMs = s.getLong("start");
            e.endMs = s.getLong("end");
            e.announced = s.getInt("announced");
            e.fromRitual = s.getBoolean("from-ritual");
            entries.put(type, e);
        }
    }

    private UUID uuid(String s) {
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
