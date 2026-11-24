package dev.pvpai.rules;

import dev.pvpai.act.Action;
import dev.pvpai.config.Kit;
import dev.pvpai.config.ModConfig;
import dev.pvpai.observe.Ctx;

/**
 * Mace chain, one step per tick:
 *  (optional) PEARL CATCH: look up, throw an ender pearl, wait pearlDelay ticks
 *  AIM    look straight down (an instant snap, so the charge lands at your feet)
 *  SELECT switch to the wind charge, then jump and throw on the SAME tick
 *  FLY    go back to the weapon, steer at the opponent. If the opponent is blocking: axe their shield mid-air first (stun slam)
 *  SMASH  hit while falling more than 1.5 blocks
 * After a smash that lands the chain keeps going with a short cooldown instead of dropping back to normal fighting.
 * Needs a wind charge in the hotbar and the Mace kit. With a sword in the hotbar the weapon swap uses the sword's charge.
 * Every reason it is NOT running is written to `state`, which the HUD shows.
 */
public final class MaceController {
    private enum P { IDLE, PAIM, PSEL, PWAIT, AIM, SELECT, FLY }

    private P p = P.IDLE;
    private int ticks, cooldown, awaitHit, stunWait, chain, pearlCooldown, pearlWait;
    private boolean stunDone;
    private float smashHealth;
    public String state = "";

    public void reset() { p = P.IDLE; ticks = 0; cooldown = 0; awaitHit = 0; stunWait = 0; chain = 0; pearlCooldown = 0; pearlWait = 0; stunDone = false; state = ""; }

    /** @return true while the combo is driving the action. */
    public boolean apply(Action a, Ctx c, ModConfig cfg) {
        if (!cfg.useWindCharge) { p = P.IDLE; state = ""; return false; }
        if (cfg.kit != Kit.MACE) {
            p = P.IDLE;
            state = (c.windSlot >= 0 || c.maceSlot >= 0) ? "mace: Kit is not Mace" : "";
            return false;
        }
        if (c.windSlot < 0) { p = P.IDLE; state = "mace: no wind charge in the hotbar"; return false; }
        if (cooldown > 0) cooldown--;
        if (pearlCooldown > 0) pearlCooldown--;
        ticks++;

        // Did the last smash land? Then keep the chain going.
        if (awaitHit > 0) {
            awaitHit--;
            if (c.tHurtTime >= 7 || c.tHealth < smashHealth - 0.01f) {
                awaitHit = 0; chain++;
                if (cfg.maceChain) cooldown = Math.min(cooldown, 8);
            }
        }

        int smashSlot = c.maceSlot >= 0 ? c.maceSlot : Utility.weaponSlot(c, cfg);
        // The mace sequence must leave the wind charge immediately after throwing it.
        // Keep the mace selected throughout flight; do not route through the sword's
        // attribute-swap path, which made the requested wind-charge -> mace transition
        // depend on a second late slot change at impact.
        int hold = smashSlot;

        if (p == P.IDLE) {
            String why = cooldown > 0 ? "cooldown " + cooldown
                    : !c.onGround ? "not on the ground"
                    : c.dist < 1.5 ? "opponent too close"
                    : c.dist > 14.0 ? String.format("opponent too far (%.0f)", c.dist)
                    : c.health <= cfg.retreatHealth ? "health too low"
                    : c.tHurtTime > 1 ? "opponent in hurt frames"
                    : null;
            if (why != null) { state = "mace: waiting (" + why + ")" + (chain > 0 ? ", chain " + chain : ""); return false; }
            p = (cfg.pearlCatch && c.pearlSlot >= 0 && pearlCooldown == 0) ? P.PAIM : P.AIM;
            ticks = 0; stunDone = false;
        }

        a.attack = false; a.use = false;
        if (p == P.PAIM || p == P.PSEL || p == P.PWAIT || p == P.AIM || p == P.SELECT) {        // stand still while setting up
            a.forward = false; a.back = false; a.left = false; a.right = false; a.sprint = false; a.jump = false;
        }
        switch (p) {
            case PAIM -> {
                a.aim = true; a.snapAim = true; a.goalPitch = -cfg.pearlPitch; a.goalYaw = c.yawToTarget;     // look up, instantly
                if (c.pitch <= -(cfg.pearlPitch - 8)) { a.selectSlot = c.pearlSlot; p = P.PSEL; ticks = 0; }  // arrived: take the pearl now
                else if (ticks > 6) { p = P.AIM; pearlCooldown = 40; ticks = 0; }                            // could not look up: skip the pearl
                state = "mace: look up (pearl)";
            }
            case PSEL -> {
                a.aim = true; a.snapAim = true; a.goalPitch = -cfg.pearlPitch; a.goalYaw = c.yawToTarget;
                a.selectSlot = c.pearlSlot;
                if (c.selectedSlot == c.pearlSlot) {
                    a.use = true;                                                                           // throw it straight up
                    pearlWait = Math.max(0, cfg.pearlDelay); pearlCooldown = cfg.pearlCooldownTicks;
                    p = P.PWAIT; ticks = 0;
                    state = "mace: pearl thrown";
                } else if (ticks > 6) { p = P.AIM; pearlCooldown = 40; ticks = 0; }
                else state = "mace: pearl";
            }
            case PWAIT -> {
                // The pearl is already thrown. Explicitly select the wind charge for every
                // wait tick, then hand off to AIM so the normal charge/jump sequence continues.
                a.use = false;
                a.selectSlot = c.windSlot;
                if (--pearlWait <= 0) {
                    p = P.AIM;
                    ticks = 0;
                }
                state = "mace: waiting for the pearl (" + Math.max(0, pearlWait) + ")";
            }
            case AIM -> {
                a.aim = true; a.snapAim = true; a.goalPitch = 90f; a.goalYaw = c.yawToTarget;               // 1: look down, instantly
                if (c.pitch >= 80f) { a.selectSlot = c.windSlot; p = P.SELECT; ticks = 0; }                 // 2: arrived: switch to the wind charge
                else if (ticks > 6) { p = P.IDLE; cooldown = cfg.windCooldownTicks / 2; }
                state = "mace: look down";
            }
            case SELECT -> {
                a.aim = true; a.snapAim = true; a.goalPitch = 90f; a.goalYaw = c.yawToTarget;
                a.selectSlot = c.windSlot;
                if (c.selectedSlot == c.windSlot) {                                                         // 3: in hand: jump and throw together
                    a.jump = true; a.use = true;
                    p = P.FLY; ticks = 0;
                    state = "mace: jump + throw";
                } else if (ticks > 6) { p = P.IDLE; cooldown = cfg.windCooldownTicks / 2; }
                else state = "mace: wind charge";
            }
            case FLY -> {
                a.selectSlot = hold; a.forward = true; a.sprint = false; a.fastAim = true;
                boolean aimed = !cfg.requireCrosshair || c.crosshairOnTarget;
                boolean hurtOk = !cfg.hurtGuard || c.tHurtTime <= 1;
                boolean inReach = c.reachDist <= cfg.reach - cfg.reachMargin;

                // Stun slam: opponent blocking in the air -> axe their shield first (works at any charge), then slam.
                if (cfg.stunSlam && c.tBlocking && !stunDone && c.axeSlot >= 0 && !c.onGround && inReach && aimed) {
                    a.selectSlot = c.axeSlot; a.attack = true;
                    stunDone = true; stunWait = Math.max(0, cfg.stunSlamDelay);
                    state = "mace: stun (axe)";
                    return true;
                }
                if (stunDone && stunWait > 0) { stunWait--; a.selectSlot = hold; state = "mace: stun slam wait"; return true; }

                // We are holding the mace during flight, so use the mace's charge threshold.
                boolean charged = stunDone || c.cooldown >= 0.6f || ticks > 30;
                boolean shielded = c.tBlocking && !stunDone;                                      // a slam into a raised shield does nothing
                boolean smash = !c.onGround && c.velY < 0 && c.fallDistance > 1.5 && inReach && aimed && hurtOk && charged && !shielded;
                if (smash) {
                    a.selectSlot = smashSlot;
                    a.attack = true;
                    p = P.IDLE; cooldown = cfg.windCooldownTicks;
                    awaitHit = 10; smashHealth = c.tHealth;
                    state = "mace: smash";
                } else if ((c.onGround && ticks > 3) || ticks > 80) {
                    p = P.IDLE; cooldown = cfg.windCooldownTicks / 2;
                    state = "mace: no smash (landed)";
                } else {
                    state = !(c.fallDistance > 1.5) ? "mace: rising" : !aimed ? "mace: aiming" : !charged ? "mace: charging" : shielded ? "mace: opponent blocking" : "mace: smash";
                }
            }
            default -> { }
        }
        return true;
    }
}
