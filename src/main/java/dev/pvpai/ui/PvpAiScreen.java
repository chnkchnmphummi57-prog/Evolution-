package dev.pvpai.ui;

import dev.pvpai.config.HotKey;
import dev.pvpai.config.HudCorner;
import dev.pvpai.config.InputMode;
import dev.pvpai.config.JumpInput;
import dev.pvpai.config.JumpResetMode;
import dev.pvpai.config.Kit;
import dev.pvpai.config.ModConfig;
import dev.pvpai.config.StrafeStyle;
import dev.pvpai.config.Strategy;
import dev.pvpai.ml.MlBackend;
import dev.pvpai.ml.Recorder;
import dev.pvpai.core.AiMode;
import dev.pvpai.core.ScopeGate;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** Quick control panel. The full settings screen (YACL) is under "Advanced". */
public final class PvpAiScreen extends Screen {
    private enum Tab {
        HOME("Home"), MOVES("Moves"), COMBAT("Combat"), INPUT("Input"), MODERN("Modern"), TECH("Tech"), ML("ML");
        final String label;
        Tab(String label) { this.label = label; }
    }

    private static final int PW = 344, PH = 226;
    private static final int BG = 0xF00D1117, BORDER = 0x40FFFFFF, TEXT = 0xFFE6EDF3, DIM = 0xFF8B949E, GREEN = 0xFF3DDC84;

    private final Screen parent;
    private Tab tab = Tab.HOME;
    private int px, py;

    public PvpAiScreen(Screen parent) {
        super(Text.literal("PvP AI"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearChildren();
        px = (width - PW) / 2;
        py = (height - PH) / 2;
        ModConfig cfg = ModConfig.get();

        int tx = px + 14;
        for (Tab t : Tab.values()) {
            int tw = textRenderer.getWidth(t.label) + 14;
            addDrawableChild(ButtonWidget.builder(Text.literal(t == tab ? "> " + t.label : t.label), btn -> { tab = t; init(); })
                    .dimensions(tx, py + 30, tw, 18).build());
            tx += tw + 3;
        }

        int x = px + 14, y = py + 60, w = 150, gap = 22, x2 = px + PW - 14 - w;
        switch (tab) {
            case HOME -> {
                addDrawableChild(toggle(x, y, w, "AI", () -> cfg.enabled, v -> {
                    if (v && !ScopeGate.allowed(client)) {
                        if (client.player != null) client.player.sendMessage(Text.literal("[PvP AI] Blocked: singleplayer or allowlisted servers only."), true);
                    } else cfg.enabled = v;
                }));
                addDrawableChild(cycle(x, y + gap, w, "Mode", AiMode.values(), () -> cfg.mode, v -> cfg.mode = v, m -> m.label));
                addDrawableChild(cycle(x, y + gap * 2, w, "Strategy", Strategy.values(), () -> cfg.strategy, v -> cfg.strategy = v, m -> m.label));
                addDrawableChild(cycle(x, y + gap * 3, w, "Kit", Kit.values(), () -> cfg.kit, v -> cfg.kit = v, m -> m.label));
                addDrawableChild(toggle(x, y + gap * 4, w, "HUD", () -> cfg.hudVisible, v -> cfg.hudVisible = v));
                addDrawableChild(cycle(x, y + gap * 5, w, "HUD corner", HudCorner.values(), () -> cfg.hudCorner, v -> cfg.hudCorner = v, m -> m.label));
            }
            case MOVES -> {
                addDrawableChild(toggle(x, y, w, "Jump", () -> cfg.jump, v -> cfg.jump = v));
                addDrawableChild(toggle(x, y + gap, w, "Crit", () -> cfg.crit, v -> cfg.crit = v));
                addDrawableChild(toggle(x, y + gap * 2, w, "Jump Reset", () -> cfg.jumpReset, v -> cfg.jumpReset = v));
                addDrawableChild(toggle(x2, y, w, "Strafing", () -> cfg.strafe, v -> cfg.strafe = v));
                addDrawableChild(toggle(x2, y + gap, w, "Retreat When Low", () -> cfg.retreatLow, v -> cfg.retreatLow = v));
                addDrawableChild(toggle(x2, y + gap * 2, w, "W-Tap", () -> cfg.wTap, v -> cfg.wTap = v));
                addDrawableChild(toggle(x2, y + gap * 3, w, "S-Tap", () -> cfg.sTap, v -> cfg.sTap = v));
                addDrawableChild(cycle(x, y + gap * 3, w, "Reset mode", JumpResetMode.values(), () -> cfg.jumpResetMode, v -> cfg.jumpResetMode = v, m -> m.label));
                addDrawableChild(new IntSlider(x2, y + gap * 4, w, "Retreat for", 1, 30, cfg.retreatSeconds, " s", v -> cfg.retreatSeconds = v));
                addDrawableChild(new IntSlider(x2, y + gap * 5, w, "Retreat cooldown", 0, 60, cfg.retreatCooldownSeconds, " s", v -> cfg.retreatCooldownSeconds = v));
                addDrawableChild(toggle(x, y + gap * 4, w, "Hard-mask disabled moves", () -> cfg.strictEnforcement, v -> cfg.strictEnforcement = v));
            }
            case COMBAT -> {
                addDrawableChild(new IntSlider(x, y, w, "Crit chance", 0, 100, cfg.critChancePercent, "%", v -> cfg.critChancePercent = v));
                addDrawableChild(new IntSlider(x, y + gap, w, "Combo lock", 1, 15, cfg.comboLock, " hits", v -> cfg.comboLock = v));
                addDrawableChild(new IntSlider(x, y + gap * 2, w, "Retreat HP", 1, 19, (int) cfg.retreatHealth, "", v -> cfg.retreatHealth = v));
                addDrawableChild(new IntSlider(x, y + gap * 3, w, "Strafe period", 4, 40, cfg.strafePeriodTicks, " ticks", v -> cfg.strafePeriodTicks = v));
                addDrawableChild(toggle(x2, y, w, "Crit-spam response", () -> cfg.critSpamResponse, v -> cfg.critSpamResponse = v));
                addDrawableChild(new IntSlider(x2, y + gap, w, "Run-back", 3, 30, cfg.runBackTicks, " ticks", v -> cfg.runBackTicks = v));
                addDrawableChild(new IntSlider(x2, y + gap * 2, w, "Punish window", 20, 400, cfg.punishTicks, " ticks", v -> cfg.punishTicks = v));
                addDrawableChild(new IntSlider(x2, y + gap * 3, w, "W-tap length", 1, 6, cfg.wTapTicks, " ticks", v -> cfg.wTapTicks = v));
                addDrawableChild(new IntSlider(x, y + gap * 4, w, "S-tap length", 1, 8, cfg.sTapTicks, " ticks", v -> cfg.sTapTicks = v));
                addDrawableChild(new IntSlider(x2, y + gap * 4, w, "S-tap chance", 0, 100, cfg.sTapChance, "%", v -> cfg.sTapChance = v));
                addDrawableChild(new IntSlider(x, y + gap * 5, w, "Crit jump at", 30, 100, cfg.critLaunchPercent, "%", v -> cfg.critLaunchPercent = v));
                addDrawableChild(new IntSlider(x2, y + gap * 5, w, "Crit priority", 0, 100, cfg.critResponsePriority, "%", v -> cfg.critResponsePriority = v));
            }
            case INPUT -> {
                addDrawableChild(cycle(x, y, w, "Input", InputMode.values(), () -> cfg.inputMode, v -> cfg.inputMode = v, m -> m.label));
                addDrawableChild(new IntSlider(x, y + gap, w, "Camera smoothness", 0, 100, cfg.cameraSmoothness, "", v -> cfg.cameraSmoothness = v));
                addDrawableChild(new IntSlider(x, y + gap * 2, w, "Max turn", 2, 60, cfg.maxTurnDegPerTick, " deg/tick", v -> cfg.maxTurnDegPerTick = v));
                addDrawableChild(cycle(x, y + gap * 3, w, "Jump input", JumpInput.values(), () -> cfg.jumpInput, v -> cfg.jumpInput = v, m -> m.label));
                addDrawableChild(cycle(x2, y, w, "Menu key", HotKey.values(), () -> cfg.menuKey, v -> cfg.menuKey = v, k -> k.label));
                addDrawableChild(cycle(x2, y + gap, w, "Toggle AI key", HotKey.values(), () -> cfg.toggleKey, v -> cfg.toggleKey = v, k -> k.label));
                addDrawableChild(cycle(x2, y + gap * 2, w, "Toggle HUD key", HotKey.values(), () -> cfg.hudKey, v -> cfg.hudKey = v, k -> k.label));
            }
            case MODERN -> {
                addDrawableChild(toggle(x, y, w, "Shield break", () -> cfg.shieldBreak, v -> cfg.shieldBreak = v));
                addDrawableChild(new IntSlider(x, y + gap, w, "Wait at least", 5, 200, cfg.shieldDelayMin, " ticks", v -> cfg.shieldDelayMin = v));
                addDrawableChild(new IntSlider(x, y + gap * 2, w, "Wait at most", 5, 200, cfg.shieldDelayMax, " ticks", v -> cfg.shieldDelayMax = v));
                addDrawableChild(new IntSlider(x, y + gap * 3, w, "Smart fail chance", 0, 100, cfg.shieldFailChance, "%", v -> cfg.shieldFailChance = v));
                addDrawableChild(toggle(x2, y, w, "Auto-totem", () -> cfg.autoTotem, v -> cfg.autoTotem = v));
                addDrawableChild(new IntSlider(x2, y + gap, w, "Totem below", 2, 19, cfg.totemHealth, " HP", v -> cfg.totemHealth = v));
                addDrawableChild(toggle(x2, y + gap * 2, w, "Auto-eat", () -> cfg.autoEat, v -> cfg.autoEat = v));
                addDrawableChild(new IntSlider(x2, y + gap * 3, w, "Eat below", 2, 19, cfg.eatHealth, " HP", v -> cfg.eatHealth = v));
                addDrawableChild(toggle(x, y + gap * 4, w, "Safe: raise shield", () -> cfg.safeBlock, v -> cfg.safeBlock = v));
                addDrawableChild(toggle(x2, y + gap * 4, w, "Healing pots", () -> cfg.healPots, v -> cfg.healPots = v));
                addDrawableChild(new IntSlider(x, y + gap * 5, w, "Pot below", 2, 19, cfg.potHealth, " HP", v -> cfg.potHealth = v));
                addDrawableChild(toggle(x2, y + gap * 5, w, "Wind charge (mace)", () -> cfg.useWindCharge, v -> cfg.useWindCharge = v));
            }
            case TECH -> {
                int cw = 100, c1 = px + 14, c2 = c1 + 105, c3 = c2 + 105;
                addDrawableChild(toggle(c1, y, cw, "Shift-tap", () -> cfg.shiftTap, v -> cfg.shiftTap = v));
                addDrawableChild(toggle(c2, y, cw, "Block-hit", () -> cfg.blockHit, v -> cfg.blockHit = v));
                addDrawableChild(toggle(c3, y, cw, "Corner hit", () -> cfg.cornerHit, v -> cfg.cornerHit = v));
                addDrawableChild(toggle(c1, y + gap, cw, "Delayed hit", () -> cfg.delayedHit, v -> cfg.delayedHit = v));
                addDrawableChild(toggle(c2, y + gap, cw, "Wall-pin", () -> cfg.wallPin, v -> cfg.wallPin = v));
                addDrawableChild(toggle(c3, y + gap, cw, "Turn-back", () -> cfg.turnBack, v -> cfg.turnBack = v));
                addDrawableChild(toggle(c1, y + gap * 2, cw, "Hit-select", () -> cfg.hitSelect, v -> cfg.hitSelect = v));
                addDrawableChild(toggle(c2, y + gap * 2, cw, "P-crit", () -> cfg.pCrit, v -> cfg.pCrit = v));
                addDrawableChild(toggle(c3, y + gap * 2, cw, "Sprint-cut", () -> cfg.sprintCut, v -> cfg.sprintCut = v));
                addDrawableChild(toggle(c1, y + gap * 3, cw, "Uppercut", () -> cfg.uppercut, v -> cfg.uppercut = v));
                addDrawableChild(toggle(c2, y + gap * 3, cw, "Delayed crit", () -> cfg.delayedCrit, v -> cfg.delayedCrit = v));
                addDrawableChild(toggle(c3, y + gap * 3, cw, "Shake", () -> cfg.crosshairShake, v -> cfg.crosshairShake = v));
                addDrawableChild(toggle(c1, y + gap * 4, cw, "Auto web", () -> cfg.autoWeb, v -> cfg.autoWeb = v));
                addDrawableChild(toggle(c2, y + gap * 4, cw, "Web crit", () -> cfg.webSpamCrit, v -> cfg.webSpamCrit = v));
                addDrawableChild(cycle(c3, y + gap * 4, cw, "Strafe", StrafeStyle.values(), () -> cfg.strafeStyle, v -> cfg.strafeStyle = v, m -> m.label));
                addDrawableChild(toggle(c1, y + gap * 5, cw, "Aim gate", () -> cfg.requireCrosshair, v -> cfg.requireCrosshair = v));
                addDrawableChild(toggle(c2, y + gap * 5, cw, "Hurt guard", () -> cfg.hurtGuard, v -> cfg.hurtGuard = v));
                addDrawableChild(toggle(c3, y + gap * 5, cw, "Attr swap", () -> cfg.attributeSwap, v -> cfg.attributeSwap = v));
            }
            case ML -> {
                addDrawableChild(toggle(x, y, w, "Record demos", () -> cfg.recordDemos, v -> cfg.recordDemos = v));
                addDrawableChild(ButtonWidget.builder(Text.literal("Reload model"), b -> MlBackend.INSTANCE.load())
                        .dimensions(x, y + gap, w, 18).build());
            }
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Advanced (YACL)"), b -> client.setScreen(YaclScreenFactory.create(this)))
                .dimensions(px + 14, py + PH - 28, 110, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(px + PW - 74, py + PH - 28, 60, 18).build());
    }

    @Override
    public void close() {
        ModConfig.save();
        client.setScreen(parent);
    }

    /** Everything that sits behind the widgets is drawn here so nothing can cover them. */
    @Override
    public void renderBackground(DrawContext g, int mouseX, int mouseY, float delta) {
        ModConfig cfg = ModConfig.get();
        g.fill(0, 0, width, height, 0x90000000);
        g.fill(px + 1, py, px + PW - 1, py + PH, BG);
        g.fill(px, py + 1, px + PW, py + PH - 1, BG);
        g.fill(px + 1, py, px + PW - 1, py + 1, BORDER);
        g.fill(px + 1, py + PH - 1, px + PW - 1, py + PH, BORDER);
        g.fill(px, py + 1, px + 1, py + PH - 1, BORDER);
        g.fill(px + PW - 1, py + 1, px + PW, py + PH - 1, BORDER);
        g.fill(px + 2, py, px + PW - 2, py + 2, cfg.enabled ? GREEN : 0xFF6B7280);

        g.drawText(textRenderer, "PvP AI", px + 14, py + 12, TEXT, true);
        String sub = (cfg.enabled ? "ON" : "OFF") + "  |  " + cfg.mode.label + "  |  " + cfg.strategy.label + "  |  " + cfg.kit.label;
        g.drawText(textRenderer, sub, px + PW - 14 - textRenderer.getWidth(sub), py + 12, cfg.enabled ? GREEN : DIM, false);

        int tx = px + 14, ty = py + 60, noteY = py + PH - 46;
        switch (tab) {
            case HOME -> {
                int rx = px + 186;
                g.drawText(textRenderer, "Scope: " + ScopeGate.describe(client), rx, ty, DIM, false);
                g.drawText(textRenderer, "Keys", rx, ty + 20, TEXT, false);
                g.drawText(textRenderer, cfg.toggleKey.label + "  toggle AI", rx, ty + 32, DIM, false);
                g.drawText(textRenderer, cfg.hudKey.label + "  toggle HUD", rx, ty + 43, DIM, false);
                g.drawText(textRenderer, cfg.menuKey.label + "  this menu", rx, ty + 54, DIM, false);
                g.drawText(textRenderer, "Change keys on the Input tab.", rx, ty + 76, DIM, false);
                g.drawText(textRenderer, "Live stats are on the HUD.", rx, ty + 87, DIM, false);
            }
            case MOVES -> g.drawText(textRenderer, "Hard mask: disabled moves are never used.", tx, noteY, DIM, false);
            case COMBAT -> { }
            case INPUT -> {
                g.drawText(textRenderer, "Packets: hits and sprint resets go as packets.", tx, ty + 90, DIM, false);
                g.drawText(textRenderer, "Movement stays on keys. Scope gate applies.", tx, ty + 101, DIM, false);
                g.drawText(textRenderer, "Direct jump = jump physics without the key.", tx, ty + 112, DIM, false);
            }
            case MODERN -> { }
            case TECH -> { }
            case ML -> {
                int rx = px + 186;
                g.drawText(textRenderer, "Model", rx, ty, TEXT, false);
                g.drawText(textRenderer, textRenderer.trimToWidth(MlBackend.INSTANCE.status, PW - 200), rx, ty + 12, DIM, false);
                g.drawText(textRenderer, "Demos", rx, ty + 32, TEXT, false);
                g.drawText(textRenderer, Recorder.INSTANCE.rows + " rows this session", rx, ty + 44, DIM, false);
                g.drawText(textRenderer, "Mode ML uses the model;", tx, ty + 60, DIM, false);
                g.drawText(textRenderer, "without one it runs Strict.", tx, ty + 71, DIM, false);
                g.drawText(textRenderer, "Record while Strict/Smart fights,", tx, ty + 92, DIM, false);
                g.drawText(textRenderer, "train with ml/train.py, then reload.", tx, ty + 103, DIM, false);
            }
        }
    }

    private static Text onOff(String label, boolean on) {
        return Text.literal(label + ": ").append(Text.literal(on ? "ON" : "OFF").formatted(on ? Formatting.GREEN : Formatting.RED));
    }

    private ButtonWidget toggle(int x, int y, int w, String label, Supplier<Boolean> get, Consumer<Boolean> set) {
        return ButtonWidget.builder(onOff(label, get.get()), b -> {
            set.accept(!get.get());
            b.setMessage(onOff(label, get.get()));
        }).dimensions(x, y, w, 18).build();
    }

    private <E extends Enum<E>> ButtonWidget cycle(int x, int y, int w, String label, E[] values, Supplier<E> get, Consumer<E> set, Function<E, String> name) {
        return ButtonWidget.builder(Text.literal(label + ": " + name.apply(get.get())), b -> {
            set.accept(values[(get.get().ordinal() + 1) % values.length]);
            b.setMessage(Text.literal(label + ": " + name.apply(get.get())));
        }).dimensions(x, y, w, 18).build();
    }

    private static final class IntSlider extends SliderWidget {
        private final String label, suffix;
        private final int min, max;
        private final IntConsumer set;

        IntSlider(int x, int y, int w, String label, int min, int max, int cur, String suffix, IntConsumer set) {
            super(x, y, w, 18, Text.empty(), (cur - min) / (double) (max - min));
            this.label = label; this.suffix = suffix; this.min = min; this.max = max; this.set = set;
            updateMessage();
        }

        private int val() { return min + (int) Math.round(value * (max - min)); }

        @Override protected void updateMessage() {
            if (label == null) return;
            setMessage(Text.literal(label + ": " + val() + suffix));
        }

        @Override protected void applyValue() { set.accept(val()); }
    }
}
