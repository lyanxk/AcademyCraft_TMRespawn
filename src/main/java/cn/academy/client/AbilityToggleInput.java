package cn.academy.client;

/** The original V handler toggles on a release within 300 ms; it must not depend on 20 Hz polling. */
final class AbilityToggleInput {
    private boolean held;
    private long pressedAt;

    boolean update(boolean down, boolean allowed, long now) {
        if (!allowed) { held = false; return false; }
        if (down) {
            if (!held) pressedAt = now;
            held = true;
            return false;
        }
        boolean toggle = held && now >= pressedAt && now - pressedAt < 300;
        held = false;
        return toggle;
    }

    boolean held() { return held; }
    void reset() { held = false; }
}
