package dev.pvpai.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.pvpai.core.AiMode;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("pvpai.json");
    private static ModConfig instance;

    public AiMode mode = AiMode.STRICT;
    public boolean enabled = false;          // always starts OFF each launch

    // Movement toggles
    public boolean jump = true, crit = true, jumpReset = true, strafe = true, retreatLow = true, wTap = true, sTap = true;
    public boolean strictEnforcement = true; // mask disabled actions at the act layer (ML/Smart soft penalty comes later)

    // Strategy and keys
    public Strategy strategy = Strategy.NORMAL;
    public HotKey menuKey = HotKey.LEFT_BRACKET, toggleKey = HotKey.RIGHT_BRACKET, hudKey = HotKey.BACKSLASH;

    // Modern PvP
    public boolean shieldBreak = true;       // axe the opponent's shield after they block for a while
    public int shieldDelayMin = 20, shieldDelayMax = 100;   // ticks of sustained blocking before the attempt (Strict: min, Smart: random)
    public int shieldFailChance = 20;        // Smart only. Strict never fails.
    public boolean autoTotem = true;
    public int totemHealth = 10;
    public boolean autoEat = true;           // golden apple when low and the opponent is not close
    public int eatHealth = 8;
    public boolean safeBlock = true;         // Safe strategy raises the offhand shield while waiting

    // HUD
    public boolean hudVisible = true, hudCompact = false;
    public HudCorner hudCorner = HudCorner.TOP_LEFT;

    // Combat behaviour
    public int critChancePercent = 20;       // chance per attack to jump first and land a crit
    public int comboLock = 5;                // at this many consecutive hits the crit chance is disabled
    public boolean critSpamResponse = true;  // run back -> knockback hit -> crit spam + jump reset
    public int runBackTicks = 10;
    public int punishTicks = 100;
    public int wTapTicks = 2;                // W-tap: release forward (this also stops sprint) for this many ticks after a hit
    public int sTapTicks = 3;                // S-tap: press back for this many ticks after a hit
    public int sTapChance = 100;             // % of hits followed by an S-tap (when in range)
    public double sTapMaxDist = 3.0;         // only S-tap when the opponent is within this distance

    // Input and camera
    public InputMode inputMode = InputMode.INPUT;
    public JumpInput jumpInput = JumpInput.KEY;
    public int cameraSmoothness = 60;        // 0 = snappy, 100 = very smooth

    // Tuning
    public double retreatHealth = 6.0;
    public int maxTurnDegPerTick = 18;
    public double engageDist = 2.9, minDist = 1.8, reach = 3.0, targetRange = 12.0;
    public int strafePeriodTicks = 14;

    // Scope gate: singleplayer is always allowed. Add ONLY servers you own / run.
    public List<String> allowedServers = new ArrayList<>();

    public static ModConfig get() {
        if (instance == null) load();
        return instance;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) instance = GSON.fromJson(Files.readString(FILE), ModConfig.class);
        } catch (Exception ignored) { }
        if (instance == null) instance = new ModConfig();
        instance.enabled = false;
    }

    public static void save() {
        try { Files.writeString(FILE, GSON.toJson(get())); } catch (IOException ignored) { }
    }
}
