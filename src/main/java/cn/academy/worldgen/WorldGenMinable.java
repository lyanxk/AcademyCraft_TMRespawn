package cn.academy.worldgen;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** 1.12.2 WorldGenMinable geometry and natural-stone predicate used by ACWorldGen. */
final class WorldGenMinable {
    private WorldGenMinable() {}

    static void generate(WorldGenLevel world, Random random, BlockPos origin, BlockState ore, int size) {
        float angle = random.nextFloat() * (float) Math.PI;
        // These endpoint operations were FLOAT operations in 1.12.2, before widening to double.
        double x0 = (float) (origin.getX() + 8) + Mth.sin(angle) * (float) size / 8.0F;
        double x1 = (float) (origin.getX() + 8) - Mth.sin(angle) * (float) size / 8.0F;
        double z0 = (float) (origin.getZ() + 8) + Mth.cos(angle) * (float) size / 8.0F;
        double z1 = (float) (origin.getZ() + 8) - Mth.cos(angle) * (float) size / 8.0F;
        double y0 = origin.getY() + random.nextInt(3) - 2;
        double y1 = origin.getY() + random.nextInt(3) - 2;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < size; i++) {
            float progress = (float) i / (float) size;
            double x = x0 + (x1 - x0) * progress;
            double y = y0 + (y1 - y0) * progress;
            double z = z0 + (z1 - z0) * progress;
            double scale = random.nextDouble() * size / 16.0;
            double diameter = (Mth.sin((float) Math.PI * progress) + 1.0F) * scale + 1.0;
            double radius = diameter / 2.0;
            int minX = Mth.floor(x - radius), maxX = Mth.floor(x + radius);
            int minY = Mth.floor(y - radius), maxY = Mth.floor(y + radius);
            int minZ = Mth.floor(z - radius), maxZ = Mth.floor(z + radius);
            for (int bx = minX; bx <= maxX; bx++) {
                double dx = (bx + .5 - x) / radius;
                if (dx * dx >= 1.0) continue;
                for (int by = minY; by <= maxY; by++) {
                    double dy = (by + .5 - y) / radius;
                    if (dx * dx + dy * dy >= 1.0) continue;
                    for (int bz = minZ; bz <= maxZ; bz++) {
                        double dz = (bz + .5 - z) / radius;
                        if (dx * dx + dy * dy + dz * dz >= 1.0) continue;
                        // Old World returned air below 0 and above 255, not modern deepslate.
                        if (by < 0 || by >= 256) continue;
                        pos.set(bx, by, bz);
                        if (naturalStone(world.getBlockState(pos))) world.setBlock(pos, ore, 2);
                    }
                }
            }
        }
    }

    private static boolean naturalStone(BlockState state) {
        // BlockStone.EnumType.isNatural: no polished variants, tuff or deepslate.
        return state.is(Blocks.STONE) || state.is(Blocks.GRANITE)
                || state.is(Blocks.DIORITE) || state.is(Blocks.ANDESITE);
    }
}
