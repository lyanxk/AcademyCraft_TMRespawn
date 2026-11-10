package cn.academy.client.render;

import cn.academy.data.SharedSkillCosts;

import cn.academy.ability.*;
import cn.academy.ability.vanilla.vecmanip.skill.VectorStrikeContext;
import cn.academy.client.render.util.*;
import cn.academy.network.*;
import cn.academy.registry.ACSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import com.mojang.math.Axis;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import java.util.*;
import static cn.academy.network.VectorStrikePayload.*;

/** Local charge/punch clocks, ground particles and SmokeEffect. */
public final class VectorStrikeEffects {
    private static final ThunderClapRequests REQUESTS=new ThunderClapRequests();
    private static final Map<Integer,Strike> LOCAL=new LinkedHashMap<>(),REMOTE=new LinkedHashMap<>();
    private static final List<Smoke> SMOKE=new ArrayList<>();
    private static final Random RANDOM=new Random();
    private static final LegacyCubicCurve PX=new LegacyCubicCurve(0,-.04,.5,-.04,1,0),
            PY=new LegacyCubicCurve(0,.8,.5,.75,1,0),PZ=new LegacyCubicCurve(0,0,.3,-.4,1,0),
            RX=new LegacyCubicCurve(0,-40,.5,-45,1,0),RY=new LegacyCubicCurve(0,0,.3,10,1,0);
    private static ClientLevel world;
    private static Player local;
    private static final class Strike {
        int id,ticks,punchTicks;final int slot,request,kind;final Player owner;final float experience;
        double created=RailgunHandEffects.seconds(),punchedAt;
        boolean released,punched;
        Strike(Player owner,int kind,int slot,int request,float experience){this.owner=owner;this.kind=kind;this.slot=slot;this.request=request;this.experience=experience;}
    }
    private static final class Smoke {
        Vec3 position,previous;final Vec3 velocity;final float life=.5f+RANDOM.nextFloat()*.2f;
        final float rotSpeed=.3f*(RANDOM.nextFloat()+3);float rotation;
        final int frame=RANDOM.nextInt(4);final double created=RailgunHandEffects.seconds();
        Smoke(Vec3 position,Vec3 velocity){this.position=position;previous=position;this.velocity=velocity;}
    }
    private static boolean sync(Minecraft mc) {
        if(world!=mc.level || local!=mc.player){world=mc.level;local=mc.player;LOCAL.clear();REMOTE.clear();SMOKE.clear();REQUESTS.end(-1);}
        return world!=null && local!=null;
    }
    public static int beginLocal(int slot) {
        var mc=Minecraft.getInstance();if(!sync(mc) || slot<0 || slot >= cn.academy.ability.AbilityState.SKILL_CHANNELS)return 0;
        var state=AbilityState.load(local);var skill=AbilityRegistry.skill(state.controlSkill(slot));
        if(skill==null || !VectorStrikeContext.handles(skill) || !state.canStartContext(skill))return 0;
        endLocal(slot,true);int request=REQUESTS.begin(slot);
        var strike=new Strike(local,VectorStrikeContext.kind(skill,state.level()),slot,request,state.experience(skill.id()));
        // Groundshock is performed by the key-down packet; key-up must not submit another release.
        strike.released=strike.kind==GROUND;
        LOCAL.put(request,strike);return request;
    }
    public static void endLocal(int slot,boolean abort) {
        if(!sync(Minecraft.getInstance()))return;
        for(var strike:List.copyOf(LOCAL.values()))if(slot<0 || strike.slot==slot) {
            if(strike.released && !abort)continue;
            strike.released=true;boolean invalid=!VectorStrikeContext.accepts(strike.kind,strike.ticks);
            PacketDistributor.sendToServer(new VectorStrikeInputPayload(strike.request,strike.ticks,abort || invalid));
            if(abort || invalid){LOCAL.remove(strike.request);if(REQUESTS.accepts(strike.slot,strike.request))REQUESTS.end(strike.slot);}
        }
    }
    public static void receive(VectorStrikePayload packet) {
        if(!sync(Minecraft.getInstance()))return;
        if(packet.phase()==KNOCKBACK_VELOCITY) {
            for(int id:packet.entities()) {
                var target=world.getEntity(id);if(target!=null)target.setDeltaMovement(packet.point());
            }
            return;
        }
        if(packet.phase()==END) {
            REMOTE.remove(packet.context());
            if(packet.caster()==local.getId())LOCAL.remove(packet.request());
            return;
        }
        if(!(world.getEntity(packet.caster()) instanceof Player owner))return;
        Strike strike=owner==local?LOCAL.get(packet.request()):REMOTE.get(packet.context());
        if(packet.phase()==START) {
            if(owner==local && packet.request()!=0) {
                if(strike==null || !REQUESTS.accepts(packet.slot(),packet.request())) {
                    PacketDistributor.sendToServer(new VectorStrikeInputPayload(packet.request(),0,true));return;
                }
                strike.id=packet.context();
            } else {
                strike=new Strike(owner,packet.kind(),packet.slot(),packet.request(),packet.experience());strike.id=packet.context();
                if(owner==local)LOCAL.put(packet.request(),strike);else REMOTE.put(packet.context(),strike);
            }
            return;
        }
        if(packet.phase()==PERFORM && (packet.kind()==BLAST || packet.kind()==ENHANCED_SHOCK)) {
            if(strike!=null){strike.punched=true;strike.punchTicks=0;strike.punchedAt=RailgunHandEffects.seconds();}
            Vec3 point=owner.getEyePosition().lerp(packet.point(),.7);
            VectorDefenseEffects.wave(point,owner.getYHeadRot()-20+RANDOM.nextFloat()*40,owner.getXRot()-10+RANDOM.nextFloat()*20,2+RANDOM.nextInt(1),1);
            if(packet.kind()==BLAST)play(packet.point(),"vecmanip.groundshock",2);
            else play(packet.point(),"vecmanip.directed_blast",.5f);
        } else if(packet.phase()==HIT) {
            float exp=owner==local?AbilityState.load(local).experience(packet.kind()==SHOCK?"vecmanip.dir_shock":"vecmanip.dir_blast"):packet.experience();
            for(int target:packet.entities()) {
                var entity=world.getEntity(target);if(entity!=null && packet.kind()!=ENHANCED_SHOCK)VectorStrikeContext.knockback(owner,entity,packet.kind(),exp);
            }
            if(packet.kind()==SHOCK) {
                if(strike!=null){strike.punched=true;strike.punchTicks=0;strike.punchedAt=RailgunHandEffects.seconds();}
                play(owner.position(),"vecmanip.directed_shock",.5f);
            }
        } else if(packet.phase()==GROUND_EFFECT) {
            if(owner==local) {
                var state=AbilityState.load(local);var skill=AbilityRegistry.skill("vecmanip.ground_shock");float exp=packet.experience();
                state.consumeRaw(skill,SharedSkillCosts.groundCp(exp),SharedSkillCosts.groundOverload(exp),local.isCreative());
            }
            play(owner.position(),"vecmanip.groundshock",2);groundParticles(packet.blocks());
        }
    }
    private static void play(Vec3 point,String sound,float volume){world.playLocalSound(point.x,point.y,point.z,ACSounds.get(sound),SoundSource.AMBIENT,volume,1,false);}
    private static void groundParticles(List<BlockPos> blocks) {
        var mc=Minecraft.getInstance();
        for(var pos:blocks) {
            int count=4+RANDOM.nextInt(4);
            for(int i=0;i<count;i++) {
                var state=world.getBlockState(pos).getBlock().defaultBlockState();
                double x=pos.getX()+RANDOM.nextDouble(),y=pos.getY()+1+RANDOM.nextDouble()*.5+.2,z=pos.getZ()+RANDOM.nextDouble();
                double vx=-.2+RANDOM.nextDouble()*.4,vy=.1+RANDOM.nextDouble()*.2,vz=-.2+RANDOM.nextDouble()*.4;
                if(!state.isAir())mc.particleEngine.add(new TerrainParticle(world,x,y,z,vx,vy,vz,state,pos).updateSprite(state,pos));
            }
            if(RANDOM.nextFloat()<.5f) {
                var position=new Vec3(pos.getX()+.5-.3+RANDOM.nextDouble()*.6,pos.getY()+1+RANDOM.nextDouble()*.2,pos.getZ()+.5-.3+RANDOM.nextDouble()*.6);
                var velocity=new Vec3(-.03+RANDOM.nextDouble()*.06,.03+RANDOM.nextDouble()*.03,-.03+RANDOM.nextDouble()*.06);
                SMOKE.add(new Smoke(position,velocity));
            }
        }
    }
    public static void tick(Minecraft mc) {
        if(!sync(mc) || mc.isPaused())return;
        for(var strike:List.copyOf(LOCAL.values()))tick(strike,true);
        for(var strike:List.copyOf(REMOTE.values()))tick(strike,false);
        double now=RailgunHandEffects.seconds();SMOKE.removeIf(smoke->{smoke.rotation+=smoke.rotSpeed;smoke.previous=smoke.position;smoke.position=smoke.position.add(smoke.velocity);return now-smoke.created>=4;});
    }
    private static void tick(Strike strike,boolean isLocal) {
        if(strike.owner.isRemoved()){if(isLocal)LOCAL.remove(strike.request);else REMOTE.remove(strike.id);return;}
        if(isLocal || strike.kind==GROUND)strike.ticks++;
        if(isLocal) {
            if(strike.punched)strike.punchTicks++;
            if(strike.kind!=GROUND && strike.ticks>=200 || strike.punched && strike.punchTicks>6 || !AbilityState.load(local).active()) {
                PacketDistributor.sendToServer(new VectorStrikeInputPayload(strike.request,strike.ticks,true));LOCAL.remove(strike.request);
            }
        }
    }
    /** Apply the original prepare/punch curves in camera space, before bob/equip transforms. */
    public static void renderHand(RenderHandEvent event) {
        if(!sync(Minecraft.getInstance()))return;
        Strike current=null;for(var strike:LOCAL.values())if(strike.kind!=GROUND)current=strike;
        if(current==null)return;double now=RailgunHandEffects.seconds(),t;
        Matrix4f transform=new Matrix4f();
        if(current.punched) {
            t=(now-current.punchedAt)/.3;transform.translate((float)PX.at(t),(float)PY.at(t),(float)PZ.at(t));
            transform.rotateX((float)Math.toRadians(RX.at(t))).rotateY((float)Math.toRadians(RY.at(t)));
        } else {
            t=Math.min(2,(now-current.created)/.15);transform.translate((float)(-.02*t),(float)(.4*t),(float)(-.05*t)).rotateX((float)Math.toRadians(-20*t));
        }
        event.setCanceled(true);
        event.getPoseStack().pushPose();
        try {
        // GameRenderer keeps V outside the pose stack and starts hands with V^-1 * bob.
        // Convert the camera-space animation A to V^-1 * A * V before prepending it:
        // V * (V^-1 * A * V) * (V^-1 * bob) = A * bob, as in VanillaHandRenderer.
        // Prepending A directly instead makes the punch rotate with the player's world heading.
        Matrix4f view=new Matrix4f(RenderSystem.getModelViewStack());
        Matrix4f relativeTransform=new Matrix4f(view).invert().mul(transform).mul(view);
        Matrix3f rotation=new Matrix3f(relativeTransform);
        var pose=event.getPoseStack().last();pose.pose().set(relativeTransform.mul(new Matrix4f(pose.pose())));
        pose.normal().set(rotation.mul(new Matrix3f(pose.normal())));
        ((cn.academy.mixin.ItemInHandAccess)Minecraft.getInstance().gameRenderer.itemInHandRenderer).academy$renderArmWithItem(
                Minecraft.getInstance().player,event.getPartialTick(),event.getInterpolatedPitch(),event.getHand(),
                event.getSwingProgress(),event.getItemStack(),event.getEquipProgress(),event.getPoseStack(),event.getMultiBufferSource(),event.getPackedLight());
        } finally {event.getPoseStack().popPose();}
    }
    public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !sync(Minecraft.getInstance()))return;
        var mc=Minecraft.getInstance();var pose=event.getPoseStack();var buffers=mc.renderBuffers().bufferSource();var camera=event.getCamera().getPosition();
        double now=RailgunHandEffects.seconds();float partial=event.getPartialTick().getGameTimeDeltaPartialTick(true);
        for(var smoke:SMOKE) {
            double t=(now-smoke.created)/smoke.life;float alpha=(float)(t<=.3f?t/.3f:t<=1.5f?1:t<=2?1-(t-1.5f)/.5f:0);
            var point=smoke.previous.lerp(smoke.position,partial).subtract(camera);
            float yaw=(float)-Math.toDegrees(Math.atan2(point.x,point.z));float pitch=(float)-Math.toDegrees(Math.atan2(point.y,Math.sqrt(point.x*point.x+point.z*point.z)));
            pose.pushPose();pose.translate(point.x,point.y,point.z);pose.mulPose(Axis.YP.rotationDegrees(-yaw+180));pose.mulPose(Axis.XP.rotationDegrees(-pitch));
            var out=buffers.getBuffer(ACShaders.VECTOR_SMOKE);float u=(smoke.frame%2)/2f,v=(smoke.frame/2)/2f;int a=(int)(255*Math.clamp(alpha,0,1));
            out.addVertex(pose.last(),-1,-1,0).setUv(u,v).setColor(255,255,255,a);
            out.addVertex(pose.last(),-1,1,0).setUv(u,v+.5f).setColor(255,255,255,a);
            out.addVertex(pose.last(),1,1,0).setUv(u+.5f,v+.5f).setColor(255,255,255,a);
            out.addVertex(pose.last(),1,-1,0).setUv(u+.5f,v).setColor(255,255,255,a);pose.popPose();
        }
        buffers.endBatch(ACShaders.VECTOR_SMOKE);
    }
    private VectorStrikeEffects(){}
}
