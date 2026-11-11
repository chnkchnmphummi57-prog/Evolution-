package dev.pvpai.core;

import dev.pvpai.act.Action;
import dev.pvpai.act.ActionApplier;
import dev.pvpai.act.CameraSmoother;
import dev.pvpai.config.InputMode;
import dev.pvpai.config.ModConfig;
import dev.pvpai.ml.MlBackend;
import dev.pvpai.ml.Recorder;
import dev.pvpai.observe.Ctx;
import dev.pvpai.observe.FightTracker;
import dev.pvpai.rules.MaceController;
import dev.pvpai.rules.StrictController;
import dev.pvpai.rules.Utility;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.UUID;

public final class AiController {
    public static final AiController INSTANCE = new AiController();

    public final StrictController strict = new StrictController(false);
    public final StrictController smart = new StrictController(true);
    public final Utility utilStrict = new Utility(false);
    public final Utility utilSmart = new Utility(true);
    public final MaceController mace = new MaceController();
    public final FightTracker tracker = new FightTracker();
    public volatile Ctx lastCtx;
    public volatile Action lastAction;
    public volatile String status = "idle";
    private boolean holdingKeys = false, holdingUse = false;
    private UUID lastTarget;

    public void tick(MinecraftClient mc) {
        CameraSmoother.tickFallback(mc);
        ModConfig cfg = ModConfig.get();
        if (!cfg.enabled) { release(); Recorder.INSTANCE.close(); status = "off"; lastCtx = null; return; }
        if (!ScopeGate.allowed(mc)) { release(); Recorder.INSTANCE.close(); cfg.enabled = false; status = "blocked: out of scope"; return; }
        if (mc.currentScreen != null) { release(); status = "paused (screen open)"; return; }

        LivingEntity target = findTarget(mc, cfg.targetRange);
        if (target == null) {
            release(); status = "no target"; lastCtx = null;
            maintain(mc, cfg);   // totem / eating / weapon still work with nobody around
            return;
        }
        if (!target.getUuid().equals(lastTarget)) { lastTarget = target.getUuid(); resetFight(); }

        Ctx ctx = Ctx.build(mc.player, target);
        if (cfg.inputMode == InputMode.PACKET) ctx.cooldown = Math.min(ctx.cooldown, ActionApplier.packetCooldown(mc.player));
        tracker.update(ctx);

        boolean useSmart = cfg.mode == AiMode.SMART;
        StrictController ctl = useSmart ? smart : strict;
        Utility util = useSmart ? utilSmart : utilStrict;
        MlBackend ml = MlBackend.INSTANCE;
        boolean mlActive = cfg.mode == AiMode.ML && ml.ready();

        Action a;
        if (mlActive) {
            a = ml.decide(ctx, cfg);
            gateAttack(a, ctx, cfg);
        } else {
            a = ctl.decide(ctx, tracker, cfg);     // ML without a model runs the Strict rules
        }
        util.apply(a, ctx, cfg, mlActive ? false : ctl.busy(), ctx.reachDist <= cfg.reach - cfg.reachMargin - tracker.reachPenalty);
        boolean maceActive = mace.apply(a, ctx, cfg);
        ActionApplier.mask(a, cfg);
        // Last line of defence against ghost hits: a real click on anything but the target is a miss (and a miss resets the cooldown).
        if (a.attack && cfg.inputMode == InputMode.INPUT && !ctx.crosshairOnTarget) a.attack = false;
        ActionApplier.apply(mc, a, target, cfg);
        if (a.attack) tracker.onAttack();
        if (!mlActive) Recorder.INSTANCE.record(ctx, cfg, a);   // only rule-mode fights make training data
        holdingKeys = true;
        lastCtx = ctx; lastAction = a;

        if (mlActive) status = "ML | combo " + tracker.combo + (maceActive ? " | " + mace.state : "");
        else status = ctl.phase + " | combo " + tracker.combo + " | crits " + ctl.critsFired
                + (maceActive ? " | " + mace.state : "") + (cfg.mode == AiMode.ML ? " | ML>strict" : "");
    }

    /** Same hit-registration gates the rule modes use, so a model cannot send ghost hits either. */
    private void gateAttack(Action a, Ctx c, ModConfig cfg) {
        boolean inReach = c.reachDist <= cfg.reach - cfg.reachMargin - tracker.reachPenalty;
        boolean clear = (!cfg.requireCrosshair || c.crosshairOnTarget) && (!cfg.hurtGuard || c.tHurtTime <= 1) && !(cfg.blockGuard && c.tBlocking);
        if (a.attack && !(inReach && clear && c.cooldown >= 0.85f)) a.attack = false;
        if (inReach && c.cooldown >= 0.8f && !c.crosshairOnTarget) a.fastAim = true;
    }

    /** Inventory-only upkeep for ticks without an opponent. Never touches movement keys. */
    private void maintain(MinecraftClient mc, ModConfig cfg) {
        Ctx self = Ctx.buildSelf(mc.player);
        Action a = new Action();
        Utility util = cfg.mode == AiMode.SMART ? utilSmart : utilStrict;
        util.apply(a, self, cfg, false, false);
        ActionApplier.inventory(mc, mc.player, a);
        if (a.use) { ActionApplier.setUse(mc, true); holdingUse = true; }
        else if (holdingUse) { ActionApplier.setUse(mc, false); holdingUse = false; }
    }

    public String critState() {
        ModConfig cfg = ModConfig.get();
        if (cfg.mode == AiMode.ML) return "ML: " + MlBackend.INSTANCE.status;
        return (cfg.mode == AiMode.SMART ? smart : strict).critState;
    }

    public String utilState() {
        return (ModConfig.get().mode == AiMode.SMART ? utilSmart : utilStrict).state;
    }

    private void resetFight() { strict.reset(); smart.reset(); utilStrict.reset(); utilSmart.reset(); mace.reset(); tracker.reset(); }

    private LivingEntity findTarget(MinecraftClient mc, double range) {
        PlayerEntity best = null;
        double bd = range;
        for (PlayerEntity other : mc.world.getPlayers()) {
            if (other == mc.player || !other.isAlive() || other.isSpectator()) continue;
            double d = mc.player.distanceTo(other);
            if (d < bd) { bd = d; best = other; }
        }
        return best;
    }

    private void release() {
        if (holdingKeys || holdingUse) {
            ActionApplier.resetKeys();
            if (holdingKeys) resetFight();
            holdingKeys = false; holdingUse = false;
        }
        CameraSmoother.clear();
    }
}
