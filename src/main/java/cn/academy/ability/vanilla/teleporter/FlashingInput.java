package cn.academy.ability.vanilla.teleporter;

/** Original Flashing.localStart/localEnd semantics; IDs here follow the modern W/S/A/D packet. */
public final class FlashingInput {
    private int held;
    private int active;

    public int update(int bits) {
        bits &= 15;
        int released = 0;
        // ClientRuntime iterates a TreeMap keyed by LWJGL2 key code, not registration order.
        // The standard codes W=17, A=30, S=31, D=32 yield W/A/S/D.
        for (int direction : new int[]{1, 3, 2, 4}) {
            int mask = 1 << (direction - 1);
            if ((bits & mask) != 0 && (held & mask) == 0) active = direction;
            else if ((bits & mask) == 0 && (held & mask) != 0 && active == direction) {
                released = direction; active = 0;
            }
        }
        held = bits;
        return released;
    }

    public int activeDirection() { return active; }
    public void cancelActive() { active = 0; }

    /** Cancel the delegate but keep realState, including presses/releases while controls are blocked. */
    public void suspend(int bits) { held = bits & 15; active = 0; }

    public void abort() { held = active = 0; }
}
