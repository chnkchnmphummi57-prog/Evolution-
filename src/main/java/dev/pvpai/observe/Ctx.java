package dev.pvpai.observe;

import dev.pvpai.config.ModConfig;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import dev.pvpai.config.Kit;
import dev.pvpai.config.Strategy;
import dev.pvpai.act.CameraSmoother;
import net.minecraft.block.Blocks;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** One-tick snapshot of the fight. Rules read fields directly; ML reads toFloats(). */
public final class Ctx {
    public static final int OBS_SIZE = 64;     // fixed ML observation length (extra slots are zero)

    public float yaw, pitch, yawToTarget, pitchToTarget;
    public double reachDist, dist, dx, dy, dz, velX, velY, velZ, tVelX, tVelY, tVelZ;
    public float health, maxHealth, tHealth, tMaxHealth, cooldown;
    public int hurtTime, tHurtTime;
    public boolean onGround, sprinting, tOnGround;
    public float edgeYaw, edgePitch;
    public boolean hasEdge, tWallBehind, crosshairOnTarget, tInWeb, inWeb;
    public float chargePerTick = 0.08f;        // attack-charge progress per tick for the held weapon (sword 1.6/s = 0.08)
    public double groundGap = 99;              // blocks between our feet and the solid block below (99 = none close)
    public BlockPos tGround;                   // block under the opponent (for placing a cobweb on their feet)
    public int nearbyOthers;
    public boolean tBlocking, tSwinging, usingItem, offhandShield, offhandTotem, offhandEmpty, holdingAxe;
    public int selectedSlot, swordSlot = -1, axeSlot = -1, foodSlot = -1, totemSlot = -1, maceSlot = -1, windSlot = -1, potSlot = -1, webSlot = -1;
    public double fallDistance;
    public float tYaw, tPitch;

    public static Ctx build(ClientPlayerEntity p, LivingEntity t) {
        Ctx c = new Ctx();
        Vec3d eye = p.getEyePos();
        Vec3d aim = t.getBoundingBox().getCenter();
        c.dx = aim.x - eye.x; c.dy = aim.y - eye.y; c.dz = aim.z - eye.z;
        double h = Math.sqrt(c.dx * c.dx + c.dz * c.dz);
        c.dist = p.distanceTo(t);
        Box bb = t.getBoundingBox();
        Vec3d np = new Vec3d(MathHelper.clamp(eye.x, bb.minX, bb.maxX),
                MathHelper.clamp(eye.y, bb.minY, bb.maxY), MathHelper.clamp(eye.z, bb.minZ, bb.maxZ));
        c.reachDist = eye.distanceTo(np);
        // Aim point on the hitbox edge nearest to us (corner hitting), nudged slightly inside so the ray still hits.
        Vec3d toCtr = bb.getCenter().subtract(np);
        if (c.reachDist > 0.05 && toCtr.length() > 0.05) {
            Vec3d ep = np.add(toCtr.multiply(0.04 / toCtr.length()));
            double ex = ep.x - eye.x, ey = ep.y - eye.y, ez = ep.z - eye.z;
            c.edgeYaw = (float) (MathHelper.atan2(ez, ex) * 180.0 / Math.PI) - 90.0f;
            c.edgePitch = (float) (-(MathHelper.atan2(ey, Math.sqrt(ex * ex + ez * ez)) * 180.0 / Math.PI));
            c.hasEdge = true;
        }
        // Wall behind the opponent (seen from us)? Used for wall-pinning.
        MinecraftClient mc = MinecraftClient.getInstance();
        double hx = t.getX() - p.getX(), hz = t.getZ() - p.getZ(), hl = Math.sqrt(hx * hx + hz * hz);
        if (mc.world != null && hl > 1.0e-4) {
            hx /= hl; hz /= hl;
            for (double off : new double[] {0.9, 1.3}) {
                for (double yy : new double[] {0.4, 1.4}) {
                    BlockPos bp = BlockPos.ofFloored(t.getX() + hx * off, t.getY() + yy, t.getZ() + hz * off);
                    if (mc.world.getBlockState(bp).isSolidBlock(mc.world, bp)) c.tWallBehind = true;
                }
            }
            for (PlayerEntity o : mc.world.getPlayers()) {
                if (o != p && o != t && o.isAlive() && !o.isSpectator() && o.distanceTo(t) < 3.0) c.nearbyOthers++;
            }
        }
        c.yaw = p.getYaw(); c.pitch = p.getPitch();
        c.yawToTarget = (float) (MathHelper.atan2(c.dz, c.dx) * 180.0 / Math.PI) - 90.0f;
        c.pitchToTarget = (float) (-(MathHelper.atan2(c.dy, h) * 180.0 / Math.PI));
        Vec3d v = p.getVelocity(), tv = t.getVelocity();
        c.velX = v.x; c.velY = v.y; c.velZ = v.z;
        c.tVelX = tv.x; c.tVelY = tv.y; c.tVelZ = tv.z;
        c.health = p.getHealth(); c.maxHealth = p.getMaxHealth();
        c.tHealth = t.getHealth(); c.tMaxHealth = t.getMaxHealth();
        c.cooldown = p.getAttackCooldownProgress(0.5f);
        c.chargePerTick = (float) (p.getAttributeValue(EntityAttributes.ATTACK_SPEED) / 20.0);
        c.hurtTime = p.hurtTime; c.tHurtTime = t.hurtTime;
        c.onGround = p.isOnGround(); c.tOnGround = t.isOnGround();
        c.sprinting = p.isSprinting();
        c.fallDistance = p.fallDistance;
        c.tYaw = MathHelper.wrapDegrees(t.getYaw() - c.yawToTarget);   // opponent facing relative to the line between us
        c.tPitch = t.getPitch();

        if (!c.hasEdge) { c.edgeYaw = c.yawToTarget; c.edgePitch = c.pitchToTarget; }

        // Does the crosshair ray really pass through the opponent's hitbox? A swing that misses it is a ghost hit.
        // A swing counts only if (1) vanilla's own crosshair pick says the click would hit this player, and (2) the rotation the
        // SERVER last received also points at the hitbox. The camera is eased every frame, so it can be ahead of what the server has.
        Box tight = bb.expand(-0.02);
        boolean vanilla = mc.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() == t;
        boolean serverLook = true;
        if (CameraSmoother.sentKnown()) {
            Vec3d sdir = Vec3d.fromPolar(CameraSmoother.sentPitch, CameraSmoother.sentYaw);
            serverLook = tight.raycast(eye, eye.add(sdir.multiply(6.0))).isPresent();
        }
        c.crosshairOnTarget = vanilla && serverLook;

        if (mc.world != null) {
            c.tGround = t.getBlockPos().down();
            c.tInWeb = mc.world.getBlockState(t.getBlockPos()).isOf(Blocks.COBWEB)
                    || mc.world.getBlockState(t.getBlockPos().up()).isOf(Blocks.COBWEB);
            c.inWeb = mc.world.getBlockState(p.getBlockPos()).isOf(Blocks.COBWEB)
                    || mc.world.getBlockState(p.getBlockPos().up()).isOf(Blocks.COBWEB);
            BlockPos feet = p.getBlockPos();
            if (solid(mc, feet.down())) c.groundGap = p.getY() - feet.getY();
            else if (solid(mc, feet.down(2))) c.groundGap = p.getY() - feet.getY() + 1.0;
        }
        c.tBlocking = t.isBlocking();
        c.tSwinging = t.handSwinging;
        scan(c, p);
        return c;
    }


    /** Own state only (health, inventory, offhand). Used on ticks with no opponent so totems and eating still work. */
    public static Ctx buildSelf(ClientPlayerEntity p) {
        Ctx c = new Ctx();
        c.yaw = p.getYaw(); c.pitch = p.getPitch();
        c.health = p.getHealth(); c.maxHealth = p.getMaxHealth();
        c.tHealth = 20f; c.tMaxHealth = 20f;
        c.dist = 99; c.reachDist = 99;
        c.onGround = p.isOnGround(); c.sprinting = p.isSprinting(); c.hurtTime = p.hurtTime;
        scan(c, p);
        return c;
    }

    private static boolean solid(MinecraftClient mc, BlockPos bp) {
        return !mc.world.getBlockState(bp).getCollisionShape(mc.world, bp).isEmpty();
    }

    private static void scan(Ctx c, ClientPlayerEntity p) {
        c.usingItem = p.isUsingItem();
        ItemStack off = p.getOffHandStack();
        c.offhandShield = off.isOf(Items.SHIELD);
        c.offhandTotem = off.isOf(Items.TOTEM_OF_UNDYING);
        c.offhandEmpty = off.isEmpty();
        PlayerInventory inv = p.getInventory();
        c.selectedSlot = inv.getSelectedSlot();
        c.holdingAxe = p.getMainHandStack().isIn(ItemTags.AXES);
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            if (c.totemSlot < 0 && s.isOf(Items.TOTEM_OF_UNDYING)) c.totemSlot = i;
            if (i < 9) {
                if (c.swordSlot < 0 && s.isIn(ItemTags.SWORDS)) c.swordSlot = i;
                if (c.axeSlot < 0 && s.isIn(ItemTags.AXES)) c.axeSlot = i;
                if (c.maceSlot < 0 && s.isOf(Items.MACE)) c.maceSlot = i;
                if (c.windSlot < 0 && s.isOf(Items.WIND_CHARGE)) c.windSlot = i;
                if (c.webSlot < 0 && s.isOf(Items.COBWEB)) c.webSlot = i;
                if (c.foodSlot < 0 && (s.isOf(Items.GOLDEN_APPLE) || s.isOf(Items.ENCHANTED_GOLDEN_APPLE))) c.foodSlot = i;
                if (c.potSlot < 0 && s.isOf(Items.SPLASH_POTION) && isHealing(s)) c.potSlot = i;
            }
        }
        if (p.getMainHandStack().isIn(ItemTags.SWORDS)) c.swordSlot = c.selectedSlot;
    }

    private static boolean isHealing(ItemStack s) {
        PotionContentsComponent pc = s.get(DataComponentTypes.POTION_CONTENTS);
        if (pc == null) return false;
        for (StatusEffectInstance e : pc.getEffects()) {
            if (e.getEffectType().equals(StatusEffects.INSTANT_HEALTH)) return true;
        }
        return false;
    }

    /** ML observation vector (single frame, OBS_SIZE floats). Layout is part of the model format: do not reorder. */
    public float[] toFloats(ModConfig cfg) {
        float[] o = new float[OBS_SIZE];
        int i = 0;
        o[i++] = (float) c(dx / 6); o[i++] = (float) c(dy / 6); o[i++] = (float) c(dz / 6); o[i++] = (float) c(dist / 6);    // 4
        o[i++] = (float) c(velX); o[i++] = (float) c(velY); o[i++] = (float) c(velZ);                                          // 7
        o[i++] = (float) c(tVelX); o[i++] = (float) c(tVelY); o[i++] = (float) c(tVelZ);                                       // 10
        o[i++] = health / Math.max(1f, maxHealth); o[i++] = tHealth / Math.max(1f, tMaxHealth);                                // 12
        o[i++] = cooldown;                                                                                                     // 13
        o[i++] = hurtTime / 10f; o[i++] = tHurtTime / 10f;                                                                     // 15
        o[i++] = MathHelper.wrapDegrees(yawToTarget - yaw) / 180f; o[i++] = (pitchToTarget - pitch) / 90f;                     // 17
        o[i++] = yaw / 180f; o[i++] = pitch / 90f;                                                                             // 19
        o[i++] = tYaw / 180f; o[i++] = tPitch / 90f;                                                                           // 21
        o[i++] = onGround ? 1 : 0; o[i++] = tOnGround ? 1 : 0; o[i++] = sprinting ? 1 : 0;                                     // 24
        o[i++] = (float) c(fallDistance / 4);                                                                                  // 25
        o[i++] = (float) c(reachDist / 6);                                                                                     // 26
        o[i++] = crosshairOnTarget ? 1 : 0; o[i++] = tBlocking ? 1 : 0; o[i++] = tSwinging ? 1 : 0; o[i++] = usingItem ? 1 : 0; // 30
        o[i++] = offhandShield ? 1 : 0; o[i++] = offhandTotem ? 1 : 0;                                                         // 32
        o[i++] = tInWeb ? 1 : 0; o[i++] = tWallBehind ? 1 : 0; o[i++] = (float) c(groundGap / 4);                              // 35
        o[i++] = cfg.jump ? 1 : 0; o[i++] = cfg.crit ? 1 : 0; o[i++] = cfg.jumpReset ? 1 : 0; o[i++] = cfg.strafe ? 1 : 0;      // 39
        o[i++] = cfg.retreatLow ? 1 : 0; o[i++] = cfg.wTap ? 1 : 0; o[i++] = cfg.sTap ? 1 : 0;                                 // 42
        o[i++] = cfg.strategy == Strategy.NORMAL ? 1 : 0; o[i++] = cfg.strategy == Strategy.SAFE ? 1 : 0;
        o[i++] = cfg.strategy == Strategy.CRIT_SPAM ? 1 : 0;                                                                   // 45
        for (Kit k : Kit.values()) o[i++] = cfg.kit == k ? 1 : 0;                                                              // 52
        return o;                                                                                                              // 53..63 reserved
    }

    private static double c(double v) { return Math.max(-1, Math.min(1, v)); }
}
