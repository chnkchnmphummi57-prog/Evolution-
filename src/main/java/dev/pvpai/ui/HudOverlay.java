package dev.pvpai.ui;

import dev.pvpai.act.Action;
import dev.pvpai.act.CameraSmoother;
import dev.pvpai.config.ModConfig;
import dev.pvpai.core.AiController;
import dev.pvpai.core.ScopeGate;
import dev.pvpai.observe.Ctx;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/** Live HUD panel. Toggle with the HUD key (default \) or the HUD tab in the config menu. */
public final class HudOverlay {
    private HudOverlay() { }

    private static final int BG = 0xD00D1117, BORDER = 0x40FFFFFF, TEXT = 0xFFE6EDF3, DIM = 0xFF8B949E,
            GREEN = 0xFF3DDC84, AMBER = 0xFFF2C94C, RED = 0xFFFF5C5C, TRACK = 0xFF1C2330, OFF_TEXT = 0xFF5B6470,
            ML_COL = 0xFFA78BFA, STRICT_COL = 0xFF58A6FF, SMART_COL = 0xFFF0883E;
    private static final int W = 190, PAD = 7, MARGIN = 6;

    public static void render(DrawContext g, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ModConfig cfg = ModConfig.get();
        CameraSmoother.frame(mc, Math.min(2f, tick.getDynamicDeltaTicks()));
        if (!cfg.hudVisible || mc.options.hudHidden || mc.player == null) return;

        TextRenderer tr = mc.textRenderer;
        AiController ai = AiController.INSTANCE;
        Ctx c = ai.lastCtx;
        Action a = ai.lastAction;

        boolean compact = cfg.hudCompact;
        int h = PAD * 2 + 18 + 12 + 39 + (compact ? 0 : 22 + 46 + 36);
        int sw = g.getScaledWindowWidth(), sh = g.getScaledWindowHeight();
        int x = switch (cfg.hudCorner) {
            case TOP_LEFT, BOTTOM_LEFT -> MARGIN;
            case TOP_RIGHT, BOTTOM_RIGHT -> sw - W - MARGIN;
        };
        int y = switch (cfg.hudCorner) {
            case TOP_LEFT, TOP_RIGHT -> MARGIN;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> sh - h - MARGIN - 24; // clear of the hotbar
        };

        int accent = cfg.enabled ? GREEN : OFF_TEXT;
        panel(g, x, y, W, h, accent);

        int ix = x + PAD, iw = W - PAD * 2, cy = y + PAD;

        // Header: status dot, title, mode pill
        g.fill(ix, cy + 3, ix + 6, cy + 9, accent);
        g.drawText(tr, "PvP AI", ix + 11, cy + 1, TEXT, true);
        g.drawText(tr, cfg.kit.label, ix + 11 + tr.getWidth("PvP AI") + 5, cy + 1, DIM, false);
        String modeLabel = (cfg.mode.label + " " + cfg.strategy.label).toUpperCase();
        int modeCol = switch (cfg.mode) { case ML -> ML_COL; case STRICT -> STRICT_COL; case SMART -> SMART_COL; };
        int pw = tr.getWidth(modeLabel) + 10;
        g.fill(ix + iw - pw, cy, ix + iw, cy + 12, (modeCol & 0x00FFFFFF) | 0x50000000);
        g.fill(ix + iw - pw, cy + 11, ix + iw, cy + 12, modeCol);
        g.drawText(tr, modeLabel, ix + iw - pw + 5, cy + 2, modeCol, false);
        cy += 18;

        // Status line
        String state = (cfg.enabled ? "" : "OFF - ") + ai.status;
        g.drawText(tr, tr.trimToWidth(state, iw), ix, cy, DIM, false);
        cy += 12;

        // Bars
        double hp = c == null ? 0 : c.health / Math.max(1f, c.maxHealth);
        double ohp = c == null ? 0 : c.tHealth / Math.max(1f, c.tMaxHealth);
        bar(g, tr, ix, cy, iw, "You", c == null ? "-" : String.format("%.0f", c.health), hp, hpColor(hp)); cy += 13;
        bar(g, tr, ix, cy, iw, "Opponent", c == null ? "-" : String.format("%.0f", c.tHealth), ohp, hpColor(ohp)); cy += 13;
        bar(g, tr, ix, cy, iw, "Attack charge", c == null ? "-" : String.format("%.0f%%", c.cooldown * 100), c == null ? 0 : c.cooldown, STRICT_COL); cy += 13;

        if (compact) return;

        // Key caps + action chips
        cy += 4;
        String[] caps = {"W", "A", "S", "D"};
        boolean[] on = {a != null && a.forward, a != null && a.left, a != null && a.back, a != null && a.right};
        for (int i = 0; i < 4; i++) {
            int kx = ix + i * 17;
            g.fill(kx, cy, kx + 15, cy + 15, on[i] ? (GREEN & 0x00FFFFFF) | 0xAA000000 : TRACK);
            g.drawText(tr, caps[i], kx + 8 - tr.getWidth(caps[i]) / 2, cy + 4, on[i] ? 0xFF0D1117 : OFF_TEXT, false);
        }
        int cx = ix + 4 * 17 + 6;
        cx += chip(g, tr, cx, cy, "JUMP", a != null && a.jump) + 3;
        chip(g, tr, cx, cy, "HIT", a != null && a.attack);
        cy += 18;

        // Movement toggles
        String[] names = {"Jump", "Crit", "J.Reset", "Strafe", "Retreat", "W-Tap", "S-Tap"};
        boolean[] state2 = {cfg.jump, cfg.crit, cfg.jumpReset, cfg.strafe, cfg.retreatLow, cfg.wTap, cfg.sTap};
        int cw = (iw - 6) / 3;
        for (int i = 0; i < names.length; i++) {
            int tx = ix + (i % 3) * (cw + 3), ty = cy + (i / 3) * 14;
            g.fill(tx, ty, tx + cw, ty + 12, state2[i] ? (GREEN & 0x00FFFFFF) | 0x45000000 : TRACK);
            g.drawText(tr, names[i], tx + (cw - tr.getWidth(names[i])) / 2, ty + 2, state2[i] ? GREEN : OFF_TEXT, false);
        }
        cy += 46;

        // Footer
        g.drawText(tr, tr.trimToWidth("Scope: " + ScopeGate.describe(mc), iw), ix, cy, DIM, false);
        g.drawText(tr, tr.trimToWidth("Crit: " + ai.critState(), iw), ix, cy + 11, DIM, false);
        g.drawText(tr, tr.trimToWidth("Util: " + ai.utilState(), iw), ix, cy + 22, DIM, false);
    }

    private static int hpColor(double f) { return f > 0.5 ? GREEN : f > 0.25 ? AMBER : RED; }

    private static void panel(DrawContext g, int x, int y, int w, int h, int accent) {
        g.fill(x + 1, y, x + w - 1, y + h, BG);          // body with notched corners
        g.fill(x, y + 1, x + w, y + h - 1, BG);
        g.fill(x + 1, y, x + w - 1, y + 1, BORDER);      // border
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, BORDER);
        g.fill(x, y + 1, x + 1, y + h - 1, BORDER);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, BORDER);
        g.fill(x + 2, y, x + w - 2, y + 2, accent);      // accent strip
    }

    private static void bar(DrawContext g, TextRenderer tr, int x, int y, int w, String label, String value, double frac, int color) {
        g.drawText(tr, label, x, y, DIM, false);
        g.drawText(tr, value, x + w - tr.getWidth(value), y, TEXT, false);
        g.fill(x, y + 10, x + w, y + 12, TRACK);
        int fw = (int) Math.round(w * Math.max(0, Math.min(1, frac)));
        if (fw > 0) g.fill(x, y + 10, x + fw, y + 12, color);
    }

    private static int chip(DrawContext g, TextRenderer tr, int x, int y, String text, boolean lit) {
        int w = tr.getWidth(text) + 8;
        g.fill(x, y, x + w, y + 15, lit ? (GREEN & 0x00FFFFFF) | 0xAA000000 : TRACK);
        g.drawText(tr, text, x + 4, y + 4, lit ? 0xFF0D1117 : OFF_TEXT, false);
        return w;
    }
}
