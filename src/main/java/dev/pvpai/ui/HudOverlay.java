package dev.pvpai.ui;

import dev.pvpai.act.Action;
import dev.pvpai.act.CameraSmoother;
import dev.pvpai.config.ModConfig;
import dev.pvpai.core.AiController;
import dev.pvpai.core.ScopeGate;
import dev.pvpai.ml.Recorder;
import dev.pvpai.observe.Ctx;
import dev.pvpai.observe.FightTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/** Evolution HUD card. Toggle with the HUD key or the Look tab; opacity, corner, compact mode and accent are adjustable. */
public final class HudOverlay {
    private HudOverlay() { }

    private static final int W = 198, PAD = 8, MARGIN = 6;

    public static void render(DrawContext g, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ModConfig cfg = ModConfig.get();
        CameraSmoother.frame(mc, Math.min(2f, tick.getDynamicDeltaTicks()));
        if (!cfg.hudVisible || mc.options.hudHidden || mc.player == null) return;

        TextRenderer tr = mc.textRenderer;
        AiController ai = AiController.INSTANCE;
        Ctx c = ai.lastCtx;
        Action a = ai.lastAction;
        FightTracker ft = ai.tracker;
        int accent = Theme.accent(cfg);
        int op = cfg.hudOpacity;

        String[] names = {"Jump", "Crit", "J.Reset", "Circle", "Retreat", "W-Tap", "S-Tap", "Shift", "Upper", "KB"};
        boolean[] on = {cfg.jump, cfg.crit, cfg.jumpReset, cfg.strafe, cfg.retreatLow, cfg.wTap, cfg.sTap, cfg.shiftTap, cfg.uppercut, cfg.kbDisplace};
        boolean compact = cfg.hudCompact;
        int inner = W - PAD * 2;
        int rows = chipRows(tr, names, inner);

        int h = 7 + 14 + 12 + 4 + 12 + 3 * 14 + 7;                              // header, subtitle, status, bars
        if (!compact) h += 4 + 16 + 4 + rows * 13 + 4 + 4 * 11;                 // keys, move chips, footer
        int sw = g.getScaledWindowWidth(), sh = g.getScaledWindowHeight();
        int x = switch (cfg.hudCorner) { case TOP_LEFT, BOTTOM_LEFT -> MARGIN; case TOP_RIGHT, BOTTOM_RIGHT -> sw - W - MARGIN; };
        int y = switch (cfg.hudCorner) { case TOP_LEFT, TOP_RIGHT -> MARGIN + 18; case BOTTOM_LEFT, BOTTOM_RIGHT -> sh - h - MARGIN - 24; };

        // card
        Theme.rounded(g, x, y, W, h, 5, Theme.alpha(Theme.STROKE, op));
        Theme.rounded(g, x + 1, y + 1, W - 2, h - 2, 4, Theme.alpha(Theme.BG, op));
        Theme.rounded(g, x + 5, y + 1, W - 10, 2, 1, accent);

        int ix = x + PAD, cy = y + 7;

        // header: title, kit, state pill
        int tw = Theme.spaced(g, tr, "EVOLUTION", ix, cy + 1, accent, 1, true);
        g.drawText(tr, cfg.kit.label, ix + tw + 6, cy + 1, Theme.MUTED, false);
        String stateText = cfg.enabled ? "ON" : "OFF";
        int pw = tr.getWidth(stateText) + 10;
        int pillCol = cfg.enabled ? accent : Theme.OFF;
        Theme.rounded(g, ix + inner - pw, cy, pw, 12, 6, Theme.alpha(pillCol, cfg.enabled ? 40 : 100));
        g.drawText(tr, stateText, ix + inner - pw + 5, cy + 2, cfg.enabled ? accent : Theme.DIM, false);
        cy += 14;

        g.drawText(tr, cfg.mode.label + "  /  " + cfg.strategy.label, ix, cy + 1, Theme.DIM, false);
        cy += 12 + 4;
        g.drawText(tr, tr.trimToWidth((cfg.enabled ? "" : "off  ") + ai.status, inner), ix, cy, Theme.TEXT, false);
        cy += 12;

        // bars
        double hp = c == null ? 0 : c.health / Math.max(1f, c.maxHealth);
        double ohp = c == null ? 0 : c.tHealth / Math.max(1f, c.tMaxHealth);
        bar(g, tr, ix, cy, inner, "You", c == null ? "-" : String.format("%.0f", c.health), hp, hpColor(hp)); cy += 14;
        bar(g, tr, ix, cy, inner, "Opponent", c == null ? "-" : String.format("%.0f", c.tHealth), ohp, hpColor(ohp)); cy += 14;
        bar(g, tr, ix, cy, inner, "Charge", c == null ? "-" : String.format("%.0f%%", c.cooldown * 100), c == null ? 0 : c.cooldown, accent); cy += 14;

        if (compact) return;

        // key caps + action chips
        cy += 4;
        String[] caps = {"W", "A", "S", "D"};
        boolean[] pressed = {a != null && a.forward, a != null && a.left, a != null && a.back, a != null && a.right};
        for (int i = 0; i < 4; i++) {
            int kx = ix + i * 17;
            Theme.rounded(g, kx, cy, 15, 15, 3, pressed[i] ? accent : Theme.CARD);
            g.drawText(tr, caps[i], kx + 8 - tr.getWidth(caps[i]) / 2, cy + 4, pressed[i] ? Theme.BG : Theme.MUTED, false);
        }
        int kx = ix + 4 * 17 + 4;
        kx += tag(g, tr, kx, cy, "JUMP", a != null && a.jump, accent) + 3;
        kx += tag(g, tr, kx, cy, "HIT", a != null && a.attack, accent) + 3;
        tag(g, tr, kx, cy, "SNEAK", a != null && a.sneak, accent);
        cy += 16 + 4;

        // move chips (wrap to the card width)
        int cxp = ix, row = 0;
        for (int i = 0; i < names.length; i++) {
            int cwid = tr.getWidth(names[i]) + 8;
            if (cxp + cwid > ix + inner) { cxp = ix; row++; }
            int chipY = cy + row * 13;
            Theme.rounded(g, cxp, chipY, cwid, 11, 3, on[i] ? Theme.mix(accent, Theme.BG, 0.72f) : Theme.CARD);
            g.drawText(tr, names[i], cxp + 4, chipY + 2, on[i] ? accent : Theme.MUTED, false);
            cxp += cwid + 3;
        }
        cy += rows * 13 + 4;

        // footer
        footer(g, tr, ix, cy, inner, "Scope", ScopeGate.describe(mc), Theme.DIM);
        footer(g, tr, ix, cy + 11, inner, "Crit", ai.critState(), Theme.DIM);
        footer(g, tr, ix, cy + 22, inner, "Util", ai.utilState(), Theme.DIM);
        String hits = cfg.recordDemos
                ? String.format("%d/%d  ghosts %d  REC %d", ft.landed, ft.attempts, ft.ghosts, Recorder.INSTANCE.rows)
                : String.format("%d/%d  ghosts %d  reach -%.2f", ft.landed, ft.attempts, ft.ghosts, cfg.reachMargin + ft.reachPenalty);
        footer(g, tr, ix, cy + 33, inner, "Hits", hits, ft.ghosts > ft.landed && ft.attempts > 4 ? Theme.BAD : Theme.DIM);
    }

    private static int chipRows(TextRenderer tr, String[] names, int maxW) {
        int x = 0, rows = 1;
        for (String n : names) {
            int w = tr.getWidth(n) + 8;
            if (x + w > maxW) { x = 0; rows++; }
            x += w + 3;
        }
        return rows;
    }

    private static int hpColor(double f) { return f > 0.5 ? Theme.GOOD : f > 0.25 ? Theme.WARN : Theme.BAD; }

    private static void bar(DrawContext g, TextRenderer tr, int x, int y, int w, String label, String value, double frac, int color) {
        g.drawText(tr, label, x, y, Theme.MUTED, false);
        g.drawText(tr, value, x + w - tr.getWidth(value), y, Theme.TEXT, false);
        Theme.rounded(g, x, y + 10, w, 3, 1, Theme.OFF);
        int fw = (int) Math.round(w * Math.max(0, Math.min(1, frac)));
        if (fw > 0) Theme.rounded(g, x, y + 10, Math.max(2, fw), 3, 1, color);
    }

    private static int tag(DrawContext g, TextRenderer tr, int x, int y, String text, boolean lit, int accent) {
        int w = tr.getWidth(text) + 8;
        Theme.rounded(g, x, y, w, 15, 3, lit ? accent : Theme.CARD);
        g.drawText(tr, text, x + 4, y + 4, lit ? Theme.BG : Theme.MUTED, false);
        return w;
    }

    private static void footer(DrawContext g, TextRenderer tr, int x, int y, int w, String label, String value, int color) {
        g.drawText(tr, label, x, y, Theme.MUTED, false);
        g.drawText(tr, tr.trimToWidth(value, w - 34), x + 34, y, color, false);
    }
}
