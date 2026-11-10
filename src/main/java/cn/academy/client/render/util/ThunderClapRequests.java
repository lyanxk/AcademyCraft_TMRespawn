package cn.academy.client.render.util;

/** The local context can terminate while its server link is still being established. */
public final class ThunderClapRequests {
    private final int[] held = new int[cn.academy.ability.AbilityState.SKILL_CHANNELS];
    private int next;
    public int begin(int slot) {
        if (++next <= 0) next = 1;
        return held[slot] = next;
    }
    public void end(int slot) {
        if (slot < 0) java.util.Arrays.fill(held, 0);
        else if (slot < held.length) held[slot] = 0;
    }
    public boolean accepts(int slot, int request) {
        return request == 0 || slot >= 0 && slot < held.length && held[slot] == request;
    }
}
