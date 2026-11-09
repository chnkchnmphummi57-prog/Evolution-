package dev.pvpai.act;

import dev.pvpai.config.InputMode;
import dev.pvpai.config.JumpInput;
import dev.pvpai.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class ActionApplier {
    private ActionApplier() { }

    // Packet mode keeps its own swing timer (sword: ~12.5 ticks) because the client cooldown is not reset by raw packets.
    private static int lastAttackAge = -1000;

    public static float packetCooldown(ClientPlayerEntity p) {
        return Math.max(0f, Math.min(1f, (p.age - lastAttackAge) / 12.5f));
    }

    public static void mask(Action a, ModConfig cfg) {
        if (!cfg.strictEnforcement) return;
        if (!cfg.jump) a.jump = false;
        if (!cfg.strafe) { a.left = false; a.right = false; }
    }

    public static void apply(MinecraftClient mc, Action a, Entity target, ModConfig cfg) {
        ClientPlayerEntity p = mc.player;
        var o = mc.options;

        // Vanilla gives NO critical hit while sprinting, so crit setup and taps stop the sprint outright.
        // This is a state change, not just a released key: holding W keeps a started sprint alive.
        if (a.stopSprint && p.isSprinting()) p.setSprinting(false);

        o.forwardKey.setPressed(a.forward);
        o.backKey.setPressed(a.back);
        o.leftKey.setPressed(a.left);
        o.rightKey.setPressed(a.right);

        // Sprint is driven directly, never through the sprint key, so hold-sprint and toggle-sprint both work.
        // Stopping sprint = releasing W (above) plus stopSprint; starting it = W held plus this call.
        if (a.sprint && a.forward && !p.isSprinting() && canSprint(p)) p.setSprinting(true);

        if (cfg.jumpInput == JumpInput.DIRECT) {
            o.jumpKey.setPressed(false);
            if (a.jump) directJump(p);
        } else {
            o.jumpKey.setPressed(a.jump);       // the space bar
        }

        o.useKey.setPressed(a.use);   // shield block / eating

        if (a.selectSlot >= 0 && a.selectSlot < 9 && p.getInventory().getSelectedSlot() != a.selectSlot) {
            p.getInventory().setSelectedSlot(a.selectSlot);
        }
        if (a.swapTotemFrom >= 0 && mc.interactionManager != null) {
            int slotId = a.swapTotemFrom < 9 ? 36 + a.swapTotemFrom : a.swapTotemFrom;   // player screen handler numbering
            mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, slotId, 40, SlotActionType.SWAP, p);   // 40 = offhand
        }

        if (a.aim) CameraSmoother.setGoal(a.goalYaw, a.goalPitch);
        if (a.attack) attack(mc, p, target, cfg);
    }

    /**
     * Minecraft has no standalone "jump packet": the server infers a jump from the movement packets that follow.
     * So the direct jump applies vanilla jump physics here, and the normal movement packets report it.
     */
    private static boolean canSprint(ClientPlayerEntity p) {
        return (p.getHungerManager().getFoodLevel() > 6 || p.getAbilities().allowFlying) && !p.isUsingItem();
    }

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
            nh.sendPacket(PlayerInteractEntityC2SPacket.attack(target, p.isSneaking()));
            p.swingHand(Hand.MAIN_HAND);        // client animation (also sends the swing packet)
            lastAttackAge = p.age;
            if ((cfg.wTap || cfg.sTap) && p.isSprinting()) {   // packet sprint reset: next hit is a sprint-knockback hit again
                nh.sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                nh.sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_SPRINTING));
            }
        } else if (mc.interactionManager != null) {
            mc.interactionManager.attackEntity(p, target);
            p.swingHand(Hand.MAIN_HAND);
        }
    }
}
