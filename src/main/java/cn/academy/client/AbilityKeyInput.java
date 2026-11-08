package cn.academy.client;

/** ClientRuntime.KeyState: a canceled hold must be released before it can start again. */
final class AbilityKeyInput {
    enum Action { NONE, DOWN, UP, ABORT }

    private boolean physicalDown;
    private boolean held;

    Action update(boolean down, boolean enabled) {
        Action action = Action.NONE;
        if (!enabled && held) {
            held = false;
            action = Action.ABORT;
        } else if (enabled && down && !held && !physicalDown) {
            held = true;
            action = Action.DOWN;
        } else if (enabled && !down && held) {
            held = false;
            action = Action.UP;
        }
        physicalDown = down;
        return action;
    }

    boolean held() { return held; }

    void reset() {
        physicalDown = held = false;
    }
}
