package dev.pvpai.core;

public enum AiMode {
    ML("ML"), STRICT("Strict"), SMART("Smart");
    public final String label;
    AiMode(String label) { this.label = label; }
}
