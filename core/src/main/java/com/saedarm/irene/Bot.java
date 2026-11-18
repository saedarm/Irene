package com.saedarm.irene;

import com.saedarm.irene.Battle.*;
import java.util.Random;

/** Drives the player with the same Input struct a human fills. Used by --autoplay and Sim. */
public final class Bot {
    private final Battle b; private final Random r = new Random(7);
    private float callCd, jumpCd, lastX, lastZ, stuckT;
    public Bot(Battle b) { this.b = b; }

    public void fill(Input in) {
        Unit p = b.player; if (!p.alive) return;
        in.fire = false; in.ads = false; in.sprint = false; in.use = false; in.crouch = false; in.moveX = 0; in.moveZ = 0;
        callCd -= Battle.DT; jumpCd -= Battle.DT;
        if (p.state == Battle.S_HELI) {
            Heli h = b.helis.get(p.heli);
            Vehicle car = null; for (Vehicle v : b.vehicles) if (v.type == Battle.V_CAR && v.usable()) car = v;
            if (car != null) { float lead = 0.15f; aim(in, p, car.x - (float) Math.sin(car.yaw) * (2 + car.speed * lead), 0.8f, car.z - (float) Math.cos(car.yaw) * (2 + car.speed * lead), p.y); in.fire = true; return; }
            Unit t = target(p, 90);
            if (t != null) { aim(in, p, t.x, t.y + 1, t.z, p.y); in.fire = true; }
            boolean nearDrop = Math.hypot(h.x - b.objX, h.z - b.objZ) < 70;
            if (h.state == Battle.H_HOVER || (nearDrop && h.state == Battle.H_ORBIT && r.nextFloat() < 0.01f)) in.presses |= Input.USE;
            return;
        }
        if (p.state == Battle.S_VEH) { drive(in, p); return; }
        if (p.state != Battle.S_FOOT) return;
        Heli th = null;
        if (p.team == Battle.SNA && (p.mag[1] > 0 || p.reserve[1] > 0)) {
            float bd = 120;
            for (Heli h : b.helis) if (h.flying() && h.team == Battle.US) { float d = (float) Math.hypot(h.x - p.x, h.z - p.z); if (d < bd && b.city.los(p.x, p.eye(), p.z, h.x, h.y, h.z)) { bd = d; th = h; } }
        }
        Unit t = target(p, 70);
        boolean busy = false;
        if (th != null && (t == null || Math.hypot(t.x - p.x, t.z - p.z) > 25)) {
            if (p.slot != 1) in.presses |= Input.SLOT2;
            float lead = (float) Math.hypot(th.x - p.x, th.z - p.z) / Battle.ROCKET_SPEED, lx = th.x, lz = th.z;
            if (th.state == Battle.H_ORBIT) { float spd = th.type == Battle.BLACKHAWK ? 20 : 26; float a = th.orbitA + spd / Math.max(30, th.orbitR) * lead; lx = th.ox + (float) Math.cos(a) * th.orbitR; lz = th.oz + (float) Math.sin(a) * th.orbitR; }
            if (aim(in, p, lx, th.y, lz, p.eye()) < 0.03f) in.fire = r.nextBoolean();
            in.ads = true; busy = true;
            if (p.mag[1] == 0) in.presses |= Input.RELOAD;
        } else if (t != null) {
            if (p.slot != 0 && p.wep[0] >= 0 && !Battle.WEAPONS[p.wep[0]].rocket) in.presses |= Input.SLOT1;
            float d = (float) Math.hypot(t.x - p.x, t.z - p.z);
            if (d < 1.8f && p.meleeCd <= 0) in.presses |= Input.MELEE;
            if (aim(in, p, t.x, t.y + t.height() * 0.7f, t.z, p.eye()) < 0.05f) in.fire = r.nextFloat() < 0.85f;
            in.ads = d > 18; busy = d < 30;
            if (d < 25 && p.grenades > 0 && r.nextFloat() < 0.003f) in.presses |= Input.GRENADE;
        }
        if (p.mag[p.slot] == 0) in.presses |= Input.RELOAD;
        if (p.reserve[0] < 30) for (float[] c : b.city.crates) if (Math.hypot(c[0] - p.x, c[1] - p.z) < 2.5f) in.presses |= Input.USE;
        float gx = b.objX, gz = b.objZ;
        Heli pick = null;
        for (Heli h : b.helis) if (h.state == Battle.H_HOVER && h.purpose == Battle.P_PICKUP && h.team == p.team) pick = h;
        if (pick != null) { gx = pick.x; gz = pick.z; if (Math.hypot(pick.x - p.x, pick.z - p.z) < 6) in.presses |= Input.USE; }
        else if (b.objective.contains("(H)") && callCd <= 0) { in.presses |= Input.CALL; callCd = 12; }
        // humvee to hop in?
        if (b.mode instanceof Story && ((Story) b.mode).ch == 4) for (Vehicle v : b.vehicles) if (v.usable() && v.type == Battle.V_HUMVEE && Math.hypot(v.x - p.x, v.z - p.z) < 40) { gx = v.x; gz = v.z; if (Battle.inVehicle(v, p.x, p.z, 1.4f)) in.presses |= Input.USE; }
        float dObj = (float) Math.hypot(gx - p.x, gz - p.z);
        boolean holdHere = pick == null && (b.objective.startsWith("Hold") || b.objective.startsWith("Protect") || b.objective.startsWith("Round") || b.objective.startsWith("Get ready") || b.objective.startsWith("Clear the last") || b.objective.startsWith("Bring down") || b.objective.startsWith("Two birds") || b.objective.startsWith("Survive"));
        float stop = b.objective.startsWith("Breach") ? 0.9f : b.objective.startsWith("Grab") ? 5.5f : 3.5f;
        if (b.doorNear(p) >= 0 && b.objective.startsWith("Breach")) { in.use = true; if (r.nextFloat() < 0.03f) in.presses |= Input.USE; }
        else if (dObj < stop) { in.use = true; if (r.nextFloat() < 0.02f) in.presses |= Input.USE; }
        else if (!(holdHere && dObj < 14) && !(busy && dObj < 12 && !b.objective.startsWith("Breach") && !b.objective.startsWith("Grab"))) {
            float[] d = b.objectiveDir(p.x, p.z);
            if (dObj < 3.5f || pick != null) d = new float[]{(gx - p.x) / dObj, (gz - p.z) / dObj};
            if (d != null) {
                float s = (float) Math.sin(p.yaw), c = (float) Math.cos(p.yaw);
                if (!busy && th == null) in.yaw += Battle.wrap((float) Math.atan2(-d[0], -d[1]) - in.yaw) * 0.15f;
                in.moveZ = d[0] * -s + d[1] * -c; in.moveX = d[0] * c + d[1] * -s;
                in.sprint = !busy && in.moveZ > 0.6f;
            }
        }
        float moved = (float) Math.hypot(p.x - lastX, p.z - lastZ); lastX = p.x; lastZ = p.z;
        if ((in.moveX != 0 || in.moveZ != 0) && moved < 0.01f) stuckT += Battle.DT; else stuckT = 0;
        if (stuckT > 0.5f && jumpCd <= 0) { in.presses |= Input.JUMP; jumpCd = 0.8f; }
        if (stuckT > 1.5f) in.moveX = r.nextBoolean() ? 1 : -1;
    }
    private float revT;
    private void drive(Input in, Unit p) {
        Vehicle v = b.vehicles.get(p.veh);
        if (p.vseat != 0) { Unit t = target(p, 90); if (t != null) { aim(in, p, t.x, t.y + 1, t.z, p.y + 1.5f); in.fire = true; } return; }
        float[] d = b.objectiveDir(v.x, v.z);
        if (d == null) { in.moveZ = 0; return; }
        float want = (float) Math.atan2(-d[0], -d[1]), diff = Battle.wrap(want - v.yaw);
        in.moveX = Math.max(-1, Math.min(1, -diff * 2.2f));
        in.moveZ = Math.abs(diff) > 1.2f ? 0.3f : v.speed < 13 ? 1 : 0;
        if (Math.abs(v.speed) < 0.5f) revT += Battle.DT; else revT = Math.max(0, revT - Battle.DT);
        if (revT > 1.0f) { in.moveZ = -1; in.moveX = -in.moveX; if (revT > 2.2f) revT = 0; }
        in.yaw = v.yaw;
    }
    private Unit target(Unit p, float range) {
        Unit best = null; float bd = range;
        for (Unit v : b.units) {
            if (!v.alive || v.team == p.team || v.state == Battle.S_HELI || v.state == Battle.S_CLIMB || v.objectiveTarget) continue;
            float d = (float) Math.hypot(v.x - p.x, v.z - p.z);
            if (d < bd && b.city.los(p.x, p.state == Battle.S_HELI ? p.y : p.eye(), p.z, v.x, v.y + 1.2f, v.z)) { bd = d; best = v; }
        }
        return best;
    }
    private float aim(Input in, Unit p, float x, float y, float z, float eye) {
        float dx = x - p.x, dy = y - eye, dz = z - p.z;
        float wy = (float) Math.atan2(-dx, -dz), wp = (float) Math.atan2(dy, Math.sqrt(dx * dx + dz * dz));
        float ey = Battle.wrap(wy - in.yaw), ep = wp - in.pitch;
        in.yaw += ey * 0.25f + (r.nextFloat() - .5f) * 0.01f; in.pitch += ep * 0.25f;
        return Math.abs(ey) + Math.abs(ep);
    }
}
