package dev.pvpai.observe;

import java.util.ArrayDeque;

/** Watches hurt-time and ground transitions to track combo length and detect opponent crit spam. */
public final class FightTracker {
    public int combo;
    public boolean oppSwingEdge, tookHitNow;
    // Hit-registration watchdog: an attack with no hurt animation on the opponent within 5 ticks counts as a ghost hit.
    public int attempts, landed, ghosts;
    public double reachPenalty;                // extra reach margin, grows after ghost hits and shrinks after landed hits
    private final ArrayDeque<Integer> pendingAttacks = new ArrayDeque<>();
    private int tick, lastHitTick = -999, prevHurt, prevTHurt;
    private boolean prevTGround = true, prevTSwing, primed;
    private final ArrayDeque<Integer> critsTaken = new ArrayDeque<>();
    private final ArrayDeque<Integer> oppJumps = new ArrayDeque<>();

    public void reset() {
        combo = 0; lastHitTick = -999; primed = false; oppSwingEdge = false; tookHitNow = false;
        critsTaken.clear(); oppJumps.clear(); pendingAttacks.clear();
        attempts = 0; landed = 0; ghosts = 0; reachPenalty = 0;
    }

    /** Call once for every attack the AI sends. */
    public void onAttack() { attempts++; pendingAttacks.add(tick); }

    public void resetSpam() { critsTaken.clear(); oppJumps.clear(); }

    public void update(Ctx c) {
        tick++;
        if (!primed) { prevHurt = c.hurtTime; prevTHurt = c.tHurtTime; prevTGround = c.tOnGround; primed = true; }

        oppSwingEdge = c.tSwinging && !prevTSwing;
        tookHitNow = c.hurtTime > prevHurt;
        if (c.tHurtTime > prevTHurt) {                                       // our hit landed
            combo++; lastHitTick = tick; landed++;
            pendingAttacks.clear();
            reachPenalty = Math.max(0, reachPenalty - 0.03);
        }
        while (!pendingAttacks.isEmpty() && tick - pendingAttacks.peekFirst() > 5) {   // no damage animation: ghost hit
            pendingAttacks.pollFirst(); ghosts++;
            reachPenalty = Math.min(0.6, reachPenalty + 0.08);
        }
        if (tick - lastHitTick > 25) combo = 0;                              // combo timed out
        if (c.hurtTime > prevHurt) {                                         // we got hit
            combo = 0;
            if (!c.tOnGround && c.tVelY < 0) critsTaken.add(tick);          // they hit us while falling = crit
        }
        if (prevTGround && !c.tOnGround && c.tVelY > 0 && c.dist < 5) oppJumps.add(tick);

        prevHurt = c.hurtTime; prevTHurt = c.tHurtTime; prevTGround = c.tOnGround; prevTSwing = c.tSwinging;
        purge(critsTaken, 80); purge(oppJumps, 60);
    }

    /** Opponent is crit-spamming: 2 crits taken in 4s, or 3 jumps next to us in 3s. */
    public boolean critSpam() { return critsTaken.size() >= 2 || oppJumps.size() >= 3; }

    private void purge(ArrayDeque<Integer> q, int window) {
        while (!q.isEmpty() && tick - q.peekFirst() > window) q.pollFirst();
    }
}
