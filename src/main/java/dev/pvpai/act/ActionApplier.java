package dev.pvpai.act;

import dev.pvpai.config.InputMode;
import dev.pvpai.config.JumpInput;
import dev.pvpai.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

/**
 * Turns an Action into game input.
 *
 * Mouse & keyboard mode sends REAL key events: the same two static calls the game's own keyboard and mouse handlers make for a
 * physical press (KeyBinding.setKeyPressed + KeyBinding.onKeyPressed). The attack is a real left-click press, so the game itself
 * runs its normal click logic (crosshair pick, cooldown, swing, packets) instead of us forcing an attack at a chosen moment.
 * Camera turning goes through changeLookDirection, the same call real mouse movement makes (see CameraSmoother).
 */
public final class ActionApplier {
    private ActionApplier() { }

    // Packet mode keeps its own swing timer because raw packets do not reset the client cooldown.
    private static int lastAttackAge = -1000;

    public static float packetCooldown(ClientPlayerEntity p) {
        double speed = p.getAttributeValue(EntityAttributes.ATTACK_SPEED);   // sword 1.6 -> 12.5 ticks, axe ~1.0 -> 20, mace 0.6 -> 33
        return Math.max(0f, Math.min(1f, (float) ((p.age - lastAttackAge) * speed / 20.0)));
    }

    // ---- real key events ----

    private static final Map<KeyBinding, Boolean> HELD = new HashMap<>();
    private static boolean attackHeld;

    private static InputUtil.Key keyOf(KeyBinding kb) {
        return InputUtil.fromTranslationKey(kb.getBoundKeyTranslationKey());
    }

    /** Press or release a key the way a keyboard/mouse event would. Only edges produce events. */
    private static void real(KeyBinding kb, boolean want) {
        if (HELD.getOrDefault(kb, false) == want) return;
        InputUtil.Key key = keyOf(kb);
        KeyBinding.setKeyPressed(key, want);
        if (want) KeyBinding.onKeyPressed(key);
        HELD.put(kb, want);
        if (kb.isPressed() != want) {          // Toggle-mode key (Toggle sneak/sprint): the release is ignored, tap again to flip it back
            KeyBinding.setKeyPressed(key, true);
            KeyBinding.onKeyPressed(key);
            KeyBinding.setKeyPressed(key, false);
        }
    }

    public static void setUse(MinecraftClient mc, boolean want) { real(mc.options.useKey, want); }

    /** Forget everything we pressed and let the physical keyboard state take over again. */
    public static void resetKeys() {
        HELD.clear();
        attackHeld = false;
        KeyBinding.updatePressedStates();
    }

    /** Runs after the player tick: releases the click and remembers the rotation that tick's movement packet carried. */
    public static void endTick(MinecraftClient mc) {
        if (attackHeld) {
            KeyBinding.setKeyPressed(keyOf(mc.options.attackKey), false);
            attackHeld = false;
        }
        if (mc.player != null) CameraSmoother.recordSent(mc.player);
    }

    public static void mask(Action a, ModConfig cfg) {
        if (!cfg.strictEnforcement) return;
        if (!cfg.jump) a.jump = false;
        if (!cfg.strafe) { a.left = false; a.right = false; }
    }

    /** Hotbar selection, totem swaps. Also used on ticks with no opponent. */
    public static void inventory(MinecraftClient mc, ClientPlayerEntity p, Action a) {
        if (a.selectSlot >= 0 && a.selectSlot < 9 && p.getInventory().getSelectedSlot() != a.selectSlot) {
            p.getInventory().setSelectedSlot(a.selectSlot);
        }
        if (a.swapTotemFrom >= 0 && mc.interactionManager != null) {
            int slotId = a.swapTotemFrom < 9 ? 36 + a.swapTotemFrom : a.swapTotemFrom;   // player screen handler numbering
            mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, slotId, 40, SlotActionType.SWAP, p);   // 40 = offhand
        }
        if (a.swapHands) {                                  // same packet the swap-hands key sends: selected slot <-> offhand
            ClientPlayNetworkHandler nh = mc.getNetworkHandler();
            if (nh != null) nh.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
        }
    }

    public static void apply(MinecraftClient mc, Action a, Entity target, ModConfig cfg) {
        ClientPlayerEntity p = mc.player;
        var o = mc.options;

        // Vanilla gives NO critical hit while sprinting: crit setup and taps end the sprint outright. Releasing W alone is not
        // enough when a sprint is already running with W held.
        if (a.stopSprint && p.isSprinting()) p.setSprinting(false);

        real(o.forwardKey, a.forward);
        real(o.backKey, a.back);
        real(o.leftKey, a.left);
        real(o.rightKey, a.right);
        real(o.sprintKey, a.sprint && a.forward && canSprint(p));   // real sprint key; works for Hold and Toggle sprint
        real(o.sneakKey, a.sneak);                                  // shift-tap

        if (cfg.jumpInput == JumpInput.DIRECT) {
            real(o.jumpKey, false);
            if (a.jump) directJump(p);
        } else {
            real(o.jumpKey, a.jump);                                // the space bar
        }

        real(o.useKey, a.use);                                      // shield block / eating / throwing

        inventory(mc, p, a);

        if (a.webGround != null && mc.interactionManager != null) { // cobweb on the opponent's feet
            Vec3d hp = new Vec3d(a.webGround.getX() + 0.5, a.webGround.getY() + 1.0, a.webGround.getZ() + 0.5);
            mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, new BlockHitResult(hp, Direction.UP, a.webGround, false));
            p.swingHand(Hand.MAIN_HAND);
        }

        if (a.aim) CameraSmoother.setGoal(a.goalYaw, a.goalPitch, a.fastAim);
        if (a.attack) attack(mc, p, target, cfg);
    }

    private static boolean canSprint(ClientPlayerEntity p) {
        return (p.getHungerManager().getFoodLevel() > 6 || p.getAbilities().allowFlying) && !p.isUsingItem();
    }

    /**
     * Minecraft has no standalone "jump packet": the server infers a jump from the movement packets that follow.
     * So the direct jump applies vanilla jump physics here, and the normal movement packets report it.
     */
    private static void directJump(ClientPlayerEntity p) {
        if (!p.isOnGround()) return;
        Vec3d v = p.getVelocity();
        double vx = v.x, vz = v.z;
        if (p.isSprinting()) {
            float yaw = p.getYaw() * 0.017453292f;
            vx -= MathHelper.sin(yaw) * 0.2;
            vz += MathHelper.cos(yaw) * 0.2;
        }
        p.setVelocity(vx, 0.42, vz);
    }

    private static void attack(MinecraftClient mc, ClientPlayerEntity p, Entity target, ModConfig cfg) {
        if (cfg.inputMode == InputMode.PACKET) {
            ClientPlayNetworkHandler nh = mc.getNetworkHandler();
            if (nh == null) return;
            nh.sendPacket(new UpdateSelectedSlotC2SPacket(p.getInventory().getSelectedSlot()));   // server must know the held item (axe swaps)
            nh.sendPacket(PlayerInteractEntityC2SPacket.attack(target, p.isSneaking()));
            p.swingHand(Hand.MAIN_HAND);        // client animation (also sends the swing packet)
            lastAttackAge = p.age;
            if ((cfg.wTap || cfg.sTap) && p.isSprinting()) {   // packet sprint reset: next hit is a sprint-knockback hit again
                nh.sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                nh.sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_SPRINTING));
            }
        } else {
            // A real left-click. The game's own click handler runs later in this same tick: it checks the crosshair target, applies
            // the cooldown rules, swings and sends the attack. If the crosshair were not on the target this would be a miss, so the
            // controller only asks for an attack when the crosshair really is on it.
            InputUtil.Key key = keyOf(mc.options.attackKey);
            KeyBinding.setKeyPressed(key, true);
            KeyBinding.onKeyPressed(key);
            attackHeld = true;
        }
    }
}
