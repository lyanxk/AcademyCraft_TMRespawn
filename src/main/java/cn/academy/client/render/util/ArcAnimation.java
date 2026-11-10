package cn.academy.client.render.util;

import java.util.Random;

/** EntityArc's independent pattern selection and visibility toggles; no invented alpha fade. */
public final class ArcAnimation {
    private final Random random;
    private final double textureWiggle, showWiggle, hideWiggle;
    private final int patternCount, life;
    private int ticks, pattern;
    private boolean shown = true;
    public ArcAnimation(Random random, int patternCount, int life, boolean weak) {
        this(random, patternCount, life, weak ? .7 : .5, weak ? .1 : .2, weak ? .4 : .2);
    }
    public ArcAnimation(Random random, int patternCount, int life, double textureWiggle, double showWiggle, double hideWiggle) {
        this.random = random; this.patternCount = patternCount; this.life = life;
        this.textureWiggle = textureWiggle; this.showWiggle = showWiggle; this.hideWiggle = hideWiggle;
    }
    public void tick() {
        ticks++;
        if (random.nextDouble() < textureWiggle) pattern = random.nextInt(patternCount);
        if (shown && random.nextDouble() < showWiggle) shown = false;
        else if (!shown && random.nextDouble() < hideWiggle) shown = true;
    }
    public int pattern() { return pattern; }
    public boolean shown() { return shown; }
    public boolean expired() { return ticks >= life; }
}
