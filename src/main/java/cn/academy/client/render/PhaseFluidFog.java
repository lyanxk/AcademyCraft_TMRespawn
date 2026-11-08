package cn.academy.client.render;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;

/** Phase liquid fog supplied through the native client fluid extension. */
public final class PhaseFluidFog {
    private static boolean hasVisionEffect(Camera camera) {
        return camera.getEntity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
    }

    public static Vector3f color(Camera camera, Vector3f current) {
        return hasVisionEffect(camera) ? current : new Vector3f(1, 1, 1);
    }

    public static void applyDistance(Camera camera, float renderDistance) {
        if (hasVisionEffect(camera)) return;
        RenderSystem.setShaderFogStart(-8);
        RenderSystem.setShaderFogEnd(Math.min(32, renderDistance));
        RenderSystem.setShaderFogShape(FogShape.SPHERE);
    }

    private PhaseFluidFog() {}
}
