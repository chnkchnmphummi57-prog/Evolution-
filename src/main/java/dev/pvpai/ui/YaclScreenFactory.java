package dev.pvpai.ui;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.ListOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.pvpai.config.HotKey;
import dev.pvpai.config.HudCorner;
import dev.pvpai.config.InputMode;
import dev.pvpai.config.JumpInput;
import dev.pvpai.config.JumpResetMode;
import dev.pvpai.config.Kit;
import dev.pvpai.config.TotemMethod;
import dev.pvpai.config.ModConfig;
import dev.pvpai.config.StrafeStyle;
import dev.pvpai.config.Strategy;
import dev.pvpai.core.AiMode;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Full settings screen built with YACL (replaces Cloth Config). */
public final class YaclScreenFactory {
    private YaclScreenFactory() { }

    public static Screen create(Screen parent) {
        ModConfig cfg = ModConfig.get();

        ConfigCategory general = ConfigCategory.createBuilder()
                .name(Text.literal("General"))
                .tooltip(Text.literal("Mode, scope and safety"))
                .option(choice("Mode", "Strict: fixed rules (every Nth attack). Smart: same rules with random rolls, aim noise and hesitation. ML: runs Strict until a model is loaded.",
                        AiMode.class, AiMode.STRICT, () -> cfg.mode, v -> cfg.mode = v, m -> m.label))
                .option(choice("Strategy", "Normal: pressure with crits and taps. Safe: stay out of reach, shield up, counter their swings, then back off. Crit spam: jump-crit every attack.",
                        Strategy.class, Strategy.NORMAL, () -> cfg.strategy, v -> cfg.strategy = v, m -> m.label))
                .option(choice("Kit", "Sword/Pot/NethPot/SMP/UHC use a sword (Pot kits throw healing pots). Axe uses an axe. Mace uses a mace and wind charges (experimental).",
                        Kit.class, Kit.SWORD, () -> cfg.kit, v -> cfg.kit = v, m -> m.label))
                .option(bool("Hard-mask disabled movements", "Disabled moves can never be used, even by a model.", true, () -> cfg.strictEnforcement, v -> cfg.strictEnforcement = v))
                .group(ListOption.<String>createBuilder()
                        .name(Text.literal("Allowlisted servers"))
                        .description(OptionDescription.of(Text.literal("Servers you own or run. Singleplayer is always allowed. Anywhere else the AI refuses to start.")))
                        .binding(new ArrayList<>(), () -> cfg.allowedServers, v -> cfg.allowedServers = new ArrayList<>(v))
                        .controller(StringControllerBuilder::create)
                        .initial("")
                        .build())
                .build();

        ConfigCategory moves = ConfigCategory.createBuilder()
                .name(Text.literal("Movements"))
                .tooltip(Text.literal("Turn individual moves on or off"))
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Combat moves"))
                        .option(bool("Jump", "Master switch for every jump the AI makes.", true, () -> cfg.jump, v -> cfg.jump = v))
                        .option(bool("Crit", "Jump, drop sprint, hit on the way down. Needs Jump on.", true, () -> cfg.crit, v -> cfg.crit = v))
                        .option(bool("Jump Reset", "Jump when hit to cut knockback.", true, () -> cfg.jumpReset, v -> cfg.jumpReset = v))
                        .option(choice("Jump reset mode", "React: jump on the first tick the hit is visible (the earliest possible). React + predict: also jump just before an expected hit (opponent swing or falling crit).",
                                JumpResetMode.class, JumpResetMode.REACT, () -> cfg.jumpResetMode, v -> cfg.jumpResetMode = v, m -> m.label))
                        .option(bool("Strafing", "Side-step while fighting.", true, () -> cfg.strafe, v -> cfg.strafe = v))
                        .option(bool("Retreat When Low", "Turn around and run when health is low, then turn back and fight after the time below (many servers have no healing).", true, () -> cfg.retreatLow, v -> cfg.retreatLow = v))
                        .option(slider("Retreat lasts", "How long the run lasts before the bot turns back and fights.", 5, 1, 30, " s", () -> cfg.retreatSeconds, v -> cfg.retreatSeconds = v))
                        .option(slider("Retreat cooldown", "No new retreat for this long after turning back.", 8, 0, 60, " s", () -> cfg.retreatCooldownSeconds, v -> cfg.retreatCooldownSeconds = v))
                        .option(bool("W-Tap", "Stop pressing W for a moment after a hit.", true, () -> cfg.wTap, v -> cfg.wTap = v))
                        .option(bool("S-Tap", "Press S for a moment after a hit.", true, () -> cfg.sTap, v -> cfg.sTap = v))
                        .build())
                .build();

        ConfigCategory combat = ConfigCategory.createBuilder()
                .name(Text.literal("Combat"))
                .tooltip(Text.literal("Crit chance, combo lock, crit-spam response"))
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Crits"))
                        .option(slider("Crit chance per attack", "Chance that an attack becomes a jump-crit. Strict fires the first one, then every Nth.", 20, 0, 100, "%", () -> cfg.critChancePercent, v -> cfg.critChancePercent = v))
                        .option(slider("Crit jump starts at", "Attack charge at which the jump starts. The hit still needs ~92%, and the jump takes ~7 ticks, so 50-80% is faster than waiting for 100%.", 70, 30, 100, "%", () -> cfg.critLaunchPercent, v -> cfg.critLaunchPercent = v))
                        .option(slider("Combo lock", "At this many hits in a row the crit chance switches off so the combo keeps going.", 5, 1, 15, " hits", () -> cfg.comboLock, v -> cfg.comboLock = v))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Crit-spam response"))
                        .option(bool("Enabled", "When the opponent spams crits: run back, knockback hit, then crit spam with jump reset.", true, () -> cfg.critSpamResponse, v -> cfg.critSpamResponse = v))
                        .option(slider("Crit priority", "Smart: chance the response picks crit spam over comboing. Strict always starts with crit spam and falls back to combo if crits keep failing.", 75, 0, 100, "%", () -> cfg.critResponsePriority, v -> cfg.critResponsePriority = v))
                        .option(slider("Run-back length", "How long to back off.", 10, 3, 30, " ticks", () -> cfg.runBackTicks, v -> cfg.runBackTicks = v))
                        .option(slider("Punish window", "How long to crit every hit afterwards.", 100, 20, 400, " ticks", () -> cfg.punishTicks, v -> cfg.punishTicks = v))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Spacing"))
                        .option(slider("Retreat health", "Back off at or below this much health.", 6, 1, 19, " HP", () -> (int) cfg.retreatHealth, v -> cfg.retreatHealth = v))
                        .option(slider("Strafe period", "Ticks between strafe direction changes.", 14, 4, 40, " ticks", () -> cfg.strafePeriodTicks, v -> cfg.strafePeriodTicks = v))
                        .build())
                .build();

        ConfigCategory taps = ConfigCategory.createBuilder()
                .name(Text.literal("Taps"))
                .tooltip(Text.literal("W-tap and S-tap tuning"))
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("W-Tap"))
                        .option(slider("Length", "Ticks W stays released after a hit (this ends the sprint).", 2, 1, 6, " ticks", () -> cfg.wTapTicks, v -> cfg.wTapTicks = v))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("S-Tap"))
                        .option(slider("Length", "Ticks S is pressed after a hit.", 3, 1, 8, " ticks", () -> cfg.sTapTicks, v -> cfg.sTapTicks = v))
                        .option(slider("Chance", "Share of hits followed by an S-tap. The rest use the W-tap if it is on.", 100, 0, 100, "%", () -> cfg.sTapChance, v -> cfg.sTapChance = v))
                        .option(Option.<Double>createBuilder()
                                .name(Text.literal("Max range"))
                                .description(OptionDescription.of(Text.literal("Only S-tap when the opponent is at most this many blocks away.")))
                                .binding(3.0, () -> cfg.sTapMaxDist, v -> cfg.sTapMaxDist = v)
                                .controller(o -> DoubleSliderControllerBuilder.create(o).range(1.5, 4.5).step(0.1).formatValue(v -> Text.literal(String.format("%.1f blocks", v))))
                                .build())
                        .build())
                .build();

        ConfigCategory input = ConfigCategory.createBuilder()
                .name(Text.literal("Input & Camera"))
                .tooltip(Text.literal("How the AI acts, and how it turns"))
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Input"))
                        .option(choice("Input mode", "Mouse & keyboard, or hits and sprint resets sent as packets. Movement stays on keys.",
                                InputMode.class, InputMode.INPUT, () -> cfg.inputMode, v -> cfg.inputMode = v, m -> m.label))
                        .option(choice("Jump input", "Space bar key press, or direct jump physics without the key.",
                                JumpInput.class, JumpInput.KEY, () -> cfg.jumpInput, v -> cfg.jumpInput = v, m -> m.label))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Hotkeys"))
                        .option(choice("Open menu", "Opens the quick panel.", HotKey.class, HotKey.LEFT_BRACKET, () -> cfg.menuKey, v -> cfg.menuKey = v, k -> k.label))
                        .option(choice("Toggle AI", "Turns the AI on or off (singleplayer and allowlisted servers only).", HotKey.class, HotKey.RIGHT_BRACKET, () -> cfg.toggleKey, v -> cfg.toggleKey = v, k -> k.label))
                        .option(choice("Toggle HUD", "Shows or hides the overlay.", HotKey.class, HotKey.BACKSLASH, () -> cfg.hudKey, v -> cfg.hudKey = v, k -> k.label))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Camera"))
                        .option(slider("Smoothness", "0 = snappy, 100 = very smooth.", 60, 0, 100, "", () -> cfg.cameraSmoothness, v -> cfg.cameraSmoothness = v))
                        .option(slider("Max turn speed", "Degrees per tick the camera may turn.", 18, 2, 60, " deg/tick", () -> cfg.maxTurnDegPerTick, v -> cfg.maxTurnDegPerTick = v))
                        .build())
                .build();

        ConfigCategory modern = ConfigCategory.createBuilder()
                .name(Text.literal("Modern PvP"))
                .tooltip(Text.literal("Shield break, totem, eating, shield block"))
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Shield break"))
                        .description(OptionDescription.of(Text.literal("When the opponent has blocked for a while, switch to an axe, hit when charged, then switch back.")))
                        .option(bool("Enabled", "Needs an axe in the hotbar.", true, () -> cfg.shieldBreak, v -> cfg.shieldBreak = v))
                        .option(slider("Wait at least", "Ticks of sustained blocking before an attempt. Strict always waits this long.", 20, 5, 200, " ticks", () -> cfg.shieldDelayMin, v -> cfg.shieldDelayMin = v))
                        .option(slider("Wait at most", "Smart waits a random time between the two values.", 100, 5, 200, " ticks", () -> cfg.shieldDelayMax, v -> cfg.shieldDelayMax = v))
                        .option(slider("Smart fail chance", "Chance that a Smart attempt fumbles. Strict never fails.", 20, 0, 100, "%", () -> cfg.shieldFailChance, v -> cfg.shieldFailChance = v))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Totem"))
                        .option(bool("Auto-totem", "Move a totem into the offhand when it is empty or health is low.", true, () -> cfg.autoTotem, v -> cfg.autoTotem = v))
                        .option(choice("Method", "Auto: inventory click, then swap hands if that does not stick (hotbar totems only for swap hands).",
                                TotemMethod.class, TotemMethod.AUTO, () -> cfg.totemMethod, v -> cfg.totemMethod = v, m -> m.label))
                        .option(slider("Totem below", "Swap a totem in at or below this health.", 10, 2, 19, " HP", () -> cfg.totemHealth, v -> cfg.totemHealth = v))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Eating"))
                        .option(bool("Auto-eat", "Eat a golden apple when low, only if the opponent is not close.", true, () -> cfg.autoEat, v -> cfg.autoEat = v))
                        .option(slider("Eat below", "Safe strategy eats 4 HP earlier.", 8, 2, 19, " HP", () -> cfg.eatHealth, v -> cfg.eatHealth = v))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Kits"))
                        .option(bool("Healing pots (Pot / NethPot)", "Throw splash Instant Health at your feet when low. Needs them in the hotbar.", true, () -> cfg.healPots, v -> cfg.healPots = v))
                        .option(slider("Pot below", "Throw a pot at or below this health.", 10, 2, 19, " HP", () -> cfg.potHealth, v -> cfg.potHealth = v))
                        .option(bool("Wind charge smash (Mace)", "Experimental: jump, wind charge, mace smash. Needs both in the hotbar.", true, () -> cfg.useWindCharge, v -> cfg.useWindCharge = v))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Defence"))
                        .option(bool("Safe strategy raises shield", "Needs a shield in the offhand.", true, () -> cfg.safeBlock, v -> cfg.safeBlock = v))
                        .build())
                .build();

        ConfigCategory tech = ConfigCategory.createBuilder()
                .name(Text.literal("Techniques"))
                .tooltip(Text.literal("Sprint resets, spacing, jump variants, mind games"))
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Sprint resets (after a hit)"))
                        .description(OptionDescription.of(Text.literal("W-tap and S-tap are in Movements and Taps. Which tap follows a hit: S by its chance, then these, then W.")))
                        .option(bool("Shift-tap", "Tap sneak after a hit: drops sprint while forward stays held.", true, () -> cfg.shiftTap, v -> cfg.shiftTap = v))
                        .option(slider("Shift-tap length", "Ticks of sneak.", 1, 1, 4, " ticks", () -> cfg.shiftTapTicks, v -> cfg.shiftTapTicks = v))
                        .option(slider("Shift-tap chance", "Share of hits followed by a shift-tap.", 20, 0, 100, "%", () -> cfg.shiftTapChance, v -> cfg.shiftTapChance = v))
                        .option(bool("Block-hit", "Raise the offhand shield for a split second after a hit. Needs a shield.", false, () -> cfg.blockHit, v -> cfg.blockHit = v))
                        .option(slider("Block-hit length", "Ticks the shield stays up.", 2, 1, 4, " ticks", () -> cfg.blockHitTicks, v -> cfg.blockHitTicks = v))
                        .option(slider("Block-hit chance", "Share of hits followed by a block-hit.", 20, 0, 100, "%", () -> cfg.blockHitChance, v -> cfg.blockHitChance = v))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Spacing and hit manipulation"))
                        .option(choice("Strafe style", "Alternate: fixed period. Circle: long orbit runs. Jitter: rapid A/D taps. Mixed: switches every 4.5 seconds.",
                                StrafeStyle.class, StrafeStyle.ALTERNATE, () -> cfg.strafeStyle, v -> cfg.strafeStyle = v, m -> m.label))
                        .option(slider("Circle run", "Ticks per direction when circle strafing.", 30, 10, 80, " ticks", () -> cfg.circleTicks, v -> cfg.circleTicks = v))
                        .option(slider("Jitter period", "Ticks per direction when jittering.", 2, 1, 6, " ticks", () -> cfg.jitterTicks, v -> cfg.jitterTicks = v))
                        .option(bool("Corner hit", "Near max reach, aim at the hitbox edge closest to you for a fractional reach gain.", true, () -> cfg.cornerHit, v -> cfg.cornerHit = v))
                        .option(bool("Delayed hit", "If the opponent is too close, step back and hit at max reach instead.", false, () -> cfg.delayedHit, v -> cfg.delayedHit = v))
                        .option(bool("Wall-pin", "Opponent against a wall: stay on them, no taps, no strafing.", true, () -> cfg.wallPin, v -> cfg.wallPin = v))
                        .option(bool("Turn-back", "At very close range: whip 180, run a block, snap back and hit.", false, () -> cfg.turnBack, v -> cfg.turnBack = v))
                        .option(slider("Turn-back chance", "Chance each time the moment comes up.", 15, 0, 100, "%", () -> cfg.turnBackChance, v -> cfg.turnBackChance = v))
                        .option(bool("Sweep disruption", "With other players near the opponent, hit without sprinting so the sweep splash lands.", true, () -> cfg.sweepDisrupt, v -> cfg.sweepDisrupt = v))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Physics and jumps"))
                        .option(bool("Hit-select", "After taking a hit, counter immediately (aggressive for 6 ticks).", true, () -> cfg.hitSelect, v -> cfg.hitSelect = v))
                        .option(bool("P-crit", "Hit the opponent the moment they jump, before their crit starts.", true, () -> cfg.pCrit, v -> cfg.pCrit = v))
                        .option(bool("Auto crit timing", "Start the jump exactly early enough that the falling hit is charged (fastest double crits). Overrides the launch %.", false, () -> cfg.critAutoLaunch, v -> cfg.critAutoLaunch = v))
                        .option(bool("Sprint-cut", "Jump and hit at the turn from rising to falling, with sprint intact.", true, () -> cfg.sprintCut, v -> cfg.sprintCut = v))
                        .option(slider("Sprint-cut chance", "Share of jump attacks that use it.", 15, 0, 100, "%", () -> cfg.sprintCutChance, v -> cfg.sprintCutChance = v))
                        .option(bool("Uppercut", "Jump and hit on the same tick (launches them upward), then S-tap a few ticks later.", true, () -> cfg.uppercut, v -> cfg.uppercut = v))
                        .option(slider("Uppercut S-tap delay", "Ticks between the uppercut and the S-tap.", 5, 1, 20, " ticks", () -> cfg.uppercutSTapDelay, v -> cfg.uppercutSTapDelay = v))
                        .option(slider("Uppercut chance", "Share of hits that become an uppercut.", 10, 0, 100, "%", () -> cfg.uppercutChance, v -> cfg.uppercutChance = v))
                        .option(bool("Delayed crit", "Crit just before touching the ground.", false, () -> cfg.delayedCrit, v -> cfg.delayedCrit = v))
                        .option(slider("Delayed crit chance", "Share of jump attacks that use it.", 25, 0, 100, "%", () -> cfg.delayedCritChance, v -> cfg.delayedCritChance = v))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Mind games"))
                        .option(bool("Crosshair shake", "Flick the camera away and back after a hit.", false, () -> cfg.crosshairShake, v -> cfg.crosshairShake = v))
                        .build())
                .build();

        ConfigCategory hitreg = ConfigCategory.createBuilder()
                .name(Text.literal("Hit registration"))
                .tooltip(Text.literal("Stops ghost hits (effect and sound, no damage)"))
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Ghost-hit guards"))
                        .description(OptionDescription.of(Text.literal("A ghost hit is a swing the server ignores. These gates only swing when it can count.")))
                        .option(bool("Aim gate", "Swing only when the crosshair ray really passes through the opponent's hitbox.", true, () -> cfg.requireCrosshair, v -> cfg.requireCrosshair = v))
                        .option(bool("Hurt guard", "Do not swing while the opponent is in hurt-immunity frames (damage is ignored).", true, () -> cfg.hurtGuard, v -> cfg.hurtGuard = v))
                        .option(bool("Shield guard", "Do not swing a sword into a raised shield. The axe handles shields.", true, () -> cfg.blockGuard, v -> cfg.blockGuard = v))
                        .option(Option.<Double>createBuilder()
                                .name(Text.literal("Reach margin"))
                                .description(OptionDescription.of(Text.literal("Swing this far inside max reach. The HUD shows the margin including the automatic extra after ghost hits.")))
                                .binding(0.15, () -> cfg.reachMargin, v -> cfg.reachMargin = v)
                                .controller(o -> DoubleSliderControllerBuilder.create(o).range(0.0, 0.6).step(0.01).formatValue(v -> Text.literal(String.format("%.2f blocks", v))))
                                .build())
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Attribute swap"))
                        .description(OptionDescription.of(Text.literal("Axe and Mace kits: charge with the sword, swap to the heavy weapon on the hit tick so you do not wait for its slow charge. Turns itself off if hits stop doing damage.")))
                        .option(bool("Enabled", "Needs a sword and the kit's weapon in the hotbar.", true, () -> cfg.attributeSwap, v -> cfg.attributeSwap = v))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Cobweb"))
                        .option(bool("Auto web", "Place a cobweb on the opponent's feet while your weapon recharges. Needs cobwebs in the hotbar.", true, () -> cfg.autoWeb, v -> cfg.autoWeb = v))
                        .option(bool("Crit spam in web", "Opponent stuck in a web: jump-crit every hit and keep out of the web.", true, () -> cfg.webSpamCrit, v -> cfg.webSpamCrit = v))
                        .build())
                .build();

        ConfigCategory ml = ConfigCategory.createBuilder()
                .name(Text.literal("ML"))
                .tooltip(Text.literal("Record demos, train, load a model"))
                .option(LabelOption.create(Text.literal("1. Turn recording on and let Strict or Smart fight for a while.")))
                .option(LabelOption.create(Text.literal("2. Train with ml/train.py, copy model.json to config/evolution/.")))
                .option(LabelOption.create(Text.literal("3. Press Reload model in the quick panel (ML tab), then set Mode to ML.")))
                .option(bool("Record demos", "Write observation/action rows to config/evolution/demos while a RULE mode fights.", false, () -> cfg.recordDemos, v -> cfg.recordDemos = v))
                .build();

        ConfigCategory hud = ConfigCategory.createBuilder()
                .name(Text.literal("HUD"))
                .tooltip(Text.literal("The live overlay"))
                .option(bool("Show HUD", "Same as the HUD key.", true, () -> cfg.hudVisible, v -> cfg.hudVisible = v))
                .option(bool("Compact HUD", "Header, status and bars only.", false, () -> cfg.hudCompact, v -> cfg.hudCompact = v))
                .option(choice("HUD corner", "Where the overlay sits.", HudCorner.class, HudCorner.TOP_LEFT, () -> cfg.hudCorner, v -> cfg.hudCorner = v, m -> m.label))
                .build();

        return YetAnotherConfigLib.createBuilder()
                .title(Text.literal("Evolution"))
                .category(general)
                .category(moves)
                .category(combat)
                .category(taps)
                .category(input)
                .category(modern)
                .category(tech)
                .category(hitreg)
                .category(ml)
                .category(hud)
                .save(ModConfig::save)
                .build()
                .generateScreen(parent);
    }

    private static Option<Boolean> bool(String name, String desc, boolean def, Supplier<Boolean> get, Consumer<Boolean> set) {
        return Option.<Boolean>createBuilder()
                .name(Text.literal(name))
                .description(OptionDescription.of(Text.literal(desc)))
                .binding(def, get, set)
                .controller(TickBoxControllerBuilder::create)
                .build();
    }

    private static Option<Integer> slider(String name, String desc, int def, int min, int max, String suffix, Supplier<Integer> get, Consumer<Integer> set) {
        return Option.<Integer>createBuilder()
                .name(Text.literal(name))
                .description(OptionDescription.of(Text.literal(desc)))
                .binding(def, get, set)
                .controller(o -> IntegerSliderControllerBuilder.create(o).range(min, max).step(1).formatValue(v -> Text.literal(v + suffix)))
                .build();
    }

    private static <E extends Enum<E>> Option<E> choice(String name, String desc, Class<E> cls, E def, Supplier<E> get, Consumer<E> set, Function<E, String> label) {
        return Option.<E>createBuilder()
                .name(Text.literal(name))
                .description(OptionDescription.of(Text.literal(desc)))
                .binding(def, get, set)
                .controller(o -> EnumControllerBuilder.create(o).enumClass(cls).formatValue(e -> Text.literal(label.apply(e))))
                .build();
    }
}
