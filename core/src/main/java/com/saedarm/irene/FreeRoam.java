package com.saedarm.irene;

import com.saedarm.irene.Battle.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Free roam: the whole city, a Humvee motor pool at the airfield and a Black Hawk on call (H).
 * Four activities are always open somewhere: clear an HVT house, blow an arms cache, break a
 * roadblock, reach a downed pilot. Gunfire raises the city's alert level, which brings patrols.
 * Dying respawns you at the airfield.
 */
public final class FreeRoam extends Mode {
    public static final int HVT = 0, CACHE = 1, ROADBLOCK = 2, PILOT = 3;
    static final String[] KIND = {"HVT house", "Arms cache", "Roadblock", "Downed pilot"};
    public static final class Activity { public int kind, house = -1; public float x, z; public Unit pilot; public float holdT; public boolean done; }
    public final ArrayList<Activity> acts = new ArrayList<>();
    public int score, deaths, completed; public float alert; private float respawnT = -1, patrolT, objT;
    private final float[] home = new float[2];

    @Override public String title() { return "Free Roam"; }
    @Override public boolean pickupAllowed() { return true; }

    @Override public void setup() {
        float[] af = b.city.marks.get("Airfield");
        home[0] = af[0] + 10; home[1] = af[1] - 12;
        b.placeSafe(b.player, home[0], home[1]);
        for (int i = 0; i < 3; i++) { Unit s = b.spawn(b.playerTeam, b.playerTeam == Battle.US ? i % 4 : 4 + i % 3, 0, 0, 0); b.placeSafe(s, home[0] + 2 + i * 1.5f, home[1] + 2); }
        if (b.playerTeam == Battle.US) {
            for (int i = 0; i < 3; i++) { int ci = b.walkCell(af[0] - 14 + i * 8, af[1] - 2); b.vehicle(Battle.V_HUMVEE, (ci % City.N) * City.CELL + 2, (ci / City.N) * City.CELL + 2, 0.4f, Battle.US); }
            Heli h = b.heli(Battle.BLACKHAWK, "Super 6-5", af[0], 45, af[1]); b.orbit(h, af[0] + 40, af[1] - 40, 70, 45);
        }
        // a few abandoned Humvees around town
        for (float[] p : new float[][]{{312, 440}, {250, 300}, {450, 220}}) { int ci = b.walkCell(p[0], p[1]); b.vehicle(Battle.V_HUMVEE, (ci % City.N) * City.CELL + 2, (ci / City.N) * City.CELL + 2, 1.0f, Battle.US); }
        for (int i = 0; i < 4; i++) newActivity();
        b.radio("Free roam. Four jobs are marked on your map. H calls the bird, E gets in a Humvee.");
    }

    private void newActivity() {
        for (int tries = 0; tries < 80; tries++) {
            int kind = b.rng.nextInt(4);
            Activity a = new Activity(); a.kind = kind;
            if (kind == HVT) {
                City.House h = b.city.houses.get(b.rng.nextInt(b.city.houses.size()));
                if (h.open() || far(h.doorX, h.doorZ)) continue;
                a.house = b.city.houses.indexOf(h); a.x = h.doorX; a.z = h.doorZ;
                b.garrison(h, 3 + b.difficulty); b.guard(h.doorX + h.nx * 8, h.doorZ + h.nz * 8, 8); b.guard(h.doorX + h.nx * 8, h.doorZ + h.nz * 8, 8);
            } else {
                int ci = b.walkCell(20 + b.rng.nextFloat() * 600, 20 + b.rng.nextFloat() * 600);
                a.x = (ci % City.N) * City.CELL + 2; a.z = (ci / City.N) * City.CELL + 2;
                if (far(a.x, a.z) || (kind == ROADBLOCK && !b.city.road[ci])) continue;
                if (kind == CACHE) { for (int i = 0; i < 4; i++) b.guard(a.x, a.z, 10); }
                else if (kind == ROADBLOCK) { b.city.tires.add(new float[]{a.x, a.z}); b.evt(Battle.E_TIRE, 0, a.x, a.z, null); for (int i = 0; i < 6; i++) b.guard(a.x, a.z, 14); }
                else { a.pilot = b.spawn(b.playerTeam, b.playerTeam == Battle.US ? 0 : 4, 0, 0, 0); b.place(a.pilot, a.x, a.z); a.pilot.perch = true; a.pilot.role = Ai.R_PERCH; a.pilot.hp = 40; }
            }
            acts.add(a); return;
        }
    }
    private boolean far(float x, float z) {
        if (b.city.inArena(x, z)) return true;
        float d = (float) Math.hypot(x - b.player.x, z - b.player.z);
        if (d < 120) return true;
        for (Activity o : acts) if (Math.hypot(o.x - x, o.z - z) < 80) return true;
        return false;
    }
    private int guardsNear(float x, float z, float r) { int n = 0; for (Unit u : b.units) if (u.alive && u.team == b.hostileTeam && u.guard && Math.hypot(u.x - x, u.z - z) < r) n++; return n; }

    @Override public void tick() {
        Unit p = b.player;
        alert = Math.max(0, alert - Battle.DT * 0.03f);
        if (b.time - p.lastShot < Battle.DT * 1.5f) alert = Math.min(5.99f, alert + 0.01f);
        if (respawnT >= 0) { respawnT -= Battle.DT; if (respawnT < 0) { b.respawnPlayer(home[0], home[1]); alert = Math.min(alert, 1); } return; }
        // activities
        for (Activity a : acts) {
            if (a.done) continue;
            boolean done = false;
            switch (a.kind) {
                case HVT: { City.House h = b.city.houses.get(a.house); int hi = a.house, n = 0; for (Unit u : b.units) if (u.alive && u.house == hi) n++; done = n == 0 && h.open(); break; }
                case CACHE: if (Math.hypot(p.x - a.x, p.z - a.z) < 2.5f && b.input.use) { a.holdT += Battle.DT; b.objProgress = a.holdT / 2; } if (a.holdT >= 2) { Proj boom = new Proj(); boom.x = a.x; boom.y = 0.6f; boom.z = a.z; boom.team = p.team; boom.owner = p.id; boom.rocket = true; b.explode(boom, 7, 150); done = true; } break;
                case ROADBLOCK: done = guardsNear(a.x, a.z, 26) == 0 && Math.hypot(p.x - a.x, p.z - a.z) < 40; break;
                case PILOT: if (!a.pilot.alive) { a.done = true; b.radio("We lost the pilot."); break; } if (Math.hypot(p.x - a.x, p.z - a.z) < 4) { a.pilot.perch = false; a.pilot.role = Ai.R_SQUAD; a.pilot.aiState = Ai.A_ADVANCE; done = true; } break;
            }
            if (done) { a.done = true; completed++; score += 500 + (int) alert * 100; b.ev(Battle.E_OBJECTIVE, 1, a.x, 0, a.z); b.radio(KIND[a.kind] + " done. +" + (500 + (int) alert * 100)); }
        }
        acts.removeIf(a -> a.done);
        while (acts.size() < 4) newActivity();
        // point the objective at the nearest job
        objT -= Battle.DT;
        if (objT <= 0) {
            objT = 2;
            Activity best = null; float bd = 1e9f;
            for (Activity a : acts) { float d = (float) Math.hypot(a.x - p.x, a.z - p.z); if (d < bd) { bd = d; best = a; } }
            if (best != null && (Math.abs(best.x - b.objX) > 1 || Math.abs(best.z - b.objZ) > 1)) {
                b.objective = KIND[best.kind] + ": " + (best.kind == HVT ? "breach and clear the house." : best.kind == CACHE ? "hold E at the cache to blow it." : best.kind == ROADBLOCK ? "kill the guards at the burning tires." : "get to him before they do.");
                b.objX = best.x; b.objZ = best.z;
            }
        }
        // patrols scale with the alert level
        patrolT -= Battle.DT;
        if (patrolT <= 0) {
            patrolT = 6;
            int level = (int) alert, want = 4 + level * 4, have = 0;
            for (Unit u : b.units) if (u.alive && u.team == b.hostileTeam && !u.guard && Math.hypot(u.x - p.x, u.z - p.z) < 150) have++;
            if (have < want && p.state != Battle.S_HELI) b.spawnMilitia(Math.min(4, want - have), p.x, p.z, 70, 130, level >= 2);
        }
    }
    @Override public void onKill(Unit victim, Unit src, boolean head, boolean melee) { if (src != null && src.player) { alert = Math.min(5.99f, alert + 0.3f); score += head ? 75 : 50; } }
    @Override public boolean playerDied() {
        Unit p = b.player;
        if (p.veh >= 0) b.exitVehicle(p);
        p.alive = false; p.hp = 0; p.deadT = 0; deaths++; respawnT = 4; score = Math.max(0, score - 250);
        b.radio("You're down. Back at the airfield in a few seconds.");
        return true;
    }
    @Override public String hud() { int lv = (int) alert; return "Alert " + "#####".substring(0, lv) + ".....".substring(lv) + "   Score " + score + "   Jobs done " + completed + "   Deaths " + deaths; }
    @Override public List<float[]> markers() { ArrayList<float[]> m = new ArrayList<>(); for (Activity a : acts) m.add(new float[]{a.x, a.z, 1}); for (Vehicle v : b.vehicles) if (v.usable() && v.type == Battle.V_HUMVEE) m.add(new float[]{v.x, v.z, 3}); return m; }
    @Override public String prompt(Unit p) {
        for (Activity a : acts) if (a.kind == CACHE && Math.hypot(p.x - a.x, p.z - a.z) < 2.5f) return "Hold E  Blow the cache";
        return null;
    }
    @Override public boolean use(Unit p, boolean pressed) { for (Activity a : acts) if (a.kind == CACHE && Math.hypot(p.x - a.x, p.z - a.z) < 2.5f) return true; return false; }
}
