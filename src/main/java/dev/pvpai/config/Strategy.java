package dev.pvpai.config;

public enum Strategy {
    NORMAL("Normal"), SAFE("Safe"), CRIT_SPAM("Crit spam");
    public final String label;
    Strategy(String label) { this.label = label; }
}
