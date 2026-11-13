package dev.pvpai.rules;

import dev.pvpai.act.Action;
import dev.pvpai.config.Kit;
import dev.pvpai.config.ModConfig;
import dev.pvpai.observe.Ctx;

/**
 * Mace kit wind-charge smash, one step per tick:
 *  1. AIM    look straight down (an instant snap, so the charge lands at your feet)
 *  2. SELECT switch to the wind charge
 *  3. THROW  jump and throw on the SAME tick
 *  4. FLY    go back to the weapon, steer at the opponent
 *  5. SMASH  hit while falling more than 1.5 blocks
 * Needs a wind charge in the hotbar. With a sword in the hotbar the weapon swap uses the sword's charge (attribute swap).
 */
public final class MaceController {
    private enum P { IDLE, AIM, SELECT, THROW, FLY }

    private P p = P.IDLE;
    private int ticks, cooldown;
    public String state = "";

    public void reset() { p = P.IDLE; ticks = 0; cooldown = 0; state = ""; }

    /** @return true while the combo is driving the action. */
    public boolean apply(Action a, Ctx c, ModConfig cfg) {
        if (cfg.kit != Kit.MACE || !cfg.useWindCharge) { p = P.IDLE; state = ""; return false; }
        if (c.windSlot < 0) { p = P.IDLE; state = "mace: no wind charge in the hotbar"; return false; }
        if (cooldown > 0) cooldown--;
        ticks++;

        int smashSlot = c.maceSlot >= 0 ? c.maceSlot : Utility.weaponSlot(c, cfg);
        boolean swap = cfg.attributeSwap && c.swordSlot >= 0 && smashSlot >= 0 && smashSlot != c.swordSlot;
        int hold = swap ? c.swordSlot : smashSlot;       // what we hold while the weapon charges

        if (p == P.IDLE) {
            boolean go = cooldown == 0 && c.onGround && c.dist >= 1.5 && c.dist <= 12.0 && c.health > cfg.retreatHealth && c.tHurtTime <= 1;
            if (!go) { state = cooldown > 0 ? "mace: cooldown " + cooldown : "mace: waiting"; return false; }
            p = P.AIM; ticks = 0;
        }

        a.attack = false; a.use = false;
        if (p == P.AIM || p == P.SELECT || p == P.THROW) {        // stand still while setting up
            a.forward = false; a.back = false; a.left = false; a.right = false; a.sprint = false; a.jump = false;
        }
        switch (p) {
            case AIM -> {
                a.aim = true; a.snapAim = true; a.goalPitch = 90f; a.goalYaw = c.yawToTarget;     // look down, instantly
                if (c.pitch >= 80f) { p = P.SELECT; ticks = 0; }
                else if (ticks > 6) { p = P.IDLE; cooldown = 40; }
                state = "mace: look down";
            }
            case SELECT -> {
                a.aim = true; a.snapAim = true; a.goalPitch = 90f; a.goalYaw = c.yawToTarget;
                a.selectSlot = c.windSlot;
                if (c.selectedSlot == c.windSlot) { p = P.THROW; ticks = 0; }
                else if (ticks > 6) { p = P.IDLE; cooldown = 40; }
                state = "mace: wind charge";
            }
            case THROW -> {
                a.aim = true; a.goalPitch = 90f; a.goalYaw = c.yawToTarget;
                a.selectSlot = c.windSlot;
                a.jump = true; a.use = true;                                                      // jump + throw at the same time
                p = P.FLY; ticks = 0;
                state = "mace: jump + throw";
            }
            case FLY -> {
                a.selectSlot = hold; a.forward = true; a.sprint = false; a.fastAim = true;
                boolean clear = (!cfg.requireCrosshair || c.crosshairOnTarget) && (!cfg.hurtGuard || c.tHurtTime <= 1) && !c.tBlocking;
                boolean charged = swap ? c.cooldown >= 0.92f : (c.cooldown >= 0.6f || ticks > 30);
                boolean smash = !c.onGround && c.velY < 0 && c.fallDistance > 1.5 && c.reachDist <= cfg.reach - cfg.reachMargin && clear && charged;
                if (smash) {
                    if (swap) a.selectSlot = smashSlot;                                           // attribute swap: sword charge, mace hit
                    a.attack = true; p = P.IDLE; cooldown = 60;
                } else if ((c.onGround && ticks > 3) || ticks > 80) { p = P.IDLE; cooldown = 40; }
                state = "mace: smash";
            }
            default -> { }
        }
        return true;
    }
}
