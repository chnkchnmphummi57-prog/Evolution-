package dev.pvpai.config;

public enum HudCorner {
    TOP_LEFT("Top left"), TOP_RIGHT("Top right"), BOTTOM_LEFT("Bottom left"), BOTTOM_RIGHT("Bottom right");
    public final String label;
    HudCorner(String label) { this.label = label; }
}
