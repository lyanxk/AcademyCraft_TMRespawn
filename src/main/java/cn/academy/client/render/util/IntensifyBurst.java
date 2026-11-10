package cn.academy.client.render.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.world.phys.Vec3;

/** EntityIntensifyEffect callbacks, including EntityX's first-update catch-up of ticks 0 and 1. */
public final class IntensifyBurst {
    private static final int[] TIMES = {0, 1, 3, 4, 6, 7, 8};
    private static final double[] HEIGHTS = {2, 1.8, 1.5, 1, .5, 0, -.1};
    private final Random utility, animation;
    private final ArrayList<SubArc> arcs = new ArrayList<>();
    private int tick, next;
    public IntensifyBurst() { this(new Random(), new Random()); }
    public IntensifyBurst(Random utility, Random animation) { this.utility = utility; this.animation = animation; }
    public void tick() {
        ++tick;
        while (next < TIMES.length && TIMES[next] <= tick) {
            int count = 3 + utility.nextInt(1); // RandUtils.rangei(3, 4) is upper-exclusive.
            for (int i = 0; i < count; i++) {
                double radius = .5 + utility.nextDouble() * .1, angle = utility.nextDouble() * Math.PI * 2;
                var arc = new SubArc(new Vec3(radius * Math.sin(angle), HEIGHTS[next], radius * Math.cos(angle)),
                        10, animation, utility);
                arc.life = 3; arc.frameRate = .6; arc.switchRate = .7; arcs.add(arc);
            }
            next++;
        }
        arcs.removeIf(arc -> arc.dead);
        arcs.forEach(arc -> arc.tick(animation));
    }
    public List<SubArc> arcs() { return List.copyOf(arcs); }
    public boolean expired() { return tick >= 15; }
}
