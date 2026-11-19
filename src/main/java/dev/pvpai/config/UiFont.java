package dev.pvpai.config;

/** Font for the HUD, menu and watermark. The TTF ones are bundled with the mod (see licenses/). */
public enum UiFont {
    DEFAULT("Minecraft", null, null),
    SMOOTH("Roboto", "pvpai", "smooth"),
    CLEAN("Source Sans", "pvpai", "clean"),
    MONO("Mono", "pvpai", "mono"),
    UNICODE("Unicode", "minecraft", "uniform");

    public final String label, namespace, path;
    UiFont(String label, String namespace, String path) { this.label = label; this.namespace = namespace; this.path = path; }
}
