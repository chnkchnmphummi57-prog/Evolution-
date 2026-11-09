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
 * Rule-based fighter with three strategies.
 *  NORMAL     - pressure with crit chance, taps, jump reset, and the crit-spam response.
 *  SAFE       - hold just outside reach (shield up), wait for the opponent to swing or hit, counter, then back off.
 *  CRIT_SPAM  - jump-crit on every attack, no combo lock.
 * smart=false (Strict): deterministic, chances become "every Nth". smart=true (Smart): random rolls, aim noise, hesitation.
 *
 * Crit rules (vanilla): the hit must land while falling, off the ground, >90% charged, and NOT SPRINTING.
 * The jump takes ~7 ticks to reach the falling phase, so it can START at a lower charge (critLaunchPercent).
 */
public final class StrictController {
    public enum Phase { NORMAL, RUN_BACK, KB_HIT, CRIT_PUNISH }

    public Phase phase = Phase.NORMAL;
    public int critsFired;
    public String critState = "-";
    private final boolean smart;
    private final Random rng = new Random();
    private ModConfig cfgRef;
    private int phaseTicks, strafeTick, tapLeft, critCounter, sTapCounter, pendingTicks, attackDelay = -1,
            critCooldown, jumpCooldown, counterTicks, idleTicks, resetPending, punishFails;
    private boolean strafeRight = true, pendingCrit, tapIsS, cycleRolled, cycleCrit, punishCombo;

    public StrictController(boolean smart) { this.smart = smart; }

    public boolean busy() { return pendingCrit || counterTicks > 0 || phase == Phase.RUN_BACK || phase == Phase.KB_HIT; }

    public void reset() {
        phase = Phase.NORMAL; phaseTicks = 0; tapLeft = 0; pendingCrit = false; attackDelay = -1;
        critsFired = 0; critCooldown = 0; jumpCooldown = 0; critCounter = 0; sTapCounter = 0; counterTicks = 0; idleTicks = 0;
        resetPending = 0; punishFails = 0; cycleRolled = false; cycleCrit = false; punishCombo = false;
    }

    private void enter(Phase p) {
        phase = p; phaseTicks = 0; pendingCrit = false;
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
        boolean safe = st == Strategy.SAFE;
        double engage = safe ? cfg.engageDist + 0.9 : cfg.engageDist;          // SAFE: hold distance just outside reach
        double minDist = safe ? cfg.minDist + 0.8 : cfg.minDist;
        double retreatHp = safe ? Math.max(cfg.retreatHealth, 10.0) : cfg.retreatHealth;
        int critPct = st == Strategy.CRIT_SPAM ? 100 : safe ? Math.min(10, cfg.critChancePercent) : cfg.critChancePercent;
        int comboLock = st == Strategy.CRIT_SPAM ? 99 : cfg.comboLock;

        float yawErr = MathHelper.wrapDegrees(c.yawToTarget - c.yaw);
        float pitchErr = c.pitchToTarget - c.pitch;
        a.aim = true;
        a.goalYaw = c.yawToTarget + (smart ? (rng.nextFloat() - 0.5f) * 1.6f : 0f);
        a.goalPitch = MathHelper.clamp(c.pitchToTarget + (smart ? (rng.nextFloat() - 0.5f) : 0f), -90f, 90f);

        if (critCooldown > 0) critCooldown--;
        if (jumpCooldown > 0) jumpCooldown--;
        boolean inReach = c.reachDist <= cfg.reach;   // eye -> hitbox, the distance the server actually checks
        boolean aimed = Math.abs(yawErr) < 12 && Math.abs(pitchErr) < 15;
        boolean aimedAir = Math.abs(yawErr) < 30 && Math.abs(pitchErr) < 50;   // eye moves ~1.25 blocks in a jump
        boolean ready = c.cooldown >= 0.92f;
        boolean launchReady = c.cooldown >= cfg.critLaunchPercent / 100f;
        boolean retreating = cfg.retreatLow && c.health <= retreatHp;
        boolean pkt = cfg.inputMode == InputMode.PACKET;

        // Jump reset, in EVERY phase. We act on the first tick the hit is visible (the earliest a jump can happen),
        // and keep trying for 3 ticks in case we were airborne at that moment. PREDICT also jumps just before an expected hit.
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

        // SAFE: counter windows open when the opponent swings or lands a hit
        if (safe) {
            if (f.oppSwingEdge && c.dist < 5.0) counterTicks = 10;
            if (f.tookHitNow) counterTicks = Math.max(counterTicks, 8);
        }
        boolean counter = counterTicks > 0;
        if (counter) counterTicks--;
        idleTicks++;

        // Phase transitions (crit-spam response is a NORMAL-strategy behaviour)
        if (retreating) enter(Phase.NORMAL);
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
                if (inReach && aimed && ready && c.sprinting) { a.attack = true; enter(Phase.CRIT_PUNISH); }
                else if (phaseTicks > 40) enter(Phase.CRIT_PUNISH);
                return a;
            }
            case CRIT_PUNISH -> {
                if (phaseTicks >= cfg.punishTicks) { f.resetSpam(); enter(Phase.NORMAL); }
            }
            default -> { }
        }

        // Movement
        if (retreating) { a.back = true; a.sprint = false; }
        else if (counter) { a.forward = true; a.sprint = true; }
        else if (safe) {
            if (c.dist > engage + 0.4) a.forward = true;
            else if (c.dist < engage - 0.3) a.back = true;
        }
        else if (c.dist > engage) { a.forward = true; a.sprint = true; }
        else { a.sprint = true; if (c.dist < minDist) a.back = true; else a.forward = true; }

        // W-tap: stop pressing W for a few ticks after a hit (that ends the sprint).
        // S-tap: stop pressing W and press S instead. In packet mode the W-tap is a sprint-reset packet sent with the hit.
        if (tapLeft > 0) {
            tapLeft--;
            if (tapIsS) { a.forward = false; a.sprint = false; a.stopSprint = true; a.back = true; }
            else if (!pkt) { a.forward = false; a.sprint = false; a.stopSprint = true; }
        }
        if (cfg.strafe && !counter && c.dist < engage + (safe ? 2.0 : 1.5)) strafe(a, cfg);

        // Attack
        boolean comboMode = phase == Phase.CRIT_PUNISH && punishCombo;       // response chose "counter by comboing"
        boolean critAllowed = cfg.crit && cfg.jump && f.combo < comboLock && !counter && !comboMode;
        boolean mayAttack = !safe || counter || idleTicks > 60;   // SAFE waits for the opponent, but not forever
        boolean critHit = false;
        if (pendingCrit) {
            pendingTicks++;
            boolean airborne = !c.onGround;
            if (airborne && c.velY < 0.15) {            // top of the jump: let go of W so the sprint ends before the hit
                a.forward = false; a.sprint = false; a.stopSprint = true;
            }
            boolean falling = airborne && c.velY < 0 && c.fallDistance > 0.0;
            if (falling && !c.sprinting && inReach && aimedAir && ready) {
                a.attack = true; critHit = true; pendingCrit = false; critsFired++; critCooldown = 2;
            } else if (pendingTicks > 16 || (c.onGround && pendingTicks > 2)) {
                pendingCrit = false; critCooldown = st == Strategy.CRIT_SPAM ? 6 : 25;   // failed: hit normally for a while
                if (phase == Phase.CRIT_PUNISH && ++punishFails >= 2) punishCombo = true; // crit not working: switch to combo
            }
        } else {
            // One roll per attack cycle. The jump may START at critLaunchPercent charge so the falling hit is charged in time.
            boolean cycleReady = critAllowed && critCooldown == 0 && jumpCooldown == 0 && c.onGround && mayAttack && aimed
                    && c.reachDist <= cfg.reach + 0.8 && launchReady;
            if (cycleReady && !cycleRolled) { cycleRolled = true; cycleCrit = rollCrit(critPct); }
            if (cycleReady && cycleCrit) {
                a.jump = true; pendingCrit = true; pendingTicks = 0; jumpCooldown = 10; cycleCrit = false;
            } else if (inReach && aimed && ready && mayAttack) {
                if (smart && attackDelay < 0) attackDelay = rng.nextInt(3);
                if (smart && attackDelay-- > 0) {
                    // human-like hesitation
                } else {
                    attackDelay = -1;
                    a.attack = true;
                }
            } else if (!(inReach && aimed && ready)) attackDelay = -1;
        }

        if (a.attack) { idleTicks = 0; cycleRolled = false; cycleCrit = false; }

        // What follows a normal hit. W-tap is the default; the S-tap takes over only by its chance (when both are on).
        if (a.attack && !critHit) {
            if (safe) { tapIsS = true; tapLeft = Math.max(2, cfg.sTapTicks); counterTicks = 0; }   // counter, then back off
            else if (st != Strategy.CRIT_SPAM) {
                boolean doS = cfg.sTap && c.dist <= cfg.sTapMaxDist && (!cfg.wTap || rollSTap(cfg));
                if (doS) { tapIsS = true; tapLeft = Math.max(1, cfg.sTapTicks); }
                else if (cfg.wTap) { tapIsS = false; tapLeft = Math.max(1, cfg.wTapTicks); }
            }
        }

        // SAFE: keep the shield up while waiting
        if (safe && cfg.safeBlock && c.offhandShield && !counter && !a.attack && !pendingCrit && c.dist < engage + 1.2) a.use = true;

        critState = !(cfg.crit && cfg.jump) ? "off (Crit/Jump toggle)"
                : comboMode ? "response: combo"
                : f.combo >= comboLock ? "combo lock (" + f.combo + ")"
                : counter ? "counter window"
                : pendingCrit ? "in the air"
                : critCooldown > 0 ? "retry in " + critCooldown
                : launchReady ? "armed" : "charging " + (int) (c.cooldown * 100) + "%";
        return a;
    }

    private boolean rollCrit(int pct) {
        double chance = phase == Phase.CRIT_PUNISH ? 1.0 : pct / 100.0;
        if (chance <= 0) return false;
        if (smart) return rng.nextDouble() < chance;
        int period = Math.max(1, (int) Math.round(1.0 / chance));
        return (critCounter++ % period) == 0;          // the very first chance fires, then every Nth
    }

    private boolean rollSTap(ModConfig cfg) {
        double chance = cfg.sTapChance / 100.0;
        if (chance <= 0) return false;
        if (smart) return rng.nextDouble() < chance;
        int period = Math.max(1, (int) Math.round(1.0 / chance));
        return (sTapCounter++ % period) == 0;
    }

    private void strafe(Action a, ModConfig cfg) {
        if (++strafeTick >= cfg.strafePeriodTicks) { strafeTick = 0; strafeRight = !strafeRight; }
        a.right = strafeRight; a.left = !strafeRight;
    }
}
