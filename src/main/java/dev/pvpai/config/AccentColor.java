package dev.pvpai.config;

/** Accent colour shared by the HUD, the menu and the watermark. RAINBOW cycles through hues. */
public enum AccentColor {
    INDIGO("Indigo", 0xFF7C6CFF), GREEN("Green", 0xFF3DDC84), BLUE("Blue", 0xFF58A6FF), PURPLE("Purple", 0xFFA78BFA),
    ORANGE("Orange", 0xFFF0883E), PINK("Pink", 0xFFF472B6), CYAN("Cyan", 0xFF22D3EE), RED("Red", 0xFFFF5C5C),
    WHITE("White", 0xFFE8EEF7), RAINBOW("Rainbow", 0xFFFFFFFF);
    public final String label;
    public final int argb;
    AccentColor(String label, int argb) { this.label = label; this.argb = argb; }
}
