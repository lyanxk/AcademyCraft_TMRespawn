package cn.academy.client.render.util;

/** RippleMarkRender's three independent 3.6 second cycles, including its byte alpha truncation. */
public final class RippleMarkAnimation {
    public record Ring(float height, float size, int alpha) {}
    private static final double[] OFFSETS = {0, -1.2, -2.4};
    public static Ring ring(double elapsed, int index) {
        double mod = (elapsed - OFFSETS[index]) % 3.6;
        float height = (float) mod * 3e-1f;
        float size = 1.9f + (1.4f - 1.9f) * (float) (mod / 3.6);
        final float in = 1.6f, out = 1.6f;
        float alpha = mod < in ? (float) mod / in
                : mod > 3.6 - out ? (float) (1 - ((float) mod - (3.6 - out)) / out) : 1;
        // Original replaces the mark's alpha (179); it does NOT multiply by 179/255.
        return new Ring(height, size, (int) (alpha * 255));
    }
    private RippleMarkAnimation() {}
}
