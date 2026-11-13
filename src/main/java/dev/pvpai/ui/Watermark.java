package dev.pvpai.ui;

import dev.pvpai.config.ModConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Adjustable watermark: text, parts, style, corner, offsets and opacity are all in the Mark tab / YACL Watermark page. */
public final class Watermark {
    private Watermark() { }

    private static String version;

    public static void render(DrawContext g, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ModConfig cfg = ModConfig.get();
        if (!cfg.watermarkEnabled || mc.options.hudHidden) return;

        TextRenderer tr = mc.textRenderer;
        int accent = Theme.accent(cfg);
        int op = Math.max(5, Math.min(100, cfg.watermarkOpacity));

        String title = cfg.watermarkText == null || cfg.watermarkText.isBlank() ? "Evolution" : cfg.watermarkText;
        List<String> parts = new ArrayList<>();
        if (cfg.watermarkVersion) parts.add("v" + version());
        if (cfg.watermarkFps) parts.add(mc.getCurrentFps() + " fps");
        if (cfg.watermarkPing) parts.add(ping(mc));
        if (cfg.watermarkTime) parts.add(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        if (cfg.watermarkState) parts.add(cfg.enabled ? "AI ON" : "AI OFF");

        boolean box = cfg.watermarkStyle == dev.pvpai.config.WatermarkStyle.BOX;
        int pad = box ? 6 : 0;
        int tw = tr.getWidth(title);
        int w = tw;
        for (String p : parts) w += 9 + tr.getWidth(p);
        int h = 12;
        int bw = w + pad * 2 + (box ? 3 : 0), bh = h + (box ? 6 : 0);

        int sw = g.getScaledWindowWidth(), sh = g.getScaledWindowHeight();
        int x = switch (cfg.watermarkCorner) { case TOP_LEFT, BOTTOM_LEFT -> cfg.watermarkX; case TOP_RIGHT, BOTTOM_RIGHT -> sw - bw - cfg.watermarkX; };
        int y = switch (cfg.watermarkCorner) { case TOP_LEFT, TOP_RIGHT -> cfg.watermarkY; case BOTTOM_LEFT, BOTTOM_RIGHT -> sh - bh - cfg.watermarkY; };

        int tx = x;
        int ty = y + (box ? 3 : 0) + 2;
        if (box) {
            Theme.rounded(g, x, y, bw, bh, 4, Theme.alpha(Theme.STROKE, op));
            Theme.rounded(g, x + 1, y + 1, bw - 2, bh - 2, 3, Theme.alpha(Theme.BG, Math.min(100, op)));
            Theme.rounded(g, x + 1, y + 3, 2, bh - 6, 1, Theme.alpha(accent, op));   // accent bar
            tx = x + 3 + pad;
        }

        g.drawText(tr, title, tx, ty, Theme.alpha(accent, op), true);
        int cx = tx + tw;
        for (String p : parts) {
            g.drawText(tr, "|", cx + 3, ty, Theme.alpha(Theme.MUTED, op), false);
            g.drawText(tr, p, cx + 9, ty, Theme.alpha(Theme.TEXT, op), false);
            cx += 9 + tr.getWidth(p);
        }
        if (cfg.watermarkStyle == dev.pvpai.config.WatermarkStyle.LINE) {
            g.fill(tx, ty + 11, tx + w, ty + 12, Theme.alpha(accent, op));
        }
    }

    private static String ping(MinecraftClient mc) {
        if (mc.isInSingleplayer()) return "local";
        var nh = mc.getNetworkHandler();
        if (nh != null && mc.player != null) {
            var entry = nh.getPlayerListEntry(mc.player.getUuid());
            if (entry != null) return entry.getLatency() + " ms";
        }
        return "- ms";
    }

    static String version() {
        if (version == null) {
            version = FabricLoader.getInstance().getModContainer("pvpai")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        }
        return version;
    }
}
