package dev.pvpai.rules;

import dev.pvpai.act.Action;
import dev.pvpai.config.Kit;
import dev.pvpai.config.ModConfig;
import dev.pvpai.config.Strategy;
import dev.pvpai.config.TotemMethod;
import dev.pvpai.observe.Ctx;

import java.util.Random;

/**
 * Modern-PvP utilities layered on top of the combat rules: weapon/hotbar management per kit, attribute swap,
 * shield breaking, auto-totem, auto-eating, healing pots and cobwebs. Runs after the combat controller and may override its action.
 */
public final class Utility {
    private enum Shield { IDLE, AXE }

    private final boolean smart;
    private final Random rng = new Random();
    private Shield sp = Shield.IDLE;
    private int blockTicks, delay = -1, shieldCooldown, axeTicks, axeWait, totemWait, totemFails, eatTicks, eatWait, potTicks, potCooldown,
            webCooldown, webPrep, swapCheck, swapFails;
    private float swapHealth;
    private boolean eating, potting, swapDisabled;
    public String state = "idle";

    public Utility(boolean smart) { this.smart = smart; }

    public void reset() {
        sp = Shield.IDLE; blockTicks = 0; delay = -1; shieldCooldown = 0; axeTicks = 0; axeWait = 0;
        totemWait = 0; totemFails = 0; eatTicks = 0; eatWait = 0; potTicks = 0; potCooldown = 0;
        webCooldown = 0; webPrep = 0; swapCheck = 0; swapFails = 0; swapDisabled = false;
        eating = false; potting = false; state = "idle";
    }

    /** The kit's heavy weapon slot (axe / mace), or the sword for the other kits. Falls back to sword, then axe. */
    public static int weaponSlot(Ctx c, ModConfig cfg) {
        int w = switch (cfg.kit) {
            case AXE -> c.axeSlot;
            case MACE -> c.maceSlot;
            default -> c.swordSlot;
        };
        if (w < 0) w = c.swordSlot >= 0 ? c.swordSlot : c.axeSlot;
        return w;
    }

    private static boolean heavyKit(ModConfig cfg) { return cfg.kit == Kit.AXE || cfg.kit == Kit.MACE; }

    /**
     * Attribute swap: the slow weapon's charge is a long wait (axe ~20 ticks, mace ~33). The server computes a hit with the
     * charge built up by the item held BEFORE the swap, so we hold the sword while charging and swap to the heavy weapon
     * on the same tick as the attack. A watchdog turns it off if hits stop doing damage (server fixed it / not supported).
     */
    public boolean attributeSwapActive(Ctx c, ModConfig cfg) {
        return cfg.attributeSwap && !swapDisabled && heavyKit(cfg) && c.swordSlot >= 0 && weaponSlot(c, cfg) >= 0
                && weaponSlot(c, cfg) != c.swordSlot;
    }

    /** Slot to hold between hits. */
    public int idleSlot(Ctx c, ModConfig cfg) {
        return attributeSwapActive(c, cfg) ? c.swordSlot : weaponSlot(c, cfg);
    }

    public void apply(Action a, Ctx c, ModConfig cfg, boolean combatBusy, boolean inReach) {
        if (shieldCooldown > 0) shieldCooldown--;
        if (eatWait > 0) eatWait--;
        if (potCooldown > 0) potCooldown--;
        if (webCooldown > 0) webCooldown--;
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
                if (totemWait == 0) totemFails++;
            } else {
                boolean hand = cfg.totemMethod == TotemMethod.HAND_SWAP || (cfg.totemMethod == TotemMethod.AUTO && totemFails >= 1);
                if (hand && c.totemSlot < 9) {
                    if (c.selectedSlot == c.totemSlot) { a.swapHands = true; totemWait = 4; }
                    else a.selectSlot = c.totemSlot;
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

        // 4) Cobweb on the opponent's feet while the weapon recharges. Combat then crit-spams the stuck opponent.
        boolean webWanted = cfg.autoWeb && c.webSlot >= 0 && c.tGround != null && !c.tInWeb && c.tOnGround && webCooldown == 0
                && !combatBusy && !eating && !potting && sp == Shield.IDLE && c.dist >= 1.8 && c.dist <= 4.4 && c.cooldown < 0.7f;
        if (webPrep > 0) webPrep--;
        boolean webBusy = false;
        if (webWanted || webPrep > 0) {
            webBusy = true;
            a.attack = false;
            if (c.selectedSlot != c.webSlot) { a.selectSlot = c.webSlot; webPrep = 3; s = "web: selecting"; }
            else if (c.tGround != null && !c.tInWeb) { a.webGround = c.tGround; webCooldown = 60; webPrep = 0; s = "web: placed"; }
        }

        // 5) Shield break: opponent has been blocking for a while -> axe. An axe disables a shield at ANY charge,
        //    so it swings immediately (Strict: same tick as the switch; Smart: 0-2 ticks later).
        boolean canBreak = cfg.shieldBreak && cfg.kit != Kit.AXE && c.axeSlot >= 0 && !potting && !webBusy;
        if (c.tBlocking) blockTicks++; else if (sp == Shield.IDLE) { blockTicks = 0; delay = -1; }
        boolean aimedOk = cfg.requireCrosshair ? c.crosshairOnTarget : Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(c.yawToTarget - c.yaw)) < 25;
        boolean hurtOk = !cfg.hurtGuard || c.tHurtTime <= 1;

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
            if (c.tBlocking && inReach && aimedOk && hurtOk && axeTicks > axeWait) {
                a.attack = true;
                finishShield();
            } else if (!c.tBlocking || axeTicks > 40 || !canBreak) {
                finishShield();   // opponent lowered the shield or it took too long
            }
        }

        // 6) Attribute swap on the hit tick (Axe / Mace kits), with a watchdog on whether the hits still do damage
        if (swapCheck > 0 && --swapCheck == 0) {
            if (c.tHealth < swapHealth - 0.01f) swapFails = Math.max(0, swapFails - 1);
            else if (!c.tBlocking && ++swapFails >= 3) swapDisabled = true;
        }
        if (attributeSwapActive(c, cfg) && a.attack && sp == Shield.IDLE && !eating && !potting && !webBusy
                && a.selectSlot < 0 && c.selectedSlot == c.swordSlot) {
            a.selectSlot = weaponSlot(c, cfg);
            swapCheck = 6; swapHealth = c.tHealth;
            s = "attr swap hit";
        }
        if (swapDisabled && heavyKit(cfg)) s = "attr swap: off (hits not registering)";

        // 7) Default hotbar: the weapon to hold between hits
        int idle = idleSlot(c, cfg);
        if (a.selectSlot < 0 && !eating && !potting && !webBusy && sp == Shield.IDLE && idle >= 0 && c.selectedSlot != idle) {
            a.selectSlot = idle;
        }
        state = s;
    }

    private void finishShield() {
        sp = Shield.IDLE; shieldCooldown = 30; blockTicks = 0; delay = -1;
    }
}
