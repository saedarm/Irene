# Irene

A first-person shooter set in Mogadishu in 1993, during Operation Gothic Serpent (the Battle of Mogadishu). "Irene" was the codeword that launched the raid on 3 October. It has three modes:
- **Story:** nine chapters that follow the real sequence of events, with checkpoints.
- **Free Roam:** the whole city with jobs, Humvees and a helicopter on call.
- **Siege:** a Call of Duty Zombies-style round survival mode with points, wall guns, a mystery crate, perks, pack-a-punch and Easter eggs.

It's a greybox desktop game built with libGDX for Windows, Mac and Linux.

The places, units, weapons and timeline follow the historical record (see `Battle of Mogadishu game research.md`). Every character is fictional, and no real soldier's name is used.

## Modes

### Story

Pick a chapter, a difficulty and an operator. Each chapter has checkpoints. If you die, "Retry from checkpoint" rebuilds the chapter from the last one you reached. Checkpoints are saved, so "Continue" on the chapter screen picks up where you left off even after you quit.

| # | Chapter | Date | What happens |
| --- | --- | --- | --- |
| 1 | Wrong House | 30 Aug 1993, 03:09 (night) | Task Force Ranger's first raid. Sneak up to a house, breach the door, clear two rooms, secure the people inside. They turn out to be UN employees. Call extraction. |
| 2 | Atto's Car | 21 Sep 1993 | Ride a Little Bird's door gun alongside Osman Atto's car. Shoot the engine block (the front) to stop it without killing him, then rope down and grab him (hold E) while the militia closes in. Shooting him yourself fails the chapter. |
| 3 | Irene | 3 Oct, 15:42 | Ride in on Super 6-7, fast-rope onto the target street, clear the courtyard, breach the target house, clear it, secure the detainees. |
| 4 | Super Six-One Down | 3 Oct, 16:20 | Super 6-1 is hit by an RPG and goes down a few blocks north-east. Get there; Super 6-8 ropes in the rescue team; hold the crash site for 2 minutes. |
| 5 | The Lost Convoy | 3 Oct, 16:35 | Drive a Humvee with the wounded from the target to National Street, through the K-4 circle and out to the airfield. Every leg has burning tires, rooftop shooters and RPG teams that go after vehicles. If your Humvee dies, a spare appears near you. |
| 6 | Super Six-Four | 3 Oct, 16:40 | Hold the second crash site in the tin shacks through 5 waves, then call in a Little Bird and climb out. |
| 7 | The Long Night | 3-4 Oct (night) | Hold the Crash Site 1 perimeter, grab the water and ammo Super 6-6 drops, and last until the relief column links up at 01:55. |
| 8 | The Mogadishu Mile | 4 Oct, 05:30 (dawn) | The vehicles are full, so you run from the crash site past the Olympic Hotel to National Street under fire from the roofs. |
| 9 | The Other Side | 3 Oct | Play the militia in Bakara Market. Bring down 2 Black Hawks with RPGs while Rangers rope in on you, then survive one more wave. |

### Free Roam

You start at the airfield with three squadmates, three Humvees and a Black Hawk you can call with H. Four jobs are always open somewhere in the city and show on the minimap as yellow triangles. The objective arrow points to the nearest one.

| Job | What to do |
| --- | --- |
| HVT house | Breach a garrisoned house and clear it |
| Arms cache | Fight to the cache and hold E for 2 seconds to blow it |
| Roadblock | Kill the guards at a burning-tire roadblock |
| Downed pilot | Reach him before the militia does. He joins your squad |

Gunfire and kills raise the city's **Alert** level (shown as `#####`), which brings more militia patrols. It drops slowly when things are quiet. If you die, you respawn at the airfield after 4 seconds and lose 250 points. Pause → End free roam shows your score.

### Siege (Zombies-style)

Round-based survival in a walled compound with four zones. You start in the Courtyard with an M9 pistol and 500 points.

- **The militia:** they climb in through boarded windows. Each one tears off a board every second until the window is open. Stand near a window and **hold E** to nail boards back up for 10 points each, up to 500 per round.
- **Rounds:** every round has more enemies, and from round 10 their health grows 10% per round. Machete runners are slow in the first rounds and fast later. Riflemen join from round 3, RPG gunners from round 7 and PKM gunners from round 10.
- **Points:** 10 per hit, 60 per kill, 100 for a headshot kill and 130 for a melee kill (V).

| Thing | Cost | Notes |
| --- | --- | --- |
| Gates | 750 or 1000 | Open the Hotel Yard, the Market and the Crash Site. Each zone has its own windows, so more open zones means more ways in. |
| Wall guns | 500-2000 | Chalk outlines on the walls: M16A2, AK-47, Remington 870, MP5, M14, M79, PKM, M249, M60. Buying again gets ammo for half price. |
| Weapons crate | 950 | Spins for a random weapon, including the AT4, RPG and The Door Gun (a hand-held minigun). After a few uses a Little Bird may haul it away to another spot, and you get your points back. A light beam marks where it is. |
| Generator | free | In the Hotel Yard. Hold E to turn the power on. Perks and the Armorer's bench need it. |
| Armorer's bench | 5000 | In the Crash Site. This is pack-a-punch: double damage, 50% bigger magazine, double reserve, a little faster, and a new name (the M16A2 becomes "Irene's Kiss"). |
| Body Armor | 2500 | 250 health |
| Speed Loader | 3000 | Reload twice as fast |
| Double Tap | 2000 | Fire 33% faster |
| Second Wind | 500 | When you'd die, you go down for 6 seconds and get back up instead (3 times a game; buy it again after each use). Works without power. |
| Stamina | 2000 | Move 12% faster |

Kills sometimes drop power-ups:

| Power-up | Effect |
| --- | --- |
| Max Ammo | Refills all your ammo |
| Insta-Kill | Every hit kills, for 30 seconds |
| Double Points | 2x points for 30 seconds |
| Danger Close | Kills everything and gives +400 |
| Carpenter | Rebuilds every window and gives +200 |

**Easter eggs** (spoilers):
- **The gunship:** three strange radio parts are hidden in the arena:
  - on a rooftop in the Hotel Yard (climb the annex)
  - beside a market counter
  - in the tail of the wreck

  Bring them to the old radio in the starting house and hold E. An AC-130 Spectre orbits for 75 seconds and hits the militia with its big guns. Task Force Ranger asked for AC-130s in September 1993 and was turned down.
- **Solid gold:** four small red IRENE tags are painted in odd places, high on two outer walls, low on the Market's east wall and on the side of the wreck. Shoot all four and the next crate spin is a Golden AK.

## Close quarters: breaching, flashbangs, melee

- **Doors:** some houses are hollow, with rooms, an inner doorway, windows and a closed door. At a door:
  - **Tap E to kick it.** A kick works 65% of the time, and Delta always kicks it in. A failed kick tells the people inside you're there.
  - **Hold E for one second to plant a breaching charge**, if you have one (Rangers 2, Delta 3). It blows 1.5 seconds later, takes the door off, and stuns everyone inside for 2.6 seconds. Anyone right behind the door is hurt badly. The game drops into slow motion for 1.4 seconds so you can make your entry.
- **Flashbangs (T):** stun enemies that can see the blast within 13 m for 3.5 seconds. They whiteout your screen too if you're looking at it.
- **Melee (V):** a rifle-butt strike for 135 damage at 2 m. It kills most militia outright.

## Smarter enemies

Every enemy has a role:
- **Riflemen** look for a spot within about 16 m that hides them when they crouch but lets them shoot when they stand up. They go there, crouch, pop up to fire for 1-3 seconds, then duck again. If you move around them so their cover no longer protects them, they find new cover.
- **Suppression:** bullets passing close by make them stay down longer and shoot worse. The same works on you: near misses widen your spread and darken the screen edges. Explosions suppress everyone nearby.
- **Flankers** (about a fifth of them) swing wide to one side of you before they engage.
- **Rushers and machete runners** close the distance and hit hard up close.
- **Gunners** (PKM, SAW) fire long bursts from cover.
- **Grenades:** if they lose sight of you for a few seconds, they may lob a grenade at where you were.
- **Shared sightings:** when one of them spots you, everyone within 35 m learns where you are.
- **Garrisons** wait inside houses, aimed at the door, until it opens or they see you. Rooftop shooters stay put.
- **RPG gunners** go after low helicopters and Humvees, and lead moving targets.
- **At night** they see you at about 36 m instead of 90 m, unless you've just fired.

Your squadmates stay with you, take cover near you, and ride in your Humvee or helicopter.

## Vehicles

- **Getting in:** E next to a Humvee. You take the driver's seat if it's free, otherwise the .50 cal. Squadmates within 22 m climb in with you. E again gets everyone out.
- **Driving:** a third-person camera. W and S are throttle and reverse, A and D steer, and Space brakes. Mouse moves the camera. Ramming at speed runs people over, but crashing into walls damages the Humvee.
- **The gun:** X switches you between driving and the .50 cal on the roof. If you take the gun, a squadmate drives toward the objective. The .50 overheats instead of reloading.
- **Damage:** small arms barely scratch a Humvee, and about three RPGs kill one. When it burns, everyone inside takes damage and gets thrown out.

## Night

- **Night chapters:** Wrong House and The Long Night take place at night, and Siege defaults to night.
- **Night setting:** on the chapter screen (and before Free Roam or Siege), the Night button cycles chapter default / always / never. You can also flip night on or off at any time from the pause menu.
- **N toggles night vision:** a green image with grain and a dark border.
- **Light sources:** at night, burning tires, wrecks, muzzle flashes and explosions light up their surroundings, and the Siege compound gets lamps once the power is on.

## Controls

| Action | Key |
| --- | --- |
| Move / look | WASD / mouse |
| Fire / aim down sights | Left / right mouse |
| Sprint, jump/vault, crouch (tap while sprinting to slide) | Shift, Space, C or Left Ctrl |
| Reload, switch weapon | R; Q, mouse wheel or 1/2 |
| Frag, flashbang, melee | G, T, V |
| Use: doors (tap kick / hold charge), vehicles, fast-rope, climb rope, ammo crates, buy, objectives | E or F |
| Switch seat in a Humvee (driver / .50 cal) | X |
| Call extraction | H |
| Night vision | N |
| Pause (night toggle, restart from checkpoint) | Esc or P |
| Fullscreen | F11 |

## Movement and parkour

- **Vaulting:** Space vaults onto anything up to 2.6 m high, and you grab ledges automatically if you're in the air and pushing forward. Chain it: rubble → wall → one-storey roof → two-storey roof.
- **Sliding:** tap C while sprinting.
- **Ropes:** fast-rope down from a hovering helicopter, and climb a rope up into one.

## The map

The city is 640 x 640 m, with real distances squeezed about 2.2x so the battle fits.
- **Target and hotel:** the target house sits one block north of the Olympic Hotel on Hawlwadig Road. Bakara Market is to the west.
- **Crash sites:** Crash Site 1 is in a walled alley a few blocks north-east. Crash Site 2 is about 360 m south, in the tin-shack belt.
- **Main roads:** National Street runs east-west five blocks south. A road goes from National Street through the K-4 circle to the airfield.
- **Edges:** the Pakistani Stadium is in the north-east and the New Port is on the east edge.
- **Generated blocks:** everything else is generated from a seed. That includes walled compounds, hollow houses you can breach (with rooms, windows and doors), solid houses, shacks, rubble, telephone poles and burning-tire barricades.

In Siege, an 88 x 88 m walled arena is built into the city just east of Crash Site 1.

## Code

```
core/     all game code
lwjgl3/   desktop launcher and the runnable-jar build
assets/   sfx/*.wav  music/*.wav  fonts/DejaVuSans-Bold.ttf
tools/    make_sfx.py (synthesizes every sound and both music loops)
Play Irene.bat     double-click to build and play on Windows
BLENDER_GUIDE.md   replacing the greybox with real models, step by step
```

| File | What it does |
| --- | --- |
| `City.java` | The map. Roads, landmarks, compounds, hollow houses with doors and windows, the Siege arena, and boxes that can be removed (doors, gates, window boards). Also handles collision, bullet and sight rays, and the AI walking grid, which knows about walls between cells so units go through doorways instead of through walls. |
| `Battle.java` | The rules engine: movement and parkour, weapons (including shotguns, grenade launchers and pack-a-punch), damage and suppression, grenades, flashbangs, breaching charges, doors, melee, Humvees and cars, helicopters, pickups, and waves. Draws nothing and plays nothing, so it runs headless. |
| `Ai.java` | Enemy and squad behaviour: roles, cover-peek cycles, suppression, flanking, grenades, garrisons, sharing sightings, and short-range pathfinding to cover. |
| `Mode.java` | The hooks a mode plugs into: setup, per-tick script, E-key use, prompts, kills, hits, player death, pickups, HUD line, minimap markers. |
| `Story.java` | The nine chapters, each split into stages, with checkpoints. Any checkpoint stage can be rebuilt from scratch. |
| `FreeRoam.java` | The jobs, the alert level and patrols, and respawning. |
| `Siege.java` | Rounds, points, gates, wall guns, the crate, the bench, perks, the generator, power-ups, window boards, and the Easter eggs. |
| `Bot.java` / `Sim.java` | A bot that drives the player with the same input a human gives (shoots, breaches, drives, extracts), and the headless balance run. |
| `Irene.java` | Camera (including the chase camera for driving), world drawing, people, helicopters, Humvees, Siege props, particles (blood, mist, splats, dust, sparks, smoke, fire, explosions, rotor wash, flashbang), night lighting with point lights, night vision, slow motion, HUD, menus, chapter select and checkpoints. |
| `Art.java` | Every model, built in code: city chunks, helicopters, Humvee, car, .50 cal, AC-130, crate, bench, every weapon. |
| `Sfx.java`, `Save.java` | Sound with distance and panning, music; settings, chapter progress, checkpoints, best scores. |

### How effects get from the rules to the screen

`Battle` never calls graphics or sound code. When something happens it adds an `Event` to `battle.events`, such as `SHOT`, `BLOOD`, `BREACH`, `FLASH`, `VEH_DOWN`, `BUY`, `POWERUP`, `CHECKPOINT` or `EGG`. `Irene.handleEvents()` turns each one into sound, particles, banners and screen effects. To add an effect, emit an event and handle it there.

## Running it

You need Java 17 or newer (Android Studio's bundled copy works).

**Easiest: double-click `Play Irene.bat`.** The first run builds the game. After a code change, delete `lwjgl3\build\libs\Irene.jar` and it rebuilds.

**Terminal:** `gradlew lwjgl3:run`. **One file to share:** `gradlew lwjgl3:dist`, then `java -jar lwjgl3/build/libs/Irene.jar`.

**Launch flags:**

| Flag | What it does |
| --- | --- |
| `--solo` | Skip the menus |
| `--mode story\|roam\|siege` | Which mode to start |
| `--chapter N` | Story chapter, 1-9 |
| `--stage N` | Start at a checkpoint stage |
| `--kit N` | Operator, 0-6 in the operator table's order |
| `--difficulty N` | 0-2 |
| `--night on\|off\|auto` | Night setting |
| `--autoplay` | The bot plays |
| `--shot file.png seconds` | Save a screenshot and quit |
| `--sim N` | Headless balance run |

For example: `Play Irene.bat --solo --mode story --chapter 5 --night on`.

## Operators

| Operator | Primary | Secondary | Frags / flash / charges | Health | Notes |
| --- | --- | --- | --- | --- | --- |
| Ranger Rifleman | CAR-15 | M72 LAW | 4 / 2 / 2 | 100 | Body armour |
| Ranger SAW Gunner | M249 SAW | M72 LAW | 2 / 1 / 1 | 115 | 8% slower |
| Delta Operator | CAR-15 + Aimpoint | M14 | 3 / 3 / 3 | 100 | Faster, quicker reloads and vaults, always kicks doors in |
| Pararescue (PJ) | CAR-15 | M72 LAW | 2 / 2 / 2 | 100 | Heals twice as fast; E heals squadmates |
| Militia Rifleman | AK-47 | RPG-7 (2) | 2 / 0 / 0 | 90 | 5% faster |
| RPG Gunner | AK-47 | RPG-7 (6) | 1 / 0 / 0 | 90 | |
| PKM Gunner | PKM | RPG-7 (1) | 1 / 0 / 0 | 105 | 7% slower |

## Tuning

**`Battle.java`, at the top:**
- **Difficulty:** `PLAYER_DMG` (damage you take per difficulty), `ENEMY_FIRE_MUL`, `ENEMY_SPREAD_ADD`.
- **Close quarters:** `KICK_CHANCE`, `PLANT_TIME`, `CHARGE_FUSE`, `BREACH_STUN`, `FLASH_RAD`, `FLASH_STUN`, `MELEE_DMG`, `SUPPRESS_RAD`.
- **Vehicles:** `V_ACCEL`, `V_MAX`, `V_STEER`, `V_RUNOVER`. Humvee health is in `vehicle()`.
- **Night:** `SIGHT_DAY`, `SIGHT_NIGHT`.
- **Weapons and operators:** the `WEAPONS` and `KITS` tables.

**`Ai.java`:**
- Role chances are in `assignRole()`.
- Cover distances are in `decide()` (20 m for riflemen, 30 m for gunners).
- Peek and cover times, and the grenade chance, are in `decide()`.
- Accuracy is in `fire()`.

**`Siege.java`:**
- Enemy count per round is `roundTotal()`, health is `enemyHp()`, and speed is `speedMul` in `spawnOne()`.
- `BOX_COST`, `PAP_COST`, `BOX_POOL`, and the power-up drop chance in `onKill()`.
- Prices for gates, wall guns and perks are set where the arena is built, in `City.arena()`.

**Chapters:** each chapter's spawns and timers live in `Story.begin()` and `Story.tick()`.

## Last balance run

The bot plays every mode, 4 runs each. It aims well, uses doors, drives and calls extraction. It never takes cover, peeks or uses height, and in Siege it never buys anything. Treat these numbers as a floor for a human player.

| Chapter | Recruit | Regular | Veteran |
| --- | --- | --- | --- |
| Wrong House | 100% | 100% | 100% |
| Atto's Car | 50% (the bot sometimes doesn't walk up to Atto) | 50% | 50% |
| Irene | 100% | 100% | 50% |
| Super Six-One Down | 100% | 100% | 100% |
| The Lost Convoy | 75% | 25% | 0% |
| Super Six-Four | 100% | 100% | 0% |
| The Long Night | 100% | 25% | 0% |
| The Mogadishu Mile | 100% | 100% | 50% |
| The Other Side | 0% (the bot can't lead helicopters) | 0% | 0% |
| Siege (bot never buys) | round 3-4 | round 3-4 | round 3-4 |
| Free Roam, 5 min | 2 deaths, 1 job | 2 deaths, 1 job | 7 deaths, 0.5 jobs |

## Not in this version

- **Real models and animation.** Everyone is still built from boxes. See `BLENDER_GUIDE.md`.
- **Multi-storey interiors.** Hollow houses are one storey (two rooms). Big buildings are solid.
- **AI convoys.** You drive one Humvee; there are no AI-driven columns, 5-tons, technicals, Malaysian Condors or Pakistani tanks yet.
- **Flying the helicopter yourself.**
- **Siege extras:** co-op, a downed-teammate revive, and more maps.
- **Civilians.** This is still an open design question; see the earlier notes.
