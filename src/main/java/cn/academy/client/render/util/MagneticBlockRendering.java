package cn.academy.client.render.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4d;
import org.joml.Matrix4f;

/** RenderEntityBlock's world-model inputs, not item-model lighting or a constant seed of 42. */
public final class MagneticBlockRendering {
    public static BlockPos samplePosition(Vec3 position) {
        // 1.12 Entity.getPosition() adds .5 to Y before constructing BlockPos.
        return BlockPos.containing(position.x,position.y+.5,position.z);
    }
    public static long seed(BlockPos pos) {
        long value=(long)(pos.getX()*3129871) ^ (long)pos.getZ()*116129781L ^ pos.getY();
        return value*value*42317861L+value*11L; // Original does not shift the result right by 16.
    }
    public static Matrix4f transform(float previousYaw,float yaw,float previousPitch,float pitch,float partial) {
        float y=previousYaw+(yaw-previousYaw)*partial, x=previousPitch+(pitch-previousPitch)*partial;
        // Modern tesselation emits local vertices; the old -blockPos translation cancels its world-space vertex origin.
        return new Matrix4f().set(new Matrix4d().rotateY(Math.toRadians(y)).rotateX(Math.toRadians(x)).translate(-.5,-.5,-.5));
    }
    public static void tessellate(ModelBlockRenderer renderer,BlockAndTintGetter level,BakedModel model,BlockState state,
                                  BlockPos pos,PoseStack pose,VertexConsumer out,boolean ambient,ModelData data) {
        Vec3 offset=state.getOffset(level,pos);pose.translate(offset.x,offset.y,offset.z);
        boolean ao=ambient && switch(model.useAmbientOcclusion(state,data,null)) {
            case TRUE -> true;
            case DEFAULT -> state.getLightEmission(level,pos)==0;
            case FALSE -> false;
        };
        if(ao)renderer.tesselateWithAO(level,model,state,pos,pose,out,true,RandomSource.create(),seed(pos),OverlayTexture.NO_OVERLAY,data,null);
        else renderer.tesselateWithoutAO(level,model,state,pos,pose,out,true,RandomSource.create(),seed(pos),OverlayTexture.NO_OVERLAY,data,null);
    }
    private MagneticBlockRendering(){}
}
