package cn.academy.client.render.util;

import java.util.Random;

/** EntityRayBase's millisecond curves and render-frame random walk, with an injectable clock. */
public final class RayAnimation {
    private final long creationTime;
    private long lastFrame;
    private double widthWiggle, glowWiggle;
    private final long life,blendIn,blendOut,shrink;
    private final double maxWidthSpeed,widthRadius;
    public RayAnimation(long now) { this(now,2500,150,1000,800,.8,.3); }
    private RayAnimation(long now,long life,long blendIn,long blendOut,long shrink,double speed,double radius) {
        creationTime=now;this.life=life;this.blendIn=blendIn;this.blendOut=blendOut;this.shrink=shrink;
        maxWidthSpeed=speed;widthRadius=radius;
    }
    public static RayAnimation mining(long now){return new RayAnimation(now,233333L*50,200,400,300,.4,.1);}
    public record Frame(double length, double width, double alpha, double glowAlpha) {}
    public Frame frame(long now, double length, Random random) {
        if (lastFrame != 0) {
            long delta = now - lastFrame;
            widthWiggle = Math.clamp(widthWiggle + delta * (-maxWidthSpeed + random.nextDouble() * maxWidthSpeed*2) / 1000.0, 0, widthRadius);
            glowWiggle = Math.clamp(glowWiggle + delta * (-.4 + random.nextDouble() * .8) / 1000.0, 0, .1);
        }
        lastFrame = now;
        long dt = now - creationTime;
        double alpha = dt > life-blendOut ? 1 - (double) (dt+blendOut-life) / blendOut : 1;
        double width = widthWiggle + (dt > life-shrink ? 1 - (double) (dt+shrink-life) / shrink : 1);
        return new Frame((dt < blendIn ? (double) dt / blendIn : 1) * length, width, alpha,
                (.9 + glowWiggle) * alpha);
    }
}
