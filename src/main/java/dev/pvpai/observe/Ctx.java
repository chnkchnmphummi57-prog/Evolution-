package dev.pvpai.observe;

import dev.pvpai.config.ModConfig;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import dev.pvpai.config.Strategy;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** One-tick snapshot of the fight. Rules read fields directly; ML reads toFloats(). */
public final class Ctx {
    public float yaw, pitch, yawToTarget, pitchToTarget;
    public double reachDist, dist, dx, dy, dz, velX, velY, velZ, tVelX, tVelY, tVelZ;
    public float health, maxHealth, tHealth, tMaxHealth, cooldown;
    public int hurtTime, tHurtTime;
    public boolean onGround, sprinting, tOnGround;
    public boolean tBlocking, tSwinging, usingItem, offhandShield, offhandTotem, offhandEmpty, holdingAxe;
    public int selectedSlot, swordSlot = -1, axeSlot = -1, foodSlot = -1, totemSlot = -1, maceSlot = -1, windSlot = -1, potSlot = -1;
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
        c.reachDist = eye.distanceTo(new Vec3d(MathHelper.clamp(eye.x, bb.minX, bb.maxX),
                MathHelper.clamp(eye.y, bb.minY, bb.maxY), MathHelper.clamp(eye.z, bb.minZ, bb.maxZ)));
        c.yaw = p.getYaw(); c.pitch = p.getPitch();
        c.yawToTarget = (float) (MathHelper.atan2(c.dz, c.dx) * 180.0 / Math.PI) - 90.0f;
        c.pitchToTarget = (float) (-(MathHelper.atan2(c.dy, h) * 180.0 / Math.PI));
        Vec3d v = p.getVelocity(), tv = t.getVelocity();
        c.velX = v.x; c.velY = v.y; c.velZ = v.z;
        c.tVelX = tv.x; c.tVelY = tv.y; c.tVelZ = tv.z;
        c.health = p.getHealth(); c.maxHealth = p.getMaxHealth();
        c.tHealth = t.getHealth(); c.tMaxHealth = t.getMaxHealth();
        c.cooldown = p.getAttackCooldownProgress(0.5f);
        c.hurtTime = p.hurtTime; c.tHurtTime = t.hurtTime;
        c.onGround = p.isOnGround(); c.tOnGround = t.isOnGround();
        c.sprinting = p.isSprinting();
        c.fallDistance = p.fallDistance;
        c.tYaw = MathHelper.wrapDegrees(t.getYaw() - c.yawToTarget);   // opponent facing relative to the line between us
        c.tPitch = t.getPitch();

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

    /** ML observation vector (single frame). History stacking happens in the bridge. */
    public float[] toFloats(ModConfig cfg, int heldItemId) {
        float[] o = new float[40];
        int i = 0;
        o[i++] = (float) c(dx / 6); o[i++] = (float) c(dy / 6); o[i++] = (float) c(dz / 6); o[i++] = (float) c(dist / 6);
        o[i++] = (float) c(velX); o[i++] = (float) c(velY); o[i++] = (float) c(velZ);
        o[i++] = (float) c(tVelX); o[i++] = (float) c(tVelY); o[i++] = (float) c(tVelZ);
        o[i++] = health / maxHealth; o[i++] = tHealth / tMaxHealth;
        o[i++] = cooldown;
        o[i++] = hurtTime / 10f; o[i++] = tHurtTime / 10f;
        o[i++] = MathHelper.wrapDegrees(yawToTarget - yaw) / 180f;
        o[i++] = (pitchToTarget - pitch) / 90f;
        o[i++] = yaw / 180f; o[i++] = pitch / 90f;
        o[i++] = tYaw / 180f; o[i++] = tPitch / 90f;
        o[i++] = onGround ? 1 : 0; o[i++] = tOnGround ? 1 : 0; o[i++] = sprinting ? 1 : 0;
        o[i++] = (float) c(fallDistance / 4);
        o[i++] = heldItemId; // replaced by one-hot in the bridge once item classes are fixed
        // toggle state, so one policy covers every toggle combination
        o[i++] = cfg.jump ? 1 : 0; o[i++] = cfg.crit ? 1 : 0; o[i++] = cfg.jumpReset ? 1 : 0;
        o[i++] = cfg.strafe ? 1 : 0; o[i++] = cfg.retreatLow ? 1 : 0; o[i++] = cfg.wTap ? 1 : 0; o[i++] = cfg.sTap ? 1 : 0;
        // strategy one-hot, so one model can serve Normal / Safe / Crit spam
        o[i++] = cfg.strategy == Strategy.NORMAL ? 1 : 0; o[i++] = cfg.strategy == Strategy.SAFE ? 1 : 0; o[i++] = cfg.strategy == Strategy.CRIT_SPAM ? 1 : 0;
        return o; // remaining slots reserved
    }

    private static double c(double v) { return Math.max(-1, Math.min(1, v)); }
}
