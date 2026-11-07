package com.power;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chaque pouvoir existe en un nombre limité d'exemplaires (2 par défaut, voir powers.copies).
 * Ce registre suit chaque exemplaire : libre, en rituel, au sol, en possession d'un joueur
 * (potion) ou porté par un joueur. Sauvegardé dans plugins/Power/registry.yml.
 */
public final class PowerRegistry {

    public enum State { FREE, RITUAL, GROUND, CARRIED, HELD }

    /** Un exemplaire d'un pouvoir. Modifié sur place : on peut le garder en référence. */
    public static final class Entry {
        public final PowerType type;
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

        Entry(PowerType type) {
            this.type = type;
        }

        void reset() {
            state = State.FREE;
            holder = null;
            itemId = null;
            world = null;
            x = 0;
            y = 0;
            z = 0;
            startMs = 0;
            endMs = 0;
            announced = 0;
            fromRitual = false;
        }
    }

    private final PowerPlugin plugin;
    private final File file;
    private final int copies;
    private final Map<PowerType, List<Entry>> slots = new EnumMap<>(PowerType.class);

    public PowerRegistry(PowerPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "registry.yml");
        this.copies = Math.max(1, Math.min(5, plugin.getConfig().getInt("powers.copies", 2)));
        for (PowerType type : PowerType.values()) {
            List<Entry> list = new ArrayList<>();
            for (int i = 0; i < copies; i++) {
                list.add(new Entry(type));
            }
            slots.put(type, list);
        }
        load();
    }

    public int copies() {
        return copies;
    }

    public List<Entry> list(PowerType type) {
        return slots.get(type);
    }

    public List<Entry> all() {
        List<Entry> out = new ArrayList<>();
        for (PowerType type : PowerType.values()) {
            out.addAll(slots.get(type));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Recherches
    // ------------------------------------------------------------------

    public Entry first(PowerType type, State state) {
        for (Entry e : slots.get(type)) {
            if (e.state == state) return e;
        }
        return null;
    }

    public Entry entryHeldBy(PowerType type, UUID who) {
        for (Entry e : slots.get(type)) {
            if (e.state == State.HELD && who.equals(e.holder)) return e;
        }
        return null;
    }

    public Entry entryByItem(PowerType type, UUID item) {
        for (Entry e : slots.get(type)) {
            if (e.itemId != null && e.itemId.equals(item)
                    && (e.state == State.RITUAL || e.state == State.GROUND)) {
                return e;
            }
        }
        return null;
    }

    /** Il reste un exemplaire que l'on peut encore crafter ou donner. */
    public boolean isFree(PowerType type) {
        return first(type, State.FREE) != null;
    }

    public boolean isHeldBy(PowerType type, UUID who) {
        return entryHeldBy(type, who) != null;
    }

    /** Vrai si tous les exemplaires sont déjà portés par d'autres joueurs (cette potion est en trop). */
    public boolean heldByOther(PowerType type, UUID who) {
        if (isHeldBy(type, who)) return false;
        int held = 0;
        for (Entry e : slots.get(type)) {
            if (e.state == State.HELD) held++;
        }
        return held >= copies;
    }

    // ------------------------------------------------------------------
    // Changements d'état
    // ------------------------------------------------------------------

    public void free(Entry e) {
        e.reset();
        save();
    }

    /** Le joueur n'a plus ce pouvoir (il n'y a pas de potion) : l'exemplaire est libre. */
    public void clearHolder(PowerType type, UUID holder) {
        Entry e = entryHeldBy(type, holder);
        if (e != null) free(e);
    }

    /** L'exemplaire devient une potion détenue (par le porteur précédent, ou un exemplaire libre). */
    public Entry setCarried(PowerType type, UUID previousHolder) {
        Entry e = previousHolder != null ? entryHeldBy(type, previousHolder) : null;
        if (e == null) e = first(type, State.FREE);
        if (e == null) return null;
        e.reset();
        e.state = State.CARRIED;
        save();
        return e;
    }

    public void markCarried(Entry e) {
        e.reset();
        e.state = State.CARRIED;
        save();
    }

    public Entry setHeld(PowerType type, UUID holder) {
        Entry e = entryHeldBy(type, holder);
        if (e != null) return e;
        e = first(type, State.CARRIED);
        if (e == null) e = first(type, State.GROUND);
        if (e == null) e = first(type, State.FREE);
        if (e == null) return null;
        e.reset();
        e.state = State.HELD;
        e.holder = holder;
        save();
        return e;
    }

    public Entry setGround(PowerType type, UUID item, Location loc) {
        Entry e = entryByItem(type, item);
        if (e != null && e.state == State.RITUAL) return e;
        if (e == null) e = first(type, State.CARRIED);
        if (e == null) e = first(type, State.FREE);
        if (e == null) return null;
        if (e.state != State.GROUND || !item.equals(e.itemId)) {
            e.reset();
            e.state = State.GROUND;
            e.itemId = item;
        }
        place(e, loc);
        save();
        return e;
    }

    public Entry setRitual(PowerType type, UUID item, Location loc, long startMs, long endMs) {
        Entry e = first(type, State.FREE);
        if (e == null) return null;
        e.reset();
        e.state = State.RITUAL;
        e.itemId = item;
        e.startMs = startMs;
        e.endMs = endMs;
        place(e, loc);
        save();
        return e;
    }

    /** La potion du rituel tombe au sol. */
    public void toGround(Entry e) {
        e.state = State.GROUND;
        e.fromRitual = true;
        save();
    }

    /** Met à jour la position en mémoire (pas de sauvegarde à chaque seconde). */
    public void touch(Entry e, Location loc) {
        place(e, loc);
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

    public String describe(CommandSender sender, Entry e) {
        boolean fr = Lang.french(sender);
        switch (e.state) {
            case RITUAL:
                long left = (e.endMs - System.currentTimeMillis()) / 1000L;
                return Lang.tr(fr, "st_ritual", Lang.clock(left), Lang.world(fr, e.world));
            case GROUND:
                return Lang.tr(fr, "st_ground", Lang.world(fr, e.world));
            case CARRIED:
                return Lang.tr(fr, "st_carried");
            case HELD:
                String name = "?";
                if (e.holder != null) {
                    OfflinePlayer holder = Bukkit.getOfflinePlayer(e.holder);
                    if (holder.getName() != null) name = holder.getName();
                }
                return Lang.tr(fr, "st_held", name);
            default:
                return Lang.tr(fr, "st_free");
        }
    }

    /** Un texte par exemplaire, séparés par « | ». */
    public String describe(CommandSender sender, PowerType type) {
        StringBuilder out = new StringBuilder();
        for (Entry e : slots.get(type)) {
            if (out.length() > 0) out.append("  |  ");
            out.append(describe(sender, e));
        }
        return out.toString();
    }

    // ------------------------------------------------------------------
    // Sauvegarde
    // ------------------------------------------------------------------

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (PowerType type : PowerType.values()) {
            List<Entry> list = slots.get(type);
            for (int i = 0; i < list.size(); i++) {
                Entry e = list.get(i);
                String p = type.name() + "." + i + ".";
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
            List<Entry> list = slots.get(type);

            if (s.contains("state")) {
                // Ancien format (un seul exemplaire)
                read(s, list.get(0));
                continue;
            }
            for (int i = 0; i < list.size(); i++) {
                ConfigurationSection c = s.getConfigurationSection(String.valueOf(i));
                if (c != null) read(c, list.get(i));
            }
        }
    }

    private void read(ConfigurationSection s, Entry e) {
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
