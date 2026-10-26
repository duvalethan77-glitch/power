package com.power;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;

import java.util.Locale;

public enum PowerType {
    // La couleur imite la potion indiquée : leaping, swiftness, strength, healing, weakness
    STRENGTH("Strength", NamedTextColor.GREEN, Color.fromRGB(0x22FF4C)),
    SPEED("Speed", NamedTextColor.AQUA, Color.fromRGB(0x33EBFF)),
    SATURATION("Saturation", NamedTextColor.GOLD, Color.fromRGB(0xFFC700)),
    HEALTH("Health", NamedTextColor.RED, Color.fromRGB(0xF82423)),
    VOID("Void", NamedTextColor.DARK_PURPLE, Color.fromRGB(0x484D48));

    public final String display;
    public final NamedTextColor textColor;
    public final Color potionColor;

    PowerType(String display, NamedTextColor textColor, Color potionColor) {
        this.display = display;
        this.textColor = textColor;
        this.potionColor = potionColor;
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
