package dev.pvpai.config;

public enum WatermarkStyle {
    BOX("Box"), LINE("Underline"), TEXT("Text only");
    public final String label;
    WatermarkStyle(String label) { this.label = label; }
}
