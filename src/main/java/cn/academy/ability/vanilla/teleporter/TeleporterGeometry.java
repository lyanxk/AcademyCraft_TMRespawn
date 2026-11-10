package cn.academy.ability.vanilla.teleporter;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/** Geometry from ThreateningTeleport, ShiftTeleport and LocationTeleport in 1.12.2. */
public final class TeleporterGeometry {
    public static LivingEntity threateningTarget(ServerPlayer player, double range) {
        Vec3 start = player.getEyePosition(), end = start.add(player.getLookAngle().scale(range));
        return nearestLiving(player, start, end);
    }

    public static LivingEntity nearestLiving(ServerPlayer player, Vec3 start, Vec3 end) {
        return (LivingEntity) nearestEntity(player, start, end, entity -> entity instanceof LivingEntity);
    }

    public static Entity nearestEntity(ServerPlayer player, Vec3 start, Vec3 end,
                                       java.util.function.Predicate<Entity> selector) {
        // This is the entity half of LambdaLib2 Raytrace; callers decide how to compare block hits.
        return player.serverLevel().getEntities(player,
                        new AABB(start, end).expandTowards(1, 1, 1),
                        entity -> entity.isAlive() && entity.isPickable() && selector.test(entity))
                .stream().filter(entity -> legacyTraceBox(entity).clip(start, end).isPresent())
                .min(Comparator.comparingDouble(entity -> start.distanceToSqr(
                        legacyTraceBox(entity).clip(start, end).orElse(end)))).orElse(null);
    }

    private static AABB legacyTraceBox(Entity entity) {
        // 1.12.2 Raytrace.rayTraceEntities calls AxisAlignedBB.expand(.3, .3, .3).
        return entity.getBoundingBox().expandTowards(.3, .3, .3);
    }

    public static List<LivingEntity> shiftTargets(ServerPlayer player, Vec3 destination) {
        Vec3 start = player.position();
        return player.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(start, destination),
                entity -> entity != player && entity.isAlive()
                        && entity.getBoundingBox().clip(start, destination).isPresent());
    }

    public static BlockPlaceContext shiftPlacement(ServerPlayer player, BlockHitResult hit) {
        return new AdjacentPlacementContext(player, hit);
    }

    private static final class AdjacentPlacementContext extends BlockPlaceContext {
        AdjacentPlacementContext(ServerPlayer player, BlockHitResult hit) {
            super(player, InteractionHand.MAIN_HAND, player.getMainHandItem(), hit);
            // ShiftTeleport explicitly places next to the traced block, including replaceable blocks.
            replaceClicked = false;
        }
    }

    public static List<Entity> locationCompanions(ServerPlayer player) {
        Vec3 origin = player.position();
        return player.serverLevel().getEntities(player, AABB.ofSize(origin, 10, 10, 10),
                // Original LocationTeleport.teleportSelector starts with EntitySelectors.living().
                entity -> entity instanceof LivingEntity && entity.distanceToSqr(origin) <= 25
                        && entity.getBbWidth() * entity.getBbWidth() * entity.getBbHeight() < 80);
    }

    private TeleporterGeometry() {}
}
