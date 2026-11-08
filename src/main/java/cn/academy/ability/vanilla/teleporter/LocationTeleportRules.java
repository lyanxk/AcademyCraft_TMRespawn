package cn.academy.ability.vanilla.teleporter;

/** Shared client/server rules from the original LocationTeleport.scala. */
public final class LocationTeleportRules {
    public static final int NAME_LENGTH = 16;

    public static boolean canCrossDimension(float experience) {
        return experience > .8f;
    }

    public static float cpCost(float experience, double distance, boolean crossDimension) {
        float legacyDistance = (float) distance;
        return (200 - experience * 50) * (crossDimension ? 2 : 1)
                * Math.max(8f, (float) Math.sqrt(Math.min(800, legacyDistance)));
    }

    /** Called after granting this teleport's experience, as in LocationTeleport.perform. */
    public static int cooldown(float experience) {
        return (int) (30 - experience * 10);
    }

    private LocationTeleportRules() {}
}
