package dev.pvpai;

import dev.pvpai.config.ModConfig;
import dev.pvpai.act.ActionApplier;
import dev.pvpai.core.AiController;
import dev.pvpai.core.Hotkeys;
import dev.pvpai.core.ScopeGate;
import dev.pvpai.ml.MlBackend;
import dev.pvpai.ml.Recorder;
import dev.pvpai.ui.HudOverlay;
import dev.pvpai.ui.PvpAiScreen;
import dev.pvpai.ui.Watermark;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.text.Text;

public final class PvpAiClient implements ClientModInitializer {
    public static final String ID = "pvpai";

    @Override
    public void onInitializeClient() {
        ModConfig.load();
        MlBackend.INSTANCE.load();

        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            ModConfig cfg = ModConfig.get();
            // Hotkeys are polled directly (any key from the menu). Poll every tick so edge state stays fresh.
            boolean toggle = Hotkeys.edge(mc, cfg.toggleKey);
            boolean hud = Hotkeys.edge(mc, cfg.hudKey);
            boolean menu = Hotkeys.edge(mc, cfg.menuKey);

            if (mc.currentScreen == null && mc.player != null) {
                if (toggle) {
                    if (!cfg.enabled && !ScopeGate.allowed(mc)) {
                        mc.player.sendMessage(Text.literal("[Evolution] Blocked: only singleplayer or allowlisted servers."), true);
                    } else {
                        cfg.enabled = !cfg.enabled;
                    }
                }
                if (hud) {
                    cfg.hudVisible = !cfg.hudVisible;
                    ModConfig.save();
                    mc.player.sendMessage(Text.literal("[Evolution] HUD " + (cfg.hudVisible ? "shown" : "hidden")), true);
                }
                if (menu) mc.setScreen(new PvpAiScreen(null));
            }
            MlBackend.INSTANCE.poll();
            if (mc.player != null && mc.world != null) AiController.INSTANCE.tick(mc);
        });

        // After the player tick: release the click and record the rotation the movement packet just carried.
        ClientTickEvents.END_CLIENT_TICK.register(ActionApplier::endTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> Recorder.INSTANCE.close());

        HudRenderCallback.EVENT.register(HudOverlay::render);
        HudRenderCallback.EVENT.register(Watermark::render);
    }
}
