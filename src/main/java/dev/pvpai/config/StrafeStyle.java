package dev.pvpai.config;

public enum StrafeStyle {
    ALTERNATE("Alternate"), CIRCLE("Circle"), JITTER("Jitter"), MIXED("Mixed");
    public final String label;
    StrafeStyle(String label) { this.label = label; }
}
