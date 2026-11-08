package cn.academy.ability.vanilla.electromaster.skill;

import java.util.function.DoubleSupplier;

/** Literal IntensifyContext timing and reward arithmetic from BodyIntensify.scala. */
public final class BodyIntensifyRules {
    public record Buffs(int count, int amplifier, int duration, int hungerDuration) {}

    public static float tickConsumption(int tick, float initialExperience) {
        return tick <= 40 ? 20 + initialExperience * (15 - 20) : 0;
    }
    public static boolean canRelease(int tick) { return tick >= 10 && tick < 100; }
    public static int cooldown(float experienceAfterReward) { return (int) (900 + experienceAfterReward * (600 - 900)); }

    public static Buffs roll(int heldTicks, float experience, DoubleSupplier random) {
        if (!canRelease(heldTicks)) throw new IllegalArgumentException("Body Intensify context is not releasable");
        int charged = Math.min(40, heldTicks);
        double probability = (charged - 10.0) / 18.0;
        int amplifier = (int) Math.floor(probability);
        int duration = (int) ((1 + random.getAsDouble()) * charged * (1.5 + (double) experience));
        int count = 0;
        for (double p = probability; p > 0; p -= 1.0) if (random.getAsDouble() < p) count++;
        // Original Random.shuffle(effects) discards its result; pre-increment i selects
        // JUMP (index 1), then REGENERATION (index 2), never speed/strength/resistance.
        return new Buffs(count, Math.min(amplifier, 1), duration, (int) (1.25f * charged));
    }
    private BodyIntensifyRules() {}
}
