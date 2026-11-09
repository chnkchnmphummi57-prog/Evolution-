package dev.pvpai.core;

import dev.pvpai.config.HotKey;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/** Rising-edge key polling. Call every tick for every key so the stored state never goes stale. */
public final class Hotkeys {
    private Hotkeys() { }
    private static final Map<Integer, Boolean> DOWN = new HashMap<>();

    public static boolean edge(MinecraftClient mc, HotKey key) {
        boolean now = GLFW.glfwGetKey(mc.getWindow().getHandle(), key.code) == GLFW.GLFW_PRESS;
        boolean was = DOWN.getOrDefault(key.code, false);
        DOWN.put(key.code, now);
        return now && !was;
    }
}
