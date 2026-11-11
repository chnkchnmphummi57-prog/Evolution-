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

    // Movement toggles
    public boolean jump = true, crit = true, jumpReset = true, strafe = true, retreatLow = true, wTap = true, sTap = true;
    public boolean strictEnforcement = true; // mask disabled actions at the act layer (ML/Smart soft penalty comes later)

    // Strategy and keys
    public Strategy strategy = Strategy.NORMAL;
    public HotKey menuKey = HotKey.LEFT_BRACKET, toggleKey = HotKey.RIGHT_BRACKET, hudKey = HotKey.BACKSLASH;

    // Kit and crit timing
    public Kit kit = Kit.SWORD;
    public int critLaunchPercent = 70;       // start the jump at this attack charge; the hit still needs ~92% (the jump takes ~7 ticks)
    public int critResponsePriority = 75;    // Smart: % chance the crit-spam response picks crit spam over comboing. Strict: always crit first.
    public JumpResetMode jumpResetMode = JumpResetMode.REACT;

    // Sword techniques
    public boolean shiftTap = true;   public int shiftTapTicks = 1, shiftTapChance = 20;     // crouch reset, forward stays held
    public boolean blockHit = false;  public int blockHitTicks = 2, blockHitChance = 20;     // shield reset, needs an offhand shield
    public boolean cornerHit = true;                                                         // aim at the hitbox edge near max reach
    public StrafeStyle strafeStyle = StrafeStyle.ALTERNATE;
    public int circleTicks = 30, jitterTicks = 2;
    public boolean delayedHit = false;                                                       // back off a little and hit at max reach
    public boolean sprintCut = true;  public int sprintCutChance = 15;                       // hit at the top of a jump, sprint intact
    public boolean hitSelect = true;                                                         // let them swing first, counter at once
    public boolean wallPin = true;                                                           // opponent against a wall: no taps, stay on them
    public boolean uppercut = true;   public int uppercutChance = 10;                        // hit while still rising
    public boolean pCrit = true;                                                             // hit them the moment they jump
    public boolean critAutoLaunch = false;                                                   // time the jump so the falling hit is charged (double crits)
    public boolean delayedCrit = false; public int delayedCritChance = 25;                   // crit just before touching the ground
    public boolean sweepDisrupt = true;                                                      // non-sprint hits when others are near the target
    public boolean crosshairShake = false;                                                   // flick the camera away and back after a hit
    public boolean turnBack = false;                                                         // 180, run a block, snap back

    // Hit registration (ghost-hit guards)
    public boolean requireCrosshair = true;  // only swing when the crosshair ray really hits the opponent's hitbox
    public boolean hurtGuard = true;         // never swing into the opponent's hurt-immunity frames (damage is ignored)
    public boolean blockGuard = true;        // never swing a sword into a raised shield (use the axe instead)
    public double reachMargin = 0.15;        // swing this far inside max reach; widens automatically after ghost hits
    public boolean attributeSwap = true;     // Axe/Mace: charge with the sword, swap to the heavy weapon on the hit tick

    // Cobweb
    public boolean autoWeb = true;           // place a cobweb on the opponent's feet (needs cobwebs in the hotbar)
    public boolean webSpamCrit = true;       // opponent stuck in a web -> crit every hit

    public int retreatSeconds = 5;           // Retreat When Low: run for this long, then turn back and fight (many servers have no healing)
    public int retreatCooldownSeconds = 8;   // no new retreat for this long afterwards
    public int uppercutSTapDelay = 5;        // uppercut = jump + hit on the same tick, S-tap this many ticks later
    public int turnBackChance = 15;          // % per close-range moment that a turn-back is tried

    // ML
    public boolean recordDemos = false;      // write (observation, action) rows while a rule mode fights

    // Modern PvP
    public TotemMethod totemMethod = TotemMethod.AUTO;
    public boolean healPots = true;          // Pot / NethPot kits: splash instant health at your feet when low
    public int potHealth = 10;
    public boolean useWindCharge = true;     // Mace kit: wind charge boost before the smash attack
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
    public int sTapChance = 30;              // % of hits followed by an S-tap (when in range)
    public double sTapMaxDist = 2.5;         // only S-tap when the opponent is within this distance

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
        if (instance.configVersion < 5) {        // v0.5: the old S-tap defaults (100%, 3 blocks) starved the W-tap
            instance.sTapChance = 30;
            instance.sTapMaxDist = 2.5;
            instance.configVersion = 5;
        }
        if (instance.configVersion < 6) {        // v0.6: new fields take their defaults
            instance.configVersion = 6;
        }
        instance.enabled = false;
    }

    public static void save() {
        try { Files.writeString(FILE, GSON.toJson(get())); } catch (IOException ignored) { }
    }
}
