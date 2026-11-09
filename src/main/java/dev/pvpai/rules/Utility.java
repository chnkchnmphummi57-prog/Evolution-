package dev.pvpai.rules;

import dev.pvpai.act.Action;
import dev.pvpai.config.Kit;
import dev.pvpai.config.ModConfig;
import dev.pvpai.config.Strategy;
import dev.pvpai.config.TotemMethod;
import dev.pvpai.observe.Ctx;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

/**
 * Modern-PvP utilities layered on top of the combat rules: weapon/hotbar management (per kit), shield breaking,
 * auto-totem, auto-eating and healing pots. Runs after the combat controller and may override its action.
 */
public final class Utility {
    private enum Shield { IDLE, AXE }

    private final boolean smart;
    private final Random rng = new Random();
    private Shield sp = Shield.IDLE;
    private int blockTicks, delay = -1, shieldCooldown, axeTicks, axeWait, totemWait, totemFails, eatTicks, eatWait, potTicks, potCooldown;
    private boolean eating, potting;
    public String state = "idle";

    public Utility(boolean smart) { this.smart = smart; }

    public void reset() {
        sp = Shield.IDLE; blockTicks = 0; delay = -1; shieldCooldown = 0; axeTicks = 0; axeWait = 0;
        totemWait = 0; totemFails = 0; eatTicks = 0; eatWait = 0; potTicks = 0; potCooldown = 0;
        eating = false; potting = false; state = "idle";
    }

    /** Primary weapon slot for the selected kit (falls back to a sword, then an axe). */
    public static int weaponSlot(Ctx c, ModConfig cfg) {
        int w = switch (cfg.kit) {
            case AXE -> c.axeSlot;
            case MACE -> c.maceSlot;
            default -> c.swordSlot;
        };
        if (w < 0) w = c.swordSlot >= 0 ? c.swordSlot : c.axeSlot;
        return w;
    }

    public void apply(Action a, Ctx c, ModConfig cfg, boolean combatBusy, boolean inReach) {
        if (shieldCooldown > 0) shieldCooldown--;
        if (eatWait > 0) eatWait--;
        if (potCooldown > 0) potCooldown--;
        String s = "idle";

        // 1) Totem: offhand empty or health low. Click-swap first; if that does not stick, fall back to swap-hands.
        boolean totemWanted = cfg.autoTotem && !c.offhandTotem && (c.offhandEmpty || c.health <= cfg.totemHealth);
        if (c.offhandTotem) { totemWait = 0; totemFails = 0; }
        if (totemWanted) {
            if (c.totemSlot < 0) {
                s = "totem: none in inventory";
            } else if (totemWait > 0) {
                totemWait--;
                s = "totem: swapping";
                if (totemWait == 0) totemFails++;           // still not in the offhand after the wait
            } else {
                boolean hand = cfg.totemMethod == TotemMethod.HAND_SWAP || (cfg.totemMethod == TotemMethod.AUTO && totemFails >= 1);
                if (hand && c.totemSlot < 9) {
                    if (c.selectedSlot == c.totemSlot) { a.swapHands = true; totemWait = 4; }
                    else a.selectSlot = c.totemSlot;       // select it now, swap hands next tick
                    s = "totem: swap hands";
                } else {
                    a.swapTotemFrom = c.totemSlot; totemWait = 4;
                    s = "totem: click swap";
                }
            }
        }

        // 2) Eating: only when the opponent is not close
        int eatHp = cfg.eatHealth + (cfg.strategy == Strategy.SAFE ? 4 : 0) + (cfg.kit == Kit.UHC ? 4 : 0);
        boolean wantEat = cfg.autoEat && eatWait == 0 && c.foodSlot >= 0 && c.health <= eatHp && c.health < c.maxHealth;
        if (!eating && wantEat && !combatBusy && !potting && c.dist >= 4.0) { eating = true; eatTicks = 0; sp = Shield.IDLE; }
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

        // 3) Healing pots (Pot / NethPot): look at your feet and throw
        boolean potKit = cfg.healPots && (cfg.kit == Kit.POT || cfg.kit == Kit.NETHPOT);
        if (!potting && potKit && c.potSlot >= 0 && c.health <= cfg.potHealth && potCooldown == 0) { potting = true; potTicks = 0; sp = Shield.IDLE; }
        if (potting) {
            potTicks++;
            a.selectSlot = c.potSlot; a.fastAim = true; a.goalPitch = 90f; a.aim = true; a.attack = false;
            if (c.selectedSlot == c.potSlot && c.pitch >= 70f && potTicks >= 2) { a.use = true; potting = false; potCooldown = 14; }
            else if (potTicks > 14 || c.potSlot < 0) { potting = false; potCooldown = 10; }
            s = "healing pot";
        }

        // 4) Shield break: opponent has been blocking for a while -> axe. An axe disables a shield at ANY charge,
        //    so it swings immediately (Strict: same tick as the switch; Smart: 0-2 ticks later).
        boolean canBreak = cfg.shieldBreak && cfg.kit != Kit.AXE && c.axeSlot >= 0 && !potting;
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
                    sp = Shield.AXE; axeTicks = 0; axeWait = smart ? rng.nextInt(3) : 0;
                }
            } else if (canBreak && blockTicks > 0 && !potting) {
                s = "shield: " + blockTicks + "/" + Math.max(0, delay);
            }
        }
        if (sp == Shield.AXE) {
            axeTicks++;
            a.selectSlot = c.axeSlot; a.attack = false; a.use = false;
            s = "shield: axe";
            if (c.tBlocking && inReach && aimedOk && axeTicks > axeWait) {
                a.attack = true;
                finishShield();
            } else if (!c.tBlocking || axeTicks > 40 || !canBreak) {
                finishShield();   // opponent lowered the shield or it took too long
            }
        }

        // 5) Default hotbar: the kit's weapon
        int weapon = weaponSlot(c, cfg);
        if (a.selectSlot < 0 && !eating && !potting && sp == Shield.IDLE && weapon >= 0 && c.selectedSlot != weapon) {
            a.selectSlot = weapon;
        }
        state = s;
    }

    private void finishShield() {
        sp = Shield.IDLE; shieldCooldown = 30; blockTicks = 0; delay = -1;
    }
}
