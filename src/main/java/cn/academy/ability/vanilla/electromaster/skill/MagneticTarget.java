package cn.academy.ability.vanilla.electromaster.skill;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** MagMovement.toTarget: classify the ordinary first ray hit, never filter out occluders before tracing. */
public record MagneticTarget(Vec3 position, Entity entity) {
    public static MagneticTarget trace(Entity player, double range, float proficiency) {
        HitResult hit = ElectricAttackTrace.trace(player, range, false);
        if (hit.getType() == HitResult.Type.BLOCK) {
            var block = player.level().getBlockState(((BlockHitResult) hit).getBlockPos()).getBlock();
            // Preserve the original redundant gates: isMetalBlock already includes weak metals.
            if (proficiency < .6f && !ElectromasterMetalRules.metal(block)) return null;
            if (!ElectromasterMetalRules.weak(block) && !ElectromasterMetalRules.metal(block)) return null;
            return new MagneticTarget(hit.getLocation(), null);
        }
        if (hit instanceof EntityHitResult entityHit && ElectromasterMetalRules.metallic(entityHit.getEntity())) {
            Entity entity = entityHit.getEntity();
            return new MagneticTarget(entity.getEyePosition(), entity);
        }
        return null;
    }
}
