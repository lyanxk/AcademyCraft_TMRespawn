package cn.academy.client.render;

import cn.academy.AcademyCraft;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;

/** Modern core-shader replacement for the legacy GLSL energy/wave programs. */
public final class ACShaders {
    private static ShaderInstance energyShader,plasmaBodyShader,tornadoShader;
    static ShaderInstance plasmaBody(){return plasmaBodyShader;}
    private static ShaderInstance guiMonochromeShader, skillTreeShader;
    private static ShaderInstance cpBarShader, cpBarOverloadShader;
    private static ShaderInstance arcShader;
    private static ShaderInstance legacyColorShader;
    private static ShaderInstance mineViewShader;
    private static ShaderInstance magneticBlockShader;
    private static ShaderInstance rayGlowShader, rayCylinderShader, railgunHandShader,diamondShader;
    static final RenderStateShard.TransparencyStateShard ARC_BLENDING = new RenderStateShard.TransparencyStateShard(
            "academy_legacy_arc_blending", () -> {
                com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                // glBlendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA) applies to alpha as well as RGB.
                // Use Separate so GlStateManager also updates its cached alpha factors.
                com.mojang.blaze3d.systems.RenderSystem.blendFuncSeparate(770, 771, 770, 771);
            }, () -> {
                com.mojang.blaze3d.systems.RenderSystem.disableBlend();
                com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            });
    private static final RenderStateShard.TransparencyStateShard PASS_ONE_BLENDING=new RenderStateShard.TransparencyStateShard(
            "academy_pass_one_blending",()->{
                com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                com.mojang.blaze3d.systems.RenderSystem.blendFuncSeparate(770,771,1,0);
            },()->{
                com.mojang.blaze3d.systems.RenderSystem.disableBlend();
                com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            });

    public static final RenderType[] MD_SMALL_GLOW=java.util.stream.Stream.of("blend_in","tile","blend_out")
            .map(name->passOneTexture("mdray_small/"+name+".png",false)).toArray(RenderType[]::new);
    public static final RenderType SILBARN_FRAGMENT = passOneTexture(ResourceLocation.fromNamespaceAndPath(
            "academy", "textures/entity/silbarn_frag.png"), true);
    public static final RenderType TP_PARTICLE=passOneTexture("tp_particle.png",true);
    public static final RenderType[] TP_MARK=java.util.stream.IntStream.range(0,7).<RenderType>mapToObj(i->RenderType.create("academy_tp_mark_"+i,
            DefaultVertexFormat.POSITION_TEX_COLOR,com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,4096,false,false,RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(()->diamondShader))
                    .setTextureState(new RenderStateShard.TextureStateShard(ResourceLocation.fromNamespaceAndPath("academy","textures/effects/tp_mark/"+i+".png"),false,false))
                    .setTransparencyState(PASS_ONE_BLENDING).setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST).setWriteMaskState(RenderStateShard.COLOR_WRITE).createCompositeState(false)))
            .toArray(RenderType[]::new);
    public static final RenderType MD_SMALL_PARTICLE=passOneTexture("md_particle.png",true);
    public static final RenderType MD_SHIELD=passOneTexture("mdshield.png",true);
    public static final RenderType ENTITY_MARKER=RenderType.create("academy_entity_marker",DefaultVertexFormat.POSITION_COLOR,
            com.mojang.blaze3d.vertex.VertexFormat.Mode.DEBUG_LINES,4096,false,false,RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(()->legacyColorShader))
                    .setLineState(new RenderStateShard.LineStateShard(java.util.OptionalDouble.of(3)))
                    .setCullState(RenderStateShard.NO_CULL).setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE).createCompositeState(false));
    public static final RenderType ENTITY_MARKER_NO_DEPTH=RenderType.create("academy_entity_marker_no_depth",DefaultVertexFormat.POSITION_COLOR,
            com.mojang.blaze3d.vertex.VertexFormat.Mode.DEBUG_LINES,4096,false,false,RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(()->legacyColorShader))
                    .setLineState(new RenderStateShard.LineStateShard(java.util.OptionalDouble.of(3)))
                    .setCullState(RenderStateShard.NO_CULL).setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE).createCompositeState(false));
    public static final RenderType PLASMA_BODY=RenderType.create("academy_plasma_body",DefaultVertexFormat.POSITION,
            com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,4096,false,false,RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(()->plasmaBodyShader))
                    .setTransparencyState(ARC_BLENDING).setCullState(RenderStateShard.CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST).setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));
    public static final RenderType VECTOR_TRAJECTORY=vectorTexture("glow_line.png",false);
    public static final RenderType VECTOR_TORNADO=RenderType.create("academy_vector_tornado",DefaultVertexFormat.NEW_ENTITY,
            com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,32768,false,false,RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(()->tornadoShader))
                    .setTextureState(new RenderStateShard.TextureStateShard(ResourceLocation.fromNamespaceAndPath("academy","textures/effects/tornado_ring.png"),false,false))
                    .setTransparencyState(ARC_BLENDING).setCullState(RenderStateShard.NO_CULL).setLightmapState(RenderStateShard.LIGHTMAP)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST).setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));
    public static final RenderType[] BLOOD_SPLASH=java.util.stream.IntStream.range(0,10)
            .mapToObj(i->vectorTexture("blood_splash/"+i+".png",true)).toArray(RenderType[]::new);
    public static final RenderType[][] BLOOD_SPRAY=java.util.stream.Stream.of("grnd","wall")
            .map(name->java.util.stream.IntStream.range(0,3).mapToObj(i->bloodSpray(name,i)).toArray(RenderType[]::new)).toArray(RenderType[][]::new);
    private static RenderType vectorTexture(String path,boolean alphaTest) {
        return RenderType.create("academy_vector_"+path,DefaultVertexFormat.POSITION_TEX_COLOR,
                com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,4096,false,false,RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(()->alphaTest?railgunHandShader:diamondShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(ResourceLocation.fromNamespaceAndPath("academy","textures/effects/"+path),false,false))
                        .setTransparencyState(ARC_BLENDING).setCullState(RenderStateShard.NO_CULL).setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE).createCompositeState(false));
    }
    private static RenderType bloodSpray(String side,int index) {
        return RenderType.create("academy_blood_spray_"+side+index,DefaultVertexFormat.NEW_ENTITY,
                com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,4096,false,false,RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeEntityCutoutNoCullShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(ResourceLocation.fromNamespaceAndPath("academy","textures/effects/blood_spray/"+side+"/"+index+".png"),false,false))
                        .setTransparencyState(ARC_BLENDING).setCullState(RenderStateShard.NO_CULL).setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setLightmapState(RenderStateShard.LIGHTMAP).setOverlayState(RenderStateShard.OVERLAY)
                        .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE).createCompositeState(false));
    }
    public static final RenderType VECTOR_SMOKE=RenderType.create("academy_vector_smoke",DefaultVertexFormat.POSITION_TEX_COLOR,
            com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,8192,false,false,RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(()->diamondShader))
                    .setTextureState(new RenderStateShard.TextureStateShard(ResourceLocation.fromNamespaceAndPath(
                            "academy","textures/effects/smokes.png"),false,false))
                    .setTransparencyState(ARC_BLENDING).setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST).setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));
    public static final RenderType VECTOR_WAVE=RenderType.create("academy_vector_wave",DefaultVertexFormat.POSITION_TEX_COLOR,
            com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,4096,false,false,RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(()->diamondShader))
                    .setTextureState(new RenderStateShard.TextureStateShard(ResourceLocation.fromNamespaceAndPath(
                            "academy","textures/effects/glow_circle.png"),true,false))
                    .setTransparencyState(ARC_BLENDING).setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST).setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));
    public static final RenderType[] MD_GLOW=java.util.stream.Stream.of("blend_in","tile","blend_out")
            .map(name->passOneTexture("mdray/"+name+".png",false)).toArray(RenderType[]::new);
    public static final RenderType DIAMOND_SHIELD=RenderType.create("academy_diamond_shield",DefaultVertexFormat.POSITION_TEX_COLOR,
            com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES,256,false,false,RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(()->diamondShader))
                    .setTextureState(new RenderStateShard.TextureStateShard(ResourceLocation.fromNamespaceAndPath(
                            "academy","textures/effects/diamond_shield.png"),false,false))
                    .setTransparencyState(ARC_BLENDING).setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST).setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));
    private static RenderType passOneTexture(String path,boolean particle) {
        return passOneTexture(ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/" + path), particle);
    }
    private static RenderType passOneTexture(ResourceLocation texture,boolean particle) {
        return RenderType.create("academy_pass_one_"+texture,DefaultVertexFormat.POSITION_TEX_COLOR,
                com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,16384,false,false,RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(()->rayGlowShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(texture,false,false))
                        .setTransparencyState(particle?ARC_BLENDING:PASS_ONE_BLENDING).setCullState(RenderStateShard.NO_CULL)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST).setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .createCompositeState(false));
    }
    public static final RenderType MD_SMALL_CYLINDER=RenderType.create("academy_md_small_cylinder",
            DefaultVertexFormat.POSITION_COLOR,com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,16384,false,false,
            RenderType.CompositeState.builder().setShaderState(new RenderStateShard.ShaderStateShard(()->rayCylinderShader))
                    .setTransparencyState(PASS_ONE_BLENDING).setCullState(RenderStateShard.CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST).setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    public static final RenderType ARC = RenderType.create("academy_legacy_arc",
            DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 16384,
            false, false, RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> arcShader))
                    .setTextureState(new RenderStateShard.TextureStateShard(
                            ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/arc/line_segment.png"), false, false))
                    .setTransparencyState(ARC_BLENDING)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .createCompositeState(false));

    public static final RenderType RAILGUN_HEAD = rayTexture("railgun/blend_in.png", false);
    public static final RenderType MD_BALL_GLOW = ballTexture("glow");
    public static final RenderType[] MD_BALL = java.util.stream.IntStream.range(0,5)
            .mapToObj(i->ballTexture(Integer.toString(i))).toArray(RenderType[]::new);
    private static RenderType ballTexture(String name) {
        return RenderType.create("academy_md_ball_"+name,DefaultVertexFormat.POSITION_TEX_COLOR,
                com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,256,false,false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(()->railgunHandShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(ResourceLocation.fromNamespaceAndPath(
                                "academy","textures/effects/mdball/"+name+".png"),false,false))
                        .setTransparencyState(PASS_ONE_BLENDING)
                        .setCullState(RenderStateShard.NO_CULL).setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE).createCompositeState(false));
    }
    public static final RenderType MD_PARTICLE=rayTexture("md_particle.png",false);
    public static final RenderType MD_PARTICLE_LUCK=rayTexture("md_particle_luck.png",false);
    public static final RenderType[][] MINE_RAY_GLOW=java.util.Arrays.stream(cn.academy.client.render.util.MineRayStyle.STYLES)
            .map(style->new RenderType[]{rayTexture(style.texture()+"/blend_in.png",false),
                    rayTexture(style.texture()+"/tile.png",false),rayTexture(style.texture()+"/blend_out.png",false)})
            .toArray(RenderType[][]::new);
    public static final RenderType RAILGUN_BODY = rayTexture("railgun/tile.png", false);
    public static final RenderType RAILGUN_TAIL = rayTexture("railgun/blend_out.png", false);
    public static final RenderType SUB_ARCS = rayTexture("arc/line_segment.png", true);
    public static final RenderType RIPPLE_MARK = RenderType.create("academy_ripple_mark",
            DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 256, false, false,
            RenderType.CompositeState.builder().setShaderState(new RenderStateShard.ShaderStateShard(() -> railgunHandShader))
                    .setTextureState(new RenderStateShard.TextureStateShard(ResourceLocation.fromNamespaceAndPath(
                            "academy", "textures/effects/ripple.png"), false, false))
                    .setTransparencyState(ARC_BLENDING).setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST).setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));
    public static final RenderType INTENSIFY_MASK = legacyHudTexture("em_intensify_mask.png");
    public static final RenderType[] INTENSIFY_HUD_ARCS = java.util.stream.IntStream.range(0, 10)
            .mapToObj(i -> legacyHudTexture("arcs/" + i + ".png")).toArray(RenderType[]::new);
    public static final RenderType INTENSIFY_BLACK = RenderType.create("academy_intensify_black",
            DefaultVertexFormat.POSITION_COLOR, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 256, false, false,
            RenderType.CompositeState.builder().setShaderState(new RenderStateShard.ShaderStateShard(() -> legacyColorShader))
                    .setTransparencyState(ARC_BLENDING).setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST).setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));
    private static RenderType legacyHudTexture(String path) {
        return RenderType.create("academy_intensify_" + path, DefaultVertexFormat.POSITION_TEX_COLOR,
                com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 256, false, false, RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(() -> arcShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(ResourceLocation.fromNamespaceAndPath(
                                "academy", "textures/effects/" + path), false, false))
                        .setTransparencyState(ARC_BLENDING).setCullState(RenderStateShard.NO_CULL)
                        .setDepthTestState(RenderStateShard.NO_DEPTH_TEST).setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .createCompositeState(false));
    }
    public static final RenderType[] RAILGUN_HAND = java.util.stream.IntStream.range(0, 40)
            .mapToObj(ACShaders::handTexture).toArray(RenderType[]::new);
    private static RenderType handTexture(int frame) {
        return RenderType.create("academy_railgun_hand_" + frame, DefaultVertexFormat.POSITION_TEX_COLOR,
                com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 256, false, false, RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(() -> railgunHandShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(ResourceLocation.fromNamespaceAndPath(
                                "academy", "textures/effects/arc_burst/" + frame + ".png"), false, false))
                        .setTransparencyState(ARC_BLENDING).setCullState(RenderStateShard.NO_CULL)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE).createCompositeState(false));
    }
    private static RenderType rayTexture(String path, boolean arc) {
        return RenderType.create("academy_legacy_" + path, DefaultVertexFormat.POSITION_TEX_COLOR,
                com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 16384, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(() -> arc ? arcShader : rayGlowShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(
                                ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/" + path), false, false))
                        .setTransparencyState(ARC_BLENDING).setCullState(RenderStateShard.NO_CULL)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(arc ? RenderStateShard.COLOR_WRITE : RenderStateShard.COLOR_DEPTH_WRITE)
                        .createCompositeState(false));
    }
    public static final RenderType RAILGUN_CYLINDER = RenderType.create("academy_legacy_ray_cylinder",
            DefaultVertexFormat.POSITION_COLOR, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 16384,
            false, false, RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> rayCylinderShader))
                    .setTransparencyState(ARC_BLENDING).setCullState(RenderStateShard.CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE).createCompositeState(false));

    static ShaderInstance skillTree() { return skillTreeShader; }
    static ShaderInstance guiMonochrome() { return guiMonochromeShader; }
    static ShaderInstance cpBar() { return cpBarShader; }
    static ShaderInstance cpBarOverload() { return cpBarOverloadShader; }

    private static final RenderStateShard.DepthTestStateShard MINE_DEPTH = new RenderStateShard.DepthTestStateShard("academy_mine_no_depth", 519) {
        @Override public void setupRenderState() { com.mojang.blaze3d.systems.RenderSystem.disableDepthTest(); }
        @Override public void clearRenderState() { com.mojang.blaze3d.systems.RenderSystem.enableDepthTest(); }
    };
    public static final RenderType MINE_VIEW = RenderType.create("academy_mine_view",
            DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES, 4096,
            false, false, RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> mineViewShader))
                    .setTextureState(new RenderStateShard.TextureStateShard(ResourceLocation.fromNamespaceAndPath(
                            "academy", "textures/effects/mineview.png"), false, false))
                    .setTransparencyState(ARC_BLENDING)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(MINE_DEPTH)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    public static final RenderType MAGNETIC_BLOCK = RenderType.create("academy_magnetic_block",
            DefaultVertexFormat.BLOCK,com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,1536,false,false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(()->magneticBlockShader))
                    .setTextureState(new RenderStateShard.TextureStateShard(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS,false,true))
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                    .setCullState(RenderStateShard.CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE).createCompositeState(false));

    public static final RenderType ENERGY = RenderType.create("academy_energy",
            DefaultVertexFormat.POSITION_COLOR, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 1536,
            false, true, RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> energyShader != null
                            ? energyShader : GameRenderer.getPositionColorShader()))
                    .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID,"vector_tornado"),
                    DefaultVertexFormat.NEW_ENTITY),shader->tornadoShader=shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID,"plasma_body"),
                    DefaultVertexFormat.POSITION),shader->plasmaBodyShader=shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID,"md_diamond"),
                    DefaultVertexFormat.POSITION_TEX_COLOR),shader->diamondShader=shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID,"magnetic_block"),
                    DefaultVertexFormat.BLOCK),shader->magneticBlockShader=shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "legacy_mine_view"),
                    DefaultVertexFormat.POSITION_TEX_COLOR), shader -> mineViewShader = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "legacy_color"),
                    DefaultVertexFormat.POSITION_COLOR), shader -> legacyColorShader = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "railgun_hand"),
                    DefaultVertexFormat.POSITION_TEX_COLOR), shader -> railgunHandShader = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "legacy_ray_glow"),
                    DefaultVertexFormat.POSITION_TEX_COLOR), shader -> rayGlowShader = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "legacy_ray_cylinder"),
                    DefaultVertexFormat.POSITION_COLOR), shader -> rayCylinderShader = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "legacy_arc"),
                    DefaultVertexFormat.POSITION_TEX_COLOR), shader -> arcShader = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "ac_energy"),
                    DefaultVertexFormat.POSITION_COLOR), shader -> energyShader = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "skill_tree"),
                    DefaultVertexFormat.POSITION_TEX_COLOR), shader -> skillTreeShader = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "gui_monochrome"),
                    DefaultVertexFormat.POSITION_TEX_COLOR), shader -> guiMonochromeShader = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "cpbar_cp"),
                    DefaultVertexFormat.POSITION_TEX_COLOR), shader -> cpBarShader = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "cpbar_overload"),
                    DefaultVertexFormat.POSITION_TEX_COLOR), shader -> cpBarOverloadShader = shader);
        } catch (IOException exception) {
            throw new RuntimeException("Unable to load AcademyCraft shaders", exception);
        }
    }

    private ACShaders() {}
}
