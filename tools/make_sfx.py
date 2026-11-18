"""Synthesizes every sound and both music loops as 22050 Hz mono 16-bit WAV.
Placeholders: drop in a real recording with the same filename to replace any of them.
Usage: python tools/make_sfx.py   (needs numpy + scipy)"""
import os
import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, lfilter

SR = 22050
ROOT = os.path.join(os.path.dirname(__file__), "..", "assets")
rng = np.random.default_rng(3)


def t(d): return np.linspace(0, d, int(SR * d), endpoint=False)
def noise(d): return rng.uniform(-1, 1, int(SR * d))
def env(n, a=0.002, d=0.2, curve=4.0):
    x = np.arange(n) / SR
    e = np.minimum(1, x / max(a, 1e-4)) * np.exp(-curve * np.maximum(0, x - a) / max(d, 1e-4))
    return e
def lp(x, f): b, a = butter(2, min(0.99, f / (SR / 2)), "low"); return lfilter(b, a, x)
def hp(x, f): b, a = butter(2, min(0.99, f / (SR / 2)), "high"); return lfilter(b, a, x)
def bp(x, lo, hi): b, a = butter(2, [lo / (SR / 2), min(0.99, hi / (SR / 2))], "band"); return lfilter(b, a, x)
def norm(x, g=0.9): m = np.max(np.abs(x)) or 1; return x / m * g
def save(path, x, g=0.9):
    x = norm(x, g); os.makedirs(os.path.dirname(path), exist_ok=True)
    wavfile.write(path, SR, (x * 32767).astype(np.int16))
def sfx(name, x, g=0.9): save(os.path.join(ROOT, "sfx", name + ".wav"), x, g)


def tone(f, d, harm=(1, .5, .25), dec=1.5):
    x = t(d); s = sum(a * np.sin(2 * np.pi * f * (i + 1) * x) for i, a in enumerate(harm)); return s * env(len(x), 0.005, d / dec, 1)


def gunshot(d=0.35, body=900, crack=4000, boom=0.5, tail=0.18):
    n = noise(d)
    c = hp(n, crack) * env(len(n), 0.0005, 0.02, 1)
    b = lp(n, body) * env(len(n), 0.001, tail, 1)
    lo = np.sin(2 * np.pi * 70 * t(d)) * env(len(n), 0.001, 0.06, 1) * boom
    return c * 0.8 + b + lo


sfx("car15", gunshot(0.3, 1400, 4500, 0.4, 0.12))
sfx("saw", gunshot(0.28, 1200, 4200, 0.5, 0.1))
sfx("m14", gunshot(0.5, 900, 3800, 0.9, 0.22))
sfx("ak", gunshot(0.38, 800, 3200, 0.7, 0.16))
sfx("pkm", gunshot(0.34, 700, 3000, 0.8, 0.13))
sfx("minigun", gunshot(0.09, 1500, 5000, 0.2, 0.03))

# RPG: whoosh with a pop
d = 1.2; n = noise(d); x = t(d)
sweep = bp(n, 300, 2500) * env(len(n), 0.01, 0.6, 1.5)
pop = lp(noise(d), 600) * env(len(n), 0.001, 0.05, 1)
sfx("rpg", pop * 1.5 + sweep)

# explosion: low rumble, crack, debris
d = 2.6; n = noise(d)
boom = lp(n, 160) * env(len(n), 0.003, 0.9, 1.2) * 2.2
crack = hp(n, 1500) * env(len(n), 0.001, 0.05, 1)
deb = bp(noise(d), 1500, 5000) * env(len(n), 0.2, 0.7, 1.5) * (rng.uniform(0, 1, len(n)) > 0.985)
sfx("explosion", boom + crack + deb * 2)

sfx("grenade", np.concatenate([bp(noise(0.06), 2000, 6000) * env(int(SR * .06), 0.001, 0.02, 1), np.zeros(1500), bp(noise(0.05), 1500, 4000) * env(int(SR * .05), 0.001, 0.015, 1)]))
# reload: two metallic clicks
def click(f=2500, d=0.05): n = noise(d); return bp(n, f, f * 2.5) * env(len(n), 0.0005, 0.012, 1)
sfx("reload", np.concatenate([click(1800), np.zeros(int(SR * 0.35)), click(2600), np.zeros(int(SR * 0.08)), click(3200)]))
sfx("hit", lp(noise(0.12), 500) * env(int(SR * .12), 0.001, 0.04, 1))
sfx("headshot", np.sin(2 * np.pi * 1800 * t(0.15)) * env(int(SR * .15), 0.001, 0.05, 1) + lp(noise(0.15), 600) * env(int(SR * .15), 0.001, 0.03, 1))
sfx("kill", np.sin(2 * np.pi * 1200 * t(0.12)) * env(int(SR * .12), 0.001, 0.04, 1) * 0.6)
sfx("hurt", lp(noise(0.25), 300) * env(int(SR * .25), 0.005, 0.1, 1) + np.sin(2 * np.pi * 110 * t(0.25)) * env(int(SR * .25), 0.01, 0.1, 1))
sfx("die", lp(noise(0.5), 250) * env(int(SR * .5), 0.01, 0.2, 1))
sfx("rope", bp(noise(1.2), 600, 3000) * env(int(SR * 1.2), 0.05, 0.6, 1.5))
# rotor: low thump at ~4.5 Hz blade passes plus turbine whine, loopable 2 s
d = 2.0; x = t(d)
thump = lp(noise(d), 120) * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 4.5 * x)) * np.abs(np.sin(2 * np.pi * 4.5 * x)) ** 4) * 3
whine = np.sin(2 * np.pi * 880 * x) * 0.05 + np.sin(2 * np.pi * 1320 * x) * 0.03
sfx("rotor", thump + whine + lp(noise(d), 400) * 0.25, 0.7)
sfx("fire", bp(noise(1.5), 200, 2500) * (rng.uniform(0, 1, int(SR * 1.5)) > 0.97) + lp(noise(1.5), 300) * 0.3)
# horn for a new wave: two low notes
sfx("wave", np.concatenate([np.sin(2 * np.pi * 220 * t(0.35)) * env(int(SR * .35), 0.01, 0.3, 1), np.sin(2 * np.pi * 196 * t(0.6)) * env(int(SR * .6), 0.01, 0.5, 1)]) * 0.8)
sfx("objective", np.concatenate([np.sin(2 * np.pi * f * t(0.12)) * env(int(SR * .12), 0.005, 0.1, 1) for f in (660, 880)]))
sfx("win", np.concatenate([np.sin(2 * np.pi * f * t(0.25)) * env(int(SR * .25), 0.01, 0.25, 1) for f in (392, 523, 659, 784)]))
sfx("fail", np.concatenate([np.sin(2 * np.pi * f * t(0.4)) * env(int(SR * .4), 0.01, 0.35, 1) for f in (330, 262, 196)]))
sfx("impact", bp(noise(0.08), 300, 2500) * env(int(SR * .08), 0.001, 0.02, 1))
x = t(0.25); sfx("whiz", bp(noise(0.25), 1500, 6000) * np.sin(np.pi * x / 0.25) ** 2)
sfx("step", lp(noise(0.06), 400) * env(int(SR * .06), 0.001, 0.02, 1))
sfx("jump", lp(noise(0.1), 700) * env(int(SR * .1), 0.002, 0.03, 1))
sfx("land", lp(noise(0.18), 300) * env(int(SR * .18), 0.001, 0.05, 1))
sfx("click", click(3000, 0.03))
x = t(0.18); sfx("radio", bp(noise(0.18), 800, 3000) * 0.5 + np.sin(2 * np.pi * 1000 * x) * env(len(x), 0.001, 0.05, 1) * 0.3)
sfx("heli_hit", hp(noise(0.12), 2000) * env(int(SR * .12), 0.001, 0.03, 1) + np.sin(2 * np.pi * 900 * t(0.12)) * env(int(SR * .12), 0.001, 0.03, 1))
sfx("crash", lp(noise(3.0), 220) * env(int(SR * 3), 0.01, 1.2, 1) * 2 + bp(noise(3.0), 1000, 5000) * env(int(SR * 3), 0.05, 1.0, 1) * (rng.uniform(0, 1, int(SR * 3)) > 0.95))
sfx("pickup", np.concatenate([click(2000), np.zeros(800), click(2400), np.sin(2 * np.pi * 990 * t(0.1)) * env(int(SR * .1), 0.002, 0.05, 1) * 0.4]))
sfx("slide", bp(noise(0.6), 200, 1800) * env(int(SR * .6), 0.02, 0.35, 1.2))


# ---- added for doors, vehicles and Siege ----
sfx("fifty", gunshot(0.45, 600, 2600, 1.4, 0.2))
sfx("pistol", gunshot(0.25, 1600, 5000, 0.25, 0.08))
sfx("shotgun", gunshot(0.55, 700, 3000, 1.2, 0.22))
sfx("kick", lp(noise(0.3), 380) * env(int(SR * .3), 0.001, 0.07, 1) * 2 + bp(noise(0.3), 800, 2500) * env(int(SR * .3), 0.001, 0.03, 1))
d = 1.8; n = noise(d)
sfx("breach", lp(n, 220) * env(len(n), 0.002, 0.5, 1.2) * 2 + hp(n, 1800) * env(len(n), 0.001, 0.04, 1) + bp(noise(d), 1500, 5000) * env(len(n), 0.05, 0.6, 1.5) * (rng.uniform(0, 1, len(n)) > 0.97))
x = t(1.6); sfx("flash", hp(noise(1.6), 1200) * env(len(x), 0.001, 0.05, 1) * 2 + np.sin(2 * np.pi * 3800 * x) * env(len(x), 0.01, 1.2, 1) * 0.4)
sfx("buy", np.concatenate([np.sin(2 * np.pi * f * t(0.09)) * env(int(SR * .09), 0.002, 0.08, 1) for f in (1320, 1760)]) + 0)
sfx("deny", np.sin(2 * np.pi * 180 * t(0.25)) * env(int(SR * .25), 0.005, 0.2, 1))
sfx("box", np.concatenate([tone(f, 0.18, (1, .4, .2), 2) for f in (523, 659, 784, 1047, 784, 659, 523, 659, 784, 1047, 1319)]))
sfx("pap", np.concatenate([lp(noise(0.4), 300) * env(int(SR * .4), 0.01, 0.3, 1), tone(110, 1.2, (1, .8, .5, .3), 1.5), tone(220, 0.8, (1, .6, .3), 2)]))
sfx("perk", np.concatenate([tone(f, 0.16, (1, .5, .25), 2) for f in (392, 494, 587, 784, 587, 784)]))
sfx("powerup", np.concatenate([tone(f, 0.12, (1, .3), 2) for f in (880, 1175, 1568)]))
sfx("round", np.concatenate([tone(98, 1.4, (1, .9, .6, .4, .2), 1.3), tone(73.4, 2.0, (1, .9, .6, .4, .2), 1.2)]))
print("wrote extra sfx")

# ---- music ----

# menu: slow drone in a Hijaz-like mode with plucked notes
bpm = 70; beat = 60 / bpm; d = beat * 32
out = np.zeros(int(SR * d))
x = t(d); out += (np.sin(2 * np.pi * 73.4 * x) + 0.5 * np.sin(2 * np.pi * 110 * x)) * 0.25 * (0.7 + 0.3 * np.sin(2 * np.pi * x / d))
scale = [293.7, 311.1, 370.0, 392.0, 440.0, 466.2, 523.3, 587.3]
for i in range(32):
    if rng.uniform() < 0.55:
        f = scale[rng.integers(0, len(scale))] / (2 if rng.uniform() < 0.4 else 1)
        n = tone(f, beat * 1.5, (1, .6, .3, .15), 3); s = int(i * beat * SR); e = min(len(out), s + len(n)); out[s:e] += n[:e - s] * 0.35
save(os.path.join(ROOT, "music", "menu.wav"), lp(out, 4000), 0.6)

# combat: driving drum loop with a low ostinato
bpm = 112; beat = 60 / bpm; d = beat * 32
out = np.zeros(int(SR * d))
kick = lp(noise(0.25), 120) * env(int(SR * .25), 0.001, 0.08, 1) * 2 + np.sin(2 * np.pi * 55 * t(0.25)) * env(int(SR * .25), 0.001, 0.12, 1)
snare = bp(noise(0.2), 600, 5000) * env(int(SR * .2), 0.001, 0.06, 1)
tom = np.sin(2 * np.pi * 120 * t(0.3)) * env(int(SR * .3), 0.001, 0.1, 1)
def put(sig, at, g=1.0):
    s = int(at * SR); e = min(len(out), s + len(sig)); out[s:e] += sig[:e - s] * g
for i in range(32):
    put(kick, i * beat, 0.9)
    if i % 2 == 1: put(snare, i * beat, 0.5)
    if i % 4 == 3: put(tom, (i + 0.5) * beat, 0.5)
    put(snare * 0.25, (i + 0.5) * beat, 0.2)
ost = [73.4, 73.4, 77.8, 73.4, 92.5, 87.3, 77.8, 73.4]
for i in range(32): put(tone(ost[i % 8], beat * 0.9, (1, .7, .4, .2), 2), i * beat, 0.35)
save(os.path.join(ROOT, "music", "combat.wav"), out, 0.6)
print("wrote sfx and music to", os.path.abspath(ROOT))
