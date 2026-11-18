package com.saedarm.irene;

import com.saedarm.irene.Battle.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Siege: round-based endless waves in a walled four-zone arena, Call of Duty Zombies style.
 * You start with an M9 and 500 points. Points buy gates into new zones, guns chalked on the walls,
 * a spin of the weapons crate, perks from the tea stalls once the generator is on, and the
 * Armorer's bench (pack-a-punch). Militia climb in through boarded windows; rebuild the boards for
 * points. Easter eggs: three radio parts call in the AC-130 (the gunship Task Force Ranger asked for
 * and never got), and four IRENE tags shot in order of discovery load the crate with a Golden AK.
 */
public final class Siege extends Mode {
    public static final int PU_AMMO = 0, PU_INSTA = 1, PU_DOUBLE = 2, PU_NUKE = 3, PU_CARPENTER = 4;
    public static final String[] PU_NAMES = {"Max Ammo", "Insta-Kill", "Double Points", "Danger Close", "Carpenter"};
    public static final String[] PERKS = {"Body Armor", "Speed Loader", "Double Tap", "Second Wind", "Stamina"};
    static final int[] PERK_BITS = {Battle.P_ARMOR, Battle.P_SPEED, Battle.P_DTAP, Battle.P_SECOND, Battle.P_STAMINA};
    static final int[] BOX_POOL = {Battle.W_M16, Battle.W_870, Battle.W_MP5, Battle.W_AK, Battle.W_M14, Battle.W_PKM, Battle.W_M60, Battle.W_SAW, Battle.W_M79, Battle.W_RPG, Battle.W_AT4, Battle.W_HANDMINI, Battle.W_CAR15_AIM};
    static final int BOX_COST = 950, PAP_COST = 5000;

    public int round, points = 500, toSpawn, spawned, rebuildPts;
    public float breakT = 6, spawnCd, instaT, doubleT, gunshipT, powerT;
    public boolean power, goldReady;
    public final boolean[] zoneOpen = {true, false, false, false};
    public int box, boxUses, boxMoveAt; public int boxState; public float boxT; public int boxWeapon, boxShow;
    public int papState; public float papT; public int papSlot, papWeapon;
    public final boolean[] parts = new boolean[3], tagsShot = new boolean[4]; public boolean radioUsed;
    public final ArrayList<float[]> powerups = new ArrayList<>(); // x, z, type, life
    private int dropsThisRound; private float rebuildT, genT, radioT, downT = -1; private int secondUses;

    @Override public String title() { return "Siege"; }
    @Override public boolean isSiege() { return true; }
    @Override public boolean defaultNight() { return true; }

    @Override public void setup() {
        if (b.playerTeam != Battle.US) b.setPlayerTeam(Battle.US, 0);
        Unit p = b.player; p.kit = 0;
        b.place(p, b.city.start.x, b.city.start.z);
        p.wep[0] = Battle.W_M9; p.mag[0] = 15; p.reserve[0] = 60; p.wep[1] = -1; p.mag[1] = p.reserve[1] = 0; p.slot = 0;
        p.grenades = 2; p.flashes = 0; p.charges = 0; p.maxHp = p.hp = 100;
        box = 0; boxMoveAt = 4 + b.rng.nextInt(6);
        b.input.yaw = 3.14f;
        b.setObj("Survive. Buy guns off the walls, open gates, find the generator.", b.city.start.x, b.city.start.z);
        b.radio("Siege. The militia come through the windows. Rebuild the boards (hold E) for points.");
    }

    // ------------------------------------------------------------------ rounds
    private int roundTotal() { return round <= 4 ? 4 + round * 3 : (int) (round * 4.5f + round * round * 0.12f); }
    private float enemyHp() { float hp = round <= 9 ? 60 + 25 * round : 285 * (float) Math.pow(1.1, round - 9); return hp; }
    @Override public void tick() {
        Unit p = b.player;
        instaT -= Battle.DT; doubleT -= Battle.DT;
        if (downT >= 0) { downT -= Battle.DT; if (downT < 0) { p.state = Battle.S_FOOT; p.hp = p.maxHp * 0.6f; b.ev(Battle.E_REVIVE, 0, p.x, p.y, p.z); } }
        // round flow
        if (breakT > 0) {
            breakT -= Battle.DT;
            if (breakT <= 0) { round++; toSpawn = roundTotal(); spawned = 0; dropsThisRound = 0; rebuildPts = 0; b.wave = round; b.ev(Battle.E_ROUND, round, 0, 0, 0); }
        } else {
            spawnCd -= Battle.DT;
            int alive = b.aliveHostiles();
            if (spawned < toSpawn && alive < 24 && spawnCd <= 0) { spawnCd = Math.max(0.5f, 2.0f - round * 0.08f); spawnOne(); }
            if (spawned >= toSpawn && alive == 0) { breakT = 9; b.radio("Round " + round + " survived."); }
        }
        // climbers at the windows
        for (Unit u : b.units) if (u.alive && u.state == Battle.S_CLIMB) climb(u);
        // the box
        if (boxState == 1) { boxT -= Battle.DT; boxShow = BOX_POOL[(int) (b.time * 12) % BOX_POOL.length]; if (boxT <= 0) {
            if (boxUses >= boxMoveAt && b.rng.nextFloat() < 0.5f && !goldReady) { boxState = 3; boxT = 4; points += BOX_COST; b.ev(Battle.E_BOX_MOVE, box, spotX(), 0, spotZ()); b.radio("A Little Bird is hauling the weapons crate away. Refunded."); }
            else { boxState = 2; boxT = 8; boxWeapon = goldReady ? Battle.W_GOLD_AK : BOX_POOL[b.rng.nextInt(BOX_POOL.length)]; goldReady = false; boxShow = boxWeapon; }
        } }
        else if (boxState == 2) { boxT -= Battle.DT; if (boxT <= 0) boxState = 0; }
        else if (boxState == 3) { boxT -= Battle.DT; if (boxT <= 0) { int nb; do nb = b.rng.nextInt(b.city.boxSpots.size()); while (nb == box && b.city.boxSpots.size() > 1); box = nb; boxUses = 0; boxMoveAt = 4 + b.rng.nextInt(6); boxState = 0; b.radio("The crate came down somewhere in " + zoneName(b.city.boxSpots.get(box).zone == -1 ? b.city.zoneOf(spotX(), spotZ()) : b.city.zoneOf(spotX(), spotZ())) + "."); } }
        if (papState == 1) { papT -= Battle.DT; if (papT <= 0) papState = 2; }
        // powerups
        for (int i = powerups.size() - 1; i >= 0; i--) {
            float[] pu = powerups.get(i); pu[3] -= Battle.DT;
            if (pu[3] <= 0) { powerups.remove(i); continue; }
            if (p.alive && Math.hypot(p.x - pu[0], p.z - pu[1]) < 1.6f) { grab((int) pu[2]); powerups.remove(i); }
        }
        // AC-130 Easter egg
        if (gunshipT > 0) {
            gunshipT -= Battle.DT;
            if ((int) ((gunshipT + Battle.DT) / 1.3f) != (int) (gunshipT / 1.3f)) {
                Unit t = null; for (Unit u : b.units) if (u.alive && u.team == b.hostileTeam && (b.city.inArena(u.x, u.z) || u.state == Battle.S_CLIMB) && (t == null || b.rng.nextFloat() < 0.3f)) t = u;
                if (t != null) { Proj boom = new Proj(); boom.x = t.x; boom.y = t.y + 0.3f; boom.z = t.z; boom.team = Battle.US; boom.owner = p.id; boom.rocket = true; b.ev(Battle.E_GUNSHIP, 0, t.x, t.y, t.z); b.explode(boom, 5, 600); }
            }
            if (gunshipT <= 0) b.radio("Spectre: Bingo fuel. Good luck down there.");
        }
        b.objective = round == 0 ? "Get ready. First round in " + (int) Math.ceil(breakT) + "." : "Round " + round + ". " + Math.max(0, toSpawn - spawned + b.aliveHostiles()) + " left.";
    }
    private String zoneName(int z) { return z == 0 ? "the Courtyard" : z == 1 ? "the Hotel Yard" : z == 2 ? "the Market" : "the Crash Site"; }
    private float spotX() { return b.city.boxSpots.get(box).x; }
    private float spotZ() { return b.city.boxSpots.get(box).z; }

    private void spawnOne() {
        ArrayList<City.Window> open = new ArrayList<>();
        for (City.Window w : b.city.windows) if (w.zone >= 0 && zoneOpen[w.zone]) open.add(w);
        if (open.isEmpty()) return;
        // prefer windows near the player
        City.Window w = open.get(b.rng.nextInt(open.size()));
        for (int i = 0; i < 3; i++) { City.Window o = open.get(b.rng.nextInt(open.size())); if (Math.hypot(o.ix - b.player.x, o.iz - b.player.z) < Math.hypot(w.ix - b.player.x, w.iz - b.player.z)) w = o; }
        float f = b.rng.nextFloat();
        int kit = Battle.KIT_MACHETE;
        if (round >= 3 && f < Math.min(0.4f, 0.12f + round * 0.02f)) kit = 4;
        if (round >= 7 && f > 0.92f) kit = 5;
        if (round >= 10 && f > 0.86f && f <= 0.92f) kit = 6;
        Unit u = b.spawn(Battle.SNA, kit, w.ox, 0, w.oz);
        u.maxHp = u.hp = enemyHp(); u.state = Battle.S_CLIMB; u.window = b.city.windows.indexOf(w); u.climbT = 0;
        u.yaw = (float) Math.atan2(w.nx, w.nz); u.speedMul = round < 3 ? 0.55f : round < 6 ? 0.8f : round < 12 ? 1f : 1.12f;
        if (kit == Battle.KIT_MACHETE) { u.role = Ai.R_MELEE; u.aiState = Ai.A_RUSH; }
        u.target = b.player.id; u.lastSeenX = b.player.x; u.lastSeenZ = b.player.z; u.lastSeenT = b.time;
        spawned++;
    }
    private void climb(Unit u) {
        City.Window w = b.city.windows.get(u.window);
        u.climbT += Battle.DT;
        int planks = 0; for (int pi : w.planks) if (!b.city.boxes.get(pi).dead) planks++;
        if (planks > 0) {
            if (u.climbT > 1.1f) { u.climbT = 0; for (int i = w.planks.size() - 1; i >= 0; i--) { City.Box pb = b.city.boxes.get(w.planks.get(i)); if (!pb.dead) { pb.dead = true; b.ev(Battle.E_PLANK, 0, w.ix, 1.5f, w.iz); break; } } }
            return;
        }
        if (u.climbT > 0.8f) { u.state = Battle.S_FOOT; b.place(u, w.ix, w.iz); u.climbT = 0; }
    }

    // ------------------------------------------------------------------ points
    private void add(int pts) { points += pts * (doubleT > 0 ? 2 : 1); b.ev(Battle.E_POINTS, pts * (doubleT > 0 ? 2 : 1), 0, 0, 0); }
    @Override public void onHit(Unit victim, Unit src, float dmg) { if (victim.alive) add(10); }
    @Override public void onKill(Unit victim, Unit src, boolean head, boolean melee) {
        if (src == null || !src.player) return;
        add(melee ? 130 : head ? 100 : 60);
        if (breakT <= 0 && dropsThisRound < 3 && b.rng.nextFloat() < 0.03f) { dropsThisRound++; powerups.add(new float[]{victim.x, victim.z, b.rng.nextInt(5), 25}); b.ev(Battle.E_POWERUP, -1, victim.x, 1, victim.z); }
    }
    @Override public float playerDamageMul(int weapon, boolean papd) { return instaT > 0 ? 100 : papd ? 2f : 1f; }
    private void grab(int type) {
        b.ev(Battle.E_POWERUP, type, b.player.x, 1, b.player.z);
        switch (type) {
            case PU_AMMO: b.refill(b.player); for (int s = 0; s < 2; s++) if (b.player.wep[s] >= 0) b.player.mag[s] = b.magSize(b.player, s); break;
            case PU_INSTA: instaT = 30; break;
            case PU_DOUBLE: doubleT = 30; break;
            case PU_NUKE: for (Unit u : b.units) if (u.alive && u.team == b.hostileTeam) { u.alive = false; u.deadT = 0; b.ev(Battle.E_DIE, u.id, u.x, u.y, u.z); b.ev2(Battle.E_BLOOD, 2, u.x, u.y + 1, u.z, 0, 1, 0); } points += 400; b.ev(Battle.E_EXPLODE, 1, b.player.x, 20, b.player.z); break;
            case PU_CARPENTER: for (City.Window w : b.city.windows) for (int pi : w.planks) b.city.revive(pi); points += 200; break;
        }
    }
    @Override public boolean playerDied() {
        Unit p = b.player;
        if (p.has(Battle.P_SECOND) && secondUses < 3) {
            secondUses++; p.perks &= ~Battle.P_SECOND; p.state = Battle.S_DOWN; p.hp = 1; downT = 6;
            b.ev(Battle.E_DOWNED, 0, p.x, p.y, p.z); b.radio("Second Wind. Hang on...");
            return true;
        }
        b.resultText = "You survived " + Math.max(0, round - 1) + " rounds.";
        return false;
    }
    @Override public void onShot(float x, float y, float z) {
        for (int i = 0; i < b.city.tags.size(); i++) {
            City.Spot t = b.city.tags.get(i);
            if (!tagsShot[i] && Battle.dist3(t.x, t.y, t.z, x, y, z) < 0.8f) {
                tagsShot[i] = true; b.ev(Battle.E_EGG, 20 + i, t.x, t.y, t.z);
                boolean all = true; for (boolean s : tagsShot) all &= s;
                if (all) { goldReady = true; b.radio("Something gold glints inside the weapons crate."); }
            }
        }
    }

    // ------------------------------------------------------------------ interaction
    private static boolean near(Unit p, float x, float z, float r) { return Math.hypot(p.x - x, p.z - z) < r; }
    private boolean has(Unit p, int w) { return p.wep[0] == w || p.wep[1] == w; }
    private int slotFor(Unit p) { return p.wep[1] < 0 ? 1 : p.wep[0] < 0 ? 0 : p.slot; }
    private boolean pay(int cost) { if (points < cost) { b.ev(Battle.E_DENY, 0, 0, 0, 0); return false; } points -= cost; b.ev(Battle.E_BUY, cost, 0, 0, 0); return true; }

    @Override public boolean use(Unit p, boolean pressed) {
        if (p.state != Battle.S_FOOT) return false;
        City c = b.city;
        // windows: hold to rebuild
        for (City.Window w : c.windows) if (near(p, w.ix, w.iz, 2.6f)) {
            int missing = -1; for (int pi : w.planks) if (c.boxes.get(pi).dead) { missing = pi; break; }
            if (missing >= 0) {
                rebuildT += Battle.DT;
                if (rebuildT > 0.7f) { rebuildT = 0; c.revive(missing); b.ev(Battle.E_REBUILD, 0, w.ix, 1.5f, w.iz); if (rebuildPts < 500) { add(10); rebuildPts += 10; } }
                return true;
            }
        }
        for (City.Gate g : c.gates) if (!c.boxes.get(g.box).dead && near(p, g.x, g.z, 3.4f)) {
            if (pressed && pay(g.cost)) { c.kill(g.box); zoneOpen[g.zoneA] = true; zoneOpen[g.zoneB] = true; b.radio("The way to " + g.name + " is open."); }
            return true;
        }
        for (City.Spot s : c.wallBuys) if (near(p, s.x, s.z, 2.3f)) {
            if (!pressed) return true;
            if (has(p, s.id)) { int sl = p.wep[0] == s.id ? 0 : 1; if (pay(s.cost / 2)) { p.reserve[sl] = (int) (Battle.WEAPONS[s.id].reserve * (p.papd[sl] ? 2 : 1)); } }
            else if (pay(s.cost)) b.give(p, slotFor(p), s.id);
            return true;
        }
        City.Spot bs = c.boxSpots.get(box);
        if (near(p, bs.x, bs.z, 2.2f) && boxState != 3) {
            if (!pressed) return true;
            if (boxState == 0 && pay(BOX_COST)) { boxState = 1; boxT = 3.2f; boxUses++; b.ev(Battle.E_BOX, 0, bs.x, 1, bs.z); }
            else if (boxState == 2) { b.give(p, slotFor(p), boxWeapon); boxState = 0; b.ev(Battle.E_PICKUP, 0, bs.x, 1, bs.z); }
            return true;
        }
        if (c.pap != null && near(p, c.pap.x, c.pap.z, 2.4f)) {
            if (!pressed) return true;
            if (!power) { b.radio("The Armorer's bench needs power. Find the generator."); return true; }
            if (papState == 0 && p.wep[p.slot] >= 0 && !p.papd[p.slot] && pay(PAP_COST)) { papState = 1; papT = 3; papSlot = p.slot; papWeapon = p.wep[p.slot]; p.wep[p.slot] = -1; if (p.wep[1 - p.slot] >= 0) p.slot = 1 - p.slot; b.ev(Battle.E_PAP, 0, c.pap.x, 1, c.pap.z); }
            else if (papState == 2) {
                int sl = p.wep[papSlot] < 0 ? papSlot : slotFor(p);
                p.wep[sl] = papWeapon; p.papd[sl] = true; p.slot = sl; p.mag[sl] = b.magSize(p, sl); p.reserve[sl] = Battle.WEAPONS[papWeapon].reserve * 2; papState = 0;
                b.ev(Battle.E_PAP, 1, c.pap.x, 1, c.pap.z); b.radio(Battle.WEAPONS[papWeapon].name + " is now " + Battle.papName(papWeapon) + ".");
            }
            return true;
        }
        for (City.Spot s : c.perkSpots) if (near(p, s.x, s.z, 2.0f)) {
            if (!pressed) return true;
            int bit = PERK_BITS[s.id];
            if (!power && s.id != 3) { b.radio(s.name + " needs power."); return true; }
            if (p.has(bit)) return true;
            if (pay(s.cost)) { p.perks |= bit; if (bit == Battle.P_ARMOR) { p.maxHp = 250; p.hp = 250; } b.ev(Battle.E_PERK, s.id, s.x, 1, s.z); }
            return true;
        }
        if (!power && near(p, c.generator.x, c.generator.z, 2.4f)) {
            genT += Battle.DT;
            if (genT > 1.5f) { power = true; b.ev(Battle.E_POWER, 0, c.generator.x, 1, c.generator.z); b.radio("The generator coughs and catches. Power's on."); }
            return true;
        }
        for (int i = 0; i < c.radioParts.size(); i++) { City.Spot s = c.radioParts.get(i); if (!parts[i] && Battle.dist3(p.x, p.y + 1, p.z, s.x, s.y + 0.4f, s.z) < 2.4f) { if (pressed) { parts[i] = true; b.ev(Battle.E_EGG, 1 + i, s.x, s.y, s.z); b.radio("A radio part. " + count(parts) + " of 3."); } return true; } }
        if (near(p, c.radio.x, c.radio.z, 2.0f) && !radioUsed && count(parts) == 3) {
            radioT += Battle.DT;
            if (radioT > 2) { radioUsed = true; gunshipT = 75; b.ev(Battle.E_EGG, 10, c.radio.x, 1, c.radio.z); b.radio("Spectre 1-1 on station. You asked for us in September. Sorry we're late."); }
            return true;
        }
        return false;
    }
    private static int count(boolean[] a) { int n = 0; for (boolean v : a) if (v) n++; return n; }
    @Override public String prompt(Unit p) {
        if (p.state != Battle.S_FOOT) return null;
        City c = b.city;
        for (City.Window w : c.windows) if (near(p, w.ix, w.iz, 2.6f)) { for (int pi : w.planks) if (c.boxes.get(pi).dead) return "Hold E  Rebuild the boards"; }
        for (City.Gate g : c.gates) if (!c.boxes.get(g.box).dead && near(p, g.x, g.z, 3.4f)) return "E  Open the gate to " + g.name + "  [" + g.cost + "]";
        for (City.Spot s : c.wallBuys) if (near(p, s.x, s.z, 2.3f)) return has(p, s.id) ? "E  " + s.name + " ammo  [" + s.cost / 2 + "]" : "E  Buy " + s.name + "  [" + s.cost + "]";
        City.Spot bs = c.boxSpots.get(box);
        if (near(p, bs.x, bs.z, 2.2f)) return boxState == 0 ? "E  Weapons crate  [" + BOX_COST + "]" : boxState == 1 ? "..." : boxState == 2 ? "E  Take the " + Battle.WEAPONS[boxWeapon].name : null;
        if (c.pap != null && near(p, c.pap.x, c.pap.z, 2.4f)) return !power ? "Needs power" : papState == 0 ? "E  Armorer's bench: upgrade your gun  [" + PAP_COST + "]" : papState == 1 ? "The armorer is working..." : "E  Take " + Battle.papName(papWeapon);
        for (City.Spot s : c.perkSpots) if (near(p, s.x, s.z, 2.0f)) return p.has(PERK_BITS[s.id]) ? s.name + " (owned)" : (!power && s.id != 3) ? s.name + " needs power" : "E  " + s.name + "  [" + s.cost + "]  " + perkBlurb(s.id);
        if (!power && near(p, c.generator.x, c.generator.z, 2.4f)) return "Hold E  Start the generator";
        for (int i = 0; i < c.radioParts.size(); i++) { City.Spot s = c.radioParts.get(i); if (!parts[i] && Battle.dist3(p.x, p.y + 1, p.z, s.x, s.y + 0.4f, s.z) < 2.4f) return "E  Pick up the strange radio part"; }
        if (near(p, c.radio.x, c.radio.z, 2.0f) && !radioUsed) return count(parts) == 3 ? "Hold E  Call Spectre" : "An old field radio. It's missing parts.";
        return null;
    }
    static String perkBlurb(int id) { return id == 0 ? "(250 health)" : id == 1 ? "(reload twice as fast)" : id == 2 ? "(fire 33% faster)" : id == 3 ? "(get back up once)" : "(move faster)"; }
    @Override public String hud() {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 5; i++) if (b.player.has(PERK_BITS[i])) s.append("   [").append(PERKS[i]).append("]");
        if (instaT > 0) s.append("   INSTA-KILL ").append((int) instaT);
        if (doubleT > 0) s.append("   DOUBLE POINTS ").append((int) doubleT);
        if (gunshipT > 0) s.append("   SPECTRE ").append((int) gunshipT);
        return s.length() == 0 ? null : s.toString().trim();
    }
    @Override public List<float[]> markers() {
        ArrayList<float[]> m = new ArrayList<>(); City c = b.city;
        City.Spot bs = c.boxSpots.get(box); m.add(new float[]{bs.x, bs.z, 4});
        if (c.pap != null) m.add(new float[]{c.pap.x, c.pap.z, 5});
        for (City.Spot s : c.perkSpots) m.add(new float[]{s.x, s.z, 2});
        for (float[] pu : powerups) m.add(new float[]{pu[0], pu[1], 6});
        return m;
    }
}
