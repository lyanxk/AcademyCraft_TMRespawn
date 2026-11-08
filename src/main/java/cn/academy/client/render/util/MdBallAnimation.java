package cn.academy.client.render.util;

import net.minecraft.util.Mth;
import java.util.function.DoubleSupplier;
import java.util.function.IntUnaryOperator;

/** EntityMdBall's render-time animation, including the original seconds/milliseconds size curve. */
public final class MdBallAnimation {
    private final double spawned;
    private double lastTime, acceleration;
    private float wiggle = .8f;
    private int texture;

    public MdBallAnimation(double spawned) { this.spawned = spawned; }

    public record Frame(int texture, double x, double y, double z, float size, int glowAlpha, int coreAlpha) {}

    public Frame frame(double time, int life, IntUnaryOperator entityRandom, DoubleSupplier globalRandom) {
        if (lastTime != 0) {
            double dt = time - lastTime;
            if (entityRandom.applyAsInt(8) < 3) acceleration = -4 + globalRandom.getAsDouble() * 8;
            wiggle += acceleration * dt;
            if (wiggle > 1) wiggle = 1;
            if (wiggle < 0) wiggle = 0;
        }
        lastTime = time;
        if (entityRandom.applyAsInt(8) < 2) texture = entityRandom.applyAsInt(5);
        double age = time - spawned;
        float phase = (float) (age / .3f);
        float alpha = alpha(age, life);
        return new Frame(texture, .03 * Mth.sin(phase),
                .04 * Mth.cos((float) (phase * 1.4 + Math.PI / 3.5)), .03 * Mth.cos(phase),
                size(age, life), (int) (alpha * (.3f + wiggle * .7f) * 255),
                (int) (alpha * (.8f + .2f * wiggle) * 255));
    }

    static float alpha(double age, int life) {
        float lifeS = life * .05f, dt = (float) age;
        if (dt > lifeS - .15f) return Math.max(0, lerp(1, 0, (dt - (lifeS - .15f)) / .15f));
        if (dt > lifeS - .4) return lerp(.6f, 1, (float) ((dt - (lifeS - .4)) / (.4 - .15f)));
        if (dt < .3f) return lerp(0, .6f, dt / .3f);
        return .6f;
    }

    static float size(double age, int life) {
        int lifeMS = life * 50;
        // This unit mismatch exists in EntityMdBall.getSize; do not replace it with tick-based scaling.
        if (age > lifeMS - 100) return Math.max(0, lerp(1.5f, 0, (float) (age - (lifeMS - 100)) / 100));
        if (age > lifeMS - 300) return lerp(1, 1.5f, (float) (age - (lifeMS - 300)) / 200);
        return 1;
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}
