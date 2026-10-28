package com.power;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;

import java.util.Locale;

public enum PowerType {
    LIGHTNING("Lightning", NamedTextColor.YELLOW, Color.fromRGB(0xFFE600), 30),
    ICE("Ice", NamedTextColor.AQUA, Color.fromRGB(0x66E0FF), 25),
    BLOOD("Blood", NamedTextColor.DARK_RED, Color.fromRGB(0x8B0000), 40),
    FIRE("Fire", NamedTextColor.GOLD, Color.fromRGB(0xFF6A00), 20);

    public final String display;
    public final NamedTextColor textColor;
    public final Color potionColor;
    public final int defaultCooldown;

    PowerType(String display, NamedTextColor textColor, Color potionColor, int defaultCooldown) {
        this.display = display;
        this.textColor = textColor;
        this.potionColor = potionColor;
        this.defaultCooldown = defaultCooldown;
    }

    /** Préfixe des réglages de ce pouvoir dans config.yml (ex. "lightning"). */
    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static PowerType parse(String s) {
        if (s == null) return null;
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
