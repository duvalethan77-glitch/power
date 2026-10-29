package com.power;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;

import java.util.Locale;

public enum PowerType {
    LIGHTNING("Lightning", NamedTextColor.YELLOW, Color.fromRGB(0xFFE600), 30),
    ICE("Ice", NamedTextColor.AQUA, Color.fromRGB(0x66E0FF), 25),
    BLOOD("Blood", NamedTextColor.DARK_RED, Color.fromRGB(0x8B0000), 40),
    FIRE("Fire", NamedTextColor.GOLD, Color.fromRGB(0xFF6A00), 20),
    WIND("Wind", TextColor.color(0xA8F0D0), Color.fromRGB(0xA8F0D0), 15),
    SHADOW("Shadow", TextColor.color(0x8A7FB0), Color.fromRGB(0x2B2B3A), 30),
    EARTH("Earth", TextColor.color(0xA0703C), Color.fromRGB(0x6B4F2A), 35),
    POISON("Poison", TextColor.color(0x6FBF4A), Color.fromRGB(0x4E9331), 30),
    LIGHT("Light", TextColor.color(0xFFF3B0), Color.fromRGB(0xFFF8D0), 35),
    PHOENIX("Phoenix", TextColor.color(0xFF4500), Color.fromRGB(0xFF4500), 60);

    public final String display;
    public final TextColor textColor;
    public final Color potionColor;
    public final int defaultCooldown;

    PowerType(String display, TextColor textColor, Color potionColor, int defaultCooldown) {
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
