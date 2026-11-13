package dev.pvpai.ui;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.ListOption;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.pvpai.config.ModConfig;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;

/** The one thing the Evolution menu cannot do itself: a list editor, for the server allowlist. */
public final class YaclScreenFactory {
    private YaclScreenFactory() { }

    public static Screen create(Screen parent) {
        ModConfig cfg = ModConfig.get();
        ConfigCategory general = ConfigCategory.createBuilder()
                .name(Text.literal("Server allowlist"))
                .group(ListOption.<String>createBuilder()
                        .name(Text.literal("Allowlisted servers"))
                        .description(OptionDescription.of(Text.literal("Servers you own or run. Singleplayer is always allowed. Anywhere else the AI refuses to start.")))
                        .binding(new ArrayList<>(), () -> cfg.allowedServers, v -> cfg.allowedServers = new ArrayList<>(v))
                        .controller(StringControllerBuilder::create)
                        .initial("")
                        .build())
                .build();
        return YetAnotherConfigLib.createBuilder()
                .title(Text.literal("Evolution"))
                .category(general)
                .save(ModConfig::save)
                .build()
                .generateScreen(parent);
    }
}
