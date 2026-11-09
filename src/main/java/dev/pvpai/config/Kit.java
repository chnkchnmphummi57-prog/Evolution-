package dev.pvpai.config;

/** Kit profile: which weapon is primary and which kit-specific extras are active. */
public enum Kit {
    SWORD("Sword"), AXE("Axe"), MACE("Mace"), POT("Pot"), NETHPOT("NethPot"), SMP("SMP"), UHC("UHC");
    public final String label;
    Kit(String label) { this.label = label; }
}
