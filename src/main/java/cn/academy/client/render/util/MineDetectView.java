package cn.academy.client.render.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** HandlerEntity/WorldUtils iteration and HandlerRender's byte colors; no rounded scan center or artificial fade. */
public final class MineDetectView {
    public record Ore(BlockPos position, int level) {}
    public final double range;
    private final boolean advanced;
    private Vec3 position;
    private int ticks;
    private List<Ore> ores = List.of();
    public MineDetectView(Vec3 initial, double range, boolean advanced) {
        position = initial; this.range = range; this.advanced = advanced;
    }
    public void tick(Vec3 target, Predicate<BlockPos> isOre, ToIntFunction<BlockPos> harvestLevel) {
        ticks++;
        if (ticks == 1) update(isOre, harvestLevel); // onFirstUpdate precedes following the target.
        position = target;
        if (ticks % 5 == 0) update(isOre, harvestLevel);
    }
    private void update(Predicate<BlockPos> isOre, ToIntFunction<BlockPos> harvestLevel) {
        var found = new ArrayList<Ore>();
        for (BlockPos p : scan(position, range, Integer.MAX_VALUE, isOre))
            found.add(new Ore(p, Math.min(3, harvestLevel.applyAsInt(p) + 1)));
        ores = List.copyOf(found);
    }
    public boolean expired() { return ticks > 100; }
    public boolean advanced() { return advanced; }
    public List<Ore> ores() { return ores; }
    public Vec3 position() { return position; }
    public static List<BlockPos> scan(Vec3 center, double range, int limit, Predicate<BlockPos> accepts) {
        var result = new ArrayList<BlockPos>();
        int minX = (int) Math.floor(center.x - range), minY = (int) Math.floor(center.y - range), minZ = (int) Math.floor(center.z - range);
        int maxX = (int) Math.ceil(center.x + range), maxY = (int) Math.ceil(center.y + range), maxZ = (int) Math.ceil(center.z + range);
        var p = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
            if (center.distanceToSqr(x, y, z) > range * range) continue;
            p.set(x, y, z);
            if (accepts.test(p)) {
                result.add(p.immutable()); if (result.size() == limit) return result;
            }
        }
        return result;
    }
    public static int alpha(Vec3 player, BlockPos block, double range) {
        double judge = 1 - player.distanceTo(new Vec3(block.getX(), block.getY(), block.getZ())) / range * 2.2;
        // Colors.f2i followed by LWJGL Color.setAlpha(int) casts to a byte, including NEGATIVE values.
        return ((int) ((.3f + (float) (judge * .7)) * 255)) & 255;
    }
    public static int color(int level) {
        return switch (Math.min(4, level)) {
            case 0 -> 0x73C8E3; case 1 -> 0xA1B5BC; case 2 -> 0x57E7F8; case 3 -> 0x61CC5E; default -> 0xEB6D54;
        };
    }
    public record Vertex(double x, double y, double z, float u, float v) {}
    private static final List<Vertex> BOX = createBox();
    public static List<Vertex> box() { return BOX; }
    private static List<Vertex> createBox() {
        double a = .05, b = .05 + .9;
        double[][] points = {{a,a,a},{b,a,a},{b,a,b},{a,a,b},{a,b,a},{b,b,a},{b,b,b},{a,b,b}};
        int[][] faces = {{0,1,2,3},{4,5,6,7},{5,1,2,6},{6,2,3,7},{7,3,0,4},{4,0,1,5}};
        float[][] uv = {{0,0},{1,0},{1,1},{0,1}};
        var result = new ArrayList<Vertex>();
        for (int[] face : faces) for (int i : new int[]{0,1,2,0,2,3}) {
            var p = points[face[i]]; result.add(new Vertex(p[0], p[1], p[2], uv[i][0], uv[i][1]));
        }
        return List.copyOf(result);
    }
}
