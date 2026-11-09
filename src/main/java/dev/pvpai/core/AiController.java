package dev.pvpai.core;

import dev.pvpai.act.Action;
import dev.pvpai.act.ActionApplier;
import dev.pvpai.act.CameraSmoother;
import dev.pvpai.config.ModConfig;
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
        if (!cfg.enabled) { release(); status = "off"; lastCtx = null; return; }
        if (!ScopeGate.allowed(mc)) { release(); cfg.enabled = false; status = "blocked: out of scope"; return; }
        if (mc.currentScreen != null) { release(); status = "paused (screen open)"; return; }

        LivingEntity target = findTarget(mc, cfg.targetRange);
        if (target == null) {
            release(); status = "no target"; lastCtx = null;
            maintain(mc, cfg);   // totem / eating / weapon still work with nobody around
            return;
        }
        if (!target.getUuid().equals(lastTarget)) { lastTarget = target.getUuid(); resetFight(); }

        Ctx ctx = Ctx.build(mc.player, target);
        if (cfg.inputMode == dev.pvpai.config.InputMode.PACKET) ctx.cooldown = Math.min(ctx.cooldown, ActionApplier.packetCooldown(mc.player));
        tracker.update(ctx);

        // ML mode lands in the next update; until then it runs the Strict rules so it is safe to test.
        boolean useSmart = cfg.mode == AiMode.SMART;
        StrictController ctl = useSmart ? smart : strict;
        Utility util = useSmart ? utilSmart : utilStrict;
        Action a = ctl.decide(ctx, tracker, cfg);
        util.apply(a, ctx, cfg, ctl.busy(), ctx.reachDist <= cfg.reach);
        boolean maceActive = mace.apply(a, ctx, cfg);
        ActionApplier.mask(a, cfg);
        ActionApplier.apply(mc, a, target, cfg);
        holdingKeys = true;
        lastCtx = ctx; lastAction = a;
        status = ctl.phase + " | combo " + tracker.combo + " | crits " + ctl.critsFired
                + (maceActive ? " | " + mace.state : "") + (cfg.mode == AiMode.ML ? " | ML>strict" : "");
    }

    /** Inventory-only upkeep for ticks without an opponent. Never touches movement keys. */
    private void maintain(MinecraftClient mc, ModConfig cfg) {
        Ctx self = Ctx.buildSelf(mc.player);
        Action a = new Action();
        Utility util = cfg.mode == AiMode.SMART ? utilSmart : utilStrict;
        util.apply(a, self, cfg, false, false);
        ActionApplier.inventory(mc, mc.player, a);
        if (a.use) { mc.options.useKey.setPressed(true); holdingUse = true; }
        else if (holdingUse) { KeyBinding.updatePressedStates(); holdingUse = false; }
    }

    public String critState() {
        return (ModConfig.get().mode == AiMode.SMART ? smart : strict).critState;
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
            KeyBinding.updatePressedStates();
            if (holdingKeys) resetFight();
            holdingKeys = false; holdingUse = false;
        }
        CameraSmoother.clear();
    }
}
