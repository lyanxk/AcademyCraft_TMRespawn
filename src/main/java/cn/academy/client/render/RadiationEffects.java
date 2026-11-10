package cn.academy.client.render;

import cn.academy.client.render.util.MdParticle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.*;

/** Client-side MDDamageHelper.onUpdateClient; network marks intentionally refer to the caster. */
public final class RadiationEffects {
    private static final List<MdParticle> PARTICLES=new ArrayList<>();
    private static final Random RANDOM=new Random(),VECTORS=new Random();
    private static ClientLevel world;
    private static void sync(Minecraft mc) {
        if(world!=mc.level){PARTICLES.clear();world=mc.level;}
    }
    public static void emit(LivingEntity entity) {
        var mc=Minecraft.getInstance();sync(mc);
        if(world==null || entity.level()!=world)return;
        PARTICLES.addAll(MdParticle.radiation(RANDOM,VECTORS,entity.position(),entity.getBbWidth(),entity.getBbHeight()));
    }
    public static void tick(Minecraft mc) {
        sync(mc);if(world==null || mc.isPaused())return;
        PARTICLES.removeIf(p->{p.tick();return p.dead;});
    }
    public static int activeParticleCount(){return PARTICLES.size();}
    public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var mc=Minecraft.getInstance();sync(mc);if(world!=null)MdParticleRenderer.render(PARTICLES,event);
    }
    private RadiationEffects(){}
}
