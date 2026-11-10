package cn.academy.client.render;

/** CPBar's wall-time animation rules, with a pause-aware clock and no rendering dependency. */
final class CPBarAnimation {
    private long rawTime = -1, time, lastDrawTime, showTime;
    private long presetTime = -10000, previousPresetTime = -10000, valueChange = -1;
    private boolean lastActive, numbersHeld, everActive;
    private float cp, overload;

    record Frame(float alpha, float cp, float overload, float numbersAlpha, float presetAlpha, boolean showPreset) { }

    long updateClock(long raw, boolean paused) {
        if (rawTime >= 0 && !paused) time += Math.max(0, raw - rawTime);
        rawTime = raw;
        return time;
    }

    void numbers(boolean held) {
        if (held == numbersHeld) return;
        numbersHeld = held;
        if (held) valueChange = time;
        else valueChange = time - valueChange > 400 ? time : -1;
    }

    void switchPreset() {
        previousPresetTime = presetTime;
        presetTime = time;
    }

    Frame frame(boolean active, boolean overloaded, float cpRatio, float overloadRatio) {
        if (!lastActive && active) { showTime = time; everActive = true; }
        float step = Math.min(100, Math.max(0, time - lastDrawTime)) * .002f;
        float alpha = !everActive ? 0 : time - showTime < 200 ? (time - showTime) / 200f
                : active ? 1 : Math.max(0, 1 - (time - lastDrawTime) / 200f);
        cp = balance(cp, alpha > 0 ? cpRatio : 0, step);
        overload = balance(overload, alpha > 0 ? overloadRatio : 0, step);
        long dt = valueChange < 0 ? Long.MAX_VALUE : time - valueChange;
        float numbersAlpha = overloaded ? 0 : numbersHeld ? Math.clamp((dt - 200) / 400f, 0, 1)
                : dt < 300 ? 1 - dt / 300f : 0;
        long sincePreset = time - presetTime;
        float presetProgress = sincePreset / 2000f;
        float presetAlpha = time - previousPresetTime > 3000 && presetProgress < .2f ? presetProgress / .2f
                : presetProgress > .8f ? (1 - presetProgress) / .2f : 1;
        boolean showPreset = sincePreset >= 0 && sincePreset < 2000;
        if (active) lastDrawTime = time;
        lastActive = active;
        return new Frame(alpha, cp, overload, numbersAlpha, Math.max(0, presetAlpha) * .75f, showPreset);
    }

    private static float balance(float from, float to, float max) {
        return from + Math.signum(to - from) * Math.min(max, Math.abs(to - from));
    }
}
