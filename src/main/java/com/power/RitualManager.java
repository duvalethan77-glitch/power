package com.power;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Le rituel : crafter une potion de pouvoir la fait flotter dans les airs pendant 20 minutes à
 * l'endroit du craft, avec des coordonnées révélées toutes les 2 min 30 (Z, puis Y, puis X).
 * À la fin elle tombe : le premier qui la ramasse gagne le pouvoir.
 */
public final class RitualManager implements Listener {

    /** Barres de boss d'un pouvoir : une en français, une en anglais. */
    private static final class Bars {
        final BossBar[] bar = new BossBar[2];
        @SuppressWarnings("unchecked")
        final Set<UUID>[] shown = new Set[] {new HashSet<UUID>(), new HashSet<UUID>()};

        Bars() {
            for (int i = 0; i < 2; i++) {
                bar[i] = BossBar.bossBar(Component.empty(), 1.0f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
            }
        }
    }

    private final PowerPlugin plugin;
    private final PowerManager manager;
    private final PowerRegistry registry;

    private final Map<PowerRegistry.Entry, Bars> bars = new HashMap<>();
    private final Map<PowerRegistry.Entry, Integer> missing = new HashMap<>();
    private boolean spawning;
    private float spin;
    private int fastCounter;

    public RitualManager(PowerPlugin plugin, PowerManager manager, PowerRegistry registry) {
        this.plugin = plugin;
        this.manager = manager;
        this.registry = registry;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::slowTick, 20L, 20L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::fastTick, 20L, 2L);
    }

    /** Retire toutes les barres (arrêt ou rechargement du plugin). */
    public void shutdown() {
        for (PowerRegistry.Entry e : registry.all()) {
            hideBars(e);
        }
    }

    // ------------------------------------------------------------------
    // Craft : lance le rituel
    // ------------------------------------------------------------------

    @EventHandler
    public void onPrepare(PrepareItemCraftEvent e) {
        if (!(e.getRecipe() instanceof Keyed k)) return;
        PowerType type = Recipes.typeOf(plugin, k);
        if (type != null && !registry.isFree(type)) {
            e.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(CraftItemEvent e) {
        if (!(e.getRecipe() instanceof Keyed k)) return;
        PowerType type = Recipes.typeOf(plugin, k);
        if (type == null) return;

        // La potion n'est jamais donnée directement : le craft lance le rituel
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;

        if (!registry.isFree(type)) {
            p.sendMessage(Component.text(Lang.tr(p, "craft_blocked"), NamedTextColor.RED));
            return;
        }

        CraftingInventory inv = e.getInventory();
        ItemStack[] matrix = inv.getMatrix();
        for (int i = 0; i < matrix.length; i++) {
            ItemStack it = matrix[i];
            if (it == null) continue;
            if (it.getAmount() <= 1) {
                matrix[i] = null;
            } else {
                it.setAmount(it.getAmount() - 1);
            }
        }
        inv.setMatrix(matrix);
        inv.setResult(null);

        Location table = inv.getLocation() != null ? inv.getLocation() : p.getLocation();
        double height = Math.max(1.5, plugin.getConfig().getDouble("ritual.hover-height", 4.0));
        Location hover = table.getBlock().getLocation().add(0.5, height, 0.5);
        int maxY = hover.getWorld().getMaxHeight() - 2;
        if (hover.getWorld().getEnvironment() == World.Environment.NETHER) {
            maxY = Math.min(maxY, 120); // sous le plafond de bedrock
        }
        if (hover.getY() > maxY) hover.setY(maxY);

        startRitual(type, p, hover);
        plugin.getServer().getScheduler().runTask(plugin, p::updateInventory);
    }

    private void startRitual(PowerType type, Player starter, Location hover) {
        long now = System.currentTimeMillis();
        long seconds = Math.max(10L, (long) (plugin.getConfig().getDouble("ritual.duration-minutes", 20) * 60));

        Item item;
        spawning = true;
        try {
            item = manager.spawnPotionEntity(hover, type, true);
        } finally {
            spawning = false;
        }
        if (item == null) return;

        PowerRegistry.Entry entry = registry.setRitual(type, item.getUniqueId(), hover, now, now + seconds * 1000L);
        if (entry == null) {
            manager.removeDisplays(item);
            item.remove();
            return;
        }
        missing.remove(entry);

        World w = hover.getWorld();
        w.strikeLightningEffect(hover.clone().subtract(0, plugin.getConfig().getDouble("ritual.hover-height", 4.0), 0));
        w.spawnParticle(Particle.FIREWORK, hover, 60, 0.6, 0.6, 0.6, 0.15);
        w.playSound(hover, Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 0.6f);

        starter.sendMessage(Component.text(
                Lang.tr(starter, "ritual_started_self", type.display, Lang.duration(seconds))
                        + " [" + Lang.world(Lang.french(starter), w.getName()) + "]", type.textColor));

        for (Player p : Bukkit.getOnlinePlayers()) {
            Component title = Component.text(Lang.tr(p, "ritual_title", type.display), type.textColor, TextDecoration.BOLD);
            String worldLabel = Lang.world(Lang.french(p), w.getName());
            Component sub = Component.text(Lang.tr(p, "ritual_started_all", starter.getName(), type.display)
                    + " [" + worldLabel + "]", NamedTextColor.GRAY);
            p.showTitle(Title.title(title, sub, Title.Times.times(
                    Duration.ofMillis(400), Duration.ofSeconds(4), Duration.ofMillis(1000))));
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.4f);
            p.sendMessage(Component.text("✦ ", type.textColor)
                    .append(Component.text(Lang.tr(p, "ritual_started_all", starter.getName(), type.display)
                            + " [" + worldLabel + "]", NamedTextColor.WHITE)));
        }
        plugin.getLogger().info("Rituel lancé : " + type.display + " par " + starter.getName());
        slowTick();
    }

    // ------------------------------------------------------------------
    // Tick lent (1 s) : annonces, chute, barres de boss, suivi au sol
    // ------------------------------------------------------------------

    private void slowTick() {
        long now = System.currentTimeMillis();
        for (PowerRegistry.Entry e : registry.all()) {
            switch (e.state) {
                case RITUAL -> tickRitual(e, now);
                case GROUND -> tickGround(e);
                default -> {
                    missing.remove(e);
                    hideBars(e);
                }
            }
        }
        syncViewers();
    }

    private void tickRitual(PowerRegistry.Entry e, long now) {
        PowerType type = e.type;
        Item item = findItem(e);
        if (item == null && chunkLoaded(e)) {
            item = respawnHover(e);
        }
        if (item != null) {
            manager.ensureDisplay(item, type);
        }

        if (now >= e.endMs) {
            if (item != null) {
                dropRitual(e, item);
            } else {
                setBars(e, BossBar.Color.PURPLE, 0f,
                        fr -> Component.text(Lang.tr(fr, "ritual_bar_wait", type.display), type.textColor));
            }
            return;
        }

        long interval = Math.max(10L, plugin.getConfig().getLong("ritual.announce-seconds", 150)) * 1000L;
        long due = (now - e.startMs) / interval;
        if (due > e.announced && due > 0) {
            announce(e, (int) due);
        }

        long total = Math.max(1L, e.endMs - e.startMs);
        float progress = (float) Math.max(0.0, Math.min(1.0, (double) (e.endMs - now) / total));
        long left = (e.endMs - now) / 1000L;
        String coords = coordText(e);
        String worldName = e.world;
        setBars(e, BossBar.Color.PURPLE, progress,
                fr -> Component.text(Lang.tr(fr, "ritual_bar", type.display, Lang.world(fr, worldName), coords,
                        Lang.clock(left)), type.textColor));
    }

    private void tickGround(PowerRegistry.Entry e) {
        PowerType type = e.type;
        Item item = findItem(e);
        if (item != null) {
            missing.remove(e);
            registry.touch(e, item.getLocation());
            manager.ensureDisplay(item, type);
        } else if (chunkLoaded(e)) {
            int count = missing.merge(e, 1, Integer::sum);
            if (count >= 3) {
                lost(e);
                return;
            }
        }

        if (e.fromRitual) {
            int x = (int) Math.floor(e.x);
            int y = (int) Math.floor(e.y);
            int z = (int) Math.floor(e.z);
            String worldName = e.world;
            setBars(e, BossBar.Color.RED, 1.0f,
                    fr -> Component.text(Lang.tr(fr, "ground_bar", type.display, Lang.world(fr, worldName), x, y, z),
                            type.textColor));
        } else {
            hideBars(e);
        }
    }

    /** La potion a disparu (détruite, brûlée, supprimée) : cet exemplaire redevient disponible. */
    private void lost(PowerRegistry.Entry e) {
        PowerType type = e.type;
        registry.free(e);
        missing.remove(e);
        hideBars(e);
        broadcast(type, "power_lost", type.display);
        plugin.getLogger().info("Pouvoir " + type.display + " perdu : de nouveau disponible.");
    }

    /** Commande admin : libère tous les exemplaires non portés et supprime leurs potions. */
    public void forceFree(PowerType type) {
        for (PowerRegistry.Entry e : registry.list(type)) {
            Item item = findItem(e);
            if (item != null) {
                manager.removeDisplays(item);
                item.remove();
            }
            registry.free(e);
            missing.remove(e);
            hideBars(e);
        }
    }

    // ------------------------------------------------------------------
    // Annonces de coordonnées : Z, puis Y, puis X
    // ------------------------------------------------------------------

    private void announce(PowerRegistry.Entry e, int due) {
        PowerType type = e.type;
        e.announced = due;
        registry.save();

        int x = (int) Math.floor(e.x);
        int y = (int) Math.floor(e.y);
        int z = (int) Math.floor(e.z);
        int idx = due - 1; // 0 = Z, 1 = Y, 2 = X, ensuite toutes les coordonnées

        World w = Bukkit.getWorld(e.world == null ? "" : e.world);
        if (w != null) {
            Location ground = new Location(w, e.x, e.y - plugin.getConfig().getDouble("ritual.hover-height", 4.0), e.z);
            w.strikeLightningEffect(ground);
            w.spawnParticle(Particle.FIREWORK, new Location(w, e.x, e.y, e.z), 40, 0.5, 0.5, 0.5, 0.2);
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            String text;
            if (idx == 0) {
                text = Lang.tr(p, "ritual_reveal", "Z", z);
            } else if (idx == 1) {
                text = Lang.tr(p, "ritual_reveal", "Y", y);
            } else if (idx == 2) {
                text = Lang.tr(p, "ritual_reveal", "X", x);
            } else {
                text = Lang.tr(p, "ritual_full", x, y, z);
            }
            text = text + "  [" + Lang.world(Lang.french(p), e.world) + "]";
            Component title = Component.text(Lang.tr(p, "ritual_title", type.display), type.textColor, TextDecoration.BOLD);
            Component sub = Component.text(text, NamedTextColor.WHITE, TextDecoration.BOLD);
            p.showTitle(Title.title(title, sub, Title.Times.times(
                    Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(800))));
            p.sendMessage(Component.text("✦ ", type.textColor)
                    .append(Component.text(type.display + " — ", type.textColor))
                    .append(Component.text(text, NamedTextColor.WHITE, TextDecoration.BOLD)));
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.2f);
            p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.4f, 1.6f);
        }
    }

    private String coordText(PowerRegistry.Entry e) {
        String z = e.announced >= 1 ? String.valueOf((int) Math.floor(e.z)) : "?";
        String y = e.announced >= 2 ? String.valueOf((int) Math.floor(e.y)) : "?";
        String x = e.announced >= 3 ? String.valueOf((int) Math.floor(e.x)) : "?";
        return "Z: " + z + "   Y: " + y + "   X: " + x;
    }

    // ------------------------------------------------------------------
    // Fin du rituel : la potion tombe
    // ------------------------------------------------------------------

    private void dropRitual(PowerRegistry.Entry e, Item item) {
        PowerType type = e.type;
        item.setGravity(true);
        item.setInvulnerable(false);
        item.setVelocity(new Vector(0, 0, 0));
        item.setPickupDelay(20);
        item.setTicksLived(1);
        try {
            item.setUnlimitedLifetime(plugin.getConfig().getBoolean("ritual.ground-unlimited", false));
        } catch (Throwable ignored) {
            // méthode absente : durée de vie normale
        }
        registry.touch(e, item.getLocation());
        registry.toGround(e);
        missing.remove(e);

        World w = item.getWorld();
        w.strikeLightningEffect(item.getLocation());
        w.spawnParticle(Particle.TOTEM_OF_UNDYING, item.getLocation(), 80, 0.6, 0.8, 0.6, 0.4);
        w.playSound(item.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.2f);

        int x = (int) Math.floor(e.x);
        int y = (int) Math.floor(e.y);
        int z = (int) Math.floor(e.z);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(Component.text("✦ ", type.textColor)
                    .append(Component.text(Lang.tr(p, "ritual_dropped", type.display, Lang.world(Lang.french(p), e.world), x, y, z), NamedTextColor.WHITE,
                            TextDecoration.BOLD)));
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.4f);
            p.showTitle(Title.title(
                    Component.text(Lang.tr(p, "ritual_title", type.display), type.textColor, TextDecoration.BOLD),
                    Component.text(Lang.tr(p, "ritual_full", x, y, z) + "  [" + Lang.world(Lang.french(p), e.world) + "]",
                            NamedTextColor.WHITE),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(5), Duration.ofMillis(1000))));
        }
    }

    // ------------------------------------------------------------------
    // Entités
    // ------------------------------------------------------------------

    private Item findItem(PowerRegistry.Entry e) {
        if (e.itemId == null) return null;
        Entity entity = Bukkit.getEntity(e.itemId);
        if (entity instanceof Item item && item.isValid()) return item;
        return null;
    }

    private boolean chunkLoaded(PowerRegistry.Entry e) {
        if (e.world == null) return false;
        World w = Bukkit.getWorld(e.world);
        if (w == null) return false;
        return w.isChunkLoaded((int) Math.floor(e.x) >> 4, (int) Math.floor(e.z) >> 4);
    }

    private Item respawnHover(PowerRegistry.Entry e) {
        PowerType type = e.type;
        World w = Bukkit.getWorld(e.world == null ? "" : e.world);
        if (w == null) return null;
        Location loc = new Location(w, e.x, e.y, e.z);
        Item item;
        spawning = true;
        try {
            item = manager.spawnPotionEntity(loc, type, true);
        } finally {
            spawning = false;
        }
        if (item != null) {
            e.itemId = item.getUniqueId();
            registry.save();
        }
        return item;
    }

    // ------------------------------------------------------------------
    // Effets visuels du rituel (toutes les 2 ticks)
    // ------------------------------------------------------------------

    private void fastTick() {
        spin += 0.12f;
        fastCounter++;

        for (PowerRegistry.Entry e : registry.all()) {
            if (e.state != PowerRegistry.State.RITUAL && !(e.state == PowerRegistry.State.GROUND && e.fromRitual)) {
                continue;
            }
            Item item = findItem(e);
            if (item == null) continue;

            if (e.state == PowerRegistry.State.RITUAL) {
                ritualVisuals(e.type, item);
            } else if (fastCounter % 5 == 0) {
                groundBeacon(e.type, item);
            }
        }
    }

    private void ritualVisuals(PowerType type, Item item) {
        World w = item.getWorld();
        Location c = item.getLocation().add(0, 0.5, 0);
        double height = plugin.getConfig().getDouble("ritual.hover-height", 4.0);
        Location base = c.clone().subtract(0, height - 1.0, 0);
        Particle.DustOptions dust = new Particle.DustOptions(type.potionColor, 1.3f);

        manager.spinDisplay(item, spin);

        // Trois points en orbite autour de la potion
        for (int k = 0; k < 3; k++) {
            double a = spin * 2.0 + k * (Math.PI * 2 / 3);
            Location o = c.clone().add(Math.cos(a) * 1.1, Math.sin(spin + k) * 0.5, Math.sin(a) * 1.1);
            Effects.emit(w, o, type);
        }
        w.spawnParticle(Particle.ENCHANT, c, 6, 1.6, 1.0, 1.6, 0.4);

        // Cercle de runes qui tourne au sol
        for (int i = 0; i < 12; i++) {
            double a = spin * 0.8 + i * (Math.PI * 2 / 12);
            w.spawnParticle(Particle.DUST, base.getX() + Math.cos(a) * 2.6, base.getY() + 0.05,
                    base.getZ() + Math.sin(a) * 2.6, 1, 0, 0, 0, 0, dust);
        }

        // Étoile à six branches
        if (fastCounter % 10 == 0) {
            Location[] pts = new Location[6];
            for (int i = 0; i < 6; i++) {
                double a = spin * 0.3 + i * (Math.PI / 3);
                pts[i] = base.clone().add(Math.cos(a) * 2.6, 0.08, Math.sin(a) * 2.6);
            }
            for (int i = 0; i < 6; i++) {
                Effects.line(w, pts[i], pts[(i + 2) % 6], 0.4, Particle.DUST, dust);
            }
        }

        // Pilier de lumière entre le sol et la potion
        if (fastCounter % 2 == 0) {
            Effects.line(w, base, c, 0.7, Particle.END_ROD, null);
        }
    }

    private void groundBeacon(PowerType type, Item item) {
        World w = item.getWorld();
        Location l = item.getLocation();
        Particle.DustOptions dust = new Particle.DustOptions(type.potionColor, 1.5f);
        for (double h = 0.3; h < 12; h += 0.6) {
            w.spawnParticle(Particle.DUST, l.getX(), l.getY() + h, l.getZ(), 1, 0.05, 0, 0.05, 0, dust);
        }
        w.spawnParticle(Particle.END_ROD, l.clone().add(0, 0.5, 0), 3, 0.3, 0.4, 0.3, 0.02);
    }

    // ------------------------------------------------------------------
    // Barres de boss
    // ------------------------------------------------------------------

    private interface TextFor {
        Component make(boolean french);
    }

    private void setBars(PowerRegistry.Entry key, BossBar.Color color, float progress, TextFor text) {
        Bars b = bars.computeIfAbsent(key, k -> new Bars());
        for (int i = 0; i < 2; i++) {
            b.bar[i].name(text.make(i == 0));
            b.bar[i].color(color);
            b.bar[i].progress(Math.max(0f, Math.min(1f, progress)));
        }
    }

    private void syncViewers() {
        for (Bars b : bars.values()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                int mine = Lang.french(p) ? 0 : 1;
                int other = 1 - mine;
                if (b.shown[other].remove(p.getUniqueId())) {
                    p.hideBossBar(b.bar[other]);
                }
                if (b.shown[mine].add(p.getUniqueId())) {
                    p.showBossBar(b.bar[mine]);
                }
            }
        }
    }

    private void hideBars(PowerRegistry.Entry key) {
        Bars b = bars.remove(key);
        if (b == null) return;
        for (int i = 0; i < 2; i++) {
            for (UUID id : b.shown[i]) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) p.hideBossBar(b.bar[i]);
            }
            b.shown[i].clear();
        }
    }

    // ------------------------------------------------------------------
    // Messages
    // ------------------------------------------------------------------

    private void broadcast(PowerType type, String key, Object... args) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(Component.text("✦ ", type.textColor)
                    .append(Component.text(Lang.tr(p, key, args), NamedTextColor.WHITE)));
        }
    }

    // ------------------------------------------------------------------
    // Ramassage, destruction, connexion
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onPickup(EntityPickupItemEvent e) {
        Item item = e.getItem();
        PowerType type = manager.potionType(item.getItemStack());
        if (type == null) return;

        PowerRegistry.Entry en = registry.entryByItem(type, item.getUniqueId());

        // La potion qui flotte n'est pas ramassable
        if (en != null && en.state == PowerRegistry.State.RITUAL) {
            e.setCancelled(true);
            return;
        }
        // Les monstres, allays, etc. ne la volent pas
        if (!(e.getEntity() instanceof Player p)) {
            e.setCancelled(true);
            return;
        }
        if (en == null) return;

        boolean win = en.fromRitual;
        missing.remove(en);
        hideBars(en);
        registry.markCarried(en);

        if (win) {
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.5, 0.8, 0.5, 0.3);
            for (Player all : Bukkit.getOnlinePlayers()) {
                all.sendMessage(Component.text("✦ ", type.textColor)
                        .append(Component.text(Lang.tr(all, "ritual_won", p.getName(), type.display),
                                NamedTextColor.WHITE, TextDecoration.BOLD)));
                all.playSound(all.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHopper(InventoryPickupItemEvent e) {
        if (manager.potionType(e.getItem().getItemStack()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDespawn(ItemDespawnEvent e) {
        PowerType type = manager.potionType(e.getEntity().getItemStack());
        if (type == null) return;
        PowerRegistry.Entry en = registry.entryByItem(type, e.getEntity().getUniqueId());
        if (en != null && en.state == PowerRegistry.State.RITUAL) {
            e.setCancelled(true);
            e.getEntity().setTicksLived(1);
        }
    }

    /** Toute potion de pouvoir lâchée par terre est suivie : si elle disparaît, le pouvoir revient. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent e) {
        if (spawning) return;
        Item item = e.getEntity();
        PowerType type = manager.potionType(item.getItemStack());
        if (type == null) return;
        if (registry.entryByItem(type, item.getUniqueId()) != null) return;
        PowerRegistry.Entry en = registry.setGround(type, item.getUniqueId(), item.getLocation());
        if (en != null) missing.remove(en);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.getServer().getScheduler().runTaskLater(plugin, this::syncViewers, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        for (Bars b : bars.values()) {
            b.shown[0].remove(id);
            b.shown[1].remove(id);
        }
    }
}
