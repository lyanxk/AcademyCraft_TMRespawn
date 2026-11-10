package cn.academy.client;

import cn.academy.AcademyCraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.minecraft.resources.ResourceLocation;
import cn.academy.registry.ACFluids;
import cn.academy.registry.ACEntities;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import cn.academy.registry.ACMenus;
import cn.academy.client.screen.ACMachineScreen;
import cn.academy.client.render.ACMachineRenderer;
import cn.academy.client.render.ACImagPhaseRenderer;
import cn.academy.client.render.ACItemRenderer;
import cn.academy.client.render.ACThrownItemRenderer;
import cn.academy.client.render.ACElectronBallRenderer;
import cn.academy.client.render.ACShaders;
import cn.academy.client.render.ACLegacyParticle;
import cn.academy.client.render.ACLegacyFont;
import cn.academy.client.render.ACGuiTextures;
import cn.academy.client.render.ACObjModel;
import cn.academy.registry.ACBlockEntities;
import cn.academy.registry.ACItems;
import cn.academy.registry.ACParticles;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class ACClientModEvents {
    static final ResourceLocation IMAG_PHASE_FLUID_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "block/black");
    static final int IMAG_PHASE_FLUID_TINT = 0xFFFFFFFF;

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ResourceLocation energy = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "energy");
            ItemProperties.register(ACItems.ENERGY_UNIT.get(), energy,
                    (stack, level, entity, seed) -> cn.academy.item.EnergyItem.legacyModelValue(stack));
            ItemProperties.register(ACItems.DEVELOPER_PORTABLE.get(), energy,
                    (stack, level, entity, seed) -> cn.academy.item.EnergyItem.legacyModelValue(stack));
            ItemProperties.register(ACItems.MATTER_UNIT.get(),
                    ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "frame"),
                    (stack, level, entity, seed) -> (int) (cn.academy.client.render.RailgunHandEffects.seconds() * 4) % 4);
            ItemProperties.register(ACItems.MATTER_UNIT.get(),
                    ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "phase_filled"),
                    (stack, level, entity, seed) -> cn.academy.item.MatterUnitItem.isFilled(stack) ? 1 : 0);
            ItemProperties.register(ACItems.INDUCTION_FACTOR.get(),
                    ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "category"),
                    (stack, level, entity, seed) -> Math.max(0, cn.academy.ability.AbilityRegistry.categoryIds().indexOf(
                            cn.academy.item.InductionFactorItem.category(stack))));
            ItemProperties.register(ACItems.MEDIA_ITEM.get(),
                    ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "media"),
                    (stack, level, entity, seed) -> cn.academy.item.MediaItem.model(cn.academy.item.MediaItem.media(stack)));
        });
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        ACShaders.register(event);
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            cn.academy.client.sound.ACMachineSounds.stopAll();
            ACObjModel.clearCache();
            ACLegacyFont.clear();
            ACGuiTextures.clear();
            cn.academy.client.render.ACSkillTreeRenderer.clear();
            cn.academy.client.sound.ACMediaLibrary.reload(manager);
            for (String model : new String[]{"solar", "ip_gen", "matrix", "windgen_base", "windgen_pillar",
                    "windgen_main", "windgen_fan", "developer_normal", "developer_advanced", "developer_portable",
                    "terminal_installer", "maghook", "maghook_open", "silbarn"}) ACObjModel.get(model);
        });
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ACParticles.ARC.get(), sprites -> new ACLegacyParticle.Provider(sprites, ACLegacyParticle.Style.ARC));
        event.registerSpriteSet(ACParticles.MELTDOWNER.get(), sprites -> new ACLegacyParticle.Provider(sprites, ACLegacyParticle.Style.MELTDOWNER));
        event.registerSpriteSet(ACParticles.TELEPORT.get(), sprites -> new ACLegacyParticle.Provider(sprites, ACLegacyParticle.Style.TELEPORT));
        event.registerSpriteSet(ACParticles.VECTOR.get(), sprites -> new ACLegacyParticle.Provider(sprites, ACLegacyParticle.Style.VECTOR));
        event.registerSpriteSet(ACParticles.SILBARN_FRAGMENT.get(), cn.academy.client.render.SilbarnEffects.Provider::new);
    }

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ACMenus.MACHINE.get(), ACMachineScreen::new);
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ACEntities.THROWN_ITEM.get(), ACThrownItemRenderer::new);
        event.registerEntityRenderer(ACEntities.ELECTRON_BALL.get(), ACElectronBallRenderer::new);
        event.registerEntityRenderer(ACEntities.MAG_MANIP_BLOCK.get(), cn.academy.client.render.MagManipBlockRenderer::new);
        event.registerBlockEntityRenderer(ACBlockEntities.MACHINE.get(), ACMachineRenderer::new);
        event.registerBlockEntityRenderer(ACBlockEntities.IMAG_PHASE.get(), ACImagPhaseRenderer::new);
    }

    @SubscribeEvent
    public static void registerFluidRendering(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override public ResourceLocation getStillTexture() { return IMAG_PHASE_FLUID_TEXTURE; }
            @Override public ResourceLocation getFlowingTexture() { return IMAG_PHASE_FLUID_TEXTURE; }
            @Override public int getTintColor() { return IMAG_PHASE_FLUID_TINT; }
            @Override public org.joml.Vector3f modifyFogColor(net.minecraft.client.Camera camera,
                    float partialTick, net.minecraft.client.multiplayer.ClientLevel level,
                    int renderDistance, float darkenWorldAmount, org.joml.Vector3f color) {
                return cn.academy.client.render.PhaseFluidFog.color(camera, color);
            }
            @Override public void modifyFogRender(net.minecraft.client.Camera camera,
                                                  net.minecraft.client.renderer.FogRenderer.FogMode mode,
                                                  float renderDistance, float partialTick, float nearDistance,
                                                  float farDistance, com.mojang.blaze3d.shaders.FogShape shape) {
                cn.academy.client.render.PhaseFluidFog.applyDistance(camera, renderDistance);
            }

        }, ACFluids.IMAG_PHASE_TYPE.get());

        IClientItemExtensions legacyModels = new IClientItemExtensions() {
            private final ACItemRenderer renderer = new ACItemRenderer();
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() { return renderer; }
            @Override public boolean shouldBobAsEntity(net.minecraft.world.item.ItemStack stack) { return false; }
        };
        event.registerItem(legacyModels,
                // These are exactly the four 1.12.2 items backed by a TEISR. Matter unit uses a
                // regular generated model plus its legacy frame item property.
                ACItems.DEVELOPER_PORTABLE.get(), ACItems.MAG_HOOK.get(), ACItems.SILBARN.get(),
                ACItems.COIN.get());
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(ACKeyMappings.ACTIVATE);
        event.register(ACKeyMappings.CAST_SKILL);
        event.register(ACKeyMappings.TERMINAL);
    }

    private ACClientModEvents() {}
}
