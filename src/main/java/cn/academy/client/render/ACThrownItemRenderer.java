package cn.academy.client.render;

import cn.academy.AcademyCraft;
import cn.academy.entity.ACThrownItemEntity;
import cn.academy.registry.ACItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Original OBJ renderer for magnetic hooks and Silbarn projectiles. */
public final class ACThrownItemRenderer extends EntityRenderer<ACThrownItemEntity> {
    private final ItemRenderer itemRenderer;

    public ACThrownItemRenderer(EntityRendererProvider.Context context) {
        super(context);
        itemRenderer = context.getItemRenderer();
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "textures/models/" + name + ".png");
    }

    @Override
    public void render(ACThrownItemEntity entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        if (entity.getItem().is(ACItems.MAG_HOOK.get())) {
            pose.pushPose();
            if (entity.isHooked()) {
                var interpolated = entity.getPosition(partialTick);
                entity.prepareHookRender();
                var offset = entity.position().subtract(interpolated);
                pose.translate(offset.x, offset.y, offset.z);
            }
            // EntityMagHook.preRender changed yaw/pitch after impact, then the legacy renderer
            // applied this same pair of rotations in both flying and open states.
            pose.mulPose(Axis.YP.rotationDegrees(-entity.getYRot() + 90));
            pose.mulPose(Axis.ZP.rotationDegrees(entity.getXRot() - 90));
            pose.scale(.0054f, .0054f, .0054f);
            ACObjModel.get(entity.isHooked() ? "maghook_open" : "maghook").render(pose,
                    buffers.getBuffer(RenderType.entityTranslucentCull(texture("maghook"))), light,
                    OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
            pose.popPose();
        } else if (entity.getItem().is(ACItems.SILBARN.get())) {
            if (entity.isSilbarnHit()) return;
            pose.pushPose();
            float spin = SilbarnEffects.rotationDegrees(entity);
            Vector3f axis = visualAxis(entity);
            pose.scale(.05f, .05f, .05f);
            pose.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(spin), axis));
            pose.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
            pose.mulPose(Axis.XP.rotationDegrees(90));
            ACObjModel.get("silbarn").render(pose,
                    buffers.getBuffer(RenderType.entityTranslucentCull(texture("silbarn"))), light,
                    OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
            pose.popPose();
        } else if (entity.getItem().is(ACItems.COIN.get())) {
            if (!entity.isLocalCoin()) return;
            LivingEntity owner = entity.getOwner() instanceof LivingEntity living ? living : null;
            if (owner != null && entity.getY() >= owner.getY()) {
                pose.pushPose();
                Minecraft minecraft = Minecraft.getInstance();
                boolean firstPerson = owner == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
                if (owner == minecraft.player) {
                    var camera = minecraft.gameRenderer.getMainCamera().getPosition();
                    pose.translate(camera.x - entity.getX(), 0, camera.z - entity.getZ());
                }
                float ownerYaw = firstPerson ? owner.getYRot() : owner.yBodyRot;
                pose.mulPose(Axis.YP.rotationDegrees(-ownerYaw));
                pose.translate(-.63, 1, .30);
                pose.scale(.3f, .3f, .3f);
                pose.translate(.5, .5, 0);
                double milliseconds = RailgunHandEffects.seconds() * 1000;
                float spin = (float) ((milliseconds % 150) * 1.2);
                var coinAxis = entity.coinAxis();
                pose.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(spin),
                        new Vector3f((float) coinAxis.x, (float) coinAxis.y, (float) coinAxis.z).normalize()));
                pose.translate(-.5, -.5, 0);
                ResourceLocation front = ResourceLocation.fromNamespaceAndPath(
                        AcademyCraft.MOD_ID, "textures/item/coin_front.png");
                ResourceLocation back = ResourceLocation.fromNamespaceAndPath(
                        AcademyCraft.MOD_ID, "textures/item/coin_back.png");
                // RendererCoinThrowing passed (coinFront, coinBack), so RenderUtils used the
                // back texture on +Z and the front texture on -Z.
                ACItemRenderer.renderCoin(pose, buffers, light, OverlayTexture.NO_OVERLAY,
                        .0625f, 0, back, front);
                pose.popPose();
            }
        } else {
            pose.pushPose();
            pose.mulPose(entityRenderDispatcher.cameraOrientation());
            itemRenderer.renderStatic(entity.getItem(), ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY,
                    pose, buffers, entity.level(), entity.getId());
            pose.popPose();
        }
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    private static Vector3f visualAxis(ACThrownItemEntity entity) {
        var vector = entity.silbarnAxis();
        Vector3f axis = new Vector3f((float) vector.x, (float) vector.y, (float) vector.z);
        return axis.lengthSquared() < 1e-6f ? axis.set(1, 1, 1).normalize() : axis.normalize();
    }

    @Override
    public ResourceLocation getTextureLocation(ACThrownItemEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
