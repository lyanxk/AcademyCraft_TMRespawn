package cn.academy.ability.vanilla.electromaster.skill;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** LambdaLib2 Raytrace semantics used by ArcGen and ThunderBolt (including entity-position hitVec). */
public final class ElectricAttackTrace {
    /** Raytrace.traceLiving(..., EntitySelectors.nothing()): entities cannot intercept this ray. */
    public static BlockHitResult traceBlocks(Entity player, double range) {
        Vec3 start = player.getEyePosition(), end = start.add(player.getLookAngle().scale(range));
        BlockHitResult hit = blocks(player, start, end, false);
        return hit == null ? BlockHitResult.miss(end, Direction.UP, new BlockPos((int) end.x, (int) end.y, (int) end.z)) : hit;
    }
    public static HitResult trace(Entity player, double range, boolean water) {
        Vec3 start = player.getEyePosition(), end = start.add(player.getLookAngle().scale(range));
        return perform(player, start, end, entity -> entity != player, water, null);
    }

    /** Original custom block selector replaces filNormal, so rejected walls do not occlude accepted blocks. */
    public static BlockHitResult traceBlocksFiltered(Entity player, double range,
            java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> filter) {
        Vec3 start = player.getEyePosition(), end = start.add(player.getLookAngle().scale(range));
        var hit = blocks(player, start, end, false, filter);
        return hit == null ? BlockHitResult.miss(end, Direction.UP, BlockPos.containing(end)) : hit;
    }

    public static HitResult traceSegment(Entity source, Vec3 start, Vec3 end, java.util.function.Predicate<Entity> selector) {
        return perform(source, start, end, selector, false, null);
    }
    public static HitResult traceSegmentFiltered(Entity source,Vec3 start,Vec3 end,java.util.function.Predicate<Entity> selector,
            java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> blocks) {
        return perform(source,start,end,selector,false,blocks);
    }
    public static BlockHitResult traceBlockSegment(Entity source,Vec3 start,Vec3 end,
            java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> filter) {
        var hit=blocks(source,start,end,false,filter);
        return hit==null?BlockHitResult.miss(end,Direction.UP,BlockPos.containing(end)):hit;
    }

    private static HitResult perform(Entity player, Vec3 start, Vec3 end, java.util.function.Predicate<Entity> selector,
            boolean water, java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> filter) {
        Entity nearest = null;
        double distance = 0;
        for (Entity entity : player.level().getEntities((Entity) null,
                new AABB(start, end).expandTowards(1, 1, 1), entity -> entity.isPickable() && selector.test(entity))) {
            Vec3 intersection = intersect(entity.getBoundingBox().expandTowards((double) .3f, (double) .3f, (double) .3f), start, end);
            if (intersection != null) {
                double candidate = start.distanceTo(intersection);
                if (candidate < distance || distance == 0) { nearest = entity; distance = candidate; }
            }
        }
        BlockHitResult block = blocks(player, start, end, water, filter);
        // RayTraceResult(Entity) stores the entity's feet, NOT the box intersection just used to select it.
        if (nearest != null && (block == null || start.distanceTo(nearest.position()) <= start.distanceTo(block.getLocation())))
            return new EntityHitResult(nearest, nearest.position());
        return block == null ? BlockHitResult.miss(end, Direction.UP,
                new BlockPos((int) end.x, (int) end.y, (int) end.z)) : block;
    }

    private static BlockHitResult blocks(Entity player, Vec3 start, Vec3 end, boolean water) {
        return blocks(player, start, end, water, null);
    }
    private static BlockHitResult blocks(Entity player, Vec3 start, Vec3 end, boolean water,
            java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> filter) {
        Vec3 current = start;
        int x = Mth.floor(current.x), y = Mth.floor(current.y), z = Mth.floor(current.z);
        int endX = Mth.floor(end.x), endY = Mth.floor(end.y), endZ = Mth.floor(end.z);
        for (int step = 0; step <= 200; step++) {
            BlockPos pos = new BlockPos(x, y, z);
            var state = player.level().getBlockState(pos);
            if (water && state.is(Blocks.WATER)) {
                // BlockLiquid's old selection box is a full cube, not the modern fluid surface height.
                Vec3 point = intersect(new AABB(pos), current, end);
                if (point != null) return new BlockHitResult(point, Direction.UP, pos, false);
            } else if (filter != null ? filter.test(state) : !state.getCollisionShape(player.level(), pos).isEmpty()) {
                var shape = state.getShape(player.level(), pos, CollisionContext.of(player));
                // Legacy filNothing includes liquids, whose old selection bounding box filled a block.
                if(filter!=null && state.liquid())shape=net.minecraft.world.phys.shapes.Shapes.block();
                // Modern VoxelShape.clip probes start + delta*.001 and returns that INSIDE point.
                // Original Block.rayTrace instead intersects each local box, including an exact t=0 face.
                Vec3 localStart = current.subtract(pos.getX(), pos.getY(), pos.getZ());
                Vec3 localEnd = end.subtract(pos.getX(), pos.getY(), pos.getZ());
                BoxHit best = null;
                for (var box : shape.toAabbs()) {
                    BoxHit hit = intersectFace(box, localStart, localEnd);
                    if (hit != null && (best == null || localStart.distanceToSqr(hit.point) < localStart.distanceToSqr(best.point))) best = hit;
                }
                if (best != null) return new BlockHitResult(best.point.add(pos.getX(), pos.getY(), pos.getZ()), best.face, pos, false);
            }
            if (step == 200 || x == endX && y == endY && z == endZ) break;
            boolean moveX = endX != x, moveY = endY != y, moveZ = endZ != z;
            double nextX = endX > x ? x + 1 : x, nextY = endY > y ? y + 1 : y, nextZ = endZ > z ? z + 1 : z;
            double dx = end.x - current.x, dy = end.y - current.y, dz = end.z - current.z;
            double fx = moveX ? (nextX - current.x) / dx : 999;
            double fy = moveY ? (nextY - current.y) / dy : 999;
            double fz = moveZ ? (nextZ - current.z) / dz : 999;
            int side;
            if (fx < fy && fx < fz) {
                side = endX > x ? 4 : 5;
                current = new Vec3(nextX, current.y + dy * fx, current.z + dz * fx);
            } else if (fy < fz) {
                side = endY > y ? 0 : 1;
                current = new Vec3(current.x + dx * fy, nextY, current.z + dz * fy);
            } else {
                side = endZ > z ? 2 : 3;
                current = new Vec3(current.x + dx * fz, current.y + dy * fz, nextZ);
            }
            x = Mth.floor(current.x) - (side == 5 ? 1 : 0);
            y = Mth.floor(current.y) - (side == 1 ? 1 : 0);
            z = Mth.floor(current.z) - (side == 3 ? 1 : 0);
        }
        return null;
    }

    /** Old AxisAlignedBB.calculateIntercept also selects an exit face when starting inside. */
    static Vec3 intersect(AABB box, Vec3 start, Vec3 end) {
        var hit = intersectFace(box, start, end);
        return hit == null ? null : hit.point;
    }
    private record BoxHit(Vec3 point, Direction face) {}
    private static BoxHit intersectFace(AABB box, Vec3 start, Vec3 end) {
        BoxHit best = null;
        double[] low = {box.minX, box.minY, box.minZ}, high = {box.maxX, box.maxY, box.maxZ};
        Direction[] lowFaces = {Direction.WEST, Direction.DOWN, Direction.NORTH};
        Direction[] highFaces = {Direction.EAST, Direction.UP, Direction.SOUTH};
        double[] origin = {start.x, start.y, start.z}, delta = {end.x - start.x, end.y - start.y, end.z - start.z};
        for (int axis = 0; axis < 3; axis++) {
            if (delta[axis] * delta[axis] < 1.0e-7) continue;
            for (int side = 0; side < 2; side++) {
                double plane = side == 0 ? low[axis] : high[axis];
                double t = (plane - origin[axis]) / delta[axis];
                if (t < 0 || t > 1) continue;
                Vec3 point = start.add(delta[0] * t, delta[1] * t, delta[2] * t);
                double[] coordinates = {point.x, point.y, point.z};
                boolean inside = true;
                for (int other = 0; other < 3; other++)
                    if (other != axis && (coordinates[other] < low[other] || coordinates[other] > high[other])) inside = false;
                if (inside && (best == null || start.distanceToSqr(point) < start.distanceToSqr(best.point)))
                    best = new BoxHit(point, side == 0 ? lowFaces[axis] : highFaces[axis]);
            }
        }
        return best;
    }

    private ElectricAttackTrace() {}
}
