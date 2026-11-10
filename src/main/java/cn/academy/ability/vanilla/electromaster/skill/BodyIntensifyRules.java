package cn.academy.ability.vanilla.electromaster.skill;

import cn.academy.data.Balance;

/** Charge-dependent buffs with deterministic proficiency-scaled duration. */
public final class BodyIntensifyRules {
    public record Buffs(int amplifier, int duration, int hungerDuration) {}

    public static float tickConsumption(int tick, float initialExperience) {
        return tick <= Balance.i("body_intensify_rules", "tick_consumption_value_1") ? Balance.i("body_intensify_rules", "tick_consumption_value_2") + initialExperience * (Balance.i("body_intensify_rules", "tick_consumption_value_3") - Balance.i("body_intensify_rules", "tick_consumption_value_4")) : 0;
    }
    public static boolean canRelease(int tick) { return tick >= Balance.i("body_intensify_rules", "can_release_value_1") && tick < Balance.i("body_intensify_rules", "can_release_value_2"); }
    public static int cooldown(float experienceAfterReward) { return (int) (Balance.i("body_intensify_rules", "cooldown_value_1") + experienceAfterReward * (Balance.i("body_intensify_rules", "cooldown_value_2") - Balance.i("body_intensify_rules", "cooldown_value_3"))); }

    public static Buffs calculate(int heldTicks, float experience) {
        if (!canRelease(heldTicks)) throw new IllegalArgumentException("Body Intensify context is not releasable");
        int charged = Math.min(Balance.i("body_intensify_rules", "calculate_charged_1"), heldTicks);
        double probability = (charged - Balance.d("body_intensify_rules", "calculate_probability_1")) / Balance.d("body_intensify_rules", "calculate_probability_2");
        int amplifier = (int) Math.floor(probability);
        float chargeProgress=(charged-Balance.i("body_intensify_rules", "calculate_charge_progress_1"))/Balance.f("body_intensify_rules", "calculate_charge_progress_2");
        int duration=Math.round(Balance.i("body_intensify_rules", "calculate_duration_1")*(1+Balance.i("body_intensify_rules", "calculate_duration_2")*experience)*(1+chargeProgress));
        return new Buffs(Math.min(amplifier, 1), duration, (int) (1.25f * charged));
    }
    private BodyIntensifyRules() {}
}
