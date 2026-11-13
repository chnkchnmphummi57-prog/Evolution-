package dev.pvpai.ui;

import dev.pvpai.config.AccentColor;
import dev.pvpai.config.HotKey;
import dev.pvpai.config.HudCorner;
import dev.pvpai.config.InputMode;
import dev.pvpai.config.JumpInput;
import dev.pvpai.config.JumpResetMode;
import dev.pvpai.config.Kit;
import dev.pvpai.config.ModConfig;
import dev.pvpai.config.Strategy;
import dev.pvpai.config.TotemMethod;
import dev.pvpai.config.WatermarkStyle;
import dev.pvpai.core.AiController;
import dev.pvpai.core.AiMode;
import dev.pvpai.core.ScopeGate;
import dev.pvpai.ml.MlBackend;
import dev.pvpai.ml.Recorder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/** Every feature as a module with settings. The menu draws this list; nothing here knows how it is drawn. */
public final class Modules {
    private Modules() { }

    public enum Cat {
        COMBAT("Combat"), MOVEMENT("Movement"), MODERN("Modern"), MACE("Mace"), VISUAL("Visual"), ML("ML"), SETTINGS("Settings");
        public final String label;
        Cat(String label) { this.label = label; }
    }

    public enum Kind { TOGGLE, SLIDER, CYCLE, ACTION, INFO, TEXT }

    public static final class Setting {
        public final String name, desc;
        public final Kind kind;
        public Supplier<Boolean> getB; public Consumer<Boolean> setB;
        public int min, max; public Supplier<Integer> getI; public IntConsumer setI; public IntFunction<String> fmt;
        public Supplier<String> getS; public Consumer<String> setS; public Runnable next, prev, run;

        Setting(String name, String desc, Kind kind) { this.name = name; this.desc = desc; this.kind = kind; }
    }

    public static final class Module {
        public final String name, desc;
        public final Cat cat;
        public final Supplier<Boolean> on;        // null = no switch, opens its settings instead
        public final Consumer<Boolean> setOn;
        public final List<Setting> settings = new ArrayList<>();

        Module(String name, String desc, Cat cat, Supplier<Boolean> on, Consumer<Boolean> setOn, Setting... s) {
            this.name = name; this.desc = desc; this.cat = cat; this.on = on; this.setOn = setOn;
            for (Setting x : s) settings.add(x);
        }
    }

    // ---- setting builders ----
    static Setting tog(String name, String desc, Supplier<Boolean> g, Consumer<Boolean> s) {
        Setting x = new Setting(name, desc, Kind.TOGGLE); x.getB = g; x.setB = s; return x;
    }
    static Setting sld(String name, String desc, int min, int max, Supplier<Integer> g, IntConsumer s, IntFunction<String> fmt) {
        Setting x = new Setting(name, desc, Kind.SLIDER); x.min = min; x.max = max; x.getI = g; x.setI = s; x.fmt = fmt; return x;
    }
    static Setting sld(String name, String desc, int min, int max, Supplier<Integer> g, IntConsumer s, String suffix) {
        return sld(name, desc, min, max, g, s, v -> v + suffix);
    }
    static <E extends Enum<E>> Setting cyc(String name, String desc, E[] vals, Supplier<E> g, Consumer<E> s, Function<E, String> label) {
        Setting x = new Setting(name, desc, Kind.CYCLE);
        x.getS = () -> label.apply(g.get());
        x.next = () -> s.accept(vals[(g.get().ordinal() + 1) % vals.length]);
        x.prev = () -> s.accept(vals[(g.get().ordinal() + vals.length - 1) % vals.length]);
        return x;
    }
    static Setting act(String name, String desc, Runnable r) { Setting x = new Setting(name, desc, Kind.ACTION); x.run = r; return x; }
    static Setting info(String name, String desc, Supplier<String> text) { Setting x = new Setting(name, desc, Kind.INFO); x.getS = text; return x; }
    static Setting text(String name, String desc, Supplier<String> g, Consumer<String> s) {
        Setting x = new Setting(name, desc, Kind.TEXT); x.getS = g; x.setS = s; return x;
    }

    private static String hund(int v) { return String.format("%.2f", v / 100.0); }

    public static List<Module> build(ModConfig cfg, Runnable openAllowlist) {
        List<Module> m = new ArrayList<>();

        // ------------------------------------------------------------ COMBAT
        m.add(new Module("Evolution AI", "Master switch. Always starts off. Singleplayer and allowlisted servers only.", Cat.COMBAT,
                () -> cfg.enabled, v -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (v && !ScopeGate.allowed(mc)) {
                        if (mc.player != null) mc.player.sendMessage(Text.literal("[Evolution] Blocked: singleplayer or allowlisted servers only."), true);
                    } else cfg.enabled = v;
                },
                cyc("Mode", "Strict: fixed rules. Smart: same rules with random rolls. ML: your trained model.", AiMode.values(), () -> cfg.mode, v -> cfg.mode = v, x -> x.label),
                cyc("Strategy", "Normal: pressure. Safe: wait outside reach, shield up, counter. Crit spam: crit every hit.", Strategy.values(), () -> cfg.strategy, v -> cfg.strategy = v, x -> x.label),
                cyc("Kit", "Sword and Axe fight normally. Mace adds the wind-charge smash.", Kit.values(), () -> cfg.kit, v -> cfg.kit = v, x -> x.label),
                cyc("Input", "Mouse and keyboard sends real key and click events. Packets sends hits as packets.", InputMode.values(), () -> cfg.inputMode, v -> cfg.inputMode = v, x -> x.label),
                cyc("Jump input", "Space bar press, or direct jump physics without the key.", JumpInput.values(), () -> cfg.jumpInput, v -> cfg.jumpInput = v, x -> x.label),
                tog("Hard mask", "Disabled moves are never used.", () -> cfg.strictEnforcement, v -> cfg.strictEnforcement = v)));

        m.add(new Module("Crit", "Jump, drop the sprint, hit while falling.", Cat.COMBAT, () -> cfg.crit, v -> cfg.crit = v,
                sld("Chance per attack", "Strict fires the first one, then every Nth.", 0, 100, () -> cfg.critChancePercent, v -> cfg.critChancePercent = v, "%"),
                sld("Jump starts at", "Attack charge when the jump begins. The hit still needs about 92%, and the jump takes about 7 ticks.", 30, 100, () -> cfg.critLaunchPercent, v -> cfg.critLaunchPercent = v, "%"),
                tog("Auto timing", "Start the jump exactly early enough that the falling hit is charged. Overrides the % above.", () -> cfg.critAutoLaunch, v -> cfg.critAutoLaunch = v),
                sld("Combo lock", "At this many hits in a row the crit chance switches off so the combo keeps going.", 1, 15, () -> cfg.comboLock, v -> cfg.comboLock = v, " hits")));

        m.add(new Module("Crit Response", "When the opponent crit-spams: run back, knockback hit, then crit spam or combo.", Cat.COMBAT,
                () -> cfg.critSpamResponse, v -> cfg.critSpamResponse = v,
                sld("Run-back", "How long to back off.", 3, 30, () -> cfg.runBackTicks, v -> cfg.runBackTicks = v, " ticks"),
                sld("Punish window", "How long to crit every hit afterwards.", 20, 400, () -> cfg.punishTicks, v -> cfg.punishTicks = v, " ticks"),
                sld("Crit priority", "Smart: chance to pick crit spam over comboing. Strict always starts with crit spam.", 0, 100, () -> cfg.critResponsePriority, v -> cfg.critResponsePriority = v, "%")));

        m.add(new Module("Jump Reset", "Jump when hit to cut knockback.", Cat.COMBAT, () -> cfg.jumpReset, v -> cfg.jumpReset = v,
                cyc("Mode", "React: jump on the first tick the hit is visible. Predict: also jump just before an expected hit.", JumpResetMode.values(), () -> cfg.jumpResetMode, v -> cfg.jumpResetMode = v, x -> x.label)));

        m.add(new Module("Hit Select", "After taking a hit, counter immediately.", Cat.COMBAT, () -> cfg.hitSelect, v -> cfg.hitSelect = v));

        m.add(new Module("Uppercut", "Jump and hit together, then S.", Cat.COMBAT, () -> cfg.uppercut, v -> cfg.uppercut = v,
                sld("Chance", "Share of hits that become an uppercut.", 0, 100, () -> cfg.uppercutChance, v -> cfg.uppercutChance = v, "%"),
                sld("Hit delay", "0: hit on the jump tick. 1: hit one tick after the jump.", 0, 1, () -> cfg.uppercutHitDelay, v -> cfg.uppercutHitDelay = v, " ticks"),
                sld("S delay", "Ticks after the jump until S is pressed. 0 presses it at the same time as the jump.", 0, 10, () -> cfg.uppercutSTapDelay, v -> cfg.uppercutSTapDelay = v, " ticks")));

        m.add(new Module("KB Displacement", "Flick the camera 180 degrees, turn back next tick, hit a tick later.", Cat.COMBAT, () -> cfg.kbDisplace, v -> cfg.kbDisplace = v,
                sld("Chance", "Share of hits that use it.", 0, 100, () -> cfg.kbDisplaceChance, v -> cfg.kbDisplaceChance = v, "%"),
                sld("Hit delay", "Ticks between turning back and the hit. Minimum 1.", 1, 3, () -> cfg.kbHitDelay, v -> cfg.kbHitDelay = v, " ticks")));

        m.add(new Module("Shield Break", "Axe the opponent's shield after they have blocked for a while. Works at any charge.", Cat.COMBAT, () -> cfg.shieldBreak, v -> cfg.shieldBreak = v,
                sld("Wait at least", "Ticks of sustained blocking before an attempt. Strict always waits this long.", 5, 200, () -> cfg.shieldDelayMin, v -> cfg.shieldDelayMin = v, " ticks"),
                sld("Wait at most", "Smart waits a random time between the two values.", 5, 200, () -> cfg.shieldDelayMax, v -> cfg.shieldDelayMax = v, " ticks"),
                sld("Smart fail chance", "Chance a Smart attempt fumbles. Strict never fails.", 0, 100, () -> cfg.shieldFailChance, v -> cfg.shieldFailChance = v, "%")));

        m.add(new Module("Attribute Swap", "Axe and Mace kits: charge with the sword, swap to the heavy weapon on the hit tick.", Cat.COMBAT, () -> cfg.attributeSwap, v -> cfg.attributeSwap = v));

        m.add(new Module("Hit Registration", "Stops ghost hits (effect and sound, no damage).", Cat.COMBAT, null, null,
                tog("Aim gate", "Swing only when the crosshair and the rotation the server has both point at the hitbox.", () -> cfg.requireCrosshair, v -> cfg.requireCrosshair = v),
                tog("Hurt guard", "Never swing while the opponent is in hurt-immunity frames.", () -> cfg.hurtGuard, v -> cfg.hurtGuard = v),
                tog("Shield guard", "Never swing a sword into a raised shield. The axe handles shields.", () -> cfg.blockGuard, v -> cfg.blockGuard = v),
                sld("Reach margin", "Swing this far inside max reach. Widens automatically after ghost hits.", 0, 30, () -> (int) Math.round(cfg.reachMargin * 100), v -> cfg.reachMargin = v / 100.0, Modules::hund),
                info("Hits", "Landed / sent, and ghost hits.", () -> AiController.INSTANCE.tracker.landed + " / " + AiController.INSTANCE.tracker.attempts + "   ghosts " + AiController.INSTANCE.tracker.ghosts)));

        // ------------------------------------------------------------ MOVEMENT
        m.add(new Module("Jump", "Master switch for every jump the AI makes.", Cat.MOVEMENT, () -> cfg.jump, v -> cfg.jump = v));

        m.add(new Module("Circle Strafe", "Hold W with A or D to circle the opponent.", Cat.MOVEMENT, () -> cfg.strafe, v -> cfg.strafe = v,
                sld("Run length", "Ticks per direction.", 6, 80, () -> cfg.circleTicks, v -> cfg.circleTicks = v, " ticks")));

        m.add(new Module("Reach Spacing", "Wait just outside reach, step in and hit at the edge of reach.", Cat.MOVEMENT, () -> cfg.reachSpacing, v -> cfg.reachSpacing = v,
                sld("Max reach", "Attack reach, eye to hitbox.", 250, 330, () -> (int) Math.round(cfg.reach * 100), v -> cfg.reach = v / 100.0, Modules::hund)));

        m.add(new Module("W-Tap", "Release W for a moment after a hit.", Cat.MOVEMENT, () -> cfg.wTap, v -> cfg.wTap = v,
                sld("Length", "Ticks W stays released.", 1, 6, () -> cfg.wTapTicks, v -> cfg.wTapTicks = v, " ticks")));

        m.add(new Module("S-Tap", "Press S when you hit, to step out of reach.", Cat.MOVEMENT, () -> cfg.sTap, v -> cfg.sTap = v,
                sld("Length", "Ticks S is pressed.", 1, 8, () -> cfg.sTapTicks, v -> cfg.sTapTicks = v, " ticks"),
                sld("Delay", "0: S on the same tick as the hit. 1: one tick later.", 0, 1, () -> cfg.sTapDelay, v -> cfg.sTapDelay = v, " ticks"),
                sld("Chance", "Share of hits followed by an S-tap. The rest use shift or W.", 0, 100, () -> cfg.sTapChance, v -> cfg.sTapChance = v, "%"),
                sld("Max range", "Only S-tap when the opponent is within this distance.", 100, 400, () -> (int) Math.round(cfg.sTapMaxDist * 100), v -> cfg.sTapMaxDist = v / 100.0, Modules::hund)));

        m.add(new Module("Shift-Tap", "Press shift on the same tick as the hit: sprint drops, forward stays held.", Cat.MOVEMENT, () -> cfg.shiftTap, v -> cfg.shiftTap = v,
                sld("Length", "Ticks of sneak.", 1, 4, () -> cfg.shiftTapTicks, v -> cfg.shiftTapTicks = v, " ticks"),
                sld("Chance", "Share of hits followed by a shift-tap.", 0, 100, () -> cfg.shiftTapChance, v -> cfg.shiftTapChance = v, "%")));

        m.add(new Module("Retreat", "At low health turn around and run, then turn back and fight.", Cat.MOVEMENT, () -> cfg.retreatLow, v -> cfg.retreatLow = v,
                sld("Health", "Retreat at or below this health.", 1, 19, () -> (int) cfg.retreatHealth, v -> cfg.retreatHealth = v, " HP"),
                sld("Run for", "How long the run lasts (many servers have no healing).", 1, 30, () -> cfg.retreatSeconds, v -> cfg.retreatSeconds = v, " s"),
                sld("Cooldown", "No new retreat for this long afterwards.", 0, 60, () -> cfg.retreatCooldownSeconds, v -> cfg.retreatCooldownSeconds = v, " s")));

        // ------------------------------------------------------------ MODERN
        m.add(new Module("Auto Totem", "Move a totem into the offhand when it is empty or health is low.", Cat.MODERN, () -> cfg.autoTotem, v -> cfg.autoTotem = v,
                sld("Below", "Swap a totem in at or below this health.", 2, 19, () -> cfg.totemHealth, v -> cfg.totemHealth = v, " HP"),
                cyc("Method", "Auto: inventory click, then swap hands if that does not stick.", TotemMethod.values(), () -> cfg.totemMethod, v -> cfg.totemMethod = v, x -> x.label)));
        m.add(new Module("Auto Eat", "Eat a golden apple when low and the opponent is not close.", Cat.MODERN, () -> cfg.autoEat, v -> cfg.autoEat = v,
                sld("Below", "Eat at or below this health. Safe strategy eats 4 HP earlier.", 2, 19, () -> cfg.eatHealth, v -> cfg.eatHealth = v, " HP")));
        m.add(new Module("Healing Pots", "Throw splash instant health at your feet when low.", Cat.MODERN, () -> cfg.healPots, v -> cfg.healPots = v,
                sld("Below", "Throw a pot at or below this health.", 2, 19, () -> cfg.potHealth, v -> cfg.potHealth = v, " HP")));
        m.add(new Module("Safe Shield", "Safe strategy raises the offhand shield while waiting.", Cat.MODERN, () -> cfg.safeBlock, v -> cfg.safeBlock = v));

        // ------------------------------------------------------------ MACE
        m.add(new Module("Wind Charge Smash", "Look down, switch to a wind charge, jump and throw together, then smash.", Cat.MACE, () -> cfg.useWindCharge, v -> cfg.useWindCharge = v,
                info("Status", "Needs a wind charge in the hotbar and the Mace kit.", () -> AiController.INSTANCE.mace.state.isEmpty() ? "idle" : AiController.INSTANCE.mace.state)));

        // ------------------------------------------------------------ VISUAL
        m.add(new Module("HUD", "The live fight card.", Cat.VISUAL, () -> cfg.hudVisible, v -> cfg.hudVisible = v,
                tog("Compact", "Header, status and bars only.", () -> cfg.hudCompact, v -> cfg.hudCompact = v),
                cyc("Corner", "Where the card sits.", HudCorner.values(), () -> cfg.hudCorner, v -> cfg.hudCorner = v, x -> x.label),
                sld("Opacity", "How opaque the card is.", 20, 100, () -> cfg.hudOpacity, v -> cfg.hudOpacity = v, "%")));
        m.add(new Module("Watermark", "The Evolution watermark. Everything about it is adjustable.", Cat.VISUAL, () -> cfg.watermarkEnabled, v -> cfg.watermarkEnabled = v,
                text("Text", "The name shown in the watermark.", () -> cfg.watermarkText, v -> cfg.watermarkText = v),
                cyc("Style", "Box, underline or text only.", WatermarkStyle.values(), () -> cfg.watermarkStyle, v -> cfg.watermarkStyle = v, x -> x.label),
                cyc("Corner", "Which corner the offsets are measured from.", HudCorner.values(), () -> cfg.watermarkCorner, v -> cfg.watermarkCorner = v, x -> x.label),
                sld("Offset X", "Distance from the left or right edge.", 0, 300, () -> cfg.watermarkX, v -> cfg.watermarkX = v, " px"),
                sld("Offset Y", "Distance from the top or bottom edge.", 0, 300, () -> cfg.watermarkY, v -> cfg.watermarkY = v, " px"),
                sld("Opacity", "How opaque it is.", 10, 100, () -> cfg.watermarkOpacity, v -> cfg.watermarkOpacity = v, "%"),
                tog("Version", "Show the mod version.", () -> cfg.watermarkVersion, v -> cfg.watermarkVersion = v),
                tog("FPS", "Show frames per second.", () -> cfg.watermarkFps, v -> cfg.watermarkFps = v),
                tog("Ping", "Show latency (local in singleplayer).", () -> cfg.watermarkPing, v -> cfg.watermarkPing = v),
                tog("Time", "Show the local clock.", () -> cfg.watermarkTime, v -> cfg.watermarkTime = v),
                tog("AI state", "Show whether the AI is on.", () -> cfg.watermarkState, v -> cfg.watermarkState = v)));
        m.add(new Module("Accent", "The colour used by the HUD, this menu and the watermark.", Cat.VISUAL, null, null,
                cyc("Colour", "Rainbow cycles through hues.", AccentColor.values(), () -> cfg.accent, v -> cfg.accent = v, x -> x.label)));

        // ------------------------------------------------------------ ML
        m.add(new Module("Record Demos", "Save what Strict or Smart does, to train a model from.", Cat.ML, () -> cfg.recordDemos, v -> cfg.recordDemos = v,
                info("Rows", "Rows recorded this session.", () -> Recorder.INSTANCE.rows + (Recorder.INSTANCE.file.isEmpty() ? "" : "  (" + Recorder.INSTANCE.file + ")")),
                info("Folder", "Where the demo files go.", () -> Recorder.folder().toString()),
                act("Copy folder path", "Copies the demos folder path, to paste into the trainer.",
                        () -> MinecraftClient.getInstance().keyboard.setClipboard(Recorder.folder().toString()))));
        m.add(new Module("Model", "The trained model used by Mode: ML. It reloads by itself when model.json changes.", Cat.ML, null, null,
                info("Status", "Result of the last load.", () -> MlBackend.INSTANCE.status),
                info("File", "Put model.json here (the trainer does it for you).", () -> MlBackend.modelFile().toString()),
                act("Reload now", "Loads model.json again.", MlBackend.INSTANCE::load)));

        // ------------------------------------------------------------ SETTINGS
        m.add(new Module("Hotkeys", "Pick the keys.", Cat.SETTINGS, null, null,
                cyc("Open menu", "Opens this menu in game.", HotKey.values(), () -> cfg.menuKey, v -> cfg.menuKey = v, x -> x.label),
                cyc("Toggle AI", "Turns the AI on or off.", HotKey.values(), () -> cfg.toggleKey, v -> cfg.toggleKey = v, x -> x.label),
                cyc("Toggle HUD", "Shows or hides the HUD card.", HotKey.values(), () -> cfg.hudKey, v -> cfg.hudKey = v, x -> x.label)));
        m.add(new Module("Camera", "How the bot turns.", Cat.SETTINGS, null, null,
                sld("Smoothness", "0 is snappy, 100 is very smooth.", 0, 100, () -> cfg.cameraSmoothness, v -> cfg.cameraSmoothness = v, ""),
                sld("Max turn", "Degrees per tick the camera may turn.", 2, 60, () -> cfg.maxTurnDegPerTick, v -> cfg.maxTurnDegPerTick = v, " deg/t"),
                sld("Reaction jitter", "Smart only: random 0-N ticks of hesitation. 0 reacts on the first tick.", 0, 3, () -> cfg.reactionJitter, v -> cfg.reactionJitter = v, " ticks")));
        m.add(new Module("Server Allowlist", "Servers you own. The AI refuses to run anywhere else.", Cat.SETTINGS, null, null,
                info("Servers", "Singleplayer is always allowed.", () -> cfg.allowedServers.isEmpty() ? "none" : String.join(", ", cfg.allowedServers)),
                act("Edit list", "Opens the list editor.", openAllowlist)));
        return m;
    }
}
