package com.saedarm.irene;

import com.saedarm.irene.Battle.*;

/**
 * Story mode: nine chapters following the historical sequence. Each chapter has checkpoint stages;
 * dying offers "retry from checkpoint", which rebuilds the chapter at that stage.
 */
public final class Story extends Mode {
    public static final String[] NAMES = {"Wrong House", "Atto's Car", "Irene", "Super Six-One Down", "The Lost Convoy", "Super Six-Four", "The Long Night", "The Mogadishu Mile", "The Other Side"};
    public static final String[] DATES = {"30 August 1993, 03:09", "21 September 1993", "3 October, 15:42", "3 October, 16:20", "3 October, 16:35", "3 October, 16:40", "3-4 October, overnight", "4 October, 05:30", "3 October, Bakara Market"};
    public static final String[] BLURB = {
            "Task Force Ranger's first raid. A night assault on a house the intel says is an Aidid safe house. Breach it, clear it, secure the people inside.",
            "Aidid's financier is driving across the city. Ride a Little Bird's door gun, stop the car by shooting the engine block, then rope down and grab him before the militia arrives.",
            "The big one. Ride Super 6-7 in, fast-rope onto the street, clear the courtyard, breach the target house and secure the detainees.",
            "Super 6-1 is hit by an RPG and goes down a few blocks north-east. Get there before the militia does, and hold until the rescue team and the column can reach you.",
            "Drive a Humvee with the wounded back to the airfield. Every intersection has burning tires and an ambush, and RPGs go after vehicles.",
            "Super 6-4 is down in the tin shacks of Wadigley. Two of you hold the wreck through five waves until a Little Bird can get in.",
            "Night at Crash Site 1. Hold the perimeter, grab the supplies Super 6-6 drops, and last until the relief column links up at 01:55.",
            "Dawn. The vehicles are full, so everyone else runs. Get from the crash site to the rally point on National Street under fire from every roof.",
            "Play the militia. The tires are lit to call fighters in. Bring down two Black Hawks with RPGs while Rangers rope in on top of you."};
    public static final boolean[] NIGHT = {true, false, false, false, false, false, true, false, false};
    public static final boolean[] SNA = {false, false, false, false, false, false, false, false, true};
    /** Stages that are checkpoints, per chapter. */
    public static final int[][] CHECKPOINTS = {{0, 2}, {0, 1}, {0, 1, 2}, {0, 1}, {0, 1, 2}, {0, 1}, {0, 2}, {0, 1}, {0}};

    public final int ch; private final int startStage;
    public int stage, cp; private float stageT;
    private City.House house; private Unit atto; private Vehicle car, hv; private int hvTicks;
    private boolean scripted;
    private final float[][] convoy = {{250, 432}, {120, 560}, {40, 616}};

    public Story(int chapter, int startStage) { this.ch = chapter; this.startStage = startStage; }
    @Override public String title() { return NAMES[ch]; }
    @Override public boolean defaultNight() { return NIGHT[ch]; }
    @Override public int checkpoint() { return cp; }

    // ------------------------------------------------------------------ helpers
    private void squad(int n, float x, float z) { for (int i = 0; i < n; i++) { Unit s = b.spawn(b.playerTeam, b.playerTeam == Battle.US ? i % 4 : 4 + i % 3, 0, 0, 0); b.placeSafe(s, x + (i % 3) * 1.5f - 1.5f, z + 2 + (i / 3) * 1.5f); } }
    private void stage(int s) {
        stage = s; stageT = 0;
        for (int c : CHECKPOINTS[ch]) if (c == s && s > cp) { cp = s; b.ev(Battle.E_CHECKPOINT, s, b.player.x, 0, b.player.z); }
    }
    private int houseAlive(City.House h) { int hi = b.city.houses.indexOf(h), n = 0; for (Unit u : b.units) if (u.alive && u.house == hi) n++; return n; }
    private float[] doorOut(City.House h) { return new float[]{h.doorX + h.nx * 1.4f, h.doorZ + h.nz * 1.4f}; }
    private float[] center(City.House h) { return new float[]{(h.x0 + h.x1) / 2, (h.z0 + h.z1) / 2}; }
    private boolean inside(City.House h) { Unit p = b.player; return p.x > h.x0 && p.x < h.x1 && p.z > h.z0 && p.z < h.z1; }
    private City.House pickHouse(float x, float z) {
        City.House best = null; float bd = 1e9f;
        for (City.House h : b.city.houses) {
            if (h.open() || b.city.inArena(h.doorX, h.doorZ) || h == b.city.targetHouse) continue;
            int oc = b.city.cellOf(h.doorX + h.nx * 2, h.doorZ + h.nz * 2);
            if (!b.city.connected[oc]) continue;
            float d = (float) Math.hypot(h.doorX - x, h.doorZ - z);
            if (d < bd) { bd = d; best = h; }
        }
        return best;
    }
    private void wreckAt(String call, float x, float z) { Heli w = b.heli(Battle.BLACKHAWK, call, x, 0, z); w.state = Battle.H_DOWN; w.hp = 0; w.yaw = 1.2f; w.y = b.city.groundAt(x, z, 1.5f, 3) + 1.2f; }

    // ------------------------------------------------------------------ setup = begin at the chosen stage
    @Override public void setup() {
        if (SNA[ch] && b.playerTeam != Battle.SNA) b.setPlayerTeam(Battle.SNA, 4);
        if (!SNA[ch] && b.playerTeam != Battle.US) b.setPlayerTeam(Battle.US, 0);
        cp = startStage;
        begin(startStage);
    }
    private void begin(int s) {
        stage = s; stageT = 0;
        float[] tgt = b.city.marks.get("Target"), cs1 = b.city.marks.get("Crash Site 1"), cs2 = b.city.marks.get("Crash Site 2"), bak = b.city.marks.get("Bakara Market");
        switch (ch) {
            case 0: { // Wrong House
                house = pickHouse(200, 200);
                float[] o = doorOut(house);
                if (s == 0) {
                    b.placeSafe(b.player, house.doorX + house.nx * 12, house.doorZ + house.nz * 12); squad(3, house.doorX + house.nx * 14, house.doorZ + house.nz * 14);
                    b.garrison(house, 3 + b.difficulty); for (int i = 0; i < 3; i++) b.guard(o[0] + house.nx * 10, o[1] + house.nz * 10, 14);
                    face(house.doorX, house.doorZ);
                    b.setObj("Breach the house. Tap E at the door to kick it, hold E to plant a charge.", o[0], o[1]);
                    b.radio("Juliet, Kilo: this is the house. Quiet approach.");
                } else {
                    b.openDoor(house); float[] c = center(house);
                    b.placeSafe(b.player, c[0], c[1]); squad(3, o[0], o[1]);
                    b.setObj("Secure the detainees: hold E in the middle of the house.", c[0], c[1]);
                }
                break;
            }
            case 1: { // Atto's Car
                float[] start = {440, 380}, dest = {170, 300};
                if (s == 0) {
                    car = b.vehicle(Battle.V_CAR, start[0], start[1], 1.57f, Battle.SNA); car.route = b.fieldTo(dest[0], dest[1]); car.destX = dest[0]; car.destZ = dest[1]; car.cruise = 10.5f;
                    int ci = b.walkCell(start[0], start[1]); car.x = (ci % City.N) * City.CELL + 2; car.z = (ci / City.N) * City.CELL + 2;
                    atto = b.spawn(Battle.SNA, 4, 0, 0, 0); atto.objectiveTarget = true; atto.mag[0] = atto.reserve[0] = 0; atto.mag[1] = atto.reserve[1] = 0; atto.role = Ai.R_PERCH;
                    b.enterVehicle(atto, car);
                    Heli lb = b.heli(Battle.LITTLEBIRD, "Star 4-1", car.x + 40, 22, car.z + 20); lb.state = Battle.H_FOLLOW; lb.follow = car.id; lb.ty = 20; lb.guns = false;
                    b.board(b.player, lb); for (int i = 0; i < 2; i++) b.board(b.spawn(Battle.US, 2, 0, 0, 0), lb);
                    b.input.yaw = 0;
                    b.setObj("Stop the car: shoot the engine block (the front of the car) from the door.", car.x, car.z);
                    b.radio("Beacon's in his cane. That's his car. Stop it, don't kill him.");
                } else {
                    car = b.vehicle(Battle.V_CAR, 330, 300, 1.57f, Battle.SNA); int ci = b.walkCell(330, 300); car.x = (ci % City.N) * City.CELL + 2; car.z = (ci / City.N) * City.CELL + 2; car.disabled = true; car.hp = 0;
                    atto = b.spawn(Battle.SNA, 4, 0, 0, 0); atto.objectiveTarget = true; atto.mag[0] = atto.reserve[0] = 0; atto.mag[1] = atto.reserve[1] = 0; atto.role = Ai.R_PERCH; atto.perch = true;
                    b.place(atto, car.x + 2.5f, car.z);
                    b.placeSafe(b.player, car.x - 8, car.z + 6); squad(2, car.x - 9, car.z + 7);
                    Heli lb = b.heli(Battle.LITTLEBIRD, "Star 4-1", car.x, 30, car.z); b.orbit(lb, car.x, car.z, 60, 28);
                    stage1Atto();
                }
                break;
            }
            case 2: { // Irene
                house = b.city.targetHouse;
                Heli six1 = b.heli(Battle.BLACKHAWK, "Super 6-1", tgt[0], 45, tgt[1]); b.orbit(six1, tgt[0], tgt[1], 95, 45);
                Heli lb = b.heli(Battle.LITTLEBIRD, "Star 4-1", tgt[0], 30, tgt[1]); b.orbit(lb, tgt[0], tgt[1], 70, 30); lb.orbitA = 2;
                b.perches(tgt[0], tgt[1], 80, 4);
                b.garrison(house, 4 + b.difficulty);
                if (s == 0) {
                    Heli h = b.heli(Battle.BLACKHAWK, "Super 6-7", 120, 40, 600);
                    b.go(h, tgt[0], 20, tgt[1] + 18, Battle.P_INSERT);
                    b.board(b.player, h); for (int i = 0; i < 3; i++) b.board(b.spawn(Battle.US, i % 4, 0, 0, 0), h);
                    b.input.yaw = h.yaw - 1.57f;
                    b.setObj("Fast-rope onto the street in front of the target. Press E in the hover.", tgt[0], tgt[1] + 18);
                    b.radio("Irene. Irene. All Juliet elements, the target is go.");
                } else {
                    b.placeSafe(b.player, tgt[0], tgt[1] + 16); squad(3, tgt[0] + 2, tgt[1] + 18);
                    b.input.yaw = 0;
                    if (s == 1) { courtyardGuards(); b.setObj("Clear the courtyard.", tgt[0], tgt[1] + 4); }
                    else { float[] o = doorOut(house); b.placeSafe(b.player, o[0], o[1] + 2); b.setObj("Breach the target house. Tap E to kick, hold E to plant a charge.", o[0], o[1]); }
                }
                break;
            }
            case 3: { // Super Six-One Down
                if (s == 0) {
                    b.placeSafe(b.player, tgt[0], tgt[1] + 14); squad(3, tgt[0] + 2, tgt[1] + 16); b.input.yaw = -0.8f;
                    Heli six1 = b.heli(Battle.BLACKHAWK, "Super 6-1", cs1[0] - 40, 30, cs1[1] + 20); b.go(six1, cs1[0], 24, cs1[1], Battle.P_NONE); six1.crashX = cs1[0]; six1.crashZ = cs1[1];
                    Heli six8 = b.heli(Battle.BLACKHAWK, "Super 6-8", tgt[0], 50, tgt[1]); b.orbit(six8, tgt[0], tgt[1], 130, 55);
                    b.setObj("Super 6-1 is flying cover over the target.", cs1[0], cs1[1]);
                    b.wavesOn = true; b.waveTimer = 0; b.waveInterval = 34;
                } else {
                    wreckAt("Super 6-1", cs1[0], cs1[1]);
                    b.placeSafe(b.player, cs1[0] - 6, cs1[1] + 3); squad(6, cs1[0] - 8, cs1[1] + 2);
                    b.setObj("Hold the crash site until the column can reach you.", cs1[0], cs1[1]); b.countdown = 120;
                    b.wavesOn = true; b.waveTimer = b.waveInterval - 6; b.waveInterval = 30; b.wave = 2;
                }
                break;
            }
            case 4: { // Lost Convoy
                float[] from = s == 0 ? new float[]{tgt[0] + 6, tgt[1] + 18} : convoy[s - 1];
                int ci = b.walkCell(from[0], from[1]);
                hv = b.vehicle(Battle.V_HUMVEE, (ci % City.N) * City.CELL + 2, (ci / City.N) * City.CELL + 2, s == 0 ? 3.14f : 2.4f, Battle.US);
                b.placeSafe(b.player, hv.x + 3, hv.z); squad(3, hv.x + 3, hv.z + 2);
                b.enterVehicle(b.player, hv);
                float[] w = convoy[s];
                b.setObj(s == 0 ? "Drive the wounded south to National Street." : s == 1 ? "Through the K-4 traffic circle." : "Get to the airfield.", w[0], w[1]);
                b.radio(s == 0 ? "Uniform 6-4, take the next left... no, the next one!" : "Keep moving, don't stop in the kill zone.");
                ambush(w[0], w[1]);
                break;
            }
            case 5: { // Super Six-Four
                wreckAt("Super 6-4", cs2[0], cs2[1]);
                b.placeSafe(b.player, cs2[0] + 4, cs2[1] + 3); squad(1, cs2[0] - 3, cs2[1] + 2);
                Heli lb = b.heli(Battle.LITTLEBIRD, "Star 4-2", cs2[0], 40, cs2[1]); b.orbit(lb, cs2[0], cs2[1], 90, 40);
                b.setObj("Protect the crash site. Hold out for 5 waves.", cs2[0], cs2[1]);
                b.wavesOn = true; b.waveTimer = b.waveInterval - 8; b.waveInterval = 34; b.wave = s == 1 ? 3 : 0;
                b.radio("Super Six-Four is down. Nobody else can get there; you're it.");
                break;
            }
            case 6: { // Long Night
                wreckAt("Super 6-1", cs1[0], cs1[1]);
                b.placeSafe(b.player, cs1[0] - 5, cs1[1] + 2); squad(5, cs1[0] - 8, cs1[1] + 3);
                b.wavesOn = true; b.waveInterval = 35;
                if (s == 0) { b.setObj("Hold the perimeter. Super 6-6 is bringing water and ammo.", cs1[0], cs1[1]); b.waveTimer = b.waveInterval - 6; }
                else { b.wave = 3; b.countdown = 150; b.setObj("Hold until the relief column links up (01:55).", cs1[0], cs1[1]); b.waveTimer = b.waveInterval - 4; }
                break;
            }
            case 7: { // Mile
                float[] from = s == 0 ? cs1 : new float[]{300, 330};
                b.placeSafe(b.player, from[0] - 4, from[1] + 2); squad(5, from[0] - 6, from[1] + 4);
                b.perches(300, 330, 110, 6 + b.difficulty);
                b.spawnMilitia(5 + 2 * b.difficulty, s == 0 ? 330 : 306, s == 0 ? 270 : 390, 10, 40, false);
                b.setObj(s == 0 ? "Run south to the Olympic Hotel." : "Keep going: the rally point on National Street.", s == 0 ? 300 : 312, s == 0 ? 330 : 432);
                b.wavesOn = true; b.waveInterval = 30; b.waveTimer = 8;
                for (int i = 0; i < 2; i++) { Heli lb = b.heli(Battle.LITTLEBIRD, "Star 4-" + (5 + i), 330, 30, 300); b.orbit(lb, 320, 330, 60 + 30 * i, 26); lb.orbitA = i * 3; }
                b.radio(s == 0 ? "Vehicles are full. Everybody else runs. Go, go!" : "Halfway. Don't stop.");
                break;
            }
            default: { // The Other Side (militia)
                b.placeSafe(b.player, bak[0] + 20, bak[1] - 30);
                squad(3, bak[0] + 16, bak[1] - 26);
                for (int i = 0; i < 3; i++) { Heli h = b.heli(Battle.BLACKHAWK, "Super 6-" + (2 + i), bak[0], 45, bak[1]); b.orbit(h, bak[0] + 40, bak[1] - 30, 110 + i * 25, 42 + i * 6); h.orbitA = i * 2.1f; }
                Heli lb = b.heli(Battle.LITTLEBIRD, "Star 4-4", bak[0], 30, bak[1]); b.orbit(lb, bak[0] + 40, bak[1] - 30, 75, 28);
                b.setObj("Bring down 2 helicopters (RPG). Refill rockets at ammo crates (E).", bak[0] + 40, bak[1] - 30);
                b.wavesOn = true; b.waveTimer = b.waveInterval - 12; b.waveInterval = 38;
                b.radio("The tires are lit. The whole city is coming.");
            }
        }
    }
    private void face(float x, float z) { b.input.yaw = (float) Math.atan2(-(x - b.player.x), -(z - b.player.z)); b.player.yaw = b.input.yaw; }
    private void courtyardGuards() { float[] tgt = b.city.marks.get("Target"); for (int i = 0; i < 5 + 2 * b.difficulty; i++) b.guard(tgt[0], tgt[1] + 4, 14); }
    private void ambush(float x, float z) {
        b.spawnMilitia(6 + 2 * b.difficulty, x, z, 15, 45, true);
        b.perches(x, z, 45, 2 + b.difficulty);
        int rpg = 0; for (Unit u : b.units) if (u.alive && u.team == Battle.SNA && Math.hypot(u.x - x, u.z - z) < 50 && rpg < 2) { equipRpg(u); rpg++; }
    }
    private void equipRpg(Unit u) { u.kit = 5; u.wep[1] = Battle.W_RPG; u.mag[1] = 1; u.reserve[1] = 3; }
    private void stage1Atto() {
        stage(1);
        b.setObj("Grab him: hold E next to Atto.", atto.x, atto.z);
        b.spawnMilitia(6 + 2 * b.difficulty, atto.x, atto.z, 40, 90, true);
        b.radio("Car's dead. Put us down, grab the package.");
    }

    // ------------------------------------------------------------------ script
    @Override public void tick() {
        stageT += Battle.DT;
        float[] tgt = b.city.marks.get("Target"), cs1 = b.city.marks.get("Crash Site 1");
        switch (ch) {
            case 0:
                if (stage == 0 && house.open()) { stage(1); float[] c = center(house); b.setObj("Clear the house.", c[0], c[1]); }
                else if (stage == 1 && houseAlive(house) == 0) { stage(2); float[] c = center(house); b.setObj("Secure the detainees: hold E in the middle of the house.", c[0], c[1]); }
                else if (stage == 2) { b.objProgress = Math.min(1, b.holdT / 3f); if (b.holdT >= 3) { stage(3); b.radio("Hold on... these are UN employees. Wrong house. Pack it up."); b.setObj("Call extraction (H) and climb the rope.", b.player.x, b.player.z); } }
                break;
            case 1:
                if (stage == 0) {
                    if (car != null) { b.objX = car.x; b.objZ = car.z; }
                    if (car.disabled) {
                        b.exitVehicle(atto); b.placeSafe(atto, atto.x, atto.z); atto.perch = true;
                        Heli lb = b.findHeli("Star 4-1"); lb.state = Battle.H_ORBIT; lb.follow = -1; lb.guns = true; b.go(lb, car.x - 10, b.hoverAlt(car.x - 10, car.z), car.z + 6, Battle.P_INSERT);
                        stage1Atto();
                    } else if (Math.hypot(car.x - car.destX, car.z - car.destZ) < 12) b.fail("The car got away into Bakara Market.");
                } else if (stage == 1) {
                    b.objX = atto.x; b.objZ = atto.z;
                    if (!atto.alive) b.fail("Atto is dead. He was the whole point.");
                    else if (b.holdT >= 1.5f || (Math.hypot(atto.x - b.player.x, atto.z - b.player.z) < 6f && b.input.use && b.player.state == Battle.S_FOOT)) { stage(2); atto.team = Battle.US; atto.alive = false; atto.deadT = 99; b.radio("Package secured."); b.setObj("Call extraction (H) and climb the rope.", b.player.x, b.player.z); }
                    b.objProgress = Math.min(1, b.holdT / 1.5f);
                }
                break;
            case 2:
                if (stage == 0 && b.player.state == Battle.S_FOOT) { stage(1); courtyardGuards(); b.setObj("Clear the courtyard.", tgt[0], tgt[1] + 4); b.radio("Chalks on the ground. Delta's going in."); }
                else if (stage == 1) { int n = 0; for (Unit u : b.units) if (u.alive && u.guard && u.house < 0 && !u.perch && u.team == b.hostileTeam && Math.hypot(u.x - tgt[0], u.z - tgt[1]) < 30) n++; if (n == 0 || stageT > 75) { stage(2); float[] o = doorOut(b.city.targetHouse); b.setObj("Breach the target house. Tap E to kick, hold E to plant a charge.", o[0], o[1]); } }
                else if (stage == 2 && b.city.targetHouse.open()) { stage(3); float[] c = center(b.city.targetHouse); b.setObj("Clear the house.", c[0], c[1]); }
                else if (stage == 3 && houseAlive(b.city.targetHouse) == 0) { stage(4); float[] c = center(b.city.targetHouse); b.setObj("Secure the detainees: hold E inside.", c[0], c[1]); }
                else if (stage == 4) { b.objProgress = Math.min(1, b.holdT / 3f); if (b.holdT >= 3) b.win("Detainees secured. Then the radio: \"We got a Black Hawk going down.\""); }
                break;
            case 3: {
                Heli six1 = b.findHeli("Super 6-1");
                if (stage == 0) {
                    if (six1 != null && six1.flying() && stageT > 4 && !scripted && Math.hypot(six1.x - cs1[0], six1.z - cs1[1]) < 30) {
                        scripted = true;
                        Unit shooter = b.spawn(Battle.SNA, 5, 0, 0, 0); b.place(shooter, cs1[0] - 34, cs1[1] + 8);
                        Proj p = new Proj(); p.kind = Battle.PR_ROCKET; p.rocket = true; p.team = Battle.SNA; p.owner = shooter.id; p.x = shooter.x; p.y = 1.6f; p.z = shooter.z;
                        float dx = six1.x - p.x, dy = six1.y - p.y, dz = six1.z - p.z, l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                        p.vx = dx / l * Battle.ROCKET_SPEED; p.vy = dy / l * Battle.ROCKET_SPEED; p.vz = dz / l * Battle.ROCKET_SPEED; b.projs.add(p); b.ev(Battle.E_ROCKET, shooter.id, p.x, p.y, p.z);
                        six1.hp = 1;
                    }
                    if (six1 != null && six1.flying() && stageT > 14) b.downHeli(six1, null);
                    if (six1 != null && !six1.flying() && !b.objective.startsWith("Super 6-1 is down")) {
                        b.setObj("Super 6-1 is down! Get to the crash site.", cs1[0], cs1[1]);
                        Heli six8 = b.findHeli("Super 6-8");
                        if (six8 != null && six8.flying()) { b.go(six8, cs1[0] - 14, b.hoverAlt(cs1[0] - 14, cs1[1]), cs1[1], Battle.P_INSERT); for (int i = 0; i < 4; i++) b.board(b.spawn(Battle.US, i == 0 ? 3 : i % 3, 0, 0, 0), six8); b.radio("Super 6-8 is putting the rescue team in on the crash."); }
                    }
                    if (six1 != null && six1.state == Battle.H_DOWN && b.near(cs1[0], cs1[1], 30)) { stage(1); b.countdown = 120; b.setObj("Hold the crash site until the column can reach you.", cs1[0], cs1[1]); b.waveInterval = 30; b.radio("We're staying with the bird. Set a perimeter."); }
                } else { b.countdown -= Battle.DT; if (b.countdown <= 0) b.win("You held Crash Site 1. Nobody is getting out tonight, though."); }
                break;
            }
            case 4: {
                if (b.player.veh >= 0) hvTicks = 0; else hvTicks++;
                if (hv != null && !hv.alive && b.player.state == Battle.S_FOOT && stageT > 1) {
                    // Humvee lost: grab another one nearby, like the convoy did
                    hv = null; float[] w = convoy[stage];
                    int ci = b.walkCell(b.player.x + 8, b.player.z + 8);
                    Vehicle n = b.vehicle(Battle.V_HUMVEE, (ci % City.N) * City.CELL + 2, (ci / City.N) * City.CELL + 2, b.player.yaw, Battle.US); hv = n;
                    b.radio("Our Humvee's gone. Get in the five-ton... the other Humvee, now!");
                }
                float[] w = convoy[stage];
                if (Math.hypot(b.player.x - w[0], b.player.z - w[1]) < 18) {
                    if (stage == 2) { b.win("The convoy limps into the airfield. Half of them are wounded."); break; }
                    stage(stage + 1); float[] nw = convoy[stage];
                    b.setObj(stage == 1 ? "Through the K-4 traffic circle." : "Get to the airfield.", nw[0], nw[1]);
                    ambush(nw[0], nw[1]);
                }
                break;
            }
            case 5:
                if (b.wave >= 3 && stage == 0) stage(1);
                if (b.wave >= 5) b.wavesOn = false;
                if (stage <= 1 && b.wave >= 5 && (b.aliveHostiles() <= 2 || b.waveTimer > 75)) { stage(2); b.setObj("Star 4-2 can get in. Call it (H) and climb the rope (E).", b.player.x, b.player.z); b.radio("Star Four-Two, we have a hole. Coming to you."); }
                else if (stage <= 1 && b.wave >= 5) b.objective = "Clear the last wave (" + b.aliveHostiles() + " left).";
                if (stage <= 1 && b.wave >= 5) b.waveTimer += Battle.DT;
                break;
            case 6:
                if (stage == 0 && b.wave >= 2 && stageT > 40) {
                    stage(1);
                    for (int i = 0; i < 3; i++) { float x = cs1[0] - 10 + i * 9, z = cs1[1] + 6 - i * 4; b.city.crates.add(new float[]{x, z}); }
                    b.setObj("Super 6-6 dropped water and ammo. Get to the crates (E).", cs1[0] - 1, cs1[1] + 2);
                    b.radio("Super 6-6, kicking out the bundles now!");
                } else if (stage == 1 && stageT > 25) { stage(2); b.countdown = 150; b.setObj("Hold until the relief column links up (01:55).", cs1[0], cs1[1]); }
                else if (stage == 2) { b.countdown -= Battle.DT; if (b.countdown <= 0) b.win("01:55. The column is here. Now everyone has to get out."); }
                break;
            case 7:
                if (stage == 0 && b.near(300, 330, 18)) { stage(1); b.setObj("Keep going: the rally point on National Street.", 312, 432); b.radio("Halfway. Don't stop."); }
                if (b.near(312, 432, 10)) b.win("You made the Mogadishu Mile. 06:30, Pakistani Stadium.");
                break;
            default:
                if (b.helisDowned < 2 && stage == 0) b.objective = "Bring down 2 helicopters (" + b.helisDowned + "/2). Refill rockets at ammo crates (E).";
                if (stage == 0 && b.helisDowned >= 2) { stage(1); b.setObj("Two birds down. Survive this wave and melt away.", b.player.x, b.player.z); b.waveTimer = b.waveInterval - 3; }
                else if (stage == 1 && stageT > 6 && b.aliveHostiles() == 0) b.win("Two Black Hawks down. The Americans will remember this street.");
        }
    }
    @Override public boolean pickupAllowed() { return (ch == 0 && stage >= 3) || (ch == 1 && stage >= 2) || (ch == 5 && stage >= 2); }
    @Override public void onBoard(Heli h) {
        if (pickupAllowed()) b.win(ch == 0 ? "Out before dawn. The papers will have fun with this one." : ch == 1 ? "Osman Atto is in custody." : "Out of Wadigley alive.");
        else b.orbit(h, h.x, h.z, 120, 45);
    }
}
