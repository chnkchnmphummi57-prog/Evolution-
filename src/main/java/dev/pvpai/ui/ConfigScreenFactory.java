package dev.pvpai.ui;

import dev.pvpai.config.HudCorner;
import dev.pvpai.config.InputMode;
import dev.pvpai.config.ModConfig;
import dev.pvpai.core.AiMode;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

public final class ConfigScreenFactory {
    private ConfigScreenFactory() { }

    public static Screen create(Screen parent) {
        ModConfig cfg = ModConfig.get();
        ConfigBuilder b = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.literal("PvP AI"))
                .setTransparentBackground(true)
                .setSavingRunnable(ModConfig::save);
        ConfigEntryBuilder e = b.entryBuilder();

        ConfigCategory general = b.getOrCreateCategory(Text.literal("General"));
        general.addEntry(e.startEnumSelector(Text.literal("Mode"), AiMode.class, cfg.mode)
                .setDefaultValue(AiMode.STRICT).setEnumNameProvider(m -> Text.literal(((AiMode) m).label))
                .setSaveConsumer(v -> cfg.mode = v).build());
        general.addEntry(e.startBooleanToggle(Text.literal("Hard-mask disabled movements"), cfg.strictEnforcement)
                .setDefaultValue(true).setSaveConsumer(v -> cfg.strictEnforcement = v).build());
        general.addEntry(e.startStrList(Text.literal("Allowlisted servers (yours only)"), cfg.allowedServers)
                .setSaveConsumer(v -> cfg.allowedServers = List.copyOf(v)).build());

        ConfigCategory hud = b.getOrCreateCategory(Text.literal("HUD"));
        hud.addEntry(e.startBooleanToggle(Text.literal("Show HUD"), cfg.hudVisible)
                .setDefaultValue(true).setSaveConsumer(v -> cfg.hudVisible = v).build());
        hud.addEntry(e.startBooleanToggle(Text.literal("Compact HUD"), cfg.hudCompact)
                .setDefaultValue(false).setSaveConsumer(v -> cfg.hudCompact = v).build());
        hud.addEntry(e.startEnumSelector(Text.literal("HUD corner"), HudCorner.class, cfg.hudCorner)
                .setDefaultValue(HudCorner.TOP_LEFT).setEnumNameProvider(m -> Text.literal(((HudCorner) m).label))
                .setSaveConsumer(v -> cfg.hudCorner = v).build());

        ConfigCategory moves = b.getOrCreateCategory(Text.literal("Movements"));
        moves.addEntry(e.startBooleanToggle(Text.literal("Jump"), cfg.jump).setDefaultValue(true).setSaveConsumer(v -> cfg.jump = v).build());
        moves.addEntry(e.startBooleanToggle(Text.literal("Crit"), cfg.crit).setDefaultValue(true).setSaveConsumer(v -> cfg.crit = v).build());
        moves.addEntry(e.startBooleanToggle(Text.literal("Jump Reset"), cfg.jumpReset).setDefaultValue(true).setSaveConsumer(v -> cfg.jumpReset = v).build());
        moves.addEntry(e.startBooleanToggle(Text.literal("Strafing"), cfg.strafe).setDefaultValue(true).setSaveConsumer(v -> cfg.strafe = v).build());
        moves.addEntry(e.startBooleanToggle(Text.literal("Retreat When Low"), cfg.retreatLow).setDefaultValue(true).setSaveConsumer(v -> cfg.retreatLow = v).build());
        moves.addEntry(e.startBooleanToggle(Text.literal("W-Tap"), cfg.wTap).setDefaultValue(true).setSaveConsumer(v -> cfg.wTap = v).build());

        ConfigCategory tune = b.getOrCreateCategory(Text.literal("Tuning"));
        tune.addEntry(e.startIntSlider(Text.literal("Max turn (deg/tick)"), cfg.maxTurnDegPerTick, 2, 60)
                .setDefaultValue(18).setSaveConsumer(v -> cfg.maxTurnDegPerTick = v).build());
        tune.addEntry(e.startIntSlider(Text.literal("Strafe period (ticks)"), cfg.strafePeriodTicks, 4, 40)
                .setDefaultValue(14).setSaveConsumer(v -> cfg.strafePeriodTicks = v).build());
        tune.addEntry(e.startDoubleField(Text.literal("Retreat health (HP)"), cfg.retreatHealth)
                .setDefaultValue(6.0).setMin(1).setMax(19).setSaveConsumer(v -> cfg.retreatHealth = v).build());
        tune.addEntry(e.startIntSlider(Text.literal("Crit chance per attack (%)"), cfg.critChancePercent, 0, 100)
                .setDefaultValue(20).setSaveConsumer(v -> cfg.critChancePercent = v).build());
        tune.addEntry(e.startIntSlider(Text.literal("Combo lock (hits)"), cfg.comboLock, 1, 15)
                .setDefaultValue(5).setSaveConsumer(v -> cfg.comboLock = v).build());
        tune.addEntry(e.startBooleanToggle(Text.literal("Crit-spam response"), cfg.critSpamResponse)
                .setDefaultValue(true).setSaveConsumer(v -> cfg.critSpamResponse = v).build());
        tune.addEntry(e.startIntSlider(Text.literal("Camera smoothness"), cfg.cameraSmoothness, 0, 100)
                .setDefaultValue(60).setSaveConsumer(v -> cfg.cameraSmoothness = v).build());
        tune.addEntry(e.startEnumSelector(Text.literal("Input mode"), InputMode.class, cfg.inputMode)
                .setDefaultValue(InputMode.INPUT).setEnumNameProvider(m -> Text.literal(((InputMode) m).label))
                .setSaveConsumer(v -> cfg.inputMode = v).build());
        return b.build();
    }
}
