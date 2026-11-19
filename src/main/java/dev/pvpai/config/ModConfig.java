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

    public int configVersion = 0;            // used to migrate old saved values
    public AiMode mode = AiMode.STRICT;
    public boolean enabled = false;          // always starts OFF each launch
    public Strategy strategy = Strategy.NORMAL;
    public Kit kit = Kit.SWORD;

    // Core moves
    public boolean jump = true, crit = true, jumpReset = true, strafe = true, retreatLow = true, wTap = true, sTap = true;
    public boolean strictEnforcement = true; // disabled moves are never used

    // Crit
    public int critChancePercent = 20;       // chance per attack to jump first and land a crit
    public int comboLock = 5;                // at this many consecutive hits the crit chance is disabled
    public int critLaunchPercent = 70;       // start the jump at this attack charge; the hit still needs ~92% (the jump takes ~7 ticks)
    public boolean critAutoLaunch = false;   // time the jump so the falling hit is charged (fastest double crits)
    public boolean critSpamResponse = true;  // opponent crit-spams: run back -> knockback hit -> crit spam / combo
    public int critResponsePriority = 75;    // Smart: % chance the response picks crit spam over comboing. Strict: crit first.
    public int runBackTicks = 10, punishTicks = 100;
    public JumpResetMode jumpResetMode = JumpResetMode.REACT;

    // Taps (all fire on the hit tick, or one tick after for S-tap)
    public int wTapTicks = 2;                // W-tap: release forward for this many ticks after a hit
    public int sTapTicks = 3;                // S-tap: press back for this many ticks
    public int sTapChance = 30;              // % of hits followed by an S-tap (when in range)
    public int sTapDelay = 0;                // 0 = S on the same tick as the hit, 1 = one tick later
    public double sTapMaxDist = 2.5;         // only S-tap when the opponent is within this distance
    public boolean shiftTap = true;          // sneak on the same tick as the hit (sprint drops, forward stays held)
    public int shiftTapTicks = 1, shiftTapChance = 20;

    // Techniques
    public boolean hitSelect = true;         // after taking a hit, counter at once
    public int circleTicks = 30;             // circle strafing: ticks per direction (hold W + A or D around the opponent)
    public boolean uppercut = true;          // jump and hit together, then S
    public int uppercutChance = 10;
    public int uppercutJumpDelay = 1;        // >0: hit first, jump this many ticks later. 0: together. <0: jump first, hit this many ticks later
    public int uppercutSTapDelay = 0;        // ticks after the jump until S is pressed (0 = at the same time as the jump)
    public boolean kbDisplace = false;       // flick the camera 180 degrees, turn back the next tick, hit a tick later
    public int kbDisplaceChance = 25;
    public int kbAngle = 180;                // how far the camera flicks (180 = straight behind, 90 = to the side)
    public int kbFlickTicks = 1;             // ticks the camera stays flicked
    public int kbHitDelay = 0;               // 0 = hit on the turn-back tick (this is what displaces the knockback), 1+ = later

    // Reach
    public boolean reachSpacing = true;      // wait just outside reach, step in and hit at the edge of reach
    public double reach = 3.0;               // attack reach (eye to hitbox)
    public double reachMargin = 0.03;        // swing this far inside max reach; widens automatically after ghost hits
    public int reactionJitter = 0;           // Smart only: 0-N random ticks of hesitation (0 = react on the first tick)

    // Hit registration (ghost-hit guards)
    public boolean requireCrosshair = true;  // only swing when the crosshair ray really hits the opponent's hitbox
    public boolean hurtGuard = true;         // never swing into the opponent's hurt-immunity frames (damage is ignored)
    public boolean blockGuard = true;        // never swing a sword into a raised shield (use the axe instead)
    public boolean attributeSwap = true;     // Axe/Mace: charge with the sword, swap to the heavy weapon on the hit tick

    // Retreat
    public double retreatHealth = 6.0;
    public int retreatSeconds = 5;           // run for this long, then turn back and fight (many servers have no healing)
    public int retreatCooldownSeconds = 8;

    // Modern PvP
    public TotemMethod totemMethod = TotemMethod.AUTO;
    public boolean autoTotem = true;
    public int totemHealth = 10;
    public boolean autoEat = true;           // golden apple when low and the opponent is not close
    public int eatHealth = 8;
    public boolean healPots = true;          // splash instant health at your feet when low
    public int potHealth = 10;
    public boolean useWindCharge = true;     // Mace kit: wind charge boost before the smash attack
    public int windCooldownTicks = 40;       // wait this long before the next wind charge
    public boolean maceChain = true;         // keep the mace chain going after a landed smash (short cooldown)
    public boolean stunSlam = true;          // opponent blocking in the air: axe their shield first, then slam
    public int stunSlamDelay = 1;            // ticks between the axe hit and the slam
    public boolean spearLunge = true;        // spear jab with Lunge to close the distance
    public double lungeDist = 4.0;           // lunge when the opponent is at least this far away
    public InputMode lungeInput = InputMode.INPUT;   // real click, or packets
    public int lungeCooldownTicks = 15;
    public boolean lungeHoldWind = true;     // hold a wind charge before every lunge (its fast attack bar makes the jab count), swap back to it after
    public double lungeTravel = 3.0;         // how far one lunge carries you. The guard never lunges if it would end closer than 1 block.
    public boolean maceShield = true;        // Mace kit: raise the shield when the opponent is above you
    public double shieldAboveHeight = 2.0;   // "above" means at least this many blocks higher
    public boolean pearlCatch = false;       // look up, throw a pearl, follow with the wind charge
    public int pearlPitch = 85;              // how far up to look for the pearl (degrees)
    public int pearlDelay = 20;              // ticks between the pearl and the wind-charge jump
    public int pearlCooldownTicks = 100;
    public boolean shieldBreak = true;       // axe the opponent's shield after they block for a while
    public int shieldDelayMin = 20, shieldDelayMax = 100;
    public int shieldFailChance = 20;        // Smart only. Strict never fails.
    public boolean safeBlock = true;         // Safe strategy raises the offhand shield while waiting

    // Input and camera
    public InputMode inputMode = InputMode.INPUT;
    public JumpInput jumpInput = JumpInput.KEY;
    public int cameraSmoothness = 60;        // 0 = snappy, 100 = very smooth
    public int maxTurnDegPerTick = 18;
    public HotKey menuKey = HotKey.LEFT_BRACKET, toggleKey = HotKey.RIGHT_BRACKET, hudKey = HotKey.BACKSLASH;

    // Spacing fallbacks and targeting
    public double engageDist = 2.9, minDist = 1.8, targetRange = 12.0;

    // Look
    public AccentColor accent = AccentColor.INDIGO;
    public UiFont uiFont = UiFont.DEFAULT;
    public boolean hudVisible = true, hudCompact = false;
    public HudCorner hudCorner = HudCorner.TOP_LEFT;
    public int hudOpacity = 92;
    public boolean watermarkEnabled = true;
    public String watermarkText = "Evolution";
    public HudCorner watermarkCorner = HudCorner.TOP_RIGHT;
    public int watermarkX = 6, watermarkY = 6, watermarkOpacity = 90;
    public WatermarkStyle watermarkStyle = WatermarkStyle.BOX;
    public boolean watermarkVersion = true, watermarkFps = true, watermarkPing = false, watermarkTime = false, watermarkState = true;

    // ML
    public boolean recordDemos = false;      // write (observation, action) rows while a rule mode fights

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
        ModConfig d = new ModConfig();           // defaults, used to repair values from older versions
        // Old saves can hold removed enum values (Kit POT, SMP...) which Gson reads as null.
        if (instance.mode == null) instance.mode = d.mode;
        if (instance.strategy == null) instance.strategy = d.strategy;
        if (instance.kit == null) instance.kit = d.kit;
        if (instance.jumpResetMode == null) instance.jumpResetMode = d.jumpResetMode;
        if (instance.totemMethod == null) instance.totemMethod = d.totemMethod;
        if (instance.inputMode == null) instance.inputMode = d.inputMode;
        if (instance.jumpInput == null) instance.jumpInput = d.jumpInput;
        if (instance.menuKey == null) instance.menuKey = d.menuKey;
        if (instance.toggleKey == null) instance.toggleKey = d.toggleKey;
        if (instance.hudKey == null) instance.hudKey = d.hudKey;
        if (instance.accent == null) instance.accent = d.accent;
        if (instance.hudCorner == null) instance.hudCorner = d.hudCorner;
        if (instance.watermarkCorner == null) instance.watermarkCorner = d.watermarkCorner;
        if (instance.watermarkStyle == null) instance.watermarkStyle = d.watermarkStyle;
        if (instance.watermarkText == null) instance.watermarkText = d.watermarkText;
        if (instance.allowedServers == null) instance.allowedServers = new ArrayList<>();
        if (instance.lungeInput == null) instance.lungeInput = d.lungeInput;
        if (instance.uiFont == null) instance.uiFont = d.uiFont;
        if (instance.configVersion < 9) {        // v0.9: shorter lunge cooldown (the lunge now chains)
            if (instance.lungeCooldownTicks == 30) instance.lungeCooldownTicks = 15;
            instance.configVersion = 9;
        }
        if (instance.configVersion < 8) {        // v0.8: the KB displacement hit now happens on the turn-back tick
            instance.kbHitDelay = 0;
            instance.configVersion = 8;
        }
        if (instance.configVersion < 7) {        // v0.7: new defaults for spacing and look
            instance.reachMargin = 0.03;
            instance.accent = AccentColor.INDIGO;
            instance.sTapChance = Math.min(instance.sTapChance, 60);
            instance.configVersion = 7;
        }
        instance.enabled = false;
    }

    public static void save() {
        try { Files.writeString(FILE, GSON.toJson(get())); } catch (IOException ignored) { }
    }
}
