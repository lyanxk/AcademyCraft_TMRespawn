package cn.academy.ability.vanilla.electromaster.skill;

/** Charge-dependent buffs with deterministic proficiency-scaled duration. */
public final class BodyIntensifyRules {
    public record Buffs(int amplifier, int duration, int hungerDuration) {}

    public static float tickConsumption(int tick, float initialExperience) {
        return tick <= 40 ? 20 + initialExperience * (15 - 20) : 0;
    }
    public static boolean canRelease(int tick) { return tick >= 10 && tick < 100; }
    public static int cooldown(float experienceAfterReward) { return (int) (900 + experienceAfterReward * (600 - 900)); }

    public static Buffs calculate(int heldTicks, float experience) {
        if (!canRelease(heldTicks)) throw new IllegalArgumentException("Body Intensify context is not releasable");
        int charged = Math.min(40, heldTicks);
        double probability = (charged - 10.0) / 18.0;
        int amplifier = (int) Math.floor(probability);
        float chargeProgress=(charged-10)/30f;
        int duration=Math.round(200*(1+2*experience)*(1+chargeProgress));
        return new Buffs(Math.min(amplifier, 1), duration, (int) (1.25f * charged));
    }
    private BodyIntensifyRules() {}
}
