package com.saedarm.irene;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

/** Settings, chapter progress, checkpoints and best results in libGDX Preferences (%USERPROFILE%/.prefs/Irene on Windows). */
public final class Save {
    private final Preferences p = Gdx.app.getPreferences("Irene");
    public float sens = p.getFloat("sens", 1f), fov = p.getFloat("fov", 75), sfx = p.getFloat("sfx", 0.8f), music = p.getFloat("music", 0.45f);
    public boolean fullscreen = p.getBoolean("fullscreen", false), invertY = p.getBoolean("invertY", false), blood = p.getBoolean("blood", true), showFps = p.getBoolean("fps", false);
    public int kit = p.getInteger("kit", 0), difficulty = p.getInteger("difficulty", 1), chapter = p.getInteger("chapter", 0);
    /** 0 = chapter default, 1 = always night, 2 = always day. */
    public int nightPref = p.getInteger("nightPref", 0);

    public boolean done(int ch) { return p.getBoolean("done" + ch, false); }
    public void done(int ch, boolean v) { if (v) { p.putBoolean("done" + ch, true); p.flush(); } }
    public int checkpoint(int ch) { return p.getInteger("cp" + ch, 0); }
    public void checkpoint(int ch, int stage) { p.putInteger("cp" + ch, stage); p.flush(); }
    public int best(String key) { return p.getInteger("best" + key, 0); }
    public void best(String key, int v) { if (v > best(key)) { p.putInteger("best" + key, v); p.flush(); } }

    public void flush() {
        p.putFloat("sens", sens); p.putFloat("fov", fov); p.putFloat("sfx", sfx); p.putFloat("music", music);
        p.putBoolean("fullscreen", fullscreen); p.putBoolean("invertY", invertY); p.putBoolean("blood", blood); p.putBoolean("fps", showFps);
        p.putInteger("kit", kit); p.putInteger("difficulty", difficulty); p.putInteger("chapter", chapter); p.putInteger("nightPref", nightPref);
        p.flush();
    }
}
