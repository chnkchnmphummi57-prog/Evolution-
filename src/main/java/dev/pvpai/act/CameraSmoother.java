package dev.pvpai.act;

import dev.pvpai.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Per-FRAME camera easing (not per tick), so rotation is smooth at any FPS.
 * Rotation goes through changeLookDirection, the same path real mouse movement uses.
 */
public final class CameraSmoother {
    private CameraSmoother() { }

    private static volatile boolean active;
    private static volatile float goalYaw, goalPitch;
    private static volatile boolean fast;
    private static boolean frameSinceTick;

    public static void setGoal(float yaw, float pitch, boolean fastAim) { goalYaw = yaw; goalPitch = pitch; fast = fastAim; active = true; }
    public static void clear() { active = false; }

    // The rotation sent to the server in the last movement packet. At the start of a tick that is what the server still has,
    // even though per-frame smoothing may already have moved the camera on.
    public static volatile float sentYaw, sentPitch;
    private static volatile boolean sentKnown;
    public static void recordSent(ClientPlayerEntity p) { sentYaw = p.getYaw(); sentPitch = p.getPitch(); sentKnown = true; }
    public static boolean sentKnown() { return sentKnown; }

    /** Called from the render callback every frame. */
    public static void frame(MinecraftClient mc, float dtTicks) {
        if (!active || mc.player == null || mc.currentScreen != null) return;
        frameSinceTick = true;
        step(mc.player, dtTicks);
    }

    /** Called at tick start: if no frame ran (HUD hidden), apply one whole-tick step so aiming still works. */
    public static void tickFallback(MinecraftClient mc) {
        if (active && !frameSinceTick && mc.player != null) step(mc.player, 1f);
        frameSinceTick = false;
    }

    private static void step(ClientPlayerEntity p, float dt) {
        ModConfig cfg = ModConfig.get();
        float s = cfg.cameraSmoothness / 100f;
        float perTick = fast ? 0.75f : 0.6f - 0.5f * s;                       // fraction of the remaining error closed per tick
        float alpha = 1f - (float) Math.pow(1f - perTick, dt);
        float yawErr = MathHelper.wrapDegrees(goalYaw - p.getYaw());
        float pitchErr = goalPitch - p.getPitch();
        float maxStep = cfg.maxTurnDegPerTick * dt * (fast ? 3f : 1f);
        float dy = MathHelper.clamp(yawErr * alpha, -maxStep, maxStep);
        float dp = MathHelper.clamp(pitchErr * alpha, fast ? -maxStep : -maxStep / 2f, fast ? maxStep : maxStep / 2f);
        if (Math.abs(dy) < 0.01f && Math.abs(dp) < 0.01f) return;
        p.changeLookDirection(dy / 0.15, dp / 0.15);           // 0.15 = vanilla degrees per mouse unit
    }
}
