package cn.academy.util;

import cn.academy.ability.AbilityContext;
import cn.academy.event.BlockDestroyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Comparator;
import java.util.HashSet;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Port of AcademyCraft 1.12.2 RangedRayDamage, including its fixed Y/Z sampling plane. */
public class RangedRayDamage {
    static final double STEP = .9;
    public final ServerPlayer player;
    public final ServerLevel world;
    public final AbilityContext ctx;
    public Vec3 pos, dir;
    public double range;
    public float totalEnergy;
    public int maxIncrement = 50;
    public float dropProb = .05f;
    public Predicate<Entity> entitySelector;
    public float startDamage = 10;
    private Vec3 start, slope;

    public RangedRayDamage(AbilityContext ctx, double range, float energy) {
        this.ctx = ctx; player = ctx.player; world = player.serverLevel();
        pos = player.position();
        float yaw = (float) (player.getYRot() * Math.PI / 180), pitch = (float) (player.getXRot() * Math.PI / 180);
        dir = new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
        this.range = range; totalEnergy = energy;
        entitySelector = entity -> entity != player;
    }

    public void perform() {
        // The original calls rotatePitch/rotateYaw on immutable Vec3d without assigning their results.
        Vec3 vp0 = new Vec3(0, 0, 1), vp1 = new Vec3(0, 1, 0);
        start = pos; slope = dir;
        double maxDistance = Double.MAX_VALUE;
        Vec3 low = start.add(0, -range, -range), high = start.add(0, range, range);
        AABB bounds = new AABB(low, high).minmax(new AABB(low.add(slope.scale(maxIncrement)), high.add(slope.scale(maxIncrement))));
        var targets = world.getEntities((Entity) null, bounds,
                entitySelector.and(entity -> entity.position().subtract(start).cross(slope).length() < range * 1.2));
        targets.sort(Comparator.comparingDouble(entity -> player.distanceToSqr(entity.position())));
        for (Entity target : targets) {
            if (!attackEntity(target)) { maxDistance = target.distanceToSqr(player); break; }
        }
        if (!ctx.canBreakBlock(world)) return;
        // int[] uses identity equality: the original does NOT deduplicate identical block coordinates.
        var processed = new HashSet<int[]>();
        for (double s = -range; s <= range; s += STEP) for (double t = -range; t <= range; t += STEP) {
            double radius = range * (.9 + world.random.nextDouble() * .2);
            if (s * s + t * t > radius * radius) continue;
            Vec3 point = start.add(vp0.scale(s)).add(vp1.scale(t));
            processed.add(new int[] {Mth.floor(point.x), Mth.floor(point.y), Mth.floor(point.z)});
        }
        float average = totalEnergy / processed.size();
        for (int[] line : processed)
            processLine(line[0], line[1], line[2], slope, average * (.95f + world.random.nextFloat() * .1f), maxDistance);
    }

    private void processLine(int x0, int y0, int z0, Vec3 slope, float energy, double maxDistanceSquared) {
        Plotter plotter = new Plotter(x0, y0, z0, slope.x, slope.y, slope.z);
        int increments = 0;
        for (int i = 0; i <= maxIncrement && energy > 0; i++) {
            increments++;
            int[] xyz = plotter.next();
            int dx = x0 - xyz[0], dy = y0 - xyz[1], dz = z0 - xyz[2];
            if (dx * dx + dy * dy + dz * dz > maxDistanceSquared) break;
            BlockPos block = new BlockPos(xyz[0], xyz[1], xyz[2]);
            energy = destroyBlock(energy, block, increments < 20);
            if (world.random.nextDouble() < .05)
                energy = destroyBlock(energy, block.relative(Direction.values()[world.random.nextInt(6)]), increments < 20);
        }
    }

    private float destroyBlock(float energy, BlockPos pos, boolean sound) {
        var state = world.getBlockState(pos);
        float hardness = state.getDestroySpeed(world, pos);
        if (hardness < 0) hardness = 233333;
        var event = new BlockDestroyEvent(world, player, pos, ctx.skill);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled() || energy < hardness) return 0;
        if (!state.isAir()) {
            // Old dropBlockAsItemWithChance rolls for each returned stack, with fortune zero.
            for (ItemStack stack : Block.getDrops(state, world, pos, world.getBlockEntity(pos), player, ItemStack.EMPTY))
                if (world.random.nextFloat() <= dropProb) Block.popResource(world, pos, stack);
            if (sound && world.random.nextDouble() < .1) {
                var type = state.getSoundType(world, pos, player);
                world.playSound(null, pos, type.getBreakSound(), SoundSource.BLOCKS, (type.getVolume() + 1) / 2, type.getPitch());
            }
        }
        world.removeBlock(pos, false);
        return energy - hardness;
    }

    protected boolean attackEntity(Entity target) {
        float distance = Math.min(maxIncrement, (float) target.position().subtract(start).cross(slope).length());
        float damage = startDamage * (1 + (distance / maxIncrement) * (.2f - 1));
        return applyAttack(target, damage);
    }
    protected boolean applyAttack(Entity target, float damage) { ctx.attack(target, damage); return true; }

    public static class Reflectible extends RangedRayDamage {
        public final Consumer<Entity> callback;
        public Reflectible(AbilityContext ctx, double range, float energy, Consumer<Entity> callback) {
            super(ctx, range, energy); this.callback = callback;
        }
        @Override protected boolean applyAttack(Entity target, float damage) {
            boolean[] result = {true};
            ctx.attackReflect(target, damage, () -> { callback.accept(target); result[0] = false; });
            return result[0];
        }
    }
}
