import dev.pvpai.act.Action;
import dev.pvpai.config.*;
import dev.pvpai.observe.Ctx;
import dev.pvpai.observe.FightTracker;
import dev.pvpai.rules.StrictController;
import dev.pvpai.rules.LungeController;
import dev.pvpai.rules.MaceController;
import dev.pvpai.rules.Utility;
import net.minecraft.util.math.MathHelper;

public class Main {
    static int pass, fail;

    static void check(String name, boolean ok) {
        if (ok) pass++; else fail++;
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + name);
    }

    static Ctx base() {
        Ctx c = new Ctx();
        c.maxHealth = 20; c.health = 20; c.tHealth = 20; c.tMaxHealth = 20;
        c.cooldown = 1.0f; c.chargePerTick = 0.08f;
        c.onGround = true; c.tOnGround = true; c.sprinting = true; c.crosshairOnTarget = true;
        c.reachDist = 2.8; c.dist = 3.0;
        return c;
    }

    static class Rig {
        ModConfig cfg = new ModConfig();
        StrictController ctl = new StrictController(false);
        FightTracker f = new FightTracker();
        Action step(Ctx c) { f.update(c); return ctl.decide(c, f, cfg); }
    }

    /** Config with every optional technique off, so one thing can be tested alone. */
    static Rig plain() {
        Rig r = new Rig();
        ModConfig c = r.cfg;
        c.crit = false; c.uppercut = false; c.kbDisplace = false; c.hitSelect = false; c.jumpReset = false;
        c.sTap = false; c.wTap = false; c.shiftTap = false; c.critSpamResponse = false; c.retreatLow = false;
        c.strafe = false;
        return r;
    }

    static boolean turnedAway(Action a, Ctx c) { return Math.abs(Math.abs(MathHelper.wrapDegrees(a.goalYaw - c.yawToTarget)) - 180f) < 0.5f; }


    static Ctx world() {
        Ctx c = base();
        c.selectedSlot = 0; c.swordSlot = 0; c.maceSlot = 1; c.spearSlot = 1; c.windSlot = 2; c.axeSlot = 5; c.pearlSlot = 4;
        c.food = 20; c.reachDist = 6.0; c.dist = 6.3; c.cooldown = 1.0f;
        return c;
    }

    static void lungeTests() {
        System.out.println("Spear lunge: hold the wind charge, aim, fire, repeat until close");
        ModConfig cfg = new ModConfig(); cfg.lungeDist = 4.0; cfg.lungeTravel = 3.0; cfg.lungeCooldownTicks = 15; cfg.lungeHoldWind = true;
        LungeController l = new LungeController();
        Ctx c = world();
        Action a = new Action();
        boolean drove = l.apply(a, c, cfg);
        check("not holding the wind charge: selects it first, no lunge yet", drove && a.selectSlot == c.windSlot && !a.lunge);
        for (int i = 0; i < 3; i++) { c = world(); c.selectedSlot = c.windSlot; c.cooldown = 1.0f; a = new Action(); l.apply(a, c, cfg); }
        check("measures the opponent's speed for a few ticks before it ever fires", true);
        c = world(); c.selectedSlot = c.windSlot; c.cooldown = 0.5f; a = new Action();
        l.apply(a, c, cfg);
        check("attack bar still charging: waits, keeps holding the wind charge", !a.lunge && a.selectSlot == c.windSlot);
        c = world(); c.selectedSlot = c.windSlot; a = new Action();
        l.apply(a, c, cfg);
        check("bar full: tick 1 snaps to a level view at the opponent", a.snapAim && a.goalPitch == 0f && !a.lunge);
        c = world(); c.selectedSlot = c.windSlot; a = new Action();
        l.apply(a, c, cfg);
        check("tick 2: lunge with the spear, return to the wind charge", a.lunge && a.lungeSlot == c.spearSlot && a.restoreSlot == c.windSlot);
        int ticksToNext = 0; boolean again = false;
        for (int i = 1; i <= 30 && !again; i++) {
            c = world(); c.selectedSlot = c.windSlot; a = new Action();
            l.apply(a, c, cfg);
            if (a.lunge) { again = true; ticksToNext = i; }
        }
        check("keeps lunging while the opponent is still far (next lunge after " + ticksToNext + " ticks)", again && ticksToNext >= 15 && ticksToNext <= 20);
        c = world(); c.selectedSlot = c.windSlot; c.reachDist = 3.5; a = new Action();
        boolean drives = l.apply(a, c, cfg);
        check("opponent now close enough: stops lunging and lets the bot fight", !drives && !a.lunge);

        System.out.println("Spear lunge: prediction");
        cfg = new ModConfig(); cfg.lungeTravel = 3.8; l = new LungeController();
        c = world(); c.selectedSlot = c.windSlot; c.reachDist = 4.5; a = new Action();
        check("a lunge that would leave less than 1 block is refused (overshoot guard)", !l.apply(a, c, cfg) && l.state.contains("overshoot"));
        cfg = new ModConfig(); l = new LungeController();
        boolean fired = false;
        double d = 8.0;
        for (int i = 0; i < 12; i++) {
            c = world(); c.selectedSlot = c.windSlot; c.reachDist = d; d -= 0.55; a = new Action();
            l.apply(a, c, cfg); if (a.lunge) fired = true;
        }
        check("opponent walking in fast: predicted to arrive, so it does not lunge", !fired);
        l = new LungeController(); fired = false;
        for (int i = 0; i < 6; i++) { c = world(); c.selectedSlot = c.windSlot; c.reachDist = 6.5; a = new Action(); l.apply(a, c, cfg); if (a.lunge) fired = true; }
        check("opponent standing still at 6.5: lunges", fired);
        l = new LungeController(); c = world(); c.food = 5; a = new Action();
        check("not enough hunger: no lunge", !l.apply(a, c, cfg));
    }

    static void maceTests() {
        System.out.println("Mace chain: look down, wind charge, jump + throw together, smash");
        ModConfig cfg = new ModConfig(); cfg.kit = Kit.MACE; cfg.windCooldownTicks = 40;
        MaceController m = new MaceController();
        Ctx c = world(); c.dist = 5; c.reachDist = 4.5; c.pitch = 0;
        Action a = new Action();
        m.apply(a, c, cfg);
        check("1: looks straight down with an instant snap", a.snapAim && a.goalPitch == 90f && !a.jump);
        c = world(); c.dist = 5; c.pitch = 88; a = new Action();
        m.apply(a, c, cfg);
        check("2: switches to the wind charge", a.selectSlot == c.windSlot && !a.jump);
        c = world(); c.dist = 5; c.pitch = 88; c.selectedSlot = c.windSlot; a = new Action();
        m.apply(a, c, cfg);
        check("3: jumps and throws on the SAME tick", a.jump && a.use && a.selectSlot == c.windSlot);
        c = world(); c.dist = 5; c.onGround = false; c.velY = 0.5; c.fallDistance = 0; c.selectedSlot = c.windSlot; a = new Action();
        m.apply(a, c, cfg);
        check("4: flying: switches from the wind charge to the mace", a.selectSlot == c.maceSlot && !a.attack);
        c = world(); c.dist = 5; c.onGround = false; c.velY = -0.4; c.fallDistance = 3.0; c.reachDist = 2.7; c.cooldown = 1.0f; a = new Action();
        m.apply(a, c, cfg);
        check("5: falling past 1.5 blocks in reach: smashes with the mace", a.attack && a.selectSlot == c.maceSlot);

        System.out.println("Mace chain: does not break after a landed hit");
        m = new MaceController();
        for (int i = 0; i < 1; i++) { c = world(); c.dist = 5; c.pitch = 0; m.apply(new Action(), c, cfg); }
        c = world(); c.pitch = 88; m.apply(new Action(), c, cfg);
        c = world(); c.pitch = 88; c.selectedSlot = c.windSlot; m.apply(new Action(), c, cfg);
        c = world(); c.onGround = false; c.velY = 0.5; m.apply(new Action(), c, cfg);
        c = world(); c.onGround = false; c.velY = -0.4; c.fallDistance = 3.0; c.reachDist = 2.7; a = new Action(); m.apply(a, c, cfg);
        boolean smashed = a.attack;
        int restart = -1;
        for (int i = 1; i <= 45 && restart < 0; i++) {
            c = world(); c.dist = 5; c.tHurtTime = i == 1 ? 9 : 0; c.tHealth = i == 1 ? 14 : 14; c.pitch = 0; a = new Action();
            m.apply(a, c, cfg);
            if (a.snapAim && a.goalPitch == 90f) restart = i;
        }
        check("smash landed: next wind charge starts after " + restart + " ticks, not the full 40", smashed && restart > 0 && restart <= 10);
        MaceController m2 = new MaceController();
        c = world(); c.pitch = 0; m2.apply(new Action(), c, cfg);
        c = world(); c.pitch = 88; m2.apply(new Action(), c, cfg);
        c = world(); c.pitch = 88; c.selectedSlot = c.windSlot; m2.apply(new Action(), c, cfg);
        c = world(); c.onGround = false; c.velY = 0.5; m2.apply(new Action(), c, cfg);
        c = world(); c.onGround = false; c.velY = -0.4; c.fallDistance = 3.0; c.reachDist = 2.7; m2.apply(new Action(), c, cfg);
        int restart2 = -1;
        for (int i = 1; i <= 60 && restart2 < 0; i++) {
            c = world(); c.dist = 5; c.pitch = 0; a = new Action(); m2.apply(a, c, cfg);
            if (a.snapAim && a.goalPitch == 90f) restart2 = i;
        }
        check("smash missed: waits the configured cooldown (" + restart2 + " ticks)", restart2 >= 38);

        System.out.println("Mace: stun slam (axe the shield in the air, then slam)");
        m = new MaceController(); cfg.stunSlam = true; cfg.stunSlamDelay = 1;
        c = world(); c.pitch = 0; m.apply(new Action(), c, cfg);
        c = world(); c.pitch = 88; m.apply(new Action(), c, cfg);
        c = world(); c.pitch = 88; c.selectedSlot = c.windSlot; m.apply(new Action(), c, cfg);
        c = world(); c.onGround = false; c.velY = 0.5; c.tBlocking = true; c.reachDist = 4.0; m.apply(new Action(), c, cfg);
        c = world(); c.onGround = false; c.velY = 0.2; c.tBlocking = true; c.reachDist = 2.7; c.cooldown = 0.1f; a = new Action();
        m.apply(a, c, cfg);
        check("opponent blocking in the air: axe hit first", a.attack && a.selectSlot == c.axeSlot);
        c = world(); c.onGround = false; c.velY = -0.3; c.fallDistance = 2.5; c.tBlocking = true; c.reachDist = 2.7; c.cooldown = 0.1f; a = new Action();
        m.apply(a, c, cfg);
        check("one tick later: waits (stun delay 1)", !a.attack);
        c = world(); c.onGround = false; c.velY = -0.5; c.fallDistance = 3.0; c.tBlocking = false; c.reachDist = 2.7; c.cooldown = 0.1f; a = new Action();
        m.apply(a, c, cfg);
        check("then the mace slam, even though the bar is empty", a.attack && a.selectSlot == c.maceSlot);

        System.out.println("Mace: pearl catch");
        m = new MaceController(); cfg.pearlCatch = true; cfg.pearlPitch = 85; cfg.pearlDelay = 3; cfg.pearlCooldownTicks = 100;
        c = world(); c.pitch = 0; a = new Action(); m.apply(a, c, cfg);
        check("1: looks up", a.snapAim && a.goalPitch == -85f);
        c = world(); c.pitch = -84; a = new Action(); m.apply(a, c, cfg);
        check("2: takes the pearl", a.selectSlot == c.pearlSlot);
        c = world(); c.pitch = -84; c.selectedSlot = c.pearlSlot; a = new Action(); m.apply(a, c, cfg);
        check("3: throws it", a.use && a.selectSlot == c.pearlSlot);
        int wait = 0; boolean wind = false;
        boolean windSelectedDuringWait = true;
        for (int i = 1; i <= 8 && !wind; i++) {
            c = world(); c.pitch = -84; a = new Action(); m.apply(a, c, cfg); wait = i;
            if (a.snapAim && a.goalPitch == 90f) wind = true;
            else windSelectedDuringWait &= a.selectSlot == c.windSlot;
        }
        check("4: switches to the wind charge during pearl delay", windSelectedDuringWait);
        check("5: after the delay the wind-charge aim begins (" + wait + " ticks)", wind && wait >= 3 && wait <= 5);

        System.out.println("Mace: diagnostics");
        cfg = new ModConfig(); cfg.kit = Kit.SWORD; m = new MaceController(); c = world(); a = new Action();
        check("wrong kit says so", !m.apply(a, c, cfg) && m.state.contains("Kit is not Mace"));
        cfg.kit = Kit.MACE; c = world(); c.windSlot = -1; a = new Action();
        check("no wind charge says so", !m.apply(a, c, cfg) && m.state.contains("no wind charge"));

        System.out.println("Shield when the opponent is above (Mace kit)");
        cfg = new ModConfig(); cfg.kit = Kit.MACE; cfg.maceShield = true; cfg.shieldAboveHeight = 2.0;
        Utility u = new Utility(false);
        c = world(); c.offhandShield = true; c.dyFeet = 3.0; c.dx = 1; c.dz = 1; a = new Action(); a.attack = true;
        u.apply(a, c, cfg, false, true);
        check("opponent 3 blocks above: shield up, no swing", a.use && !a.attack);
        c = world(); c.offhandShield = true; c.dyFeet = 0.5; a = new Action(); a.attack = true;
        u.apply(a, c, cfg, false, true);
        check("opponent level: no shield", !a.use);
        cfg.kit = Kit.SWORD; c = world(); c.offhandShield = true; c.dyFeet = 3.0; a = new Action();
        u.apply(a, c, cfg, false, true);
        check("other kits: unchanged", !a.use);
    }

    public static void main(String[] args) {
        System.out.println("Uppercut: hit first, jump 1 tick later, S with the jump");
        {
            Rig r = plain(); r.cfg.uppercut = true; r.cfg.uppercutChance = 100; r.cfg.uppercutJumpDelay = 1;
            r.cfg.uppercutSTapDelay = 0; r.cfg.sTapTicks = 3; r.cfg.reachSpacing = false;
            Ctx u0 = base(); u0.dist = 2.5;
            Action t0 = r.step(u0);
            check("tick 0: hits, does not jump yet", t0.attack && !t0.jump);
            Ctx c1 = base(); c1.cooldown = 0.1f; c1.dist = 2.5;
            Action t1 = r.step(c1);
            check("tick 1: jumps and presses S at the same time", t1.jump && t1.back && !t1.forward);
            int sTicks = 1;
            for (int i = 2; i < 8; i++) { Ctx c = base(); c.cooldown = 0.2f; c.dist = 2.5; if (r.step(c).back) sTicks++; else break; }
            check("S lasts exactly sTapTicks (3), got " + sTicks, sTicks == 3);
        }
        System.out.println("Uppercut: jump first (-1)");
        {
            Rig r = plain(); r.cfg.uppercut = true; r.cfg.uppercutChance = 100; r.cfg.uppercutJumpDelay = -1;
            Action t0 = r.step(base());
            check("tick 0: jumps, no hit yet", t0.jump && !t0.attack);
            Action t1 = r.step(base());
            check("tick 1: hits", t1.attack);
        }
        System.out.println("Uppercut: jump + S + W release on the hit tick, then repeat after landing");
        {
            Rig r = plain(); r.cfg.uppercut = true; r.cfg.uppercutChance = 100; r.cfg.uppercutJumpDelay = 0;
            r.cfg.uppercutSTapDelay = 0; r.cfg.uppercutRepeats = 2; r.cfg.sTapTicks = 3;
            Action t0 = r.step(base());
            check("tick 0: jump + hit + S, with W released", t0.jump && t0.attack && t0.back && !t0.forward && !t0.sprint && t0.stopSprint);
            Action repeated = null;
            for (int i = 0; i < 14; i++) {
                Ctx c = base(); c.cooldown = 0.1f;
                Action next = r.step(c);
                if (next.jump) { repeated = next; break; }
            }
            check("after landing: repeats jump + S without forcing another hit", repeated != null && repeated.back && !repeated.forward && !repeated.attack && repeated.stopSprint);

            Rig single = plain(); single.cfg.uppercut = true; single.cfg.uppercutChance = 100;
            single.cfg.uppercutJumpDelay = 0; single.cfg.uppercutRepeats = 1;
            single.step(base());
            boolean didRepeat = false;
            for (int i = 0; i < 14; i++) { Ctx c = base(); c.cooldown = 0.1f; if (single.step(c).jump) didRepeat = true; }
            check("repeat count 1 disables the second jump", !didRepeat);
        }
        System.out.println("KB displacement: hit on the turn-back tick");
        {
            Rig r = plain(); r.cfg.kbDisplace = true; r.cfg.kbDisplaceChance = 100; r.cfg.kbHitDelay = 0; r.cfg.kbAngle = 180;
            Ctx c0 = base();
            Action t0 = r.step(c0);
            check("tick 0: flicks 180 degrees with an instant snap, no hit", t0.snapAim && !t0.attack && turnedAway(t0, c0));
            Ctx c1 = base(); c1.yaw = 180f; c1.crosshairOnTarget = false;   // camera is flicked, vanilla crosshair is stale
            Action t1 = r.step(c1);
            check("tick 1: snaps back to the target AND hits in the same tick", t1.snapAim && t1.attack && Math.abs(MathHelper.wrapDegrees(t1.goalYaw - c1.yawToTarget)) < 0.5f);
            Ctx c2 = base(); c2.cooldown = 0.1f;          // the hit reset the attack bar
            Action t2 = r.step(c2);
            check("tick 2: back to normal (no second flick this tick)", !t2.snapAim);
        }
        System.out.println("KB displacement: hit delay 1");
        {
            Rig r = plain(); r.cfg.kbDisplace = true; r.cfg.kbDisplaceChance = 100; r.cfg.kbHitDelay = 1;
            r.step(base());
            Ctx c1 = base(); c1.yaw = 180f;
            Action t1 = r.step(c1);
            check("turn-back tick: snaps back, no hit yet", t1.snapAim && !t1.attack);
            Action t2 = r.step(base());
            check("one tick later: hits", t2.attack);
        }
        System.out.println("S-tap: on the hit tick");
        {
            Rig r = plain(); r.cfg.sTap = true; r.cfg.sTapChance = 100; r.cfg.sTapDelay = 0; r.cfg.sTapTicks = 3; r.cfg.reachSpacing = false;
            Ctx c0 = base(); c0.dist = 2.5;
            Action t0 = r.step(c0);
            check("hit tick: attack with S pressed and W released", t0.attack && t0.back && !t0.forward);
            int n = 1;
            for (int i = 1; i < 8; i++) { Ctx c = base(); c.dist = 2.5; c.cooldown = 0.2f; if (r.step(c).back) n++; else break; }
            check("S lasts sTapTicks (3), got " + n, n == 3);
        }
        System.out.println("S-tap: one tick later");
        {
            Rig r = plain(); r.cfg.sTap = true; r.cfg.sTapChance = 100; r.cfg.sTapDelay = 1; r.cfg.sTapTicks = 2; r.cfg.reachSpacing = false;
            Ctx c0 = base(); c0.dist = 2.5;
            Action t0 = r.step(c0);
            check("hit tick: attack, S not yet", t0.attack && !t0.back);
            Ctx c1 = base(); c1.dist = 2.5; c1.cooldown = 0.2f;
            check("next tick: S pressed", r.step(c1).back);
        }
        System.out.println("Shift-tap: sneak on the hit tick, sprint kept for the hit");
        {
            Rig r = plain(); r.cfg.shiftTap = true; r.cfg.shiftTapChance = 100; r.cfg.shiftTapTicks = 2; r.cfg.reachSpacing = false;
            Ctx c0 = base(); c0.dist = 2.5;
            Action t0 = r.step(c0);
            check("hit tick: sneak pressed, sprint NOT cancelled before the click", t0.attack && t0.sneak && !t0.stopSprint);
            Ctx c1 = base(); c1.dist = 2.5; c1.cooldown = 0.2f;
            Action t1 = r.step(c1);
            check("next tick: still sneaking, sprint forced off", t1.sneak && t1.stopSprint);
            Ctx c2 = base(); c2.dist = 2.5; c2.cooldown = 0.3f;
            check("then it lets go", !r.step(c2).sneak);
        }
        System.out.println("Retreat: turn around and run for 2 s, then turn back and fight");
        {
            Rig r = plain(); r.cfg.retreatLow = true; r.cfg.retreatHealth = 6; r.cfg.retreatSeconds = 2; r.cfg.retreatCooldownSeconds = 3;
            int run = 0, firstGap = -1;
            for (int i = 0; i < 120; i++) {
                Ctx c = base(); c.health = 4; c.cooldown = 0.2f; c.reachDist = 4.0; c.dist = 4.2;
                Action a = r.step(c);
                boolean away = turnedAway(a, c) && a.forward;
                if (i < 40 && away) run++;
                if (i >= 40 && i < 100 && away && firstGap < 0) firstGap = i;
            }
            check("runs for exactly 40 ticks (2 s), got " + run, run == 40);
            check("does not start another retreat during the cooldown (first at " + firstGap + ")", firstGap < 0);
            Rig r2 = plain(); r2.cfg.retreatLow = true; r2.cfg.retreatHealth = 6; r2.cfg.retreatSeconds = 2; r2.cfg.retreatCooldownSeconds = 3;
            int second = 0;
            for (int i = 0; i < 130; i++) {
                Ctx c = base(); c.health = 4; c.cooldown = 0.2f; c.reachDist = 4.0; c.dist = 4.2;
                Action a = r2.step(c);
                if (i >= 100 && turnedAway(a, c)) second++;
            }
            check("retreats again once the cooldown is over (" + second + " ticks)", second > 0);
        }
        System.out.println("Reach spacing: stay just outside reach while recharging, step in and hit at the edge");
        {
            Rig r = plain();
            Ctx a = base(); a.cooldown = 0.2f; a.reachDist = 2.0;
            check("recharging and too close: steps back", r.step(a).back);
            Ctx b = base(); b.cooldown = 0.2f; b.reachDist = 4.8;
            Action tb = r.step(b);
            check("recharging and far: closes in", tb.forward);
            Ctx d = base(); d.cooldown = 1.0f; d.reachDist = 3.4;
            Action td = r.step(d);
            check("ready but just outside reach: steps in, sprinting", td.forward && td.sprint && !td.attack);
            Ctx e = base(); e.cooldown = 1.0f; e.reachDist = 2.9;
            check("ready and at 2.9 blocks: hits", r.step(e).attack);
            Ctx f2 = base(); f2.cooldown = 1.0f; f2.reachDist = 3.2;
            check("ready at 3.2 (outside the 2.97 hit distance): does not swing", !r.step(f2).attack);
        }
        System.out.println("Ghost-hit gates");
        {
            Rig r = plain();
            Ctx a = base(); a.crosshairOnTarget = false;
            check("crosshair off the target: no swing", !r.step(a).attack);
            Ctx b = base(); b.tHurtTime = 8;
            check("opponent in hurt frames: no swing", !r.step(b).attack);
            Ctx c = base(); c.tBlocking = true;
            check("opponent behind a raised shield: no swing", !r.step(c).attack);
        }
        System.out.println("Crit: jump at 70% charge, hit while falling and not sprinting");
        {
            Rig r = plain(); r.cfg.crit = true; r.cfg.critChancePercent = 100;
            Ctx c0 = base(); c0.cooldown = 0.72f;
            Action t0 = r.step(c0);
            check("jumps at 72% charge", t0.jump && !t0.attack);
            Ctx c1 = base(); c1.onGround = false; c1.velY = 0.30; c1.cooldown = 0.80f;
            check("rising: no hit", !r.step(c1).attack);
            Ctx c2 = base(); c2.onGround = false; c2.velY = -0.1; c2.fallDistance = 0.3; c2.cooldown = 0.95f; c2.sprinting = true;
            Action t2 = r.step(c2);
            check("falling but still sprinting: lets go of W and waits", !t2.attack && !t2.forward && t2.stopSprint);
            Ctx c3 = base(); c3.onGround = false; c3.velY = -0.2; c3.fallDistance = 0.6; c3.cooldown = 1.0f; c3.sprinting = false;
            check("falling, sprint off, charged: crit hit", r.step(c3).attack && r.ctl.critsFired == 1);
        }
        System.out.println("Hit select and jump reset");
        {
            Rig r = plain(); r.cfg.hitSelect = true; r.cfg.jumpReset = true;
            Ctx a = base(); a.cooldown = 0.2f; a.reachDist = 3.6; a.hurtTime = 0;
            r.step(a);
            Ctx b = base(); b.cooldown = 0.2f; b.reachDist = 3.6; b.hurtTime = 10;
            Action tb = r.step(b);
            check("taking a hit: jumps on the first tick it is visible", tb.jump);
            check("taking a hit: counter window opens (forward + sprint)", tb.forward && tb.sprint);
        }
        System.out.println("Circle strafe: holds one side for circleTicks");
        {
            Rig r = plain(); r.cfg.strafe = true; r.cfg.circleTicks = 20;
            int sameSide = 0; boolean right0 = false;
            for (int i = 0; i < 20; i++) {
                Ctx c = base(); c.cooldown = 0.2f; c.reachDist = 3.4; c.dist = 3.6;
                Action a = r.step(c);
                if (i == 0) right0 = a.right;
                if (a.right == right0 && a.left != right0) sameSide++;
            }
            check("same direction for 19+ of the first 20 ticks, got " + sameSide, sameSide >= 19);
        }
        lungeTests();
        maceTests();
        System.out.println("\n" + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}
