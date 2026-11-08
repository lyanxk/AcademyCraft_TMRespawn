package cn.academy.worldgen;

import cn.academy.config.ACConfig;
import cn.academy.registry.ACBlocks;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** The original PhaseLiquidGenerator (priority 1), then ACWorldGen (priority 2). */
public final class ACWorldGen extends Feature<NoneFeatureConfiguration> {
    public ACWorldGen() { super(NoneFeatureConfiguration.CODEC); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel world = context.level();
        if (!world.getLevel().dimensionTypeRegistration().is(BuiltinDimensionTypes.OVERWORLD)) return false;
        int chunkX = context.origin().getX() >> 4;
        int chunkZ = context.origin().getZ() >> 4;
        int x = chunkX * 16, z = chunkZ * 16;
        long seed = chunkSeed(world.getSeed(), chunkX, chunkZ);
        Random random = new Random(seed);
        boolean generated = false;
        if (ACConfig.GENERATE_PHASE_LIQUID.get() && random.nextDouble() < .3) {
            BlockPos origin = new BlockPos(x + random.nextInt(16) + 8,
                    5 + random.nextInt(30), z + random.nextInt(16) + 8);
            generated = WorldGenPhaseLiq.generate(world, random, origin);
        }
        if (ACConfig.GENERATE_ORES.get()) {
            // Forge 1.12.2 reset the random for EACH IWorldGenerator, including after failed lakes.
            random.setSeed(seed);
            generateOre(world, random, x, z, ACBlocks.RESO_ORE.get().defaultBlockState(), 9, 8);
            generateOre(world, random, x, z, ACBlocks.CONSTRAINT_METAL.get().defaultBlockState(), 12, 8);
            generateOre(world, random, x, z, ACBlocks.CRYSTAL_ORE.get().defaultBlockState(), 12, 12);
            generateOre(world, random, x, z, ACBlocks.IMAGSIL_ORE.get().defaultBlockState(), 11, 8);
            generated = true;
        }
        return generated;
    }

    private static void generateOre(WorldGenLevel world, Random random, int x, int z,
                                    BlockState ore, int size, int density) {
        for (int i = 0; i < density; i++) {
            // Preserve CustomWorldGen's X/Y/Z consumption order, including failed veins.
            BlockPos origin = new BlockPos(x + random.nextInt(16), random.nextInt(60), z + random.nextInt(16));
            WorldGenMinable.generate(world, random, origin, ore, size);
        }
    }

    public static long chunkSeed(long worldSeed, int chunkX, int chunkZ) {
        Random random = new Random(worldSeed);
        // GameRegistry.generateWorld uses `nextLong() >> 2 + 1L`: addition precedes shift.
        // Do not replace this with vanilla's odd multipliers or (nextLong() >> 2) + 1.
        long xSeed = random.nextLong() >> 3;
        long zSeed = random.nextLong() >> 3;
        return (xSeed * chunkX + zSeed * chunkZ) ^ worldSeed;
    }
}
