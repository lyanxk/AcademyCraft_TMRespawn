package cn.academy.ability.vanilla.vecmanip.skill;

/** Vertical launches calibrated for ordinary living-entity gravity and air drag. */
public final class GroundShockLaunch {
    private static final double[] SPEEDS = new double[5];

    static {
        for (int tier = 0; tier < SPEEDS.length; tier++) {
            double height = 3 + 2 * tier + .01; // Small margin against floating-point rounding.
            double low = 0, high = 3;
            for (int iteration = 0; iteration < 40; iteration++) {
                double speed = (low + high) / 2;
                double rise = 0;
                for (double velocity = speed; velocity > 0; velocity = (velocity - .08) * .98)
                    rise += velocity;
                if (rise < height) low = speed;
                else high = speed;
            }
            SPEEDS[tier] = high;
        }
    }

    public static double speed(float experience) {
        int tier = (int) (Math.clamp(experience, 0f, 1f) * 4);
        return SPEEDS[tier];
    }

    private GroundShockLaunch() {}
}
