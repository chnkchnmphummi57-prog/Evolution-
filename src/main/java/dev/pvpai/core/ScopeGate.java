package dev.pvpai.core;

import dev.pvpai.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.util.Locale;

/** Hard gate: the AI only runs in singleplayer/LAN-host worlds or on servers the owner allowlisted. */
public final class ScopeGate {
    private ScopeGate() { }

    public static boolean allowed(MinecraftClient c) {
        if (c.world == null || c.player == null) return false;
        if (c.isInSingleplayer()) return true;
        ServerInfo e = c.getCurrentServerEntry();
        if (e == null) return false;
        String addr = e.address.toLowerCase(Locale.ROOT);
        return ModConfig.get().allowedServers.stream().anyMatch(s -> s.trim().equalsIgnoreCase(addr));
    }

    public static String describe(MinecraftClient c) {
        if (c.world == null) return "no world";
        if (c.isInSingleplayer()) return "singleplayer";
        return allowed(c) ? "allowlisted server" : "BLOCKED (server not allowlisted)";
    }
}
