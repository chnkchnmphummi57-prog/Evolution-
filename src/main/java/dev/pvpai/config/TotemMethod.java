package dev.pvpai.config;

public enum TotemMethod {
    AUTO("Auto"), CLICK("Inventory click"), HAND_SWAP("Swap hands (hotbar)");
    public final String label;
    TotemMethod(String label) { this.label = label; }
}
