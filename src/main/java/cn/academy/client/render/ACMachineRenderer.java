package cn.academy.client.render;

import cn.academy.AcademyCraft;
import cn.academy.block.ACMachineBlock;
import cn.academy.block.MachineKind;
import cn.academy.block.entity.ACMachineBlockEntity;
import cn.academy.registry.ACItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** Restores the original machine OBJ geometry and moving matrix/wind/cat-engine parts. */
public final class ACMachineRenderer implements BlockEntityRenderer<ACMachineBlockEntity> {
    public ACMachineRenderer(BlockEntityRendererProvider.Context ignored) {}

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "textures/models/" + name + ".png");
    }

    @Override
    public void render(ACMachineBlockEntity machine, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        switch (machine.kind()) {
            case SOLAR_GENERATOR -> renderModel(machine, pose, buffers, packedLight, "solar", "solar", .014f, 90);
            case PHASE_GENERATOR -> {
                int frame = Math.max(0, Math.min(4, Math.round(machine.phaseLiquid() / 2_000f)));
                renderStaticModel(pose, buffers, packedLight, "ip_gen", "ip_gen" + frame, 1, 0);
            }
            case MATRIX -> renderMatrix(machine, partialTick, pose, buffers, packedLight);
            case WIND_BASE -> renderModel(machine, pose, buffers, packedLight, "windgen_base",
                    machine.windStructureCompleteForRender() ? "windgen_base" : "windgen_base_disabled", 1, 0);
            case WIND_PILLAR -> renderStaticModel(pose, buffers, packedLight,
                    "windgen_pillar", "windgen_pillar", 1, 0);
            case WIND_GENERATOR -> renderWindMain(machine, partialTick, pose, buffers, packedLight);
            case DEVELOPER_NORMAL -> renderModel(machine, pose, buffers, packedLight,
                    "developer_normal", "developer_normal", .5f, 180);
            case DEVELOPER_ADVANCED -> renderModel(machine, pose, buffers, packedLight,
                    "developer_advanced", "developer_advanced", .5f, 180);
            case CAT_ENGINE -> renderCatEngine(machine, partialTick, pose, buffers, packedLight);
            default -> { }
        }
    }

    private static void renderModel(ACMachineBlockEntity machine, PoseStack pose, MultiBufferSource buffers, int light,
                                    String modelName, String textureName, float scale, float rotationY) {
        pose.pushPose();
        translateForFacing(machine, pose, .5, .5);
        rotateForFacing(machine, pose);
        if (rotationY != 0) pose.mulPose(Axis.YP.rotationDegrees(rotationY));
        pose.scale(scale, scale, scale);
        // Only the two developer TESRs disabled culling in 1.12.2. All other OBJ machine
        // renderers inherited enabled back-face culling.
        RenderType renderType = modelName.startsWith("developer_")
                ? RenderType.entityTranslucent(texture(textureName))
                : RenderType.entityTranslucentCull(texture(textureName));
        VertexConsumer consumer = buffers.getBuffer(renderType);
        ACObjModel.get(modelName).render(pose, consumer, light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        pose.popPose();
    }

    /** Legacy TESRs that were not RenderBlockMulti always rendered at the block centre. */
    private static void renderStaticModel(PoseStack pose, MultiBufferSource buffers, int light,
                                          String modelName, String textureName, float scale, float rotationY) {
        pose.pushPose();
        pose.translate(.5, 0, .5);
        if (rotationY != 0) pose.mulPose(Axis.YP.rotationDegrees(rotationY));
        pose.scale(scale, scale, scale);
        ACObjModel.get(modelName).render(pose,
                buffers.getBuffer(RenderType.entityTranslucentCull(texture(textureName))),
                light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        pose.popPose();
    }

    private static void renderMatrix(ACMachineBlockEntity machine, float partialTick, PoseStack pose,
                                     MultiBufferSource buffers, int light) {
        ACObjModel model = ACObjModel.get("matrix");
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucentCull(texture("matrix")));
        pose.pushPose();
        translateForFacing(machine, pose, 1, 1);
        rotateForFacing(machine, pose);
        model.render(pose, consumer, light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1, "Main", "Core");
        if (machine.isMatrixWorking()) {
            float time = (machine.getLevel() == null ? 0 : machine.getLevel().getGameTime()) + partialTick;
            for (int index = 0; index < 3; index++) {
                pose.pushPose();
                pose.translate(0, .1 * Math.sin(time / 20f * 1.111f + index * 40), 0);
                pose.mulPose(Axis.YP.rotationDegrees(time * 2.5f + index * 120));
                model.render(pose, consumer, light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1, "Shield");
                pose.popPose();
            }
        }
        pose.popPose();
    }

    private static void renderWindMain(ACMachineBlockEntity machine, float partialTick, PoseStack pose,
                                       MultiBufferSource buffers, int light) {
        pose.pushPose();
        // BlockMulti used a 0.5 x 0.4 rotation centre for this asymmetric three-block machine.
        translateForFacing(machine, pose, .5, .4);
        rotateForFacing(machine, pose);
        ACObjModel.get("windgen_main").render(pose,
                buffers.getBuffer(RenderType.entityTranslucentCull(texture("windgen_main"))), light,
                OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        if (machine.items.getStackInSlot(0).is(ACItems.WINDGEN_FAN.get())
                && machine.windStructureCompleteForRender() && machine.windNoObstacleForRender()) {
            pose.pushPose();
            pose.translate(0, .5, .82);
            pose.mulPose(Axis.ZN.rotationDegrees(machine.clientWindRotation(partialTick)));
            ACObjModel.get("windgen_fan").render(pose,
                    buffers.getBuffer(RenderType.entityTranslucentCull(texture("windgen_fan"))), light,
                    OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
            pose.popPose();
        }
        pose.popPose();
    }

    private static void rotateForFacing(ACMachineBlockEntity machine, PoseStack pose) {
        if (!machine.getBlockState().hasProperty(ACMachineBlock.FACING)) return;
        float angle = legacyRotation(machine.getBlockState().getValue(ACMachineBlock.FACING));
        if (angle != 0) pose.mulPose(Axis.YP.rotationDegrees(angle));
    }

    static float legacyRotation(net.minecraft.core.Direction facing) {
        return switch (facing) {
            case EAST -> 90;
            case SOUTH -> 0;
            case WEST -> 270;
            default -> 180;
        };
    }

    /** Port of BlockMulti's pivotOffset + rotCenters placement calculation. */
    private static void translateForFacing(ACMachineBlockEntity machine, PoseStack pose,
                                           double centerX, double centerZ) {
        var facing = machine.getBlockState().hasProperty(ACMachineBlock.FACING)
                ? machine.getBlockState().getValue(ACMachineBlock.FACING) : net.minecraft.core.Direction.NORTH;
        double[] pivot = legacyPivot(facing, centerX, centerZ);
        pose.translate(pivot[0], 0, pivot[1]);
    }

    static double[] legacyPivot(net.minecraft.core.Direction facing, double centerX, double centerZ) {
        double x = switch (facing) {
            case SOUTH -> 1 - centerX;
            case WEST -> centerZ;
            case EAST -> 1 - centerZ;
            default -> centerX;
        };
        double z = switch (facing) {
            case SOUTH -> 1 - centerZ;
            case WEST -> 1 - centerX;
            case EAST -> centerX;
            default -> centerZ;
        };
        return new double[]{x, z};
    }

    private static void renderCatEngine(ACMachineBlockEntity machine, float partialTick, PoseStack pose,
                                        MultiBufferSource buffers, int light) {
        float time = ((machine.getLevel() == null ? 0 : machine.getLevel().getGameTime()) + partialTick) / 20f;
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double relativeX = machine.getBlockPos().getX() + .5 - camera.x;
        double relativeZ = machine.getBlockPos().getZ() + .5 - camera.z;
        float billboardYaw = (float) Math.toDegrees(Math.atan2(relativeX, relativeZ)) + 180;
        pose.pushPose();
        pose.translate(.5, .03 * Math.sin(time * .006), .5);
        pose.mulPose(Axis.YP.rotationDegrees(billboardYaw));
        pose.translate(0, .5, 0);
        pose.mulPose(Axis.XP.rotationDegrees(machine.clientCatRotation(partialTick)));
        pose.translate(-.5, -.5, 0);
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(ResourceLocation.fromNamespaceAndPath(
                AcademyCraft.MOD_ID, "textures/block/cat_engine.png")));
        vertex(out, pose, 0, 0, 0, 0, 0, light);
        vertex(out, pose, 1, 0, 0, 1, 0, light);
        vertex(out, pose, 1, 1, 0, 1, 1, light);
        vertex(out, pose, 0, 1, 0, 0, 1, light);
        pose.popPose();
    }

    private static void vertex(VertexConsumer out, PoseStack pose, float x, float y, float z,
                               float u, float v, int light) {
        out.addVertex(pose.last(), x, y, z).setColor(1f, 1f, 1f, 1f).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(), 0, 0, 1);
    }

    @Override
    public AABB getRenderBoundingBox(ACMachineBlockEntity machine) {
        double radius = machine.kind() == MachineKind.WIND_GENERATOR ? 9
                : machine.kind() == MachineKind.MATRIX || machine.kind() == MachineKind.DEVELOPER_NORMAL
                || machine.kind() == MachineKind.DEVELOPER_ADVANCED ? 3 : 2;
        return new AABB(machine.getBlockPos()).inflate(radius);
    }

    @Override public int getViewDistance() { return 128; }
}
