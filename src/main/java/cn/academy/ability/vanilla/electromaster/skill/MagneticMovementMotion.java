package cn.academy.ability.vanilla.electromaster.skill;

import net.minecraft.world.phys.Vec3;

/** MovementContext's remembered velocity and per-axis .08 approach, including its zero-distance branch. */
public final class MagneticMovementMotion {
    private Vec3 remembered = Vec3.ZERO;
    public static double range(int level,float experience){return level>=3?25+25*experience:25;}
    public static double speed(int level,float experience){return level>=3?1+2*experience:1;}
    public Vec3 step(Vec3 position, Vec3 target, Vec3 actual) {
        return step(position,target,actual,1);
    }
    public Vec3 step(Vec3 position, Vec3 target, Vec3 actual,double speed) {
        if (Math.abs(remembered.lengthSqr() - actual.lengthSqr()) > .5) remembered = actual;
        Vec3 delta = target.subtract(position);
        double mod = Math.sqrt(delta.x * delta.x + delta.y * delta.y + delta.z * delta.z);
        remembered = new Vec3(adjust(remembered.x, delta.x / mod*speed), adjust(remembered.y, delta.y / mod*speed), adjust(remembered.z, delta.z / mod*speed));
        return remembered;
    }
    private static double adjust(double from, double to) {
        double delta = to - from;
        if (Math.abs(delta) < .08) return to;
        return delta > 0 ? from + .08 : from - .08;
    }
    public static float reward(double distance) { return Math.max(.005f, .0011f * (float) distance); }
}
