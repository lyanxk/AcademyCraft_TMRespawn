package cn.academy.client.render.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** CurrentChargingHUD and SubArc2D logic, independent of Minecraft drawing and wall-clock APIs. */
public final class IntensifyHudAnimation {
    public record Quad(int texture, double x, double y, double size, double alpha) {}
    public record Frame(double maskAlpha, List<Quad> arcs, boolean expired) {}
    private static final class Arc {
        double x, y, size, switchRate;
        int frame, age, life;
        boolean shown = true, dead;
    }
    private final Random utility, animation;
    private final ArrayList<Arc> arcs = new ArrayList<>();
    private final double created;
    private double blend = -1;
    public IntensifyHudAnimation(double now) { this(now, new Random(), new Random()); }
    public IntensifyHudAnimation(double now, Random utility, Random animation) {
        this.created = now; this.utility = utility; this.animation = animation;
        generate(false);
    }
    private void generate(boolean success) {
        int count = success ? 10 + utility.nextInt(5) : 5 + utility.nextInt(2);
        for (int i = 0; i < count; i++) {
            double radius = success ? .6 + utility.nextDouble() * .4 : .84 + utility.nextDouble() * .12;
            double angle = utility.nextDouble() * Math.PI * 2;
            double size = (success ? 35 : 25) + utility.nextDouble() * 5;
            Arc arc = new Arc(); arc.x = radius * Math.sin(angle); arc.y = radius * Math.cos(angle); arc.size = size;
            arc.frame = animation.nextInt(10); arc.life = success ? 25 : 233333; arc.switchRate = success ? .2 : 0;
            arcs.add(arc);
        }
    }
    public void blend(double now, boolean performed, boolean firstPerson) {
        blend = now; arcs.clear();
        if (performed && firstPerson) generate(true);
    }
    public void tick() {
        arcs.removeIf(arc -> arc.dead);
        for (Arc arc : arcs) {
            if (animation.nextDouble() < .5 * .3) arc.frame = animation.nextInt(10);
            if (animation.nextDouble() < .9) arc.age++;
            if (arc.age == arc.life) arc.dead = true;
            if (arc.shown) { if (animation.nextDouble() < .4 * arc.switchRate) arc.shown = false; }
            else if (animation.nextDouble() < .3 * arc.switchRate) arc.shown = true;
        }
    }
    public Frame frame(double now, double width, double height) {
        boolean ending = blend != -1;
        double alpha = ending ? Math.max(0, 1 - (now - blend) / .2) : Math.min((now - created) / .5, 1);
        List<Quad> quads = arcs.stream().filter(arc -> !arc.dead && arc.shown)
                .map(arc -> new Quad(arc.frame, width / 2 + width / 2 * arc.x - arc.size / 2,
                        height / 2 + height / 2 * arc.y - arc.size / 2, arc.size, ending ? .4 : .3)).toList();
        return new Frame(alpha, quads, ending && now - blend > 1);
    }
}
