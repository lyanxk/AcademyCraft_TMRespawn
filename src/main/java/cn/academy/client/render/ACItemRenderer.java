package cn.academy.client.render;

import cn.academy.AcademyCraft;
import cn.academy.registry.ACItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/** Custom inventory/held renderer for the four items that had a 1.12.2 TEISR. */
public final class ACItemRenderer extends BlockEntityWithoutLevelRenderer {
    public ACItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "textures/models/" + name + ".png");
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                             MultiBufferSource buffers, int light, int overlay) {
        boolean coin = stack.is(ACItems.COIN.get());
        boolean developer = stack.is(ACItems.DEVELOPER_PORTABLE.get());
        boolean magHook = stack.is(ACItems.MAG_HOOK.get());
        boolean silbarn = stack.is(ACItems.SILBARN.get());
        if (!coin && !developer && !magHook && !silbarn) {
            super.renderByItem(stack, context, pose, buffers, light, overlay);
            return;
        }

        pose.pushPose();
        // ItemRenderer has already shifted a built-in model by -0.5.  Temporarily cancel that
        // shift, apply the exact 1.12.2 BakedModelForTEISR perspective matrix, then put the shift
        // back before applying the old TEISR's own model matrix.
        pose.translate(.5, .5, .5);
        LegacyItem legacyItem = coin ? LegacyItem.COIN : developer ? LegacyItem.DEVELOPER
                : magHook ? LegacyItem.MAG_HOOK : LegacyItem.SILBARN;
        pose.mulPose(legacyPerspective(legacyItem, context));
        pose.translate(-.5, -.5, -.5);

        if (coin) {
            renderCoin(pose, buffers, light, overlay);
        } else {
            String model;
            if (developer) model = "developer_portable";
            else if (magHook) {
                model = "maghook";
                pose.mulPose(new LegacyTransformChain().scale(.01f).build());
            } else {
                model = "silbarn";
                pose.mulPose(new LegacyTransformChain().scale(.0625f).rotate(90, 0, 0).build());
            }
            ACObjModel.get(model).render(pose,
                    // The 1.12.2 TEISR inherited the item renderer's enabled back-face culling.
                    // Using the no-cull translucent type renders the rear half of closed meshes as
                    // well, which darkens alpha textures such as Silbarn and changes the silhouette.
                    buffers.getBuffer(RenderType.entityTranslucentCull(texture(model))),
                    light, overlay, 1, 1, 1, 1);
        }
        pose.popPose();
    }

    enum LegacyItem { COIN, DEVELOPER, MAG_HOOK, SILBARN }

    /** Exact matrices returned by the four 1.12.2 BakedModelForTEISR instances. */
    static Matrix4f legacyPerspective(LegacyItem item, ItemDisplayContext context) {
        LegacyTransformChain chain = new LegacyTransformChain();
        return switch (item) {
            case COIN -> switch (context) {
                case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> chain.scale(.5f).translate(.2f, 0, -.1f).build();
                case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> chain.scale(.2f).build();
                case GROUND -> chain.scale(-.3f, -.3f, .3f).translate(0, .1f, 0).build();
                default -> chain.build();
            };
            case DEVELOPER -> switch (context) {
                case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND ->
                        chain.rotate(0, 180, 0).scale(.3f).translate(.34f, -.1f, -.1f).build();
                case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> chain.rotate(0, 180, 0).scale(.2f).build();
                case GROUND -> chain.scale(-.15f, -.15f, .15f).translate(0, .1f, 0).build();
                default -> chain.build();
            };
            case MAG_HOOK -> magHookPerspective(context);
            case SILBARN -> silbarnPerspective(context);
        };
    }

    private static Matrix4f magHookPerspective(ItemDisplayContext context) {
        Matrix4f firstRight = new LegacyTransformChain().scale(1.4f).rotate(0, 90, 180)
                .translate(0, .5f, .4f).build();
        Matrix4f thirdRight = new LegacyTransformChain().rotate(0, 90, 180).scale(.8f)
                .translate(-.4f, .5f, .7f).build();
        return switch (context) {
            case FIRST_PERSON_RIGHT_HAND -> firstRight;
            case FIRST_PERSON_LEFT_HAND -> new LegacyTransformChain(firstRight).translate(1.4f, 0, 0).build();
            case THIRD_PERSON_RIGHT_HAND -> thirdRight;
            case THIRD_PERSON_LEFT_HAND -> new LegacyTransformChain(thirdRight).translate(.9f, 0, 0).build();
            case GROUND -> new LegacyTransformChain().rotate(0, 90, 180).translate(-.4f, .9f, .7f)
                    .scale(.5f).build();
            default -> new Matrix4f();
        };
    }

    private static Matrix4f silbarnPerspective(ItemDisplayContext context) {
        Matrix4f firstRight = new LegacyTransformChain().rotate(0, 90, 90).translate(1, .5f, .2f).build();
        Matrix4f thirdRight = new LegacyTransformChain().rotate(90, 0, 90).scale(.6f)
                .translate(-.3f, .3f, -.3f).build();
        return switch (context) {
            case FIRST_PERSON_RIGHT_HAND -> firstRight;
            case FIRST_PERSON_LEFT_HAND -> new LegacyTransformChain(firstRight).translate(0, -1, 0).build();
            case THIRD_PERSON_RIGHT_HAND, GROUND -> thirdRight;
            case THIRD_PERSON_LEFT_HAND -> new LegacyTransformChain(thirdRight).translate(0, 0, .5f).build();
            default -> new Matrix4f();
        };
    }

    /** Exact pre-multiplying behaviour of LambdaLib2 0.2.0 TransformChain. */
    private static final class LegacyTransformChain {
        private final Matrix4f result;

        LegacyTransformChain() {
            result = new Matrix4f();
        }

        LegacyTransformChain(Matrix4f source) {
            result = new Matrix4f(source);
        }

        LegacyTransformChain translate(float x, float y, float z) {
            return apply(new Matrix4f().translation(x, y, z));
        }

        LegacyTransformChain scale(float scale) {
            return scale(scale, scale, scale);
        }

        LegacyTransformChain scale(float x, float y, float z) {
            return apply(new Matrix4f().scaling(x, y, z));
        }

        LegacyTransformChain rotate(float x, float y, float z) {
            return apply(legacyEuler(x, y, z));
        }

        private LegacyTransformChain apply(Matrix4f operation) {
            result.set(operation.mul(new Matrix4f(result)));
            return this;
        }

        Matrix4f build() {
            return new Matrix4f(result);
        }
    }

    /** Exact field layout and formula of LambdaLib2 TransformUtils.rotateEuler. */
    static Matrix4f legacyEuler(float x, float y, float z) {
        float a3 = (float) Math.toRadians(x), a2 = (float) Math.toRadians(y), a1 = (float) Math.toRadians(z);
        float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
        float c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);
        float c3 = (float) Math.cos(a3), s3 = (float) Math.sin(a3);
        return new Matrix4f()
                .m00(c1 * c3 - s1 * s2 * s3).m01(c3 * s1 + c1 * s2 * s3).m02(-c2 * s3)
                .m10(-c2 * s1).m11(c1 * c2).m12(s2)
                .m20(c1 * s3 + c3 * s1 * s2).m21(s1 * s3 - c1 * c3 * s2).m22(c2 * c3);
    }

    private static void renderCoin(PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ResourceLocation front = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "textures/item/coin_front.png");
        ResourceLocation back = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "textures/item/coin_back.png");
        renderCoin(pose, buffers, light, overlay, .04f, .5f, front, back);
    }

    static void renderCoin(PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                           float width, float centerZ,
                           ResourceLocation positiveFace, ResourceLocation negativeFace) {
        pose.pushPose();
        // Exact modern-buffer port of RenderUtils.drawEquippedItem. The old helper placed the
        // front/back at +/-width and emitted 32 alpha-tested side
        // slices, so the coin had real thickness in GUI, hand, ground and entity-frame views.
        pose.translate(0, 0, centerZ);
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(positiveFace));
        itemVertex(out, pose, 0, 0, width, 1, 1, light, overlay, 0, 0, 1);
        itemVertex(out, pose, 1, 0, width, 0, 1, light, overlay, 0, 0, 1);
        itemVertex(out, pose, 1, 1, width, 0, 0, light, overlay, 0, 0, 1);
        itemVertex(out, pose, 0, 1, width, 1, 0, light, overlay, 0, 0, 1);
        out = buffers.getBuffer(RenderType.entityCutoutNoCull(negativeFace));
        itemVertex(out, pose, 0, 1, -width, 1, 0, light, overlay, 0, 0, -1);
        itemVertex(out, pose, 1, 1, -width, 0, 0, light, overlay, 0, 0, -1);
        itemVertex(out, pose, 1, 0, -width, 0, 1, light, overlay, 0, 0, -1);
        itemVertex(out, pose, 0, 0, -width, 1, 1, light, overlay, 0, 0, -1);

        final int slices = 32;
        final float texelInset = 1f / (32 * slices);
        for (int index = 0; index < slices; index++) {
            float x = (float) index / slices;
            float u = 1 - x - texelInset;
            itemVertex(out, pose, x, 0, -width, u, 1, light, overlay, -1, 0, 0);
            itemVertex(out, pose, x, 0, width, u, 1, light, overlay, -1, 0, 0);
            itemVertex(out, pose, x, 1, width, u, 0, light, overlay, -1, 0, 0);
            itemVertex(out, pose, x, 1, -width, u, 0, light, overlay, -1, 0, 0);

            // RenderUtils.drawEquippedItem set -X once for this entire side batch; it did not
            // switch the second strip to +X. Preserve that legacy lighting quirk exactly.
            itemVertex(out, pose, x, 1, width, u, 0, light, overlay, -1, 0, 0);
            itemVertex(out, pose, x, 0, width, u, 1, light, overlay, -1, 0, 0);
            itemVertex(out, pose, x, 0, -width, u, 1, light, overlay, -1, 0, 0);
            itemVertex(out, pose, x, 1, -width, u, 0, light, overlay, -1, 0, 0);
        }
        pose.popPose();
    }

    private static void itemVertex(VertexConsumer out, PoseStack pose, float x, float y, float z,
                                   float u, float v, int light, int overlay) {
        itemVertex(out, pose, x, y, z, u, v, light, overlay, 0, 0, 1);
    }

    private static void itemVertex(VertexConsumer out, PoseStack pose, float x, float y, float z,
                                   float u, float v, int light, int overlay, float nx, float ny, float nz) {
        out.addVertex(pose.last(), x, y, z).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(overlay).setLight(light).setNormal(pose.last(), nx, ny, nz);
    }

    @Override
    public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager manager) {
        ACObjModel.clearCache();
    }
}
