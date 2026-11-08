package cn.academy.client.render;

/** NotifyUI's six-second timeline, measured in pause-aware seconds like GameTimer. */
final class NotificationAnimation {
    private long rawTime = -1, time, received = -1;
    record Frame(boolean visible, float backAlpha, float iconX, float iconAlpha, float textAlpha) {}

    void clock(long raw, boolean paused) {
        if (rawTime >= 0 && !paused) time += Math.max(0, raw - rawTime);
        rawTime = raw;
    }
    void show() { received = time; }
    Frame frame() {
        double dt = (time - received) / 1000.0;
        if (received < 0 || dt >= 6) return new Frame(false, 0, 34, 0, 0);
        if (dt < .5) {
            // Preserve upstream's mixed-unit fade: dt is seconds but divisors are 300/200.
            return new Frame(true, (float) Math.min(dt / 300, 1), 420,
                    (float) Math.clamp((dt - 200) / 300, 0, 1), -1);
        }
        if (dt < 1) {
            float scan = net.minecraft.util.Mth.sin((float) ((dt - .5) / .5) * (float) Math.PI / 2);
            return new Frame(true, 1, 420 + (34 - 420) * scan, 1, Math.max(.1f, scan));
        }
        float alpha = dt < 5.7 ? 1 : 1 - (float) ((dt - 5.7) / .3);
        return new Frame(true, alpha, 34, alpha, Math.max(.1f, alpha));
    }
}
