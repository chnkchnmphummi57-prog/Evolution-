package net.minecraft.util.math;
public final class MathHelper {
    public static float wrapDegrees(float d) { d = d % 360f; if (d >= 180f) d -= 360f; if (d < -180f) d += 360f; return d; }
    public static float clamp(float v, float lo, float hi) { return v < lo ? lo : Math.min(v, hi); }
    public static int clamp(int v, int lo, int hi) { return v < lo ? lo : Math.min(v, hi); }
}
