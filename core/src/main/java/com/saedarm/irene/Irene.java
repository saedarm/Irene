package com.saedarm.irene;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.decals.CameraGroupStrategy;
import com.badlogic.gdx.graphics.g3d.decals.Decal;
import com.badlogic.gdx.graphics.g3d.decals.DecalBatch;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.saedarm.irene.Battle.*;

import java.util.ArrayList;
import java.util.zip.Deflater;

/** Screens, camera, effects, HUD. Turns Battle events into sound, blood, tracers and feedback. */
public final class Irene extends ApplicationAdapter {
    enum Screen { TITLE, CHAPTERS, KIT, SETTINGS, PLAY, PAUSE, RESULT }
    public static final int MODE_STORY = 0, MODE_ROAM = 1, MODE_SIEGE = 2;

    // launch flags
    public boolean solo, autoplay; public String shotFile; public float shotAt = -1;
    public int flagMode = -1, flagChapter = -1, flagStage = -1, flagKit = -1, flagDiff = -1, flagNight = -1;

    private Screen screen = Screen.TITLE, settingsBack = Screen.TITLE;
    private Save save; private Sfx sfx; private Art art;
    private Battle b; private Bot bot; private int pickMode, pickChapter, pickKit, pickStage; private long seed = System.nanoTime();
    private PerspectiveCamera cam, vmCam; private ModelBatch batch; private Environment env, vmEnv; private DecalBatch decals;
    private SpriteBatch sb; private ShapeRenderer sr; private ExtendViewport ui;
    private BitmapFont f18, f26, f46, f110; private final GlyphLayout gl = new GlyphLayout();
    private Texture white, soft, spark; private TextureRegion softR, sparkR;
    private float acc, playT, resultT, uiT, slowT;
    private boolean clicked, nvg; private final Vector2 mouse = new Vector2();
    private int lastW, lastH;
    private final DirectionalLight sun = new DirectionalLight(), fill = new DirectionalLight();
    private final PointLight[] pts = new PointLight[5];
    private final ArrayList<float[]> flashes = new ArrayList<>(); // light flashes {x,y,z,life,r,g,b,intensity}

    // view feel
    private float fovNow = 75, shake, recoilP, recoilY, kick, vmSwitch, landDip, hitMark, killMark;
    private final ArrayList<float[]> hurts = new ArrayList<>();
    private final ArrayList<String[]> feed = new ArrayList<>(); private final ArrayList<Float> feedT = new ArrayList<>();
    private String banner = ""; private float bannerT;
    private final ArrayList<float[]> floatText = new ArrayList<>(); private final ArrayList<String> floatStr = new ArrayList<>();
    private final ArrayList<float[]> gunshipShots = new ArrayList<>();

    // ---------------------------------------------------------------- particles (decals)
    private static final int MAXP = 2600, MAXSPLAT = 180;
    private final Decal[] pd = new Decal[MAXP];
    private final float[] px = new float[MAXP], py = new float[MAXP], pz = new float[MAXP], pvx = new float[MAXP], pvy = new float[MAXP], pvz = new float[MAXP],
            plife = new float[MAXP], pmax = new float[MAXP], ps0 = new float[MAXP], ps1 = new float[MAXP], pr = new float[MAXP], pg = new float[MAXP], pb = new float[MAXP], pa = new float[MAXP], pgrav = new float[MAXP];
    private final int[] pkind = new int[MAXP];
    private int pn;
    private final Decal[] splats = new Decal[MAXSPLAT]; private final float[] splatT = new float[MAXSPLAT]; private int splatN, splatHead;
    private final Decal[] fixed = new Decal[64];
    private final ArrayList<float[]> tracers = new ArrayList<>();
    private final ArrayList<float[]> litTires = new ArrayList<>();
    private float flashT; private final Vector3 muzzle = new Vector3();
    private final ArrayList<Integer> dyn = new ArrayList<>();

    private final Matrix4 m4 = new Matrix4(), base = new Matrix4();
    private final Vector3 v3 = new Vector3(), v3b = new Vector3();

    @Override public void create() {
        save = new Save(); sfx = new Sfx(); sfx.volume = save.sfx; sfx.musicVolume = save.music;
        batch = new ModelBatch(); sb = new SpriteBatch(); sr = new ShapeRenderer(); ui = new ExtendViewport(1280, 720);
        FreeTypeFontGenerator g = new FreeTypeFontGenerator(Gdx.files.internal("fonts/DejaVuSans-Bold.ttf"));
        f18 = font(g, 18); f26 = font(g, 26); f46 = font(g, 46); f110 = font(g, 110); g.dispose();
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888); pm.setColor(Color.WHITE); pm.fill(); white = new Texture(pm); pm.dispose();
        soft = radial(64, 2.2f); spark = radial(32, 0.8f); softR = new TextureRegion(soft); sparkR = new TextureRegion(spark);
        cam = new PerspectiveCamera(75, Gdx.graphics.getWidth(), Gdx.graphics.getHeight()); cam.near = 0.06f; cam.far = 560;
        vmCam = new PerspectiveCamera(58, Gdx.graphics.getWidth(), Gdx.graphics.getHeight()); vmCam.near = 0.01f; vmCam.far = 5; vmCam.position.set(0, 0, 0); vmCam.lookAt(0, 0, -1); vmCam.update();
        env = new Environment(); vmEnv = new Environment();
        env.add(sun); env.add(fill);
        for (int i = 0; i < pts.length; i++) { pts[i] = new PointLight().set(0, 0, 0, 0, -100, 0, 0); env.add(pts[i]); }
        decals = new DecalBatch(MAXP + MAXSPLAT + 128, new CameraGroupStrategy(cam));
        for (int i = 0; i < MAXP; i++) pd[i] = Decal.newDecal(1, 1, softR, true);
        for (int i = 0; i < MAXSPLAT; i++) splats[i] = Decal.newDecal(1, 1, softR, true);
        for (int i = 0; i < fixed.length; i++) fixed[i] = Decal.newDecal(1, 1, softR, true);
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean keyDown(int k) { return key(k); }
            @Override public boolean touchDown(int x, int y, int p, int btn) { if (btn == Buttons.LEFT) clicked = true; return true; }
            @Override public boolean scrolled(float ax, float ay) { if (screen == Screen.PLAY && b != null) b.input.presses |= Battle.Input.SWAP; return true; }
        });
        pickChapter = flagChapter >= 0 ? flagChapter : save.chapter; pickKit = flagKit >= 0 ? flagKit : save.kit;
        pickMode = flagMode >= 0 ? flagMode : MODE_STORY; pickStage = Math.max(0, flagStage);
        if (flagDiff >= 0) save.difficulty = flagDiff;
        if (flagNight >= 0) save.nightPref = flagNight;
        if (save.fullscreen && !solo) Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
        if (solo || autoplay) startGame();
    }
    private BitmapFont font(FreeTypeFontGenerator g, int size) {
        FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter(); p.size = size; p.minFilter = Texture.TextureFilter.Linear; p.magFilter = Texture.TextureFilter.Linear;
        p.borderWidth = size > 30 ? 2 : 1; p.borderColor = new Color(0, 0, 0, 0.7f); return g.generateFont(p);
    }
    private Texture radial(int s, float pow) {
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888); pm.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < s; y++) for (int x = 0; x < s; x++) {
            float dx = (x + .5f) / s * 2 - 1, dy = (y + .5f) / s * 2 - 1, d = (float) Math.sqrt(dx * dx + dy * dy);
            float a = (float) Math.pow(Math.max(0, 1 - d), pow); pm.setColor(1, 1, 1, a); pm.drawPixel(x, y);
        }
        Texture t = new Texture(pm); t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear); pm.dispose(); return t;
    }

    // ================================================================ starting a game
    private Mode makeMode() {
        if (pickMode == MODE_ROAM) return new FreeRoam();
        if (pickMode == MODE_SIEGE) return new Siege();
        return new Story(pickChapter, pickStage);
    }
    private boolean nightFor(Mode m) { return save.nightPref == 1 || (save.nightPref == 0 && m.defaultNight()); }
    private void startGame() {
        Mode m = makeMode();
        int kit = pickKit;
        boolean sna = pickMode == MODE_STORY && Story.SNA[pickChapter];
        if (sna && Battle.KITS[kit].team != Battle.SNA) kit = 4;
        if (!sna && Battle.KITS[kit].team != Battle.US) kit = 0;
        b = new Battle(seed++, m, kit, save.difficulty, nightFor(m));
        bot = autoplay ? new Bot(b) : null;
        if (art != null) art.dispose();
        art = new Art(b.city); gunMi = null;
        dyn.clear(); for (int i = 0; i < b.city.boxes.size(); i++) if (b.city.boxes.get(i).dynamic) dyn.add(i);
        if (b.player.state == Battle.S_FOOT) b.input.yaw = b.player.yaw;
        if (b.player.state == Battle.S_VEH) b.input.yaw = b.vehicles.get(b.player.veh).yaw;
        b.input.pitch = -0.05f;
        pn = 0; splatN = 0; splatHead = 0; tracers.clear(); litTires.clear(); hurts.clear(); feed.clear(); feedT.clear(); floatText.clear(); floatStr.clear(); flashes.clear(); gunshipShots.clear();
        for (int i = 0; i < b.city.tires.size(); i += 4) litTires.add(b.city.tires.get(i));
        acc = 0; playT = 0; resultT = 0; slowT = 0; nvg = false;
        banner = m.title(); bannerT = 3;
        screen = Screen.PLAY; catchMouse(true);
        save.kit = pickKit; save.chapter = pickChapter; save.flush();
    }
    private void catchMouse(boolean c) { if (!autoplay) Gdx.input.setCursorCatched(c); }

    private boolean key(int k) {
        if (screen == Screen.PLAY) {
            Battle.Input in = b.input;
            switch (k) {
                case Keys.SPACE: in.presses |= Battle.Input.JUMP; break;
                case Keys.R: in.presses |= Battle.Input.RELOAD; break;
                case Keys.G: in.presses |= Battle.Input.GRENADE; break;
                case Keys.T: in.presses |= Battle.Input.FLASH; break;
                case Keys.V: in.presses |= Battle.Input.MELEE; break;
                case Keys.Q: in.presses |= Battle.Input.SWAP; break;
                case Keys.X: in.presses |= Battle.Input.SEAT; break;
                case Keys.NUM_1: in.presses |= Battle.Input.SLOT1; break;
                case Keys.NUM_2: in.presses |= Battle.Input.SLOT2; break;
                case Keys.E: case Keys.F: in.presses |= Battle.Input.USE; break;
                case Keys.H: in.presses |= Battle.Input.CALL; break;
                case Keys.C: case Keys.CONTROL_LEFT: in.presses |= Battle.Input.CROUCH; break;
                case Keys.N: nvg = !nvg; sfx.play("click", 0.5f, 0.7f, 0); break;
                case Keys.ESCAPE: case Keys.P: screen = Screen.PAUSE; catchMouse(false); sfx.stopLoops(); break;
                case Keys.F11: save.fullscreen = !save.fullscreen; applyFullscreen(); break;
            }
            return true;
        }
        if (k == Keys.ESCAPE) {
            if (screen == Screen.PAUSE) { screen = Screen.PLAY; catchMouse(true); }
            else if (screen == Screen.SETTINGS) screen = settingsBack;
            else if (screen != Screen.TITLE) screen = Screen.TITLE;
        }
        if (k == Keys.F11) { save.fullscreen = !save.fullscreen; applyFullscreen(); }
        return true;
    }
    private void applyFullscreen() { if (save.fullscreen) Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode()); else Gdx.graphics.setWindowedMode(1600, 900); save.flush(); }

    @Override public void resize(int w, int h) { ui.update(w, h, true); cam.viewportWidth = w; cam.viewportHeight = h; vmCam.viewportWidth = w; vmCam.viewportHeight = h; }

    // ================================================================ main loop
    @Override public void render() {
        float dt = Math.min(0.1f, Gdx.graphics.getDeltaTime()); uiT += dt;
        if (Gdx.graphics.getWidth() != lastW || Gdx.graphics.getHeight() != lastH) { lastW = Gdx.graphics.getWidth(); lastH = Gdx.graphics.getHeight(); resize(lastW, lastH); }
        ui.unproject(mouse.set(Gdx.input.getX(), Gdx.input.getY()));
        sfx.music(screen == Screen.PLAY || screen == Screen.PAUSE);
        float sim = dt;
        if (screen == Screen.PLAY) {
            if (slowT > 0) { slowT -= dt; sim = dt * 0.35f; }
            readInput(dt);
            acc += sim;
            while (acc >= Battle.DT) { if (bot != null) bot.fill(b.input); b.tick(); acc -= Battle.DT; handleEvents(); }
            playT += dt;
            if (b.result != 0) { resultT += dt; if (resultT > 3.2f) { screen = Screen.RESULT; catchMouse(false); sfx.stopLoops(); recordResult(); } }
        }
        if (b != null && (screen == Screen.PLAY || screen == Screen.PAUSE || screen == Screen.RESULT || (screen == Screen.SETTINGS && settingsBack == Screen.PAUSE))) {
            updateEffects(screen == Screen.PLAY ? sim : 0);
            drawWorld(dt);
            drawHud(dt);
        } else {
            Gdx.gl.glClearColor(0.09f, 0.08f, 0.07f, 1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        }
        drawMenus();
        clicked = false;
        if (shotFile != null && shotAt >= 0 && uiT >= shotAt) { screenshot(shotFile); shotFile = null; Gdx.app.exit(); }
    }
    private void screenshot(String file) {
        Pixmap pm = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        PixmapIO.writePNG(new FileHandle(file), pm, Deflater.DEFAULT_COMPRESSION, true); pm.dispose();
    }
    private void readInput(float dt) {
        Battle.Input in = b.input; Unit p = b.player;
        if (bot != null) return;
        float sens = save.sens * 0.0022f * (p.ads ? 0.6f : 1);
        in.yaw -= Gdx.input.getDeltaX() * sens;
        in.pitch -= Gdx.input.getDeltaY() * sens * (save.invertY ? -1 : 1);
        in.pitch = MathUtils.clamp(in.pitch, -1.45f, 1.45f);
        in.moveX = (Gdx.input.isKeyPressed(Keys.D) ? 1 : 0) - (Gdx.input.isKeyPressed(Keys.A) ? 1 : 0);
        in.moveZ = (Gdx.input.isKeyPressed(Keys.W) ? 1 : 0) - (Gdx.input.isKeyPressed(Keys.S) ? 1 : 0);
        in.sprint = Gdx.input.isKeyPressed(Keys.SHIFT_LEFT);
        in.crouch = Gdx.input.isKeyPressed(Keys.C) || Gdx.input.isKeyPressed(Keys.CONTROL_LEFT);
        in.fire = Gdx.input.isButtonPressed(Buttons.LEFT);
        in.ads = Gdx.input.isButtonPressed(Buttons.RIGHT);
        in.use = Gdx.input.isKeyPressed(Keys.E) || Gdx.input.isKeyPressed(Keys.F);
    }
    private void recordResult() {
        if (b.mode instanceof Story) { Story st = (Story) b.mode; if (b.result == 1) { save.done(st.ch, true); save.checkpoint(st.ch, 0); } }
        if (b.mode instanceof Siege) save.best("siege", ((Siege) b.mode).round);
        if (b.mode instanceof FreeRoam) save.best("roam", ((FreeRoam) b.mode).score);
    }

    // ================================================================ events -> feedback
    private static String gunSound(int wi) {
        switch (wi) {
            case Battle.W_SAW: return "saw"; case Battle.W_M14: return "m14"; case Battle.W_AK: case Battle.W_GOLD_AK: return "ak"; case Battle.W_PKM: case Battle.W_M60: return "pkm";
            case Battle.W_MINIGUN: case Battle.W_HANDMINI: return "minigun"; case Battle.W_M2: return "fifty"; case Battle.W_M9: return "pistol"; case Battle.W_870: return "shotgun";
            default: return "car15";
        }
    }
    private void handleEvents() {
        Unit p = b.player;
        for (Event e : b.events) {
            switch (e.type) {
                case Battle.E_SHOT: {
                    int wi = e.a % 32, uid = e.a / 32; boolean mine = uid == p.id;
                    String s = gunSound(wi);
                    if (mine) {
                        sfx.play(s, 0.55f, 0.97f + MathUtils.random(0.06f), 0); Weapon w = Battle.WEAPONS[wi];
                        recoilP += w.recoil * 0.011f * (p.ads ? 0.6f : 1) * (p.crouch ? 0.7f : 1) / Math.max(1, w.pellets * 0.6f); recoilY += MathUtils.random(-1f, 1f) * w.recoil * 0.004f; kick = 1; flashT = 0.05f;
                    } else sfx.at(s, e.x, e.y, e.z, 0.8f, 260);
                    if (MathUtils.random() < 0.5f) flashes.add(new float[]{e.x, e.y, e.z, 0.05f, 1, 0.75f, 0.4f, mine ? 7 : 5});
                    break;
                }
                case Battle.E_TRACER: {
                    boolean mine = Math.abs(e.x - p.x) < 0.6f && Math.abs(e.z - p.z) < 0.6f;
                    if (MathUtils.random() < (e.a == Battle.W_MINIGUN || e.a == Battle.W_M2 ? 0.35f : mine ? 0.5f : 0.4f)) {
                        float ox = e.x, oy = e.y, oz = e.z;
                        if (mine && p.state == Battle.S_FOOT) { ox = muzzle.x; oy = muzzle.y; oz = muzzle.z; }
                        tracers.add(new float[]{ox, oy, oz, e.x2, e.y2, e.z2, 0, mine ? 1 : 0});
                    }
                    if (!mine) { float d = segDist(p.x, p.eye(), p.z, e.x, e.y, e.z, e.x2, e.y2, e.z2); if (d < 2.2f) { sfx.play("whiz", 0.5f, 0.9f + MathUtils.random(0.3f), MathUtils.random(-0.6f, 0.6f)); shake = Math.max(shake, 0.05f); } }
                    break;
                }
                case Battle.E_IMPACT: impact(e.x, e.y, e.z, e.x2, e.y2, e.z2, e.a == 9); if (MathUtils.random() < 0.3f) sfx.at("impact", e.x, e.y, e.z, 0.4f, 40); break;
                case Battle.E_BLOOD: blood(e.x, e.y, e.z, e.x2, e.y2, e.z2, e.a); break;
                case Battle.E_HIT: hitMark = 0.18f; sfx.play("hit", 0.45f); break;
                case Battle.E_HEADSHOT: hitMark = 0.22f; sfx.play("headshot", 0.6f); break;
                case Battle.E_KILL: killMark = 0.35f; sfx.play("kill", 0.5f); if (!(b.mode instanceof Siege)) addFloat(e.a == 1 ? "HEADSHOT +150" : "+100"); break;
                case Battle.E_HURT: { float ang = (float) Math.atan2(-(e.x - p.x), -(e.z - p.z)) - b.input.yaw; hurts.add(new float[]{ang, 1.2f}); shake = Math.max(shake, 0.12f); sfx.play("hurt", 0.5f, 0.9f + MathUtils.random(0.2f), 0); break; }
                case Battle.E_DIE: sfx.at("die", e.x, e.y, e.z, 0.6f, 60); break;
                case Battle.E_ROCKET: sfx.at("rpg", e.x, e.y, e.z, 1f, 300); for (int i = 0; i < 10; i++) puff(e.x, e.y, e.z, MathUtils.random(-2f, 2f), MathUtils.random(0, 1.5f), MathUtils.random(-2f, 2f), 0.6f, 1.6f, 1.5f, .7f, .68f, .64f, .6f, -0.3f); if (Math.hypot(e.x - p.x, e.z - p.z) < 2) { kick = 2; shake = 0.2f; } break;
                case Battle.E_EXPLODE: explosion(e.x, e.y, e.z, e.a == 1); break;
                case Battle.E_HELI_HIT: if (MathUtils.random() < 0.3f) sfx.at("heli_hit", e.x, e.y, e.z, 0.6f, 150); for (int i = 0; i < 3; i++) sparkAt(e.x, e.y, e.z); break;
                case Battle.E_HELI_CRASHING: sfx.at("radio", e.x, e.y, e.z, 1, 2000); addFeed("!", b.helis.get(e.a).call + " going down"); break;
                case Battle.E_HELI_DOWN: sfx.at("crash", e.x, e.y, e.z, 1f, 600); explosion(e.x, e.y, e.z, true); shake = Math.max(shake, 0.6f * Math.max(0, 1 - dist(p, e.x, e.z) / 200)); break;
                case Battle.E_ROPE: sfx.at("rope", e.x, e.y, e.z, 0.8f, 120); break;
                case Battle.E_WAVE: banner = "WAVE " + e.a; bannerT = 2.6f; sfx.play("wave", 0.7f); break;
                case Battle.E_ROUND: banner = "ROUND " + e.a; bannerT = 3.5f; sfx.play("round", 0.8f); break;
                case Battle.E_OBJECTIVE: if (playT > 0.5f) { sfx.play("objective", 0.6f); banner = e.a == 1 ? "JOB DONE" : "NEW OBJECTIVE"; bannerT = 2f; } break;
                case Battle.E_CHECKPOINT: banner = "CHECKPOINT"; bannerT = 2f; sfx.play("objective", 0.5f, 1.3f, 0); if (b.mode instanceof Story) save.checkpoint(((Story) b.mode).ch, e.a); break;
                case Battle.E_WIN: sfx.play("win", 0.8f); banner = "MISSION COMPLETE"; bannerT = 5; break;
                case Battle.E_FAIL: sfx.play("fail", 0.8f); banner = b.resultText.toUpperCase(); bannerT = 5; break;
                case Battle.E_RELOAD: if (dist(p, e.x, e.z) < 1) sfx.play("reload", 0.5f); break;
                case Battle.E_GRENADE: if (dist(p, e.x, e.z) < 1) sfx.play("grenade", 0.4f, 1.3f, 0); break;
                case Battle.E_TIRE: litTires.add(new float[]{e.x, e.z}); if (dist(p, e.x, e.z) < 150) addFeed("~", "Tires burning to the " + compassWord(e.x, e.z)); break;
                case Battle.E_BOARD: sfx.play("rope", 0.6f, 1.2f, 0); break;
                case Battle.E_RADIO: addFeed("RADIO", e.text); sfx.play("radio", 0.35f); break;
                case Battle.E_PICKUP: sfx.play("pickup", 0.6f); if (!(b.mode instanceof Siege)) addFloat("RESUPPLIED"); break;
                case Battle.E_JUMP: sfx.play("jump", 0.35f); break;
                case Battle.E_LAND: if (e.a == p.id) { sfx.play("land", 0.5f); landDip = 0.12f; } break;
                case Battle.E_MANTLE: if (e.a == p.id) { sfx.play("jump", 0.4f, 0.8f, 0); landDip = -0.08f; } break;
                case Battle.E_SLIDE: if (e.a == p.id) sfx.play("slide", 0.5f); break;
                case Battle.E_DOOR: sfx.at("kick", e.x, e.y, e.z, 1, 60); if (e.a == 0) { for (int i = 0; i < 8; i++) puff(e.x, 1.2f, e.z, MathUtils.random(-1f, 1f), MathUtils.random(0, 1f), MathUtils.random(-1f, 1f), 0.2f, 0.9f, 0.6f, .6f, .55f, .45f, .5f, 0); } else addFloat("THE DOOR HOLDS. KICK AGAIN"); shake = Math.max(shake, 0.08f); break;
                case Battle.E_PLANT: sfx.play("click", 0.6f, 0.8f, 0); addFloat("CHARGE SET. STACK UP"); break;
                case Battle.E_BREACH: slowT = 1.4f; sfx.at("breach", e.x, e.y, e.z, 1, 120); shake = Math.max(shake, 0.4f); for (int i = 0; i < 18; i++) { int k = newP(); if (k < 0) break; px[k] = e.x; py[k] = 1.2f; pz[k] = e.z; pvx[k] = MathUtils.random(-7f, 7f); pvy[k] = MathUtils.random(1f, 6f); pvz[k] = MathUtils.random(-7f, 7f); ps0[k] = ps1[k] = MathUtils.random(0.05f, 0.14f); plife[k] = pmax[k] = 1.2f; pr[k] = .35f; pg[k] = .22f; pb[k] = .12f; pa[k] = 1; pgrav[k] = 16; pkind[k] = 3; pd[k].setTextureRegion(sparkR); } banner = "BREACH"; bannerT = 1.2f; break;
                case Battle.E_FLASH: sfx.at("flash", e.x, e.y, e.z, 1, 120); puff(e.x, e.y + 0.3f, e.z, 0, 0, 0, 1, 9, 0.12f, 1, 1, 1, 1, 0); flashes.add(new float[]{e.x, e.y + 0.5f, e.z, 0.15f, 1, 1, 1, 60}); for (int i = 0; i < 10; i++) puff(e.x, e.y + 0.3f, e.z, MathUtils.random(-1f, 1f), MathUtils.random(0, 1.5f), MathUtils.random(-1f, 1f), 0.4f, 2.2f, 2.5f, .85f, .85f, .85f, .6f, -0.2f); break;
                case Battle.E_MELEE: sfx.at("hit", e.x, e.y, e.z, 0.8f, 30); if (e.a == p.id) kick = 2.5f; break;
                case Battle.E_VEH_ENTER: if (e.a == p.id) { sfx.play("kick", 0.4f, 1.4f, 0); if (p.vseat == 0) b.input.yaw = b.vehicles.get(p.veh).yaw; } break;
                case Battle.E_VEH_EXIT: if (e.a == p.id) sfx.play("kick", 0.4f, 1.2f, 0); break;
                case Battle.E_VEH_HIT: if (MathUtils.random() < 0.3f) sfx.at("heli_hit", e.x, e.y, e.z, 0.5f, 80); sparkAt(e.x, e.y, e.z); break;
                case Battle.E_VEH_DOWN: sfx.at("crash", e.x, e.y, e.z, 1, 300); addFeed("!", "Humvee destroyed"); break;
                case Battle.E_CRASH: sfx.at("crash", e.x, e.y, e.z, 0.5f, 60); shake = Math.max(shake, 0.25f); break;
                case Battle.E_CAR_STOP: sfx.at("heli_hit", e.x, e.y, e.z, 1, 300); for (int i = 0; i < 12; i++) puff(e.x, 1.2f, e.z, MathUtils.random(-0.5f, 0.5f), MathUtils.random(1f, 2.5f), MathUtils.random(-0.5f, 0.5f), 0.5f, 2.5f, 3, .9f, .9f, .9f, .6f, -0.2f); break;
                case Battle.E_BUY: sfx.play("buy", 0.7f); break;
                case Battle.E_DENY: sfx.play("deny", 0.6f); addFloat("NOT ENOUGH POINTS"); break;
                case Battle.E_POINTS: addFloat("+" + e.a); break;
                case Battle.E_POWER: sfx.play("objective", 0.8f, 0.7f, 0); banner = "POWER ON"; bannerT = 2.5f; break;
                case Battle.E_BOX: sfx.at("box", e.x, e.y, e.z, 1, 60); break;
                case Battle.E_BOX_MOVE: sfx.play("box", 0.8f, 0.7f, 0); for (int i = 0; i < 20; i++) puff(e.x, 1, e.z, MathUtils.random(-1f, 1f), MathUtils.random(2f, 6f), MathUtils.random(-1f, 1f), 0.3f, 1.4f, 1.5f, .7f, .8f, 1, .7f, 0); break;
                case Battle.E_PAP: sfx.at(e.a == 0 ? "pap" : "win", e.x, e.y, e.z, 1, 60); break;
                case Battle.E_PERK: sfx.play("perk", 0.8f); banner = Siege.PERKS[e.a].toUpperCase(); bannerT = 2f; break;
                case Battle.E_POWERUP: if (e.a >= 0) { sfx.play("powerup", 0.9f); banner = Siege.PU_NAMES[e.a].toUpperCase(); bannerT = 2.5f; } break;
                case Battle.E_EGG: sfx.play("headshot", 0.8f, e.a >= 20 ? 1.5f : 0.8f, 0); for (int i = 0; i < 12; i++) sparkAt(e.x, e.y + 0.3f, e.z); if (e.a == 10) { banner = "SPECTRE ON STATION"; bannerT = 3; } break;
                case Battle.E_GUNSHIP: gunshipShots.add(new float[]{e.x, e.y, e.z, 0.15f}); break;
                case Battle.E_REBUILD: sfx.at("impact", e.x, e.y, e.z, 0.8f, 30); break;
                case Battle.E_PLANK: sfx.at("kick", e.x, e.y, e.z, 0.6f, 50); break;
                case Battle.E_DOWNED: banner = "DOWN"; bannerT = 2; sfx.play("hurt", 1, 0.6f, 0); break;
                case Battle.E_REVIVE: banner = "BACK ON YOUR FEET"; bannerT = 2; break;
                case Battle.E_SPAWN: banner = "BACK AT THE AIRFIELD"; bannerT = 2; break;
            }
        }
        b.events.clear();
    }
    private static float dist(Unit p, float x, float z) { return (float) Math.hypot(p.x - x, p.z - z); }
    private String compassWord(float x, float z) {
        Unit p = b.player; double a = Math.toDegrees(Math.atan2(x - p.x, -(z - p.z))); if (a < 0) a += 360;
        String[] w = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"}; return w[(int) Math.round(a / 45) % 8];
    }
    private static float segDist(float px, float py, float pz, float ax, float ay, float az, float bx, float by, float bz) {
        float dx = bx - ax, dy = by - ay, dz = bz - az, l2 = dx * dx + dy * dy + dz * dz; if (l2 < 1e-6f) return 999;
        float t = MathUtils.clamp(((px - ax) * dx + (py - ay) * dy + (pz - az) * dz) / l2, 0, 1);
        float qx = ax + dx * t - px, qy = ay + dy * t - py, qz = az + dz * t - pz; return (float) Math.sqrt(qx * qx + qy * qy + qz * qz);
    }
    private void addFeed(String who, String s) { feed.add(new String[]{who, s}); feedT.add(7f); while (feed.size() > 6) { feed.remove(0); feedT.remove(0); } }
    private void addFloat(String s) { floatText.add(new float[]{0, 1.1f}); floatStr.add(s); while (floatText.size() > 6) { floatText.remove(0); floatStr.remove(0); } }

    // ================================================================ particles
    private int newP() { if (pn >= MAXP - 2) return -1; return pn++; }
    private void puff(float x, float y, float z, float vx, float vy, float vz, float s0, float s1, float life, float r, float g, float bl, float a, float grav) {
        int i = newP(); if (i < 0) return;
        px[i] = x; py[i] = y; pz[i] = z; pvx[i] = vx; pvy[i] = vy; pvz[i] = vz; ps0[i] = s0; ps1[i] = s1; plife[i] = pmax[i] = life; pr[i] = r; pg[i] = g; pb[i] = bl; pa[i] = a; pgrav[i] = grav; pkind[i] = 0;
        pd[i].setTextureRegion(softR);
    }
    private void blood(float x, float y, float z, float dx, float dy, float dz, int kind) {
        if (!save.blood) { for (int i = 0; i < 4; i++) puff(x, y, z, MathUtils.random(-1f, 1f), MathUtils.random(0.5f), MathUtils.random(-1f, 1f), 0.15f, 0.35f, 0.3f, .55f, .5f, .45f, .5f, 0); return; }
        int n = kind == 2 ? 46 : kind == 1 ? 28 : 16;
        for (int k = 0; k < n; k++) {
            int i = newP(); if (i < 0) return;
            float sp = kind == 2 ? 6 : 3.5f;
            px[i] = x; py[i] = y; pz[i] = z;
            pvx[i] = dx * sp * MathUtils.random(0.3f, 1.2f) + MathUtils.random(-1.5f, 1.5f); pvy[i] = dy * sp * 0.5f + MathUtils.random(0.2f, 2.5f); pvz[i] = dz * sp * MathUtils.random(0.3f, 1.2f) + MathUtils.random(-1.5f, 1.5f);
            float s = MathUtils.random(0.05f, 0.13f); ps0[i] = s; ps1[i] = s * 0.8f; plife[i] = pmax[i] = MathUtils.random(0.6f, 1.4f);
            pr[i] = MathUtils.random(0.35f, 0.55f); pg[i] = 0.01f; pb[i] = 0.02f; pa[i] = 0.95f; pgrav[i] = 13; pkind[i] = 1; pd[i].setTextureRegion(sparkR);
        }
        for (int k = 0; k < (kind == 0 ? 2 : 4); k++) puff(x, y, z, dx * 1.2f + MathUtils.random(-.4f, .4f), dy + .2f, dz * 1.2f + MathUtils.random(-.4f, .4f), 0.12f, 0.6f, 0.35f, .55f, .03f, .03f, .55f, 0);
        float[] nrm = new float[2];
        float t = b.city.ray(x, y, z, dx, dy, dz, 3.2f, nrm);
        if (t < 3.1f) splat(x + dx * t, y + dy * t, z + dz * t, (int) nrm[0], nrm[1], MathUtils.random(0.35f, 0.8f) * (kind == 2 ? 1.6f : 1));
    }
    private void splat(float x, float y, float z, int axis, float sign, float size) {
        Decal d = splats[splatHead]; splatHead = (splatHead + 1) % MAXSPLAT; if (splatN < MAXSPLAT) splatN++;
        d.setTextureRegion(softR); d.setDimensions(size, size * MathUtils.random(0.7f, 1.3f));
        d.setColor(MathUtils.random(0.28f, 0.42f), 0.01f, 0.015f, 0.92f);
        if (axis == 1) { d.setPosition(x, y + 0.02f, z); d.setRotation(v3.set(0, 1, 0), v3b.set(MathUtils.random(-1f, 1f), 0, 1).nor()); }
        else if (axis == 0) { d.setPosition(x + sign * 0.03f, y, z); d.setRotation(v3.set(sign, 0, 0), v3b.set(0, 1, 0)); }
        else { d.setPosition(x, y, z + sign * 0.03f); d.setRotation(v3.set(0, 0, sign), v3b.set(0, 1, 0)); }
        splatT[(splatHead + MAXSPLAT - 1) % MAXSPLAT] = 40;
    }
    private void impact(float x, float y, float z, float nx, float ny, float nz, boolean metal) {
        if (metal) { for (int k = 0; k < 4; k++) sparkAt(x, y, z); return; }
        for (int k = 0; k < 3; k++) puff(x, y, z, nx * 1.5f + MathUtils.random(-.6f, .6f), ny * 1.5f + MathUtils.random(0.2f, 1f), nz * 1.5f + MathUtils.random(-.6f, .6f), 0.08f, 0.5f, 0.5f, .72f, .64f, .52f, .6f, -0.2f);
        for (int k = 0; k < 3; k++) { int i = newP(); if (i < 0) return; px[i] = x; py[i] = y; pz[i] = z; pvx[i] = nx * 3 + MathUtils.random(-2f, 2f); pvy[i] = ny * 3 + MathUtils.random(0.5f, 3f); pvz[i] = nz * 3 + MathUtils.random(-2f, 2f); ps0[i] = ps1[i] = 0.035f; plife[i] = pmax[i] = 0.5f; pr[i] = .45f; pg[i] = .38f; pb[i] = .3f; pa[i] = 1; pgrav[i] = 14; pkind[i] = 3; pd[i].setTextureRegion(sparkR); }
        Decal d = splats[splatHead]; splatHead = (splatHead + 1) % MAXSPLAT; if (splatN < MAXSPLAT) splatN++;
        d.setTextureRegion(sparkR); d.setDimensions(0.09f, 0.09f); d.setColor(0.08f, 0.07f, 0.06f, 0.85f);
        if (ny != 0) { d.setPosition(x, y + 0.015f, z); d.setRotation(v3.set(0, 1, 0), v3b.set(0, 0, 1)); }
        else { d.setPosition(x + nx * 0.02f, y, z + nz * 0.02f); d.setRotation(v3.set(nx, 0, nz), v3b.set(0, 1, 0)); }
        splatT[(splatHead + MAXSPLAT - 1) % MAXSPLAT] = 25;
    }
    private void sparkAt(float x, float y, float z) { int i = newP(); if (i < 0) return; px[i] = x; py[i] = y; pz[i] = z; pvx[i] = MathUtils.random(-5f, 5f); pvy[i] = MathUtils.random(-1f, 5f); pvz[i] = MathUtils.random(-5f, 5f); ps0[i] = 0.08f; ps1[i] = 0.02f; plife[i] = pmax[i] = 0.3f; pr[i] = 1; pg[i] = .85f; pb[i] = .4f; pa[i] = 1; pgrav[i] = 10; pkind[i] = 2; pd[i].setTextureRegion(sparkR); }
    private void explosion(float x, float y, float z, boolean big) {
        Unit p = b.player; float d = dist(p, x, z);
        sfx.at("explosion", x, y, z, 1, 500);
        shake = Math.max(shake, (big ? 0.7f : 0.5f) * Math.max(0, 1 - d / 60));
        flashes.add(new float[]{x, y + 1, z, 0.25f, 1, 0.6f, 0.25f, big ? 70 : 45});
        puff(x, y + 0.5f, z, 0, 0, 0, 1f, big ? 7 : 5, 0.18f, 1, .95f, .7f, 1, 0);
        for (int k = 0; k < (big ? 16 : 10); k++) puff(x, y + 0.5f, z, MathUtils.random(-4f, 4f), MathUtils.random(1f, 5f), MathUtils.random(-4f, 4f), 0.8f, 2.6f, MathUtils.random(0.3f, 0.6f), 1, MathUtils.random(.45f, .7f), .15f, .95f, -1);
        for (int k = 0; k < (big ? 22 : 14); k++) puff(x, y + 0.6f, z, MathUtils.random(-2.5f, 2.5f), MathUtils.random(1f, 4f), MathUtils.random(-2.5f, 2.5f), 1.2f, 5, MathUtils.random(2f, 4.5f), .22f, .2f, .18f, .75f, -0.6f);
        for (int k = 0; k < 18; k++) { int i = newP(); if (i < 0) break; px[i] = x; py[i] = y + .4f; pz[i] = z; pvx[i] = MathUtils.random(-9f, 9f); pvy[i] = MathUtils.random(3f, 11f); pvz[i] = MathUtils.random(-9f, 9f); ps0[i] = ps1[i] = MathUtils.random(0.06f, 0.16f); plife[i] = pmax[i] = 1.4f; pr[i] = .2f; pg[i] = .17f; pb[i] = .14f; pa[i] = 1; pgrav[i] = 18; pkind[i] = 3; pd[i].setTextureRegion(sparkR); }
        Decal s = splats[splatHead]; splatHead = (splatHead + 1) % MAXSPLAT; if (splatN < MAXSPLAT) splatN++;
        s.setTextureRegion(softR); s.setDimensions(5, 5); s.setColor(0.06f, 0.05f, 0.04f, 0.8f); s.setPosition(x, b.city.groundAt(x, z, 0.5f, y + 1) + 0.03f, z); s.setRotation(v3.set(0, 1, 0), v3b.set(0, 0, 1));
        splatT[(splatHead + MAXSPLAT - 1) % MAXSPLAT] = 60;
    }

    private float emitT;
    private void updateEffects(float dt) {
        emitT += dt;
        Unit p = b.player;
        if (emitT > 0.06f && dt > 0) {
            emitT = 0;
            for (float[] t : litTires) {
                if (dist(p, t[0], t[1]) > 260) continue;
                puff(t[0] + MathUtils.random(-0.6f, 0.6f), 0.8f, t[1] + MathUtils.random(-0.6f, 0.6f), MathUtils.random(-0.3f, 0.3f) + 0.6f, MathUtils.random(2.2f, 3.5f), MathUtils.random(-0.3f, 0.3f), 0.8f, 5.5f, 6, .07f, .065f, .06f, .7f, -0.15f);
                if (MathUtils.random() < 0.6f) puff(t[0] + MathUtils.random(-0.5f, 0.5f), 0.5f, t[1] + MathUtils.random(-0.5f, 0.5f), 0, MathUtils.random(1f, 2f), 0, 0.5f, 0.2f, 0.35f, 1, MathUtils.random(.35f, .6f), .1f, .9f, -1);
            }
            for (Heli h : b.helis) {
                if (h.state == Battle.H_DOWN || h.state == Battle.H_CRASH) {
                    puff(h.x + MathUtils.random(-1f, 1f), h.y + 1, h.z + MathUtils.random(-1f, 1f), 0.5f, MathUtils.random(2f, 3.4f), 0, 1, 6, 6, .1f, .09f, .085f, .75f, -0.1f);
                    if (h.state == Battle.H_DOWN && h.timer < 120 && MathUtils.random() < 0.5f) puff(h.x + MathUtils.random(-1.5f, 1.5f), h.y, h.z + MathUtils.random(-1.5f, 1.5f), 0, 1.5f, 0, 0.6f, 0.2f, 0.4f, 1, .5f, .12f, .9f, -1);
                } else {
                    float g = b.city.groundAt(h.x, h.z, 1, h.y);
                    if (h.y - g < 22 && dist(p, h.x, h.z) < 200) for (int k = 0; k < 2; k++) {
                        float a = MathUtils.random(6.283f), r = MathUtils.random(2f, 6f);
                        puff(h.x + MathUtils.cos(a) * r, g + 0.3f, h.z + MathUtils.sin(a) * r, MathUtils.cos(a) * 6, 0.6f, MathUtils.sin(a) * 6, 0.6f, 2.8f, 1.1f, .76f, .67f, .52f, .45f, 0);
                    }
                }
            }
            for (Vehicle v : b.vehicles) {
                if (!v.alive && v.burnT < 90) { puff(v.x + MathUtils.random(-1f, 1f), 1.5f, v.z + MathUtils.random(-1f, 1f), 0.4f, MathUtils.random(2f, 3f), 0, 0.8f, 5, 5, .1f, .09f, .085f, .7f, -0.1f); if (MathUtils.random() < 0.5f) puff(v.x + MathUtils.random(-1f, 1f), 1, v.z + MathUtils.random(-1f, 1f), 0, 1.4f, 0, 0.5f, 0.2f, 0.4f, 1, .5f, .12f, .9f, -1); }
                else if (v.disabled || (v.alive && v.hp < v.maxHp * 0.4f)) puff(v.x - MathUtils.sin(v.yaw) * 2, 1.2f, v.z - MathUtils.cos(v.yaw) * 2, 0, 1.5f, 0, 0.4f, 2.4f, 2.5f, .3f, .3f, .3f, .5f, -0.1f);
                else if (v.alive && Math.abs(v.speed) > 6) puff(v.x + MathUtils.sin(v.yaw) * 2.6f, 0.3f, v.z + MathUtils.cos(v.yaw) * 2.6f, 0, 0.4f, 0, 0.6f, 2.6f, 1.2f, .75f, .66f, .52f, .4f, 0);
            }
        }
        for (Proj pr0 : b.projs) if (pr0.rocket && dt > 0) puff(pr0.x, pr0.y, pr0.z, MathUtils.random(-.3f, .3f), MathUtils.random(.1f, .5f), MathUtils.random(-.3f, .3f), 0.25f, 1.3f, 1.4f, .8f, .78f, .74f, .55f, -0.2f);
        for (int i = 0; i < pn; i++) {
            plife[i] -= dt;
            if (plife[i] <= 0) { kill(i); i--; continue; }
            pvy[i] -= pgrav[i] * dt;
            if (pkind[i] == 0) { pvx[i] *= (1 - 1.5f * dt); pvz[i] *= (1 - 1.5f * dt); if (pgrav[i] < 0) pvy[i] *= (1 - 0.6f * dt); }
            float nx = px[i] + pvx[i] * dt, ny = py[i] + pvy[i] * dt, nz = pz[i] + pvz[i] * dt;
            if ((pkind[i] == 1 || pkind[i] == 3) && pvy[i] < 0) {
                float g = b.city.groundAt(nx, nz, 0.05f, py[i] + 0.05f);
                if (ny <= g) {
                    if (pkind[i] == 1 && MathUtils.random() < 0.5f) splat(nx, g, nz, 1, 1, MathUtils.random(0.2f, 0.5f));
                    if (pkind[i] == 1) { kill(i); i--; continue; } else { ny = g; pvy[i] *= -0.3f; pvx[i] *= 0.5f; pvz[i] *= 0.5f; }
                }
            }
            px[i] = nx; py[i] = ny; pz[i] = nz;
        }
        for (int i = 0; i < MAXSPLAT; i++) if (splatT[i] > 0) splatT[i] -= dt;
        for (int i = tracers.size() - 1; i >= 0; i--) { tracers.get(i)[6] += dt; if (tracers.get(i)[6] > 0.07f) tracers.remove(i); }
        for (int i = flashes.size() - 1; i >= 0; i--) { flashes.get(i)[3] -= dt; if (flashes.get(i)[3] <= 0) flashes.remove(i); }
        for (int i = gunshipShots.size() - 1; i >= 0; i--) { gunshipShots.get(i)[3] -= dt; if (gunshipShots.get(i)[3] <= 0) gunshipShots.remove(i); }
        for (int i = hurts.size() - 1; i >= 0; i--) { hurts.get(i)[1] -= dt; if (hurts.get(i)[1] <= 0) hurts.remove(i); }
        for (int i = feedT.size() - 1; i >= 0; i--) { feedT.set(i, feedT.get(i) - dt); if (feedT.get(i) <= 0) { feedT.remove(i); feed.remove(i); } }
        for (int i = floatText.size() - 1; i >= 0; i--) { floatText.get(i)[1] -= dt; if (floatText.get(i)[1] <= 0) { floatText.remove(i); floatStr.remove(i); } }
        shake = Math.max(0, shake - dt * 1.4f); hitMark -= dt; killMark -= dt; bannerT -= dt; flashT -= dt;
        recoilP *= (1 - Math.min(1, 9 * dt)); recoilY *= (1 - Math.min(1, 9 * dt)); kick *= (1 - Math.min(1, 14 * dt)); landDip *= (1 - Math.min(1, 8 * dt));
    }
    private void kill(int i) {
        int j = --pn; if (i == j) return;
        px[i] = px[j]; py[i] = py[j]; pz[i] = pz[j]; pvx[i] = pvx[j]; pvy[i] = pvy[j]; pvz[i] = pvz[j]; plife[i] = plife[j]; pmax[i] = pmax[j];
        ps0[i] = ps0[j]; ps1[i] = ps1[j]; pr[i] = pr[j]; pg[i] = pg[j]; pb[i] = pb[j]; pa[i] = pa[j]; pgrav[i] = pgrav[j]; pkind[i] = pkind[j];
        Decal t = pd[i]; pd[i] = pd[j]; pd[j] = t;
    }

    // ================================================================ lighting
    private void setupLight() {
        boolean night = b.night;
        float dusk = 0;
        if (b.mode instanceof Story && ((Story) b.mode).ch == 7) dusk = 0.3f; // dawn
        Color sky;
        if (!night) {
            float amb = 0.5f - dusk * 0.2f;
            sky = new Color(0.80f, 0.79f, 0.72f, 1).lerp(new Color(0.55f, 0.5f, 0.5f, 1), dusk);
            env.set(new ColorAttribute(ColorAttribute.AmbientLight, amb * 1.02f, amb, amb * 0.92f, 1));
            sun.set(0.78f - dusk * 0.2f, 0.68f - dusk * 0.25f, 0.54f - dusk * 0.2f, -0.55f, -0.62f, 0.35f);
            fill.set(0.18f, 0.2f, 0.25f, 0.4f, -0.3f, -0.6f);
        } else if (!nvg) {
            sky = new Color(0.035f, 0.04f, 0.07f, 1);
            env.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.13f, 0.14f, 0.2f, 1));
            sun.set(0.14f, 0.16f, 0.24f, 0.3f, -0.8f, 0.4f); fill.set(0, 0, 0, 0, -1, 0);
        } else {
            sky = new Color(0.04f, 0.14f, 0.05f, 1);
            env.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.5f, 0.55f, 0.5f, 1));
            sun.set(0.35f, 0.4f, 0.35f, 0.3f, -0.8f, 0.4f); fill.set(0.1f, 0.1f, 0.1f, -0.3f, -0.4f, -0.6f);
        }
        env.set(new ColorAttribute(ColorAttribute.Fog, sky.r, sky.g, sky.b, 1));
        // point lights: the nearest few fires, muzzle flashes and explosions
        ArrayList<float[]> cand = new ArrayList<>();
        for (float[] f : flashes) cand.add(new float[]{f[0], f[1], f[2], f[4], f[5], f[6], f[7] * Math.min(1, f[3] * 20)});
        if (flashT > 0) cand.add(new float[]{muzzle.x, muzzle.y, muzzle.z, 1, 0.8f, 0.45f, 9});
        float flick = 0.8f + MathUtils.random(0.4f);
        for (float[] t : litTires) cand.add(new float[]{t[0], 1.2f, t[1], 1, 0.5f, 0.15f, 30 * flick});
        for (Heli h : b.helis) if (!h.flying()) cand.add(new float[]{h.x, h.y + 1, h.z, 1, 0.45f, 0.12f, 35 * flick});
        for (Vehicle v : b.vehicles) if (!v.alive && v.burnT < 90) cand.add(new float[]{v.x, 1.5f, v.z, 1, 0.45f, 0.12f, 30 * flick});
        if (b.mode instanceof Siege && ((Siege) b.mode).power) for (float[] l : siegeLamps()) cand.add(new float[]{l[0], 3, l[1], 1, 0.85f, 0.55f, 40});
        float cx = cam.position.x, cz = cam.position.z;
        cand.sort((a, c) -> Float.compare((a[0] - cx) * (a[0] - cx) + (a[2] - cz) * (a[2] - cz) - a[6] * 4, (c[0] - cx) * (c[0] - cx) + (c[2] - cz) * (c[2] - cz) - c[6] * 4));
        float boost = !night ? 0.25f : nvg ? 0.5f : 1;
        for (int i = 0; i < pts.length; i++) {
            if (i < cand.size()) { float[] c = cand.get(i); pts[i].set(c[3], c[4], c[5], c[0], c[1], c[2], c[6] * boost); }
            else pts[i].set(0, 0, 0, 0, -100, 0, 0);
        }
        vmEnv.clear();
        float va = !night ? 0.7f : nvg ? 0.6f : 0.3f;
        vmEnv.set(new ColorAttribute(ColorAttribute.AmbientLight, va, va, va * 0.95f, 1)); vmEnv.add(new DirectionalLight().set(0.8f * va, 0.76f * va, 0.68f * va, -0.3f, -0.8f, -0.4f));
        if (flashT > 0) vmEnv.add(new PointLight().set(1, 0.8f, 0.4f, 0.1f, 0, -0.6f, 0.6f));
        Gdx.gl.glClearColor(sky.r, sky.g, sky.b, 1);
    }
    private ArrayList<float[]> siegeLamps() {
        ArrayList<float[]> l = new ArrayList<>(); City c = b.city;
        l.add(new float[]{c.generator.x, c.generator.z}); l.add(new float[]{c.pap.x, c.pap.z}); l.add(new float[]{c.start.x, c.start.z});
        for (City.Spot s : c.perkSpots) l.add(new float[]{s.x, s.z});
        return l;
    }

    // ================================================================ world drawing
    private void drawWorld(float dt) {
        Unit p = b.player;
        setupLight();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Weapon w = Battle.WEAPONS[p.weapon()];
        boolean third = p.state == Battle.S_VEH && p.vseat == 0;
        float targetFov = save.fov * (p.ads ? (p.weapon() == Battle.W_M14 || p.weapon() == Battle.W_CAR15_AIM ? 0.55f : 0.7f) : 1) + (p.sprinting ? 6 : 0) + (p.slide > 0 ? 8 : 0);
        fovNow += (targetFov - fovNow) * Math.min(1, 14 * dt); cam.fieldOfView = fovNow;
        float yaw = b.input.yaw + recoilY, pitch = b.input.pitch + recoilP;
        if (bot != null) { yaw = p.yaw; pitch = p.pitch; }
        float sx = MathUtils.random(-1f, 1f) * shake * 0.3f, sy = MathUtils.random(-1f, 1f) * shake * 0.3f;
        if (third) {
            Vehicle v = b.vehicles.get(p.veh);
            float cy = bot != null ? v.yaw : yaw, cp = MathUtils.clamp(pitch, -0.6f, 0.5f) - 0.25f;
            float dx = -MathUtils.sin(cy) * MathUtils.cos(cp), dy = MathUtils.sin(cp), dz = -MathUtils.cos(cy) * MathUtils.cos(cp);
            float dist = 9;
            float tx = v.x, ty = 1.8f, tz = v.z;
            float rd = b.city.ray(tx, ty, tz, -dx, -dy + 0.3f, -dz, dist, null);
            cam.position.set(tx - dx * Math.min(dist, rd - 0.3f), ty - dy * Math.min(dist, rd - 0.3f) + 1.2f, tz - dz * Math.min(dist, rd - 0.3f));
            cam.direction.set(dx, dy - 0.12f, dz).nor(); cam.up.set(0, 1, 0);
        } else {
            float eye = p.state == Battle.S_HELI ? p.y + 0.4f : p.state == Battle.S_VEH ? (p.vseat == 1 ? 2.75f : 1.45f) : p.eye();
            float moving = (float) Math.min(1, Math.hypot(p.vx, p.vz) / 5);
            float bob = p.onGround && p.state == Battle.S_FOOT ? MathUtils.sin(p.walkPhase * 2.2f) * 0.045f * moving : 0;
            if (!p.alive) eye = p.y + Math.max(0.25f, 1.6f - p.deadT * 2);
            cam.position.set(p.x, eye + bob - landDip, p.z);
            cam.direction.set(-MathUtils.sin(yaw + sx * 0.1f) * MathUtils.cos(pitch + sy * 0.1f), MathUtils.sin(pitch + sy * 0.1f), -MathUtils.cos(yaw + sx * 0.1f) * MathUtils.cos(pitch + sy * 0.1f));
            cam.up.set(0, 1, 0);
            float roll = p.slide > 0 ? 0.06f : 0; if (!p.alive) roll = Math.min(1.2f, p.deadT); if (p.state == Battle.S_DOWN) roll = 0.3f;
            if (roll != 0) cam.up.rotateRad(cam.direction, roll);
        }
        cam.update();
        sfx.listener(cam.position.x, cam.position.y, cam.position.z, yaw);
        v3.set(cam.direction).crs(cam.up).nor();
        float side = p.ads ? 0 : 0.13f, down = p.ads ? 0.05f : 0.12f;
        muzzle.set(cam.position).mulAdd(cam.direction, 0.75f).mulAdd(v3, side).mulAdd(cam.up, -down);
        float nearest = 999; for (Heli h : b.helis) if (h.flying()) nearest = Math.min(nearest, cam.position.dst(h.x, h.y, h.z));
        sfx.rotor(screen == Screen.PLAY ? Math.max(0, 1 - nearest / 220f) * 0.7f : 0);

        art.reset();
        batch.begin(cam);
        for (int i = 0; i < art.chunks.size; i++) if (cam.frustum.boundsInFrustum(art.chunkBounds.get(i))) batch.render(art.chunks.get(i), env);
        for (int bi : dyn) {
            City.Box bx = b.city.boxes.get(bi);
            if (bx.dead || dist(p, bx.x0, bx.z0) > 200) continue;
            ModelInstance mi = art.tint(bx.color).next();
            mi.transform.setToTranslation((bx.x0 + bx.x1) / 2, (bx.y0 + bx.y1) / 2, (bx.z0 + bx.z1) / 2).scale(bx.x1 - bx.x0, bx.y1 - bx.y0, bx.z1 - bx.z0);
        }
        for (Unit u : b.units) if ((!u.player || third) && (u.alive || u.deadT < 30) && u.state != Battle.S_HELI) drawUnit(u);
        for (Heli h : b.helis) if (!(p.state == Battle.S_HELI && p.heli == h.id)) drawHeli(h); else drawSeatedOthers(h);
        for (Vehicle v : b.vehicles) drawVehicle(v);
        for (int i = 0; i < b.city.crates.size(); i++) { float[] c = b.city.crates.get(i); if (dist(p, c[0], c[1]) < 150) art.crate.next().transform.setToTranslation(c[0], b.city.groundAt(c[0], c[1], 0.4f, 2), c[1]); }
        for (float[] t : b.city.tires) if (dist(p, t[0], t[1]) < 180) art.tire.next().transform.setToTranslation(t[0], 0, t[1]);
        for (Proj pr0 : b.projs) {
            if (pr0.kind == Battle.PR_CHARGE) { art.tint(0x3E4A2E).next().transform.setToTranslation(pr0.x, pr0.y, pr0.z).scale(0.35f, 0.25f, 0.12f); continue; }
            ModelInstance mi = pr0.rocket ? art.rocket.next() : art.grenade.next();
            mi.transform.setToTranslation(pr0.x, pr0.y, pr0.z);
            if (pr0.rocket) mi.transform.rotateRad(Vector3.Y, (float) Math.atan2(pr0.vx, pr0.vz));
        }
        if (b.mode instanceof Siege) drawSiege((Siege) b.mode);
        if (b.mode instanceof FreeRoam) for (FreeRoam.Activity a : ((FreeRoam) b.mode).acts) if (a.kind == FreeRoam.CACHE) art.crate.next().transform.setToTranslation(a.x, 0, a.z).scale(1.5f, 1.5f, 1.5f);
        art.render(batch, env);
        batch.end();

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST); Gdx.gl.glEnable(GL20.GL_BLEND);
        sr.setProjectionMatrix(cam.combined); sr.begin(ShapeRenderer.ShapeType.Line);
        for (float[] t : tracers) {
            float f = t[6] / 0.07f; float hx = t[0] + (t[3] - t[0]) * Math.min(1, f * 1.6f), hy = t[1] + (t[4] - t[1]) * Math.min(1, f * 1.6f), hz = t[2] + (t[5] - t[2]) * Math.min(1, f * 1.6f);
            float tx = t[0] + (t[3] - t[0]) * Math.max(0, f * 1.6f - 0.5f), ty = t[1] + (t[4] - t[1]) * Math.max(0, f * 1.6f - 0.5f), tz = t[2] + (t[5] - t[2]) * Math.max(0, f * 1.6f - 0.5f);
            sr.line(tx, ty, tz, hx, hy, hz, new Color(1, 0.75f, 0.35f, 0.2f), new Color(1, 0.95f, 0.6f, 0.95f));
        }
        if (b.mode instanceof Siege && ((Siege) b.mode).gunshipT > 0) { float[] pl = gunshipPos(); for (float[] g : gunshipShots) sr.line(pl[0], pl[1] - 2, pl[2], g[0], g[1], g[2], new Color(1, 0.9f, 0.6f, 0.9f), new Color(1, 0.6f, 0.3f, 0.9f)); }
        sr.end();

        int fi = 0;
        for (int i = 0; i < MAXSPLAT; i++) if (splatT[i] > 0) { Decal d = splats[i]; if (splatT[i] < 3) d.setColor(d.getColor().r, d.getColor().g, d.getColor().b, Math.min(d.getColor().a, splatT[i] / 3)); decals.add(d); }
        for (int i = 0; i < pn; i++) {
            float life = 1 - plife[i] / pmax[i];
            float s = ps0[i] + (ps1[i] - ps0[i]) * life;
            float a = pa[i] * (pkind[i] == 0 ? (life < 0.1f ? life * 10 : 1 - life) : 1);
            Decal d = pd[i];
            d.setPosition(px[i], py[i], pz[i]); d.setDimensions(s, s); d.setColor(pr[i], pg[i], pb[i], a); d.lookAt(cam.position, cam.up);
            decals.add(d);
        }
        if (flashT > 0 && p.alive) fi = glow(fi, muzzle.x, muzzle.y, muzzle.z, p.state == Battle.S_HELI ? 0.5f : 0.32f, 1, 0.85f, 0.45f, 0.95f, sparkR);
        if (b.mode instanceof Siege) fi = siegeGlows((Siege) b.mode, fi);
        decals.flush();

        if (p.alive && screen != Screen.RESULT && !third) drawViewModel(dt, w);
    }
    private int glow(int fi, float x, float y, float z, float size, float r, float g, float bl, float a, TextureRegion reg) {
        if (fi >= fixed.length) return fi;
        Decal d = fixed[fi]; d.setTextureRegion(reg); d.setPosition(x, y, z); d.setDimensions(size, size); d.setColor(r, g, bl, a); d.lookAt(cam.position, cam.up); decals.add(d);
        return fi + 1;
    }
    private float[] gunshipPos() { float a = uiT * 0.12f; Unit p = b.player; return new float[]{b.city.ax + 44 + MathUtils.cos(a) * 160, 220, b.city.az + 44 + MathUtils.sin(a) * 160, a}; }

    private void drawViewModel(float dt, Weapon w) {
        Unit p = b.player;
        Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
        vmCam.fieldOfView = 58; vmCam.update();
        int wi = p.weapon();
        vmSwitch += ((p.ads ? 1 : 0) - vmSwitch) * Math.min(1, 16 * dt);
        float moving = (float) Math.min(1, Math.hypot(p.vx, p.vz) / 5);
        float t = p.walkPhase * 2.2f;
        float bobX = MathUtils.sin(t * 0.5f) * 0.012f * moving * (1 - vmSwitch * 0.8f), bobY = Math.abs(MathUtils.sin(t * 0.5f)) * 0.01f * moving * (1 - vmSwitch * 0.8f);
        float gx = MathUtils.lerp(0.1f, 0.0f, vmSwitch), gy = MathUtils.lerp(-0.1f, sightDrop(wi) * VM_SCALE, vmSwitch), gz = MathUtils.lerp(-0.24f, -0.3f, vmSwitch);
        if (p.state == Battle.S_HELI) { gx = 0.06f; gy = -0.24f; gz = -0.5f; }
        if (p.state == Battle.S_VEH && p.vseat == 1) { gx = 0; gy = -0.2f; gz = -0.35f; }
        float reload = p.reloadT > 0 ? MathUtils.sin(Math.min(1, 1 - p.reloadT / Math.max(0.1f, w.reload)) * MathUtils.PI) : 0;
        float sprint = p.sprinting ? 1 : 0;
        float melee = p.meleeCd > Battle.MELEE_CD - 0.3f ? MathUtils.sin((Battle.MELEE_CD - p.meleeCd) / 0.3f * MathUtils.PI) : 0;
        m4.idt().translate(gx + bobX + sprint * 0.04f - melee * 0.08f, gy + bobY - reload * 0.12f - sprint * 0.04f, gz + kick * 0.035f - melee * 0.15f);
        m4.rotateRad(Vector3.Y, sprint * 0.7f + melee * 0.8f).rotateRad(Vector3.X, kick * 0.06f - sprint * 0.25f).rotateRad(Vector3.Z, reload * 0.6f).scale(VM_SCALE, VM_SCALE, VM_SCALE);
        if (gunMi == null || gunMi.length != art.guns.length) { gunMi = new ModelInstance[art.guns.length]; for (int i = 0; i < gunMi.length; i++) gunMi[i] = new ModelInstance(art.guns[i]); handsMi = new ModelInstance(art.hands); }
        ModelInstance gun = gunMi[wi]; gun.transform.set(m4);
        batch.begin(vmCam); batch.render(gun, vmEnv);
        if (p.state != Battle.S_HELI && !(p.state == Battle.S_VEH && p.vseat == 1)) { handsMi.transform.set(m4); batch.render(handsMi, vmEnv); }
        batch.end();
    }
    private ModelInstance[] gunMi; private ModelInstance handsMi;
    private static final float VM_SCALE = 0.62f;
    private static float sightDrop(int wi) {
        switch (wi) { case Battle.W_CAR15_AIM: case Battle.W_M14: return -0.085f; case Battle.W_RPG: return -0.09f; case Battle.W_LAW: case Battle.W_AT4: return -0.085f; case Battle.W_SAW: return -0.07f; case Battle.W_M9: return -0.04f; default: return -0.075f; }
    }

    private static final int[] SHIRTS = {0xE8E4DA, 0x3A5F8A, 0x8A2E2E, 0x4E6B3A, 0xB09A6A, 0x6A4A7A, 0xD8C04A, 0x2E2E2E};
    private static final int[] WRAPS = {0x7A2E2E, 0x2E4A7A, 0x6B6B3A, 0x9A7A4A, 0x3A6B5A};
    private void drawUnit(Unit u) {
        Unit p = b.player;
        if (dist(p, u.x, u.z) > 260) return;
        boolean us = u.team == Battle.US;
        int uniform = us ? 0xBCA67C : SHIRTS[u.id % SHIRTS.length];
        int pants = us ? 0xB39C70 : (u.id % 3 == 0 ? WRAPS[u.id % WRAPS.length] : 0x5A5048);
        int skin = us ? (u.id % 4 == 0 ? 0x6B4A35 : 0xC9A086) : (u.id % 2 == 0 ? 0x4A3326 : 0x5E3E2B);
        float dead = u.alive ? 0 : Math.min(1, u.deadT / 0.45f);
        float crouch = u.crouch ? 0.35f : 0;
        if (u.state == Battle.S_VEH && u.vseat != 1) crouch = 0.5f;
        if (u.state == Battle.S_DOWN) crouch = 0.6f;
        base.setToTranslation(u.x, u.y, u.z).rotateRad(Vector3.Y, u.yaw);
        if (dead > 0) base.rotateRad(Vector3.X, -dead * 1.5f * (u.id % 2 == 0 ? 1 : -0.9f));
        if (u.stunT > 0 && u.alive) base.rotateRad(Vector3.Z, MathUtils.sin(uiT * 9 + u.id) * 0.15f);
        float sw = u.alive && u.onGround ? MathUtils.sin(u.walkPhase * 2.2f) * 0.6f * (float) Math.min(1, Math.hypot(u.vx, u.vz) / 4) : 0;
        if (u.state == Battle.S_ROPE_DOWN || u.state == Battle.S_ROPE_UP || u.state == Battle.S_CLIMB) sw = 0.3f * MathUtils.sin(uiT * 6 + u.id);
        float hip = 0.9f - crouch;
        part(0x3A2E22, -0.11f, hip, 0, 0.17f, 0.9f - crouch * 0.4f, 0.2f, sw, true);
        part(0x3A2E22, 0.11f, hip, 0, 0.17f, 0.9f - crouch * 0.4f, 0.2f, -sw, true);
        part(pants, -0.11f, hip, 0, 0.18f, 0.62f - crouch * 0.3f, 0.21f, sw, true);
        part(pants, 0.11f, hip, 0, 0.18f, 0.62f - crouch * 0.3f, 0.21f, -sw, true);
        float ty = hip + 0.31f;
        part(uniform, 0, ty, 0, 0.46f, 0.62f, 0.26f, 0, false);
        if (us) { part(0x5E5844, 0, ty + 0.06f, 0, 0.5f, 0.46f, 0.31f, 0, false); part(0x4E4A38, 0.12f, ty - 0.02f, -0.16f, 0.12f, 0.16f, 0.06f, 0, false); part(0x4E4A38, -0.1f, ty - 0.02f, -0.16f, 0.12f, 0.16f, 0.06f, 0, false); }
        float hy = ty + 0.31f + 0.13f;
        part(skin, 0, hy, 0, 0.23f, 0.25f, 0.24f, 0, false);
        if (us) part(0x8E7D58, 0, hy + 0.11f, 0.01f, 0.31f, 0.16f, 0.33f, 0, false);
        else if (u.id % 3 == 1) part(WRAPS[(u.id / 3) % WRAPS.length], 0, hy + 0.1f, 0, 0.27f, 0.12f, 0.27f, 0, false);
        float aimP = u.alive ? u.pitch : 0;
        m4.set(base).translate(0, ty + 0.2f, 0).rotateRad(Vector3.X, aimP);
        if (u.state == Battle.S_CLIMB) m4.rotateRad(Vector3.X, 1.2f);
        armOrGun(art.tint(uniform), m4, 0.21f, -0.12f, -0.2f, 0.12f, 0.12f, 0.42f);
        armOrGun(art.tint(uniform), m4, -0.15f, -0.12f, -0.28f, 0.12f, 0.12f, 0.42f);
        int wi = u.weapon();
        if (wi == Battle.W_MACHETE) armOrGun(art.tint(0x9A9A9A), m4, 0.2f, 0.05f, -0.45f, 0.03f, 0.08f, 0.6f);
        else if (u.state != Battle.S_VEH || u.vseat != 1) { int gc = wi == Battle.W_AK || wi == Battle.W_M14 ? 0x5A3A22 : wi == Battle.W_GOLD_AK ? 0xD4A62A : 0x1E1E1E; armOrGun(art.tint(gc), m4, 0.06f, -0.08f, -0.42f, 0.06f, 0.1f, 0.82f); }
        if (u.slot == 1 && wi >= 0 && Battle.WEAPONS[wi].rocket) armOrGun(art.tint(0x4F5A3A), m4, 0.08f, 0.08f, -0.2f, 0.13f, 0.13f, 1.0f);
    }
    private void armOrGun(Art.Pool pool, Matrix4 parent, float x, float y, float z, float w, float h, float d) { ModelInstance mi = pool.next(); mi.transform.set(parent).translate(x, y, z).scale(w, h, d); }
    private void part(int rgb, float x, float y, float z, float w, float h, float d, float swing, boolean pivot) {
        ModelInstance mi = art.tint(rgb).next();
        if (pivot) mi.transform.set(base).translate(x, y, z).rotateRad(Vector3.X, swing).translate(0, -h / 2, 0).scale(w, h, d);
        else mi.transform.set(base).translate(x, y, z).scale(w, h, d);
    }
    private void drawSeatedOthers(Heli h) { for (Unit u : b.units) if (u.alive && !u.player && u.state == Battle.S_HELI && u.heli == h.id) drawUnit(u); }
    private void drawHeli(Heli h) {
        boolean down = h.state == Battle.H_DOWN;
        Art.Pool body = h.type == Battle.BLACKHAWK ? (down ? art.blackhawkWreck : art.blackhawk) : (down ? art.littlebirdWreck : art.littlebird);
        ModelInstance mi = body.next();
        float roll = down ? 0.45f : h.roll, pitch = down ? 0.15f : h.pitchA;
        mi.transform.setToTranslation(h.x, h.y, h.z).rotateRad(Vector3.Y, h.yaw).rotateRad(Vector3.X, pitch).rotateRad(Vector3.Z, roll);
        float top = h.type == Battle.BLACKHAWK ? 2.0f : 1.25f;
        if (down) { ModelInstance stub = art.tint(0x1A1A1A).next(); stub.transform.set(mi.transform).translate(1.2f, top, 0.4f).rotateRad(Vector3.Z, -0.5f).scale(3.2f, 0.08f, 0.3f); }
        else {
            ModelInstance r = art.rotor.next();
            r.transform.set(mi.transform).translate(0, top, 0).rotateRad(Vector3.Y, h.rotor);
            if (h.type == Battle.LITTLEBIRD) r.transform.scale(0.6f, 1, 0.6f);
            ModelInstance tr = art.tailRotor.next();
            float tail = h.type == Battle.BLACKHAWK ? 9.1f : 5.3f;
            tr.transform.set(mi.transform).translate(0.3f, 0.9f, tail).rotateRad(Vector3.Z, 1.5708f).rotateRad(Vector3.Y, h.rotor * 3);
        }
        if (h.ropes && h.flying()) {
            float g = b.city.groundAt(h.x, h.z, 1, h.y - 1), len = h.y - g - 1;
            for (int s = -1; s <= 1; s += 2) {
                float c = MathUtils.cos(h.yaw), sn = MathUtils.sin(h.yaw);
                ModelInstance rope = art.tint(0x2A2018).next(); rope.transform.setToTranslation(h.x + c * 1.6f * s, g + len / 2, h.z - sn * 1.6f * s).scale(0.05f, len, 0.05f);
            }
        }
        drawSeatedOthers(h);
    }
    private void drawVehicle(Vehicle v) {
        if (dist(b.player, v.x, v.z) > 260) return;
        Art.Pool body = v.type == Battle.V_CAR ? art.car : v.alive ? art.humvee : art.humveeBurnt;
        ModelInstance mi = body.next();
        mi.transform.setToTranslation(v.x, v.y, v.z).rotateRad(Vector3.Y, v.yaw);
        float hw = v.halfW() - 0.12f, hl = v.halfL() - 0.55f;
        for (int i = 0; i < 4; i++) {
            float wx = (i % 2 == 0 ? -hw : hw), wz = (i < 2 ? -hl : hl);
            ModelInstance wh = art.wheel.next();
            wh.transform.set(mi.transform).translate(wx, 0.45f, wz);
            if (i < 2) wh.transform.rotateRad(Vector3.Y, -v.steer * 0.5f);
            wh.transform.rotateRad(Vector3.X, -v.wheel);
        }
        if (v.type == Battle.V_HUMVEE && v.alive) {
            Unit p = b.player;
            boolean playerGun = p.state == Battle.S_VEH && p.veh == v.id && p.vseat == 1;
            float gy = playerGun ? p.yaw : v.gunYaw, gp = playerGun ? p.pitch : v.gunPitch;
            if (!playerGun) { ModelInstance g = art.fifty.next(); g.transform.setToTranslation(v.x, 2.25f, v.z).rotateRad(Vector3.Y, v.seats[1] >= 0 ? gy : v.yaw).rotateRad(Vector3.X, gp); }
        }
    }

    // ---------------------------------------------------------------- Siege props
    private static final int[] PERK_COLORS = {0xB03030, 0x2E8A3E, 0xD07A20, 0x3050B0, 0xD0B030};
    private void drawSiege(Siege s) {
        City c = b.city;
        for (City.Spot w : c.wallBuys) {
            float tx = w.nz, tz = -w.nx; // along the wall
            float yawGun = (float) Math.atan2(-tx, -tz);
            float ox = w.x + w.nx * 0.05f, oz = w.z + w.nz * 0.05f;
            // chalk frame
            for (int k = 0; k < 4; k++) {
                ModelInstance f = art.tint(0xEDEDE4).next();
                boolean horiz = k < 2; float off = k % 2 == 0 ? 1 : -1;
                float cx = ox + (horiz ? 0 : tx * 0.85f * off), cz = oz + (horiz ? 0 : tz * 0.85f * off), cy = w.y + (horiz ? 0.38f * off : 0);
                float lenA = horiz ? 1.7f : 0.04f, lenY = horiz ? 0.04f : 0.8f;
                f.transform.setToTranslation(cx, cy, cz).rotateRad(Vector3.Y, yawGun).scale(0.02f, lenY, horiz ? lenA : 0.04f);
            }
            art.gun(w.id).next().transform.setToTranslation(ox + w.nx * 0.05f, w.y, oz + w.nz * 0.05f).rotateRad(Vector3.Y, yawGun).scale(1.5f, 1.5f, 1.5f);
        }
        City.Spot bs = c.boxSpots.get(s.box);
        if (s.boxState != 3) art.mysteryBox.next().transform.setToTranslation(bs.x, 0, bs.z);
        else art.mysteryBox.next().transform.setToTranslation(bs.x, (4 - s.boxT) * 8, bs.z);
        if (s.boxState == 1 || s.boxState == 2) art.gun(s.boxShow).next().transform.setToTranslation(bs.x, 1.3f + (s.boxState == 1 ? (3.2f - s.boxT) * 0.2f : 0.6f), bs.z).rotateRad(Vector3.Y, uiT * (s.boxState == 1 ? 6 : 1)).scale(1.4f, 1.4f, 1.4f);
        art.bench.next().transform.setToTranslation(c.pap.x, 0, c.pap.z);
        if (s.papState >= 1) art.gun(s.papWeapon).next().transform.setToTranslation(c.pap.x, 1.15f, c.pap.z).rotateRad(Vector3.Y, uiT * 2).scale(1.4f, 1.4f, 1.4f);
        for (City.Spot ps : c.perkSpots) { ModelInstance sign = art.tint(PERK_COLORS[ps.id]).next(); sign.transform.setToTranslation(ps.x, 2.05f, ps.z).scale(1.8f, 0.35f, 1.3f); }
        for (int i = 0; i < c.radioParts.size(); i++) if (!s.parts[i]) { City.Spot rp = c.radioParts.get(i); art.tint(0x6A6A2A).next().transform.setToTranslation(rp.x, rp.y + 0.12f, rp.z).rotateRad(Vector3.Y, uiT).scale(0.3f, 0.2f, 0.2f); }
        for (float[] pu : s.powerups) { int col = new int[]{0x3A8A3A, 0xD03030, 0xD0B030, 0xD07A20, 0x6A6AC0}[(int) pu[2]]; art.tint(col).next().transform.setToTranslation(pu[0], 1 + MathUtils.sin(uiT * 3) * 0.15f, pu[1]).rotateRad(Vector3.Y, uiT * 2).scale(0.5f, 0.5f, 0.5f); }
        if (s.gunshipT > 0) { float[] pl = gunshipPos(); art.plane.next().transform.setToTranslation(pl[0], pl[1], pl[2]).rotateRad(Vector3.Y, -pl[3]).rotateRad(Vector3.Z, 0.35f); }
        for (Unit u : b.units) if (u.alive && u.state == Battle.S_CLIMB) { /* drawn by drawUnit */ }
    }
    private int siegeGlows(Siege s, int fi) {
        City c = b.city;
        City.Spot bs = c.boxSpots.get(s.box);
        for (int k = 0; k < 5; k++) fi = glow(fi, bs.x, 2 + k * 6, bs.z, 3.5f, 0.6f, 0.75f, 1, 0.12f, softR); // the beacon
        if (s.power) for (float[] l : siegeLamps()) fi = glow(fi, l[0], 3, l[1], 1.2f, 1, 0.85f, 0.5f, 0.8f, softR);
        if (s.papState == 1) fi = glow(fi, c.pap.x, 1.4f, c.pap.z, 2f, 1, 0.5f, 0.2f, 0.7f, softR);
        for (int i = 0; i < c.tags.size(); i++) { City.Spot t = c.tags.get(i); fi = glow(fi, t.x + t.nx * 0.03f, t.y, t.z + t.nz * 0.03f, 0.55f, s.tagsShot[i] ? 0.3f : 0.9f, s.tagsShot[i] ? 0.3f : 0.1f, 0.1f, 0.9f, sparkR); }
        for (int i = 0; i < c.radioParts.size(); i++) if (!s.parts[i] && MathUtils.sin(uiT * 4 + i) > 0.6f) { City.Spot rp = c.radioParts.get(i); fi = glow(fi, rp.x, rp.y + 0.3f, rp.z, 0.4f, 1, 1, 0.8f, 0.9f, sparkR); }
        for (float[] pu : s.powerups) fi = glow(fi, pu[0], 1, pu[1], 1.6f, 0.4f, 1, 0.4f, 0.5f, softR);
        return fi;
    }

    // ================================================================ HUD
    private void drawHud(float dt) {
        Unit p = b.player;
        ui.apply(); float W = ui.getWorldWidth(), H = ui.getWorldHeight(), cx = W / 2, cy = H / 2;
        Gdx.gl.glEnable(GL20.GL_BLEND); Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        sr.setProjectionMatrix(ui.getCamera().combined); sb.setProjectionMatrix(ui.getCamera().combined);
        Weapon w = Battle.WEAPONS[p.weapon()];
        boolean playing = screen == Screen.PLAY && b.result == 0 && p.alive;
        boolean third = p.state == Battle.S_VEH && p.vseat == 0;
        // night vision: tint everything green
        if (nvg && b.night) {
            Gdx.gl.glBlendFunc(GL20.GL_DST_COLOR, GL20.GL_ZERO);
            sr.begin(ShapeRenderer.ShapeType.Filled); sr.setColor(0.35f, 1f, 0.4f, 1); sr.rect(0, 0, W, H); sr.end();
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            sr.begin(ShapeRenderer.ShapeType.Filled);
            for (int i = 0; i < 60; i++) { sr.setColor(0.6f, 1, 0.6f, MathUtils.random(0.03f, 0.08f)); sr.rect(MathUtils.random(W), MathUtils.random(H), MathUtils.random(2, 40), 1); }
            vignette(W, H, 0, 0, 0, 1.4f);
            sr.end();
        }
        sr.begin(ShapeRenderer.ShapeType.Filled);
        float hpF = p.hp / p.maxHp;
        if (hpF < 0.7f && p.alive) vignette(W, H, 0.55f, 0.0f, 0.0f, (0.7f - hpF) * 0.9f);
        if (p.suppress > 0.3f) vignette(W, H, 0.05f, 0.04f, 0.03f, Math.min(1, p.suppress * 0.35f));
        if (p.state == Battle.S_DOWN) { sr.setColor(0.2f, 0.2f, 0.2f, 0.45f); sr.rect(0, 0, W, H); }
        if (playing && !third) {
            if (!p.ads && p.state != Battle.S_ROPE_DOWN) {
                float gap = 8 + (w.hip + p.bloom + (Math.hypot(p.vx, p.vz) > 1 ? 1.2f : 0) + p.suppress * 1.5f) * 6;
                sr.setColor(1, 1, 1, 0.85f);
                sr.rect(cx - gap - 12, cy - 1, 12, 2); sr.rect(cx + gap, cy - 1, 12, 2); sr.rect(cx - 1, cy + gap, 2, 12); sr.rect(cx - 1, cy - gap - 12, 2, 12);
            } else if (p.ads) { sr.setColor(1, 0.2f, 0.15f, 0.9f); sr.circle(cx, cy, 2.2f, 10); }
        }
        if (playing && (hitMark > 0 || killMark > 0)) {
            boolean k = killMark > 0; sr.setColor(1, k ? 0.2f : 1, k ? 0.2f : 1, 0.95f);
            for (int i = 0; i < 4; i++) { float a = 0.785f + i * 1.5708f; sr.rectLine(cx + MathUtils.cos(a) * 9, cy + MathUtils.sin(a) * 9, cx + MathUtils.cos(a) * 19, cy + MathUtils.sin(a) * 19, 2.5f); }
        }
        if (playing) for (float[] h : hurts) {
            float a = h[0] + 1.5708f; float al = Math.min(1, h[1]);
            sr.setColor(0.9f, 0.1f, 0.05f, al * 0.85f);
            for (int i = -3; i <= 3; i++) { float aa = a + i * 0.06f; sr.rectLine(cx + MathUtils.cos(aa) * 110, cy + MathUtils.sin(aa) * 110, cx + MathUtils.cos(aa + 0.06f) * 110, cy + MathUtils.sin(aa + 0.06f) * 110, 7); }
        }
        sr.setColor(0, 0, 0, 0.45f); sr.rect(30, 30, 260, 16);
        sr.setColor(hpF > 0.5f ? 0.85f : 0.9f, hpF > 0.5f ? 0.85f : 0.25f, hpF > 0.5f ? 0.8f : 0.2f, 0.9f); sr.rect(32, 32, 256 * Math.max(0, hpF), 12);
        sr.setColor(0, 0, 0, 0.45f); sr.rect(W - 300, 24, 276, 74);
        if (p.state == Battle.S_HELI || (p.state == Battle.S_VEH && p.vseat == 1)) { sr.setColor(0.2f, 0.2f, 0.2f, 0.8f); sr.rect(W - 290, 34, 256, 10); sr.setColor(1, 0.4f + 0.6f * (1 - p.heat), 0.1f, 0.95f); sr.rect(W - 290, 34, 256 * Math.min(1, p.heat), 10); }
        if (p.state == Battle.S_VEH) { Vehicle v = b.vehicles.get(p.veh); sr.setColor(0, 0, 0, 0.45f); sr.rect(cx - 130, 30, 260, 14); sr.setColor(0.6f, 0.8f, 0.4f, 0.9f); sr.rect(cx - 128, 32, 256 * Math.max(0, v.hp / v.maxHp), 10); }
        float compW = 520; sr.setColor(0, 0, 0, 0.4f); sr.rect(cx - compW / 2, H - 46, compW, 26);
        if (b.objProgress > 0 && b.objProgress < 1) { sr.setColor(0, 0, 0, 0.5f); sr.rect(cx - 150, cy - 90, 300, 12); sr.setColor(0.95f, 0.8f, 0.3f, 0.95f); sr.rect(cx - 148, cy - 88, 296 * b.objProgress, 8); }
        float mm = 210, mx = W - mm - 24, my = H - mm - 24;
        sr.setColor(0, 0, 0, 0.55f); sr.rect(mx - 4, my - 4, mm + 8, mm + 8);
        sr.end();

        sb.begin();
        float span = 160; int cells = (int) (span / City.CELL);
        int srcX = (int) (p.x / City.CELL) - cells / 2, srcY = (int) (p.z / City.CELL) - cells / 2;
        sb.setColor(1, 1, 1, 0.9f); sb.draw(art.minimap, mx, my, mm, mm, srcX, srcY, cells, cells, false, false); sb.setColor(Color.WHITE);
        sb.end();
        sr.begin(ShapeRenderer.ShapeType.Filled);
        float ox = srcX * City.CELL, oz = srcY * City.CELL, sc = mm / (cells * City.CELL);
        for (Unit u : b.units) {
            if (!u.alive || u.player) continue;
            boolean friend = u.team == p.team;
            boolean firing = b.time - u.lastShot < 1.2f;
            if (!friend && !firing) continue;
            float ux = mx + (u.x - ox) * sc, uy = my + mm - (u.z - oz) * sc;
            if (ux < mx || uy < my || ux > mx + mm || uy > my + mm) continue;
            sr.setColor(friend ? 0.3f : 0.95f, friend ? 0.9f : 0.2f, friend ? 0.4f : 0.15f, 1); sr.circle(ux, uy, 3.2f, 8);
        }
        for (Heli h : b.helis) {
            float ux = mx + (h.x - ox) * sc, uy = my + mm - (h.z - oz) * sc;
            if (ux < mx || uy < my || ux > mx + mm || uy > my + mm) continue;
            if (!h.flying()) { sr.setColor(0.1f, 0.1f, 0.1f, 1); sr.rect(ux - 5, uy - 5, 10, 10); sr.setColor(1, 0.5f, 0.1f, 1); sr.rect(ux - 3, uy - 3, 6, 6); }
            else { boolean friend = h.team == p.team; sr.setColor(friend ? 0.4f : 1f, friend ? 0.7f : 0.35f, friend ? 1f : 0.2f, 1); sr.circle(ux, uy, 5, 4); }
        }
        for (float[] m : b.mode.markers()) {
            float ux = mx + (m[0] - ox) * sc, uy = my + mm - (m[1] - oz) * sc;
            ux = MathUtils.clamp(ux, mx + 4, mx + mm - 4); uy = MathUtils.clamp(uy, my + 4, my + mm - 4);
            int k = (int) m[2];
            if (k == 3) { sr.setColor(0.7f, 0.65f, 0.4f, 1); sr.rect(ux - 3, uy - 2, 6, 4); }
            else { sr.setColor(k == 1 ? 1 : k == 4 ? 0.5f : k == 5 ? 1 : k == 6 ? 0.4f : 0.9f, k == 1 ? 0.7f : k == 4 ? 0.7f : k == 5 ? 0.4f : k == 6 ? 1 : 0.9f, k == 1 ? 0.1f : k == 4 ? 1 : k == 5 ? 0.2f : k == 6 ? 0.4f : 0.9f, 1); sr.triangle(ux, uy + 5, ux - 4, uy - 3, ux + 4, uy - 3); }
        }
        { float ux = mx + (b.objX - ox) * sc, uy = my + mm - (b.objZ - oz) * sc; ux = MathUtils.clamp(ux, mx + 4, mx + mm - 4); uy = MathUtils.clamp(uy, my + 4, my + mm - 4); sr.setColor(1, 0.85f, 0.2f, 1); sr.rect(ux - 4, uy - 4, 8, 8); }
        { float ux = mx + (p.x - ox) * sc, uy = my + mm - (p.z - oz) * sc, a = -b.input.yaw;
            sr.setColor(1, 1, 1, 1); float fx = MathUtils.sin(a), fy = MathUtils.cos(a);
            sr.triangle(ux + fx * 8, uy + fy * 8, ux - fy * 5 - fx * 5, uy + fx * 5 - fy * 5, ux + fy * 5 - fx * 5, uy - fx * 5 - fy * 5); }
        float bearing = (float) Math.toDegrees(-b.input.yaw);
        for (int d = 0; d < 360; d += 15) { float rel = wrapDeg(d - bearing); if (Math.abs(rel) > 90) continue; float x = cx + rel / 90 * compW / 2; sr.setColor(1, 1, 1, 0.7f); sr.rect(x - 1, H - 46, 2, d % 45 == 0 ? 10 : 5); }
        float objB = (float) Math.toDegrees(Math.atan2(b.objX - p.x, -(b.objZ - p.z)));
        float relO = MathUtils.clamp(wrapDeg(objB - bearing), -90, 90); sr.setColor(1, 0.85f, 0.2f, 1);
        float ox2 = cx + relO / 90 * compW / 2; sr.triangle(ox2, H - 22, ox2 - 7, H - 34, ox2 + 7, H - 34);
        sr.end();
        // flashbang whiteout
        if (p.flashT > 0) { sr.begin(ShapeRenderer.ShapeType.Filled); sr.setColor(1, 1, 1, Math.min(1, p.flashT)); sr.rect(0, 0, W, H); sr.end(); }

        sb.begin();
        for (int d = 0; d < 360; d += 45) { float rel = wrapDeg(d - bearing); if (Math.abs(rel) > 88) continue; text(f18, new String[]{"N", "NE", "E", "SE", "S", "SW", "W", "NW"}[d / 45], cx + rel / 90 * compW / 2, H - 50, Color.WHITE, 0); }
        int dObj = (int) Math.hypot(b.objX - p.x, b.objZ - p.z);
        text(f18, dObj + " m", ox2, H - 4, new Color(1, 0.85f, 0.2f, 1), 0);
        text(f18, b.mode.title().toUpperCase() + (b.wave > 0 && !(b.mode instanceof Siege) ? "   WAVE " + b.wave : "") + (b.night ? (nvg ? "   NVG" : "   NIGHT (N for NVG)") : ""), 30, H - 24, new Color(1, 0.85f, 0.2f, 1), -1);
        wrapText(f18, b.objective, 30, H - 50, 440, Color.WHITE);
        String mh = b.mode.hud();
        if (mh != null) text(f18, mh, 30, H - 110, new Color(0.95f, 0.9f, 0.6f, 1), -1);
        if (b.countdown > 0) text(f26, String.format("Hold: %d:%02d", (int) b.countdown / 60, (int) b.countdown % 60), 30, H - 140, new Color(1, 0.9f, 0.6f, 1), -1);
        for (int i = 0; i < feed.size(); i++) { float a = Math.min(1, feedT.get(i)); String[] f = feed.get(i); text(f18, (f[0].equals("RADIO") ? "[radio] " : f[0].equals("!") ? "[!] " : "") + f[1], 30, H * 0.62f - i * 24, new Color(0.85f, 0.95f, 0.85f, a), -1); }
        String ammo;
        int s = p.slot; if (p.wep[s] < 0) s = 1 - s;
        if (p.state == Battle.S_HELI || (p.state == Battle.S_VEH && p.vseat == 1)) ammo = p.heat >= 1 ? "OVERHEATED" : "mounted gun";
        else if (third) ammo = String.format("%d km/h", (int) Math.abs(b.vehicles.get(p.veh).speed * 3.6f));
        else ammo = p.reloadT > 0 ? "reloading..." : p.mag[s] + " / " + p.reserve[s];
        text(f26, ammo, W - 40, 88, p.mag[s] == 0 && p.state == Battle.S_FOOT ? new Color(1, 0.3f, 0.2f, 1) : Color.WHITE, 1);
        String wname = third ? "Humvee" : (p.papd[s] && p.state == Battle.S_FOOT ? Battle.papName(p.weapon()) : w.name);
        text(f18, wname + "   frag " + p.grenades + "  flash " + p.flashes + "  charge " + p.charges, W - 40, 52, p.papd[s] ? new Color(0.7f, 0.5f, 1, 1) : new Color(0.9f, 0.9f, 0.85f, 1), 1);
        text(f18, Battle.KITS[p.kit].name + "   " + (int) Math.max(0, p.hp) + " HP", 30, 70, Color.WHITE, -1);
        if (b.mode instanceof Siege) { Siege sg = (Siege) b.mode; text(f110, String.valueOf(Math.max(1, sg.round)), 30, 230, new Color(0.75f, 0.1f, 0.08f, 1), -1); text(f26, "$ " + sg.points, 30, 108, new Color(1, 0.9f, 0.5f, 1), -1); }
        else text(f18, "Kills " + p.kills + (b.helisDowned > 0 ? "   Helicopters " + b.helisDowned : ""), 30, 100, new Color(0.9f, 0.9f, 0.85f, 1), -1);
        String prompt = prompt(p);
        if (prompt != null && playing) text(f26, prompt, cx, cy - 120, new Color(1, 1, 1, 0.55f + 0.45f * Math.abs(MathUtils.sin(uiT * 3))), 0);
        if (p.state == Battle.S_DOWN) text(f46, "DOWN", cx, cy + 60, new Color(1, 0.3f, 0.2f, 1), 0);
        for (int i = 0; i < floatText.size(); i++) { float[] f = floatText.get(i); text(f18, floatStr.get(i), cx + 60, cy + 30 + (1.1f - f[1]) * 40 + i * 18, new Color(1, 0.9f, 0.5f, Math.min(1, f[1] * 2)), -1); }
        if (bannerT > 0) { float a = Math.min(1, bannerT); text(banner.length() > 14 ? f46 : f110, banner, cx, cy + 190, new Color(1, 0.9f, 0.6f, a), 0); }
        if (save.showFps) text(f18, Gdx.graphics.getFramesPerSecond() + " fps", W - 30, 20, Color.WHITE, 1);
        sb.end();
    }
    private String prompt(Unit p) {
        if (p.state == Battle.S_HELI) { Heli h = b.helis.get(p.heli); return h.state == Battle.H_HOVER ? "E  Fast-rope" : h.state == Battle.H_FOLLOW ? "LMB  door gun" : "E  Put us down here     LMB  door gun"; }
        if (p.state == Battle.S_VEH) return p.vseat == 0 ? "WASD drive   Space brake   X take the .50   E get out" : p.vseat == 1 ? "LMB  .50 cal   X drive   E get out" : "E  get out";
        String m = b.mode.prompt(p); if (m != null) return m;
        for (Vehicle v : b.vehicles) if (v.usable() && Battle.inVehicle(v, p.x, p.z, 1.6f)) return v.type == Battle.V_HUMVEE ? "E  Get in the Humvee" : null;
        for (Heli h : b.helis) if (h.state == Battle.H_HOVER && h.purpose == Battle.P_PICKUP && h.team == p.team && Math.hypot(h.x - p.x, h.z - p.z) < 7) return "E  Climb the rope";
        if (b.doorNear(p) >= 0) return p.charges > 0 ? "Tap E  kick the door      Hold E  breaching charge (" + p.charges + ")" : "E  Kick the door";
        for (int i = 0; i < b.city.crates.size(); i++) { float[] c = b.city.crates.get(i); if (Math.hypot(c[0] - p.x, c[1] - p.z) < 2.6f) return b.crateCd[i] > 0 ? "Crate empty (" + (int) b.crateCd[i] + "s)" : "E  Ammo crate"; }
        if (Math.hypot(b.objX - p.x, b.objZ - p.z) < 4 && b.objective.contains("hold E")) return "Hold E";
        if (b.objective.contains("(H)")) return "H  Call extraction";
        return null;
    }
    private void vignette(float W, float H, float r, float g, float bl, float a) {
        for (int i = 0; i < 6; i++) { float t = 60 - i * 10, al = a * (i + 1) / 6f; sr.setColor(r, g, bl, Math.min(1, al * 0.35f)); sr.rect(0, 0, W, t); sr.rect(0, H - t, W, t); sr.rect(0, 0, t, H); sr.rect(W - t, 0, t, H); }
    }
    private static float wrapDeg(float a) { while (a > 180) a -= 360; while (a < -180) a += 360; return a; }
    private void text(BitmapFont f, String s, float x, float y, Color c, int align) { gl.setText(f, s); f.setColor(c); f.draw(sb, s, align == 0 ? x - gl.width / 2 : align > 0 ? x - gl.width : x, y); }
    private void wrapText(BitmapFont f, String s, float x, float y, float w, Color c) { f.setColor(c); f.draw(sb, s, x, y, w, -1, true); }

    // ================================================================ menus (immediate mode)
    private final ArrayList<float[]> rects = new ArrayList<>(); private final ArrayList<Object[]> labels = new ArrayList<>();
    private boolean button(String label, float x, float y, float w, float h, boolean selected) {
        boolean hover = mouse.x >= x && mouse.x <= x + w && mouse.y >= y && mouse.y <= y + h;
        rects.add(new float[]{x, y, w, h, selected ? 2 : hover ? 1 : 0});
        labels.add(new Object[]{label, x + w / 2, y + h / 2 + 9, f26, 0});
        if (hover && clicked) { sfx.play("click", 0.5f); clicked = false; return true; }
        return false;
    }
    private void label(BitmapFont f, String s, float x, float y, int align) { labels.add(new Object[]{s, x, y, f, align}); }
    private void flushUi(float dim) {
        ui.apply(); float W = ui.getWorldWidth(), H = ui.getWorldHeight();
        Gdx.gl.glEnable(GL20.GL_BLEND); Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        sr.setProjectionMatrix(ui.getCamera().combined); sb.setProjectionMatrix(ui.getCamera().combined);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        if (dim > 0) { sr.setColor(0, 0, 0, dim); sr.rect(0, 0, W, H); }
        for (float[] r : rects) { sr.setColor(r[4] == 2 ? new Color(0.72f, 0.55f, 0.2f, 0.95f) : r[4] == 1 ? new Color(0.35f, 0.3f, 0.22f, 0.95f) : new Color(0.16f, 0.14f, 0.11f, 0.9f)); sr.rect(r[0], r[1], r[2], r[3]); }
        sr.end();
        sb.begin();
        for (Object[] l : labels) {
            BitmapFont f = (BitmapFont) l[3]; String s = (String) l[0]; int al = (Integer) l[4];
            if (s.length() > 70 && f == f18) { f.setColor(0.85f, 0.82f, 0.75f, 1); float wdt = al == 0 ? 900 : 600; f.draw(sb, s, (Float) l[1] - (al == 0 ? wdt / 2 : 0), (Float) l[2], wdt, al == 0 ? Align.center : Align.left, true); }
            else text(f, s, (Float) l[1], (Float) l[2], new Color(0.95f, 0.92f, 0.85f, 1), al);
        }
        sb.end();
        rects.clear(); labels.clear();
    }
    private String nightLabel() { return "Night: " + (save.nightPref == 0 ? "chapter default" : save.nightPref == 1 ? "always" : "never"); }
    private void drawMenus() {
        float W = ui.getWorldWidth(), H = ui.getWorldHeight(), cx = W / 2;
        switch (screen) {
            case TITLE: {
                label(f110, "IRENE", cx, H - 90, 0);
                label(f26, "Mogadishu, 1993", cx, H - 250, 0);
                if (button("Story", cx - 160, H / 2 + 50, 320, 56, false)) { pickMode = MODE_STORY; screen = Screen.CHAPTERS; }
                if (button("Free Roam", cx - 160, H / 2 - 16, 320, 56, false)) { pickMode = MODE_ROAM; screen = Screen.KIT; fixKit(); }
                if (button("Siege", cx - 160, H / 2 - 82, 320, 56, false)) { pickMode = MODE_SIEGE; screen = Screen.KIT; }
                if (button("Settings", cx - 160, H / 2 - 148, 320, 56, false)) { settingsBack = Screen.TITLE; screen = Screen.SETTINGS; }
                if (button("Quit", cx - 160, H / 2 - 214, 320, 56, false)) Gdx.app.exit();
                label(f18, "Fictional characters. The places, units and events follow the historical record; see the README for sources.", cx, 40, 0);
                flushUi(0);
                break;
            }
            case CHAPTERS: {
                label(f46, "Story", cx, H - 50, 0);
                for (int i = 0; i < Story.NAMES.length; i++) if (button((i + 1) + ". " + Story.NAMES[i] + (save.done(i) ? "  *" : ""), 50, H - 140 - i * 58, 440, 50, pickChapter == i)) pickChapter = i;
                label(f26, Story.NAMES[pickChapter], 540, H - 130, -1);
                label(f18, Story.DATES[pickChapter] + (Story.NIGHT[pickChapter] ? "   (night)" : "") + (Story.SNA[pickChapter] ? "   (you play the militia)" : ""), 540, H - 165, -1);
                label(f18, Story.BLURB[pickChapter] + "                                              ", 540, H - 200, -1);
                if (button(nightLabel(), 540, 230, 380, 46, false)) { save.nightPref = (save.nightPref + 1) % 3; save.flush(); }
                String[] dn = {"Recruit", "Regular", "Veteran"};
                for (int d = 0; d < 3; d++) if (button(dn[d], 540 + d * 170, 170, 160, 46, save.difficulty == d)) { save.difficulty = d; save.flush(); }
                int cp = save.checkpoint(pickChapter);
                if (cp > 0 && button("Continue (checkpoint " + cp + ")", W - 400, 100, 360, 52, false)) { pickStage = cp; screen = Screen.KIT; fixKit(); }
                if (button("Start chapter", W - 400, 36, 360, 56, false)) { pickStage = 0; screen = Screen.KIT; fixKit(); }
                if (button("Back", 50, 30, 200, 50, false)) screen = Screen.TITLE;
                flushUi(0);
                break;
            }
            case KIT: {
                boolean siege = pickMode == MODE_SIEGE;
                label(f46, siege ? "Siege" : "Choose your operator", cx, H - 60, 0);
                if (siege) {
                    label(f18, "Round-based survival in a walled compound. You start with an M9 and 500 points. Buy guns off the walls, open gates into new areas, start the generator for perks and the Armorer's bench (pack-a-punch), and spin the weapons crate. Rebuild window boards between kills. There are secrets.", 120, H - 140, -1);
                    label(f18, "Best round so far: " + save.best("siege"), 120, H - 260, -1);
                } else {
                    int row = 0;
                    boolean sna = pickMode == MODE_STORY && Story.SNA[pickChapter];
                    for (int i = 0; i < Battle.KIT_MACHETE; i++) {
                        Kit k = Battle.KITS[i];
                        if ((k.team == Battle.SNA) != sna) continue;
                        if (button(k.name, 60, H - 160 - row * 76, 420, 62, pickKit == i)) pickKit = i;
                        row++;
                    }
                    Kit k = Battle.KITS[pickKit];
                    label(f26, k.name, 540, H - 150, -1);
                    label(f18, k.blurb, 540, H - 190, -1);
                    label(f18, "Primary: " + Battle.WEAPONS[k.primary].name + "     Secondary: " + Battle.WEAPONS[k.secondary].name, 540, H - 250, -1);
                    label(f18, "Health " + (int) k.hp + "   Speed " + (int) (k.speed * 100) + "%" + (k.armor ? "   Body armour" : ""), 540, H - 280, -1);
                }
                if (pickMode != MODE_STORY) { if (button(nightLabel(), 540, 230, 380, 46, false)) { save.nightPref = (save.nightPref + 1) % 3; save.flush(); } String[] dn = {"Recruit", "Regular", "Veteran"}; for (int d = 0; d < 3; d++) if (button(dn[d], 540 + d * 170, 170, 160, 46, save.difficulty == d)) { save.difficulty = d; save.flush(); } }
                label(f18, "WASD move  Shift sprint  Space jump/vault  C slide  LMB fire  RMB aim  R reload  Q swap  G frag  T flashbang  V melee  E use/breach/vehicle  X switch seat  H extraction  N night vision  Esc pause", cx, 120, 0);
                if (button("Back", 60, 40, 200, 50, false)) screen = pickMode == MODE_STORY ? Screen.CHAPTERS : Screen.TITLE;
                if (button("Deploy", W - 300, 40, 260, 56, false)) startGame();
                flushUi(0);
                break;
            }
            case SETTINGS: {
                label(f46, "Settings", cx, H - 60, 0);
                float y = H - 160;
                y = slider("Mouse sensitivity", save.sens, 0.2f, 3f, y, v -> save.sens = v);
                y = slider("Field of view", save.fov, 60, 100, y, v -> save.fov = v);
                y = slider("Sound volume", save.sfx, 0, 1, y, v -> { save.sfx = v; sfx.volume = v; });
                y = slider("Music volume", save.music, 0, 1, y, v -> { save.music = v; sfx.musicVolume = v; });
                if (button("Fullscreen: " + (save.fullscreen ? "on" : "off"), cx - 220, y, 440, 50, false)) { save.fullscreen = !save.fullscreen; applyFullscreen(); } y -= 62;
                if (button("Invert mouse Y: " + (save.invertY ? "on" : "off"), cx - 220, y, 440, 50, false)) save.invertY = !save.invertY; y -= 62;
                if (button("Blood: " + (save.blood ? "on" : "off"), cx - 220, y, 440, 50, false)) save.blood = !save.blood; y -= 62;
                if (button("Show FPS: " + (save.showFps ? "on" : "off"), cx - 220, y, 440, 50, false)) save.showFps = !save.showFps;
                if (button("Back", 60, 40, 200, 50, false)) { save.flush(); screen = settingsBack; }
                flushUi(settingsBack == Screen.PAUSE ? 0.7f : 0);
                break;
            }
            case PAUSE: {
                label(f110, "PAUSED", cx, H - 90, 0);
                float y = H / 2 + 60;
                if (button("Resume", cx - 180, y, 360, 52, false)) { screen = Screen.PLAY; catchMouse(true); } y -= 62;
                if (button("Night: " + (b.night ? "on" : "off") + " (switch now)", cx - 180, y, 360, 52, false)) { b.night = !b.night; } y -= 62;
                if (b.mode instanceof Story) { Story st = (Story) b.mode; if (button("Restart from checkpoint", cx - 180, y, 360, 52, false)) { pickStage = st.cp; startGame(); } y -= 62; }
                else { if (button("Restart", cx - 180, y, 360, 52, false)) startGame(); y -= 62; }
                if (b.mode instanceof FreeRoam) { if (button("End free roam", cx - 180, y, 360, 52, false)) { b.win("Free roam session over."); resultT = 99; } y -= 62; }
                if (button("Settings", cx - 180, y, 360, 52, false)) { settingsBack = Screen.PAUSE; screen = Screen.SETTINGS; } y -= 62;
                if (button("Quit to menu", cx - 180, y, 360, 52, false)) { screen = Screen.TITLE; sfx.stopLoops(); }
                flushUi(0.6f);
                break;
            }
            case RESULT: {
                boolean won = b.result == 1;
                label(won ? f46 : f110, won ? (b.mode instanceof Story ? "CHAPTER COMPLETE" : "SESSION OVER") : b.mode instanceof Siege ? "OVERRUN" : "KIA", cx, H - 110, 0);
                label(f26, b.resultText, cx, H - 200, 0);
                int acc2 = b.shots > 0 ? 100 * b.hitsLanded / b.shots : 0;
                label(f26, "Kills " + b.player.kills + "     Headshots " + b.headshots + "     Accuracy " + acc2 + "%     Time " + (int) b.time / 60 + ":" + String.format("%02d", (int) b.time % 60), cx, H - 270, 0);
                if (b.mode instanceof Siege) label(f26, "Rounds survived: " + Math.max(0, ((Siege) b.mode).round - 1) + "    Best: " + save.best("siege"), cx, H - 310, 0);
                if (b.mode instanceof FreeRoam) { FreeRoam fr = (FreeRoam) b.mode; label(f26, "Score " + fr.score + "   Jobs done " + fr.completed + "   Deaths " + fr.deaths, cx, H - 310, 0); }
                if (b.mode instanceof Story) {
                    Story st = (Story) b.mode;
                    if (won) { if (st.ch + 1 < Story.NAMES.length && button("Next chapter", cx - 330, 120, 240, 56, false)) { pickChapter = st.ch + 1; pickStage = 0; screen = Screen.KIT; fixKit(); } }
                    else {
                        if (st.cp > 0 && button("Retry from checkpoint", cx - 420, 120, 300, 56, false)) { pickStage = st.cp; startGame(); }
                        if (button("Restart chapter", cx - 100, 120, 220, 56, false)) { pickStage = 0; startGame(); }
                    }
                    if (button("Chapters", cx + 140, 120, 200, 56, false)) screen = Screen.CHAPTERS;
                } else {
                    if (button("Again", cx - 230, 120, 200, 56, false)) startGame();
                    if (button("Title", cx + 30, 120, 200, 56, false)) screen = Screen.TITLE;
                }
                flushUi(0.55f);
                break;
            }
            default: break;
        }
    }
    private void fixKit() {
        boolean sna = pickMode == MODE_STORY && Story.SNA[pickChapter];
        if ((Battle.KITS[pickKit].team == Battle.SNA) != sna || pickKit >= Battle.KIT_MACHETE) pickKit = sna ? 4 : 0;
    }
    interface FloatSet { void set(float v); }
    private float slider(String name, float v, float lo, float hi, float y, FloatSet set) {
        float W = ui.getWorldWidth(), cx = W / 2, x0 = cx - 220, w = 440;
        label(f18, name + ": " + (hi > 10 ? String.valueOf((int) v) : String.format("%.2f", v)), cx, y + 72, 0);
        rects.add(new float[]{x0, y + 20, w, 10, 0});
        float f = (v - lo) / (hi - lo);
        rects.add(new float[]{x0 + f * w - 8, y + 12, 16, 26, 2});
        if (Gdx.input.isButtonPressed(Buttons.LEFT) && mouse.y > y + 5 && mouse.y < y + 45 && mouse.x > x0 - 10 && mouse.x < x0 + w + 10) set.set(lo + MathUtils.clamp((mouse.x - x0) / w, 0, 1) * (hi - lo));
        return y - 80;
    }

    @Override public void dispose() { save.flush(); sfx.dispose(); if (art != null) art.dispose(); batch.dispose(); sb.dispose(); sr.dispose(); decals.dispose(); white.dispose(); soft.dispose(); spark.dispose(); }
}
