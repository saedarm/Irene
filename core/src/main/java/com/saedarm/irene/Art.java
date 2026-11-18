package com.saedarm.irene;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

import java.util.Random;

/**
 * Every model is built in code (greybox). Swap any of these for a glTF from Blender later; see the
 * build guide in the README. Per-model instance pools are reset each frame so drawing allocates nothing.
 */
public final class Art implements Disposable {
    private final ModelBuilder mb = new ModelBuilder();
    private final Array<Model> owned = new Array<>();
    private static final long ATTR = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.ColorPacked;

    // ---- city chunks (one mesh per 80 m square, vertex coloured) ----
    public static final int CHUNK = 20; // cells
    public final Array<ModelInstance> chunks = new Array<>();
    public final Array<BoundingBox> chunkBounds = new Array<>();
    public Texture minimap;

    // ---- pooled part models ----
    public final Pool box;            // unit cube, white; tinted via material instances per colour
    private final java.util.HashMap<Integer, Pool> tinted = new java.util.HashMap<>();
    public final Model rotorBlade, wreckSmoke;
    public final Pool blackhawk, littlebird, blackhawkWreck, littlebirdWreck, rotor, tailRotor, crate, tire, rocket, grenade, humvee, humveeBurnt, car, wheel, fifty, plane, mysteryBox, bench, lamp;
    public final Model[] guns = new Model[Battle.WEAPONS.length];
    public final Model hands;

    public static final class Pool {
        final Model model; final Array<ModelInstance> list = new Array<>(); int used;
        Pool(Model m) { model = m; }
        public ModelInstance next() { if (used == list.size) list.add(new ModelInstance(model)); ModelInstance mi = list.get(used++); mi.transform.idt(); return mi; }
        void reset() { used = 0; }
        void render(ModelBatch b, Environment e) { for (int i = 0; i < used; i++) b.render(list.get(i), e); }
    }

    public Art(City city) {
        box = new Pool(cube(0xFFFFFF));
        buildCity(city);
        // helicopters: MH-60 Black Hawk and MH-6 Little Bird, built from boxes
        blackhawk = new Pool(blackhawkModel(false)); blackhawkWreck = new Pool(blackhawkModel(true));
        littlebird = new Pool(littlebirdModel(false)); littlebirdWreck = new Pool(littlebirdModel(true));
        rotorBlade = null; wreckSmoke = null;
        rotor = new Pool(rotorModel(4, 8.2f)); tailRotor = new Pool(rotorModel(2, 1.4f));
        crate = new Pool(crateModel()); tire = new Pool(tireModel());
        rocket = new Pool(simple(0x4F5A3A, 0.12f, 0.12f, 0.9f)); grenade = new Pool(simple(0x3E4A2E, 0.14f, 0.14f, 0.14f));
        humvee = new Pool(humveeModel(false)); humveeBurnt = new Pool(humveeModel(true)); car = new Pool(carModel()); wheel = new Pool(simple(0x151515, 0.32f, 0.9f, 0.9f));
        fifty = new Pool(fiftyModel()); plane = new Pool(planeModel()); mysteryBox = new Pool(boxModel()); bench = new Pool(benchModel()); lamp = new Pool(simple(0xFFE7A0, 0.25f, 0.25f, 0.25f));
        for (int i = 0; i < guns.length; i++) guns[i] = gunModel(i);
        hands = handsModel();
    }

    public Pool tint(int rgb) { Pool p = tinted.get(rgb); if (p == null) { p = new Pool(cube(rgb)); tinted.put(rgb, p); } return p; }

    private Pool[] all() { return new Pool[]{box, blackhawk, littlebird, blackhawkWreck, littlebirdWreck, rotor, tailRotor, crate, tire, rocket, grenade, humvee, humveeBurnt, car, wheel, fifty, plane, mysteryBox, bench, lamp}; }
    private final java.util.HashMap<Integer, Pool> gunPools = new java.util.HashMap<>();
    public Pool gun(int w) { Pool p = gunPools.get(w); if (p == null) { p = new Pool(guns[w]); gunPools.put(w, p); } return p; }
    public void reset() { for (Pool p : all()) p.reset(); for (Pool p : tinted.values()) p.reset(); for (Pool p : gunPools.values()) p.reset(); }
    public void render(ModelBatch b, Environment e) {
        for (Pool p : all()) p.render(b, e); for (Pool p : tinted.values()) p.render(b, e); for (Pool p : gunPools.values()) p.render(b, e);
    }

    // ------------------------------------------------------------------ helpers
    static Color c(int rgb) { return new Color(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1); }
    private Model cube(int rgb) {
        mb.begin(); MeshPartBuilder p = mb.part("c", GL20.GL_TRIANGLES, ATTR, new Material(ColorAttribute.createDiffuse(c(rgb))));
        p.setColor(Color.WHITE); BoxShapeBuilder.build(p, 0, 0, 0, 1, 1, 1);
        Model m = mb.end(); owned.add(m); return m;
    }
    private Model simple(int rgb, float w, float h, float d) {
        mb.begin(); MeshPartBuilder p = mb.part("c", GL20.GL_TRIANGLES, ATTR, new Material(ColorAttribute.createDiffuse(Color.WHITE)));
        p.setColor(c(rgb)); BoxShapeBuilder.build(p, 0, 0, 0, w, h, d);
        Model m = mb.end(); owned.add(m); return m;
    }
    /** Box from corner coordinates into a part builder, with colour. */
    private static void bx(MeshPartBuilder p, int rgb, float x0, float y0, float z0, float x1, float y1, float z1) {
        p.setColor(c(rgb)); BoxShapeBuilder.build(p, (x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2, Math.abs(x1 - x0), Math.abs(y1 - y0), Math.abs(z1 - z0));
    }
    private static int shade(int rgb, float f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * f)), g = Math.min(255, (int) (((rgb >> 8) & 255) * f)), b = Math.min(255, (int) ((rgb & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    // ------------------------------------------------------------------ city
    private void buildCity(City city) {
        Random r = new Random(99);
        int nCh = City.N / CHUNK;
        java.util.ArrayList<java.util.ArrayList<City.Box>> per = new java.util.ArrayList<>();
        for (int i = 0; i < nCh * nCh; i++) per.add(new java.util.ArrayList<>());
        for (City.Box b : city.boxes) {
            int cx = Math.max(0, Math.min(nCh - 1, (int) (((b.x0 + b.x1) / 2) / City.CELL / CHUNK)));
            int cz = Math.max(0, Math.min(nCh - 1, (int) (((b.z0 + b.z1) / 2) / City.CELL / CHUNK)));
            per.get(cz * nCh + cx).add(b);
        }
        Material mat = new Material(ColorAttribute.createDiffuse(Color.WHITE));
        for (int ch = 0; ch < nCh * nCh; ch++) {
            int chx = ch % nCh, chz = ch / nCh;
            mb.begin();
            int partN = 0, count = 0;
            MeshPartBuilder p = mb.part("p" + partN++, GL20.GL_TRIANGLES, ATTR, mat);
            // ground: one quad per cell (road, paved road, open ground)
            for (int z = chz * CHUNK; z < (chz + 1) * CHUNK; z++) for (int x = chx * CHUNK; x < (chx + 1) * CHUNK; x++) {
                int i = z * City.N + x;
                int col = city.paved[i] ? 0x6E6A62 : city.road[i] ? 0xB49C76 : 0xC4AE88;
                col = shade(col, 0.94f + r.nextFloat() * 0.1f);
                p.setColor(c(col));
                float x0 = x * City.CELL, z0 = z * City.CELL, x1 = x0 + City.CELL, z1 = z0 + City.CELL;
                p.rect(x0, 0, z1, x1, 0, z1, x1, 0, z0, x0, 0, z0, 0, 1, 0);
                if (++count > 1100) { p = mb.part("p" + partN++, GL20.GL_TRIANGLES, ATTR, mat); count = 0; }
            }
            for (City.Box b : per.get(ch)) {
                if (b.dynamic || !b.visible()) continue;
                int col = shade(b.color, 0.9f + r.nextFloat() * 0.16f);
                bx(p, col, b.x0, b.y0, b.z0, b.x1, b.y1, b.z1); count++;
                // dark band at the base (dirt splash) and windows/doors on real buildings
                if (b.kind == City.K_HOUSE || b.kind == City.K_HOTEL || b.kind == City.K_TARGET) {
                    bx(p, shade(col, 0.72f), b.x0 - 0.02f, 0, b.z0 - 0.02f, b.x1 + 0.02f, 0.35f, b.z1 + 0.02f); count++;
                    int stories = Math.max(1, Math.round((b.y1 - b.y0) / City.STORY));
                    for (int s = 0; s < stories; s++) {
                        float wy0 = s * City.STORY + 1.1f, wy1 = wy0 + 1.2f;
                        for (float x = b.x0 + 1.4f; x < b.x1 - 1.6f; x += 3.1f) {
                            if (r.nextFloat() < 0.25f) continue;
                            int wc = r.nextFloat() < 0.2f ? 0x3B5560 : 0x1E1A16;
                            bx(p, wc, x, wy0, b.z0 - 0.05f, x + 0.9f, wy1, b.z0 + 0.02f); bx(p, wc, x, wy0, b.z1 - 0.02f, x + 0.9f, wy1, b.z1 + 0.05f); count += 2;
                        }
                        for (float z = b.z0 + 1.4f; z < b.z1 - 1.6f; z += 3.1f) {
                            if (r.nextFloat() < 0.25f) continue;
                            bx(p, 0x1E1A16, b.x0 - 0.05f, wy0, z, b.x0 + 0.02f, wy1, z + 0.9f); bx(p, 0x1E1A16, b.x1 - 0.02f, wy0, z, b.x1 + 0.05f, wy1, z + 0.9f); count += 2;
                        }
                    }
                    float dx = (b.x0 + b.x1) / 2;
                    bx(p, 0x3A2A1C, dx - 0.6f, 0, b.z1 - 0.02f, dx + 0.6f, 2.1f, b.z1 + 0.06f); count++;
                    if (b.kind == City.K_HOTEL) { bx(p, 0x2E5E7E, b.x0 + 2, b.y1 - 0.2f, b.z1 + 0.05f, b.x1 - 2, b.y1 + 1.4f, b.z1 + 0.2f); count++; } // faded sign board
                } else if (b.kind == City.K_SHACK) {
                    bx(p, shade(0x9FA3A0, 0.8f + r.nextFloat() * 0.3f), b.x0 - 0.2f, b.y1, b.z0 - 0.2f, b.x1 + 0.2f, b.y1 + 0.08f, b.z1 + 0.2f); count++; // tin roof
                } else if (b.kind == City.K_WALL && b.y1 - b.y0 > 2) {
                    // bullet pocks and a darker cap stone
                    bx(p, shade(col, 0.82f), b.x0 - 0.03f, b.y1 - 0.15f, b.z0 - 0.03f, b.x1 + 0.03f, b.y1, b.z1 + 0.03f); count++;
                }
                if (count > 1100) { p = mb.part("p" + partN++, GL20.GL_TRIANGLES, ATTR, mat); count = 0; }
            }
            // leaning telephone poles along the main roads
            for (int k = 0; k < 6; k++) {
                int x = chx * CHUNK + r.nextInt(CHUNK), z = chz * CHUNK + r.nextInt(CHUNK);
                if (!city.road[z * City.N + x] || city.blocked[z * City.N + x]) continue;
                float px = x * City.CELL + 0.3f, pz = z * City.CELL + 0.3f;
                bx(p, 0x5B4632, px, 0, pz, px + 0.22f, 7.5f, pz + 0.22f); bx(p, 0x5B4632, px - 0.8f, 6.8f, pz, px + 1.0f, 6.95f, pz + 0.15f);
            }
            Model m = mb.end(); owned.add(m);
            ModelInstance mi = new ModelInstance(m); chunks.add(mi);
            BoundingBox bb = new BoundingBox(); mi.calculateBoundingBox(bb); chunkBounds.add(bb);
        }
        // minimap texture: one pixel per cell, coloured by height
        Pixmap pm = new Pixmap(City.N, City.N, Pixmap.Format.RGBA8888);
        for (int z = 0; z < City.N; z++) for (int x = 0; x < City.N; x++) {
            int i = z * City.N + x; float h = city.roofH[i];
            if (city.paved[i]) pm.setColor(0.32f, 0.31f, 0.29f, 0.9f);
            else if (h < 0.6f) pm.setColor(0.55f, 0.49f, 0.39f, 0.85f);
            else { float f = Math.min(1, 0.45f + h / 18f); pm.setColor(0.78f * f + 0.1f, 0.72f * f + 0.1f, 0.6f * f + 0.08f, 0.95f); }
            pm.drawPixel(x, z);
        }
        minimap = new Texture(pm); pm.dispose();
    }

    // ------------------------------------------------------------------ vehicles
    private Model blackhawkModel(boolean wreck) {
        int body = wreck ? 0x1B1B1B : 0x2D3032, dark = wreck ? 0x111111 : 0x1F2224, glass = wreck ? 0x101010 : 0x3D4E55;
        mb.begin(); MeshPartBuilder p = mb.part("h", GL20.GL_TRIANGLES, ATTR, new Material(ColorAttribute.createDiffuse(Color.WHITE)));
        // model faces -z (forward)
        bx(p, body, -1.1f, -1.0f, -3.6f, 1.1f, 1.0f, 2.2f);         // cabin
        bx(p, body, -0.9f, -0.8f, -5.0f, 0.9f, 0.6f, -3.6f);        // nose
        bx(p, glass, -0.85f, -0.1f, -5.05f, 0.85f, 0.55f, -3.9f);   // windscreen
        bx(p, body, -0.55f, -0.2f, 2.2f, 0.55f, 0.6f, 9.4f);        // tail boom
        bx(p, body, -0.12f, 0.4f, 8.6f, 0.12f, 2.4f, 9.6f);         // fin
        bx(p, body, -1.7f, 0.4f, 8.8f, 1.7f, 0.55f, 9.4f);          // stabilator
        bx(p, dark, -0.7f, 1.0f, -2.6f, 0.7f, 1.55f, 1.2f);         // engine deck
        bx(p, dark, -0.12f, 1.55f, -0.6f, 0.12f, 1.9f, -0.2f);      // mast
        bx(p, dark, -1.12f, -0.6f, -2.8f, -1.08f, 0.6f, -0.8f);     // open door frames
        bx(p, dark, 1.08f, -0.6f, -2.8f, 1.12f, 0.6f, -0.8f);
        bx(p, dark, -1.6f, -1.6f, -3.4f, -1.4f, -1.0f, -3.2f); bx(p, dark, 1.4f, -1.6f, -3.4f, 1.6f, -1.0f, -3.2f); bx(p, dark, -0.1f, -1.4f, 7.8f, 0.1f, -0.2f, 8.0f); // gear
        bx(p, 0x111111, 1.15f, -0.2f, -2.2f, 1.9f, -0.05f, -2.05f); bx(p, 0x111111, -1.9f, -0.2f, -2.2f, -1.15f, -0.05f, -2.05f); // miniguns
        if (wreck) { bx(p, 0x2A1E12, -1.5f, -1.05f, -4f, 1.5f, -0.95f, 3f); }
        Model m = mb.end(); owned.add(m); return m;
    }
    private Model littlebirdModel(boolean wreck) {
        int body = wreck ? 0x1B1B1B : 0x222426, glass = wreck ? 0x101010 : 0x44565C;
        mb.begin(); MeshPartBuilder p = mb.part("h", GL20.GL_TRIANGLES, ATTR, new Material(ColorAttribute.createDiffuse(Color.WHITE)));
        p.setColor(c(body)); com.badlogic.gdx.graphics.g3d.utils.shapebuilders.SphereShapeBuilder.build(p, new Matrix4().scale(1, 1, 1.35f), 2.0f, 1.9f, 2.0f, 10, 8);
        bx(p, glass, -0.75f, 0.0f, -1.55f, 0.75f, 0.75f, -1.0f);
        bx(p, body, -0.25f, 0.05f, 1.2f, 0.25f, 0.45f, 5.4f);
        bx(p, body, -0.08f, 0.2f, 5.0f, 0.08f, 1.4f, 5.5f);
        bx(p, 0x151515, -1.1f, -1.25f, -1.3f, -0.95f, -1.15f, 1.3f); bx(p, 0x151515, 0.95f, -1.25f, -1.3f, 1.1f, -1.15f, 1.3f); // skids
        bx(p, 0x3A3A3A, -1.55f, -0.55f, -0.7f, -1.0f, -0.45f, 0.7f); bx(p, 0x3A3A3A, 1.0f, -0.55f, -0.7f, 1.55f, -0.45f, 0.7f); // benches
        bx(p, 0x151515, -0.15f, 0.9f, -0.2f, 0.15f, 1.15f, 0.2f);
        Model m = mb.end(); owned.add(m); return m;
    }
    private Model rotorModel(int blades, float len) {
        mb.begin(); MeshPartBuilder p = mb.part("r", GL20.GL_TRIANGLES, ATTR, new Material(ColorAttribute.createDiffuse(Color.WHITE)));
        for (int i = 0; i < blades; i++) {
            float a = (float) (Math.PI * 2 * i / blades);
            Matrix4 t = new Matrix4().rotateRad(Vector3.Y, a);
            p.setVertexTransform(t); bx(p, 0x1A1A1A, 0, -0.04f, -0.16f, len, 0.04f, 0.16f);
        }
        p.setVertexTransform(null); bx(p, 0x101010, -0.3f, -0.15f, -0.3f, 0.3f, 0.15f, 0.3f);
        Model m = mb.end(); owned.add(m); return m;
    }
    private Model crateModel() {
        mb.begin(); MeshPartBuilder p = mb.part("c", GL20.GL_TRIANGLES, ATTR, new Material(ColorAttribute.createDiffuse(Color.WHITE)));
        bx(p, 0x4E5A33, -0.6f, 0, -0.35f, 0.6f, 0.55f, 0.35f); bx(p, 0x3B4426, -0.62f, 0.2f, -0.37f, 0.62f, 0.3f, 0.37f);
        bx(p, 0x4E5A33, -0.5f, 0.55f, -0.3f, 0.3f, 0.95f, 0.3f); bx(p, 0xC8B44A, -0.2f, 0.6f, -0.31f, 0.05f, 0.85f, -0.29f);
        Model m = mb.end(); owned.add(m); return m;
    }
    private Model tireModel() {
        mb.begin(); MeshPartBuilder p = mb.part("t", GL20.GL_TRIANGLES, ATTR, new Material(ColorAttribute.createDiffuse(Color.WHITE)));
        for (int i = 0; i < 5; i++) { float a = i * 1.3f; p.setVertexTransform(new Matrix4().translate((float) Math.cos(a) * 0.7f, 0.2f + (i % 2) * 0.35f, (float) Math.sin(a) * 0.7f).rotateRad(Vector3.Y, a)); bx(p, 0x141414, -0.45f, -0.15f, -0.45f, 0.45f, 0.15f, 0.45f); }
        p.setVertexTransform(null); bx(p, 0x5A4632, -1.2f, 0, -0.4f, 1.0f, 0.3f, 0.3f);
        Model m = mb.end(); owned.add(m); return m;
    }


    // ------------------------------------------------------------------ vehicles, props
    private Model humveeModel(boolean burnt) {
        int body = burnt ? 0x1E1C1A : 0xB59B6E, dark = burnt ? 0x121212 : 0x2A2A26, glass = burnt ? 0x0E0E0E : 0x3E4E52;
        mb.begin(); MeshPartBuilder p = mb.part("v", GL20.GL_TRIANGLES, ATTR, new Material(ColorAttribute.createDiffuse(Color.WHITE)));
        bx(p, body, -1.1f, 0.45f, -2.4f, 1.1f, 1.05f, 2.35f);   // hull
        bx(p, body, -1.0f, 1.05f, -2.4f, 1.0f, 1.25f, -1.1f);   // hood
        bx(p, body, -1.05f, 1.05f, -1.1f, 1.05f, 1.9f, 1.0f);   // cab
        bx(p, glass, -0.95f, 1.35f, -1.12f, 0.95f, 1.85f, -1.06f); // windscreen
        bx(p, glass, -1.07f, 1.35f, -0.95f, -1.05f, 1.8f, 0.8f); bx(p, glass, 1.05f, 1.35f, -0.95f, 1.07f, 1.8f, 0.8f);
        bx(p, body, -1.05f, 1.05f, 1.0f, -0.95f, 1.5f, 2.35f); bx(p, body, 0.95f, 1.05f, 1.0f, 1.05f, 1.5f, 2.35f); // bed sides
        bx(p, dark, -0.45f, 1.9f, -0.35f, 0.45f, 2.05f, 0.55f);    // turret ring
        bx(p, dark, -1.15f, 0.55f, -2.5f, 1.15f, 0.8f, -2.4f);      // bumper
        bx(p, 0x2E2E2E, -0.8f, 0.85f, -2.45f, 0.8f, 1.0f, -2.41f);  // grille
        Model m = mb.end(); owned.add(m); return m;
    }
    private Model carModel() {
        mb.begin(); MeshPartBuilder p = mb.part("c", GL20.GL_TRIANGLES, ATTR, new Material(ColorAttribute.createDiffuse(Color.WHITE)));
        bx(p, 0x6E7E86, -0.95f, 0.4f, -2.2f, 0.95f, 1.0f, 2.2f); bx(p, 0x6E7E86, -0.85f, 1.0f, -0.9f, 0.85f, 1.5f, 1.2f);
        bx(p, 0x2E3A3E, -0.8f, 1.05f, -0.95f, 0.8f, 1.45f, -0.9f); bx(p, 0x2E3A3E, -0.87f, 1.05f, -0.8f, 0.87f, 1.45f, 1.1f);
        bx(p, 0x222222, -0.9f, 0.45f, -2.25f, 0.9f, 0.7f, -2.18f);
        Model m = mb.end(); owned.add(m); return m;
    }
    private Model fiftyModel() {
        mb.begin(); MeshPartBuilder p = mb.part("f", GL20.GL_TRIANGLES, ATTR, new Material(ColorAttribute.createDiffuse(Color.WHITE)));
        bx(p, 0x2B2B2B, -0.1f, -0.1f, -0.3f, 0.1f, 0.12f, 0.35f); bx(p, 0x222222, -0.03f, -0.03f, -1.45f, 0.03f, 0.03f, -0.3f);
        bx(p, 0x3E4A2E, 0.1f, -0.15f, -0.15f, 0.3f, 0.08f, 0.1f); bx(p, 0x2B2B2B, -0.25f, -0.2f, 0.32f, 0.25f, 0.15f, 0.38f);
        bx(p, 0x4A4A44, -0.55f, -0.5f, -0.1f, 0.55f, 0.3f, -0.05f); // shield
        Model m = mb.end(); owned.add(m); return m;
    }
    private Model planeModel() { // AC-130 silhouette, seen from far below
        mb.begin(); MeshPartBuilder p = mb.part("a", GL20.GL_TRIANGLES, ATTR, new Material(ColorAttribute.createDiffuse(Color.WHITE)));
        bx(p, 0x3A3E42, -1.6f, -1.6f, -14, 1.6f, 1.6f, 14); bx(p, 0x3A3E42, -20, 0.8f, -3, 20, 1.2f, 1.5f); bx(p, 0x3A3E42, -6, 0.4f, 11, 6, 0.7f, 13.5f); bx(p, 0x3A3E42, -0.2f, 0.6f, 10, 0.2f, 6, 14);
        for (float x : new float[]{-12, -6, 6, 12}) bx(p, 0x2A2A2A, x - 0.6f, 0.2f, -5, x + 0.6f, 1.2f, -1);
        Model m = mb.end(); owned.add(m); return m;
    }
    private Model boxModel() { // the weapons crate
        mb.begin(); MeshPartBuilder p = mb.part("b", GL20.GL_TRIANGLES, ATTR, new Material(ColorAttribute.createDiffuse(Color.WHITE)));
        bx(p, 0x5A4630, -0.9f, 0, -0.45f, 0.9f, 0.75f, 0.45f); bx(p, 0x3E2E1E, -0.92f, 0.3f, -0.47f, 0.92f, 0.4f, 0.47f);
        bx(p, 0xC9B04A, -0.25f, 0.45f, -0.48f, 0.25f, 0.65f, -0.46f); bx(p, 0x6A5236, -0.92f, 0.75f, -0.47f, 0.92f, 0.85f, 0.47f);
        Model m = mb.end(); owned.add(m); return m;
    }
    private Model benchModel() { // the armorer's bench
        mb.begin(); MeshPartBuilder p = mb.part("b", GL20.GL_TRIANGLES, ATTR, new Material(ColorAttribute.createDiffuse(Color.WHITE)));
        bx(p, 0x5A3E2A, -1.4f, 0.85f, -0.6f, 1.4f, 1.0f, 0.6f); for (float x : new float[]{-1.3f, 1.2f}) for (float z : new float[]{-0.5f, 0.4f}) bx(p, 0x3A2A1A, x, 0, z, x + 0.12f, 0.85f, z + 0.12f);
        bx(p, 0x7A7A72, -0.3f, 1.0f, -0.2f, 0.3f, 1.3f, 0.2f); bx(p, 0x9A2A2A, 0.6f, 1.0f, -0.3f, 1.1f, 1.15f, 0.1f); bx(p, 0x3A3A3A, -1.2f, 1.0f, 0.1f, -0.5f, 1.05f, 0.5f);
        bx(p, 0x2A2A2A, -1.4f, 1.0f, 0.55f, 1.4f, 2.2f, 0.62f); // pegboard
        Model m = mb.end(); owned.add(m); return m;
    }

    // ------------------------------------------------------------------ first-person guns (forward is -z, units are metres)
    private Model gunModel(int w) {
        mb.begin(); MeshPartBuilder p = mb.part("g", GL20.GL_TRIANGLES, ATTR, new Material(ColorAttribute.createDiffuse(Color.WHITE)));
        int blk = 0x2B2B2B, gry = 0x3E3E3E, wood = 0x7A4A2A, olive = 0x56613F;
        switch (w) {
            case Battle.W_CAR15: case Battle.W_CAR15_AIM:
                bx(p, gry, -0.03f, -0.05f, -0.18f, 0.03f, 0.03f, 0.12f);   // receiver
                bx(p, blk, -0.012f, -0.01f, -0.55f, 0.012f, 0.012f, -0.18f); // barrel
                bx(p, blk, -0.025f, -0.035f, -0.42f, 0.025f, 0.02f, -0.18f); // handguard
                bx(p, blk, -0.02f, -0.13f, -0.08f, 0.02f, -0.05f, -0.03f);  // magazine
                bx(p, blk, -0.015f, -0.11f, 0.04f, 0.015f, -0.05f, 0.08f);   // grip
                bx(p, blk, -0.02f, -0.06f, 0.12f, 0.02f, 0.01f, 0.32f);      // stock
                if (w == Battle.W_CAR15_AIM) { bx(p, blk, -0.018f, 0.03f, -0.1f, 0.018f, 0.075f, 0.0f); bx(p, 0xCC2222, -0.004f, 0.05f, 0.001f, 0.004f, 0.058f, 0.003f); }
                else { bx(p, blk, -0.01f, 0.03f, 0.02f, 0.01f, 0.07f, 0.05f); bx(p, blk, -0.004f, 0.012f, -0.45f, 0.004f, 0.05f, -0.43f); }
                break;
            case Battle.W_SAW:
                bx(p, gry, -0.04f, -0.06f, -0.2f, 0.04f, 0.04f, 0.15f); bx(p, blk, -0.015f, -0.015f, -0.62f, 0.015f, 0.015f, -0.2f);
                bx(p, 0x3E4A2E, -0.07f, -0.16f, -0.12f, 0.0f, -0.04f, 0.0f); // ammo box
                bx(p, blk, -0.015f, -0.12f, 0.06f, 0.015f, -0.05f, 0.1f); bx(p, blk, -0.025f, -0.07f, 0.15f, 0.025f, 0.02f, 0.35f);
                bx(p, blk, -0.04f, -0.12f, -0.5f, -0.035f, -0.015f, -0.48f); bx(p, blk, 0.035f, -0.12f, -0.5f, 0.04f, -0.015f, -0.48f); // bipod
                break;
            case Battle.W_M14:
                bx(p, wood, -0.03f, -0.07f, -0.45f, 0.03f, 0.0f, 0.34f); bx(p, gry, -0.02f, 0.0f, -0.2f, 0.02f, 0.03f, 0.1f);
                bx(p, blk, -0.011f, 0.0f, -0.7f, 0.011f, 0.02f, -0.45f); bx(p, blk, -0.018f, -0.13f, -0.12f, 0.018f, -0.07f, -0.04f);
                bx(p, blk, -0.016f, 0.03f, -0.12f, 0.016f, 0.07f, -0.02f); bx(p, 0xCC2222, -0.004f, 0.045f, -0.019f, 0.004f, 0.053f, -0.017f);
                break;
            case Battle.W_AK: case Battle.W_PKM:
                bx(p, gry, -0.03f, -0.05f, -0.2f, 0.03f, 0.03f, 0.1f); bx(p, wood, -0.028f, -0.045f, -0.42f, 0.028f, 0.02f, -0.2f);
                bx(p, blk, -0.011f, -0.005f, -0.62f, 0.011f, 0.015f, -0.42f); bx(p, wood, -0.022f, -0.08f, 0.1f, 0.022f, 0.0f, 0.36f);
                bx(p, blk, -0.015f, -0.12f, 0.02f, 0.015f, -0.05f, 0.06f);
                if (w == Battle.W_AK) { p.setVertexTransform(new Matrix4().translate(0, -0.05f, -0.1f).rotateRad(Vector3.X, 0.35f)); bx(p, 0x2A2A2A, -0.018f, -0.11f, -0.03f, 0.018f, 0f, 0.03f); p.setVertexTransform(null); }
                else { bx(p, 0x3B3B2E, -0.08f, -0.17f, -0.14f, -0.01f, -0.05f, -0.02f); bx(p, blk, -0.016f, -0.016f, -0.7f, 0.016f, 0.016f, -0.42f); }
                break;
            case Battle.W_RPG:
                bx(p, olive, -0.04f, -0.04f, -0.5f, 0.04f, 0.04f, 0.55f); bx(p, wood, -0.045f, -0.045f, -0.15f, 0.045f, 0.045f, 0.1f);
                bx(p, olive, -0.075f, -0.075f, -0.82f, 0.075f, 0.075f, -0.5f); bx(p, 0x2E3324, -0.03f, -0.075f, -0.95f, 0.03f, -0.03f, -0.82f);
                bx(p, blk, -0.015f, -0.14f, -0.12f, 0.015f, -0.04f, -0.08f); bx(p, blk, -0.015f, -0.14f, 0.02f, 0.015f, -0.04f, 0.06f);
                bx(p, blk, 0.04f, 0.0f, -0.1f, 0.07f, 0.05f, 0.0f);
                break;
            case Battle.W_LAW:
                bx(p, olive, -0.035f, -0.035f, -0.45f, 0.035f, 0.035f, 0.35f); bx(p, blk, -0.04f, -0.04f, -0.47f, 0.04f, 0.04f, -0.43f);
                bx(p, blk, -0.01f, 0.035f, -0.1f, 0.01f, 0.07f, -0.08f); bx(p, blk, -0.012f, -0.1f, -0.05f, 0.012f, -0.035f, 0.0f);
                break;
            case Battle.W_M9:
                bx(p, blk, -0.015f, -0.02f, -0.18f, 0.015f, 0.02f, 0.02f); bx(p, blk, -0.014f, -0.12f, -0.02f, 0.014f, -0.02f, 0.02f); bx(p, gry, -0.016f, 0.0f, -0.18f, 0.016f, 0.03f, 0.0f);
                break;
            case Battle.W_M16: case Battle.W_GOLD_AK: case Battle.W_MP5: {
                int c1 = w == Battle.W_GOLD_AK ? 0xD4A62A : blk, c2 = w == Battle.W_GOLD_AK ? 0xB88A1E : gry;
                float bl = w == Battle.W_MP5 ? -0.4f : -0.62f;
                bx(p, c2, -0.03f, -0.05f, -0.2f, 0.03f, 0.03f, 0.12f); bx(p, c1, -0.012f, -0.01f, bl, 0.012f, 0.012f, -0.2f); bx(p, c1, -0.026f, -0.035f, bl + 0.15f, 0.026f, 0.02f, -0.2f);
                bx(p, c1, -0.02f, -0.14f, -0.1f, 0.02f, -0.05f, -0.04f); bx(p, c1, -0.015f, -0.11f, 0.04f, 0.015f, -0.05f, 0.08f); bx(p, c1, -0.022f, -0.06f, 0.12f, 0.022f, 0.01f, 0.36f);
                bx(p, c1, -0.01f, 0.03f, 0.0f, 0.01f, 0.07f, 0.05f);
                break; }
            case Battle.W_870:
                bx(p, gry, -0.03f, -0.05f, -0.15f, 0.03f, 0.03f, 0.1f); bx(p, blk, -0.016f, -0.0f, -0.7f, 0.016f, 0.03f, -0.15f); bx(p, wood, -0.03f, -0.05f, -0.5f, 0.03f, -0.0f, -0.3f);
                bx(p, wood, -0.024f, -0.09f, 0.1f, 0.024f, 0.0f, 0.38f); bx(p, blk, -0.012f, -0.04f, -0.66f, 0.012f, -0.0f, -0.15f);
                break;
            case Battle.W_M79:
                bx(p, olive, -0.04f, -0.04f, -0.5f, 0.04f, 0.04f, -0.05f); bx(p, wood, -0.03f, -0.08f, -0.05f, 0.03f, 0.0f, 0.32f); bx(p, blk, -0.012f, -0.12f, -0.04f, 0.012f, -0.04f, 0.0f);
                break;
            case Battle.W_M60:
                bx(p, gry, -0.04f, -0.06f, -0.25f, 0.04f, 0.04f, 0.12f); bx(p, blk, -0.016f, -0.016f, -0.78f, 0.016f, 0.016f, -0.25f); bx(p, olive, -0.04f, -0.05f, -0.45f, 0.04f, 0.02f, -0.25f);
                bx(p, 0x3E4A2E, -0.09f, -0.18f, -0.15f, -0.02f, -0.06f, 0.0f); bx(p, olive, -0.025f, -0.08f, 0.12f, 0.025f, 0.02f, 0.38f);
                break;
            case Battle.W_HANDMINI:
                bx(p, 0x3C3C3C, -0.05f, -0.05f, -0.2f, 0.05f, 0.05f, 0.15f);
                for (int i = 0; i < 6; i++) { float a = i * 1.047f; float ox = (float) Math.cos(a) * 0.028f, oy = (float) Math.sin(a) * 0.028f; bx(p, 0x5A5A5A, ox - 0.006f, oy - 0.006f, -0.6f, ox + 0.006f, oy + 0.006f, -0.2f); }
                bx(p, 0x4F5A3A, 0.05f, -0.12f, -0.1f, 0.16f, 0.0f, 0.1f); bx(p, 0x222222, -0.015f, -0.13f, 0.0f, 0.015f, -0.05f, 0.04f);
                break;
            case Battle.W_AT4:
                bx(p, 0x5E6A3E, -0.045f, -0.045f, -0.5f, 0.045f, 0.045f, 0.45f); bx(p, blk, -0.012f, -0.12f, -0.1f, 0.012f, -0.045f, -0.05f); bx(p, blk, 0.045f, 0.0f, -0.15f, 0.07f, 0.04f, -0.05f);
                break;
            case Battle.W_MACHETE:
                bx(p, 0x8A8A8A, -0.004f, -0.02f, -0.5f, 0.004f, 0.03f, -0.05f); bx(p, wood, -0.012f, -0.02f, -0.05f, 0.012f, 0.02f, 0.08f);
                break;
            case Battle.W_M2:
                bx(p, 0x2B2B2B, -0.08f, -0.08f, -0.3f, 0.08f, 0.09f, 0.25f); bx(p, 0x222222, -0.025f, -0.025f, -1.3f, 0.025f, 0.025f, -0.3f);
                bx(p, 0x2B2B2B, -0.16f, -0.05f, 0.22f, 0.16f, -0.02f, 0.26f); bx(p, 0x3E4A2E, 0.08f, -0.12f, -0.15f, 0.22f, 0.05f, 0.05f);
                break;
            default: // door minigun on its pintle
                bx(p, 0x3C3C3C, -0.045f, -0.045f, -0.15f, 0.045f, 0.045f, 0.12f);
                for (int i = 0; i < 6; i++) { float a = i * 1.047f; float ox = (float) Math.cos(a) * 0.026f, oy = (float) Math.sin(a) * 0.026f; bx(p, 0x4A4A4A, ox - 0.006f, oy - 0.006f, -0.6f, ox + 0.006f, oy + 0.006f, -0.15f); }
                bx(p, 0x555555, -0.035f, -0.035f, -0.56f, 0.035f, 0.035f, -0.52f);
                bx(p, 0x2E2E2E, -0.12f, -0.02f, 0.12f, 0.12f, 0.0f, 0.15f); bx(p, 0x2E2E2E, -0.012f, -0.25f, -0.02f, 0.012f, -0.045f, 0.02f);
                bx(p, 0x4F5A3A, 0.05f, -0.09f, -0.12f, 0.13f, -0.02f, 0.02f);
        }
        Model m = mb.end(); owned.add(m); return m;
    }
    private Model handsModel() {
        mb.begin(); MeshPartBuilder p = mb.part("h", GL20.GL_TRIANGLES, ATTR, new Material(ColorAttribute.createDiffuse(Color.WHITE)));
        bx(p, 0x5A5444, 0.01f, -0.11f, 0.05f, 0.06f, -0.06f, 0.34f); bx(p, 0x2A2A24, 0.0f, -0.12f, 0.02f, 0.05f, -0.05f, 0.08f); // right sleeve + glove
        bx(p, 0x5A5444, -0.1f, -0.1f, -0.36f, -0.05f, -0.05f, -0.12f); bx(p, 0x2A2A24, -0.06f, -0.07f, -0.38f, -0.015f, -0.03f, -0.33f);
        Model m = mb.end(); owned.add(m); return m;
    }

    @Override public void dispose() { for (Model m : owned) m.dispose(); if (minimap != null) minimap.dispose(); }

    /** For translucent things (rotor blur etc) if needed later. */
    static Material translucent(Color col, float a) { return new Material(ColorAttribute.createDiffuse(col), new BlendingAttribute(a)); }
}
