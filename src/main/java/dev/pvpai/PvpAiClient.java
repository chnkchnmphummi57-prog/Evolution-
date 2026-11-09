package dev.pvpai;

import dev.pvpai.config.ModConfig;
import dev.pvpai.core.AiController;
import dev.pvpai.core.Hotkeys;
import dev.pvpai.core.ScopeGate;
import dev.pvpai.ui.HudOverlay;
import dev.pvpai.ui.PvpAiScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.text.Text;

public final class PvpAiClient implements ClientModInitializer {
    public static final String ID = "pvpai";

    @Override
    public void onInitializeClient() {
        ModConfig.load();

        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            ModConfig cfg = ModConfig.get();
            // Hotkeys are polled directly (any key from the menu). Poll every tick so edge state stays fresh.
            boolean toggle = Hotkeys.edge(mc, cfg.toggleKey);
            boolean hud = Hotkeys.edge(mc, cfg.hudKey);
            boolean menu = Hotkeys.edge(mc, cfg.menuKey);

            if (mc.currentScreen == null && mc.player != null) {
                if (toggle) {
                    if (!cfg.enabled && !ScopeGate.allowed(mc)) {
                        mc.player.sendMessage(Text.literal("[PvP AI] Blocked: only singleplayer or allowlisted servers."), true);
                    } else {
                        cfg.enabled = !cfg.enabled;
                    }
                }
                if (hud) {
                    cfg.hudVisible = !cfg.hudVisible;
                    ModConfig.save();
                    mc.player.sendMessage(Text.literal("[PvP AI] HUD " + (cfg.hudVisible ? "shown" : "hidden")), true);
                }
                if (menu) mc.setScreen(new PvpAiScreen(null));
            }
            if (mc.player != null && mc.world != null) AiController.INSTANCE.tick(mc);
        });

        HudRenderCallback.EVENT.register(HudOverlay::render);
    }
}
