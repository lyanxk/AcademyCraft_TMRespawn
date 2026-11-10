package cn.academy.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Render the original hand through a scoped pose without recursively raising RenderHandEvent. */
@Mixin(ItemInHandRenderer.class)
public interface ItemInHandAccess {
    @Invoker("renderArmWithItem")
    void academy$renderArmWithItem(AbstractClientPlayer player,float partialTicks,float pitch,InteractionHand hand,
                                   float swingProgress,ItemStack stack,float equipProgress,PoseStack pose,
                                   MultiBufferSource buffers,int light);
}
