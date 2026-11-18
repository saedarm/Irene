package com.saedarm.irene;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;

import java.util.HashMap;

/** Loads assets/sfx/*.wav, plays with distance falloff and pan, throttles repeats, survives no audio device. */
public final class Sfx {
    public static final String[] NAMES = {"car15", "saw", "m14", "ak", "pkm", "minigun", "rpg", "explosion", "grenade", "reload", "hit", "headshot", "kill", "hurt", "die",
            "rope", "rotor", "fire", "wave", "objective", "win", "fail", "impact", "whiz", "step", "jump", "land", "click", "radio", "heli_hit", "crash", "pickup", "slide",
            "fifty", "pistol", "shotgun", "kick", "breach", "flash", "buy", "deny", "box", "pap", "perk", "powerup", "round"};
    private final HashMap<String, Sound> sounds = new HashMap<>();
    private final HashMap<String, Long> last = new HashMap<>();
    private Music menu, combat, current;
    public float volume = 0.8f, musicVolume = 0.5f;
    public float lx, ly, lz, lyaw;
    private long rotorId = -1;
    private boolean ok = true;

    public Sfx() {
        try {
            for (String n : NAMES) { try { sounds.put(n, Gdx.audio.newSound(Gdx.files.internal("sfx/" + n + ".wav"))); } catch (Exception e) { /* missing file: skip */ } }
            try { menu = Gdx.audio.newMusic(Gdx.files.internal("music/menu.wav")); menu.setLooping(true); } catch (Exception ignored) { }
            try { combat = Gdx.audio.newMusic(Gdx.files.internal("music/combat.wav")); combat.setLooping(true); } catch (Exception ignored) { }
        } catch (Throwable t) { ok = false; }
    }

    public void listener(float x, float y, float z, float yaw) { lx = x; ly = y; lz = z; lyaw = yaw; }

    public void play(String n, float vol) { play(n, vol, 1, 0); }
    public void play(String n, float vol, float pitch, float pan) {
        if (!ok || volume <= 0) return;
        Sound s = sounds.get(n); if (s == null) return;
        long now = System.currentTimeMillis(); Long l = last.get(n);
        if (l != null && now - l < 35) return;
        last.put(n, now);
        try { s.play(Math.min(1, vol * volume), pitch, Math.max(-1, Math.min(1, pan))); } catch (Throwable ignored) { }
    }
    /** Positional: louder when close, panned by direction relative to where you face. */
    public void at(String n, float x, float y, float z, float vol, float range) {
        float dx = x - lx, dy = y - ly, dz = z - lz, d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d > range) return;
        float f = 1 - d / range; f *= f;
        float ang = (float) Math.atan2(-dx, -dz) - lyaw;
        float pan = (float) -Math.sin(ang) * Math.min(1, d / 6);
        play(n, vol * (0.15f + 0.85f * f), 0.95f + (float) Math.random() * 0.1f, pan);
    }

    /** One looping rotor sound, volume set from the nearest helicopter every frame. */
    public void rotor(float vol) {
        if (!ok) return; Sound s = sounds.get("rotor"); if (s == null) return;
        try {
            if (rotorId < 0 && vol > 0.01f) rotorId = s.loop(vol * volume);
            else if (rotorId >= 0) s.setVolume(rotorId, vol * volume);
        } catch (Throwable ignored) { }
    }

    public void music(boolean inGame) {
        Music want = inGame ? combat : menu;
        if (want == current) { if (current != null) current.setVolume(musicVolume); return; }
        try { if (current != null) current.stop(); current = want; if (current != null && musicVolume > 0) { current.setVolume(musicVolume); current.play(); } } catch (Throwable ignored) { }
    }
    public void stopLoops() { Sound s = sounds.get("rotor"); if (s != null && rotorId >= 0) { s.stop(rotorId); rotorId = -1; } }
    public void dispose() { for (Sound s : sounds.values()) s.dispose(); if (menu != null) menu.dispose(); if (combat != null) combat.dispose(); }
}
