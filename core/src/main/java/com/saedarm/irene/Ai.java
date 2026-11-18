package com.saedarm.irene;

import com.saedarm.irene.Battle.*;

/**
 * Enemy and squad brains. Each unit has a role and a state:
 *  - Riflemen pick cover that hides them crouched but lets them shoot standing, then cycle cover -> peek -> cover.
 *  - Suppressed units (bullets snapping past) stay down longer and shoot worse.
 *  - Flankers swing wide to the side of you before engaging.
 *  - Rushers and machete runners close the distance.
 *  - Gunners (PKM/SAW) hold cover and fire long bursts.
 *  - Anyone who loses sight of you for a few seconds may lob a grenade at where you were.
 *  - Garrisons wait inside houses until the door goes or they see you. Perches sit on roofs.
 *  - Spotting you tells everyone nearby where you are.
 */
final class Ai {
    static final int R_RIFLE = 0, R_FLANK = 1, R_RUSH = 2, R_GUNNER = 3, R_GARRISON = 4, R_PERCH = 5, R_MELEE = 6, R_SQUAD = 7;
    static final int A_ADVANCE = 0, A_MOVE_COVER = 1, A_COVER = 2, A_PEEK = 3, A_FLANK = 4, A_HOLD = 5, A_RUSH = 6;
    static final int WIN = 10; // local path window radius in cells

    static void assignRole(Battle b, Unit u) {
        if (u.kit == Battle.KIT_MACHETE) { u.role = R_MELEE; u.aiState = A_RUSH; return; }
        if (u.team == b.playerTeam) { u.role = R_SQUAD; u.aiState = A_ADVANCE; return; }
        float f = b.rng.nextFloat();
        if (u.kit == 6 || u.kit == 1) u.role = R_GUNNER;
        else if (f < 0.22f) u.role = R_FLANK;
        else if (u.team == Battle.SNA && f < 0.36f) u.role = R_RUSH;
        else u.role = R_RIFLE;
        u.aiState = u.role == R_RUSH ? A_RUSH : A_ADVANCE;
    }

    static void tick(Battle b, Unit u) {
        float DT = Battle.DT;
        if (u.state == Battle.S_HELI) { Heli h = b.helis.get(u.heli); b.seatPos(u, h); if (!h.flying()) { u.state = Battle.S_FOOT; u.heli = -1; } return; }
        if (u.state == Battle.S_ROPE_DOWN || u.state == Battle.S_ROPE_UP) { b.rope(u); return; }
        if (u.state == Battle.S_CLIMB || u.state == Battle.S_DOWN) return;
        if (u.state == Battle.S_VEH) {
            Vehicle v = b.vehicles.get(u.veh); b.seatVeh(u, v);
            if (u.vseat >= 2) { u.think -= DT; if (u.think <= 0) { u.think = 0.3f; perceive(b, u); } Unit t = target(b, u); if (t != null) { aimAt(u, t.x, t.y + 1.1f, t.z, 9); fire(b, u, t, true); } }
            return;
        }
        if (b.time - u.lastHurt > 6 && u.hp < u.maxHp) u.hp = Math.min(u.maxHp, u.hp + 8 * DT);
        if (u.stunT > 0) {
            u.stunT -= DT; u.crouch = false; u.fireCd = Math.max(u.fireCd, 0.3f);
            float a = (float) Math.sin(b.time * 7 + u.id) * 1.2f;
            b.moveWorld(u, (float) Math.cos(a) * 1.2f, (float) Math.sin(a) * 1.2f, false, false, 0, 0);
            return;
        }
        u.meleeCd -= DT; u.stateT -= DT;
        u.think -= DT;
        if (u.think <= 0) { u.think = 0.22f + b.rng.nextFloat() * 0.12f; perceive(b, u); decide(b, u); }
        Unit t = target(b, u);
        Heli th = u.targetHeli >= 0 ? b.helis.get(u.targetHeli) : null;
        if (th != null && !th.flying()) { th = null; u.targetHeli = -1; }
        boolean sees = t != null && u.react <= 0.35f;

        // ---- aim
        if (th != null) aimAtHeli(b, u, th);
        else if (t != null) aimAt(u, t.x, t.y + t.height() * (b.rng.nextFloat() < 0.15f ? 0.92f : 0.65f), t.z, 9);

        // ---- move
        float spd = Battle.WALK * 0.8f * Battle.KITS[u.kit].speed; boolean sprint = false;
        float[] dir = null;
        float dp = (float) Math.hypot(b.player.x - u.x, b.player.z - u.z);
        switch (u.aiState) {
            case A_HOLD: break;
            case A_ADVANCE: {
                if (u.role == R_SQUAD) { if (dp > 7 && !(sees && dp < 22)) { dir = b.flowDir(u.x, u.z, b.field); sprint = dp > 16; } }
                else if (!(sees && dist(u, t) < 16)) { dir = advanceDir(b, u); sprint = !sees && dp > 45; }
                break;
            }
            case A_MOVE_COVER: case A_FLANK: {
                dir = localDir(b, u);
                if (dir == null && u.goalX >= 0) { float dx = u.goalX - u.x, dz = u.goalZ - u.z, l = (float) Math.hypot(dx, dz); if (l > 0.3f) dir = new float[]{dx / l, dz / l}; }
                sprint = !sees || u.aiState == A_FLANK;
                if (u.goalX >= 0 && Math.hypot(u.goalX - u.x, u.goalZ - u.z) < 0.9f) {
                    if (u.aiState == A_FLANK) { u.aiState = A_ADVANCE; u.role = R_RIFLE; }
                    else { u.aiState = A_COVER; u.stateT = 0.5f + b.rng.nextFloat() * 0.9f; }
                }
                break;
            }
            case A_COVER: u.crouch = true; break;
            case A_PEEK: u.crouch = false; if (b.rng.nextFloat() < 0.02f) u.strafe = -u.strafe; break;
            case A_RUSH: {
                if (t != null && dist(u, t) > 1.4f) { dir = dist(u, t) < 12 && sees ? direct(u, t) : (t.player || t.team == b.playerTeam ? b.flowDir(u.x, u.z, b.field) : direct(u, t)); sprint = true; }
                else if (t == null) { dir = b.flowDir(u.x, u.z, b.field); sprint = dp > 20; }
                break;
            }
        }
        if (u.perch || u.role == R_PERCH) dir = null;
        u.crouch = u.aiState == A_COVER || (u.aiState == A_HOLD && u.role == R_GARRISON && u.id % 2 == 0);
        if (u.stuck > 0.5f) { u.strafe = -u.strafe; u.stuck = 0; if (dir != null) dir = new float[]{-dir[1] * u.strafe, dir[0] * u.strafe}; }
        u.stuck = Math.max(0, u.stuck - DT * 0.3f);
        float wx = 0, wz = 0;
        if (dir != null) {
            float s = (sprint ? Battle.SPRINT * 0.88f : Battle.WALK * 0.85f) * Battle.KITS[u.kit].speed * (u.role == R_MELEE ? 1.1f : 1) * u.speedMul;
            if (sees && !sprint) s *= 0.75f;
            wx = dir[0] * s; wz = dir[1] * s;
            if (!(t != null && sees) && th == null) u.yaw += Battle.wrap((float) Math.atan2(-dir[0], -dir[1]) - u.yaw) * Math.min(1, 8 * DT);
        } else if (u.aiState == A_PEEK && u.role != R_GARRISON) {
            float rx = (float) Math.cos(u.yaw), rz = (float) -Math.sin(u.yaw);
            wx = rx * u.strafe * 1.4f; wz = rz * u.strafe * 1.4f;
        }
        // keep a little space between people
        for (Unit o : b.units) if (o != u && o.alive && o.state == Battle.S_FOOT && Math.abs(o.x - u.x) < 0.9f && Math.abs(o.z - u.z) < 0.9f) { wx += (u.x - o.x) * 3; wz += (u.z - o.z) * 3; }
        u.sprinting = sprint && dir != null;
        if (u.perch || u.role == R_PERCH) { u.vx = u.vz = 0; } else b.moveWorld(u, wx, wz, false, false, 0, 0);

        // ---- shoot
        if (th != null) { rocketAtHeli(b, u, th); return; }
        if (t != null && sees && u.aiState != A_COVER) fire(b, u, t, dir != null);
        else if (u.reloadT > 0) reloadTick(b, u);
        else if (u.mag[0] < Battle.WEAPONS[u.wep[0]].mag / 3 && u.aiState == A_COVER && u.reserve[0] > 0) { u.reloadT = Battle.WEAPONS[u.wep[0]].reload; }
    }

    // ------------------------------------------------------------------ perception
    static Unit target(Battle b, Unit u) { if (u.target < 0) return null; Unit t = b.byId(u.target); if (t == null || !t.alive) { u.target = -1; return null; } return t; }
    static float dist(Unit a, Unit t) { return (float) Math.hypot(t.x - a.x, t.z - a.z); }
    static float[] direct(Unit u, Unit t) { float dx = t.x - u.x, dz = t.z - u.z, l = (float) Math.hypot(dx, dz); return l < 0.1f ? null : new float[]{dx / l, dz / l}; }

    static void perceive(Battle b, Unit u) {
        Unit best = null; float bd = u.team == b.playerTeam ? 70 : 95;
        float ex = u.x, ey = u.eye(), ez = u.z;
        for (Unit v : b.units) {
            if (!v.alive || v.team == u.team || v.state == Battle.S_HELI || v.state == Battle.S_ROPE_UP || v.state == Battle.S_CLIMB || v.objectiveTarget) continue;
            float d = (float) Battle.dist3(v.x, v.y, v.z, u.x, u.y, u.z);
            if (d > b.sightRange(v) && !(u.house >= 0 && d < 8)) continue;
            if (v.player) d *= 0.8f;
            if (d < bd && b.city.los(ex, ey, ez, v.x, v.y + (v.crouch ? 0.9f : 1.3f), v.z)) { bd = d; best = v; }
        }
        if (best != null) {
            if (best.id != u.target) { u.target = best.id; u.react = (0.35f + b.rng.nextFloat() * 0.5f) * (1.4f - u.skill); }
            if (b.time - u.lastSeenT > 2 && u.team != b.playerTeam) alert(b, u, best);
            u.lastSeenX = best.x; u.lastSeenZ = best.z; u.lastSeenT = b.time;
            if (u.aiState == A_HOLD && u.role == R_GARRISON) u.aiState = A_PEEK;
        } else if (u.target >= 0 && b.time - u.lastSeenT > 1.0f) u.target = -1;
        u.react -= 0.25f;
        // militia with rockets look for low helicopters
        if (u.team == Battle.SNA && Battle.KITS[u.kit].secondary == Battle.W_RPG && (u.mag[1] > 0 || u.reserve[1] > 0) && u.targetHeli < 0) {
            for (Heli h : b.helis) {
                if (!h.flying() || h.team == Battle.SNA) continue;
                float d = (float) Battle.dist3(h.x, h.y, h.z, u.x, u.y, u.z);
                float chance = (u.kit == 5 ? Battle.AI_RPG_CHANCE : Battle.AI_RPG_CHANCE * 0.3f) * (0.6f + b.difficulty * 0.3f);
                if (d < 130 && h.y < 60 && b.rng.nextFloat() < chance && b.city.los(ex, ey, ez, h.x, h.y, h.z)) { u.targetHeli = h.id; u.react = 1.2f; break; }
            }
        }
    }
    /** Tell nearby friends where the enemy is. */
    static void alert(Battle b, Unit u, Unit seen) {
        for (Unit o : b.units) {
            if (o == u || !o.alive || o.team != u.team || o.player) continue;
            if (Math.hypot(o.x - u.x, o.z - u.z) > 35) continue;
            if (b.time - o.lastSeenT > 3) { o.lastSeenX = seen.x; o.lastSeenZ = seen.z; o.lastSeenT = b.time - 1; if (o.aiState == A_HOLD && o.role != R_GARRISON && o.role != R_PERCH) o.aiState = A_ADVANCE; }
        }
    }

    // ------------------------------------------------------------------ decisions
    static void decide(Battle b, Unit u) {
        Unit t = target(b, u);
        boolean known = b.time - u.lastSeenT < 8;
        float tx = t != null ? t.x : u.lastSeenX, tz = t != null ? t.z : u.lastSeenZ, ty = t != null ? t.y + 1.5f : 1.5f;
        switch (u.role) {
            case R_PERCH: return;
            case R_MELEE: case R_RUSH:
                if (t == null && u.role == R_RUSH && known && u.mag[0] > 0) u.aiState = A_RUSH;
                u.aiState = A_RUSH; return;
            case R_GARRISON:
                if (u.aiState == A_HOLD) { City.House h = u.house >= 0 ? b.city.houses.get(u.house) : null; if (h != null && h.open() && known) u.aiState = A_PEEK; return; }
                if (t == null && u.aiState == A_PEEK && b.rng.nextFloat() < 0.05f) u.aiState = A_HOLD;
                return;
            default: break;
        }
        if (u.role == R_SQUAD) {
            float dp = (float) Math.hypot(b.player.x - u.x, b.player.z - u.z);
            if (b.player.state != Battle.S_FOOT || dp > 22 || t == null) { u.aiState = A_ADVANCE; return; }
        }
        if (!known && t == null) { if (u.aiState != A_ADVANCE) u.aiState = A_ADVANCE; return; }
        float d = (float) Math.hypot(tx - u.x, tz - u.z);
        switch (u.aiState) {
            case A_ADVANCE:
                if (u.role == R_FLANK && t != null && d < 70) { if (pickFlank(b, u, tx, tz)) { u.aiState = A_FLANK; return; } u.role = R_RIFLE; }
                if (t != null && d < (u.role == R_GUNNER ? 55 : 42)) {
                    if (pickCover(b, u, tx, ty, tz, u.role == R_GUNNER ? 30 : 20)) u.aiState = A_MOVE_COVER;
                    else { u.aiState = A_PEEK; u.stateT = 1.5f + b.rng.nextFloat(); }
                }
                break;
            case A_MOVE_COVER: if (b.rng.nextFloat() < 0.01f) u.aiState = A_ADVANCE; break;
            case A_COVER: {
                if (u.coverX >= 0 && t != null && b.city.los(t.x, t.y + 1.5f, t.z, u.coverX, u.y + 1.0f, u.coverZ)) { u.aiState = A_ADVANCE; break; } // flanked: move
                if (u.stateT > 0) break;
                if (u.suppress > 1.2f && b.rng.nextFloat() < 0.7f) { u.stateT = 0.6f + b.rng.nextFloat(); break; }
                if (b.time - u.lastSeenT > 3 && known && u.grenades > 0 && d > 7 && d < 26 && b.rng.nextFloat() < 0.35f) { b.lobAt(u, u.lastSeenX, u.lastSeenZ); u.stateT = 1.2f; break; }
                if (u.hp < u.maxHp * 0.35f && b.rng.nextFloat() < 0.3f && pickCover(b, u, tx, ty, tz, 40)) { u.aiState = A_MOVE_COVER; break; }
                u.aiState = A_PEEK; u.stateT = u.role == R_GUNNER ? 3 + b.rng.nextFloat() * 1.5f : 1.3f + b.rng.nextFloat() * 1.3f;
                break;
            }
            case A_PEEK:
                if (u.suppress > 1.6f) { u.aiState = u.coverX >= 0 ? A_COVER : A_ADVANCE; u.stateT = 1 + b.rng.nextFloat(); break; }
                if (u.stateT <= 0) {
                    if (b.time - u.lastSeenT > 2.5f) { u.aiState = A_ADVANCE; u.coverX = -1; }
                    else if (u.coverX >= 0) { u.aiState = A_COVER; u.stateT = 0.8f + b.rng.nextFloat() * 1.2f; }
                    else { u.aiState = A_ADVANCE; }
                }
                break;
            default: break;
        }
    }
    static float[] advanceDir(Battle b, Unit u) {
        // militia and rangers both converge on the fight around the player
        return b.flowDir(u.x, u.z, b.field);
    }

    /** Find a nearby cell that hides you crouched from (tx,ty,tz) but lets you shoot standing. */
    static boolean pickCover(Battle b, Unit u, float tx, float ty, float tz, float prefer) {
        City c = b.city;
        int ucx = (int) (u.x / City.CELL), ucz = (int) (u.z / City.CELL);
        int best = -1; float bs = 1e9f;
        for (int dz = -4; dz <= 4; dz++) for (int dx = -4; dx <= 4; dx++) {
            int cx = ucx + dx, cz = ucz + dz;
            if (cx < 0 || cz < 0 || cx >= City.N || cz >= City.N) continue;
            int ci = cz * City.N + cx; if (!c.connected[ci]) continue;
            float px = cx * City.CELL + 2, pz = cz * City.CELL + 2;
            float g = c.groundAt(px, pz, 0.3f, u.y + 1);
            float d = (float) Math.hypot(px - tx, pz - tz);
            if (d < 8 || d > 70) continue;
            if (c.los(tx, ty, tz, px, g + 1.0f, pz)) continue;            // not hidden when crouched
            boolean peek = c.los(tx, ty, tz, px, g + 1.62f, pz);
            boolean taken = false;
            for (Unit o : b.units) if (o != u && o.alive && o.coverX >= 0 && Math.abs(o.coverX - px) < 1.5f && Math.abs(o.coverZ - pz) < 1.5f) { taken = true; break; }
            if (taken) continue;
            float s = (float) Math.hypot(px - u.x, pz - u.z) + Math.abs(d - prefer) * 0.45f + (peek ? 0 : 7) + b.rng.nextFloat() * 2;
            if (s < bs) { bs = s; best = ci; }
        }
        if (best < 0) return false;
        u.coverX = (best % City.N) * City.CELL + 2; u.coverZ = (best / City.N) * City.CELL + 2;
        setGoal(b, u, u.coverX, u.coverZ);
        return true;
    }
    static boolean pickFlank(Battle b, Unit u, float tx, float tz) {
        float dx = u.x - tx, dz = u.z - tz, l = (float) Math.hypot(dx, dz); if (l < 1) return false;
        float side = u.id % 2 == 0 ? 1 : -1;
        float px = -dz / l * side, pz = dx / l * side;
        for (float r = 18; r >= 10; r -= 4) {
            float gx = tx + px * r + dx / l * 8, gz = tz + pz * r + dz / l * 8;
            if (Math.hypot(gx - u.x, gz - u.z) > WIN * City.CELL - 4) { gx = u.x + (gx - u.x) * 0.5f; gz = u.z + (gz - u.z) * 0.5f; }
            int ci = b.city.cellOf(gx, gz);
            if (b.city.connected[ci]) { setGoal(b, u, (ci % City.N) * City.CELL + 2, (ci / City.N) * City.CELL + 2); return u.lfGoal >= 0; }
        }
        return false;
    }

    // ------------------------------------------------------------------ local pathing (small BFS window around the goal)
    static void setGoal(Battle b, Unit u, float gx, float gz) {
        u.goalX = gx; u.goalZ = gz;
        int W = 2 * WIN + 1;
        if (u.lf == null) u.lf = new int[W * W];
        int gcx = (int) (gx / City.CELL), gcz = (int) (gz / City.CELL);
        u.lfOx = gcx - WIN; u.lfOz = gcz - WIN;
        java.util.Arrays.fill(u.lf, Integer.MAX_VALUE);
        int[] q = new int[W * W]; int h = 0, t = 0;
        u.lf[WIN * W + WIN] = 0; q[t++] = WIN * W + WIN;
        while (h < t) {
            int c = q[h++], lx = c % W, lz = c / W, d = u.lf[c];
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) continue;
                int nx = lx + dx, nz = lz + dz; if (nx < 0 || nz < 0 || nx >= W || nz >= W) continue;
                if (!b.city.pass(u.lfOx + lx, u.lfOz + lz, dx, dz)) continue;
                int n = nz * W + nx, nd = d + (dx != 0 && dz != 0 ? 14 : 10);
                if (nd < u.lf[n]) { if (u.lf[n] == Integer.MAX_VALUE) q[t++] = n; u.lf[n] = nd; }
            }
        }
        int ux = (int) (u.x / City.CELL) - u.lfOx, uz = (int) (u.z / City.CELL) - u.lfOz;
        u.lfGoal = (ux >= 0 && uz >= 0 && ux < W && uz < W && u.lf[uz * W + ux] != Integer.MAX_VALUE) ? 1 : -1;
    }
    static float[] localDir(Battle b, Unit u) {
        if (u.lf == null || u.lfGoal < 0) return null;
        int W = 2 * WIN + 1;
        int cx = (int) (u.x / City.CELL), cz = (int) (u.z / City.CELL), lx = cx - u.lfOx, lz = cz - u.lfOz;
        if (lx < 0 || lz < 0 || lx >= W || lz >= W) return null;
        int bd = u.lf[lz * W + lx], bx = cx, bz = cz;
        if (bd == 0) { float dx = u.goalX - u.x, dz = u.goalZ - u.z, l = (float) Math.hypot(dx, dz); return l < 0.2f ? null : new float[]{dx / l, dz / l}; }
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            int nx = lx + dx, nz = lz + dz; if ((dx == 0 && dz == 0) || nx < 0 || nz < 0 || nx >= W || nz >= W) continue;
            if (!b.city.pass(cx, cz, dx, dz)) continue;
            if (u.lf[nz * W + nx] < bd) { bd = u.lf[nz * W + nx]; bx = cx + dx; bz = cz + dz; }
        }
        float tx = bx * City.CELL + 2, tz = bz * City.CELL + 2, dx = tx - u.x, dz = tz - u.z, l = (float) Math.hypot(dx, dz);
        return l < 0.05f ? null : new float[]{dx / l, dz / l};
    }

    // ------------------------------------------------------------------ shooting
    static void aimAt(Unit u, float x, float y, float z, float rate) {
        float dx = x - u.x, dy = y - u.eye(), dz = z - u.z;
        float wy = (float) Math.atan2(-dx, -dz), wp = (float) Math.atan2(dy, Math.sqrt(dx * dx + dz * dz));
        u.yaw += Battle.wrap(wy - u.yaw) * Math.min(1, rate * Battle.DT); u.pitch += (wp - u.pitch) * Math.min(1, rate * Battle.DT);
    }
    static void aimAtHeli(Battle b, Unit u, Heli th) {
        float lead = (float) Battle.dist3(th.x, th.y, th.z, u.x, u.y, u.z) / Battle.ROCKET_SPEED, lx = th.x, lz = th.z;
        if (th.state == Battle.H_ORBIT) { float spd = th.type == Battle.BLACKHAWK ? 20 : 26; float a2 = th.orbitA + spd / Math.max(30, th.orbitR) * lead; lx = th.ox + (float) Math.cos(a2) * th.orbitR; lz = th.oz + (float) Math.sin(a2) * th.orbitR; }
        aimAt(u, lx, th.y, lz, 9);
    }
    static void reloadTick(Battle b, Unit u) {
        u.reloadT -= Battle.DT;
        if (u.reloadT <= 0) { int s = u.slot; Weapon w = Battle.WEAPONS[u.weapon()]; int take = Math.min(w.mag - u.mag[s], Math.max(u.reserve[s], 30)); u.mag[s] += take; }
    }
    static void rocketAtHeli(Battle b, Unit u, Heli th) {
        u.fireCd -= Battle.DT;
        if (u.reloadT > 0) { reloadTick(b, u); return; }
        if (u.react > 0) return;
        if (u.slot != 1) { u.slot = 1; u.fireCd = 0.6f; return; }
        if (u.mag[1] <= 0) { if (u.reserve[1] > 0) { u.reserve[1]--; u.reloadT = Battle.WEAPONS[Battle.W_RPG].reload; } else { u.targetHeli = -1; u.slot = 0; } return; }
        if (u.fireCd <= 0) { u.mag[1]--; u.fireCd = 4; b.fireRocket(u, u.yaw, u.pitch, Battle.AI_RPG_SPREAD - u.skill * 2.5f, Battle.PR_ROCKET, 1); u.targetHeli = -1; u.react = 2.5f; u.slot = 0; }
    }
    static void fire(Battle b, Unit u, Unit t, boolean moving) {
        u.fireCd -= Battle.DT;
        if (u.reloadT > 0) { reloadTick(b, u); return; }
        Weapon w = Battle.WEAPONS[u.weapon()];
        float d = dist(u, t);
        if (w.melee) {
            if (d < 1.7f && Math.abs(t.y - u.y) < 1.4f && u.meleeCd <= 0) { u.meleeCd = 0.9f; b.ev(Battle.E_MELEE, u.id, u.x, u.y, u.z); b.damage(t, w.dmg * (0.6f + b.wave * 0.05f), u, false); b.ev2(Battle.E_BLOOD, 0, t.x, t.y + 1.2f, t.z, (float) -Math.sin(u.yaw), 0.1f, (float) -Math.cos(u.yaw)); }
            return;
        }
        // rockets at vehicles
        if (t.state == Battle.S_VEH && Battle.KITS[u.kit].secondary == Battle.W_RPG && (u.mag[1] > 0 || u.reserve[1] > 0) && d > 12 && d < 90) {
            Vehicle v = b.vehicles.get(t.veh);
            if (u.slot != 1) { u.slot = 1; u.fireCd = 0.8f; return; }
            if (u.mag[1] <= 0) { u.reserve[1]--; u.reloadT = 3; u.mag[1] = 1; return; }
            float lead = d / Battle.ROCKET_SPEED; float fx = (float) -Math.sin(v.yaw) * v.speed * lead, fz = (float) -Math.cos(v.yaw) * v.speed * lead;
            aimAt(u, v.x + fx, 1.1f, v.z + fz, 60);
            if (u.fireCd <= 0) { u.mag[1]--; u.fireCd = 5; b.fireRocket(u, u.yaw, u.pitch, 3.0f - u.skill * 1.5f, Battle.PR_ROCKET, 1); }
            return;
        }
        if (u.slot != 0 && u.state != Battle.S_VEH) { u.slot = 0; u.fireCd = 0.4f; return; }
        if (u.mag[0] <= 0) { u.reloadT = w.reload * 1.1f; return; }
        if (u.burstPause > 0) { u.burstPause -= Battle.DT; return; }
        if (u.fireCd > 0) return;
        if (!b.city.los(u.x, u.eye(), u.z, t.x, t.y + (t.crouch ? 0.9f : 1.2f), t.z)) return;
        boolean friendly = u.team == b.playerTeam;
        float err = (friendly ? 0 : Battle.ENEMY_SPREAD_ADD) + w.hip * 0.7f + (1.2f - u.skill) * 4.2f + (t.player && t.sprinting ? 2.2f : 0) + (t.crouch ? 1.0f : 0) + d * 0.02f
                + u.suppress * 1.8f + (moving ? 1.5f : 0) + (b.night ? 1.5f : 0);
        if (t.player && b.time - t.lastHurt < 0.6f) err += 1.5f;
        if (u.role == R_RUSH) err += 1.5f;
        u.fireCd = w.interval * (friendly ? 1.5f : Battle.ENEMY_FIRE_MUL) * (u.role == R_GUNNER ? 0.7f : 1);
        u.mag[0]--; u.lastShot = b.time;
        b.shoot(u, u.weapon(), u.yaw, u.pitch, err, u.x, u.eye() - 0.1f, u.z, 1);
        u.burst++;
        int burstLen = u.role == R_GUNNER ? 8 + b.rng.nextInt(8) : 3 + b.rng.nextInt(5);
        if (u.burst > burstLen) { u.burst = 0; u.burstPause = 0.35f + b.rng.nextFloat() * (1.0f - u.skill * 0.5f); }
    }
}
