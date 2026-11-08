package cn.academy.client.render;

import java.util.Random;

/** Original CPBar keyframes and LambdaLib2 CubicCurve interpolation (MIT; see META-INF/LICENSE-LambdaLib2.txt). */
final class CPBarInterference {
    private final int[] time = new int[61];
    private final double[] alpha = new double[61], dx = new double[60], dy = new double[60];
    record Offset(float x, float y, float alpha) { }

    CPBarInterference(Random random) {
        alpha[0] = .2 + random.nextDouble() * .6;
        for (int i = 0; i < 60; i++) {
            int duration = 80 + random.nextInt(320);
            float norm = random.nextFloat(), angle = random.nextFloat() * (float) (Math.PI * 2);
            norm = norm * norm * norm;
            time[i + 1] = time[i] + duration;
            dx[i] = Math.sin(angle) * norm * 9 * (964f / 147f);
            dy[i] = Math.cos(angle) * norm * 9;
            alpha[i + 1] = .4 + random.nextDouble() * .3;
        }
    }

    Offset at(long absoluteMilliseconds) {
        int t = (int) Math.floorMod(absoluteMilliseconds, time[60]);
        int frame = 0;
        while (time[frame + 1] <= t) frame++;
        return new Offset((float) dx[frame], (float) dy[frame], (float) curve(t / 10 * 10));
    }

    private double slope(int i) {
        if (i == 0) return (alpha[1] - alpha[0]) / time[1];
        if (i == 60) return (alpha[60] - alpha[59]) / (time[60] - time[59]);
        return .5 * ((alpha[i + 1] - alpha[i]) / (time[i + 1] - time[i])
                + (alpha[i] - alpha[i - 1]) / (time[i] - time[i - 1]));
    }

    private double curve(int x) {
        if (x == 0) return alpha[0];
        int i = 1;
        while (time[i] < x) i++;
        double length = time[i] - time[i - 1], t = (x - time[i - 1]) / length;
        double y0 = alpha[i - 1], y1 = alpha[i], m0 = slope(i - 1) * length, m1 = slope(i) * length;
        return t * t * t * (m0 + m1 + 2 * y0 - 2 * y1)
                + t * t * (-2 * m0 - m1 - 3 * y0 + 3 * y1) + t * m0 + y0;
    }
}
