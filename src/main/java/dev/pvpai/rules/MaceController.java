package dev.pvpai.rules;

import dev.pvpai.act.Action;
import dev.pvpai.config.Kit;
import dev.pvpai.config.ModConfig;
import dev.pvpai.observe.Ctx;

/**
 * EXPERIMENTAL. Mace kit: jump, throw a wind charge at your feet near the top of the jump, go back to the sword to keep
 * charging, then swap to the mace on the hit tick (attribute swap) and smash on the way down (fall > 1.5 blocks).
 * Without a sword it falls back to holding the mace and waiting for its charge. Needs a mace and wind charges in the hotbar.
 */
public final class MaceController {
    private enum P { IDLE, JUMP, ASCEND, THROWN, FLY }

    private P p = P.IDLE;
    private int ticks, cooldown;
    public String state = "";

    public void reset() { p = P.IDLE; ticks = 0; cooldown = 0; state = ""; }

    /** @return true while the smash combo is driving the action. */
    public boolean apply(Action a, Ctx c, ModConfig cfg) {
        if (cfg.kit != Kit.MACE || !cfg.useWindCharge || c.maceSlot < 0 || c.windSlot < 0) { p = P.IDLE; state = ""; return false; }
        if (cooldown > 0) cooldown--;
        ticks++;
        boolean swap = cfg.attributeSwap && c.swordSlot >= 0;
        int hold = swap ? c.swordSlot : c.maceSlot;      // what we hold while charging

        if (p == P.IDLE) {
            boolean go = cooldown == 0 && c.onGround && c.dist >= 2.5 && c.dist <= 9.0 && c.health > cfg.retreatHealth;
            if (!go) { state = ""; return false; }
            p = P.JUMP; ticks = 0;
        }

        a.attack = false; a.use = false;
        switch (p) {
            case JUMP -> {
                a.selectSlot = c.windSlot; a.jump = true; a.forward = true; a.sprint = true;
                p = P.ASCEND; ticks = 0;
                state = "mace: jump";
            }
            case ASCEND -> {
                a.selectSlot = c.windSlot; a.forward = true;
                a.aim = true; a.fastAim = true; a.goalPitch = 90f;          // look at your feet
                if (!c.onGround && c.pitch >= 75f && c.selectedSlot == c.windSlot) { a.use = true; p = P.THROWN; ticks = 0; }
                else if (ticks > 14 || (c.onGround && ticks > 3)) { p = P.IDLE; cooldown = 40; }
                state = "mace: wind charge";
            }
            case THROWN -> {
                a.selectSlot = hold; a.forward = true;
                if (ticks >= 1) { p = P.FLY; ticks = 0; }
                state = "mace: boost";
            }
            case FLY -> {
                a.selectSlot = hold; a.forward = true; a.sprint = false; a.fastAim = true;
                boolean clear = (!cfg.requireCrosshair || c.crosshairOnTarget) && (!cfg.hurtGuard || c.tHurtTime <= 1) && !c.tBlocking;
                boolean charged = swap ? c.cooldown >= 0.92f : (c.cooldown >= 0.6f || ticks > 30);
                boolean smash = !c.onGround && c.velY < 0 && c.fallDistance > 1.5 && c.reachDist <= cfg.reach - cfg.reachMargin
                        && clear && charged;
                if (smash) {
                    if (swap) a.selectSlot = c.maceSlot;                    // attribute swap: sword charge, mace hit
                    a.attack = true; p = P.IDLE; cooldown = 60;
                } else if ((c.onGround && ticks > 3) || ticks > 70) { p = P.IDLE; cooldown = 40; }
                state = "mace: smash";
            }
            default -> { }
        }
        return true;
    }
}
