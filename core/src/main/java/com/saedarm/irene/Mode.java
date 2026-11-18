package com.saedarm.irene;

import com.saedarm.irene.Battle.*;

/** A game mode plugs into Battle through these hooks. Story, FreeRoam and Siege extend it. */
public abstract class Mode {
    protected Battle b;
    void attach(Battle b) { this.b = b; }
    public abstract String title();
    /** Build the starting state (units, helicopters, vehicles, objective). */
    public abstract void setup();
    /** Runs every tick before the result check. */
    public void tick() {}
    /** E/F pressed or held. Return true if the mode used it (wall buys, crates, radio parts...). */
    public boolean use(Unit p, boolean pressed) { return false; }
    /** What the HUD says near something interactive, or null. */
    public String prompt(Unit p) { return null; }
    public void onKill(Unit victim, Unit src, boolean head, boolean melee) {}
    public void onHit(Unit victim, Unit src, float dmg) {}
    /** Extra damage multiplier for the player's shots (pack-a-punch, insta-kill). */
    public float playerDamageMul(int weapon, boolean papd) { return papd ? 2f : 1f; }
    /** Player hp hit 0. Return true if the mode handled it (respawn, downed). Otherwise the run fails. */
    public boolean playerDied() { return false; }
    public boolean pickupAllowed() { return false; }
    public void onBoard(Heli h) {}
    public void onHeliDown(Heli h, Unit src) {}
    /** Extra HUD line (round, points...). */
    public String hud() { return null; }
    /** Whether the game should treat this as night right now (Story chapters can default to night). */
    public boolean defaultNight() { return false; }
    public boolean isSiege() { return false; }
    /** Player shot landed at a point (Easter eggs). */
    public void onShot(float x, float y, float z) {}
    /** Extra minimap markers {x, z, kind}. kind 0 objective, 1 activity, 2 shop. */
    public java.util.List<float[]> markers() { return java.util.Collections.emptyList(); }
    /** Checkpoint stage reached in this run (Story), -1 otherwise. */
    public int checkpoint() { return -1; }
}
