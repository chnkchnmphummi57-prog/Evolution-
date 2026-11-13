package dev.pvpai.rules;

import dev.pvpai.act.Action;
import dev.pvpai.config.InputMode;
import dev.pvpai.config.JumpResetMode;
import dev.pvpai.config.ModConfig;
import dev.pvpai.config.Strategy;
import dev.pvpai.observe.Ctx;
import dev.pvpai.observe.FightTracker;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

/**
 * Rule-based fighter: three strategies (Normal / Safe / Crit spam) plus the kept sword techniques:
 * reach spacing, circle strafing, hit-select, W/S/shift taps, uppercut, KB displacement, crit, jump reset, retreat.
 * smart=false (Strict): deterministic, chances become "every Nth". smart=true (Smart): random rolls, aim noise, optional jitter.
 *
 * Hit registration: a swing is only sent when the crosshair ray hits the hitbox, the opponent is out of hurt-immunity,
 * is not behind a raised shield, and is inside (max reach - margin - adaptive penalty).
 * Crit rules (vanilla): the hit must land while falling, off the ground, >90% charged, and NOT SPRINTING.
 */
public final class StrictController {
    public enum Phase { NORMAL, RUN_BACK, KB_HIT, CRIT_PUNISH }
    private enum Tap { NONE, W, S, SHIFT }

    public Phase phase = Phase.NORMAL;
    public int critsFired;
    public String critState = "-";
    private final boolean smart;
    private final Random rng = new Random();
    private final int[] ctr = new int[10];
    private ModConfig cfgRef;
    private int phaseTicks, strafeTick, tapLeft, tapDelay, pendingTicks, attackDelay = -1, critCooldown, jumpCooldown,
            counterTicks, idleTicks, resetPending, punishFails, blockWait, snapTicks, retreatLeft, retreatCooldown,
            upSWait = -1, upAttackIn, kbState, kbWait, kbTicks;
    private boolean strafeRight = true, pendingCrit, cycleRolled, cycleCrit, punishCombo;
    private Tap tapType = Tap.NONE;

    public StrictController(boolean smart) { this.smart = smart; }

    public boolean busy() { return pendingCrit || counterTicks > 0 || kbState != 0 || upAttackIn > 0 || phase == Phase.RUN_BACK || phase == Phase.KB_HIT; }

    public void reset() {
        phase = Phase.NORMAL; phaseTicks = 0; tapLeft = 0; tapDelay = 0; pendingCrit = false; attackDelay = -1; critsFired = 0;
        critCooldown = 0; jumpCooldown = 0; counterTicks = 0; idleTicks = 0; resetPending = 0; punishFails = 0;
        blockWait = 0; snapTicks = 0; retreatLeft = 0; retreatCooldown = 0; upSWait = -1; upAttackIn = 0;
        kbState = 0; kbWait = 0; kbTicks = 0;
        cycleRolled = false; cycleCrit = false; punishCombo = false; tapType = Tap.NONE;
        java.util.Arrays.fill(ctr, 0);
    }

    private void enter(Phase p) {
        phase = p; phaseTicks = 0; pendingCrit = false; tapLeft = 0;
        if (p == Phase.CRIT_PUNISH) {
            punishFails = 0;
            // Crit has priority. Strict always starts with crit spam; Smart picks it with critResponsePriority%.
            punishCombo = smart && cfgRef != null && rng.nextInt(100) >= cfgRef.critResponsePriority;
        }
    }

    public Action decide(Ctx c, FightTracker f, ModConfig cfg) {
        cfgRef = cfg;
        Action a = new Action();
        Strategy st = cfg.strategy;
        boolean spamStrat = st == Strategy.CRIT_SPAM;
        boolean safe = st == Strategy.SAFE;
        double engage = safe ? cfg.engageDist + 0.9 : cfg.engageDist;          // SAFE: hold distance just outside reach
        double minDist = safe ? cfg.minDist + 0.8 : cfg.minDist;
        double retreatHp = safe ? Math.max(cfg.retreatHealth, 10.0) : cfg.retreatHealth;
        int critPct = spamStrat ? 100 : safe ? Math.min(10, cfg.critChancePercent) : cfg.critChancePercent;
        int comboLock = spamStrat ? 99 : cfg.comboLock;

        // Reach: hit at the edge of reach. hitDist is the largest distance we swing from, holdDist is where we wait while recharging
        // (just outside the opponent's own reach).
        double hitDist = cfg.reach - cfg.reachMargin - f.reachPenalty;
        double holdDist = cfg.reach + 0.35;
        boolean inReach = c.reachDist <= hitDist;

        float yawErr = MathHelper.wrapDegrees(c.yawToTarget - c.yaw);
        float pitchErr = c.pitchToTarget - c.pitch;
        a.aim = true;
        a.goalYaw = c.yawToTarget + (smart ? (rng.nextFloat() - 0.5f) * 1.6f : 0f);
        a.goalPitch = MathHelper.clamp(c.pitchToTarget + (smart ? (rng.nextFloat() - 0.5f) : 0f), -90f, 90f);

        if (critCooldown > 0) critCooldown--;
        if (jumpCooldown > 0) jumpCooldown--;

        // Hit-registration gates
        if (c.tBlocking) blockWait++; else blockWait = 0;
        boolean blockHold = cfg.blockGuard && c.tBlocking && blockWait < 100;   // a sword into a raised shield is a ghost hit
        boolean hurtOk = !cfg.hurtGuard || c.tHurtTime <= 1;                    // damage during hurt-immunity is ignored
        boolean aimed = Math.abs(yawErr) < 12 && Math.abs(pitchErr) < 15;
        boolean look = cfg.requireCrosshair ? c.crosshairOnTarget : aimed;
        boolean clear = hurtOk && !blockHold;
        boolean pkt = cfg.inputMode == InputMode.PACKET;
        if (inReach && c.cooldown >= 0.8f && !c.crosshairOnTarget) a.fastAim = true;   // line the crosshair up quickly before a swing
        if (snapTicks > 0) { snapTicks--; a.fastAim = true; }

        // Hit-select / SAFE counter windows
        if ((safe || cfg.hitSelect) && f.tookHitNow) counterTicks = Math.max(counterTicks, safe ? 8 : 6);
        if (safe && f.oppSwingEdge && c.dist < 5.0) counterTicks = Math.max(counterTicks, 10);
        boolean counter = counterTicks > 0;
        if (counter) counterTicks--;
        idleTicks++;

        boolean ready = c.cooldown >= ((counter && cfg.hitSelect) ? 0.85f : 0.92f);
        boolean soon = c.cooldown >= 0.92f - 2f * c.chargePerTick;                // ready within about two ticks
        boolean launchReady = cfg.critAutoLaunch
                ? (0.92f - c.cooldown) / Math.max(0.02f, c.chargePerTick) <= 7.0f
                : c.cooldown >= cfg.critLaunchPercent / 100f;

        // Retreat When Low: turn around and sprint away, but only for retreatSeconds (many servers have no healing), then turn
        // back and fight. A cooldown follows so it cannot loop forever.
        boolean lowHp = cfg.retreatLow && c.health <= retreatHp;
        if (retreatCooldown > 0) retreatCooldown--;
        if (retreatLeft > 0) {
            if (!lowHp) { retreatLeft = 0; snapTicks = 5; }
            else if (--retreatLeft == 0) { retreatCooldown = Math.max(0, cfg.retreatCooldownSeconds) * 20; snapTicks = 5; }
        } else if (lowHp && retreatCooldown == 0) {
            retreatLeft = Math.max(1, cfg.retreatSeconds) * 20;
        }
        boolean retreating = retreatLeft > 0;

        // Jump reset, in EVERY phase: first tick the hit is visible, keep trying for 3 ticks. PREDICT also jumps before an expected hit.
        if (cfg.jump && cfg.jumpReset) {
            boolean go = false;
            if (f.tookHitNow) resetPending = 3;
            if (resetPending > 0) {
                resetPending--;
                if (c.onGround && jumpCooldown == 0) { go = true; resetPending = 0; }
            }
            if (!go && cfg.jumpResetMode == JumpResetMode.PREDICT && c.onGround && jumpCooldown == 0
                    && c.dist <= cfg.reach + 0.6 && f.oppSwingEdge) go = true;
            if (go) { a.jump = true; jumpCooldown = 10; }
        }

        // Phase transitions
        if (retreating) { if (phase != Phase.NORMAL) enter(Phase.NORMAL); }
        else if (phase == Phase.NORMAL && st == Strategy.NORMAL && cfg.critSpamResponse && f.critSpam()) enter(Phase.RUN_BACK);
        phaseTicks++;

        switch (phase) {
            case RUN_BACK -> {
                critState = "paused: run back";
                a.back = true;
                if (cfg.strafe) strafe(a, cfg);
                if (phaseTicks >= cfg.runBackTicks || c.dist >= 4.5) enter(Phase.KB_HIT);
                return a;
            }
            case KB_HIT -> {
                critState = "paused: knockback hit";
                a.forward = true; a.sprint = true;
                if (inReach && look && clear && ready && c.sprinting) { a.attack = true; enter(Phase.CRIT_PUNISH); }
                else if (phaseTicks > 40) enter(Phase.CRIT_PUNISH);
                return a;
            }
            case CRIT_PUNISH -> {
                if (phaseTicks >= cfg.punishTicks) { f.resetSpam(); enter(Phase.NORMAL); }
            }
            default -> { }
        }

        // ---- KB displacement: tick 1 flick 180 (below), tick 2 turn back, then the hit kbHitDelay ticks later ----
        if (kbState != 0) {
            kbTicks++;
            critState = "kb displacement";
            if (kbState == 2) {                                  // tick 2: turn back to the target
                a.snapAim = true;
                kbWait = Math.max(1, cfg.kbHitDelay);
                kbState = 3;
                return a;
            }
            if (--kbWait > 0) return a;                          // wait for the hit tick
            if (inReach && look && clear && c.cooldown >= 0.85f) { a.attack = true; kbState = 0; critCooldown = 2; idleTicks = 0; return a; }
            if (kbTicks > 8) kbState = 0;                        // could not line up: give up
            return a;
        }

        // ---- Movement ----
        if (retreating) {
            a.goalYaw = c.yawToTarget + 180f; a.fastAim = true;       // face away and run
            a.forward = true; a.sprint = true;
        } else if (counter) {
            a.forward = true; a.sprint = true;
        } else if (safe) {
            if (c.dist > engage + 0.4) a.forward = true;
            else if (c.dist < engage - 0.3) a.back = true;
        } else if (cfg.reachSpacing) {
            if (soon || pendingCrit) {                                // about to swing: step in to the edge of reach
                if (c.reachDist > hitDist - 0.05) { a.forward = true; a.sprint = true; }
                else if (c.reachDist < hitDist - 0.7) a.back = true;
            } else {                                                  // recharging: wait just outside their reach
                if (c.reachDist < holdDist - 0.15) a.back = true;
                else if (c.reachDist > holdDist + 0.5) { a.forward = true; a.sprint = true; }
            }
        } else if (c.dist > engage) { a.forward = true; a.sprint = true; }
        else { a.sprint = true; if (c.dist < minDist) a.back = true; else a.forward = true; }

        // Circle strafing: hold A or D (with W) so we orbit the opponent.
        boolean circling = cfg.strafe && !counter && !retreating && c.dist < engage + (safe ? 2.0 : 2.6);
        if (circling) {
            strafe(a, cfg);
            if (!a.back && c.reachDist > holdDist - 0.1) a.forward = true;
        }

        // Taps that start after the hit tick (W-tap, S-tap with one tick delay, later ticks of any tap)
        if (tapDelay > 0 && --tapDelay == 0) tapLeft = Math.max(1, cfg.sTapTicks);
        if (tapLeft > 0) {
            tapLeft--;
            switch (tapType) {
                case S -> { a.forward = false; a.sprint = false; a.stopSprint = true; a.back = true; a.left = false; a.right = false; }
                case W -> { if (!pkt) { a.forward = false; a.sprint = false; a.stopSprint = true; } }
                case SHIFT -> { a.sneak = true; a.sprint = false; a.stopSprint = true; }
                default -> { }
            }
        }

        // ---- Attack ----
        boolean comboMode = phase == Phase.CRIT_PUNISH && punishCombo;
        boolean critAllowed = cfg.crit && cfg.jump && f.combo < comboLock && !counter && !comboMode;
        boolean mayAttack = !safe || counter || idleTicks > 60;
        boolean critHit = false, upHit = false;

        if (upAttackIn > 0 && --upAttackIn == 0) {                    // uppercut with the hit one tick after the jump
            if (inReach && look && clear) { a.attack = true; upHit = true; }
        }

        if (!a.attack) {
            if (pendingCrit) {
                pendingTicks++;
                boolean airborne = !c.onGround;
                if (airborne && c.velY < 0.15) {                      // top of the jump: let go of W so the sprint ends before the hit
                    a.forward = false; a.sprint = false; a.stopSprint = true;
                }
                boolean falling = airborne && c.velY < 0 && c.fallDistance > 0.0 && !c.sprinting;
                if (falling && inReach && look && clear && ready) {
                    a.attack = true; critHit = true; pendingCrit = false; critsFired++; critCooldown = 2;
                } else if (pendingTicks > 16 || (c.onGround && pendingTicks > 2)) {
                    pendingCrit = false; critCooldown = spamStrat ? 6 : 25;   // failed: hit normally for a while
                    if (phase == Phase.CRIT_PUNISH && ++punishFails >= 2) punishCombo = true;
                }
            } else {
                // One roll per attack cycle. The jump may START below full charge so the falling hit is charged in time.
                boolean cycleReady = critAllowed && critCooldown == 0 && jumpCooldown == 0 && c.onGround && mayAttack && aimed
                        && c.reachDist <= hitDist + 0.8 && launchReady && clear;
                if (cycleReady && !cycleRolled) { cycleRolled = true; cycleCrit = phase == Phase.CRIT_PUNISH || chance(critPct, 0); }
                if (cycleReady && cycleCrit) {
                    a.jump = true; pendingCrit = true; pendingTicks = 0; jumpCooldown = 10; cycleCrit = false;
                } else if (inReach && look && ready && mayAttack && clear) {
                    boolean uppercut = cfg.uppercut && cfg.jump && !spamStrat && !safe && !counter && !retreating && c.onGround
                            && jumpCooldown == 0 && chance(cfg.uppercutChance, 7);
                    boolean kbFlick = !uppercut && cfg.kbDisplace && !spamStrat && !safe && !counter && chance(cfg.kbDisplaceChance, 6);
                    if (uppercut) {
                        a.jump = true; jumpCooldown = 10; upSWait = Math.max(0, cfg.uppercutSTapDelay);
                        if (cfg.uppercutHitDelay <= 0) { a.attack = true; upHit = true; }   // jump and hit on the same tick
                        else upAttackIn = 1;                                                   // hit one tick after the jump
                    } else if (kbFlick) {
                        kbState = 2; kbTicks = 0;                                  // tick 1 is this one: the flick
                        a.goalYaw = c.yawToTarget + 180f; a.snapAim = true;                   // flick now, turn back next tick
                        a.forward = false; a.back = false; a.left = false; a.right = false; a.sprint = false;
                        critState = "kb displacement";
                        return a;
                    } else {
                        if (smart && cfg.reactionJitter > 0 && attackDelay < 0) attackDelay = rng.nextInt(cfg.reactionJitter + 1);
                        if (smart && attackDelay-- > 0) {
                            // human-like hesitation (off by default: reaction jitter 0)
                        } else {
                            attackDelay = -1;
                            a.attack = true;                       // first tick the swing is possible: no extra delay
                        }
                    }
                } else attackDelay = -1;
            }
        }

        if (a.attack) { idleTicks = 0; cycleRolled = false; cycleCrit = false; }

        // Uppercut: S is pressed upSWait ticks after the jump (0 = at the same time as the jump)
        if (upSWait == 0) { tapType = Tap.S; tapLeft = Math.max(1, cfg.sTapTicks); a.back = true; a.forward = false; a.sprint = false; upSWait = -1; }
        else if (upSWait > 0) upSWait--;

        // What follows a normal hit. The tap starts on the SAME tick as the hit (S-tap can be delayed one tick).
        // The sprint is cancelled only after the click has been processed, so the hit itself is still a sprint hit.
        if (a.attack && !critHit && !upHit) {
            if (safe) { tapType = Tap.S; tapLeft = Math.max(2, cfg.sTapTicks); counterTicks = 0; }   // counter, then back off
            else if (!spamStrat) {
                Tap pick = Tap.NONE;
                boolean otherTaps = cfg.wTap || cfg.shiftTap;
                if (cfg.sTap && c.dist <= cfg.sTapMaxDist && (!otherTaps || chance(cfg.sTapChance, 1))) pick = Tap.S;
                else if (cfg.shiftTap && chance(cfg.shiftTapChance, 2)) pick = Tap.SHIFT;
                if (pick == Tap.NONE && cfg.wTap) pick = Tap.W;
                tapType = pick;
                switch (pick) {
                    case S -> {
                        if (cfg.sTapDelay <= 0) { a.back = true; a.forward = false; tapLeft = Math.max(0, cfg.sTapTicks - 1); }
                        else { tapDelay = cfg.sTapDelay; tapLeft = 0; }
                    }
                    case SHIFT -> { a.sneak = true; tapLeft = Math.max(0, cfg.shiftTapTicks - 1); }
                    case W -> tapLeft = Math.max(1, cfg.wTapTicks);
                    default -> tapLeft = 0;
                }
            }
        }

        // SAFE: keep the shield up while waiting
        if (safe && cfg.safeBlock && c.offhandShield && !counter && !a.attack && !pendingCrit && c.dist < engage + 1.2) a.use = true;

        critState = !(cfg.crit && cfg.jump) ? "off (Crit/Jump toggle)"
                : retreating ? "retreating (" + (retreatLeft / 20 + 1) + "s)"
                : comboMode ? "response: combo"
                : f.combo >= comboLock ? "combo lock (" + f.combo + ")"
                : counter ? "counter window"
                : pendingCrit ? "in the air"
                : critCooldown > 0 ? "retry in " + critCooldown
                : blockHold ? "opponent blocking"
                : launchReady ? "armed" : "charging " + (int) (c.cooldown * 100) + "%";
        return a;
    }

    /** Strict: every Nth time (first one fires). Smart: random. */
    private boolean chance(int pct, int idx) {
        if (pct <= 0) return false;
        if (pct >= 100) return true;
        if (smart) return rng.nextInt(100) < pct;
        int period = Math.max(1, Math.round(100f / pct));
        return (ctr[idx]++ % period) == 0;
    }

    private void strafe(Action a, ModConfig cfg) {
        int period = Math.max(6, cfg.circleTicks);
        if (smart) period += rng.nextInt(5);
        if (++strafeTick >= period) { strafeTick = 0; strafeRight = !strafeRight; }
        a.right = strafeRight; a.left = !strafeRight;
    }
}
