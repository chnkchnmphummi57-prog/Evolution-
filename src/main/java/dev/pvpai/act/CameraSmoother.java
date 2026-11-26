package dev.pvpai.act;

import dev.pvpai.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Per-FRAME camera easing (not per tick), so rotation is smooth at any FPS.
 * Angles are applied directly in degrees; mouse-sensitivity scaling is not suitable for calculated aim angles.
 */
public final class CameraSmoother {
    private CameraSmoother() { }

    private static volatile boolean active;
    private static volatile float goalYaw, goalPitch;
    private static volatile boolean fast;
    private static boolean frameSinceTick;

    public static void setGoal(float yaw, float pitch, boolean fastAim) { goalYaw = yaw; goalPitch = pitch; fast = fastAim; active = true; }
    public static void clear() { active = false; }

    /** Sets an exact rotation. changeLookDirection applies mouse-sensitivity scaling, so it cannot reliably snap to a target. */
    public static void snap(ClientPlayerEntity p, float yaw, float pitch) {
        p.setYaw(yaw);
        p.setPitch(MathHelper.clamp(pitch, -90f, 90f));
    }

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

    private static void step(ClientPlayerEntity p, float dtTicks) {
        ModConfig cfg = ModConfig.get();

        // RenderTickCounter reports elapsed game ticks, not seconds. Convert it so the
        // response feels consistent at 30, 60, 144+ FPS instead of changing with FPS.
        float safeTicks = Math.max(0.001f, Math.min(2f, dtTicks));
        float dtSeconds = safeTicks / 20f;

        // Smoothness controls the easing time only. It must NOT reduce the maximum
        // tracking speed: otherwise a high smoothness setting makes the camera lag.
        float smoothness = MathHelper.clamp(cfg.cameraSmoothness, 0, 100) / 100f;
        float timeConstant = 0.018f + 0.062f * smoothness; // 18–80 ms
        float alpha = 1f - (float) Math.exp(-dtSeconds / timeConstant);

        // Keep the existing configurable turn-rate setting, but use it as a real
        // per-tick speed limit. The v0.7.5 default is migrated from 18 to 45 deg/tick.
        float maxSpeed = Math.max(1f, cfg.maxTurnDegPerTick) * (fast ? 1.6f : 1f);
        float maxStep = maxSpeed * safeTicks;

        float yawError = MathHelper.wrapDegrees(goalYaw - p.getYaw());
        float pitchError = goalPitch - p.getPitch();
        float yawStep = MathHelper.clamp(yawError * alpha, -maxStep, maxStep);
        float pitchStep = MathHelper.clamp(pitchError * alpha, -maxStep, maxStep);

        if (Math.abs(yawStep) < 0.001f && Math.abs(pitchStep) < 0.001f) return;
        p.setYaw(p.getYaw() + yawStep);
        p.setPitch(MathHelper.clamp(p.getPitch() + pitchStep, -90f, 90f));
    }
}
