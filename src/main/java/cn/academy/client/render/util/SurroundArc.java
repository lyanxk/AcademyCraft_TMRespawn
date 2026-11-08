package cn.academy.client.render.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** EntitySurroundArc's first-update generation, probabilistic replacement, and SubArcHandler tick ordering. */
public final class SurroundArc {
    public static final ArcFactory.Arc[] THIN, NORMAL, BOLD;
    static {
        var factory = new ArcFactory();
        factory.widthShrink = .9; factory.maxOffset = .8; factory.passes = 3; factory.width = .2; factory.branchFactor = .7;
        THIN = factory.generateList(10, 1.5, 2);
        factory.width = .3;
        NORMAL = factory.generateList(10, 3, 4);
        factory.width = .35; factory.maxOffset = 1.2; factory.branchFactor = .45;
        BOLD = factory.generateList(10, 3.5, 4.5);
    }
    public final ArcFactory.Arc[] templates;
    private final ArrayList<SubArc> arcs = new ArrayList<>();
    private final CubePointFactory points;
    private final Random animation, utility, entityRandom;
    private final int count;
    private final int life;
    private int ticks;
    public SurroundArc(boolean item, double width, double height) {
        this(item, width, height, new Random(), new Random(), new Random(), new Random());
    }
    public SurroundArc(boolean item, double width, double height, Random points, Random animation, Random utility, Random entityRandom) {
        this(item ? THIN : NORMAL, item ? 4 : 6, 100000, width, height, points, animation, utility, entityRandom);
    }
    public static SurroundArc bold(double width, double height) {
        return new SurroundArc(BOLD, 5, 100, width, height, new Random(), new Random(), new Random(), new Random());
    }
    public static SurroundArc magneticBlock() {
        return new SurroundArc(THIN,4,233333,1.3,1.3,new Random(),new Random(),new Random(),new Random());
    }
    SurroundArc(ArcFactory.Arc[] templates, int count, int life, double width, double height,
                Random points, Random animation, Random utility, Random entityRandom) {
        this.templates = templates; this.count = count; this.life = life;
        this.points = new CubePointFactory(width, height, width, points).setCentered(true);
        this.animation = animation; this.utility = utility; this.entityRandom = entityRandom;
    }
    public void tick() {
        ticks++;
        if (arcs.isEmpty()) for (int i = 0; i < count; i++) {
            // Original computes and discards a spherical direction before using CubePointFactory.next().
            entityRandom.nextDouble(); entityRandom.nextDouble();
            var arc = new SubArc(points.next(), templates.length, animation, utility);
            arc.frameRate = .6; arc.switchRate = .7; arcs.add(arc);
        }
        // Dead arcs are removed without ticking; all-dead batches regenerate on the NEXT update.
        arcs.removeIf(arc -> arc.dead);
        arcs.forEach(arc -> arc.tick(animation));
    }
    public boolean expired() { return ticks >= life; }
    public List<SubArc> arcs() { return List.copyOf(arcs); }
}
