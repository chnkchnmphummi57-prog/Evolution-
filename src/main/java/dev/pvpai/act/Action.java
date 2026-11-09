package dev.pvpai.act;

public final class Action {
    public boolean forward, back, left, right, jump, sprint, attack;
    public boolean aim, stopSprint, use;
    public int selectSlot = -1, swapTotemFrom = -1;
    public float goalYaw, goalPitch;   // camera goal; CameraSmoother glides there every frame
}
