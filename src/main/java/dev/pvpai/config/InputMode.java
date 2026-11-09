package dev.pvpai.config;

public enum InputMode {
    INPUT("Mouse & keyboard"), PACKET("Packets");
    public final String label;
    InputMode(String label) { this.label = label; }
}
