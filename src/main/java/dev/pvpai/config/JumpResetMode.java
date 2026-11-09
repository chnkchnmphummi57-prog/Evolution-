package dev.pvpai.config;

public enum JumpResetMode {
    REACT("React (first tick)"), PREDICT("React + predict");
    public final String label;
    JumpResetMode(String label) { this.label = label; }
}
