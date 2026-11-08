package cn.academy.ability.vanilla.electromaster.skill;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.function.Predicate;

/** MagManip/EntityBlock constants and operation order, including original integer/float conversions. */
public final class MagneticManipulationRules {
    public static boolean accepts(BlockState state) {
        return ElectromasterMetalRules.metal(state.getBlock()) && !(state.getBlock() instanceof DoorBlock)
                && !(state.getBlock() instanceof cn.academy.block.ACMultiblockPartBlock)
                && !(state.getBlock() instanceof cn.academy.block.ACMachineBlock machine && switch (machine.kind()) {
                    case MATRIX, SOLAR_GENERATOR, WIND_BASE, WIND_GENERATOR, DEVELOPER_NORMAL, DEVELOPER_ADVANCED -> true;
                    default -> false;
                });
    }
    public static Vec3 grabbedPosition(BlockPos pos) {
        return new Vec3((int)(pos.getX()+.5), (int)(pos.getY()+.5), (int)(pos.getZ()+.5));
    }
    public static Vec3 floatTarget(Vec3 target) { return new Vec3((float)target.x, (float)target.y, (float)target.z); }
    public static Vec3 followVelocity(Vec3 position, Vec3 target) {
        double distanceSquared = position.distanceToSqr(target);
        return target.subtract(position).normalize().scale(.2).scale(distanceSquared < 4 ? distanceSquared / 4 : 1);
    }
    public static BlockPos placement(BlockPos origin, Direction face, Predicate<BlockPos> replaceable) {
        if (replaceable.test(origin)) return origin;
        if (replaceable.test(origin.relative(face))) return origin.relative(face);
        // Original condition is x != 0 && y != 0 && z != 0: only the eight corners, not all 26 neighbors.
        for (int x=-1;x<=1;x++) for (int y=-1;y<=1;y++) for (int z=-1;z<=1;z++)
            if (x!=0 && y!=0 && z!=0 && replaceable.test(origin.offset(x,y,z))) return origin.offset(x,y,z);
        BlockPos current=origin;
        for (int i=0;i<10;i++) {
            if (replaceable.test(current)) return current;
            current=current.relative(face);
        }
        return null;
    }
    private MagneticManipulationRules() {}
}
