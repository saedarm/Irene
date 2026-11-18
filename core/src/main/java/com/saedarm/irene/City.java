package com.saedarm.irene;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Random;

/**
 * The map: a compressed Mogadishu. Plain Java, no gdx. Every solid thing is an axis-aligned box.
 * x = east, z = south (north is -z), y = up. Metres.
 *
 * Real layout (see the research report): the target house was one block north of the Olympic
 * Hotel on Hawlwadig Road; Bakara Market lay just west/south-west; Super 6-1 went down a few
 * blocks north-east in a narrow walled alley; Super 6-4 came down among tin shacks about 600 m
 * south of it; National Street runs east-west about five blocks south of the hotel; the
 * Pakistani Stadium is in the north-east; the airfield and K-4 circle are toward the south-west.
 * Real distances are squeezed roughly 2.2x so the whole fight fits in 640 m.
 */
public final class City {
    public static final float CELL = 4f;
    public static final int N = 160;                 // 160 cells * 4 m = 640 m
    public static final float SIZE = N * CELL;
    public static final float STORY = 3.2f;

    public static final int K_HOUSE = 0, K_WALL = 1, K_SHACK = 2, K_LANDMARK = 3, K_WRECK = 4, K_STALL = 5, K_RUBBLE = 6, K_HOTEL = 7, K_TARGET = 8, K_STAIR = 9,
            K_DOOR = 10, K_GATE = 11, K_PLANK = 12, K_INVIS = 13, K_ROOF = 14, K_FURN = 15, K_SILL = 16;

    public static final class Box {
        public final float x0, y0, z0, x1, y1, z1; public final int kind, color;
        /** dead = removed (door kicked in, gate bought, plank torn off). */
        public boolean dead;
        /** Drawn by the dynamic pass, not baked into the city mesh (doors, gates, planks). */
        public boolean dynamic;
        /** Blocks bullets and sight. False for invisible player blockers and planks. */
        public boolean bullets = true;
        /** Forces a nav-edge block even when short (doors, window sills). */
        public boolean navForce;
        Box(float x0, float y0, float z0, float x1, float y1, float z1, int kind, int color) {
            this.x0 = Math.min(x0, x1); this.x1 = Math.max(x0, x1); this.y0 = y0; this.y1 = y1;
            this.z0 = Math.min(z0, z1); this.z1 = Math.max(z0, z1); this.kind = kind; this.color = color;
            dynamic = kind == K_DOOR || kind == K_GATE || kind == K_PLANK;
            bullets = kind != K_INVIS && kind != K_PLANK;
        }
        public boolean visible() { return kind != K_INVIS; }
    }

    /** A hollow one-storey house you can breach. Doors can be kicked or blown. */
    public static final class House {
        public int door = -1;                 // box index of the door (-1 = open doorway)
        public float doorX, doorZ, nx, nz;    // door centre and outward normal
        public float x0, z0, x1, z1;          // interior bounds
        public final ArrayList<float[]> spots = new ArrayList<>(); // interior standing spots {x,z}
        public boolean open() { return door < 0; }
    }
    public final ArrayList<House> houses = new ArrayList<>();
    public final boolean[] edgeE = new boolean[N * N], edgeS = new boolean[N * N]; // wall between cell and its east / south neighbour

    // ---- Siege arena (only when built with siege = true) ----
    public boolean siege;
    public float ax, az; // arena origin (north-west corner)
    public static final float ARENA = 88;
    public static final class Gate { public int box; public int cost; public String name; public int zoneA, zoneB; public float x, z; }
    public static final class Window { public float ix, iz, ox, oz, nx, nz; public int zone; public final ArrayList<Integer> planks = new ArrayList<>(); }
    public static final class Spot { public float x, y, z, nx, nz; public int id, cost; public String name; public int zone; Spot(float x, float y, float z, float nx, float nz) { this.x = x; this.y = y; this.z = z; this.nx = nx; this.nz = nz; } }
    public final ArrayList<Gate> gates = new ArrayList<>();
    public final ArrayList<Window> windows = new ArrayList<>();
    public final ArrayList<Spot> wallBuys = new ArrayList<>(), boxSpots = new ArrayList<>(), perkSpots = new ArrayList<>(), radioParts = new ArrayList<>(), tags = new ArrayList<>();
    public Spot pap, generator, radio, start, wreck;
    public final float[][] zones = new float[4][]; // {x0,z0,x1,z1} absolute

    /** Named places: name -> {x, z}. */
    public final LinkedHashMap<String, float[]> marks = new LinkedHashMap<>();
    public final ArrayList<Box> boxes = new ArrayList<>();
    public final boolean[] road = new boolean[N * N];
    public final boolean[] paved = new boolean[N * N];     // cracked asphalt main roads (the rest is dirt and sand)
    public final boolean[] blocked = new boolean[N * N];   // for AI navigation
    public final float[] roofH = new float[N * N];          // tallest top over the cell (minimap, spawn checks)
    public final boolean[] connected = new boolean[N * N];  // walkable AND reachable from the main streets
    private int[][] cellBoxes;
    public final ArrayList<float[]> tires = new ArrayList<>(); // burning tire barricades {x,z}
    public final ArrayList<float[]> crates = new ArrayList<>(); // ammo crates {x,z}

    // Road plan in cell units: {x0, z0, x1, z1} rectangles, named.
    static final int HAWL_X = 78, SHAL_X = 62, NAT_Z = 108;

    public City(long seed) { this(seed, false); }
    public City(long seed, boolean siege) {
        this.siege = siege;
        Random r = new Random(seed);
        marks.put("Target", new float[]{282, 252});
        marks.put("Olympic Hotel", new float[]{278, 324});
        marks.put("Bakara Market", new float[]{176, 318});
        marks.put("Crash Site 1", new float[]{380, 202});
        marks.put("Crash Site 2", new float[]{372, 566});
        marks.put("National St", new float[]{440, NAT_Z * CELL + 6});
        marks.put("K-4 Circle", new float[]{120, 560});
        marks.put("Pakistani Stadium", new float[]{548, 92});
        marks.put("New Port", new float[]{612, 446});
        marks.put("Airfield", new float[]{40, 616});

        // --- roads ---
        rect(HAWL_X - 2, 0, HAWL_X + 2, N); pave(HAWL_X - 2, 0, HAWL_X + 2, N); // Hawlwadig Road (N-S, wide)
        rect(SHAL_X - 1, 20, SHAL_X + 1, N);               // Shalalawi Street (N-S)
        rect(0, NAT_Z - 2, N, NAT_Z + 2); pave(0, NAT_Z - 2, N, NAT_Z + 2); // National Street (E-W, wide)
        rect(112, 0, 115, N);                              // eastern avenue
        rect(86, 40, 88, 62);                              // wide dirt street west of the 6-1 alley
        rect(0, 30, N, 32);                                // northern road toward the stadium
        diag(30, 140, 78, NAT_Z, 3);                       // road from K-4 up to the hotel corner
        // minor street grid, jittered so blocks are uneven like the old town
        for (int x = 6; x < N - 4; ) { int w = r.nextInt(5) == 0 ? 2 : 1; int z0 = r.nextInt(3) * 8; rect(x, z0, x + w, N - r.nextInt(2) * 10); x += 11 + r.nextInt(7); }
        for (int z = 8; z < N - 4; ) { int w = r.nextInt(5) == 0 ? 2 : 1; int x0 = r.nextInt(3) * 10; rect(x0, z, N - r.nextInt(2) * 8, z + w); z += 10 + r.nextInt(6); }
        // K-4 roundabout
        circleRoad(30, 140, 6);
        rect(6, 139, 31, 142); rect(6, 139, 9, 150); // road from K-4 out to the airfield
        // stadium grounds, airfield apron, market lanes and the crash-site open ground get cleared
        clear(128, 12, 152, 34); clear(0, 146, 22, N); clear(146, 100, N, 124);
        clear(64, 65, 86, 72); // the street in front of the target (fast-rope point)

        // --- landmarks (built before lots; their cells are skipped by the lot pass) ---
        boolean[] reserved = new boolean[N * N];
        // Olympic Hotel: 5 storeys (sources say 4-7), on the west side of Hawlwadig Road
        landmark(reserved, 64, 76, 75, 86, 5, K_HOTEL, 0xE8E2D0);
        stairs(256, 306, 5 * STORY, 0, reserved);
        // Target house: three storeys with a walled compound, one block north of the hotel
        forceGate = 1; compoundAt(reserved, 66, 56, 75, 65, 3, K_TARGET, 0xD9C7A0, r); forceGate = -1;
        stairs(264, 228, 3 * STORY, 0, reserved);
        targetHouse = houses.get(houses.size() - 1);
        // Bakara Market: dense low stalls with awnings
        market(reserved, 32, 70, 56, 92, r);
        // Crash Site 1: a narrow alley with 3 m stone walls on both sides, off a wide dirt street
        alleyCrash(reserved, 88, 50);
        // Crash Site 2: tin-shack belt (Wadigley)
        shackBelt(reserved, 82, 130, 108, 152, r);
        // Pakistani Stadium: a ring of stands
        stadium(reserved, 137, 23);
        // K-4 monument
        add(120 - 2.5f, 0, 560 - 2.5f, 120 + 2.5f, 4.5f, 560 + 2.5f, K_LANDMARK, 0xF2F0EA);
        add(120 - .8f, 0, 560 - .8f, 120 + .8f, 9f, 560 + .8f, K_LANDMARK, 0xF2F0EA);
        // New Port: warehouses on the east edge
        for (int i = 0; i < 4; i++) add(574 + i * 14, 0, 404, 584 + i * 14, 7, 428, K_LANDMARK, 0x9AA3A8);
        // a mosque with a white tower near the market
        add(228, 0, 354, 242, 7, 366, K_LANDMARK, 0xF4F1E8); add(238, 0, 350, 242, 22, 354, K_LANDMARK, 0xF4F1E8);
        markRect(reserved, 56, 87, 61, 92); markRect(reserved, 142, 100, N, 108);
        if (siege) arena(reserved, r);

        // --- lots: walled compounds, plain houses, shacks, rubble ---
        boolean[] used = new boolean[N * N];
        for (int i = 0; i < N * N; i++) used[i] = road[i] || reserved[i];
        for (int z = 0; z < N; z++) for (int x = 0; x < N; x++) {
            if (used[z * N + x]) continue;
            int w = 3 + r.nextInt(4), d = 3 + r.nextInt(4);
            int x1 = x, z1 = z;
            while (x1 - x < w && x1 < N && !used[z * N + x1]) x1++;
            while (z1 - z < d && z1 < N && free(used, x, z1, x1)) z1++;
            if (x1 - x < 2 || z1 - z < 2) { used[z * N + x] = true; rubble(x, z, r); continue; }
            for (int zz = z; zz < z1; zz++) for (int xx = x; xx < x1; xx++) used[zz * N + xx] = true;
            float roll = r.nextFloat();
            boolean shackZone = z > 118 || x < 20;
            if (shackZone && roll < 0.6f) shacks(x, z, x1, z1, r);
            else if (roll < 0.70f) compound(x, z, x1, z1, 1 + (r.nextFloat() < 0.35f ? 1 : 0) + (r.nextFloat() < 0.06f ? 1 : 0), K_HOUSE, houseColor(r), r);
            else if (roll < 0.90f) {
                if (x1 - x >= 3 && z1 - z >= 3 && r.nextFloat() < 0.55f) breachHouse(x, z, x1, z1, houseColor(r), -1, r);
                else house(x, z, x1, z1, 1 + (r.nextFloat() < 0.4f ? 1 : 0), houseColor(r), r);
            }
            else rubbleLot(x, z, x1, z1, r);
        }
        // the edges are the sea (east) and open scrub; keep a low wall so nobody walks off the map
        add(-2, 0, -2, 0, 4, SIZE + 2, K_WALL, 0x8A7D66); add(SIZE, 0, -2, SIZE + 2, 4, SIZE + 2, K_WALL, 0x8A7D66);
        add(-2, 0, -2, SIZE + 2, 4, 0, K_WALL, 0x8A7D66); add(-2, 0, SIZE, SIZE + 2, 4, SIZE + 2, K_WALL, 0x8A7D66);

        // burning-tire barricades at intersections, ammo crates in courtyards
        for (int i = 0; i < 60; i++) {
            int x = 4 + r.nextInt(N - 8), z = 4 + r.nextInt(N - 8);
            if (road[z * N + x] && road[z * N + x + 1] && road[(z + 1) * N + x]) tires.add(new float[]{x * CELL + 2, z * CELL + 2});
        }
        buildIndex();
        flood(NAT_Z * N + HAWL_X);
        if (siege) flood(cellOf(start.x, start.z));
        for (int i = 0; i < 400 && crates.size() < 26; i++) {
            int x = 4 + r.nextInt(N - 8), z = 4 + r.nextInt(N - 8);
            if (!road[z * N + x] && connected[z * N + x] && !blocked[z * N + x + 1] && !inArena(x * CELL, z * CELL)) crates.add(new float[]{x * CELL + 2, z * CELL + 2});
        }
    }

    private void flood(int start) {
        if (blocked[start]) return;
        int[] q = new int[N * N]; int h = 0, t = 0; q[t++] = start; connected[start] = true;
        while (h < t) {
            int c = q[h++], cx = c % N, cz = c / N;
            int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : nb) { int nx = cx + d[0], nz = cz + d[1]; if (pass(cx, cz, d[0], d[1]) && !connected[nz * N + nx]) { connected[nz * N + nx] = true; q[t++] = nz * N + nx; } }
        }
    }

    static int houseColor(Random r) {
        int[] p = {0xE9E4D6, 0xDCCFB4, 0xCFB991, 0xE3D3B0, 0xC9A77C, 0xB9C2C3, 0xD8C6A6, 0xEDE8DC, 0xA8B6AE};
        return p[r.nextInt(p.length)];
    }

    private boolean free(boolean[] used, int x0, int z, int x1) { for (int x = x0; x < x1; x++) if (used[z * N + x]) return false; return true; }
    private void rect(int x0, int z0, int x1, int z1) { for (int z = Math.max(0, z0); z < Math.min(N, z1); z++) for (int x = Math.max(0, x0); x < Math.min(N, x1); x++) road[z * N + x] = true; }
    private void pave(int x0, int z0, int x1, int z1) { for (int z = Math.max(0, z0); z < Math.min(N, z1); z++) for (int x = Math.max(0, x0); x < Math.min(N, x1); x++) paved[z * N + x] = true; }
    private void clear(int x0, int z0, int x1, int z1) { rect(x0, z0, x1, z1); }
    private void markRect(boolean[] res, int x0, int z0, int x1, int z1) { for (int z = z0; z < z1; z++) for (int x = x0; x < x1; x++) res[z * N + x] = true; }
    private void diag(int ax, int az, int bx, int bz, int w) {
        int steps = Math.max(Math.abs(bx - ax), Math.abs(bz - az));
        for (int i = 0; i <= steps; i++) { int x = ax + (bx - ax) * i / steps, z = az + (bz - az) * i / steps; rect(x - w / 2, z - w / 2, x + w - w / 2, z + w - w / 2); }
    }
    private void circleRoad(int cx, int cz, int rad) {
        for (int z = cz - rad - 2; z <= cz + rad + 2; z++) for (int x = cx - rad - 2; x <= cx + rad + 2; x++) {
            double d = Math.hypot(x - cx, z - cz); if (d <= rad + 2 && x >= 0 && z >= 0 && x < N && z < N) road[z * N + x] = true;
        }
    }

    int add(float x0, float y0, float z0, float x1, float y1, float z1, int kind, int color) { boxes.add(new Box(x0, y0, z0, x1, y1, z1, kind, color)); return boxes.size() - 1; }

    private void landmark(boolean[] res, int cx0, int cz0, int cx1, int cz1, int stories, int kind, int color) {
        markRect(res, cx0 - 1, cz0 - 1, cx1 + 1, cz1 + 1);
        float x0 = cx0 * CELL, z0 = cz0 * CELL, x1 = cx1 * CELL, z1 = cz1 * CELL, h = stories * STORY;
        add(x0, 0, z0, x1, h, z1, kind, color);
        parapet(x0, z0, x1, z1, h, color);
    }
    private void parapet(float x0, float z0, float x1, float z1, float h, int color) {
        float t = 0.25f, p = 0.9f;
        add(x0, h, z0, x1, h + p, z0 + t, K_WALL, color); add(x0, h, z1 - t, x1, h + p, z1, K_WALL, color);
        add(x0, h, z0, x0 + t, h + p, z1, K_WALL, color); add(x1 - t, h, z0, x1, h + p, z1, K_WALL, color);
    }
    /** External staircase up the side of a building, so roofs are reachable without parkour. dir 0 = runs south, 1 = runs east. */
    private void stairs(float x, float z, float top, int dir, boolean[] res) {
        int steps = (int) Math.ceil(top / 0.45f);
        for (int i = 0; i < steps; i++) {
            float h = Math.min(top, (i + 1) * 0.45f);
            if (dir == 0) add(x - 1.6f, 0, z + i * 0.55f, x, h, z + (i + 1) * 0.55f, K_STAIR, 0xBFAE8E);
            else add(x + i * 0.55f, 0, z - 1.6f, x + (i + 1) * 0.55f, h, z, K_STAIR, 0xBFAE8E);
        }
    }

    private void compoundAt(boolean[] res, int cx0, int cz0, int cx1, int cz1, int stories, int kind, int color, Random r) {
        markRect(res, cx0, cz0, cx1, cz1);
        compound(cx0, cz0, cx1, cz1, stories, kind, color, r);
    }

    /** A walled courtyard (about 3 m walls, a gate toward the nearest road) with a house at the back. */
    private void compound(int cx0, int cz0, int cx1, int cz1, int stories, int kind, int color, Random r) {
        float x0 = cx0 * CELL + .3f, z0 = cz0 * CELL + .3f, x1 = cx1 * CELL - .3f, z1 = cz1 * CELL - .3f;
        float wh = 2.6f + r.nextFloat() * 0.6f, t = 0.3f;
        int wallColor = r.nextBoolean() ? color : 0xC2B08E;
        // gate side: the side that touches a road
        int gate = forceGate >= 0 ? forceGate : gateSide(cx0, cz0, cx1, cz1);
        int gcx = (cx0 + cx1) / 2, gcz = (cz0 + cz1) / 2;
        float gx = gcx * CELL, gz = gcz * CELL; // gate is one cell wide
        if (gate == 0) { add(x0, 0, z0, gx, wh, z0 + t, K_WALL, wallColor); add(gx + CELL, 0, z0, x1, wh, z0 + t, K_WALL, wallColor); } else add(x0, 0, z0, x1, wh, z0 + t, K_WALL, wallColor);
        if (gate == 1) { add(x0, 0, z1 - t, gx, wh, z1, K_WALL, wallColor); add(gx + CELL, 0, z1 - t, x1, wh, z1, K_WALL, wallColor); } else add(x0, 0, z1 - t, x1, wh, z1, K_WALL, wallColor);
        if (gate == 2) { add(x0, 0, z0, x0 + t, wh, gz, K_WALL, wallColor); add(x0, 0, gz + CELL, x0 + t, wh, z1, K_WALL, wallColor); } else add(x0, 0, z0, x0 + t, wh, z1, K_WALL, wallColor);
        if (gate == 3) { add(x1 - t, 0, z0, x1, wh, gz, K_WALL, wallColor); add(x1 - t, 0, gz + CELL, x1, wh, z1, K_WALL, wallColor); } else add(x1 - t, 0, z0, x1, wh, z1, K_WALL, wallColor);
        // house on the side away from the gate, leaving a courtyard
        float hx0 = x0 + 1.2f, hz0 = z0 + 1.2f, hx1 = x1 - 1.2f, hz1 = z1 - 1.2f;
        float spanX = hx1 - hx0, spanZ = hz1 - hz0;
        if (gate == 0) hz0 += spanZ * 0.4f; else if (gate == 1) hz1 -= spanZ * 0.4f; else if (gate == 2) hx0 += spanX * 0.4f; else hx1 -= spanX * 0.4f;
        float h = stories * STORY;
        if (kind == K_TARGET) {
            // the target: a hollow two-room house at the back of the courtyard (cell aligned), door facing the gate
            int bx0 = cx0 + 1, bx1 = cx1 - 1, bz0 = cz0 + 1, bz1 = cz0 + 1 + Math.max(3, (cz1 - cz0) / 2);
            breachHouse(bx0, bz0, bx1, bz1, color, 1, r);
            add(bx0 * CELL, 3.2f, bz0 * CELL, (bx0 + 2) * CELL, 6.4f, bz1 * CELL, K_TARGET, color); // solid upper storey on one side
            return;
        }
        add(hx0, 0, hz0, hx1, h, hz1, kind, color);
        if (r.nextFloat() < 0.6f) parapet(hx0, hz0, hx1, hz1, h, color);
        // a low step/box next to the house wall: a parkour route up (crate -> wall -> roof)
        if (r.nextFloat() < 0.7f) add(hx0 - 0.9f, 0, hz0 + 0.3f, hx0, 1.1f, hz0 + 1.4f, K_RUBBLE, 0x8F7A5C);
    }
    private int forceGate = -1;
    public House targetHouse;
    private int gateSide(int cx0, int cz0, int cx1, int cz1) {
        int mx = (cx0 + cx1) / 2, mz = (cz0 + cz1) / 2;
        if (cz0 > 0 && road[(cz0 - 1) * N + mx]) return 0;
        if (cz1 < N && road[cz1 * N + mx]) return 1;
        if (cx0 > 0 && road[mz * N + cx0 - 1]) return 2;
        if (cx1 < N && road[mz * N + cx1]) return 3;
        return 0;
    }
    private void house(int cx0, int cz0, int cx1, int cz1, int stories, int color, Random r) {
        float x0 = cx0 * CELL + .6f, z0 = cz0 * CELL + .6f, x1 = cx1 * CELL - .6f, z1 = cz1 * CELL - .6f, h = stories * STORY;
        add(x0, 0, z0, x1, h, z1, K_HOUSE, color);
        if (stories == 1 && r.nextFloat() < 0.5f) add(x0 - 0.8f, 0, z0 + 0.5f, x0, 1.2f, z0 + 1.6f, K_RUBBLE, 0x8F7A5C);
        if (r.nextFloat() < 0.5f) parapet(x0, z0, x1, z1, h, color);
    }
    private void shacks(int cx0, int cz0, int cx1, int cz1, Random r) {
        for (int z = cz0; z < cz1; z++) for (int x = cx0; x < cx1; x++) {
            if (r.nextFloat() < 0.35f) continue;
            float jx = r.nextFloat() * 0.8f, jz = r.nextFloat() * 0.8f;
            int c = r.nextBoolean() ? 0x8C6A4F : (r.nextBoolean() ? 0x9FA3A0 : 0x7E5F45);
            add(x * CELL + .4f + jx, 0, z * CELL + .4f + jz, x * CELL + 3.2f + jx * .3f, 2.2f + r.nextFloat() * 0.5f, z * CELL + 3.2f + jz * .3f, K_SHACK, c);
        }
    }
    private void rubble(int x, int z, Random r) {
        if (r.nextFloat() < 0.5f) return;
        float h = 0.4f + r.nextFloat() * 0.7f;
        add(x * CELL + .8f, 0, z * CELL + .8f, x * CELL + 2.6f + r.nextFloat(), h, z * CELL + 2.6f + r.nextFloat(), K_RUBBLE, 0x9B8C74);
    }
    private void rubbleLot(int cx0, int cz0, int cx1, int cz1, Random r) {
        // a bombed-out shell: two broken walls and rubble heaps
        float x0 = cx0 * CELL + .5f, z0 = cz0 * CELL + .5f, x1 = cx1 * CELL - .5f, z1 = cz1 * CELL - .5f;
        add(x0, 0, z0, x0 + .4f, 2f + r.nextFloat() * 3, z1 - 2, K_WALL, 0xB8A88A);
        add(x0, 0, z0, x1 - 3, 1.5f + r.nextFloat() * 3, z0 + .4f, K_WALL, 0xB8A88A);
        for (int i = 0; i < 3; i++) rubble(cx0 + r.nextInt(Math.max(1, cx1 - cx0)), cz0 + r.nextInt(Math.max(1, cz1 - cz0)), r);
    }
    private void market(boolean[] res, int cx0, int cz0, int cx1, int cz1, Random r) {
        markRect(res, cx0, cz0, cx1, cz1);
        for (int z = cz0; z < cz1; z += 2) for (int x = cx0; x < cx1; x += 3) {
            if ((x - cx0) % 9 == 6 || (z - cz0) % 8 == 6) { road[z * N + x] = true; continue; } // lanes
            float bx = x * CELL + .5f, bz = z * CELL + .5f;
            int c = new int[]{0xB5482F, 0x2F6E8C, 0xD6A33C, 0x6E8C3A, 0x8C3A6E}[r.nextInt(5)];
            add(bx, 0, bz, bx + 10.5f, 1.0f, bz + 6.5f, K_STALL, 0x8B6B47);            // counter
            add(bx - .3f, 2.6f, bz - .3f, bx + 10.8f, 2.8f, bz + 6.8f, K_STALL, c);     // awning
            add(bx, 0, bz, bx + .3f, 2.6f, bz + .3f, K_STALL, 0x6B5236); add(bx + 10.2f, 0, bz, bx + 10.5f, 2.6f, bz + .3f, K_STALL, 0x6B5236);
        }
    }
    private void alleyCrash(boolean[] res, int cx, int cz) {
        // wide dirt street to the west, narrow alley running east with 3 m stone walls
        markRect(res, cx, cz - 3, cx + 12, cz + 4);
        for (int x = cx; x < cx + 12; x++) road[cz * N + x] = true;
        float zA = cz * CELL - 0.3f, zB = (cz + 1) * CELL + 0.3f;
        add(cx * CELL + 4, 0, zA - 0.5f, (cx + 12) * CELL, 3.1f, zA, K_WALL, 0xB3A07E);
        add(cx * CELL + 4, 0, zB, (cx + 12) * CELL, 3.1f, zB + 0.5f, K_WALL, 0xB3A07E);
        add(cx * CELL + 6, 0, (cz - 3) * CELL, (cx + 6) * CELL, 6.4f, zA - 0.6f, K_HOUSE, 0xDCCFB4);
        add(cx * CELL + 6, 0, zB + 0.6f, (cx + 6) * CELL, 3.2f, (cz + 4) * CELL, K_HOUSE, 0xE3D3B0);
        add((cx + 7) * CELL, 0, (cz - 3) * CELL, (cx + 12) * CELL, 3.2f, zA - 0.6f, K_HOUSE, 0xCFB991);
        add((cx + 7) * CELL, 0, zB + 0.6f, (cx + 12) * CELL, 6.4f, (cz + 4) * CELL, K_HOUSE, 0xD8C6A6);
    }
    private void shackBelt(boolean[] res, int cx0, int cz0, int cx1, int cz1, Random r) {
        markRect(res, cx0, cz0, cx1, cz1);
        int ccx = (int) (372 / CELL), ccz = (int) (566 / CELL);
        for (int z = cz0; z < cz1; z++) for (int x = cx0; x < cx1; x++) {
            if (Math.abs(x - ccx) < 4 && Math.abs(z - ccz) < 4) { road[z * N + x] = true; continue; } // open patch where 6-4 lies
            if ((x - cx0) % 5 == 0 || (z - cz0) % 6 == 0) { road[z * N + x] = true; continue; }
            if (r.nextFloat() < 0.25f) continue;
            float jx = r.nextFloat() * .6f, jz = r.nextFloat() * .6f;
            add(x * CELL + .3f + jx, 0, z * CELL + .3f + jz, x * CELL + 3.4f, 2.1f + r.nextFloat() * .5f, z * CELL + 3.4f, K_SHACK, r.nextBoolean() ? 0x8C6A4F : 0x9FA3A0);
        }
    }
    private void stadium(boolean[] res, int cx, int cz) {
        markRect(res, cx - 11, cz - 11, cx + 12, cz + 12);
        float ox = cx * CELL, oz = cz * CELL;
        for (int i = 0; i < 28; i++) {
            double a = i * Math.PI * 2 / 28; if (i == 7 || i == 21) continue; // two entrances
            float x = ox + (float) Math.cos(a) * 40, z = oz + (float) Math.sin(a) * 30;
            add(x - 4.5f, 0, z - 4.5f, x + 4.5f, 6.5f, z + 4.5f, K_LANDMARK, 0xCFC6B4);
        }
    }


    // ------------------------------------------------------------------ hollow houses
    private static final int H_DOOR = 0, H_WINDOW = 1, H_OPEN = 2;
    /** A wall running along x (thin in z) from x0 to x1, with holes {start, end, type}. Returns the door box index or -1. */
    private int wallX(float x0, float x1, float z0, float z1, float h, int color, float[][] holes) {
        int door = -1; float cur = x0;
        for (float[] o : holes) {
            if (o[0] > cur) add(cur, 0, z0, o[0], h, z1, K_WALL, color);
            door = Math.max(door, hole(true, o, z0, z1, h, color));
            cur = o[1];
        }
        if (cur < x1) add(cur, 0, z0, x1, h, z1, K_WALL, color);
        return door;
    }
    private int wallZ(float z0, float z1, float x0, float x1, float h, int color, float[][] holes) {
        int door = -1; float cur = z0;
        for (float[] o : holes) {
            if (o[0] > cur) add(x0, 0, cur, x1, h, o[0], K_WALL, color);
            door = Math.max(door, hole(false, o, x0, x1, h, color));
            cur = o[1];
        }
        if (cur < z1) add(x0, 0, cur, x1, h, z1, K_WALL, color);
        return door;
    }
    private int hole(boolean alongX, float[] o, float a0, float a1, float h, int color) {
        int type = (int) o[2];
        float mid = (a0 + a1) / 2;
        if (type == H_DOOR) {
            box(alongX, o[0], o[1], a0, a1, 2.2f, h, K_WALL, color);
            int d = box(alongX, o[0], o[1], mid - 0.07f, mid + 0.07f, 0, 2.2f, K_DOOR, 0x6B4A2E);
            boxes.get(d).navForce = true; return d;
        } else if (type == H_WINDOW) {
            int s = box(alongX, o[0], o[1], a0, a1, 0, 1.0f, K_SILL, color); boxes.get(s).navForce = true;
            box(alongX, o[0], o[1], a0, a1, 2.1f, h, K_WALL, color);
            box(alongX, o[0], o[1], a0, a1, 1.0f, 2.1f, K_INVIS, 0); // you can shoot through a window but not climb out of it
        } else box(alongX, o[0], o[1], a0, a1, 2.2f, h, K_WALL, color);
        return -1;
    }
    private int box(boolean alongX, float s0, float s1, float a0, float a1, float y0, float y1, int kind, int color) {
        return alongX ? add(s0, y0, a0, s1, y1, a1, kind, color) : add(a0, y0, s0, a1, y1, s1, kind, color);
    }

    /** A hollow one-storey house filling the cells [cx0,cx1) x [cz0,cz1). doorSide 0 N, 1 S, 2 W, 3 E, -1 = toward a road. */
    private House breachHouse(int cx0, int cz0, int cx1, int cz1, int color, int doorSide, Random r) { return breachHouse(cx0, cz0, cx1, cz1, color, doorSide, r, false); }
    private House breachHouse(int cx0, int cz0, int cx1, int cz1, int color, int doorSide, Random r, boolean openDoorway) {
        House hs = new House();
        float X0 = cx0 * CELL, Z0 = cz0 * CELL, X1 = cx1 * CELL, Z1 = cz1 * CELL, t = 0.3f, H = 3.0f;
        int side = doorSide >= 0 ? doorSide : gateSide(cx0, cz0, cx1, cz1);
        int dcx = (cx0 + cx1) / 2, dcz = (cz0 + cz1) / 2;
        int dType = openDoorway ? H_OPEN : H_DOOR;
        float[] doorX = {dcx * CELL + 1.4f, dcx * CELL + 2.6f, dType}, doorZ = {dcz * CELL + 1.4f, dcz * CELL + 2.6f, dType};
        int wcx = cx0 + 1 + r.nextInt(Math.max(1, cx1 - cx0 - 2)), wcz = cz0 + 1 + r.nextInt(Math.max(1, cz1 - cz0 - 2));
        float[] winX = {wcx * CELL + 1.2f, wcx * CELL + 2.8f, H_WINDOW}, winZ = {wcz * CELL + 1.2f, wcz * CELL + 2.8f, H_WINDOW};
        int d0 = wallX(X0, X1, Z0, Z0 + t, H, color, side == 0 ? new float[][]{doorX} : (wcx == dcx && side != 1 ? new float[0][] : new float[][]{winX}));
        int d1 = wallX(X0, X1, Z1 - t, Z1, H, color, side == 1 ? new float[][]{doorX} : new float[][]{winX});
        int d2 = wallZ(Z0 + t, Z1 - t, X0, X0 + t, H, color, side == 2 ? new float[][]{doorZ} : new float[][]{winZ});
        int d3 = wallZ(Z0 + t, Z1 - t, X1 - t, X1, H, color, side == 3 ? new float[][]{doorZ} : new float[][]{winZ});
        hs.door = Math.max(Math.max(d0, d1), Math.max(d2, d3));
        if (side == 0) { hs.doorX = dcx * CELL + 2; hs.doorZ = Z0; hs.nx = 0; hs.nz = -1; }
        else if (side == 1) { hs.doorX = dcx * CELL + 2; hs.doorZ = Z1; hs.nx = 0; hs.nz = 1; }
        else if (side == 2) { hs.doorX = X0; hs.doorZ = dcz * CELL + 2; hs.nx = -1; hs.nz = 0; }
        else { hs.doorX = X1; hs.doorZ = dcz * CELL + 2; hs.nx = 1; hs.nz = 0; }
        // a dividing wall with an open doorway when the house is big enough: two rooms to clear
        if (cx1 - cx0 >= 4) { float xm = ((cx0 + cx1) / 2) * CELL; int rz = cz0 + r.nextInt(cz1 - cz0); wallZ(Z0 + t, Z1 - t, xm - 0.15f, xm + 0.15f, H, color, new float[][]{{rz * CELL + 1.3f, rz * CELL + 2.7f, H_OPEN}}); }
        else if (cz1 - cz0 >= 4) { float zm = ((cz0 + cz1) / 2) * CELL; int rx = cx0 + r.nextInt(cx1 - cx0); wallX(X0 + t, X1 - t, zm - 0.15f, zm + 0.15f, H, color, new float[][]{{rx * CELL + 1.3f, rx * CELL + 2.7f, H_OPEN}}); }
        add(X0, H, Z0, X1, H + 0.3f, Z1, K_ROOF, color);
        if (r.nextFloat() < 0.5f) parapet(X0, Z0, X1, Z1, H + 0.3f, color);
        // a mattress and a crate (cover) inside, plus rubble outside to climb on the roof
        int fx = cx0 + r.nextInt(cx1 - cx0), fz = cz0 + r.nextInt(cz1 - cz0);
        add(fx * CELL + 0.6f, 0, fz * CELL + 0.6f, fx * CELL + 2.4f, 0.15f, fz * CELL + 1.6f, K_FURN, 0x8A6E5A);
        add(X1 - 1.3f, 0, Z1 - 1.3f, X1 - 0.4f, 0.9f, Z1 - 0.4f, K_FURN, 0x6B5A3E);
        if (side != 2) add(X0 - 0.9f, 0, Z0 + 1.0f, X0, 1.3f, Z0 + 2.1f, K_RUBBLE, 0x8F7A5C);
        hs.x0 = X0 + t; hs.z0 = Z0 + t; hs.x1 = X1 - t; hs.z1 = Z1 - t;
        for (int z = cz0; z < cz1; z++) for (int x = cx0; x < cx1; x++) hs.spots.add(new float[]{x * CELL + 2, z * CELL + 2});
        houses.add(hs);
        return hs;
    }

    // ------------------------------------------------------------------ Siege arena (zombies-style)
    public boolean inArena(float x, float z) { return siege && x >= ax && z >= az && x <= ax + ARENA && z <= az + ARENA; }
    public int zoneOf(float x, float z) {
        if (!inArena(x, z)) return -1;
        boolean east = x >= ax + 44, south = z >= az + 44;
        return south ? (east ? 2 : 0) : (east ? 3 : 1);
    }
    private Spot spot(float rx, float y, float rz, float nx, float nz) { return new Spot(ax + rx, y, az + rz, nx, nz); }
    private void wallBuy(float rx, float y, float rz, float nx, float nz, int weapon, int cost, String name) {
        Spot s = spot(rx, y, rz, nx, nz); s.id = weapon; s.cost = cost; s.name = name; s.zone = zoneOf(s.x - nx, s.z - nz); wallBuys.add(s);
    }
    private void perk(float rx, float rz, int id, int cost, String name) { Spot s = spot(rx, 0, rz, 0, 0); s.id = id; s.cost = cost; s.name = name; s.zone = zoneOf(s.x, s.z); perkSpots.add(s); add(s.x - 0.8f, 0, s.z - 0.6f, s.x + 0.8f, 1.9f, s.z + 0.6f, K_FURN, 0x55524A); }
    private void window(boolean alongX, float pos, float wall0, float wall1, float nx, float nz, float h, int color) {
        float[] o = {pos - 0.8f, pos + 0.8f};
        // sill, lintel, invisible blocker, planks
        int s = alongX ? add(o[0], 0, wall0, o[1], 1.0f, wall1, K_SILL, color) : add(wall0, 0, o[0], wall1, 1.0f, o[1], K_SILL, color);
        boxes.get(s).navForce = true;
        if (alongX) { add(o[0], 2.4f, wall0, o[1], h, wall1, K_WALL, color); add(o[0], 1.0f, wall0, o[1], 2.4f, wall1, K_INVIS, 0); }
        else { add(wall0, 2.4f, o[0], wall1, h, o[1], K_WALL, color); add(wall0, 1.0f, o[0], wall1, 2.4f, o[1], K_INVIS, 0); }
        Window w = new Window(); w.nx = nx; w.nz = nz;
        float cx = alongX ? pos : (wall0 + wall1) / 2, cz = alongX ? (wall0 + wall1) / 2 : pos;
        w.ix = cx - nx * 1.7f; w.iz = cz - nz * 1.7f; w.ox = cx + nx * 3.5f; w.oz = cz + nz * 3.5f; w.zone = zoneOf(w.ix, w.iz);
        float inner = alongX ? (nz > 0 ? wall0 - 0.08f : wall1 + 0.0f) : (nx > 0 ? wall0 - 0.08f : wall1);
        for (int k = 0; k < 5; k++) {
            float y = 1.12f + k * 0.25f;
            int b = alongX ? add(o[0] - 0.1f, y, inner, o[1] + 0.1f, y + 0.17f, inner + 0.08f, K_PLANK, 0x7A5A3A) : add(inner, y, o[0] - 0.1f, inner + 0.08f, y + 0.17f, o[1] + 0.1f, K_PLANK, 0x7A5A3A);
            w.planks.add(b);
        }
        windows.add(w);
    }
    private void outerWall(boolean alongX, float fixed0, float fixed1, float nx, float nz, float[] winPos) {
        float H = 6f; int col = 0xB8A684;
        float a = alongX ? ax : az, len = ARENA, cur = a;
        java.util.Arrays.sort(winPos);
        for (float wp : winPos) {
            float p = a + wp;
            if (p - 0.8f > cur) { if (alongX) add(cur, 0, fixed0, p - 0.8f, H, fixed1, K_WALL, col); else add(fixed0, 0, cur, fixed1, H, p - 0.8f, K_WALL, col); }
            window(alongX, p, fixed0, fixed1, nx, nz, H, col);
            cur = p + 0.8f;
        }
        if (cur < a + len) { if (alongX) add(cur, 0, fixed0, a + len, H, fixed1, K_WALL, col); else add(fixed0, 0, cur, fixed1, H, a + len, K_WALL, col); }
    }
    private void innerWall(boolean alongX, float fixed, float[][] gateRanges, int[] costs, int[][] zonePairs, String[] names) {
        float H = 4.5f, t = 0.2f; int col = 0xC9B48C; float cur = 0;
        for (int i = 0; i < gateRanges.length; i++) {
            float g0 = gateRanges[i][0], g1 = gateRanges[i][1];
            if (g0 > cur) { if (alongX) add(ax + cur, 0, fixed - t, ax + g0, H, fixed + t, K_WALL, col); else add(fixed - t, 0, az + cur, fixed + t, H, az + g0, K_WALL, col); }
            add(alongX ? ax + g0 : fixed - t, 4.0f, alongX ? fixed - t : az + g0, alongX ? ax + g1 : fixed + t, H, alongX ? fixed + t : az + g1, K_WALL, col);
            Gate gt = new Gate();
            gt.box = alongX ? add(ax + g0, 0, fixed - 0.25f, ax + g1, 4.0f, fixed + 0.25f, K_GATE, 0x5E6A70) : add(fixed - 0.25f, 0, az + g0, fixed + 0.25f, 4.0f, az + g1, K_GATE, 0x5E6A70);
            gt.cost = costs[i]; gt.zoneA = zonePairs[i][0]; gt.zoneB = zonePairs[i][1]; gt.name = names[i];
            gt.x = alongX ? ax + (g0 + g1) / 2 : fixed; gt.z = alongX ? fixed : az + (g0 + g1) / 2;
            gates.add(gt);
            cur = g1;
        }
        if (cur < ARENA) { if (alongX) add(ax + cur, 0, fixed - t, ax + ARENA, H, fixed + t, K_WALL, col); else add(fixed - t, 0, az + cur, fixed + t, H, az + ARENA, K_WALL, col); }
    }
    private void arena(boolean[] res, Random r) {
        int acx = 116, acz = 44, n = 22; ax = acx * CELL; az = acz * CELL;
        for (int z = acz - 1; z < acz + n + 1; z++) for (int x = acx - 1; x < acx + n + 1; x++) { res[z * N + x] = true; if (z >= acz && x >= acx && z < acz + n && x < acx + n) { road[z * N + x] = false; paved[z * N + x] = false; } else road[z * N + x] = true; }
        zones[0] = new float[]{ax, az + 44, ax + 44, az + 88}; zones[1] = new float[]{ax, az, ax + 44, az + 44};
        zones[2] = new float[]{ax + 44, az + 44, ax + 88, az + 88}; zones[3] = new float[]{ax + 44, az, ax + 88, az + 44};
        float T = 0.6f;
        outerWall(true, az, az + T, 0, -1, new float[]{12, 32, 58, 78});
        outerWall(true, az + ARENA - T, az + ARENA, 0, 1, new float[]{14, 34, 56, 76});
        outerWall(false, ax, ax + T, -1, 0, new float[]{20, 60, 78});
        outerWall(false, ax + ARENA - T, ax + ARENA, 1, 0, new float[]{24, 64});
        innerWall(false, ax + 44, new float[][]{{18, 26}, {62, 70}}, new int[]{1000, 750}, new int[][]{{1, 3}, {0, 2}}, new String[]{"Crash Site", "Market"});
        innerWall(true, az + 44, new float[][]{{18, 26}, {62, 70}}, new int[]{750, 1000}, new int[][]{{0, 1}, {2, 3}}, new String[]{"Hotel Yard", "Crash Site"});
        // A: the courtyard. A hollow house with an open doorway; the radio sits inside.
        breachHouse(acx + 1, acz + 16, acx + 5, acz + 20, 0xE3D3B0, 0, r, true);
        start = spot(22, 0, 62, 0, 0);
        radio = spot(6, 0, 78, 0, 0); add(radio.x - 0.5f, 0, radio.z - 0.4f, radio.x + 0.5f, 0.9f, radio.z + 0.4f, K_FURN, 0x4A4A3A);
        add(ax + 26, 0, az + 70, ax + 29, 1.1f, az + 72, K_RUBBLE, 0x8F7A5C); add(ax + 32, 0, az + 52, ax + 38, 1.0f, az + 53, K_WALL, 0xA89870); // sandbags
        add(ax + 14, 0, az + 50, ax + 16, 1.1f, az + 52, K_RUBBLE, 0x8F7A5C);
        wallBuy(0.65f, 1.5f, 52, 1, 0, Battle.W_AK, 600, "AK-47");
        wallBuy(24, 1.5f, ARENA - 0.65f, 0, -1, Battle.W_M16, 500, "M16A2");
        wallBuy(43.75f, 1.5f, 80, -1, 0, Battle.W_870, 900, "Remington 870");
        Spot b1 = spot(36, 0, 76, 0, 0); boxSpots.add(b1);
        // B: hotel yard. A two-storey block with an annex to climb (a radio part on the roof), the generator, two perk stalls
        add(ax + 6, 0, az + 6, ax + 18, 6.4f, az + 18, K_HOUSE, 0xE8E2D0); parapet(ax + 6, az + 6, ax + 18, az + 18, 6.4f, 0xE8E2D0);
        add(ax + 18, 0, az + 10, ax + 22, 3.2f, az + 16, K_HOUSE, 0xDCCFB4); add(ax + 22, 0, az + 12, ax + 23.2f, 1.2f, az + 13.4f, K_RUBBLE, 0x8F7A5C);
        radioParts.add(spot(9, 6.4f, 9, 0, 0));
        generator = spot(30, 0, 8, 0, 0); add(generator.x - 1, 0, generator.z - 0.7f, generator.x + 1, 1.5f, generator.z + 0.7f, K_FURN, 0x3E5A3A);
        perk(36, 32, 0, 2500, "Body Armor"); perk(6, 36, 3, 500, "Second Wind");
        wallBuy(34, 1.5f, 43.75f, 0, -1, Battle.W_MP5, 1000, "MP5");
        wallBuy(0.65f, 1.5f, 30, 1, 0, Battle.W_M14, 1200, "M14");
        // C: market rows
        for (int row = 0; row < 3; row++) for (int col = 0; col < 2; col++) {
            float x0 = ax + 50 + col * 16, z0 = az + 52 + row * 12;
            add(x0, 0, z0, x0 + 10, 1.0f, z0 + 2.5f, K_STALL, 0x8B6B47);
            add(x0 - 0.3f, 2.6f, z0 - 0.5f, x0 + 10.3f, 2.8f, z0 + 3.0f, K_STALL, new int[]{0xB5482F, 0x2F6E8C, 0xD6A33C, 0x6E8C3A}[(row + col) % 4]);
            add(x0, 0, z0, x0 + 0.3f, 2.6f, z0 + 0.3f, K_STALL, 0x6B5236); add(x0 + 9.7f, 0, z0, x0 + 10, 2.6f, z0 + 0.3f, K_STALL, 0x6B5236);
        }
        radioParts.add(spot(55, 0.3f, 67.0f, 0, 0));
        boxSpots.add(spot(82, 0, 82, 0, 0));
        perk(84, 48, 2, 2000, "Double Tap");
        wallBuy(66, 1.5f, ARENA - 0.65f, 0, -1, Battle.W_M79, 1500, "M79 Thumper");
        wallBuy(ARENA - 0.65f, 1.5f, 80, -1, 0, Battle.W_PKM, 1600, "PKM");
        // D: the crash site. A wrecked Black Hawk, the Armorer's bench, two perks, a box spot
        wreck = spot(64, 0, 24, 0, 0); add(ax + 62.8f, 0, az + 17, ax + 65.2f, 2.4f, az + 31.4f, K_WRECK, 0x1B1B1B);
        radioParts.add(spot(64, 1.0f, 32.2f, 0, 0));
        pap = spot(80, 0, 8, 0, 0); add(pap.x - 1.4f, 0, pap.z - 0.6f, pap.x + 1.4f, 1.0f, pap.z + 0.6f, K_FURN, 0x5A3E2A);
        perk(50, 6, 1, 3000, "Speed Loader"); perk(50, 38, 4, 2000, "Stamina");
        boxSpots.add(spot(82, 0, 36, 0, 0));
        wallBuy(ARENA - 0.65f, 1.5f, 34, -1, 0, Battle.W_SAW, 1800, "M249 SAW");
        wallBuy(70, 1.5f, 0.65f, 0, 1, Battle.W_M60, 2000, "M60");
        add(ax + 70, 0, az + 14, ax + 76, 1.0f, az + 15, K_WALL, 0xA89870);
        // four IRENE tags in odd places (shoot them all)
        tags.add(spot(6, 4.8f, ARENA - 0.62f, 0, -1)); tags.add(spot(40, 5.2f, 0.62f, 0, 1));
        tags.add(spot(ARENA - 0.62f, 0.5f, 86.5f, -1, 0)); tags.add(spot(65.25f, 1.8f, 26, 1, 0));
    }

    private void buildIndex() {
        ArrayList<ArrayList<Integer>> lists = new ArrayList<>(N * N);
        for (int i = 0; i < N * N; i++) lists.add(null);
        for (int b = 0; b < boxes.size(); b++) {
            Box bx = boxes.get(b);
            int ix0 = cl((int) Math.floor(bx.x0 / CELL)), ix1 = cl((int) Math.floor((bx.x1 - 1e-3f) / CELL));
            int iz0 = cl((int) Math.floor(bx.z0 / CELL)), iz1 = cl((int) Math.floor((bx.z1 - 1e-3f) / CELL));
            for (int z = iz0; z <= iz1; z++) for (int x = ix0; x <= ix1; x++) {
                int c = z * N + x;
                if (lists.get(c) == null) lists.set(c, new ArrayList<>());
                lists.get(c).add(b);
                if (bx.kind != K_INVIS && bx.kind != K_PLANK) roofH[c] = Math.max(roofH[c], bx.y1);
            }
        }
        cellBoxes = new int[N * N][];
        for (int i = 0; i < N * N; i++) {
            ArrayList<Integer> l = lists.get(i);
            if (l == null) { cellBoxes[i] = new int[0]; continue; }
            int[] a = new int[l.size()]; for (int k = 0; k < a.length; k++) a[k] = l.get(k); cellBoxes[i] = a;
        }
        rebuildNav(0, 0, N - 1, N - 1);
    }

    /** Recompute blocked cells and wall edges in a cell range from the live boxes. */
    private void rebuildNav(int cx0, int cz0, int cx1, int cz1) {
        cx0 = cl(cx0 - 1); cz0 = cl(cz0 - 1); cx1 = cl(cx1 + 1); cz1 = cl(cz1 + 1);
        for (int z = cz0; z <= cz1; z++) for (int x = cx0; x <= cx1; x++) { int c = z * N + x; blocked[c] = false; edgeE[c] = false; edgeS[c] = false; }
        java.util.HashSet<Integer> seen = new java.util.HashSet<>();
        for (int qz = cz0; qz <= cz1; qz++) for (int qx = cx0; qx <= cx1; qx++) for (int bi : cellBoxes[qz * N + qx]) {
            if (!seen.add(bi)) continue;
            Box b = boxes.get(bi);
            if (b.dead || b.y0 >= 1f || b.y1 <= 0.55f) continue;
            if (b.kind == K_PLANK) continue;
            float w = b.x1 - b.x0, d = b.z1 - b.z0;
            boolean thinZ = d < 0.7f && w >= d, thinX = w < 0.7f && d > w;
            if ((thinZ && (w >= 1.0f || b.navForce)) || (thinX && (d >= 1.0f || b.navForce))) {
                if (thinZ) {
                    int zb = Math.round(((b.z0 + b.z1) / 2) / CELL);
                    if (zb <= 0 || zb >= N) continue;
                    for (int x = cl((int) (b.x0 / CELL)); x <= cl((int) ((b.x1 - 1e-3f) / CELL)); x++) {
                        float ov = Math.min(b.x1, (x + 1) * CELL) - Math.max(b.x0, x * CELL);
                        if (ov > 1.6f || (b.navForce && ov > 0.3f)) edgeS[(zb - 1) * N + x] = true;
                    }
                } else {
                    int xb = Math.round(((b.x0 + b.x1) / 2) / CELL);
                    if (xb <= 0 || xb >= N) continue;
                    for (int z = cl((int) (b.z0 / CELL)); z <= cl((int) ((b.z1 - 1e-3f) / CELL)); z++) {
                        float ov = Math.min(b.z1, (z + 1) * CELL) - Math.max(b.z0, z * CELL);
                        if (ov > 1.6f || (b.navForce && ov > 0.3f)) edgeE[z * N + xb - 1] = true;
                    }
                }
                continue;
            }
            if (w < 1.0f && d < 1.0f) continue; // posts and poles: units slide around them
            for (int z = cl((int) Math.floor(b.z0 / CELL)); z <= cl((int) Math.floor((b.z1 - 1e-3f) / CELL)); z++)
                for (int x = cl((int) Math.floor(b.x0 / CELL)); x <= cl((int) Math.floor((b.x1 - 1e-3f) / CELL)); x++) {
                    // a thick box blocks a cell if it covers a real part of it
                    float ox = Math.min(b.x1, (x + 1) * CELL) - Math.max(b.x0, x * CELL), oz = Math.min(b.z1, (z + 1) * CELL) - Math.max(b.z0, z * CELL);
                    if (ox > 1.2f && oz > 1.2f) blocked[z * N + x] = true;
                }
        }
    }

    /** Remove a dynamic box (door, gate, plank) and update navigation around it. */
    public void kill(int bi) {
        Box b = boxes.get(bi); if (b.dead) return; b.dead = true;
        if (b.kind == K_PLANK) return;
        rebuildNav((int) (b.x0 / CELL), (int) (b.z0 / CELL), (int) (b.x1 / CELL), (int) (b.z1 / CELL));
        java.util.Arrays.fill(connected, false); flood(NAT_Z * N + HAWL_X);
        if (siege && start != null) flood(cellOf(start.x, start.z));
        navVersion++;
    }
    public void revive(int bi) { boxes.get(bi).dead = false; }
    /** Bumped whenever doors/gates open so path fields get rebuilt. */
    public int navVersion;

    /** Can a unit step from cell (cx,cz) to (cx+dx,cz+dz)? Walls on cell edges count; diagonals need both sides clear. */
    public boolean pass(int cx, int cz, int dx, int dz) {
        int nx = cx + dx, nz = cz + dz;
        if (!walkable(nx, nz)) return false;
        if (dx != 0 && dz != 0) return pass(cx, cz, dx, 0) && pass(cx, cz, 0, dz) && pass(cx + dx, cz, 0, dz) && pass(cx, cz + dz, dx, 0);
        if (dx == 1) return !edgeE[cz * N + cx];
        if (dx == -1) return !edgeE[cz * N + nx];
        if (dz == 1) return !edgeS[cz * N + cx];
        return !edgeS[nz * N + cx];
    }

    static int cl(int v) { return Math.max(0, Math.min(N - 1, v)); }

    public int[] boxesAt(int cx, int cz) { return cellBoxes[cl(cz) * N + cl(cx)]; }
    public int cellOf(float x, float z) { return cl((int) (z / CELL)) * N + cl((int) (x / CELL)); }
    public boolean walkable(int cx, int cz) { return cx >= 0 && cz >= 0 && cx < N && cz < N && !blocked[cz * N + cx]; }

    /** Ray vs world. Returns distance to first hit (or maxDist). Writes hit normal axis into nrm[0] (0=x,1=y,2=z, sign in nrm[1]). */
    public float ray(float ox, float oy, float oz, float dx, float dy, float dz, float maxDist, float[] nrm) {
        float best = maxDist; int axis = -1; float sign = 0;
        if (dy < 0) { float t = -oy / dy; if (t < best) { best = t; axis = 1; sign = 1; } }
        // 2D DDA across cells
        int cx = (int) Math.floor(ox / CELL), cz = (int) Math.floor(oz / CELL);
        int sx = dx > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
        float tdx = Math.abs(dx) < 1e-6f ? 1e9f : Math.abs(CELL / dx), tdz = Math.abs(dz) < 1e-6f ? 1e9f : Math.abs(CELL / dz);
        float tmx = Math.abs(dx) < 1e-6f ? 1e9f : ((sx > 0 ? (cx + 1) * CELL - ox : ox - cx * CELL) / Math.abs(dx));
        float tmz = Math.abs(dz) < 1e-6f ? 1e9f : ((sz > 0 ? (cz + 1) * CELL - oz : oz - cz * CELL) / Math.abs(dz));
        float tCell = 0;
        int guard = 0;
        while (tCell <= best && guard++ < 400) {
            if (cx >= 0 && cz >= 0 && cx < N && cz < N) {
                for (int bi : cellBoxes[cz * N + cx]) {
                    Box b = boxes.get(bi);
                    if (b.dead || !b.bullets) continue;
                    float t = slab(b, ox, oy, oz, dx, dy, dz, best);
                    if (t >= 0 && t < best) { best = t; axis = lastAxis; sign = lastSign; }
                }
            } else if ((cx < -1 && sx < 0) || (cz < -1 && sz < 0) || (cx > N && sx > 0) || (cz > N && sz > 0)) break;
            if (tmx < tmz) { tCell = tmx; tmx += tdx; cx += sx; } else { tCell = tmz; tmz += tdz; cz += sz; }
        }
        if (nrm != null) { nrm[0] = axis; nrm[1] = sign; }
        return best;
    }
    private int lastAxis; private float lastSign;
    private float slab(Box b, float ox, float oy, float oz, float dx, float dy, float dz, float maxT) {
        float tmin = 0, tmax = maxT; int ax = -1; float sg = 0;
        float[] o = {ox, oy, oz}, d = {dx, dy, dz}, lo = {b.x0, b.y0, b.z0}, hi = {b.x1, b.y1, b.z1};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-7f) { if (o[i] < lo[i] || o[i] > hi[i]) return -1; continue; }
            float t1 = (lo[i] - o[i]) / d[i], t2 = (hi[i] - o[i]) / d[i]; float s = -1;
            if (t1 > t2) { float tt = t1; t1 = t2; t2 = tt; s = 1; }
            if (t1 > tmin) { tmin = t1; ax = i; sg = s; }
            if (t2 < tmax) tmax = t2;
            if (tmin > tmax) return -1;
        }
        if (ax < 0) return -1; // started inside
        lastAxis = ax; lastSign = sg; return tmin;
    }

    /** True if nothing solid between two points. */
    public boolean los(float ax, float ay, float az, float bx, float by, float bz) {
        float dx = bx - ax, dy = by - ay, dz = bz - az, len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.01f) return true;
        return ray(ax, ay, az, dx / len, dy / len, dz / len, len, null) >= len - 0.05f;
    }

    /** Highest box top at (x,z) within radius that is at or below maxY (what you can stand on). */
    public float groundAt(float x, float z, float rad, float maxY) {
        float g = 0;
        int cx0 = (int) ((x - rad) / CELL), cx1 = (int) ((x + rad) / CELL), cz0 = (int) ((z - rad) / CELL), cz1 = (int) ((z + rad) / CELL);
        for (int cz = cz0; cz <= cz1; cz++) for (int cx = cx0; cx <= cx1; cx++) {
            if (cx < 0 || cz < 0 || cx >= N || cz >= N) continue;
            for (int bi : cellBoxes[cz * N + cx]) {
                Box b = boxes.get(bi);
                if (b.dead || b.y1 > maxY || b.y1 <= g) continue;
                if (x + rad * 0.7f > b.x0 && x - rad * 0.7f < b.x1 && z + rad * 0.7f > b.z0 && z - rad * 0.7f < b.z1) g = b.y1;
            }
        }
        return g;
    }

    /** Lowest box bottom above y (for head bumps). */
    public float ceilingAt(float x, float z, float rad, float y) {
        float c = 1e9f; int cx = (int) (x / CELL), cz = (int) (z / CELL);
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            int ix = cx + dx, iz = cz + dz; if (ix < 0 || iz < 0 || ix >= N || iz >= N) continue;
            for (int bi : cellBoxes[iz * N + ix]) { Box b = boxes.get(bi); if (!b.dead && b.y0 >= y && b.y0 < c && x + rad > b.x0 && x - rad < b.x1 && z + rad > b.z0 && z - rad < b.z1) c = b.y0; }
        }
        return c;
    }
}
