package dev.pvpai.config;

public enum JumpInput {
    KEY("Space bar (key press)"), DIRECT("Direct (jump physics)");
    public final String label;
    JumpInput(String label) { this.label = label; }
}
