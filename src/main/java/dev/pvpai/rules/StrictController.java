package dev.pvpai.rules;

import dev.pvpai.act.Action;
import dev.pvpai.config.InputMode;
import dev.pvpai.config.JumpResetMode;
import dev.pvpai.config.ModConfig;
import dev.pvpai.config.StrafeStyle;
import dev.pvpai.config.Strategy;
import dev.pvpai.observe.Ctx;
import dev.pvpai.observe.FightTracker;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

/**
 * Rule-based fighter: three strategies (Normal / Safe / Crit spam) plus the sword techniques.
 * smart=false (Strict): deterministic, chances become "every Nth". smart=true (Smart): random rolls, aim noise, hesitation.
 *
 * Hit registration: a swing is only sent when the crosshair ray hits the hitbox, the opponent is out of hurt-immunity,
 * is not behind a raised shield, and is inside (max reach - margin - adaptive penalty). That is what stops ghost hits.
 *
 * Crit rules (vanilla): the hit must land while falling, off the ground, >90% charged, and NOT SPRINTING.
 */
public final class StrictController {
    public enum Phase { NORMAL, RUN_BACK, KB_HIT, CRIT_PUNISH, TURN_BACK }
    private enum Tap { NONE, W, S, SHIFT, BLOCK }
    private enum Jump { CRIT, DELAYED_CRIT, SPRINT_CUT }

    public Phase phase = Phase.NORMAL;
    public int critsFired;
    public String critState = "-";
    private final boolean smart;
    private final Random rng = new Random();
    private final int[] ctr = new int[10];
    private ModConfig cfgRef;
    private int phaseTicks, strafeTick, styleTick, tapLeft, pendingTicks, attackDelay = -1, critCooldown, jumpCooldown,
            counterTicks, idleTicks, resetPending, punishFails, blockWait, spacingWait, turnCooldown, turnProbe, snapTicks, shakeLeft, shakeSign = 1,
            retreatLeft, retreatCooldown, uppercutTap;
    private boolean strafeRight = true, pendingCrit, cycleRolled, cycleCrit, punishCombo;
    private Tap tapType = Tap.NONE;
    private Jump kind = Jump.CRIT;

    public StrictController(boolean smart) { this.smart = smart; }

    public boolean busy() { return pendingCrit || counterTicks > 0 || phase == Phase.RUN_BACK || phase == Phase.KB_HIT || phase == Phase.TURN_BACK; }

    public void reset() {
        phase = Phase.NORMAL; phaseTicks = 0; tapLeft = 0; pendingCrit = false; attackDelay = -1; critsFired = 0;
        critCooldown = 0; jumpCooldown = 0; counterTicks = 0; idleTicks = 0; resetPending = 0; punishFails = 0;
        blockWait = 0; spacingWait = 0; turnCooldown = 0; turnProbe = 0; snapTicks = 0; shakeLeft = 0;
        retreatLeft = 0; retreatCooldown = 0; uppercutTap = 0;
        cycleRolled = false; cycleCrit = false; punishCombo = false; tapType = Tap.NONE; kind = Jump.CRIT;
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
        boolean webbed = cfg.webSpamCrit && c.tInWeb;                 // opponent stuck in a cobweb: crit every hit
        boolean spamStrat = webbed || st == Strategy.CRIT_SPAM;
        boolean safe = st == Strategy.SAFE && !webbed;
        boolean pin = cfg.wallPin && c.tWallBehind && !safe && !webbed;   // opponent against a wall: stay on them
        double engage = safe ? cfg.engageDist + 0.9 : pin ? Math.min(cfg.engageDist, 2.4) : cfg.engageDist;
        double minDist = safe ? cfg.minDist + 0.8 : webbed ? Math.max(cfg.minDist, 2.1) : cfg.minDist;   // never step into the web
        double retreatHp = safe ? Math.max(cfg.retreatHealth, 10.0) : cfg.retreatHealth;
        int critPct = spamStrat ? 100 : safe ? Math.min(10, cfg.critChancePercent) : cfg.critChancePercent;
        int comboLock = spamStrat ? 99 : cfg.comboLock;

        // Reach and aim. Corner hitting: near max reach, aim at the hitbox edge closest to us.
        double reachUse = cfg.reach - cfg.reachMargin - f.reachPenalty;
        boolean inReach = c.reachDist <= reachUse;
        boolean corner = cfg.cornerHit && c.hasEdge && c.reachDist > reachUse - 0.4;
        float aimYaw = corner ? c.edgeYaw : c.yawToTarget;
        float aimPitch = corner ? c.edgePitch : c.pitchToTarget;
        float yawErr = MathHelper.wrapDegrees(aimYaw - c.yaw);
        float pitchErr = aimPitch - c.pitch;
        a.aim = true;
        a.goalYaw = aimYaw + (smart ? (rng.nextFloat() - 0.5f) * 1.6f : 0f);
        a.goalPitch = MathHelper.clamp(aimPitch + (smart ? (rng.nextFloat() - 0.5f) : 0f), -90f, 90f);

        if (critCooldown > 0) critCooldown--;
        if (jumpCooldown > 0) jumpCooldown--;
        if (turnCooldown > 0) turnCooldown--;

        // Hit-registration gates
        if (c.tBlocking) blockWait++; else blockWait = 0;
        boolean blockHold = cfg.blockGuard && c.tBlocking && blockWait < 100;   // a sword into a raised shield is a ghost hit
        boolean hurtOk = !cfg.hurtGuard || c.tHurtTime <= 1;                    // damage during hurt-immunity is ignored
        boolean aimed = Math.abs(yawErr) < 12 && Math.abs(pitchErr) < 15;
        boolean aimedAir = Math.abs(yawErr) < 30 && Math.abs(pitchErr) < 50;
        boolean look = cfg.requireCrosshair ? c.crosshairOnTarget : aimed;
        boolean lookAir = cfg.requireCrosshair ? c.crosshairOnTarget : aimedAir;
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
        boolean launchReady = cfg.critAutoLaunch
                ? (0.92f - c.cooldown) / Math.max(0.02f, c.chargePerTick) <= 7.0f      // flight time to the falling phase is ~7 ticks
                : c.cooldown >= cfg.critLaunchPercent / 100f;
        // Retreat When Low: turn around and sprint away, but only for retreatSeconds (many servers have no healing), then turn
        // back and fight. After that there is a cooldown before the next retreat so it cannot loop forever.
        boolean lowHp = cfg.retreatLow && c.health <= retreatHp;
        if (retreatCooldown > 0) retreatCooldown--;
        if (retreatLeft > 0) {
            if (!lowHp) { retreatLeft = 0; snapTicks = 5; }                                   // healed: stop running
            else if (--retreatLeft == 0) {                                                    // time is up: turn back
                retreatCooldown = Math.max(0, cfg.retreatCooldownSeconds) * 20;
                snapTicks = 5;
            }
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
                    && c.dist <= cfg.reach + 0.6 && (f.oppSwingEdge || (!c.tOnGround && c.tVelY < -0.12))) go = true;
            if (go) { a.jump = true; jumpCooldown = 10; }
        }

        // Phase transitions
        if (retreating) enter(Phase.NORMAL);
        else if (phase == Phase.NORMAL && st == Strategy.NORMAL && !webbed && cfg.critSpamResponse && f.critSpam()) enter(Phase.RUN_BACK);
        else if (phase == Phase.NORMAL && cfg.turnBack && !safe && !pin && !spamStrat && turnCooldown == 0 && c.dist < 1.6
                && (++turnProbe % 10 == 0) && chance(cfg.turnBackChance, 5)) enter(Phase.TURN_BACK);
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
            case TURN_BACK -> {                       // whip 180, run about a block, snap back
                critState = "paused: turn-back";
                a.goalYaw = c.yawToTarget + 180f; a.fastAim = true;
                a.forward = true; a.sprint = true;
                if (phaseTicks >= 6) { enter(Phase.NORMAL); turnCooldown = 120; snapTicks = 4; }
                return a;
            }
            case CRIT_PUNISH -> {
                if (phaseTicks >= cfg.punishTicks) { f.resetSpam(); enter(Phase.NORMAL); }
            }
            default -> { }
        }

        // Movement
        if (retreating) {
            a.goalYaw = c.yawToTarget + 180f; a.fastAim = true;       // face away and run
            a.forward = true; a.sprint = true;
        }
        else if (counter) { a.forward = true; a.sprint = true; }
        else if (safe) {
            if (c.dist > engage + 0.4) a.forward = true;
            else if (c.dist < engage - 0.3) a.back = true;
        }
        else if (c.dist > engage) { a.forward = true; a.sprint = true; }
        else { a.sprint = true; if (c.dist < minDist) a.back = true; else a.forward = true; }

        // Taps after a hit. W: release W. S: release W, press S. Shift: sneak (forward stays held). Block-hit: raise the shield.
        // In packet mode the W-tap is a sprint-reset packet sent with the hit instead of a key release.
        if (uppercutTap > 0 && --uppercutTap == 0) { tapType = Tap.S; tapLeft = Math.max(1, cfg.sTapTicks); }
        if (tapLeft > 0) {
            tapLeft--;
            switch (tapType) {
                case S -> { a.forward = false; a.sprint = false; a.stopSprint = true; a.back = true; }
                case W -> { if (!pkt) { a.forward = false; a.sprint = false; a.stopSprint = true; } }
                case SHIFT -> { a.sneak = true; a.sprint = false; a.stopSprint = true; }
                case BLOCK -> { a.use = true; }
                default -> { }
            }
        }
        if (cfg.strafe && !counter && !pin && !webbed && !retreating && c.dist < engage + (safe ? 2.0 : 1.5)) strafe(a, cfg);

        // Crosshair shake: flick away and back after a hit (decoys the opponent's tracking)
        if (shakeLeft > 0) { shakeLeft--; if (shakeLeft >= 1) a.goalYaw += shakeSign * 28f; a.fastAim = true; }

        // Attack
        boolean comboMode = phase == Phase.CRIT_PUNISH && punishCombo;
        boolean critAllowed = cfg.crit && cfg.jump && f.combo < comboLock && !counter && !comboMode;
        boolean mayAttack = !safe || counter || idleTicks > 60;
        boolean sweepWanted = cfg.sweepDisrupt && c.nearbyOthers > 0 && !safe;
        boolean critHit = false, upHit = false;

        // P-crit: hit them the moment they jump, before their crit can start
        if (cfg.pCrit && !safe && !spamStrat && f.oppJumpEdge && c.onGround && !pendingCrit && inReach && look && clear && c.cooldown >= 0.85f) {
            a.attack = true;
        }

        if (!a.attack) {
            if (pendingCrit) {
                pendingTicks++;
                boolean airborne = !c.onGround;
                boolean noSprintKind = kind == Jump.CRIT || kind == Jump.DELAYED_CRIT;
                if (noSprintKind && airborne && c.velY < 0.15) {        // top of the jump: let go of W so the sprint ends before the hit
                    a.forward = false; a.sprint = false; a.stopSprint = true;
                }
                if (!noSprintKind) { a.forward = true; a.sprint = true; }
                boolean timing = switch (kind) {
                    case CRIT -> airborne && c.velY < 0 && c.fallDistance > 0.0 && !c.sprinting;
                    case DELAYED_CRIT -> airborne && c.velY < 0 && c.fallDistance > 0.0 && !c.sprinting && (c.groundGap < 0.75 || pendingTicks > 12);
                    case SPRINT_CUT -> airborne && c.velY <= 0.03 && c.velY > -0.25 && c.sprinting;   // the turn from rising to falling
                };
                if (timing && inReach && lookAir && clear && ready) {
                    a.attack = true; pendingCrit = false; critCooldown = 2;
                    if (noSprintKind) { critHit = true; critsFired++; }
                } else if (pendingTicks > 16 || (c.onGround && pendingTicks > 2)) {
                    pendingCrit = false; critCooldown = spamStrat ? 6 : 25;   // failed: hit normally for a while
                    if (phase == Phase.CRIT_PUNISH && ++punishFails >= 2) punishCombo = true;   // crit not working: switch to combo
                }
            } else {
                // One roll per attack cycle. The jump may START below full charge so the falling hit is charged in time.
                boolean cycleReady = critAllowed && critCooldown == 0 && jumpCooldown == 0 && c.onGround && mayAttack && aimed
                        && c.reachDist <= reachUse + 0.8 && launchReady && clear;
                if (cycleReady && !cycleRolled) { cycleRolled = true; cycleCrit = phase == Phase.CRIT_PUNISH || chance(critPct, 0); }
                if (cycleReady && cycleCrit) {
                    kind = spamStrat ? Jump.CRIT : pickJump(c, cfg);
                    a.jump = true; pendingCrit = true; pendingTicks = 0; jumpCooldown = 10; cycleCrit = false;
                } else if (inReach && look && ready && mayAttack && clear) {
                    if (cfg.uppercut && cfg.jump && !spamStrat && !safe && !counter && !retreating && c.onGround && jumpCooldown == 0
                            && chance(cfg.uppercutChance, 7)) {
                        a.jump = true; a.attack = true; jumpCooldown = 10;     // jump and hit on the same tick: launches them upward
                        upHit = true; uppercutTap = Math.max(1, cfg.uppercutSTapDelay);
                    } else if (cfg.delayedHit && !safe && !counter && c.reachDist < reachUse - 0.7 && spacingWait < 5) {
                        spacingWait++;                             // step back a little and hit at max reach instead
                        a.back = true; a.forward = false; a.sprint = false;
                    } else if (sweepWanted && c.sprinting) {
                        a.forward = false; a.sprint = false; a.stopSprint = true;   // sweep needs a non-sprint hit; swing next tick
                    } else {
                        if (smart && attackDelay < 0) attackDelay = rng.nextInt(3);
                        if (smart && attackDelay-- > 0) {
                            // human-like hesitation
                        } else {
                            attackDelay = -1;
                            a.attack = true;
                        }
                    }
                } else { attackDelay = -1; spacingWait = 0; }
            }
        }

        if (a.attack) { idleTicks = 0; cycleRolled = false; cycleCrit = false; spacingWait = 0; }

        // What follows a normal hit
        if (a.attack && !critHit && !upHit) {
            if (safe) { tapType = Tap.S; tapLeft = Math.max(2, cfg.sTapTicks); counterTicks = 0; }   // counter, then back off
            else if (!spamStrat) {
                Tap pick = Tap.NONE;
                if (!pin) {
                    boolean otherTaps = cfg.wTap || cfg.shiftTap || (cfg.blockHit && c.offhandShield);
                    if (cfg.sTap && c.dist <= cfg.sTapMaxDist && (!otherTaps || chance(cfg.sTapChance, 1))) pick = Tap.S;
                    else if (cfg.shiftTap && chance(cfg.shiftTapChance, 2)) pick = Tap.SHIFT;
                    else if (cfg.blockHit && c.offhandShield && chance(cfg.blockHitChance, 3)) pick = Tap.BLOCK;
                }
                if (pick == Tap.NONE && cfg.wTap) pick = Tap.W;
                tapType = pick;
                tapLeft = switch (pick) {
                    case S -> Math.max(1, cfg.sTapTicks);
                    case SHIFT -> Math.max(1, cfg.shiftTapTicks);
                    case BLOCK -> Math.max(1, cfg.blockHitTicks);
                    case W -> Math.max(1, cfg.wTapTicks);
                    default -> 0;
                };
            }
            if (cfg.crosshairShake && chance(30, 6)) { shakeLeft = 3; shakeSign = smart ? (rng.nextBoolean() ? 1 : -1) : -shakeSign; }
        }

        // SAFE: keep the shield up while waiting
        if (safe && cfg.safeBlock && c.offhandShield && !counter && !a.attack && !pendingCrit && c.dist < engage + 1.2) a.use = true;

        critState = !(cfg.crit && cfg.jump) ? "off (Crit/Jump toggle)"
                : retreating ? "retreating (" + (retreatLeft / 20 + 1) + "s)"
                : webbed ? "web: crit spam"
                : comboMode ? "response: combo"
                : f.combo >= comboLock ? "combo lock (" + f.combo + ")"
                : counter ? "counter window"
                : pendingCrit ? "in the air (" + kind.name().toLowerCase().replace('_', ' ') + ")"
                : critCooldown > 0 ? "retry in " + critCooldown
                : blockHold ? "opponent blocking"
                : launchReady ? "armed" : "charging " + (int) (c.cooldown * 100) + "%";
        return a;
    }

    private Jump pickJump(Ctx c, ModConfig cfg) {
        boolean charged = c.cooldown >= 0.85f;
        if (cfg.sprintCut && charged && chance(cfg.sprintCutChance, 4)) return Jump.SPRINT_CUT;
        if (cfg.delayedCrit && chance(cfg.delayedCritChance, 8)) return Jump.DELAYED_CRIT;
        return Jump.CRIT;
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
        StrafeStyle style = cfg.strafeStyle;
        if (style == StrafeStyle.MIXED) style = ((styleTick++ / 90) % 2 == 0) ? StrafeStyle.CIRCLE : StrafeStyle.JITTER;
        int period = switch (style) {
            case CIRCLE -> Math.max(10, cfg.circleTicks);      // long runs: orbit the opponent
            case JITTER -> Math.max(1, cfg.jitterTicks);       // rapid A/D taps
            default -> cfg.strafePeriodTicks;
        };
        if (smart && style != StrafeStyle.JITTER) period += rng.nextInt(3);
        if (++strafeTick >= period) { strafeTick = 0; strafeRight = !strafeRight; }
        a.right = strafeRight; a.left = !strafeRight;
    }
}
