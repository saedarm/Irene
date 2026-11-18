# Irene: replacing the greybox with real models

Everything in the game is built from boxes in `Art.java`. This guide replaces it one piece at a time with Blender models, without breaking the game in between. Do the steps in order. Each one is playable when it's done.

Tools, all free:
- **Blender 4.x** (blender.org)
- **gdx-gltf** (github.com/mgsx-dev/gdx-gltf): loads glTF files into libGDX and adds a PBR shader with shadows. Same `ModelInstance` API as now.
- **Mixamo** (mixamo.com, free with an Adobe account): auto-rigs a character and gives you run, crouch, aim, fire and death animations. Rigify, built into Blender, is the no-account alternative.
- **CC0 textures** from ambientCG.com and polyhaven.com (plaster, concrete, corrugated tin, sand, rust). CC0 means no credit required and no licence questions.

Budgets so it runs well on a normal Windows PC (the city has a lot of buildings and up to about 50 people on screen):

| Thing | Triangles | Texture |
| --- | --- | --- |
| Soldier / militiaman | 3,000-6,000 | one 1024 atlas |
| First-person arms + gun | 6,000-10,000 | 2048 |
| Black Hawk | 8,000-15,000 | 2048 |
| Little Bird | 4,000-8,000 | 1024 |
| Building kit piece | 50-400 | shared 2048 atlas for the whole kit |
| Props (crate, tires, pole) | 50-500 | shared atlas |

---

## Step 1. Add gdx-gltf and load one test model

1. In `build.gradle`, add Jitpack to the repositories:
   ```groovy
   repositories { mavenCentral(); maven { url 'https://jitpack.io' } }
   ```
2. In `core/build.gradle`:
   ```groovy
   api "com.github.mgsx-dev.gdx-gltf:gltf:2.2.1"
   ```
   (check the releases page for the newest version).
3. Make a `models/` folder under `assets/`.
4. In Blender, export the default cube: **File → Export → glTF 2.0**. Use these export settings and keep them for every model:
   - Format: **glTF Binary (.glb)**
   - Include: **Selected Objects** only
   - Transform: **+Y Up** on
   - Mesh: **Apply Modifiers** on, UVs on, Normals on, **Tangents** on (needed for normal maps)
   - Animation: on for characters and rotors, off for buildings
5. Load it in `Art.java`:
   ```java
   SceneAsset cube = new GLBLoader().load(Gdx.files.internal("models/cube.glb"));
   ModelInstance mi = new ModelInstance(cube.scene.model);
   ```
6. To get the PBR look and shadows, switch `Irene.java` from `ModelBatch` + `Environment` to gdx-gltf's `SceneManager`:
   ```java
   sceneManager = new SceneManager();
   sceneManager.setCamera(cam);
   DirectionalShadowLight sun = new DirectionalShadowLight(4096, 4096, 120, 120, 1, 400);
   sun.direction.set(-0.55f, -0.62f, 0.35f).nor(); sun.color.set(1f, 0.9f, 0.75f, 1);
   sceneManager.environment.add(sun);
   sceneManager.setAmbientLight(0.5f);
   ```
   Add each frame's instances with `sceneManager.getRenderableProviders().add(...)`, then call `sceneManager.update(dt)` and `sceneManager.render()`. Move the shadow box with the player every frame (`sun.setCenter(player position)`), because one shadow map can't cover 640 m.

**Units:** 1 Blender unit = 1 metre = 1 game unit. A person is 1.8 tall. In Blender, the model's forward is **-Y**. After export with +Y Up that becomes **-Z**, which is forward in the game. The origin goes at the feet, centred.

---

## Step 2. Props first (practice the pipeline)

Start with things that don't move: ammo crate, tire barricade, telephone pole, rubble piles, a burned-out car. Low risk and quick wins.

1. Model with simple shapes. Bevel the edges a little (Ctrl+B, 2 segments) so edges catch light.
2. **UV unwrap:** select all → U → Smart UV Project. Then pack every prop onto one shared atlas (UV → Pack Islands) so they all use one texture.
3. **Texture:** in the Shading tab, plug an ambientCG texture set (Color, Normal, Roughness) into the Principled BSDF. gdx-gltf reads base colour, normal, metallic/roughness and emission straight from the glTF.
4. Export to `assets/models/props.glb`, with each prop as its own named object (`crate`, `tires`, `pole`).
5. In `Art.java`, replace `crateModel()` with the node from the file:
   ```java
   Model props = new GLBLoader().load(Gdx.files.internal("models/props.glb")).scene.model;
   crate = new Pool(props); // ModelInstance(props, "crate") picks one node
   ```

---

## Step 3. Characters: a Ranger and a militiaman

This is the biggest visual jump. You need 2 base bodies and some swappable parts.

### Model
1. Start from a base mesh instead of from scratch. MakeHuman (free, CC0 output) or Blender's Human Base Meshes asset pack both work. Keep it under 6,000 triangles.
2. **Ranger:** desert camo uniform (my research didn't pin down the pattern; check photos from Somalia before you texture it), PASGT helmet with a tan cover, a chest rig or vest with magazine pouches, gloves, boots. Make the helmet, vest and gun separate objects so the kits can swap them.
3. **Militiaman:** short-sleeve shirt or T-shirt, a macawis (wrap skirt) or trousers, sandals, an optional head wrap. Make 3-4 shirt colours in the texture, or tint in code like the greybox does now. Variety matters more than detail. A crowd of identical men looks wrong fast.
4. Use fictional faces and invented unit patches. Don't model real soldiers.

### Rig and animate
1. Export the body as FBX and upload it to Mixamo. Place the markers (chin, wrists, elbows, knees, groin). Mixamo returns a rigged model.
2. Download these animations **with the same skeleton**, "In Place" ticked where offered:
   - idle (rifle), walk forward, run forward, strafe left and right, crouch idle, crouch walk
   - rifle aim idle, firing rifle, reload
   - throw grenade, RPG fire (shoulder-launch)
   - 3-4 different deaths (forward, backward, spin, head shot)
   - hit reaction
   - fast-rope / climbing (use a ladder-climb clip slowed down)
3. Import them all into one Blender file. Put each one in the NLA editor as its own action, named simply (`idle`, `run`, `aim`, `fire`, `death1`...). Export once with **Animation → NLA Strips** on.
4. In the game, swap the boxes in `drawUnit()` for an animated instance per unit:
   ```java
   AnimationController ac = new AnimationController(instance);
   ac.animate(moving ? "run" : u.crouch ? "crouch_idle" : "aim", -1, 1f, null, 0.2f); // loop, 0.2 s blend
   ```
   Pick the clip from the unit's state each frame: dead → a death clip (play once, hold the last frame), roping → climb, moving fast → run, firing → fire. Keep one `AnimationController` per unit in a map keyed by unit id.
5. **Aim pitch:** rotate the spine bone by the unit's pitch after the animation applies (`instance.getNode("mixamorig:Spine1").rotation`), so they aim up at rooftops and helicopters.
6. **Attach the gun** to the right hand bone (`mixamorig:RightHand`) by copying its global transform into the gun instance every frame.

### First-person arms
1. Separate file: just the forearms and gloves plus the gun, posed in front of a camera at the origin looking down -Y.
2. Animate in Blender directly: idle sway, fire kick, reload (mag out, mag in, charging handle), sprint (gun tilted down), ADS (sight lined up on the camera's centre).
3. Replace `art.guns[]` and `art.hands` with these, and drive `AnimationController` from the weapon state in `drawViewModel()`. Keep the current procedural bob and recoil on top. It still feels good.

---

## Step 4. Modular building kit (with interiors)

The city generator already knows every building's footprint, height and storey count, so it can place kit pieces instead of boxes.

1. Model the kit on a **4 m grid** (the map's cell size), storeys **3.2 m** tall:
   - wall straight, wall with window, wall with door, wall broken (hole, rebar), corner piece
   - floor/ceiling tile, roof tile with parapet, interior stair (fits in one 4 x 4 cell)
   - courtyard wall 2.8 m, gate, gate with metal doors
   - tin shack (3 variants), market stall with awning, telephone pole, Olympic Hotel facade pieces, a mosque minaret
2. Texture everything from one 2048 atlas: whitewashed plaster, ochre plaster, painted plaster with the lower half worn to bare concrete, stone, corrugated tin, rust. Add a dirt gradient at the bottom of walls. Bullet pocks and scorch marks are cheapest as decals (the game already draws bullet holes and blood as decals).
3. Export each piece as a named node in one `kit.glb`.
4. In `City.java`, keep the boxes for physics and bullets: they stay the source of truth. In `Art.java`, replace each `K_HOUSE` box with kit pieces: walls around the footprint, a floor per storey, a roof, and one stair cell. Batch by building so you still draw chunks, not 50,000 objects.
5. **Interiors:** to make buildings enterable, generate the colliders from the same layout: wall boxes 0.25 m thick, floors as thin boxes, door cells left open, stair cells as step boxes (the movement code already handles 0.45 m steps). The AI nav grid needs a second layer per floor. Start with ground floors only.

---

## Step 5. Helicopters

### MH-60L Black Hawk
1. Use a side view and a top view of a UH-60 as background images (Add → Image → Reference). Size it to the real thing: fuselage about 15.3 m without rotors, main rotor 16.4 m across, tail rotor 3.35 m.
2. Box-model the cabin, then the nose, tail boom and stabilator. Make the cabin doors open (the game rides with them open), with a minigun on a pintle mount in each door. 160th SOAR birds were dark, nearly black.
3. **Separate objects, named exactly:** `body`, `main_rotor`, `tail_rotor`, `door_gun_l`, `door_gun_r`. Put the rotor's origin on the mast so it spins in place. The game spins `main_rotor` around Y and `tail_rotor` around X in code, so no animation is needed.
4. Also make a **wreck** version: body crushed on one side, tail boom broken off at an angle, rotor blades bent and snapped, scorch textures. Export it as `blackhawk_wreck`.
5. Add an empty object named `rope_l` and `rope_r` at each door, where the fast-ropes hang from. Read their positions in code instead of the current guess.

### MH-6 / AH-6 Little Bird
Egg-shaped body, a thin tail boom, skids, and outboard benches on the MH-6 (Delta rode 4 per bird, outside). The AH-6 swaps the benches for minigun and rocket pods. Same naming as above.

### Rotor blur
At full speed, swap the blades for a flat disc with a transparent radial blur texture. Two meshes in the file (`main_rotor` and `main_rotor_blur`); show the blur one when the rotor speed is high.

---

## Step 6. Weapons (third person)

One file, one named node each: `car15`, `car15_aimpoint`, `m249`, `m14`, `m60`, `ak47`, `pkm`, `rpg7`, `rpg_rocket`, `m72`, `m67`. 500-1,500 triangles each. Put the origin at the pistol grip so attaching to the hand bone just works. Use real proportions. Gun reference photos are everywhere, and none of this needs to be exact to the millimetre.

---

## Step 7. Blood and effects textures

The game already sprays particles and leaves splats. Better textures are the cheap win here.

1. In Blender or any paint program, paint a 1024 sheet with 4 x 4 cells: blood splats (8 varieties), blood drips on walls (4), bullet holes in plaster (2) and tin (2).
2. Load it as a `TextureAtlas` and pick a random cell in `splat()` and `impact()` instead of the soft circle.
3. Smoke: a 4 x 4 sheet of soft smoke puffs. Fire: a 4 x 4 flipbook. Muzzle flash: 4 star shapes. Pick by particle kind.
4. Optional: a screen-space blood overlay at the edges when you're hurt, replacing the red bars in `vignette()`.

---

## Step 8. Vehicles (for the convoy missions)

| Vehicle | Notes |
| --- | --- |
| HMMWV | Some armoured, many with canvas doors; a gun ring on the roof (M2 .50 or Mk 19) |
| M939 5-ton truck | Open cargo bed with sandbags on the floor, as the convoy used |
| Technical | Toyota-style pickup with a DShK or ZU-23 in the back |
| Malaysian Condor APC | 4-wheeled, boxy, white UN paint |
| Pakistani M48 tank | Big turret, long barrel |

Make the wheels separate objects (`wheel_fl`, `wheel_fr`, `wheel_rl`, `wheel_rr`) so code can spin and steer them. For driving, gdx-bullet's `btRaycastVehicle` is the standard libGDX route, or a simple arcade model on the existing collision boxes.

---

## Checklist per model before export

- Scale applied (Ctrl+A → All Transforms), 1 unit = 1 m
- Origin at the feet / base / grip / mast as described above
- Faces -Y in Blender
- Normals facing out (Overlays → Face Orientation: everything blue)
- One material per model where possible, textures packed in the .glb
- Under the triangle budget
- Named nodes match what the code asks for
