package dev.pvpai.rules;

import dev.pvpai.act.Action;
import dev.pvpai.config.ModConfig;
import dev.pvpai.config.Strategy;
import dev.pvpai.observe.Ctx;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

/**
 * Modern-PvP utilities layered on top of the combat rules: hotbar management, shield breaking with an axe,
 * auto-totem, and auto-eating. Runs after the combat controller and may override its action.
 */
public final class Utility {
    private enum Shield { IDLE, AXE }

    private final boolean smart;
    private final Random rng = new Random();
    private Shield sp = Shield.IDLE;
    private int blockTicks, delay = -1, shieldCooldown, axeTicks, totemCooldown, eatTicks, eatWait;
    private boolean eating;
    public String state = "idle";

    public Utility(boolean smart) { this.smart = smart; }

    public void reset() {
        sp = Shield.IDLE; blockTicks = 0; delay = -1; shieldCooldown = 0; axeTicks = 0;
        totemCooldown = 0; eatTicks = 0; eatWait = 0; eating = false; state = "idle";
    }

    public void apply(Action a, Ctx c, ModConfig cfg, boolean combatBusy, boolean inReach) {
        if (shieldCooldown > 0) shieldCooldown--;
        if (totemCooldown > 0) totemCooldown--;
        if (eatWait > 0) eatWait--;
        String s = "idle";

        // 1) Totem into the offhand when it is empty or health is low
        if (cfg.autoTotem && totemCooldown == 0 && !c.offhandTotem && c.totemSlot >= 0
                && (c.offhandEmpty || c.health <= cfg.totemHealth)) {
            a.swapTotemFrom = c.totemSlot;
            totemCooldown = 12;
            s = "totem to offhand";
        }

        // 2) Eating: only when the opponent is not close
        int eatHp = cfg.strategy == Strategy.SAFE ? cfg.eatHealth + 4 : cfg.eatHealth;
        boolean wantEat = cfg.autoEat && eatWait == 0 && c.foodSlot >= 0 && c.health <= eatHp && c.health < c.maxHealth;
        if (!eating && wantEat && !combatBusy && c.dist >= 4.0) { eating = true; eatTicks = 0; sp = Shield.IDLE; }
        if (eating) {
            eatTicks++;
            boolean done = eatTicks > 45 || (eatTicks > 6 && !c.usingItem) || c.health >= c.maxHealth - 1;
            if (done || c.dist < 2.6) {
                eating = false; eatWait = 80;
            } else {
                a.selectSlot = c.foodSlot; a.use = true;
                a.attack = false; a.jump = false;
                a.forward = false; a.sprint = false; a.stopSprint = true;
                a.left = false; a.right = false; a.back = c.dist < 6.0;
                state = "eating";
                return;
            }
        }

        // 3) Shield break: opponent has been blocking for a while -> axe, swing when charged, back to the sword
        boolean canBreak = cfg.shieldBreak && c.axeSlot >= 0;
        if (c.tBlocking) blockTicks++; else if (sp == Shield.IDLE) { blockTicks = 0; delay = -1; }
        boolean aimedOk = Math.abs(MathHelper.wrapDegrees(c.yawToTarget - c.yaw)) < 25;

        if (sp == Shield.IDLE) {
            if (canBreak && blockTicks > 0 && delay < 0) {
                int lo = Math.min(cfg.shieldDelayMin, cfg.shieldDelayMax), hi = Math.max(cfg.shieldDelayMin, cfg.shieldDelayMax);
                delay = smart ? lo + rng.nextInt(hi - lo + 1) : lo;   // Strict: fixed. Smart: random in the range.
            }
            if (canBreak && delay >= 0 && blockTicks >= delay && shieldCooldown == 0 && !combatBusy && c.dist <= 4.5) {
                if (smart && rng.nextInt(100) < cfg.shieldFailChance) {   // Smart only: the attempt fumbles
                    shieldCooldown = 40; blockTicks = 0; delay = -1;
                    s = "shield: fumbled";
                } else {
                    sp = Shield.AXE; axeTicks = 0;
                }
            } else if (canBreak && blockTicks > 0) {
                s = "shield: " + blockTicks + "/" + Math.max(0, delay);
            }
        }
        if (sp == Shield.AXE) {
            axeTicks++;
            a.selectSlot = c.axeSlot; a.attack = false; a.use = false;
            s = "shield: axe";
            if (c.holdingAxe && c.cooldown >= 0.92f && inReach && aimedOk) {
                a.attack = true;
                finishShield();
            } else if (!c.tBlocking || axeTicks > 60 || !canBreak) {
                finishShield();   // opponent lowered the shield or it took too long
            }
        }

        // 4) Default hotbar: back to the sword
        if (a.selectSlot < 0 && !eating && sp == Shield.IDLE && c.swordSlot >= 0 && c.selectedSlot != c.swordSlot) {
            a.selectSlot = c.swordSlot;
        }
        state = s;
    }

    private void finishShield() {
        sp = Shield.IDLE; shieldCooldown = 30; blockTicks = 0; delay = -1;
    }
}
