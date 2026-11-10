package cn.academy.ability.vanilla.teleporter;

import cn.academy.data.Balance;

/** Shared client/server rules from the original LocationTeleport.scala. */
public final class LocationTeleportRules {
    public static final int NAME_LENGTH = 16;

    public static boolean canCrossDimension(float experience) {
        return experience > Balance.f("location_teleport_rules", "can_cross_dimension_threshold_1");
    }

    public static float cpCost(float experience, double distance, boolean crossDimension) {
        float legacyDistance = (float) distance;
        return (Balance.i("location_teleport_rules", "cp_cost_value_1") - experience * Balance.i("location_teleport_rules", "cp_cost_value_2")) * (crossDimension ? Balance.i("location_teleport_rules", "cp_cost_value_3") : 1)
                * Math.max(Balance.f("location_teleport_rules", "cp_cost_value_4"), (float) Math.sqrt(Math.min(Balance.i("location_teleport_rules", "cp_cost_value_5"), legacyDistance)));
    }

    /** Called after granting this teleport's experience, as in LocationTeleport.perform. */
    public static int cooldown(float experience) {
        return (int) (Balance.i("location_teleport_rules", "cooldown_value_1") - experience * Balance.i("location_teleport_rules", "cooldown_value_2"));
    }

    private LocationTeleportRules() {}
}
