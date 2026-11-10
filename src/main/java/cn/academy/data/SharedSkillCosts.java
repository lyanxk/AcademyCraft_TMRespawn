package cn.academy.data;

/** Server calculations and local prediction use identical datapack parameters. */
public final class SharedSkillCosts {
    private static float curve(String profile, String start, String end, float experience) {
        float a = Balance.f(profile, start), b = Balance.f(profile, end); return a + (b - a) * experience;
    }
    public static float plasmaCharge(float exp) {
        return Balance.f("plasma_cannon_context", "tick_consume_raw_1")
                + (Balance.f("plasma_cannon_context", "tick_consume_raw_2") - Balance.f("plasma_cannon_context", "tick_consume_raw_3")) * exp;
    }
    public static float wingCp(float exp) {
        return Balance.f("storm_wing_context", "tick_consume_raw_1")
                + (Balance.f("storm_wing_context", "tick_consume_raw_2") - Balance.f("storm_wing_context", "tick_consume_raw_3")) * exp;
    }
    public static float wingOverload(float exp) {
        return Balance.f("storm_wing_context", "tick_consume_raw_4")
                + (Balance.f("storm_wing_context", "tick_consume_raw_5") - Balance.f("storm_wing_context", "tick_consume_raw_6")) * exp;
    }
    public static float accelerationCp(boolean enhanced, float exp) {
        return enhanced ? Balance.f("vector_impulse_context", "perform_consume_raw_1")
                : curve("vector_impulse_context", "perform_snapshot_1", "perform_snapshot_2", exp);
    }
    public static float accelerationOverload(boolean enhanced, float exp) {
        return enhanced ? Balance.f("vector_impulse_context", "perform_consume_raw_2")
                : curve("vector_impulse_context", "perform_live_1", "perform_live_2", exp);
    }
    public static float defenseCp(boolean reflection, float exp) {
        return curve("vector_defense_context", reflection ? "tick_live_3" : "tick_live_7", reflection ? "tick_live_4" : "tick_live_8", exp);
    }
    public static float defenseOverload(boolean reflection, float exp) {
        return curve("vector_defense_context", reflection ? "tick_live_5" : "tick_live_9", reflection ? "tick_live_6" : "tick_live_10", exp)
                / (reflection ? Balance.f("vector_defense_context", "tick_consume_raw_1") : 1);
    }
    public static float groundCp(float exp) { return curve("vector_strike_context", "release_snapshot_3", "release_snapshot_4", exp); }
    public static float groundOverload(float exp) { return curve("vector_strike_context", "release_snapshot_5", "release_snapshot_6", exp); }
    private SharedSkillCosts() { }
}
