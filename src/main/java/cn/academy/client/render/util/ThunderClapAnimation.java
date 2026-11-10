package cn.academy.client.render.util;

/** ThunderClapContextC's client tick counter and EntityX's delayed surround removal. */
public final class ThunderClapAnimation {
    private int ticks, tail = -1;
    public void tick() { if (active()) ticks++; else if (tail > 0) tail--; }
    public boolean active() { return tail < 0; }
    public void terminate() { if (active()) tail = 10; }
    public boolean expired() { return tail == 0; }
    public float walkSpeed() { return active() ? Math.max(.001f, .1f - (.1f - .001f) / 60 * ticks) : .1f; }
}
