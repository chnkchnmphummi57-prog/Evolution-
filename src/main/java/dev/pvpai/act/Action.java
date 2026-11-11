package dev.pvpai.act;

public final class Action {
    public boolean forward, back, left, right, jump, sprint, attack;
    public boolean aim, stopSprint, use, fastAim, swapHands, sneak;
    public int selectSlot = -1, swapTotemFrom = -1;
    public net.minecraft.util.math.BlockPos webGround;   // place a cobweb on top of this block (opponent's feet)
    public float goalYaw, goalPitch;   // camera goal; CameraSmoother glides there every frame
}
