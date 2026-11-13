package dev.pvpai.config;

/** Kit profile: which weapon is primary. Sword and Axe fight normally, Mace adds the wind-charge smash. */
public enum Kit {
    SWORD("Sword"), AXE("Axe"), MACE("Mace");
    public final String label;
    Kit(String label) { this.label = label; }
}
