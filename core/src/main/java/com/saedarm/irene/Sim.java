package com.saedarm.irene;

/** Headless balance run: the autoplay bot plays every chapter, Free Roam and Siege. gradlew core:sim -Pruns=6 */
public final class Sim {
    public static void main(String[] a) {
        int runs = a.length > 0 ? Integer.parseInt(a[0]) : 6;
        int diffFrom = a.length > 1 ? Integer.parseInt(a[1]) : 0, diffTo = a.length > 2 ? Integer.parseInt(a[2]) : 2;
        for (int diff = diffFrom; diff <= diffTo; diff++) {
            System.out.println("Difficulty " + new String[]{"Recruit", "Regular", "Veteran"}[diff]);
            for (int ch = 0; ch < Story.NAMES.length; ch++) {
                int win = 0, fail = 0, timeout = 0, cp = 0; float tSum = 0; int kSum = 0; String why = "";
                long ms = System.currentTimeMillis();
                for (int i = 0; i < runs; i++) {
                    Story st = new Story(ch, 0);
                    Battle b = new Battle(1000 + i, st, Story.SNA[ch] ? 4 : 0, diff, Story.NIGHT[ch]);
                    Bot bot = new Bot(b);
                    while (b.result == 0 && b.time < 600) { bot.fill(b.input); b.tick(); b.events.clear(); }
                    if (b.result == 1) win++; else if (b.result == 2) { fail++; why = b.resultText; } else { timeout++; why = "stuck: " + b.objective; }
                    tSum += b.time; kSum += b.player.kills; cp += st.stage;
                }
                System.out.printf("  %-20s win %3d%%  fail %3d%%  timeout %3d%%  avg %4.0fs  kills %5.1f  stage %.1f  (%d ms/run)  %s%n",
                        Story.NAMES[ch], 100 * win / runs, 100 * fail / runs, 100 * timeout / runs, tSum / runs, kSum / (float) runs, cp / (float) runs, (System.currentTimeMillis() - ms) / runs, why);
            }
            { int rounds = 0, best = 0; long ms = System.currentTimeMillis();
                for (int i = 0; i < runs; i++) { Siege sg = new Siege(); Battle b = new Battle(2000 + i, sg, 0, diff, true); Bot bot = new Bot(b); while (b.result == 0 && b.time < 900) { bot.fill(b.input); b.tick(); b.events.clear(); } rounds += sg.round; best = Math.max(best, sg.round); }
                System.out.printf("  %-20s avg round %.1f  best %d  (%d ms/run)%n", "Siege", rounds / (float) runs, best, (System.currentTimeMillis() - ms) / runs); }
            { int deaths = 0, jobs = 0; long ms = System.currentTimeMillis();
                for (int i = 0; i < runs; i++) { FreeRoam fr = new FreeRoam(); Battle b = new Battle(3000 + i, fr, 0, diff, false); Bot bot = new Bot(b); while (b.time < 300) { bot.fill(b.input); b.tick(); b.events.clear(); } deaths += fr.deaths; jobs += fr.completed; }
                System.out.printf("  %-20s 5 min: deaths %.1f  jobs %.1f  (%d ms/run)%n", "Free Roam", deaths / (float) runs, jobs / (float) runs, (System.currentTimeMillis() - ms) / runs); }
        }
    }
}
