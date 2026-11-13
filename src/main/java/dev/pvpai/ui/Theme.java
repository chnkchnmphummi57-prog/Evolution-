package dev.pvpai.ui;

import dev.pvpai.config.AccentColor;
import dev.pvpai.config.ModConfig;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Shared colours and drawing helpers for the Evolution HUD, menu and watermark. Only plain fills, so it is version-proof. */
public final class Theme {
    private Theme() { }

    public static final int BG = 0xFF0B0C1E, SIDEBAR = 0xFF0E1026, CARD = 0xFF13152F, CARD_HOVER = 0xFF1B1E42, STROKE = 0xFF24274D,
            TEXT = 0xFFEEF0FF, DIM = 0xFF8C90B5, MUTED = 0xFF5A5E85, OFF = 0xFF2C2F58,
            GOOD = 0xFF4ADE80, WARN = 0xFFFBBF24, BAD = 0xFFF87171;

    public static int accent(ModConfig cfg) {
        if (cfg.accent == AccentColor.RAINBOW) return hsv((System.currentTimeMillis() % 6000L) / 6000f, 0.55f, 1.0f);
        return cfg.accent.argb;
    }

    public static int hsv(float h, float s, float v) {
        int i = (int) (h * 6f);
        float f = h * 6f - i, p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return 0xFF000000 | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    /** Multiplies the colour's alpha by pct percent. */
    public static int alpha(int argb, int pct) {
        int a = ((argb >>> 24) * Math.max(0, Math.min(100, pct))) / 100;
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    public static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    public static boolean in(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    /** Filled rounded rectangle from horizontal strips. */
    public static void rounded(DrawContext g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) { g.fill(x, y, x + w, y + h, color); return; }
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
        g.fill(x, y + r, x + w, y + h - r, color);
    }

    /** Rounded card with a 1px stroke. */
    public static void card(DrawContext g, int x, int y, int w, int h, int r, int fill, int stroke) {
        rounded(g, x, y, w, h, r, stroke);
        rounded(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
    }

    /** Letter-spaced text. Returns the width drawn. */
    public static int spaced(DrawContext g, TextRenderer tr, String s, int x, int y, int color, int spacing, boolean shadow) {
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            g.drawText(tr, ch, cx, y, color, shadow);
            cx += tr.getWidth(ch) + spacing;
        }
        return cx - x - spacing;
    }

    public static int spacedWidth(TextRenderer tr, String s, int spacing) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) w += tr.getWidth(String.valueOf(s.charAt(i))) + spacing;
        return Math.max(0, w - spacing);
    }
}
