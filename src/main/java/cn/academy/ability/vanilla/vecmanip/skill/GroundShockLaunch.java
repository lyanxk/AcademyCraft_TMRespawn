package cn.academy.ability.vanilla.vecmanip.skill;

import cn.academy.data.GameplayData;

import cn.academy.data.Balance;

/** Vertical launches calibrated for ordinary living-entity gravity and air drag. */
public final class GroundShockLaunch {
    public static double speed(float experience) {
        double[] speeds = GameplayData.current().cached("ground_shock_launch", () -> {
            var heights = Balance.array("ground_shock_launch", "heights");
            double[] result = new double[heights.length];
            for (int tier = 0; tier < result.length; tier++) {
                double low = 0, high = Balance.d("ground_shock_launch", "maximum_velocity");
                for (int iteration = 0; iteration < 40; iteration++) {
                    double velocity = (low + high) / 2, rise = 0;
                    for (double current = velocity; current > 0; current = (current - Balance.d("ground_shock_launch", "gravity")) * Balance.d("ground_shock_launch", "air_drag")) rise += current;
                    if (rise < heights[tier]) low = velocity; else high = velocity;
                }
                result[tier] = high;
            }
            return result;
        });
        return speeds[(int) (Math.clamp(experience, 0f, 1f) * (speeds.length - 1))];
    }

    private GroundShockLaunch() {}
}
