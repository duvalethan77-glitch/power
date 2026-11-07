package com.power;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Menu /power info : la liste des pouvoirs, puis la recette de chacun. */
public final class InfoGui {

    /** Identifie nos inventaires (type == null : menu principal). */
    public static final class Holder implements InventoryHolder {
        public final PowerType type;
        public final Map<Integer, PowerType> slots = new HashMap<>();
        private Inventory inventory;

        public Holder(PowerType type) {
            this.type = type;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final int[] MENU_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 21, 23, 25};
    private static final int[] GRID_SLOTS = {11, 12, 13, 20, 21, 22, 29, 30, 31};
    public static final int BACK_SLOT = 40;
    private static final int ARROW_SLOT = 23;
    private static final int RESULT_SLOT = 24;

    private final PowerPlugin plugin;
    private final PowerManager manager;
    private final PowerRegistry registry;

    public InfoGui(PowerPlugin plugin, PowerManager manager, PowerRegistry registry) {
        this.plugin = plugin;
        this.manager = manager;
        this.registry = registry;
    }

    // ------------------------------------------------------------------
    // Menu principal
    // ------------------------------------------------------------------

    public void openMenu(Player p) {
        Holder holder = new Holder(null);
        Inventory inv = Bukkit.createInventory(holder, 36,
                Component.text(Lang.tr(p, "gui_menu_title"), NamedTextColor.DARK_GRAY));
        holder.inventory = inv;
        fill(inv);

        PowerType current = manager.getPower(p);
        PowerType[] types = PowerType.values();
        for (int i = 0; i < types.length && i < MENU_SLOTS.length; i++) {
            int slot = MENU_SLOTS[i];
            inv.setItem(slot, icon(p, types[i], types[i] == current, true));
            holder.slots.put(slot, types[i]);
        }
        inv.setItem(31, ritualItem(p));
        p.openInventory(inv);
    }

    /** Explication du rituel, avec les durées de la config. */
    private ItemStack ritualItem(Player p) {
        long minutes = Math.round(plugin.getConfig().getDouble("ritual.duration-minutes", 20));
        long interval = Math.max(10L, plugin.getConfig().getLong("ritual.announce-seconds", 150));

        ItemStack item = new ItemStack(Material.ENCHANTING_TABLE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(Lang.tr(p, "gui_ritual_title"), NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        String[] steps = {
                Lang.tr(p, "ri_1", (int) minutes),
                Lang.tr(p, "ri_2"),
                Lang.tr(p, "ri_3", Lang.duration(interval)),
                Lang.tr(p, "ri_4"),
                Lang.tr(p, "ri_5", registry.copies()),
                Lang.tr(p, "ri_6"),
                Lang.tr(p, "ri_7")
        };
        for (String step : steps) {
            List<String> rows = wrap(step, 40);
            for (int i = 0; i < rows.size(); i++) {
                lore.add(line((i == 0 ? "" : "   ") + rows.get(i), i == 0 ? NamedTextColor.WHITE : NamedTextColor.GRAY));
            }
            lore.add(Component.empty());
        }
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    // ------------------------------------------------------------------
    // Détail d'un pouvoir : la recette en grille 3x3
    // ------------------------------------------------------------------

    public void openDetail(Player p, PowerType type) {
        Holder holder = new Holder(type);
        Inventory inv = Bukkit.createInventory(holder, 45,
                Component.text(Lang.tr(p, "gui_detail_title", type.display), NamedTextColor.DARK_GRAY));
        holder.inventory = inv;
        fill(inv);

        Recipes.Def d = Recipes.get(type);
        if (d != null) {
            Material[] grid = d.grid();
            for (int i = 0; i < GRID_SLOTS.length; i++) {
                inv.setItem(GRID_SLOTS[i], new ItemStack(grid[i]));
            }
        }

        inv.setItem(ARROW_SLOT, named(Material.ARROW, Component.text("→", NamedTextColor.GRAY)));
        inv.setItem(RESULT_SLOT, icon(p, type, false, false));
        inv.setItem(33, statusItem(p, type));
        inv.setItem(BACK_SLOT, named(Material.BARRIER,
                Component.text(Lang.tr(p, "gui_back"), NamedTextColor.RED)));
        p.openInventory(inv);
    }

    // ------------------------------------------------------------------
    // Objets
    // ------------------------------------------------------------------

    private ItemStack statusItem(Player p, PowerType type) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(line(Lang.tr(p, "gui_status"), NamedTextColor.GOLD));
        List<Component> lore = new ArrayList<>();
        for (PowerRegistry.Entry entry : registry.list(type)) {
            lore.add(line("• " + registry.describe(p, entry), NamedTextColor.WHITE));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void fill(Inventory inv) {
        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, pane);
        }
    }

    private ItemStack named(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    /** Icône d'un pouvoir (potion colorée, sans effet : on ne peut pas la boire). */
    private ItemStack icon(Player p, PowerType type, boolean current, boolean clickHint) {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setColor(type.potionColor);
        meta.displayName(Component.text(type.display, type.textColor).decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        section(lore, Lang.tr(p, "gui_passive"), Lang.tr(p, "p_" + type.configKey()));
        section(lore, Lang.tr(p, "gui_active"), Lang.tr(p, "a_" + type.configKey()));
        lore.add(line(Lang.tr(p, "gui_cooldown", manager.cooldownSeconds(type)), NamedTextColor.AQUA));
        lore.add(line(Lang.tr(p, "gui_status") + " :", NamedTextColor.GOLD));
        for (PowerRegistry.Entry entry : registry.list(type)) {
            lore.add(line("• " + registry.describe(p, entry), NamedTextColor.WHITE));
        }

        Recipes.Def d = Recipes.get(type);
        if (d != null) {
            lore.add(Component.empty());
            lore.add(line(Lang.tr(p, "gui_recipe"), NamedTextColor.GOLD));
            // Un objet par ligne, avec la quantité
            Map<Material, Integer> counts = new LinkedHashMap<>();
            for (Material m : d.grid()) {
                counts.merge(m, 1, Integer::sum);
            }
            for (Map.Entry<Material, Integer> entry : counts.entrySet()) {
                String amount = entry.getValue() > 1 ? " x" + entry.getValue() : "";
                lore.add(line("• " + Recipes.pretty(entry.getKey()) + amount, NamedTextColor.GRAY));
            }
        }

        if (current) {
            lore.add(Component.empty());
            lore.add(line("✔ " + Lang.tr(p, "gui_current"), NamedTextColor.GREEN));
            meta.setEnchantmentGlintOverride(true);
        }
        if (clickHint) {
            lore.add(Component.empty());
            lore.add(line(Lang.tr(p, "gui_click"), NamedTextColor.YELLOW));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private void section(List<Component> lore, String label, String text) {
        lore.add(line(label + " :", NamedTextColor.GOLD));
        for (String row : wrap(text, 38)) {
            lore.add(line(row, NamedTextColor.GRAY));
        }
    }

    private List<String> wrap(String text, int width) {
        List<String> rows = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            if (current.length() > 0 && current.length() + word.length() + 1 > width) {
                rows.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) current.append(' ');
            current.append(word);
        }
        if (current.length() > 0) rows.add(current.toString());
        return rows;
    }
}
