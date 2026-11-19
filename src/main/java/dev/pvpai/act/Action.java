package dev.pvpai.act;

public final class Action {
    public boolean forward, back, left, right, jump, sprint, attack;
    public boolean aim, stopSprint, use, fastAim, snapAim, swapHands, sneak;   // snapAim: turn to the goal instantly (a real flick)
    public boolean lunge;                       // spear jab swap (see ActionApplier.lunge)
    public int selectSlot = -1, swapTotemFrom = -1, lungeSlot = -1, restoreSlot = -1;
    public float goalYaw, goalPitch;   // camera goal; CameraSmoother glides there every frame
}
