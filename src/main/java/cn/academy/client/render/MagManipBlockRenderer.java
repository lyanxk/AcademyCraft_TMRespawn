package cn.academy.client.render;

import cn.academy.entity.MagManipEntityBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import cn.academy.client.render.util.MagneticBlockRendering;
import net.minecraft.world.level.block.RenderShape;
import net.neoforged.neoforge.client.model.data.ModelData;

public final class MagManipBlockRenderer extends EntityRenderer<MagManipEntityBlock> {
    public MagManipBlockRenderer(EntityRendererProvider.Context context){super(context);}
    @Override public void render(MagManipEntityBlock entity,float yaw,float partialTick,PoseStack pose,MultiBufferSource buffers,int light) {
        var state=entity.blockState();if(state.getRenderShape()!=RenderShape.MODEL)return;
        var dispatcher=Minecraft.getInstance().getBlockRenderer();
        var model=dispatcher.getBlockModel(state);var pos=MagneticBlockRendering.samplePosition(entity.position());
        var data=model.getModelData(entity.level(),pos,state,ModelData.EMPTY);
        pose.pushPose();
        pose.mulPose(MagneticBlockRendering.transform(entity.previousSpinYaw,entity.spinYaw,entity.previousSpinPitch,entity.spinPitch,partialTick));
        MagneticBlockRendering.tessellate(dispatcher.getModelRenderer(),entity.level(),model,state,pos,pose,
                buffers.getBuffer(ACShaders.MAGNETIC_BLOCK),Minecraft.useAmbientOcclusion(),data);
        pose.popPose();
    }
    @Override public ResourceLocation getTextureLocation(MagManipEntityBlock entity){return TextureAtlas.LOCATION_BLOCKS;}
}
