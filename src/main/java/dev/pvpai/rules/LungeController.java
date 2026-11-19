package dev.pvpai.rules;

import dev.pvpai.act.Action;
import dev.pvpai.config.ModConfig;
import dev.pvpai.observe.Ctx;

/**
 * Spear lunge to close the distance, repeated until the opponent is close enough.
 *
 * Why the wind charge: a jab only lunges with a full attack bar, and the server judges the bar with the item held BEFORE a swap in
 * the same tick. A wind charge is not a weapon, so its attack bar refills in about 5 ticks. So the bot holds the wind charge,
 * and each lunge is: select the spear, jab, select the wind charge again, all inside one tick (see ActionApplier.lunge).
 *
 * Prediction: it watches how fast the distance is closing, estimates the distance a few ticks ahead, and only lunges when that
 * estimate is at least lungeDist AND a lunge (lungeTravel) would still leave 1+ block. So it neither lunges when the opponent
 * is already walking in, nor overshoots into them.
 * Ticks: hold the wind charge until the bar is full, snap to a level view (the rotation reaches the server first), then fire.
 */
public final class LungeController {
    private enum P { IDLE, FIRE }

    private P p = P.IDLE;
    private int cooldown;
    private double lastReach = Double.NaN, closing;
    private int samples;
    public String state = "";

    public void reset() { p = P.IDLE; cooldown = 0; lastReach = Double.NaN; closing = 0; samples = 0; state = ""; }

    /** @return true while it is driving the action (also while it holds the wind charge waiting for the next lunge). */
    public boolean apply(Action a, Ctx c, ModConfig cfg) {
        if (!cfg.spearLunge || c.spearSlot < 0) { p = P.IDLE; state = ""; lastReach = Double.NaN; samples = 0; return false; }
        if (cooldown > 0) cooldown--;

        // Closing speed (blocks per tick, positive = coming closer), smoothed, and the distance about 4 ticks ahead.
        double step = Double.isNaN(lastReach) ? 0 : lastReach - c.reachDist;
        lastReach = c.reachDist;
        samples++;
        closing = 0.6 * closing + 0.4 * step;
        double pred = c.reachDist - closing * 4.0;

        boolean far = pred >= cfg.lungeDist && pred <= cfg.lungeDist + 12.0;
        boolean overshoot = pred - cfg.lungeTravel < 1.0;
        if (c.food < 7) { p = P.IDLE; state = "lunge: hunger too low (needs 7)"; return false; }
        if (pred < cfg.lungeDist) { p = P.IDLE; state = String.format("lunge: close enough (%.1f ahead)", pred); return false; }
        if (!far) { p = P.IDLE; state = String.format("lunge: too far for now (%.1f)", pred); return false; }
        if (overshoot) { p = P.IDLE; state = String.format("lunge: would overshoot (%.1f ahead, travel %.1f)", pred, cfg.lungeTravel); return false; }

        // Hold the wind charge between lunges. Without one, fall back to whatever is in hand.
        boolean useWind = cfg.lungeHoldWind && c.windSlot >= 0;
        int holder = useWind ? c.windSlot : c.selectedSlot;
        a.attack = false; a.jump = false;
        if (useWind && c.selectedSlot != c.windSlot) {
            a.selectSlot = c.windSlot;
            p = P.IDLE;
            state = "lunge: holding wind charge";
            return true;
        }
        if (useWind) a.selectSlot = c.windSlot;
        if (samples < 5) { state = "lunge: measuring the opponent's speed"; p = P.IDLE; return true; }   // no prediction without a few ticks of history
        if (cooldown > 0) { state = "lunge: cooldown " + cooldown; p = P.IDLE; return true; }
        if (c.cooldown < 0.98f) { state = "lunge: attack bar charging"; p = P.IDLE; return true; }

        a.aim = true; a.goalYaw = c.yawToTarget; a.goalPitch = 0f;           // level view = longest horizontal lunge
        if (p == P.IDLE) {
            a.snapAim = true;                                                 // tick 1: turn; the server gets the rotation first
            p = P.FIRE;
            state = "lunge: aiming";
            return true;
        }
        a.lunge = true; a.lungeSlot = c.spearSlot; a.restoreSlot = holder;    // tick 2: spear, jab, back to the holder
        cooldown = cfg.lungeCooldownTicks;
        p = P.IDLE;
        state = String.format("lunge: fired (%.1f ahead)", pred);
        return true;
    }
}
