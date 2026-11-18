package com.saedarm.irene.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.saedarm.irene.Irene;

/**
 * Desktop entry. Flags: --solo (skip menus), --mode story|roam|siege, --chapter N (1-9), --stage N (checkpoint),
 * --kit N, --difficulty N, --night on|off|auto, --autoplay, --shot file.png seconds, --sim N
 */
public final class Launcher {
    public static void main(String[] args) {
        Irene g = new Irene();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--solo": g.solo = true; break;
                case "--autoplay": g.autoplay = true; break;
                case "--mode": { String m = args[++i]; g.flagMode = m.startsWith("roam") || m.startsWith("free") ? Irene.MODE_ROAM : m.startsWith("siege") ? Irene.MODE_SIEGE : Irene.MODE_STORY; break; }
                case "--chapter": g.flagChapter = Integer.parseInt(args[++i]) - 1; break;
                case "--stage": g.flagStage = Integer.parseInt(args[++i]); break;
                case "--kit": g.flagKit = Integer.parseInt(args[++i]); break;
                case "--difficulty": g.flagDiff = Integer.parseInt(args[++i]); break;
                case "--night": { String n = args[++i]; g.flagNight = n.equals("on") ? 1 : n.equals("off") ? 2 : 0; break; }
                case "--shot": g.shotFile = args[++i]; g.shotAt = Float.parseFloat(args[++i]); break;
                case "--sim": com.saedarm.irene.Sim.main(new String[]{args[++i]}); return;
            }
        }
        Lwjgl3ApplicationConfiguration c = new Lwjgl3ApplicationConfiguration();
        c.setTitle("Irene - Mogadishu 1993");
        c.setWindowedMode(1600, 900);
        c.useVsync(true);
        c.setForegroundFPS(144);
        c.setBackBufferConfig(8, 8, 8, 8, 24, 0, 4);
        new Lwjgl3Application(g, c);
    }
}
