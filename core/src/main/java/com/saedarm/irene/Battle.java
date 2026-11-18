package com.saedarm.irene;

import java.util.ArrayList;
import java.util.Random;

/**
 * The rules engine. No gdx imports: it never draws or plays sound. Things that happen are pushed to
 * {@link #events} and the screen turns them into sound, blood, tracers and HUD feedback.
 * Fixed tick {@link #DT}. Modes (Story, FreeRoam, Siege) plug in through {@link Mode}.
 * The AI lives in {@link Ai}.
 */
public final class Battle {
    public static final float DT = 1 / 60f;

    // ---- movement feel ----
    public static final float GRAV = 24f, WALK = 5.2f, SPRINT = 8.4f, CROUCH_SPD = 2.8f, ADS_MUL = 0.62f;
    public static final float SLIDE_SPD = 12.5f, SLIDE_TIME = 0.75f, JUMP = 7.4f, STEP = 0.55f;
    public static final float MANTLE_MAX = 2.6f, MANTLE_TIME = 0.30f, RAD = 0.38f;
    public static final float H_STAND = 1.8f, H_CROUCH = 1.15f, EYE_STAND = 1.64f, EYE_CROUCH = 1.02f;
    public static final float ROPE_DOWN = 7.5f, ROPE_UP = 4.5f;
    public static final float REGEN_DELAY = 4.5f, REGEN_RATE = 30f;
    // ---- combat feel ----
    public static final float HEAD_MUL = 2.3f, LEG_MUL = 0.75f, ARMOR_MUL = 0.8f;
    public static final float ROCKET_SPEED = 95f, ROCKET_SPLASH = 6f, GRENADE_SPLASH = 7f, GRENADE_FUSE = 2.6f;
    public static final float HELI_RPG_DMG = 260f;
    public static final int MAX_HOSTILES = 42;
    /** Damage the player takes, by difficulty. The main "how hard is it" knob. */
    public static final float[] PLAYER_DMG = {0.25f, 0.45f, 0.7f};
    /** Seconds between enemy shots = weapon interval * this. */
    public static final float ENEMY_FIRE_MUL = 2.6f, ENEMY_SPREAD_ADD = 2.0f;
    /** Chance per AI think (about 4/s) that an RPG gunner commits to a helicopter, and his aim error in degrees. */
    public static final float AI_RPG_CHANCE = 0.03f, AI_RPG_SPREAD = 6f;
    // ---- close quarters ----
    public static final float MELEE_RANGE = 2.1f, MELEE_DMG = 135f, MELEE_CD = 0.65f;
    public static final float KICK_CHANCE = 0.65f, PLANT_TIME = 1.0f, CHARGE_FUSE = 1.5f, BREACH_STUN = 2.6f;
    public static final float FLASH_FUSE = 1.4f, FLASH_RAD = 13f, FLASH_STUN = 3.5f;
    public static final float SUPPRESS_RAD = 2.4f;
    // ---- vehicles ----
    public static final float V_ACCEL = 9f, V_MAX = 19f, V_REV = 6f, V_STEER = 1.9f, V_RUNOVER = 3.5f;
    // ---- sight ----
    public static final float SIGHT_DAY = 90f, SIGHT_NIGHT = 36f;

    public static final int US = 0, SNA = 1;

    // ---------------------------------------------------------------- weapons
    public static final class Weapon {
        public final String name; public final float dmg, interval, reload, hip, ads, recoil, range; public final int mag, reserve;
        public final boolean rocket; public int pellets = 1; public boolean lob, semi, melee;
        Weapon(String n, float dmg, float interval, float reload, float hip, float ads, float recoil, float range, int mag, int reserve, boolean rocket) {
            name = n; this.dmg = dmg; this.interval = interval; this.reload = reload; this.hip = hip; this.ads = ads; this.recoil = recoil; this.range = range; this.mag = mag; this.reserve = reserve; this.rocket = rocket;
            semi = rocket;
        }
        Weapon pellets(int p) { pellets = p; return this; }
        Weapon lob() { lob = true; semi = true; return this; }
        Weapon semi() { semi = true; return this; }
        Weapon melee() { melee = true; return this; }
    }
    public static final int W_CAR15 = 0, W_CAR15_AIM = 1, W_SAW = 2, W_M14 = 3, W_AK = 4, W_PKM = 5, W_RPG = 6, W_LAW = 7, W_MINIGUN = 8,
            W_M2 = 9, W_M9 = 10, W_M16 = 11, W_870 = 12, W_MP5 = 13, W_M79 = 14, W_M60 = 15, W_GOLD_AK = 16, W_HANDMINI = 17, W_AT4 = 18, W_MACHETE = 19;
    public static final Weapon[] WEAPONS = {
            new Weapon("CAR-15", 34, 0.075f, 2.2f, 2.4f, 0.55f, 0.9f, 260, 30, 210, false),
            new Weapon("CAR-15 / Aimpoint", 34, 0.075f, 1.8f, 2.0f, 0.25f, 0.75f, 260, 30, 240, false),
            new Weapon("M249 SAW", 30, 0.068f, 5.0f, 3.4f, 1.2f, 1.0f, 260, 100, 300, false),
            new Weapon("M14", 72, 0.22f, 2.6f, 2.8f, 0.1f, 2.8f, 400, 20, 100, false).semi(),
            new Weapon("AK-47", 38, 0.10f, 2.5f, 3.0f, 0.9f, 1.4f, 220, 30, 180, false),
            new Weapon("PKM", 36, 0.085f, 5.5f, 3.8f, 1.5f, 1.3f, 260, 100, 200, false),
            new Weapon("RPG-7", 240, 1.0f, 3.2f, 1.6f, 0.6f, 4f, 300, 1, 5, true),
            new Weapon("M72 LAW", 220, 1.0f, 2.0f, 1.6f, 0.6f, 4f, 300, 1, 1, true),
            new Weapon("M134 door gun", 22, 0.025f, 0, 1.6f, 1.6f, 0.12f, 300, 9999, 0, false),
            new Weapon("M2 .50 cal", 70, 0.11f, 0, 1.4f, 1.0f, 1.6f, 400, 9999, 0, false),
            new Weapon("M9 Beretta", 30, 0.16f, 1.5f, 2.2f, 0.7f, 1.2f, 120, 15, 60, false).semi(),
            new Weapon("M16A2", 36, 0.09f, 2.3f, 2.4f, 0.4f, 1.0f, 280, 30, 180, false),
            new Weapon("Remington 870", 22, 0.85f, 3.2f, 5.5f, 4.0f, 3.5f, 45, 7, 42, false).pellets(8).semi(),
            new Weapon("MP5", 28, 0.066f, 2.0f, 2.2f, 0.8f, 0.6f, 150, 30, 240, false),
            new Weapon("M79 Thumper", 260, 1.0f, 2.6f, 1.2f, 0.6f, 3f, 200, 1, 18, true).lob(),
            new Weapon("M60", 42, 0.1f, 6.0f, 3.6f, 1.4f, 1.5f, 280, 100, 300, false),
            new Weapon("Golden AK", 150, 0.08f, 1.8f, 1.6f, 0.3f, 0.8f, 300, 40, 400, false),
            new Weapon("The Door Gun", 60, 0.03f, 6.0f, 2.6f, 2.0f, 0.3f, 260, 300, 900, false),
            new Weapon("AT4", 400, 1.0f, 2.5f, 1.4f, 0.5f, 4f, 300, 1, 3, true),
            new Weapon("Machete", 45, 0.8f, 0, 0, 0, 0, 2.0f, 1, 0, false).melee(),
    };
    public static String papName(int w) {
        switch (w) {
            case W_M9: return "Mogadishu Mile"; case W_M16: return "Irene's Kiss"; case W_870: return "Black Sea"; case W_MP5: return "Super Six"; case W_AK: return "Bakara Bargain";
            case W_M14: return "Long Night"; case W_PKM: return "Burning Tire"; case W_M79: return "K-4 Circle"; case W_M60: return "Hawlwadig"; case W_SAW: return "Lost Convoy";
            case W_CAR15: case W_CAR15_AIM: return "Chalk Four"; case W_RPG: return "Tail Rotor"; case W_GOLD_AK: return "Solid Gold"; case W_HANDMINI: return "Gun Run"; case W_AT4: return "Danger Close";
            default: return WEAPONS[w].name + " II";
        }
    }

    // ---------------------------------------------------------------- operator kits
    public static final class Kit {
        public final String name, blurb; public final int team, primary, secondary, grenades; public final float hp, speed, regen; public final boolean armor;
        public int flashes, charges;
        Kit(String n, String b, int team, int p, int s, int g, float hp, float speed, float regen, boolean armor) { name = n; blurb = b; this.team = team; primary = p; secondary = s; grenades = g; this.hp = hp; this.speed = speed; this.regen = regen; this.armor = armor; flashes = team == US ? 2 : 0; charges = team == US ? 2 : 0; }
        Kit cq(int f, int c) { flashes = f; charges = c; return this; }
    }
    public static final Kit[] KITS = {
            new Kit("Ranger Rifleman", "CAR-15, M72 LAW, 4 frags, 2 flashbangs, 2 breaching charges. Body armour.", US, W_CAR15, W_LAW, 4, 100, 1.0f, 1.0f, true),
            new Kit("Ranger SAW Gunner", "M249 SAW (100 rd), LAW, 2 frags. Slower, tougher.", US, W_SAW, W_LAW, 2, 115, 0.92f, 1.0f, true).cq(1, 1),
            new Kit("Delta Operator", "Aimpoint CAR-15 + M14. Faster, quicker reloads, 3 frags, 3 flashbangs, 3 charges.", US, W_CAR15_AIM, W_M14, 3, 100, 1.07f, 1.0f, true).cq(3, 3),
            new Kit("Pararescue (PJ)", "CAR-15, LAW. Heals twice as fast, patches up squadmates (E).", US, W_CAR15, W_LAW, 2, 100, 1.0f, 2.0f, true),
            new Kit("Militia Rifleman", "AK-47, RPG-7 (2 rockets), 2 grenades. Light and quick.", SNA, W_AK, W_RPG, 2, 90, 1.05f, 1.0f, false),
            new Kit("RPG Gunner", "AK-47, RPG-7 with 6 rockets. Built to bring down helicopters.", SNA, W_AK, W_RPG, 1, 90, 1.0f, 1.0f, false),
            new Kit("PKM Gunner", "PKM belt-fed MG, RPG-7 (1 rocket). Slower, tougher.", SNA, W_PKM, W_RPG, 1, 105, 0.93f, 1.0f, false),
            new Kit("Machete Runner", "(enemy only)", SNA, W_MACHETE, W_MACHETE, 0, 90, 1.25f, 1.0f, false),
    };
    public static final int KIT_MACHETE = 7;

    // ---------------------------------------------------------------- input
    public static final class Input {
        public float moveX, moveZ, yaw, pitch;
        public boolean fire, ads, sprint, crouch, use;
        public int presses;
        public static final int JUMP = 1, RELOAD = 2, GRENADE = 4, SWAP = 8, USE = 16, CALL = 32, SLOT1 = 64, SLOT2 = 128, CROUCH = 256, FLASH = 512, MELEE = 1024, SEAT = 2048;
        public void clearPresses() { presses = 0; }
    }

    // ---------------------------------------------------------------- units
    public static final int S_FOOT = 0, S_HELI = 1, S_ROPE_DOWN = 2, S_ROPE_UP = 3, S_MANTLE = 4, S_VEH = 5, S_DOWN = 6, S_CLIMB = 7;
    public static final int P_ARMOR = 1, P_SPEED = 2, P_DTAP = 4, P_SECOND = 8, P_STAMINA = 16;
    public static final class Unit {
        public int id, team, kit; public boolean player, alive = true, perch, guard, objectiveTarget;
        public float x, y, z, vx, vy, vz, yaw, pitch, hp, maxHp;
        public boolean onGround, crouch, sprinting, ads; public float slide;
        public int state = S_FOOT, heli = -1, veh = -1, vseat = -1; public float seat;
        public float mantleT, mx0, my0, mz0, mx1, my1, mz1;
        public int slot; public final int[] wep = new int[2], mag = new int[2], reserve = new int[2]; public final boolean[] papd = new boolean[2];
        public int grenades, flashes, charges, perks;
        public float fireCd, reloadT, lastHurt = -99, bloom, walkPhase, deadT, heat, meleeCd, lastShot = -99;
        public boolean triggerHeld;
        public int target = -1, targetHeli = -1; public float think, react, strafe, burst, burstPause, skill, stuck;
        public int kills;
        public float hurtYaw, suppress, stunT, flashT;
        // AI memory
        public int role, aiState, house = -1; public float stateT, coverX = -1, coverZ, lastSeenX, lastSeenZ, lastSeenT = -99, goalX = -1, goalZ;
        public int[] lf; public int lfOx, lfOz, lfGoal = -1;
        public float climbT, speedMul = 1; public int window = -1; // siege: climbing in through a window
        public int weapon() {
            if (state == S_HELI) return W_MINIGUN;
            if (state == S_VEH && vseat == 1) return W_M2;
            int w = wep[slot]; return w >= 0 ? w : wep[1 - slot] >= 0 ? wep[1 - slot] : W_M9;
        }
        public boolean papNow() { return state == S_FOOT || state == S_DOWN ? papd[slot] : false; }
        public float height() { return crouch || slide > 0 || state == S_DOWN ? H_CROUCH : H_STAND; }
        public float eye() { return y + (state == S_DOWN ? 0.6f : crouch || slide > 0 ? EYE_CROUCH : EYE_STAND); }
        public boolean has(int perk) { return (perks & perk) != 0; }
    }

    // ---------------------------------------------------------------- helicopters
    public static final int BLACKHAWK = 0, LITTLEBIRD = 1;
    public static final int H_ORBIT = 0, H_GOTO = 1, H_HOVER = 2, H_CRASH = 3, H_DOWN = 4, H_LEAVE = 5, H_FOLLOW = 6;
    public static final int P_NONE = 0, P_INSERT = 1, P_PICKUP = 2, P_CRASH = 3, P_LAND = 4;
    public static final class Heli {
        public int id, type, team = US; public String call;
        public float x, y, z, yaw, hp, maxHp, rotor, roll, pitchA;
        public int state, purpose, follow = -1; public float tx, ty, tz, ox, oz, orbitR, orbitA, timer, gunCd, gunBurst, crashSpin, crashX = -1, crashZ;
        public boolean ropes, guns = true;
        public float radius() { return type == BLACKHAWK ? 5.5f : 3.2f; }
        public boolean flying() { return state != H_DOWN && state != H_CRASH; }
    }

    // ---------------------------------------------------------------- vehicles
    public static final int V_HUMVEE = 0, V_CAR = 1;
    public static final class Vehicle {
        public int id, type, team = US; public float x, y, z, yaw, speed, steer, hp, maxHp, gunYaw, gunPitch, heat, wheel, burnT, gunCd, gunBurst;
        public final int[] seats = {-1, -1, -1, -1}; // 0 driver, 1 gunner (.50 cal), 2-3 passengers
        public boolean alive = true, disabled, aiDrive; public float destX = -1, destZ; public int[] route; public float cruise = 12;
        public float halfW() { return type == V_HUMVEE ? 1.1f : 0.95f; }
        public float halfL() { return type == V_HUMVEE ? 2.4f : 2.2f; }
        public boolean usable() { return alive && !disabled; }
    }

    // ---------------------------------------------------------------- projectiles
    public static final int PR_ROCKET = 0, PR_GRENADE = 1, PR_FLASH = 2, PR_LOB = 3, PR_CHARGE = 4;
    public static final class Proj { public int kind; public boolean rocket, alive = true; public float x, y, z, vx, vy, vz, t, dmgMul = 1; public int team, owner, house = -1, door = -1; }

    // ---------------------------------------------------------------- events
    public static final int E_SHOT = 1, E_TRACER = 2, E_IMPACT = 3, E_BLOOD = 4, E_HIT = 5, E_HEADSHOT = 6, E_KILL = 7, E_HURT = 8,
            E_ROCKET = 9, E_EXPLODE = 10, E_HELI_HIT = 11, E_HELI_DOWN = 12, E_ROPE = 13, E_WAVE = 14, E_OBJECTIVE = 15, E_WIN = 16, E_FAIL = 17,
            E_RELOAD = 18, E_GRENADE = 19, E_TIRE = 20, E_BOARD = 21, E_RADIO = 22, E_PICKUP = 23, E_JUMP = 24, E_LAND = 25, E_MANTLE = 26, E_SLIDE = 27, E_DIE = 28, E_HELI_CRASHING = 29,
            E_DOOR = 30, E_BREACH = 31, E_FLASH = 32, E_PLANT = 33, E_MELEE = 34, E_VEH_ENTER = 35, E_VEH_EXIT = 36, E_VEH_HIT = 37, E_VEH_DOWN = 38, E_CRASH = 39,
            E_BUY = 40, E_DENY = 41, E_POWER = 42, E_BOX = 43, E_BOX_MOVE = 44, E_PAP = 45, E_PERK = 46, E_POWERUP = 47, E_ROUND = 48, E_EGG = 49, E_GUNSHIP = 50,
            E_REBUILD = 51, E_PLANK = 52, E_DOWNED = 53, E_REVIVE = 54, E_CHECKPOINT = 55, E_SPAWN = 56, E_POINTS = 57, E_CAR_STOP = 58;
    public static final class Event {
        public final int type, a; public final float x, y, z, x2, y2, z2; public final String text;
        Event(int type, int a, float x, float y, float z, float x2, float y2, float z2, String text) { this.type = type; this.a = a; this.x = x; this.y = y; this.z = z; this.x2 = x2; this.y2 = y2; this.z2 = z2; this.text = text; }
    }
    public final ArrayList<Event> events = new ArrayList<>();
    void ev(int type, int a, float x, float y, float z) { events.add(new Event(type, a, x, y, z, 0, 0, 0, null)); }
    void ev2(int type, int a, float x, float y, float z, float x2, float y2, float z2) { events.add(new Event(type, a, x, y, z, x2, y2, z2, null)); }
    void evt(int type, int a, float x, float z, String text) { events.add(new Event(type, a, x, 0, z, 0, 0, 0, text)); }
    public void radio(String s) { events.add(new Event(E_RADIO, 0, 0, 0, 0, 0, 0, 0, s)); }

    // ---------------------------------------------------------------- state
    public final City city;
    public final Random rng;
    public final Mode mode;
    public final ArrayList<Unit> units = new ArrayList<>();
    public final ArrayList<Heli> helis = new ArrayList<>();
    public final ArrayList<Vehicle> vehicles = new ArrayList<>();
    public final ArrayList<Proj> projs = new ArrayList<>();
    public final Input input = new Input();
    public Unit player;
    public int difficulty, playerTeam, hostileTeam, playerKit;
    public boolean night;
    public float time;
    public int wave, result; public String resultText = "";
    public String objective = ""; public float objX, objZ, objProgress, countdown = -1;
    public int helisDowned, headshots, shots, hitsLanded;
    public final float[] crateCd;
    public float holdT, plantT; public int plantDoor = -1;
    final int[] field = new int[City.N * City.N], objField = new int[City.N * City.N];
    private int fieldCell = -1, objFieldCell = -1, fieldNav = -1, objNav = -1; private float fieldAge;
    private int nextId;
    public boolean wavesOn; public float waveTimer, waveInterval = 40f;

    public Battle(long seed, Mode mode, int kit, int difficulty, boolean night) {
        this.rng = new Random(seed);
        this.city = new City(seed, mode.isSiege());
        this.mode = mode; this.difficulty = difficulty; this.night = night; this.playerKit = kit;
        crateCd = new float[city.crates.size() + 32];
        playerTeam = KITS[kit].team; hostileTeam = 1 - playerTeam;
        player = spawn(playerTeam, kit, 0, 0, 0); player.player = true; player.skill = 1;
        mode.attach(this);
        mode.setup();
        refreshFields(true);
    }

    /** Switch side (used by modes that force a side). */
    public void setPlayerTeam(int team, int kit) {
        playerTeam = team; hostileTeam = 1 - team; player.team = team; playerKit = kit; equip(player, kit);
    }

    // ================================================================ spawning helpers (used by modes)
    public Unit spawn(int team, int kit, float x, float y, float z) {
        Unit u = new Unit(); u.id = nextId++; u.team = team; u.kit = kit;
        equip(u, kit);
        u.x = x; u.y = y; u.z = z;
        u.skill = 0.35f + 0.25f * difficulty + rng.nextFloat() * 0.2f;
        u.strafe = rng.nextBoolean() ? 1 : -1;
        Ai.assignRole(this, u);
        units.add(u); return u;
    }
    void equip(Unit u, int kit) {
        Kit k = KITS[kit]; u.kit = kit;
        u.maxHp = u.hp = k.hp;
        u.wep[0] = k.primary; u.wep[1] = k.secondary;
        for (int s = 0; s < 2; s++) { u.mag[s] = WEAPONS[u.wep[s]].mag; u.reserve[s] = WEAPONS[u.wep[s]].reserve; u.papd[s] = false; }
        u.grenades = k.grenades; u.flashes = k.flashes; u.charges = k.charges;
    }
    public void give(Unit u, int slot, int w) { u.wep[slot] = w; u.papd[slot] = false; if (w >= 0) { u.mag[slot] = WEAPONS[w].mag; u.reserve[slot] = WEAPONS[w].reserve; } u.slot = slot; u.reloadT = 0; u.fireCd = 0.4f; }
    /** Place on the nearest open, connected cell (safe for scripted spawns). */
    public void placeSafe(Unit u, float x, float z) {
        int c = city.cellOf(x, z);
        if (!city.connected[c] || solid(x, z, 0, H_STAND)) { c = walkCell(x, z); x = (c % City.N) * City.CELL + 2 + (rng.nextFloat() - .5f); z = (c / City.N) * City.CELL + 2 + (rng.nextFloat() - .5f); }
        place(u, x, z);
    }
    public void place(Unit u, float x, float z) { u.x = x; u.z = z; u.y = city.groundAt(x, z, RAD, 1.5f); u.state = S_FOOT; u.heli = -1; u.veh = -1; }
    public void board(Unit u, Heli h) { u.state = S_HELI; u.heli = h.id; u.seat = countSeated(h); seatPos(u, h); }
    private int countSeated(Heli h) { int n = 0; for (Unit u : units) if (u.alive && u.state == S_HELI && u.heli == h.id) n++; return n - 1; }
    public Unit guard(float cx, float cz, float r) {
        for (int t = 0; t < 40; t++) {
            float x = cx + (rng.nextFloat() * 2 - 1) * r, z = cz + (rng.nextFloat() * 2 - 1) * r;
            int c = city.cellOf(x, z);
            if (city.connected[c]) { Unit u = spawn(hostileTeam, hostileKit(), 0, 0, 0); place(u, x, z); u.guard = true; return u; }
        }
        return null;
    }
    /** Put n hostiles inside a house, waiting for the door. */
    public void garrison(City.House h, int n) {
        int hi = city.houses.indexOf(h);
        for (int i = 0; i < n; i++) {
            float[] s = h.spots.get(rng.nextInt(h.spots.size()));
            Unit u = spawn(hostileTeam, hostileKit() == KIT_MACHETE ? 4 : hostileKit(), 0, 0, 0);
            place(u, s[0] + rng.nextFloat() - 0.5f, s[1] + rng.nextFloat() - 0.5f);
            u.guard = true; u.house = hi; u.role = Ai.R_GARRISON; u.aiState = Ai.A_HOLD;
            u.yaw = (float) Math.atan2(-(h.doorX - u.x), -(h.doorZ - u.z));
        }
    }
    public void perches(float cx, float cz, float r, int n) {
        int placed = 0;
        for (int t = 0; t < 200 && placed < n; t++) {
            float x = cx + (rng.nextFloat() * 2 - 1) * r, z = cz + (rng.nextFloat() * 2 - 1) * r;
            float g = city.groundAt(x, z, 0.3f, 30);
            if (g > 2.9f && g < 17 && city.ceilingAt(x, z, 0.3f, g + 0.1f) > g + 3) {
                Unit u = spawn(hostileTeam, hostileTeam == SNA ? 4 : 0, x, g, z); u.perch = true; u.guard = true; u.role = Ai.R_PERCH; placed++;
            }
        }
    }
    public int hostileKit() {
        if (hostileTeam == SNA) { float f = rng.nextFloat(); return f < 0.6f ? 4 : (f < 0.78f ? 5 : (f < 0.9f ? 6 : KIT_MACHETE)); }
        float f = rng.nextFloat(); return f < 0.6f ? 0 : (f < 0.85f ? 1 : 2);
    }
    public Heli heli(int type, String call, float x, float y, float z) {
        Heli h = new Heli(); h.id = helis.size(); h.type = type; h.call = call; h.x = x; h.y = y; h.z = z;
        h.maxHp = h.hp = type == BLACKHAWK ? 650 : 400; h.state = H_ORBIT; h.orbitR = 100; h.ox = x; h.oz = z; h.ty = y;
        helis.add(h); return h;
    }
    public void orbit(Heli h, float cx, float cz, float r, float alt) { if (!h.flying()) return; h.state = H_ORBIT; h.ox = cx; h.oz = cz; h.orbitR = r; h.ty = alt; h.purpose = P_NONE; h.ropes = false; }
    public void go(Heli h, float x, float y, float z, int purpose) { if (!h.flying()) return; h.state = H_GOTO; h.tx = x; h.ty = y; h.tz = z; h.purpose = purpose; h.ropes = false; }
    public Vehicle vehicle(int type, float x, float z, float yaw, int team) {
        Vehicle v = new Vehicle(); v.id = vehicles.size(); v.type = type; v.team = team; v.x = x; v.z = z; v.yaw = yaw; v.maxHp = v.hp = type == V_HUMVEE ? 700 : 600;
        vehicles.add(v); return v;
    }
    public void setObj(String s, float x, float z) { objective = s; objX = x; objZ = z; objProgress = 0; holdT = 0; events.add(new Event(E_OBJECTIVE, 0, x, 0, z, 0, 0, 0, s)); }
    public Heli findHeli(String call) { for (Heli h : helis) if (h.call.equals(call)) return h; return null; }
    public Unit byId(int id) { for (Unit u : units) if (u.id == id) return u; return null; }
    public int aliveHostiles() { int n = 0; for (Unit u : units) if (u.alive && u.team == hostileTeam) n++; return n; }
    public boolean near(float x, float z, float r) { return player.state == S_FOOT && Math.hypot(player.x - x, player.z - z) < r; }
    public void win(String s) { if (result != 0) return; result = 1; resultText = s; ev(E_WIN, 0, 0, 0, 0); }
    public void fail(String s) { if (result != 0) return; result = 2; resultText = s; ev(E_FAIL, 0, 0, 0, 0); }
    public void respawnPlayer(float x, float z) {
        Unit p = player; equip(p, p.kit); p.alive = true; p.deadT = 0; p.state = S_FOOT; p.heli = -1; p.veh = -1; p.vseat = -1; place(p, x, z); p.vx = p.vy = p.vz = 0; p.suppress = 0; p.stunT = 0;
        ev(E_SPAWN, 0, x, p.y, z);
    }

    // ================================================================ tick
    public void tick() {
        time += DT;
        if (result != 0) { tickHelis(); tickProjs(); tickVehicles(); return; }
        for (int i = 0; i < units.size(); i++) {
            Unit u = units.get(i);
            if (!u.alive) { u.deadT += DT; continue; }
            u.suppress = Math.max(0, u.suppress - DT * 0.7f);
            u.flashT = Math.max(0, u.flashT - DT);
            if (u.player) control(u, input); else Ai.tick(this, u);
        }
        input.clearPresses();
        tickHelis();
        tickVehicles();
        tickProjs();
        for (int i = 0; i < crateCd.length; i++) crateCd[i] = Math.max(0, crateCd[i] - DT);
        fieldAge += DT;
        if (fieldAge > 0.5f || city.navVersion != fieldNav) refreshFields(false);
        mode.tick();
        if (wavesOn) tickWaves();
        if (units.size() > 160) units.removeIf(u -> !u.alive && u.deadT > 20 && !u.player);
    }

    // ---------------------------------------------------------------- player control
    private void control(Unit u, Input in) {
        Kit k = KITS[u.kit];
        u.meleeCd -= DT;
        if (u.state == S_DOWN) { u.vx = u.vz = 0; weaponLogic(u, in.fire, false); return; }
        u.yaw = in.yaw; u.pitch = Math.max(-1.45f, Math.min(1.45f, in.pitch));
        float regen = k.regen * (u.has(P_ARMOR) ? 1.2f : 1);
        if (time - u.lastHurt > REGEN_DELAY / regen && u.hp < u.maxHp) u.hp = Math.min(u.maxHp, u.hp + REGEN_RATE * regen * DT);
        if (u.stunT > 0) u.stunT -= DT;
        u.ads = in.ads && u.state != S_ROPE_DOWN && u.state != S_ROPE_UP;
        if ((in.presses & Input.SLOT1) != 0 && u.slot != 0 && u.wep[0] >= 0) { u.slot = 0; u.reloadT = 0; u.fireCd = 0.35f; }
        if ((in.presses & Input.SLOT2) != 0 && u.slot != 1 && u.wep[1] >= 0) { u.slot = 1; u.reloadT = 0; u.fireCd = 0.45f; }
        if ((in.presses & Input.SWAP) != 0 && u.wep[1 - u.slot] >= 0) { u.slot ^= 1; u.reloadT = 0; u.fireCd = 0.4f; }

        if (u.state == S_HELI) { heliSeat(u, in); return; }
        if (u.state == S_VEH) { vehSeat(u, in); return; }
        if (u.state == S_ROPE_DOWN || u.state == S_ROPE_UP) { rope(u); return; }
        if (u.state == S_MANTLE) { mantle(u); return; }

        boolean wantCrouch = in.crouch;
        if ((in.presses & Input.CROUCH) != 0 && u.sprinting && u.onGround && u.slide <= 0) {
            u.slide = SLIDE_TIME; float sx = (float) -Math.sin(u.yaw), sz = (float) -Math.cos(u.yaw);
            u.vx = sx * SLIDE_SPD; u.vz = sz * SLIDE_SPD; ev(E_SLIDE, u.id, u.x, u.y, u.z);
        }
        if (u.slide > 0) { u.slide -= DT; u.crouch = true; }
        else if (wantCrouch) u.crouch = true;
        else if (u.crouch && city.ceilingAt(u.x, u.z, RAD, u.y + 0.2f) > u.y + H_STAND) u.crouch = false;

        u.sprinting = in.sprint && in.moveZ > 0.3f && !u.crouch && !u.ads && u.reloadT <= 0;
        float spd = (u.sprinting ? SPRINT : u.crouch ? CROUCH_SPD : WALK) * k.speed * (u.ads ? ADS_MUL : 1) * (u.has(P_STAMINA) ? 1.12f : 1);
        move(u, in.moveX, in.moveZ, spd, (in.presses & Input.JUMP) != 0, in.moveZ > 0.2f);

        weaponLogic(u, in.fire, (in.presses & Input.RELOAD) != 0);
        if ((in.presses & Input.GRENADE) != 0 && u.grenades > 0) throwNade(u, PR_GRENADE);
        if ((in.presses & Input.FLASH) != 0 && u.flashes > 0) throwNade(u, PR_FLASH);
        if ((in.presses & Input.MELEE) != 0 && u.meleeCd <= 0) melee(u);
        boolean pressed = (in.presses & Input.USE) != 0;
        if (pressed || in.use) use(u, pressed, in.use); else { holdT = 0; plantT = 0; }
        if ((in.presses & Input.CALL) != 0) callPickup(u);
    }

    /** Horizontal move + gravity + collisions + jump + auto-mantle. moveX = strafe, moveZ = forward. */
    void move(Unit u, float mx, float mz, float spd, boolean jump, boolean pushingForward) {
        float sin = (float) Math.sin(u.yaw), cos = (float) Math.cos(u.yaw);
        float fx = -sin, fz = -cos, rx = cos, rz = -sin;
        float len = (float) Math.sqrt(mx * mx + mz * mz); if (len > 1) { mx /= len; mz /= len; }
        float wx = (fx * mz + rx * mx) * spd, wz = (fz * mz + rz * mx) * spd;
        moveWorld(u, wx, wz, jump, pushingForward, fx, fz);
    }
    /** Move with a desired world-space velocity (AI uses this directly). */
    void moveWorld(Unit u, float wx, float wz, boolean jump, boolean pushingForward, float fx, float fz) {
        if (u.slide > 0) { u.vx *= (1 - 1.6f * DT); u.vz *= (1 - 1.6f * DT); }
        else {
            float acc = u.onGround ? 14f : 2.5f;
            u.vx += (wx - u.vx) * Math.min(1, acc * DT); u.vz += (wz - u.vz) * Math.min(1, acc * DT);
        }
        if (jump && u.state == S_FOOT) {
            if (!tryMantle(u, fx, fz, MANTLE_MAX)) { if (u.onGround) { u.vy = JUMP; u.onGround = false; ev(E_JUMP, u.id, u.x, u.y, u.z); } }
            else return;
        }
        if (!u.onGround && pushingForward && u.vy < 3 && u.player && tryMantle(u, fx, fz, 1.9f)) return;
        float h = u.height();
        float nx = u.x + u.vx * DT;
        if (!solid(nx, u.z, u.y, h)) u.x = nx; else { u.vx = 0; if (!u.player) u.stuck += DT; }
        float nz = u.z + u.vz * DT;
        if (!solid(u.x, nz, u.y, h)) u.z = nz; else { u.vz = 0; if (!u.player) u.stuck += DT; }
        u.vy -= GRAV * DT;
        float ny = u.y + u.vy * DT;
        float g = city.groundAt(u.x, u.z, RAD, u.y + STEP);
        boolean was = u.onGround;
        if (ny <= g) { if (!was && u.vy < -9 && u.player) ev(E_LAND, u.id, u.x, g, u.z); ny = g; u.vy = 0; u.onGround = true; }
        else u.onGround = false;
        float ceil = city.ceilingAt(u.x, u.z, RAD, u.y + 0.05f);
        if (ny + h > ceil) { ny = Math.max(g, ceil - h); u.vy = Math.min(0, u.vy); }
        u.y = ny;
        float hs = (float) Math.sqrt(u.vx * u.vx + u.vz * u.vz);
        if (u.onGround) u.walkPhase += hs * DT * 1.6f;
    }

    boolean solid(float x, float z, float y, float h) {
        int cx0 = (int) ((x - RAD) / City.CELL), cx1 = (int) ((x + RAD) / City.CELL), cz0 = (int) ((z - RAD) / City.CELL), cz1 = (int) ((z + RAD) / City.CELL);
        for (int cz = cz0; cz <= cz1; cz++) for (int cx = cx0; cx <= cx1; cx++) {
            if (cx < 0 || cz < 0 || cx >= City.N || cz >= City.N) return true;
            for (int bi : city.boxesAt(cx, cz)) {
                City.Box b = city.boxes.get(bi);
                if (b.dead || b.kind == City.K_PLANK || b.y1 <= y + STEP || b.y0 >= y + h) continue;
                if (x + RAD > b.x0 && x - RAD < b.x1 && z + RAD > b.z0 && z - RAD < b.z1) return true;
            }
        }
        for (Vehicle v : vehicles) if (inVehicle(v, x, z, RAD) && y < v.y + 1.9f) return true;
        return false;
    }
    static boolean inVehicle(Vehicle v, float x, float z, float pad) {
        float dx = x - v.x, dz = z - v.z, c = (float) Math.cos(v.yaw), s = (float) Math.sin(v.yaw);
        float lx = dx * c - dz * s, lz = dx * s + dz * c; // local: x right, z back
        return Math.abs(lx) < v.halfW() + pad && Math.abs(lz) < v.halfL() + pad;
    }

    /** Parkour: if there is a ledge just ahead within reach, climb onto it. */
    private boolean tryMantle(Unit u, float fx, float fz, float reach) {
        float px = u.x + fx * (RAD + 0.45f), pz = u.z + fz * (RAD + 0.45f);
        float top = city.groundAt(px, pz, 0.15f, u.y + reach);
        if (top <= u.y + STEP || top > u.y + reach) return false;
        if (!solid(px, pz, u.y, Math.min(H_STAND, top - u.y))) return false;
        if (city.ceilingAt(px, pz, RAD, top + 0.05f) < top + H_CROUCH) return false;
        float lx = u.x + fx * (RAD + 0.75f), lz = u.z + fz * (RAD + 0.75f);
        if (solid(lx, lz, top, H_CROUCH)) { lx = px; lz = pz; if (solid(lx, lz, top, H_CROUCH)) return false; }
        u.state = S_MANTLE; u.mantleT = 0; u.mx0 = u.x; u.my0 = u.y; u.mz0 = u.z; u.mx1 = lx; u.my1 = top; u.mz1 = lz; u.vx = u.vy = u.vz = 0;
        ev(E_MANTLE, u.id, u.x, u.y, u.z);
        return true;
    }
    private void mantle(Unit u) {
        float speed = u.kit == 2 ? 1.25f : 1f;
        u.mantleT += DT * speed;
        float t = Math.min(1, u.mantleT / MANTLE_TIME);
        float up = Math.min(1, t * 1.6f), fw = Math.max(0, (t - 0.45f) / 0.55f);
        u.y = u.my0 + (u.my1 - u.my0 + 0.05f) * up;
        u.x = u.mx0 + (u.mx1 - u.mx0) * fw; u.z = u.mz0 + (u.mz1 - u.mz0) * fw;
        if (t >= 1) { u.state = S_FOOT; u.y = u.my1; u.onGround = true; u.vx = (u.mx1 - u.mx0) * 3; u.vz = (u.mz1 - u.mz0) * 3; }
    }

    void rope(Unit u) {
        Heli h = helis.get(u.heli);
        u.x = h.x + (float) Math.cos(h.yaw) * (u.seat % 2 == 0 ? 1.6f : -1.6f); u.z = h.z - (float) Math.sin(h.yaw) * (u.seat % 2 == 0 ? 1.6f : -1.6f);
        float g = city.groundAt(u.x, u.z, RAD, h.y - 2);
        if (u.state == S_ROPE_DOWN) {
            u.y -= ROPE_DOWN * DT;
            if (!h.flying()) { u.state = S_FOOT; u.heli = -1; return; }
            if (u.y <= g) { u.y = g; u.state = S_FOOT; u.heli = -1; u.onGround = true; ev(E_LAND, u.id, u.x, u.y, u.z); }
        } else {
            u.y += ROPE_UP * DT;
            if (!h.flying()) { u.state = S_FOOT; u.heli = -1; return; }
            if (u.y >= h.y - 1.2f) { board(u, h); ev(E_BOARD, u.id, h.x, h.y, h.z); if (u.player) mode.onBoard(h); }
        }
    }
    void seatPos(Unit u, Heli h) {
        float side = u.seat % 2 == 0 ? 1 : -1, back = (u.seat / 2) * 0.9f - 0.4f;
        float c = (float) Math.cos(h.yaw), s = (float) Math.sin(h.yaw);
        float lx = side * 1.25f, lz = back;
        u.x = h.x + lx * c + lz * s; u.z = h.z - lx * s + lz * c; u.y = h.y - 1.3f;
    }
    private void heliSeat(Unit u, Input in) {
        Heli h = helis.get(u.heli);
        seatPos(u, h);
        if (!h.flying()) { u.state = S_FOOT; u.heli = -1; u.y = city.groundAt(u.x, u.z, RAD, h.y + 2); return; }
        u.heat = Math.max(0, u.heat - DT * 0.35f);
        if (in.fire && u.heat < 1 && u.fireCd <= 0) {
            u.fireCd = WEAPONS[W_MINIGUN].interval; u.heat += 0.012f; u.lastShot = time;
            shoot(u, W_MINIGUN, u.yaw, u.pitch, WEAPONS[W_MINIGUN].hip, u.x, u.y + 0.3f, u.z, 1);
        }
        u.fireCd -= DT;
        if ((in.presses & Input.USE) != 0) {
            if (h.state == H_HOVER && h.purpose != P_PICKUP) startRopes(h);
            else if (h.state == H_HOVER && h.purpose == P_PICKUP) { h.purpose = P_INSERT; startRopes(h); }
            else if (h.state != H_LEAVE) {
                float gx = h.x - (float) Math.sin(h.yaw) * 12, gz = h.z - (float) Math.cos(h.yaw) * 12;
                go(h, gx, hoverAlt(gx, gz), gz, P_INSERT); radio(h.call + ": coming into the hover. Ropes in ten.");
            }
        }
    }
    public float hoverAlt(float x, float z) {
        float m = 0;
        for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) { int c = city.cellOf(x + dx * City.CELL, z + dz * City.CELL); m = Math.max(m, city.roofH[c]); }
        return Math.max(16, m + 11);
    }
    public void startRopes(Heli h) {
        h.ropes = true; h.timer = 0;
        ev(E_ROPE, h.id, h.x, h.y, h.z);
        int i = 0;
        for (Unit u : units) if (u.alive && u.state == S_HELI && u.heli == h.id) { u.state = S_ROPE_DOWN; u.y = h.y - 1.5f - (i++) * 2.2f; }
    }

    // ---------------------------------------------------------------- use: E / F
    private void use(Unit u, boolean pressed, boolean held) {
        // vehicles first
        if (pressed) for (Vehicle v : vehicles) if (v.usable() && inVehicle(v, u.x, u.z, 1.6f)) { enterVehicle(u, v); return; }
        if (mode.use(u, pressed)) return;
        // board a hovering pickup bird
        for (Heli h : helis) {
            if (h.state == H_HOVER && h.purpose == P_PICKUP && h.team == u.team && pressed && Math.hypot(h.x - u.x, h.z - u.z) < 7) {
                u.state = S_ROPE_UP; u.heli = h.id; u.seat = 0;
                for (Unit f : units) if (f.alive && !f.player && f.team == u.team && f.state == S_FOOT && Math.hypot(f.x - u.x, f.z - u.z) < 25) { f.state = S_ROPE_UP; f.heli = h.id; f.seat = 1 + countSeated(h); f.y = u.y - 3; }
                ev(E_BOARD, u.id, u.x, u.y, u.z); return;
            }
        }
        // doors: tap to kick, hold to plant a breaching charge
        int door = doorNear(u);
        if (door >= 0) {
            City.House hs = city.houses.get(door);
            if (pressed) { plantT = 0; plantDoor = door; }
            if (held && plantDoor == door && u.charges > 0) {
                plantT += DT; objProgress = Math.min(1, plantT / PLANT_TIME);
                if (plantT >= PLANT_TIME) { plantCharge(u, door); plantT = 0; plantDoor = -1; objProgress = 0; }
                return;
            }
            if (pressed && u.charges <= 0) { kick(u, hs); return; }
            if (!held && plantT > 0 && plantT < 0.3f) { kick(u, hs); plantT = 0; }
            return;
        }
        if (plantDoor >= 0 && !held && plantT > 0 && plantT < 0.3f) { City.House hs = city.houses.get(plantDoor); if (!hs.open()) kick(u, hs); }
        // ammo crates
        for (int i = 0; i < city.crates.size(); i++) {
            float[] c = city.crates.get(i);
            if (pressed && crateCd[i] <= 0 && Math.hypot(c[0] - u.x, c[1] - u.z) < 2.6f) { refill(u); crateCd[i] = 30; ev(E_PICKUP, u.id, c[0], 0, c[1]); return; }
        }
        // medic: patch a hurt squadmate
        if (pressed && u.kit == 3) for (Unit f : units) if (f.alive && !f.player && f.team == u.team && f.hp < f.maxHp && Math.hypot(f.x - u.x, f.z - u.z) < 2.5f) { f.hp = f.maxHp; ev(E_PICKUP, f.id, f.x, f.y, f.z); return; }
        // objective hold
        if (Math.hypot(objX - u.x, objZ - u.z) < 4 && Math.abs(u.y - city.groundAt(objX, objZ, 1, 2)) < 2) holdT += DT; else holdT = 0;
    }
    public void refill(Unit u) {
        Kit k = KITS[u.kit];
        for (int s = 0; s < 2; s++) if (u.wep[s] >= 0) { Weapon w = WEAPONS[u.wep[s]]; u.reserve[s] = (int) (w.reserve * (u.papd[s] ? 2 : 1)); if (w.rocket) u.mag[s] = w.mag; }
        u.grenades = Math.max(u.grenades, k.grenades); u.flashes = Math.max(u.flashes, k.flashes); u.charges = Math.max(u.charges, k.charges);
    }
    /** House index whose closed door is within reach of the unit, or -1. */
    public int doorNear(Unit u) {
        for (int i = 0; i < city.houses.size(); i++) {
            City.House h = city.houses.get(i);
            if (h.open()) continue;
            if (Math.abs(h.doorX - u.x) < 1.9f && Math.abs(h.doorZ - u.z) < 1.9f) return i;
        }
        return -1;
    }
    private void kick(Unit u, City.House h) {
        if (h.open()) return;
        if (rng.nextFloat() < KICK_CHANCE || u.kit == 2) { openDoor(h); ev(E_DOOR, 0, h.doorX, 1, h.doorZ); alertHouse(h, 0.6f); }
        else { ev(E_DOOR, 1, h.doorX, 1, h.doorZ); alertHouse(h, 1.2f); }
    }
    public void openDoor(City.House h) { if (h.door >= 0) { city.kill(h.door); h.door = -1; } }
    private void alertHouse(City.House h, float react) {
        int hi = city.houses.indexOf(h);
        for (Unit v : units) if (v.alive && v.house == hi) { v.aiState = Ai.A_ADVANCE; v.react = Math.max(v.react, react); v.lastSeenX = h.doorX; v.lastSeenZ = h.doorZ; v.lastSeenT = time; }
    }
    private void plantCharge(Unit u, int hi) {
        City.House h = city.houses.get(hi);
        u.charges--;
        Proj p = new Proj(); p.kind = PR_CHARGE; p.team = u.team; p.owner = u.id; p.house = hi; p.x = h.doorX; p.y = 1.1f; p.z = h.doorZ;
        projs.add(p); ev(E_PLANT, u.id, p.x, p.y, p.z);
    }

    // ---------------------------------------------------------------- vehicles
    public void enterVehicle(Unit u, Vehicle v) {
        int seat = -1;
        if (u.player) { if (v.seats[0] < 0) seat = 0; else if (v.seats[1] < 0) seat = 1; else if (v.seats[2] < 0) seat = 2; else if (v.seats[3] < 0) seat = 3; }
        else { for (int s : new int[]{1, 2, 3, 0}) if (v.seats[s] < 0) { seat = s; break; } }
        if (seat < 0) return;
        v.seats[seat] = u.id; u.state = S_VEH; u.veh = v.id; u.vseat = seat; u.crouch = false; u.vx = u.vz = 0;
        ev(E_VEH_ENTER, u.id, v.x, 1, v.z);
        if (u.player) {
            v.team = u.team;
            for (Unit f : units) if (f.alive && !f.player && f.team == u.team && f.state == S_FOOT && Math.hypot(f.x - v.x, f.z - v.z) < 22) enterVehicle(f, v);
            if (seat != 0) assignAiDriver(v);
        }
    }
    void assignAiDriver(Vehicle v) {
        if (v.seats[0] >= 0) return;
        for (int s = 2; s < 4; s++) if (v.seats[s] >= 0) { Unit d = byId(v.seats[s]); v.seats[s] = -1; v.seats[0] = d.id; d.vseat = 0; v.aiDrive = true; return; }
    }
    public void exitVehicle(Unit u) {
        if (u.veh < 0) return;
        Vehicle v = vehicles.get(u.veh);
        v.seats[u.vseat] = -1;
        float c = (float) Math.cos(v.yaw), s = (float) Math.sin(v.yaw);
        float[][] offs = {{-1, 0}, {1, 0}, {0, 1}, {0, -1}};
        float ex = v.x, ez = v.z;
        for (float[] o : offs) {
            float lx = o[0] * (v.halfW() + 0.9f), lz = o[1] * (v.halfL() + 0.9f);
            float x = v.x + lx * c + lz * s, z = v.z - lx * s + lz * c;
            u.veh = -1;
            if (!solid(x, z, 0, H_STAND)) { ex = x; ez = z; break; }
            u.veh = v.id;
        }
        u.veh = -1; u.vseat = -1; place(u, ex, ez);
        ev(E_VEH_EXIT, u.id, v.x, 1, v.z);
        if (u.player) {
            v.aiDrive = false;
            for (int k = 0; k < 4; k++) if (v.seats[k] >= 0) { Unit f = byId(v.seats[k]); if (f != null && !f.player) exitVehicle(f); }
        }
    }
    void seatVeh(Unit u, Vehicle v) {
        float[][] off = {{-0.5f, 0.9f, -0.2f}, {0f, 2.05f, 0.4f}, {0.5f, 0.9f, -0.2f}, {0.5f, 0.9f, 1.1f}};
        float[] o = off[Math.max(0, Math.min(3, u.vseat))];
        float c = (float) Math.cos(v.yaw), s = (float) Math.sin(v.yaw);
        u.x = v.x + o[0] * c + o[2] * s; u.z = v.z - o[0] * s + o[2] * c; u.y = v.y + o[1] - (u.vseat == 1 ? 0.2f : 0.9f);
    }
    private void vehSeat(Unit u, Input in) {
        Vehicle v = vehicles.get(u.veh);
        seatVeh(u, v);
        if (!v.alive) { exitVehicle(u); return; }
        if ((in.presses & Input.USE) != 0) { exitVehicle(u); return; }
        if ((in.presses & Input.SEAT) != 0) {
            int want = u.vseat == 0 ? 1 : 0;
            if (v.seats[want] >= 0) { Unit o = byId(v.seats[want]); if (o != null && !o.player) { v.seats[u.vseat] = o.id; o.vseat = u.vseat; } else return; } else v.seats[u.vseat] = -1;
            v.seats[want] = u.id; u.vseat = want; v.aiDrive = want != 0 && v.seats[0] >= 0;
            if (want == 1) assignAiDriver(v);
            ev(E_VEH_ENTER, u.id, v.x, 1, v.z);
        }
        if (u.vseat == 1) {
            u.heat = Math.max(0, u.heat - DT * 0.4f); u.fireCd -= DT;
            if (in.fire && u.heat < 1 && u.fireCd <= 0) {
                u.fireCd = WEAPONS[W_M2].interval; u.heat += 0.03f; u.lastShot = time;
                shoot(u, W_M2, u.yaw, u.pitch, WEAPONS[W_M2].hip, u.x, u.y + 1.5f, u.z, 1);
            }
        } else if (u.vseat >= 2) weaponLogic(u, in.fire, (in.presses & Input.RELOAD) != 0);
    }
    private void tickVehicles() {
        for (Vehicle v : vehicles) {
            if (!v.alive) { v.burnT += DT; v.speed *= 0.9f; continue; }
            float throttle = 0, steer = 0; boolean brake = false;
            Unit d = v.seats[0] >= 0 ? byId(v.seats[0]) : null;
            if (d != null && !d.alive) { v.seats[0] = -1; d = null; }
            if (v.disabled) { v.speed *= (1 - 2 * DT); }
            else if (d != null && d.player) { throttle = input.moveZ; steer = input.moveX; brake = (input.presses & Input.JUMP) != 0 || input.crouch; }
            else if ((d != null && v.aiDrive) || v.route != null) {
                float[] dir = v.route != null ? flowDir(v.x, v.z, v.route) : (Math.hypot(objX - v.x, objZ - v.z) > 14 ? flowDir(v.x, v.z, objField) : null);
                if (v.route != null && v.destX >= 0 && Math.hypot(v.destX - v.x, v.destZ - v.z) < 10) dir = null;
                if (dir != null) {
                    float want = (float) Math.atan2(-dir[0], -dir[1]);
                    float diff = wrap(want - v.yaw);
                    steer = Math.max(-1, Math.min(1, -diff * 2.2f));
                    float sp = v.cruise * (1 - Math.min(0.7f, Math.abs(diff)));
                    throttle = v.speed < sp ? 1 : 0;
                    if (Math.abs(diff) > 2.3f) { throttle = -0.6f; steer = -steer; }
                } else brake = true;
            }
            if (brake) v.speed *= (1 - 3.5f * DT);
            if (throttle > 0) v.speed = Math.min(V_MAX, v.speed + throttle * V_ACCEL * DT * (v.speed < 0 ? 2 : 1));
            else if (throttle < 0) v.speed = Math.max(-V_REV, v.speed + throttle * V_ACCEL * DT * (v.speed > 0 ? 2 : 1));
            else v.speed *= (1 - 0.6f * DT);
            v.steer += (steer - v.steer) * Math.min(1, 6 * DT);
            float turn = v.steer * V_STEER * Math.min(1, Math.abs(v.speed) / 4) * Math.signum(v.speed == 0 ? 1 : v.speed) * (1 - Math.min(0.5f, Math.abs(v.speed) / 40));
            float nyaw = v.yaw - turn * DT;
            float fx = (float) -Math.sin(nyaw), fz = (float) -Math.cos(nyaw);
            float nx = v.x + fx * v.speed * DT, nz = v.z + fz * v.speed * DT;
            if (vehicleClear(v, nx, nz, nyaw)) { v.x = nx; v.z = nz; v.yaw = nyaw; }
            else if (vehicleClear(v, v.x, v.z, nyaw)) { v.yaw = nyaw; v.speed *= -0.25f; }
            else {
                if (Math.abs(v.speed) > 7) { ev(E_CRASH, v.id, v.x, 1, v.z); damageVehicle(v, Math.abs(v.speed) * 3, null); }
                v.speed *= -0.25f;
            }
            v.wheel += v.speed * DT / 0.45f;
            v.y = 0;
            // running people over
            if (Math.abs(v.speed) > V_RUNOVER) for (Unit u : units) {
                if (!u.alive || u.state != S_FOOT || !inVehicle(v, u.x, u.z, 0.3f)) continue;
                if (u.team != v.team) { damage(u, Math.abs(v.speed) * 16, d, false); if (!u.alive) ev2(E_BLOOD, 2, u.x, u.y + 1, u.z, fx, 0.4f, fz); }
                else { u.vx += fx * 6; u.vz += fz * 6; }
            }
            // AI .50 gunner
            Unit g = v.seats[1] >= 0 ? byId(v.seats[1]) : null;
            if (g != null && !g.player && g.alive) vehicleGunner(v, g);
            for (int s = 0; s < 4; s++) if (v.seats[s] >= 0) { Unit o = byId(v.seats[s]); if (o == null || !o.alive) v.seats[s] = -1; else if (!o.player) { seatVeh(o, v); if (s == 1) { o.yaw = v.gunYaw; } } }
        }
    }
    private boolean vehicleClear(Vehicle v, float x, float z, float yaw) {
        float c = (float) Math.cos(yaw), s = (float) Math.sin(yaw);
        float hw = v.halfW(), hl = v.halfL();
        float[][] pts = {{-hw, -hl}, {hw, -hl}, {-hw, hl}, {hw, hl}, {-hw, 0}, {hw, 0}, {0, -hl}, {0, hl}};
        for (float[] p : pts) {
            float px = x + p[0] * c + p[1] * s, pz = z - p[0] * s + p[1] * c;
            if (pointSolid(px, pz, 0.7f) || pointSolid(px, pz, 1.4f)) return false;
        }
        for (Vehicle o : vehicles) if (o != v && Math.hypot(o.x - x, o.z - z) < 4.5f && inVehicle(o, x, z, hw * 0.9f)) return false;
        return true;
    }
    private boolean pointSolid(float x, float z, float y) {
        if (x < 0 || z < 0 || x >= City.SIZE || z >= City.SIZE) return true;
        for (int bi : city.boxesAt((int) (x / City.CELL), (int) (z / City.CELL))) {
            City.Box b = city.boxes.get(bi);
            if (b.dead || b.kind == City.K_PLANK) continue;
            if (y > b.y0 && y < b.y1 && x > b.x0 && x < b.x1 && z > b.z0 && z < b.z1) {
                // tire barricades and rubble under 1.2 m can be rammed through at speed
                return true;
            }
        }
        return false;
    }
    private void vehicleGunner(Vehicle v, Unit g) {
        v.gunCd -= DT;
        Unit best = null; float bd = 85;
        for (Unit t : units) {
            if (!t.alive || t.team == v.team || t.state == S_HELI || t.state == S_VEH) continue;
            float dd = (float) Math.hypot(t.x - v.x, t.z - v.z);
            if (dd < bd && city.los(g.x, g.y + 1.5f, g.z, t.x, t.y + 1.2f, t.z)) { bd = dd; best = t; }
        }
        if (best == null) return;
        float dx = best.x - g.x, dz = best.z - g.z, dy = best.y + 1.0f - (g.y + 1.5f);
        v.gunYaw = (float) Math.atan2(-dx, -dz); v.gunPitch = (float) Math.atan2(dy, Math.sqrt(dx * dx + dz * dz));
        if (v.gunCd > 0) return;
        if (v.gunBurst <= 0) { v.gunBurst = 0.5f + rng.nextFloat() * 0.6f; v.gunCd = 0.8f + rng.nextFloat(); return; }
        v.gunCd = WEAPONS[W_M2].interval * 1.3f; v.gunBurst -= v.gunCd;
        shoot(g, W_M2, v.gunYaw, v.gunPitch, 3.5f, g.x, g.y + 1.5f, g.z, 1);
    }
    public void damageVehicle(Vehicle v, float dmg, Unit src) {
        if (!v.alive) return;
        v.hp -= dmg; ev(E_VEH_HIT, v.id, v.x, 1.2f, v.z);
        if (v.hp > 0) return;
        if (v.type == V_CAR) { v.disabled = true; v.hp = 0; ev(E_CAR_STOP, v.id, v.x, 1, v.z); return; }
        v.alive = false; ev(E_VEH_DOWN, v.id, v.x, 1, v.z);
        Proj boom = new Proj(); boom.x = v.x; boom.y = 1; boom.z = v.z; boom.team = -1; boom.owner = src != null ? src.id : -1; explode(boom, 6, 120);
        for (int s = 0; s < 4; s++) if (v.seats[s] >= 0) { Unit o = byId(v.seats[s]); if (o != null) { exitVehicle(o); damage(o, 55, src, false); } }
    }

    // ---------------------------------------------------------------- helicopters, pickup
    public void callPickup(Unit u) {
        if (u.team != US) { radio("No birds for us. Keep moving."); return; }
        if (!mode.pickupAllowed()) { radio("Negative, no extraction yet."); return; }
        Heli best = null; float bd = 1e9f;
        for (Heli h : helis) if (h.team == US && h.flying() && h.follow < 0) {
            float d = (float) Math.hypot(h.x - u.x, h.z - u.z); if (d < bd) { bd = d; best = h; }
        }
        if (best == null) { float[] af = city.marks.get("Airfield"); best = heli(BLACKHAWK, "Super 6-" + (6 + reserveBirds++), af[0], 45, af[1]); }
        go(best, u.x, hoverAlt(u.x, u.z), u.z, P_PICKUP);
        radio(best.call + ": inbound to your position. Rope's coming down, press E to climb.");
    }
    private int reserveBirds;

    // ---------------------------------------------------------------- weapons
    void weaponLogic(Unit u, boolean fire, boolean reloadPress) {
        int wi = u.weapon(); Weapon w = WEAPONS[wi]; int s = u.slot; if (u.wep[s] < 0) s = 1 - s;
        u.fireCd -= DT; u.bloom = Math.max(0, u.bloom - DT * 6);
        if (!fire) u.triggerHeld = false;
        if (u.reloadT > 0) {
            u.reloadT -= DT * (u.kit == 2 ? 1.25f : 1) * (u.has(P_SPEED) ? 2f : 1);
            if (u.reloadT <= 0) { int full = magSize(u, s), need = full - u.mag[s], take = Math.min(need, u.reserve[s]); u.mag[s] += take; u.reserve[s] -= take; }
            return;
        }
        if ((reloadPress || (u.mag[s] == 0 && fire)) && u.mag[s] < magSize(u, s) && u.reserve[s] > 0 && !w.melee) { u.reloadT = w.reload; ev(E_RELOAD, u.id, u.x, u.y, u.z); return; }
        if (fire && w.semi && u.triggerHeld) return;
        if (fire && u.fireCd <= 0 && u.mag[s] > 0 && !u.sprinting && u.stunT <= 0) {
            u.triggerHeld = true;
            u.fireCd = w.interval * (u.has(P_DTAP) ? 0.75f : 1) * (u.papd[s] ? 0.85f : 1); u.mag[s]--; u.lastShot = time;
            float spread = (u.ads ? w.ads : w.hip) + u.bloom + (Math.hypot(u.vx, u.vz) > 1 ? 1.2f : 0) + (u.onGround ? 0 : 3) + u.suppress * 1.5f;
            if (u.crouch) spread *= 0.75f;
            float mul = u.player ? mode.playerDamageMul(wi, u.papd[s]) : 1;
            if (w.rocket) fireRocket(u, u.yaw, u.pitch, spread, w.lob ? PR_LOB : PR_ROCKET, mul);
            else for (int p = 0; p < w.pellets; p++) shoot(u, wi, u.yaw, u.pitch, spread, u.x, u.eye() - 0.1f, u.z, mul);
            u.bloom = Math.min(4, u.bloom + w.recoil * 0.35f);
            if (u.player) shots++;
        }
    }
    public int magSize(Unit u, int s) { int w = u.wep[s]; if (w < 0) return 0; return (int) (WEAPONS[w].mag * (u.papd[s] && !WEAPONS[w].rocket ? 1.5f : 1)); }

    private final float[] nrm = new float[2];
    /** Hitscan. yaw/pitch in radians, spread in degrees. */
    void shoot(Unit u, int wi, float yaw, float pitch, float spreadDeg, float ox, float oy, float oz, float dmgMul) {
        Weapon w = WEAPONS[wi];
        float sp = (float) Math.toRadians(spreadDeg) * (float) Math.sqrt(rng.nextFloat());
        float ang = rng.nextFloat() * 6.2832f;
        float y2 = yaw + (float) Math.cos(ang) * sp, p2 = pitch + (float) Math.sin(ang) * sp;
        float dx = (float) (-Math.sin(y2) * Math.cos(p2)), dy = (float) Math.sin(p2), dz = (float) (-Math.cos(y2) * Math.cos(p2));
        float t = city.ray(ox, oy, oz, dx, dy, dz, w.range, nrm);
        int axis = (int) nrm[0];
        Unit hit = null; float hitY = 0;
        for (Unit v : units) {
            if (!v.alive || v == u || v.team == u.team || v.state == S_HELI || (v.state == S_VEH && v.vseat != 1) || v.state == S_CLIMB) continue;
            float th = rayUnit(v, ox, oy, oz, dx, dy, dz, t);
            if (th < t) { t = th; hit = v; hitY = oy + dy * th - v.y; }
        }
        Heli hh = null;
        for (Heli h : helis) {
            if (!h.flying() || h.team == u.team) continue;
            float th = raySphere(h.x, h.y, h.z, h.radius() * 0.7f, ox, oy, oz, dx, dy, dz);
            if (th >= 0 && th < t) { t = th; hh = h; hit = null; }
        }
        Vehicle hv = null;
        for (Vehicle v : vehicles) {
            if (u.veh == v.id) continue;
            float th = rayVehicle(v, ox, oy, oz, dx, dy, dz, t);
            if (th < t) { t = th; hv = v; hit = null; hh = null; }
        }
        float hx = ox + dx * t, hy = oy + dy * t, hz = oz + dz * t;
        ev2(E_TRACER, wi, ox, oy, oz, hx, hy, hz);
        ev(E_SHOT, u.id * 32 + wi, ox, oy, oz);
        // near misses suppress whoever they pass
        for (Unit v : units) {
            if (!v.alive || v == hit || v.team == u.team || v.state == S_HELI) continue;
            float px = v.x - ox, py = v.y + 1.2f - oy, pz = v.z - oz, along = px * dx + py * dy + pz * dz;
            if (along < 2 || along > t + 1) continue;
            float qx = px - dx * along, qy = py - dy * along, qz = pz - dz * along;
            if (qx * qx + qy * qy + qz * qz < SUPPRESS_RAD * SUPPRESS_RAD) v.suppress = Math.min(3, v.suppress + (v.player ? 0.1f : u.player ? 0.45f : 0.2f));
        }
        if (hit != null) {
            float h = hit.height(); boolean head = hitY > h * 0.84f; boolean legs = hitY < h * 0.45f;
            float dmg = w.dmg * dmgMul * (head ? HEAD_MUL : legs ? LEG_MUL : 1) * (KITS[hit.kit].armor && !head && !legs ? ARMOR_MUL : 1);
            if (wi != W_MINIGUN && t > 60) dmg *= Math.max(0.6f, 1 - (t - 60) / 300f);
            if (w.pellets > 1) dmg *= Math.max(0.3f, 1 - t / 40f);
            damage(hit, dmg, u, head);
            ev2(E_BLOOD, head ? 1 : 0, hx, hy, hz, dx, dy, dz);
        } else if (hh != null) {
            hh.hp -= wi == W_M2 ? 6 : 1.2f; ev(E_HELI_HIT, hh.id, hx, hy, hz);
            if (hh.hp <= 0) downHeli(hh, u);
        } else if (hv != null) {
            float dm = w.dmg * (hv.type == V_CAR ? (wi == W_MINIGUN ? 0.25f : 0.5f) : 0.06f) * (wi == W_M2 ? 3 : 1);
            if (hv.type == V_CAR) { // the engine block is the front third
                float lx = hx - hv.x, lz = hz - hv.z, c = (float) Math.cos(hv.yaw), s = (float) Math.sin(hv.yaw);
                float back = lx * s + lz * c; if (back < -hv.halfL() * 0.3f) dm *= 2.5f; else dm *= 0.4f;
            }
            damageVehicle(hv, dm, u); events.add(new Event(E_IMPACT, 9, hx, hy, hz, -dx, 0, -dz, null));
        } else if (t < w.range - 0.1f) {
            events.add(new Event(E_IMPACT, axis, hx, hy, hz, axis == 0 ? nrm[1] : 0, axis == 1 ? nrm[1] : 0, axis == 2 ? nrm[1] : 0, null));
        }
        if (u.player) mode.onShot(hx, hy, hz);
    }
    static float rayUnit(Unit v, float ox, float oy, float oz, float dx, float dy, float dz, float maxT) {
        float r = 0.36f, ex = ox - v.x, ez = oz - v.z;
        float a = dx * dx + dz * dz, b = 2 * (ex * dx + ez * dz), c = ex * ex + ez * ez - r * r;
        if (a < 1e-6f) return 1e9f;
        float disc = b * b - 4 * a * c; if (disc < 0) return 1e9f;
        float sq = (float) Math.sqrt(disc);
        for (float t : new float[]{(-b - sq) / (2 * a), (-b + sq) / (2 * a)}) {
            if (t < 0 || t > maxT) continue;
            float y = oy + dy * t; if (y >= v.y && y <= v.y + v.height()) return t;
        }
        return 1e9f;
    }
    static float raySphere(float cx, float cy, float cz, float r, float ox, float oy, float oz, float dx, float dy, float dz) {
        float ex = ox - cx, ey = oy - cy, ez = oz - cz;
        float b = ex * dx + ey * dy + ez * dz, c = ex * ex + ey * ey + ez * ez - r * r, d = b * b - c;
        if (d < 0) return -1; return -b - (float) Math.sqrt(d);
    }
    static float rayVehicle(Vehicle v, float ox, float oy, float oz, float dx, float dy, float dz, float maxT) {
        float c = (float) Math.cos(v.yaw), s = (float) Math.sin(v.yaw);
        float rx = ox - v.x, rz = oz - v.z;
        float lox = rx * c - rz * s, loz = rx * s + rz * c, ldx = dx * c - dz * s, ldz = dx * s + dz * c;
        float[] o = {lox, oy - v.y, loz}, d = {ldx, dy, ldz}, lo = {-v.halfW(), 0.3f, -v.halfL()}, hi = {v.halfW(), 1.85f, v.halfL()};
        float tmin = 0, tmax = maxT;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-7f) { if (o[i] < lo[i] || o[i] > hi[i]) return 1e9f; continue; }
            float t1 = (lo[i] - o[i]) / d[i], t2 = (hi[i] - o[i]) / d[i]; if (t1 > t2) { float tt = t1; t1 = t2; t2 = tt; }
            tmin = Math.max(tmin, t1); tmax = Math.min(tmax, t2); if (tmin > tmax) return 1e9f;
        }
        return tmin > 0.01f ? tmin : 1e9f;
    }

    public void damage(Unit v, float dmg, Unit src, boolean head) {
        if (!v.alive || v.state == S_DOWN) return;
        if (v.objectiveTarget && (src == null || !src.player || src.state == S_HELI)) return;
        if (v.player) { dmg *= PLAYER_DMG[difficulty]; if (src != null && src.weapon() == W_MACHETE) dmg *= 1.3f; }
        if (!v.player && v.team == playerTeam) dmg *= 0.6f;
        v.hp -= dmg; v.lastHurt = time;
        if (src != null) v.hurtYaw = (float) Math.atan2(-(src.x - v.x), -(src.z - v.z));
        if (src != null && !v.player) { if (v.target < 0) { v.target = src.id; v.react = 0.2f; } v.lastSeenX = src.x; v.lastSeenZ = src.z; v.lastSeenT = time; if (v.aiState == Ai.A_HOLD) v.aiState = Ai.A_ADVANCE; }
        if (v.player) ev(E_HURT, (int) dmg, src != null ? src.x : v.x, 0, src != null ? src.z : v.z);
        if (src != null && src.player) { ev(head ? E_HEADSHOT : E_HIT, v.id, v.x, v.y, v.z); hitsLanded++; mode.onHit(v, src, dmg); }
        if (v.hp <= 0) {
            if (v.player && mode.playerDied()) return;
            v.alive = false; v.hp = 0; v.deadT = 0;
            if (v.veh >= 0) { Vehicle vh = vehicles.get(v.veh); vh.seats[v.vseat] = -1; v.y = 0.2f; }
            if (v.state == S_HELI || v.state == S_ROPE_DOWN || v.state == S_ROPE_UP) v.y = city.groundAt(v.x, v.z, RAD, v.y);
            ev(E_DIE, v.id, v.x, v.y, v.z);
            boolean melee = src != null && src.player && src.meleeCd > MELEE_CD - 0.05f;
            if (src != null) { src.kills++; if (src.player) { if (head) headshots++; ev(E_KILL, head ? 1 : 0, v.x, v.y, v.z); } }
            mode.onKill(v, src, head, melee);
            if (v.player) fail(resultText.isEmpty() ? "You were killed." : resultText);
        }
    }

    void fireRocket(Unit u, float yaw, float pitch, float spreadDeg, int kind, float mul) {
        float sp = (float) Math.toRadians(spreadDeg) * rng.nextFloat(), ang = rng.nextFloat() * 6.2832f;
        float y2 = yaw + (float) Math.cos(ang) * sp, p2 = pitch + (float) Math.sin(ang) * sp + (kind == PR_LOB ? 0.12f : 0);
        Proj p = new Proj(); p.kind = kind; p.rocket = true; p.team = u.team; p.owner = u.id; p.dmgMul = mul;
        float dx = (float) (-Math.sin(y2) * Math.cos(p2)), dy = (float) Math.sin(p2), dz = (float) (-Math.cos(y2) * Math.cos(p2));
        float speed = kind == PR_LOB ? 55 : ROCKET_SPEED;
        p.x = u.x + dx * 0.8f; p.y = (u.state == S_HELI ? u.y : u.eye()) - 0.1f; p.z = u.z + dz * 0.8f;
        p.vx = dx * speed; p.vy = dy * speed; p.vz = dz * speed;
        projs.add(p); ev(E_ROCKET, u.id, p.x, p.y, p.z);
    }
    /** Throw a frag or flashbang along the view. */
    void throwNade(Unit u, int kind) {
        if (kind == PR_FLASH) u.flashes--; else u.grenades--;
        Proj p = new Proj(); p.kind = kind; p.team = u.team; p.owner = u.id;
        float a = u.pitch + 0.25f;
        float dx = (float) (-Math.sin(u.yaw) * Math.cos(a)), dy = (float) Math.sin(a), dz = (float) (-Math.cos(u.yaw) * Math.cos(a));
        p.x = u.x + dx * 0.5f; p.y = u.eye(); p.z = u.z + dz * 0.5f; p.vx = dx * 17 + u.vx; p.vy = dy * 17; p.vz = dz * 17 + u.vz;
        projs.add(p); ev(E_GRENADE, u.id, p.x, p.y, p.z);
    }
    /** AI lob toward a point. */
    void lobAt(Unit u, float tx, float tz) {
        u.grenades--;
        float dx = tx - u.x, dz = tz - u.z, d = (float) Math.sqrt(dx * dx + dz * dz); if (d < 1) return;
        float g = GRAV * 0.8f, ang = 0.75f, v = (float) Math.sqrt(d * g / Math.sin(2 * ang)) * 0.95f;
        Proj p = new Proj(); p.kind = PR_GRENADE; p.team = u.team; p.owner = u.id;
        p.x = u.x; p.y = u.eye(); p.z = u.z;
        p.vx = dx / d * v * (float) Math.cos(ang); p.vz = dz / d * v * (float) Math.cos(ang); p.vy = v * (float) Math.sin(ang);
        projs.add(p); ev(E_GRENADE, u.id, p.x, p.y, p.z);
    }
    private void melee(Unit u) {
        u.meleeCd = MELEE_CD; ev(E_MELEE, u.id, u.x, u.y, u.z);
        float fx = (float) -Math.sin(u.yaw), fz = (float) -Math.cos(u.yaw);
        Unit best = null; float bd = MELEE_RANGE;
        for (Unit v : units) {
            if (!v.alive || v.team == u.team || v.state != S_FOOT) continue;
            float dx = v.x - u.x, dz = v.z - u.z, d = (float) Math.sqrt(dx * dx + dz * dz);
            if (d < bd && (dx * fx + dz * fz) / Math.max(0.01f, d) > 0.5f && Math.abs(v.y - u.y) < 1.5f) { bd = d; best = v; }
        }
        if (best != null) { damage(best, MELEE_DMG * mode.playerDamageMul(-1, false), u, false); ev2(E_BLOOD, 0, best.x, best.y + 1.3f, best.z, fx, 0.2f, fz); }
    }

    void tickProjs() {
        for (int i = projs.size() - 1; i >= 0; i--) {
            Proj p = projs.get(i); p.t += DT;
            if (p.kind == PR_CHARGE) {
                if (p.t >= CHARGE_FUSE) { breach(p); projs.remove(i); }
                continue;
            }
            if (p.rocket) {
                p.vy -= (p.kind == PR_LOB ? GRAV * 0.55f : 1.5f) * DT;
                float sp = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy + p.vz * p.vz), step = sp * DT;
                float dx = p.vx / sp, dy = p.vy / sp, dz = p.vz / sp;
                float t = city.ray(p.x, p.y, p.z, dx, dy, dz, step, null);
                boolean boom = t < step || p.t > 4.2f;
                for (Heli h : helis) if (h.flying() && h.team != p.team && raySphere(h.x, h.y, h.z, h.radius(), p.x, p.y, p.z, dx, dy, dz) >= 0 && dist3(h.x, h.y, h.z, p.x, p.y, p.z) < step + h.radius()) {
                    hitHeli(h, p); boom = true; t = Math.min(t, (float) dist3(h.x, h.y, h.z, p.x, p.y, p.z) - h.radius() * 0.5f); break;
                }
                for (Vehicle v : vehicles) { float tv = rayVehicle(v, p.x, p.y, p.z, dx, dy, dz, step); if (tv < step && v.team != p.team) { boom = true; t = Math.min(t, tv); damageVehicle(v, 320 * p.dmgMul, byId(p.owner)); } }
                for (Unit v : units) if (v.alive && v.team != p.team && v.state != S_HELI && rayUnit(v, p.x, p.y, p.z, dx, dy, dz, step) < step) { boom = true; t = Math.min(t, rayUnit(v, p.x, p.y, p.z, dx, dy, dz, step)); }
                float m = Math.max(0, Math.min(t, step) - 0.05f);
                p.x += dx * m; p.y += dy * m; p.z += dz * m;
                if (boom) { explode(p, ROCKET_SPLASH, 210 * p.dmgMul); projs.remove(i); }
            } else {
                p.vy -= GRAV * 0.8f * DT;
                float nx = p.x + p.vx * DT, ny = p.y + p.vy * DT, nz = p.z + p.vz * DT;
                float g = city.groundAt(nx, nz, 0.1f, p.y + 0.1f);
                if (ny < g) { ny = g; p.vy *= -0.3f; p.vx *= 0.6f; p.vz *= 0.6f; }
                boolean wall = pointSolid(nx, nz, ny + 0.05f);
                if (wall) { p.vx *= -0.4f; p.vz *= -0.4f; } else { p.x = nx; p.z = nz; }
                p.y = ny;
                if (p.kind == PR_FLASH && p.t > FLASH_FUSE) { flash(p); projs.remove(i); }
                else if (p.kind == PR_GRENADE && p.t > GRENADE_FUSE) { explode(p, GRENADE_SPLASH, 160); projs.remove(i); }
            }
        }
    }
    static double dist3(float a, float b, float c, float x, float y, float z) { return Math.sqrt((a - x) * (a - x) + (b - y) * (b - y) + (c - z) * (c - z)); }

    void explode(Proj p, float rad, float dmg) {
        ev(E_EXPLODE, p.rocket ? 1 : 0, p.x, p.y, p.z);
        Unit src = p.owner >= 0 ? byId(p.owner) : null;
        for (Unit v : units) {
            if (!v.alive || v.state == S_HELI || (v.state == S_VEH && v.vseat != 1)) continue;
            float d = (float) dist3(v.x, v.y + 1, v.z, p.x, p.y, p.z);
            if (d > rad) continue;
            if (!city.los(p.x, p.y + 0.3f, p.z, v.x, v.y + 1.2f, v.z)) continue;
            if (v.team == p.team && v != src) continue;
            float f = 1 - d / rad; damage(v, dmg * f * f + (d < 1.5f ? 60 : 0), src, false);
            v.suppress = 3;
            if (!v.alive) ev2(E_BLOOD, 2, v.x, v.y + 1, v.z, v.x - p.x, 0.5f, v.z - p.z);
        }
        for (Vehicle v : vehicles) if (v.alive && Math.hypot(v.x - p.x, v.z - p.z) < rad && p.kind != PR_ROCKET && p.kind != PR_LOB) damageVehicle(v, dmg * 0.4f, src);
    }
    private void breach(Proj p) {
        City.House h = city.houses.get(p.house);
        openDoor(h);
        ev(E_BREACH, p.house, h.doorX, 1.1f, h.doorZ);
        ev(E_EXPLODE, 0, h.doorX, 1.1f, h.doorZ);
        Unit src = byId(p.owner);
        for (Unit v : units) {
            if (!v.alive || v.team == p.team) continue;
            boolean inside = v.x > h.x0 - 0.5f && v.x < h.x1 + 0.5f && v.z > h.z0 - 0.5f && v.z < h.z1 + 0.5f;
            float d = (float) Math.hypot(v.x - h.doorX, v.z - h.doorZ);
            if (inside || d < 5) { v.stunT = BREACH_STUN; v.aiState = Ai.A_ADVANCE; if (d < 2.8f) damage(v, 120, src, false); }
        }
    }
    private void flash(Proj p) {
        ev(E_FLASH, 0, p.x, p.y, p.z);
        for (Unit v : units) {
            if (!v.alive) continue;
            float d = (float) dist3(v.x, v.eye(), v.z, p.x, p.y + 0.2f, p.z);
            if (d > FLASH_RAD || !city.los(p.x, p.y + 0.2f, p.z, v.x, v.eye(), v.z)) continue;
            float f = 1 - d / FLASH_RAD;
            if (v.player) {
                float fx = (float) (-Math.sin(v.yaw) * Math.cos(v.pitch)), fz = (float) (-Math.cos(v.yaw) * Math.cos(v.pitch));
                float facing = ((p.x - v.x) * fx + (p.z - v.z) * fz) / Math.max(0.1f, d);
                v.flashT = Math.max(v.flashT, (0.4f + Math.max(0, facing) * 2.6f) * f + 0.3f);
            } else if (v.team != p.team) { v.stunT = Math.max(v.stunT, FLASH_STUN * (0.4f + f)); v.aiState = Ai.A_ADVANCE; }
        }
    }
    private void hitHeli(Heli h, Proj p) {
        h.hp -= HELI_RPG_DMG * p.dmgMul; ev(E_HELI_HIT, h.id, p.x, p.y, p.z);
        if (h.hp <= 0) downHeli(h, byId(p.owner));
        else radio(h.call + ": we're hit! We're hit! Still flying.");
    }
    public void downHeli(Heli h, Unit src) {
        if (!h.flying()) return;
        h.state = H_CRASH; h.hp = 0; h.crashSpin = 0; h.timer = 0;
        ev(E_HELI_CRASHING, h.id, h.x, h.y, h.z);
        radio(h.call + " going down! " + h.call + " going down!");
        if (src != null && src.player) helisDowned++;
        mode.onHeliDown(h, src);
        for (Unit u : units) if (u.alive && u.heli == h.id && (u.state == S_ROPE_UP || u.state == S_ROPE_DOWN)) { u.state = S_FOOT; u.heli = -1; }
    }

    // ---------------------------------------------------------------- helicopter flight
    private void tickHelis() {
        for (Heli h : helis) {
            h.rotor += (h.state == H_DOWN ? Math.max(0, 6 - h.timer) : 28) * DT;
            float px = h.x, pz = h.z;
            switch (h.state) {
                case H_ORBIT: {
                    float spd = h.type == BLACKHAWK ? 20 : 26;
                    h.orbitA += spd / Math.max(30, h.orbitR) * DT;
                    float tx = h.ox + (float) Math.cos(h.orbitA) * h.orbitR, tz = h.oz + (float) Math.sin(h.orbitA) * h.orbitR;
                    steer(h, tx, h.ty, tz, spd * 1.6f);
                    break;
                }
                case H_FOLLOW: {
                    Vehicle v = h.follow >= 0 ? vehicles.get(h.follow) : null;
                    if (v == null) { h.state = H_ORBIT; break; }
                    float side = 14, fx = (float) -Math.sin(v.yaw), fz = (float) -Math.cos(v.yaw);
                    float tx = v.x + (float) Math.cos(v.yaw) * side - fx * 6, tz = v.z - (float) Math.sin(v.yaw) * side - fz * 6;
                    steer(h, tx, h.ty, tz, 30);
                    float want = (float) Math.atan2(-(v.x - h.x), -(v.z - h.z)) + 1.57f; h.yaw += wrap(want - h.yaw) * Math.min(1, 2 * DT);
                    break;
                }
                case H_GOTO: {
                    float d = (float) Math.hypot(h.tx - h.x, h.tz - h.z);
                    steer(h, h.tx, d > 60 ? Math.max(h.ty, 35) : h.ty, h.tz, Math.min(34, 4 + d * 0.55f));
                    if (d < 1.5f && Math.abs(h.y - h.ty) < 1.5f) { h.state = H_HOVER; h.timer = 0; if (h.purpose == P_PICKUP) h.ropes = true; if (h.purpose == P_CRASH) downHeli(h, null); }
                    break;
                }
                case H_HOVER: {
                    h.timer += DT;
                    h.x += (float) Math.sin(time * 0.9f + h.id) * 0.4f * DT; h.z += (float) Math.cos(time * 0.7f + h.id) * 0.4f * DT;
                    h.roll *= 0.95f; h.pitchA *= 0.95f;
                    if (h.purpose == P_INSERT && h.ropes) {
                        boolean anyone = false; for (Unit u : units) if (u.alive && u.heli == h.id && (u.state == S_ROPE_DOWN || u.state == S_HELI)) anyone = true;
                        if (!anyone && h.timer > 1.5f) leaveAfter(h);
                    }
                    if (h.purpose == P_INSERT && !h.ropes && !hasPlayer(h) && h.timer > 1.2f) startRopes(h);
                    if (h.purpose == P_PICKUP && h.timer > 40 && !hasPlayer(h)) leaveAfter(h);
                    break;
                }
                case H_LEAVE: steer(h, h.tx, 50, h.tz, 30); if (Math.hypot(h.tx - h.x, h.tz - h.z) < 10) orbit(h, h.ox, h.oz, 140, 50); break;
                case H_CRASH: {
                    h.timer += DT; h.crashSpin += DT * 5.5f; h.yaw += h.crashSpin * DT;
                    h.y -= (4 + h.timer * 7) * DT;
                    if (h.crashX >= 0) { h.x += (h.crashX - h.x) * Math.min(1, 5f * DT); h.z += (h.crashZ - h.z) * Math.min(1, 5f * DT); }
                    float g = city.groundAt(h.x, h.z, 1.5f, h.y);
                    if (h.y <= g + 1.2f) { if (h.crashX >= 0) { h.x = h.crashX; h.z = h.crashZ; g = city.groundAt(h.x, h.z, 1.5f, h.y); } h.y = g + 1.2f; h.state = H_DOWN; h.timer = 0; ev(E_HELI_DOWN, h.id, h.x, h.y, h.z); crashCasualties(h); }
                    break;
                }
                case H_DOWN: h.timer += DT; break;
            }
            if (h.flying() && h.state != H_HOVER) {
                float vx = (h.x - px) / DT, vz = (h.z - pz) / DT;
                float c = (float) Math.cos(h.yaw), s = (float) Math.sin(h.yaw);
                float fwd = -(vx * s + vz * c), side = vx * c - vz * s;
                h.pitchA += (-fwd * 0.012f - h.pitchA) * Math.min(1, 3 * DT); h.roll += (side * 0.02f - h.roll) * Math.min(1, 3 * DT);
            }
            if (h.flying() && result == 0 && h.guns) heliGuns(h);
        }
    }
    private boolean hasPlayer(Heli h) { return player.alive && player.heli == h.id && (player.state == S_HELI || player.state == S_ROPE_UP); }
    private void leaveAfter(Heli h) { h.state = H_LEAVE; h.ropes = false; h.purpose = P_NONE; h.tx = h.x + (h.ox - h.x) * 0.5f; h.tz = h.z + (h.oz - h.z) * 0.5f; if (h.tx == h.x) h.tx += 60; }
    private void steer(Heli h, float tx, float ty, float tz, float spd) {
        float dx = tx - h.x, dz = tz - h.z, d = (float) Math.sqrt(dx * dx + dz * dz);
        if (d > 0.01f) { float m = Math.min(d, spd * DT); h.x += dx / d * m; h.z += dz / d * m; }
        h.y += (ty - h.y) * Math.min(1, 1.2f * DT);
        if (d > 3 && h.state != H_FOLLOW) { float want = (float) Math.atan2(-dx, -dz); float diff = wrap(want - h.yaw); h.yaw += diff * Math.min(1, 2.2f * DT); }
    }
    public static float wrap(float a) { while (a > Math.PI) a -= 6.2832f; while (a < -Math.PI) a += 6.2832f; return a; }
    private void crashCasualties(Heli h) {
        for (Unit u : units) if (u.alive && u.heli == h.id && u.state == S_HELI) { u.state = S_FOOT; u.heli = -1; u.y = city.groundAt(u.x, u.z, RAD, h.y + 3); if (u.player) radio("You crawl out of the wreck."); else damage(u, 40, null, false); }
    }
    private void heliGuns(Heli h) {
        if (h.state == H_GOTO && h.purpose == P_CRASH) return;
        h.gunCd -= DT;
        if (h.gunCd > 0) return;
        if (h.gunBurst <= 0) { h.gunBurst = 0.6f + rng.nextFloat() * 0.6f; h.gunCd = 1.2f + rng.nextFloat() * 1.6f; return; }
        Unit best = null; float bd = h.type == LITTLEBIRD ? 110 : 80;
        for (Unit v : units) {
            if (!v.alive || v.team == h.team || v.state == S_HELI || v.objectiveTarget || v.house >= 0 && v.aiState == Ai.A_HOLD) continue;
            float d = (float) dist3(v.x, v.y, v.z, h.x, h.y, h.z);
            if (d < bd && city.los(h.x, h.y - 1.5f, h.z, v.x, v.y + 1.2f, v.z)) { bd = d; best = v; }
        }
        if (best == null) { h.gunCd = 0.4f; return; }
        float interval = 0.06f; h.gunCd = interval; h.gunBurst -= interval;
        float dx = best.x - h.x, dy = best.y + 1.0f - (h.y - 1.5f), dz = best.z - h.z;
        float yaw = (float) Math.atan2(-dx, -dz), pitch = (float) Math.atan2(dy, Math.sqrt(dx * dx + dz * dz));
        float spread = (best.player ? 4.5f : 3f) - difficulty * 0.4f;
        shoot(gunnerFor(h.team), W_MINIGUN, yaw, pitch, spread, h.x, h.y - 1.5f, h.z, 1);
    }
    private final Unit[] gunners = new Unit[2];
    Unit gunnerFor(int team) {
        if (gunners[team] == null) { gunners[team] = new Unit(); gunners[team].team = team; gunners[team].id = -10 - team; gunners[team].alive = false; gunners[team].wep[0] = W_MINIGUN; }
        return gunners[team];
    }

    // ---------------------------------------------------------------- navigation
    void refreshFields(boolean force) {
        fieldAge = 0;
        boolean navChanged = city.navVersion != fieldNav;
        int pc = walkCell(player.x, player.z);
        if (pc != fieldCell || navChanged || force) { fieldCell = pc; bfs(pc, field); }
        int oc = walkCell(objX, objZ);
        if (oc != objFieldCell || navChanged || force) { objFieldCell = oc; bfs(oc, objField); }
        fieldNav = city.navVersion;
    }
    int walkCell(float x, float z) {
        int cx = City.cl((int) (x / City.CELL)), cz = City.cl((int) (z / City.CELL));
        for (int r = 0; r < 12; r++) for (int dz = -r; dz <= r; dz++) for (int dx = -r; dx <= r; dx++) { int xx = cx + dx, zz = cz + dz; if (xx >= 0 && zz >= 0 && xx < City.N && zz < City.N && city.connected[zz * City.N + xx]) return zz * City.N + xx; }
        return cz * City.N + cx;
    }
    private final int[] queue = new int[City.N * City.N];
    public void bfs(int start, int[] dist) {
        java.util.Arrays.fill(dist, Integer.MAX_VALUE);
        int head = 0, tail = 0; dist[start] = 0; queue[tail++] = start;
        while (head < tail) {
            int c = queue[head++], cx = c % City.N, cz = c / City.N, d = dist[c];
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) continue;
                if (!city.pass(cx, cz, dx, dz)) continue;
                int n = (cz + dz) * City.N + cx + dx, nd = d + (dx != 0 && dz != 0 ? 14 : 10);
                if (nd < dist[n]) { if (dist[n] == Integer.MAX_VALUE) queue[tail++] = n; dist[n] = nd; }
            }
        }
    }
    /** Direction {dx, dz} to walk to get closer along a distance field. */
    public float[] flowDir(float x, float z, int[] dist) {
        int cx = City.cl((int) (x / City.CELL)), cz = City.cl((int) (z / City.CELL));
        int bestD = dist[cz * City.N + cx], bx = cx, bz = cz;
        if (bestD == Integer.MAX_VALUE) bestD = Integer.MAX_VALUE - 1;
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            if (dx == 0 && dz == 0) continue;
            if (!city.pass(cx, cz, dx, dz)) continue;
            int d = dist[(cz + dz) * City.N + cx + dx]; if (d < bestD) { bestD = d; bx = cx + dx; bz = cz + dz; }
        }
        float tx = bx * City.CELL + 2, tz = bz * City.CELL + 2, dx = tx - x, dz = tz - z, l = (float) Math.sqrt(dx * dx + dz * dz);
        if (l < 0.05f) return null;
        return new float[]{dx / l, dz / l};
    }
    public float[] objectiveDir(float x, float z) { return flowDir(x, z, objField); }
    public int[] fieldTo(float x, float z) { int[] f = new int[City.N * City.N]; bfs(walkCell(x, z), f); return f; }

    // ---------------------------------------------------------------- waves (modes call these)
    private void tickWaves() {
        waveTimer += DT;
        int alive = aliveHostiles();
        if (waveTimer >= waveInterval || (alive < 3 && waveTimer > waveInterval * 0.5f)) { waveTimer = 0; spawnWave(alive); }
    }
    public int waveSize() { return 3 + wave * (1 + difficulty) + (int) (wave * wave * 0.15f); }
    public void spawnWave(int alive) {
        wave++;
        int n = Math.min(MAX_HOSTILES - alive, waveSize());
        if (n <= 0) return;
        events.add(new Event(E_WAVE, wave, 0, 0, 0, 0, 0, 0, null));
        if (hostileTeam == US) { ropeWave(n); return; }
        spawnMilitia(n, player.x, player.z, 55, 115, true);
        if (wave % 3 == 0) perches(player.x, player.z, 70, 2 + difficulty);
        radio(wave == 1 ? "Here they come." : "Wave " + wave + ". More of them, every direction.");
    }
    public int spawnMilitia(int n, float cx, float cz, float minR, float maxR, boolean tires) {
        float[] tire = null; float bt = 1e9f;
        if (tires) for (float[] t : city.tires) { float d = (float) Math.hypot(t[0] - cx, t[1] - cz); if (d > minR && d < maxR && Math.abs(d - (minR + maxR) / 2) < bt) { bt = Math.abs(d - (minR + maxR) / 2); tire = t; } }
        if (tire != null) events.add(new Event(E_TIRE, 0, tire[0], 0, tire[1], 0, 0, 0, null));
        int made = 0;
        for (int tries = 0; tries < 600 && made < n; tries++) {
            float x, z;
            if (tire != null && tries % 2 == 0) { x = tire[0] + (rng.nextFloat() - .5f) * 30; z = tire[1] + (rng.nextFloat() - .5f) * 30; }
            else { double a = rng.nextFloat() * 6.283; float r = minR + rng.nextFloat() * (maxR - minR); x = cx + (float) Math.cos(a) * r; z = cz + (float) Math.sin(a) * r; }
            if (x < 6 || z < 6 || x > City.SIZE - 6 || z > City.SIZE - 6 || city.inArena(x, z)) continue;
            int c = city.cellOf(x, z);
            if (!city.connected[c] || field[c] == Integer.MAX_VALUE) continue;
            if (city.los(player.x, player.eye(), player.z, x, 1.4f, z) && tries < 400) continue;
            Unit u = spawn(hostileTeam, hostileKit(), 0, 0, 0); place(u, x, z); made++;
        }
        return made;
    }
    public void ropeWave(int n) {
        Heli best = null;
        for (Heli h : helis) if (h.flying() && h.team == US && h.type == BLACKHAWK && (h.state == H_ORBIT || h.state == H_LEAVE)) { best = h; break; }
        if (best == null) {
            for (int i = 0; i < n; i++) { Unit u = spawn(US, hostileKit(), 0, 0, 0); place(u, Math.min(City.SIZE - 20, player.x + 90 + rng.nextFloat() * 20), Math.max(20, player.z - 70 + rng.nextFloat() * 20)); }
            return;
        }
        double a = rng.nextFloat() * 6.283;
        for (int tries = 0; tries < 50; tries++) {
            float r = 45 + rng.nextFloat() * 35; float x = player.x + (float) Math.cos(a) * r, z = player.z + (float) Math.sin(a) * r;
            int c = city.cellOf(x, z);
            if (x > 10 && z > 10 && x < City.SIZE - 10 && z < City.SIZE - 10 && city.connected[c] && field[c] != Integer.MAX_VALUE) {
                go(best, x, hoverAlt(x, z), z, P_INSERT);
                for (int i = 0; i < Math.min(n, 8); i++) { Unit u = spawn(US, hostileKit(), 0, 0, 0); board(u, best); }
                radio(best.call + " is putting a chalk in on your position!");
                return;
            }
            a += 0.7;
        }
    }
    public float sightRange(Unit target) {
        if (!night) return SIGHT_DAY;
        return time - target.lastShot < 1.5f ? SIGHT_DAY : SIGHT_NIGHT;
    }
}
